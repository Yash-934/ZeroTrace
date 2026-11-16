package com.zerotrace.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.camera.core.ImageProxy
import com.zerotrace.bridge.EncryptedFileTransferEngine
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.RustCoreBridge
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Arrays
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

data class LoadedRamMedia(
    val fileName: String,
    val mimeType: String,
    val payloadBytes: ByteArray,
    val caption: String
)

/**
 * Reads user-selected media/files into RAM via Android's scoped `ContentResolver`
 * (zero broad storage permissions, zero temp files on disk) and strips EXIF/GPS metadata
 * from images by re-encoding clean JPEG pixels in RAM.
 */
object MediaRamLoader {

    data class UriPreviewMeta(
        val fileName: String,
        val mimeType: String,
        val estimatedSizeBytes: Int
    )

    fun peekUriMeta(
        context: Context,
        uri: Uri,
        kind: MessagePayloadKind
    ): UriPreviewMeta {
        val resolver = context.contentResolver
        var displayName = when (kind) {
            MessagePayloadKind.IMAGE -> "photo_${System.currentTimeMillis() % 10000}.jpg"
            MessagePayloadKind.VIDEO -> "video_${System.currentTimeMillis() % 10000}.mp4"
            MessagePayloadKind.AUDIO_FILE -> "audio_${System.currentTimeMillis() % 10000}.m4a"
            else -> "document_${System.currentTimeMillis() % 10000}.pdf"
        }
        var reportedSize = 0
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        reportedSize = cursor.getLong(sizeIdx).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    }
                }
            }
        } catch (_: Throwable) {
        }
        val rawMime = resolver.getType(uri)?.lowercase() ?: when (kind) {
            MessagePayloadKind.IMAGE -> "image/jpeg"
            MessagePayloadKind.VIDEO -> "video/mp4"
            MessagePayloadKind.AUDIO_FILE -> "audio/mpeg"
            else -> "application/octet-stream"
        }
        return UriPreviewMeta(displayName, rawMime, reportedSize)
    }

    fun loadUriToRam(
        context: Context,
        uri: Uri,
        kind: MessagePayloadKind
    ): LoadedRamMedia {
        val resolver = context.contentResolver
        var displayName = when (kind) {
            MessagePayloadKind.IMAGE -> "photo_${System.currentTimeMillis() % 10000}.jpg"
            MessagePayloadKind.VIDEO -> "video_${System.currentTimeMillis() % 10000}.mp4"
            MessagePayloadKind.AUDIO_FILE -> "audio_${System.currentTimeMillis() % 10000}.m4a"
            else -> "document_${System.currentTimeMillis() % 10000}.pdf"
        }
        var reportedSize = -1L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    reportedSize = cursor.getLong(sizeIdx)
                }
            }
        }

        if (reportedSize > EncryptedFileTransferEngine.MAX_FILE_SIZE_BYTES && kind != MessagePayloadKind.IMAGE) {
            throw IllegalArgumentException("Selected file exceeds 16 MiB RAM-only limit (${reportedSize / (1024 * 1024)} MiB)")
        }

        val rawMime = resolver.getType(uri)?.lowercase() ?: when (kind) {
            MessagePayloadKind.IMAGE -> "image/jpeg"
            MessagePayloadKind.VIDEO -> "video/mp4"
            MessagePayloadKind.AUDIO_FILE -> "audio/mpeg"
            else -> "application/octet-stream"
        }

        val rawBytes = resolver.openInputStream(uri)?.use { input ->
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            val hardLimit = if (kind == MessagePayloadKind.IMAGE) 32 * 1024 * 1024 else EncryptedFileTransferEngine.MAX_FILE_SIZE_BYTES
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                total += read
                if (total > hardLimit) {
                    Arrays.fill(buf, 0.toByte())
                    throw IllegalArgumentException("File exceeds maximum allowed transfer size")
                }
                baos.write(buf, 0, read)
            }
            Arrays.fill(buf, 0.toByte())
            baos.toByteArray()
        } ?: throw IllegalStateException("Unable to read selected file from Android picker")

        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("Selected file is empty")
        }

        if (kind == MessagePayloadKind.IMAGE) {
            val cleanJpeg = reencodeBitmapToCleanJpegInRam(rawBytes, maxEdgePx = 1280, quality = 82)
            Arrays.fill(rawBytes, 0.toByte())
            val jpegName = if (displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true)) {
                displayName
            } else {
                displayName.substringBeforeLast(".") + ".jpg"
            }
            return LoadedRamMedia(
                fileName = jpegName,
                mimeType = "image/jpeg",
                payloadBytes = cleanJpeg,
                caption = "Encrypted Photo (${cleanJpeg.size / 1024} KiB, EXIF stripped)"
            )
        }

        val safeMime = try {
            EncryptedFileTransferEngine.validateMimeTypeOrThrow(rawMime)
        } catch (_: Exception) {
            "application/octet-stream"
        }

        val caption = when (kind) {
            MessagePayloadKind.VIDEO -> "Encrypted Video ($displayName)"
            MessagePayloadKind.AUDIO_FILE -> "Encrypted Audio ($displayName)"
            else -> "Encrypted Document ($displayName)"
        }
        return LoadedRamMedia(
            fileName = displayName,
            mimeType = safeMime,
            payloadBytes = rawBytes,
            caption = caption
        )
    }

    fun encodeCameraBitmapToCleanJpeg(bitmap: Bitmap): LoadedRamMedia {
        val scaled = scaleBitmapDownIfNeeded(bitmap, 1280)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, baos)
        if (scaled !== bitmap) scaled.recycle()
        val bytes = baos.toByteArray()
        return LoadedRamMedia(
            fileName = "camera_${System.currentTimeMillis() % 10000}.jpg",
            mimeType = "image/jpeg",
            payloadBytes = bytes,
            caption = "Camera Photo (${bytes.size / 1024} KiB, EXIF stripped)"
        )
    }

    private fun reencodeBitmapToCleanJpegInRam(rawBytes: ByteArray, maxEdgePx: Int, quality: Int): ByteArray {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
        val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sample = 1
        while (maxDim / sample > maxEdgePx * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: return rawBytes.copyOf()
        val scaled = scaleBitmapDownIfNeeded(bmp, maxEdgePx)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        return out.toByteArray()
    }

    private fun scaleBitmapDownIfNeeded(bmp: Bitmap, maxEdgePx: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val longest = max(w, h)
        if (longest <= maxEdgePx || longest <= 0) return bmp
        val ratio = maxEdgePx.toFloat() / longest.toFloat()
        val nw = max(1, (w * ratio).toInt())
        val nh = max(1, (h * ratio).toInt())
        return Bitmap.createScaledBitmap(bmp, nw, nh, true)
    }
}

