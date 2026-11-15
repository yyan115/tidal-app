package io.github.yyan115.opentune.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yyan115.opentune.R

private fun inter() = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold))
private fun serif() = FontFamily(Font(R.font.instrument_serif_regular), Font(R.font.instrument_serif_italic, style = FontStyle.Italic))
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun grotesk() = FontFamily(*listOf(400 to FontWeight.Normal, 500 to FontWeight.Medium, 600 to FontWeight.SemiBold, 700 to FontWeight.Bold).map { (value, weight) ->
    Font(R.font.space_grotesk, weight, variationSettings = FontVariation.Settings(FontVariation.weight(value)))
}.toTypedArray())
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun mono() = FontFamily(*listOf(400 to FontWeight.Normal, 500 to FontWeight.Medium).map { (value, weight) ->
    Font(R.font.jetbrains_mono, weight, variationSettings = FontVariation.Settings(FontVariation.weight(value)))
}.toTypedArray())

/** The typefaces of a theme. Large titles use [display] at [displayScale] of the size that the screens ask for. */
class ThemeFonts(val body: FontFamily, val display: FontFamily, val mono: FontFamily, val displayWeight: FontWeight, val displayScale: Float, val displayTracking: TextUnit = 0.sp)

private val Mono by lazy { mono() }
private val Inter by lazy { inter() }
private val Serif by lazy { serif() }
private val Grotesk by lazy { grotesk() }

/** Clean sans-serif titles, the default for most themes. */
val SansFonts by lazy { ThemeFonts(Inter, Inter, Mono, FontWeight.SemiBold, .68f, (-0.5).sp) }
/** Editorial serif titles for the themes that are meant to feel like print. */
val SerifFonts by lazy { ThemeFonts(Inter, Serif, Mono, FontWeight.Normal, 1f) }
/** Geometric grotesque titles for the bold themes. */
val GroteskFonts by lazy { ThemeFonts(Grotesk, Grotesk, Mono, FontWeight.Bold, .7f, (-0.8).sp) }

val LocalThemeFonts = staticCompositionLocalOf { SansFonts }

/** The typefaces of the active theme. Use these instead of naming a typeface, so that every theme looks as designed. */
object AppFonts {
    val body: FontFamily @Composable @ReadOnlyComposable get() = LocalThemeFonts.current.body
    val display: FontFamily @Composable @ReadOnlyComposable get() = LocalThemeFonts.current.display
    val mono: FontFamily @Composable @ReadOnlyComposable get() = LocalThemeFonts.current.mono

    /** The style of a large title. [size] and [line] are the sizes that the serif design uses. The sans-serif themes scale them down. */
    @Composable @ReadOnlyComposable
    fun title(size: Int, line: Int): TextStyle = LocalThemeFonts.current.let {
        TextStyle(fontFamily = it.display, fontWeight = it.displayWeight, fontSize = (size * it.displayScale).sp, lineHeight = (line * it.displayScale).sp, letterSpacing = it.displayTracking)
    }
}

/**
 * One complete look: colours, corner radius and typefaces. [dark] says whether the colours are light on dark,
 * which the system bars and the window of the player follow.
 */
class AppTheme(val id: String, @StringRes val nameRes: Int, val dark: Boolean, val scheme: ColorScheme, val radius: Int, val fonts: ThemeFonts) {
    val shapes get() = Shapes(extraSmall = RoundedCornerShape((radius / 2).dp), small = RoundedCornerShape(radius.dp),
        medium = RoundedCornerShape((radius + 4).dp), large = RoundedCornerShape((radius + 12).dp), extraLarge = RoundedCornerShape((radius + 16).dp))
}

private fun c(hex: Long) = Color(hex or 0xFF000000)

/**
 * Builds a scheme from a few colours. Surfaces rise from the background toward the text colour.
 * [tint] and [tintAmount] give the surfaces a hint of colour, and [outline] sets the colour of dividers and borders.
 */
private fun scheme(dark: Boolean, background: Long, text: Long, secondary: Long, accent: Long, onAccent: Long, tertiary: Long = accent, tint: Long? = null, tintAmount: Float = 0f, outline: Long? = null): ColorScheme {
    val bg = c(background); val fg = c(text); val primary = c(accent)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val floor = if (tint != null) lerp(bg, c(tint), tintAmount) else bg
    fun tone(amount: Float) = lerp(floor, fg, amount)
    val line = outline?.let(::c)
    return base.copy(
        primary = primary, onPrimary = c(onAccent), primaryContainer = lerp(bg, primary, .2f), onPrimaryContainer = primary,
        secondary = c(secondary), onSecondary = bg, secondaryContainer = tone(.12f), onSecondaryContainer = fg,
        // A deleting action is red in every theme. The default of Material for a dark scheme is a pale pink that does not read as a warning.
        error = if (dark) c(0xFF6B63) else c(0xB3261E), onError = if (dark) c(0x2B0A08) else c(0xFFFFFF),
        tertiary = c(tertiary), onTertiary = c(onAccent), tertiaryContainer = lerp(bg, c(tertiary), .14f), onTertiaryContainer = c(tertiary),
        background = bg, onBackground = fg, surface = bg, onSurface = fg, surfaceVariant = tone(.09f), onSurfaceVariant = c(secondary),
        surfaceContainerLowest = bg, surfaceContainerLow = tone(.03f), surfaceContainer = tone(.05f),
        surfaceContainerHigh = tone(.08f), surfaceContainerHighest = tone(.12f),
        outline = line?.let { lerp(bg, it, .8f) } ?: tone(.5f), outlineVariant = line?.let { lerp(bg, it, .32f) } ?: tone(.18f), surfaceTint = primary,
        inverseSurface = fg, inverseOnSurface = bg, inversePrimary = primary,
    )
}

