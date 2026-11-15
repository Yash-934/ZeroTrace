package com.zerotrace.ui.updates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.StatusPrivacyPolicy
import com.zerotrace.network.ConnectivityState
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.EmptyState
import com.zerotrace.ui.components.SecurityInformationCard
import com.zerotrace.ui.components.StatusCard
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.CyanAccent
import com.zerotrace.ui.theme.EmeraldPrimary
import com.zerotrace.ui.theme.MonospaceStyle

/**
 * Screen 4 of Section 4: Updates Tab (Peer-to-Peer Ephemeral Status)
 * Honest Security Model (Section 8):
 * - No central status server is used.
 * - Status availability depends on live peer-to-peer Tor connectivity.
 * - Never promises 24-hour cloud persistence while offline.
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
    onPublishStatus: (String, StatusMediaType) -> Unit,
    onOpenStatusViewer: (EphemeralStatusItem) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onEmergencyWipe: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI
    var showComposerDialog by remember { mutableStateOf(false) }

    val ownStatuses = remember(statuses) { statuses.filter { it.isOwnStatus } }
    val recentPeerStatuses = remember(statuses) { statuses.filter { !it.isOwnStatus && !it.viewedByLocalUser } }
    val viewedPeerStatuses = remember(statuses) { statuses.filter { !it.isOwnStatus && it.viewedByLocalUser } }

    if (showComposerDialog) {
        CreateEphemeralStatusDialog(
            onPublish = { caption, mediaType ->
                onPublishStatus(caption, mediaType)
                showComposerDialog = false
            },
            onDismiss = { showComposerDialog = false }
        )
    }

    Scaffold(
        topBar = {
            ZeroTraceTopBar(
                title = if (isHi) "अपडेट (P2P स्थिति)" else "Updates",
                subtitle = "Peer-to-Peer Ephemeral Status • Live Tor RAM Only",
                connectivityState = connectivityState,
                torProgress = torProgress,
                onEmergencyWipeClick = onEmergencyWipe
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showComposerDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
        ) {
            // Honest P2P Ephemeral Status Security Architecture Card
            item {
                SecurityInformationCard(
                    title = "Peer-to-Peer Ephemeral Status (Zero Central Server)",
                    protocolSuite = "ChaCha20-Poly1305 + ML-DSA-87 Signed Status Frame",
                    torRoutingNote = "Shared only with verified peers during active Tor Onion sessions",
                    storagePolicyNote = "No 24h cloud server cache: status updates live strictly in volatile RAM and vanish when sessions close."
                )
            }

            // Status Audience Privacy Selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Privacy:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPrivacyPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = privacyPolicy == policy,
                            onClick = { onPrivacyPolicyChange(policy) },
                            label = {
                                Text(
                                    text = if (policy == StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS) {
                                        "All Verified Contacts"
                                    } else {
                                        "Selected Peers Only"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // My Status Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                size = 52.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isHi) "मेरी स्थिति (My Status)" else "My Ephemeral Status",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (ownStatuses.isEmpty()) {
                                        "Tap + to broadcast an encrypted RAM status to live peers"
                                    } else {
                                        "${ownStatuses.size} active status card(s) in volatile RAM"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = { showComposerDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("add_my_status_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (ownStatuses.isNotEmpty()) {
                item {
                    Text(
                        text = "MY ACTIVE RAM STATUSES",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldPrimary
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
                        title = "No Peer Status Updates in RAM",
                        description = "When a verified contact shares an ephemeral status over an active Tor Onion session, it appears here in volatile memory.",
                        primaryActionLabel = "Create My Ephemeral Status",
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
    onPublish: (String, StatusMediaType) -> Unit,
    onDismiss: () -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(StatusMediaType.TEXT_CARD) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Ephemeral P2P Status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Encrypted with ChaCha20-Poly1305 and held strictly in volatile RAM. Disappears immediately on session close or Emergency Wipe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == StatusMediaType.TEXT_CARD,
                        onClick = { selectedType = StatusMediaType.TEXT_CARD },
                        label = { Text("Text Status") }
                    )
                    FilterChip(
                        selected = selectedType == StatusMediaType.IMAGE_CARD,
                        onClick = { selectedType = StatusMediaType.IMAGE_CARD },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Encrypted Image Card") }
                    )
                }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("Write an ephemeral status message…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_caption_input"),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val text = caption.ifBlank { "Tor v3 Hidden Service active • Post-Quantum Hybrid E2EE verified." }
                    onPublish(text, selectedType)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldPrimary,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("publish_status_confirm_btn")
            ) {
                Text("Broadcast to Peers", fontWeight = FontWeight.Bold)
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
    onDeleteOwnStatus: (String) -> Unit
) {
    BackHandler(onBack = onBack)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A2E24), Color(0xFF06080C))
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Progress & Author Bar
                Column {
                    LinearProgressIndicator(
                        progress = { 1f },
                        color = EmeraldPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    text = "${statusItem.authorOnion.take(18)}…onion • Volatile RAM",
                                    style = MonospaceStyle.copy(fontSize = 10.sp),
                                    color = CyanAccent
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

                // Center Status Card Content
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.38f)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "E2EE PEER-TO-PEER STATUS CARD",
                                style = MonospaceStyle.copy(fontSize = 11.sp),
                                color = EmeraldPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = statusItem.captionText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Expires automatically when session closes • Zero server storage",
                            style = MonospaceStyle.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.7f)
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
                            placeholder = { Text("Reply over encrypted Tor ratchet…") },
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
                                .background(EmeraldPrimary, RoundedCornerShape(24.dp))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Encrypted Status Reply",
                                tint = Color.Black
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
