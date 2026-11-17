package app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.ui.components.GlyphIcon
import app.ui.components.StatusPill
import app.ui.components.Tone
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize

/**
 * Ajuste del servidor para las apps independientes (un APK por app). Sin servidor cada app usa datos de
 * demostración en su propia memoria; con la dirección del backend, todas comparten pedidos en tiempo real.
 */
@Composable
fun ServerSettingsButton(isRemote: Boolean, onChanged: () -> Unit) {
    val colors = AppTheme.colors
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    var open by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().padding(start = Space.x2, end = Space.x2, top = Space.x2, bottom = if (compact) 96.dp else Space.x2),
        contentAlignment = if (compact) Alignment.BottomStart else Alignment.CenterEnd,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.9f))
                .border(1.dp, if (isRemote) colors.success else colors.line, CircleShape)
                .pressable(scale = 0.9f) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(AppIcons.Settings, if (isRemote) colors.success else colors.inkMuted, size = 18.dp)
        }
    }

    if (open) {
        var url by remember { mutableStateOf(savedApiBaseUrl() ?: "") }
        AppDialog(onDismiss = { open = false }, title = "Servidor") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                StatusPill(
                    if (isRemote) "Conectado al servidor · pedidos en tiempo real" else "Sin servidor · datos de demostración en este dispositivo",
                    tone = if (isRemote) Tone.SUCCESS else Tone.INFO,
                )
                AppText(
                    "Para que el menú, la cocina y el panel compartan pedidos, escribe la dirección de la computadora " +
                        "donde corre el servidor, por ejemplo http://192.168.1.50:8080 (el teléfono debe estar en la misma red Wi‑Fi). " +
                        "Déjalo vacío para usar datos de demostración.",
                    color = colors.inkMuted,
                )
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "Dirección del servidor",
                    placeholder = "http://192.168.1.50:8080",
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(Space.x1))
                AppButton(
                    "Guardar y conectar",
                    onClick = {
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
                    },
                    fullWidth = true,
                )
                AppButton("Cancelar", onClick = { open = false }, variant = ButtonVariant.GHOST, fullWidth = true)
            }
        }
    }
}
