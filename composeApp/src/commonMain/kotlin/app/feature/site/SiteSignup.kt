package app.feature.site

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.platform.openExternalUrl
import app.shared.api.SignupRequest
import app.shared.api.SignupResponse
import app.shared.data.RepositoryException
import app.shared.model.Plan
import app.shared.util.formatMoney
import app.shared.util.slugify
import app.shell.LocalRepo
import app.ui.components.AppButton
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.ErrorState
import app.ui.components.LoadingState
import app.ui.components.TagBadge
import app.ui.components.Toasts
import app.ui.components.Tone
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

// ───────────────────────── 8. Registro en tres pasos ─────────────────────────

private fun isValidEmail(raw: String): Boolean {
    val value = raw.trim()
    val at = value.indexOf('@')
    if (at <= 0 || at != value.lastIndexOf('@') || ' ' in value) return false
    val domain = value.substring(at + 1)
    return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
}

@Composable
internal fun SignupFlow(
    plans: List<Plan>?,
    plansError: String?,
    initialPlanId: String?,
    maxHeight: Dp,
    onRetryPlans: () -> Unit,
    onClose: () -> Unit,
    onNavigate: (route: String) -> Unit,
) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors

    var step by remember { mutableIntStateOf(0) }
    var restaurantName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var planId by remember { mutableStateOf(initialPlanId) }
    var showErrors by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<SignupResponse?>(null) }

    val selectedPlan = plans?.firstOrNull { it.id == planId }
        ?: plans?.firstOrNull { it.highlighted }
        ?: plans?.firstOrNull()

    val nameError = if (restaurantName.trim().length < 2) "Escribe el nombre de tu restaurante" else null
    val ownerError = if (ownerName.trim().length < 2) "Escribe tu nombre" else null
    val emailError = if (!isValidEmail(email)) "Escribe un correo válido" else null
    val passwordError = if (password.length < 8) "Usa al menos 8 caracteres" else null

    fun submit(plan: Plan) {
        if (loading) return
        scope.launch {
            loading = true
            submitError = null
            try {
                result = repo.signup(
                    SignupRequest(
                        restaurantName = restaurantName.trim(),
                        ownerName = ownerName.trim(),
                        email = email.trim(),
                        password = password,
                        planId = plan.id,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = (e as? RepositoryException)?.message ?: "No pudimos crear tu restaurante. Intenta de nuevo."
                submitError = message
                Toasts.show(message, Tone.DANGER)
            } finally {
                loading = false
            }
        }
    }

    val next: () -> Unit = {
        when (step) {
            0 -> if (nameError == null && ownerError == null) {
                showErrors = false
                step = 1
            } else {
                showErrors = true
            }
            1 -> if (emailError == null && passwordError == null) {
                showErrors = false
                step = 2
            } else {
                showErrors = true
            }
            else -> selectedPlan?.let { submit(it) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(Space.x5),
    ) {
        val done = result
        if (done != null) {
            SignupSuccess(response = done, onNavigate = onNavigate, onClose = onClose)
            return@Column
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Paso ${step + 1} de 3")
                Spacer(Modifier.height(Space.x1))
                AppText(
                    text = when (step) {
                        0 -> "Crea tu restaurante"
                        1 -> "Tu cuenta de acceso"
                        else -> "Elige tu plan"
                    },
                    style = AppTheme.type.h2,
                )
            }
            AppIconButton(icon = AppIcons.Close, onClick = onClose, contentDescription = "Cerrar")
        }
        Spacer(Modifier.height(Space.x4))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            repeat(3) { i ->
                val fill by animateFloatAsState(if (i <= step) 1f else 0f, tween(Motion.SLOW, easing = Motion.standard))
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(c.line)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(c.primary))
                }
            }
        }
        Spacer(Modifier.height(Space.x6))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(Motion.SLOW, easing = Motion.standard)) { it / 5 * direction } +
                    fadeIn(tween(Motion.SLOW))) togetherWith
                    (slideOutHorizontally(tween(Motion.BASE, easing = Motion.exit)) { -it / 5 * direction } +
                        fadeOut(tween(Motion.FAST)))
            },
        ) { current ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                when (current) {
                    0 -> {
                        AppTextField(
                            value = restaurantName,
                            onValueChange = { restaurantName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Nombre de tu restaurante",
                            placeholder = "Ej. Taquería La Esquina",
                            leadingIcon = AppIcons.Store,
                            error = if (showErrors) nameError else null,
                        )
                        val slug = slugify(restaurantName)
                        if (slug.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                                SiteIcon(AppIcons.Link, tint = c.inkSubtle, size = 16.dp)
                                AppText("Tu menú vivirá en /#/r/$slug", style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1)
                            }
                        }
                        AppTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Tu nombre",
                            placeholder = "Nombre y apellido",
                            leadingIcon = AppIcons.Person,
                            error = if (showErrors) ownerError else null,
                        )
                    }
                    1 -> {
                        AppTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Correo electrónico",
                            placeholder = "tu@correo.com",
                            leadingIcon = AppIcons.Mail,
                            error = if (showErrors) emailError else null,
                            keyboardType = KeyboardType.Email,
                        )
                        AppTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Contraseña",
                            placeholder = "Mínimo 8 caracteres",
                            leadingIcon = AppIcons.Lock,
                            isPassword = true,
                            error = if (showErrors) passwordError else null,
                            keyboardType = KeyboardType.Password,
                        )
                        AppText(
                            "Con este correo y contraseña entrarás al panel de tu restaurante.",
                            style = AppTheme.type.bodySm,
                            color = c.inkSubtle,
                        )
                    }
                    else -> {
                        when {
                            plans == null && plansError != null -> ErrorState(message = plansError, onRetry = onRetryPlans)
                            plans == null -> LoadingState(Modifier.fillMaxWidth().height(160.dp), message = "Cargando planes…")
                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                                    plans.forEach { plan ->
                                        PlanOption(plan, selected = plan.id == selectedPlan?.id) { planId = plan.id }
                                    }
                                }
                                if (selectedPlan != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(c.surfaceSunken, AppTheme.shapes.md)
                                            .padding(Space.x4),
                                        horizontalArrangement = Arrangement.spacedBy(Space.x3),
                                    ) {
                                        SiteIcon(AppIcons.CreditCard, tint = c.inkMuted, size = 20.dp)
                                        AppText(
                                            text = "Primer mes de prueba, después ${formatMoney(selectedPlan.priceCents)}/mes " +
                                                "con Mercado Pago",
                                            modifier = Modifier.weight(1f),
                                            style = AppTheme.type.bodySm,
                                        )
                                    }
                                }
                            }
                        }
                        val failure = submitError
                        if (failure != null) AppText(failure, style = AppTheme.type.bodySm, color = c.danger)
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.x6))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                AppButton(
                    text = "Atrás",
                    onClick = {
                        showErrors = false
                        step -= 1
                    },
                    variant = ButtonVariant.GHOST,
                    icon = AppIcons.Back,
                    enabled = !loading,
                )
            }
            Spacer(Modifier.weight(1f))
            AppButton(
                text = if (step < 2) "Continuar" else "Crear mi restaurante",
                onClick = next,
                size = ButtonSize.LG,
                enabled = step < 2 || selectedPlan != null,
                loading = loading,
            )
        }
    }
}

