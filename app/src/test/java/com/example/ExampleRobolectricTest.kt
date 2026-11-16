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

    @Test
    fun testChatRomVaultDoubleCipherCascadeAndFineChunking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keystoreManager = KeystoreManager(context)
        val romVault = com.zerotrace.security.ChatRomVault(context, keystoreManager)
        val peerOnion = "gsvas7k5tmpcrs4p6llfveyojgdta5vivip4wcpenvoboldqhpzh35qd.onion"

        // 1. Verify 100 KB video uses fine 2 KiB chunks -> 50 chunks (~2% smooth steps)
        val videoBytes = ByteArray(100 * 1024) { idx -> (idx * 31).toByte() }
        val encSteps = mutableListOf<Int>()
        val bundle = com.zerotrace.bridge.EncryptedFileTransferEngine.encryptAndChunkPayload(
            rawFileName = "clip_100kb.mp4",
            mimeType = "video/mp4",
            payloadBytes = videoBytes,
            stripImageMetadata = false,
            onChunkEncryptProgress = { done, _ -> encSteps.add(done) }
        )
        assertEquals(50, bundle.manifest.totalChunks)
        assertEquals(50, encSteps.size)
        val decryptedVideo = com.zerotrace.bridge.EncryptedFileTransferEngine.decryptAndVerifyChunks(
            manifest = bundle.manifest,
            fileSecretKey32 = bundle.fileSecretKey32,
            chunks = bundle.chunks
        )
        assertArrayEquals(videoBytes, decryptedVideo)

        // 2. Verify Double-Cipher Cascade ROM Vault encryption, tamper detection & archive round-trip
        val sealed = romVault.sealCascadeEnvelope(videoBytes, peerOnion, "TEST-MEDIA")
        val opened = romVault.openCascadeEnvelope(sealed, peerOnion, "TEST-MEDIA")
        assertArrayEquals(videoBytes, opened)

        // Flip 1 byte in outer ciphertext -> must fail authentication
        val tampered = sealed.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0xFF).toByte()
        try {
            romVault.openCascadeEnvelope(tampered, peerOnion, "TEST-MEDIA")
            fail("Expected tampered double-cipher ROM envelope to fail authentication")
        } catch (_: Exception) {
            // Expected AEAD failure
        }

        // Wrong onion AAD -> must fail
        try {
            romVault.openCascadeEnvelope(sealed, "otherpeer.onion", "TEST-MEDIA")
            fail("Expected wrong peer onion AAD to fail")
        } catch (_: Exception) {
            // Expected AAD failure
        }

        val mediaMsg = com.zerotrace.bridge.RichConversationMessage(
            id = "msg-vid-1",
            senderLabel = "You",
            bodyText = "100 KB encrypted video",
            timestampEpochMillis = 1_700_000_000_000L,
            isOutgoing = true,
            ratchetStepIndex = 1,
            pqEpoch = 1,
            pqBraidAdvanced = false,
            payloadKind = com.zerotrace.bridge.MessagePayloadKind.VIDEO,
            fileTransfer = com.zerotrace.bridge.EncryptedTransferProgress(
                manifest = bundle.manifest,
                direction = com.zerotrace.bridge.TransferDirection.OUTGOING,
                state = com.zerotrace.bridge.TransferLifecycleState.VERIFIED_READY_IN_RAM,
                chunksProcessed = bundle.manifest.totalChunks,
                bytesTransferred = videoBytes.size.toLong(),
                decryptedInMemoryBytes = decryptedVideo,
                isStoredInRom = true
            )
        )
        romVault.saveChatArchive(peerOnion, listOf(mediaMsg), "🎥 clip_100kb.mp4", 1_700_000_000_000L)
        val stats = romVault.inspectChatRomStats(peerOnion)
        assertEquals(1, stats.messageCount)
        assertEquals(1, stats.mediaBlobCount)
        assertTrue(stats.totalEncryptedBytes > 100 * 1024)

        val loadedArchive = romVault.loadChatArchive(peerOnion, hydrateSmallMediaIntoRam = false)
        assertNotNull(loadedArchive)
        assertEquals(1, loadedArchive!!.messages.size)
        assertNull(loadedArchive.messages[0].fileTransfer?.decryptedInMemoryBytes)
        assertTrue(loadedArchive.messages[0].fileTransfer?.isStoredInRom == true)
        val onDemandBlob = romVault.loadMediaBlob(peerOnion, bundle.manifest.transferId)
        assertArrayEquals(videoBytes, onDemandBlob)

        // keepDataOnions persists in prefs snapshot
        val prefs = com.zerotrace.security.PersistedPrefsSnapshot(keepDataOnions = setOf(peerOnion))
        val decodedPrefs = com.zerotrace.security.EncryptedVaultCodec.decodePrefsAndProfile(
            com.zerotrace.security.EncryptedVaultCodec.encodePrefsAndProfile(prefs)
        )
        assertEquals(setOf(peerOnion), decodedPrefs?.keepDataOnions)

        romVault.wipeAllChatRomVaults()
        assertEquals(0, romVault.inspectChatRomStats(peerOnion).mediaBlobCount)
        bundle.wipeZeroize()
    }
}
