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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotrace.bridge.CallMediaKind
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.SessionRatchetMetrics
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.network.ConnectivityState
import com.zerotrace.network.TorTransportBackend
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.ConversationFilter
import com.zerotrace.ui.ZeroTraceScreen
import com.zerotrace.ui.components.ChatListItem
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.EmptyState
import com.zerotrace.ui.components.ErrorState
import com.zerotrace.ui.components.SecurityInformationCard
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.theme.AmberWarning
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.CyanAccent
import com.zerotrace.ui.theme.EmeraldPrimary
import com.zerotrace.ui.theme.MonospaceStyle

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
                subtitle = if (isHi) "TOR v3 • पोस्ट-क्वांटम हाइब्रिड E2EE" else "TOR v3 • PQ-HYBRID RAM MESSENGER",
                connectivityState = connectivityState,
                torProgress = torProgress,
                onSearchToggle = { searchBarExpanded = !searchBarExpanded },
                onQrScannerClick = { onNavigate(ZeroTraceScreen.ADD_CONTACT) },
                onSecurityDashboardClick = { onNavigate(ZeroTraceScreen.SECURITY_DASHBOARD) },
                onEmergencyWipeClick = onEmergencyLock
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(ZeroTraceScreen.ADD_CONTACT) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
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

            // WhatsApp-style Filter Chips + Group Chat MLS Gate Chip
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
                            label = {
                                Text(
                                    text = if (isHi) filter.labelHi else filter.labelEn,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }

                    FilterChip(
                        selected = false,
                        onClick = { onShowGroupChatMlsDialog(true) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AmberWarning
                            )
                        },
                        label = {
                            Text(
                                text = "Groups (MLS Gate)",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberWarning
                            )
                        },
                        modifier = Modifier.testTag("group_chat_mls_chip")
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
                                color = EmeraldPrimary.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDismissBanner() }
                            ) {
                                Text(
                                    text = statusBanner,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldPrimary,
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
                                    color = CyanAccent
                                )
                            }
                            Button(
                                onClick = onResumeActiveChat,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
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
        ConnectivityState.TOR_READY -> EmeraldPrimary
        ConnectivityState.ERROR,
        ConnectivityState.OFFLINE -> CrimsonDanger
        else -> AmberWarning
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tor_status_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val headerText = if (progress in 1..99) {
                            "${if (isHi) state.labelHi else state.labelEn} ($progress%)"
                        } else {
                            if (isHi) state.labelHi else state.labelEn
                        }
                        Text(
                            text = headerText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val detailLine = when {
                            !bootstrapSummary.isNullOrBlank() &&
                                (state == ConnectivityState.INITIALIZING ||
                                    state == ConnectivityState.TOR_BOOTSTRAPPING ||
                                    state == ConnectivityState.ONION_SERVICE_STARTING) ->
                                bootstrapSummary
                            onionAddress != null -> onionAddress
                            else -> if (isHi) backend.labelHi else backend.labelEn
                        }
                        Text(
                            text = detailLine,
                            style = MonospaceStyle.copy(fontSize = 10.sp),
                            color = CyanAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRetryTor,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("retry_tor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reconnect Tor",
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEmergencyLock,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("emergency_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Emergency Lock & Wipe",
                            tint = CrimsonDanger,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    color = statusColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMyQrClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_my_qr_button")
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isHi) "मेरा QR" else "My QR Invite", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onAddContactClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_add_contact_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isHi) "संपर्क जोड़ें" else "Pair Contact",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
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
    onBack: () -> Unit,
    onStartChat: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onReverifyQr: () -> Unit,
    onToggleBlock: () -> Unit,
    onClearChatHistoryRam: () -> Unit,
    onDeleteContact: () -> Unit
) {
    val encryptedFilesCount = sharedMessagesInRam.count { it.fileTransfer != null }

    Scaffold(
        topBar = {
            ZeroTraceTopBar(
                title = "Contact Security Profile",
                subtitle = "Verified Peer Identity & Fingerprints",
                connectivityState = connectivityState,
                torProgress = torProgress,
                onBackClick = onBack
            )
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
            // Header Avatar + Alias + Action Row
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContactAvatar(
                            alias = contact.alias,
                            onionAddress = contact.onionAddress,
                            isVerified = !contact.identityChangedWarning,
                            size = 82.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
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
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = contact.onionAddress,
                            style = MonospaceStyle.copy(fontSize = 11.sp),
                            color = CyanAccent
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileActionPill(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                label = "Message",
                                tint = EmeraldPrimary,
                                tag = "profile_start_chat_btn",
                                onClick = onStartChat
                            )
                            ProfileActionPill(
                                icon = Icons.Default.Call,
                                label = "Audio Call",
                                tint = CyanAccent,
                                tag = "profile_voice_call_btn",
                                onClick = onStartVoiceCall
                            )
                            ProfileActionPill(
                                icon = Icons.Default.Videocam,
                                label = "Video Call",
                                tint = CyanAccent,
                                tag = "profile_video_call_btn",
                                onClick = onStartVideoCall
                            )
                            ProfileActionPill(
                                icon = Icons.Default.QrCode,
                                label = "Re-Verify",
                                tint = AmberWarning,
                                tag = "profile_reverify_btn",
                                onClick = onReverifyQr
                            )
                        }
                    }
                }
            }

            // Dual Classical + Post-Quantum Fingerprints
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
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
                                tint = EmeraldPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dual Safety Numbers & Fingerprints",
                                style = MaterialTheme.typography.titleSmall,
                                color = EmeraldPrimary
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
                            color = CyanAccent
                        )
                        Text(
                            text = "SHA-256 Classical FP: ${contact.classicalFingerprintHex.take(32)}…\nSHA-256 PQ FP: ${contact.pqFingerprintHex.take(32)}…",
                            style = MonospaceStyle.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Shared Encrypted Media & Disappearing Session Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Encrypted Attachments in Volatile RAM",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "$encryptedFilesCount file(s)",
                                style = MonospaceStyle.copy(fontSize = 12.sp),
                                color = EmeraldPrimary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Inactivity RAM Auto-Purge Timer",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = "${disappearingTimerSec / 60} min",
                                style = MonospaceStyle.copy(fontSize = 12.sp),
                                color = CyanAccent
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
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AmberWarning)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wipe Volatile Chat & File Buffers in RAM", color = AmberWarning)
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

@Composable
private fun ProfileActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f))
                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Section 10: Group Chats Architecture Evaluation Dialog
 * Explains why ZeroTrace refuses to fake group chats by reusing a 1-to-1 Double Ratchet session
 * and documents the required MLS (RFC 9420) / Sender Keys architecture over Tor.
 */
@Composable
fun GroupChatMlsSecurityDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = AmberWarning
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
                    color = AmberWarning
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
