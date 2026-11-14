package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.data.model.ThemeMode

private val DarkDeskScheme = darkColorScheme(
    primary = BrassAmber,
    onPrimary = DarkDeskBackground,
    primaryContainer = BrassAmberDim,
    onPrimaryContainer = DarkDeskOnSurface,
    secondary = KeycapLight,
    onSecondary = DarkDeskBackground,
    background = DarkDeskBackground,
    onBackground = DarkDeskOnSurface,
    surface = DarkDeskSurface,
    onSurface = DarkDeskOnSurface,
    surfaceVariant = DarkDeskSurfaceElevated,
    onSurfaceVariant = DarkDeskOnSurfaceMuted,
    outline = DarkDeskOutline
)

private val LightStudioScheme = lightColorScheme(
    primary = LightAccentInk,
    onPrimary = PaperCrisp,
    primaryContainer = ColorTokens.WarmSandContainer,
    onPrimaryContainer = LightStudioOnSurface,
    secondary = PaperInk,
    onSecondary = PaperCrisp,
    background = LightStudioBackground,
    onBackground = LightStudioOnSurface,
    surface = LightStudioSurface,
    onSurface = LightStudioOnSurface,
    surfaceVariant = LightStudioSurfaceElevated,
    onSurfaceVariant = LightStudioOnSurfaceMuted,
    outline = LightStudioOutline
)

private object ColorTokens {
    val WarmSandContainer = androidx.compose.ui.graphics.Color(0xFFE0D2BC)
}

@Composable
fun KeystrokeTheme(
    themeMode: ThemeMode = ThemeMode.DARK_DESK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.DARK_DESK -> DarkDeskScheme
        ThemeMode.LIGHT_STUDIO -> LightStudioScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
