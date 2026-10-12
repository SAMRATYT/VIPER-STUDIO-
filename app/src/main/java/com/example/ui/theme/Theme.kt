package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ViperDarkColorScheme = darkColorScheme(
    primary = ViperGreen,
    onPrimary = Color(0xFF051209),
    primaryContainer = Color(0xFF00391A),
    onPrimaryContainer = ViperGreenLight,
    secondary = ViperCyan,
    onSecondary = Color(0xFF00232A),
    secondaryContainer = Color(0xFF004D5C),
    onSecondaryContainer = Color(0xFFB5F5FF),
    tertiary = ViperAmber,
    onTertiary = Color(0xFF261000),
    background = ViperBackground,
    onBackground = TextPrimary,
    surface = ViperSurface,
    onSurface = TextPrimary,
    surfaceVariant = ViperSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ViperBorder,
    outlineVariant = ViperBorderSubtle,
    error = ViperCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to Dark Creator Studio Mode
    dynamicColor: Boolean = false, // Keep high-contrast VIPER theme consistent
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ViperDarkColorScheme,
        typography = Typography,
        content = content
    )
}
