package com.closetvr.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// La identidad "Deep Ocean" es oscura por diseño: se usa el mismo esquema en modo claro y oscuro del sistema.
private val OceanColorScheme = darkColorScheme(
    primary = OceanTurquoise,
    onPrimary = OceanAbyss,
    primaryContainer = OceanDeep,
    onPrimaryContainer = OceanFoam,
    secondary = OceanSky,
    onSecondary = OceanAbyss,
    secondaryContainer = OceanNavy,
    onSecondaryContainer = OceanFoam,
    tertiary = OceanAqua,
    onTertiary = OceanAbyss,
    background = OceanBlack,
    onBackground = OceanWhite,
    surface = OceanSurface,
    onSurface = OceanWhite,
    surfaceVariant = OceanSurfaceHigh,
    onSurfaceVariant = OceanTextMuted,
    outline = OceanOutline,
    outlineVariant = OceanOutline,
    error = OceanError,
    onError = OceanBlack
)

private val ClosetShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ClosetVRTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OceanColorScheme,
        typography = ClosetTypography,
        shapes = ClosetShapes,
        content = content
    )
}
