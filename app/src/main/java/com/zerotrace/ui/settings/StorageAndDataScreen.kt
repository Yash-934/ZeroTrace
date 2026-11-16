package com.zerotrace.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.security.ChatStorageBreakdown
import com.zerotrace.ui.AppLanguage
import com.zerotrace.ui.components.ContactAvatar
import com.zerotrace.ui.theme.CrimsonDanger
import com.zerotrace.ui.theme.MonospaceStyle
import com.zerotrace.ui.theme.ZeroTracePalette

@Composable
fun StorageAndDataScreen(
    language: AppLanguage,
    totalRamBytes: Long,
    totalRamMessages: Int,
    totalRamMediaCount: Int,
    totalRamStatusesCount: Int,
    totalRamCallsCount: Int,
    totalRomBytes: Long,
    totalRomMessages: Int,
    totalRomMediaCount: Int,
    keepDataChatsCount: Int,
    contactsByOnion: Map<String, VerifiedContactRecord>,
    chatBreakdowns: List<ChatStorageBreakdown>,
    statusBanner: String?,
    errorBanner: String?,
    onDismissBanner: () -> Unit,
    onToggleKeepDataForChat: (onionAddress: String, enabled: Boolean) -> Unit,
    onMoveChatRamToRom: (onionAddress: String) -> Unit,
    onMoveChatRomToRam: (onionAddress: String) -> Unit,
    onDeleteChatRamOnly: (onionAddress: String) -> Unit,
    onDeleteChatRomOnly: (onionAddress: String) -> Unit,
    onDeleteChatBoth: (onionAddress: String) -> Unit,
    onMoveAllRamToRom: () -> Unit,
    onMoveAllRomToRam: () -> Unit,
    onWipeAllRam: () -> Unit,
    onWipeAllRom: () -> Unit,
    onWipeEverything: () -> Unit,
    onBack: () -> Unit
) {
    val isHi = language == AppLanguage.HINDI
    var confirmGlobalAction by remember { mutableStateOf<String?>(null) }
    var deleteDialogTarget by remember { mutableStateOf<ChatStorageBreakdown?>(null) }

    if (confirmGlobalAction != null) {
        val action = confirmGlobalAction!!
        val (title, body, onConfirm) = when (action) {
            "WIPE_RAM" -> Triple(
                if (isHi) "अस्थिर RAM साफ़ करें?" else "Wipe Volatile RAM Buffers?",
                if (isHi) {
                    "यह केवल RAM से अस्थायी डेटा, मीडिया बफ़र, स्टेटस और कॉल लॉग हटाएगा। 'Keep Data' चैट्स एन्क्रिप्टेड ROM में सुरक्षित रहेंगी।"
                } else {
                    "Clears volatile RAM buffers, in-memory media, statuses & call history. 'Keep Data' chats remain safely encrypted in ROM."
                },
                onWipeAllRam
            )
            "WIPE_ROM" -> Triple(
                if (isHi) "एन्क्रिप्टेड ROM वॉल्ट नष्ट करें?" else "Shred All Encrypted ROM Vaults?",
                if (isHi) {
                    "यह सभी Keep-Data चैट्स की एन्क्रिप्टेड ROM फ़ाइलों को 0-ओवरराइट कर स्थायी रूप से हटा देगा।"
                } else {
                    "Zero-overwrites and deletes all per-chat Keep Data files and the global encrypted ROM vault on disk."
                },
                onWipeAllRom
            )
            else -> Triple(
                if (isHi) "सारा डेटा (RAM + ROM) हटाएं?" else "Wipe Both RAM & Encrypted ROM?",
                if (isHi) {
                    "RAM और ROM दोनों से सभी चैट, मीडिया, स्टेटस और कॉल इतिहास स्थायी रूप से मिटा दिए जाएंगे।"
                } else {
                    "Permanently deletes all chats, media files, statuses, and call logs from both RAM and Encrypted ROM."
                },
                onWipeEverything
            )
        }
        AlertDialog(
            onDismissRequest = { confirmGlobalAction = null },
            title = { Text(title, color = CrimsonDanger, fontWeight = FontWeight.Bold) },
            text = { Text(body) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmGlobalAction = null
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) {
                    Text(if (isHi) "हटाएं" else "Confirm Wipe")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmGlobalAction = null }) {
                    Text(if (isHi) "रद्द करें" else "Cancel")
                }
            }
        )
    }

    deleteDialogTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteDialogTarget = null },
            title = {
                Text(
                    text = if (isHi) "${target.alias} का डेटा हटाएं" else "Delete Data — ${target.alias}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RAM: ${formatBytesCompact(target.ramTotalBytes)} (${target.ramMessageCount} msgs) • " +
                            "ROM: ${formatBytesCompact(target.romTotalBytes)} (${target.romMessageCount} msgs)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            deleteDialogTarget = null
                            onDeleteChatRamOnly(target.onionAddress)
                        },
                        enabled = target.ramMessageCount > 0 || target.ramTotalBytes > 0L,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isHi) "केवल RAM से हटाएं (ROM सुरक्षित रहे)" else "Delete from RAM Only (Keep ROM)")
                    }
                    OutlinedButton(
                        onClick = {
                            deleteDialogTarget = null
                            onDeleteChatRomOnly(target.onionAddress)
                        },
                        enabled = target.romMessageCount > 0 || target.romTotalBytes > 0L,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isHi) "केवल Encrypted ROM से हटाएं" else "Delete from Encrypted ROM Only", color = CrimsonDanger)
                    }
                    Button(
                        onClick = {
                            deleteDialogTarget = null
                            onDeleteChatBoth(target.onionAddress)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isHi) "RAM और ROM दोनों से हटाएं" else "Delete Both (RAM + ROM)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { deleteDialogTarget = null }) {
                    Text(if (isHi) "बंद करें" else "Close")
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
                        IconButton(onClick = onBack, modifier = Modifier.testTag("storage_back_button")) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = if (isHi) "स्टोरेज और डेटा (RAM / ROM)" else "Storage & Data",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isHi) {
                                    "अस्थिर RAM बनाम डबल-एन्क्रिप्टेड ROM वॉल्ट"
                                } else {
                                    "Volatile RAM vs Double-Cipher Hardware ROM Vault"
                                },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("storage_and_data_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!errorBanner.isNullOrBlank()) {
                item {
                    Surface(
                        color = CrimsonDanger.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissBanner() }
                    ) {
                        Text(
                            text = errorBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonDanger,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            } else if (!statusBanner.isNullOrBlank()) {
                item {
                    Surface(
                        color = ZeroTracePalette.mintContainer(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
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
            }

            // Allocation Split Visual Bar
            item {
                val combined = (totalRamBytes + totalRomBytes).coerceAtLeast(1L)
                val ramFraction = (totalRamBytes.toFloat() / combined.toFloat()).coerceIn(0f, 1f)
                val romFraction = (totalRomBytes.toFloat() / combined.toFloat()).coerceIn(0f, 1f)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isHi) "कुल उपयोग: ${formatBytesCompact(totalRamBytes + totalRomBytes)}"
                                else "Total Used: ${formatBytesCompact(totalRamBytes + totalRomBytes)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "$keepDataChatsCount Keep-Data chat(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            if (ramFraction > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .weight(ramFraction.coerceAtLeast(0.02f))
                                        .fillMaxSize()
                                        .background(Color(0xFF0284C7))
                                )
                            }
                            if (romFraction > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .weight(romFraction.coerceAtLeast(0.02f))
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "● RAM: ${formatBytesCompact(totalRamBytes)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF0284C7)
                            )
                            Text(
                                text = "● Encrypted ROM: ${formatBytesCompact(totalRomBytes)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Volatile RAM Card + Move / Delete actions
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF0284C7))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isHi) "अस्थिर RAM (Volatile Memory)" else "Volatile RAM Memory",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = formatBytesCompact(totalRamBytes),
                                style = MonospaceStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFF0284C7)
                            )
                        }
                        Text(
                            text = "$totalRamMessages msgs • $totalRamMediaCount in-RAM media • $totalRamStatusesCount statuses • $totalRamCallsCount calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onMoveAllRamToRom,
                                enabled = totalRamMessages > 0 || totalRamMediaCount > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("move_all_ram_to_rom_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHi) "सब RAM -> ROM करें" else "Move All -> ROM",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { confirmGlobalAction = "WIPE_RAM" },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wipe_all_ram_btn")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonDanger, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHi) "RAM साफ़ करें" else "Wipe RAM",
                                    color = CrimsonDanger,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Ultra-Encrypted ROM Card + Move / Delete actions
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SdStorage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isHi) "अल्ट्रा-एन्क्रिप्टेड ROM वॉल्ट" else "Ultra-Encrypted ROM Vault",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Text(
                                text = formatBytesCompact(totalRomBytes),
                                style = MonospaceStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "$totalRomMessages msgs • $totalRomMediaCount encrypted ROM blobs • $keepDataChatsCount Keep-Data chats",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            color = ZeroTracePalette.mintContainer(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.EnhancedEncryption,
                                    contentDescription = null,
                                    tint = ZeroTracePalette.mintContent(),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Dual-Cipher Cascade: ChaCha20-Poly1305 (256b) + AES-256-GCM • Hardware StrongBox/TEE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = ZeroTracePalette.mintContent()
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onMoveAllRomToRam,
                                enabled = totalRomMessages > 0 || totalRomMediaCount > 0 || totalRomBytes > 0L,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("move_all_rom_to_ram_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHi) "सब ROM -> RAM करें" else "Move ROM -> RAM",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = { confirmGlobalAction = "WIPE_ROM" },
                                enabled = totalRomBytes > 0L || totalRomMessages > 0,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("wipe_all_rom_btn")
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonDanger, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isHi) "ROM नष्ट करें" else "Shred ROM",
                                    color = CrimsonDanger,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = { confirmGlobalAction = "WIPE_BOTH" },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isHi) "सब कुछ स्थायी रूप से मिटाएं (RAM + ROM)" else "Wipe Everything (RAM + ROM)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Section Header: Per-Chat Storage Breakdown
            item {
                Text(
                    text = if (isHi) "प्रति-चैट स्टोरेज और Keep Data नियंत्रण" else "Per-Chat Storage, Move & Delete",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (chatBreakdowns.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.SdStorage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isHi) "अभी कोई चैट डेटा नहीं है" else "No chat data in RAM or ROM yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(chatBreakdowns, key = { it.onionAddress }) { item ->
                    val contact = contactsByOnion[item.onionAddress.lowercase()]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            1.dp,
                            if (item.keepDataEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ContactAvatar(
                                    alias = item.alias,
                                    onionAddress = item.onionAddress,
                                    isVerified = contact != null && !contact.identityChangedWarning,
                                    avatarBytes = contact?.avatarBytes,
                                    size = 42.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.alias,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (item.keepDataEnabled) {
                                            if (isHi) "Keep Data चालू • डबल-एन्क्रिप्टेड ROM" else "Keep Data ON • Dual-Cipher ROM"
                                        } else {
                                            if (isHi) "केवल RAM मोड" else "RAM-Only Mode"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.keepDataEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = item.keepDataEnabled,
                                    onCheckedChange = { enabled ->
                                        onToggleKeepDataForChat(item.onionAddress, enabled)
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "RAM: ${formatBytesCompact(item.ramTotalBytes)} (${item.ramMessageCount}m, ${item.ramMediaCount}f)",
                                    style = MonospaceStyle.copy(fontSize = 11.sp),
                                    color = Color(0xFF0284C7)
                                )
                                Text(
                                    text = "ROM: ${formatBytesCompact(item.romTotalBytes)} (${item.romMessageCount}m, ${item.romMediaCount}f)",
                                    style = MonospaceStyle.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onMoveChatRamToRom(item.onionAddress) },
                                    enabled = item.ramMessageCount > 0 || item.ramTotalBytes > 0L,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Move -> ROM", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onMoveChatRomToRam(item.onionAddress) },
                                    enabled = item.romMessageCount > 0 || item.romTotalBytes > 0L,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Move -> RAM", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { deleteDialogTarget = item },
                                    enabled = item.ramMessageCount > 0 || item.romMessageCount > 0 ||
                                        item.ramTotalBytes > 0L || item.romTotalBytes > 0L,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete chat storage",
                                        tint = CrimsonDanger,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatBytesCompact(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
