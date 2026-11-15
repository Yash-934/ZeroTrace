package com.zerotrace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.TransferDirection
import com.zerotrace.bridge.TransferLifecycleState
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.network.ConnectivityState
import com.zerotrace.ui.theme.MonospaceStyle
import com.zerotrace.ui.theme.ZeroTraceAmber
import com.zerotrace.ui.theme.ZeroTraceCyan
import com.zerotrace.ui.theme.ZeroTraceEmerald
import com.zerotrace.ui.theme.ZeroTraceOutgoingBubble
import com.zerotrace.ui.theme.ZeroTraceRed
import com.zerotrace.ui.theme.ZeroTraceSurfaceElevated
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainBottomTab(val labelEn: String, val labelHi: String, val icon: ImageVector, val tag: String) {
    CHATS("Chats", "चैट", Icons.AutoMirrored.Filled.Chat, "bottom_tab_chats"),
    UPDATES("Updates", "अपडेट", Icons.Default.Update, "bottom_tab_updates"),
    CALLS("Calls", "कॉल", Icons.Default.Call, "bottom_tab_calls"),
    SETTINGS("Settings", "सेटिंग्स", Icons.Default.Settings, "bottom_tab_settings")
}

@Composable
fun ZeroTraceTopBar(
    title: String,
    subtitle: String? = null,
    connectivityState: ConnectivityState,
    torProgress: Int,
    onBackClick: (() -> Unit)? = null,
    onSearchToggle: (() -> Unit)? = null,
    onQrScannerClick: (() -> Unit)? = null,
    onSecurityDashboardClick: (() -> Unit)? = null,
    onEmergencyWipeClick: (() -> Unit)? = null,
    trailingActions: @Composable (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("topbar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    RoundedCornerShape(11.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "ZeroTrace Shield",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SecureStatusBadge(
                                connectivityState = connectivityState,
                                torProgress = torProgress
                            )
                        }
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trailingActions != null) {
                        trailingActions()
                    } else {
                        if (onQrScannerClick != null) {
                            IconButton(
                                onClick = onQrScannerClick,
                                modifier = Modifier.testTag("topbar_qr_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan or Share QR",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (onSearchToggle != null) {
                            IconButton(
                                onClick = onSearchToggle,
                                modifier = Modifier.testTag("topbar_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (onSecurityDashboardClick != null) {
                            IconButton(
                                onClick = onSecurityDashboardClick,
                                modifier = Modifier.testTag("topbar_security_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Security Dashboard",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (onEmergencyWipeClick != null) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.testTag("topbar_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More actions",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    if (onSecurityDashboardClick != null) {
                                        DropdownMenuItem(
                                            text = { Text("Security & Ratchet Audit") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Security,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onSecurityDashboardClick()
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Emergency RAM Wipe & Lock",
                                                color = ZeroTraceRed,
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = ZeroTraceRed
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onEmergencyWipeClick()
                                        }
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

@Composable
fun ZeroTraceBottomNavigation(
    selectedTab: MainBottomTab,
    unreadChatsCount: Int,
    activeUpdatesCount: Int,
    missedCallsCount: Int,
    isHindi: Boolean,
    onTabSelected: (MainBottomTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("zerotrace_bottom_nav")
    ) {
        MainBottomTab.entries.forEach { tab ->
            val badgeCount = when (tab) {
                MainBottomTab.CHATS -> unreadChatsCount
                MainBottomTab.UPDATES -> activeUpdatesCount
                MainBottomTab.CALLS -> missedCallsCount
                MainBottomTab.SETTINGS -> 0
            }
            val selected = selectedTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.testTag(tab.tag),
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
                                ) {
                                    Text(
                                        text = badgeCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = if (isHindi) tab.labelHi else tab.labelEn
                        )
                    }
                },
                label = {
                    Text(
                        text = if (isHindi) tab.labelHi else tab.labelEn,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun SecureStatusBadge(
    connectivityState: ConnectivityState,
    torProgress: Int,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (connectivityState) {
        ConnectivityState.E2EE_SESSION_READY -> ZeroTraceEmerald
        ConnectivityState.ONION_SERVICE_READY, ConnectivityState.TOR_READY -> ZeroTraceCyan
        ConnectivityState.TOR_BOOTSTRAPPING,
        ConnectivityState.AUTHENTICATING_PEER,
        ConnectivityState.CONNECTING_TO_PEER,
        ConnectivityState.ONION_SERVICE_STARTING,
        ConnectivityState.INITIALIZING -> ZeroTraceAmber
        ConnectivityState.ERROR, ConnectivityState.OFFLINE, ConnectivityState.DISCONNECTING -> ZeroTraceRed
    }
    val shortLabel = when (connectivityState) {
        ConnectivityState.E2EE_SESSION_READY -> "PQ-E2EE LIVE"
        ConnectivityState.ONION_SERVICE_READY -> "TOR v3 READY"
        ConnectivityState.TOR_BOOTSTRAPPING -> "TOR $torProgress%"
        ConnectivityState.AUTHENTICATING_PEER -> "PQXDH..."
        ConnectivityState.OFFLINE -> "OFFLINE"
        ConnectivityState.ERROR -> "FAIL-CLOSED"
        else -> connectivityState.code.take(10)
    }

    Surface(
        color = badgeColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = shortLabel,
                style = MonospaceStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = badgeColor
            )
        }
    }
}

@Composable
fun ContactAvatar(
    alias: String,
    onionAddress: String,
    isVerified: Boolean = true,
    isOnlineOrSessionActive: Boolean = false,
    hasActiveStatusRing: Boolean = false,
    size: Dp = 50.dp,
    modifier: Modifier = Modifier
) {
    val avatarColors = listOf(
        Color(0xFF00E676),
        Color(0xFF00B8D4),
        Color(0xFF7C4DFF),
        Color(0xFFFF9100),
        Color(0xFF1DE9B6),
        Color(0xFF40C4FF)
    )
    val colorIndex = kotlin.math.abs(onionAddress.hashCode()) % avatarColors.size
    val baseColor = avatarColors[colorIndex]
    val initials = alias.trim().split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifEmpty { "ZT" }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            baseColor.copy(alpha = 0.28f),
                            baseColor.copy(alpha = 0.10f)
                        )
                    )
                )
                .border(
                    width = if (hasActiveStatusRing) 2.5.dp else 1.2.dp,
                    color = if (hasActiveStatusRing) ZeroTraceEmerald else baseColor.copy(alpha = 0.55f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = baseColor
            )
        }

        if (isOnlineOrSessionActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(ZeroTraceEmerald)
            )
        } else if (isVerified) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Verified Identity",
                    tint = ZeroTraceEmerald,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    contact: VerifiedContactRecord,
    lastMessagePreview: String,
    lastMessageTimeMillis: Long,
    unreadCount: Int,
    isPinned: Boolean,
    isMuted: Boolean,
    isSessionLive: Boolean,
    onClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleArchive: () -> Unit,
    onClearChatRam: () -> Unit
) {
    var contextMenuExpanded by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Surface(
        color = if (isSessionLive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { contextMenuExpanded = true }
            )
            .testTag("chat_list_item_${contact.alias}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(
                alias = contact.alias,
                onionAddress = contact.onionAddress,
                isVerified = !contact.identityChangedWarning,
                isOnlineOrSessionActive = isSessionLive,
                modifier = Modifier.clickable { onProfileClick() }
            )
            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = contact.alias,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "E2EE Verified",
                            tint = if (contact.identityChangedWarning) ZeroTraceRed else ZeroTraceEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = timeFormatter.format(Date(lastMessageTimeMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unreadCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lastMessagePreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unreadCount > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMuted) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = "Muted",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(16.dp)
                            )
                        }
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(16.dp)
                            )
                        }
                        if (unreadCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(21.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${contact.onionAddress.take(16)}…onion • Safety ${contact.classicalSafetyDigits.take(11)}",
                    style = MonospaceStyle.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DropdownMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = { contextMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (isPinned) "Unpin Conversation" else "Pin Conversation") },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = {
                        contextMenuExpanded = false
                        onTogglePin()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isMuted) "Unmute Alerts" else "Mute Alerts") },
                    leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                    onClick = {
                        contextMenuExpanded = false
                        onToggleMute()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Archive / Unarchive") },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    onClick = {
                        contextMenuExpanded = false
                        onToggleArchive()
                    }
                )
                DropdownMenuItem(
                    text = { Text("View Contact Security Profile") },
                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                    onClick = {
                        contextMenuExpanded = false
                        onProfileClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Zeroize Volatile Chat RAM", color = ZeroTraceRed) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ZeroTraceRed) },
                    onClick = {
                        contextMenuExpanded = false
                        onClearChatRam()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: RichConversationMessage,
    onReplyClick: (RichConversationMessage) -> Unit,
    onReactClick: (RichConversationMessage, String) -> Unit,
    onDeleteFromRamClick: (RichConversationMessage) -> Unit,
    onInspectInfoClick: (RichConversationMessage) -> Unit,
    onAcceptFileTransfer: ((String) -> Unit)? = null,
    onRejectFileTransfer: ((String) -> Unit)? = null,
    onMediaPreviewClick: ((RichConversationMessage) -> Unit)? = null
) {
    var actionMenuExpanded by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val bubbleBg = if (message.isOutgoing) {
        ZeroTraceOutgoingBubble
    } else {
        ZeroTraceSurfaceElevated
    }
    val bubbleBorder = if (message.pqBraidAdvanced) {
        ZeroTraceCyan.copy(alpha = 0.65f)
    } else if (message.isOutgoing) {
        ZeroTraceEmerald.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                color = bubbleBg,
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isOutgoing) 16.dp else 4.dp,
                    bottomEnd = if (message.isOutgoing) 4.dp else 16.dp
                ),
                border = BorderStroke(1.dp, bubbleBorder),
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 320.dp)
                    .combinedClickable(
                        onClick = {
                            if (message.payloadKind in setOf(
                                    MessagePayloadKind.IMAGE,
                                    MessagePayloadKind.VIDEO,
                                    MessagePayloadKind.DOCUMENT
                                )
                            ) {
                                onMediaPreviewClick?.invoke(message)
                            } else {
                                actionMenuExpanded = true
                            }
                        },
                        onLongClick = { actionMenuExpanded = true }
                    )
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    // Reply Preview Banner if replying to an earlier message
                    if (!message.replyToPreview.isNullOrBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.26f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(24.dp)
                                        .background(ZeroTraceEmerald, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = message.replyToPreview,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Render Media / File Transfer / Voice Note / Contact Card if present
                    when (message.payloadKind) {
                        MessagePayloadKind.IMAGE,
                        MessagePayloadKind.VIDEO,
                        MessagePayloadKind.DOCUMENT,
                        MessagePayloadKind.AUDIO_FILE -> {
                            val transfer = message.fileTransfer
                            Surface(
                                color = Color.Black.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ZeroTraceCyan.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val icon = when (message.payloadKind) {
                                            MessagePayloadKind.IMAGE -> Icons.Default.Image
                                            MessagePayloadKind.VIDEO -> Icons.Default.Videocam
                                            MessagePayloadKind.AUDIO_FILE -> Icons.Default.AudioFile
                                            else -> Icons.Default.Description
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = message.payloadKind.name,
                                            tint = ZeroTraceCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = transfer?.manifest?.sanitizedFileName ?: "Encrypted File",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (transfer != null) {
                                                Text(
                                                    text = "${transfer.manifest.totalSizeBytes / 1024} KiB • ${transfer.manifest.totalChunks} AEAD Chunks • SHA-256",
                                                    style = MonospaceStyle.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (transfer != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        when (transfer.state) {
                                            TransferLifecycleState.AWAITING_RECEIVER_CONSENT -> {
                                                Text(
                                                    text = "Receiver consent required before decrypting chunks in RAM",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ZeroTraceAmber
                                                )
                                                if (transfer.direction == TransferDirection.INCOMING &&
                                                    onAcceptFileTransfer != null &&
                                                    onRejectFileTransfer != null
                                                ) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Button(
                                                            onClick = { onAcceptFileTransfer(transfer.manifest.transferId) },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = ZeroTraceEmerald,
                                                                contentColor = Color.Black
                                                            ),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Text("Accept & Verify", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                        OutlinedButton(
                                                            onClick = { onRejectFileTransfer(transfer.manifest.transferId) },
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                        ) {
                                                            Text("Decline", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                }
                                            }

                                            TransferLifecycleState.ENCRYPTING_AND_CHUNKING,
                                            TransferLifecycleState.TRANSMITTING_OVER_TOR,
                                            TransferLifecycleState.VERIFYING_INTEGRITY -> {
                                                val progress = if (transfer.manifest.totalChunks > 0) {
                                                    transfer.chunksProcessed.toFloat() / transfer.manifest.totalChunks.toFloat()
                                                } else 1f
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = ZeroTraceEmerald
                                                )
                                                Text(
                                                    text = "${transfer.state.name} (${transfer.chunksProcessed}/${transfer.manifest.totalChunks})",
                                                    style = MonospaceStyle.copy(fontSize = 10.sp),
                                                    color = ZeroTraceCyan
                                                )
                                            }

                                            TransferLifecycleState.VERIFIED_READY_IN_RAM -> {
                                                Text(
                                                    text = "✓ ChaCha20-Poly1305 + SHA-256 Verified (${transfer.manifest.sha256DigestHex.take(12)}…)",
                                                    style = MonospaceStyle.copy(fontSize = 10.sp),
                                                    color = ZeroTraceEmerald
                                                )
                                            }

                                            else -> {
                                                Text(
                                                    text = transfer.failureReason ?: transfer.state.name,
                                                    style = MonospaceStyle.copy(fontSize = 10.sp),
                                                    color = ZeroTraceRed
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        MessagePayloadKind.VOICE_NOTE -> {
                            Surface(
                                color = Color.Black.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Encrypted Voice Note",
                                        tint = ZeroTraceEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "E2EE Voice Note (${message.voiceDurationSeconds ?: 4}s)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Opus/PCM RAM-Only Stream • Zero Disk Cache",
                                            style = MonospaceStyle.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        MessagePayloadKind.CONTACT_CARD -> {
                            Surface(
                                color = Color.Black.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContactPage,
                                        contentDescription = "Verified Contact Card",
                                        tint = ZeroTraceCyan
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Signed ZeroTrace Peer Card",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        MessagePayloadKind.TEXT -> Unit
                    }

                    Text(
                        text = message.bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (message.pqBraidAdvanced) {
                                "Ratchet #${message.ratchetStepIndex} • ML-KEM-1024 Epoch ${message.pqEpoch}"
                            } else {
                                "Ratchet #${message.ratchetStepIndex} • PQ Epoch ${message.pqEpoch}"
                            },
                            style = MonospaceStyle.copy(fontSize = 9.sp),
                            color = if (message.pqBraidAdvanced) ZeroTraceCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = timeFormatter.format(Date(message.timestampEpochMillis)),
                                style = MonospaceStyle.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (message.isOutgoing) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Authenticated E2EE Delivered",
                                    tint = ZeroTraceEmerald,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reaction Pill
            if (!message.reactionEmoji.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .align(if (message.isOutgoing) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = message.reactionEmoji,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Long-Press Message Action Context Menu
            DropdownMenu(
                expanded = actionMenuExpanded,
                onDismissRequest = { actionMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    onClick = {
                        actionMenuExpanded = false
                        onReplyClick(message)
                    }
                )
                DropdownMenuItem(
                    text = { Text("React 🔒 / ⚡ / ✅") },
                    leadingIcon = { Icon(Icons.Default.EmojiEmotions, contentDescription = null) },
                    onClick = {
                        actionMenuExpanded = false
                        val nextReaction = when (message.reactionEmoji) {
                            "🔒" -> "⚡"
                            "⚡" -> "✅"
                            "✅" -> null
                            else -> "🔒"
                        }
                        onReactClick(message, nextReaction ?: "")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Cryptographic Message Info") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        actionMenuExpanded = false
                        onInspectInfoClick(message)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete & Zeroize from RAM", color = ZeroTraceRed) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ZeroTraceRed) },
                    onClick = {
                        actionMenuExpanded = false
                        onDeleteFromRamClick(message)
                    }
                )
            }
        }
    }
}

@Composable
fun MessageComposer(
    draftText: String,
    onDraftChanged: (String) -> Unit,
    replyingToMessage: RichConversationMessage?,
    onCancelReply: () -> Unit,
    canSend: Boolean,
    isRecordingVoiceNote: Boolean,
    onSendClick: () -> Unit,
    onAttachmentSheetClick: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onQuickCameraClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            AnimatedVisibility(visible = replyingToMessage != null) {
                if (replyingToMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to Ratchet #${replyingToMessage.ratchetStepIndex}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = replyingToMessage.bodyText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onCancelReply,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Reply",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAttachmentSheetClick,
                    enabled = canSend,
                    modifier = Modifier.testTag("composer_attach_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach Encrypted File",
                        tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = draftText,
                    onValueChange = onDraftChanged,
                    enabled = canSend,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input"),
                    placeholder = {
                        Text(
                            text = if (isRecordingVoiceNote) {
                                "Recording encrypted RAM voice note..."
                            } else if (canSend) {
                                "Encrypted message (RAM-only)..."
                            } else {
                                "Fail-closed: Tor E2EE required"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = onQuickCameraClick,
                            enabled = canSend
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Send Encrypted Photo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                if (draftText.isNotBlank()) {
                    IconButton(
                        onClick = onSendClick,
                        enabled = canSend,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Encrypted Message"
                        )
                    }
                } else {
                    IconButton(
                        onClick = onToggleVoiceRecording,
                        enabled = canSend,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isRecordingVoiceNote) ZeroTraceRed else MaterialTheme.colorScheme.primary,
                            contentColor = if (isRecordingVoiceNote) Color.White else Color.Black
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_note_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingVoiceNote) Icons.Default.Check else Icons.Default.Mic,
                            contentDescription = if (isRecordingVoiceNote) "Finish & Send Voice Note" else "Record Encrypted Voice Note"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentPicker(
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onPickDocument: () -> Unit,
    onPickAudio: () -> Unit,
    onPickContactCard: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("attachment_picker_sheet")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Encrypted Attachment Transfer",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Per-file ChaCha20-Poly1305 256-bit key • 32 KiB chunks • Max 16 MiB • EXIF stripped",
                        style = MonospaceStyle.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Attachment Picker")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                AttachmentOptionButton(
                    icon = Icons.Default.Image,
                    label = "Photos",
                    tint = ZeroTraceEmerald,
                    tag = "attach_photo_btn",
                    onClick = onPickPhoto
                )
                AttachmentOptionButton(
                    icon = Icons.Default.Videocam,
                    label = "Videos",
                    tint = ZeroTraceCyan,
                    tag = "attach_video_btn",
                    onClick = onPickVideo
                )
                AttachmentOptionButton(
                    icon = Icons.Default.Description,
                    label = "Document",
                    tint = Color(0xFF7C4DFF),
                    tag = "attach_doc_btn",
                    onClick = onPickDocument
                )
                AttachmentOptionButton(
                    icon = Icons.Default.AudioFile,
                    label = "Audio",
                    tint = ZeroTraceAmber,
                    tag = "attach_audio_btn",
                    onClick = onPickAudio
                )
                AttachmentOptionButton(
                    icon = Icons.Default.ContactPage,
                    label = "Peer Card",
                    tint = Color(0xFF40C4FF),
                    tag = "attach_contact_btn",
                    onClick = onPickContactCard
                )
            }
        }
    }
}

@Composable
private fun AttachmentOptionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag(tag)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f))
                .border(1.dp, tint.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun VoiceCallControls(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onUpgradeToVideo: () -> Unit,
    onEndCall: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallControlCircleButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                active = isMuted,
                onClick = onToggleMute,
                tag = "call_mute_button"
            )
            CallControlCircleButton(
                icon = if (isSpeakerOn) Icons.Default.SpeakerPhone else Icons.Default.VolumeUp,
                label = "Speaker",
                active = isSpeakerOn,
                onClick = onToggleSpeaker,
                tag = "call_speaker_button"
            )
            CallControlCircleButton(
                icon = Icons.Default.Videocam,
                label = "Video",
                active = false,
                onClick = onUpgradeToVideo,
                tag = "call_upgrade_video_button"
            )
            IconButton(
                onClick = onEndCall,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = ZeroTraceRed,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(58.dp)
                    .testTag("call_end_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Encrypted Call"
                )
            }
        }
    }
}

@Composable
fun VideoCallControls(
    isMuted: Boolean,
    isCameraEnabled: Boolean,
    isFrontCamera: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallControlCircleButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Muted" else "Mic",
                active = isMuted,
                onClick = onToggleMute,
                tag = "video_call_mute_button"
            )
            CallControlCircleButton(
                icon = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = if (isCameraEnabled) "Cam On" else "Cam Off",
                active = !isCameraEnabled,
                onClick = onToggleCamera,
                tag = "video_call_camera_button"
            )
            CallControlCircleButton(
                icon = Icons.Default.FlipCameraAndroid,
                label = if (isFrontCamera) "Front" else "Rear",
                active = false,
                onClick = onFlipCamera,
                tag = "video_call_flip_button"
            )
            IconButton(
                onClick = onEndCall,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = ZeroTraceRed,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(58.dp)
                    .testTag("video_call_end_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Encrypted Video Call"
                )
            }
        }
    }
}

@Composable
private fun CallControlCircleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (active) Color.Black else MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .size(50.dp)
                .testTag(tag)
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusCard(
    statusItem: EphemeralStatusItem,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (statusItem.viewedByLocalUser) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("status_card_${statusItem.statusId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(
                alias = statusItem.authorAlias,
                onionAddress = statusItem.authorOnion,
                hasActiveStatusRing = !statusItem.viewedByLocalUser,
                size = 48.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusItem.authorAlias,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = ZeroTraceEmerald.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (statusItem.mediaType == StatusMediaType.IMAGE_CARD) "ENCRYPTED IMAGE" else "P2P RAM STATUS",
                            style = MonospaceStyle.copy(fontSize = 9.sp),
                            color = ZeroTraceEmerald,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = statusItem.captionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Shared ${timeFormatter.format(Date(statusItem.createdAtEpochMillis))} • Live Peer RAM Only (No Central Server)",
                    style = MonospaceStyle.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
            if (onDeleteClick != null) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Wipe Status",
                        tint = ZeroTraceRed
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (primaryActionLabel != null && onPrimaryAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black
                    )
                ) {
                    Text(primaryActionLabel, fontWeight = FontWeight.Bold)
                }
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onSecondaryAction) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}

@Composable
fun LoadingState(
    title: String,
    subtitle: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ErrorState(
    errorMessage: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        color = ZeroTraceRed.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ZeroTraceRed.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Security Error",
                tint = ZeroTraceRed
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
            }
        }
    }
}

@Composable
fun SecurityInformationCard(
    title: String,
    protocolSuite: String,
    torRoutingNote: String,
    storagePolicyNote: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Crypto: $protocolSuite",
                style = MonospaceStyle.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Network: $torRoutingNote",
                style = MonospaceStyle.copy(fontSize = 11.sp),
                color = ZeroTraceCyan
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Storage: $storagePolicyNote",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
