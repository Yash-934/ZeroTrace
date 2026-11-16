package com.zerotrace.ui.settings

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import com.zerotrace.media.rememberSafeMediaPicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotrace.bridge.LocalUserProfile
import com.zerotrace.bridge.PublicIdentitySummary
import com.zerotrace.network.ConnectivityState
import com.zerotrace.network.TorTransportBackend
import com.zerotrace.security.BiometricAuthHelper
import com.zerotrace.security.BiometricCapability
import com.zerotrace.security.HardwareSecurityLevel
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.ZeroTraceScreen
import com.zerotrace.ui.components.ContactAvatar
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
    localProfile: LocalUserProfile = LocalUserProfile(),
    connectivityState: ConnectivityState,
    torProgress: Int,
    themeMode: AppThemeMode,
    accentPalette: AccentPalette,
    wallpaperStyle: ChatWallpaperStyle,
    foreverVaultEnabled: Boolean = true,
    sessionTimeoutSeconds: Long,
    autoLockOnBackground: Boolean,
    biometricAppLockEnabled: Boolean = false,
    flagSecureEnabled: Boolean,
    transportBackend: TorTransportBackend,
    volatileMessagesInRamCount: Int,
    volatileStatusesInRamCount: Int,
    volatileCallsInRamCount: Int,
    totalRamBytes: Long = 0L,
    totalRomBytes: Long = 0L,
    keepDataChatsCount: Int = 0,
    statusBanner: String? = null,
    errorBanner: String? = null,
    onDismissBanner: () -> Unit = {},
    onUpdateLocalProfile: (String, String, Uri?, Boolean) -> Unit = { _, _, _, _ -> },
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectAccentPalette: (AccentPalette) -> Unit,
    onSelectWallpaperStyle: (ChatWallpaperStyle) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleForeverVault: (Boolean) -> Unit = {},
    onSelectTimeoutSeconds: (Long) -> Unit,
    onToggleAutoLock: (Boolean) -> Unit,
    onRequestToggleBiometric: (Boolean) -> Unit = {},
    onToggleFlagSecure: (Boolean) -> Unit,
    onSelectTransportBackend: (TorTransportBackend) -> Unit,
    onWipeVolatileCaches: () -> Unit,
    onDisconnectAllSessions: () -> Unit,
    onDeleteLocalIdentity: () -> Unit,
    onNavigate: (ZeroTraceScreen) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isHi = language == AppLanguage.HINDI
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by rememberSaveable { mutableStateOf(false) }
    val biometricCapability = remember { BiometricAuthHelper.checkCapability(context) }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurface
    )

    if (showEditProfileDialog) {
        EditLocalProfileDialog(
            initialProfile = localProfile,
            onionAddress = identitySummary?.onionAddress ?: "local.onion",
            isHi = isHi,
            onSave = { name, bio, avatarUri, removePhoto ->
                showEditProfileDialog = false
                onUpdateLocalProfile(name, bio, avatarUri, removePhoto)
            },
            onDismiss = { showEditProfileDialog = false }
        )
    }

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
            if (!errorBanner.isNullOrBlank()) {
                Surface(
                    color = CrimsonDanger.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onDismissBanner() }
                ) {
                    Text(
                        text = errorBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonDanger,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else if (!statusBanner.isNullOrBlank()) {
                Surface(
                    color = ZeroTracePalette.mintContainer(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onDismissBanner() }
                ) {
                    Text(
                        text = statusBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZeroTracePalette.mintContent(),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // WhatsApp-style Editable User Profile Header Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditProfileDialog = true }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("settings_profile_header"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    ContactAvatar(
                        alias = localProfile.displayName,
                        onionAddress = identitySummary?.onionAddress ?: "local.onion",
                        isVerified = true,
                        avatarBytes = localProfile.avatarJpegBytes,
                        size = 62.dp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change Profile Photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localProfile.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = localProfile.bio.ifBlank {
                            if (isHi) "बायो और प्रोफ़ाइल फ़ोटो सेट करने के लिए टैप करें" else "Tap to set your bio & profile photo"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { showEditProfileDialog = true },
                    modifier = Modifier.testTag("settings_edit_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { onNavigate(ZeroTraceScreen.MY_IDENTITY) }) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "My Pairing QR Code",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))

            // Top Flat WhatsApp-style Rows matching Mockup Screen 8
            SettingsNavRow(
                icon = Icons.Default.Info,
                title = if (isHi) "गोपनीयता और सुरक्षा" else "Privacy & Security",
                subtitle = if (isHi) {
                    "एंड-टू-एंड एन्क्रिप्शन, TOR रूटिंग, फ़िंगरप्रिंट लॉक"
                } else {
                    "End-to-end encryption, TOR routing, biometric lock & vault"
                },
                onClick = { onNavigate(ZeroTraceScreen.PRIVACY_CENTER) },
                modifier = Modifier.testTag("open_privacy_center_card")
            )

            SettingsNavRow(
                icon = Icons.Default.Timer,
                title = if (isHi) "सत्र और गायब होने वाले संदेश" else "Session Retention & Disappearing",
                subtitle = if (foreverVaultEnabled || sessionTimeoutSeconds <= 0L) {
                    if (isHi) "फ़ॉरेवर एन्क्रिप्टेड वॉल्ट (Keystore AES-256-GCM)" else "Forever Encrypted Vault • Keystore AES-256-GCM"
                } else {
                    "Ephemeral timeout: ${sessionTimeoutSeconds / 60} min • Auto-wipe: ${if (autoLockOnBackground) "On" else "Off"}"
                },
                onClick = { onNavigate(ZeroTraceScreen.PRIVACY_CENTER) }
            )

            SettingsNavRow(
                icon = Icons.Default.AccessTime,
                title = if (isHi) "स्टोरेज और डेटा (RAM / ROM)" else "Storage & Data",
                subtitle = if (totalRamBytes > 0L || totalRomBytes > 0L || keepDataChatsCount > 0) {
                    "RAM: ${formatBytesCompact(totalRamBytes)} ($volatileMessagesInRamCount msgs) • ROM: ${formatBytesCompact(totalRomBytes)} ($keepDataChatsCount Keep-Data)"
                } else {
                    "$volatileMessagesInRamCount msgs, $volatileStatusesInRamCount statuses, $volatileCallsInRamCount calls in RAM • Tap to manage"
                },
                onClick = { onNavigate(ZeroTraceScreen.STORAGE_AND_DATA) },
                modifier = Modifier.testTag("settings_storage_and_data_row")
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

            // Session Retention, Biometric Lock & Screen Security
            SettingsSectionCard(icon = Icons.Default.Lock, title = "FOREVER VAULT, BIOMETRIC LOCK & SCREEN SECURITY") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHi) "फ़ॉरेवर सत्र (एन्क्रिप्टेड वॉल्ट)" else "Forever Session (Encrypted Vault)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (foreverVaultEnabled) {
                                "Active: Old chats & sessions stay safely persisted under Keystore AES-256-GCM across restarts."
                            } else {
                                "Off: Volatile RAM-only mode wipes chats after idle timeout or app close."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = foreverVaultEnabled,
                        onCheckedChange = onToggleForeverVault,
                        modifier = Modifier.testTag("forever_vault_switch")
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0L to "Forever", 60L to "1 min", 300L to "5 min", 900L to "15 min").forEach { (sec, label) ->
                        val isSelected = if (sec == 0L) {
                            foreverVaultEnabled || sessionTimeoutSeconds <= 0L
                        } else {
                            !foreverVaultEnabled && sessionTimeoutSeconds == sec
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectTimeoutSeconds(sec) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = chipColors,
                            modifier = Modifier.testTag("timeout_chip_$sec")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHi) "फ़िंगरप्रिंट / बायोमेट्रिक ऐप लॉक" else "Fingerprint / Biometric App Lock",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Text(
                            text = when (biometricCapability) {
                                is BiometricCapability.Unavailable -> biometricCapability.reason
                                else -> "Hardware BiometricPrompt locks ZeroTrace on launch & background resume."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (biometricCapability is BiometricCapability.Unavailable) {
                                CrimsonDanger
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = biometricAppLockEnabled,
                        onCheckedChange = onRequestToggleBiometric,
                        modifier = Modifier.testTag("settings_biometric_lock_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHi) "ऐप बैकग्राउंड होने पर सत्र मिटाएं" else "Wipe volatile sessions when app backgrounds",
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

            SettingsSectionCard(title = "DISAPPEARING & FOREVER VAULT TIMER") {
                Text(
                    text = "Choose Forever Encrypted Vault (no auto-wipe) or a strict inactivity auto-purge timer:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0L to "Forever", 60L to "1 min", 300L to "5 min", 900L to "15 min").forEach { (sec, label) ->
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

@Composable
private fun EditLocalProfileDialog(
    initialProfile: LocalUserProfile,
    onionAddress: String,
    isHi: Boolean,
    onSave: (displayName: String, bio: String, avatarUri: Uri?, removePhoto: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var nameDraft by rememberSaveable { mutableStateOf(initialProfile.displayName) }
    var bioDraft by rememberSaveable { mutableStateOf(initialProfile.bio) }
    var pickedAvatarUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var removePhoto by rememberSaveable { mutableStateOf(false) }
    val pickedAvatarUri = remember(pickedAvatarUriString) {
        pickedAvatarUriString?.let { Uri.parse(it) }
    }

    val mediaPicker = rememberSafeMediaPicker(
        onImagePicked = { uri ->
            pickedAvatarUriString = uri.toString()
            removePhoto = false
        }
    )

    val previewBitmap = remember(pickedAvatarUri) {
        pickedAvatarUri?.let { uri ->
            try {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (maxSide > 0 && maxSide / sample > 360) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                } ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(resolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val w = info.size.width
                        val h = info.size.height
                        val largest = maxOf(w, h)
                        if (largest > 360) {
                            val scale = 360f / largest.toFloat()
                            decoder.setTargetSize(
                                (w * scale).toInt().coerceAtLeast(1),
                                (h * scale).toInt().coerceAtLeast(1)
                            )
                        }
                    }
                } else null
                bmp?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isHi) "प्रोफ़ाइल संपादित करें" else "Edit Encrypted Profile",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .clickable { mediaPicker.pickImage() },
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = "Selected Profile Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ContactAvatar(
                            alias = nameDraft.ifBlank { "ZeroTrace User" },
                            onionAddress = onionAddress,
                            isVerified = false,
                            avatarBytes = if (removePhoto) null else initialProfile.avatarJpegBytes,
                            size = 86.dp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { mediaPicker.pickImage() },
                        modifier = Modifier.testTag("profile_pick_photo_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isHi) "फ़ोटो चुनें" else "Choose Photo", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!removePhoto && (pickedAvatarUri != null || initialProfile.avatarJpegBytes != null)) {
                        TextButton(
                            onClick = {
                                pickedAvatarUriString = null
                                removePhoto = true
                            }
                        ) {
                            Text("Remove", color = CrimsonDanger, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { if (it.length <= 48) nameDraft = it },
                    label = { Text(if (isHi) "आपका नाम" else "Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )

                OutlinedTextField(
                    value = bioDraft,
                    onValueChange = { if (it.length <= 160) bioDraft = it },
                    label = { Text(if (isHi) "बायो / स्टेटस" else "Bio / About") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_bio_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        nameDraft.trim().ifEmpty { "ZeroTrace User" },
                        bioDraft.trim(),
                        pickedAvatarUri,
                        removePhoto
                    )
                },
                modifier = Modifier.testTag("profile_save_button")
            ) {
                Text(if (isHi) "सहेजें" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isHi) "रद्द करें" else "Cancel")
            }
        }
    )
}
