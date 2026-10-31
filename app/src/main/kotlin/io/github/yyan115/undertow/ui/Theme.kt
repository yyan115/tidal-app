package io.github.yyan115.undertow.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun UndertowTheme(theme: String, paletteId: String = "studio", content: @Composable () -> Unit) {
    val isDark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val palette = colorTheme(paletteId)
    MaterialTheme(colorScheme = if (isDark) palette.dark else palette.light,
        typography = interfaceTypography(palette.serif), shapes = palette.shapes,
        content = content)
}

fun interfaceTypography(serif: Boolean = false): Typography {
    val base = Typography()
    val display = if (serif) EditorialFont else InterfaceFont
    val weight = if (serif) FontWeight.Normal else FontWeight.SemiBold
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = display, fontWeight = weight),
        displayMedium = base.displayMedium.copy(fontFamily = display, fontWeight = weight),
        displaySmall = base.displaySmall.copy(fontFamily = display, fontWeight = weight),
        headlineLarge = base.headlineLarge.copy(fontFamily = display, fontWeight = weight),
        headlineMedium = base.headlineMedium.copy(fontFamily = display, fontWeight = weight),
        headlineSmall = base.headlineSmall.copy(fontFamily = display, fontWeight = weight),
        titleLarge = base.titleLarge.copy(fontFamily = display, fontWeight = weight),
        titleMedium = base.titleMedium.copy(fontFamily = InterfaceFont), titleSmall = base.titleSmall.copy(fontFamily = InterfaceFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = InterfaceFont), bodyMedium = base.bodyMedium.copy(fontFamily = InterfaceFont),
        bodySmall = base.bodySmall.copy(fontFamily = InterfaceFont), labelLarge = base.labelLarge.copy(fontFamily = InterfaceFont),
        labelMedium = base.labelMedium.copy(fontFamily = InterfaceFont), labelSmall = base.labelSmall.copy(fontFamily = InterfaceFont),
    )
}
