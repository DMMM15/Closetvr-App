package com.closetvr.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ───────────── Paleta "Deep Ocean Blue" ─────────────
// Del negro puro y el azul marino más profundo, pasando por azules medios y turquesa, hasta el blanco.
val OceanBlack = Color(0xFF000000)      // fondos / contrastes profundos
val OceanAbyss = Color(0xFF030B1A)      // azul marino muy oscuro
val OceanNavy = Color(0xFF071B38)
val OceanDeep = Color(0xFF0B2E57)
val OceanBlue = Color(0xFF1558A0)
val OceanSky = Color(0xFF2F8FD8)
val OceanTurquoise = Color(0xFF19C5C8)
val OceanAqua = Color(0xFF7FE9E4)
val OceanFoam = Color(0xFFDDF6F6)
val OceanWhite = Color(0xFFFFFFFF)

// Superficies y bordes (tarjetas, campos, barras)
val OceanSurface = Color(0xFF081C36)
val OceanSurfaceHigh = Color(0xFF0E2A4D)
val OceanOutline = Color(0xFF1E4170)
val OceanTextMuted = Color(0xFFA9C3DD)
val OceanError = Color(0xFFFF6B7A)

// Degradados reutilizables
val OceanBackgroundBrush: Brush
    get() = Brush.verticalGradient(listOf(OceanNavy, OceanAbyss, OceanBlack))

val OceanAccentBrush: Brush
    get() = Brush.horizontalGradient(listOf(OceanSky, OceanTurquoise))
