package cl.smartworker.app.ui.screens

import androidx.activity.compose.BackHandler
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.smartworker.app.data.Repo
import cl.smartworker.app.data.RutUtils
import cl.smartworker.app.data.User
import cl.smartworker.app.ui.*
import cl.smartworker.app.ui.theme.*

@Composable
private fun SocialRow() {
    val ctx = LocalContext.current
    val soon = { Toast.makeText(ctx, "Disponible en una versión futura", Toast.LENGTH_SHORT).show() }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlineButton("Google", Modifier.weight(1f), onClick = soon)
        OutlineButton("Apple", Modifier.weight(1f), onClick = soon)
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Line)
        Text("  o continúa con  ", color = Muted, fontSize = 12.sp)
        HorizontalDivider(Modifier.weight(1f), color = Line)
    }
}

@Composable
fun LoginScreen(onLoggedIn: (User) -> Unit, onRegister: () -> Unit) {
    var rut by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    BackHandler(enabled = loading) { }


    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("SmartWorker", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        Text("Qué bueno verte de nuevo", fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
        Spacer(Modifier.height(8.dp))
        Text("Entra a tu espacio y continúa justo donde lo dejaste.", color = Muted)
        Spacer(Modifier.height(24.dp))

        SwField("Correo electrónico", rut, { rut = it; error = null }, "tu@correo.com", Icons.Outlined.Email, keyboard = KeyboardType.Email)
        Spacer(Modifier.height(14.dp))
        SwField("Contraseña", pass, { pass = it; error = null }, "••••••••", Icons.Outlined.Lock,
            error = error, password = true)

        Text("¿Olvidaste tu contraseña?", color = Terracota, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.End).padding(vertical = 12.dp))

        PrimaryButton(if (loading) "Conectando…" else "Iniciar sesión", enabled = !loading) {
            if (!Patterns.EMAIL_ADDRESS.matcher(rut.trim()).matches() || pass.isBlank()) {
                error = "Ingresa tu correo y contraseña."
            } else {
                loading = true
                Repo.login(rut, pass) { result ->
                    loading = false
                    result.onSuccess(onLoggedIn).onFailure { error = it.message }
                }
            }
        }

        OrDivider()
        SocialRow()
        Spacer(Modifier.height(20.dp))

        Text(
            buildAnnotatedString {
                append("¿Aún no tienes cuenta? ")
                withStyle(SpanStyle(color = Terracota, fontWeight = FontWeight.Bold)) { append("Crear cuenta") }
            },
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable(enabled = !loading) { onRegister() }
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Ingresa con tu cuenta de Firebase. El RUT se conserva en tu perfil.",
            color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun RegisterScreen(onRegistered: (User) -> Unit, onLogin: () -> Unit) {
    var rut by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    BackHandler(enabled = loading) { }


    val rutErr = if (submitted && !RutUtils.isValid(rut)) "Rut inválido (ej: 11111111-1)" else null
    val mailErr = if (submitted && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) "Correo inválido" else null
    val passOk = pass.length >= 8 && pass.any { it.isUpperCase() } && pass.any { it.isDigit() }
    val passErr = if (submitted && !passOk) "Mínimo 8 caracteres, una mayúscula y un número" else null

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Text("SmartWorker", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(20.dp))
        Text("Crea tu cuenta", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Un espacio simple y seguro, hecho para ti.", color = Muted)
        Spacer(Modifier.height(20.dp))

        SwField("Rut", rut, { rut = it; serverError = null }, "Indique su rut ej: 11111111-k", Icons.Outlined.Person, error = rutErr)
        Spacer(Modifier.height(12.dp))
        SwField("Correo electrónico", email, { email = it }, "tu@correo.com", Icons.Outlined.Email,
            error = mailErr, keyboard = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        SwField("Contraseña", pass, { pass = it }, "••••••••", Icons.Outlined.Lock, error = passErr,
            helper = "Mínimo 8 caracteres, una mayúscula y un número.", password = true)
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = terms, onCheckedChange = { terms = it },
                colors = CheckboxDefaults.colors(checkedColor = Terracota))
            Text(
                buildAnnotatedString {
                    append("He leído y acepto los ")
                    withStyle(SpanStyle(color = Terracota, fontWeight = FontWeight.SemiBold)) { append("Términos") }
                    append(" y la ")
                    withStyle(SpanStyle(color = Terracota, fontWeight = FontWeight.SemiBold)) { append("Política de privacidad") }
                    append(".")
                },
                fontSize = 12.sp
            )
        }
        if (submitted && !terms) Text("Debes aceptar los términos", color = Red, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))

        if (serverError != null) Text(serverError!!, color = Red, fontSize = 12.sp)
        PrimaryButton(if (loading) "Creando cuenta…" else "Crear cuenta", enabled = !loading) {
            submitted = true
            if (RutUtils.isValid(rut) && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() && passOk && terms) {
                loading = true
                serverError = null
                Repo.register(rut, email, pass) { result ->
                    loading = false
                    result.onSuccess(onRegistered).onFailure { serverError = it.message }
                }
            }
        }

        OrDivider()
        SocialRow()
        Spacer(Modifier.height(20.dp))
        Text(
            buildAnnotatedString {
                append("¿Ya tienes una cuenta? ")
                withStyle(SpanStyle(color = Terracota, fontWeight = FontWeight.Bold)) { append("Iniciar sesión") }
            },
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable(enabled = !loading) { onLogin() }
        )
    }
}