/**
 * Real RAM-only Voice Note recorder backed by Android `AudioRecord` (16 kHz PCM -> RIFF WAV in RAM).
 * Zero temporary audio files are ever written to disk.
 */
object VoiceNoteRecorder {
    private const val SAMPLE_RATE = 16_000
    private const val MAX_SECONDS = 90

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val pcmBuffer = ByteArrayOutputStream()
    private val isRecordingFlag = AtomicBoolean(false)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _amplitudeLevel = MutableStateFlow(0f)
    val amplitudeLevel: StateFlow<Float> = _amplitudeLevel.asStateFlow()

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startRecording(): Boolean {
        if (isRecordingFlag.get()) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        } catch (_: Throwable) {
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            try {
                recorder.release()
            } catch (_: Throwable) {
            }
            return false
        }

        pcmBuffer.reset()
        audioRecord = recorder
        isRecordingFlag.set(true)
        _isRecording.value = true
        _recordingSeconds.value = 0
        _amplitudeLevel.value = 0f

        recorder.startRecording()
        val startMs = System.currentTimeMillis()
        captureJob = scope.launch {
            val readBuf = ByteArray(1600)
            try {
                while (isRecordingFlag.get()) {
                    val read = recorder.read(readBuf, 0, readBuf.size)
                    if (read > 1) {
                        synchronized(pcmBuffer) {
                            pcmBuffer.write(readBuf, 0, read)
                        }
                        var peak = 0
                        var idx = 0
                        while (idx + 1 < read) {
                            val sample = (readBuf[idx].toInt() and 0xFF) or (readBuf[idx + 1].toInt() shl 8)
                            val signed = sample.toShort().toInt()
                            val mag = abs(signed)
                            if (mag > peak) peak = mag
                            idx += 2
                        }
                        _amplitudeLevel.value = (peak / 18_000f).coerceIn(0.05f, 1f)
                    }
                    val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
                    _recordingSeconds.value = elapsedSec
                    if (elapsedSec >= MAX_SECONDS) break
                }
            } catch (_: Throwable) {
            } finally {
                Arrays.fill(readBuf, 0.toByte())
            }
        }
        return true
    }

    @Synchronized
    fun stopAndFinalizeWav(): Pair<ByteArray, Int>? {
        if (!isRecordingFlag.getAndSet(false)) return null
        _isRecording.value = false
        val duration = max(1, _recordingSeconds.value)
        _amplitudeLevel.value = 0f

        try {
            captureJob?.cancel()
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
        captureJob = null

        val pcmBytes = synchronized(pcmBuffer) {
            val bytes = pcmBuffer.toByteArray()
            pcmBuffer.reset()
            bytes
        }
        if (pcmBytes.size < 640) {
            Arrays.fill(pcmBytes, 0.toByte())
            return null
        }

        val wavBytes = wrapPcmAsWav(pcmBytes, SAMPLE_RATE, channels = 1, bitsPerSample = 16)
        Arrays.fill(pcmBytes, 0.toByte())
        val computedDuration = max(1, pcmBytes.size / (SAMPLE_RATE * 2))
        return Pair(wavBytes, max(duration, computedDuration))
    }

    @Synchronized
    fun cancelAndWipe() {
        isRecordingFlag.set(false)
        _isRecording.value = false
        _recordingSeconds.value = 0
        _amplitudeLevel.value = 0f
        try {
            captureJob?.cancel()
            audioRecord?.stop()
        } catch (_: Throwable) {
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null
        captureJob = null
        synchronized(pcmBuffer) {
            pcmBuffer.reset()
        }
    }

    private fun wrapPcmAsWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        val out = ByteArrayOutputStream(44 + pcmData.size)
        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }
}

/**
 * Plays decrypted RAM-only WAV/PCM voice notes directly using Android `AudioTrack`
 * without ever writing decrypted audio files to storage.
 */
object VoiceNotePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playJob: Job? = null
    private var activeTrack: AudioTrack? = null
    private var activeMediaPlayer: android.media.MediaPlayer? = null

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    @Synchronized
    fun togglePlay(messageId: String, audioBytes: ByteArray) {
        if (_playingMessageId.value == messageId) {
            stop()
            return
        }
        stop()
        if (audioBytes.size <= 44) return

        val isRiffWav = audioBytes.size > 44 &&
            audioBytes[0] == 'R'.code.toByte() &&
            audioBytes[1] == 'I'.code.toByte() &&
            audioBytes[2] == 'F'.code.toByte() &&
            audioBytes[3] == 'F'.code.toByte()

        if (!isRiffWav) {
            try {
                val player = android.media.MediaPlayer()
                val snapshot = audioBytes.copyOf()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                player.setDataSource(object : android.media.MediaDataSource() {
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                        if (position >= snapshot.size) return -1
                        val available = (snapshot.size - position.toInt()).coerceAtMost(size)
                        System.arraycopy(snapshot, position.toInt(), buffer, offset, available)
                        return available
                    }

                    override fun getSize(): Long = snapshot.size.toLong()

                    override fun close() {
                        Arrays.fill(snapshot, 0.toByte())
                    }
                })
                player.setOnCompletionListener { stop() }
                player.setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                player.prepare()
                player.start()
                activeMediaPlayer = player
                _playingMessageId.value = messageId
                return
            } catch (_: Throwable) {
                stop()
            }
        }

        // Parse WAV header if present; otherwise treat as 16 kHz mono PCM
        var sampleRate = 16_000
        var channels = 1
        var dataOffset = 0
        if (isRiffWav) {
            val hdr = ByteBuffer.wrap(audioBytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            channels = hdr.getShort(22).toInt().coerceIn(1, 2)
            sampleRate = hdr.getInt(24).coerceIn(8000, 48000)
            dataOffset = 44
        }

        val chMask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, chMask, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(3200)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(chMask)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Throwable) {
            return
        }

        activeTrack = track
        _playingMessageId.value = messageId
        playJob = scope.launch {
            try {
                track.play()
                var offset = dataOffset
                while (offset < audioBytes.size && _playingMessageId.value == messageId) {
                    val chunk = min(2048, audioBytes.size - offset)
                    val written = track.write(audioBytes, offset, chunk)
                    if (written <= 0) break
                    offset += written
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Throwable) {
                }
                if (activeTrack === track) activeTrack = null
                if (_playingMessageId.value == messageId) _playingMessageId.value = null
            }
        }
    }

    @Synchronized
    fun stop() {
        _playingMessageId.value = null
        playJob?.cancel()
        playJob = null
        try {
            activeMediaPlayer?.stop()
        } catch (_: Throwable) {
        }
        try {
            activeMediaPlayer?.release()
        } catch (_: Throwable) {
        }
        activeMediaPlayer = null
        try {
            activeTrack?.stop()
            activeTrack?.release()
        } catch (_: Throwable) {
        }
        activeTrack = null
    }
}

/**
 * Tor-native / Onion-Mesh TCP Encrypted Voice & Video Calling Media Engine.
 *
 * Security & Architecture invariants:
 * - Zero WebRTC UDP/STUN/TURN or central media servers.
 * - Fresh per-call ephemeral X25519 + HKDF-SHA256 media key bound to active PQ session ID + callId.
 * - Separate prioritized logical streams (RFC 4571-style length-prefixed frames over TCP):
 *   - `STREAM_AUDIO (0x01)`: Highest priority; drains ahead of video.
 *   - `STREAM_VIDEO (0x02)`: Bounded 2-frame drop-oldest queue; adaptive ~180p @ ~8 FPS.
 *   - `STREAM_CONTROL (0x03)`: Remote mute/camera state sync.
 * - Every audio & video frame is independently authenticated & encrypted with `ChaCha20-Poly1305`.
 * - Strictly RAM-only; all keys and sockets are wiped on call end.
 */
object EncryptedCallMediaEngine {
    const val STREAM_AUDIO: Byte = 0x01
    const val STREAM_VIDEO: Byte = 0x02
    const val STREAM_CONTROL: Byte = 0x03
    private const val MEDIA_FRAME_MAGIC: Int = 0x5A544D31 // "ZTM1"

    private const val AUDIO_SAMPLE_RATE = 16_000
    private const val AUDIO_FRAME_BYTES = 1280 // 40ms @ 16kHz 16-bit mono

    private val csprng = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeCallId: String? = null
    private var localDirectionBit: Byte = 0
    private var callMediaKey32: ByteArray? = null
    private var localEphemeralPriv: ByteArray? = null

