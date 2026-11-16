package com.zerotrace.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.network.ConnectivityState
import com.zerotrace.network.TorTransportBackend
import com.zerotrace.security.HardwareSecurityLevel
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.ZeroTraceScreen
import com.zerotrace.ui.theme.AccentPalette
import com.zerotrace.ui.theme.AppThemeMode
import com.zerotrace.ui.theme.ChatWallpaperStyle
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.MonospaceStyle
import com.zerotrace.ui.theme.ZeroTracePalette

/**
 * Settings Screen matching Mockup Screen 8 (flat WhatsApp-style icon + title + subtitle rows)
 */
@Composable
fun ZeroTraceSettingsScreen(
    language: AppLanguage,
    identitySummary: PublicIdentitySummary?,
    connectivityState: ConnectivityState,
    torProgress: Int,
    themeMode: AppThemeMode,
    accentPalette: AccentPalette,
    wallpaperStyle: ChatWallpaperStyle,
    sessionTimeoutSeconds: Long,
    autoLockOnBackground: Boolean,
    flagSecureEnabled: Boolean,
    transportBackend: TorTransportBackend,
    volatileMessagesInRamCount: Int,
    volatileStatusesInRamCount: Int,
    volatileCallsInRamCount: Int,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectAccentPalette: (AccentPalette) -> Unit,
    onSelectWallpaperStyle: (ChatWallpaperStyle) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTimeoutSeconds: (Long) -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onToggleFlagSecure: (Boolean) -> Unit,
    onSelectTransportBackend: (TorTransportBackend) -> Unit,
    onWipeVolatileCaches: () -> Unit,
    onDisconnectAllSessions: () -> Unit,
    onDeleteLocalIdentity: () -> Unit,
    onNavigate: (ZeroTraceScreen) -> Unit,
    onBack: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurface
    )

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    if (isHi) "स्थानीय पहचान स्थायी रूप से हटाएं?" else "Destroy Local Identity & Keystore Key?",
                    color = CrimsonDanger
                )
            },
            text = {
                Text(
                    if (isHi) {
                        "यह ऑपरेशन आपके डिवाइस की क्लासिकल + पोस्ट-क्वांटम कुंजियों, सभी सत्यापित संपर्कों और Android कीस्टोर AES-256-GCM रैपिंग कुंजी को स्थायी रूप से मिटा देगा।"
                    } else {
                        "This permanently overwrites and deletes your wrapped identity file, all verified contacts, and the Android Keystore AES-256-GCM master wrapping key."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteLocalIdentity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                    modifier = Modifier.testTag("confirm_delete_identity_dialog_button")
                ) {
                    Text(if (isHi) "स्थायी रूप से नष्ट करें" else "Destroy Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(if (isHi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (ZeroTracePalette.isLight()) 0.dp else 3.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = if (isHi) "सेटिंग्स" else "Settings",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "TOR v3 • ${connectivityState.code}" +
                                    if (torProgress in 1..99) " ($torProgress%)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .testTag("settings_screen")
        ) {
            // Top Flat WhatsApp-style Rows matching Mockup Screen 8
            SettingsNavRow(
                icon = Icons.Default.Info,
                title = if (isHi) "गोपनीयता और सुरक्षा" else "Privacy & Security",
                subtitle = if (isHi) {
                    "एंड-टू-एंड एन्क्रिप्शन, TOR रूटिंग, गायब होने वाले संदेश"
                } else {
                    "End-to-end encryption, TOR routing, disappearing messages"
                },
                onClick = { onNavigate(ZeroTraceScreen.PRIVACY_CENTER) },
                modifier = Modifier.testTag("open_privacy_center_card")
            )

            SettingsNavRow(
                icon = Icons.Default.Timer,
                title = if (isHi) "गायब होने वाले संदेश" else "Disappearing Messages",
                subtitle = "Default: ${sessionTimeoutSeconds / 60} minutes • ${if (autoLockOnBackground) "On" else "Off"}",
                onClick = { onNavigate(ZeroTraceScreen.PRIVACY_CENTER) }
            )

            SettingsNavRow(
                icon = Icons.Default.AccessTime,
                title = if (isHi) "स्टोरेज और अस्थिर RAM" else "Storage & Data",
                subtitle = "$volatileMessagesInRamCount msgs, $volatileStatusesInRamCount statuses, $volatileCallsInRamCount calls in RAM",
                onClick = onWipeVolatileCaches
            )

            SettingsNavRow(
                icon = Icons.Default.VerifiedUser,
                title = if (isHi) "सत्यापित संपर्क और PQ ऑडिट" else "Verified Contacts & PQ",
                subtitle = "Verified PQ keys, Safety Numbers, StrongBox & JNI",
                onClick = { onNavigate(ZeroTraceScreen.SECURITY_DASHBOARD) },
                modifier = Modifier.testTag("open_security_dashboard_card")
            )

            SettingsNavRow(
                icon = Icons.Default.QrCode,
                title = if (isHi) "मेरी पहचान और QR कोड" else "My Identity & QR Code",
                subtitle = "${identitySummary?.onionAddress ?: "No active identity"} • PQ Safety: ${identitySummary?.pqSafetyDigits?.take(12) ?: "—"}…",
                onClick = { onNavigate(ZeroTraceScreen.MY_IDENTITY) },
                modifier = Modifier.testTag("settings_my_qr_button")
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Appearance, Theme & Language
            SettingsSectionCard(
                icon = Icons.Default.Palette,
                title = if (isHi) "दिखावट और भाषा" else "Appearance & Language"
            ) {
                Text("Theme Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = chipColors
                        )
                    }
                }

                Text("Accent Palette", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AccentPalette.entries.forEach { palette ->
                        FilterChip(
                            selected = accentPalette == palette,
                            onClick = { onSelectAccentPalette(palette) },
                            label = { Text(palette.label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = chipColors
                        )
                    }
                }

                Text("Chat Wallpaper", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatWallpaperStyle.entries.forEach { wp ->
                        FilterChip(
                            selected = wallpaperStyle == wp,
                            onClick = { onSelectWallpaperStyle(wp) },
                            label = { Text(wp.label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = chipColors
                        )
                    }
                }

                Text("Interface Language", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.displayLabel, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = chipColors,
                            modifier = Modifier.testTag("settings_lang_${lang.code}")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            // Session Timeout & Screen Security
            SettingsSectionCard(title = "SESSION TIMEOUT & SCREEN SECURITY") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60L to "1 min", 300L to "5 min", 900L to "15 min").forEach { (sec, label) ->
                        FilterChip(
                            selected = sessionTimeoutSeconds == sec,
                            onClick = { onSelectTimeoutSeconds(sec) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = chipColors
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHi) "ऐप बैकग्राउंड होने पर तुरंत सत्र मिटाएं" else "Wipe volatile sessions when app backgrounds",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoLockOnBackground,
                        onCheckedChange = onToggleAutoLock,
                        modifier = Modifier.testTag("auto_lock_switch")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHi) "स्क्रीनशॉट सुरक्षा (FLAG_SECURE)" else "Screenshot Protection (FLAG_SECURE)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Keep OFF in browser Streaming Emulator so virtual display frames render visibly.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = flagSecureEnabled,
                        onCheckedChange = onToggleFlagSecure,
                        modifier = Modifier.testTag("flag_secure_switch")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            // Storage & Volatile RAM Cache Cleanup
            SettingsSectionCard(title = "VOLATILE RAM CACHE CLEANUP") {
                Text(
                    text = "ZeroTrace never writes messages, media, or call logs to SQLite ($volatileMessagesInRamCount msgs • $volatileStatusesInRamCount statuses • $volatileCallsInRamCount calls in RAM).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onWipeVolatileCaches,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wipe_volatile_caches_button")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zeroize All Volatile Caches & Clipboard Now")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            // Tor Network Transport Configuration
            SettingsSectionCard(title = "TOR ONION TRANSPORT (FAIL-CLOSED)") {
                Text(
                    text = "Direct clearnet IP fallback is strictly disabled. Embedded Tor connects peers across different mobile data/Wi-Fi networks without Orbot or same-Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TorTransportBackend.entries.forEach { mode ->
                    FilterChip(
                        selected = transportBackend == mode,
                        onClick = { onSelectTransportBackend(mode) },
                        label = {
                            Text(
                                text = if (isHi) mode.labelHi else mode.labelEn,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = null,
                        colors = chipColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transport_mode_${mode.name}")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            // Emergency Session & Identity Destruction Controls
            SettingsSectionCard(title = "EMERGENCY SESSION & IDENTITY CONTROLS") {
                OutlinedButton(
                    onClick = onDisconnectAllSessions,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_disconnect_all_button")
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect All Sessions & Wipe RAM", color = MaterialTheme.colorScheme.tertiary)
                }

                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_delete_identity_button")
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Local Identity & Keystore Key")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            // About & Open-Source Licenses
            SettingsSectionCard(title = "ABOUT & CRYPTOGRAPHIC SPECIFICATIONS") {
                Text(
                    text = "ZeroTrace Messenger 2.0 (v2.0.0-strict-ephemeral)\n" +
                        "• Classical Crypto: X25519 (RFC 7748) & Ed25519 (RFC 8032)\n" +
                        "• Post-Quantum Crypto: NIST FIPS 203 ML-KEM-1024 & FIPS 204 ML-DSA-87\n" +
                        "• Symmetric AEAD & KDF: ChaCha20-Poly1305 (RFC 8439) & HKDF-SHA256\n" +
                        "• Honest Limitations: Group Chats require MLS/Sender Keys (planned). Voice/Video calling over Tor TCP is high-latency and experimental. Ephemeral statuses require live P2P Tor connectivity.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dedicated Privacy Center Screen
 */
@Composable
fun ZeroTracePrivacyCenterScreen(
    connectivityState: ConnectivityState,
    torProgress: Int,
    hardwareSecurityLevel: HardwareSecurityLevel,
    flagSecureEnabled: Boolean,
    autoLockOnBackground: Boolean,
    biometricAppLockEnabled: Boolean,
    disappearingTimerSec: Long,
    readReceiptsEnabled: Boolean,
    typingIndicatorsEnabled: Boolean,
    metadataMinimizedMode: Boolean,
    onToggleFlagSecure: (Boolean) -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onToggleBiometricAppLock: (Boolean) -> Unit,
    onSelectDisappearingTimer: (Long) -> Unit,
    onToggleReadReceipts: (Boolean) -> Unit,
    onToggleTypingIndicators: (Boolean) -> Unit,
    onToggleMetadataMinimized: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurface
    )

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (ZeroTracePalette.isLight()) 0.dp else 3.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Privacy & Security",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "TOR v3 • ${connectivityState.code}" +
                                    if (torProgress in 1..99) " ($torProgress%)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .testTag("privacy_center_screen")
        ) {
            SettingsSectionCard(icon = Icons.Default.Shield, title = "CRYPTOGRAPHIC & HARDWARE STATUS") {
                Surface(
                    color = ZeroTracePalette.mintContainer(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "• E2EE Protocol: ZT-PQXDH-P2P-v1 + Hybrid Double Ratchet\n" +
                            "• Hardware Key Backing: ${hardwareSecurityLevel.name}\n" +
                            "• Tor Fail-Closed Routing: Active (${connectivityState.code})\n" +
                            "• Cloud Backups: Hard-Disabled (allowBackup=false)",
                        style = MonospaceStyle.copy(fontSize = 11.sp),
                        color = ZeroTracePalette.mintContent(),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            SettingsSectionCard(title = "SCREEN & APP LOCK HARDENING") {
                PrivacyToggleRow(
                    title = "Screenshot & Recording Protection (FLAG_SECURE)",
                    subtitle = "Blocks OS screenshots and Recent Apps thumbnails (keep OFF on browser Streaming Emulator)",
                    checked = flagSecureEnabled,
                    onCheckedChange = onToggleFlagSecure
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                PrivacyToggleRow(
                    title = "Auto-Wipe Sessions on App Background",
                    subtitle = "Destroys active ratchet keys and chat RAM when app leaves foreground",
                    checked = autoLockOnBackground,
                    onCheckedChange = onToggleAutoLock
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                PrivacyToggleRow(
                    title = "Biometric / Device Credential Gate",
                    subtitle = "Require hardware biometric prompt when unlocking local identity",
                    checked = biometricAppLockEnabled,
                    onCheckedChange = onToggleBiometricAppLock
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            SettingsSectionCard(title = "DISAPPEARING SESSION TIMER") {
                Text(
                    text = "Maximum inactivity duration before volatile sessions and attachments are zeroized:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60L to "1 min", 300L to "5 min", 900L to "15 min").forEach { (sec, label) ->
                        FilterChip(
                            selected = disappearingTimerSec == sec,
                            onClick = { onSelectDisappearingTimer(sec) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = chipColors
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            SettingsSectionCard(title = "METADATA MINIMIZATION & RECEIPTS") {
                PrivacyToggleRow(
                    title = "Metadata-Minimized Mode",
                    subtitle = "Strips EXIF camera tags, normalizes packet padding, and suppresses unnecessary headers",
                    checked = metadataMinimizedMode,
                    onCheckedChange = onToggleMetadataMinimized
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                PrivacyToggleRow(
                    title = "Authenticated Delivery Receipts",
                    subtitle = "Send encrypted ratchet acknowledgments inside the active session",
                    checked = readReceiptsEnabled,
                    onCheckedChange = onToggleReadReceipts
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                PrivacyToggleRow(
                    title = "Ephemeral Typing Indicators",
                    subtitle = "Share typing state only inside established Tor E2EE sessions",
                    checked = typingIndicatorsEnabled,
                    onCheckedChange = onToggleTypingIndicators
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector? = null,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

