package com.zerotrace.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zerotrace.bridge.ActiveCallUiState
import com.zerotrace.bridge.CallDirectionFilter
import com.zerotrace.bridge.CallMediaKind
import com.zerotrace.bridge.CallProtocolState
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.bridge.VolatileCallHistoryEntry
import com.zerotrace.network.ConnectivityState
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.EmptyState
import com.zerotrace.ui.components.VideoCallControls
import com.zerotrace.ui.components.VoiceCallControls
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.theme.AmberWarning
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.MonospaceStyle
import com.zerotrace.ui.theme.ZeroTracePalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Screen 6 of Section 4: Calls Tab
 * Clean Light-mode layout matching Mockup Screen 5 with Tor-TCP E2EE Calling.
 */
@Composable
fun ZeroTraceCallsTabScreen(
    language: AppLanguage,
    connectivityState: ConnectivityState,
    torProgress: Int,
    verifiedContacts: List<VerifiedContactRecord>,
    callHistory: List<VolatileCallHistoryEntry>,
    selectedFilter: CallDirectionFilter,
    onFilterChange: (CallDirectionFilter) -> Unit,
    onStartAudioCall: (VerifiedContactRecord) -> Unit,
    onStartVideoCall: (VerifiedContactRecord) -> Unit,
    onSimulateIncomingCall: (VerifiedContactRecord, CallMediaKind) -> Unit,
    onClearCallHistory: () -> Unit,
    onOpenAddContact: () -> Unit,
    onEmergencyWipe: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI
    val filteredHistory = remember(callHistory, selectedFilter) {
        callHistory.filter { entry ->
            when (selectedFilter) {
                CallDirectionFilter.ALL -> true
                CallDirectionFilter.MISSED -> entry.wasMissed
                CallDirectionFilter.AUDIO -> entry.mediaKind == CallMediaKind.AUDIO
                CallDirectionFilter.VIDEO -> entry.mediaKind == CallMediaKind.VIDEO
            }
        }
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
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isHi) "कॉल" else "Calls",
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
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("calls_tab_screen"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 88.dp)
        ) {
            // Pill Filter Chips + Clear Volatile Call Log
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CallDirectionFilter.entries.forEach { filter ->
                            val isSelected = selectedFilter == filter
                            val label = when (filter) {
                                CallDirectionFilter.ALL -> "All"
                                CallDirectionFilter.MISSED -> "Missed"
                                CallDirectionFilter.AUDIO -> "Audio"
                                CallDirectionFilter.VIDEO -> "Video"
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { onFilterChange(filter) },
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
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier.height(34.dp)
                            )
                        }
                    }
                    if (callHistory.isNotEmpty()) {
                        IconButton(
                            onClick = onClearCallHistory,
                            modifier = Modifier.testTag("clear_call_history_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear RAM Call History",
                                tint = CrimsonDanger
                            )
                        }
                    }
                }
            }

            if (verifiedContacts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Call,
                        title = "No Verified Peers for Encrypted Calling",
                        description = "Pair a contact via ZT2|v1 QR code first so ZeroTrace can authenticate call keys with dual Classical + Post-Quantum signatures.",
                        primaryActionLabel = "Pair Verified Contact",
                        onPrimaryAction = onOpenAddContact
                    )
                }
            } else {
                items(verifiedContacts, key = { "dial_${it.onionAddress}" }) { contact ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                ContactAvatar(
                                    alias = contact.alias,
                                    onionAddress = contact.onionAddress,
                                    avatarBytes = contact.avatarBytes,
                                    size = 46.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = contact.alias,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = "Encrypted • onion: ${contact.onionAddress.take(14)}…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { onStartAudioCall(contact) },
                                    modifier = Modifier.testTag("start_audio_call_${contact.alias}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Start Encrypted Audio Call",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onStartVideoCall(contact) },
                                    modifier = Modifier.testTag("start_video_call_${contact.alias}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Start Encrypted Video Call",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(
                                    onClick = { onSimulateIncomingCall(contact, CallMediaKind.AUDIO) },
                                    modifier = Modifier.testTag("simulate_incoming_call_${contact.alias}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RingVolume,
                                        contentDescription = "Test Incoming Call Ring",
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 58.dp)
                        )
                    }
                }
            }

            if (filteredHistory.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "RECENT CALLS (${filteredHistory.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(filteredHistory, key = { it.callId }) { entry ->
                    val matchedAvatar = verifiedContacts.firstOrNull {
                        it.onionAddress.equals(entry.peerOnion, ignoreCase = true)
                    }?.avatarBytes
                    CallHistoryRow(entry = entry, avatarBytes = matchedAvatar)
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(
    entry: VolatileCallHistoryEntry,
    avatarBytes: ByteArray? = null
) {
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                ContactAvatar(
                    alias = entry.peerAlias,
                    onionAddress = entry.peerOnion,
                    avatarBytes = avatarBytes,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = entry.peerAlias,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when {
                            entry.wasMissed -> Icons.AutoMirrored.Filled.CallMissed
                            entry.isIncoming -> Icons.AutoMirrored.Filled.CallReceived
                            else -> Icons.AutoMirrored.Filled.CallMade
                        }
                        val tint = if (entry.wasMissed) CrimsonDanger else MaterialTheme.colorScheme.primary
                        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${if (entry.wasMissed) "Missed" else if (entry.isIncoming) "Incoming" else "Outgoing"} • ${formatter.format(Date(entry.timestampEpochMillis))} • Encrypted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = if (entry.durationSeconds > 0) formatCallDuration(entry.durationSeconds) else "Tor",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 56.dp)
        )
    }
}

