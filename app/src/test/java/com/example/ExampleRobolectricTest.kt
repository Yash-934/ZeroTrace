package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zerotrace.bridge.RustCoreBridge
import com.zerotrace.security.BackupPolicy
import com.zerotrace.security.KeystoreManager
import com.zerotrace.ui.pairing.QrCodeEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ZeroTrace Messenger 2.0 — Android Device Security & Robolectric Test Suite (Section 9).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun testAppNameAndStrictBackupExclusions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("ZeroTrace", context.getString(R.string.app_name))

        val backupAudit = BackupPolicy.auditBackupConfiguration(context)
        assertTrue("android:allowBackup must be false", backupAudit.allowBackupFlagDisabled)
        assertTrue("noBackupFilesDir must exist", backupAudit.usesNoBackupDirectory)
        assertTrue(backupAudit.isCompliant)
    }

    @Test
    fun testKeystoreWrappingTamperResistanceAndPermanentDestruction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keystoreManager = KeystoreManager(context)

        val (summary, rawIdentityBytes) = RustCoreBridge.generateNewDeviceIdentity()
        keystoreManager.saveWrappedIdentity(rawIdentityBytes)
        assertTrue(keystoreManager.hasStoredIdentity())

        val loadedBytes = keystoreManager.loadWrappedIdentity()
        assertNotNull(loadedBytes)
        assertArrayEquals(rawIdentityBytes, loadedBytes)

        val restoredSummary = RustCoreBridge.restoreWrappedIdentity(loadedBytes!!)
        assertEquals(summary.onionAddress, restoredSummary.onionAddress)
        assertEquals(summary.classicalFingerprintHex, restoredSummary.classicalFingerprintHex)
        assertEquals(summary.pqFingerprintHex, restoredSummary.pqFingerprintHex)

        // Verify AAD binding & tamper resistance on wrapped envelope
        val aad = "ZT2-TEST-AAD".toByteArray(Charsets.UTF_8)
        val wrapped = keystoreManager.wrapBytes("SecretKeyMaterial".toByteArray(), aad)
        val tampered = wrapped.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0xFF).toByte()
        try {
            keystoreManager.unwrapBytes(tampered, aad)
            fail("Expected tampered AES-256-GCM envelope to fail authentication")
        } catch (_: Exception) {
            // Expected AEADBadTagException
        }

        // Permanent identity deletion removes files and resets keystore
        keystoreManager.deleteIdentityAndWrappingKey()
        assertFalse(keystoreManager.hasStoredIdentity())
        assertNull(keystoreManager.loadWrappedIdentity())
    }

    @Test
    fun testQrBitmapGeneration() {
        RustCoreBridge.generateNewDeviceIdentity()
        val (qrPayload, _) = RustCoreBridge.generateSignedPairingQr()
        val bitmap = QrCodeEngine.encodeQrBitmap(qrPayload, 320)
        assertEquals(320, bitmap.width)
        assertEquals(320, bitmap.height)
    }

    @Test
    fun testKeystoreWrappedPrefsProfileAndForeverChatVault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keystoreManager = KeystoreManager(context)
        val prefsBytes = "ZT2-PREFS-TEST-PAYLOAD".toByteArray(Charsets.UTF_8)
        val vaultBytes = "ZT2-VAULT-TEST-PAYLOAD".toByteArray(Charsets.UTF_8)

        keystoreManager.saveWrappedPrefsAndProfile(prefsBytes)
        keystoreManager.saveWrappedChatVault(vaultBytes)

        assertArrayEquals(prefsBytes, keystoreManager.loadWrappedPrefsAndProfile())
        assertArrayEquals(vaultBytes, keystoreManager.loadWrappedChatVault())

        // Wiping chat vault preserves prefs/profile while removing the chat vault envelope
        keystoreManager.wipeWrappedChatVault()
        assertNull(keystoreManager.loadWrappedChatVault())
        assertArrayEquals(prefsBytes, keystoreManager.loadWrappedPrefsAndProfile())

        keystoreManager.deleteIdentityAndWrappingKey()
        assertNull(keystoreManager.loadWrappedPrefsAndProfile())
    }

    @Test
    fun testFragmentActivitySupports32BitActivityResultRegistryRequestCodes() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val launcher = activity.activityResultRegistry.register(
            "test_pick_visual_media",
            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { _ -> }
        // In fragment:1.2.5 this threw IllegalArgumentException: Can only use lower 16 bits for requestCode
        launcher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
        launcher.unregister()
    }

    @Test
    fun testCompressAvatarUriToCompactJpegAndPrefsRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempFile = java.io.File(context.cacheDir, "test_avatar_source.png")
        try {
            val sourceBitmap = android.graphics.Bitmap.createBitmap(
                240,
                160,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            sourceBitmap.eraseColor(android.graphics.Color.rgb(37, 211, 102))
            java.io.FileOutputStream(tempFile).use { out ->
                assertTrue(sourceBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out))
            }
            sourceBitmap.recycle()

            val compressedJpeg = com.zerotrace.security.EncryptedVaultCodec
                .compressAvatarUriToCompactJpeg(context, android.net.Uri.fromFile(tempFile))
            assertTrue("Compressed avatar JPEG must be non-empty", compressedJpeg.isNotEmpty())
            assertTrue("Compressed avatar JPEG must fit within 24 KiB", compressedJpeg.size <= 24 * 1024)

            val snapshot = com.zerotrace.security.PersistedPrefsSnapshot(
                profile = com.zerotrace.bridge.LocalUserProfile(
                    displayName = "Rahul Sharma",
                    bio = "ZeroTrace active",
                    avatarJpegBytes = compressedJpeg,
                    updatedAtEpochSec = 1_700_000_000L
                )
            )
            val encoded = com.zerotrace.security.EncryptedVaultCodec.encodePrefsAndProfile(snapshot)
            val decoded = com.zerotrace.security.EncryptedVaultCodec.decodePrefsAndProfile(encoded)
            assertNotNull(decoded)
            assertEquals("Rahul Sharma", decoded!!.profile.displayName)
            assertEquals("ZeroTrace active", decoded.profile.bio)
            assertArrayEquals(compressedJpeg, decoded.profile.avatarJpegBytes)
        } finally {
            tempFile.delete()
        }
    }
}
