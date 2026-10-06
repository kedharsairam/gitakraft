package com.gitakraft.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kraft.ui.tokens.KraftRadius
import com.kraft.ui.tokens.KraftTypeScale

// -- Brand --
val Saffron = Color(0xFFFF9F0A)

// Dark: saffron fill with near-black label text (≈9:1).
val InkOnSaffron = Color(0xFF1A1207)

// -- Dark: near-black, warm gray (the only theme) --
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceHighDark = Color(0xFF2E2E33)
val TextSecondaryDark = Color(0xFFB8B0A4)
val TakeawayTintDark = Color(0xFF2A2118)

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
    small = RoundedCornerShape(KraftRadius.Small),
    medium = RoundedCornerShape(KraftRadius.Standard),
    large = RoundedCornerShape(KraftRadius.Medium),
    extraLarge = RoundedCornerShape(KraftRadius.Hero),
)

val GitaTypography = Typography(
    displayLarge = TextStyle(fontSize = KraftTypeScale.LargeTitle, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = KraftTypeScale.Title2, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = KraftTypeScale.Title3, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = KraftTypeScale.Callout, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = KraftTypeScale.Body, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = KraftTypeScale.Subheadline, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = KraftTypeScale.Caption1, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

/** Devanagari verse: large with liturgical air. */
val VerseSanskrit = TextStyle(fontSize = KraftTypeScale.Title1, fontWeight = FontWeight.Medium, lineHeight = 42.sp)

/** IAST transliteration: quiet companion line. */
val VerseIast = TextStyle(fontSize = KraftTypeScale.Subheadline, fontWeight = FontWeight.Normal, lineHeight = 22.sp)

@Composable
fun GitaKraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = GitaTypography,
        shapes = GitaShapes,
        content = content,
    )
}