/** The themes in the order of the picker. Each one has its own colours, shape and type. */
val appThemes: List<AppTheme> = listOf(
    AppTheme("light", R.string.theme_light, false,
        scheme(false, 0xFAFAF9, 0x16181B, 0x5C6168, 0x2554F0, 0xFFFFFF), 10, SansFonts),
    AppTheme("dark", R.string.theme_dark, true,
        scheme(true, 0x0F1012, 0xEEF0F3, 0xA3A8B0, 0x8AB4FF, 0x0A1A3A, tint = 0x8AB4FF, tintAmount = .04f), 10, SansFonts),
    AppTheme("obsidian", R.string.theme_obsidian, true,
        scheme(true, 0x000000, 0xF4F4F5, 0x9C9CA3, 0xFFFFFF, 0x000000, outline = 0x8C8C96), 4, SansFonts),
    AppTheme("gold", R.string.theme_gold, true,
        scheme(true, 0x0B0A08, 0xF4EDDD, 0xB8AE96, 0xDCB766, 0x2A1E08, tint = 0xDCB766, tintAmount = .05f, outline = 0x9A8450), 3, SerifFonts),
    AppTheme("cyberpunk", R.string.theme_cyberpunk, true,
        scheme(true, 0x0C0716, 0xF6F0FF, 0xBBA8E6, 0xFF3D9A, 0x20001A, tertiary = 0x22E6FF, tint = 0xB026FF, tintAmount = .1f, outline = 0x9470FF), 2, GroteskFonts),
    AppTheme("neon", R.string.theme_neon, true,
        scheme(true, 0x050907, 0xE9FFF1, 0x8ED2A8, 0x3DFF9A, 0x002712, tertiary = 0xC8FF3D, tint = 0x3DFF9A, tintAmount = .05f, outline = 0x2CB872), 14, GroteskFonts),
    AppTheme("ocean", R.string.theme_ocean, true,
        scheme(true, 0x06131C, 0xE6F4F8, 0x8FB5C4, 0x4FD8D0, 0x022B2D, tint = 0x4FD8D0, tintAmount = .05f), 16, SansFonts),
    AppTheme("ember", R.string.theme_ember, true,
        scheme(true, 0x150C08, 0xF8EBDF, 0xC9A794, 0xFF8A4C, 0x3A1403, tint = 0xFF8A4C, tintAmount = .05f), 8, SerifFonts),
    AppTheme("forest", R.string.theme_forest, true,
        scheme(true, 0x09110B, 0xE9F2E7, 0x9FBAA2, 0x93D99A, 0x0A2A12, tint = 0x93D99A, tintAmount = .05f), 12, GroteskFonts),
    AppTheme("paper", R.string.theme_paper, false,
        scheme(false, 0xF6F0E4, 0x2A2420, 0x6B6054, 0x8A2D3F, 0xFFFFFF, outline = 0x7A5F4F), 3, SerifFonts),
    AppTheme("sky", R.string.theme_sky, false,
        scheme(false, 0xF2F7FC, 0x10202E, 0x45596B, 0x0A66D0, 0xFFFFFF, tint = 0x0A66D0, tintAmount = .03f), 16, SansFonts),
    AppTheme("rose", R.string.theme_rose, false,
        scheme(false, 0xFFF5F6, 0x2B1A1E, 0x765860, 0xC72A63, 0xFFFFFF, tertiary = 0x8B3FD1), 18, GroteskFonts),
)

/** The id that follows the device: light by day and dark by night. */
const val SYSTEM_THEME = "system"

/** The theme for an id. The id [SYSTEM_THEME] picks Light or Dark according to [systemDark]. An unknown id gives Dark. */
fun themeFor(id: String, systemDark: Boolean): AppTheme = when (id) {
    SYSTEM_THEME -> appThemes.first { it.id == if (systemDark) "dark" else "light" }
    else -> appThemes.firstOrNull { it.id == id } ?: appThemes.first { it.id == "dark" }
}

/**
 * Settings and backups made before themes were named kept a mode ("dark", "light" or "system") and a palette ("gallery", "cobalt" and so on).
 * This gives the theme that looks closest. An id that is already a theme stays as it is.
 */
fun migrateTheme(theme: String, palette: String?): String {
    if (palette == null) return if (theme == SYSTEM_THEME || appThemes.any { it.id == theme }) theme else "dark"
    return when {
        theme == SYSTEM_THEME -> SYSTEM_THEME
        theme == "light" -> if (palette == "paper") "paper" else "light"
        else -> when (palette) { "gallery" -> "gold"; "cobalt", "reference" -> "ocean"; "copper", "archive" -> "ember"; else -> "dark" }
    }
}
