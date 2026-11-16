package com.zerotrace.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.SessionRatchetMetrics
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.media.MediaExportAndOpenHelper
import com.zerotrace.network.ConnectivityState
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.components.AttachmentPicker
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.MessageBubble
import com.zerotrace.ui.components.MessageComposer
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.theme.ChatWallpaperStyle
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.MonospaceStyle
import com.zerotrace.ui.theme.ZeroTracePalette
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

/**
 * WhatsApp-Style Chat Conversation Screen (Screen 2 of Section 4)
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
    onDeleteMessageForEveryone: (RichConversationMessage) -> Unit = {},
    onInspectMessageInfo: (RichConversationMessage?) -> Unit,
    onOpenMediaPreview: (RichConversationMessage) -> Unit,
    onSaveMediaToGallery: (RichConversationMessage) -> Unit = {},
    onOpenMediaExternally: (RichConversationMessage) -> Unit = {},
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

    val wallpaperColor = ZeroTracePalette.chatWallpaper(wallpaperStyle)

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (ZeroTracePalette.isLight()) 0.dp else 4.dp
            ) {
                Column {
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
                                avatarBytes = peerContact?.avatarBytes,
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
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        isSessionLive ->
                                            "Online • TOR v3 • E2E Encrypted"
                                        else ->
                                            "Connecting Tor .onion… (Tap Reconnect)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (isSessionLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
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
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Autorenew,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                        },
                                        modifier = Modifier.testTag("chat_pq_rekey_button"),
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
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
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
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
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        if (showReconnectAction) {
                            TextButton(onClick = onReconnectPeer) {
                                Text("Reconnect", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                    // Compact E2EE Notice Banner matching Mockup Screen 2
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = ZeroTracePalette.mintContainer(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .clickable { showSecuritySheet = true }
                                    .testTag("chat_security_notice_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = ZeroTracePalette.mintContent(),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isHi) {
                                            "पोस्ट-क्वांटम हाइब्रिड E2EE • केवल RAM सत्र"
                                        } else {
                                            "Post-Quantum E2EE over Tor v3 • RAM Only • Tap to verify"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZeroTracePalette.mintContent()
                                    )
                                }
                            }
                        }
                    }

                    // Date / Ephemeral Session Divider Pill
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "TODAY • EPOCH ${ratchetMetrics?.ratchetEpoch ?: 1} • Tx:${ratchetMetrics?.sendCount ?: 0} Rx:${ratchetMetrics?.recvCount ?: 0}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            onDeleteForEveryoneClick = { onDeleteMessageForEveryone(it) },
                            onInspectInfoClick = { onInspectMessageInfo(it) },
                            onMediaPreviewClick = { onOpenMediaPreview(it) },
                            onSaveMediaClick = { onSaveMediaToGallery(it) },
                            onOpenExternallyClick = { onOpenMediaExternally(it) },
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
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                shadowElevation = 4.dp,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "$unseenIncomingCount",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary,
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
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
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
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
                        color = MaterialTheme.colorScheme.secondary
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
        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Verified Post-Quantum Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Peer: ${contact.alias}\nOnion: ${contact.onionAddress}",
                    style = MonospaceStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Classical Safety Digits:\n${contact.classicalSafetyDigits}",
                    style = MonospaceStyle.copy(fontSize = 11.sp)
                )
                Text(
                    text = "Post-Quantum Safety Digits (ML-DSA-87 / ML-KEM-1024):\n${contact.pqSafetyDigits}",
                    style = MonospaceStyle.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.primary
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
 * Screen 12 of Section 4: Encrypted Media / Attachment Preview & Export Screen
 * Supports inline Image, inline VideoView playback (shredding temp cache on dispose),
 * Document preview + FileProvider external open, Audio playback, and scoped MediaStore Save to Gallery.
 */
