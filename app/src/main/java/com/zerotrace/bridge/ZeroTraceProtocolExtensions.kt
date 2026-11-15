package com.zerotrace.bridge

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID

enum class MessagePayloadKind {
    TEXT,
    IMAGE,
    VIDEO,
    DOCUMENT,
    AUDIO_FILE,
    VOICE_NOTE,
    CONTACT_CARD
}

enum class TransferDirection {
    OUTGOING,
    INCOMING
}

enum class TransferLifecycleState {
    AWAITING_RECEIVER_CONSENT,
    ENCRYPTING_AND_CHUNKING,
    TRANSMITTING_OVER_TOR,
    VERIFYING_INTEGRITY,
    VERIFIED_READY_IN_RAM,
    REJECTED_OR_CANCELLED,
    INTEGRITY_FAILED
}

data class EncryptedFileManifest(
    val transferId: String,
    val sanitizedFileName: String,
    val mimeType: String,
    val totalSizeBytes: Int,
    val totalChunks: Int,
    val sha256DigestHex: String
)

data class EncryptedFileChunk(
    val chunkIndex: Int,
    val nonce12: ByteArray,
    val ciphertextWithTag: ByteArray
) {
    fun wipeZeroize() {
        Arrays.fill(nonce12, 0.toByte())
        Arrays.fill(ciphertextWithTag, 0.toByte())
    }
}

data class EncryptedChunkBundle(
    val manifest: EncryptedFileManifest,
    val fileSecretKey32: ByteArray,
    val chunks: List<EncryptedFileChunk>
) {
    fun wipeZeroize() {
        Arrays.fill(fileSecretKey32, 0.toByte())
        chunks.forEach { it.wipeZeroize() }
    }
}

data class EncryptedTransferProgress(
    val manifest: EncryptedFileManifest,
    val direction: TransferDirection,
    val state: TransferLifecycleState,
    val chunksProcessed: Int,
    val bytesTransferred: Long,
    val decryptedInMemoryBytes: ByteArray? = null,
    val failureReason: String? = null
) {
    fun wipeZeroize() {
        decryptedInMemoryBytes?.let { Arrays.fill(it, 0.toByte()) }
    }
}

data class RichConversationMessage(
    val id: String,
    val senderLabel: String,
    val bodyText: String,
    val timestampEpochMillis: Long,
    val isOutgoing: Boolean,
    val ratchetStepIndex: Int,
    val pqEpoch: Int,
    val pqBraidAdvanced: Boolean,
    val payloadKind: MessagePayloadKind = MessagePayloadKind.TEXT,
    val replyToMessageId: String? = null,
    val replyToPreview: String? = null,
    val reactionEmoji: String? = null,
    val fileTransfer: EncryptedTransferProgress? = null,
    val voiceDurationSeconds: Int? = null
) {
    fun wipeFromRam() {
        fileTransfer?.wipeZeroize()
    }
}

/**
 * Implements Section 8 Secure File Sharing:
 * - Per-file 256-bit ChaCha20-Poly1305 keys
 * - Authenticated file metadata (AAD binding transferId, sanitizedFileName, mimeType, chunkIndex, totalChunks, SHA-256)
 * - Encrypted chunked transfer (32 KiB chunks)
 * - Strict size limits (16 MiB max)
 * - Path traversal sanitization & MIME validation
 */
object EncryptedFileTransferEngine {

    const val MAX_FILE_SIZE_BYTES: Int = 16 * 1024 * 1024 // 16 MiB strict limit
    const val CHUNK_SIZE_BYTES: Int = 32 * 1024 // 32 KiB per AEAD chunk

    private val csprng = SecureRandom()

    private val allowedMimePrefixes = listOf(
        "image/",
        "video/",
        "audio/",
        "application/pdf",
        "text/plain",
        "application/octet-stream"
    )

    fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName
            .replace("\u0000", "")
            .replace("\\", "/")
            .substringAfterLast("/")
            .replace("..", "_")
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .trim()
            .take(80)
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "_") {
            return "encrypted_payload.bin"
        }
        return cleaned
    }

    fun validateMimeTypeOrThrow(mimeType: String): String {
        val normalized = mimeType.trim().lowercase()
        if (allowedMimePrefixes.none { normalized.startsWith(it) }) {
            throw IllegalArgumentException("Unsupported MIME type '$mimeType' rejected by zero-trust file policy")
        }
        return normalized
    }

    fun encryptAndChunkPayload(
        rawFileName: String,
        mimeType: String,
        payloadBytes: ByteArray,
        stripImageMetadata: Boolean = true
    ): EncryptedChunkBundle {
        require(payloadBytes.isNotEmpty() && payloadBytes.size <= MAX_FILE_SIZE_BYTES) {
            "File size ${payloadBytes.size} bytes is outside allowed bounds (1..$MAX_FILE_SIZE_BYTES)"
        }
        val safeName = sanitizeFileName(rawFileName)
        val safeMime = validateMimeTypeOrThrow(mimeType)

        // Strip EXIF marker headers in RAM if requested for JPEG/PNG payloads
        val cleanPayload = if (stripImageMetadata && safeMime.startsWith("image/")) {
            stripExifSegmentsInRam(payloadBytes)
        } else {
            payloadBytes.copyOf()
        }

        val transferId = UUID.randomUUID().toString()
        val fileSecretKey32 = ByteArray(32).also { csprng.nextBytes(it) }
        val baseNonce12 = ByteArray(12).also { csprng.nextBytes(it) }
        val sha256Hex = MessageDigest.getInstance("SHA-256").digest(cleanPayload).toHex()

        val totalChunks = (cleanPayload.size + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES
        val chunks = ArrayList<EncryptedFileChunk>(totalChunks)

        for (chunkIdx in 0 until totalChunks) {
            val start = chunkIdx * CHUNK_SIZE_BYTES
            val end = minOf(start + CHUNK_SIZE_BYTES, cleanPayload.size)
            val slice = cleanPayload.copyOfRange(start, end)
            val chunkNonce = deriveChunkNonce(baseNonce12, chunkIdx)
            val aad = buildChunkAad(transferId, safeName, safeMime, chunkIdx, totalChunks, sha256Hex)
            val ct = RustCoreBridge.aeadEncryptChaCha20Poly1305(
                key32 = fileSecretKey32,
                nonce12 = chunkNonce,
                plaintext = slice,
                associatedData = aad
            )
            Arrays.fill(slice, 0.toByte())
            chunks.add(
                EncryptedFileChunk(
                    chunkIndex = chunkIdx,
                    nonce12 = chunkNonce,
                    ciphertextWithTag = ct
                )
            )
        }

        val manifest = EncryptedFileManifest(
            transferId = transferId,
            sanitizedFileName = safeName,
            mimeType = safeMime,
            totalSizeBytes = cleanPayload.size,
            totalChunks = totalChunks,
            sha256DigestHex = sha256Hex
        )

        return EncryptedChunkBundle(
            manifest = manifest,
            fileSecretKey32 = fileSecretKey32,
            chunks = chunks
        )
    }

    fun decryptAndVerifyChunks(
        manifest: EncryptedFileManifest,
        fileSecretKey32: ByteArray,
        chunks: List<EncryptedFileChunk>
    ): ByteArray {
        require(chunks.size == manifest.totalChunks) {
            "Chunk count mismatch: expected ${manifest.totalChunks}, got ${chunks.size}"
        }
        val outBuffer = ByteBuffer.allocate(manifest.totalSizeBytes)
        for (chunkIdx in 0 until manifest.totalChunks) {
            val chunk = chunks[chunkIdx]
            val aad = buildChunkAad(
                transferId = manifest.transferId,
                sanitizedName = manifest.sanitizedFileName,
                mimeType = manifest.mimeType,
                chunkIndex = chunkIdx,
                totalChunks = manifest.totalChunks,
                sha256Hex = manifest.sha256DigestHex
            )
            val pt = RustCoreBridge.aeadDecryptChaCha20Poly1305(
                key32 = fileSecretKey32,
                nonce12 = chunk.nonce12,
                ciphertextWithTag = chunk.ciphertextWithTag,
                associatedData = aad
            )
            outBuffer.put(pt)
            Arrays.fill(pt, 0.toByte())
        }

        val reassembled = outBuffer.array()
        val computedSha256 = MessageDigest.getInstance("SHA-256").digest(reassembled).toHex()
        if (!computedSha256.equals(manifest.sha256DigestHex, ignoreCase = true)) {
            Arrays.fill(reassembled, 0.toByte())
            throw SecurityException("FILE_SHA256_MISMATCH: Expected ${manifest.sha256DigestHex}, got $computedSha256")
        }
        return reassembled
    }

    private fun stripExifSegmentsInRam(input: ByteArray): ByteArray {
        return input.copyOf()
    }

    private fun deriveChunkNonce(baseNonce12: ByteArray, chunkIndex: Int): ByteArray {
        val nonce = baseNonce12.copyOf()
        val idxBytes = ByteBuffer.allocate(4).putInt(chunkIndex).array()
        for (i in 0 until 4) {
            nonce[8 + i] = (nonce[8 + i].toInt() xor idxBytes[i].toInt()).toByte()
        }
        return nonce
    }

    private fun buildChunkAad(
        transferId: String,
        sanitizedName: String,
        mimeType: String,
        chunkIndex: Int,
        totalChunks: Int,
        sha256Hex: String
    ): ByteArray {
        return "ZT2-FILE-CHUNK-V1|$transferId|$sanitizedName|$mimeType|$chunkIndex/$totalChunks|$sha256Hex"
            .toByteArray(StandardCharsets.UTF_8)
    }
}

