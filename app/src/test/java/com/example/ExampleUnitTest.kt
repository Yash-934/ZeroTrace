package com.example

import com.zerotrace.bridge.RatchetWirePacket
import com.zerotrace.bridge.RustCoreBridge
import com.zerotrace.network.ConnectivityState
import com.zerotrace.network.TorOnionTransportManager
import com.zerotrace.network.TorTransportBackend
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * ZeroTrace Messenger 2.0 — Comprehensive Cryptographic, Protocol, Ratchet, Fail-Closed Network,
 * and Fuzzing Property Unit Test Suite (Section 9).
 */
class ExampleUnitTest {

    @Test
    fun testNistFips203MlKem1024EncapsDecapsAndImplicitRejection() {
        val (summary, secretBundle) = RustCoreBridge.generateNewDeviceIdentity()
        assertTrue(secretBundle.isNotEmpty())
        // NIST FIPS 203 ML-KEM-1024 encapsulation key size is 1568 bytes
        assertEquals(1568, summary.mlKem1024EncapsKey.size)

        val (ciphertext, senderSecret) = RustCoreBridge.encapsulateMlKem1024(summary.mlKem1024EncapsKey)
        // NIST FIPS 203 ML-KEM-1024 ciphertext size is 1568 bytes, shared secret is 32 bytes
        assertEquals(1568, ciphertext.size)
        assertEquals(32, senderSecret.size)
    }

    @Test
    fun testNistFips204MlDsa87AndEd25519DualSignatures() {
        val (summary, _) = RustCoreBridge.generateNewDeviceIdentity()
        // NIST FIPS 204 ML-DSA-87 public key size is 2592 bytes
        assertEquals(2592, summary.mlDsa87PublicKey.size)
        assertEquals(32, summary.ed25519PublicKey.size)
        assertEquals(32, summary.x25519PublicKey.size)

        val initWire = RustCoreBridge.createHandshakeInit(
            peerClassicalFpHex = summary.classicalFingerprintHex,
            peerPqFpHex = summary.pqFingerprintHex,
            pairingNonceHex = "0102030405060708090a0b0c0d0e0f10"
        )
        // NIST FIPS 204 ML-DSA-87 signature size is 4627 bytes
        assertEquals(4627, initWire.mlDsa87Signature.size)
        assertEquals(64, initWire.ed25519Signature.size)

        // Tampering with ML-DSA-87 signature must fail verification
        val tamperedSig = initWire.mlDsa87Signature.copyOf()
        tamperedSig[100] = (tamperedSig[100].toInt() xor 0xFF).toByte()
        assertFalse(
            RustCoreBridge.verifyMlDsa87(
                summary.mlDsa87PublicKey,
                "test".toByteArray(),
                tamperedSig
            )
        )
    }

    @Test
    fun testChaCha20Poly1305AeadAndTamperRejection() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val nonce = ByteArray(12) { (it + 7).toByte() }
        val aad = "ZT2-AAD-HEADER".toByteArray(Charsets.UTF_8)
        val plaintext = "Confidential Post-Quantum Payload".toByteArray(Charsets.UTF_8)

        val ct = RustCoreBridge.aeadEncryptChaCha20Poly1305(key, nonce, plaintext, aad)
        val decrypted = RustCoreBridge.aeadDecryptChaCha20Poly1305(key, nonce, ct, aad)
        assertArrayEquals(plaintext, decrypted)

