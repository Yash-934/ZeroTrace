package com.zerotrace.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
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
import com.zerotrace.ui.components.SecurityInformationCard
import com.zerotrace.ui.components.VideoCallControls
import com.zerotrace.ui.components.VoiceCallControls
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.theme.AmberWarning
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.CyanAccent
import com.zerotrace.ui.theme.EmeraldPrimary
import com.zerotrace.ui.theme.MonospaceStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen 6 of Section 4: Calls Tab
 * Honest Architecture (Section 9):
 * - Standard WebRTC UDP/STUN/TURN is NEVER used because STUN/ICE leaks real IP addresses and Tor only routes TCP streams.
 * - Calls use experimental Tor-TCP chunked AEAD framing (`ChaCha20-Poly1305`) with explicit high-latency disclosure.
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
            ZeroTraceTopBar(
                title = if (isHi) "कॉल (Tor E2EE)" else "Calls",
                subtitle = "Tor-Routed TCP Encrypted Calling (Experimental)",
                connectivityState = connectivityState,
                torProgress = torProgress,
                onEmergencyWipeClick = onEmergencyWipe
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .testTag("calls_tab_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
        ) {
            // Honest Tor Calling Architecture Warning Card (Section 9)
            item {
                SecurityInformationCard(
                    title = "Experimental Tor-TCP Calling (Zero WebRTC/STUN Leaks)",
                    protocolSuite = "Ephemeral PQXDH Call Key + ChaCha20-Poly1305 Frame Stream",
                    torRoutingNote = "Tor supports TCP only (no UDP). STUN/TURN/ICE are disabled to prevent clearnet IP leaks.",
                    storagePolicyNote = "High-latency TCP-over-Tor transport. Volatile call history is stored strictly in RAM."
                )
            }

            // Filter Chips + Clear Volatile Call Log
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CallDirectionFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { onFilterChange(filter) },
                                label = { Text(filter.name, style = MaterialTheme.typography.labelSmall) }
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

            // Verified Peers Quick Dial Section
            item {
                Text(
                    text = "ENCRYPTED PEER DIALER (${verifiedContacts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldPrimary
                )
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        size = 44.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = contact.alias,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${contact.onionAddress.take(18)}…onion",
                                            style = MonospaceStyle.copy(fontSize = 10.sp),
                                            color = CyanAccent
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = { onStartAudioCall(contact) },
                                        modifier = Modifier.testTag("start_audio_call_${contact.alias}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Start Encrypted Audio Call",
                                            tint = EmeraldPrimary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onStartVideoCall(contact) },
                                        modifier = Modifier.testTag("start_video_call_${contact.alias}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = "Start Encrypted Video Call",
                                            tint = CyanAccent
                                        )
                                    }
                                    IconButton(
                                        onClick = { onSimulateIncomingCall(contact, CallMediaKind.AUDIO) },
                                        modifier = Modifier.testTag("simulate_incoming_call_${contact.alias}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RingVolume,
                                            contentDescription = "Test Incoming Call Ring",
                                            tint = AmberWarning
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Volatile RAM Call History Section
            item {
                Text(
                    text = "VOLATILE RAM CALL HISTORY (${filteredHistory.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (filteredHistory.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No volatile call entries in RAM. Call logs are never persisted to SQLite or Android CallLog provider.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filteredHistory, key = { it.callId }) { entry ->
                    CallHistoryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(entry: VolatileCallHistoryEntry) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    entry.wasMissed -> Icons.AutoMirrored.Filled.CallMissed
                    entry.isIncoming -> Icons.AutoMirrored.Filled.CallReceived
                    else -> Icons.AutoMirrored.Filled.CallMade
                }
                val tint = if (entry.wasMissed) CrimsonDanger else EmeraldPrimary
                Icon(imageVector = icon, contentDescription = null, tint = tint)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${entry.peerAlias} (${entry.mediaKind.name})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "${formatter.format(Date(entry.timestampEpochMillis))} • Duration ${entry.durationSeconds}s • Tor TCP",
                        style = MonospaceStyle.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "RAM-ONLY",
                style = MonospaceStyle.copy(fontSize = 10.sp),
                color = CyanAccent
            )
        }
    }
}

/**
 * Screen 7 of Section 4: Encrypted Voice Call Screen
 */
@Composable
fun ZeroTraceVoiceCallScreen(
    callState: ActiveCallUiState?,
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Security & Experimental Tor-TCP Notice
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = AmberWarning.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "EXPERIMENTAL TOR-TCP VOICE STREAM • NO STUN/UDP LEAKS",
                            style = MonospaceStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = AmberWarning,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End-to-End Encrypted • Safety: ${callState.peerSafetyDigits.take(19)}",
                            style = MonospaceStyle.copy(fontSize = 11.sp),
                            color = EmeraldPrimary
                        )
                    }
                }

                // Center Avatar & Call Status
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ContactAvatar(
                        alias = callState.peerAlias,
                        onionAddress = callState.peerOnion,
                        isOnlineOrSessionActive = callState.protocolState == CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM,
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
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (callState.protocolState) {
                            CallProtocolState.ROUTING_TOR_CIRCUIT -> "Routing 3-hop Tor Onion Circuit…"
                            CallProtocolState.PERFORMING_PQXDH_CALL_AUTH -> "Authenticating ML-KEM-1024 + X25519 Call Key…"
                            CallProtocolState.RINGING_PEER -> "Ringing Verified Peer over Onion Stream…"
                            CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM -> formatCallDuration(callState.elapsedSeconds)
                            else -> callState.protocolState.name
                        },
                        style = MonospaceStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        color = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Rx Audio: ${callState.remoteAudioPacketsReceived} AEAD frames • ChaCha20-Poly1305 • Ephemeral X25519",
                        style = MonospaceStyle.copy(fontSize = 11.sp),
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
                                    style = MonospaceStyle.copy(fontSize = 10.sp),
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
 * Screen 8 of Section 4: Encrypted Video Call Screen
 */
@Composable
fun ZeroTraceVideoCallScreen(
    callState: ActiveCallUiState?,
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
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Throwable) {
            }
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

                    val analyzer = ImageAnalysis.Builder()
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
        color = Color(0xFF05080B),
        modifier = Modifier
            .fillMaxSize()
            .testTag("video_call_screen")
    ) {
        if (callState == null) {
            Box(contentAlignment = Alignment.Center) {
                Text("Video call ended.", color = Color.White)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Remote Peer Live Encrypted Video Surface
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
                                    listOf(Color(0xFF0E2920), Color(0xFF040608))
                                )
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ContactAvatar(
                            alias = callState.peerAlias,
                            onionAddress = callState.peerOnion,
                            size = 96.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "${callState.peerAlias} • Encrypted Video over Tor TCP",
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
                                    "Waiting for remote video frames • ${formatCallDuration(callState.elapsedSeconds)}"
                                }
                            },
                            style = MonospaceStyle.copy(fontSize = 13.sp),
                            color = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Prioritized Audio+Video ChaCha20-Poly1305 over Onion TCP (Zero WebRTC UDP/STUN IP leaks)",
                            style = MonospaceStyle.copy(fontSize = 10.sp),
                            color = AmberWarning,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Top-start security & live stream status pill
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 24.dp, start = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${callState.peerAlias} • ${formatCallDuration(callState.elapsedSeconds)}",
                                style = MonospaceStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        Text(
                            text = "A:${callState.remoteAudioPacketsReceived} V:${callState.remoteVideoFramesReceived} • Safety ${callState.peerSafetyDigits.take(10)}",
                            style = MonospaceStyle.copy(fontSize = 10.sp),
                            color = CyanAccent
                        )
                    }
                }

                // Floating Local CameraX Self-Preview Window (Top End)
                Surface(
                    color = Color(0xFF101920),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, EmeraldPrimary),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp, end = 16.dp)
                        .size(width = 118.dp, height = 164.dp)
                ) {
                    if (isCameraActive) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Local Camera Preview",
                                tint = if (callState.isCameraEnabled) EmeraldPrimary else CrimsonDanger
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (!callState.isCameraEnabled) {
                                    "Camera Off"
                                } else {
                                    "Camera Perm\nRequired"
                                },
                                style = MonospaceStyle.copy(fontSize = 10.sp),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Bottom Video Call Controls
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp)
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = EmeraldPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "INCOMING AUTHENTICATED TOR ${callState.mediaKind.name} CALL",
                            style = MonospaceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                            color = EmeraldPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dual Ed25519 + ML-DSA-87 Caller Signature Verified",
                        style = MonospaceStyle.copy(fontSize = 11.sp),
                        color = CyanAccent
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ContactAvatar(
                        alias = callState.peerAlias,
                        onionAddress = callState.peerOnion,
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
                        color = CyanAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Safety Number: ${callState.peerSafetyDigits}",
                        style = MonospaceStyle.copy(fontSize = 11.sp),
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
                                containerColor = EmeraldPrimary,
                                contentColor = Color.Black
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
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decline & Reply in Encrypted Chat")
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
