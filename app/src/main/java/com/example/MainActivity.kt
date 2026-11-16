package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerotrace.bridge.CallMediaKind
import com.zerotrace.media.ExternalPickerGuard
import com.zerotrace.security.BiometricAuthHelper
import com.zerotrace.security.SecureWindowManager
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.ZeroTraceScreen
import com.zerotrace.ui.ZeroTraceViewModel
import com.zerotrace.ui.calls.ZeroTraceCallsTabScreen
import com.zerotrace.ui.calls.ZeroTraceIncomingCallScreen
import com.zerotrace.ui.calls.ZeroTraceVideoCallScreen
import com.zerotrace.ui.calls.ZeroTraceVoiceCallScreen
import com.zerotrace.ui.chat.EncryptedMediaPreviewScreen
import com.zerotrace.ui.chat.EphemeralChatScreen
import com.zerotrace.ui.components.MainBottomTab
import com.zerotrace.ui.components.ZeroTraceBottomNavigation
import com.zerotrace.ui.contacts.ContactProfileScreen
import com.zerotrace.ui.contacts.ZeroTraceHomeScreen
import com.zerotrace.ui.dashboard.SecurityDashboardScreen
import com.zerotrace.ui.identity.IdentityCreationScreen
import com.zerotrace.ui.identity.MyIdentityScreen
import com.zerotrace.ui.onboarding.ZeroTraceOnboardingScreen
import com.zerotrace.ui.onboarding.ZeroTraceSplashScreen
import com.zerotrace.ui.pairing.AddContactScreen
import com.zerotrace.ui.pairing.ContactVerificationScreen
import com.zerotrace.ui.settings.ZeroTracePrivacyCenterScreen
import com.zerotrace.ui.settings.ZeroTraceSettingsScreen
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.ZeroTraceTheme
import com.zerotrace.ui.updates.ZeroTraceStatusViewerScreen
import com.zerotrace.ui.updates.ZeroTraceUpdatesTabScreen
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    private val viewModel: ZeroTraceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Default FLAG_SECURE is false so the AI Studio Streaming Emulator renders frames visibly;
        // users can toggle FLAG_SECURE on at any time from Settings or Privacy Center.
        SecureWindowManager.setSecureFlag(window, viewModel.uiState.value.flagSecureEnabled)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(uiState.flagSecureEnabled) {
                SecureWindowManager.setSecureFlag(window, uiState.flagSecureEnabled)
            }

            val launchUnlockPrompt = {
                BiometricAuthHelper.authenticate(
                    activity = this@MainActivity,
                    title = "Unlock ZeroTrace",
                    subtitle = "Verify fingerprint or device lock",
                    description = "Authenticate to access your encrypted messages and Tor identity.",
                    onSuccess = { viewModel.onBiometricUnlockSucceeded() },
                    onError = { err -> viewModel.onBiometricUnlockError(err) }
                )
            }

            val handleToggleBiometric: (Boolean) -> Unit = { enable ->
                if (!enable) {
                    viewModel.setBiometricAppLockEnabled(false)
                } else {
                    BiometricAuthHelper.authenticate(
                        activity = this@MainActivity,
                        title = "Enable Fingerprint Lock",
                        subtitle = "Confirm your fingerprint to arm app lock",
                        description = "ZeroTrace will require biometric verification on launch and resume.",
                        onSuccess = {
                            viewModel.setBiometricAppLockEnabled(true)
                            Toast.makeText(
                                this@MainActivity,
                                "Biometric fingerprint lock enabled.",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onError = { err ->
                            viewModel.onBiometricUnlockError(err)
                            Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            var isActivityResumed by remember { mutableStateOf(false) }
            DisposableEffect(lifecycleOwner) {
                isActivityResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> isActivityResumed = true
                        Lifecycle.Event.ON_PAUSE -> isActivityResumed = false
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(uiState.isAppBiometricLocked, uiState.identitySummary != null, isActivityResumed) {
                if (uiState.isAppBiometricLocked && uiState.identitySummary != null && isActivityResumed) {
                    delay(180L)
                    if (isActivityResumed) {
                        launchUnlockPrompt()
                    }
                }
            }

            ZeroTraceTheme(
                themeMode = uiState.themeMode,
                accentPalette = uiState.accentPalette
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    if (uiState.isAppBiometricLocked && uiState.identitySummary != null) {
                        BiometricLockGateScreen(
                            isHindi = uiState.language == AppLanguage.HINDI,
                            errorMessage = uiState.biometricPromptMessage,
                            onUnlockClick = launchUnlockPrompt
                        )
                    } else {
                        ZeroTraceRootApp(
                            viewModel = viewModel,
                            isFlagSecureActive = uiState.flagSecureEnabled,
                            onRequestToggleBiometric = handleToggleBiometric
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ExternalPickerGuard.onHostActivityResumed()
    }

    override fun onStop() {
        super.onStop()
        if (ExternalPickerGuard.isExternalPickerInFlight()) {
            return
        }
        viewModel.lockAppIfBiometricEnabled()
        val state = viewModel.uiState.value
        if (state.autoLockOnBackground && !state.foreverVaultEnabled && state.activeSessionHandle != null) {
            viewModel.terminateActiveChatSession()
        }
    }
}

@Composable
private fun BiometricLockGateScreen(
    isHindi: Boolean,
    errorMessage: String?,
    onUnlockClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("biometric_lock_gate_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Biometric Lock",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = if (isHindi) "ZeroTrace लॉक है" else "ZeroTrace Locked",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isHindi) {
                    "अपनी पोस्ट-क्वांटम एन्क्रिप्टेड चैट और कीस्टोर वॉल्ट खोलने के लिए फ़िंगरप्रिंट सत्यापित करें।"
                } else {
                    "Verify your fingerprint or device screen lock to open your encrypted Keystore vault."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = CrimsonDanger.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonDanger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(26.dp))
            Button(
                onClick = onUnlockClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("biometric_unlock_button")
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHindi) "फ़िंगरप्रिंट से अनलॉक करें" else "Unlock with Fingerprint",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ZeroTraceRootApp(
    viewModel: ZeroTraceViewModel,
    isFlagSecureActive: Boolean,
    onRequestToggleBiometric: (Boolean) -> Unit = viewModel::setBiometricAppLockEnabled
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connectivityState by viewModel.connectivityState.collectAsStateWithLifecycle()
    val torProgress by viewModel.torBootstrapProgress.collectAsStateWithLifecycle()
    val torBootstrapSummary by viewModel.torBootstrapSummary.collectAsStateWithLifecycle()
    val transportBackend by viewModel.transportBackend.collectAsStateWithLifecycle()
    val localListenerPort by viewModel.localListenerPort.collectAsStateWithLifecycle()
    val transportError by viewModel.transportError.collectAsStateWithLifecycle()
    val connectedPeerOnions by viewModel.connectedPeerOnions.collectAsStateWithLifecycle()
    val voiceRecordingSeconds by viewModel.voiceRecordingSeconds.collectAsStateWithLifecycle()
    val voiceRecordingAmplitude by viewModel.voiceRecordingAmplitude.collectAsStateWithLifecycle()
    val playingVoiceMessageId by viewModel.playingVoiceMessageId.collectAsStateWithLifecycle()
    val remoteCallVideoFrame by viewModel.remoteCallVideoFrame.collectAsStateWithLifecycle()

    val effectiveErrorBanner = uiState.errorBannerMessage ?: transportError
    val isHindi = uiState.language == AppLanguage.HINDI
    val activeCallAvatarBytes = uiState.activeCallState?.peerOnion?.let { peerOnion ->
        uiState.verifiedContacts.firstOrNull {
            it.onionAddress.equals(peerOnion, ignoreCase = true)
        }?.avatarBytes
    }

    when (uiState.currentScreen) {
        ZeroTraceScreen.SPLASH -> {
            ZeroTraceSplashScreen(language = uiState.language)
        }

        ZeroTraceScreen.ONBOARDING -> {
            ZeroTraceOnboardingScreen(
                language = uiState.language,
                limitationsAccepted = uiState.limitationsAccepted,
                onSelectLanguage = viewModel::setLanguage,
                onToggleAcceptance = viewModel::acceptOnboardingLimitations,
                onProceed = viewModel::proceedFromOnboardingToIdentityCreation
            )
        }

        ZeroTraceScreen.IDENTITY_CREATION -> {
            IdentityCreationScreen(
                language = uiState.language,
                isGenerating = uiState.isGeneratingIdentity,
                identitySummary = uiState.identitySummary,
                hardwareSecurityLevel = uiState.hardwareSecurityLevel,
                connectivityState = connectivityState,
                onGenerateIdentity = viewModel::generateAndStoreProtectedIdentity,
                onContinueToHome = viewModel::finishIdentityCreationToHome
            )
        }

        ZeroTraceScreen.HOME, ZeroTraceScreen.SETTINGS -> {
            val activeTab = if (uiState.currentScreen == ZeroTraceScreen.SETTINGS) {
                MainBottomTab.SETTINGS
            } else {
                uiState.selectedBottomTab
            }

            BackHandler(enabled = activeTab != MainBottomTab.CHATS) {
                viewModel.selectBottomTab(MainBottomTab.CHATS)
            }

            val totalUnreadChats = uiState.unreadCountByOnion.values.sum()
            val unviewedUpdates = uiState.ephemeralStatuses.count { !it.isOwnStatus && !it.viewedByLocalUser }
            val missedCalls = uiState.callHistory.count { it.wasMissed }
            val totalRichMsgsInRam = uiState.richMessagesByOnion.values.sumOf { it.size }

            Scaffold(
                bottomBar = {
                    ZeroTraceBottomNavigation(
                        selectedTab = activeTab,
                        unreadChatsCount = totalUnreadChats,
                        activeUpdatesCount = unviewedUpdates,
                        missedCallsCount = missedCalls,
                        isHindi = isHindi,
                        onTabSelected = { tab -> viewModel.selectBottomTab(tab) }
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (activeTab) {
                        MainBottomTab.CHATS -> {
                            ZeroTraceHomeScreen(
                                language = uiState.language,
                                identitySummary = uiState.identitySummary,
                                connectivityState = connectivityState,
                                torProgress = torProgress,
                                torBootstrapSummary = torBootstrapSummary,
                                transportBackend = transportBackend,
                                verifiedContacts = uiState.verifiedContacts,
                                pinnedOnions = uiState.pinnedOnions,
                                mutedOnions = uiState.mutedOnions,
                                archivedOnions = uiState.archivedOnions,
                                unreadCountByOnion = uiState.unreadCountByOnion,
                                lastMessagePreviewByOnion = uiState.lastMessagePreviewByOnion,
                                lastMessageTimeByOnion = uiState.lastMessageTimeByOnion,
                                searchQuery = uiState.chatSearchQuery,
                                selectedFilter = uiState.conversationFilter,
                                showGroupChatMlsDialog = uiState.showGroupChatMlsDialog,
                                activePeerContact = uiState.activePeerContact,
                                activeRatchetMetrics = uiState.activeRatchetMetrics,
                                statusBanner = uiState.statusBannerMessage,
                                errorBanner = effectiveErrorBanner,
                                onSearchQueryChange = viewModel::updateChatSearchQuery,
                                onFilterSelected = viewModel::setConversationFilter,
                                onShowGroupChatMlsDialog = viewModel::setShowGroupChatMlsDialog,
                                onNavigate = viewModel::navigateTo,
                                onStartChatWithContact = viewModel::startAuthenticatedSessionWithContact,
                                onOpenContactProfile = viewModel::openContactProfile,
                                onTogglePin = viewModel::togglePinConversation,
                                onToggleMute = viewModel::toggleMuteConversation,
                                onToggleArchive = viewModel::toggleArchiveConversation,
                                onClearContactChatRam = viewModel::clearChatRamForContact,
                                onScanLoopbackPeerNow = viewModel::scanLoopbackVerificationPeerQr,
                                onResumeActiveChat = { viewModel.navigateTo(ZeroTraceScreen.CHAT) },
                                onEmergencyLock = viewModel::triggerEmergencyLockAndWipe,
                                onRetryTor = viewModel::retryTorBootstrap,
                                onDismissBanner = viewModel::dismissBanners
                            )
                        }

                        MainBottomTab.UPDATES -> {
                            ZeroTraceUpdatesTabScreen(
                                language = uiState.language,
                                identitySummary = uiState.identitySummary,
                                localAvatarBytes = uiState.localProfile.avatarJpegBytes,
                                verifiedContacts = uiState.verifiedContacts,
                                connectivityState = connectivityState,
                                torProgress = torProgress,
                                statuses = uiState.ephemeralStatuses,
                                privacyPolicy = uiState.statusPrivacyPolicy,
                                onPrivacyPolicyChange = viewModel::setStatusPrivacyPolicy,
                                onPublishStatus = { caption, mediaType, mediaUri ->
                                    viewModel.publishOwnEphemeralStatus(
                                        caption = caption,
                                        mediaType = mediaType,
                                        mediaUri = mediaUri
                                    )
                                },
                                onOpenStatusViewer = viewModel::openStatusViewer,
                                onDeleteStatus = viewModel::deleteEphemeralStatus,
                                onEmergencyWipe = viewModel::triggerEmergencyLockAndWipe
                            )
                        }

                        MainBottomTab.CALLS -> {
                            ZeroTraceCallsTabScreen(
                                language = uiState.language,
                                connectivityState = connectivityState,
                                torProgress = torProgress,
                                verifiedContacts = uiState.verifiedContacts,
                                callHistory = uiState.callHistory,
                                selectedFilter = uiState.callFilter,
                                onFilterChange = viewModel::setCallFilter,
                                onStartAudioCall = { contact ->
                                    viewModel.startEncryptedCall(contact, CallMediaKind.AUDIO)
                                },
                                onStartVideoCall = { contact ->
                                    viewModel.startEncryptedCall(contact, CallMediaKind.VIDEO)
                                },
                                onSimulateIncomingCall = { contact, kind ->
                                    viewModel.simulateIncomingEncryptedCall(contact, kind)
                                },
                                onClearCallHistory = viewModel::clearVolatileCallHistory,
                                onOpenAddContact = { viewModel.navigateTo(ZeroTraceScreen.ADD_CONTACT) },
                                onEmergencyWipe = viewModel::triggerEmergencyLockAndWipe
                            )
                        }

                        MainBottomTab.SETTINGS -> {
                            ZeroTraceSettingsScreen(
                                language = uiState.language,
                                identitySummary = uiState.identitySummary,
                                localProfile = uiState.localProfile,
                                connectivityState = connectivityState,
                                torProgress = torProgress,
                                themeMode = uiState.themeMode,
                                accentPalette = uiState.accentPalette,
                                wallpaperStyle = uiState.wallpaperStyle,
                                foreverVaultEnabled = uiState.foreverVaultEnabled,
                                sessionTimeoutSeconds = uiState.sessionTimeoutSeconds,
                                autoLockOnBackground = uiState.autoLockOnBackground,
                                biometricAppLockEnabled = uiState.biometricAppLockEnabled,
                                flagSecureEnabled = uiState.flagSecureEnabled,
                                transportBackend = transportBackend,
                                volatileMessagesInRamCount = totalRichMsgsInRam,
                                volatileStatusesInRamCount = uiState.ephemeralStatuses.size,
                                volatileCallsInRamCount = uiState.callHistory.size,
                                statusBanner = uiState.statusBannerMessage,
                                errorBanner = effectiveErrorBanner,
                                onDismissBanner = viewModel::dismissBanners,
                                onUpdateLocalProfile = viewModel::updateLocalUserProfile,
                                onSelectThemeMode = viewModel::setThemeMode,
                                onSelectAccentPalette = viewModel::setAccentPalette,
                                onSelectWallpaperStyle = viewModel::setWallpaperStyle,
                                onSelectLanguage = viewModel::setLanguage,
                                onToggleForeverVault = viewModel::setForeverVaultEnabled,
                                onSelectTimeoutSeconds = viewModel::setSessionTimeoutSeconds,
                                onToggleAutoLock = viewModel::setAutoLockOnBackground,
                                onRequestToggleBiometric = onRequestToggleBiometric,
                                onToggleFlagSecure = viewModel::setFlagSecureEnabled,
                                onSelectTransportBackend = { viewModel.setTransportBackend(it) },
                                onWipeVolatileCaches = viewModel::wipeAllVolatileCachesAndMediaInRam,
                                onDisconnectAllSessions = viewModel::triggerEmergencyLockAndWipe,
                                onDeleteLocalIdentity = viewModel::deleteLocalIdentityPermanently,
                                onNavigate = viewModel::navigateTo,
                                onBack = { viewModel.selectBottomTab(MainBottomTab.CHATS) }
                            )
                        }
                    }
                }
            }
        }

        ZeroTraceScreen.MY_IDENTITY -> {
            MyIdentityScreen(
                language = uiState.language,
                summary = uiState.identitySummary,
                hardwareSecurityLevel = uiState.hardwareSecurityLevel,
                connectivityState = connectivityState,
                pairingQrString = uiState.myPairingQrString,
                pairingQrExpirySec = uiState.myPairingQrExpirySec,
                onRefreshQr = viewModel::refreshMyPairingQr,
                onBack = { viewModel.navigateBack() }
            )
        }

        ZeroTraceScreen.ADD_CONTACT -> {
            AddContactScreen(
                language = uiState.language,
                myPairingQrString = uiState.myPairingQrString,
                myPairingQrExpirySec = uiState.myPairingQrExpirySec,
                errorBanner = effectiveErrorBanner,
                onQrScanned = { viewModel.onQrInviteScannedOrEntered(it) },
                onScanLoopbackPeerQr = viewModel::scanLoopbackVerificationPeerQr,
                onRefreshMyQr = viewModel::refreshMyPairingQr,
                onCancelPairing = { viewModel.navigateBack() }
            )
        }

        ZeroTraceScreen.CONTACT_VERIFICATION -> {
            ContactVerificationScreen(
                language = uiState.language,
                localIdentity = uiState.identitySummary,
                scannedInvite = uiState.pendingScannedInvite,
                contactAlias = uiState.pendingContactAlias,
                identityReplacementAlert = uiState.identityReplacementAlert,
                onAliasChanged = viewModel::updatePendingContactAlias,
                onConfirmVerifiedContact = viewModel::confirmAndSaveVerifiedContact,
                onCancel = { viewModel.navigateBack() }
            )
        }

        ZeroTraceScreen.CHAT -> {
            val activePeer = uiState.activePeerContact
            val richMsgs = if (activePeer != null) {
                uiState.richMessagesByOnion[activePeer.onionAddress].orEmpty()
            } else {
                emptyList()
            }
            val isPeerConnected = activePeer?.let {
                it.onionAddress.lowercase() in connectedPeerOnions ||
                    com.zerotrace.network.TorOnionTransportManager.isLoopbackVerificationContact(it)
            } == true

            EphemeralChatScreen(
                language = uiState.language,
                peerContact = activePeer,
                connectivityState = connectivityState,
                isPeerMeshConnected = isPeerConnected,
                richMessages = richMsgs,
                ratchetMetrics = uiState.activeRatchetMetrics,
                replyingToMessage = uiState.replyingToMessage,
                inspectedMessageInfo = uiState.inspectedMessageInfo,
                showAttachmentSheet = uiState.showAttachmentSheet,
                isRecordingVoiceNote = uiState.isRecordingVoiceNote,
                voiceRecordingSeconds = voiceRecordingSeconds,
                voiceRecordingAmplitude = voiceRecordingAmplitude,
                playingVoiceMessageId = playingVoiceMessageId,
                inChatSearchQuery = uiState.inChatSearchQuery,
                wallpaperStyle = uiState.wallpaperStyle,
                statusBanner = uiState.statusBannerMessage,
                errorBanner = effectiveErrorBanner,
                onInChatSearchChange = viewModel::updateInChatSearchQuery,
                onSendMessage = { viewModel.sendEphemeralChatMessage(it) },
                onReplySelected = viewModel::setReplyingToMessage,
                onReactMessage = viewModel::reactToMessage,
                onDeleteMessageFromRam = viewModel::deleteSingleMessageFromRam,
                onDeleteMessageForEveryone = viewModel::deleteMessageForEveryone,
                onInspectMessageInfo = viewModel::setInspectedMessageInfo,
                onOpenMediaPreview = viewModel::openMediaPreview,
                onSaveMediaToGallery = viewModel::saveMessageMediaToGallery,
                onOpenMediaExternally = viewModel::openMessageMediaExternally,
                onShowAttachmentSheet = viewModel::setShowAttachmentSheet,
                onToggleVoiceRecording = viewModel::toggleVoiceNoteRecording,
                onCancelVoiceRecording = viewModel::cancelVoiceNoteRecording,
                onToggleVoicePlay = viewModel::toggleVoiceNotePlayback,
                onPickMediaUri = viewModel::sendMediaFromUri,
                onCapturedCameraBitmap = viewModel::sendCapturedCameraBitmap,
                onSendEncryptedAttachment = { kind, name, mime, bytes, caption ->
                    viewModel.sendEncryptedAttachmentPayload(kind, name, mime, bytes, caption)
                },
                onStartVoiceCall = {
                    if (activePeer != null) viewModel.startEncryptedCall(activePeer, CallMediaKind.AUDIO)
                },
                onStartVideoCall = {
                    if (activePeer != null) viewModel.startEncryptedCall(activePeer, CallMediaKind.VIDEO)
                },
                onOpenContactProfile = {
                    if (activePeer != null) viewModel.openContactProfile(activePeer)
                },
                onTriggerPqBraidStep = viewModel::triggerManualPqBraidRatchetStep,
                onReconnectPeer = {
                    activePeer?.let(viewModel::startAuthenticatedSessionWithContact)
                },
                onBackToChats = viewModel::navigateBackToChatsPreservingSession,
                onDisconnectAndWipeSession = viewModel::terminateActiveChatSession
            )
        }

        ZeroTraceScreen.CONTACT_PROFILE -> {
            val profileContact = uiState.inspectedProfileContact ?: uiState.activePeerContact
            if (profileContact != null) {
                ContactProfileScreen(
                    contact = profileContact,
                    connectivityState = connectivityState,
                    torProgress = torProgress,
                    sharedMessagesInRam = uiState.richMessagesByOnion[profileContact.onionAddress].orEmpty(),
                    disappearingTimerSec = uiState.disappearingMessageTimerSec,
                    isBlocked = profileContact.onionAddress in uiState.blockedOnions,
                    onBack = { viewModel.navigateBack() },
                    onStartChat = { viewModel.startAuthenticatedSessionWithContact(profileContact) },
                    onStartVoiceCall = { viewModel.startEncryptedCall(profileContact, CallMediaKind.AUDIO) },
                    onStartVideoCall = { viewModel.startEncryptedCall(profileContact, CallMediaKind.VIDEO) },
                    onReverifyQr = { viewModel.navigateTo(ZeroTraceScreen.ADD_CONTACT) },
                    onToggleBlock = { viewModel.toggleBlockPeer(profileContact.onionAddress) },
                    onClearChatHistoryRam = { viewModel.clearChatRamForContact(profileContact.onionAddress) },
                    onDeleteContact = { viewModel.deleteVerifiedContact(profileContact) }
                )
            } else {
                viewModel.navigateBack()
            }
        }

        ZeroTraceScreen.STATUS_VIEWER -> {
            ZeroTraceStatusViewerScreen(
                statuses = uiState.activeViewingStatuses,
                currentIndex = uiState.activeViewingStatusIndex,
                fallbackStatus = uiState.activeViewingStatus,
                localAvatarBytes = uiState.localProfile.avatarJpegBytes,
                verifiedContacts = uiState.verifiedContacts,
                onStepStatus = viewModel::stepViewingStatus,
                onBack = { viewModel.navigateBack() },
                onReplyToStatus = { statusItem, replyText ->
                    viewModel.sendStatusReplyToPeer(statusItem, replyText)
                },
                onSaveStatusToGallery = viewModel::saveStatusMediaToGallery,
                onDeleteOwnStatus = viewModel::deleteEphemeralStatus
            )
        }

        ZeroTraceScreen.VOICE_CALL -> {
            ZeroTraceVoiceCallScreen(
                callState = uiState.activeCallState,
                peerAvatarBytes = activeCallAvatarBytes,
                onToggleMute = viewModel::toggleCallMute,
                onToggleSpeaker = viewModel::toggleCallSpeaker,
                onUpgradeToVideo = viewModel::toggleCallCamera,
                onEndCall = viewModel::endActiveCall
            )
        }

        ZeroTraceScreen.VIDEO_CALL -> {
            ZeroTraceVideoCallScreen(
                callState = uiState.activeCallState,
                peerAvatarBytes = activeCallAvatarBytes,
                remoteVideoFrame = remoteCallVideoFrame,
                onLocalCameraFrame = viewModel::submitLocalCallVideoFrame,
                onToggleMute = viewModel::toggleCallMute,
                onToggleCamera = viewModel::toggleCallCamera,
                onFlipCamera = viewModel::flipCallCamera,
                onEndCall = viewModel::endActiveCall
            )
        }

        ZeroTraceScreen.INCOMING_CALL -> {
            ZeroTraceIncomingCallScreen(
                callState = uiState.activeCallState,
                peerAvatarBytes = activeCallAvatarBytes,
                onAcceptCall = viewModel::acceptIncomingEncryptedCall,
                onDeclineCall = viewModel::endActiveCall,
                onQuickMessageReply = {
                    val peerOnion = uiState.activeCallState?.peerOnion
                    viewModel.endActiveCall()
                    val contact = uiState.verifiedContacts.find {
                        it.onionAddress.equals(peerOnion, ignoreCase = true)
                    }
                    if (contact != null) {
                        viewModel.startAuthenticatedSessionWithContact(contact)
                    }
                }
            )
        }

        ZeroTraceScreen.MEDIA_PREVIEW -> {
            EncryptedMediaPreviewScreen(
                message = uiState.mediaPreviewMessage,
                connectivityState = connectivityState,
                torProgress = torProgress,
                playingVoiceMessageId = playingVoiceMessageId,
                onToggleVoicePlay = viewModel::toggleVoiceNotePlayback,
                onSaveToGallery = viewModel::saveMessageMediaToGallery,
                onOpenExternally = viewModel::openMessageMediaExternally,
                onBack = { viewModel.navigateBack() },
                onWipeFromRamAndClose = { msg ->
                    viewModel.deleteSingleMessageFromRam(msg)
                    viewModel.navigateBack()
                }
            )
        }

        ZeroTraceScreen.PRIVACY_CENTER -> {
            ZeroTracePrivacyCenterScreen(
                connectivityState = connectivityState,
                torProgress = torProgress,
                hardwareSecurityLevel = uiState.hardwareSecurityLevel,
                flagSecureEnabled = uiState.flagSecureEnabled,
                autoLockOnBackground = uiState.autoLockOnBackground,
                biometricAppLockEnabled = uiState.biometricAppLockEnabled,
                disappearingTimerSec = uiState.disappearingMessageTimerSec,
                readReceiptsEnabled = uiState.readReceiptsEnabled,
                typingIndicatorsEnabled = uiState.typingIndicatorsEnabled,
                metadataMinimizedMode = uiState.metadataMinimizedMode,
                onToggleFlagSecure = viewModel::setFlagSecureEnabled,
                onToggleAutoLock = viewModel::setAutoLockOnBackground,
                onToggleBiometricAppLock = onRequestToggleBiometric,
                onSelectDisappearingTimer = viewModel::setDisappearingMessageTimer,
                onToggleReadReceipts = viewModel::setReadReceiptsEnabled,
                onToggleTypingIndicators = viewModel::setTypingIndicatorsEnabled,
                onToggleMetadataMinimized = viewModel::setMetadataMinimizedMode,
                onBack = { viewModel.navigateBack() }
            )
        }

        ZeroTraceScreen.SECURITY_DASHBOARD -> {
            SecurityDashboardScreen(
                language = uiState.language,
                identitySummary = uiState.identitySummary,
                hardwareSecurityLevel = uiState.hardwareSecurityLevel,
                isNativeRustLoaded = uiState.isNativeRustLibraryLoaded,
                nativeRustStatusNote = uiState.nativeRustStatusNote,
                connectivityState = connectivityState,
                transportBackend = transportBackend,
                localListenerPort = localListenerPort,
                ratchetMetrics = uiState.activeRatchetMetrics,
                backupAudit = uiState.backupAuditStatus,
                integrityReport = uiState.deviceIntegrityReport,
                isFlagSecureActive = isFlagSecureActive,
                onBack = { viewModel.navigateBack() }
            )
        }
    }
}

