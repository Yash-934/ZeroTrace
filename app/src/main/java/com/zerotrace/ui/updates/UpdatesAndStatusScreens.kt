package com.zerotrace.ui.updates

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.StatusPrivacyPolicy
import com.zerotrace.media.MediaExportAndOpenHelper
import com.zerotrace.network.ConnectivityState
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.EmptyState
import com.zerotrace.ui.components.StatusCard
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.MonospaceStyle
import com.zerotrace.ui.theme.ZeroTracePalette

/**
 * Screen 4 of Section 4: Updates Tab (Peer-to-Peer Ephemeral Status)
 * Broadcasts encrypted Text, Photo, and Video statuses to verified peers with 24h RAM expiry.
 */
@Composable
fun ZeroTraceUpdatesTabScreen(
    language: AppLanguage,
    identitySummary: PublicIdentitySummary?,
    connectivityState: ConnectivityState,
    torProgress: Int,
    statuses: List<EphemeralStatusItem>,
    privacyPolicy: StatusPrivacyPolicy,
    onPrivacyPolicyChange: (StatusPrivacyPolicy) -> Unit,
    onPublishStatus: (String, StatusMediaType, Uri?) -> Unit,
    onOpenStatusViewer: (EphemeralStatusItem) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onEmergencyWipe: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI
    var showComposerDialog by remember { mutableStateOf(false) }

    val activeStatuses = remember(statuses) { statuses.filterNot { it.isExpired() } }
    val ownStatuses = remember(activeStatuses) { activeStatuses.filter { it.isOwnStatus } }
    val recentPeerStatuses = remember(activeStatuses) { activeStatuses.filter { !it.isOwnStatus && !it.viewedByLocalUser } }
    val viewedPeerStatuses = remember(activeStatuses) { activeStatuses.filter { !it.isOwnStatus && it.viewedByLocalUser } }

    if (showComposerDialog) {
        CreateEphemeralStatusDialog(
            onPublish = { caption, mediaType, mediaUri ->
                onPublishStatus(caption, mediaType, mediaUri)
                showComposerDialog = false
            },
            onDismiss = { showComposerDialog = false }
        )
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Update,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isHi) "माय स्टेटस" else "My Status",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "TOR v3 • ${connectivityState.code}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showComposerDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_new_status")
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Post Ephemeral Status")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("updates_tab_screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 88.dp)
        ) {
            // Clean centered E2EE story disclaimer pill matching Mockup Screen 4
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHi) {
                                    "स्टेटस एंड-टू-एंड एन्क्रिप्टेड हैं • 24h"
                                } else {
                                    "Stories are end-to-end encrypted"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Status Audience Privacy Selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHi) "गोपनीयता:" else "Privacy:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPrivacyPolicy.entries.forEach { policy ->
                        val isSelected = privacyPolicy == policy
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPrivacyPolicyChange(policy) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = null,
                            shape = RoundedCornerShape(50),
                            label = {
                                Text(
                                    text = if (policy == StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS) {
                                        "All Verified Contacts"
                                    } else {
                                        "Selected Peers"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // My Status Clean Row matching Mockup Screen 4
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (ownStatuses.isNotEmpty()) {
                                onOpenStatusViewer(ownStatuses.first())
                            } else {
                                showComposerDialog = true
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        ContactAvatar(
                            alias = "Me",
                            onionAddress = identitySummary?.onionAddress ?: "local.onion",
                            hasActiveStatusRing = ownStatuses.isNotEmpty(),
                            size = 50.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isHi) "मेरी स्थिति •" else "My Status •",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (ownStatuses.isEmpty()) {
                                    "Disappearing in 24h • Tap + to share"
                                } else {
                                    "${ownStatuses.size} active • Disappearing in ${ownStatuses.first().remainingHours()}h"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showComposerDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("add_my_status_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            }

            if (ownStatuses.isNotEmpty()) {
                item {
                    Text(
                        text = "MY ACTIVE STATUSES (${ownStatuses.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(ownStatuses, key = { it.statusId }) { item ->
                    StatusCard(
                        statusItem = item,
                        onClick = { onOpenStatusViewer(item) },
                        onDeleteClick = { onDeleteStatus(item.statusId) }
                    )
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            }

            // Recent Unviewed Peer Updates
            item {
                Text(
                    text = "RECENT PEER UPDATES (${recentPeerStatuses.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (recentPeerStatuses.isEmpty() && viewedPeerStatuses.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Update,
                        title = "No Peer Status Updates Yet",
                        description = "When a verified peer shares a text, photo, or video status over an active P2P session, it automatically syncs here and expires after 24 hours.",
                        primaryActionLabel = "Create My 24h Status",
                        onPrimaryAction = { showComposerDialog = true }
                    )
                }
            } else {
                items(recentPeerStatuses, key = { it.statusId }) { item ->
                    StatusCard(
                        statusItem = item,
                        onClick = { onOpenStatusViewer(item) }
                    )
                }
            }

            if (viewedPeerStatuses.isNotEmpty()) {
                item {
                    Text(
                        text = "VIEWED UPDATES (${viewedPeerStatuses.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(viewedPeerStatuses, key = { it.statusId }) { item ->
                    StatusCard(
                        statusItem = item,
                        onClick = { onOpenStatusViewer(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateEphemeralStatusDialog(
    onPublish: (String, StatusMediaType, Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(StatusMediaType.TEXT_CARD) }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            selectedType = StatusMediaType.IMAGE_CARD
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            selectedType = StatusMediaType.VIDEO_CARD
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share E2EE 24h Status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Encrypted with ChaCha20-Poly1305 & delivered P2P to verified connected peers. Auto-deletes after 24 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == StatusMediaType.TEXT_CARD,
                        onClick = {
                            selectedType = StatusMediaType.TEXT_CARD
                            selectedMediaUri = null
                        },
                        leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("Text", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = selectedType == StatusMediaType.IMAGE_CARD,
                        onClick = {
                            selectedType = StatusMediaType.IMAGE_CARD
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("Photo", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = selectedType == StatusMediaType.VIDEO_CARD,
                        onClick = {
                            selectedType = StatusMediaType.VIDEO_CARD
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("Video", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                if (selectedType != StatusMediaType.TEXT_CARD) {
                    Surface(
                        color = ZeroTracePalette.mintContainer(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectedType == StatusMediaType.VIDEO_CARD) {
                                    videoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                } else {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (selectedMediaUri != null) {
                                    val seg = selectedMediaUri?.lastPathSegment ?: "media"
                                    "Selected (${selectedType.name.removeSuffix("_CARD")}): $seg"
                                } else {
                                    if (selectedType == StatusMediaType.VIDEO_CARD) {
                                        "Tap to choose Video from Gallery…"
                                    } else {
                                        "Tap to choose Photo from Gallery…"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ZeroTracePalette.mintContent(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Add a caption or status message…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_caption_input"),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            val canPublish = selectedType == StatusMediaType.TEXT_CARD || selectedMediaUri != null || caption.isNotBlank()
            Button(
                onClick = {
                    val text = caption.ifBlank {
                        when (selectedType) {
                            StatusMediaType.VIDEO_CARD -> "Encrypted Video Status (24h)"
                            StatusMediaType.IMAGE_CARD -> "Encrypted Photo Status (24h)"
                            else -> "Tor v3 Hidden Service active • Post-Quantum Hybrid E2EE verified."
                        }
                    }
                    onPublish(text, selectedType, selectedMediaUri)
                },
                enabled = canPublish,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("publish_status_confirm_btn")
            ) {
                Text("Broadcast E2EE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Screen 5 of Section 4: Full-Screen Ephemeral Status Viewer
 */
@Composable
fun ZeroTraceStatusViewerScreen(
    statusItem: EphemeralStatusItem?,
    onBack: () -> Unit,
    onReplyToStatus: (EphemeralStatusItem, String) -> Unit,
    onSaveStatusToGallery: (EphemeralStatusItem) -> Unit = {},
    onDeleteOwnStatus: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var replyText by remember { mutableStateOf("") }

    Surface(
        color = Color(0xFF061611),
        modifier = Modifier
            .fillMaxSize()
            .testTag("status_viewer_screen")
    ) {
        if (statusItem == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("Status expired or wiped from RAM.", color = Color.White)
            }
        } else {
            val mediaBytes = statusItem.encryptedPayloadBytes
            val imageBitmap = remember(statusItem.statusId, mediaBytes?.size) {
                if (statusItem.mediaType == StatusMediaType.IMAGE_CARD && mediaBytes != null && mediaBytes.isNotEmpty()) {
                    try {
                        BitmapFactory.decodeByteArray(mediaBytes, 0, mediaBytes.size)?.asImageBitmap()
                    } catch (_: Throwable) {
                        null
                    }
                } else {
                    null
                }
            }

            val tempVideoFile = remember(statusItem.statusId, mediaBytes?.size) {
                if (statusItem.mediaType == StatusMediaType.VIDEO_CARD && mediaBytes != null && mediaBytes.isNotEmpty()) {
                    MediaExportAndOpenHelper.prepareTempCacheFile(
                        context = context,
                        fileName = statusItem.sanitizedFileName ?: "status_${statusItem.statusId.take(6)}.mp4",
                        bytes = mediaBytes
                    )
                } else {
                    null
                }
            }

            DisposableEffect(tempVideoFile) {
                onDispose {
                    MediaExportAndOpenHelper.shredTempCacheFile(tempVideoFile)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A2E24), Color(0xFF06080C))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Progress & Author Bar
                Column {
                    LinearProgressIndicator(
                        progress = { 1f },
                        color = Color(0xFF00E676),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close Status",
                                    tint = Color.White
                                )
                            }
                            ContactAvatar(
                                alias = statusItem.authorAlias,
                                onionAddress = statusItem.authorOnion,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = statusItem.authorAlias,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "${statusItem.authorOnion.take(16)}…onion • ${statusItem.remainingHours()}h left",
                                    style = MonospaceStyle.copy(fontSize = 10.sp),
                                    color = Color(0xFF97F0FF)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mediaBytes != null && mediaBytes.isNotEmpty() &&
                                (statusItem.mediaType == StatusMediaType.IMAGE_CARD || statusItem.mediaType == StatusMediaType.VIDEO_CARD)
                            ) {
                                IconButton(
                                    onClick = { onSaveStatusToGallery(statusItem) },
                                    modifier = Modifier.testTag("status_save_gallery_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Save Status Media to Gallery",
                                        tint = Color(0xFF00E676)
                                    )
                                }
                            }
                            if (statusItem.isOwnStatus) {
                                IconButton(onClick = { onDeleteOwnStatus(statusItem.statusId) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Wipe Status",
                                        tint = CrimsonDanger
                                    )
                                }
                            }
                        }
                    }
                }

                // Center Status Card Content (Photo / Video / Text)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.42f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "E2EE P2P STATUS • ${statusItem.mediaType.name.replace("_", " ")}",
                                style = MonospaceStyle.copy(fontSize = 11.sp),
                                color = Color(0xFF00E676)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = statusItem.captionText,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        } else if (tempVideoFile != null) {
                            val videoUri = remember(tempVideoFile) {
                                MediaExportAndOpenHelper.getShareableUri(context, tempVideoFile)
                            }
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val controller = MediaController(ctx)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setVideoURI(videoUri)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Text(
                            text = statusItem.captionText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "${statusItem.remainingHours()}h left • Auto-deletes after 24h • Zero central server",
                            style = MonospaceStyle.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Bottom Reply Bar (for peer status) or Wipe Button (for own status)
                if (!statusItem.isOwnStatus) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Reply over encrypted Tor ratchet…", color = Color.White.copy(alpha = 0.65f)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    onReplyToStatus(statusItem, replyText)
                                    replyText = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Encrypted Status Reply",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDeleteOwnStatus(statusItem.statusId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonDanger)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zeroize My Status from RAM Now", color = CrimsonDanger)
                    }
                }
            }
        }
    }
}
