package app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.platform.saveApiBaseUrl
import app.platform.savedApiBaseUrl
import app.ui.components.AppButton
import app.ui.components.AppDialog
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.ButtonVariant
import app.ui.components.StatusPill
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.Space

/**
 * Ajuste del servidor para las apps independientes (un APK por app). Sin servidor cada app usa datos de
 * demostración en su propia memoria; con la dirección del backend, todas comparten pedidos en tiempo real.
 * Se abre desde una pestaña en el margen de la pantalla; conectado, la pestaña lleva un punto verde.
 */
@Composable
fun ServerSettingsButton(isRemote: Boolean, onChanged: () -> Unit) {
    val colors = AppTheme.colors
    var open by remember { mutableStateOf(false) }

    ShellEdgeTab(
        icon = AppIcons.Settings,
        contentDescription = "Dirección del servidor",
        onClick = { open = true },
        dot = if (isRemote) colors.positive else null,
    )

    if (open) {
        var url by remember { mutableStateOf(savedApiBaseUrl() ?: "") }
        val save = {
            val clean = url.trim().trimEnd('/')
            saveApiBaseUrl(
                when {
                    clean.isBlank() -> null
                    clean.startsWith("http://") || clean.startsWith("https://") -> clean
                    else -> "http://$clean"
                },
            )
            open = false
            onChanged()
        }
        AppDialog(onDismiss = { open = false }, title = "Servidor", maxWidth = 480.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                StatusPill(
                    if (isRemote) "Conectado · pedidos en tiempo real" else "Sin servidor · datos de demostración",
                    tone = if (isRemote) Tone.SUCCESS else Tone.NEUTRAL,
                )
                AppText(
                    "Para que el menú, la cocina y el panel compartan pedidos, escribe la dirección de la computadora " +
                        "donde corre el servidor. El teléfono debe estar en la misma red Wi-Fi. " +
                        "Déjala vacía para usar datos de demostración en este dispositivo.",
                    style = AppTheme.type.bodySm,
                    color = colors.inkMuted,
                )
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Dirección del servidor",
                    placeholder = "http://192.168.1.50:8080",
                    leadingIcon = AppIcons.Globe,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(Space.x1))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                    AppButton("Cancelar", onClick = { open = false }, variant = ButtonVariant.GHOST)
                    AppButton("Guardar y conectar", onClick = save, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
