package com.zerotrace.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.zerotrace.bridge.CallMediaKind
import com.zerotrace.bridge.EncryptedFileManifest
import com.zerotrace.bridge.EncryptedTransferProgress
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.LocalUserProfile
import com.zerotrace.bridge.MessageDeliveryState
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.StatusPrivacyPolicy
import com.zerotrace.bridge.TransferDirection
import com.zerotrace.bridge.TransferLifecycleState
import com.zerotrace.bridge.VolatileCallHistoryEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class PersistedPrefsSnapshot(
    val profile: LocalUserProfile = LocalUserProfile(),
    val foreverVaultEnabled: Boolean = true,
    val sessionTimeoutSeconds: Long = 0L,
    val disappearingMessageTimerSec: Long = 0L,
    val biometricAppLockEnabled: Boolean = false,
    val flagSecureEnabled: Boolean = false,
    val autoLockOnBackground: Boolean = false,
    val readReceiptsEnabled: Boolean = true,
    val typingIndicatorsEnabled: Boolean = true,
    val metadataMinimizedMode: Boolean = true,
    val themeModeName: String = "LIGHT",
    val accentPaletteName: String = "EMERALD",
    val wallpaperStyleName: String = "OBSIDIAN_GRID",
    val languageCode: String = "en",
    val pinnedOnions: Set<String> = emptySet(),
    val mutedOnions: Set<String> = emptySet(),
    val archivedOnions: Set<String> = emptySet(),
    val blockedOnions: Set<String> = emptySet()
)

data class PersistedChatVaultSnapshot(
    val richMessagesByOnion: Map<String, List<RichConversationMessage>> = emptyMap(),
    val lastMessagePreviewByOnion: Map<String, String> = emptyMap(),
    val lastMessageTimeByOnion: Map<String, Long> = emptyMap(),
    val unreadCountByOnion: Map<String, Int> = emptyMap(),
    val callHistory: List<VolatileCallHistoryEntry> = emptyList(),
    val ephemeralStatuses: List<EphemeralStatusItem> = emptyList()
)

/**
 * Binary codecs for Keystore AES-256-GCM wrapped preferences, user profile, and Forever Chat Vault.
 * Strictly zero plaintext disk writes: output bytes are always wrapped/unwrapped via [KeystoreManager].
 */
object EncryptedVaultCodec {

    private const val PREFS_MAGIC = 0x5A545032 // "ZTP2"
    private const val VAULT_MAGIC = 0x5A545632 // "ZTV2"
    private const val MAX_ATTACHMENT_PERSIST_BYTES = 2 * 1024 * 1024 // 2 MiB cap per vaulted attachment

    fun encodePrefsAndProfile(snapshot: PersistedPrefsSnapshot): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(PREFS_MAGIC)
            out.writeUTF(snapshot.profile.displayName.take(48))
            out.writeUTF(snapshot.profile.bio.take(160))
            val avatar = snapshot.profile.avatarJpegBytes
            if (avatar != null && avatar.isNotEmpty() && avatar.size <= 64 * 1024) {
                out.writeInt(avatar.size)
                out.write(avatar)
            } else {
                out.writeInt(0)
            }
            out.writeLong(snapshot.profile.updatedAtEpochSec)

            out.writeBoolean(snapshot.foreverVaultEnabled)
            out.writeLong(snapshot.sessionTimeoutSeconds)
            out.writeLong(snapshot.disappearingMessageTimerSec)
            out.writeBoolean(snapshot.biometricAppLockEnabled)
            out.writeBoolean(snapshot.flagSecureEnabled)
            out.writeBoolean(snapshot.autoLockOnBackground)
            out.writeBoolean(snapshot.readReceiptsEnabled)
            out.writeBoolean(snapshot.typingIndicatorsEnabled)
            out.writeBoolean(snapshot.metadataMinimizedMode)

            out.writeUTF(snapshot.themeModeName)
            out.writeUTF(snapshot.accentPaletteName)
            out.writeUTF(snapshot.wallpaperStyleName)
            out.writeUTF(snapshot.languageCode)

