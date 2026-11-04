package cl.smartworker.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.*
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/** Authentication: cuentas; Firestore: perfiles e historial; RTDB: última lectura. */
object Repo {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val realtime = FirebaseDatabase.getInstance("https://smartworker-bb004-default-rtdb.firebaseio.com")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()
    private val _sensor = MutableStateFlow(SensorState())
    val sensor = _sensor.asStateFlow()
    private val _workers = MutableStateFlow<List<Worker>>(emptyList())
    val workers = _workers.asStateFlow()
    private val _status = MutableStateFlow("Inicia sesión para conectar Firebase.")
    val status = _status.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready = _ready.asStateFlow()
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history = _history.asStateFlow()
    private val _historyStatus = MutableStateFlow("")
    val historyStatus = _historyStatus.asStateFlow()
    private var session = 0
    private var authBusy = false
    private var profileListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null
    private var historyGeneration = 0
    private val readingListeners = mutableMapOf<String, Pair<DatabaseReference, ValueEventListener>>()
    private val profiles = mutableMapOf<String, DocumentSnapshot>()
    private val readings = mutableMapOf<String, DataSnapshot>()
    private val readErrors = mutableMapOf<String, String>()

    init {
        scope.launch {
            while (isActive) {
                delay(10000)
                if (_user.value?.role == Role.TRABAJADOR && _sensor.value.auto && _ready.value && !_saving.value) {
                    modify { s -> s.copy(ambientTemp = ((s.ambientTemp + Random.nextFloat() - .5f).coerceIn(15f, 45f) * 10).roundToInt() / 10f) }
                    publishReading()
                }
                if (_user.value?.role == Role.SUPERVISOR) rebuildWorkers()
            }
        }
    }

    fun login(email: String, password: String, done: (Result<User>) -> Unit) {
        if (authBusy) return
        authBusy = true
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { loadProfile(done) }
            .addOnFailureListener { finishAuthError(it, done) }
    }

    fun register(rut: String, email: String, password: String, done: (Result<User>) -> Unit) {
        if (authBusy) return
        if (!RutUtils.isValid(rut)) { done(Result.failure(Exception("RUT inválido."))); return }
        authBusy = true
        fun ensureProfile() {
            val uid = auth.currentUser!!.uid
            val ref = db.collection("usuarios").document(uid)
            // Permite completar un registro si la cuenta se creó pero Firestore falló.
            // Nunca modifica un perfil existente ni sus permisos.
            db.runTransaction { transaction ->
                if (!transaction.get(ref).exists()) transaction.set(ref, mapOf(
                    "uid" to uid, "rut" to RutUtils.normalize(rut),
                    "nombre" to email.trim().substringBefore("@").replaceFirstChar { it.uppercase() },
                    "correo" to auth.currentUser!!.email.orEmpty(), "rol" to Role.TRABAJADOR.name,
                    "supervisorUid" to "", "zona" to "Sin asignar", "cargo" to "Trabajador",
                    "creadoEn" to FieldValue.serverTimestamp()
                ))
                null
            }.addOnSuccessListener { loadProfile(done) }
                .addOnFailureListener {
                    finishAuthError(Exception("La cuenta existe en Authentication, pero no se pudo guardar el perfil: ${errorMessage(it)} Vuelve a registrarte con el mismo correo y contraseña para completar el perfil."), done)
                }
        }
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { ensureProfile() }
            .addOnFailureListener { error ->
                if (error is FirebaseAuthUserCollisionException) {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { ensureProfile() }
                        .addOnFailureListener { finishAuthError(it, done) }
                } else finishAuthError(error, done)
            }
    }

    private fun loadProfile(done: (Result<User>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return finishAuthError(Exception("Sesión no disponible."), done)
        db.collection("usuarios").document(uid).get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val role = Role.entries.firstOrNull { it.name == doc.getString("rol") }
                if (!doc.exists() || role == null) {
                    finishAuthError(Exception("No existe un perfil válido en Firestore. Si tu registro quedó incompleto, repítelo con los mismos datos; si se cambió el rol, revisa su valor en la consola."), done)
                } else {
                    clearSession()
                    val u = User(doc.getString("rut").orEmpty(), doc.getString("nombre").orEmpty(), auth.currentUser?.email.orEmpty(), uid, role)
                    _user.value = u
                    authBusy = false
                    if (role == Role.TRABAJADOR) reloadReading() else listenTeam(uid)
                    done(Result.success(u))
                }
            }.addOnFailureListener { finishAuthError(it, done) }
    }

    private fun finishAuthError(error: Exception, done: (Result<User>) -> Unit) {
        authBusy = false
        auth.signOut()
        done(Result.failure(Exception(errorMessage(error))))
    }

    private fun clearSession() {
        session++
        profileListener?.remove(); profileListener = null
        stopHistory()
        readingListeners.values.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        readingListeners.clear(); profiles.clear(); readings.clear(); readErrors.clear()
        _workers.value = emptyList(); _sensor.value = SensorState()
        _ready.value = false; _saving.value = false; _user.value = null
    }

