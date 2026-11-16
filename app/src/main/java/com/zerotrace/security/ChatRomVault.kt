package com.zerotrace.security

import android.content.Context
import com.zerotrace.bridge.EncryptedFileManifest
import com.zerotrace.bridge.EncryptedTransferProgress
import com.zerotrace.bridge.MessageDeliveryState
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.RustCoreBridge
import com.zerotrace.bridge.TransferDirection
import com.zerotrace.bridge.TransferLifecycleState
import com.zerotrace.bridge.toHex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class RomChatArchive(
    val onionAddress: String,
    val messages: List<RichConversationMessage>,
    val lastPreview: String?,
    val lastTimestampMs: Long?
)

data class ChatRomDiskStats(
    val onionAddress: String,
    val messageCount: Int = 0,
    val mediaBlobCount: Int = 0,
    val totalEncryptedBytes: Long = 0L
)

data class ChatStorageBreakdown(
    val onionAddress: String,
    val alias: String,
    val keepDataEnabled: Boolean,
    val ramMessageCount: Int,
    val ramMediaCount: Int,
    val ramTotalBytes: Long,
    val romMessageCount: Int,
    val romMediaCount: Int,
    val romTotalBytes: Long
)

/**
 * Post-Quantum-Margin Double-Layer Cascade Per-Chat ROM Vault.
 *
 * Security Architecture:
 *  - Every message archive and media blob is encrypted by TWO independent 256-bit AEAD ciphers:
 *      Layer 1 (Inner): 256-bit ChaCha20-Poly1305 with fresh 96-bit nonce and HKDF-SHA512 derived per-blob key
 *      Layer 2 (Outer): 256-bit AES-256-GCM with fresh 96-bit IV and strict domain-separated AAD
 *  - Both 256-bit ephemeral keys + 64-byte master root entropy are sealed by Android Keystore
 *    (StrongBox hardware HSM when available, else hardware TEE).
 *  - Filenames on disk are SHA-256 digests so even `.onion` identities and filenames remain hidden.
 *  - Stored strictly inside `context.noBackupFilesDir/zt_rom_vault_v3/` (never cloud backed up).
 */
