package com.zerotrace.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.SessionRatchetMetrics
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.network.ConnectivityState
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.components.AttachmentPicker
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.MessageBubble
import com.zerotrace.ui.components.MessageComposer
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.theme.AmberWarning
import com.zerotrace.ui.theme.ChatWallpaperStyle
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.CyanAccent
import com.zerotrace.ui.theme.EmeraldPrimary
import com.zerotrace.ui.theme.MonospaceStyle
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

/**
 * WhatsApp-Style Chat Conversation Screen (Screen 2 of Section 4)
 * Features:
 * - Top bar with Back button, Contact Avatar, Verified Badge, Tor & Ratchet status, Voice Call, Video Call, Search, More menu
 * - Top E2EE security banner with tap-to-verify fingerprints
 * - Rich MessageBubbles with replies, reactions, voice notes, and chunked ChaCha20-Poly1305 file transfers
 * - Collapsible AttachmentPicker & MessageComposer
 * - Leaving or pressing Wipe immediately zeroizes the volatile chat buffer in RAM
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EphemeralChatScreen(
    language: AppLanguage,
    peerContact: VerifiedContactRecord?,
    connectivityState: ConnectivityState,
    isPeerMeshConnected: Boolean,
    richMessages: List<RichConversationMessage>,
    ratchetMetrics: SessionRatchetMetrics?,
    replyingToMessage: RichConversationMessage?,
    inspectedMessageInfo: RichConversationMessage?,
    showAttachmentSheet: Boolean,
    isRecordingVoiceNote: Boolean,
    voiceRecordingSeconds: Int = 0,
    voiceRecordingAmplitude: Float = 0f,
    playingVoiceMessageId: String? = null,
    inChatSearchQuery: String,
    wallpaperStyle: ChatWallpaperStyle,
    statusBanner: String?,
    errorBanner: String?,
    onInChatSearchChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onReplySelected: (RichConversationMessage?) -> Unit,
    onReactMessage: (RichConversationMessage, String) -> Unit,
    onDeleteMessageFromRam: (RichConversationMessage) -> Unit,
    onInspectMessageInfo: (RichConversationMessage?) -> Unit,
    onOpenMediaPreview: (RichConversationMessage) -> Unit,
    onShowAttachmentSheet: (Boolean) -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit = {},
    onToggleVoicePlay: (RichConversationMessage) -> Unit = {},
    onPickMediaUri: (Uri, MessagePayloadKind) -> Unit = { _, _ -> },
    onCapturedCameraBitmap: (Bitmap) -> Unit = {},
    onSendEncryptedAttachment: (MessagePayloadKind, String, String, ByteArray, String) -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onOpenContactProfile: () -> Unit,
    onTriggerPqBraidStep: () -> Unit,
    onReconnectPeer: () -> Unit,
    onBackToChats: () -> Unit,
    onDisconnectAndWipeSession: () -> Unit
) {
    BackHandler(onBack = onBackToChats)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isSessionLive = (ratchetMetrics != null && isPeerMeshConnected) ||
        connectivityState == ConnectivityState.E2EE_SESSION_READY
    val isHi = language == AppLanguage.HINDI
    var draftText by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var showSecuritySheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickMediaUri(uri, MessagePayloadKind.IMAGE)
        }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickMediaUri(uri, MessagePayloadKind.VIDEO)
        }
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onPickMediaUri(uri, MessagePayloadKind.DOCUMENT)
        }
    }
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onPickMediaUri(uri, MessagePayloadKind.AUDIO_FILE)
        }
    }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            onCapturedCameraBitmap(bitmap)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraCaptureLauncher.launch(null)
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleVoiceRecording()
        }
    }

    val displayedMessages = remember(richMessages, inChatSearchQuery) {
        if (inChatSearchQuery.isBlank()) {
            richMessages
        } else {
            richMessages.filter {
                it.bodyText.contains(inChatSearchQuery, ignoreCase = true) ||
                    (it.fileTransfer?.manifest?.sanitizedFileName?.contains(inChatSearchQuery, ignoreCase = true) == true)
            }
        }
    }

    val listState = rememberLazyListState()
    val totalItemsCount by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount <= 2 || lastVisible >= (totalItemsCount - 2).coerceAtLeast(0)
        }
    }
    var previousMsgCount by remember { mutableIntStateOf(displayedMessages.size) }
    var unseenIncomingCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (displayedMessages.isNotEmpty()) {
            listState.scrollToItem(displayedMessages.size + 1)
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            unseenIncomingCount = 0
        }
    }

    LaunchedEffect(displayedMessages.size) {
        val newSize = displayedMessages.size
        if (newSize > previousMsgCount) {
            val added = displayedMessages.subList(previousMsgCount, newSize)
            val lastIsOutgoing = added.lastOrNull()?.isOutgoing == true
            if (isAtBottom || lastIsOutgoing) {
                val targetIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                listState.animateScrollToItem(targetIndex)
                unseenIncomingCount = 0
            } else {
                unseenIncomingCount += added.count { !it.isOutgoing }.coerceAtLeast(1)
            }
        } else if (newSize < previousMsgCount) {
            unseenIncomingCount = 0
        }
        previousMsgCount = newSize
    }

    if (inspectedMessageInfo != null) {
        MessageCryptographicInfoDialog(
            message = inspectedMessageInfo,
            sessionIdHex = ratchetMetrics?.sessionIdHex ?: "VOLATILE-PQ-SESSION",
            onDismiss = { onInspectMessageInfo(null) }
        )
    }

    if (showSecuritySheet && peerContact != null) {
        ChatSessionSecurityDialog(
            contact = peerContact,
            metrics = ratchetMetrics,
            onOpenFullProfile = {
                showSecuritySheet = false
                onOpenContactProfile()
            },
            onDismiss = { showSecuritySheet = false }
        )
    }

    val wallpaperColor = when (wallpaperStyle) {
        ChatWallpaperStyle.OBSIDIAN_GRID -> MaterialTheme.colorScheme.background
        ChatWallpaperStyle.DEEP_EMERALD_MESH -> Color(0xFF061510)
        ChatWallpaperStyle.PURE_BLACK_AMOLED -> Color(0xFF000000)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenContactProfile() }
                    ) {
                        IconButton(
                            onClick = onBackToChats,
                            modifier = Modifier.testTag("chat_back_wipe_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Chats"
                            )
                        }
                        ContactAvatar(
                            alias = peerContact?.alias ?: "Peer",
                            onionAddress = peerContact?.onionAddress ?: "peer.onion",
                            isOnlineOrSessionActive = isSessionLive,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = peerContact?.alias ?: "Verified Peer",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = "Verified",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = when {
                                    isSessionLive ->
                                        "P2P E2EE Live • Epoch ${ratchetMetrics?.ratchetEpoch ?: 1} • Braids ${ratchetMetrics?.pqBraidStepsCompleted ?: 0}"
                                    else ->
                                        "Connecting Tor .onion / P2P… (Tap Reconnect)"
                                },
                                style = MonospaceStyle.copy(fontSize = 10.sp),
                                color = if (isSessionLive) EmeraldPrimary else AmberWarning,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (!isSessionLive) {
                                    Modifier.clickable { onReconnectPeer() }
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onStartVoiceCall,
                            modifier = Modifier.testTag("chat_voice_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Encrypted Voice Call over Tor",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onStartVideoCall,
                            modifier = Modifier.testTag("chat_video_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Encrypted Video Call over Tor",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onTriggerPqBraidStep,
                            modifier = Modifier.testTag("chat_pq_rekey_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = "Manual ML-KEM-1024 Braid Step",
                                tint = CyanAccent
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { moreMenuExpanded = true },
                                modifier = Modifier.testTag("chat_more_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Conversation Menu"
                                )
                            }
                            DropdownMenu(
                                expanded = moreMenuExpanded,
                                onDismissRequest = { moreMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("View Contact Profile & Safety Numbers") },
                                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onOpenContactProfile()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Search in Volatile Conversation") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        searchVisible = !searchVisible
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Advance ML-KEM-1024 Braid Ratchet") },
                                    leadingIcon = { Icon(Icons.Default.Autorenew, contentDescription = null, tint = CyanAccent) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onTriggerPqBraidStep()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Destroy Session & Wipe RAM", color = CrimsonDanger, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.LinkOff, contentDescription = null, tint = CrimsonDanger) },
                                    onClick = {
                                        moreMenuExpanded = false
                                        onDisconnectAndWipeSession()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = wallpaperColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .testTag("chat_screen")
        ) {
            // Optional In-Chat Search Bar
            AnimatedVisibility(visible = searchVisible || inChatSearchQuery.isNotBlank()) {
                OutlinedTextField(
                    value = inChatSearchQuery,
                    onValueChange = onInChatSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = { Text("Filter volatile messages in RAM…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onInChatSearchChange("")
                                searchVisible = false
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp)
                )
            }

            if (errorBanner != null) {
                Surface(
                    color = CrimsonDanger.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorBanner,
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonDanger,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onReconnectPeer) {
                            Text("Reconnect", color = CrimsonDanger, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (statusBanner != null) {
                val showReconnectAction = !isSessionLive && (
                    statusBanner.contains("disconnect", ignoreCase = true) ||
                        statusBanner.contains("connect", ignoreCase = true) ||
                        statusBanner.contains("waiting", ignoreCase = true) ||
                        statusBanner.contains("कट", ignoreCase = true)
                    )
                Surface(
                    color = AmberWarning.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusBanner,
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarning,
                            modifier = Modifier.weight(1f)
                        )
                        if (showReconnectAction) {
                            TextButton(onClick = onReconnectPeer) {
                                Text("Reconnect", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Messages List + Floating Scroll-to-Bottom Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("chat_message_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // WhatsApp-style E2EE Notice Banner at top of conversation
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { showSecuritySheet = true }
                                .testTag("chat_security_notice_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isHi) {
                                            "पोस्ट-क्वांटम हाइब्रिड E2EE • केवल RAM सत्र"
                                        } else {
                                            "HYBRID PQ DOUBLE RATCHET • VOLATILE RAM ONLY"
                                        },
                                        style = MonospaceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                        color = EmeraldPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Messages and attachments are encrypted with X25519 + ML-KEM-1024 + ChaCha20-Poly1305 over Tor v3 Onion streams. Nothing is written to SQLite or disk. Tap to inspect safety numbers.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Date / Ephemeral Session Divider Pill
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "TODAY • SESSION ${ratchetMetrics?.sessionIdHex?.take(8) ?: "LIVE"} • Tx:${ratchetMetrics?.sendCount ?: 0} Rx:${ratchetMetrics?.recvCount ?: 0}",
                                    style = MonospaceStyle.copy(fontSize = 10.sp),
                                    color = CyanAccent,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    items(displayedMessages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            playingVoiceMessageId = playingVoiceMessageId,
                            onReplyClick = { onReplySelected(it) },
                            onReactClick = { target, emoji -> onReactMessage(target, emoji) },
                            onDeleteFromRamClick = { onDeleteMessageFromRam(it) },
                            onInspectInfoClick = { onInspectMessageInfo(it) },
                            onMediaPreviewClick = { onOpenMediaPreview(it) },
                            onVoicePlayToggle = onToggleVoicePlay
                        )
                    }
                }

                // Floating Jump-to-Latest Pill when scrolled up
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isAtBottom || unseenIncomingCount > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (unseenIncomingCount > 0) {
                            Surface(
                                color = EmeraldPrimary,
                                shape = CircleShape,
                                shadowElevation = 4.dp,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "$unseenIncomingCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        SmallFloatingActionButton(
                            onClick = {
                                unseenIncomingCount = 0
                                coroutineScope.launch {
                                    val lastIdx = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                    listState.animateScrollToItem(lastIdx)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = EmeraldPrimary,
                            modifier = Modifier.testTag("scroll_to_bottom_pill")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Scroll to latest messages"
                            )
                        }
                    }
                }
            }

            // Collapsible Attachment Picker Sheet -> launches real Android system pickers (Zero Disk Temp Files)
            AnimatedVisibility(visible = showAttachmentSheet) {
                AttachmentPicker(
                    onPickPhoto = {
                        onShowAttachmentSheet(false)
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickVideo = {
                        onShowAttachmentSheet(false)
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPickDocument = {
                        onShowAttachmentSheet(false)
                        documentPickerLauncher.launch(arrayOf("*/*"))
                    },
                    onPickAudio = {
                        onShowAttachmentSheet(false)
                        audioPickerLauncher.launch(arrayOf("audio/*"))
                    },
                    onPickContactCard = {
                        onShowAttachmentSheet(false)
                        val payload = ("ZT2-SIGNED-PEER-CARD|${peerContact?.alias ?: "Peer"}|" +
                            "${peerContact?.onionAddress ?: ""}|${peerContact?.classicalSafetyDigits ?: ""}")
                            .toByteArray(StandardCharsets.UTF_8)
                        onSendEncryptedAttachment(
                            MessagePayloadKind.CONTACT_CARD,
                            "verified_peer_card.txt",
                            "text/plain",
                            payload,
                            "Signed ZeroTrace Peer Card (${peerContact?.alias ?: "Peer"})"
                        )
                    },
                    onDismiss = { onShowAttachmentSheet(false) }
                )
            }

            // WhatsApp-style Message Composer with real Camera & AudioRecord permissions
            MessageComposer(
                draftText = draftText,
                onDraftChanged = { draftText = it },
                replyingToMessage = replyingToMessage,
                onCancelReply = { onReplySelected(null) },
                canSend = isSessionLive,
                isRecordingVoiceNote = isRecordingVoiceNote,
                recordingSeconds = voiceRecordingSeconds,
                recordingAmplitude = voiceRecordingAmplitude,
                onSendClick = {
                    val toSend = draftText.trim()
                    if (toSend.isNotEmpty()) {
                        draftText = ""
                        onSendMessage(toSend)
                    }
                },
                onAttachmentSheetClick = { onShowAttachmentSheet(!showAttachmentSheet) },
                onToggleVoiceRecording = {
                    if (isRecordingVoiceNote) {
                        onToggleVoiceRecording()
                    } else {
                        val hasAudioPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasAudioPerm) {
                            onToggleVoiceRecording()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onCancelVoiceRecording = onCancelVoiceRecording,
                onQuickCameraClick = {
                    val hasCamPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasCamPerm) {
                        cameraCaptureLauncher.launch(null)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            )
        }
    }
}

