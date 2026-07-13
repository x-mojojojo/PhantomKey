package com.phantomkey.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deep night palette with cyan / violet accents — "phantom" aesthetic.
val PhantomCyan = Color(0xFF5CE1E6)
val PhantomViolet = Color(0xFF7C5CFF)
val PhantomBg = Color(0xFF0B1020)
val PhantomSurface = Color(0xFF141A2E)
val PhantomSurfaceVariant = Color(0xFF1C243D)
val PhantomOnBg = Color(0xFFE8ECF8)
val PhantomMuted = Color(0xFF9AA3C0)
val PhantomDanger = Color(0xFFFF6B8A)
val PhantomSuccess = Color(0xFF5CFFB1)

private val DarkColors = darkColorScheme(
    primary = PhantomCyan,
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = PhantomCyan,
    secondary = PhantomViolet,
    onSecondary = Color(0xFF1A0A4A),
    secondaryContainer = Color(0xFF3A2A7A),
    onSecondaryContainer = Color(0xFFE0D6FF),
    tertiary = PhantomSuccess,
    background = PhantomBg,
    onBackground = PhantomOnBg,
    surface = PhantomSurface,
    onSurface = PhantomOnBg,
    surfaceVariant = PhantomSurfaceVariant,
    onSurfaceVariant = PhantomMuted,
    outline = Color(0xFF3A4460),
    error = PhantomDanger,
    onError = Color(0xFF3B0014),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A70),
    onPrimary = Color.White,
    secondary = Color(0xFF5B3FD4),
    onSecondary = Color.White,
    background = Color(0xFFF4F6FC),
    onBackground = Color(0xFF101428),
    surface = Color.White,
    onSurface = Color(0xFF101428),
    surfaceVariant = Color(0xFFE4E8F4),
    onSurfaceVariant = Color(0xFF4A5268),
    error = Color(0xFFB00040),
)

@Composable
fun PhantomKeyTheme(
    darkTheme: Boolean = true, // default dark for the "phantom" look
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme || !isSystemInDarkTheme()) {
        // Prefer dark always for brand consistency; still honour explicit light if wanted later.
        DarkColors
    } else {
        LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = PhantomTypography,
        content = content,
    )
}