/**
 * Screen 7 of Section 4: Encrypted Voice Call Screen
 */
@Composable
fun ZeroTraceVoiceCallScreen(
    callState: ActiveCallUiState?,
    peerAvatarBytes: ByteArray? = null,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onUpgradeToVideo: () -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler(onBack = onEndCall)
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Surface(
        color = Color(0xFF060E0B),
        modifier = Modifier
            .fillMaxSize()
            .testTag("voice_call_screen")
    ) {
        if (callState == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("Call ended.", color = Color.White)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0A241C), Color(0xFF06080C))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Security & Tor-TCP Notice
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = AmberWarning.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "TOR-TCP VOICE STREAM • ZERO STUN/UDP LEAKS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AmberWarning,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End-to-End Encrypted • Safety: ${callState.peerSafetyDigits.take(19)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF25D366)
                        )
                    }
                }

                // Center Avatar & Call Status
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ContactAvatar(
                        alias = callState.peerAlias,
                        onionAddress = callState.peerOnion,
                        isOnlineOrSessionActive = callState.protocolState == CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM,
                        avatarBytes = peerAvatarBytes,
                        size = 112.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = callState.peerAlias,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = callState.peerOnion,
                        style = MonospaceStyle.copy(fontSize = 11.sp),
                        color = Color(0xFF8696A0)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (callState.protocolState) {
                            CallProtocolState.ROUTING_TOR_CIRCUIT -> "Routing Tor Onion Circuit…"
                            CallProtocolState.PERFORMING_PQXDH_CALL_AUTH -> "Authenticating ML-KEM-1024 + X25519 Call Key…"
                            CallProtocolState.RINGING_PEER -> "Ringing Verified Peer…"
                            CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM -> formatCallDuration(callState.elapsedSeconds)
                            else -> callState.protocolState.name
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF25D366)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Rx Audio: ${callState.remoteAudioPacketsReceived} frames • ChaCha20-Poly1305",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center
                    )
                    if (callState.isRemoteMuted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = AmberWarning.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MicOff,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${callState.peerAlias} muted microphone",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberWarning
                                )
                            }
                        }
                    }
                }

                // Bottom Voice Call Controls
                VoiceCallControls(
                    isMuted = callState.isMuted,
                    isSpeakerOn = callState.isSpeakerOn,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onUpgradeToVideo = onUpgradeToVideo,
                    onEndCall = onEndCall
                )
            }
        }
    }
}

/**
 * Screen 8 of Section 4: Encrypted Video Call Screen matching Mockup Screen 3
 * Uses PreviewView.ImplementationMode.COMPATIBLE + explicit YUV_420_888 ImageAnalysis (320x240)
 * + rounded PiP camera overlay & Tor circuit banner.
 */
