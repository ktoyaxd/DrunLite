package com.drun.lite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Exact CSS variables from css/main.css
object DrunThemeColors {
    val TgBg = Color(0xFF181818)
    val TgText = Color(0xFFFFFFFF)
    val TgTextSecondary = Color(0xFFAAAAAA)
    val TgOutline = Color(0xFF505050)
    val TgOutlineFocus = Color(0xFF82AED5)
    val TgBtnBg = Color(0xFF229AF0)
    val TgBtnIcon = Color(0xFFFFFFFF)
    val TgInputBg = Color(0xFF2C2C2C)
    val HeaderBg = Color(0xFF212121)
    val NavBg = Color(0xF2212121)
    val HoverBg = Color(0xFF2B2B2B)
    val BubbleIn = Color(0xFF2B2B2B)
    val BubbleOut = Color(0xFF229AF0)
    val DividerColor = Color(0xFF303030)
    val SettingsItemBorder = Color(0xFF2C2C2C)
    val ErrorRed = Color(0xFFFF5252)
    val Gold = Color(0xFFFFD700)
}

private val DarkColors = darkColorScheme(
    primary = DrunThemeColors.TgBtnBg,
    background = DrunThemeColors.TgBg,
    surface = DrunThemeColors.HeaderBg,
    onPrimary = Color.White,
    onBackground = DrunThemeColors.TgText,
    onSurface = DrunThemeColors.TgText
)

@Composable
fun DrunLiteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
