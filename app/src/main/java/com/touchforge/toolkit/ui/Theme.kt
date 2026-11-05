package com.touchforge.toolkit.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object CyberColors {
    val Background = Color(0xFF080C14)
    val Surface = Color(0xFF0F1726)
    val SurfaceVariant = Color(0xFF162134)
    val Border = Color(0xFF1F2E45)
    val BorderGlow = Color(0x3300E5FF)

    val Cyan = Color(0xFF00E5FF)
    val Teal = Color(0xFF00BFA5)
    val Violet = Color(0xFF7C4DFF)
    val Amber = Color(0xFFFF9100)
    val Red = Color(0xFFFF3D71)
    val Green = Color(0xFF00E676)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF64748B)
}

private val DarkScheme = darkColorScheme(
    primary = CyberColors.Cyan,
    onPrimary = Color(0xFF04101A),
    primaryContainer = Color(0xFF063345),
    onPrimaryContainer = CyberColors.Cyan,
    secondary = CyberColors.Teal,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = CyberColors.Teal,
    tertiary = CyberColors.Violet,
    background = CyberColors.Background,
    onBackground = CyberColors.TextPrimary,
    surface = CyberColors.Surface,
    onSurface = CyberColors.TextPrimary,
    surfaceVariant = CyberColors.SurfaceVariant,
    onSurfaceVariant = CyberColors.TextSecondary,
    outline = CyberColors.Border,
    error = CyberColors.Red,
    onError = Color.White,
)

val CyberShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun TouchForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        shapes = CyberShapes,
        content = content,
    )
}