@Composable
private fun MessageCryptographicInfoDialog(
    message: RichConversationMessage,
    sessionIdHex: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldPrimary) },
        title = { Text("Cryptographic Message Telemetry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Session ID: ${sessionIdHex.take(16)}…\n" +
                        "Ratchet Chain Step: #${message.ratchetStepIndex}\n" +
                        "ML-KEM-1024 Braid Epoch: ${message.pqEpoch}\n" +
                        "AEAD Cipher: ChaCha20-Poly1305 (RFC 8439)\n" +
                        "Storage: Volatile Heap RAM Only (0x00 on close)",
                    style = MonospaceStyle.copy(fontSize = 11.sp)
                )
                val transfer = message.fileTransfer
                if (transfer != null) {
                    Text(
                        text = "Attachment File Key: Ephemeral 256-bit ChaCha20-Poly1305\n" +
                            "Sanitized Name: ${transfer.manifest.sanitizedFileName}\n" +
                            "MIME: ${transfer.manifest.mimeType}\n" +
                            "AEAD Chunks: ${transfer.manifest.totalChunks} (${transfer.manifest.totalSizeBytes} bytes)\n" +
                            "SHA-256 Digest: ${transfer.manifest.sha256DigestHex}",
                        style = MonospaceStyle.copy(fontSize = 10.sp),
                        color = CyanAccent
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ChatSessionSecurityDialog(
    contact: VerifiedContactRecord,
    metrics: SessionRatchetMetrics?,
    onOpenFullProfile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldPrimary) },
        title = { Text("Verified Post-Quantum Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Peer: ${contact.alias}\nOnion: ${contact.onionAddress}",
                    style = MonospaceStyle.copy(fontSize = 11.sp),
                    color = CyanAccent
                )
                Text(
                    text = "Classical Safety Digits:\n${contact.classicalSafetyDigits}",
                    style = MonospaceStyle.copy(fontSize = 11.sp)
                )
                Text(
                    text = "Post-Quantum Safety Digits (ML-DSA-87 / ML-KEM-1024):\n${contact.pqSafetyDigits}",
                    style = MonospaceStyle.copy(fontSize = 11.sp),
                    color = EmeraldPrimary
                )
                Text(
                    text = "Ratchet Epoch: ${metrics?.ratchetEpoch ?: 1} • PQ Braids: ${metrics?.pqBraidStepsCompleted ?: 0}",
                    style = MonospaceStyle.copy(fontSize = 11.sp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenFullProfile) {
                Text("Full Security Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Screen 12 of Section 4: Encrypted Media / Attachment Preview Screen
 * Inspects a decrypted attachment strictly in volatile RAM with SHA-256 integrity proof.
 */
@Composable
fun EncryptedMediaPreviewScreen(
    message: RichConversationMessage?,
    connectivityState: ConnectivityState,
    torProgress: Int,
    onBack: () -> Unit,
    onWipeFromRamAndClose: (RichConversationMessage) -> Unit
) {
    BackHandler(onBack = onBack)
    val transfer = message?.fileTransfer

    Scaffold(
        topBar = {
            ZeroTraceTopBar(
                title = transfer?.manifest?.sanitizedFileName ?: "Encrypted Attachment Preview",
                subtitle = "RAM-Only Decrypted Buffer • Zero Temp Files",
                connectivityState = connectivityState,
                torProgress = torProgress,
                onBackClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .testTag("media_preview_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (message == null || transfer == null) {
                Text("No encrypted attachment selected.")
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val decryptedBytes = transfer.decryptedInMemoryBytes
                    val previewBitmap = if (message.payloadKind == MessagePayloadKind.IMAGE && decryptedBytes != null) {
                        remember(transfer.manifest.transferId, decryptedBytes.size) {
                            try {
                                BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)?.asImageBitmap()
                            } catch (_: Throwable) {
                                null
                            }
                        }
                    } else {
                        null
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = transfer.manifest.sanitizedFileName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(340.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        } else {
                            Icon(
                                imageVector = if (message.payloadKind == MessagePayloadKind.IMAGE) {
                                    Icons.Default.Image
                                } else {
                                    Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        Text(
                            text = transfer.manifest.sanitizedFileName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "MIME: ${transfer.manifest.mimeType} • Size: ${transfer.manifest.totalSizeBytes} bytes • Chunks: ${transfer.manifest.totalChunks}",
                            style = MonospaceStyle.copy(fontSize = 12.sp),
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SHA-256 Integrity Verified:\n${transfer.manifest.sha256DigestHex}",
                            style = MonospaceStyle.copy(fontSize = 11.sp),
                            color = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Decrypted bytes reside exclusively in volatile JVM heap memory and are never written to MediaStore or disk cache.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { onWipeFromRamAndClose(message) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonDanger,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zeroize Attachment Buffer (0x00) & Remove")
                }
            }
        }
    }
}
