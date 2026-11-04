package cl.smartworker.app.data

import kotlin.math.roundToInt

enum class Role { TRABAJADOR, SUPERVISOR }

enum class Risk(val short: String, val label: String) {
    BAJO("BAJO", "RIESGO BAJO"),
    MEDIO("MEDIO", "RIESGO MEDIO"),
    ALTO("ALTO", "RIESGO ALTO"),
    CRITICO("CRÍTICO", "CRÍTICO")
}

enum class Activity(val label: String, val points: Int) {
    REPOSO("Reposo", 0),
    MODERADA("Moderada", 1),
    INTENSA("Intensa", 2)
}

data class User(
    val rut: String,
    val name: String,
    val email: String,
    val uid: String,
    val role: Role
) {
    val firstName: String get() = name.substringBefore(" ")
}

/**
 * Lógica de riesgo ponderado por puntos (misma idea del informe: temperatura + humedad + esfuerzo).
 * Ajusta los umbrales aquí si tu profesor/a pide otros valores.
 */
object RiskEngine {
    const val CRITICAL_TEMP = 40f   // °C estimados -> alerta crítica directa

    fun tempPoints(t: Float) = when {
        t < 28f -> 0
        t < 33f -> 1
        else -> 3
    }

    fun humidityPoints(h: Int) = when {
        h < 50 -> 0
        h <= 70 -> 1
        else -> 2
    }

    fun evaluate(estTemp: Float, humidity: Int, activity: Activity): Risk {
        if (estTemp >= CRITICAL_TEMP) return Risk.CRITICO
        val score = tempPoints(estTemp) + humidityPoints(humidity) + activity.points
        return when {
            score <= 1 -> Risk.BAJO
            score <= 3 -> Risk.MEDIO
            else -> Risk.ALTO
        }
    }
}

/** Lectura "del wearable". En esta versión es simulada (ver Repo.kt). */
data class SensorState(
    val ambientTemp: Float = 35f,   // DHT: temperatura ambiente °C
    val humidity: Int = 31,         // DHT: humedad relativa %
    val uv: Int = 9,                // índice UV
    val activity: Activity = Activity.MODERADA, // MPU6050: nivel de esfuerzo
    val exposureMin: Int = 138,     // minutos de exposición acumulada
    val auto: Boolean = false,      // variación automática de la simulación
    val safeConfirmed: Boolean = false
) {
    val solarBoost: Int get() = (uv / 3f).roundToInt()
    val estTemp: Float get() = ambientTemp + solarBoost
    val risk: Risk get() = RiskEngine.evaluate(estTemp, humidity, activity)
}

data class Worker(
    val id: String,
    val name: String,
    val jobTitle: String,
    val zone: String,
    val code: String,
    val risk: Risk,
    val temp: Int,
    val uv: Int,
    val exposureMin: Int,
    val connected: Boolean = false,
    val hasReading: Boolean = false,
    val humidity: Int = 0,
    val updatedAt: Long = 0,
    val safeConfirmed: Boolean = false
) {
    val initials: String
        get() = name.split(" ").take(2).joinToString("") { it.take(1) }.uppercase()
}

fun fmtMin(m: Int) = "${m / 60} h ${m % 60} min"

data class HistoryEntry(val id: String, val time: Long, val temperature: Double, val humidity: Long, val risk: String)

fun readingTime(time: Long): String = if (time <= 0) "Sin lectura" else
    java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
