package cl.smartworker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cl.smartworker.app.data.Risk

// Paleta tomada de los mockups (terracota + crema)
val Terracota = Color(0xFFC65D3E)
val TerracotaSoft = Color(0xFFF8E3DA)
val Crema = Color(0xFFFBF6F2)
val Ink = Color(0xFF2A2321)
val Muted = Color(0xFF7D716C)
val Line = Color(0xFFE9E1DC)

val Green = Color(0xFF2E7D5B)
val GreenBg = Color(0xFFE2F1E8)
val Amber = Color(0xFFB87412)
val AmberBg = Color(0xFFFCEBCB)
val Orange = Color(0xFFC2571A)
val OrangeBg = Color(0xFFFFE6D1)
val Red = Color(0xFFB5392F)
val RedBg = Color(0xFFFBE0DA)
val BlueBg = Color(0xFFDDEBF3)
val Blue = Color(0xFF2B6C8F)

fun Risk.fg(): Color = when (this) {
    Risk.BAJO -> Green
    Risk.MEDIO -> Amber
    Risk.ALTO -> Orange
    Risk.CRITICO -> Red
}

fun Risk.bg(): Color = when (this) {
    Risk.BAJO -> GreenBg
    Risk.MEDIO -> AmberBg
    Risk.ALTO -> OrangeBg
    Risk.CRITICO -> RedBg
}

@Composable
fun SmartWorkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Terracota,
            onPrimary = Color.White,
            background = Color.White,
            surface = Color.White,
            onSurface = Ink,
            outline = Line,
            error = Red
        ),
        content = content
    )
}
