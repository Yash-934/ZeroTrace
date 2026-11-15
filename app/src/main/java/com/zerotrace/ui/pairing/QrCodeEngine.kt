package com.zerotrace.ui.pairing

import android.graphics.Bitmap
import android.graphics.Color
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
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap
import java.util.concurrent.Executors

/**
 * Deterministic local QR matrix encoder & CameraX YUV frame analyzer using ZXing Core.
 * Requires zero Play Services or network calls.
 */
object QrCodeEngine {

    fun encodeQrBitmap(payload: String, sizePx: Int = 640): ImageBitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 1)
        }
        val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        val darkPixel = Color.rgb(9, 13, 16) // Charcoal obsidian
        val lightPixel = Color.rgb(0, 230, 118) // ZeroTrace high-contrast emerald

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) darkPixel else lightPixel
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap.asImageBitmap()
    }

    fun decodeLumaBuffer(
        yPlaneBytes: ByteArray,
        width: Int,
        height: Int
    ): String? {
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
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply {
                val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
                hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
                hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
                setHints(hints)
            }
            reader.decodeWithState(binaryBitmap).text
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun CameraXQrScannerPreview(
    onQrDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
                                            val buffer = plane.buffer
                                            val bytes = ByteArray(buffer.remaining())
                                            buffer.get(bytes)
                                            val decoded = QrCodeEngine.decodeLumaBuffer(
                                                bytes,
                                                imageProxy.width,
                                                imageProxy.height
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
