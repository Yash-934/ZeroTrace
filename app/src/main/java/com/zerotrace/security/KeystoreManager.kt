package com.zerotrace.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the Android Keystore AES-256-GCM wrapping key and encrypted envelopes in `noBackupFilesDir`.
 *
 * Architecture (Section 5.1):
 *   Hardware-backed AES-256-GCM Key (StrongBox -> TEE -> Software Fallback)
 *       |
 *       v
 *   Encrypted Identity & Contact Material (12-byte IV + 128-bit GCM Tag + AAD binding)
 *       |
 *       v
 *   App-Private `noBackupFilesDir` Storage
 */
class KeystoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "zerotrace_identity_wrap_key_v2"
        private const val IDENTITY_FILE_NAME = "identity_envelope_v2.bin"
        private const val CONTACTS_FILE_NAME = "contacts_envelope_v2.bin"
        private const val PREFS_PROFILE_FILE_NAME = "prefs_profile_envelope_v2.bin"
        private const val CHAT_VAULT_FILE_NAME = "chat_vault_envelope_v2.bin"
        private const val ENVELOPE_MAGIC = 0x5A543245 // "ZT2E"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }

    private var requestedStrongBox: Boolean = false
    private var currentSecurityLevel: HardwareSecurityLevel = HardwareSecurityLevel.UNINITIALIZED

    // Volatile fallback key used only in local JVM unit test environments without AndroidKeyStore provider
    @Volatile
    private var jvmTestFallbackKey: SecretKey? = null

    fun getHardwareSecurityLevel(): HardwareSecurityLevel {
        if (currentSecurityLevel == HardwareSecurityLevel.UNINITIALIZED) {
            val key = getOrCreateWrappingKey()
            if (jvmTestFallbackKey == null) {
                currentSecurityLevel = StrongBoxManager.inspectKeySecurityLevel(key, requestedStrongBox)
            }
        }
        return currentSecurityLevel
    }

    @Synchronized
    fun getOrCreateWrappingKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                currentSecurityLevel = StrongBoxManager.inspectKeySecurityLevel(
                    existing.secretKey,
                    requestedStrongBox
                )
                return existing.secretKey
            }

            if (StrongBoxManager.hasStrongBoxFeature(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val strongBoxKey = generateAndroidKeystoreKey(useStrongBox = true)
                    requestedStrongBox = true
                    currentSecurityLevel = StrongBoxManager.inspectKeySecurityLevel(strongBoxKey, true)
                    return strongBoxKey
                } catch (_: StrongBoxUnavailableException) {
                    // Fall through to TEE / standard AndroidKeyStore
                } catch (_: Exception) {
                    // Fall through to TEE / standard AndroidKeyStore
                }
            }

            val teeKey = generateAndroidKeystoreKey(useStrongBox = false)
            requestedStrongBox = false
            currentSecurityLevel = StrongBoxManager.inspectKeySecurityLevel(teeKey, false)
            return teeKey
        } catch (_: Exception) {
            // Honest software fallback for JVM Robolectric unit tests where AndroidKeyStore is absent
            currentSecurityLevel = HardwareSecurityLevel.SOFTWARE_FALLBACK
            jvmTestFallbackKey?.let { return it }
            val raw = ByteArray(32)
            SecureRandom().nextBytes(raw)
            val sk = SecretKeySpec(raw, "AES")
            Arrays.fill(raw, 0.toByte())
            jvmTestFallbackKey = sk
            return sk
        }
    }

    private fun generateAndroidKeystoreKey(useStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Wraps sensitive bytes using the Android Keystore AES-256-GCM key with domain-separated AAD.
     */
    fun wrapBytes(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val secretKey = getOrCreateWrappingKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (jvmTestFallbackKey != null) {
            val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        } else {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        }
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
        require(iv.size == GCM_IV_LENGTH) { "Unexpected GCM IV length: ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext)

        val buffer = ByteBuffer.allocate(4 + 4 + iv.size + ciphertext.size)
        buffer.putInt(ENVELOPE_MAGIC)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    /**
     * Unwraps an AES-256-GCM envelope and returns the decrypted plaintext bytes.
     */
    fun unwrapBytes(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        require(envelope.size > 4 + 4 + GCM_IV_LENGTH + 16) { "Envelope truncated" }
        val buffer = ByteBuffer.wrap(envelope)
        val magic = buffer.int
        require(magic == ENVELOPE_MAGIC) { "Invalid envelope magic header" }
        val ivLen = buffer.int
        require(ivLen == GCM_IV_LENGTH) { "Invalid GCM IV length" }
        val iv = ByteArray(ivLen)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val secretKey = getOrCreateWrappingKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    fun saveWrappedIdentity(serializedIdentity: ByteArray) {
        val aad = "ZT2-WRAPPED-IDENTITY-V2".toByteArray(Charsets.UTF_8)
        val wrapped = wrapBytes(serializedIdentity, aad)
        val file = File(context.noBackupFilesDir, IDENTITY_FILE_NAME)
        file.writeBytes(wrapped)
    }

    fun loadWrappedIdentity(): ByteArray? {
        val file = File(context.noBackupFilesDir, IDENTITY_FILE_NAME)
        if (!file.exists()) return null
        val aad = "ZT2-WRAPPED-IDENTITY-V2".toByteArray(Charsets.UTF_8)
        return unwrapBytes(file.readBytes(), aad)
    }

    fun hasStoredIdentity(): Boolean {
        return File(context.noBackupFilesDir, IDENTITY_FILE_NAME).exists()
    }

    fun saveWrappedContacts(serializedContacts: ByteArray) {
        val aad = "ZT2-WRAPPED-CONTACTS-V2".toByteArray(Charsets.UTF_8)
        val wrapped = wrapBytes(serializedContacts, aad)
        val file = File(context.noBackupFilesDir, CONTACTS_FILE_NAME)
        file.writeBytes(wrapped)
    }

    fun loadWrappedContacts(): ByteArray? {
        val file = File(context.noBackupFilesDir, CONTACTS_FILE_NAME)
        if (!file.exists()) return null
        val aad = "ZT2-WRAPPED-CONTACTS-V2".toByteArray(Charsets.UTF_8)
        return unwrapBytes(file.readBytes(), aad)
    }

    fun saveWrappedPrefsAndProfile(serialized: ByteArray) {
        val aad = "ZT2-WRAPPED-PREFS-PROFILE-V2".toByteArray(Charsets.UTF_8)
        val wrapped = wrapBytes(serialized, aad)
        File(context.noBackupFilesDir, PREFS_PROFILE_FILE_NAME).writeBytes(wrapped)
    }

    fun loadWrappedPrefsAndProfile(): ByteArray? {
        val file = File(context.noBackupFilesDir, PREFS_PROFILE_FILE_NAME)
        if (!file.exists()) return null
        val aad = "ZT2-WRAPPED-PREFS-PROFILE-V2".toByteArray(Charsets.UTF_8)
        return unwrapBytes(file.readBytes(), aad)
    }

    fun saveWrappedChatVault(serializedVault: ByteArray) {
        val aad = "ZT2-WRAPPED-CHAT-VAULT-V2".toByteArray(Charsets.UTF_8)
        val wrapped = wrapBytes(serializedVault, aad)
        File(context.noBackupFilesDir, CHAT_VAULT_FILE_NAME).writeBytes(wrapped)
    }

    fun loadWrappedChatVault(): ByteArray? {
        val file = File(context.noBackupFilesDir, CHAT_VAULT_FILE_NAME)
        if (!file.exists()) return null
        val aad = "ZT2-WRAPPED-CHAT-VAULT-V2".toByteArray(Charsets.UTF_8)
        return unwrapBytes(file.readBytes(), aad)
    }

    @Synchronized
    fun wipeWrappedChatVault() {
        val file = File(context.noBackupFilesDir, CHAT_VAULT_FILE_NAME)
        if (file.exists()) {
            val len = file.length().toInt().coerceAtMost(2 * 1024 * 1024)
            if (len > 0) {
                file.writeBytes(ByteArray(len))
            }
            file.delete()
        }
    }

    /**
     * Permanently deletes wrapped identity, contacts, prefs/profile, and chat vault files,
     * overwrites their bytes on disk best-effort, and deletes the master wrapping key from AndroidKeyStore.
     */
    @Synchronized
    fun deleteIdentityAndWrappingKey() {
        listOf(
            IDENTITY_FILE_NAME,
            CONTACTS_FILE_NAME,
            PREFS_PROFILE_FILE_NAME,
            CHAT_VAULT_FILE_NAME
        ).forEach { fileName ->
            val file = File(context.noBackupFilesDir, fileName)
            if (file.exists()) {
                val len = file.length().toInt().coerceAtMost(2 * 1024 * 1024)
                if (len > 0) {
                    file.writeBytes(ByteArray(len))
                }
                file.delete()
            }
        }
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        } catch (_: Exception) {
        }
        jvmTestFallbackKey = null
        currentSecurityLevel = HardwareSecurityLevel.UNINITIALIZED
    }
}
