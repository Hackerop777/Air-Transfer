package com.airtransfer.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AirTransferLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

private val StitchDarkColorScheme = darkColorScheme(
    primary = StitchPrimary,
    onPrimary = StitchOnPrimary,
    primaryContainer = StitchPrimaryContainer,
    onPrimaryContainer = StitchOnPrimaryContainer,
    secondary = StitchSecondary,
    onSecondary = StitchSurface,
    secondaryContainer = StitchSecondaryContainer,
    tertiary = StitchTertiary,
    onTertiary = StitchSurface,
    tertiaryContainer = StitchTertiaryContainer,
    background = StitchSurface,
    surface = StitchSurfaceContainer,
    onBackground = StitchOnSurface,
    onSurface = StitchOnSurface,
    onSurfaceVariant = StitchOnSurfaceVariant,
    outline = StitchOutline,
    outlineVariant = StitchOutlineVariant,
    error = StitchError,
    errorContainer = StitchErrorContainer
)

@Composable
fun AirTransferTheme(
    darkTheme: Boolean = false, // Clean, eye-comforting porcelain light palette
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) StitchDarkColorScheme else AirTransferLightColorScheme,
        typography = Typography,
        content = content
    )
}
