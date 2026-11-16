package com.zerotrace.media

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val launchImageChain: () -> Unit,
    private val launchVideoChain: () -> Unit,
    private val launchDocumentChain: (Array<String>, String) -> Unit
) {
    fun pickImage() = launchImageChain()
    fun pickVideo() = launchVideoChain()
    fun pickDocument(mimeTypes: Array<String> = arrayOf("*/*"), fallbackMime: String = "*/*") =
        launchDocumentChain(mimeTypes, fallbackMime)
}

private enum class ActiveSafePickerKind {
    NONE,
    IMAGE,
    VIDEO,
    DOCUMENT,
    AUDIO
}

private data class FallbackMediaEntry(
    val uri: Uri,
    val displayName: String,
    val subtitle: String,
    val thumbnail: Bitmap? = null
)

private data class AvatarPresetSpec(
    val label: String,
    val initials: String,
    val bgColorInt: Int
)

/**
 * Crash-proof, zero-permission media & file picker helper.
 * Order for images/videos:
 *  1) [ActivityResultContracts.PickVisualMedia] (Android Photo Picker)
 *  2) [ActivityResultContracts.GetContent] (standard Gallery / Files chooser)
 *  3) Raw `ACTION_GET_CONTENT` without `CATEGORY_OPENABLE` (legacy/OEM galleries)
 *  4) `ACTION_PICK` against MediaStore (`EXTERNAL_CONTENT_URI`)
 *  5) [ActivityResultContracts.OpenDocument] (Storage Access Framework)
 *  6) In-App Zero-Permission Fallback Picker (MediaStore app-owned entries + Camera + Privacy Avatars)
 *     when the device/emulator has zero external Gallery/DocumentsUI APKs installed.
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

    var pendingKindName by rememberSaveable { mutableStateOf(ActiveSafePickerKind.NONE.name) }
    var fallbackDialogKind by remember { mutableStateOf<ActiveSafePickerKind?>(null) }

    fun setPendingKind(kind: ActiveSafePickerKind) {
        pendingKindName = kind.name
    }

    fun getPendingKind(): ActiveSafePickerKind {
        return try {
            ActiveSafePickerKind.valueOf(pendingKindName)
        } catch (_: Throwable) {
            ActiveSafePickerKind.NONE
        }
    }

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
        deliverUri(getPendingKind(), uri)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        deliverUri(getPendingKind(), uri)
    }

    val genericActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        deliverUri(getPendingKind(), uri)
    }

    val cameraBitmapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        ExternalPickerGuard.clearOnPickerResult()
        if (bitmap != null) {
            val uri = writeBitmapToShareableTempUri(context, bitmap, "camera_pick_${System.currentTimeMillis()}.jpg")
            if (uri != null) {
                deliverUri(ActiveSafePickerKind.IMAGE, uri)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                ExternalPickerGuard.markLaunchingExternalPicker()
                cameraBitmapLauncher.launch(null)
            } catch (_: Throwable) {
                ExternalPickerGuard.clearOnPickerResult()
                Toast.makeText(context, "Camera app unavailable on this device.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (fallbackDialogKind != null) {
        val activeKind = fallbackDialogKind!!
        InAppFallbackPickerDialog(
            kind = activeKind,
            onDismiss = { fallbackDialogKind = null },
            onSelectUri = { uri ->
                fallbackDialogKind = null
                deliverUri(activeKind, uri)
            },
            onLaunchCamera = {
                fallbackDialogKind = null
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    try {
                        ExternalPickerGuard.markLaunchingExternalPicker()
                        cameraBitmapLauncher.launch(null)
                    } catch (_: Throwable) {
                        ExternalPickerGuard.clearOnPickerResult()
                        Toast.makeText(context, "Camera app unavailable on this device.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )
    }

    return remember(context) {
        SafeMediaPickerController(
            launchImageChain = {
                ExternalPickerGuard.markLaunchingExternalPicker()
                setPendingKind(ActiveSafePickerKind.IMAGE)

                val launched = tryLaunch {
                    photoVisualLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } || tryLaunch {
                    getContentLauncher.launch("image/*")
                } || tryLaunch {
                    genericActivityLauncher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                        }
                    )
                } || tryLaunch {
                    genericActivityLauncher.launch(
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    )
                } || tryLaunch {
                    genericActivityLauncher.launch(
                        Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                    )
                } || tryLaunch {
                    openDocumentLauncher.launch(arrayOf("image/*"))
                }

                if (!launched) {
                    ExternalPickerGuard.clearOnPickerResult()
                    fallbackDialogKind = ActiveSafePickerKind.IMAGE
                }
            },
            launchVideoChain = {
                ExternalPickerGuard.markLaunchingExternalPicker()
                setPendingKind(ActiveSafePickerKind.VIDEO)

                val launched = tryLaunch {
                    videoVisualLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                } || tryLaunch {
                    getContentLauncher.launch("video/*")
                } || tryLaunch {
                    genericActivityLauncher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "video/*"
                        }
                    )
                } || tryLaunch {
                    genericActivityLauncher.launch(
                        Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    )
                } || tryLaunch {
                    genericActivityLauncher.launch(
                        Intent(Intent.ACTION_PICK).apply { type = "video/*" }
                    )
                } || tryLaunch {
                    openDocumentLauncher.launch(arrayOf("video/*"))
                }

                if (!launched) {
                    ExternalPickerGuard.clearOnPickerResult()
                    fallbackDialogKind = ActiveSafePickerKind.VIDEO
                }
            },
            launchDocumentChain = { mimeTypes, fallbackMime ->
                ExternalPickerGuard.markLaunchingExternalPicker()
                val resolvedKind = if (mimeTypes.any { it.startsWith("audio/") } || fallbackMime.startsWith("audio/")) {
                    ActiveSafePickerKind.AUDIO
                } else {
                    ActiveSafePickerKind.DOCUMENT
                }
                setPendingKind(resolvedKind)

                val launched = tryLaunch {
                    openDocumentLauncher.launch(mimeTypes)
                } || tryLaunch {
                    getContentLauncher.launch(fallbackMime)
                } || tryLaunch {
                    genericActivityLauncher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = fallbackMime
                        }
                    )
                }

                if (!launched) {
                    ExternalPickerGuard.clearOnPickerResult()
                    fallbackDialogKind = resolvedKind
                }
            }
        )
    }
}

private inline fun tryLaunch(block: () -> Unit): Boolean {
    return try {
        block()
        true
    } catch (_: Throwable) {
        false
    }
}

private fun writeBitmapToShareableTempUri(
    context: Context,
    bitmap: Bitmap,
    fileName: String
): Uri? {
    return try {
        val dir = File(context.cacheDir, "ephemeral_open").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Throwable) {
        null
    }
}

private fun generateAvatarPresetBitmap(spec: AvatarPresetSpec): Bitmap {
    val size = 256
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = spec.bgColorInt
        style = Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    canvas.drawCircle(size / 2f, size / 2f, size * 0.42f, ringPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 96f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(spec.initials, canvas.width / 2f, yPos, textPaint)
    return bmp
}

private fun queryAccessibleMediaStoreEntries(
    context: Context,
    kind: ActiveSafePickerKind
): List<FallbackMediaEntry> {
    val results = mutableListOf<FallbackMediaEntry>()
    val resolver = context.contentResolver

    if (kind == ActiveSafePickerKind.IMAGE) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE
            )
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                var count = 0
                while (cursor.moveToNext() && count < 20) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "image_$id.jpg" else "image_$id.jpg"
                    val sizeKb = if (sizeCol >= 0) (cursor.getLong(sizeCol) / 1024L).coerceAtLeast(1L) else 0L
                    val thumb = try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    } catch (_: Throwable) {
                        null
                    }
                    results.add(
                        FallbackMediaEntry(
                            uri = uri,
                            displayName = name,
                            subtitle = if (sizeKb > 0) "${sizeKb} KB • Gallery" else "Gallery Image",
                            thumbnail = thumb
                        )
                    )
                    count++
                }
            }
        } catch (_: Throwable) {
        }
    } else if (kind == ActiveSafePickerKind.VIDEO) {
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE
            )
            resolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                var count = 0
                while (cursor.moveToNext() && count < 15) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "video_$id.mp4" else "video_$id.mp4"
                    val sizeKb = if (sizeCol >= 0) (cursor.getLong(sizeCol) / 1024L).coerceAtLeast(1L) else 0L
                    results.add(
                        FallbackMediaEntry(
                            uri = uri,
                            displayName = name,
                            subtitle = if (sizeKb > 0) "${sizeKb} KB • Video" else "Video File"
                        )
                    )
                    count++
                }
            }
        } catch (_: Throwable) {
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE
            )
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Downloads.SIZE)
                var count = 0
                while (cursor.moveToNext() && count < 15) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "file_$id" else "file_$id"
                    val sizeKb = if (sizeCol >= 0) (cursor.getLong(sizeCol) / 1024L).coerceAtLeast(1L) else 0L
                    results.add(
                        FallbackMediaEntry(
                            uri = uri,
                            displayName = name,
                            subtitle = if (sizeKb > 0) "${sizeKb} KB • Downloads" else "Saved File"
                        )
                    )
                    count++
                }
            }
        } catch (_: Throwable) {
        }
    }

    return results
}

@Composable
private fun InAppFallbackPickerDialog(
    kind: ActiveSafePickerKind,
    onDismiss: () -> Unit,
    onSelectUri: (Uri) -> Unit,
    onLaunchCamera: () -> Unit
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<FallbackMediaEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(kind) {
        loading = true
        entries = withContext(Dispatchers.IO) {
            queryAccessibleMediaStoreEntries(context, kind)
        }
        loading = false
    }

    val avatarPresets = remember {
        listOf(
            AvatarPresetSpec("Emerald", "ZT", android.graphics.Color.rgb(0, 168, 132)),
            AvatarPresetSpec("Ocean", "PQ", android.graphics.Color.rgb(2, 132, 199)),
            AvatarPresetSpec("Royal", "E2", android.graphics.Color.rgb(109, 40, 217)),
            AvatarPresetSpec("Amber", "V3", android.graphics.Color.rgb(217, 119, 6)),
            AvatarPresetSpec("Teal", "ON", android.graphics.Color.rgb(15, 118, 110)),
            AvatarPresetSpec("Cobalt", "ZK", android.graphics.Color.rgb(37, 99, 235))
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = when (kind) {
                    ActiveSafePickerKind.VIDEO -> Icons.Default.Videocam
                    ActiveSafePickerKind.DOCUMENT, ActiveSafePickerKind.AUDIO -> Icons.Default.Description
                    else -> Icons.Default.PhotoLibrary
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = when (kind) {
                    ActiveSafePickerKind.IMAGE -> "Select Photo"
                    ActiveSafePickerKind.VIDEO -> "Select Video"
                    ActiveSafePickerKind.AUDIO -> "Select Audio File"
                    else -> "Select Document"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "System Gallery / Files picker is unavailable on this Android container. Use Camera or choose from accessible ZeroTrace media below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (kind == ActiveSafePickerKind.IMAGE) {
                    Button(
                        onClick = onLaunchCamera,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo with Camera")
                    }
                }

                if (!loading && entries.isNotEmpty()) {
                    Text(
                        text = "Accessible Device Media (${entries.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(entries) { entry ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectUri(entry.uri) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (entry.thumbnail != null) {
                                        Image(
                                            bitmap = entry.thumbnail.asImageBitmap(),
                                            contentDescription = entry.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.displayName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!loading) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (kind == ActiveSafePickerKind.IMAGE) {
                                "No saved images found yet. Tip: save a QR image first, use Camera above, or pick a quick avatar badge below."
                            } else {
                                "No accessible files found on this headless container without an Android Files app."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (kind == ActiveSafePickerKind.IMAGE) {
                    Text(
                        text = "Quick ZeroTrace Avatar Badges",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(avatarPresets) { spec ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val bmp = generateAvatarPresetBitmap(spec)
                                        val uri = writeBitmapToShareableTempUri(
                                            context,
                                            bmp,
                                            "avatar_${spec.label.lowercase()}_${System.currentTimeMillis()}.jpg"
                                        )
                                        bmp.recycle()
                                        if (uri != null) {
                                            onSelectUri(uri)
                                        }
                                    }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(spec.bgColorInt)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = spec.initials,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = spec.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
