package com.zerotrace.ui.updates

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import com.zerotrace.media.rememberSafeMediaPicker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.StatusPrivacyPolicy
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.media.MediaExportAndOpenHelper
import com.zerotrace.network.ConnectivityState
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.EmptyState
import com.zerotrace.ui.components.StatusCard
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.ZeroTraceEmerald
import com.zerotrace.ui.theme.ZeroTracePalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Screen 4 of Section 4: Updates Tab (Peer-to-Peer Ephemeral Status)
 * Broadcasts encrypted Text, Photo, and Video statuses to verified peers with 24h RAM expiry.
 */
@Composable
fun ZeroTraceUpdatesTabScreen(
    language: AppLanguage,
    identitySummary: PublicIdentitySummary?,
    localAvatarBytes: ByteArray? = null,
    verifiedContacts: List<VerifiedContactRecord> = emptyList(),
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
    var expandOwnStatuses by remember { mutableStateOf(false) }

    val activeStatuses = remember(statuses) { statuses.filterNot { it.isExpired() } }
    val ownStatuses = remember(activeStatuses) {
        activeStatuses.filter { it.isOwnStatus }.sortedBy { it.createdAtEpochMillis }
    }
    val peerAuthorGroups = remember(activeStatuses) {
        activeStatuses
            .filter { !it.isOwnStatus }
            .groupBy { it.authorOnion.lowercase() }
            .values
            .map { list -> list.sortedBy { it.createdAtEpochMillis } }
            .sortedByDescending { group -> group.maxOfOrNull { it.createdAtEpochMillis } ?: 0L }
    }
    val recentPeerGroups = remember(peerAuthorGroups) {
        peerAuthorGroups.filter { group -> group.any { !it.viewedByLocalUser } }
    }
    val viewedPeerGroups = remember(peerAuthorGroups) {
        peerAuthorGroups.filter { group -> group.all { it.viewedByLocalUser } }
    }

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
                                text = if (isHi) "स्टेटस और अपडेट" else "Updates",
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 88.dp)
        ) {
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
                                    "Status updates are end-to-end encrypted"
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
                                selectedContainerColor = ZeroTracePalette.activePillContainer(),
                                selectedLabelColor = ZeroTracePalette.activePillContent(),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
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

            // My Status Clean Row with segmented ring when multiple statuses exist
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
                            statusSegmentCount = ownStatuses.size,
                            unseenStatusSegmentCount = ownStatuses.size,
                            avatarBytes = localAvatarBytes,
                            size = 52.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isHi) "मेरा स्टेटस" else "My status",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (ownStatuses.isEmpty()) {
                                    "Tap + to add status update (24h)"
                                } else {
                                    val latest = ownStatuses.last()
                                    "${formatWhatsAppStatusTime(latest.createdAtEpochMillis)} • ${ownStatuses.size} active"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ownStatuses.isNotEmpty()) {
                            IconButton(onClick = { expandOwnStatuses = !expandOwnStatuses }) {
                                Icon(
                                    imageVector = if (expandOwnStatuses) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Manage My Statuses",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            }

            if (ownStatuses.isNotEmpty() && expandOwnStatuses) {
                items(ownStatuses, key = { it.statusId }) { item ->
                    StatusCard(
                        statusItem = item,
                        onClick = { onOpenStatusViewer(item) },
                        onDeleteClick = { onDeleteStatus(item.statusId) }
                    )
                }
            }

            // Recent Unviewed Peer Updates (Grouped per Author with Segmented Rings!)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isHi) "हाल के अपडेट (${recentPeerGroups.size})" else "Recent updates",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (recentPeerGroups.isEmpty() && viewedPeerGroups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Update,
                        title = "No Peer Status Updates Yet",
                        description = "When a verified peer shares a text, photo, or video status over an active P2P session, it automatically syncs here and expires after 24 hours.",
                        primaryActionLabel = "Create My 24h Status",
                        onPrimaryAction = { showComposerDialog = true }
                    )
                }
            } else if (recentPeerGroups.isEmpty()) {
                item {
                    Text(
                        text = "All caught up — no unread updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else {
                items(recentPeerGroups, key = { "recent_${it.first().authorOnion}" }) { group ->
                    val authorOnion = group.first().authorOnion
                    val authorAvatar = verifiedContacts.firstOrNull {
                        it.onionAddress.equals(authorOnion, ignoreCase = true)
                    }?.avatarBytes
                    SegmentedAuthorStatusRow(
                        group = group,
                        avatarBytes = authorAvatar,
                        onClick = {
                            val firstUnread = group.firstOrNull { !it.viewedByLocalUser } ?: group.first()
                            onOpenStatusViewer(firstUnread)
                        }
                    )
                }
            }

            if (viewedPeerGroups.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isHi) "देखे गए अपडेट (${viewedPeerGroups.size})" else "Viewed updates",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(viewedPeerGroups, key = { "viewed_${it.first().authorOnion}" }) { group ->
                    val authorOnion = group.first().authorOnion
                    val authorAvatar = verifiedContacts.firstOrNull {
                        it.onionAddress.equals(authorOnion, ignoreCase = true)
                    }?.avatarBytes
                    SegmentedAuthorStatusRow(
                        group = group,
                        avatarBytes = authorAvatar,
                        onClick = { onOpenStatusViewer(group.first()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedAuthorStatusRow(
    group: List<EphemeralStatusItem>,
    avatarBytes: ByteArray? = null,
    onClick: () -> Unit
) {
    val latest = group.maxByOrNull { it.createdAtEpochMillis } ?: return
    val unseenCount = group.count { !it.viewedByLocalUser }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("status_author_group_${latest.authorOnion.take(8)}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(
            alias = latest.authorAlias,
            onionAddress = latest.authorOnion,
            hasActiveStatusRing = true,
            statusSegmentCount = group.size,
            unseenStatusSegmentCount = unseenCount,
            avatarBytes = avatarBytes,
            size = 52.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = latest.authorAlias,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (group.size > 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${group.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unseenCount > 0) ZeroTraceEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatWhatsAppStatusTime(latest.createdAtEpochMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatWhatsAppStatusTime(epochMillis: Long): String {
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
    val sameYear = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    val dayDiff = nowCal.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today, $timeStr"
        sameYear && dayDiff == 1 -> "Yesterday, $timeStr"
        else -> SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(epochMillis))
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

    val mediaPicker = rememberSafeMediaPicker(
        onImagePicked = { uri ->
            selectedMediaUri = uri
            selectedType = StatusMediaType.IMAGE_CARD
        },
        onVideoPicked = { uri ->
            selectedMediaUri = uri
            selectedType = StatusMediaType.VIDEO_CARD
        }
    )

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
                            mediaPicker.pickImage()
                        },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text("Photo", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = selectedType == StatusMediaType.VIDEO_CARD,
                        onClick = {
                            selectedType = StatusMediaType.VIDEO_CARD
                            mediaPicker.pickVideo()
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
                                    mediaPicker.pickVideo()
                                } else {
                                    mediaPicker.pickImage()
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
 * Screen 5 of Section 4: Full-Screen WhatsApp-Style Ephemeral Status Viewer
 */
@Composable
fun ZeroTraceStatusViewerScreen(
    statuses: List<EphemeralStatusItem>,
    currentIndex: Int,
    fallbackStatus: EphemeralStatusItem?,
    localAvatarBytes: ByteArray? = null,
    verifiedContacts: List<VerifiedContactRecord> = emptyList(),
    onStepStatus: (Int) -> Unit,
    onBack: () -> Unit,
    onReplyToStatus: (EphemeralStatusItem, String) -> Unit,
    onSaveStatusToGallery: (EphemeralStatusItem) -> Unit = {},
    onDeleteOwnStatus: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val resolvedStatuses = remember(statuses, fallbackStatus) {
        if (statuses.isNotEmpty()) statuses else listOfNotNull(fallbackStatus)
    }
    val safeIndex = currentIndex.coerceIn(0, (resolvedStatuses.size - 1).coerceAtLeast(0))
    val statusItem = resolvedStatuses.getOrNull(safeIndex)

    var replyText by remember(statusItem?.statusId) { mutableStateOf("") }
    var isHoldingToPause by remember { mutableStateOf(false) }
    var isReplyFocused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var likedHeart by remember(statusItem?.statusId) { mutableStateOf(false) }
    var quickSentBanner by remember(statusItem?.statusId) { mutableStateOf<String?>(null) }
    var segmentProgress by remember(statusItem?.statusId) { mutableFloatStateOf(0f) }

    val isPaused = isHoldingToPause || isReplyFocused || replyText.isNotBlank() || menuExpanded

    LaunchedEffect(statusItem?.statusId, isPaused) {
        if (statusItem == null) return@LaunchedEffect
        val totalDurationMs = if (statusItem.mediaType == StatusMediaType.VIDEO_CARD) 12_000L else 5_500L
        val tickMs = 40L
        while (segmentProgress < 1f) {
            if (!isPaused) {
                segmentProgress = (segmentProgress + tickMs.toFloat() / totalDurationMs.toFloat()).coerceAtMost(1f)
            }
            delay(tickMs)
        }
        onStepStatus(+1)
    }

    Surface(
        color = Color.Black,
        modifier = Modifier
            .fillMaxSize()
            .testTag("status_viewer_screen")
    ) {
        if (statusItem == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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

            val parsedCardBg = remember(statusItem.backgroundHex) {
                try {
                    Color(android.graphics.Color.parseColor(statusItem.backgroundHex))
                } catch (_: Throwable) {
                    Color(0xFF075E54)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (statusItem.mediaType == StatusMediaType.TEXT_CARD) parsedCardBg else Color.Black)
                    .pointerInput(statusItem.statusId) {
                        detectTapGestures(
                            onPress = {
                                isHoldingToPause = true
                                tryAwaitRelease()
                                isHoldingToPause = false
                            },
                            onTap = { offset ->
                                if (offset.x < size.width * 0.28f) {
                                    onStepStatus(-1)
                                } else {
                                    onStepStatus(+1)
                                }
                            }
                        )
                    }
            ) {
                // Full-bleed center stage (Photo / Video / Text Card)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        imageBitmap != null -> {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = statusItem.captionText,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        tempVideoFile != null -> {
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
                                    .fillMaxHeight(0.76f)
                            )
                        }
                        else -> {
                            Text(
                                text = statusItem.captionText,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 26.sp,
                                    lineHeight = 35.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 28.dp)
                            )
                        }
                    }
                }

                // Top WhatsApp Progress Bars + Author Header Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.30f))
                        .statusBarsPadding()
                        .padding(top = 6.dp, start = 10.dp, end = 10.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        resolvedStatuses.forEachIndexed { idx, _ ->
                            val fillFraction = when {
                                idx < safeIndex -> 1f
                                idx == safeIndex -> segmentProgress
                                else -> 0f
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.34f))
                            ) {
                                if (fillFraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fillFraction)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close Status",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            val viewerAvatarBytes = if (statusItem.isOwnStatus) {
                                localAvatarBytes
                            } else {
                                verifiedContacts.firstOrNull {
                                    it.onionAddress.equals(statusItem.authorOnion, ignoreCase = true)
                                }?.avatarBytes
                            }
                            ContactAvatar(
                                alias = statusItem.authorAlias,
                                onionAddress = statusItem.authorOnion,
                                isVerified = false,
                                avatarBytes = viewerAvatarBytes,
                                size = 38.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = statusItem.authorAlias,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatWhatsAppStatusTime(statusItem.createdAtEpochMillis),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = Color.White.copy(alpha = 0.82f),
                                    maxLines = 1
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("status_viewer_more_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Status Options",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                if (mediaBytes != null && mediaBytes.isNotEmpty() &&
                                    (statusItem.mediaType == StatusMediaType.IMAGE_CARD || statusItem.mediaType == StatusMediaType.VIDEO_CARD)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Save to Gallery") },
                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onSaveStatusToGallery(statusItem)
                                        }
                                    )
                                }
                                if (statusItem.isOwnStatus) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Status", color = CrimsonDanger) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonDanger) },
                                        onClick = {
                                            menuExpanded = false
                                            onDeleteOwnStatus(statusItem.statusId)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("E2EE • Auto-deletes in ${statusItem.remainingHours()}h") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    onClick = { menuExpanded = false }
                                )
                            }
                        }
                    }
                }

                // Bottom Overlay: Caption Scrim + Quick Confirmation Pill + WhatsApp Reply Pill Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    AnimatedVisibility(visible = quickSentBanner != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = Color(0xFF1F2C34).copy(alpha = 0.94f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = quickSentBanner.orEmpty(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (statusItem.mediaType != StatusMediaType.TEXT_CARD && statusItem.captionText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.52f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = statusItem.captionText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (!statusItem.isOwnStatus) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Dark Rounded "Reply" Pill matching screenshot
                            Surface(
                                color = Color(0xFF1F2C34),
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (replyText.isEmpty()) {
                                            Text(
                                                text = "Reply",
                                                color = Color.White.copy(alpha = 0.78f),
                                                fontSize = 15.5.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = replyText,
                                            onValueChange = { replyText = it },
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 15.5.sp
                                            ),
                                            cursorBrush = SolidColor(ZeroTraceEmerald),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(
                                                onSend = {
                                                    if (replyText.isNotBlank()) {
                                                        onReplyToStatus(statusItem, replyText)
                                                        quickSentBanner = "Encrypted reply sent"
                                                        replyText = ""
                                                    }
                                                }
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isReplyFocused = it.isFocused }
                                                .testTag("status_reply_input")
                                        )
                                    }

                                    if (replyText.isBlank()) {
                                        Text(
                                            text = "❤️",
                                            fontSize = 20.sp,
                                            modifier = Modifier
                                                .clickable {
                                                    likedHeart = true
                                                    onReplyToStatus(statusItem, "❤️")
                                                    quickSentBanner = "Sent ❤️ over Tor"
                                                }
                                                .padding(horizontal = 6.dp)
                                        )
                                        Text(
                                            text = "😢",
                                            fontSize = 20.sp,
                                            modifier = Modifier
                                                .clickable {
                                                    onReplyToStatus(statusItem, "😢")
                                                    quickSentBanner = "Sent 😢 over Tor"
                                                }
                                                .padding(start = 4.dp)
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                onReplyToStatus(statusItem, replyText)
                                                quickSentBanner = "Encrypted reply sent"
                                                replyText = ""
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send Encrypted Reply",
                                                tint = ZeroTraceEmerald,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Two right dark circular buttons matching WhatsApp screenshot
                            if (mediaBytes != null && mediaBytes.isNotEmpty() &&
                                (statusItem.mediaType == StatusMediaType.IMAGE_CARD || statusItem.mediaType == StatusMediaType.VIDEO_CARD)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1F2C34))
                                        .clickable {
                                            onSaveStatusToGallery(statusItem)
                                            quickSentBanner = "Saving to Gallery…"
                                        }
                                        .testTag("status_save_gallery_btn"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Save Status Media to Gallery",
                                        tint = Color.White,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1F2C34))
                                        .clickable {
                                            onReplyToStatus(statusItem, "🔥")
                                            quickSentBanner = "Sent 🔥 over Tor"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = "Quick React",
                                        tint = Color.White,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1F2C34))
                                    .clickable {
                                        likedHeart = !likedHeart
                                        onReplyToStatus(statusItem, "❤️")
                                        quickSentBanner = "Sent ❤️ over Tor"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (likedHeart) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like Status",
                                    tint = if (likedHeart) Color(0xFFFF3B5C) else Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${safeIndex + 1}/${resolvedStatuses.size} • Auto-deletes in ${statusItem.remainingHours()}h",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            TextButton(onClick = { onDeleteOwnStatus(statusItem.statusId) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = CrimsonDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete", color = CrimsonDanger, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