@Composable
private fun PlanOption(plan: Plan, selected: Boolean, onSelect: () -> Unit) {
    val c = AppTheme.colors
    val shape = AppTheme.shapes.md
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(scale = 0.99f) { onSelect() }
            .clip(shape)
            .background(if (selected) c.primarySoft else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.line, shape)
            .heightIn(min = 64.dp)
            .padding(Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Box(
            modifier = Modifier.size(20.dp).border(2.dp, if (selected) c.primary else c.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(c.primary, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                AppText(plan.name, style = AppTheme.type.title)
                if (plan.highlighted) TagBadge("Más popular", tone = Tone.PRIMARY)
            }
            AppText("+ ${formatBps(plan.feeBps)} por pedido en línea", style = AppTheme.type.bodySm, color = c.inkMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            AppText(formatMoney(plan.priceCents), style = AppTheme.type.title)
            AppText("/mes", style = AppTheme.type.label, color = c.inkMuted)
        }
    }
}

// ───────────────────────── Éxito ─────────────────────────

@Composable
private fun SignupSuccess(response: SignupResponse, onNavigate: (route: String) -> Unit, onClose: () -> Unit) {
    val c = AppTheme.colors
    val slug = response.tenant.slug
    val checkoutUrl = response.subscriptionCheckoutUrl
    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear = true }
    val progress by animateFloatAsState(
        targetValue = if (appear) 1f else 0f,
        animationSpec = tween(Motion.HERO, delayMillis = 180, easing = Motion.standard),
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppIconButton(icon = AppIcons.Close, onClick = onClose, contentDescription = "Cerrar")
        }
        SuccessCheck()
        Spacer(Modifier.height(Space.x5))
        Column(
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 20.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText("¡${response.tenant.name} ya está en línea!", style = AppTheme.type.h2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.x2))
            AppText(
                text = "Tu primer mes de prueba ya empezó. Este es el link de tu menú:",
                color = c.inkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.x5))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable(scale = 0.99f) { onNavigate("/r/$slug") }
                    .clip(AppTheme.shapes.md)
                    .background(c.surfaceSunken)
                    .border(1.dp, c.line, AppTheme.shapes.md)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SiteIcon(AppIcons.Link, tint = c.inkMuted, size = 18.dp)
                Spacer(Modifier.width(Space.x3))
                AppText("/#/r/$slug", modifier = Modifier.weight(1f), style = AppTheme.type.title, maxLines = 1)
                Spacer(Modifier.width(Space.x3))
                SiteIcon(AppIcons.ExternalLink, tint = c.inkSubtle, size = 18.dp)
            }
            Spacer(Modifier.height(Space.x6))
            AppButton("Ir a mi panel", onClick = { onNavigate("/panel") }, size = ButtonSize.LG, fullWidth = true)
            if (checkoutUrl != null) {
                Spacer(Modifier.height(Space.x3))
                AppButton(
                    text = "Activar suscripción en Mercado Pago",
                    onClick = { openExternalUrl(checkoutUrl) },
                    variant = ButtonVariant.SECONDARY,
                    size = ButtonSize.LG,
                    icon = AppIcons.CreditCard,
                    fullWidth = true,
                )
            }
        }
    }
}

/** Palomita con rebote y un anillo que se expande. */
@Composable
private fun SuccessCheck() {
    val c = AppTheme.colors
    val scale = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, Motion.bouncy()) }
        ring.animateTo(1f, tween(900, easing = Motion.standard))
    }
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(72.dp)
                .graphicsLayer {
                    val grow = 1f + ring.value * 0.45f
                    scaleX = grow
                    scaleY = grow
                    alpha = (1f - ring.value) * 0.45f
                }
                .background(c.success, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(c.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SiteIcon(AppIcons.Check, tint = c.surface, size = 36.dp)
        }
    }
}
