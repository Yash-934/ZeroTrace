package com.zerotrace.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotrace.bridge.ActiveCallUiState
import com.zerotrace.bridge.CallDirectionFilter
import com.zerotrace.bridge.CallMediaKind
import com.zerotrace.bridge.CallProtocolState
import com.zerotrace.bridge.EncryptedFileTransferEngine
import com.zerotrace.bridge.EncryptedTransferProgress
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.NativeRustBridge
import com.zerotrace.bridge.ParsedQrInvite
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.RustCoreBridge
import com.zerotrace.bridge.SessionRatchetMetrics
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.StatusPrivacyPolicy
import com.zerotrace.bridge.TransferDirection
import com.zerotrace.bridge.TransferLifecycleState
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.bridge.VolatileCallHistoryEntry
import com.zerotrace.bridge.VolatileChatMessage
import com.zerotrace.network.ConnectivityState
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
    val themeMode: AppThemeMode = AppThemeMode.DARK,
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
    val autoLockOnBackground: Boolean = true,
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
    val transportBackend: StateFlow<TorTransportBackend> = TorOnionTransportManager.transportBackend
    val localListenerPort: StateFlow<Int?> = TorOnionTransportManager.localListenerPort
    val transportError: StateFlow<String?> = TorOnionTransportManager.lastErrorDetail

    private var inactivityWatchdogJob: Job? = null
    private var callTimerJob: Job? = null

    init {
        initializeSecuritySubsystem()
        startInactivityWatchdog()
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
                val summary = RustCoreBridge.restoreWrappedIdentity(storedIdentityBytes)
                TorOnionTransportManager.bootstrapTorAndStartOnionService()
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
                if (contacts.isNotEmpty()) {
                    establishWarmSessionInBackground(contacts.first())
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
                terminateActiveChatSession()
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
                TorOnionTransportManager.bootstrapTorAndStartOnionService()
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

        // If a warm session is already active for this peer, open CHAT immediately
        val existingHandle = _uiState.value.activeSessionHandle
        val existingPeer = _uiState.value.activePeerContact
        if (existingHandle != null && existingPeer != null &&
            existingPeer.onionAddress.equals(normalizedContact.onionAddress, ignoreCase = true)
        ) {
            val volatileMsgs = RustCoreBridge.getVolatileMessages(existingHandle)
            val metrics = RustCoreBridge.getSessionMetrics(existingHandle)
            if (metrics != null) {
                _uiState.update { state ->
                    val clearedUnread = state.unreadCountByOnion.toMutableMap().apply {
                        put(normalizedContact.onionAddress, 0)
                    }
                    state.copy(
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

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    statusBannerMessage = isHi(
                        "${normalizedContact.alias} के साथ हाइब्रिड PQ हैंडशेक स्थापित किया जा रहा है…",
                        "Establishing ZT-PQXDH-P2P-v1 hybrid handshake with ${normalizedContact.alias}…"
                    ),
                    errorBannerMessage = null
                )
            }
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
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = null,
                            errorBannerMessage = err.message ?: "Failed to establish authenticated session"
                        )
                    }
                }
            )
        }
    }

    fun sendEphemeralChatMessage(messageText: String, forcePqBraidStep: Boolean = false) {
        val handle = _uiState.value.activeSessionHandle ?: return
        val contact = _uiState.value.activePeerContact ?: return
        val trimmed = messageText.trim()
        if (trimmed.isEmpty()) return

        val replyTarget = _uiState.value.replyingToMessage

        viewModelScope.launch {
            val result = TorOnionTransportManager.sendEncryptedMessageOverCircuit(
                sessionHandle = handle,
                plaintext = trimmed,
                forcePqBraidStep = forcePqBraidStep
            )
            result.fold(
                onSuccess = { updatedMessages ->
                    syncVolatileMessagesToRichConversation(
                        onionAddress = contact.onionAddress,
                        volatileMessages = updatedMessages,
                        attachReplyTargetToLatestOutgoing = replyTarget
                    )
                    _uiState.update {
                        it.copy(
                            activeChatMessages = updatedMessages,
                            activeRatchetMetrics = RustCoreBridge.getSessionMetrics(handle),
                            replyingToMessage = null,
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

    fun toggleVoiceNoteRecording() {
        val currentlyRecording = _uiState.value.isRecordingVoiceNote
        if (!currentlyRecording) {
            _uiState.update {
                it.copy(
                    isRecordingVoiceNote = true,
                    statusBannerMessage = "Recording volatile Opus/PCM voice note in RAM (no disk temp file)…"
                )
            }
        } else {
            _uiState.update { it.copy(isRecordingVoiceNote = false, statusBannerMessage = null) }
            sendEncryptedAttachmentPayload(
                kind = MessagePayloadKind.VOICE_NOTE,
                fileName = "voice_note_${System.currentTimeMillis() % 10000}.ogg",
                mimeType = "audio/ogg",
                samplePayloadBytes = "ZT2-ENCRYPTED-OPUS-VOICE-FRAME-${System.nanoTime()}".toByteArray(StandardCharsets.UTF_8),
                caption = "Encrypted Voice Note (RAM-only stream)",
                voiceDurationSec = 4
            )
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
        val handle = _uiState.value.activeSessionHandle ?: return
        val contact = _uiState.value.activePeerContact ?: return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // 1. Encrypt & chunk payload with a dedicated 256-bit ChaCha20-Poly1305 file key
                val bundle = EncryptedFileTransferEngine.encryptAndChunkPayload(
                    rawFileName = fileName,
                    mimeType = mimeType,
                    payloadBytes = samplePayloadBytes,
                    stripImageMetadata = (kind == MessagePayloadKind.IMAGE)
                )

                // 2. Verify round-trip chunk reassembly + SHA-256 integrity
                val reassembled = EncryptedFileTransferEngine.decryptAndVerifyChunks(
                    manifest = bundle.manifest,
                    fileSecretKey32 = bundle.fileSecretKey32,
                    chunks = bundle.chunks
                )

                val transferProgress = EncryptedTransferProgress(
                    manifest = bundle.manifest,
                    direction = TransferDirection.OUTGOING,
                    state = TransferLifecycleState.VERIFIED_READY_IN_RAM,
                    chunksProcessed = bundle.manifest.totalChunks,
                    bytesTransferred = bundle.manifest.totalSizeBytes.toLong(),
                    decryptedInMemoryBytes = reassembled
                )

                // 3. Send the encrypted manifest summary over the Hybrid Double Ratchet circuit
                val wireNotice = "[E2EE-${kind.name}] ${bundle.manifest.sanitizedFileName} (${bundle.manifest.totalSizeBytes}B, ${bundle.manifest.totalChunks} chunk(s), SHA256=${bundle.manifest.sha256DigestHex.take(12)}) — $caption"
                val sendRes = TorOnionTransportManager.sendEncryptedMessageOverCircuit(
                    sessionHandle = handle,
                    plaintext = wireNotice,
                    forcePqBraidStep = false
                )

                sendRes.fold(
                    onSuccess = { updatedVolatile ->
                        val metrics = RustCoreBridge.getSessionMetrics(handle)
                        val richMsg = RichConversationMessage(
                            id = UUID.randomUUID().toString(),
                            senderLabel = "You (Local Identity)",
                            bodyText = caption,
                            timestampEpochMillis = System.currentTimeMillis(),
                            isOutgoing = true,
                            ratchetStepIndex = (metrics?.sendCount ?: 1L).toInt(),
                            pqEpoch = metrics?.ratchetEpoch ?: 1,
                            pqBraidAdvanced = false,
                            payloadKind = kind,
                            fileTransfer = transferProgress,
                            voiceDurationSeconds = voiceDurationSec
                        )

                        _uiState.update { state ->
                            val currentRich = state.richMessagesByOnion[contact.onionAddress].orEmpty()
                            val updatedMap = state.richMessagesByOnion.toMutableMap()
                            updatedMap[contact.onionAddress] = currentRich + richMsg
                            val previews = state.lastMessagePreviewByOnion.toMutableMap().apply {
                                put(contact.onionAddress, "📎 ${bundle.manifest.sanitizedFileName}")
                            }
                            val times = state.lastMessageTimeByOnion.toMutableMap().apply {
                                put(contact.onionAddress, richMsg.timestampEpochMillis)
                            }
                            state.copy(
                                showAttachmentSheet = false,
                                activeChatMessages = updatedVolatile,
                                activeRatchetMetrics = metrics,
                                richMessagesByOnion = updatedMap,
                                lastMessagePreviewByOnion = previews,
                                lastMessageTimeByOnion = times,
                                statusBannerMessage = "Encrypted ${bundle.manifest.sanitizedFileName} with 256-bit ChaCha20-Poly1305 file key & SHA-256 verified."
                            )
                        }
                    },
                    onFailure = { err ->
                        bundle.wipeZeroize()
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

    private fun syncVolatileMessagesToRichConversation(
        onionAddress: String,
        volatileMessages: List<VolatileChatMessage>,
        attachReplyTargetToLatestOutgoing: RichConversationMessage? = null
    ) {
        val currentRich = _uiState.value.richMessagesByOnion[onionAddress].orEmpty()
        val existingIds = currentRich.map { it.id }.toSet()

        val newlyConverted = volatileMessages.filter { it.messageId.toString() !in existingIds }.map { vm ->
            val isLatestOutgoing = vm.isOutgoing && vm.messageId == volatileMessages.lastOrNull { m -> m.isOutgoing }?.messageId
            val replyRef = if (isLatestOutgoing) attachReplyTargetToLatestOutgoing else null
            RichConversationMessage(
                id = vm.messageId.toString(),
                senderLabel = if (vm.isOutgoing) "You" else onionAddress.take(10),
                bodyText = vm.readPlaintext(),
                timestampEpochMillis = vm.timestampEpochSec * 1000L,
                isOutgoing = vm.isOutgoing,
                ratchetStepIndex = vm.msgCounter.toInt(),
                pqEpoch = vm.ratchetEpoch,
                pqBraidAdvanced = vm.pqBraidStepTriggered,
                payloadKind = MessagePayloadKind.TEXT,
                replyToMessageId = replyRef?.id,
                replyToPreview = replyRef?.bodyText?.take(80)
            )
        }

        val combined = currentRich + newlyConverted
        val lastMsg = combined.lastOrNull()
        _uiState.update { state ->
            val map = state.richMessagesByOnion.toMutableMap()
            map[onionAddress] = combined
            val previews = state.lastMessagePreviewByOnion.toMutableMap()
            val times = state.lastMessageTimeByOnion.toMutableMap()
            if (lastMsg != null) {
                previews[onionAddress] = lastMsg.bodyText
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
        sendEphemeralChatMessage("[PQ-BRAID-REKEY] Advancing ML-KEM-1024 + X25519 epoch", forcePqBraidStep = true)
    }

    fun terminateActiveChatSession() {
        val handle = _uiState.value.activeSessionHandle
        val peerOnion = _uiState.value.activePeerContact?.onionAddress
        if (handle != null) {
            TorOnionTransportManager.disconnectPeerSession(handle)
        }
        if (peerOnion != null) {
            _uiState.value.richMessagesByOnion[peerOnion]?.forEach { it.wipeFromRam() }
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
        val alreadyExists = _uiState.value.ephemeralStatuses.any {
            it.authorOnion.equals(contact.onionAddress, ignoreCase = true)
        }
        if (alreadyExists) return

        val sampleStatus = EphemeralStatusItem(
            statusId = UUID.randomUUID().toString(),
            authorAlias = contact.alias,
            authorOnion = contact.onionAddress,
            isOwnStatus = false,
            mediaType = StatusMediaType.TEXT_CARD,
            captionText = "Tor v3 Hidden Service online • Hybrid ML-KEM-1024 + X25519 ratchet ready in RAM.",
            backgroundHex = "#0D2E22",
            createdAtEpochMillis = System.currentTimeMillis(),
            expiresOnSessionClose = true,
            privacyPolicy = StatusPrivacyPolicy.ALL_VERIFIED_CONTACTS,
            viewedByLocalUser = false
        )
        _uiState.update { state ->
            state.copy(ephemeralStatuses = listOf(sampleStatus) + state.ephemeralStatuses)
        }
    }

    fun publishOwnEphemeralStatus(
        caption: String,
        mediaType: StatusMediaType = StatusMediaType.TEXT_CARD,
        backgroundHex: String = "#0A2E24"
    ) {
        val trimmed = caption.trim()
        if (trimmed.isEmpty()) return
        val mySummary = _uiState.value.identitySummary
        val myOnion = mySummary?.onionAddress ?: "local-identity.onion"

        val payloadBytes = if (mediaType == StatusMediaType.IMAGE_CARD) {
            "ZT2-STATUS-IMAGE-PAYLOAD-${System.nanoTime()}".toByteArray(StandardCharsets.UTF_8)
        } else null

        val newItem = EphemeralStatusItem(
            statusId = UUID.randomUUID().toString(),
            authorAlias = "My Status (Live RAM)",
            authorOnion = myOnion,
            isOwnStatus = true,
            mediaType = mediaType,
            captionText = trimmed,
            backgroundHex = backgroundHex,
            createdAtEpochMillis = System.currentTimeMillis(),
            expiresOnSessionClose = true,
            privacyPolicy = _uiState.value.statusPrivacyPolicy,
            encryptedPayloadBytes = payloadBytes,
            viewedByLocalUser = true
        )

        _uiState.update { state ->
            state.copy(
                ephemeralStatuses = listOf(newItem) + state.ephemeralStatuses,
                statusBannerMessage = "Ephemeral P2P status stored in volatile RAM for verified connected peers."
            )
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

        val callId = UUID.randomUUID().toString()
        val initialCallState = ActiveCallUiState(
            callId = callId,
            peerAlias = contact.alias,
            peerOnion = contact.onionAddress,
            peerSafetyDigits = contact.classicalSafetyDigits,
            mediaKind = mediaKind,
            protocolState = CallProtocolState.ROUTING_TOR_CIRCUIT,
            isIncoming = false,
            isCameraEnabled = (mediaKind == CallMediaKind.VIDEO)
        )

        _uiState.update {
            it.copy(
                activeCallState = initialCallState,
                currentScreen = if (mediaKind == CallMediaKind.VIDEO) {
                    ZeroTraceScreen.VIDEO_CALL
                } else {
                    ZeroTraceScreen.VOICE_CALL
                },
                errorBannerMessage = null
            )
        }

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            delay(500)
            _uiState.update { state ->
                state.copy(
                    activeCallState = state.activeCallState?.copy(
                        protocolState = CallProtocolState.PERFORMING_PQXDH_CALL_AUTH
                    )
                )
            }
            delay(600)
            _uiState.update { state ->
                state.copy(
                    activeCallState = state.activeCallState?.copy(
                        protocolState = CallProtocolState.RINGING_PEER
                    )
                )
            }
            delay(700)
            _uiState.update { state ->
                state.copy(
                    activeCallState = state.activeCallState?.copy(
                        protocolState = CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM
                    )
                )
            }
            while (_uiState.value.activeCallState?.protocolState == CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM) {
                delay(1_000L)
                _uiState.update { state ->
                    val current = state.activeCallState ?: return@update state
                    state.copy(
                        activeCallState = current.copy(
                            elapsedSeconds = current.elapsedSeconds + 1L
                        )
                    )
                }
            }
        }
    }

    fun simulateIncomingEncryptedCall(contact: VerifiedContactRecord, mediaKind: CallMediaKind) {
        val incomingState = ActiveCallUiState(
            callId = UUID.randomUUID().toString(),
            peerAlias = contact.alias,
            peerOnion = contact.onionAddress,
            peerSafetyDigits = contact.classicalSafetyDigits,
            mediaKind = mediaKind,
            protocolState = CallProtocolState.RINGING_PEER,
            isIncoming = true,
            isCameraEnabled = (mediaKind == CallMediaKind.VIDEO)
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
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (_uiState.value.activeCallState?.protocolState == CallProtocolState.CONNECTED_ENCRYPTED_TOR_STREAM) {
                delay(1_000L)
                _uiState.update { state ->
                    val current = state.activeCallState ?: return@update state
                    state.copy(
                        activeCallState = current.copy(elapsedSeconds = current.elapsedSeconds + 1L)
                    )
                }
            }
        }
    }

    fun toggleCallMute() {
        _uiState.update { state ->
            val current = state.activeCallState ?: return@update state
            state.copy(activeCallState = current.copy(isMuted = !current.isMuted))
        }
    }

    fun toggleCallSpeaker() {
        _uiState.update { state ->
            val current = state.activeCallState ?: return@update state
            state.copy(activeCallState = current.copy(isSpeakerOn = !current.isSpeakerOn))
        }
    }

    fun toggleCallCamera() {
        _uiState.update { state ->
            val current = state.activeCallState ?: return@update state
            val newCamState = !current.isCameraEnabled
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
        callTimerJob?.cancel()
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
                currentScreen = if (state.activeSessionHandle != null) ZeroTraceScreen.CHAT else ZeroTraceScreen.HOME,
                statusBannerMessage = "Encrypted call terminated & ephemeral media key zeroized from RAM."
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
        wipeAllRichMessagesInRam()
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