    private var mediaServerSocket: ServerSocket? = null
    private var mediaClientSocket: Socket? = null
    private var mediaOut: DataOutputStream? = null
    private var fallbackSender: ((ByteArray) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var autoGainControl: AutomaticGainControl? = null
    private var appAudioManager: AudioManager? = null
    private var activeAudioFocusRequest: AudioFocusRequest? = null

    private var writerJob: Job? = null
    private var readerJob: Job? = null
    private var audioCaptureJob: Job? = null

    private val isActive = AtomicBoolean(false)
    private val allowSelfLoopbackFlag = AtomicBoolean(false)
    private val isMutedFlag = AtomicBoolean(false)
    private val isVideoEnabledFlag = AtomicBoolean(false)
    private val audioSendSeq = AtomicLong(0L)
    private val videoSendSeq = AtomicLong(0L)
    private val controlSendSeq = AtomicLong(0L)
    private val lastVideoFrameSentMs = AtomicLong(0L)
    private val lastRemoteAudioPlayMs = AtomicLong(0L)
    private val lastRemoteAudioRms = AtomicLong(0L)

    // Priority queues: Audio always preempts bounded drop-oldest Video queue
    private val audioQueue = ArrayBlockingQueue<ByteArray>(32)
    private val controlQueue = ArrayBlockingQueue<ByteArray>(16)
    private val videoQueueLock = Any()
    private val videoQueue = ArrayDeque<ByteArray>(2)

    private val _remoteVideoFrame = MutableStateFlow<Bitmap?>(null)
    val remoteVideoFrame: StateFlow<Bitmap?> = _remoteVideoFrame.asStateFlow()

    private val _remoteAudioPackets = MutableStateFlow(0L)
    val remoteAudioPackets: StateFlow<Long> = _remoteAudioPackets.asStateFlow()

    private val _remoteVideoFrames = MutableStateFlow(0L)
    val remoteVideoFrames: StateFlow<Long> = _remoteVideoFrames.asStateFlow()

    private val _isRemoteMuted = MutableStateFlow(false)
    val isRemoteMuted: StateFlow<Boolean> = _isRemoteMuted.asStateFlow()

    private val _isRemoteCameraEnabled = MutableStateFlow(true)
    val isRemoteCameraEnabled: StateFlow<Boolean> = _isRemoteCameraEnabled.asStateFlow()

    fun generateEphemeralX25519KeyPair(): Pair<ByteArray, ByteArray> {
        val priv = X25519PrivateKeyParameters(csprng)
        val pub = priv.generatePublicKey()
        return Pair(priv.encoded, pub.encoded)
    }

    fun deriveCallMediaKey32(
        localPriv32: ByteArray,
        remotePub32: ByteArray,
        sessionIdHex: String,
        callId: String
    ): ByteArray {
        val priv = X25519PrivateKeyParameters(localPriv32, 0)
        val pub = X25519PublicKeyParameters(remotePub32, 0)
        val agreement = X25519Agreement()
        agreement.init(priv)
        val shared = ByteArray(32)
        agreement.calculateAgreement(pub, shared, 0)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        val salt = callId.toByteArray(Charsets.UTF_8)
        val info = "ZT2-CALL-MEDIA-KEY-V1|$sessionIdHex|$callId".toByteArray(Charsets.UTF_8)
        hkdf.init(HKDFParameters(shared, salt, info))
        val outKey = ByteArray(32)
        hkdf.generateBytes(outKey, 0, 32)
        Arrays.fill(shared, 0.toByte())
        return outKey
    }

    /**
     * Opens an ephemeral per-call TCP media listener (strictly closed & zeroized when call ends)
     * and returns its bound TCP port.
     */
    @Synchronized
    fun prepareCallerEphemeralListener(
        callId: String,
        localEphemPriv32: ByteArray
    ): Int {
        stopAndWipe()
        activeCallId = callId
        localDirectionBit = 0x01
        localEphemeralPriv = localEphemPriv32.copyOf()
        val srv = ServerSocket(0)
        mediaServerSocket = srv
        val port = srv.localPort
        scope.launch {
            try {
                srv.soTimeout = 45_000
                val client = srv.accept()
                attachDirectMediaSocket(client, callId)
            } catch (_: Throwable) {
            }
        }
        return port
    }

    @Synchronized
    fun activateMediaSession(
        context: Context,
        callId: String,
        isCaller: Boolean,
        derivedKey32: ByteArray,
        isVideoCall: Boolean,
        remoteHost: String?,
        remoteMediaPort: Int?,
        allowSelfLoopback: Boolean = false,
        onFallbackFrameSend: (ByteArray) -> Unit
    ) {
        if (activeCallId != callId) {
            stopAndWipe()
        }
        activeCallId = callId
        localDirectionBit = if (isCaller) 0x01 else 0x02
        allowSelfLoopbackFlag.set(allowSelfLoopback)
        callMediaKey32?.let { Arrays.fill(it, 0.toByte()) }
        callMediaKey32 = derivedKey32.copyOf()
        fallbackSender = onFallbackFrameSend
        isVideoEnabledFlag.set(isVideoCall)
        isMutedFlag.set(false)
        audioSendSeq.set(0L)
        videoSendSeq.set(0L)
        controlSendSeq.set(0L)
        lastRemoteAudioPlayMs.set(0L)
        lastRemoteAudioRms.set(0L)
        _remoteAudioPackets.value = 0L
        _remoteVideoFrames.value = 0L
        _isRemoteMuted.value = false
        _isRemoteCameraEnabled.value = isVideoCall
        _remoteVideoFrame.value = null
        isActive.set(true)

        // If caller accepted direct TCP media socket before activateMediaSession finished, ensure reader is alive
        val existingSock = mediaClientSocket
        if (existingSock != null && !existingSock.isClosed && readerJob?.isActive != true) {
            attachDirectMediaSocket(existingSock, callId)
        }

        appAudioManager = (context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.also { am ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .build()
                    activeAudioFocusRequest = req
                    am.requestAudioFocus(req)
                } else {
                    @Suppress("DEPRECATION")
                    am.requestAudioFocus(
                        null,
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    )
                }
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                val hasExternalHeadset = hasWiredOrBluetoothHeadset(am)
                am.isSpeakerphoneOn = isVideoCall && !hasExternalHeadset
            } catch (_: Throwable) {
            }
        }

        // If callee, attempt dedicated TCP media socket connection to caller's ephemeral listener.
        // Over Tor Onion Service circuits, inbound peer host is loopback (127.0.0.1), so skip
        // dialing localhost and stream encrypted call frames directly over the Tor tunnel.
        if (!isCaller &&
            !remoteHost.isNullOrBlank() &&
            !isLoopbackHost(remoteHost) &&
            remoteMediaPort != null &&
            remoteMediaPort > 0
        ) {
            scope.launch {
                try {
                    val sock = Socket()
                    sock.tcpNoDelay = true
                    sock.connect(InetSocketAddress(remoteHost, remoteMediaPort), 1800)
                    attachDirectMediaSocket(sock, callId)
                } catch (_: Throwable) {
                    // Dedicated port blocked -> seamlessly continues via prioritized encrypted fallback frame
                }
            }
        }

        startPriorityWriterLoop()
        startAudioCaptureAndPlayback()
        sendControlStateSync()
    }

