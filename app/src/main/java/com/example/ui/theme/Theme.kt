package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF090E1A),
    primaryContainer = Color(0xFF0C355B),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = BlueLight,
    onSecondary = Color(0xFF090E1A),
    secondaryContainer = NavyCardElevated,
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = StatusReady,
    onTertiary = Color.White,
    background = NavyBackground,
    onBackground = NavyTextPrimary,
    surface = NavySurface,
    onSurface = NavyTextPrimary,
    surfaceVariant = NavyCard,
    onSurfaceVariant = NavyTextSecondary,
    outline = NavyBorder,
    outlineVariant = NavyBorderSubtle,
    error = Color(0xFFF87171),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = LightNavyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = LightNavyBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightNavySurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightNavyCard,
    onSurfaceVariant = Color(0xFF475569),
    outline = LightNavyBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun UDMRepairTheme(
    darkTheme: Boolean = true, // Default to true per request "Dark navy theme"
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
