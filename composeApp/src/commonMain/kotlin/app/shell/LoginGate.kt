package app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.shared.data.RepositoryException
import app.shared.demo.DemoData
import app.shared.model.Role
import app.shared.model.UserSession
import app.shared.util.label
import app.ui.components.AppButton
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.ButtonSize
import app.ui.components.GlyphIcon
import app.ui.components.LabelText
import app.ui.components.OptionList
import app.ui.components.SpinnerArc
import app.ui.components.StatusDot
import app.ui.components.edgeTop
import app.ui.components.flatSurface
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Ancho máximo de la columna de acceso. */
private val LoginMaxWidth = 400.dp

/**
 * Muestra el acceso (con cuentas demo de un toque) hasta tener sesión con un rol permitido;
 * entonces dibuja `content(session)`.
 */
@Composable
fun LoginGate(
    allowedRoles: Set<Role>, title: String, subtitle: String, demoAccounts: List<Pair<String, String>>,
    onExit: () -> Unit, content: @Composable (UserSession) -> Unit,
) {
    val repo = LocalRepo.current
    val session by repo.session.collectAsState()
    val allowed = session?.takeIf { it.role in allowedRoles }

    AnimatedContent(
        targetState = allowed,
        contentKey = { it?.userId },
        transitionSpec = {
            fadeIn(tween(Motion.BASE, easing = Motion.standard)) togetherWith fadeOut(tween(Motion.FAST, easing = Motion.exit))
        },
        label = "loginGate",
    ) { current ->
        if (current != null) {
            content(current)
        } else {
            LoginScreen(
                title = title,
                subtitle = subtitle,
                demoAccounts = demoAccounts,
                blockedSession = session?.takeIf { it.role !in allowedRoles },
                onExit = onExit,
            )
        }
    }
}

/**
 * Acceso en NOCHE: todo sobre el fondo, sin tarjeta. Antetítulo en mono, título 32/36, dos campos y UNA acción
 * en brasa ("Entrar"). Las cuentas de demostración son una lista de filas con filete: un toque entra.
 */
