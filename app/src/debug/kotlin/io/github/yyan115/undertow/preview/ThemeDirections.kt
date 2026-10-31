package io.github.yyan115.undertow.preview

import android.graphics.Typeface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design candidates for review. These are not part of a release build. */
data class ThemeDirection(
    val id: String,
    val name: String,
    val light: Boolean,
    val colors: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
)

private fun color(hex: Long) = Color(hex or 0xFF000000)

private fun palette(
    light: Boolean,
    background: Long,
    text: Long,
    muted: Long,
    low: Long,
    container: Long,
    high: Long,
    highest: Long,
    accent: Long,
    onAccent: Long,
    accentContainer: Long,
    onAccentContainer: Long,
    outline: Long,
    divider: Long,
): ColorScheme {
    val base = if (light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = color(accent), onPrimary = color(onAccent),
        primaryContainer = color(accentContainer), onPrimaryContainer = color(onAccentContainer),
        secondary = color(muted), onSecondary = color(background),
        secondaryContainer = color(highest), onSecondaryContainer = color(text),
        tertiary = color(accent), onTertiary = color(onAccent),
        tertiaryContainer = color(accentContainer), onTertiaryContainer = color(onAccentContainer),
        background = color(background), onBackground = color(text),
        surface = color(background), onSurface = color(text),
        surfaceVariant = color(high), onSurfaceVariant = color(muted),
        surfaceContainerLowest = color(background), surfaceContainerLow = color(low),
        surfaceContainer = color(container), surfaceContainerHigh = color(high),
        surfaceContainerHighest = color(highest),
        outline = color(outline), outlineVariant = color(divider), surfaceTint = color(accent),
        inverseSurface = color(text), inverseOnSurface = color(background), inversePrimary = color(accentContainer),
        error = color(if (light) 0xAD2424 else 0xFFB4AB),
        onError = color(if (light) 0xFFFFFF else 0x690005),
        errorContainer = color(if (light) 0xFFDAD6 else 0x5A2421),
        onErrorContainer = color(if (light) 0x410002 else 0xFFDAD6),
    )
}

private fun type(display: FontFamily, weight: FontWeight, tracking: Float = 0f): Typography {
    val body = FontFamily.SansSerif
    fun heading(size: Int, line: Int, value: FontWeight = weight) = TextStyle(
        fontFamily = display, fontWeight = value, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)
    fun text(size: Int, line: Int, value: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = body, fontWeight = value, fontSize = size.sp, lineHeight = line.sp)
    return Typography(
        displayLarge = heading(54, 60), displayMedium = heading(44, 50), displaySmall = heading(36, 42),
        headlineLarge = heading(34, 40), headlineMedium = heading(32, 38), headlineSmall = heading(28, 34),
        titleLarge = heading(23, 29), titleMedium = text(16, 23, FontWeight.SemiBold),
        titleSmall = text(14, 20, FontWeight.SemiBold),
        bodyLarge = text(16, 24), bodyMedium = text(14, 21), bodySmall = text(12, 18),
        labelLarge = text(14, 20, FontWeight.SemiBold), labelMedium = text(12, 16, FontWeight.Medium),
        labelSmall = text(11, 15, FontWeight.Medium),
    )
}

private fun corners(small: Int, medium: Int, large: Int) = Shapes(
    extraSmall = RoundedCornerShape((small / 2).dp), small = RoundedCornerShape(small.dp),
    medium = RoundedCornerShape(medium.dp), large = RoundedCornerShape(large.dp), extraLarge = RoundedCornerShape(large.dp),
)

val themeDirections = listOf(
    ThemeDirection("studio", "Studio", false,
        palette(false, 0x101010, 0xF4F2ED, 0xB2B0AB, 0x151515, 0x1B1B1B, 0x222222, 0x303030,
            0xF4F2ED, 0x171717, 0x383838, 0xFFFFFF, 0x8F8D88, 0x383838),
        type(FontFamily.SansSerif, FontWeight.Black, -.8f), corners(2, 4, 6)),
    ThemeDirection("cobalt", "Cobalt", false,
        palette(false, 0x0C1224, 0xF0F3FF, 0xAEBBD8, 0x111A32, 0x15203C, 0x1C2948, 0x263757,
            0x6485FF, 0x07143B, 0x294BB7, 0xF7F9FF, 0x8191B5, 0x344363),
        type(FontFamily.SansSerif, FontWeight.Bold, -.6f), corners(14, 18, 24)),
    ThemeDirection("paper", "Paper", true,
        palette(true, 0xF7F1E7, 0x292321, 0x6A5B52, 0xF1EADD, 0xEEE4D7, 0xE9DDCE, 0xDED0BE,
            0x852D42, 0xFFFFFF, 0xEAD1D5, 0x4E1726, 0x8D7D70, 0xCEC0AF),
        type(FontFamily.Serif, FontWeight.Normal, -.6f), corners(3, 6, 10)),
    ThemeDirection("copper", "Copper", false,
        palette(false, 0x1B1613, 0xF5EBDE, 0xC4B09B, 0x211B17, 0x29211C, 0x342820, 0x453326,
            0xF4B183, 0x42230F, 0x6C3D24, 0xFFE8D6, 0xA38B77, 0x554232),
        type(FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)), FontWeight.Bold, -.3f), corners(6, 10, 14)),
)
