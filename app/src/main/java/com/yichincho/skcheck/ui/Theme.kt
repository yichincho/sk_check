package com.yichincho.skcheck.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2348C9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E9FC),
    onPrimaryContainer = Color(0xFF16328F),
    secondaryContainer = Color(0xFFE6E9F0),
    onSecondaryContainer = Color(0xFF242A36),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF12161C),
    surface = Color(0xFFF4F5F7),
    onSurface = Color(0xFF12161C),
    surfaceVariant = Color(0xFFE9ECF0),
    onSurfaceVariant = Color(0xFF545C67),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHighest = Color(0xFFE9ECF0),
    outline = Color(0xFFB3BAC4),
    outlineVariant = Color(0xFFE1E4E9),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB4C3FF),
    onPrimary = Color(0xFF0E2272),
    primaryContainer = Color(0xFF263E9E),
    onPrimaryContainer = Color(0xFFE3E9FC),
    secondaryContainer = Color(0xFF2A2F3C),
    onSecondaryContainer = Color(0xFFDDE1EA),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFFA9AFBC),
    surfaceContainer = Color(0xFF1A1E27),
    surfaceContainerHigh = Color(0xFF1A1E27),
    surfaceContainerLow = Color(0xFF1A1E27),
    surfaceContainerHighest = Color(0xFF262B36),
    outline = Color(0xFF4C5262),
    outlineVariant = Color(0xFF2C303C),
    error = Color(0xFFF2B8B5),
)

/** Taiwan convention: up is red, down is green. */
data class MarketColors(val up: Color, val down: Color, val alertBg: Color, val alertFg: Color, val warnBg: Color, val warnFg: Color, val okBg: Color, val okFg: Color)

private val LightMarket = MarketColors(Color(0xFFB3122A), Color(0xFF0B6B37), Color(0xFFFFEDEA), Color(0xFFA3122A), Color(0xFFFFF3C4), Color(0xFF7A5900), Color(0xFFE3F4E8), Color(0xFF0B6B37))
private val DarkMarket = MarketColors(Color(0xFFFF8A8A), Color(0xFF6FD39A), Color(0xFF3F1C1E), Color(0xFFFFB4AB), Color(0xFF3D3410), Color(0xFFF5D76E), Color(0xFF14301F), Color(0xFF6FD39A))

val LocalMarketColors = staticCompositionLocalOf { LightMarket }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalMarketColors provides if (dark) DarkMarket else LightMarket) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
