package com.zerotrace.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotrace.bridge.EphemeralStatusItem
import com.zerotrace.bridge.MessageDeliveryState
import com.zerotrace.bridge.MessagePayloadKind
import com.zerotrace.bridge.RichConversationMessage
import com.zerotrace.bridge.StatusMediaType
import com.zerotrace.bridge.TransferDirection
import com.zerotrace.bridge.TransferLifecycleState
import com.zerotrace.bridge.VerifiedContactRecord
import com.zerotrace.network.ConnectivityState
import com.zerotrace.ui.theme.MonospaceStyle
import com.zerotrace.ui.theme.WhatsAppReadTickBlue
import com.zerotrace.ui.theme.ZeroTraceAmber
import com.zerotrace.ui.theme.ZeroTraceEmerald
import com.zerotrace.ui.theme.ZeroTracePalette
import com.zerotrace.ui.theme.ZeroTraceRed
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
        tonalElevation = if (ZeroTracePalette.isLight()) 0.dp else 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "ZeroTrace Shield",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            color = if (onBackClick == null && ZeroTracePalette.isLight()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SecureStatusBadge(
                                connectivityState = connectivityState,
                                torProgress = torProgress
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
    val isLight = ZeroTracePalette.isLight()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (isLight) 0.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("zerotrace_bottom_nav")
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainBottomTab.entries.forEach { tab ->
                    val badgeCount = when (tab) {
                        MainBottomTab.CHATS -> unreadChatsCount
                        MainBottomTab.UPDATES -> activeUpdatesCount
                        MainBottomTab.CALLS -> missedCallsCount
                        MainBottomTab.SETTINGS -> 0
                    }
                    val selected = selectedTab == tab
                    val activePillBg = ZeroTracePalette.activePillContainer()
                    val activeTint = ZeroTracePalette.activePillContent()
                    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 4.dp)
                            .testTag(tab.tag)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) activePillBg else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = if (isHindi) tab.labelHi else tab.labelEn,
                                    tint = if (selected) activeTint else inactiveTint,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            if (badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeCount.coerceAtMost(9).toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHindi) tab.labelHi else tab.labelEn,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) MaterialTheme.colorScheme.onSurface else inactiveTint,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecureStatusBadge(
    connectivityState: ConnectivityState,
    torProgress: Int,
    modifier: Modifier = Modifier
) {
    val isLight = ZeroTracePalette.isLight()
    val badgeColor = when (connectivityState) {
        ConnectivityState.E2EE_SESSION_READY -> MaterialTheme.colorScheme.primary
        ConnectivityState.ONION_SERVICE_READY, ConnectivityState.TOR_READY -> MaterialTheme.colorScheme.primary
        ConnectivityState.TOR_BOOTSTRAPPING,
        ConnectivityState.AUTHENTICATING_PEER,
        ConnectivityState.CONNECTING_TO_PEER,
        ConnectivityState.ONION_SERVICE_STARTING,
        ConnectivityState.INITIALIZING -> MaterialTheme.colorScheme.tertiary
        ConnectivityState.ERROR, ConnectivityState.OFFLINE, ConnectivityState.DISCONNECTING -> ZeroTraceRed
    }
    val shortLabel = when (connectivityState) {
        ConnectivityState.E2EE_SESSION_READY -> "E2EE LIVE"
        ConnectivityState.ONION_SERVICE_READY -> "ONLINE"
        ConnectivityState.TOR_READY -> "TOR READY"
        ConnectivityState.TOR_BOOTSTRAPPING -> "TOR $torProgress%"
        ConnectivityState.AUTHENTICATING_PEER -> "PQXDH..."
        ConnectivityState.OFFLINE -> "OFFLINE"
        ConnectivityState.ERROR -> "OFFLINE"
        else -> connectivityState.code.take(10)
    }

    Surface(
        color = if (isLight) badgeColor else badgeColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = if (isLight) null else BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (isLight) Color.White else badgeColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                color = if (isLight) Color.White else badgeColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
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
    statusSegmentCount: Int = 0,
    unseenStatusSegmentCount: Int = 0,
    avatarBytes: ByteArray? = null,
    size: Dp = 50.dp,
    modifier: Modifier = Modifier
) {
    val avatarColors = listOf(
        Color(0xFF00A884),
        Color(0xFF0284C7),
        Color(0xFF6D28D9),
        Color(0xFFD97706),
        Color(0xFF0F766E),
        Color(0xFF2563EB)
    )
    val colorIndex = kotlin.math.abs(onionAddress.hashCode()) % avatarColors.size
    val baseColor = avatarColors[colorIndex]
    val initials = alias.trim().split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifEmpty { "ZT" }

    val showSegmentedRing = statusSegmentCount > 0 || hasActiveStatusRing
    val totalSegments = if (statusSegmentCount > 0) statusSegmentCount else if (hasActiveStatusRing) 1 else 0
    val unseenSegments = if (statusSegmentCount > 0) unseenStatusSegmentCount.coerceIn(0, totalSegments) else if (hasActiveStatusRing) 1 else 0
    val viewedRingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val innerSize = if (showSegmentedRing) (size - 7.dp).coerceAtLeast(24.dp) else size

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (showSegmentedRing && totalSegments > 0) {
            Canvas(modifier = Modifier.size(size)) {
                val strokePx = 2.4.dp.toPx()
                val inset = strokePx / 2f
                val arcSize = androidx.compose.ui.geometry.Size(
                    width = this.size.width - strokePx,
                    height = this.size.height - strokePx
                )
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                if (totalSegments == 1) {
                    drawArc(
                        color = if (unseenSegments > 0) ZeroTraceEmerald else viewedRingColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                } else {
                    val gapDeg = if (totalSegments > 8) 6f else 10f
                    val sweepDeg = ((360f - gapDeg * totalSegments) / totalSegments).coerceAtLeast(6f)
                    for (segIdx in 0 until totalSegments) {
                        val startAngle = -90f + segIdx * (sweepDeg + gapDeg) + gapDeg / 2f
                        val segColor = if (segIdx < unseenSegments) ZeroTraceEmerald else viewedRingColor
                        drawArc(
                            color = segColor,
                            startAngle = startAngle,
                            sweepAngle = sweepDeg,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        val decodedAvatar = remember(avatarBytes?.size, avatarBytes?.contentHashCode()) {
            if (avatarBytes != null && avatarBytes.isNotEmpty()) {
                try {
                    BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)?.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
        }

        Box(
            modifier = Modifier
                .size(innerSize)
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
                    width = 1.dp,
                    color = baseColor.copy(alpha = 0.45f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (decodedAvatar != null) {
                Image(
                    bitmap = decodedAvatar,
                    contentDescription = "$alias profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = baseColor
                )
            }
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
        } else if (isVerified && !showSegmentedRing) {
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
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Surface(
        color = Color.Transparent,
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
                avatarBytes = contact.avatarBytes,
                modifier = Modifier.clickable { onProfileClick() }
            )
            Spacer(modifier = Modifier.width(12.dp))

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
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "E2EE Verified",
                            tint = if (contact.identityChangedWarning) ZeroTraceRed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeFormatter.format(Date(lastMessageTimeMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (unreadCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$lastMessagePreview • onion: ${contact.onionAddress.take(11)}…",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
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
                                    .size(15.dp)
                            )
                        }
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(15.dp)
                            )
                        }
                    }
                }
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
    playingVoiceMessageId: String? = null,
    onReplyClick: (RichConversationMessage) -> Unit,
    onReactClick: (RichConversationMessage, String) -> Unit,
    onDeleteFromRamClick: (RichConversationMessage) -> Unit,
    onDeleteForEveryoneClick: ((RichConversationMessage) -> Unit)? = null,
    onInspectInfoClick: (RichConversationMessage) -> Unit,
    onAcceptFileTransfer: ((String) -> Unit)? = null,
    onRejectFileTransfer: ((String) -> Unit)? = null,
    onMediaPreviewClick: ((RichConversationMessage) -> Unit)? = null,
    onSaveMediaClick: ((RichConversationMessage) -> Unit)? = null,
    onOpenExternallyClick: ((RichConversationMessage) -> Unit)? = null,
    onVoicePlayToggle: ((RichConversationMessage) -> Unit)? = null
) {
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    if (showDeleteDialog) {
        val canDeleteForEveryone = message.isOutgoing && !message.isDeletedPlaceholder && onDeleteForEveryoneClick != null
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete message?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = if (canDeleteForEveryone) {
                        "Delete this message only on your device or for everyone in this encrypted chat?"
                    } else {
                        "Delete this message from your device?"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (canDeleteForEveryone) {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                onDeleteForEveryoneClick?.invoke(message)
                            },
                            modifier = Modifier.testTag("dialog_delete_for_everyone_btn")
                        ) {
                            Text("Delete for everyone", color = ZeroTraceRed, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteFromRamClick(message)
                        },
                        modifier = Modifier.testTag("dialog_delete_for_me_btn")
                    ) {
                        Text("Delete for me", color = ZeroTraceRed, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    val bubbleBg = if (message.isOutgoing) {
        ZeroTracePalette.outgoingBubble()
    } else {
        ZeroTracePalette.incomingBubble()
    }
    val bubbleBorder = if (message.pqBraidAdvanced) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    } else if (message.isOutgoing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    val innerSurfaceColor = ZeroTracePalette.bubbleInnerCard()
    val emeraldTint = MaterialTheme.colorScheme.primary
    val tealTint = MaterialTheme.colorScheme.secondary

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
                shadowElevation = if (ZeroTracePalette.isLight()) 1.dp else 0.dp,
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 320.dp)
                    .combinedClickable(
                        onClick = {
                            if (!message.isDeletedPlaceholder &&
                                message.payloadKind in setOf(
                                    MessagePayloadKind.IMAGE,
                                    MessagePayloadKind.VIDEO,
                                    MessagePayloadKind.DOCUMENT,
                                    MessagePayloadKind.AUDIO_FILE
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
                    if (!message.isDeletedPlaceholder && !message.replyToPreview.isNullOrBlank()) {
                        Surface(
                            color = innerSurfaceColor,
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
                                        .height(32.dp)
                                        .background(emeraldTint, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    if (!message.replyToSenderLabel.isNullOrBlank()) {
                                        Text(
                                            text = message.replyToSenderLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = emeraldTint,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
                    }

                    // Render Media / File Transfer / Voice Note / Contact Card if present
                    if (!message.isDeletedPlaceholder) {
                        when (message.payloadKind) {
                            MessagePayloadKind.IMAGE,
                            MessagePayloadKind.VIDEO,
                            MessagePayloadKind.DOCUMENT,
                            MessagePayloadKind.AUDIO_FILE -> {
                                val transfer = message.fileTransfer
                                Surface(
                                    color = innerSurfaceColor,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, tealTint.copy(alpha = 0.28f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        val decryptedBytes = transfer?.decryptedInMemoryBytes
                                        val isUploading = transfer != null && (
                                            transfer.state == TransferLifecycleState.ENCRYPTING_AND_CHUNKING ||
                                                transfer.state == TransferLifecycleState.TRANSMITTING_OVER_TOR ||
                                                transfer.state == TransferLifecycleState.VERIFYING_INTEGRITY
                                            )
                                        val uploadProgress = transfer?.effectiveFraction() ?: 0f
                                        val uploadPercent = (uploadProgress * 100f).toInt().coerceIn(0, 100)
                                        val totalKb = ((transfer?.manifest?.totalSizeBytes ?: 0) / 1024).coerceAtLeast(1)
                                        val doneKb = (
                                            (
                                                (transfer?.bytesTransferred ?: 0L).takeIf { it > 0L }
                                                    ?: ((totalKb * 1024L) * uploadProgress).toLong()
                                                ) / 1024L
                                            ).toInt().coerceIn(0, totalKb)

                                    val imageBitmap = if (message.payloadKind == MessagePayloadKind.IMAGE && decryptedBytes != null) {
                                        remember(message.id, decryptedBytes.size) {
                                            try {
                                                BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)?.asImageBitmap()
                                            } catch (_: Throwable) {
                                                null
                                            }
                                        }
                                    } else {
                                        null
                                    }

                                    if (imageBitmap != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(190.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(enabled = !isUploading) { onMediaPreviewClick?.invoke(message) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = imageBitmap,
                                                contentDescription = transfer?.manifest?.sanitizedFileName ?: "Encrypted Image",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.matchParentSize()
                                            )
                                            if (isUploading) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(Color.Black.copy(alpha = 0.35f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(60.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.65f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            progress = { uploadProgress.coerceAtLeast(0.06f) },
                                                            color = Color.White,
                                                            trackColor = Color.White.copy(alpha = 0.25f),
                                                            strokeWidth = 3.dp,
                                                            modifier = Modifier.size(50.dp)
                                                        )
                                                        Text(
                                                            text = if (transfer?.state == TransferLifecycleState.ENCRYPTING_AND_CHUNKING) {
                                                                "ENC"
                                                            } else {
                                                                "$uploadPercent%"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            color = Color.White
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    } else if (message.payloadKind == MessagePayloadKind.VIDEO && decryptedBytes != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF0F172A))
                                                .clickable(enabled = !isUploading) { onMediaPreviewClick?.invoke(message) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isUploading) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.65f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        progress = { uploadProgress.coerceAtLeast(0.06f) },
                                                        color = Color.White,
                                                        trackColor = Color.White.copy(alpha = 0.25f),
                                                        strokeWidth = 3.dp,
                                                        modifier = Modifier.size(50.dp)
                                                    )
                                                    Text(
                                                        text = "$uploadPercent%",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color.White
                                                    )
                                                }
                                            } else {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(44.dp)
                                                            .clip(CircleShape)
                                                            .background(emeraldTint),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PlayArrow,
                                                            contentDescription = "Play Video",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(26.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Tap to Play Encrypted Video",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (message.payloadKind == MessagePayloadKind.AUDIO_FILE &&
                                            decryptedBytes != null &&
                                            onVoicePlayToggle != null
                                        ) {
                                            val isPlaying = playingVoiceMessageId == message.id
                                            IconButton(
                                                onClick = { onVoicePlayToggle(message) },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = emeraldTint,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                    contentDescription = if (isPlaying) "Stop Audio" else "Play Audio",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        } else {
                                            val icon = when (message.payloadKind) {
                                                MessagePayloadKind.IMAGE -> Icons.Default.Image
                                                MessagePayloadKind.VIDEO -> Icons.Default.Videocam
                                                MessagePayloadKind.AUDIO_FILE -> Icons.Default.AudioFile
                                                else -> Icons.Default.Description
                                            }
                                            if (isUploading) {
                                                Box(
                                                    modifier = Modifier.size(36.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(
                                                        progress = { uploadProgress.coerceAtLeast(0.06f) },
                                                        color = emeraldTint,
                                                        trackColor = emeraldTint.copy(alpha = 0.2f),
                                                        strokeWidth = 2.8.dp,
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = message.payloadKind.name,
                                                        tint = emeraldTint,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = message.payloadKind.name,
                                                    tint = tealTint,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
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
                                                    text = "${(transfer.manifest.totalSizeBytes / 1024).coerceAtLeast(1)} KiB • ${transfer.manifest.totalChunks} Chunks • SHA-256",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
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
                                                    color = MaterialTheme.colorScheme.tertiary
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
                                                                containerColor = emeraldTint,
                                                                contentColor = MaterialTheme.colorScheme.onPrimary
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
                                                val stateLabel = when (transfer.state) {
                                                    TransferLifecycleState.ENCRYPTING_AND_CHUNKING ->
                                                        "Encrypting $uploadPercent% (${transfer.chunksProcessed}/${transfer.manifest.totalChunks})"
                                                    TransferLifecycleState.TRANSMITTING_OVER_TOR ->
                                                        "Uploading $uploadPercent% (${transfer.chunksProcessed}/${transfer.manifest.totalChunks})"
                                                    TransferLifecycleState.VERIFYING_INTEGRITY ->
                                                        "Verifying integrity…"
                                                    else -> transfer.state.name
                                                }
                                                LinearProgressIndicator(
                                                    progress = { uploadProgress.coerceAtLeast(0.05f) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp)),
                                                    color = emeraldTint,
                                                    trackColor = emeraldTint.copy(alpha = 0.22f)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = stateLabel,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        color = tealTint
                                                    )
                                                    Text(
                                                        text = "$doneKb / $totalKb KB",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            TransferLifecycleState.VERIFIED_READY_IN_RAM -> {
                                                Text(
                                                    text = "✓ ChaCha20-Poly1305 Verified",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                                    color = emeraldTint
                                                )
                                                if (decryptedBytes != null && (onMediaPreviewClick != null || onSaveMediaClick != null)) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        if (onMediaPreviewClick != null) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    if (message.payloadKind == MessagePayloadKind.DOCUMENT && onOpenExternallyClick != null) {
                                                                        onMediaPreviewClick(message)
                                                                    } else {
                                                                        onMediaPreviewClick(message)
                                                                    }
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                                modifier = Modifier.height(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Open", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                        if (onSaveMediaClick != null) {
                                                            Button(
                                                                onClick = { onSaveMediaClick(message) },
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = emeraldTint,
                                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                                ),
                                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                                modifier = Modifier
                                                                    .height(32.dp)
                                                                    .testTag("save_media_btn_${message.id}")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Download,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Save", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                    }
                                                }
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
                            val isPlaying = playingVoiceMessageId == message.id
                            val hasPlayableAudio = message.fileTransfer?.decryptedInMemoryBytes != null
                            Surface(
                                color = innerSurfaceColor,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, emeraldTint.copy(alpha = if (isPlaying) 0.8f else 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clickable(enabled = hasPlayableAudio) {
                                        onVoicePlayToggle?.invoke(message)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onVoicePlayToggle?.invoke(message) },
                                        enabled = hasPlayableAudio,
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = if (isPlaying) ZeroTraceRed else emeraldTint,
                                            contentColor = if (isPlaying) Color.White else MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Stop Voice Note" else "Play Voice Note",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = null,
                                                tint = emeraldTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "E2EE Voice Note (${message.voiceDurationSeconds ?: 1}s)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = if (isPlaying) {
                                                "Playing decrypted voice note…"
                                            } else if (hasPlayableAudio) {
                                                "Tap Play • Encrypted Voice (RAM)"
                                            } else {
                                                "Audio chunks unavailable in RAM"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                            color = if (isPlaying) tealTint else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        MessagePayloadKind.CONTACT_CARD -> {
                            Surface(
                                color = innerSurfaceColor,
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
                                        tint = tealTint
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
                    }

                    if (message.isDeletedPlaceholder) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = message.bodyText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.5.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = message.bodyText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            color = if (message.isOutgoing && ZeroTracePalette.isLight()) {
                                ZeroTracePalette.mintContent()
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = timeFormatter.format(Date(message.timestampEpochMillis)),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (message.isOutgoing) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val (tickIcon, tickTint, tickDesc) = when (message.deliveryState) {
                                MessageDeliveryState.SENDING -> Triple(
                                    Icons.Default.AccessTime,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    "Sending encrypted message"
                                )
                                MessageDeliveryState.SENT -> Triple(
                                    Icons.Default.Done,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    "Sent to Tor P2P circuit"
                                )
                                MessageDeliveryState.DELIVERED -> Triple(
                                    Icons.Default.DoneAll,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    "Delivered to peer device"
                                )
                                MessageDeliveryState.READ -> Triple(
                                    Icons.Default.DoneAll,
                                    WhatsAppReadTickBlue,
                                    "Read by peer"
                                )
                            }
                            Icon(
                                imageVector = tickIcon,
                                contentDescription = tickDesc,
                                tint = tickTint,
                                modifier = Modifier.size(15.dp)
                            )
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
                if (!message.isDeletedPlaceholder) {
                    DropdownMenuItem(
                        text = { Text("Reply") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                        onClick = {
                            actionMenuExpanded = false
                            onReplyClick(message)
                        }
                    )
                    if (message.fileTransfer?.decryptedInMemoryBytes != null) {
                        if (onMediaPreviewClick != null) {
                            DropdownMenuItem(
                                text = { Text("Open / Preview Attachment") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = {
                                    actionMenuExpanded = false
                                    onMediaPreviewClick(message)
                                }
                            )
                        }
                        if (onSaveMediaClick != null) {
                            DropdownMenuItem(
                                text = { Text("Save to Gallery / Downloads") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = emeraldTint) },
                                onClick = {
                                    actionMenuExpanded = false
                                    onSaveMediaClick(message)
                                }
                            )
                        }
                    }
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
                }
                DropdownMenuItem(
                    text = { Text("Cryptographic Message Info") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        actionMenuExpanded = false
                        onInspectInfoClick(message)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (message.isDeletedPlaceholder) "Delete for me" else "Delete…",
                            color = ZeroTraceRed
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ZeroTraceRed) },
                    onClick = {
                        actionMenuExpanded = false
                        if (message.isDeletedPlaceholder) {
                            onDeleteFromRamClick(message)
                        } else {
                            showDeleteDialog = true
                        }
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
    recordingSeconds: Int = 0,
    recordingAmplitude: Float = 0f,
    onSendClick: () -> Unit,
    onAttachmentSheetClick: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit = {},
    onQuickCameraClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (ZeroTracePalette.isLight()) 0.dp else 6.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
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
                                    text = "Replying to ${replyingToMessage.senderLabel} • Ratchet #${replyingToMessage.ratchetStepIndex}",
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
                                modifier = Modifier.size(32.dp)
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

            if (isRecordingVoiceNote) {
                val mins = recordingSeconds / 60
                val secs = recordingSeconds % 60
                val timerText = String.format(Locale.US, "%02d:%02d", mins, secs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onCancelVoiceRecording,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = ZeroTraceRed.copy(alpha = 0.18f),
                            contentColor = ZeroTraceRed
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cancel_voice_recording_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Discard Voice Recording"
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, ZeroTraceRed.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(ZeroTraceRed)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "REC • $timerText",
                                        style = MonospaceStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                        color = ZeroTraceRed
                                    )
                                }
                                Text(
                                    text = "RAM PCM • Zero Disk",
                                    style = MonospaceStyle.copy(fontSize = 10.sp),
                                    color = ZeroTraceEmerald
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { recordingAmplitude.coerceIn(0.08f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onToggleVoiceRecording,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Finish & Send Voice Note"
                        )
                    }
                }
            } else {
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
                                text = if (canSend) {
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
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = ZeroTracePalette.bubbleInnerCard(),
                            unfocusedContainerColor = ZeroTracePalette.bubbleInnerCard()
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (draftText.isNotBlank()) {
                        IconButton(
                            onClick = onSendClick,
                            enabled = canSend,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
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
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("voice_note_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record Encrypted Voice Note"
                            )
                        }
                    }
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = if (ZeroTracePalette.isLight()) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("attachment_picker_sheet")
    ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    color = ZeroTracePalette.mintContainer(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = ZeroTracePalette.mintContent(),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Files are ChaCha20-Poly1305 encrypted before sharing • EXIF stripped",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZeroTracePalette.mintContent()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Share File",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "E2E Encrypted Transfer • TOR v3 • Max 16 MiB",
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
                        tint = MaterialTheme.colorScheme.primary,
                        tag = "attach_photo_btn",
                        onClick = onPickPhoto
                    )
                    AttachmentOptionButton(
                        icon = Icons.Default.Videocam,
                        label = "Videos",
                        tint = MaterialTheme.colorScheme.secondary,
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
                        tint = MaterialTheme.colorScheme.tertiary,
                        tag = "attach_audio_btn",
                        onClick = onPickAudio
                    )
                    AttachmentOptionButton(
                        icon = Icons.Default.ContactPage,
                        label = "Peer Card",
                        tint = Color(0xFF0284C7),
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
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
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
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val statusKindLabel = when (statusItem.mediaType) {
        StatusMediaType.IMAGE_CARD -> "PHOTO"
        StatusMediaType.VIDEO_CARD -> "VIDEO"
        StatusMediaType.TEXT_CARD -> "TEXT"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp)
                .testTag("status_card_${statusItem.statusId}"),
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
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = ZeroTracePalette.mintContainer(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "$statusKindLabel • ${statusItem.remainingHours()}h",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                            color = ZeroTracePalette.mintContent(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${timeFormatter.format(Date(statusItem.createdAtEpochMillis))} • ${statusItem.captionText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onDeleteClick != null) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Wipe Status",
                        tint = ZeroTraceRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 60.dp)
        )
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
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
                color = MaterialTheme.colorScheme.secondary
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