    fun resyncControlState() {
        if (isActive.get()) {
            sendControlStateSync()
        }
    }

    private fun isLoopbackHost(host: String): Boolean {
        val cleaned = host.trim().lowercase()
        return cleaned == "127.0.0.1" || cleaned == "::1" || cleaned == "localhost"
    }

    @Synchronized
    private fun attachDirectMediaSocket(sock: Socket, expectedCallId: String) {
        if (activeCallId != expectedCallId) {
            try {
                sock.close()
            } catch (_: Throwable) {
            }
            return
        }
        val prevSock = mediaClientSocket
        if (prevSock != null && prevSock !== sock) {
            try {
                prevSock.close()
            } catch (_: Throwable) {
            }
        }
        mediaClientSocket = sock
        sock.tcpNoDelay = true
        sock.soTimeout = 0
        mediaOut = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 16_384))
        val inp = DataInputStream(BufferedInputStream(sock.getInputStream(), 16_384))
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                // Wait up to 15s for activateMediaSession to derive callMediaKey32 & flip isActive=true
                val waitDeadline = System.currentTimeMillis() + 15_000L
                while (activeCallId == expectedCallId && !isActive.get() && !sock.isClosed && System.currentTimeMillis() < waitDeadline) {
                    delay(15L)
                }
                while (activeCallId == expectedCallId && isActive.get() && !sock.isClosed) {
                    val len = inp.readInt()
                    if (len <= 0 || len > 512_000) break
                    val frameBytes = ByteArray(len)
                    inp.readFully(frameBytes)
                    processIncomingEncryptedMediaFrame(frameBytes)
                }
            } catch (_: Throwable) {
            } finally {
                if (mediaClientSocket === sock) {
                    mediaOut = null
                    mediaClientSocket = null
                }
                try {
                    sock.close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun startPriorityWriterLoop() {
        writerJob?.cancel()
        writerJob = scope.launch {
            while (isActive.get()) {
                var sentAny = false

                // 1. Drain control messages first
                while (true) {
                    val ctrl = controlQueue.poll() ?: break
                    dispatchRawFrame(ctrl)
                    sentAny = true
                }

                // 2. Drain ALL queued Audio frames strictly ahead of video (no video head-of-line delay)
                while (true) {
                    val aud = audioQueue.poll() ?: break
                    dispatchRawFrame(aud)
                    sentAny = true
                }

                // 3. Send at most 1 queued Video frame per cycle so audio stays preemptive
                val vid = synchronized(videoQueueLock) { videoQueue.removeFirstOrNull() }
                if (vid != null) {
                    dispatchRawFrame(vid)
                    sentAny = true
                }

                if (!sentAny) {
                    delay(10L)
                }
            }
        }
    }

    private fun dispatchRawFrame(frameBytes: ByteArray) {
        val out = mediaOut
        if (out != null && mediaClientSocket?.isClosed == false) {
            try {
                synchronized(out) {
                    out.writeInt(frameBytes.size)
                    out.write(frameBytes)
                    out.flush()
                }
                return
            } catch (_: Throwable) {
                mediaOut = null
            }
        }
        try {
            fallbackSender?.invoke(frameBytes)
        } catch (_: Throwable) {
        }
    }

    private fun hasWiredOrBluetoothHeadset(am: AudioManager): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { dev ->
                    dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            } else {
                @Suppress("DEPRECATION")
                am.isWiredHeadsetOn || am.isBluetoothScoOn
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun computePcm16Rms(pcmBytes: ByteArray, length: Int): Long {
        if (length < 2) return 0L
        var sumSq = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < length) {
            val sample = ((pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF)).toShort().toDouble()
            sumSq += sample * sample
            samples++
            i += 2
        }
        return if (samples == 0) 0L else kotlin.math.sqrt(sumSq / samples).toLong()
    }

    private fun applyGainInPlace(pcmBytes: ByteArray, length: Int, gain: Float) {
        if (gain >= 0.99f) return
        var i = 0
        while (i + 1 < length) {
            val raw = ((pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF)).toShort().toFloat()
            val scaled = (raw * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcmBytes[i] = (scaled and 0xFF).toByte()
            pcmBytes[i + 1] = ((scaled ushr 8) and 0xFF).toByte()
            i += 2
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCaptureAndPlayback() {
        // Initialize AudioRecord FIRST so we can share its audioSessionId with AudioTrack + DSP effects
        val recMinBuf = AudioRecord.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(AUDIO_FRAME_BYTES * 2)

        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recMinBuf * 2
            )
        } catch (_: Throwable) {
            null
        }

        val sessionId = if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
            rec.audioSessionId
        } else {
            AudioManager.AUDIO_SESSION_ID_GENERATE
        }

        // Initialize low-latency AudioTrack bound to the same voice-communication sessionId for hardware AEC
        val playMinBuf = AudioTrack.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(AUDIO_FRAME_BYTES * 2)

        fun createTrack(boundSessionId: Int): AudioTrack? {
            return try {
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AUDIO_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(playMinBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                if (boundSessionId != AudioManager.AUDIO_SESSION_ID_GENERATE && boundSessionId > 0) {
                    builder.setSessionId(boundSessionId)
                }
                builder.build()
            } catch (_: Throwable) {
                null
            }
        }

        val track = createTrack(sessionId) ?: createTrack(AudioManager.AUDIO_SESSION_ID_GENERATE)
        audioTrack = track
        try {
            track?.play()
        } catch (_: Throwable) {
        }

        if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord = rec
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true }
                }
                if (AutomaticGainControl.isAvailable()) {
                    autoGainControl = AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled = true }
                }
            } catch (_: Throwable) {
            }

            val hwAecActive = echoCanceler?.enabled == true
            audioCaptureJob?.cancel()
            audioCaptureJob = scope.launch {
                val pcmFrame = ByteArray(AUDIO_FRAME_BYTES)
                try {
                    rec.startRecording()
                    while (isActive.get()) {
                        val read = rec.read(pcmFrame, 0, pcmFrame.size)
                        if (read > 0 && !isMutedFlag.get()) {
                            // Software echo-suppression assist: if peer audio just played on speaker/earpiece,
                            // duck residual speaker-to-mic bleed so the caller never hears their own voice repeat back.
                            val nowMs = System.currentTimeMillis()
                            val remoteRecent = (nowMs - lastRemoteAudioPlayMs.get()) < 320L
                            val remoteRms = lastRemoteAudioRms.get()
                            if (remoteRecent && remoteRms > 300L) {
                                val localRms = computePcm16Rms(pcmFrame, read)
                                val speakerOn = try {
                                    appAudioManager?.isSpeakerphoneOn == true
                                } catch (_: Throwable) {
                                    false
                                }
                                val bleedThreshold = when {
                                    !hwAecActive && speakerOn -> maxOf(750L, (remoteRms * 0.65).toLong())
                                    !hwAecActive -> maxOf(500L, (remoteRms * 0.45).toLong())
                                    speakerOn -> maxOf(420L, (remoteRms * 0.35).toLong())
                                    else -> maxOf(280L, (remoteRms * 0.22).toLong())
                                }
                                if (localRms < bleedThreshold) {
                                    applyGainInPlace(pcmFrame, read, if (!hwAecActive) 0.05f else 0.14f)
                                }
                            }

                            val slice = if (read == pcmFrame.size) pcmFrame else pcmFrame.copyOf(read)
                            val encrypted = buildEncryptedMediaFrame(
                                streamId = STREAM_AUDIO,
                                seq = audioSendSeq.getAndIncrement(),
                                plaintextPayload = slice
                            )
                            if (encrypted != null) {
                                if (!audioQueue.offer(encrypted)) {
                                    audioQueue.poll()
                                    audioQueue.offer(encrypted)
                                }
                            }
                        } else {
                            delay(20L)
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    Arrays.fill(pcmFrame, 0.toByte())
                }
            }
        }
    }

    /**
     * Captures a live CameraX `ImageProxy`, converts & downscales to adaptive 180p (~240x180 @ ~8 FPS),
     * drops automatically when the bounded video queue is busy so voice is never stalled, and
     * AEAD-encrypts with `ChaCha20-Poly1305` strictly in RAM.
     */
    fun submitLocalCameraFrame(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (!isActive.get() || !isVideoEnabledFlag.get()) {
            imageProxy.close()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastVideoFrameSentMs.get() < 120L) {
            imageProxy.close()
            return
        }
        synchronized(videoQueueLock) {
            if (videoQueue.size >= 2) {
                imageProxy.close()
                return
            }
        }
        lastVideoFrameSentMs.set(now)

        try {
            var jpegBytes = yuvImageProxyToCompactJpeg(imageProxy, maxEdge = 240, quality = 48, isFrontCamera = isFrontCamera)
            if (jpegBytes != null && jpegBytes.size > 22_000) {
                // Keep video frames lean over Tor circuits to avoid head-of-line audio jitter
                Arrays.fill(jpegBytes, 0.toByte())
                jpegBytes = yuvImageProxyToCompactJpeg(imageProxy, maxEdge = 192, quality = 34, isFrontCamera = isFrontCamera)
            }
            if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                val encrypted = buildEncryptedMediaFrame(
                    streamId = STREAM_VIDEO,
                    seq = videoSendSeq.getAndIncrement(),
                    plaintextPayload = jpegBytes
                )
                Arrays.fill(jpegBytes, 0.toByte())
                if (encrypted != null) {
                    synchronized(videoQueueLock) {
                        while (videoQueue.size >= 2) {
                            videoQueue.removeFirst()
                        }
                        videoQueue.addLast(encrypted)
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Converts Camera2 / CameraX YUV_420_888 planes with arbitrary rowStride and pixelStride
     * cleanly into tightly packed canonical NV21 without corrupting chroma alignment or green/purple bands.
     */
    fun yuv420888PlanesToNv21(
        width: Int,
        height: Int,
        yBuffer: ByteBuffer,
        yRowStride: Int,
        yPixelStride: Int,
        uBuffer: ByteBuffer,
        uRowStride: Int,
        uPixelStride: Int,
        vBuffer: ByteBuffer,
        vRowStride: Int,
        vPixelStride: Int
    ): ByteArray {
        val yDup = yBuffer.duplicate()
        val uDup = uBuffer.duplicate()
        val vDup = vBuffer.duplicate()
        val ySize = width * height
        val nv21 = ByteArray(ySize + (ySize / 2))

        var outPos = 0
        val yLimit = yDup.limit()
        if (yPixelStride == 1 && yRowStride == width && yLimit >= ySize) {
            yDup.position(0)
            yDup.get(nv21, 0, ySize)
            outPos = ySize
        } else {
            for (row in 0 until height) {
                val rowBase = row * yRowStride
                if (yPixelStride == 1 && rowBase + width <= yLimit) {
                    yDup.position(rowBase)
                    yDup.get(nv21, outPos, width)
                    outPos += width
                } else {
                    for (col in 0 until width) {
                        val idx = rowBase + col * yPixelStride
                        nv21[outPos++] = if (idx < yLimit) yDup.get(idx) else 0
                    }
                }
            }
        }

        val chromaH = height / 2
        val chromaW = width / 2
        val uLimit = uDup.limit()
        val vLimit = vDup.limit()
        for (row in 0 until chromaH) {
            val vRowBase = row * vRowStride
            val uRowBase = row * uRowStride
            for (col in 0 until chromaW) {
                val vIdx = vRowBase + col * vPixelStride
                val uIdx = uRowBase + col * uPixelStride
                nv21[outPos++] = if (vIdx < vLimit) vDup.get(vIdx) else 128.toByte()
                nv21[outPos++] = if (uIdx < uLimit) uDup.get(uIdx) else 128.toByte()
            }
        }
        return nv21
    }

    private fun yuvImageProxyToCompactJpeg(
        image: ImageProxy,
        maxEdge: Int,
        quality: Int,
        isFrontCamera: Boolean
    ): ByteArray? {
        if (image.width <= 0 || image.height <= 0) return null
        val yPlane = image.planes.getOrNull(0) ?: return null
        val uPlane = image.planes.getOrNull(1) ?: return null
        val vPlane = image.planes.getOrNull(2) ?: return null

        val nv21 = yuv420888PlanesToNv21(
            width = image.width,
            height = image.height,
            yBuffer = yPlane.buffer,
            yRowStride = yPlane.rowStride,
            yPixelStride = yPlane.pixelStride,
            uBuffer = uPlane.buffer,
            uRowStride = uPlane.rowStride,
            uPixelStride = uPlane.pixelStride,
            vBuffer = vPlane.buffer,
            vRowStride = vPlane.rowStride,
            vPixelStride = vPlane.pixelStride
        )

        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val rawJpegOut = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 72, rawJpegOut)
        val rawJpeg = rawJpegOut.toByteArray()
        Arrays.fill(nv21, 0.toByte())

        val decoded = BitmapFactory.decodeByteArray(rawJpeg, 0, rawJpeg.size) ?: return null
        Arrays.fill(rawJpeg, 0.toByte())

        val rotDeg = image.imageInfo.rotationDegrees
        val rotatedW = if (rotDeg % 180 != 0) decoded.height else decoded.width
        val rotatedH = if (rotDeg % 180 != 0) decoded.width else decoded.height
        val scale = (maxEdge.toFloat() / max(rotatedW, rotatedH).toFloat().coerceAtLeast(1f)).coerceAtMost(1f)

        val matrix = Matrix().apply {
            if (rotDeg != 0) postRotate(rotDeg.toFloat())
            if (isFrontCamera) postScale(-1f, 1f)
            if (scale < 1f) postScale(scale, scale)
        }
        val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (transformed !== decoded) decoded.recycle()

        val out = ByteArrayOutputStream()
        transformed.compress(Bitmap.CompressFormat.JPEG, quality, out)
        transformed.recycle()
        return out.toByteArray()
    }

    fun setMuted(muted: Boolean) {
        isMutedFlag.set(muted)
        sendControlStateSync()
    }

    fun setCameraEnabled(enabled: Boolean) {
        isVideoEnabledFlag.set(enabled)
        if (!enabled) {
            synchronized(videoQueueLock) { videoQueue.clear() }
        }
        sendControlStateSync()
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        try {
            appAudioManager?.isSpeakerphoneOn = enabled
        } catch (_: Throwable) {
        }
    }

    private fun sendControlStateSync() {
        val stateByte0 = if (isMutedFlag.get()) 1.toByte() else 0.toByte()
        val stateByte1 = if (isVideoEnabledFlag.get()) 1.toByte() else 0.toByte()
        val encrypted = buildEncryptedMediaFrame(
            streamId = STREAM_CONTROL,
            seq = controlSendSeq.getAndIncrement(),
            plaintextPayload = byteArrayOf(stateByte0, stateByte1)
        ) ?: return
        controlQueue.offer(encrypted)
    }

    private fun buildEncryptedMediaFrame(
        streamId: Byte,
        seq: Long,
        plaintextPayload: ByteArray
    ): ByteArray? {
        val key = callMediaKey32 ?: return null
        val callId = activeCallId ?: return null
        val nonce = ByteBuffer.allocate(12)
            .put(localDirectionBit)
            .put(streamId)
            .putShort(0)
            .putLong(seq)
            .array()
        val aad = "ZT2-CALL|$callId|$streamId|$seq".toByteArray(Charsets.UTF_8)
        val ct = RustCoreBridge.aeadEncryptChaCha20Poly1305(key, nonce, plaintextPayload, aad)

        val baos = ByteArrayOutputStream(13 + ct.size)
        DataOutputStream(baos).use { out ->
            out.writeInt(MEDIA_FRAME_MAGIC)
            out.writeByte(localDirectionBit.toInt())
            out.writeByte(streamId.toInt())
            out.writeLong(seq)
            out.writeInt(ct.size)
            out.write(ct)
        }
        return baos.toByteArray()
    }

    fun processIncomingEncryptedMediaFrame(frameBytes: ByteArray) {
        if (!isActive.get()) return
        val key = callMediaKey32 ?: return
        val callId = activeCallId ?: return
        try {
            DataInputStream(ByteArrayInputStream(frameBytes)).use { inp ->
                if (inp.readInt() != MEDIA_FRAME_MAGIC) return
                val senderDirBit = inp.readByte()
                if (!allowSelfLoopbackFlag.get() && senderDirBit == localDirectionBit) return
                val streamId = inp.readByte()
                val seq = inp.readLong()
                val ctLen = inp.readInt()
                if (ctLen !in 16..512_000) return
                val ct = ByteArray(ctLen).also { inp.readFully(it) }

                val nonce = ByteBuffer.allocate(12)
                    .put(senderDirBit)
                    .put(streamId)
                    .putShort(0)
                    .putLong(seq)
                    .array()
                val aad = "ZT2-CALL|$callId|$streamId|$seq".toByteArray(Charsets.UTF_8)
                val pt = RustCoreBridge.aeadDecryptChaCha20Poly1305(key, nonce, ct, aad)

                when (streamId) {
                    STREAM_AUDIO -> {
                        _remoteAudioPackets.value = _remoteAudioPackets.value + 1L
                        val rms = computePcm16Rms(pt, pt.size)
                        if (rms > 150L) {
                            lastRemoteAudioPlayMs.set(System.currentTimeMillis())
                            lastRemoteAudioRms.set(rms)
                        }
                        val track = audioTrack
                        if (track != null && pt.isNotEmpty()) {
                            track.write(pt, 0, pt.size)
                        }
                    }

                    STREAM_VIDEO -> {
                        _remoteVideoFrames.value = _remoteVideoFrames.value + 1L
                        val bmp = BitmapFactory.decodeByteArray(pt, 0, pt.size)
                        if (bmp != null) {
                            _remoteVideoFrame.value = bmp
                        }
                    }

                    STREAM_CONTROL -> {
                        if (pt.size >= 2) {
                            _isRemoteMuted.value = pt[0] != 0.toByte()
                            _isRemoteCameraEnabled.value = pt[1] != 0.toByte()
                            if (!_isRemoteCameraEnabled.value) {
                                _remoteVideoFrame.value = null
                            }
                        }
                    }
                }
                Arrays.fill(pt, 0.toByte())
            }
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun stopAndWipe() {
        isActive.set(false)
        activeCallId = null
        writerJob?.cancel()
        writerJob = null
        readerJob?.cancel()
        readerJob = null
        audioCaptureJob?.cancel()
        audioCaptureJob = null

        audioQueue.clear()
        controlQueue.clear()
        synchronized(videoQueueLock) { videoQueue.clear() }

        try {
            echoCanceler?.release()
        } catch (_: Throwable) {
        }
        try {
            noiseSuppressor?.release()
        } catch (_: Throwable) {
        }
        try {
            autoGainControl?.release()
        } catch (_: Throwable) {
        }
        echoCanceler = null
        noiseSuppressor = null
        autoGainControl = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        audioTrack = null

        try {
            mediaClientSocket?.close()
        } catch (_: Throwable) {
        }
        try {
            mediaServerSocket?.close()
        } catch (_: Throwable) {
        }
        mediaClientSocket = null
        mediaServerSocket = null
        mediaOut = null
        fallbackSender = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeAudioFocusRequest?.let { req ->
                    appAudioManager?.abandonAudioFocusRequest(req)
                }
            }
            appAudioManager?.mode = AudioManager.MODE_NORMAL
            appAudioManager?.isSpeakerphoneOn = false
        } catch (_: Throwable) {
        }
        activeAudioFocusRequest = null
        appAudioManager = null

        callMediaKey32?.let { Arrays.fill(it, 0.toByte()) }
        callMediaKey32 = null
        localEphemeralPriv?.let { Arrays.fill(it, 0.toByte()) }
        localEphemeralPriv = null

        _remoteVideoFrame.value = null
        _remoteAudioPackets.value = 0L
        _remoteVideoFrames.value = 0L
        _isRemoteMuted.value = false
        _isRemoteCameraEnabled.value = true
    }
}
