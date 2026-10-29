package io.github.yyan115.undertow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val dark = darkColorScheme(
    primary = Color(0xFFA6DDCC), onPrimary = Color(0xFF083B30), primaryContainer = Color(0xFF244D43), onPrimaryContainer = Color(0xFFBDEDDC),
    secondary = Color(0xFFB7C8CE), onSecondary = Color(0xFF223238), secondaryContainer = Color(0xFF2A3B42), onSecondaryContainer = Color(0xFFD9E8ED),
    background = Color(0xFF10171B), onBackground = Color(0xFFE4EBED), surface = Color(0xFF10171B), onSurface = Color(0xFFE4EBED),
    surfaceVariant = Color(0xFF263239), onSurfaceVariant = Color(0xFFAFBDC3), outline = Color(0xFF6D7B81),
    surfaceContainer = Color(0xFF1A252B), surfaceContainerHigh = Color(0xFF253138), surfaceContainerLow = Color(0xFF152026),
    outlineVariant = Color(0xFF3C4D54),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF5A2421), onErrorContainer = Color(0xFFFFDAD6),
)
private val light = lightColorScheme(
    primary = Color(0xFF246B59), onPrimary = Color.White, primaryContainer = Color(0xFFC8ECDF), onPrimaryContainer = Color(0xFF0B4032),
    secondary = Color(0xFF4D626B), onSecondary = Color.White, secondaryContainer = Color(0xFFDCE8EC), onSecondaryContainer = Color(0xFF243840),
    background = Color(0xFFF7F9F7), onBackground = Color(0xFF17251F), surface = Color(0xFFF7F9F7), onSurface = Color(0xFF17251F),
    surfaceVariant = Color(0xFFE3EAE5), onSurfaceVariant = Color(0xFF4F6258), outline = Color(0xFF72847A),
    surfaceContainer = Color(0xFFEDF2EE), surfaceContainerHigh = Color(0xFFE4EBE6), surfaceContainerLow = Color(0xFFF0F5F1),
    outlineVariant = Color(0xFFC2D0C7),
)

@Composable
fun UndertowTheme(theme: String, content: @Composable () -> Unit) {
    val isDark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (isDark) dark else light,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content)
}