    fun logout() {
        clearSession()
        auth.signOut()
        _status.value = "Sesión cerrada."
    }

    fun reloadReading() {
        val u = _user.value?.takeIf { it.role == Role.TRABAJADOR } ?: return
        val token = session
        _ready.value = false
        _status.value = "Authentication y perfil Firestore: OK. Consultando Realtime Database…"
        realtime.getReference("lecturasActuales").child(u.uid).get()
            .addOnSuccessListener { snap ->
                if (token != session) return@addOnSuccessListener
                _sensor.value = if (snap.exists()) decode(snap) else SensorState()
                _ready.value = true
                _status.value = if (snap.exists()) "Última lectura guardada: ${readingTime(number(snap, "actualizadoEn").toLong())}. Datos simulados." else "Firebase conectado. Aún no has guardado lecturas; utiliza Métricas."
            }.addOnFailureListener {
                if (token == session) _status.value = "Realtime Database: ${errorMessage(it)} Usa Reintentar conexión en Métricas."
            }
    }

    fun publishReading() {
        val u = _user.value?.takeIf { it.role == Role.TRABAJADOR } ?: return
        if (!_ready.value || _saving.value) return
        val token = session
        val s = _sensor.value
        val id = UUID.randomUUID().toString()
        val values = mapOf<String, Any>(
            "temperaturaAmbiente" to s.ambientTemp.toDouble(), "temperaturaEstimada" to s.estTemp.toDouble(),
            "humedad" to s.humidity, "uv" to s.uv, "actividad" to s.activity.name,
            "exposicionMin" to s.exposureMin, "aSalvo" to s.safeConfirmed,
            "riesgo" to s.risk.name, "origen" to "SIMULADO", "lecturaId" to id
        )
        _saving.value = true
        var rtdbStatus = "pendiente"
        var firestoreStatus = "pendiente"
        var remaining = 2
        fun completed() {
            if (token != session) return
            remaining--
            _status.value = "Realtime Database: $rtdbStatus. Historial Firestore: $firestoreStatus."
            _saving.value = remaining > 0
        }
        _status.value = "Enviando lectura simulada; esperando confirmación de ambos servicios…"
        realtime.getReference("lecturasActuales").child(u.uid)
            .setValue(values + ("actualizadoEn" to ServerValue.TIMESTAMP))
            .addOnSuccessListener { rtdbStatus = "guardado"; completed() }
            .addOnFailureListener { rtdbStatus = errorMessage(it); completed() }
        db.collection("usuarios").document(u.uid).collection("historial").document(id)
            .set(values + ("fecha" to FieldValue.serverTimestamp()))
            .addOnSuccessListener { firestoreStatus = "guardado"; completed() }
            .addOnFailureListener { firestoreStatus = errorMessage(it); completed() }
    }