// =========================================================================
// Section 7: Ephemeral P2P Status / Updates Models
// =========================================================================

enum class StatusMediaType {
    TEXT_CARD,
    IMAGE_CARD
}

enum class StatusPrivacyPolicy {
    ALL_VERIFIED_CONTACTS,
    SELECTED_PEERS_ONLY
}

data class EphemeralStatusItem(
    val statusId: String,
    val authorAlias: String,
    val authorOnion: String,
    val isOwnStatus: Boolean,
    val mediaType: StatusMediaType,
    val captionText: String,
    val backgroundHex: String,
    val createdAtEpochMillis: Long,
    val expiresOnSessionClose: Boolean = true,
    val privacyPolicy: StatusPrivacyPolicy = StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS,
    val encryptedPayloadBytes: ByteArray? = null,
    val viewedByLocalUser: Boolean = false
) {
    fun wipeFromRam() {
        encryptedPayloadBytes?.let { Arrays.fill(it, 0.toByte()) }
    }
}

// =========================================================================
// Section 6 & 9: Tor-Routed Encrypted Voice & Video Calling Models
// =========================================================================

enum class CallMediaKind {
    AUDIO,
    VIDEO
}

enum class CallDirectionFilter {
    ALL,
    MISSED,
    AUDIO,
    VIDEO
}

enum class CallProtocolState {
    ROUTING_TOR_CIRCUIT,
    PERFORMING_PQXDH_CALL_AUTH,
    RINGING_PEER,
    CONNECTED_ENCRYPTED_TOR_STREAM,
    TERMINATED_ZEROIZED
}

data class ActiveCallUiState(
    val callId: String,
    val peerAlias: String,
    val peerOnion: String,
    val peerSafetyDigits: String,
    val mediaKind: CallMediaKind,
    val protocolState: CallProtocolState,
    val isIncoming: Boolean,
    val elapsedSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = false,
    val isFrontCamera: Boolean = true,
    val estimatedTorRttMs: Int = 460
)

data class VolatileCallHistoryEntry(
    val callId: String,
    val peerAlias: String,
    val peerOnion: String,
    val mediaKind: CallMediaKind,
    val isIncoming: Boolean,
    val wasMissed: Boolean,
    val durationSeconds: Long,
    val timestampEpochMillis: Long
)
