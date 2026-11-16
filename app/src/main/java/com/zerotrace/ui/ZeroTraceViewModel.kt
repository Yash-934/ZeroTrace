package com.zerotrace.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotrace.bridge.ActiveCallUiState
import com.zerotrace.bridge.CallDirectionFilter
import com.zerotrace.bridge.CallMediaKind
import com.zerotrace.bridge.CallProtocolState
import com.zerotrace.bridge.E2eeChatEnvelope
import com.zerotrace.bridge.EncryptedFileTransferEngine
import com.zerotrace.bridge.EncryptedTransferProgress
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.NativeRustBridge
import com.zerotrace.bridge.ParsedQrInvite
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.RustCoreBridge
import com.zerotrace.bridge.STATUS_TTL_24H_MS
import com.zerotrace.bridge.SessionRatchetMetrics
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.StatusPrivacyPolicy
import com.zerotrace.bridge.TransferDirection
import com.zerotrace.bridge.TransferLifecycleState
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.bridge.VolatileCallHistoryEntry
import com.zerotrace.bridge.VolatileChatMessage
import com.zerotrace.media.EncryptedCallMediaEngine
import com.zerotrace.media.MediaExportAndOpenHelper
import com.zerotrace.media.MediaRamLoader
import com.zerotrace.media.VoiceNotePlayer
import com.zerotrace.media.VoiceNoteRecorder
import com.zerotrace.network.ConnectivityState
import com.zerotrace.network.PeerHelloWire
import com.zerotrace.network.PeerTransportEvent
import com.zerotrace.network.SessionService
import com.zerotrace.network.TorOnionTransportManager
import com.zerotrace.network.TorTransportBackend
import com.zerotrace.security.BackupPolicy
import com.zerotrace.security.DeviceIntegrityReport
import com.zerotrace.security.HardwareSecurityLevel
import com.zerotrace.security.KeystoreManager
import com.zerotrace.security.SecureWindowManager
import com.zerotrace.ui.components.MainBottomTab
import com.zerotrace.ui.theme.AccentPalette
import com.zerotrace.ui.theme.AppThemeMode
import com.zerotrace.ui.theme.ChatWallpaperStyle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ZeroTraceScreen {
    SPLASH,
    ONBOARDING,
    IDENTITY_CREATION,
    HOME,
    MY_IDENTITY,
    ADD_CONTACT,
    CONTACT_VERIFICATION,
    CHAT,
    CONTACT_PROFILE,
    STATUS_VIEWER,
    VOICE_CALL,
    VIDEO_CALL,
    INCOMING_CALL,
    MEDIA_PREVIEW,
    PRIVACY_CENTER,
    SECURITY_DASHBOARD,
    SETTINGS
}

enum class AppLanguage(val code: String, val displayLabel: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी (Hindi)")
}

enum class ConversationFilter(val labelEn: String, val labelHi: String) {
    ALL("All", "सभी"),
    UNREAD("Unread", "अपठित"),
    PINNED("Pinned", "पिन किए गए"),
    VERIFIED("Verified PQ", "सत्यापित PQ"),
    ARCHIVED("Archived", "संग्रहीत")
}

data class IdentityReplacementAlert(
    val existingContact: VerifiedContactRecord,
    val incomingInvite: ParsedQrInvite
)

data class ZeroTraceUiState(
    val currentScreen: ZeroTraceScreen = ZeroTraceScreen.SPLASH,
    val selectedBottomTab: MainBottomTab = MainBottomTab.CHATS,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val accentPalette: AccentPalette = AccentPalette.EMERALD,
    val wallpaperStyle: ChatWallpaperStyle = ChatWallpaperStyle.OBSIDIAN_GRID,
    val limitationsAccepted: Boolean = false,
    val isGeneratingIdentity: Boolean = false,
    val identitySummary: PublicIdentitySummary? = null,
    val hardwareSecurityLevel: HardwareSecurityLevel = HardwareSecurityLevel.UNINITIALIZED,
    val isNativeRustLibraryLoaded: Boolean = NativeRustBridge.isNativeLibraryLoaded,
    val nativeRustStatusNote: String = NativeRustBridge.loadErrorReason ?: "libzerotrace_core.so active",
    val verifiedContacts: List<VerifiedContactRecord> = emptyList(),
    val pinnedOnions: Set<String> = emptySet(),
    val mutedOnions: Set<String> = emptySet(),
    val archivedOnions: Set<String> = emptySet(),
    val blockedOnions: Set<String> = emptySet(),
    val unreadCountByOnion: Map<String, Int> = emptyMap(),
    val lastMessagePreviewByOnion: Map<String, String> = emptyMap(),
    val lastMessageTimeByOnion: Map<String, Long> = emptyMap(),
    val chatSearchQuery: String = "",
    val conversationFilter: ConversationFilter = ConversationFilter.ALL,
    val inChatSearchQuery: String = "",
    val showGroupChatMlsDialog: Boolean = false,
    val myPairingQrString: String? = null,
    val myPairingQrExpirySec: Long = 0L,
    val pendingScannedInvite: ParsedQrInvite? = null,
    val pendingContactAlias: String = "",
    val identityReplacementAlert: IdentityReplacementAlert? = null,
    val activeSessionHandle: Long? = null,
    val activePeerContact: VerifiedContactRecord? = null,
    val inspectedProfileContact: VerifiedContactRecord? = null,
    val activeChatMessages: List<VolatileChatMessage> = emptyList(),
    val richMessagesByOnion: Map<String, List<RichConversationMessage>> = emptyMap(),
    val replyingToMessage: RichConversationMessage? = null,
    val inspectedMessageInfo: RichConversationMessage? = null,
    val mediaPreviewMessage: RichConversationMessage? = null,
    val showAttachmentSheet: Boolean = false,
    val isRecordingVoiceNote: Boolean = false,
    val activeRatchetMetrics: SessionRatchetMetrics? = null,
    // Updates / Ephemeral P2P Status state
    val ephemeralStatuses: List<EphemeralStatusItem> = emptyList(),
    val activeViewingStatus: EphemeralStatusItem? = null,
    val statusPrivacyPolicy: StatusPrivacyPolicy = StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS,
    // Calls state
    val activeCallState: ActiveCallUiState? = null,
    val callHistory: List<VolatileCallHistoryEntry> = emptyList(),
    val callFilter: CallDirectionFilter = CallDirectionFilter.ALL,
    val callSearchQuery: String = "",
    // Privacy & Security Controls
    val sessionTimeoutSeconds: Long = 300L,
    val disappearingMessageTimerSec: Long = 300L,
    val autoLockOnBackground: Boolean = false,
    val biometricAppLockEnabled: Boolean = false,
    val flagSecureEnabled: Boolean = false,
    val readReceiptsEnabled: Boolean = true,
    val typingIndicatorsEnabled: Boolean = true,
    val metadataMinimizedMode: Boolean = true,
    val socksPort: Int = 9050,
    val statusBannerMessage: String? = null,
    val errorBannerMessage: String? = null,
    val backupAuditStatus: BackupPolicy.BackupAuditStatus? = null,
    val deviceIntegrityReport: DeviceIntegrityReport = SecureWindowManager.evaluateDeviceIntegrityIndicators()
)

class ZeroTraceViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val keystoreManager = KeystoreManager(appContext)

    private val _uiState = MutableStateFlow(ZeroTraceUiState())
    val uiState: StateFlow<ZeroTraceUiState> = _uiState.asStateFlow()

    val connectivityState: StateFlow<ConnectivityState> = TorOnionTransportManager.connectivityState
    val torBootstrapProgress: StateFlow<Int> = TorOnionTransportManager.torBootstrapProgress
    val torBootstrapSummary: StateFlow<String?> = TorOnionTransportManager.torBootstrapSummary
    val transportBackend: StateFlow<TorTransportBackend> = TorOnionTransportManager.transportBackend
    val localListenerPort: StateFlow<Int?> = TorOnionTransportManager.localListenerPort
    val transportError: StateFlow<String?> = TorOnionTransportManager.lastErrorDetail
    val connectedPeerOnions: StateFlow<Set<String>> = TorOnionTransportManager.connectedPeerOnions
    val voiceRecordingSeconds: StateFlow<Int> = VoiceNoteRecorder.recordingSeconds
    val voiceRecordingAmplitude: StateFlow<Float> = VoiceNoteRecorder.amplitudeLevel
    val playingVoiceMessageId: StateFlow<String?> = VoiceNotePlayer.playingMessageId
    val remoteCallVideoFrame: StateFlow<Bitmap?> = EncryptedCallMediaEngine.remoteVideoFrame

    private var inactivityWatchdogJob: Job? = null
    private var callTimerJob: Job? = null
    private val processedVolatileMsgKeys = ConcurrentHashMap.newKeySet<String>()

    // Active / pending E2EE call key & signaling state (RAM only)
    private var pendingCallPrivKey32: ByteArray? = null
    private var pendingIncomingRemoteHost: String? = null
    private var pendingIncomingMediaPort: Int? = null
    private var pendingIncomingRemoteEphemPub: ByteArray? = null

    init {
        TorOnionTransportManager.initializePlatform(appContext)
        observePeerTransportEvents()
        initializeSecuritySubsystem()
        startInactivityWatchdog()
    }

    private fun observePeerTransportEvents() {
        viewModelScope.launch {
            TorOnionTransportManager.peerTransportEvents.collect { event ->
                when (event) {
                    is PeerTransportEvent.PeerConnected -> {
                        ensurePeerContactFromHello(event.peerHello)
                        val currentPeer = _uiState.value.activePeerContact?.onionAddress
                        if (currentPeer != null && currentPeer.equals(event.peerOnion, ignoreCase = true)) {
                            _uiState.update { state ->
                                if (state.statusBannerMessage?.contains("disconnected", ignoreCase = true) == true ||
                                    state.statusBannerMessage?.contains("कट गया") == true
                                ) {
                                    state.copy(statusBannerMessage = null)
                                } else state
                            }
                        }
                        // Push own non-expired (<24h) ephemeral statuses automatically to newly connected verified peer
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val now = System.currentTimeMillis()
                            val activeOwnStatuses = _uiState.value.ephemeralStatuses.filter {
                                it.isOwnStatus && !it.isExpired(now) &&
                                    it.privacyPolicy == StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS
                            }
                            for (st in activeOwnStatuses) {
                                TorOnionTransportManager.sendStatusItemToPeer(event.peerOnion, st)
                            }
                        }
                    }
                    is PeerTransportEvent.PeerDisconnected -> {
                        val currentPeerOnion = _uiState.value.activePeerContact?.onionAddress
                        if (currentPeerOnion != null && currentPeerOnion.equals(event.peerOnion, ignoreCase = true)) {
                            _uiState.update { state ->
                                state.copy(
                                    statusBannerMessage = isHi(
                                        "पीयर P2P कनेक्शन अस्थायी रूप से कट गया — पुनः जुड़ने का प्रयास हो रहा है…",
                                        "Peer P2P circuit temporarily disconnected — auto-reconnecting…"
                                    )
                                )
                            }
                        }
                    }
                    is PeerTransportEvent.SessionEstablished -> {
                        val contact = event.peerHello?.let { ensurePeerContactFromHello(it) }
                            ?: _uiState.value.verifiedContacts.firstOrNull {
                                it.onionAddress.equals(event.peerOnion, ignoreCase = true)
                            }
                        val canonicalOnion = contact?.onionAddress ?: event.peerOnion
                        val volatileMsgs = RustCoreBridge.getVolatileMessages(event.sessionHandle)
                        val metrics = RustCoreBridge.getSessionMetrics(event.sessionHandle)
                        syncVolatileMessagesToRichConversation(canonicalOnion, volatileMsgs)

                        _uiState.update { state ->
                            val isCurrentPeer = state.activePeerContact?.onionAddress?.equals(canonicalOnion, ignoreCase = true) == true ||
                                state.activePeerContact == null
                            state.copy(
                                activeSessionHandle = if (isCurrentPeer) event.sessionHandle else state.activeSessionHandle,
                                activePeerContact = state.activePeerContact ?: contact,
                                activeChatMessages = if (isCurrentPeer) volatileMsgs else state.activeChatMessages,
                                activeRatchetMetrics = if (isCurrentPeer) metrics else state.activeRatchetMetrics,
                                statusBannerMessage = null,
                                errorBannerMessage = null
                            )
                        }
                    }
                    is PeerTransportEvent.IncomingMessage -> {
                        val contact = _uiState.value.verifiedContacts.firstOrNull {
                            it.onionAddress.equals(event.peerOnion, ignoreCase = true)
                        }
                        val canonicalOnion = contact?.onionAddress ?: event.peerOnion
                        val volatileMsgs = RustCoreBridge.getVolatileMessages(event.sessionHandle)
                        val metrics = RustCoreBridge.getSessionMetrics(event.sessionHandle)
                        syncVolatileMessagesToRichConversation(canonicalOnion, volatileMsgs)

                        _uiState.update { state ->
                            val isViewingThisPeer = state.currentScreen == ZeroTraceScreen.CHAT &&
                                state.activePeerContact?.onionAddress?.equals(canonicalOnion, ignoreCase = true) == true
                            val updatedUnread = state.unreadCountByOnion.toMutableMap()
                            if (isViewingThisPeer) {
                                updatedUnread[canonicalOnion] = 0
                            } else {
                                updatedUnread[canonicalOnion] = (updatedUnread[canonicalOnion] ?: 0) + 1
                            }
                            state.copy(
                                activeSessionHandle = if (isViewingThisPeer || state.activeSessionHandle == null) event.sessionHandle else state.activeSessionHandle,
                                activeChatMessages = if (isViewingThisPeer) volatileMsgs else state.activeChatMessages,
                                activeRatchetMetrics = if (isViewingThisPeer) metrics else state.activeRatchetMetrics,
                                unreadCountByOnion = updatedUnread,
                                statusBannerMessage = null,
                                errorBannerMessage = null
                            )
                        }
                    }
                    is PeerTransportEvent.DeliveryAck -> {
                        val handle = _uiState.value.activeSessionHandle
                        if (handle != null) {
                            _uiState.update { state ->
                                state.copy(
                                    activeRatchetMetrics = RustCoreBridge.getSessionMetrics(handle) ?: state.activeRatchetMetrics
                                )
                            }
                        }
                    }
                    is PeerTransportEvent.IncomingCallInvite -> {
                        handleIncomingCallInviteEvent(event)
                    }
                    is PeerTransportEvent.CallAccepted -> {
                        handleCallAcceptedEvent(event)
                    }
                    is PeerTransportEvent.CallRejected -> {
                        val active = _uiState.value.activeCallState
                        if (active != null && active.callId == event.callId) {
                            cleanupCallResourcesAndReturn("Call declined by peer: ${event.reason}")
                        }
                    }
                    is PeerTransportEvent.CallEnded -> {
                        val active = _uiState.value.activeCallState
                        if (active != null && active.callId == event.callId) {
                            cleanupCallResourcesAndReturn("Encrypted call ended by ${active.peerAlias}. Media key zeroized.")
                        }
                    }
                    is PeerTransportEvent.IncomingCallMediaFrame -> Unit
                    is PeerTransportEvent.IncomingStatusItem -> {
                        val incoming = event.statusItem
                        if (!incoming.isExpired()) {
                            val knownAlias = _uiState.value.verifiedContacts.firstOrNull {
                                it.onionAddress.equals(event.peerOnion, ignoreCase = true)
                            }?.alias ?: incoming.authorAlias
                            val resolved = incoming.copy(authorAlias = knownAlias, isOwnStatus = false)
                            _uiState.update { state ->
                                val alreadyPresent = state.ephemeralStatuses.any { it.statusId == resolved.statusId }
                                if (alreadyPresent) {
                                    resolved.wipeFromRam()
                                    state
                                } else {
                                    val showBanner = state.selectedBottomTab != MainBottomTab.UPDATES
                                    state.copy(
                                        ephemeralStatuses = listOf(resolved) + state.ephemeralStatuses,
                                        statusBannerMessage = if (showBanner) {
                                            isHi(
                                                "${resolved.authorAlias} ने नया E2EE स्टेटस साझा किया (24h में हटेगा)",
                                                "${resolved.authorAlias} shared an E2EE status (auto-deletes in 24h)"
                                            )
                                        } else state.statusBannerMessage
                                    )
                                }
                            }
                        } else {
                            incoming.wipeFromRam()
                        }
                    }
                }
            }
        }
    }

    private fun ensurePeerContactFromHello(hello: PeerHelloWire): VerifiedContactRecord {
        val existing = _uiState.value.verifiedContacts.firstOrNull {
            it.onionAddress.equals(hello.onionAddress, ignoreCase = true)
        }
        if (existing != null) return existing

        val classicalFpBytes = ByteArray(hello.classicalFingerprintHex.length / 2) { i ->
            hello.classicalFingerprintHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val pqFpBytes = ByteArray(hello.pqFingerprintHex.length / 2) { i ->
            hello.pqFingerprintHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val autoContact = VerifiedContactRecord(
            alias = "Peer-${hello.onionAddress.take(6).uppercase()}",
            onionAddress = hello.onionAddress.lowercase(),
            classicalFingerprintHex = hello.classicalFingerprintHex.lowercase(),
            pqFingerprintHex = hello.pqFingerprintHex.lowercase(),
            classicalSafetyDigits = RustCoreBridge.formatSafetyDigits(classicalFpBytes),
            pqSafetyDigits = RustCoreBridge.formatSafetyDigits(pqFpBytes),
            ed25519PublicKey = hello.ed25519PublicKey.copyOf(),
            x25519PublicKey = hello.x25519PublicKey.copyOf(),
            mlDsa87PublicKey = null,
            mlKem1024EncapsKey = null,
            pairingNonceHex = hello.pairingNonceHex,
            verifiedAtEpochSec = System.currentTimeMillis() / 1000L
        )
        val updated = _uiState.value.verifiedContacts + autoContact
        saveEncryptedContactsToDisk(updated)
        _uiState.update { it.copy(verifiedContacts = updated) }
        return autoContact
    }

    private fun initializeSecuritySubsystem() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val backupAudit = BackupPolicy.auditBackupConfiguration(appContext)
            val hwLevel = keystoreManager.getHardwareSecurityLevel()
            delay(350)

            val storedIdentityBytes = try {
                keystoreManager.loadWrappedIdentity()
            } catch (_: Exception) {
                null
            }

            if (storedIdentityBytes != null) {
                var summary = RustCoreBridge.restoreWrappedIdentity(storedIdentityBytes)
                RustCoreBridge.migrateLegacyOnionSeedIfNeeded()?.let { (migratedSummary, migratedBundle) ->
                    try {
                        keystoreManager.saveWrappedIdentity(migratedBundle)
                    } finally {
                        Arrays.fill(migratedBundle, 0.toByte())
                    }
                    summary = migratedSummary
                }

                val loadedContacts = loadEncryptedContactsFromDisk()
                val contacts = loadedContacts.map { TorOnionTransportManager.normalizeLoopbackContactIfNeeded(it) }
                if (contacts != loadedContacts) {
                    saveEncryptedContactsToDisk(contacts)
                }
                contacts.forEach { seedLoopbackPeerEphemeralStatusIfNeeded(it) }
                _uiState.update {
                    it.copy(
                        currentScreen = ZeroTraceScreen.HOME,
                        selectedBottomTab = MainBottomTab.CHATS,
                        limitationsAccepted = true,
                        identitySummary = summary,
                        hardwareSecurityLevel = keystoreManager.getHardwareSecurityLevel(),
                        verifiedContacts = contacts,
                        backupAuditStatus = backupAudit
                    )
                }
                refreshMyPairingQr()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val torReady = TorOnionTransportManager.bootstrapTorAndStartOnionService()
                    if (torReady && contacts.isNotEmpty()) {
                        establishWarmSessionInBackground(contacts.first())
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        currentScreen = ZeroTraceScreen.ONBOARDING,
                        hardwareSecurityLevel = hwLevel,
                        backupAuditStatus = backupAudit
                    )
                }
            }
        }
    }

    private fun startInactivityWatchdog() {
        inactivityWatchdogJob?.cancel()
        inactivityWatchdogJob = viewModelScope.launch {
            while (true) {
                delay(5_000L)
                val timeout = _uiState.value.sessionTimeoutSeconds
                val purged = RustCoreBridge.purgeExpiredSessions(timeout)
                if (purged > 0) {
                    wipeAllRichMessagesInRam()
                    TorOnionTransportManager.emergencyDisconnectAndWipe()
                    _uiState.update {
                        it.copy(
                            activeSessionHandle = null,
                            activePeerContact = null,
                            activeChatMessages = emptyList(),
                            richMessagesByOnion = emptyMap(),
                            activeRatchetMetrics = null,
                            currentScreen = if (it.currentScreen == ZeroTraceScreen.CHAT) ZeroTraceScreen.HOME else it.currentScreen,
                            statusBannerMessage = isHi(
                                "निष्क्रियता टाइमआउट: अस्थायी सत्र और RAM साफ़ कर दिए गए।",
                                "Inactivity timeout reached: volatile session & RAM wiped."
                            )
                        )
                    }
                }
                // Enforce 24-hour auto-deletion & RAM zeroization for Ephemeral P2P Statuses
                val now = System.currentTimeMillis()
                val currentStatuses = _uiState.value.ephemeralStatuses
                if (currentStatuses.any { it.isExpired(now) }) {
                    currentStatuses.filter { it.isExpired(now) }.forEach { it.wipeFromRam() }
                    val remaining = currentStatuses.filterNot { it.isExpired(now) }
                    _uiState.update { state ->
                        val viewingExpired = state.activeViewingStatus?.isExpired(now) == true
                        state.copy(
                            ephemeralStatuses = remaining,
                            activeViewingStatus = if (viewingExpired) null else state.activeViewingStatus,
                            currentScreen = if (viewingExpired && state.currentScreen == ZeroTraceScreen.STATUS_VIEWER) {
                                ZeroTraceScreen.HOME
                            } else state.currentScreen
                        )
                    }
                }
            }
        }
    }

    fun selectBottomTab(tab: MainBottomTab) {
        _uiState.update {
            it.copy(
                selectedBottomTab = tab,
                currentScreen = ZeroTraceScreen.HOME,
                errorBannerMessage = null
            )
        }
    }

    fun navigateTo(screen: ZeroTraceScreen) {
        _uiState.update {
            it.copy(
                currentScreen = screen,
                selectedBottomTab = if (screen == ZeroTraceScreen.SETTINGS) MainBottomTab.SETTINGS else it.selectedBottomTab,
                errorBannerMessage = null,
                statusBannerMessage = null
            )
        }
        if (screen == ZeroTraceScreen.MY_IDENTITY || screen == ZeroTraceScreen.ADD_CONTACT) {
            refreshMyPairingQr()
        }
    }

    fun navigateBack(): Boolean {
        val current = _uiState.value.currentScreen
        return when (current) {
            ZeroTraceScreen.SPLASH, ZeroTraceScreen.ONBOARDING, ZeroTraceScreen.IDENTITY_CREATION -> false
            ZeroTraceScreen.HOME -> {
                if (_uiState.value.selectedBottomTab != MainBottomTab.CHATS) {
                    _uiState.update { it.copy(selectedBottomTab = MainBottomTab.CHATS) }
                    true
                } else {
                    false
                }
            }
            ZeroTraceScreen.MEDIA_PREVIEW -> {
                _uiState.update {
                    it.copy(
                        currentScreen = ZeroTraceScreen.CHAT,
                        mediaPreviewMessage = null
                    )
                }
                true
            }
            ZeroTraceScreen.CONTACT_PROFILE -> {
                val targetScreen = if (_uiState.value.activeSessionHandle != null) {
                    ZeroTraceScreen.CHAT
                } else {
                    ZeroTraceScreen.HOME
                }
                _uiState.update { it.copy(currentScreen = targetScreen) }
                true
            }
            ZeroTraceScreen.STATUS_VIEWER -> {
                _uiState.update {
                    it.copy(
                        currentScreen = ZeroTraceScreen.HOME,
                        selectedBottomTab = MainBottomTab.UPDATES,
                        activeViewingStatus = null
                    )
                }
                true
            }
            ZeroTraceScreen.VOICE_CALL, ZeroTraceScreen.VIDEO_CALL, ZeroTraceScreen.INCOMING_CALL -> {
                endActiveCall()
                true
            }
            ZeroTraceScreen.CHAT -> {
                navigateBackToChatsPreservingSession()
                true
            }
            ZeroTraceScreen.CONTACT_VERIFICATION -> {
                _uiState.update {
                    it.copy(
                        currentScreen = ZeroTraceScreen.ADD_CONTACT,
                        pendingScannedInvite = null,
                        identityReplacementAlert = null
                    )
                }
                true
            }
            else -> {
                _uiState.update { it.copy(currentScreen = ZeroTraceScreen.HOME, errorBannerMessage = null) }
                true
            }
        }
    }

    fun acceptOnboardingLimitations(accepted: Boolean) {
        _uiState.update { it.copy(limitationsAccepted = accepted) }
    }

    fun proceedFromOnboardingToIdentityCreation() {
        if (!_uiState.value.limitationsAccepted) return
        _uiState.update { it.copy(currentScreen = ZeroTraceScreen.IDENTITY_CREATION) }
    }

    fun generateAndStoreProtectedIdentity() {
        if (_uiState.value.isGeneratingIdentity) return
        _uiState.update { it.copy(isGeneratingIdentity = true, errorBannerMessage = null) }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val (summary, rawPrivateBundle) = RustCoreBridge.generateNewDeviceIdentity()
                try {
                    keystoreManager.saveWrappedIdentity(rawPrivateBundle)
                } finally {
                    Arrays.fill(rawPrivateBundle, 0.toByte())
                }
                val hwLevel = keystoreManager.getHardwareSecurityLevel()
                val (qrString, parsedQr) = RustCoreBridge.generateSignedPairingQr()

                _uiState.update {
                    it.copy(
                        isGeneratingIdentity = false,
                        identitySummary = summary,
                        hardwareSecurityLevel = hwLevel,
                        myPairingQrString = qrString,
                        myPairingQrExpirySec = parsedQr.expiryUnixSec,
                        statusBannerMessage = isHi(
                            "डिवाइस पहचान उत्पन्न और कीस्टोर AES-256-GCM के तहत सुरक्षित की गई।",
                            "Hybrid Classical + PQ identity generated & wrapped under Keystore AES-256-GCM."
                        )
                    )
                }
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    TorOnionTransportManager.bootstrapTorAndStartOnionService()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingIdentity = false,
                        errorBannerMessage = "Identity generation failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        _uiState.update { it.copy(flagSecureEnabled = enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setAccentPalette(palette: AccentPalette) {
        _uiState.update { it.copy(accentPalette = palette) }
    }

    fun setWallpaperStyle(style: ChatWallpaperStyle) {
        _uiState.update { it.copy(wallpaperStyle = style) }
    }

    fun setDisappearingMessageTimer(seconds: Long) {
        _uiState.update {
            it.copy(
                disappearingMessageTimerSec = seconds,
                sessionTimeoutSeconds = seconds.coerceIn(60L, 1800L)
            )
        }
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(readReceiptsEnabled = enabled) }
    }

    fun setTypingIndicatorsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(typingIndicatorsEnabled = enabled) }
    }

    fun setBiometricAppLockEnabled(enabled: Boolean) {
        _uiState.update { it.copy(biometricAppLockEnabled = enabled) }
    }

    fun setMetadataMinimizedMode(enabled: Boolean) {
        _uiState.update { it.copy(metadataMinimizedMode = enabled) }
    }

    fun finishIdentityCreationToHome() {
        if (_uiState.value.identitySummary == null) return
        _uiState.update {
            it.copy(
                currentScreen = ZeroTraceScreen.HOME,
                selectedBottomTab = MainBottomTab.CHATS
            )
        }
    }

    fun refreshMyPairingQr() {
        if (!RustCoreBridge.isIdentityLoaded()) return
        try {
            val (qr, parsed) = RustCoreBridge.generateSignedPairingQr()
            _uiState.update {
                it.copy(
                    myPairingQrString = qr,
                    myPairingQrExpirySec = parsed.expiryUnixSec
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorBannerMessage = e.message) }
        }
    }

    // -------------------------------------------------------------------------
    // Chats Tab Search, Filtering, Pinning, Muting, Archiving, Group Chat Gate
    // -------------------------------------------------------------------------

    fun updateChatSearchQuery(query: String) {
        _uiState.update { it.copy(chatSearchQuery = query) }
    }

    fun setConversationFilter(filter: ConversationFilter) {
        _uiState.update { it.copy(conversationFilter = filter) }
    }

    fun updateInChatSearchQuery(query: String) {
        _uiState.update { it.copy(inChatSearchQuery = query) }
    }

    fun setShowGroupChatMlsDialog(show: Boolean) {
        _uiState.update { it.copy(showGroupChatMlsDialog = show) }
    }

    fun togglePinConversation(onionAddress: String) {
        _uiState.update { state ->
            val updated = state.pinnedOnions.toMutableSet()
            if (!updated.add(onionAddress)) updated.remove(onionAddress)
            state.copy(pinnedOnions = updated)
        }
    }

    fun toggleMuteConversation(onionAddress: String) {
        _uiState.update { state ->
            val updated = state.mutedOnions.toMutableSet()
            if (!updated.add(onionAddress)) updated.remove(onionAddress)
            state.copy(mutedOnions = updated)
        }
    }

    fun toggleArchiveConversation(onionAddress: String) {
        _uiState.update { state ->
            val updated = state.archivedOnions.toMutableSet()
            if (!updated.add(onionAddress)) updated.remove(onionAddress)
            state.copy(archivedOnions = updated)
        }
    }

    fun toggleBlockPeer(onionAddress: String) {
        _uiState.update { state ->
            val updated = state.blockedOnions.toMutableSet()
            if (!updated.add(onionAddress)) updated.remove(onionAddress)
            state.copy(
                blockedOnions = updated,
                statusBannerMessage = if (onionAddress in updated) {
                    "Peer blocked: inbound Tor circuits from this onion address will be rejected."
                } else {
                    "Peer unblocked."
                }
            )
        }
    }

    fun openContactProfile(contact: VerifiedContactRecord) {
        _uiState.update {
            it.copy(
                inspectedProfileContact = contact,
                currentScreen = ZeroTraceScreen.CONTACT_PROFILE
            )
        }
    }

    fun deleteVerifiedContact(contact: VerifiedContactRecord) {
        clearChatRamForContact(contact.onionAddress)
        val remaining = _uiState.value.verifiedContacts.filterNot {
            it.onionAddress.equals(contact.onionAddress, ignoreCase = true)
        }
        saveEncryptedContactsToDisk(remaining)
        _uiState.update {
            it.copy(
                verifiedContacts = remaining,
                inspectedProfileContact = null,
                currentScreen = ZeroTraceScreen.HOME,
                statusBannerMessage = "Contact '${contact.alias}' removed & wrapped store updated."
            )
        }
    }

    fun clearChatRamForContact(onionAddress: String) {
        _uiState.value.richMessagesByOnion[onionAddress]?.forEach { it.wipeFromRam() }
        _uiState.update { state ->
            val updatedMap = state.richMessagesByOnion.toMutableMap().apply { remove(onionAddress) }
            val updatedPreviews = state.lastMessagePreviewByOnion.toMutableMap().apply { remove(onionAddress) }
            val updatedUnread = state.unreadCountByOnion.toMutableMap().apply { remove(onionAddress) }
            state.copy(
                richMessagesByOnion = updatedMap,
                lastMessagePreviewByOnion = updatedPreviews,
                unreadCountByOnion = updatedUnread,
                statusBannerMessage = "Volatile chat RAM for ${onionAddress.take(10)}…onion zeroized."
            )
        }
    }

    // -------------------------------------------------------------------------
    // QR Pairing & Verification
    // -------------------------------------------------------------------------

    fun onQrInviteScannedOrEntered(rawQr: String, suggestedAlias: String = "") {
        try {
            val parsed = RustCoreBridge.parseAndVerifyPairingQr(rawQr)
            val existingByOnion = _uiState.value.verifiedContacts.find {
                it.onionAddress.equals(parsed.onionAddress, ignoreCase = true)
            }

            if (existingByOnion != null &&
                (!existingByOnion.classicalFingerprintHex.equals(parsed.classicalFingerprintHex, ignoreCase = true) ||
                    !existingByOnion.pqFingerprintHex.equals(parsed.pqFingerprintHex, ignoreCase = true))
            ) {
                _uiState.update {
                    it.copy(
                        pendingScannedInvite = parsed,
                        pendingContactAlias = existingByOnion.alias,
                        identityReplacementAlert = IdentityReplacementAlert(
                            existingContact = existingByOnion,
                            incomingInvite = parsed
                        ),
                        currentScreen = ZeroTraceScreen.CONTACT_VERIFICATION
                    )
                }
                return
            }

            val defaultAlias = suggestedAlias.ifBlank {
                "Peer-${parsed.onionAddress.take(6).uppercase()}"
            }
            _uiState.update {
                it.copy(
                    pendingScannedInvite = parsed,
                    pendingContactAlias = defaultAlias,
                    identityReplacementAlert = null,
                    errorBannerMessage = null,
                    currentScreen = ZeroTraceScreen.CONTACT_VERIFICATION
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorBannerMessage = "QR Verification Rejected: ${e.message}")
            }
        }
    }

    fun scanLoopbackVerificationPeerQr() {
        viewModelScope.launch {
            try {
                val (qrString, record) = TorOnionTransportManager.generateLoopbackPeerQrInvite()
                onQrInviteScannedOrEntered(qrString, record.alias)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorBannerMessage = e.message) }
            }
        }
    }

    fun updatePendingContactAlias(alias: String) {
        _uiState.update { it.copy(pendingContactAlias = alias.take(32)) }
    }

    fun confirmAndSaveVerifiedContact(allowIdentityReplacement: Boolean = false) {
        val invite = _uiState.value.pendingScannedInvite ?: return
        val alert = _uiState.value.identityReplacementAlert
        if (alert != null && !allowIdentityReplacement) {
            _uiState.update {
                it.copy(errorBannerMessage = "Identity replacement blocked: explicit re-verification confirmation required.")
            }
            return
        }

        val alias = _uiState.value.pendingContactAlias.trim().ifBlank {
            "Peer-${invite.onionAddress.take(6).uppercase()}"
        }

        val newRecord = VerifiedContactRecord(
            alias = alias,
            onionAddress = invite.onionAddress,
            classicalFingerprintHex = invite.classicalFingerprintHex,
            pqFingerprintHex = invite.pqFingerprintHex,
            classicalSafetyDigits = invite.classicalSafetyDigits,
            pqSafetyDigits = invite.pqSafetyDigits,
            ed25519PublicKey = invite.ed25519PublicKey,
            x25519PublicKey = invite.x25519PublicKey,
            mlDsa87PublicKey = null,
            mlKem1024EncapsKey = null,
            pairingNonceHex = invite.pairingNonceHex,
            verifiedAtEpochSec = System.currentTimeMillis() / 1000L,
            identityChangedWarning = false
        )

        val updatedList = _uiState.value.verifiedContacts
            .filterNot { it.onionAddress.equals(invite.onionAddress, ignoreCase = true) } + newRecord

        saveEncryptedContactsToDisk(updatedList)

        // Seed an ephemeral P2P status card from the verified loopback peer if none exists yet
        seedLoopbackPeerEphemeralStatusIfNeeded(newRecord)

        _uiState.update {
            it.copy(
                verifiedContacts = updatedList,
                pendingScannedInvite = null,
                pendingContactAlias = "",
                identityReplacementAlert = null,
                currentScreen = ZeroTraceScreen.HOME,
                selectedBottomTab = MainBottomTab.CHATS,
                statusBannerMessage = isHi(
                    "संपर्क '$alias' सत्यापित और एन्क्रिप्टेड रूप में सहेजा गया।",
                    "Contact '$alias' verified & saved in Keystore-wrapped storage."
                )
            )
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            establishWarmSessionInBackground(newRecord)
        }
    }

    private suspend fun establishWarmSessionInBackground(contact: VerifiedContactRecord) {
        val normalized = TorOnionTransportManager.normalizeLoopbackContactIfNeeded(contact)
        val res = TorOnionTransportManager.establishAuthenticatedPeerSession(normalized)
        res.onSuccess { handle ->
            val volatileMsgs = RustCoreBridge.getVolatileMessages(handle)
            val metrics = RustCoreBridge.getSessionMetrics(handle)
            syncVolatileMessagesToRichConversation(normalized.onionAddress, volatileMsgs)
            _uiState.update { state ->
                state.copy(
                    activeSessionHandle = handle,
                    activePeerContact = normalized,
                    activeChatMessages = volatileMsgs,
                    activeRatchetMetrics = metrics,
                    errorBannerMessage = null
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rich Chat Conversation, Replies, Reactions, Encrypted File Transfers
    // -------------------------------------------------------------------------

    fun startAuthenticatedSessionWithContact(contact: VerifiedContactRecord) {
        val normalizedContact = TorOnionTransportManager.normalizeLoopbackContactIfNeeded(contact)
        if (normalizedContact.onionAddress in _uiState.value.blockedOnions) {
            _uiState.update {
                it.copy(errorBannerMessage = "Cannot start session: '${normalizedContact.alias}' is currently blocked.")
            }
            return
        }

        // Check if a live P2P session or warm session is already active for this peer
        val existingHandle = TorOnionTransportManager.getActiveRealPeerSessionHandle(normalizedContact.onionAddress)
            ?: _uiState.value.activeSessionHandle?.takeIf {
                _uiState.value.activePeerContact?.onionAddress.equals(normalizedContact.onionAddress, ignoreCase = true)
            }
        if (existingHandle != null) {
            val volatileMsgs = RustCoreBridge.getVolatileMessages(existingHandle)
            val metrics = RustCoreBridge.getSessionMetrics(existingHandle)
            if (metrics != null) {
                viewModelScope.launch {
                    syncVolatileMessagesToRichConversation(normalizedContact.onionAddress, volatileMsgs)
                }
                _uiState.update { state ->
                    val clearedUnread = state.unreadCountByOnion.toMutableMap().apply {
                        put(normalizedContact.onionAddress, 0)
                    }
                    state.copy(
                        activeSessionHandle = existingHandle,
                        activePeerContact = normalizedContact,
                        activeChatMessages = volatileMsgs,
                        activeRatchetMetrics = metrics,
                        unreadCountByOnion = clearedUnread,
                        currentScreen = ZeroTraceScreen.CHAT,
                        statusBannerMessage = null,
                        errorBannerMessage = null
                    )
                }
                return
            }
        }

        // Open the Chat screen immediately and establish the P2P session over the Hotspot/LAN circuit
        _uiState.update { state ->
            val clearedUnread = state.unreadCountByOnion.toMutableMap().apply {
                put(normalizedContact.onionAddress, 0)
            }
            state.copy(
                activePeerContact = normalizedContact,
                unreadCountByOnion = clearedUnread,
                currentScreen = ZeroTraceScreen.CHAT,
                statusBannerMessage = isHi(
                    "${normalizedContact.alias} के साथ हाइब्रिड PQ हैंडशेक स्थापित किया जा रहा है…",
                    "Connecting ZT-PQXDH-P2P-v1 circuit with ${normalizedContact.alias}…"
                ),
                errorBannerMessage = null
            )
        }

        viewModelScope.launch {
            SessionService.startServiceIfPermitted(appContext)
            val result = TorOnionTransportManager.establishAuthenticatedPeerSession(normalizedContact)
            result.fold(
                onSuccess = { handle ->
                    val volatileMsgs = RustCoreBridge.getVolatileMessages(handle)
                    val metrics = RustCoreBridge.getSessionMetrics(handle)
                    syncVolatileMessagesToRichConversation(normalizedContact.onionAddress, volatileMsgs)

                    _uiState.update { state ->
                        val clearedUnread = state.unreadCountByOnion.toMutableMap().apply {
                            put(normalizedContact.onionAddress, 0)
                        }
                        state.copy(
                            activeSessionHandle = handle,
                            activePeerContact = normalizedContact,
                            activeChatMessages = volatileMsgs,
                            activeRatchetMetrics = metrics,
                            unreadCountByOnion = clearedUnread,
                            currentScreen = ZeroTraceScreen.CHAT,
                            statusBannerMessage = null,
                            errorBannerMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    // Check if an incoming peer handshake succeeded concurrently while we were connecting
                    val concurrentHandle = TorOnionTransportManager.getActiveRealPeerSessionHandle(normalizedContact.onionAddress)
                    if (concurrentHandle != null) {
                        val volatileMsgs = RustCoreBridge.getVolatileMessages(concurrentHandle)
                        val metrics = RustCoreBridge.getSessionMetrics(concurrentHandle)
                        syncVolatileMessagesToRichConversation(normalizedContact.onionAddress, volatileMsgs)
                        _uiState.update { state ->
                            state.copy(
                                activeSessionHandle = concurrentHandle,
                                activePeerContact = normalizedContact,
                                activeChatMessages = volatileMsgs,
                                activeRatchetMetrics = metrics,
                                statusBannerMessage = null,
                                errorBannerMessage = null
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                statusBannerMessage = err.message ?: "Waiting for peer on Tor .onion / P2P circuit…",
                                errorBannerMessage = null
                            )
                        }
                    }
                }
            )
        }
    }

    fun sendEphemeralChatMessage(messageText: String, forcePqBraidStep: Boolean = false) {
        val contact = _uiState.value.activePeerContact ?: return
        val trimmed = messageText.trim()
        if (trimmed.isEmpty()) return

        val replyTarget = _uiState.value.replyingToMessage
        val msgId = UUID.randomUUID().toString()
        val wirePlaintext = if (replyTarget != null) {
            E2eeChatEnvelope.encodeText(
                msgId = msgId,
                body = trimmed,
                replyToId = replyTarget.id,
                replyToPreview = replyTarget.bodyText.take(80),
                replyToSender = replyTarget.senderLabel
            )
        } else {
            E2eeChatEnvelope.encodeText(msgId = msgId, body = trimmed)
        }

        viewModelScope.launch {
            var handle = TorOnionTransportManager.getActiveRealPeerSessionHandle(contact.onionAddress)
                ?: _uiState.value.activeSessionHandle

            if (handle == null) {
                val connectRes = TorOnionTransportManager.establishAuthenticatedPeerSession(contact)
                handle = connectRes.getOrNull()
                    ?: TorOnionTransportManager.getActiveRealPeerSessionHandle(contact.onionAddress)
                if (handle == null) {
                    _uiState.update {
                        it.copy(
                            errorBannerMessage = connectRes.exceptionOrNull()?.message
                                ?: "Peer device is not reachable yet on Tor .onion / P2P circuit."
                        )
                    }
                    return@launch
                }
            }

            val result = TorOnionTransportManager.sendEncryptedMessageOverCircuit(
                sessionHandle = handle,
                plaintext = wirePlaintext,
                forcePqBraidStep = forcePqBraidStep,
                contact = contact
            )
            result.fold(
                onSuccess = { updatedMessages ->
                    val effectiveHandle = TorOnionTransportManager.getActiveRealPeerSessionHandle(contact.onionAddress) ?: handle
                    syncVolatileMessagesToRichConversation(
                        onionAddress = contact.onionAddress,
                        volatileMessages = updatedMessages
                    )
                    _uiState.update {
                        it.copy(
                            activeSessionHandle = effectiveHandle,
                            activeChatMessages = updatedMessages,
                            activeRatchetMetrics = RustCoreBridge.getSessionMetrics(effectiveHandle),
                            replyingToMessage = null,
                            statusBannerMessage = null,
                            errorBannerMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(errorBannerMessage = "Send failed (Fail-Closed): ${err.message}")
                    }
                }
            )
        }
    }

    fun setReplyingToMessage(message: RichConversationMessage?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun setInspectedMessageInfo(message: RichConversationMessage?) {
        _uiState.update { it.copy(inspectedMessageInfo = message) }
    }

    fun openMediaPreview(message: RichConversationMessage) {
        _uiState.update {
            it.copy(
                mediaPreviewMessage = message,
                currentScreen = ZeroTraceScreen.MEDIA_PREVIEW
            )
        }
    }

    fun reactToMessage(message: RichConversationMessage, emoji: String) {
        val contact = _uiState.value.activePeerContact ?: return
        _uiState.update { state ->
            val currentList = state.richMessagesByOnion[contact.onionAddress].orEmpty()
            val updatedList = currentList.map { item ->
                if (item.id == message.id) {
                    item.copy(reactionEmoji = emoji.ifBlank { null })
                } else {
                    item
                }
            }
            val map = state.richMessagesByOnion.toMutableMap()
            map[contact.onionAddress] = updatedList
            state.copy(richMessagesByOnion = map)
        }
        val handle = TorOnionTransportManager.getActiveRealPeerSessionHandle(contact.onionAddress)
            ?: _uiState.value.activeSessionHandle ?: return
        if (!TorOnionTransportManager.isLoopbackVerificationContact(contact)) {
            viewModelScope.launch {
                val reactPayload = E2eeChatEnvelope.encodeReaction(message.id, emoji)
                TorOnionTransportManager.sendEncryptedMessageOverCircuit(
                    sessionHandle = handle,
                    plaintext = reactPayload,
                    contact = contact
                )
            }
        }
    }

    fun deleteSingleMessageFromRam(message: RichConversationMessage) {
        val contact = _uiState.value.activePeerContact ?: return
        message.wipeFromRam()
        _uiState.update { state ->
            val currentList = state.richMessagesByOnion[contact.onionAddress].orEmpty()
            val updatedList = currentList.filterNot { it.id == message.id }
            val map = state.richMessagesByOnion.toMutableMap()
            map[contact.onionAddress] = updatedList
            state.copy(
                richMessagesByOnion = map,
                statusBannerMessage = "Message #${message.ratchetStepIndex} & associated keys zeroized from RAM."
            )
        }
    }

    fun setShowAttachmentSheet(show: Boolean) {
        _uiState.update { it.copy(showAttachmentSheet = show) }
    }

    fun startRealVoiceRecording() {
        if (_uiState.value.isRecordingVoiceNote) return
        val started = VoiceNoteRecorder.startRecording()
        if (started) {
            _uiState.update {
                it.copy(
                    isRecordingVoiceNote = true,
                    errorBannerMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isRecordingVoiceNote = false,
                    errorBannerMessage = "Microphone unavailable or permission denied for voice recording."
                )
            }
        }
    }

    fun cancelVoiceNoteRecording() {
        VoiceNoteRecorder.cancelAndWipe()
        _uiState.update { it.copy(isRecordingVoiceNote = false, statusBannerMessage = null) }
    }

    fun toggleVoiceNoteRecording() {
        val currentlyRecording = _uiState.value.isRecordingVoiceNote
        if (!currentlyRecording) {
            startRealVoiceRecording()
        } else {
            _uiState.update { it.copy(isRecordingVoiceNote = false, statusBannerMessage = null) }
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val finalized = VoiceNoteRecorder.stopAndFinalizeWav()
                if (finalized == null) {
                    _uiState.update {
                        it.copy(statusBannerMessage = "Voice note too short — recording discarded from RAM.")
                    }
                    return@launch
                }
                val (wavBytes, durationSec) = finalized
                sendEncryptedAttachmentPayload(
                    kind = MessagePayloadKind.VOICE_NOTE,
                    fileName = "voice_note_${System.currentTimeMillis() % 10000}.wav",
                    mimeType = "audio/wav",
                    samplePayloadBytes = wavBytes,
                    caption = "Voice Note (${durationSec}s)",
                    voiceDurationSec = durationSec
                )
            }
        }
    }

    fun toggleVoiceNotePlayback(message: RichConversationMessage) {
        val bytes = message.fileTransfer?.decryptedInMemoryBytes ?: return
        VoiceNotePlayer.togglePlay(message.id, bytes)
    }

    fun sendMediaFromUri(uri: Uri, kind: MessagePayloadKind) {
        _uiState.update { it.copy(showAttachmentSheet = false) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val loaded = MediaRamLoader.loadUriToRam(appContext, uri, kind)
                sendEncryptedAttachmentPayload(
                    kind = kind,
                    fileName = loaded.fileName,
                    mimeType = loaded.mimeType,
                    samplePayloadBytes = loaded.payloadBytes,
                    caption = loaded.caption
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorBannerMessage = "Media load rejected: ${e.message}")
                }
            }
        }
    }

    fun sendCapturedCameraBitmap(bitmap: Bitmap) {
        _uiState.update { it.copy(showAttachmentSheet = false) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val loaded = MediaRamLoader.encodeCameraBitmapToCleanJpeg(bitmap)
                sendEncryptedAttachmentPayload(
                    kind = MessagePayloadKind.IMAGE,
                    fileName = loaded.fileName,
                    mimeType = loaded.mimeType,
                    samplePayloadBytes = loaded.payloadBytes,
                    caption = loaded.caption
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorBannerMessage = "Camera capture rejected: ${e.message}")
                }
            }
        }
    }

    fun sendEncryptedAttachmentPayload(
        kind: MessagePayloadKind,
        fileName: String,
        mimeType: String,
        samplePayloadBytes: ByteArray,
        caption: String,
        voiceDurationSec: Int? = null
    ) {
        val contact = _uiState.value.activePeerContact ?: return
        val handle = TorOnionTransportManager.getActiveRealPeerSessionHandle(contact.onionAddress)
            ?: _uiState.value.activeSessionHandle ?: return
        val replyTarget = _uiState.value.replyingToMessage

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Encrypt & chunk payload with a dedicated 256-bit ChaCha20-Poly1305 file key
                val bundle = EncryptedFileTransferEngine.encryptAndChunkPayload(
                    rawFileName = fileName,
                    mimeType = mimeType,
                    payloadBytes = samplePayloadBytes,
                    stripImageMetadata = (kind == MessagePayloadKind.IMAGE)
                )

                // 2. Verify round-trip chunk reassembly + SHA-256 integrity for local sender preview in RAM
                val reassembled = EncryptedFileTransferEngine.decryptAndVerifyChunks(
                    manifest = bundle.manifest,
                    fileSecretKey32 = bundle.fileSecretKey32,
                    chunks = bundle.chunks
                )

                // 3. Transmit actual ChaCha20-Poly1305 encrypted chunks to peer device (if real peer)
                val isRealPeer = !TorOnionTransportManager.isLoopbackVerificationContact(contact)
                if (isRealPeer) {
                    val chunksSent = TorOnionTransportManager.sendEncryptedMediaChunks(
                        peerOnion = contact.onionAddress,
                        transferId = bundle.manifest.transferId,
                        chunks = bundle.chunks
                    )
                    if (!chunksSent) {
                        bundle.wipeZeroize()
                        Arrays.fill(reassembled, 0.toByte())
                        _uiState.update {
                            it.copy(
                                showAttachmentSheet = false,
                                errorBannerMessage = "Media transfer failed: Peer P2P circuit not connected."
                            )
                        }
                        return@launch
                    }
                }

                val transferProgress = EncryptedTransferProgress(
                    manifest = bundle.manifest,
                    direction = TransferDirection.OUTGOING,
                    state = TransferLifecycleState.VERIFIED_READY_IN_RAM,
                    chunksProcessed = bundle.manifest.totalChunks,
                    bytesTransferred = bundle.manifest.totalSizeBytes.toLong(),
                    decryptedInMemoryBytes = reassembled
                )

                val msgId = UUID.randomUUID().toString()
                val wireEnvelope = E2eeChatEnvelope.encodeMedia(
                    msgId = msgId,
                    payloadKind = kind,
                    caption = caption,
                    voiceDurationSec = voiceDurationSec,
                    manifest = bundle.manifest,
                    fileSecretKey32 = bundle.fileSecretKey32,
                    replyToId = replyTarget?.id,
                    replyToPreview = replyTarget?.bodyText?.take(80),
                    replyToSender = replyTarget?.senderLabel
                )

                val sendRes = TorOnionTransportManager.sendEncryptedMessageOverCircuit(
                    sessionHandle = handle,
                    plaintext = wireEnvelope,
                    forcePqBraidStep = false,
                    contact = contact
                )

                sendRes.fold(
                    onSuccess = { updatedVolatile ->
                        // Mark outgoing volatile message ID so sync does not duplicate it
                        updatedVolatile.lastOrNull { it.isOutgoing }?.let { vm ->
                            processedVolatileMsgKeys.add("${contact.onionAddress}:${vm.messageId}")
                        }
                        val effectiveHandle = TorOnionTransportManager.getActiveRealPeerSessionHandle(contact.onionAddress) ?: handle
                        val metrics = RustCoreBridge.getSessionMetrics(effectiveHandle)
                        val richMsg = RichConversationMessage(
                            id = msgId,
                            senderLabel = "You",
                            bodyText = caption,
                            timestampEpochMillis = System.currentTimeMillis(),
                            isOutgoing = true,
                            ratchetStepIndex = (metrics?.sendCount ?: 1L).toInt(),
                            pqEpoch = metrics?.ratchetEpoch ?: 1,
                            pqBraidAdvanced = false,
                            payloadKind = kind,
                            replyToMessageId = replyTarget?.id,
                            replyToPreview = replyTarget?.bodyText?.take(80),
                            replyToSenderLabel = replyTarget?.senderLabel,
                            fileTransfer = transferProgress,
                            voiceDurationSeconds = voiceDurationSec
                        )
                        bundle.wipeZeroize()

                        _uiState.update { state ->
                            val currentRich = state.richMessagesByOnion[contact.onionAddress].orEmpty()
                            val updatedMap = state.richMessagesByOnion.toMutableMap()
                            updatedMap[contact.onionAddress] = currentRich + richMsg
                            val previews = state.lastMessagePreviewByOnion.toMutableMap().apply {
                                val icon = if (kind == MessagePayloadKind.VOICE_NOTE) "🎤" else "📎"
                                put(contact.onionAddress, "$icon ${bundle.manifest.sanitizedFileName}")
                            }
                            val times = state.lastMessageTimeByOnion.toMutableMap().apply {
                                put(contact.onionAddress, richMsg.timestampEpochMillis)
                            }
                            state.copy(
                                showAttachmentSheet = false,
                                replyingToMessage = null,
                                activeSessionHandle = effectiveHandle,
                                activeChatMessages = updatedVolatile,
                                activeRatchetMetrics = metrics,
                                richMessagesByOnion = updatedMap,
                                lastMessagePreviewByOnion = previews,
                                lastMessageTimeByOnion = times,
                                statusBannerMessage = null,
                                errorBannerMessage = null
                            )
                        }
                    },
                    onFailure = { err ->
                        bundle.wipeZeroize()
                        Arrays.fill(reassembled, 0.toByte())
                        _uiState.update {
                            it.copy(
                                showAttachmentSheet = false,
                                errorBannerMessage = "Attachment transfer aborted (Fail-Closed): ${err.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showAttachmentSheet = false,
                        errorBannerMessage = "Encrypted file engine rejected attachment: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun syncVolatileMessagesToRichConversation(
        onionAddress: String,
        volatileMessages: List<VolatileChatMessage>
    ) {
        val currentRich = _uiState.value.richMessagesByOnion[onionAddress].orEmpty().toMutableList()
        val peerAlias = _uiState.value.verifiedContacts.firstOrNull {
            it.onionAddress.equals(onionAddress, ignoreCase = true)
        }?.alias ?: onionAddress.take(10)

        var changed = false
        for (vm in volatileMessages) {
            val volKey = "$onionAddress:${vm.messageId}"
            if (!processedVolatileMsgKeys.add(volKey)) continue

            val rawText = vm.readPlaintext()
            val decoded = E2eeChatEnvelope.decode(rawText, vm.messageId.toString())
            when (decoded) {
                is E2eeChatEnvelope.Decoded.Reaction -> {
                    val idx = currentRich.indexOfFirst { it.id == decoded.targetMsgId }
                    if (idx >= 0) {
                        currentRich[idx] = currentRich[idx].copy(reactionEmoji = decoded.emoji.ifBlank { null })
                        changed = true
                    }
                }

                is E2eeChatEnvelope.Decoded.Text -> {
                    if (currentRich.any { it.id == decoded.msgId }) continue
                    currentRich.add(
                        RichConversationMessage(
                            id = decoded.msgId,
                            senderLabel = if (vm.isOutgoing) "You" else peerAlias,
                            bodyText = decoded.body,
                            timestampEpochMillis = vm.timestampEpochSec * 1000L,
                            isOutgoing = vm.isOutgoing,
                            ratchetStepIndex = vm.msgCounter.toInt(),
                            pqEpoch = vm.ratchetEpoch,
                            pqBraidAdvanced = vm.pqBraidStepTriggered,
                            payloadKind = MessagePayloadKind.TEXT,
                            replyToMessageId = decoded.replyToId,
                            replyToPreview = decoded.replyToPreview,
                            replyToSenderLabel = decoded.replyToSender
                        )
                    )
                    changed = true
                }

                is E2eeChatEnvelope.Decoded.Media -> {
                    if (currentRich.any { it.id == decoded.msgId }) continue
                    val chunks = TorOnionTransportManager.awaitAndTakeMediaChunks(
                        transferId = decoded.manifest.transferId,
                        expectedChunks = decoded.manifest.totalChunks
                    )
                    val progress = if (chunks != null) {
                        try {
                            val decryptedBytes = EncryptedFileTransferEngine.decryptAndVerifyChunks(
                                manifest = decoded.manifest,
                                fileSecretKey32 = decoded.fileSecretKey32,
                                chunks = chunks
                            )
                            chunks.forEach { it.wipeZeroize() }
                            EncryptedTransferProgress(
                                manifest = decoded.manifest,
                                direction = TransferDirection.INCOMING,
                                state = TransferLifecycleState.VERIFIED_READY_IN_RAM,
                                chunksProcessed = decoded.manifest.totalChunks,
                                bytesTransferred = decoded.manifest.totalSizeBytes.toLong(),
                                decryptedInMemoryBytes = decryptedBytes
                            )
                        } catch (e: Exception) {
                            EncryptedTransferProgress(
                                manifest = decoded.manifest,
                                direction = TransferDirection.INCOMING,
                                state = TransferLifecycleState.INTEGRITY_FAILED,
                                chunksProcessed = chunks.size,
                                bytesTransferred = 0L,
                                failureReason = e.message
                            )
                        }
                    } else {
                        EncryptedTransferProgress(
                            manifest = decoded.manifest,
                            direction = TransferDirection.INCOMING,
                            state = TransferLifecycleState.INTEGRITY_FAILED,
                            chunksProcessed = 0,
                            bytesTransferred = 0L,
                            failureReason = "Encrypted media chunks missing or interrupted"
                        )
                    }
                    Arrays.fill(decoded.fileSecretKey32, 0.toByte())

                    currentRich.add(
                        RichConversationMessage(
                            id = decoded.msgId,
                            senderLabel = if (vm.isOutgoing) "You" else peerAlias,
                            bodyText = decoded.caption,
                            timestampEpochMillis = vm.timestampEpochSec * 1000L,
                            isOutgoing = vm.isOutgoing,
                            ratchetStepIndex = vm.msgCounter.toInt(),
                            pqEpoch = vm.ratchetEpoch,
                            pqBraidAdvanced = vm.pqBraidStepTriggered,
                            payloadKind = decoded.payloadKind,
                            replyToMessageId = decoded.replyToId,
                            replyToPreview = decoded.replyToPreview,
                            replyToSenderLabel = decoded.replyToSender,
                            fileTransfer = progress,
                            voiceDurationSeconds = decoded.voiceDurationSec
                        )
                    )
                    changed = true
                }
            }
        }

        if (!changed) return
        val lastMsg = currentRich.lastOrNull()
        _uiState.update { state ->
            val map = state.richMessagesByOnion.toMutableMap()
            map[onionAddress] = currentRich
            val previews = state.lastMessagePreviewByOnion.toMutableMap()
            val times = state.lastMessageTimeByOnion.toMutableMap()
            if (lastMsg != null) {
                val previewStr = when (lastMsg.payloadKind) {
                    MessagePayloadKind.VOICE_NOTE -> "🎤 Voice Note (${lastMsg.voiceDurationSeconds ?: 1}s)"
                    MessagePayloadKind.IMAGE, MessagePayloadKind.VIDEO, MessagePayloadKind.DOCUMENT, MessagePayloadKind.AUDIO_FILE ->
                        "📎 ${lastMsg.fileTransfer?.manifest?.sanitizedFileName ?: lastMsg.bodyText}"
                    else -> lastMsg.bodyText
                }
                previews[onionAddress] = previewStr
                times[onionAddress] = lastMsg.timestampEpochMillis
            }
            state.copy(
                richMessagesByOnion = map,
                lastMessagePreviewByOnion = previews,
                lastMessageTimeByOnion = times
            )
        }
    }

    fun triggerManualPqBraidRatchetStep() {
        val contact = _uiState.value.activePeerContact ?: return
        val liveHandle = TorOnionTransportManager.getActiveRealPeerSessionHandle(contact.onionAddress)
            ?: _uiState.value.activeSessionHandle
        if (liveHandle == null || !TorOnionTransportManager.isPeerConnectedOnMesh(contact.onionAddress)) {
            startAuthenticatedSessionWithContact(contact)
        } else {
            sendEphemeralChatMessage("[PQ-BRAID-REKEY] Advancing ML-KEM-1024 + X25519 epoch", forcePqBraidStep = true)
        }
    }

    fun navigateBackToChatsPreservingSession() {
        _uiState.update {
            it.copy(
                currentScreen = ZeroTraceScreen.HOME,
                selectedBottomTab = MainBottomTab.CHATS,
                replyingToMessage = null,
                inspectedMessageInfo = null,
                mediaPreviewMessage = null,
                showAttachmentSheet = false,
                statusBannerMessage = null,
                errorBannerMessage = null
            )
        }
    }

    fun terminateActiveChatSession() {
        val handle = _uiState.value.activeSessionHandle
        val peerOnion = _uiState.value.activePeerContact?.onionAddress
        VoiceNoteRecorder.cancelAndWipe()
        VoiceNotePlayer.stop()
        EncryptedCallMediaEngine.stopAndWipe()
        if (handle != null) {
            TorOnionTransportManager.disconnectPeerSession(handle)
        }
        if (peerOnion != null) {
            _uiState.value.richMessagesByOnion[peerOnion]?.forEach { it.wipeFromRam() }
            processedVolatileMsgKeys.removeIf { it.startsWith("$peerOnion:") }
        }
        SessionService.stopServiceAndWipe(appContext)
        _uiState.update { state ->
            val updatedRich = state.richMessagesByOnion.toMutableMap().apply {
                if (peerOnion != null) remove(peerOnion)
            }
            state.copy(
                activeSessionHandle = null,
                activePeerContact = null,
                activeChatMessages = emptyList(),
                richMessagesByOnion = updatedRich,
                replyingToMessage = null,
                inspectedMessageInfo = null,
                mediaPreviewMessage = null,
                showAttachmentSheet = false,
                activeRatchetMetrics = null,
                currentScreen = ZeroTraceScreen.HOME,
                statusBannerMessage = isHi(
                    "सत्र समाप्त: अस्थायी चैट बफ़र और रैचेट कुंजियाँ RAM से मिटा दी गईं।",
                    "Session terminated: volatile chat buffers & ratchet keys zeroized from RAM."
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Ephemeral P2P Status / Updates Tab
    // -------------------------------------------------------------------------

    private fun seedLoopbackPeerEphemeralStatusIfNeeded(contact: VerifiedContactRecord) {
        if (!TorOnionTransportManager.isLoopbackVerificationContact(contact)) return
        val alreadyExists = _uiState.value.ephemeralStatuses.any {
            it.authorOnion.equals(contact.onionAddress, ignoreCase = true)
        }
        if (alreadyExists) return

        val now = System.currentTimeMillis()
        val sampleStatus = EphemeralStatusItem(
            statusId = UUID.randomUUID().toString(),
            authorAlias = contact.alias,
            authorOnion = contact.onionAddress,
            isOwnStatus = false,
            mediaType = StatusMediaType.TEXT_CARD,
            captionText = "Tor v3 Hidden Service online • Hybrid ML-KEM-1024 + X25519 ratchet ready in RAM.",
            backgroundHex = "#00A859",
            createdAtEpochMillis = now,
            expiresOnSessionClose = false,
            privacyPolicy = StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS,
            viewedByLocalUser = false,
            expiresAtEpochMillis = now + STATUS_TTL_24H_MS
        )
        _uiState.update { state ->
            state.copy(ephemeralStatuses = listOf(sampleStatus) + state.ephemeralStatuses)
        }
    }

    fun publishOwnEphemeralStatus(
        caption: String,
        mediaType: StatusMediaType = StatusMediaType.TEXT_CARD,
        backgroundHex: String = "#00A859",
        mediaUri: Uri? = null,
        selectedPeerOnions: Set<String>? = null
    ) {
        val trimmed = caption.trim()
        if (trimmed.isEmpty() && mediaUri == null) return
        val mySummary = _uiState.value.identitySummary
        val myOnion = mySummary?.onionAddress ?: "local-identity.onion"
        val policy = _uiState.value.statusPrivacyPolicy

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val loadedMedia = if (mediaUri != null && mediaType != StatusMediaType.TEXT_CARD) {
                    val kind = if (mediaType == StatusMediaType.VIDEO_CARD) {
                        MessagePayloadKind.VIDEO
                    } else {
                        MessagePayloadKind.IMAGE
                    }
                    MediaRamLoader.loadUriToRam(appContext, mediaUri, kind)
                } else null

                val now = System.currentTimeMillis()
                val newItem = EphemeralStatusItem(
                    statusId = UUID.randomUUID().toString(),
                    authorAlias = "My Status",
                    authorOnion = myOnion,
                    isOwnStatus = true,
                    mediaType = mediaType,
                    captionText = trimmed.ifEmpty {
                        when (mediaType) {
                            StatusMediaType.VIDEO_CARD -> "Encrypted Video Status (24h)"
                            StatusMediaType.IMAGE_CARD -> "Encrypted Photo Status (24h)"
                            else -> ""
                        }
                    },
                    backgroundHex = backgroundHex,
                    createdAtEpochMillis = now,
                    expiresOnSessionClose = false,
                    privacyPolicy = policy,
                    encryptedPayloadBytes = loadedMedia?.payloadBytes,
                    viewedByLocalUser = true,
                    expiresAtEpochMillis = now + STATUS_TTL_24H_MS,
                    mimeType = loadedMedia?.mimeType,
                    sanitizedFileName = loadedMedia?.fileName
                )

                _uiState.update { state ->
                    state.copy(
                        ephemeralStatuses = listOf(newItem) + state.ephemeralStatuses,
                        statusBannerMessage = isHi(
                            "स्टेटस 24 घंटे के लिए RAM में सहेजा गया — सत्यापित पीयर्स को भेजा जा रहा है…",
                            "Status saved in RAM (auto-deletes in 24h) — syncing encrypted to peers…"
                        )
                    )
                }

                val targets = if (policy == StatusPrivacyPolicy.SELECTED_PEERS_ONLY && !selectedPeerOnions.isNullOrEmpty()) {
                    selectedPeerOnions
                } else {
                    null
                }
                val deliveredCount = TorOnionTransportManager.broadcastStatusItem(newItem, targets)
                _uiState.update { state ->
                    state.copy(
                        statusBannerMessage = if (deliveredCount > 0) {
                            isHi(
                                "E2EE स्टेटस $deliveredCount कनेक्टेड पीयर को भेजा गया (24h में स्वतः डिलीट)।",
                                "E2EE status delivered to $deliveredCount connected peer(s) • Auto-deletes in 24h."
                            )
                        } else {
                            isHi(
                                "स्टेटस तैयार (24h) • पीयर के ऑनलाइन/कनेक्ट होते ही स्वतः सिंक होगा।",
                                "Status live (24h auto-delete) • Will deliver automatically when verified peer connects."
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorBannerMessage = "Could not publish status media: ${e.message}")
                }
            }
        }
    }

    fun saveMessageMediaToGallery(message: RichConversationMessage) {
        val transfer = message.fileTransfer ?: return
        val bytes = transfer.decryptedInMemoryBytes
        if (bytes == null || bytes.isEmpty()) {
            _uiState.update {
                it.copy(errorBannerMessage = "Decrypted payload was already zeroized from RAM.")
            }
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = MediaExportAndOpenHelper.saveDecryptedMediaToDevice(
                context = appContext,
                fileName = transfer.manifest.sanitizedFileName,
                mimeType = transfer.manifest.mimeType,
                kind = message.payloadKind,
                bytes = bytes
            )
            res.fold(
                onSuccess = { savedPath ->
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = isHi(
                                "गैलरी/डाउनलोड में सहेजा गया: $savedPath",
                                "Saved to device Gallery/Downloads: $savedPath"
                            )
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(errorBannerMessage = "Could not save media: ${err.message}")
                    }
                }
            )
        }
    }

    fun saveStatusMediaToGallery(status: EphemeralStatusItem) {
        val bytes = status.encryptedPayloadBytes
        if (bytes == null || bytes.isEmpty()) {
            _uiState.update { it.copy(errorBannerMessage = "No downloadable photo/video payload in this status.") }
            return
        }
        val kind = if (status.mediaType == StatusMediaType.VIDEO_CARD) MessagePayloadKind.VIDEO else MessagePayloadKind.IMAGE
        val name = status.sanitizedFileName ?: if (kind == MessagePayloadKind.VIDEO) {
            "status_${status.statusId.take(6)}.mp4"
        } else {
            "status_${status.statusId.take(6)}.jpg"
        }
        val mime = status.mimeType ?: if (kind == MessagePayloadKind.VIDEO) "video/mp4" else "image/jpeg"
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val res = MediaExportAndOpenHelper.saveDecryptedMediaToDevice(
                context = appContext,
                fileName = name,
                mimeType = mime,
                kind = kind,
                bytes = bytes
            )
            res.fold(
                onSuccess = { savedPath ->
                    _uiState.update {
                        it.copy(statusBannerMessage = "Saved status media to Gallery: $savedPath")
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(errorBannerMessage = "Could not save status media: ${err.message}")
                    }
                }
            )
        }
    }

    fun openMessageMediaExternally(message: RichConversationMessage) {
        val transfer = message.fileTransfer ?: return
        val bytes = transfer.decryptedInMemoryBytes
        if (bytes == null || bytes.isEmpty()) {
            _uiState.update { it.copy(errorBannerMessage = "Decrypted file is no longer in RAM.") }
            return
        }
        val res = MediaExportAndOpenHelper.openDecryptedFileExternally(
            context = appContext,
            fileName = transfer.manifest.sanitizedFileName,
            mimeType = transfer.manifest.mimeType,
            kind = message.payloadKind,
            bytes = bytes
        )
        res.onFailure { err ->
            _uiState.update {
                it.copy(errorBannerMessage = "No viewer app available or cannot open: ${err.message}")
            }
        }
    }

    fun openStatusViewer(status: EphemeralStatusItem) {
        _uiState.update { state ->
            val updatedList = state.ephemeralStatuses.map {
                if (it.statusId == status.statusId) it.copy(viewedByLocalUser = true) else it
            }
            state.copy(
                ephemeralStatuses = updatedList,
                activeViewingStatus = status.copy(viewedByLocalUser = true),
                currentScreen = ZeroTraceScreen.STATUS_VIEWER
            )
        }
    }

    fun deleteEphemeralStatus(statusId: String) {
        _uiState.update { state ->
            state.ephemeralStatuses.find { it.statusId == statusId }?.wipeFromRam()
            val remaining = state.ephemeralStatuses.filterNot { it.statusId == statusId }
            state.copy(
                ephemeralStatuses = remaining,
                activeViewingStatus = if (state.activeViewingStatus?.statusId == statusId) null else state.activeViewingStatus,
                currentScreen = if (state.currentScreen == ZeroTraceScreen.STATUS_VIEWER) ZeroTraceScreen.HOME else state.currentScreen,
                statusBannerMessage = "Ephemeral status payload zeroized from RAM."
            )
        }
    }

    fun setStatusPrivacyPolicy(policy: StatusPrivacyPolicy) {
        _uiState.update { it.copy(statusPrivacyPolicy = policy) }
    }

    // -------------------------------------------------------------------------
    // Encrypted Voice & Video Calling over Tor TCP
    // -------------------------------------------------------------------------

    fun setCallFilter(filter: CallDirectionFilter) {
        _uiState.update { it.copy(callFilter = filter) }
    }

    fun updateCallSearchQuery(query: String) {
        _uiState.update { it.copy(callSearchQuery = query) }
    }

    fun startEncryptedCall(contact: VerifiedContactRecord, mediaKind: CallMediaKind) {
        if (connectivityState.value == ConnectivityState.OFFLINE ||
            connectivityState.value == ConnectivityState.ERROR
        ) {
            _uiState.update {
                it.copy(
                    errorBannerMessage = "Fail-Closed Call Policy: Cannot start call while Tor transport is offline (Clearnet STUN/UDP is strictly disabled)."
                )
            }
            return
        }

        val isLoopback = TorOnionTransportManager.isLoopbackVerificationContact(contact)
        if (!isLoopback && !TorOnionTransportManager.isPeerConnectedOnMesh(contact.onionAddress)) {
            _uiState.update {
                it.copy(
                    errorBannerMessage = "Peer ${contact.alias} is not currently connected on Tor / P2P circuit. Connect first before calling."
                )
            }
            return
        }

        val callId = UUID.randomUUID().toString()
        val (ephemPriv, ephemPub) = EncryptedCallMediaEngine.generateEphemeralX25519KeyPair()
        pendingCallPrivKey32?.let { Arrays.fill(it, 0.toByte()) }
        pendingCallPrivKey32 = ephemPriv

        val initialCallState = ActiveCallUiState(
            callId = callId,
            peerAlias = contact.alias,
            peerOnion = contact.onionAddress,
            peerSafetyDigits = contact.classicalSafetyDigits,
            mediaKind = mediaKind,
            protocolState = CallProtocolState.ROUTING_TOR_CIRCUIT,
            isIncoming = false,
            isCameraEnabled = (mediaKind == CallMediaKind.VIDEO),
            isSpeakerOn = (mediaKind == CallMediaKind.VIDEO)
        )

        _uiState.update {
            it.copy(
                activeCallState = initialCallState,
                currentScreen = if (mediaKind == CallMediaKind.VIDEO) {
                    ZeroTraceScreen.VIDEO_CALL
                } else {
                    ZeroTraceScreen.VOICE_CALL
                },
                errorBannerMessage = null,
                statusBannerMessage = null
            )
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val mediaPort = EncryptedCallMediaEngine.prepareCallerEphemeralListener(callId, ephemPriv)

            _uiState.update { state ->
                state.copy(
                    activeCallState = state.activeCallState?.copy(
                        protocolState = CallProtocolState.PERFORMING_PQXDH_CALL_AUTH
                    )
                )
            }

            if (isLoopback) {
                // Loopback test mode: derive local call key and run local audio/video capture stream
                val handle = _uiState.value.activeSessionHandle
                val sessionIdHex = handle?.let { RustCoreBridge.getSessionMetrics(it)?.sessionIdHex } ?: "LOOPBACK-CALL"
                val key32 = EncryptedCallMediaEngine.deriveCallMediaKey32(ephemPriv, ephemPub, sessionIdHex, callId)
                EncryptedCallMediaEngine.activateMediaSession(
                    context = appContext,
                    callId = callId,
                    isCaller = true,
                    derivedKey32 = key32,
                    isVideoCall = (mediaKind == CallMediaKind.VIDEO),
                    remoteHost = null,
                    remoteMediaPort = null,
                    onFallbackFrameSend = { frame ->
                        EncryptedCallMediaEngine.processIncomingEncryptedMediaFrame(frame)
                    }
                )
                Arrays.fill(key32, 0.toByte())
                _uiState.update { state ->
                    state.copy(
                        activeCallState = state.activeCallState?.copy(
                            protocolState = CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM
                        )
                    )
                }
                runCallTimerAndTelemetryLoop(callId)
                return@launch
            }

            val invited = TorOnionTransportManager.sendCallInvite(
                peerOnion = contact.onionAddress,
                callId = callId,
                mediaKind = mediaKind,
                mediaTcpPort = mediaPort,
                ephemeralCallPubKey = ephemPub
            )
            if (!invited) {
                cleanupCallResourcesAndReturn("Unable to deliver authenticated call invite over P2P circuit.")
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    activeCallState = state.activeCallState?.copy(
                        protocolState = CallProtocolState.RINGING_PEER
                    )
                )
            }
        }
    }

    private fun handleIncomingCallInviteEvent(event: PeerTransportEvent.IncomingCallInvite) {
        if (_uiState.value.activeCallState != null) {
            TorOnionTransportManager.sendCallReject(event.peerOnion, event.callId, "Busy in another encrypted call")
            return
        }
        val contact = _uiState.value.verifiedContacts.firstOrNull {
            it.onionAddress.equals(event.peerOnion, ignoreCase = true)
        }
        val incomingState = ActiveCallUiState(
            callId = event.callId,
            peerAlias = contact?.alias ?: "Peer-${event.peerOnion.take(6).uppercase()}",
            peerOnion = event.peerOnion,
            peerSafetyDigits = contact?.classicalSafetyDigits ?: "VERIFIED-ONION-KEY",
            mediaKind = event.mediaKind,
            protocolState = CallProtocolState.RINGING_PEER,
            isIncoming = true,
            isCameraEnabled = (event.mediaKind == CallMediaKind.VIDEO),
            isSpeakerOn = (event.mediaKind == CallMediaKind.VIDEO)
        )
        pendingIncomingRemoteHost = event.remoteHost ?: TorOnionTransportManager.getPeerRemoteHostAddress(event.peerOnion)
        pendingIncomingMediaPort = event.mediaTcpPort
        pendingIncomingRemoteEphemPub = event.ephemeralCallPubKey.copyOf()

        _uiState.update {
            it.copy(
                activeCallState = incomingState,
                currentScreen = ZeroTraceScreen.INCOMING_CALL,
                errorBannerMessage = null,
                statusBannerMessage = null
            )
        }
    }

    private fun handleCallAcceptedEvent(event: PeerTransportEvent.CallAccepted) {
        val current = _uiState.value.activeCallState ?: return
        if (current.callId != event.callId) return
        val localPriv = pendingCallPrivKey32 ?: return
        pendingCallPrivKey32 = null

        val sessionIdHex = event.callId

        _uiState.update { state ->
            state.copy(
                activeCallState = state.activeCallState?.copy(
                    protocolState = CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM
                )
            )
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val derivedKey32 = EncryptedCallMediaEngine.deriveCallMediaKey32(
                localPriv32 = localPriv,
                remotePub32 = event.ephemeralCallPubKey,
                sessionIdHex = sessionIdHex,
                callId = event.callId
            )
            Arrays.fill(localPriv, 0.toByte())

            EncryptedCallMediaEngine.activateMediaSession(
                context = appContext,
                callId = event.callId,
                isCaller = true,
                derivedKey32 = derivedKey32,
                isVideoCall = current.isCameraEnabled,
                remoteHost = null,
                remoteMediaPort = null,
                onFallbackFrameSend = { frame ->
                    TorOnionTransportManager.sendCallMediaFallbackFrame(event.peerOnion, event.callId, frame)
                }
            )
            Arrays.fill(derivedKey32, 0.toByte())
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            runCallTimerAndTelemetryLoop(event.callId)
        }
    }

    fun simulateIncomingEncryptedCall(contact: VerifiedContactRecord, mediaKind: CallMediaKind) {
        val (ephemPriv, ephemPub) = EncryptedCallMediaEngine.generateEphemeralX25519KeyPair()
        Arrays.fill(ephemPriv, 0.toByte())
        pendingIncomingRemoteHost = null
        pendingIncomingMediaPort = null
        pendingIncomingRemoteEphemPub = ephemPub

        val incomingState = ActiveCallUiState(
            callId = UUID.randomUUID().toString(),
            peerAlias = contact.alias,
            peerOnion = contact.onionAddress,
            peerSafetyDigits = contact.classicalSafetyDigits,
            mediaKind = mediaKind,
            protocolState = CallProtocolState.RINGING_PEER,
            isIncoming = true,
            isCameraEnabled = (mediaKind == CallMediaKind.VIDEO),
            isSpeakerOn = (mediaKind == CallMediaKind.VIDEO)
        )
        _uiState.update {
            it.copy(
                activeCallState = incomingState,
                currentScreen = ZeroTraceScreen.INCOMING_CALL
            )
        }
    }

    fun acceptIncomingEncryptedCall() {
        val call = _uiState.value.activeCallState ?: return
        val remoteEphemPub = pendingIncomingRemoteEphemPub
        val remoteHost = pendingIncomingRemoteHost
        val remotePort = pendingIncomingMediaPort

        val (ephemPriv, ephemPub) = EncryptedCallMediaEngine.generateEphemeralX25519KeyPair()
        val sessionIdHex = call.callId

        val connected = call.copy(protocolState = CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM)
        _uiState.update {
            it.copy(
                activeCallState = connected,
                currentScreen = if (call.mediaKind == CallMediaKind.VIDEO) {
                    ZeroTraceScreen.VIDEO_CALL
                } else {
                    ZeroTraceScreen.VOICE_CALL
                }
            )
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            TorOnionTransportManager.sendCallAccept(
                peerOnion = call.peerOnion,
                callId = call.callId,
                mediaKind = call.mediaKind,
                ephemeralCallPubKey = ephemPub
            )

            if (remoteEphemPub != null) {
                val key32 = EncryptedCallMediaEngine.deriveCallMediaKey32(
                    localPriv32 = ephemPriv,
                    remotePub32 = remoteEphemPub,
                    sessionIdHex = sessionIdHex,
                    callId = call.callId
                )
                EncryptedCallMediaEngine.activateMediaSession(
                    context = appContext,
                    callId = call.callId,
                    isCaller = false,
                    derivedKey32 = key32,
                    isVideoCall = (call.mediaKind == CallMediaKind.VIDEO),
                    remoteHost = remoteHost,
                    remoteMediaPort = remotePort,
                    onFallbackFrameSend = { frame ->
                        TorOnionTransportManager.sendCallMediaFallbackFrame(call.peerOnion, call.callId, frame)
                    }
                )
                Arrays.fill(key32, 0.toByte())
            }
            Arrays.fill(ephemPriv, 0.toByte())
            pendingIncomingRemoteEphemPub = null
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            runCallTimerAndTelemetryLoop(call.callId)
        }
    }

    private suspend fun runCallTimerAndTelemetryLoop(callId: String) {
        var ticks = 0
        while (_uiState.value.activeCallState?.callId == callId &&
            _uiState.value.activeCallState?.protocolState == CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM
        ) {
            delay(1_000L)
            ticks++
            if (ticks <= 3 || ticks % 5 == 0) {
                EncryptedCallMediaEngine.resyncControlState()
            }
            _uiState.update { state ->
                val current = state.activeCallState ?: return@update state
                if (current.callId != callId) return@update state
                state.copy(
                    activeCallState = current.copy(
                        elapsedSeconds = current.elapsedSeconds + 1L,
                        remoteAudioPacketsReceived = EncryptedCallMediaEngine.remoteAudioPackets.value,
                        remoteVideoFramesReceived = EncryptedCallMediaEngine.remoteVideoFrames.value,
                        isRemoteMuted = EncryptedCallMediaEngine.isRemoteMuted.value,
                        isRemoteCameraEnabled = EncryptedCallMediaEngine.isRemoteCameraEnabled.value
                    )
                )
            }
        }
    }

    fun submitLocalCallVideoFrame(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        EncryptedCallMediaEngine.submitLocalCameraFrame(imageProxy, isFrontCamera)
    }

    fun toggleCallMute() {
        _uiState.update { state ->
            val current = state.activeCallState ?: return@update state
            val nextMute = !current.isMuted
            EncryptedCallMediaEngine.setMuted(nextMute)
            state.copy(activeCallState = current.copy(isMuted = nextMute))
        }
    }

    fun toggleCallSpeaker() {
        _uiState.update { state ->
            val current = state.activeCallState ?: return@update state
            val nextSpeaker = !current.isSpeakerOn
            EncryptedCallMediaEngine.setSpeakerEnabled(nextSpeaker)
            state.copy(activeCallState = current.copy(isSpeakerOn = nextSpeaker))
        }
    }

    fun toggleCallCamera() {
        _uiState.update { state ->
            val current = state.activeCallState ?: return@update state
            val newCamState = !current.isCameraEnabled
            EncryptedCallMediaEngine.setCameraEnabled(newCamState)
            state.copy(
                activeCallState = current.copy(
                    isCameraEnabled = newCamState,
                    mediaKind = if (newCamState) CallMediaKind.VIDEO else current.mediaKind
                ),
                currentScreen = if (newCamState) ZeroTraceScreen.VIDEO_CALL else state.currentScreen
            )
        }
    }

    fun flipCallCamera() {
        _uiState.update { state ->
            val current = state.activeCallState ?: return@update state
            state.copy(activeCallState = current.copy(isFrontCamera = !current.isFrontCamera))
        }
    }

    fun endActiveCall() {
        val endedCall = _uiState.value.activeCallState
        if (endedCall != null) {
            if (endedCall.isIncoming && endedCall.protocolState == CallProtocolState.RINGING_PEER) {
                TorOnionTransportManager.sendCallReject(endedCall.peerOnion, endedCall.callId, "Declined")
            } else {
                TorOnionTransportManager.sendCallEnd(endedCall.peerOnion, endedCall.callId)
            }
        }
        cleanupCallResourcesAndReturn("Encrypted call terminated & ephemeral media key zeroized from RAM.")
    }

    private fun cleanupCallResourcesAndReturn(bannerText: String) {
        callTimerJob?.cancel()
        EncryptedCallMediaEngine.stopAndWipe()
        pendingCallPrivKey32?.let { Arrays.fill(it, 0.toByte()) }
        pendingCallPrivKey32 = null
        pendingIncomingRemoteEphemPub = null
        pendingIncomingRemoteHost = null
        pendingIncomingMediaPort = null

        val endedCall = _uiState.value.activeCallState
        val newHistoryEntry = if (endedCall != null) {
            VolatileCallHistoryEntry(
                callId = endedCall.callId,
                peerAlias = endedCall.peerAlias,
                peerOnion = endedCall.peerOnion,
                mediaKind = endedCall.mediaKind,
                isIncoming = endedCall.isIncoming,
                wasMissed = (endedCall.isIncoming && endedCall.protocolState != CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM),
                durationSeconds = endedCall.elapsedSeconds,
                timestampEpochMillis = System.currentTimeMillis()
            )
        } else null

        _uiState.update { state ->
            state.copy(
                activeCallState = null,
                callHistory = if (newHistoryEntry != null) listOf(newHistoryEntry) + state.callHistory else state.callHistory,
                currentScreen = if (state.activePeerContact != null) ZeroTraceScreen.CHAT else ZeroTraceScreen.HOME,
                statusBannerMessage = bannerText
            )
        }
    }

    fun clearVolatileCallHistory() {
        _uiState.update {
            it.copy(
                callHistory = emptyList(),
                statusBannerMessage = "Volatile call log cleared from RAM."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Emergency Lock, RAM Cleanup & System Lifecycle
    // -------------------------------------------------------------------------

    fun triggerEmergencyLockAndWipe() {
        callTimerJob?.cancel()
        VoiceNoteRecorder.cancelAndWipe()
        VoiceNotePlayer.stop()
        EncryptedCallMediaEngine.stopAndWipe()
        pendingCallPrivKey32?.let { Arrays.fill(it, 0.toByte()) }
        pendingCallPrivKey32 = null
        processedVolatileMsgKeys.clear()
        wipeAllRichMessagesInRam()
        MediaExportAndOpenHelper.shredAllEphemeralOpenCache(appContext)
        _uiState.value.ephemeralStatuses.forEach { it.wipeFromRam() }
        val wipedCount = TorOnionTransportManager.emergencyDisconnectAndWipe()
        SessionService.stopServiceAndWipe(appContext)
        SecureWindowManager.scrubClipboardNow(appContext)

        _uiState.update {
            it.copy(
                activeSessionHandle = null,
                activePeerContact = null,
                activeChatMessages = emptyList(),
                richMessagesByOnion = emptyMap(),
                lastMessagePreviewByOnion = emptyMap(),
                unreadCountByOnion = emptyMap(),
                ephemeralStatuses = emptyList(),
                activeViewingStatus = null,
                activeCallState = null,
                callHistory = emptyList(),
                activeRatchetMetrics = null,
                myPairingQrString = null,
                pendingScannedInvite = null,
                currentScreen = if (it.identitySummary != null) ZeroTraceScreen.HOME else ZeroTraceScreen.ONBOARDING,
                statusBannerMessage = isHi(
                    "आपातकालीन लॉक सक्रिय: $wipedCount सत्र समाप्त, लिसनर बंद, और क्लिपबोर्ड साफ़।",
                    "EMERGENCY LOCK EXECUTED: $wipedCount session(s) zeroized, listener closed, clipboard scrubbed."
                ),
                errorBannerMessage = null
            )
        }
    }

    fun wipeAllVolatileCachesAndMediaInRam() {
        wipeAllRichMessagesInRam()
        MediaExportAndOpenHelper.shredAllEphemeralOpenCache(appContext)
        _uiState.value.ephemeralStatuses.forEach { it.wipeFromRam() }
        SecureWindowManager.scrubClipboardNow(appContext)
        _uiState.update {
            it.copy(
                richMessagesByOnion = emptyMap(),
                lastMessagePreviewByOnion = emptyMap(),
                unreadCountByOnion = emptyMap(),
                ephemeralStatuses = emptyList(),
                callHistory = emptyList(),
                statusBannerMessage = "All volatile chat buffers, decrypted attachments, statuses & call logs wiped from RAM."
            )
        }
    }

    private fun wipeAllRichMessagesInRam() {
        _uiState.value.richMessagesByOnion.values.flatten().forEach { it.wipeFromRam() }
    }

    fun retryTorBootstrap() {
        _uiState.update { it.copy(errorBannerMessage = null, statusBannerMessage = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = TorOnionTransportManager.bootstrapTorAndStartOnionService()
            if (ok) {
                val firstContact = _uiState.value.verifiedContacts.firstOrNull()
                if (firstContact != null) {
                    establishWarmSessionInBackground(firstContact)
                }
            }
        }
    }

    fun setTransportBackend(backend: TorTransportBackend, customSocksPort: Int = _uiState.value.socksPort) {
        TorOnionTransportManager.configureTransportBackend(backend, customSocksPort)
        _uiState.update { it.copy(socksPort = customSocksPort, errorBannerMessage = null) }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = TorOnionTransportManager.bootstrapTorAndStartOnionService()
            if (ok) {
                val firstContact = _uiState.value.verifiedContacts.firstOrNull()
                if (firstContact != null) {
                    establishWarmSessionInBackground(firstContact)
                }
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        _uiState.update { it.copy(language = language) }
    }

    fun setSessionTimeoutSeconds(seconds: Long) {
        _uiState.update { it.copy(sessionTimeoutSeconds = seconds.coerceIn(60L, 1800L)) }
    }

    fun setAutoLockOnBackground(enabled: Boolean) {
        _uiState.update { it.copy(autoLockOnBackground = enabled) }
    }

    fun deleteLocalIdentityPermanently() {
        callTimerJob?.cancel()
        VoiceNoteRecorder.cancelAndWipe()
        VoiceNotePlayer.stop()
        EncryptedCallMediaEngine.stopAndWipe()
        pendingCallPrivKey32?.let { Arrays.fill(it, 0.toByte()) }
        pendingCallPrivKey32 = null
        processedVolatileMsgKeys.clear()
        wipeAllRichMessagesInRam()
        TorOnionTransportManager.emergencyDisconnectAndWipe()
        SessionService.stopServiceAndWipe(appContext)
        RustCoreBridge.wipeLoadedIdentityFromRam()
        keystoreManager.deleteIdentityAndWrappingKey()
        SecureWindowManager.scrubClipboardNow(appContext)

        _uiState.update {
            ZeroTraceUiState(
                currentScreen = ZeroTraceScreen.ONBOARDING,
                language = it.language,
                hardwareSecurityLevel = HardwareSecurityLevel.UNINITIALIZED,
                backupAuditStatus = BackupPolicy.auditBackupConfiguration(appContext),
                statusBannerMessage = isHi(
                    "स्थानीय पहचान और कीस्टोर कुंजी स्थायी रूप से हटा दी गई।",
                    "Local identity, contacts & Keystore wrapping key permanently destroyed."
                )
            )
        }
    }

    fun dismissBanners() {
        _uiState.update { it.copy(statusBannerMessage = null, errorBannerMessage = null) }
    }

    private fun saveEncryptedContactsToDisk(contacts: List<VerifiedContactRecord>) {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(0x5A544332) // "ZTC2"
            out.writeInt(contacts.size)
            for (c in contacts) {
                out.writeUTF(c.alias)
                out.writeUTF(c.onionAddress)
                out.writeUTF(c.classicalFingerprintHex)
                out.writeUTF(c.pqFingerprintHex)
                out.writeUTF(c.classicalSafetyDigits)
                out.writeUTF(c.pqSafetyDigits)
                out.writeInt(c.ed25519PublicKey.size)
                out.write(c.ed25519PublicKey)
                out.writeInt(c.x25519PublicKey.size)
                out.write(c.x25519PublicKey)
                out.writeUTF(c.pairingNonceHex)
                out.writeLong(c.verifiedAtEpochSec)
            }
        }
        val bytes = baos.toByteArray()
        try {
            keystoreManager.saveWrappedContacts(bytes)
        } finally {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    private fun loadEncryptedContactsFromDisk(): List<VerifiedContactRecord> {
        val raw = try {
            keystoreManager.loadWrappedContacts()
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        try {
            DataInputStream(ByteArrayInputStream(raw)).use { inp ->
                if (inp.readInt() != 0x5A544332) return emptyList()
                val count = inp.readInt().coerceIn(0, 500)
                val list = ArrayList<VerifiedContactRecord>(count)
                for (i in 0 until count) {
                    val alias = inp.readUTF()
                    val onion = inp.readUTF()
                    val cFp = inp.readUTF()
                    val pqFp = inp.readUTF()
                    val cDigits = inp.readUTF()
                    val pqDigits = inp.readUTF()
                    val edLen = inp.readInt()
                    val edPk = ByteArray(edLen).also { inp.readFully(it) }
                    val xLen = inp.readInt()
                    val xPk = ByteArray(xLen).also { inp.readFully(it) }
                    val nonceHex = inp.readUTF()
                    val verifiedAt = inp.readLong()
                    list.add(
                        VerifiedContactRecord(
                            alias = alias,
                            onionAddress = onion,
                            classicalFingerprintHex = cFp,
                            pqFingerprintHex = pqFp,
                            classicalSafetyDigits = cDigits,
                            pqSafetyDigits = pqDigits,
                            ed25519PublicKey = edPk,
                            x25519PublicKey = xPk,
                            mlDsa87PublicKey = null,
                            mlKem1024EncapsKey = null,
                            pairingNonceHex = nonceHex,
                            verifiedAtEpochSec = verifiedAt
                        )
                    )
                }
                return list
            }
        } catch (_: Exception) {
            return emptyList()
        } finally {
            Arrays.fill(raw, 0.toByte())
        }
    }

    private fun isHi(hi: String, en: String): String {
        return if (_uiState.value.language == AppLanguage.HINDI) hi else en
    }
}