    private fun listenTeam(uid: String) {
        val token = session
        _status.value = "Consultando equipo en Firestore…"
        profileListener = db.collection("usuarios").whereEqualTo("supervisorUid", uid)
            .whereEqualTo("rol", "TRABAJADOR").addSnapshotListener { snapshot, error ->
                if (token != session) return@addSnapshotListener
                if (error != null) {
                    readingListeners.values.forEach { (ref, l) -> ref.removeEventListener(l) }
                    readingListeners.clear(); profiles.clear(); readings.clear(); readErrors.clear()
                    _workers.value = emptyList()
                    _status.value = "Firestore: ${errorMessage(error)}"
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                val ids = docs.map { it.id }.toSet()
                (readingListeners.keys.toSet() - ids).forEach { id ->
                    readingListeners.remove(id)?.let { (ref, l) -> ref.removeEventListener(l) }
                    readings.remove(id); readErrors.remove(id)
                }
                profiles.clear(); docs.forEach { profiles[it.id] = it }
                docs.forEach { doc ->
                    if (doc.id !in readingListeners) {
                        val ref = realtime.getReference("lecturasActuales").child(doc.id)
                        val listener = object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (token != session || doc.id !in profiles) return
                                readings[doc.id] = snapshot; readErrors.remove(doc.id); rebuildWorkers(); teamStatus()
                            }
                            override fun onCancelled(error: DatabaseError) {
                                if (token != session || doc.id !in profiles) return
                                readings.remove(doc.id); readErrors[doc.id] = error.message
                                rebuildWorkers(); teamStatus()
                            }
                        }
                        readingListeners[doc.id] = ref to listener
                        ref.addValueEventListener(listener)
                    }
                }
                rebuildWorkers(); teamStatus()
            }
    }

    private fun teamStatus() {
        _status.value = when {
            profiles.isEmpty() -> "No hay trabajadores asignados a tu UID en Firestore."
            readErrors.isNotEmpty() -> "No se pudieron leer ${readErrors.size} lecturas. Revisa permisos/supervisorUid en Realtime Database."
            readings.size < profiles.size -> "Cargando lecturas de Realtime Database…"
            else -> "Equipo cargado. Lecturas simuladas; se actualizan al recibir cambios de Firebase."
        }
    }

    private fun rebuildWorkers() {
        _workers.value = profiles.map { (id, profile) ->
            val snap = readings[id]
            val hasReading = snap?.exists() == true
            val state = if (hasReading) decode(snap!!) else SensorState()
            val time = snap?.let { number(it, "actualizadoEn").toLong() } ?: 0
            Worker(id, profile.getString("nombre").orEmpty(), profile.getString("cargo").orEmpty(),
                profile.getString("zona").orEmpty(), profile.getString("rut").orEmpty(),
                if (hasReading) state.risk else Risk.BAJO, state.estTemp.roundToInt(), state.uv, state.exposureMin,
                hasReading && System.currentTimeMillis() - time in 0..90000, hasReading, state.humidity, time, state.safeConfirmed)
        }.sortedByDescending { if (it.hasReading) it.risk.ordinal else -1 }
    }

    fun watchHistory(uid: String) {
        stopHistory()
        val token = session
        val generation = historyGeneration
        _historyStatus.value = "Cargando historial…"
        historyListener = db.collection("usuarios").document(uid).collection("historial")
            .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(20).addSnapshotListener { snapshot, error ->
                if (token != session || generation != historyGeneration) return@addSnapshotListener
                if (error != null) {
                    _history.value = emptyList(); _historyStatus.value = errorMessage(error)
                } else {
                    _history.value = snapshot?.documents.orEmpty().map { HistoryEntry(it.id,
                        it.getTimestamp("fecha")?.toDate()?.time ?: 0, it.getDouble("temperaturaEstimada") ?: 0.0,
                        it.getLong("humedad") ?: 0, it.getString("riesgo").orEmpty()) }
                    _historyStatus.value = if (_history.value.isEmpty()) "Aún no hay lecturas guardadas." else "Últimas lecturas guardadas en Firestore (simuladas)."
                }
            }
    }

    fun stopHistory() {
        historyGeneration++
        historyListener?.remove(); historyListener = null
        _history.value = emptyList(); _historyStatus.value = ""
    }

    private fun number(s: DataSnapshot, key: String) = s.child(key).value as? Number ?: 0
    private fun decode(s: DataSnapshot) = SensorState(
        number(s, "temperaturaAmbiente").toFloat(), number(s, "humedad").toInt(), number(s, "uv").toInt(),
        Activity.entries.firstOrNull { it.name == s.child("actividad").value } ?: Activity.MODERADA,
        number(s, "exposicionMin").toInt(), false, s.child("aSalvo").value == true)

    private fun modify(f: (SensorState) -> SensorState) {
        _sensor.update { old ->
            val next = f(old)
            if (next.risk != old.risk || next.risk != Risk.CRITICO) next.copy(safeConfirmed = false) else next
        }
    }
    fun setTemp(t: Float) = modify { it.copy(ambientTemp = (t * 10).roundToInt() / 10f) }
    fun setHumidity(h: Int) = modify { it.copy(humidity = h) }
    fun setUv(v: Int) = modify { it.copy(uv = v) }
    fun setActivity(a: Activity) = modify { it.copy(activity = a) }
    fun setAuto(on: Boolean) = modify { it.copy(auto = on) }
    fun confirmSafe() {
        if (!_ready.value || _saving.value) return
        _sensor.update { it.copy(safeConfirmed = true) }
        publishReading()
    }

    private fun errorMessage(e: Exception): String = when {
        e is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "El correo no es válido."
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL" -> "Correo o contraseña incorrectos."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Habilita Correo electrónico/Contraseña en Authentication."
            "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple la política de Firebase."
            "ERROR_USER_DISABLED" -> "La cuenta está deshabilitada."
            else -> "Authentication: ${e.errorCode}."
        }
        e is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Permiso denegado. Revisa las reglas de Firestore."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Firestore no está disponible. Revisa Internet y que la base esté creada."
            else -> "Firestore: ${e.code}. ${e.localizedMessage.orEmpty()}"
        }
        e is com.google.firebase.FirebaseNetworkException -> "Sin conexión. Revisa Internet y vuelve a intentar."
        else -> e.localizedMessage ?: "No se pudo completar la operación."
    }
}

object RutUtils {
    fun normalize(input: String): String {
        val c = input.replace(".", "").replace("-", "").replace(" ", "").uppercase()
        return if (c.length < 2) c else c.dropLast(1) + "-" + c.last()
    }

    fun isValid(input: String): Boolean {
        val c = input.replace(".", "").replace("-", "").replace(" ", "").uppercase()
        if (c.length !in 8..9) return false
        val body = c.dropLast(1)
        if (!body.all { it.isDigit() }) return false
        var sum = 0
        var f = 2
        for (ch in body.reversed()) {
            sum += ch.digitToInt() * f
            f = if (f == 7) 2 else f + 1
        }
        val r = 11 - sum % 11
        val dv = when (r) { 11 -> '0'; 10 -> 'K'; else -> r.digitToChar() }
        return c.last() == dv
    }
}
