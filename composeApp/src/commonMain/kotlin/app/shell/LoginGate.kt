package app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shared.data.RepositoryException
import app.shared.demo.DemoData
import app.shared.model.Role
import app.shared.model.UserSession
import app.shared.util.label
import app.ui.components.AppButton
import app.ui.components.AppCard
import app.ui.components.AppChip
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.GlyphTile
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
            (fadeIn(tween(Motion.SLOW, easing = Motion.standard)) +
                slideInVertically(tween(Motion.SLOW, easing = Motion.standard)) { it / 28 }) togetherWith
                fadeOut(tween(Motion.FAST, easing = Motion.exit))
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

@OptIn(ExperimentalLayoutApi::class)
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

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.HERO, easing = Motion.standard)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x4, vertical = if (compact) Space.x8 else Space.x14),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 432.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear.value
                    translationY = 18.dp.toPx() * (1f - appear.value)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppCard(modifier = Modifier.fillMaxWidth(), padding = if (compact) Space.x5 else Space.x8, elevated = true) {
                GlyphTile(AppIcons.Lock, tint = colors.primary, background = colors.primarySoft, size = 44.dp)
                Spacer(Modifier.height(Space.x5))
                AppText(title, style = AppTheme.type.h2)
                Spacer(Modifier.height(Space.x1))
                AppText(subtitle, style = AppTheme.type.body, color = colors.inkMuted)

                if (blockedSession != null) {
                    Spacer(Modifier.height(Space.x4))
                    Row(
                        Modifier.fillMaxWidth().background(colors.warningSoft, AppTheme.shapes.sm).padding(Space.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            "Tu sesión como ${blockedSession.name} (${blockedSession.role.label()}) no tiene acceso a esta sección. Entra con otra cuenta.",
                            modifier = Modifier.weight(1f),
                            style = AppTheme.type.bodySm,
                            color = colors.ink,
                        )
                    }
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
                    Row(
                        Modifier.padding(top = Space.x4).fillMaxWidth().background(colors.dangerSoft, AppTheme.shapes.sm).padding(Space.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(error ?: "", modifier = Modifier.weight(1f), style = AppTheme.type.bodySm, color = colors.danger)
                    }
                }

                Spacer(Modifier.height(Space.x5))
                AppButton(
                    text = "Entrar",
                    onClick = { signIn(email, password) },
                    size = ButtonSize.LG,
                    loading = loading && loadingEmail == email,
                    enabled = !loading || loadingEmail == email,
                    fullWidth = true,
                )

                if (demoAccounts.isNotEmpty()) {
                    Spacer(Modifier.height(Space.x6))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
                        AppText(
                            "Cuentas de demostración",
                            modifier = Modifier.padding(horizontal = Space.x3),
                            style = AppTheme.type.label,
                            color = colors.inkSubtle,
                        )
                        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
                    }
                    Spacer(Modifier.height(Space.x4))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.x2, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(Space.x2),
                    ) {
                        demoAccounts.forEach { (label, accountEmail) ->
                            AppChip(
                                text = label,
                                selected = loadingEmail == accountEmail,
                                onClick = {
                                    email = accountEmail
                                    password = DemoData.DEMO_PASSWORD
                                    signIn(accountEmail, DemoData.DEMO_PASSWORD)
                                },
                                icon = AppIcons.Person,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.x3))
                    AppText(
                        "Un toque para entrar · contraseña ${DemoData.DEMO_PASSWORD}",
                        modifier = Modifier.fillMaxWidth(),
                        style = AppTheme.type.label,
                        color = colors.inkSubtle,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(Space.x4))
            AppButton("Salir", onClick = onExit, variant = ButtonVariant.GHOST, size = ButtonSize.SM, icon = AppIcons.Back)
            Spacer(Modifier.width(Space.x1))
        }
    }
}

@Suppress("unused")
private val unusedSize = Modifier.size(0.dp)
