package com.urfeed.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D6CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF00B5A3),
    tertiary = Color(0xFF9C6BFF),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF101520),
    onSurface = Color(0xFF101520),
    error = Color(0xFFE53935)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF07111E),
    secondary = Color(0xFF5AD7CA),
    tertiary = Color(0xFFBA9AFF),
    background = Color(0xFF0D1117),
    surface = Color(0xFF121A24),
    onBackground = Color(0xFFEAF2FF),
    onSurface = Color(0xFFEAF2FF),
    error = Color(0xFFF87171)
)

@Composable
fun URFeedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
