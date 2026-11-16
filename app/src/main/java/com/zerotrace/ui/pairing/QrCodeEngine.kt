package com.zerotrace.ui.pairing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap
import java.util.concurrent.Executors

/**
 * Deterministic local QR matrix encoder, Gallery Bitmap/Screenshot decoder,
 * & CameraX YUV frame analyzer using ZXing Core.
 * Requires zero Play Services or network calls.
 */
object QrCodeEngine {

    /**
     * Generates a high-contrast Black-on-White QR bitmap with optimal module sizing
     * (`ErrorCorrectionLevel.L`, `MARGIN = 2`) so older devices with low-resolution cameras
     * or weak auto-focus can scan the 11-field `ZT2|v1` payload rapidly, and gallery screenshots
     * decode reliably.
     */
    fun encodeQrAndroidBitmap(payload: String, sizePx: Int = 680): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
            put(EncodeHintType.MARGIN, 2)
        }
        val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        val darkPixel = Color.BLACK
        val lightPixel = Color.WHITE

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) darkPixel else lightPixel
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun encodeQrBitmap(payload: String, sizePx: Int = 680): ImageBitmap {
        return encodeQrAndroidBitmap(payload, sizePx).asImageBitmap()
    }

    private fun createReader(): MultiFormatReader {
        return MultiFormatReader().apply {
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
            hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
            hints[DecodeHintType.TRY_HARDER] = java.lang.Boolean.TRUE
            setHints(hints)
        }
    }

    private fun decodeFromSource(reader: MultiFormatReader, source: LuminanceSource): String? {
        try {
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
        }
        try {
            reader.reset()
            return reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source))).text
        } catch (_: Exception) {
        }
        try {
            reader.reset()
            val inverted = source.invert()
            return reader.decodeWithState(BinaryBitmap(HybridBinarizer(inverted))).text
        } catch (_: Exception) {
        }
        return null
    }

    fun decodeLumaBuffer(
        yPlaneBytes: ByteArray,
        width: Int,
        height: Int
    ): String? {
        val reader = createReader()
        return try {
            val source = PlanarYUVLuminanceSource(
                yPlaneBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            decodeFromSource(reader, source) ?: run {
                // Fallback: rotate 90 degrees for portrait camera sensors on older devices
                val rotated = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[x * height + (height - y - 1)] = yPlaneBytes[y * width + x]
                    }
                }
                val rotatedSource = PlanarYUVLuminanceSource(
                    rotated,
                    height,
                    width,
                    0,
                    0,
                    height,
                    width,
                    false
                )
                decodeFromSource(reader, rotatedSource)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes a QR code from an Android [Bitmap] (including full-screen phone screenshots
     * where the QR code occupies only a central card of the screen).
     */
    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val reader = createReader()
        val candidates = mutableListOf<Bitmap>()
        candidates.add(bitmap)

        // If the image is a high-resolution screenshot, also add a scaled version and center-crops
        if (bitmap.width > 1200 || bitmap.height > 1200) {
            val scale = 1000f / maxOf(bitmap.width, bitmap.height)
            val scaledW = (bitmap.width * scale).toInt().coerceAtLeast(120)
            val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(120)
            try {
                candidates.add(Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true))
            } catch (_: Exception) {
            }
        }

        // Center crop windows for full-screen screenshots where the QR is in the middle/upper half
        if (bitmap.width >= 300 && bitmap.height >= 400) {
            try {
                val cropW = (bitmap.width * 0.85f).toInt()
                val cropH = (bitmap.height * 0.65f).toInt()
                val startX = (bitmap.width - cropW) / 2
                val startY = (bitmap.height * 0.12f).toInt().coerceAtMost(bitmap.height - cropH)
                candidates.add(Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH))
            } catch (_: Exception) {
            }
        }

        for (candidate in candidates) {
            val w = candidate.width
            val h = candidate.height
            if (w <= 0 || h <= 0) continue
            try {
                val pixels = IntArray(w * h)
                candidate.getPixels(pixels, 0, w, 0, 0, w, h)
                val source = RGBLuminanceSource(w, h, pixels)
                val text = decodeFromSource(reader, source)
                if (!text.isNullOrBlank()) {
                    return text.trim()
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Decodes a QR code from a content [Uri] chosen via the zero-permission Android Photo Picker.
     */
    fun decodeQrFromUri(context: Context, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri) ?: return null
            boundsStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }

            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxSide > 0 && maxSide / sample > 2048) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null
            decodeQrFromBitmap(bitmap)
        } catch (_: Throwable) {
            null
        }
    }
}

@Composable
fun CameraXQrScannerPreview(
    onQrDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { imageProxy: ImageProxy ->
                                    try {
                                        val plane = imageProxy.planes.firstOrNull()
                                        if (plane != null) {
                                            val width = imageProxy.width
                                            val height = imageProxy.height
                                            val rowStride = plane.rowStride
                                            val pixelStride = plane.pixelStride
                                            val buffer = plane.buffer
                                            buffer.rewind()
                                            val yBytes = ByteArray(width * height)
                                            if (rowStride == width && pixelStride == 1) {
                                                buffer.get(yBytes, 0, minOf(buffer.remaining(), yBytes.size))
                                            } else {
                                                val rowBuffer = ByteArray(rowStride)
                                                for (row in 0 until height) {
                                                    val bytesToRead = if (row == height - 1) {
                                                        minOf(rowStride, buffer.remaining())
                                                    } else {
                                                        rowStride
                                                    }
                                                    if (buffer.remaining() < bytesToRead) break
                                                    buffer.get(rowBuffer, 0, bytesToRead)
                                                    if (pixelStride == 1) {
                                                        System.arraycopy(rowBuffer, 0, yBytes, row * width, minOf(width, bytesToRead))
                                                    } else {
                                                        for (col in 0 until width) {
                                                            val idx = col * pixelStride
                                                            if (idx < bytesToRead) {
                                                                yBytes[row * width + col] = rowBuffer[idx]
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            val decoded = QrCodeEngine.decodeLumaBuffer(
                                                yBytes,
                                                width,
                                                height
                                            )
                                            if (decoded != null && decoded.startsWith("ZT2|")) {
                                                onQrDetected(decoded)
                                            }
                                        }
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) {
                        // Camera hardware unavailable in headless container or denied
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        Box(
            modifier = Modifier
                .size(210.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp)
                )
        )

        Text(
            text = "ALIGN ZT2|v1 PAIRING QR IN FRAME",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
