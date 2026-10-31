package io.github.yyan115.undertow.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yyan115.undertow.R

val InterfaceFont = FontFamily(
    Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val EditorialFont = FontFamily(
    Font(R.font.instrument_serif_regular), Font(R.font.instrument_serif_italic, style = FontStyle.Italic),
)

data class AppColorTheme(
    val id: String, val name: String, val description: String,
    val dark: ColorScheme, val light: ColorScheme,
    val radius: Int = 6,
) {
    val shapes get() = Shapes(extraSmall = RoundedCornerShape((radius / 2).dp), small = RoundedCornerShape(radius.dp),
        medium = RoundedCornerShape((radius + 4).dp), large = RoundedCornerShape((radius + 12).dp), extraLarge = RoundedCornerShape((radius + 16).dp))
}

private fun c(hex: Long) = Color(hex or 0xFF000000)
private fun colors(dark: Boolean, background: Long, text: Long, secondary: Long, accent: Long, onAccent: Long): ColorScheme {
    val bg = c(background); val fg = c(text); val primary = c(accent)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    fun tone(amount: Float) = lerp(bg, fg, amount)
    return base.copy(
        primary = primary, onPrimary = c(onAccent), primaryContainer = lerp(bg, primary, .18f), onPrimaryContainer = primary,
        secondary = c(secondary), onSecondary = bg, secondaryContainer = tone(.11f), onSecondaryContainer = fg,
        tertiary = primary, onTertiary = c(onAccent), tertiaryContainer = lerp(bg, primary, .12f), onTertiaryContainer = primary,
        background = bg, onBackground = fg, surface = bg, onSurface = fg, surfaceVariant = tone(.08f), onSurfaceVariant = c(secondary),
        surfaceContainerLowest = bg, surfaceContainerLow = tone(.025f), surfaceContainer = tone(.045f),
        surfaceContainerHigh = tone(.07f), surfaceContainerHighest = tone(.11f),
        outline = tone(.47f), outlineVariant = tone(.17f), surfaceTint = primary,
        inverseSurface = fg, inverseOnSurface = bg, inversePrimary = primary,
    )
}

val appColorThemes = listOf(
    AppColorTheme("studio", "Studio", "Black & white",
        colors(true, 0x101010, 0xF4F2ED, 0xB2B0AB, 0xF4F2ED, 0x171717),
        colors(false, 0xF7F7F5, 0x171717, 0x65645F, 0x292827, 0xFFFFFF), radius = 2),
    AppColorTheme("cobalt", "Cobalt", "Ink blue",
        colors(true, 0x080B17, 0xF0F3FF, 0xADB8D1, 0xB4C2E5, 0x18233E),
        colors(false, 0xF4F6FC, 0x18213B, 0x58637E, 0x3151C0, 0xFFFFFF), radius = 14),
    AppColorTheme("paper", "Paper", "Ivory & burgundy",
        colors(true, 0x21191A, 0xF4EADF, 0xC6B6AE, 0xE4A7B4, 0x431A29),
        colors(false, 0xF7F1E7, 0x292321, 0x6A5B52, 0x852D42, 0xFFFFFF), radius = 3),
    AppColorTheme("copper", "Copper", "Warm charcoal",
        colors(true, 0x1B1613, 0xF5EBDE, 0xC4B09B, 0xF4B183, 0x42230F),
        colors(false, 0xF7EEE5, 0x30251E, 0x6F5D4E, 0x8D4526, 0xFFFFFF), radius = 6),
    AppColorTheme("gallery", "Gallery", "Ink & champagne",
        colors(true, 0x07080A, 0xF1EBDD, 0xB6B0A4, 0xD5C5AA, 0x292015),
        colors(false, 0xF0ECE3, 0x25221F, 0x6A6258, 0x6C5030, 0xFFFFFF), radius = 4),
    AppColorTheme("archive", "Archive", "Oxblood & bone",
        colors(true, 0x12080D, 0xF5E9E0, 0xC5AFB5, 0xDABBB2, 0x382028),
        colors(false, 0xF5ECE6, 0x362027, 0x795A63, 0x7C304B, 0xFFFFFF), radius = 8),
    AppColorTheme("reference", "Reference", "Midnight & silver",
        colors(true, 0x070D16, 0xEAF0F0, 0xA8BAC8, 0xB5C7D6, 0x142839),
        colors(false, 0xF0F3F4, 0x16242F, 0x536573, 0x31586F, 0xFFFFFF), radius = 3),
)

fun colorTheme(id: String) = appColorThemes.firstOrNull { it.id == id } ?: appColorThemes.first { it.id == "gallery" }
