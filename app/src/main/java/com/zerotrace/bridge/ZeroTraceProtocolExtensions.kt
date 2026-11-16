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
    val replyToSenderLabel: String? = null,
    val reactionEmoji: String? = null,
    val fileTransfer: EncryptedTransferProgress? = null,
    val voiceDurationSeconds: Int? = null
) {
    fun wipeFromRam() {
        fileTransfer?.wipeZeroize()
    }
}

/**
 * Structured, authenticated chat envelope serialized INSIDE the `ChaCha20-Poly1305` Double Ratchet
 * plaintext so quoted replies, emoji reactions, and encrypted file keys never leak outside E2EE.
 */
object E2eeChatEnvelope {
    private const val PREFIX = "ZT2ENV1|"
    private val b64Enc = java.util.Base64.getUrlEncoder().withoutPadding()
    private val b64Dec = java.util.Base64.getUrlDecoder()

    sealed interface Decoded {
        data class Text(
            val msgId: String,
            val body: String,
            val replyToId: String?,
            val replyToPreview: String?,
            val replyToSender: String?
        ) : Decoded

        data class Media(
            val msgId: String,
            val payloadKind: MessagePayloadKind,
            val caption: String,
            val voiceDurationSec: Int?,
            val manifest: EncryptedFileManifest,
            val fileSecretKey32: ByteArray,
            val replyToId: String?,
            val replyToPreview: String?,
            val replyToSender: String?
        ) : Decoded

        data class Reaction(
            val targetMsgId: String,
            val emoji: String
        ) : Decoded
    }

