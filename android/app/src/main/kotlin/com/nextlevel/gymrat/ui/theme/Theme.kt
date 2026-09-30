package com.nextlevel.gymrat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Brand colours shared with iOS so both apps look the same. These are the iOS system
 * colours the iOS app uses (accent/tint and status). Dynamic colour (Material You) is
 * deliberately off: it would make the Android palette depend on the user's wallpaper.
 */
object GymRatColors {
    val Accent = Color(0xFF007AFF) // iOS default tint (systemBlue)
    val StatusReady = Color(0xFF34C759) // iOS systemGreen
    val StatusNotReady = Color(0xFFFF9500) // iOS systemOrange
    val StatusError = Color(0xFFFF3B30) // iOS systemRed
}

private val LightColors = lightColorScheme(primary = GymRatColors.Accent)
private val DarkColors = darkColorScheme(primary = GymRatColors.Accent)

@Composable
fun GymRatTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
