package com.zerotrace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val ObsidianBg = Color(0xFF0B141A)
val CharcoalSurface = Color(0xFF111B21)
val ElevatedCarbon = Color(0xFF202C33)
val OutgoingBubbleDark = Color(0xFF005C4B)
val IncomingBubbleDark = Color(0xFF202C33)
val BorderSlate = Color(0xFF222E35)
val EmeraldPrimary = Color(0xFF00A884)
val EmeraldDim = Color(0xFF008069)
val EmeraldContainer = Color(0xFF005C4B)
val CyanAccent = Color(0xFF008069)
val AmberWarning = Color(0xFFD97706)
val CrimsonDanger = Color(0xFFEF4444)
val TextPrimary = Color(0xFFE9EDEF)
val TextSecondary = Color(0xFF8696A0)

// Aliases used by ZeroTraceComponents
val ZeroTraceEmerald = Color(0xFF1DAA61)
val ZeroTraceCyan = CyanAccent
val ZeroTraceAmber = AmberWarning
val ZeroTraceRed = CrimsonDanger
val ZeroTraceOutgoingBubble = OutgoingBubbleDark
val ZeroTraceSurfaceElevated = IncomingBubbleDark
val WhatsAppReadTickBlue = Color(0xFF53BDEB)
val WhatsAppGreenPillBgLight = Color(0xFFD8FDD2)
val WhatsAppGreenPillTextLight = Color(0xFF0A5C36)

enum class AppThemeMode(val labelEn: String, val labelHi: String) {
    LIGHT("Light (Default)", "लाइट (डिफ़ॉल्ट)"),
    DARK("Dark (WhatsApp)", "डार्क (व्हाट्सएप)"),
    SYSTEM("System Default", "सिस्टम डिफ़ॉल्ट");

    val label: String get() = labelEn
}

enum class AccentPalette(val labelEn: String, val primaryColor: Color, val containerColor: Color) {
    EMERALD("WhatsApp Green", Color(0xFF00A884), Color(0xFF005C4B)),
    CYBER_TEAL("Classic Teal", Color(0xFF008069), Color(0xFF004D40)),
    NEON_CYAN("Ocean Blue", Color(0xFF0284C7), Color(0xFF073B59));

    val label: String get() = labelEn
}

enum class ChatWallpaperStyle(val labelEn: String, val bgColorDark: Color, val bgColorLight: Color) {
    OBSIDIAN_GRID("WhatsApp Classic", Color(0xFF0B141A), Color(0xFFEFEAE2)),
    DEEP_EMERALD_MESH("Encrypted Mint", Color(0xFF061A14), Color(0xFFE5F6EC)),
    PURE_BLACK_AMOLED("Clean Light / AMOLED", Color(0xFF000000), Color(0xFFF5F7F9));

    val label: String get() = labelEn
}

object ZeroTracePalette {
    @Composable
    fun isLight(): Boolean = MaterialTheme.colorScheme.background.red > 0.5f &&
        MaterialTheme.colorScheme.background.green > 0.5f &&
        MaterialTheme.colorScheme.background.blue > 0.5f

    @Composable
    fun outgoingBubble(): Color = if (isLight()) Color(0xFFD9FDD3) else OutgoingBubbleDark

    @Composable
    fun incomingBubble(): Color = if (isLight()) Color.White else IncomingBubbleDark

    @Composable
    fun bubbleInnerCard(): Color = if (isLight()) Color(0xFFF0F2F5) else Color(0xFF182229)

    @Composable
    fun activePillContainer(): Color = if (isLight()) WhatsAppGreenPillBgLight else OutgoingBubbleDark

    @Composable
    fun activePillContent(): Color = if (isLight()) WhatsAppGreenPillTextLight else Color(0xFFE9EDEF)

    @Composable
    fun accentEmerald(): Color = MaterialTheme.colorScheme.primary

    @Composable
    fun accentTeal(): Color = MaterialTheme.colorScheme.secondary

    @Composable
    fun accentAmber(): Color = MaterialTheme.colorScheme.tertiary

    @Composable
    fun mintContainer(): Color = MaterialTheme.colorScheme.primaryContainer

    @Composable
    fun mintContent(): Color = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    fun chatWallpaper(style: ChatWallpaperStyle): Color =
        if (isLight()) style.bgColorLight else style.bgColorDark
}

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold)
)

val MonospaceStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 16.sp
)

// Original WhatsApp clean sans-serif typography across all headers, body, subtitles, chips & timestamps
val ZeroTraceTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

@Composable
fun ZeroTraceTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    accentPalette: AccentPalette = AccentPalette.EMERALD,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val lightPrimary = when (accentPalette) {
        AccentPalette.EMERALD -> Color(0xFF1DAA61)
        AccentPalette.CYBER_TEAL -> Color(0xFF008069)
        AccentPalette.NEON_CYAN -> Color(0xFF0284C7)
    }

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = accentPalette.primaryColor,
            onPrimary = Color(0xFF062018),
            primaryContainer = accentPalette.containerColor,
            onPrimaryContainer = Color(0xFFE9EDEF),
            secondary = Color(0xFF00A884),
            onSecondary = Color(0xFF062018),
            secondaryContainer = Color(0xFF182229),
            onSecondaryContainer = Color(0xFFD1D7DB),
            tertiary = AmberWarning,
            onTertiary = Color(0xFF261900),
            background = ObsidianBg,
            onBackground = TextPrimary,
            surface = CharcoalSurface,
            onSurface = TextPrimary,
            surfaceVariant = ElevatedCarbon,
            onSurfaceVariant = TextSecondary,
            outline = BorderSlate,
            error = CrimsonDanger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = lightPrimary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9FDD3),
            onPrimaryContainer = Color(0xFF111B21),
            secondary = Color(0xFF008069),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE7FCE3),
            onSecondaryContainer = Color(0xFF0A5C36),
            tertiary = Color(0xFFB45309),
            onTertiary = Color.White,
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF111B21),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111B21),
            surfaceVariant = Color(0xFFF0F2F5),
            onSurfaceVariant = Color(0xFF667781),
            outline = Color(0xFFE9EDEF),
            error = Color(0xFFEF4444),
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZeroTraceTypography,
        content = content
    )
}
