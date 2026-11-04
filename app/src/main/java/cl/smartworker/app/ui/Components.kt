package cl.smartworker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.smartworker.app.data.Risk
import cl.smartworker.app.ui.theme.*

@Composable
fun RiskChip(risk: Risk, text: String = risk.short) {
    Row(
        modifier = Modifier
            .background(risk.bg(), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(risk.fg(), CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(text, color = risk.fg(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    bg: Color = Crema,
    border: Color = Line,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    var m = modifier.clip(shape).background(bg).border(BorderStroke(1.dp, border), shape)
    if (onClick != null) m = m.clickable { onClick() }
    Column(m.padding(14.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (danger) Red else Terracota)
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
    ) { Text(text, fontWeight = FontWeight.Medium) }
}

@Composable
fun SwField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    error: String? = null,
    helper: String? = null,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text
) {
    var visible by remember { mutableStateOf(false) }
    Column {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text(placeholder, color = Muted) },
            leadingIcon = { Icon(icon, null, tint = Muted) },
            trailingIcon = if (password) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "Mostrar u ocultar contraseña", tint = Muted
                        )
                    }
                }
            } else null,
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            isError = error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Crema, unfocusedContainerColor = Crema,
                unfocusedBorderColor = Line, focusedBorderColor = Terracota
            )
        )
        val msg = error ?: helper
        if (msg != null) {
            Text(
                msg, fontSize = 12.sp, color = if (error != null) Red else Muted,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(subtitle, fontSize = 12.sp, color = Muted)
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        trailing?.invoke()
    }
}

@Composable
fun RoundIconButton(icon: ImageVector = Icons.Outlined.Notifications, onClick: () -> Unit = {}) {
    Box(
        Modifier.size(40.dp).border(1.dp, Line, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Ink, modifier = Modifier.size(20.dp)) }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String,
    note: String,
    bg: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(bg, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.sp, color = Ink.copy(alpha = .75f), modifier = Modifier.weight(1f))
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = Ink.copy(alpha = .6f))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 12.sp, color = Ink.copy(alpha = .7f), modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(note, fontSize = 11.sp, color = Ink.copy(alpha = .65f))
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = Terracota, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                modifier = Modifier.clickable { onAction() })
        }
    }
}

@Composable
fun Avatar(initials: String, risk: Risk, size: Int = 42) {
    Box(
        Modifier.size(size.dp).background(risk.bg(), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(initials, color = risk.fg(), fontWeight = FontWeight.Bold, fontSize = (size / 3).sp) }
}