@Composable
fun EncryptedMediaPreviewScreen(
    message: RichConversationMessage?,
    connectivityState: ConnectivityState,
    torProgress: Int,
    playingVoiceMessageId: String? = null,
    onToggleVoicePlay: (RichConversationMessage) -> Unit = {},
    onSaveToGallery: (RichConversationMessage) -> Unit = {},
    onOpenExternally: (RichConversationMessage) -> Unit = {},
    onBack: () -> Unit,
    onWipeFromRamAndClose: (RichConversationMessage) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val transfer = message?.fileTransfer

    Scaffold(
        topBar = {
            ZeroTraceTopBar(
                title = transfer?.manifest?.sanitizedFileName ?: "Encrypted Attachment Preview",
                subtitle = "RAM-Only Decrypted Buffer • Open or Save",
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
                .navigationBarsPadding()
                .padding(16.dp)
                .testTag("media_preview_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (message == null || transfer == null) {
                Text("No encrypted attachment selected.")
            } else {
                val decryptedBytes = transfer.decryptedInMemoryBytes
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
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

                    val tempVideoFile = if (message.payloadKind == MessagePayloadKind.VIDEO && decryptedBytes != null) {
                        remember(transfer.manifest.transferId, decryptedBytes.size) {
                            MediaExportAndOpenHelper.prepareTempCacheFile(
                                context = context,
                                fileName = transfer.manifest.sanitizedFileName,
                                bytes = decryptedBytes
                            )
                        }
                    } else {
                        null
                    }

                    DisposableEffect(tempVideoFile) {
                        onDispose {
                            MediaExportAndOpenHelper.shredTempCacheFile(tempVideoFile)
                        }
                    }

                    val textDocumentPreview = remember(transfer.manifest.transferId, decryptedBytes?.size) {
                        if (message.payloadKind == MessagePayloadKind.DOCUMENT &&
                            decryptedBytes != null &&
                            (transfer.manifest.mimeType.startsWith("text/") ||
                                transfer.manifest.sanitizedFileName.endsWith(".txt", true) ||
                                transfer.manifest.sanitizedFileName.endsWith(".md", true) ||
                                transfer.manifest.sanitizedFileName.endsWith(".json", true) ||
                                transfer.manifest.sanitizedFileName.endsWith(".csv", true))
                        ) {
                            try {
                                String(decryptedBytes, 0, minOf(decryptedBytes.size, 8192), StandardCharsets.UTF_8)
                            } catch (_: Throwable) {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when {
                            previewBitmap != null -> {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = transfer.manifest.sanitizedFileName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(340.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Spacer(modifier = Modifier.height(14.dp))
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
                                        .height(320.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.Black)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(onClick = { onOpenExternally(message) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Video in System Player")
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            message.payloadKind == MessagePayloadKind.AUDIO_FILE ||
                                message.payloadKind == MessagePayloadKind.VOICE_NOTE -> {
                                val isPlaying = playingVoiceMessageId == message.id
                                Icon(
                                    imageVector = Icons.Default.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                if (decryptedBytes != null) {
                                    Button(
                                        onClick = { onToggleVoicePlay(message) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isPlaying) "Stop Audio" else "Play Decrypted Audio")
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            else -> {
                                Icon(
                                    imageVector = if (message.payloadKind == MessagePayloadKind.IMAGE) {
                                        Icons.Default.Image
                                    } else {
                                        Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                if (textDocumentPreview != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = textDocumentPreview,
                                            style = MonospaceStyle.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                if (decryptedBytes != null) {
                                    Button(
                                        onClick = { onOpenExternally(message) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Document in Viewer")
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }

                        Text(
                            text = transfer.manifest.sanitizedFileName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "MIME: ${transfer.manifest.mimeType} • Size: ${transfer.manifest.totalSizeBytes} bytes • Chunks: ${transfer.manifest.totalChunks}",
                            style = MonospaceStyle.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SHA-256 Verified: ${transfer.manifest.sha256DigestHex}",
                            style = MonospaceStyle.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Decrypted bytes reside in volatile RAM until you choose Save to Gallery / Downloads or Wipe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (decryptedBytes != null) {
                    Button(
                        onClick = { onSaveToGallery(message) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("preview_save_gallery_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save to Device Gallery / Downloads", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { onWipeFromRamAndClose(message) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonDanger)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zeroize Attachment Buffer (0x00) & Remove", color = CrimsonDanger)
                }
            }
        }
    }
}