    private fun encStr(s: String?): String {
        if (s == null) return ""
        return b64Enc.encodeToString(s.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decStr(s: String): String {
        if (s.isEmpty()) return ""
        return String(b64Dec.decode(s), StandardCharsets.UTF_8)
    }

    fun encodeText(
        msgId: String,
        body: String,
        replyToId: String? = null,
        replyToPreview: String? = null,
        replyToSender: String? = null
    ): String {
        return listOf(
            "ZT2ENV1",
            "MSG",
            encStr(msgId),
            encStr(body),
            encStr(replyToId),
            encStr(replyToPreview),
            encStr(replyToSender)
        ).joinToString("|")
    }

    fun encodeMedia(
        msgId: String,
        payloadKind: MessagePayloadKind,
        caption: String,
        voiceDurationSec: Int?,
        manifest: EncryptedFileManifest,
        fileSecretKey32: ByteArray,
        replyToId: String? = null,
        replyToPreview: String? = null,
        replyToSender: String? = null
    ): String {
        return listOf(
            "ZT2ENV1",
            "MEDIA",
            encStr(msgId),
            payloadKind.name,
            encStr(caption),
            (voiceDurationSec ?: 0).toString(),
            encStr(manifest.transferId),
            encStr(manifest.sanitizedFileName),
            encStr(manifest.mimeType),
            manifest.totalSizeBytes.toString(),
            manifest.totalChunks.toString(),
            encStr(manifest.sha256DigestHex),
            b64Enc.encodeToString(fileSecretKey32),
            encStr(replyToId),
            encStr(replyToPreview),
            encStr(replyToSender)
        ).joinToString("|")
    }

    fun encodeReaction(targetMsgId: String, emoji: String): String {
        return listOf(
            "ZT2ENV1",
            "REACT",
            encStr(targetMsgId),
            encStr(emoji)
        ).joinToString("|")
    }

    fun decode(rawPlaintext: String, fallbackMsgId: String): Decoded {
        if (!rawPlaintext.startsWith(PREFIX)) {
            return Decoded.Text(
                msgId = fallbackMsgId,
                body = rawPlaintext,
                replyToId = null,
                replyToPreview = null,
                replyToSender = null
            )
        }
        return try {
            val parts = rawPlaintext.split("|")
            when (parts.getOrNull(1)) {
                "MSG" -> Decoded.Text(
                    msgId = decStr(parts.getOrElse(2) { "" }).ifEmpty { fallbackMsgId },
                    body = decStr(parts.getOrElse(3) { "" }),
                    replyToId = decStr(parts.getOrElse(4) { "" }).ifEmpty { null },
                    replyToPreview = decStr(parts.getOrElse(5) { "" }).ifEmpty { null },
                    replyToSender = decStr(parts.getOrElse(6) { "" }).ifEmpty { null }
                )

                "MEDIA" -> {
                    val kind = try {
                        MessagePayloadKind.valueOf(parts.getOrElse(3) { "DOCUMENT" })
                    } catch (_: Exception) {
                        MessagePayloadKind.DOCUMENT
                    }
                    val dur = parts.getOrElse(5) { "0" }.toIntOrNull()?.takeIf { it > 0 }
                    val manifest = EncryptedFileManifest(
                        transferId = decStr(parts.getOrElse(6) { "" }),
                        sanitizedFileName = decStr(parts.getOrElse(7) { "" }),
                        mimeType = decStr(parts.getOrElse(8) { "" }),
                        totalSizeBytes = parts.getOrElse(9) { "0" }.toIntOrNull() ?: 0,
                        totalChunks = parts.getOrElse(10) { "0" }.toIntOrNull() ?: 0,
                        sha256DigestHex = decStr(parts.getOrElse(11) { "" })
                    )
                    val keyBytes = b64Dec.decode(parts.getOrElse(12) { "" })
                    Decoded.Media(
                        msgId = decStr(parts.getOrElse(2) { "" }).ifEmpty { fallbackMsgId },
                        payloadKind = kind,
                        caption = decStr(parts.getOrElse(4) { "" }),
                        voiceDurationSec = dur,
                        manifest = manifest,
                        fileSecretKey32 = keyBytes,
                        replyToId = decStr(parts.getOrElse(13) { "" }).ifEmpty { null },
                        replyToPreview = decStr(parts.getOrElse(14) { "" }).ifEmpty { null },
                        replyToSender = decStr(parts.getOrElse(15) { "" }).ifEmpty { null }
                    )
                }

                "REACT" -> Decoded.Reaction(
                    targetMsgId = decStr(parts.getOrElse(2) { "" }),
                    emoji = decStr(parts.getOrElse(3) { "" })
                )

                else -> Decoded.Text(
                    msgId = fallbackMsgId,
                    body = rawPlaintext,
                    replyToId = null,
                    replyToPreview = null,
                    replyToSender = null
                )
            }
        } catch (_: Exception) {
            Decoded.Text(
                msgId = fallbackMsgId,
                body = rawPlaintext,
                replyToId = null,
                replyToPreview = null,
                replyToSender = null
            )
        }
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

        // Strip EXIF/XMP/IPTC marker segments in RAM if requested for JPEG/image payloads
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

    /**
     * Strips JPEG APP1..APP15 (`0xFFE1..0xFFEF`, which hold EXIF, GPS, XMP, and camera metadata)
     * strictly in RAM while leaving SOS scan image data untouched.
     */
    fun stripExifSegmentsInRam(input: ByteArray): ByteArray {
        if (input.size < 4 || input[0] != 0xFF.toByte() || input[1] != 0xD8.toByte()) {
            return input.copyOf()
        }
        return try {
            val out = java.io.ByteArrayOutputStream(input.size)
            out.write(0xFF)
            out.write(0xD8)
            var idx = 2
            while (idx + 1 < input.size) {
                if (input[idx] != 0xFF.toByte()) {
                    out.write(input, idx, input.size - idx)
                    break
                }
                while (idx < input.size && input[idx] == 0xFF.toByte()) idx++
                if (idx >= input.size) break
                val marker = input[idx].toInt() and 0xFF
                idx++
                if (marker == 0xDA || marker == 0xD9) {
                    out.write(0xFF)
                    out.write(marker)
                    if (idx < input.size) out.write(input, idx, input.size - idx)
                    break
                }
                if (marker == 0x00 || marker in 0xD0..0xD7) {
                    out.write(0xFF)
                    out.write(marker)
                    continue
                }
                if (idx + 1 >= input.size) break
                val segLen = ((input[idx].toInt() and 0xFF) shl 8) or (input[idx + 1].toInt() and 0xFF)
                if (segLen < 2 || idx + segLen > input.size) {
                    out.write(0xFF)
                    out.write(marker)
                    out.write(input, idx, input.size - idx)
                    break
                }
                // Strip APP1..APP15 (EXIF / XMP / ICC / GPS) and COM comments
                if (marker !in 0xE1..0xEF && marker != 0xFE) {
                    out.write(0xFF)
                    out.write(marker)
                    out.write(input, idx, segLen)
                }
                idx += segLen
            }
            out.toByteArray()
        } catch (_: Exception) {
            input.copyOf()
        }
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
    val estimatedTorRttMs: Int = 180,
    val remoteAudioPacketsReceived: Long = 0L,
    val remoteVideoFramesReceived: Long = 0L,
    val isRemoteMuted: Boolean = false,
    val isRemoteCameraEnabled: Boolean = true,
    val callTransportNote: String = "Tor/Onion TCP AEAD Media Stream"
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
