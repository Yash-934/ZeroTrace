package com.zerotrace.media

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Prevents MainActivity.onStop() from prematurely locking the UI or unmounting dialogs/launchers
 * while an intentional in-app Android Photo Picker / Gallery / Document chooser / Share sheet is open.
 */
object ExternalPickerGuard {
    private val inFlight = AtomicBoolean(false)
    private val armedAtMs = AtomicLong(0L)

    fun markLaunchingExternalPicker() {
        armedAtMs.set(System.currentTimeMillis())
        inFlight.set(true)
    }

    fun isExternalPickerInFlight(): Boolean {
        if (!inFlight.get()) return false
        val ageMs = System.currentTimeMillis() - armedAtMs.get()
        if (ageMs > 10 * 60_000L) {
            inFlight.set(false)
            return false
        }
        return true
    }

    fun clearOnPickerResult() {
        inFlight.set(false)
    }

    fun onHostActivityResumed() {
        inFlight.set(false)
    }
}

class SafeMediaPickerController(
    private val context: Context,
    private val launchPhotoPicker: () -> Boolean,
    private val launchGetContentImage: () -> Boolean,
    private val launchOpenDocImage: () -> Boolean,
    private val launchVideoPicker: () -> Boolean,
    private val launchGetContentVideo: () -> Boolean,
    private val launchOpenDocVideo: () -> Boolean,
    private val launchOpenDocGeneric: (Array<String>) -> Boolean,
    private val launchGetContentGeneric: (String) -> Boolean
) {
    fun pickImage() {
        ExternalPickerGuard.markLaunchingExternalPicker()
        if (launchPhotoPicker()) return
        if (launchGetContentImage()) return
        if (launchOpenDocImage()) return
        ExternalPickerGuard.clearOnPickerResult()
        Toast.makeText(
            context,
            "No gallery or photo picker app available on this device.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun pickVideo() {
        ExternalPickerGuard.markLaunchingExternalPicker()
        if (launchVideoPicker()) return
        if (launchGetContentVideo()) return
        if (launchOpenDocVideo()) return
        ExternalPickerGuard.clearOnPickerResult()
        Toast.makeText(
            context,
            "No video picker app available on this device.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun pickDocument(mimeTypes: Array<String> = arrayOf("*/*"), fallbackMime: String = "*/*") {
        ExternalPickerGuard.markLaunchingExternalPicker()
        if (launchOpenDocGeneric(mimeTypes)) return
        if (launchGetContentGeneric(fallbackMime)) return
        ExternalPickerGuard.clearOnPickerResult()
        Toast.makeText(
            context,
            "No file manager app available on this device.",
            Toast.LENGTH_LONG
        ).show()
    }
}

private enum class ActiveSafePickerKind {
    NONE,
    IMAGE,
    VIDEO,
    DOCUMENT,
    AUDIO
}

/**
 * Crash-proof, zero-permission media/file picker helper.
 * Primary: [ActivityResultContracts.PickVisualMedia] (Android Photo Picker).
 * Fallback 1: [ActivityResultContracts.GetContent] (works on all MIUI/ColorOS/Vivo/AOSP Gallery apps).
 * Fallback 2: [ActivityResultContracts.OpenDocument] (SAF chooser).
 * All Intent creations and `.launch()` calls are verified and guarded against ActivityNotFoundException / SecurityException.
 */
@Composable
fun rememberSafeMediaPicker(
    onImagePicked: (Uri) -> Unit = {},
    onVideoPicked: (Uri) -> Unit = {},
    onDocumentPicked: (Uri) -> Unit = {},
    onAudioPicked: (Uri) -> Unit = {}
): SafeMediaPickerController {
    val context = LocalContext.current
    val latestOnImage by rememberUpdatedState(onImagePicked)
    val latestOnVideo by rememberUpdatedState(onVideoPicked)
    val latestOnDocument by rememberUpdatedState(onDocumentPicked)
    val latestOnAudio by rememberUpdatedState(onAudioPicked)

    var pendingKind by remember { mutableStateOf(ActiveSafePickerKind.NONE) }

    fun deliverUri(kind: ActiveSafePickerKind, uri: Uri?) {
        ExternalPickerGuard.clearOnPickerResult()
        if (uri == null) return
        when (kind) {
            ActiveSafePickerKind.IMAGE -> latestOnImage(uri)
            ActiveSafePickerKind.VIDEO -> latestOnVideo(uri)
            ActiveSafePickerKind.AUDIO -> latestOnAudio(uri)
            ActiveSafePickerKind.DOCUMENT, ActiveSafePickerKind.NONE -> latestOnDocument(uri)
        }
    }

    val photoVisualLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        deliverUri(ActiveSafePickerKind.IMAGE, uri)
    }

    val videoVisualLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        deliverUri(ActiveSafePickerKind.VIDEO, uri)
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        deliverUri(pendingKind, uri)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        deliverUri(pendingKind, uri)
    }

    return remember(context) {
        SafeMediaPickerController(
            context = context,
            launchPhotoPicker = {
                try {
                    val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    val contract = ActivityResultContracts.PickVisualMedia()
                    val intent = contract.createIntent(context, req)
                    val resolvable = intent.resolveActivity(context.packageManager) != null
                    if (resolvable) {
                        pendingKind = ActiveSafePickerKind.IMAGE
                        photoVisualLauncher.launch(req)
                        true
                    } else {
                        false
                    }
                } catch (_: Throwable) {
                    false
                }
            },
            launchGetContentImage = {
                try {
                    pendingKind = ActiveSafePickerKind.IMAGE
                    getContentLauncher.launch("image/*")
                    true
                } catch (_: Throwable) {
                    false
                }
            },
            launchOpenDocImage = {
                try {
                    pendingKind = ActiveSafePickerKind.IMAGE
                    openDocumentLauncher.launch(arrayOf("image/*"))
                    true
                } catch (_: Throwable) {
                    false
                }
            },
            launchVideoPicker = {
                try {
                    val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    val contract = ActivityResultContracts.PickVisualMedia()
                    val intent = contract.createIntent(context, req)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        pendingKind = ActiveSafePickerKind.VIDEO
                        videoVisualLauncher.launch(req)
                        true
                    } else {
                        false
                    }
                } catch (_: Throwable) {
                    false
                }
            },
            launchGetContentVideo = {
                try {
                    pendingKind = ActiveSafePickerKind.VIDEO
                    getContentLauncher.launch("video/*")
                    true
                } catch (_: Throwable) {
                    false
                }
            },
            launchOpenDocVideo = {
                try {
                    pendingKind = ActiveSafePickerKind.VIDEO
                    openDocumentLauncher.launch(arrayOf("video/*"))
                    true
                } catch (_: Throwable) {
                    false
                }
            },
            launchOpenDocGeneric = { mimes ->
                try {
                    pendingKind = if (mimes.any { it.startsWith("audio/") }) {
                        ActiveSafePickerKind.AUDIO
                    } else {
                        ActiveSafePickerKind.DOCUMENT
                    }
                    openDocumentLauncher.launch(mimes)
                    true
                } catch (_: Throwable) {
                    false
                }
            },
            launchGetContentGeneric = { mime ->
                try {
                    pendingKind = if (mime.startsWith("audio/")) {
                        ActiveSafePickerKind.AUDIO
                    } else {
                        ActiveSafePickerKind.DOCUMENT
                    }
                    getContentLauncher.launch(mime)
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        )
    }
}
