package cl.smartworker.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.smartworker.app.data.*
import cl.smartworker.app.ui.*
import cl.smartworker.app.ui.theme.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun nowHm() = LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm"))

// ============================================================ RESUMEN
@Composable
fun SupervisorSummaryScreen(onOpenWorker: (String) -> Unit, onAllAlerts: () -> Unit, onTeam: () -> Unit) {
    val user by Repo.user.collectAsState()
    val workers by Repo.workers.collectAsState()
    val total = workers.size
    val connected = workers.count { it.connected }
    val coverage = if (total == 0) 0 else connected * 100 / total
    val low = workers.count { it.hasReading && it.risk == Risk.BAJO }
    val attention = workers.count { it.hasReading && it.risk != Risk.BAJO }
    val alerts = workers.filter { it.risk == Risk.CRITICO }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("Buenos días, ${user?.firstName ?: ""}", "Equipo asignado · Firebase") { RoundIconButton { } }

        SoftCard(Modifier.fillMaxWidth(), bg = GreenBg.copy(alpha = .5f), border = GreenBg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, null, tint = Green)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cobertura de monitoreo $coverage%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("$connected de $total con lecturas de los últimos 90 s", fontSize = 11.sp, color = Muted)
                }
                Text("$coverage%", color = Green, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Counter("$total", "Plantilla registrada", Crema, Ink, Modifier.weight(1f))
            Counter("$low", "Riesgo bajo", GreenBg, Green, Modifier.weight(1f))
            Counter("$attention", "Atención", AmberBg, Amber, Modifier.weight(1f))
        }

        SectionTitle("Alertas activas", "Ver todas (${alerts.size})", onAllAlerts)
        if (alerts.isEmpty()) {
            Text("Sin alertas críticas en este momento.", color = Muted, fontSize = 12.sp)
        } else {
            alerts.forEach { AlertRow(it) { onOpenWorker(it.id) } }
        }

        SectionTitle("Estado por zona", "Mapa", onTeam)
        SoftCard(Modifier.fillMaxWidth(), bg = Color.White) {
            val zones = workers.filter { it.hasReading }.groupBy { it.zone }.map { (zone, team) -> Triple(zone, team.maxBy { it.risk.ordinal }.risk, team.size) }
            zones.forEachIndexed { i, (name, r, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = r.fg(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("$n trabajadores", fontSize = 11.sp, color = Muted)
                    }
                    RiskChip(r)
                }
                if (i < zones.lastIndex) HorizontalDivider(color = Line)
            }
        }
        Spacer(Modifier.height(8.dp))
        CloudStatus()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Counter(value: String, label: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = fg)
        Text(label, fontSize = 11.sp, color = Ink.copy(alpha = .7f))
    }
}

@Composable
private fun AlertRow(w: Worker, onClick: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), bg = RedBg.copy(alpha = .6f), border = Red.copy(alpha = .4f), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(w.initials, w.risk, 38)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(w.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Exposición térmica crítica · ${w.zone}", fontSize = 11.sp, color = Ink.copy(alpha = .7f))
            }
            Text(readingTime(w.updatedAt), fontSize = 11.sp, color = Muted)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Red)
        }
    }
}

// ============================================================ EQUIPO
@Composable
fun SupervisorTeamScreen(onOpenWorker: (String) -> Unit) {
    val all by Repo.workers.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0 todos, 1 riesgo, 2 sin conexión

    val atRisk = all.filter { it.hasReading && it.risk.ordinal >= Risk.ALTO.ordinal }
    val offline = all.filter { !it.connected }
    val list = when (filter) { 1 -> atRisk; 2 -> offline; else -> all }
        .filter { query.isBlank() || it.name.contains(query, true) || it.zone.contains(query, true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("Trabajadores", "${all.size} registrados · ${all.count { it.connected }} con datos recientes") {
            RoundIconButton(Icons.Outlined.PersonAdd)
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(14.dp), placeholder = { Text("Buscar por nombre o zona", color = Muted) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Crema, unfocusedContainerColor = Crema,
                unfocusedBorderColor = Line, focusedBorderColor = Terracota
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todos ${all.size}", "En riesgo ${atRisk.size}", "Sin datos recientes ${offline.size}").forEachIndexed { i, t ->
                FilterChip(
                    selected = filter == i, onClick = { filter = i }, label = { Text(t, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Color.White)
                )
            }
        }
        Text("Prioridad operativa · Mayor riesgo", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 8.dp))
        CloudStatus()
        if (list.isEmpty()) Text("No hay trabajadores que coincidan. Revisa la asignación del supervisor en Firebase.", color = Muted, fontSize = 12.sp)
        LazyColumn {
            items(list, key = { it.id }) { w ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenWorker(w.id) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(w.initials, w.risk)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(w.jobTitle, fontSize = 12.sp, color = Muted)
                        Text(if (w.hasReading) "${w.zone} · ${readingTime(w.updatedAt)}" else "Sin lecturas", fontSize = 11.sp, color = Muted)
                    }
                    if (w.hasReading) RiskChip(w.risk) else Text("Sin lectura", color = Muted, fontSize = 11.sp)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
                }
                HorizontalDivider(color = Line)
            }
        }
    }
}

