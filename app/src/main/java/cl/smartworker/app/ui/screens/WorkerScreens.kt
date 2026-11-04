package cl.smartworker.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.smartworker.app.data.*
import cl.smartworker.app.ui.*
import cl.smartworker.app.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val SUPERVISOR_PHONE = "+56912345678"

fun dialSupervisor(ctx: android.content.Context) {
    Toast.makeText(ctx, "Configura un número real de contacto antes de usar llamadas.", Toast.LENGTH_LONG).show()
}

private fun todayLabel(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es", "CL")))
        .replaceFirstChar { it.uppercase() }

// ============================================================ INICIO
@Composable
fun WorkerHomeScreen(onGoMetrics: () -> Unit) {
    val s by Repo.sensor.collectAsState()
    val user by Repo.user.collectAsState()
    val risk = s.risk
    val ctx = LocalContext.current

    val message = when (risk) {
        Risk.BAJO -> "Condiciones favorables. Mantén tu hidratación habitual."
        Risk.MEDIO -> "La temperatura sube. Haz pausas breves y toma agua."
        Risk.ALTO -> "La radiación solar eleva la sensación térmica. Reduce la exposición ahora."
        Risk.CRITICO -> "Riesgo crítico. Detén la actividad y ve a una zona protegida."
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("Hola, ${user?.firstName ?: ""}", todayLabel()) { RoundIconButton { } }
        CloudStatus()

        SoftCard(Modifier.fillMaxWidth(), bg = risk.bg().copy(alpha = .35f), border = risk.bg()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RiskChip(risk, risk.label)
                Spacer(Modifier.weight(1f))
                Text("Lectura simulada",
                    color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.estTemp.roundToInt()}°", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    Text("Temperatura estimada", fontSize = 13.sp, color = Ink.copy(alpha = .8f))
                }
                Box(
                    Modifier.size(52.dp).background(AmberBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.WbSunny, null, tint = Amber, modifier = Modifier.size(28.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            RiskBar(risk)
            Spacer(Modifier.height(8.dp))
            Text(message, fontSize = 12.sp, color = Ink.copy(alpha = .8f))
        }

        SectionTitle("Cuídate ahora", "Ver protocolo") {
            Toast.makeText(ctx, "Protocolo de calor: pausa, sombra, agua", Toast.LENGTH_SHORT).show()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TipCard(Icons.Outlined.WaterDrop, "Hidrátate", "Bebe 250 ml durante los próximos 15 min.", Modifier.weight(1f))
            TipCard(Icons.Outlined.BeachAccess, "Busca sombra", "Toma una pausa protegida por 10 minutos.", Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        SoftCard(Modifier.fillMaxWidth(), bg = GreenBg, border = GreenBg, onClick = onGoMetrics) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null, tint = Green)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Simulador del prototipo", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Green)
                    Text("Exposición simulada: ${fmtMin(s.exposureMin)}", fontSize = 11.sp, color = Ink.copy(alpha = .7f))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Green)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("SmartWorker combina condiciones ambientales y tiempo de exposición.",
            fontSize = 11.sp, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RiskBar(risk: Risk) {
    val active = risk.ordinal // 0..3
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val colors = listOf(Green, Amber, Orange, Red)
        for (i in 0..3) {
            Box(
                Modifier.weight(1f).height(5.dp).background(
                    if (i <= active) colors[i] else Line, RoundedCornerShape(50)
                )
            )
        }
    }
}

@Composable
private fun TipCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier) {
    SoftCard(modifier, bg = androidx.compose.ui.graphics.Color.White, border = Line) {
        Box(Modifier.size(28.dp).background(TerracotaSoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Terracota, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(body, fontSize = 11.sp, color = Muted)
    }
}

// ============================================================ MÉTRICAS
@Composable
fun WorkerMetricsScreen() {
    val s by Repo.sensor.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("Condiciones ambientales", "Datos simulados · Firebase") {
            RoundIconButton(Icons.Outlined.Refresh)
        }

        Row(
            Modifier.fillMaxWidth().background(GreenBg, RoundedCornerShape(10.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Sensors, null, tint = Green, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Simulación: sin enlace al Arduino", fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("Demo", color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Temperatura estimada", "${s.estTemp.roundToInt()}", "°C", "+${s.solarBoost} °C por radiación solar",
                AmberBg, Icons.Outlined.Thermostat, Modifier.weight(1f))
            StatTile("Radiación UV", "${s.uv}", uvLabel(s.uv), "Protección obligatoria",
                AmberBg, Icons.Outlined.WbSunny, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Humedad relativa", "${s.humidity}", "%", if (s.humidity < 40) "Ambiente seco" else "Ambiente húmedo",
                BlueBg, Icons.Outlined.WaterDrop, Modifier.weight(1f))
            StatTile("Exposición acumulada", "${s.exposureMin / 60}h${(s.exposureMin % 60).toString().padStart(2, '0')}", "min",
                "Límite recomendado: 3 h", Crema, Icons.Outlined.Timer, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))
        Text("La temperatura, humedad, UV y actividad de esta pantalla son simulados. Los cambios se envían con Guardar lectura o con la variación automática.", color = Muted, fontSize = 12.sp)

        SimulatorCard()
        Spacer(Modifier.height(16.dp))
    }
}

private fun uvLabel(uv: Int) = when {
    uv <= 2 -> "Bajo"
    uv <= 5 -> "Moderado"
    uv <= 7 -> "Alto"
    uv <= 10 -> "Muy alto"
    else -> "Extremo"
}

/** Sustituye al wearable mientras no haya hardware: mueve los sliders y observa cómo cambia el riesgo. */
@Composable
private fun SimulatorCard() {
    val s by Repo.sensor.collectAsState()
    val saving by Repo.saving.collectAsState()
    val ready by Repo.ready.collectAsState()
    SectionTitle("Simulador del wearable (demo)")
    SoftCard(Modifier.fillMaxWidth()) {
        Text("Temperatura ambiente: ${"%.1f".format(s.ambientTemp)} °C", fontSize = 12.sp)
        Slider(s.ambientTemp, { Repo.setTemp(it) }, valueRange = 15f..45f,
            colors = SliderDefaults.colors(thumbColor = Terracota, activeTrackColor = Terracota))
        Text("Humedad: ${s.humidity} %", fontSize = 12.sp)
        Slider(s.humidity.toFloat(), { Repo.setHumidity(it.roundToInt()) }, valueRange = 10f..100f,
            colors = SliderDefaults.colors(thumbColor = Terracota, activeTrackColor = Terracota))
        Text("Índice UV: ${s.uv}", fontSize = 12.sp)
        Slider(s.uv.toFloat(), { Repo.setUv(it.roundToInt()) }, valueRange = 0f..12f,
            colors = SliderDefaults.colors(thumbColor = Terracota, activeTrackColor = Terracota))
        Text("Esfuerzo físico simulado", fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Activity.entries.forEach { a ->
                FilterChip(
                    selected = s.activity == a, onClick = { Repo.setActivity(a) },
                    label = { Text(a.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TerracotaSoft)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Variación y envío cada 10 segundos", fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(s.auto, { Repo.setAuto(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Terracota))
        }
        Text("Riesgo actual: ${s.risk.short}", fontWeight = FontWeight.Bold, color = s.risk.fg())
        Spacer(Modifier.height(12.dp))
        PrimaryButton(if (saving) "Esperando Firebase…" else "Guardar lectura en Firebase", enabled = ready && !saving) { Repo.publishReading() }
        if (!ready) TextButton(onClick = { Repo.reloadReading() }) { Text("Reintentar conexión") }
        CloudStatus()
    }
}

// ============================================================ AYUDA / ALERTA
@Composable
fun WorkerAlertScreen(onLogout: () -> Unit) {
    val s by Repo.sensor.collectAsState()
    val ctx = LocalContext.current
    val critical = s.risk == Risk.CRITICO && !s.safeConfirmed
    val ready by Repo.ready.collectAsState()
    val saving by Repo.saving.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("Alerta de seguridad", "Alerta calculada con datos simulados") {
            RoundIconButton(Icons.Outlined.Phone) { dialSupervisor(ctx) }
        }

        if (critical) {
            SoftCard(Modifier.fillMaxWidth(), bg = RedBg.copy(alpha = .6f), border = Red.copy(alpha = .5f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(44.dp).background(RedBg, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = Red)
                    }
                    Spacer(Modifier.weight(1f))
                    RiskChip(Risk.CRITICO)
                }
                Spacer(Modifier.height(10.dp))
                Text("Detente y protégete ahora", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("La temperatura estimada alcanzó ${s.estTemp.roundToInt()} °C y tu exposición supera el límite seguro.",
                    fontSize = 13.sp, color = Ink.copy(alpha = .8f))
            }
        } else {
            SoftCard(Modifier.fillMaxWidth(), bg = GreenBg, border = GreenBg) {
                Icon(Icons.Outlined.Shield, null, tint = Green)
                Spacer(Modifier.height(6.dp))
                Text(if (s.safeConfirmed) "Confirmaste que estás a salvo" else "Sin alertas críticas",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Tu nivel de riesgo actual es ${s.risk.short.lowercase()}. Estas pautas siempre aplican.",
                    fontSize = 12.sp, color = Ink.copy(alpha = .75f))
            }
        }

        SectionTitle("Haz esto de inmediato")
        StepRow(1, "Detén la actividad", "Interrumpe el esfuerzo físico de inmediato.", Icons.Outlined.PanTool)
        Spacer(Modifier.height(8.dp))
        StepRow(2, "Ve a una zona protegida", "Dirígete a una zona protegida definida para tu lugar de trabajo.", Icons.Outlined.BeachAccess)
        Spacer(Modifier.height(8.dp))
        StepRow(3, "Hidrátate lentamente", "Bebe agua en pequeñas cantidades.", Icons.Outlined.WaterDrop)
        Spacer(Modifier.height(16.dp))

        PrimaryButton("Confirmar que estoy a salvo", danger = critical, enabled = ready && !saving) {
            Repo.confirmSafe()
            Toast.makeText(ctx, "Enviando confirmación. Revisa el estado de guardado.", Toast.LENGTH_SHORT).show()
        }
        Spacer(Modifier.height(10.dp))
        OutlineButton("Solicitar asistencia", Modifier.fillMaxWidth()) {
            Toast.makeText(ctx, "Función de demostración: no se envió asistencia.", Toast.LENGTH_LONG).show()
        }
        Spacer(Modifier.height(12.dp))
        SoftCard(Modifier.fillMaxWidth(), bg = GreenBg, border = GreenBg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sensors, null, tint = Green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("El supervisor asignado puede consultar las lecturas guardadas. No se envían notificaciones push.", fontSize = 12.sp)
            }
        }
        CloudStatus()
        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cerrar sesión", color = Muted)
        }
    }
}

@Composable
private fun StepRow(n: Int, title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp).background(TerracotaSoft, CircleShape), contentAlignment = Alignment.Center) {
                Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Terracota)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(body, fontSize = 11.sp, color = Muted)
            }
            Icon(icon, null, tint = Terracota, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun CloudStatus() {
    val status by Repo.status.collectAsState()
    Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
}