class ChatRomVault(
    private val context: Context,
    private val keystoreManager: KeystoreManager
) {
    companion object {
        private const val VAULT_ROOT_DIR = "zt_rom_vault_v3"
        private const val MASTER_ENTROPY_FILE = "rom_master_entropy_v3.bin"
        private const val CHAT_ARCHIVE_FILE = "archive.ztr"
        private const val ENVELOPE_MAGIC = 0x5A545233 // "ZTR3"
        private const val ARCHIVE_MAGIC = 0x5A544133  // "ZTA3"
        private const val MASTER_AAD = "ZT2-ROM-MASTER-ENTROPY-V3"
    }

    private val csprng = SecureRandom()
    private val ioLock = Any()

    @Volatile
    private var cachedMasterEntropy64: ByteArray? = null

    private fun vaultRootDir(): File = File(context.noBackupFilesDir, VAULT_ROOT_DIR).apply { mkdirs() }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).toHex()

    private fun chatDir(onionAddress: String, create: Boolean = false): File {
        val norm = onionAddress.trim().lowercase()
        val dir = File(vaultRootDir(), sha256Hex("ZT2-CHAT-DIR|$norm"))
        if (create && !dir.exists()) dir.mkdirs()
        return dir
    }

    private fun mediaBlobFile(onionAddress: String, transferId: String, createDir: Boolean = false): File {
        val dir = chatDir(onionAddress, create = createDir)
        val hashedName = "blob_${sha256Hex("ZT2-BLOB|$transferId")}.ztb"
        return File(dir, hashedName)
    }

    private fun getOrCreateMasterEntropy64(): ByteArray {
        cachedMasterEntropy64?.let { return it }
        synchronized(ioLock) {
            cachedMasterEntropy64?.let { return it }
            val file = File(vaultRootDir(), MASTER_ENTROPY_FILE)
            if (file.exists()) {
                try {
                    val loaded = keystoreManager.unwrapBytes(
                        file.readBytes(),
                        MASTER_AAD.toByteArray(Charsets.UTF_8)
                    )
                    if (loaded.size == 64) {
                        cachedMasterEntropy64 = loaded
                        return loaded
                    }
                    Arrays.fill(loaded, 0.toByte())
                } catch (_: Exception) {
                    // Fall through to re-generate if corrupted
                }
            }
            val fresh = ByteArray(64).also { csprng.nextBytes(it) }
            val wrapped = keystoreManager.wrapBytes(fresh, MASTER_AAD.toByteArray(Charsets.UTF_8))
            file.writeBytes(wrapped)
            cachedMasterEntropy64 = fresh
            return fresh
        }
    }

    private fun deriveDomainKeyMix(
        masterEntropy64: ByteArray,
        salt32: ByteArray,
        domainInfo: String
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(masterEntropy64, "HmacSHA512"))
        mac.update(salt32)
        mac.update(domainInfo.toByteArray(Charsets.UTF_8))
        return mac.doFinal() // 64 bytes: [0..31] L1 mix, [32..63] L2 mix
    }

    /**
     * Encrypts [plaintext] with dual independent 256-bit AEAD cascade ciphers
     * (ChaCha20-Poly1305 inner + AES-256-GCM outer) and wraps key material with Android Keystore.
     */
    fun sealCascadeEnvelope(
        plaintext: ByteArray,
        onionAddress: String,
        domainTag: String
    ): ByteArray {
        val normOnion = onionAddress.trim().lowercase()
        val masterEntropy = getOrCreateMasterEntropy64()
        val salt32 = ByteArray(32).also { csprng.nextBytes(it) }
        val kChaChaRand = ByteArray(32).also { csprng.nextBytes(it) }
        val kAesRand = ByteArray(32).also { csprng.nextBytes(it) }
        val nonceL1 = ByteArray(12).also { csprng.nextBytes(it) }
        val ivL2 = ByteArray(12).also { csprng.nextBytes(it) }
        val mix64 = deriveDomainKeyMix(masterEntropy, salt32, "ZT2-ROM-KDF|$normOnion|$domainTag")

        val effChaChaKey = ByteArray(32) { i -> (kChaChaRand[i].toInt() xor mix64[i].toInt()).toByte() }
        val effAesKey = ByteArray(32) { i -> (kAesRand[i].toInt() xor mix64[32 + i].toInt()).toByte() }
        val packedKeys = ByteArray(96).also {
            System.arraycopy(kChaChaRand, 0, it, 0, 32)
            System.arraycopy(kAesRand, 0, it, 32, 32)
            System.arraycopy(salt32, 0, it, 64, 32)
        }

        return try {
            val hwAad = "ZT2-ROM-HW-SEAL|$normOnion|$domainTag".toByteArray(Charsets.UTF_8)
            val hwWrappedKeys = keystoreManager.wrapBytes(packedKeys, hwAad)

            // Layer 1: 256-bit ChaCha20-Poly1305
            val aadL1 = "ZT2-ROM-L1-CHACHA|$normOnion|$domainTag|${plaintext.size}".toByteArray(Charsets.UTF_8)
            val l1Ciphertext = RustCoreBridge.aeadEncryptChaCha20Poly1305(
                key32 = effChaChaKey,
                nonce12 = nonceL1,
                plaintext = plaintext,
                associatedData = aadL1
            )

            // Layer 2: 256-bit AES-256-GCM cascade over Layer 1 output
            val aadL2 = "ZT2-ROM-L2-AESGCM|$normOnion|$domainTag|${l1Ciphertext.size}".toByteArray(Charsets.UTF_8)
            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            aesCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(effAesKey, "AES"), GCMParameterSpec(128, ivL2))
            aesCipher.updateAAD(aadL2)
            val l2Ciphertext = aesCipher.doFinal(l1Ciphertext)

            val totalCap = 4 + 4 + hwWrappedKeys.size + 12 + 12 + 4 + l2Ciphertext.size
            ByteBuffer.allocate(totalCap).apply {
                putInt(ENVELOPE_MAGIC)
                putInt(hwWrappedKeys.size)
                put(hwWrappedKeys)
                put(nonceL1)
                put(ivL2)
                putInt(l2Ciphertext.size)
                put(l2Ciphertext)
            }.array()
        } finally {
            Arrays.fill(kChaChaRand, 0.toByte())
            Arrays.fill(kAesRand, 0.toByte())
            Arrays.fill(salt32, 0.toByte())
            Arrays.fill(mix64, 0.toByte())
            Arrays.fill(effChaChaKey, 0.toByte())
            Arrays.fill(effAesKey, 0.toByte())
            Arrays.fill(packedKeys, 0.toByte())
        }
    }

    /**
     * Unwraps Hardware Keystore keys, verifies + decrypts outer AES-256-GCM, then verifies +
     * decrypts inner ChaCha20-Poly1305. Fails closed if any byte, tag, or onion AAD is tampered.
     */
    fun openCascadeEnvelope(
        envelopeBytes: ByteArray,
        onionAddress: String,
        domainTag: String
    ): ByteArray {
        val normOnion = onionAddress.trim().lowercase()
        val buf = ByteBuffer.wrap(envelopeBytes)
        require(buf.remaining() > 4 + 4 + 12 + 12 + 4) { "ROM envelope truncated" }
        val magic = buf.int
        require(magic == ENVELOPE_MAGIC) { "Invalid ROM envelope header" }

        val hwLen = buf.int
        require(hwLen in 32..8192 && buf.remaining() >= hwLen + 28) { "Corrupt wrapped key header" }
        val hwWrappedKeys = ByteArray(hwLen).also { buf.get(it) }
        val nonceL1 = ByteArray(12).also { buf.get(it) }
        val ivL2 = ByteArray(12).also { buf.get(it) }
        val ctLen = buf.int
        require(ctLen > 16 && buf.remaining() >= ctLen) { "Corrupt ROM ciphertext length" }
        val l2Ciphertext = ByteArray(ctLen).also { buf.get(it) }

        val hwAad = "ZT2-ROM-HW-SEAL|$normOnion|$domainTag".toByteArray(Charsets.UTF_8)
        val packedKeys = keystoreManager.unwrapBytes(hwWrappedKeys, hwAad)
        require(packedKeys.size == 96) { "Unexpected key bundle length" }

        val kChaChaRand = packedKeys.copyOfRange(0, 32)
        val kAesRand = packedKeys.copyOfRange(32, 64)
        val salt32 = packedKeys.copyOfRange(64, 96)
        val masterEntropy = getOrCreateMasterEntropy64()
        val mix64 = deriveDomainKeyMix(masterEntropy, salt32, "ZT2-ROM-KDF|$normOnion|$domainTag")
        val effChaChaKey = ByteArray(32) { i -> (kChaChaRand[i].toInt() xor mix64[i].toInt()).toByte() }
        val effAesKey = ByteArray(32) { i -> (kAesRand[i].toInt() xor mix64[32 + i].toInt()).toByte() }

        return try {
            // Layer 2 Decrypt: AES-256-GCM
            val innerLenGuess = ctLen - 16
            val aadL2 = "ZT2-ROM-L2-AESGCM|$normOnion|$domainTag|$innerLenGuess".toByteArray(Charsets.UTF_8)
            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(effAesKey, "AES"), GCMParameterSpec(128, ivL2))
            aesCipher.updateAAD(aadL2)
            val l1Ciphertext = aesCipher.doFinal(l2Ciphertext)

            // Layer 1 Decrypt: ChaCha20-Poly1305
            val ptLenGuess = l1Ciphertext.size - 16
            val aadL1 = "ZT2-ROM-L1-CHACHA|$normOnion|$domainTag|$ptLenGuess".toByteArray(Charsets.UTF_8)
            RustCoreBridge.aeadDecryptChaCha20Poly1305(
                key32 = effChaChaKey,
                nonce12 = nonceL1,
                ciphertextWithTag = l1Ciphertext,
                associatedData = aadL1
            )
        } finally {
            Arrays.fill(packedKeys, 0.toByte())
            Arrays.fill(kChaChaRand, 0.toByte())
            Arrays.fill(kAesRand, 0.toByte())
            Arrays.fill(salt32, 0.toByte())
            Arrays.fill(mix64, 0.toByte())
            Arrays.fill(effChaChaKey, 0.toByte())
            Arrays.fill(effAesKey, 0.toByte())
        }
    }

    fun saveMediaBlob(
        onionAddress: String,
        transferId: String,
        payloadBytes: ByteArray
    ) {
        if (payloadBytes.isEmpty()) return
        synchronized(ioLock) {
            val file = mediaBlobFile(onionAddress, transferId, createDir = true)
            val sealed = sealCascadeEnvelope(payloadBytes, onionAddress, "MEDIA|$transferId")
            file.writeBytes(sealed)
        }
    }

    fun loadMediaBlob(onionAddress: String, transferId: String): ByteArray? {
        synchronized(ioLock) {
            val file = mediaBlobFile(onionAddress, transferId, createDir = false)
            if (!file.exists()) return null
            return try {
                openCascadeEnvelope(file.readBytes(), onionAddress, "MEDIA|$transferId")
            } catch (_: Exception) {
                null
            }
        }
    }

    fun hasMediaBlob(onionAddress: String, transferId: String): Boolean {
        return mediaBlobFile(onionAddress, transferId, createDir = false).exists()
    }

    /**
     * Persists a chat's full message timeline + media blobs to the dual-cipher ROM vault.
     * Any non-empty `decryptedInMemoryBytes` is automatically stored in an individual `.ztb` blob.
     */
    fun saveChatArchive(
        onionAddress: String,
        messages: List<RichConversationMessage>,
        lastPreview: String?,
        lastTimestampMs: Long?
    ) {
        val normOnion = onionAddress.trim().lowercase()
        synchronized(ioLock) {
            val dir = chatDir(normOnion, create = true)
            // Persist any in-RAM media blobs individually first so up to 16 MiB files work smoothly
            messages.forEach { msg ->
                val tr = msg.fileTransfer
                val bytes = tr?.decryptedInMemoryBytes
                if (tr != null && bytes != null && bytes.isNotEmpty()) {
                    val blobFile = mediaBlobFile(normOnion, tr.manifest.transferId, createDir = true)
                    if (!blobFile.exists()) {
                        val sealedBlob = sealCascadeEnvelope(bytes, normOnion, "MEDIA|${tr.manifest.transferId}")
                        blobFile.writeBytes(sealedBlob)
                    }
                }
            }

            val baos = ByteArrayOutputStream()
            DataOutputStream(baos).use { out ->
                out.writeInt(ARCHIVE_MAGIC)
                out.writeUTF(normOnion)
                out.writeUTF(lastPreview ?: "")
                out.writeLong(lastTimestampMs ?: 0L)
                val persisted = messages.filter {
                    it.fileTransfer == null ||
                        it.fileTransfer.state == TransferLifecycleState.VERIFIED_READY_IN_RAM ||
                        it.isDeletedPlaceholder ||
                        hasMediaBlob(normOnion, it.fileTransfer.manifest.transferId)
                }
                out.writeInt(persisted.size)
                for (m in persisted) {
                    out.writeUTF(m.id)
                    out.writeUTF(m.senderLabel)
                    out.writeUTF(m.bodyText)
                    out.writeLong(m.timestampEpochMillis)
                    out.writeBoolean(m.isOutgoing)
                    out.writeInt(m.ratchetStepIndex)
                    out.writeInt(m.pqEpoch)
                    out.writeBoolean(m.pqBraidAdvanced)
                    out.writeUTF(m.payloadKind.name)
                    out.writeUTF(m.replyToMessageId ?: "")
                    out.writeUTF(m.replyToPreview ?: "")
                    out.writeUTF(m.replyToSenderLabel ?: "")
                    out.writeUTF(m.reactionEmoji ?: "")
                    out.writeInt(m.voiceDurationSeconds ?: -1)
                    out.writeUTF(m.deliveryState.name)
                    out.writeUTF(m.wireSessionIdHex ?: "")
                    out.writeLong(m.wireMsgNum ?: -1L)
                    out.writeBoolean(m.isDeletedPlaceholder)

                    val tr = m.fileTransfer
                    if (tr != null) {
                        out.writeBoolean(true)
                        out.writeUTF(tr.manifest.transferId)
                        out.writeUTF(tr.manifest.sanitizedFileName)
                        out.writeUTF(tr.manifest.mimeType)
                        out.writeInt(tr.manifest.totalSizeBytes)
                        out.writeInt(tr.manifest.totalChunks)
                        out.writeUTF(tr.manifest.sha256DigestHex)
                        out.writeUTF(tr.direction.name)
                        out.writeUTF(tr.state.name)
                        out.writeInt(tr.chunksProcessed)
                        out.writeLong(tr.bytesTransferred)
                    } else {
                        out.writeBoolean(false)
                    }
                }
            }
            val rawArchive = baos.toByteArray()
            try {
                val sealedArchive = sealCascadeEnvelope(rawArchive, normOnion, "ARCHIVE")
                File(dir, CHAT_ARCHIVE_FILE).writeBytes(sealedArchive)
            } finally {
                Arrays.fill(rawArchive, 0.toByte())
            }
        }
    }

    fun loadChatArchive(onionAddress: String, hydrateSmallMediaIntoRam: Boolean = true): RomChatArchive? {
        val normOnion = onionAddress.trim().lowercase()
        synchronized(ioLock) {
            val archiveFile = File(chatDir(normOnion, create = false), CHAT_ARCHIVE_FILE)
            if (!archiveFile.exists()) return null
            val raw = try {
                openCascadeEnvelope(archiveFile.readBytes(), normOnion, "ARCHIVE")
            } catch (_: Exception) {
                return null
            }
            return try {
                DataInputStream(ByteArrayInputStream(raw)).use { inp ->
                    if (inp.readInt() != ARCHIVE_MAGIC) return null
                    val storedOnion = inp.readUTF()
                    val preview = inp.readUTF().ifEmpty { null }
                    val ts = inp.readLong().takeIf { it > 0L }
                    val count = inp.readInt().coerceIn(0, 5000)
                    val list = ArrayList<RichConversationMessage>(count)
                    for (i in 0 until count) {
                        val id = inp.readUTF()
                        val sender = inp.readUTF()
                        val body = inp.readUTF()
                        val msgTs = inp.readLong()
                        val isOut = inp.readBoolean()
                        val step = inp.readInt()
                        val epoch = inp.readInt()
                        val braid = inp.readBoolean()
                        val kind = try {
                            MessagePayloadKind.valueOf(inp.readUTF())
                        } catch (_: Exception) {
                            MessagePayloadKind.TEXT
                        }
                        val rId = inp.readUTF().ifEmpty { null }
                        val rPrev = inp.readUTF().ifEmpty { null }
                        val rSender = inp.readUTF().ifEmpty { null }
                        val react = inp.readUTF().ifEmpty { null }
                        val voiceDur = inp.readInt().takeIf { it >= 0 }
                        val deliv = try {
                            MessageDeliveryState.valueOf(inp.readUTF())
                        } catch (_: Exception) {
                            MessageDeliveryState.SENT
                        }
                        val sessHex = inp.readUTF().ifEmpty { null }
                        val msgNum = inp.readLong().takeIf { it >= 0L }
                        val isDeleted = inp.readBoolean()

                        val hasTransfer = inp.readBoolean()
                        val transfer = if (hasTransfer) {
                            val txId = inp.readUTF()
                            val fileName = inp.readUTF()
                            val mime = inp.readUTF()
                            val totalBytes = inp.readInt()
                            val totalChunks = inp.readInt()
                            val sha = inp.readUTF()
                            val dir = try {
                                TransferDirection.valueOf(inp.readUTF())
                            } catch (_: Exception) {
                                TransferDirection.OUTGOING
                            }
                            val st = try {
                                TransferLifecycleState.valueOf(inp.readUTF())
                            } catch (_: Exception) {
                                TransferLifecycleState.VERIFIED_READY_IN_RAM
                            }
                            val chunksDone = inp.readInt()
                            val bytesDone = inp.readLong()
                            val inRom = hasMediaBlob(normOnion, txId)
                            val hydratedBytes = if (inRom && hydrateSmallMediaIntoRam && totalBytes <= 1024 * 1024) {
                                loadMediaBlob(normOnion, txId)
                            } else null
                            EncryptedTransferProgress(
                                manifest = EncryptedFileManifest(
                                    transferId = txId,
                                    sanitizedFileName = fileName,
                                    mimeType = mime,
                                    totalSizeBytes = totalBytes,
                                    totalChunks = totalChunks,
                                    sha256DigestHex = sha
                                ),
                                direction = dir,
                                state = st,
                                chunksProcessed = chunksDone,
                                bytesTransferred = bytesDone,
                                decryptedInMemoryBytes = hydratedBytes,
                                isStoredInRom = inRom
                            )
                        } else null

                        list.add(
                            RichConversationMessage(
                                id = id,
                                senderLabel = sender,
                                bodyText = body,
                                timestampEpochMillis = msgTs,
                                isOutgoing = isOut,
                                ratchetStepIndex = step,
                                pqEpoch = epoch,
                                pqBraidAdvanced = braid,
                                payloadKind = kind,
                                replyToMessageId = rId,
                                replyToPreview = rPrev,
                                replyToSenderLabel = rSender,
                                reactionEmoji = react,
                                fileTransfer = transfer,
                                voiceDurationSeconds = voiceDur,
                                deliveryState = if (deliv == MessageDeliveryState.SENDING) MessageDeliveryState.SENT else deliv,
                                wireSessionIdHex = sessHex,
                                wireMsgNum = msgNum,
                                isDeletedPlaceholder = isDeleted
                            )
                        )
                    }
                    RomChatArchive(
                        onionAddress = storedOnion,
                        messages = list,
                        lastPreview = preview,
                        lastTimestampMs = ts
                    )
                }
            } catch (_: Exception) {
                null
            } finally {
                Arrays.fill(raw, 0.toByte())
            }
        }
    }

    fun inspectChatRomStats(onionAddress: String): ChatRomDiskStats {
        val normOnion = onionAddress.trim().lowercase()
        synchronized(ioLock) {
            val dir = chatDir(normOnion, create = false)
            if (!dir.exists()) return ChatRomDiskStats(normOnion)
            val files = dir.listFiles().orEmpty()
            var totalBytes = 0L
            var blobCount = 0
            files.forEach { f ->
                totalBytes += f.length()
                if (f.name.endsWith(".ztb")) blobCount++
            }
            val msgCount = loadChatArchive(normOnion, hydrateSmallMediaIntoRam = false)?.messages?.size ?: 0
            return ChatRomDiskStats(
                onionAddress = normOnion,
                messageCount = msgCount,
                mediaBlobCount = blobCount,
                totalEncryptedBytes = totalBytes
            )
        }
    }

    fun getTotalRomDiskBytes(): Long {
        synchronized(ioLock) {
            val root = File(context.noBackupFilesDir, VAULT_ROOT_DIR)
            var total = 0L
            if (root.exists()) {
                root.walkTopDown().filter { it.isFile }.forEach { total += it.length() }
            }
            return total + keystoreManager.getGlobalChatVaultDiskBytes()
        }
    }

    fun deleteChatFromRom(onionAddress: String) {
        val normOnion = onionAddress.trim().lowercase()
        synchronized(ioLock) {
            val dir = chatDir(normOnion, create = false)
            shredDirectory(dir)
        }
    }

    fun wipeAllChatRomVaults() {
        synchronized(ioLock) {
            val root = File(context.noBackupFilesDir, VAULT_ROOT_DIR)
            shredDirectory(root)
            cachedMasterEntropy64?.let { Arrays.fill(it, 0.toByte()) }
            cachedMasterEntropy64 = null
        }
    }

    private fun shredDirectory(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                shredDirectory(child)
            } else {
                try {
                    val len = child.length().toInt().coerceAtMost(2 * 1024 * 1024)
                    if (len > 0) child.writeBytes(ByteArray(len))
                } catch (_: Exception) {
                }
                child.delete()
            }
        }
        dir.delete()
    }
}
