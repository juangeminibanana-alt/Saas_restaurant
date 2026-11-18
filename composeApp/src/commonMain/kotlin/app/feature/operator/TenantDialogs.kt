package app.feature.operator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.api.CreateTenantRequest
import app.shared.model.Plan
import app.shared.model.TenantStatus
import app.shared.model.TenantSummary
import app.shared.util.formatMoney
import app.shared.util.formatMoneyCompact
import app.shared.util.label
import app.shared.util.newId
import app.shared.util.relativeTime
import app.shared.util.slugify
import app.shell.LocalNavigator
import app.ui.components.AppButton
import app.ui.components.AppDialog
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.ButtonVariant
import app.ui.components.LabelText
import app.ui.components.SelectionMark
import app.ui.components.StatusPill
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.components.flatSurface
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.launch

/** Alto máximo de la zona con scroll dentro de un diálogo, según el alto disponible. */
@Composable
internal fun dialogBodyMaxHeight(reserved: Dp = 260.dp): Dp {
    val available = LocalOpViewportHeight.current - reserved
    return if (available < 220.dp) 220.dp else if (available > 620.dp) 620.dp else available
}

/** Fila de botones al pie de un diálogo: en teléfono se apilan a todo lo ancho. */
@Composable
internal fun DialogButtons(content: @Composable (Modifier) -> Unit) {
    if (LocalWindowSize.current == WindowSize.COMPACT) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            content(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.x3, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content(Modifier)
        }
    }
}

// ───────────────────────── Suspender / reactivar ─────────────────────────

@Composable
internal fun TenantStatusDialog(tenant: TenantSummary, store: OperatorStore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val suspending = remember { tenant.status != TenantStatus.SUSPENDED }
    val name = remember { tenant.name }
    AppDialog(
        onDismiss = { if (!busy) onDismiss() },
        title = if (suspending) "¿Suspender a $name?" else "¿Reactivar a $name?",
        maxWidth = 460.dp,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            AppText(
                if (suspending) {
                    "Su menú dejará de recibir pedidos y su equipo no podrá entrar al panel hasta que lo reactives. No se borra ningún dato."
                } else {
                    "Su menú volverá a recibir pedidos y su equipo recuperará el acceso al panel de inmediato."
                },
                color = AppTheme.colors.inkMuted,
            )
            DialogButtons { m ->
                val confirm: @Composable () -> Unit = {
                    AppButton(
                        if (suspending) "Suspender restaurante" else "Reactivar restaurante",
                        onClick = {
                            busy = true
                            scope.launch {
                                val ok = store.setStatus(tenant, if (suspending) TenantStatus.SUSPENDED else TenantStatus.ACTIVE)
                                busy = false
                                if (ok) onDismiss()
                            }
                        },
                        modifier = m,
                        variant = if (suspending) ButtonVariant.DANGER else ButtonVariant.PRIMARY,
                        loading = busy,
                    )
                }
                val cancel: @Composable () -> Unit = {
                    AppButton("Cancelar", onClick = onDismiss, modifier = m, variant = ButtonVariant.GHOST, enabled = !busy)
                }
                if (LocalWindowSize.current == WindowSize.COMPACT) { confirm(); cancel() } else { cancel(); confirm() }
            }
        }
    }
}

// ───────────────────────── Cambiar plan ─────────────────────────

/** Opción de plan: superficie + filete con la marca de selección en tinta; elegida, el filete pasa a tinta. */
@Composable
internal fun PlanOptionCard(
    plan: Plan,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    val colors = AppTheme.colors
    val ring by animateColorAsState(if (selected) colors.select else colors.line, tween(Motion.FAST), label = "plan-ring")
    Row(
        modifier
            .fillMaxWidth()
            .flatSurface(AppTheme.controlFill, ring, AppTheme.shapes.md)
            .pressable(scale = 0.99f, onClick = onClick)
            .padding(horizontal = Space.x4, vertical = Space.x3),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                AppText(plan.name, Modifier.weight(1f, fill = false), style = AppTheme.type.title, maxLines = 1)
                if (note != null) TagBadge(note) else if (plan.highlighted) TagBadge("Recomendado", tone = Tone.PRIMARY)
            }
            AppText("${formatBps(plan.feeBps)} de comisión por pedido", style = AppTheme.type.bodySm, color = colors.inkMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            AppText(formatMoney(plan.priceCents), style = AppTheme.type.monoMd, maxLines = 1)
            LabelText("al mes")
        }
    }
}