            writeStringSet(out, snapshot.pinnedOnions)
            writeStringSet(out, snapshot.mutedOnions)
            writeStringSet(out, snapshot.archivedOnions)
            writeStringSet(out, snapshot.blockedOnions)
        }
        return baos.toByteArray()
    }

    fun decodePrefsAndProfile(bytes: ByteArray): PersistedPrefsSnapshot? {
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { inp ->
                if (inp.readInt() != PREFS_MAGIC) return null
                val displayName = inp.readUTF()
                val bio = inp.readUTF()
                val avatarLen = inp.readInt().coerceIn(0, 64 * 1024)
                val avatar = if (avatarLen > 0) ByteArray(avatarLen).also { inp.readFully(it) } else null
                val updatedAt = inp.readLong()

                val foreverEnabled = inp.readBoolean()
                val sessionTimeout = inp.readLong()
                val disappearingTimer = inp.readLong()
                val biometric = inp.readBoolean()
                val flagSecure = inp.readBoolean()
                val autoLock = inp.readBoolean()
                val readReceipts = inp.readBoolean()
                val typing = inp.readBoolean()
                val metadataMin = inp.readBoolean()

                val themeName = inp.readUTF()
                val accentName = inp.readUTF()
                val wallpaperName = inp.readUTF()
                val langCode = inp.readUTF()

                val pinned = readStringSet(inp)
                val muted = readStringSet(inp)
                val archived = readStringSet(inp)
                val blocked = readStringSet(inp)

                PersistedPrefsSnapshot(
                    profile = LocalUserProfile(
                        displayName = displayName.ifBlank { "ZeroTrace User" },
                        bio = bio,
                        avatarJpegBytes = avatar,
                        updatedAtEpochSec = updatedAt
                    ),
                    foreverVaultEnabled = foreverEnabled,
                    sessionTimeoutSeconds = sessionTimeout,
                    disappearingMessageTimerSec = disappearingTimer,
                    biometricAppLockEnabled = biometric,
                    flagSecureEnabled = flagSecure,
                    autoLockOnBackground = autoLock,
                    readReceiptsEnabled = readReceipts,
                    typingIndicatorsEnabled = typing,
                    metadataMinimizedMode = metadataMin,
                    themeModeName = themeName,
                    accentPaletteName = accentName,
                    wallpaperStyleName = wallpaperName,
                    languageCode = langCode,
                    pinnedOnions = pinned,
                    mutedOnions = muted,
                    archivedOnions = archived,
                    blockedOnions = blocked
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun encodeChatVault(snapshot: PersistedChatVaultSnapshot): ByteArray {
        val now = System.currentTimeMillis()
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(VAULT_MAGIC)

            out.writeInt(snapshot.richMessagesByOnion.size)
            for ((onion, rawList) in snapshot.richMessagesByOnion) {
                out.writeUTF(onion)
                val persistedList = rawList
                    .filter { msg ->
                        msg.fileTransfer == null ||
                            msg.fileTransfer.state == TransferLifecycleState.VERIFIED_READY_IN_RAM ||
                            msg.isDeletedPlaceholder
                    }
                    .takeLast(150)
                out.writeInt(persistedList.size)
                for (m in persistedList) {
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
                        val bytes = tr.decryptedInMemoryBytes
                        if (bytes != null && bytes.isNotEmpty() && bytes.size <= MAX_ATTACHMENT_PERSIST_BYTES) {
                            out.writeInt(bytes.size)
                            out.write(bytes)
                        } else {
                            out.writeInt(0)
                        }
                    } else {
                        out.writeBoolean(false)
                    }
                }
            }

            out.writeInt(snapshot.lastMessagePreviewByOnion.size)
            for ((k, v) in snapshot.lastMessagePreviewByOnion) {
                out.writeUTF(k)
                out.writeUTF(v)
            }

            out.writeInt(snapshot.lastMessageTimeByOnion.size)
            for ((k, v) in snapshot.lastMessageTimeByOnion) {
                out.writeUTF(k)
                out.writeLong(v)
            }

            out.writeInt(snapshot.unreadCountByOnion.size)
            for ((k, v) in snapshot.unreadCountByOnion) {
                out.writeUTF(k)
                out.writeInt(v)
            }

            val calls = snapshot.callHistory.take(100)
            out.writeInt(calls.size)
            for (c in calls) {
                out.writeUTF(c.callId)
                out.writeUTF(c.peerAlias)
                out.writeUTF(c.peerOnion)
                out.writeUTF(c.mediaKind.name)
                out.writeBoolean(c.isIncoming)
                out.writeBoolean(c.wasMissed)
                out.writeLong(c.durationSeconds)
                out.writeLong(c.timestampEpochMillis)
            }

            val activeStatuses = snapshot.ephemeralStatuses.filterNot { it.isExpired(now) }.take(60)
            out.writeInt(activeStatuses.size)
            for (st in activeStatuses) {
                out.writeUTF(st.statusId)
                out.writeUTF(st.authorAlias)
                out.writeUTF(st.authorOnion)
                out.writeBoolean(st.isOwnStatus)
                out.writeUTF(st.mediaType.name)
                out.writeUTF(st.captionText)
                out.writeUTF(st.backgroundHex)
                out.writeLong(st.createdAtEpochMillis)
                out.writeBoolean(st.expiresOnSessionClose)
                out.writeUTF(st.privacyPolicy.name)
                out.writeBoolean(st.viewedByLocalUser)
                out.writeLong(st.expiresAtEpochMillis)
                out.writeUTF(st.mimeType ?: "")
                out.writeUTF(st.sanitizedFileName ?: "")
                val media = st.encryptedPayloadBytes
                if (media != null && media.isNotEmpty() && media.size <= MAX_ATTACHMENT_PERSIST_BYTES) {
                    out.writeInt(media.size)
                    out.write(media)
                } else {
                    out.writeInt(0)
                }
            }
        }
        return baos.toByteArray()
    }

    fun decodeChatVault(bytes: ByteArray): PersistedChatVaultSnapshot? {
        val now = System.currentTimeMillis()
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { inp ->
                if (inp.readInt() != VAULT_MAGIC) return null

                val threadCount = inp.readInt().coerceIn(0, 500)
                val richMap = LinkedHashMap<String, List<RichConversationMessage>>(threadCount)
                for (t in 0 until threadCount) {
                    val onion = inp.readUTF()
                    val msgCount = inp.readInt().coerceIn(0, 500)
                    val msgList = ArrayList<RichConversationMessage>(msgCount)
                    for (m in 0 until msgCount) {
                        val id = inp.readUTF()
                        val senderLabel = inp.readUTF()
                        val bodyText = inp.readUTF()
                        val ts = inp.readLong()
                        val isOut = inp.readBoolean()
                        val step = inp.readInt()
                        val epoch = inp.readInt()
                        val braid = inp.readBoolean()
                        val kind = try {
                            MessagePayloadKind.valueOf(inp.readUTF())
                        } catch (_: Exception) {
                            MessagePayloadKind.TEXT
                        }
                        val replyId = inp.readUTF().ifEmpty { null }
                        val replyPrev = inp.readUTF().ifEmpty { null }
                        val replySender = inp.readUTF().ifEmpty { null }
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
                            val payloadLen = inp.readInt().coerceIn(0, MAX_ATTACHMENT_PERSIST_BYTES)
                            val payload = if (payloadLen > 0) ByteArray(payloadLen).also { inp.readFully(it) } else null
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
                                decryptedInMemoryBytes = payload
                            )
                        } else null

                        msgList.add(
                            RichConversationMessage(
                                id = id,
                                senderLabel = senderLabel,
                                bodyText = bodyText,
                                timestampEpochMillis = ts,
                                isOutgoing = isOut,
                                ratchetStepIndex = step,
                                pqEpoch = epoch,
                                pqBraidAdvanced = braid,
                                payloadKind = kind,
                                replyToMessageId = replyId,
                                replyToPreview = replyPrev,
                                replyToSenderLabel = replySender,
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
                    richMap[onion] = msgList
                }

                val previewCount = inp.readInt().coerceIn(0, 500)
                val previews = LinkedHashMap<String, String>(previewCount)
                for (i in 0 until previewCount) {
                    previews[inp.readUTF()] = inp.readUTF()
                }

                val timeCount = inp.readInt().coerceIn(0, 500)
                val times = LinkedHashMap<String, Long>(timeCount)
                for (i in 0 until timeCount) {
                    times[inp.readUTF()] = inp.readLong()
                }

                val unreadCount = inp.readInt().coerceIn(0, 500)
                val unreads = LinkedHashMap<String, Int>(unreadCount)
                for (i in 0 until unreadCount) {
                    unreads[inp.readUTF()] = inp.readInt()
                }

                val callCount = inp.readInt().coerceIn(0, 200)
                val calls = ArrayList<VolatileCallHistoryEntry>(callCount)
                for (i in 0 until callCount) {
                    val callId = inp.readUTF()
                    val peerAlias = inp.readUTF()
                    val peerOnion = inp.readUTF()
                    val kind = try {
                        CallMediaKind.valueOf(inp.readUTF())
                    } catch (_: Exception) {
                        CallMediaKind.AUDIO
                    }
                    val isInc = inp.readBoolean()
                    val missed = inp.readBoolean()
                    val dur = inp.readLong()
                    val ts = inp.readLong()
                    calls.add(
                        VolatileCallHistoryEntry(
                            callId = callId,
                            peerAlias = peerAlias,
                            peerOnion = peerOnion,
                            mediaKind = kind,
                            isIncoming = isInc,
                            wasMissed = missed,
                            durationSeconds = dur,
                            timestampEpochMillis = ts
                        )
                    )
                }

                val statusCount = inp.readInt().coerceIn(0, 100)
                val statuses = ArrayList<EphemeralStatusItem>(statusCount)
                for (i in 0 until statusCount) {
                    val statusId = inp.readUTF()
                    val authorAlias = inp.readUTF()
                    val authorOnion = inp.readUTF()
                    val isOwn = inp.readBoolean()
                    val mediaType = try {
                        StatusMediaType.valueOf(inp.readUTF())
                    } catch (_: Exception) {
                        StatusMediaType.TEXT_CARD
                    }
                    val caption = inp.readUTF()
                    val bgHex = inp.readUTF()
                    val created = inp.readLong()
                    val expiresClose = inp.readBoolean()
                    val policy = try {
                        StatusPrivacyPolicy.valueOf(inp.readUTF())
                    } catch (_: Exception) {
                        StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS
                    }
                    val viewed = inp.readBoolean()
                    val expiresAt = inp.readLong()
                    val mime = inp.readUTF().ifEmpty { null }
                    val fileName = inp.readUTF().ifEmpty { null }
                    val mediaLen = inp.readInt().coerceIn(0, MAX_ATTACHMENT_PERSIST_BYTES)
                    val mediaBytes = if (mediaLen > 0) ByteArray(mediaLen).also { inp.readFully(it) } else null
                    val item = EphemeralStatusItem(
                        statusId = statusId,
                        authorAlias = authorAlias,
                        authorOnion = authorOnion,
                        isOwnStatus = isOwn,
                        mediaType = mediaType,
                        captionText = caption,
                        backgroundHex = bgHex,
                        createdAtEpochMillis = created,
                        expiresOnSessionClose = expiresClose,
                        privacyPolicy = policy,
                        encryptedPayloadBytes = mediaBytes,
                        viewedByLocalUser = viewed,
                        expiresAtEpochMillis = expiresAt,
                        mimeType = mime,
                        sanitizedFileName = fileName
                    )
                    if (!item.isExpired(now)) {
                        statuses.add(item)
                    } else {
                        item.wipeFromRam()
                    }
                }

                PersistedChatVaultSnapshot(
                    richMessagesByOnion = richMap,
                    lastMessagePreviewByOnion = previews,
                    lastMessageTimeByOnion = times,
                    unreadCountByOnion = unreads,
                    callHistory = calls,
                    ephemeralStatuses = statuses
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun compressAvatarUriToCompactJpeg(context: Context, uri: Uri): ByteArray {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot read selected image")
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw IllegalStateException("Unsupported image format")
        val dim = minOf(decoded.width, decoded.height)
        val xOff = (decoded.width - dim) / 2
        val yOff = (decoded.height - dim) / 2
        val square = Bitmap.createBitmap(decoded, xOff, yOff, dim, dim)
        val scaled = Bitmap.createScaledBitmap(square, 160, 160, true)
        if (square !== decoded) square.recycle()
        if (scaled !== decoded) decoded.recycle()

        var quality = 82
        var outBytes: ByteArray
        do {
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            outBytes = baos.toByteArray()
            quality -= 12
        } while (outBytes.size > 24 * 1024 && quality >= 30)
        scaled.recycle()
        return outBytes
    }

    private fun writeStringSet(out: DataOutputStream, set: Set<String>) {
        out.writeInt(set.size)
        for (s in set) out.writeUTF(s)
    }

    private fun readStringSet(inp: DataInputStream): Set<String> {
        val n = inp.readInt().coerceIn(0, 500)
        val set = LinkedHashSet<String>(n)
        for (i in 0 until n) set.add(inp.readUTF())
        return set
    }
}