// ============================================================ DETALLE
@Composable
fun WorkerDetailScreen(id: String, onBack: () -> Unit) {
    val workers by Repo.workers.collectAsState()
    val w = workers.firstOrNull { it.id == id }
    if (w == null) {
        Column(Modifier.padding(20.dp)) {
            Text("El trabajador ya no está disponible en tu equipo.")
            TextButton(onClick = onBack) { Text("Volver") }
        }
        return
    }
    val history by Repo.history.collectAsState()
    val historyStatus by Repo.historyStatus.collectAsState()
    DisposableEffect(id) { Repo.watchHistory(id); onDispose { Repo.stopHistory() } }
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Detalle del trabajador", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Monitoreo individual", fontSize = 12.sp, color = Muted)
            }
            RoundIconButton(Icons.Outlined.MoreHoriz)
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(w.initials, w.risk, 52)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${w.jobTitle} · ID ${w.code}", fontSize = 11.sp, color = Muted)
                    Text("${w.zone}", fontSize = 11.sp, color = Muted)
                }
                if (w.hasReading) RiskChip(w.risk) else Text("Sin lectura", color = Muted, fontSize = 11.sp)
            }
        }

        SectionTitle("Última lectura", readingTime(w.updatedAt))
        Text("Origen: simulador de la aplicación", color = Muted, fontSize = 12.sp)
        if (w.hasReading) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Temperatura estimada", "${w.temp}", "°C", "", AmberBg, Icons.Outlined.Thermostat, Modifier.weight(1f))
                StatTile("Humedad", "${w.humidity}", "%", "", BlueBg, Icons.Outlined.WaterDrop, Modifier.weight(1f))
            }
            Text("UV simulado: ${w.uv} · Exposición simulada: ${fmtMin(w.exposureMin)}", fontSize = 12.sp, color = Muted)
        } else Text("Este trabajador aún no tiene una lectura disponible.", color = Muted)

        SectionTitle("Historial reciente")
        Text(historyStatus, fontSize = 12.sp, color = Muted)
        SoftCard(Modifier.fillMaxWidth(), bg = Color.White) {
            history.forEach { entry ->
                HistoryRow(readingTime(entry.time), Muted, "Riesgo ${entry.risk}", "${entry.temperature} °C estimados · ${entry.humidity}% humedad")
            }
        }
        Spacer(Modifier.height(10.dp))
        if (w.risk == Risk.CRITICO) {
            Row(
                Modifier.fillMaxWidth().background(AmberBg, RoundedCornerShape(10.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Schedule, null, tint = Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (w.safeConfirmed) "El trabajador confirmó que está a salvo" else "Sin confirmación del trabajador", fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("Llamar", Modifier.weight(1f)) { dialSupervisor(ctx) }
            Button(
                onClick = { confirm = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Red)
            ) { Text("Enviar asistencia", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Enviar asistencia") },
            text = { Text("Este botón pertenece al prototipo. Aún no está conectado a un servicio de emergencias.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    Toast.makeText(ctx, "Función de demostración: no se envió asistencia.", Toast.LENGTH_LONG).show()
                }) { Text("Enviar", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun HistoryRow(time: String, dot: Color, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(time, fontSize = 11.sp, color = Muted, modifier = Modifier.width(70.dp))
        Box(Modifier.padding(top = 5.dp).size(8.dp).background(dot, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(body, fontSize = 11.sp, color = Muted)
        }
    }
}

// ============================================================ ALERTAS
@Composable
fun SupervisorAlertsScreen(onOpenWorker: (String) -> Unit, onLogout: () -> Unit) {
    val workers by Repo.workers.collectAsState()
    val alerts = workers.filter { it.hasReading && it.risk.ordinal >= Risk.ALTO.ordinal }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("Alertas", "${alerts.size} en las últimas lecturas")
        CloudStatus()
        if (alerts.isEmpty()) {
            Text("No hay alertas en las lecturas disponibles.", color = Muted)
        }
        alerts.forEach { w ->
            SoftCard(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                bg = w.risk.bg().copy(alpha = .5f), border = w.risk.bg(), onClick = { onOpenWorker(w.id) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(w.initials, w.risk, 38)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${w.temp} °C estimados · ${w.zone}", fontSize = 11.sp, color = Muted)
                    }
                    if (w.hasReading) RiskChip(w.risk) else Text("Sin lectura", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cerrar sesión", color = Muted)
        }
    }
}
