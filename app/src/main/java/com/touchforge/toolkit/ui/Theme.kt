package com.touchforge.toolkit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF00BFA5)
private val TealDim = Color(0xFF00695C)
private val Ink = Color(0xFF0E1116)
private val SurfaceDark = Color(0xFF151A21)

private val DarkScheme = darkColorScheme(
    primary = Teal,
    secondary = Color(0xFF7C8DA6),
    background = Ink,
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF1C232D),
    primaryContainer = TealDim,
)

private val LightScheme = lightColorScheme(
    primary = TealDim,
    secondary = Color(0xFF546E7A),
)

@Composable
fun TouchForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
