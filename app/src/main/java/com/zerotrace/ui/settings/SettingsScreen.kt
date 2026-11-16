package com.zerotrace.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.components.ZeroTraceTopBar
import com.zerotrace.ui.theme.AccentPalette
import com.zerotrace.ui.theme.AppThemeMode
import com.zerotrace.ui.theme.ChatWallpaperStyle
import com.zerotrace.ui.theme.AmberWarning
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.CyanAccent
import com.zerotrace.ui.theme.EmeraldPrimary
import com.zerotrace.ui.theme.MonospaceStyle

/**
 * Screen 13 of Section 4: WhatsApp-Style Settings Tab
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
            ZeroTraceTopBar(
                title = if (isHi) "सेटिंग्स" else "Settings",
                subtitle = "Identity, Privacy, Appearance & Tor Transport",
                connectivityState = connectivityState,
                torProgress = torProgress,
                onQrScannerClick = { onNavigate(ZeroTraceScreen.MY_IDENTITY) },
                onSecurityDashboardClick = { onNavigate(ZeroTraceScreen.SECURITY_DASHBOARD) },
                onEmergencyWipeClick = onDisconnectAllSessions
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("settings_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. WhatsApp-Style Profile Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(ZeroTraceScreen.MY_IDENTITY) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        ContactAvatar(
                            alias = "ZeroTrace Node",
                            onionAddress = identitySummary?.onionAddress ?: "local.onion",
                            size = 58.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Local Hybrid PQ Identity",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = identitySummary?.onionAddress ?: "No active identity",
                                style = MonospaceStyle.copy(fontSize = 11.sp),
                                color = CyanAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "PQ Safety: ${identitySummary?.pqSafetyDigits?.take(19) ?: "—"}…",
                                style = MonospaceStyle.copy(fontSize = 10.sp),
                                color = EmeraldPrimary
                            )
                        }
                    }
                    IconButton(
                        onClick = { onNavigate(ZeroTraceScreen.MY_IDENTITY) },
                        modifier = Modifier.testTag("settings_my_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "My QR Code",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. Quick Navigation Row: Privacy Center & Security Dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(ZeroTraceScreen.PRIVACY_CENTER) }
                        .testTag("open_privacy_center_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Privacy Center", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "E2EE, Locks & Metadata",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(ZeroTraceScreen.SECURITY_DASHBOARD) }
                        .testTag("open_security_dashboard_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = CyanAccent)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Crypto Audit", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Ratchet, StrongBox & JNI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            // 3. Appearance, Theme, Accent & Wallpaper
            SettingsSectionCard(title = if (isHi) "दिखावट, थीम और भाषा" else "APPEARANCE, THEME & LANGUAGE") {
                Text("Theme Mode", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Accent Palette", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentPalette.entries.forEach { palette ->
                        FilterChip(
                            selected = accentPalette == palette,
                            onClick = { onSelectAccentPalette(palette) },
                            label = { Text(palette.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Chat Wallpaper", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatWallpaperStyle.entries.forEach { wp ->
                        FilterChip(
                            selected = wallpaperStyle == wp,
                            onClick = { onSelectWallpaperStyle(wp) },
                            label = { Text(wp.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Interface Language", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.displayLabel) },
                            modifier = Modifier.testTag("settings_lang_${lang.code}")
                        )
                    }
                }
            }

            // 4. Storage & Volatile RAM Cache Cleanup
            SettingsSectionCard(title = "STORAGE & VOLATILE RAM CACHE") {
                Text(
                    text = "ZeroTrace never writes messages, media, or call logs to SQLite. Current volatile heap footprint:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Active Chat Messages in RAM: $volatileMessagesInRamCount\n" +
                        "• Ephemeral Status Cards in RAM: $volatileStatusesInRamCount\n" +
                        "• Volatile Call Log Entries in RAM: $volatileCallsInRamCount",
                    style = MonospaceStyle.copy(fontSize = 11.sp),
                    color = EmeraldPrimary
                )
                OutlinedButton(
                    onClick = onWipeVolatileCaches,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wipe_volatile_caches_button")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zeroize All Volatile Caches & Clipboard Now")
                }
            }

            // 5. Session Timeout & Screenshot Protection
            SettingsSectionCard(title = "SESSION TIMEOUT & SCREEN SECURITY") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60L to "1 min", 300L to "5 min", 900L to "15 min").forEach { (sec, label) ->
                        FilterChip(
                            selected = sessionTimeoutSeconds == sec,
                            onClick = { onSelectTimeoutSeconds(sec) },
                            label = { Text(label) }
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

            // 6. Tor Network Transport Configuration
            SettingsSectionCard(title = "TOR ONION TRANSPORT CONFIGURATION (FAIL-CLOSED)") {
                Text(
                    text = "Direct clearnet IP fallback is strictly disabled. Embedded Tor connects peers across different mobile data/Wi-Fi networks without Orbot or same-Wi-Fi; external Tor SOCKS5 and local Wi-Fi/Hotspot P2P remain optional backends.",
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transport_mode_${mode.name}")
                    )
                }
            }

            // 7. Emergency Session & Identity Destruction Controls
            SettingsSectionCard(title = "EMERGENCY SESSION & IDENTITY CONTROLS") {
                OutlinedButton(
                    onClick = onDisconnectAllSessions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_disconnect_all_button")
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AmberWarning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect All Sessions & Wipe RAM", color = AmberWarning)
                }

                Button(
                    onClick = { showDeleteConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
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

            // 8. About & Open-Source Licenses
            SettingsSectionCard(title = "ABOUT, LIMITATIONS & OPEN-SOURCE LICENSES") {
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

/**
 * Screen 14 of Section 4: Dedicated Privacy Center Screen
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

    Scaffold(
        topBar = {
            ZeroTraceTopBar(
                title = "Privacy Center",
                subtitle = "Zero-Trust Controls, Keystore & Metadata Hardening",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("privacy_center_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsSectionCard(title = "CRYPTOGRAPHIC & HARDWARE STATUS") {
                Text(
                    text = "• E2EE Protocol: ZT-PQXDH-P2P-v1 + Hybrid Double Ratchet + Periodic ML-KEM-1024 Braid\n" +
                        "• Hardware Key Backing: ${hardwareSecurityLevel.name}\n" +
                        "• Tor Fail-Closed Routing: Active (${connectivityState.code})\n" +
                        "• Cloud Backups: Hard-Disabled (allowBackup=false)",
                    style = MonospaceStyle.copy(fontSize = 11.sp),
                    color = EmeraldPrimary
                )
            }

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
                            label = { Text(label) }
                        )
                    }
                }
            }

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
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = CyanAccent
            )
            content()
        }
    }
}
