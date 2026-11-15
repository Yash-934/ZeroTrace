package com.zerotrace.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.Window
import android.view.WindowManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enforces window screenshot protection (`FLAG_SECURE`), sensitive clipboard scrubbing,
 * and informational device-integrity risk checks.
 *
 * Streaming Emulator Note:
 * Android's SurfaceFlinger blanks out any window with `FLAG_SECURE` when streaming over a
 * virtual display / WebRTC emulator preview, producing a solid black screen between the status
 * bar and navigation bar. We detect emulator hardware (`ranchu`/`goldfish`/`sdk_gphone`) so the
 * UI remains visible in the browser emulator preview while enabling `FLAG_SECURE` by default on
 * physical hardware (and allowing manual toggling in Settings).
 */
object SecureWindowManager {

    private var clipboardClearJob: Job? = null

    fun isRunningOnEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            hardware.contains("ranchu") ||
            hardware.contains("goldfish") ||
            product.contains("sdk") ||
            product.contains("emulator")
    }

    /**
     * Applies or clears `FLAG_SECURE` on the activity [window].
     */
    fun setSecureFlag(window: Window, enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    fun applySecureFlag(window: Window) {
        setSecureFlag(window, true)
    }

    /**
     * Checks whether `FLAG_SECURE` is currently active on the window.
     */
    fun isSecureFlagActive(window: Window): Boolean {
        return (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    }

    /**
     * Copies a public value (such as `.onion` address or fingerprint) with `EXTRA_IS_SENSITIVE` masked
     * preview on Android 13+ and schedules an automatic clipboard wipe after [autoClearSeconds].
     */
    fun copyWithAutoScrub(
        context: Context,
        label: String,
        value: String,
        scope: CoroutineScope,
        autoClearSeconds: Long = 45L
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        } else {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)

        clipboardClearJob?.cancel()
        clipboardClearJob = scope.launch(Dispatchers.Main) {
            delay(autoClearSeconds * 1000L)
            scrubClipboardNow(context)
        }
    }

    fun scrubClipboardNow(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    /**
     * Informational Device Integrity Check (Section 5.4):
     * Reports elevated risk indicators if common root binaries or test-keys build tags exist.
     * Never claims that passing this check guarantees immunity from kernel compromise.
     */
    fun evaluateDeviceIntegrityIndicators(): DeviceIntegrityReport {
        val indicators = mutableListOf<String>()
        val buildTags = Build.TAGS ?: ""
        if (buildTags.contains("test-keys")) {
            indicators.add("OS signed with test-keys")
        }
        val suPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su"
        )
        if (suPaths.any { File(it).exists() }) {
            indicators.add("Superuser binary detected in system path")
        }
        return DeviceIntegrityReport(
            hasElevatedRiskIndicators = indicators.isNotEmpty(),
            indicators = indicators
        )
    }
}

data class DeviceIntegrityReport(
    val hasElevatedRiskIndicators: Boolean,
    val indicators: List<String>
)