@Composable
internal fun ChangePlanDialog(tenant: TenantSummary, store: OperatorStore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val initial = remember { tenant.planId }
    var selected by remember { mutableStateOf(tenant.planId) }
    var busy by remember { mutableStateOf(false) }
    AppDialog(onDismiss = { if (!busy) onDismiss() }, title = "Cambiar plan", maxWidth = 520.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            AppText(
                "Elige el plan de ${tenant.name}. El nuevo monto aplica desde su siguiente cobro.",
                style = AppTheme.type.bodySm,
                color = AppTheme.colors.inkMuted,
            )
            Column(
                Modifier.fillMaxWidth().heightIn(max = dialogBodyMaxHeight()).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                store.plans.forEach { plan ->
                    PlanOptionCard(
                        plan = plan,
                        selected = plan.id == selected,
                        onClick = { selected = plan.id },
                        note = if (plan.id == initial) "Plan actual" else null,
                    )
                }
            }
            DialogButtons { m ->
                val save: @Composable () -> Unit = {
                    AppButton(
                        "Guardar cambio",
                        onClick = {
                            val plan = store.plans.firstOrNull { it.id == selected }
                            if (plan != null) {
                                busy = true
                                scope.launch {
                                    val ok = store.assignPlan(tenant, plan)
                                    busy = false
                                    if (ok) onDismiss()
                                }
                            }
                        },
                        modifier = m,
                        enabled = selected != initial,
                        loading = busy,
                    )
                }
                val cancel: @Composable () -> Unit = {
                    AppButton("Cancelar", onClick = onDismiss, modifier = m, variant = ButtonVariant.GHOST, enabled = !busy)
                }
                if (LocalWindowSize.current == WindowSize.COMPACT) { save(); cancel() } else { cancel(); save() }
            }
        }
    }
}

// ───────────────────────── Detalle ─────────────────────────

@Composable
internal fun TenantDetailDialog(
    tenant: TenantSummary,
    store: OperatorStore,
    onDismiss: () -> Unit,
    onChangePlan: () -> Unit,
    onToggleStatus: () -> Unit,
    onOpenPublic: () -> Unit,
) {
    val plan = store.plans.firstOrNull { it.id == tenant.planId }
    val suspended = tenant.status == TenantStatus.SUSPENDED
    AppDialog(onDismiss = onDismiss, title = "Detalle del restaurante", maxWidth = 580.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = dialogBodyMaxHeight()).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x5),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x4), verticalAlignment = Alignment.CenterVertically) {
                    TenantMonogram(tenant.name, tenant.primary, size = 52.dp)
                    Column(Modifier.weight(1f)) {
                        AppText(tenant.name, style = AppTheme.type.h2, maxLines = 2)
                        AppText("/r/${tenant.slug}", style = AppTheme.type.mono, color = AppTheme.colors.inkMuted, maxLines = 1)
                    }
                    StatusPill(tenant.status.label(), tone = tenant.status.tone())
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    DetailFigure("Pedidos · 30 d", formatCount(tenant.orders30d), Modifier.weight(1f))
                    DetailFigure("Ventas · 30 d", formatMoneyCompact(tenant.gmv30dCents), Modifier.weight(1f))
                    DetailFigure("MRR", formatMoney(tenant.mrrCents), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                    OpKeyValue("Dueño", tenant.ownerName.ifBlank { "Sin registrar" }, Modifier.weight(1f))
                    OpKeyValue("Correo", tenant.ownerEmail.ifBlank { "Sin registrar" }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                    OpKeyValue(
                        "Plan",
                        if (plan != null) "${tenant.planName} · ${formatMoney(plan.priceCents)} al mes" else tenant.planName,
                        Modifier.weight(1f),
                    )
                    OpKeyValue(
                        "Comisión por pedido",
                        if (plan != null) formatBps(plan.feeBps) else "Según plan",
                        Modifier.weight(1f),
                        mono = plan != null,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                    OpKeyValue(
                        "Alta",
                        if (tenant.createdAt > 0) "${formatDate(tenant.createdAt)} · ${relativeTime(tenant.createdAt)}" else "Sin fecha",
                        Modifier.weight(1f),
                    )
                    OpKeyValue(
                        "Mercado Pago",
                        if (tenant.mpConnected) "Cuenta conectada" else "Sin conectar",
                        Modifier.weight(1f),
                    )
                }
                OpCopyRow("Link público del menú", "/#/r/${tenant.slug}")
            }
            AppDivider()
            DialogButtons { m ->
                AppButton("Abrir menú", onClick = onOpenPublic, modifier = m, variant = ButtonVariant.GHOST, icon = AppIcons.ExternalLink)
                AppButton("Cambiar plan", onClick = onChangePlan, modifier = m, variant = ButtonVariant.SECONDARY)
                AppButton(
                    if (suspended) "Reactivar" else "Suspender",
                    onClick = onToggleStatus,
                    modifier = m,
                    variant = if (suspended) ButtonVariant.PRIMARY else ButtonVariant.DANGER,
                )
            }
        }
    }
}

