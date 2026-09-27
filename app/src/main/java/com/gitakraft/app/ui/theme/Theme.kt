package com.gitakraft.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// -- Brand --
val Saffron = Color(0xFFFF9F0A)

// Light: deep amber fill so white label text clears 4.5:1.
val AmberDeep = Color(0xFF8A5200)

// Dark: saffron fill with near-black label text (≈9:1).
val InkOnSaffron = Color(0xFF1A1207)

// -- Light: warm paper, deep ink --
val Paper = Color(0xFFFAF7F0)
val PaperSurface = Color(0xFFFFFFFF)
val Ink = Color(0xFF1C1A16)
val InkMuted = Color(0xFF6B6257)
val TakeawayTintLight = Color(0xFFFFF3DF)

// -- Dark: near-black, warm gray --
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceHighDark = Color(0xFF2E2E33)
val TextSecondaryDark = Color(0xFFB8B0A4)
val TakeawayTintDark = Color(0xFF2A2118)

private val LightColorScheme = lightColorScheme(
    primary = AmberDeep,
    onPrimary = Color.White,
    secondary = Saffron,
    onSecondary = InkOnSaffron,
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkMuted,
    outlineVariant = Color(0xFFE2DACA),
    tertiaryContainer = TakeawayTintLight,
    onTertiaryContainer = Ink,
)

private val DarkColorScheme = darkColorScheme(
    primary = Saffron,
    onPrimary = InkOnSaffron,
    secondary = Saffron,
    onSecondary = InkOnSaffron,
    background = BackgroundDark,
    onBackground = Color(0xFFF5EFE4),
    surface = SurfaceDark,
    onSurface = Color(0xFFF5EFE4),
    surfaceVariant = SurfaceHighDark,
    onSurfaceVariant = TextSecondaryDark,
    outlineVariant = Color(0xFF2E2E33),
    tertiaryContainer = TakeawayTintDark,
    onTertiaryContainer = Color(0xFFF5EFE4),
)

val GitaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val GitaTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

/** Devanagari verse: large with liturgical air. */
val VerseSanskrit = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, lineHeight = 42.sp)

/** IAST transliteration: quiet companion line. */
val VerseIast = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)

@Composable
fun GitaKraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = GitaTypography,
        shapes = GitaShapes,
        content = content,
    )
}