@Composable
fun ZeroTraceVideoCallScreen(
    callState: ActiveCallUiState?,
    peerAvatarBytes: ByteArray? = null,
    remoteVideoFrame: Bitmap? = null,
    onLocalCameraFrame: (ImageProxy, Boolean) -> Unit = { img, _ -> img.close() },
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler(onBack = onEndCall)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentFrameCallback by rememberUpdatedState(onLocalCameraFrame)
    val currentFrontCamera by rememberUpdatedState(callState?.isFrontCamera ?: true)

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            // COMPATIBLE uses TextureView so rounded corner clipping & overlay pills render cleanly without SurfaceView Z-order glitches
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener(
                {
                    try {
                        providerFuture.get().unbindAll()
                    } catch (_: Throwable) {
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
            cameraExecutor.shutdownNow()
        }
    }

    val isCameraActive = permissionsGranted && (callState?.isCameraEnabled == true)
    val isFront = callState?.isFrontCamera ?: true

    LaunchedEffect(isCameraActive, isFront) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    provider.unbindAll()
                    if (!isCameraActive) return@addListener

                    val selector = if (isFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(320, 240),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val analyzer = ImageAnalysis.Builder()
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(cameraExecutor) { imageProxy ->
                                currentFrameCallback(imageProxy, currentFrontCamera)
                            }
                        }

                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analyzer)
                } catch (_: Throwable) {
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("video_call_screen")
    ) {
        if (callState == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("Video call ended.", color = MaterialTheme.colorScheme.onBackground)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                // Clean Light/Theme Top Header matching Mockup Screen 3
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onEndCall) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "End and Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        ContactAvatar(
                            alias = callState.peerAlias,
                            onionAddress = callState.peerOnion,
                            isOnlineOrSessionActive = callState.protocolState == CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM,
                            avatarBytes = peerAvatarBytes,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = callState.peerAlias,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatCallDuration(callState.elapsedSeconds)} • TOR Circuit Active • End-to-End Encrypted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    }
                }

                // Rounded Main Video Stage matching Mockup Screen 3
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF0A1412))
                ) {
                    if (remoteVideoFrame != null && callState.isRemoteCameraEnabled) {
                        Image(
                            bitmap = remoteVideoFrame.asImageBitmap(),
                            contentDescription = "Remote Encrypted Video Feed",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0xFF111B21), Color(0xFF0B141A))
                                    )
                                )
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            ContactAvatar(
                                alias = callState.peerAlias,
                                onionAddress = callState.peerOnion,
                                avatarBytes = peerAvatarBytes,
                                size = 96.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = callState.peerAlias,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (callState.protocolState) {
                                    CallProtocolState.RINGING_PEER -> "Ringing ${callState.peerAlias}…"
                                    CallProtocolState.ROUTING_TOR_CIRCUIT -> "Routing Tor/Onion Stream…"
                                    CallProtocolState.PERFORMING_PQXDH_CALL_AUTH -> "Deriving Ephemeral X25519 Key…"
                                    else -> if (!callState.isRemoteCameraEnabled) {
                                        "Peer camera paused • ${formatCallDuration(callState.elapsedSeconds)}"
                                    } else {
                                        "Connecting peer video frames • ${formatCallDuration(callState.elapsedSeconds)}"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF25D366)
                            )
                        }
                    }

                    // Top-Left Tor Circuit Pill Overlay matching Mockup
                    Surface(
                        color = Color.White.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOR v3 • Circuit Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111B21)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1DAA61))
                            )
                        }
                    }

                    // Bottom-Right Floating Self-Preview Window matching Mockup Screen 3
                    Surface(
                        color = Color(0xFF111B21),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(width = 102.dp, height = 142.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isCameraActive) {
                                AndroidView(
                                    factory = { previewView },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Local Camera Preview",
                                        tint = if (callState.isCameraEnabled) Color(0xFF25D366) else CrimsonDanger
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (!callState.isCameraEnabled) "Camera Off" else "Camera Perm\nRequired",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "You • Encrypted",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Mint Call Secured Onion Banner matching Mockup Screen 3
                Surface(
                    color = ZeroTracePalette.mintContainer(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = ZeroTracePalette.mintContent(),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Call secured • Onion: ${callState.peerOnion.take(22)}…",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = ZeroTracePalette.mintContent(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom Video Call Controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VideoCallControls(
                        isMuted = callState.isMuted,
                        isCameraEnabled = callState.isCameraEnabled,
                        isFrontCamera = callState.isFrontCamera,
                        onToggleMute = onToggleMute,
                        onToggleCamera = onToggleCamera,
                        onFlipCamera = onFlipCamera,
                        onEndCall = onEndCall
                    )
                }
            }
        }
    }
}

/**
 * Screen 9 of Section 4: Incoming Call Screen
 */
@Composable
fun ZeroTraceIncomingCallScreen(
    callState: ActiveCallUiState?,
    peerAvatarBytes: ByteArray? = null,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onQuickMessageReply: () -> Unit
) {
    BackHandler(onBack = onDeclineCall)
    Surface(
        color = Color(0xFF06130E),
        modifier = Modifier
            .fillMaxSize()
            .testTag("incoming_call_screen")
    ) {
        if (callState == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("No active incoming call.", color = Color.White)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF25D366))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "INCOMING ENCRYPTED ${callState.mediaKind.name} CALL",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF25D366)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dual Ed25519 + ML-DSA-87 Caller Signature Verified",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD1D7DB)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ContactAvatar(
                        alias = callState.peerAlias,
                        onionAddress = callState.peerOnion,
                        avatarBytes = peerAvatarBytes,
                        size = 110.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = callState.peerAlias,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = callState.peerOnion,
                        style = MonospaceStyle.copy(fontSize = 11.sp),
                        color = Color(0xFF8696A0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Safety Number: ${callState.peerSafetyDigits}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = onDeclineCall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonDanger,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f)
                                .testTag("incoming_call_decline_btn")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Decline", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = onAcceptCall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f)
                                .testTag("incoming_call_accept_btn")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accept E2EE", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onQuickMessageReply,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decline & Reply in Encrypted Chat", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatCallDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
