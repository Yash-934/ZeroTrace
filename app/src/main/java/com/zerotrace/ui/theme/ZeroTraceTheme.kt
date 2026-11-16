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

val ObsidianBg = Color(0xFF070B0E)
val CharcoalSurface = Color(0xFF0E151B)
val ElevatedCarbon = Color(0xFF162029)
val OutgoingBubbleDark = Color(0xFF063B27)
val IncomingBubbleDark = Color(0xFF18232C)
val BorderSlate = Color(0xFF22303C)
val EmeraldPrimary = Color(0xFF00E676)
val EmeraldDim = Color(0xFF00A854)
val EmeraldContainer = Color(0xFF07291A)
val CyanAccent = Color(0xFF18FFFF)
val AmberWarning = Color(0xFFFFB300)
val CrimsonDanger = Color(0xFFFF5252)
val TextPrimary = Color(0xFFECEFF4)
val TextSecondary = Color(0xFF94A3B8)

// Aliases used by ZeroTraceComponents
val ZeroTraceEmerald = EmeraldPrimary
val ZeroTraceCyan = CyanAccent
val ZeroTraceAmber = AmberWarning
val ZeroTraceRed = CrimsonDanger
val ZeroTraceOutgoingBubble = OutgoingBubbleDark
val ZeroTraceSurfaceElevated = IncomingBubbleDark

enum class AppThemeMode(val labelEn: String, val labelHi: String) {
    LIGHT("Light (Default)", "लाइट (डिफ़ॉल्ट)"),
    DARK("Dark (Obsidian)", "डार्क (ऑब्सीडियन)"),
    SYSTEM("System Default", "सिस्टम डिफ़ॉल्ट");

    val label: String get() = labelEn
}

enum class AccentPalette(val labelEn: String, val primaryColor: Color, val containerColor: Color) {
    EMERALD("ZeroTrace Emerald", Color(0xFF00E676), Color(0xFF07291A)),
    CYBER_TEAL("Quantum Teal", Color(0xFF00BFA5), Color(0xFF052925)),
    NEON_CYAN("Onion Cyan", Color(0xFF00E5FF), Color(0xFF05262E));

    val label: String get() = labelEn
}

enum class ChatWallpaperStyle(val labelEn: String, val bgColorDark: Color, val bgColorLight: Color) {
    OBSIDIAN_GRID("Clean Slate", Color(0xFF070B0E), Color(0xFFF5F7F9)),
    DEEP_EMERALD_MESH("Encrypted Forest", Color(0xFF05130D), Color(0xFFECFDF5)),
    PURE_BLACK_AMOLED("Pure Contrast", Color(0xFF000000), Color(0xFFFFFFFF));

    val label: String get() = labelEn
}

object ZeroTracePalette {
    @Composable
    fun isLight(): Boolean = MaterialTheme.colorScheme.background.red > 0.5f &&
        MaterialTheme.colorScheme.background.green > 0.5f &&
        MaterialTheme.colorScheme.background.blue > 0.5f

    @Composable
    fun outgoingBubble(): Color = if (isLight()) Color(0xFFC2F8D0) else OutgoingBubbleDark

    @Composable
    fun incomingBubble(): Color = if (isLight()) Color.White else IncomingBubbleDark

    @Composable
    fun bubbleInnerCard(): Color = if (isLight()) Color(0xFFF3F5F7) else Color.Black.copy(alpha = 0.25f)

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

val ZeroTraceTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 14.sp
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
        AccentPalette.EMERALD -> Color(0xFF00C15B)
        AccentPalette.CYBER_TEAL -> Color(0xFF00897B)
        AccentPalette.NEON_CYAN -> Color(0xFF0284C7)
    }

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = accentPalette.primaryColor,
            onPrimary = Color(0xFF03180D),
            primaryContainer = accentPalette.containerColor,
            onPrimaryContainer = Color(0xFF99F6C6),
            secondary = CyanAccent,
            onSecondary = Color(0xFF002022),
            secondaryContainer = Color(0xFF0D2B33),
            onSecondaryContainer = Color(0xFF97F0FF),
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
            onError = Color(0xFF280003)
        )
    } else {
        lightColorScheme(
            primary = lightPrimary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC2F8D0),
            onPrimaryContainer = Color(0xFF052E16),
            secondary = Color(0xFF0F766E),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFCCFBF1),
            onSecondaryContainer = Color(0xFF134E4A),
            tertiary = Color(0xFFB45309),
            onTertiary = Color.White,
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF111827),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111827),
            surfaceVariant = Color(0xFFEBEFF3),
            onSurfaceVariant = Color(0xFF6B7280),
            outline = Color(0xFFE5E7EB),
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