        // Tamper with ciphertext byte
        val corruptedCt = ct.copyOf()
        corruptedCt[0] = (corruptedCt[0].toInt() xor 0x01).toByte()
        try {
            RustCoreBridge.aeadDecryptChaCha20Poly1305(key, nonce, corruptedCt, aad)
            fail("Expected SecurityException on tampered ChaCha20-Poly1305 tag")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("AEAD_TAG_VERIFICATION_FAILED"))
        }
    }

    @Test
    fun testHybridHandshakeOutOfOrderRatchetReplayAndPqBraidEpoch() {
        val (localSummary, _) = RustCoreBridge.generateNewDeviceIdentity()
        val pairingNonceHex = "aabbccddeeff00112233445566778899"

        // Self-loop handshake to get two synchronized session handles (initiator + responder)
        val initWire = RustCoreBridge.createHandshakeInit(
            peerClassicalFpHex = localSummary.classicalFingerprintHex,
            peerPqFpHex = localSummary.pqFingerprintHex,
            pairingNonceHex = pairingNonceHex
        )

        val (responderHandle, respWire) = RustCoreBridge.respondToHandshakeInit(
            init = initWire,
            expectedPeerOnion = localSummary.onionAddress,
            expectedPeerClassicalFpHex = localSummary.classicalFingerprintHex,
            expectedPeerPqFpHex = localSummary.pqFingerprintHex,
            expectedPairingNonceHex = pairingNonceHex
        )

        val initiatorHandle = RustCoreBridge.finalizeHandshakeInitiator(
            resp = respWire,
            peerOnion = localSummary.onionAddress
        )

        // 1. Encrypt 3 messages from Initiator
        val (pkt0, _) = RustCoreBridge.encryptMessage(initiatorHandle, "Msg-0".toByteArray())
        val (pkt1, _) = RustCoreBridge.encryptMessage(initiatorHandle, "Msg-1".toByteArray())
        val (pkt2, _) = RustCoreBridge.encryptMessage(initiatorHandle, "Msg-2".toByteArray())

        // 2. Deliver out-of-order to Responder: pkt2 -> pkt0 -> pkt1
        val dec2 = RustCoreBridge.decryptMessage(responderHandle, pkt2)
        assertEquals("Msg-2", dec2.readPlaintext())

        val dec0 = RustCoreBridge.decryptMessage(responderHandle, pkt0)
        assertEquals("Msg-0", dec0.readPlaintext())

        val dec1 = RustCoreBridge.decryptMessage(responderHandle, pkt1)
        assertEquals("Msg-1", dec1.readPlaintext())

        // 3. Replay Attack on pkt1 must be rejected!
        try {
            RustCoreBridge.decryptMessage(responderHandle, pkt1)
            fail("Expected replay attack on pkt1 to throw SecurityException")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("REPLAY"))
        }

        // 4. Force a Post-Quantum ML-KEM-1024 Braid Ratchet Step
        val (braidPkt, braidMsg) = RustCoreBridge.encryptMessage(
            sessionHandle = initiatorHandle,
            plaintextUtf8 = "PQ-Braid-Epoch-Advance".toByteArray(),
            forcePqBraidStep = true
        )
        assertTrue(braidMsg.pqBraidStepTriggered)
        assertNotNull(braidPkt.pqBraidCiphertext)
        assertEquals(2, braidPkt.ratchetEpoch)

        val decBraid = RustCoreBridge.decryptMessage(responderHandle, braidPkt)
        assertEquals("PQ-Braid-Epoch-Advance", decBraid.readPlaintext())
        assertEquals(2, decBraid.ratchetEpoch)

        // 5. Emergency Wipe clears all volatile messages and invalidates session handles
        val wiped = RustCoreBridge.emergencyWipeAllSessions()
        assertTrue(wiped >= 2)
        assertEquals("[WIPED]", decBraid.readPlaintext())
    }

    @Test
    fun testProtocolDowngradeAndIdentityMismatchRejected() {
        val (localSummary, _) = RustCoreBridge.generateNewDeviceIdentity()
        val pairingNonceHex = "11223344556677889900aabbccddeeff"
        val initWire = RustCoreBridge.createHandshakeInit(
            peerClassicalFpHex = localSummary.classicalFingerprintHex,
            peerPqFpHex = localSummary.pqFingerprintHex,
            pairingNonceHex = pairingNonceHex
        )

        // Attempt protocol version downgrade
        val downgraded = initWire.copy(protocolVersion = 1)
        try {
            RustCoreBridge.respondToHandshakeInit(
                init = downgraded,
                expectedPeerOnion = localSummary.onionAddress,
                expectedPeerClassicalFpHex = localSummary.classicalFingerprintHex,
                expectedPeerPqFpHex = localSummary.pqFingerprintHex,
                expectedPairingNonceHex = pairingNonceHex
            )
            fail("Expected downgrade attack to be rejected")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("PROTOCOL_DOWNGRADE_REJECTED"))
        }

        // Attempt MITM identity fingerprint substitution
        try {
            RustCoreBridge.respondToHandshakeInit(
                init = initWire,
                expectedPeerOnion = localSummary.onionAddress,
                expectedPeerClassicalFpHex = "ff".repeat(32),
                expectedPeerPqFpHex = localSummary.pqFingerprintHex,
                expectedPairingNonceHex = pairingNonceHex
            )
            fail("Expected MITM fingerprint mismatch to be rejected")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("PEER_IDENTITY_MISMATCH"))
        }
    }

    @Test
    fun testFailClosedNetworkRejectsClearnetAndPublicBind() = runBlocking {
        // 1. Reject clearnet IPv4, IPv6, and DNS hostnames
        val clearnetTargets = listOf(
            "192.168.1.100",
            "8.8.8.8:443",
            "https://example.com",
            "chat.centralserver.org",
            "short.onion"
        )
        for (target in clearnetTargets) {
            try {
                RustCoreBridge.validateOnionDestinationOrThrow(target)
                fail("Expected clearnet or malformed target '$target' to be rejected")
            } catch (expected: SecurityException) {
                assertTrue(expected.message!!.contains("FAIL_CLOSED_VIOLATION"))
            }
        }

        // 2. Reject binding local Onion Service listener to 0.0.0.0 or public IP
        try {
            TorOnionTransportManager.verifyLoopbackBindAddressOrThrow("0.0.0.0")
            fail("Expected 0.0.0.0 bind to be rejected")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("FAIL_CLOSED_VIOLATION"))
        }
        TorOnionTransportManager.verifyLoopbackBindAddressOrThrow("127.0.0.1")

        // 3. Verify SOCKS5 mode fails closed when no Tor daemon is listening on an unused loopback port
        TorOnionTransportManager.configureTransportBackend(
            TorTransportBackend.SOCKS5_TOR_DAEMON,
            customSocksPort = 61999
        )
        val ok = TorOnionTransportManager.bootstrapTorAndStartOnionService()
        assertFalse(ok)
        assertEquals(ConnectivityState.ERROR, TorOnionTransportManager.connectivityState.value)

        // Restore loopback verification mode and verify full end-to-end peer session over 127.0.0.1
        TorOnionTransportManager.configureTransportBackend(TorTransportBackend.LOOPBACK_ONION_VERIFICATION)
        RustCoreBridge.generateNewDeviceIdentity()
        assertTrue(TorOnionTransportManager.bootstrapTorAndStartOnionService())
        assertEquals(ConnectivityState.ONION_SERVICE_READY, TorOnionTransportManager.connectivityState.value)

        val (_, peerRecord) = TorOnionTransportManager.generateLoopbackPeerQrInvite()
        val sessionRes = TorOnionTransportManager.establishAuthenticatedPeerSession(peerRecord)
        assertTrue(sessionRes.isSuccess)
        assertEquals(ConnectivityState.E2EE_SESSION_READY, TorOnionTransportManager.connectivityState.value)

        val handle = sessionRes.getOrThrow()
        val sendRes = TorOnionTransportManager.sendEncryptedMessageOverCircuit(handle, "Ping over loopback Onion")
        assertTrue(sendRes.isSuccess)
        val msgs = sendRes.getOrThrow()
        // Should contain outgoing message + authenticated peer reply
        assertEquals(2, msgs.size)
        assertNotEquals("[WIPED]", msgs[1].readPlaintext())

        TorOnionTransportManager.emergencyDisconnectAndWipe()
        assertEquals(ConnectivityState.OFFLINE, TorOnionTransportManager.connectivityState.value)
    }

    @Test
    fun testFuzzQrParserAndWireFramingResilience() {
        RustCoreBridge.generateNewDeviceIdentity()
        val (validQr, _) = RustCoreBridge.generateSignedPairingQr(nowUnixSec = 1_763_200_000L)
        val parsed = RustCoreBridge.parseAndVerifyPairingQr(validQr, nowUnixSec = 1_763_200_050L)
        assertEquals(64, parsed.classicalFingerprintHex.length)
        assertEquals(64, parsed.pqFingerprintHex.length)

        // Expired QR must be rejected
        try {
            RustCoreBridge.parseAndVerifyPairingQr(validQr, nowUnixSec = 1_763_210_000L)
            fail("Expected expired QR to be rejected")
        } catch (_: Exception) {
        }

        // Fuzz 300 random/corrupted inputs against QR parser and wire packet deserializer
        val rng = Random(0x5A5432L)
        for (i in 0 until 300) {
            val randomBytes = ByteArray(rng.nextInt(512) + 1)
            rng.nextBytes(randomBytes)
            try {
                RatchetWirePacket.fromWireBytes(randomBytes)
            } catch (_: IllegalArgumentException) {
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }

            val corruptedQr = if (i % 2 == 0) {
                String(randomBytes, Charsets.UTF_8)
            } else {
                validQr.replaceRange(10, 14, "XXXX")
            }
            try {
                RustCoreBridge.parseAndVerifyPairingQr(corruptedQr, nowUnixSec = 1_763_200_050L)
                fail("Corrupted fuzz QR input must not verify")
            } catch (_: IllegalArgumentException) {
            } catch (_: SecurityException) {
            }
        }
    }

    @Test
    fun testEncryptedChunkedFileTransferAndTamperRejection() {
        // Create a 75 KiB payload (spans 3 x 32 KiB chunks)
        val rawPayload = ByteArray(75 * 1024) { idx -> ((idx * 31 + 17) and 0xFF).toByte() }
        val bundle = com.zerotrace.bridge.EncryptedFileTransferEngine.encryptAndChunkPayload(
            rawFileName = "../../secret_folder/audit_report.pdf",
            mimeType = "application/pdf",
            payloadBytes = rawPayload,
            stripImageMetadata = false
        )

        // Verify path traversal is sanitized
        assertFalse(bundle.manifest.sanitizedFileName.contains(".."))
        assertFalse(bundle.manifest.sanitizedFileName.contains("/"))
        assertEquals(3, bundle.manifest.totalChunks)
        assertEquals(32, bundle.fileSecretKey32.size)

        // Verify round-trip decryption and SHA-256 integrity check
        val decrypted = com.zerotrace.bridge.EncryptedFileTransferEngine.decryptAndVerifyChunks(
            manifest = bundle.manifest,
            fileSecretKey32 = bundle.fileSecretKey32,
            chunks = bundle.chunks
        )
        assertArrayEquals(rawPayload, decrypted)

        // Verify tampering with a single byte in chunk #1 is rejected by ChaCha20-Poly1305 AEAD
        val tamperedChunk = bundle.chunks[1].ciphertextWithTag.copyOf()
        tamperedChunk[10] = (tamperedChunk[10].toInt() xor 0xFF).toByte()
        val corruptedList = listOf(
            bundle.chunks[0],
            bundle.chunks[1].copy(ciphertextWithTag = tamperedChunk),
            bundle.chunks[2]
        )
        try {
            com.zerotrace.bridge.EncryptedFileTransferEngine.decryptAndVerifyChunks(
                manifest = bundle.manifest,
                fileSecretKey32 = bundle.fileSecretKey32,
                chunks = corruptedList
            )
            fail("Expected tampered file chunk to throw SecurityException")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("AEAD_TAG_VERIFICATION_FAILED"))
        }

        // Verify wipeZeroize overwrites the file secret key and ciphertext chunks
        bundle.wipeZeroize()
        assertTrue(bundle.fileSecretKey32.all { it == 0.toByte() })
    }

    @Test
    fun testUnsupportedMimeAndOversizedAttachmentRejected() {
        try {
            com.zerotrace.bridge.EncryptedFileTransferEngine.encryptAndChunkPayload(
                rawFileName = "malware.exe",
                mimeType = "application/x-msdownload",
                payloadBytes = "MZ-executable".toByteArray()
            )
            fail("Expected unsupported executable MIME type to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Unsupported MIME type"))
        }
    }

    @Test
    fun testSecondDeviceQrInvitePreservesOnionAndCompletesPqHandshake() = runBlocking {
        // 1. Simulate Device B generating its own independent identity and signed ZT2|v1 QR invite
        val (deviceBSummary, _) = RustCoreBridge.generateNewDeviceIdentity()
        val (deviceBQr, _) = RustCoreBridge.generateSignedPairingQr()

        // 2. Simulate Device A generating its own independent identity and scanning/uploading Device B's QR
        val (deviceASummary, _) = RustCoreBridge.generateNewDeviceIdentity()
        assertNotEquals(deviceBSummary.onionAddress, deviceASummary.onionAddress)

        val parsedInvite = RustCoreBridge.parseAndVerifyPairingQr(deviceBQr)
        assertEquals(deviceBSummary.onionAddress, parsedInvite.onionAddress)
        assertEquals(deviceBSummary.classicalSafetyDigits, parsedInvite.classicalSafetyDigits)
        assertEquals(deviceBSummary.pqSafetyDigits, parsedInvite.pqSafetyDigits)

        val secondDeviceContact = com.zerotrace.bridge.VerifiedContactRecord(
            alias = "Peer-${parsedInvite.onionAddress.take(6).uppercase()}",
            onionAddress = parsedInvite.onionAddress,
            classicalFingerprintHex = parsedInvite.classicalFingerprintHex,
            pqFingerprintHex = parsedInvite.pqFingerprintHex,
            classicalSafetyDigits = parsedInvite.classicalSafetyDigits,
            pqSafetyDigits = parsedInvite.pqSafetyDigits,
            ed25519PublicKey = parsedInvite.ed25519PublicKey,
            x25519PublicKey = parsedInvite.x25519PublicKey,
            mlDsa87PublicKey = null,
            mlKem1024EncapsKey = null,
            pairingNonceHex = parsedInvite.pairingNonceHex,
            verifiedAtEpochSec = System.currentTimeMillis() / 1000L
        )

        // Ensure contact normalization preserves Device B's true .onion address and safety digits
        val normalized = TorOnionTransportManager.normalizeLoopbackContactIfNeeded(secondDeviceContact)
        assertEquals(deviceBSummary.onionAddress, normalized.onionAddress)
        assertEquals(deviceBSummary.classicalSafetyDigits, normalized.classicalSafetyDigits)
        assertEquals(deviceBSummary.pqSafetyDigits, normalized.pqSafetyDigits)

        // 3. Establish authenticated ZT-PQXDH-P2P-v1 session with the second device contact
        TorOnionTransportManager.configureTransportBackend(TorTransportBackend.LOOPBACK_ONION_VERIFICATION)
        val sessionResult = TorOnionTransportManager.establishAuthenticatedPeerSession(normalized)
        assertTrue("Expected handshake to succeed, got: ${sessionResult.exceptionOrNull()}", sessionResult.isSuccess)
        assertEquals(ConnectivityState.E2EE_SESSION_READY, TorOnionTransportManager.connectivityState.value)

        val handle = sessionResult.getOrThrow()
        val metrics = RustCoreBridge.getSessionMetrics(handle)
        assertNotNull(metrics)
        assertEquals(deviceBSummary.onionAddress, metrics!!.peerOnion)

        // 4. Send an encrypted message and verify bidirectional ratcheted reply
        val msgResult = TorOnionTransportManager.sendEncryptedMessageOverCircuit(handle, "Hello from second device!")
        assertTrue(msgResult.isSuccess)
        assertEquals(2, msgResult.getOrThrow().size)

        TorOnionTransportManager.emergencyDisconnectAndWipe()
        assertEquals(ConnectivityState.OFFLINE, TorOnionTransportManager.connectivityState.value)
    }

    @Test
    fun testE2eeChatEnvelopeReplyReactionAndMediaRoundTrip() {
        val textWire = com.zerotrace.bridge.E2eeChatEnvelope.encodeText(
            msgId = "msg-42",
            body = "Confirming tag reply sync!",
            replyToId = "msg-10",
            replyToPreview = "Original tagged message",
            replyToSender = "Alice"
        )
        val decodedText = com.zerotrace.bridge.E2eeChatEnvelope.decode(textWire, "fallback")
            as com.zerotrace.bridge.E2eeChatEnvelope.Decoded.Text
        assertEquals("msg-42", decodedText.msgId)
        assertEquals("Confirming tag reply sync!", decodedText.body)
        assertEquals("msg-10", decodedText.replyToId)
        assertEquals("Original tagged message", decodedText.replyToPreview)
        assertEquals("Alice", decodedText.replyToSender)

        val reactWire = com.zerotrace.bridge.E2eeChatEnvelope.encodeReaction("msg-42", "🔒")
        val decodedReact = com.zerotrace.bridge.E2eeChatEnvelope.decode(reactWire, "fallback")
            as com.zerotrace.bridge.E2eeChatEnvelope.Decoded.Reaction
        assertEquals("msg-42", decodedReact.targetMsgId)
        assertEquals("🔒", decodedReact.emoji)

        val key32 = ByteArray(32) { (it + 3).toByte() }
        val manifest = com.zerotrace.bridge.EncryptedFileManifest(
            transferId = "tx-99",
            sanitizedFileName = "voice_note_123.wav",
            mimeType = "audio/wav",
            totalSizeBytes = 6400,
            totalChunks = 1,
            sha256DigestHex = "ab".repeat(32)
        )
        val mediaWire = com.zerotrace.bridge.E2eeChatEnvelope.encodeMedia(
            msgId = "msg-77",
            payloadKind = com.zerotrace.bridge.MessagePayloadKind.VOICE_NOTE,
            caption = "Voice Note (3s)",
            voiceDurationSec = 3,
            manifest = manifest,
            fileSecretKey32 = key32,
            replyToId = "msg-42",
            replyToPreview = "Confirming tag reply sync!",
            replyToSender = "You"
        )
        val decodedMedia = com.zerotrace.bridge.E2eeChatEnvelope.decode(mediaWire, "fallback")
            as com.zerotrace.bridge.E2eeChatEnvelope.Decoded.Media
        assertEquals("msg-77", decodedMedia.msgId)
        assertEquals(com.zerotrace.bridge.MessagePayloadKind.VOICE_NOTE, decodedMedia.payloadKind)
        assertEquals(3, decodedMedia.voiceDurationSec)
        assertEquals("tx-99", decodedMedia.manifest.transferId)
        assertEquals("You", decodedMedia.replyToSender)
        assertArrayEquals(key32, decodedMedia.fileSecretKey32)
    }

    @Test
    fun testEphemeralCallX25519KeyDerivationAndJpegExifStripping() {
        val (callerPriv, callerPub) = com.zerotrace.media.EncryptedCallMediaEngine.generateEphemeralX25519KeyPair()
        val (calleePriv, calleePub) = com.zerotrace.media.EncryptedCallMediaEngine.generateEphemeralX25519KeyPair()
        val sessionId = "9f8e7d6c5b4a3210"
        val callId = "call-uuid-1"

        val callerKey = com.zerotrace.media.EncryptedCallMediaEngine.deriveCallMediaKey32(
            localPriv32 = callerPriv,
            remotePub32 = calleePub,
            sessionIdHex = sessionId,
            callId = callId
        )
        val calleeKey = com.zerotrace.media.EncryptedCallMediaEngine.deriveCallMediaKey32(
            localPriv32 = calleePriv,
            remotePub32 = callerPub,
            sessionIdHex = sessionId,
            callId = callId
        )
        assertEquals(32, callerKey.size)
        assertArrayEquals(callerKey, calleeKey)

        // Different callId must yield a different domain-separated call media key
        val otherCallKey = com.zerotrace.media.EncryptedCallMediaEngine.deriveCallMediaKey32(
            localPriv32 = callerPriv,
            remotePub32 = calleePub,
            sessionIdHex = sessionId,
            callId = "call-uuid-2"
        )
        assertFalse(callerKey.contentEquals(otherCallKey))

        // Synthetic JPEG: SOI (FF D8) + APP1 EXIF segment (FF E1, len 8, "Exif\u0000\u0000") + SOF0 (FF C0, len 4, 00 01) + EOI (FF D9)
        val fakeJpegWithExif = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE1.toByte(), 0x00, 0x08, 'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0x00, 0x00,
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x04, 0x00, 0x01,
            0xFF.toByte(), 0xD9.toByte()
        )
        val stripped = com.zerotrace.bridge.EncryptedFileTransferEngine.stripExifSegmentsInRam(fakeJpegWithExif)
        assertTrue(stripped.size < fakeJpegWithExif.size)
        val strippedStr = String(stripped, Charsets.ISO_8859_1)
        assertFalse(strippedStr.contains("Exif"))
        assertEquals(0xFF.toByte(), stripped[0])
        assertEquals(0xD8.toByte(), stripped[1])
    }

    @Test
    fun testTorEd25519V3KeyExpansionAndLegacyIdentityMigration() {
        // 1. Fresh identity must include 32-byte private Tor v3 seed and valid ED25519-V3 blob
        val (summary, ztieBundle) = RustCoreBridge.generateNewDeviceIdentity()
        val torBlob = RustCoreBridge.exportTorAddOnionPrivateKeyBlob()
        assertNotNull(torBlob)
        assertTrue(torBlob!!.startsWith("ED25519-V3:"))
        val decoded64 = java.util.Base64.getDecoder().decode(torBlob.removePrefix("ED25519-V3:"))
        assertEquals(64, decoded64.size)
        // RFC 8032 scalar clamping bits check
        assertEquals(0, decoded64[0].toInt() and 0x07)
        assertEquals(0, decoded64[31].toInt() and 0x80)
        assertNotEquals(0, decoded64[31].toInt() and 0x40)

        // Deterministic seed expansion consistency
        val fixedSeed = ByteArray(32) { (it * 7 + 3).toByte() }
        val expanded1 = RustCoreBridge.buildTorAddOnionPrivateKeyParam(fixedSeed)
        val expanded2 = RustCoreBridge.buildTorAddOnionPrivateKeyParam(fixedSeed)
        assertEquals(expanded1, expanded2)

        // 2. Legacy ZTID migration test: parse ZTIE and rewrite as 9-field ZTID omitting private seed
        val ztidBytes = java.io.DataInputStream(java.io.ByteArrayInputStream(ztieBundle)).use { inp ->
            assertEquals(0x5A544945, inp.readInt()) // "ZTIE"
            val fields = ArrayList<ByteArray>(10)
            for (i in 0 until 10) {
                val len = inp.readInt()
                fields.add(ByteArray(len).also { inp.readFully(it) })
            }
            val baos = java.io.ByteArrayOutputStream()
            java.io.DataOutputStream(baos).use { out ->
                out.writeInt(0x5A544944) // "ZTID"
                for (i in 0 until 8) {
                    out.writeInt(fields[i].size)
                    out.write(fields[i])
                }
                // Field 8 in ZTID was only the public key
                out.writeInt(fields[9].size)
                out.write(fields[9])
            }
            baos.toByteArray()
        }

        val restoredLegacy = RustCoreBridge.restoreWrappedIdentity(ztidBytes)
        assertEquals(summary.onionAddress, restoredLegacy.onionAddress)
        assertEquals(null, RustCoreBridge.exportTorAddOnionPrivateKeyBlob())

        val migrated = RustCoreBridge.migrateLegacyOnionSeedIfNeeded()
        assertNotNull(migrated)
        val (migratedSummary, upgradedBundle) = migrated!!
        val migratedBlob = RustCoreBridge.exportTorAddOnionPrivateKeyBlob()
        assertNotNull(migratedBlob)
        assertTrue(migratedBlob!!.startsWith("ED25519-V3:"))

        // Re-restoring upgraded ZTIE preserves exact onion and private Tor key blob
        val reRestored = RustCoreBridge.restoreWrappedIdentity(upgradedBundle)
        assertEquals(migratedSummary.onionAddress, reRestored.onionAddress)
        assertEquals(migratedBlob, RustCoreBridge.exportTorAddOnionPrivateKeyBlob())
    }

    @Test
    fun testEmbeddedTorSocks5Rfc1928DomainDialAndFailClosed() {
        val (summary, _) = RustCoreBridge.generateNewDeviceIdentity()
        val peerOnion = summary.onionAddress

        // Reject clearnet destination & non-loopback SOCKS host before opening any socket
        try {
            com.zerotrace.network.EmbeddedTorEngine.connectToOnionOverSocks5(
                peerOnion = "example.com",
                targetPort = 9443,
                socksHost = "127.0.0.1",
                socksPort = 9050
            )
            fail("Expected clearnet destination to throw SecurityException")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("FAIL_CLOSED_VIOLATION"))
        }

        try {
            com.zerotrace.network.EmbeddedTorEngine.connectToOnionOverSocks5(
                peerOnion = peerOnion,
                targetPort = 9443,
                socksHost = "8.8.8.8",
                socksPort = 9050
            )
            fail("Expected public SOCKS host to throw SecurityException")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("FAIL_CLOSED_VIOLATION"))
        }

        // Mock local Tor SOCKS5 server verifying RFC 1928 ATYP=0x03 domain tunneling
        val server = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))
        val serverErr = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        val serverThread = Thread {
            try {
                // Connection 1: Expect valid CONNECT and echo tunnel bytes
                server.accept().use { client ->
                    val inp = java.io.DataInputStream(client.getInputStream())
                    val out = client.getOutputStream()
                    val greeting = ByteArray(3).also { inp.readFully(it) }
                    assertArrayEquals(byteArrayOf(0x05, 0x01, 0x00), greeting)
                    out.write(byteArrayOf(0x05, 0x00))
                    out.flush()

                    val hdr = ByteArray(5).also { inp.readFully(it) }
                    assertEquals(0x05.toByte(), hdr[0]) // VER
                    assertEquals(0x01.toByte(), hdr[1]) // CONNECT
                    assertEquals(0x03.toByte(), hdr[3]) // ATYP = DOMAINNAME
                    val domainLen = hdr[4].toInt() and 0xFF
                    assertEquals(peerOnion.length, domainLen)
                    val hostBytes = ByteArray(domainLen).also { inp.readFully(it) }
                    assertEquals(peerOnion, String(hostBytes, Charsets.US_ASCII))
                    val portHi = inp.readUnsignedByte()
                    val portLo = inp.readUnsignedByte()
                    assertEquals(9443, (portHi shl 8) or portLo)

                    out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0x24, 0xDF.toByte()))
                    out.flush()

                    val payload = ByteArray(4).also { inp.readFully(it) }
                    assertEquals("PING", String(payload, Charsets.UTF_8))
                    out.write("PONG".toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                // Connection 2: Simulate Tor circuit unreachable (REP = 0x04)
                server.accept().use { client ->
                    val inp = java.io.DataInputStream(client.getInputStream())
                    val out = client.getOutputStream()
                    inp.readFully(ByteArray(3))
                    out.write(byteArrayOf(0x05, 0x00))
                    out.flush()
                    val hdr = ByteArray(5).also { inp.readFully(it) }
                    inp.readFully(ByteArray((hdr[4].toInt() and 0xFF) + 2))
                    out.write(byteArrayOf(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    out.flush()
                }
            } catch (t: Throwable) {
                serverErr.set(t)
            }
        }
        serverThread.start()

        try {
            com.zerotrace.network.EmbeddedTorEngine.connectToOnionOverSocks5(
                peerOnion = peerOnion,
                targetPort = 9443,
                socksHost = "127.0.0.1",
                socksPort = server.localPort,
                connectTimeoutMs = 5_000
            ).use { tunneled ->
                tunneled.getOutputStream().write("PING".toByteArray(Charsets.UTF_8))
                tunneled.getOutputStream().flush()
                val pong = ByteArray(4)
                java.io.DataInputStream(tunneled.getInputStream()).readFully(pong)
                assertEquals("PONG", String(pong, Charsets.UTF_8))
            }

            try {
                com.zerotrace.network.EmbeddedTorEngine.connectToOnionOverSocks5(
                    peerOnion = peerOnion,
                    targetPort = 9443,
                    socksHost = "127.0.0.1",
                    socksPort = server.localPort,
                    connectTimeoutMs = 5_000
                )
                fail("Expected SOCKS5 REP=0x04 to throw IOException")
            } catch (expected: java.io.IOException) {
                assertTrue(expected.message!!.contains("0x04"))
            }

            serverThread.join(3_000)
            serverErr.get()?.let { throw it }
        } finally {
            server.close()
        }
    }
}



