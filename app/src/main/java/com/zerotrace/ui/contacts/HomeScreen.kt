package com.zerotrace.ui.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.zerotrace.bridge.CallMediaKind
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.SessionRatchetMetrics
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.network.ConnectivityState
import com.zerotrace.network.TorTransportBackend
import com.zerotrace.security.ChatStorageBreakdown
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.ConversationFilter
import com.zerotrace.ui.ZeroTraceScreen
import com.zerotrace.ui.components.ChatListItem
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.EmptyState
import com.zerotrace.ui.components.ErrorState
import com.zerotrace.ui.components.SecurityInformationCard
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.settings.formatBytesCompact
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.MonospaceStyle
import com.zerotrace.ui.theme.ZeroTracePalette

/**
 * WhatsApp-Style Chats Tab (Screen 1 of Section 4)
 * Retains full ZeroTrace security controls (Tor circuit state, Emergency Wipe, QR verification)
 * while offering familiar search, filter chips, pinned/muted/archived conversations, and quick actions.
 */
@Composable
fun ZeroTraceHomeScreen(
    language: AppLanguage,
    identitySummary: PublicIdentitySummary?,
    connectivityState: ConnectivityState,
    torProgress: Int,
    torBootstrapSummary: String? = null,
    transportBackend: TorTransportBackend,
    verifiedContacts: List<VerifiedContactRecord>,
    pinnedOnions: Set<String>,
    mutedOnions: Set<String>,
    archivedOnions: Set<String>,
    unreadCountByOnion: Map<String, Int>,
    lastMessagePreviewByOnion: Map<String, String>,
    lastMessageTimeByOnion: Map<String, Long>,
    searchQuery: String,
    selectedFilter: ConversationFilter,
    showGroupChatMlsDialog: Boolean,
    activePeerContact: VerifiedContactRecord?,
    activeRatchetMetrics: SessionRatchetMetrics?,
    statusBanner: String?,
    errorBanner: String?,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (ConversationFilter) -> Unit,
    onShowGroupChatMlsDialog: (Boolean) -> Unit,
    onNavigate: (ZeroTraceScreen) -> Unit,
    onStartChatWithContact: (VerifiedContactRecord) -> Unit,
    onOpenContactProfile: (VerifiedContactRecord) -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    onToggleArchive: (String) -> Unit,
    onClearContactChatRam: (String) -> Unit,
    onScanLoopbackPeerNow: () -> Unit,
    onResumeActiveChat: () -> Unit,
    onEmergencyLock: () -> Unit,
    onRetryTor: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI
    var searchBarExpanded by remember { mutableStateOf(false) }

    val filteredContacts = remember(
        verifiedContacts,
        searchQuery,
        selectedFilter,
        pinnedOnions,
        archivedOnions,
        unreadCountByOnion
    ) {
        verifiedContacts
            .filter { c ->
                val matchesQuery = searchQuery.isBlank() ||
                    c.alias.contains(searchQuery, ignoreCase = true) ||
                    c.onionAddress.contains(searchQuery, ignoreCase = true) ||
                    c.classicalSafetyDigits.contains(searchQuery, ignoreCase = true)
                if (!matchesQuery) return@filter false

                when (selectedFilter) {
                    ConversationFilter.ALL -> c.onionAddress !in archivedOnions
                    ConversationFilter.UNREAD -> (unreadCountByOnion[c.onionAddress] ?: 0) > 0
                    ConversationFilter.PINNED -> c.onionAddress in pinnedOnions
                    ConversationFilter.VERIFIED -> !c.identityChangedWarning
                    ConversationFilter.ARCHIVED -> c.onionAddress in archivedOnions
                }
            }
            .sortedWith(
                compareByDescending<VerifiedContactRecord> { it.onionAddress in pinnedOnions }
                    .thenByDescending { lastMessageTimeByOnion[it.onionAddress] ?: (it.verifiedAtEpochSec * 1000L) }
            )
    }

    if (showGroupChatMlsDialog) {
        GroupChatMlsSecurityDialog(onDismiss = { onShowGroupChatMlsDialog(false) })
    }

    Scaffold(
        topBar = {
            ZeroTraceTopBar(
                title = "ZeroTrace",
                subtitle = "• TOR v3",
                connectivityState = connectivityState,
                torProgress = torProgress,
                onSearchToggle = { searchBarExpanded = !searchBarExpanded },
                onQrScannerClick = { onNavigate(ZeroTraceScreen.ADD_CONTACT) },
                onEmergencyWipeClick = onEmergencyLock
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(ZeroTraceScreen.ADD_CONTACT) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_new_secure_chat")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Encrypted Chat / Pair Peer"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("home_screen"),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Optional Search Field
            item {
                AnimatedVisibility(visible = searchBarExpanded || searchQuery.isNotBlank()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("chats_search_input"),
                        placeholder = {
                            Text(
                                if (isHi) "संपर्क, .onion पता या सुरक्षा अंक खोजें…"
                                else "Search contacts, .onion or safety digits…"
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            }

            // WhatsApp-style Borderless Filter Chips + Group Chat MLS Gate Chip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConversationFilter.entries.forEach { filter ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterSelected(filter) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            label = {
                                Text(
                                    text = if (isHi) filter.labelHi else filter.labelEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ZeroTracePalette.activePillContainer(),
                                selectedLabelColor = ZeroTracePalette.activePillContent(),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }

                    FilterChip(
                        selected = false,
                        onClick = { onShowGroupChatMlsDialog(true) },
                        shape = RoundedCornerShape(50),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        label = {
                            Text(
                                text = "Groups",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("group_chat_mls_chip")
                    )
                }
            }

            // Status or Error Banner
            if (statusBanner != null || errorBanner != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        if (errorBanner != null) {
                            ErrorState(
                                errorMessage = errorBanner,
                                onRetry = onRetryTor,
                                onDismiss = onDismissBanner
                            )
                        } else if (statusBanner != null) {
                            Surface(
                                color = ZeroTracePalette.mintContainer(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDismissBanner() }
                            ) {
                                Text(
                                    text = statusBanner,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZeroTracePalette.mintContent(),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Compact Tor Circuit & Identity Strip + Quick Actions
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TorConnectivityStrip(
                        language = language,
                        state = connectivityState,
                        progress = torProgress,
                        bootstrapSummary = torBootstrapSummary,
                        backend = transportBackend,
                        onionAddress = identitySummary?.onionAddress,
                        onMyQrClick = { onNavigate(ZeroTraceScreen.MY_IDENTITY) },
                        onAddContactClick = { onNavigate(ZeroTraceScreen.ADD_CONTACT) },
                        onRetryTor = onRetryTor,
                        onEmergencyLock = onEmergencyLock
                    )
                }
            }

            // Active Volatile Session Resume Card (if currently established in RAM)
            if (activePeerContact != null && activeRatchetMetrics != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onResumeActiveChat() }
                            .testTag("active_session_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHi) "सक्रिय RAM-केवल E2EE सत्र" else "LIVE VOLATILE PQ-RATCHET SESSION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = activePeerContact.alias,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Epoch ${activeRatchetMetrics.ratchetEpoch} • PQ Braids: ${activeRatchetMetrics.pqBraidStepsCompleted} • RAM Msgs: ${activeRatchetMetrics.volatileMessagesInRam}",
                                    style = MonospaceStyle.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Button(
                                onClick = onResumeActiveChat,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Conversations List or Empty State
            if (filteredContacts.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        EmptyState(
                            icon = Icons.Default.QrCodeScanner,
                            title = if (isHi) "कोई सत्यापित बातचीत नहीं" else "No Verified Conversations Yet",
                            description = if (isHi) {
                                "ZeroTrace में कोई केंद्रीय सर्वर या फ़ोन नंबर निर्देशिका नहीं है। QR कोड स्कैन करके या लूपबैक सत्यापन पीयर जोड़कर पोस्ट-क्वांटम E2EE चैट शुरू करें।"
                            } else {
                                "ZeroTrace has no central server or phone-number directory. Pair via signed ZT2|v1 QR code or pair the built-in Loopback Verification Peer to test full hybrid ML-KEM-1024 + Double Ratchet messaging."
                            },
                            primaryActionLabel = if (isHi) "लूपबैक सत्यापन पीयर जोड़ें" else "Pair Loopback Verification Peer",
                            onPrimaryAction = onScanLoopbackPeerNow,
                            secondaryActionLabel = if (isHi) "QR स्कैनर / आमंत्रण खोलें" else "Open QR Pairing & Scanner",
                            onSecondaryAction = { onNavigate(ZeroTraceScreen.ADD_CONTACT) }
                        )
                    }
                }
            } else {
                items(filteredContacts, key = { it.onionAddress }) { contact ->
                    val isLive = activePeerContact?.onionAddress == contact.onionAddress
                    val preview = lastMessagePreviewByOnion[contact.onionAddress]
                        ?: if (isLive) "Active hybrid PQ ratchet session in RAM"
                        else "Tap to initiate ZT-PQXDH-P2P-v1 handshake over Tor"
                    val timestamp = lastMessageTimeByOnion[contact.onionAddress]
                        ?: (contact.verifiedAtEpochSec * 1000L)
                    val unread = unreadCountByOnion[contact.onionAddress] ?: 0

                    ChatListItem(
                        contact = contact,
                        lastMessagePreview = preview,
                        lastMessageTimeMillis = timestamp,
                        unreadCount = unread,
                        isPinned = contact.onionAddress in pinnedOnions,
                        isMuted = contact.onionAddress in mutedOnions,
                        isSessionLive = isLive,
                        onClick = { onStartChatWithContact(contact) },
                        onProfileClick = { onOpenContactProfile(contact) },
                        onTogglePin = { onTogglePin(contact.onionAddress) },
                        onToggleMute = { onToggleMute(contact.onionAddress) },
                        onToggleArchive = { onToggleArchive(contact.onionAddress) },
                        onClearChatRam = { onClearContactChatRam(contact.onionAddress) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        modifier = Modifier.padding(start = 78.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TorConnectivityStrip(
    language: AppLanguage,
    state: ConnectivityState,
    progress: Int,
    bootstrapSummary: String?,
    backend: TorTransportBackend,
    onionAddress: String?,
    onMyQrClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onRetryTor: () -> Unit,
    onEmergencyLock: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI
    val statusColor = when (state) {
        ConnectivityState.ONION_SERVICE_READY,
        ConnectivityState.E2EE_SESSION_READY,
        ConnectivityState.TOR_READY -> MaterialTheme.colorScheme.primary
        ConnectivityState.ERROR,
        ConnectivityState.OFFLINE -> CrimsonDanger
        else -> MaterialTheme.colorScheme.tertiary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tor_status_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (progress in 1..99 || state == ConnectivityState.ERROR) {
            Surface(
                color = ZeroTracePalette.mintContainer(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (state == ConnectivityState.ERROR) {
                            bootstrapSummary ?: "Tor circuit disconnected • Tap retry"
                        } else {
                            "${if (isHi) state.labelHi else state.labelEn} ($progress%)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ZeroTracePalette.mintContent(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRetryTor,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("retry_tor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reconnect Tor",
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Exact Mockup Screen 1: Side-by-Side QR & Pairing + Pair Contact Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onMyQrClick,
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("home_my_qr_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isHi) "QR व पेयरिंग" else "QR & Pairing",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = if (isHi) "मेरा QR आमंत्रण" else "My QR Invite",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            Button(
                onClick = onAddContactClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("home_add_contact_button")
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isHi) "संपर्क जोड़ें" else "Pair Contact",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Screen 3 of Section 4: Contact Profile Screen
 * Displays verified identity fingerprints, safety numbers, quick Chat / Voice Call / Video Call actions,
 * shared encrypted files in RAM, disappearing timer, block/delete contact, and session termination.
 */
@Composable
fun ContactProfileScreen(
    contact: VerifiedContactRecord,
    connectivityState: ConnectivityState,
    torProgress: Int,
    sharedMessagesInRam: List<RichConversationMessage>,
    disappearingTimerSec: Long,
    isBlocked: Boolean,
    isKeepDataEnabled: Boolean = false,
    storageBreakdown: ChatStorageBreakdown? = null,
    statusBanner: String? = null,
    onDismissBanner: () -> Unit = {},
    onToggleKeepData: (Boolean) -> Unit = {},
    onMoveRamToRom: () -> Unit = {},
    onMoveRomToRam: () -> Unit = {},
    onOpenStorageManager: () -> Unit = {},
    onOpenMedia: (RichConversationMessage) -> Unit = {},
    onSaveMedia: (RichConversationMessage) -> Unit = {},
    onBack: () -> Unit,
    onStartChat: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onReverifyQr: () -> Unit,
    onToggleBlock: () -> Unit,
    onClearChatHistoryRam: () -> Unit,
    onDeleteContact: () -> Unit
) {
    val allMediaMessages = remember(sharedMessagesInRam) {
        sharedMessagesInRam
            .filter { !it.isDeletedPlaceholder && it.fileTransfer != null }
            .sortedByDescending { it.timestampEpochMillis }
    }
    val encryptedFilesCount = allMediaMessages.size
    var mediaFilterTab by remember { mutableStateOf(0) } // 0 = All, 1 = Photos & Videos, 2 = Docs & Audio
    val filteredMediaMessages = remember(allMediaMessages, mediaFilterTab) {
        when (mediaFilterTab) {
            1 -> allMediaMessages.filter {
                it.payloadKind == MessagePayloadKind.IMAGE || it.payloadKind == MessagePayloadKind.VIDEO
            }
            2 -> allMediaMessages.filter {
                it.payloadKind == MessagePayloadKind.DOCUMENT ||
                    it.payloadKind == MessagePayloadKind.AUDIO_FILE ||
                    it.payloadKind == MessagePayloadKind.VOICE_NOTE
            }
            else -> allMediaMessages
        }
    }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copiedPeerId by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Contact Info",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("contact_profile_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Mockup Contact Header: Avatar, Alias, Peer ID Mint Box, Call + Message Buttons
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContactAvatar(
                        alias = contact.alias,
                        onionAddress = contact.onionAddress,
                        isVerified = !contact.identityChangedWarning,
                        avatarBytes = contact.avatarBytes,
                        size = 86.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.alias,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Online • Peer-${contact.onionAddress.take(6).uppercase()} • TOR v3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (contact.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = contact.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Soft Mint Peer ID & Safety Number Box (Mockup)
                    Surface(
                        color = ZeroTracePalette.mintContainer(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Peer ID: ${contact.onionAddress}",
                                    style = MonospaceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = ZeroTracePalette.mintContent(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Safety Number: ${contact.classicalSafetyDigits} • Verified ✓",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZeroTracePalette.mintContent()
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(contact.onionAddress))
                                    copiedPeerId = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = if (copiedPeerId) "Copied Peer ID" else "Copy Peer ID",
                                    tint = ZeroTracePalette.mintContent()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Primary 2-Button Action Row (Call & Message matching mockup)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStartVoiceCall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("profile_voice_call_btn")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Call", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onStartChat,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ZeroTracePalette.isLight()) Color(0xFF64748B) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("profile_start_chat_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Message", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStartVideoCall,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_video_call_btn")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Video Call")
                        }
                        OutlinedButton(
                            onClick = onReverifyQr,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("profile_reverify_btn")
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-Verify QR")
                        }
                    }
                }
            }

            // Dual Classical + Post-Quantum Fingerprints
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dual Safety Numbers & Fingerprints",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Classical (Ed25519 + X25519) Safety Digits:\n${contact.classicalSafetyDigits}",
                            style = MonospaceStyle.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Post-Quantum (ML-DSA-87 + ML-KEM-1024) Safety Digits:\n${contact.pqSafetyDigits}",
                            style = MonospaceStyle.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "SHA-256 Classical FP: ${contact.classicalFingerprintHex.take(32)}…\nSHA-256 PQ FP: ${contact.pqFingerprintHex.take(32)}…",
                            style = MonospaceStyle.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!statusBanner.isNullOrBlank()) {
                item {
                    Surface(
                        color = ZeroTracePalette.mintContainer(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissBanner() }
                    ) {
                        Text(
                            text = statusBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZeroTracePalette.mintContent(),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // Per-Chat Keep Data (Ultra-Encrypted ROM Vault) & RAM -> ROM Migration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isKeepDataEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.EnhancedEncryption,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Keep Data (Encrypted ROM Vault)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isKeepDataEnabled) {
                                        "ON • Dual-cipher ChaCha20-Poly1305 (256-bit) + StrongBox AES-256-GCM ROM encryption."
                                    } else {
                                        "OFF • Chat & media stay in volatile RAM only. Turn ON to keep & encrypt in ROM."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isKeepDataEnabled,
                                onCheckedChange = onToggleKeepData,
                                modifier = Modifier.testTag("profile_keep_data_switch")
                            )
                        }

                        if (storageBreakdown != null) {
                            Surface(
                                color = ZeroTracePalette.mintContainer(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "RAM: ${formatBytesCompact(storageBreakdown.ramTotalBytes)} (${storageBreakdown.ramMessageCount}m)",
                                        style = MonospaceStyle.copy(fontSize = 11.sp),
                                        color = Color(0xFF0284C7)
                                    )
                                    Text(
                                        text = "ROM: ${formatBytesCompact(storageBreakdown.romTotalBytes)} (${storageBreakdown.romMessageCount}m)",
                                        style = MonospaceStyle.copy(fontSize = 11.sp),
                                        color = ZeroTracePalette.mintContent()
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onMoveRamToRom,
                                enabled = sharedMessagesInRam.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_move_ram_to_rom_btn")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.DriveFileMove,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Move RAM -> ROM",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = if ((storageBreakdown?.romTotalBytes ?: 0L) > 0L) onMoveRomToRam else onOpenStorageManager,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SdStorage, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if ((storageBreakdown?.romTotalBytes ?: 0L) > 0L) "Move ROM -> RAM" else "Storage & Data",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenStorageManager,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SdStorage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Full Storage & Data Manager", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Sent & Received Media, Docs & Voice Gallery (visible directly on Chat Profile!)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Media, Docs & Voice",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$encryptedFilesCount file(s)",
                                style = MonospaceStyle.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to "All (${allMediaMessages.size})",
                                1 to "Photos & Videos",
                                2 to "Docs & Audio"
                            ).forEach { (idx, label) ->
                                FilterChip(
                                    selected = mediaFilterTab == idx,
                                    onClick = { mediaFilterTab = idx },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(50),
                                    border = null
                                )
                            }
                        }

                        if (filteredMediaMessages.isEmpty()) {
                            Text(
                                text = "No sent or received media in this chat yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            filteredMediaMessages.take(40).forEach { mediaMsg ->
                                val tr = mediaMsg.fileTransfer ?: return@forEach
                                val bytes = tr.decryptedInMemoryBytes
                                val thumb = if (mediaMsg.payloadKind == MessagePayloadKind.IMAGE && bytes != null) {
                                    remember(mediaMsg.id, bytes.size) {
                                        try {
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                        } catch (_: Throwable) {
                                            null
                                        }
                                    }
                                } else null

                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenMedia(mediaMsg) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ZeroTracePalette.mintContainer()),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (thumb != null) {
                                                Image(
                                                    bitmap = thumb,
                                                    contentDescription = tr.manifest.sanitizedFileName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                val icon = when (mediaMsg.payloadKind) {
                                                    MessagePayloadKind.IMAGE -> Icons.Default.Image
                                                    MessagePayloadKind.VIDEO -> Icons.Default.PlayArrow
                                                    MessagePayloadKind.AUDIO_FILE,
                                                    MessagePayloadKind.VOICE_NOTE -> Icons.Default.AudioFile
                                                    else -> Icons.Default.Description
                                                }
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = ZeroTracePalette.mintContent(),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tr.manifest.sanitizedFileName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val dirLabel = if (mediaMsg.isOutgoing) "Sent" else "Received"
                                            val locLabel = if (tr.isStoredInRom) "ROM" else "RAM"
                                            Text(
                                                text = "$dirLabel • ${formatBytesCompact(tr.manifest.totalSizeBytes.toLong())} • $locLabel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { onSaveMedia(mediaMsg) }) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Save to Gallery",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Inactivity RAM Auto-Purge Timer",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = if (disappearingTimerSec <= 0L) {
                                    "Forever (Encrypted Vault)"
                                } else {
                                    "${disappearingTimerSec / 60} min"
                                },
                                style = MonospaceStyle.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Danger Zone: Wipe Chat RAM, Block Peer, Delete Contact
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearChatHistoryRam,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wipe Volatile Chat & File Buffers in RAM", color = MaterialTheme.colorScheme.tertiary)
                        }

                        OutlinedButton(
                            onClick = onToggleBlock,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = CrimsonDanger)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBlocked) "Unblock Peer .onion" else "Block Peer .onion Endpoint",
                                color = CrimsonDanger
                            )
                        }

                        Button(
                            onClick = onDeleteContact,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonDanger,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Contact from Wrapped Store")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section 10: Group Chats Architecture Evaluation Dialog
 */
@Composable
fun GroupChatMlsSecurityDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        },
        title = {
            Text(
                text = "Group Chats: MLS / Sender Keys Gate",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Why 1-to-1 Double Ratchet is NOT reused for Groups:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "A 1-to-1 Hybrid Double Ratchet + ML-KEM-1024 Braid session derives symmetric chains strictly between two verified key bundles. Reusing a pairwise session for multi-party fanout breaks transcript consistency, forward secrecy on member removal, and out-of-order tree ratcheting.",
                    style = MaterialTheme.typography.bodySmall
                )
                SecurityInformationCard(
                    title = "Planned Group Protocol (Zero-Server)",
                    protocolSuite = "MLS (RFC 9420) + Hybrid PQ TreeKEM (ML-KEM-1024 + X25519)",
                    torRoutingNote = "Decentralized P2P commit distribution over Tor v3 Onion streams",
                    storagePolicyNote = "Status: PLANNED — Disabled until full multi-party TreeKEM epoch recovery is verified."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Understood (Keep Fail-Closed)")
            }
        }
    )
}
