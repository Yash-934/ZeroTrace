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
    DARK("Dark (Obsidian Default)", "डार्क (डिफ़ॉल्ट)"),
    LIGHT("Light", "लाइट"),
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
    OBSIDIAN_GRID("Obsidian Carbon", Color(0xFF070B0E), Color(0xFFECE5DD)),
    DEEP_EMERALD_MESH("Encrypted Forest", Color(0xFF05130D), Color(0xFFE1F3E8)),
    PURE_BLACK_AMOLED("Pure Pitch Black", Color(0xFF000000), Color(0xFFF5F7FA));

    val label: String get() = labelEn
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
    themeMode: AppThemeMode = AppThemeMode.DARK,
    accentPalette: AccentPalette = AccentPalette.EMERALD,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
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
            primary = Color(0xFF008048),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD2F8E5),
            onPrimaryContainer = Color(0xFF002110),
            secondary = Color(0xFF006874),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF9EEFFD),
            onSecondaryContainer = Color(0xFF001F24),
            tertiary = Color(0xFFB26A00),
            onTertiary = Color.White,
            background = Color(0xFFF4F7F6),
            onBackground = Color(0xFF0F171E),
            surface = Color.White,
            onSurface = Color(0xFF0F171E),
            surfaceVariant = Color(0xFFE7ECEA),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            error = Color(0xFFD32F2F),
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZeroTraceTypography,
        content = content
    )
}