/** Cifra del detalle: mosaico con filete, etiqueta en mono y el número en mono 20/24. */
@Composable
private fun DetailFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .flatSurface(AppTheme.controlFill, AppTheme.colors.line, AppTheme.shapes.md)
            .padding(horizontal = Space.x3, vertical = Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        LabelText(label)
        AppText(value, style = AppTheme.type.monoLg, maxLines = 1)
    }
}

// ───────────────────────── Crear restaurante ─────────────────────────

private fun isValidEmail(value: String): Boolean {
    val v = value.trim()
    val at = v.indexOf('@')
    return at > 0 && v.indexOf('.', at) > at + 1 && !v.endsWith(".") && v.none { it.isWhitespace() }
}

@Composable
private fun FormPair(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    if (LocalWindowSize.current == WindowSize.COMPACT) {
        a(Modifier.fillMaxWidth())
        b(Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x3), verticalAlignment = Alignment.Top) {
            a(Modifier.weight(1f))
            b(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun CreateTenantDialog(store: OperatorStore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val navigator = LocalNavigator.current

    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var slugEdited by remember { mutableStateOf(false) }
    var cuisine by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var ownerEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(newId("Mesa")) }
    var planId by remember { mutableStateOf((store.plans.firstOrNull { it.highlighted } ?: store.plans.firstOrNull())?.id) }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<TenantSummary?>(null) }

    val nameError = if (name.isBlank()) "Escribe el nombre del restaurante" else null
    val slugError = when {
        slug.length < 3 -> "Usa al menos 3 letras o números"
        store.tenants.any { it.slug == slug } -> "Ese link ya está en uso"
        else -> null
    }
    val ownerError = if (ownerName.isBlank()) "Escribe el nombre del dueño" else null
    val emailError = if (!isValidEmail(ownerEmail)) "Escribe un correo válido" else null
    val passwordError = if (password.length < 8) "Usa al menos 8 caracteres" else null
    val planError = if (planId == null) "Elige un plan" else null
    val valid = listOf(nameError, slugError, ownerError, emailError, passwordError, planError).all { it == null }

    AppDialog(
        onDismiss = { if (!busy) onDismiss() },
        title = if (created == null) "Crear restaurante" else "Restaurante creado",
        maxWidth = 600.dp,
    ) {
        AnimatedContent(
            targetState = created != null,
            transitionSpec = {
                fadeIn(tween(Motion.SLOW, delayMillis = Motion.FAST, easing = Motion.standard)) togetherWith fadeOut(tween(Motion.FAST))
            },
            label = "create-tenant",
        ) { done ->
            val result = created
            if (done && result != null) {
                CreatedTenantSummary(
                    tenant = result,
                    email = ownerEmail.trim(),
                    password = password,
                    onOpen = { onDismiss(); navigator.navigate("/r/${result.slug}") },
                    onDone = onDismiss,
                )
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = dialogBodyMaxHeight()).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Space.x4),
                    ) {
                        FormPair(
                            a = { m ->
                                AppTextField(
                                    value = name,
                                    onValueChange = {
                                        name = it
                                        if (!slugEdited) slug = slugify(it)
                                    },
                                    modifier = m,
                                    label = "Nombre del restaurante",
                                    placeholder = "Ej. Fonda del Centro",
                                    leadingIcon = AppIcons.Store,
                                    error = nameError.takeIf { submitted },
                                )
                            },
                            b = { m ->
                                AppTextField(
                                    value = cuisine,
                                    onValueChange = { cuisine = it },
                                    modifier = m,
                                    label = "Tipo de cocina",
                                    placeholder = "Ej. Mexicana, cafetería, sushi",
                                    leadingIcon = AppIcons.Chef,
                                )
                            },
                        )
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                            AppTextField(
                                value = slug,
                                onValueChange = { raw ->
                                    slugEdited = true
                                    slug = raw.lowercase().filter { c -> c in 'a'..'z' || c in '0'..'9' || c == '-' }.take(40)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = "Link del menú",
                                placeholder = "fonda-del-centro",
                                leadingIcon = AppIcons.Link,
                                error = slugError.takeIf { submitted },
                            )
                            AppText(
                                if (slug.isBlank()) "Se genera solo a partir del nombre; puedes ajustarlo." else "Link público: /#/r/$slug",
                                style = AppTheme.type.bodySm,
                                color = AppTheme.colors.inkSubtle,
                            )
                        }
                        FormPair(
                            a = { m ->
                                AppTextField(
                                    value = ownerName,
                                    onValueChange = { ownerName = it },
                                    modifier = m,
                                    label = "Nombre del dueño",
                                    placeholder = "Nombre y apellido",
                                    leadingIcon = AppIcons.Person,
                                    error = ownerError.takeIf { submitted },
                                )
                            },
                            b = { m ->
                                AppTextField(
                                    value = ownerEmail,
                                    onValueChange = { ownerEmail = it.trim() },
                                    modifier = m,
                                    label = "Correo del dueño",
                                    placeholder = "dueno@restaurante.mx",
                                    leadingIcon = AppIcons.Mail,
                                    error = emailError.takeIf { submitted },
                                    keyboardType = KeyboardType.Email,
                                )
                            },
                        )
                        AppTextField(
                            value = password,
                            onValueChange = { password = it.filter { c -> !c.isWhitespace() } },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Contraseña inicial",
                            placeholder = "Mínimo 8 caracteres",
                            leadingIcon = AppIcons.Lock,
                            error = passwordError.takeIf { submitted },
                        )
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                            LabelText("Plan")
                            if (store.plans.isEmpty()) {
                                AppText(
                                    "Aún no hay planes. Crea uno en la sección Planes.",
                                    style = AppTheme.type.bodySm,
                                    color = AppTheme.colors.danger,
                                )
                            }
                            store.plans.forEach { plan ->
                                PlanOptionCard(plan = plan, selected = plan.id == planId, onClick = { planId = plan.id })
                            }
                        }
                    }
                    DialogButtons { m ->
                        val create: @Composable () -> Unit = {
                            AppButton(
                                "Crear restaurante",
                                onClick = {
                                    submitted = true
                                    val chosen = planId
                                    if (valid && chosen != null) {
                                        busy = true
                                        scope.launch {
                                            val result = store.createTenant(
                                                CreateTenantRequest(
                                                    name = name.trim(),
                                                    slug = slug.trim('-'),
                                                    cuisine = cuisine.trim(),
                                                    ownerName = ownerName.trim(),
                                                    ownerEmail = ownerEmail.trim(),
                                                    ownerPassword = password,
                                                    planId = chosen,
                                                ),
                                            )
                                            busy = false
                                            if (result != null) created = result
                                        }
                                    }
                                },
                                modifier = m,
                                icon = AppIcons.Add,
                                loading = busy,
                            )
                        }
                        val cancel: @Composable () -> Unit = {
                            AppButton("Cancelar", onClick = onDismiss, modifier = m, variant = ButtonVariant.GHOST, enabled = !busy)
                        }
                        if (LocalWindowSize.current == WindowSize.COMPACT) { create(); cancel() } else { cancel(); create() }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatedTenantSummary(
    tenant: TenantSummary,
    email: String,
    password: String,
    onOpen: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = dialogBodyMaxHeight()).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x4), verticalAlignment = Alignment.CenterVertically) {
                // Confirmación plana: cuadro con filete y la palomita en verde. Sin rebote.
                Box(
                    Modifier.size(Sizes.controlLg).flatSurface(AppTheme.controlFill, AppTheme.colors.lineStrong, AppTheme.shapes.md),
                    contentAlignment = Alignment.Center,
                ) {
                    OpIcon(AppIcons.Check, tint = AppTheme.colors.positive, size = Sizes.icon)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                    AppText("${tenant.name} ya tiene su menú en línea", style = AppTheme.type.h2)
                    AppText(
                        "Plan ${tenant.planName}. Comparte el link y las credenciales con el dueño.",
                        style = AppTheme.type.bodySm,
                        color = AppTheme.colors.inkMuted,
                    )
                }
            }
            OpCopyRow("Link público del menú", "/#/r/${tenant.slug}")
            OpCopyRow("Correo del dueño", tenant.ownerEmail.ifBlank { email })
            OpCopyRow("Contraseña inicial", password)
            OpNotice("Entrega la contraseña por un canal seguro. El dueño entra al panel con estos datos.", icon = AppIcons.Lock)
        }
        DialogButtons { m ->
            val open: @Composable () -> Unit = {
                AppButton("Abrir menú", onClick = onOpen, modifier = m, variant = ButtonVariant.SECONDARY, icon = AppIcons.ExternalLink)
            }
            val done: @Composable () -> Unit = { AppButton("Listo", onClick = onDone, modifier = m) }
            if (LocalWindowSize.current == WindowSize.COMPACT) { done(); open() } else { open(); done() }
        }
    }
}
