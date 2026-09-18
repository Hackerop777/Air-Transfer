package com.airtransfer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    darkTheme: Boolean = true, // Default to sleek Stitch dark aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StitchDarkColorScheme,
        typography = Typography,
        content = content
    )
}