@Composable
private fun LoginScreen(
    title: String,
    subtitle: String,
    demoAccounts: List<Pair<String, String>>,
    blockedSession: UserSession?,
    onExit: () -> Unit,
) {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val compact = LocalWindowSize.current == WindowSize.COMPACT

    var email by remember { mutableStateOf(demoAccounts.firstOrNull()?.second ?: "") }
    var password by remember { mutableStateOf(DemoData.DEMO_PASSWORD) }
    var loading by remember { mutableStateOf(false) }
    var loadingEmail by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun signIn(withEmail: String, withPassword: String) {
        if (loading) return
        if (withEmail.isBlank() || withPassword.isBlank()) {
            error = "Escribe tu correo y tu contraseña."
            return
        }
        loading = true
        loadingEmail = withEmail
        error = null
        scope.launch {
            try {
                repo.login(withEmail.trim(), withPassword)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RepositoryException) {
                error = e.message ?: "No pudimos iniciar sesión."
            } catch (e: Exception) {
                error = "No pudimos iniciar sesión. Revisa tu conexión e inténtalo de nuevo."
            } finally {
                loading = false
                loadingEmail = null
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.gutter, vertical = if (compact) Space.x4 else Space.x14),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = LoginMaxWidth).fillMaxWidth()) {
            AppIconButton(AppIcons.Back, onClick = onExit, contentDescription = "Salir")
            Spacer(Modifier.height(if (compact) Space.x6 else Space.x10))
            LabelText("Acceso", color = colors.inkMuted)
            Spacer(Modifier.height(Space.x1))
            AppText(title, style = AppTheme.type.h1)
            Spacer(Modifier.height(Space.x2))
            AppText(subtitle, style = AppTheme.type.body, color = colors.inkMuted)

            if (blockedSession != null) {
                Spacer(Modifier.height(Space.x4))
                LoginNotice(
                    "Tu sesión como ${blockedSession.name} (${blockedSession.role.label()}) no tiene acceso a esta sección. Entra con otra cuenta.",
                    dot = colors.warning,
                )
            }

            Spacer(Modifier.height(Space.x6))
            AppTextField(
                value = email,
                onValueChange = { email = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = "Correo",
                placeholder = "tu@correo.com",
                leadingIcon = AppIcons.Mail,
                keyboardType = KeyboardType.Email,
                enabled = !loading,
            )
            Spacer(Modifier.height(Space.x4))
            AppTextField(
                value = password,
                onValueChange = { password = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                label = "Contraseña",
                placeholder = "Tu contraseña",
                leadingIcon = AppIcons.Lock,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                enabled = !loading,
            )

            AnimatedVisibility(
                visible = error != null,
                enter = expandVertically(tween(Motion.BASE)) + fadeIn(tween(Motion.BASE)),
                exit = shrinkVertically(tween(Motion.FAST)) + fadeOut(tween(Motion.FAST)),
            ) {
                LoginNotice(error ?: "", dot = colors.danger, modifier = Modifier.padding(top = Space.x4))
            }

            Spacer(Modifier.height(Space.x5))
            AppButton(
                text = "Entrar",
                onClick = { signIn(email, password) },
                size = ButtonSize.LG,
                loading = loading && loadingEmail == email,
                enabled = !loading || loadingEmail == email,
                fullWidth = true,
                trailingIcon = AppIcons.Forward,
            )

            if (demoAccounts.isNotEmpty()) {
                Spacer(Modifier.height(Space.x8))
                Row(Modifier.fillMaxWidth().padding(bottom = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                    LabelText("Cuentas de demostración", modifier = Modifier.weight(1f))
                    LabelText("Contraseña ${DemoData.DEMO_PASSWORD}")
                }
                OptionList {
                    demoAccounts.forEachIndexed { index, (label, accountEmail) ->
                        DemoAccountRow(
                            label = label,
                            email = accountEmail,
                            first = index == 0,
                            busy = loadingEmail == accountEmail,
                            enabled = !loading,
                            onClick = {
                                email = accountEmail
                                password = DemoData.DEMO_PASSWORD
                                signIn(accountEmail, DemoData.DEMO_PASSWORD)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Space.x3))
                AppText("Un toque para entrar.", style = AppTheme.type.bodySm, color = colors.inkSubtle)
            }
            Spacer(Modifier.height(Space.x8))
        }
    }
}

/** Fila de una cuenta de demostración: nombre, correo en mono y flecha. Un toque inicia sesión. */
@Composable
private fun DemoAccountRow(label: String, email: String, first: Boolean, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizes.controlXl)
            .then(if (first) Modifier else Modifier.edgeTop(colors.line))
            .pressable(enabled = enabled, scale = 0.99f, onClick = onClick)
            .padding(horizontal = Space.x4, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            AppText(label, style = AppTheme.type.titleSm, maxLines = 1)
            AppText(email, style = AppTheme.type.mono, color = colors.inkMuted, maxLines = 1)
        }
        Spacer(Modifier.width(Space.x3))
        if (busy) {
            SpinnerArc(color = colors.ink, size = Sizes.icon)
        } else {
            GlyphIcon(AppIcons.Forward, tint = colors.inkSubtle, size = Sizes.icon)
        }
    }
}

/** Aviso plano: superficie + filete con un punto del tono; el texto va en tinta. */
@Composable
private fun LoginNotice(text: String, dot: Color, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier.fillMaxWidth().flatSurface(colors.surface, colors.line, AppTheme.shapes.md).padding(Space.x3),
        verticalAlignment = Alignment.Top,
    ) {
        StatusDot(dot, Modifier.padding(top = Space.x2 - 1.dp))
        Spacer(Modifier.width(Space.x2))
        AppText(text, modifier = Modifier.weight(1f), style = AppTheme.type.bodySm)
    }
}
