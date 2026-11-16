package com.zerotrace.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.zerotrace.bridge.EncryptedFileTransferEngine
import com.zerotrace.bridge.MessagePayloadKind
import java.io.File
import java.io.RandomAccessFile
import java.util.Arrays
import java.util.UUID

/**
 * Play-Policy compliant helper (zero broad storage permissions):
 * - Exports decrypted photos/videos/audio/documents on user request via scoped `MediaStore`
 *   (`IS_PENDING = 1` on API 29+).
 * - Materializes decrypted payloads strictly into app-private `cacheDir/ephemeral_open/` only while
 *   previewing/playing videos or launching `Intent.ACTION_VIEW` via `FileProvider`, and shreds
 *   temp files with `0x00` passes on dispose / wipe.
 */
object MediaExportAndOpenHelper {

    private const val TEMP_DIR_NAME = "ephemeral_open"

    fun saveDecryptedMediaToDevice(
        context: Context,
        fileName: String,
        mimeType: String,
        kind: MessagePayloadKind,
        bytes: ByteArray
    ): Result<String> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Payload is empty or has already been wiped from RAM."))
        }
        val safeName = EncryptedFileTransferEngine.sanitizeFileName(fileName)
        val safeMime = mimeType.ifBlank { inferMime(safeName, kind) }

        return try {
            val resolver = context.applicationContext.contentResolver
            val (collectionUri, relativeDir) = when (kind) {
                MessagePayloadKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "Pictures/ZeroTrace"
                MessagePayloadKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "Movies/ZeroTrace"
                MessagePayloadKind.AUDIO_FILE, MessagePayloadKind.VOICE_NOTE ->
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "Music/ZeroTrace"
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI to "Download/ZeroTrace"
                    } else {
                        return Result.failure(
                            IllegalStateException("Use Open/Share to save documents externally on Android 8–9.")
                        )
                    }
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, safeMime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val insertedUri: Uri = resolver.insert(collectionUri, values)
                ?: return Result.failure(IllegalStateException("Android MediaStore rejected destination entry."))

            resolver.openOutputStream(insertedUri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: run {
                resolver.delete(insertedUri, null, null)
                return Result.failure(IllegalStateException("Unable to open output stream for $safeName"))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publish = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(insertedUri, publish, null, null)
            }

            Result.success("$relativeDir/$safeName")
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun prepareTempCacheFile(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ): File {
        val dir = File(context.applicationContext.cacheDir, TEMP_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
        val safeName = EncryptedFileTransferEngine.sanitizeFileName(fileName)
        val slot = File(dir, UUID.randomUUID().toString().take(8)).apply { mkdirs() }
        val target = File(slot, safeName)
        target.writeBytes(bytes)
        return target
    }

    fun getShareableUri(context: Context, file: File): Uri {
        val appCtx = context.applicationContext
        return FileProvider.getUriForFile(
            appCtx,
            "${appCtx.packageName}.fileprovider",
            file
        )
    }

    fun openDecryptedFileExternally(
        context: Context,
        fileName: String,
        mimeType: String,
        kind: MessagePayloadKind,
        bytes: ByteArray
    ): Result<Unit> {
        if (bytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Media payload is empty or already wiped from RAM."))
        }
        return try {
            val file = prepareTempCacheFile(context, fileName, bytes)
            val uri = getShareableUri(context, file)
            val effectiveMime = mimeType.ifBlank { inferMime(fileName, kind) }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, effectiveMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = context.packageManager
            ExternalPickerGuard.markLaunchingExternalPicker()
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                Result.success(Unit)
            } else {
                val chooser = Intent.createChooser(intent, "Open $fileName").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
                Result.success(Unit)
            }
        } catch (t: Throwable) {
            ExternalPickerGuard.clearOnPickerResult()
            Result.failure(t)
        }
    }

    fun shredTempCacheFile(file: File?) {
        if (file == null || !file.exists()) return
        try {
            val len = file.length()
            if (len > 0L) {
                RandomAccessFile(file, "rw").use { raf ->
                    val zeros = ByteArray(minOf(len.toInt(), 32_768))
                    Arrays.fill(zeros, 0.toByte())
                    var rem = len
                    while (rem > 0L) {
                        val chunk = minOf(rem, zeros.size.toLong()).toInt()
                        raf.write(zeros, 0, chunk)
                        rem -= chunk
                    }
                    raf.fd.sync()
                }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                val parent = file.parentFile
                file.delete()
                if (parent != null && parent.name != TEMP_DIR_NAME && parent.listFiles()?.isEmpty() == true) {
                    parent.delete()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun shredAllEphemeralOpenCache(context: Context) {
        try {
            val dir = File(context.applicationContext.cacheDir, TEMP_DIR_NAME)
            if (!dir.exists()) return
            dir.walkBottomUp().forEach { entry ->
                if (entry.isFile) {
                    shredTempCacheFile(entry)
                } else if (entry != dir) {
                    entry.delete()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun inferMime(fileName: String, kind: MessagePayloadKind): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".webm") -> "video/webm"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".txt") -> "text/plain"
            kind == MessagePayloadKind.IMAGE -> "image/jpeg"
            kind == MessagePayloadKind.VIDEO -> "video/mp4"
            kind == MessagePayloadKind.AUDIO_FILE || kind == MessagePayloadKind.VOICE_NOTE -> "audio/wav"
            else -> "application/octet-stream"
        }
    }
}
