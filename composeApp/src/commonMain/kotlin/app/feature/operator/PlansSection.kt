package app.feature.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.shared.model.Plan
import app.shared.util.formatMoney
import app.shared.util.newId
import app.ui.components.AppButton
import app.ui.components.AppDialog
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppSwitch
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.ButtonVariant
import app.ui.components.EmptyState
import app.ui.components.ErrorState
import app.ui.components.PageHeader
import app.ui.components.Skeleton
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val UNLIMITED_LOCATIONS = 99

@Composable
internal fun PlansSection(store: OperatorStore, onReload: () -> Unit) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    var editing by remember { mutableStateOf<Plan?>(null) }
    var creating by remember { mutableStateOf(false) }
    val plans = store.plans

    OpPage {
        PageHeader(
            title = "Planes",
            subtitle = "Precio mensual y comisión por pedido de cada plan",
            actions = {
                AppButton(
                    if (compact) "Nuevo" else "Nuevo plan",
                    onClick = { creating = true },
                    icon = AppIcons.Add,
                    enabled = store.loaded,
                )
            },
        )
        when {
            !store.loaded && store.error != null -> ErrorState(store.error ?: "", Modifier.fillMaxWidth(), onRetry = onReload)
            !store.loaded -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                OpGrid((1..3).toList(), planColumns(maxWidth >= 920.dp, maxWidth >= 600.dp)) {
                    Skeleton(Modifier.fillMaxWidth().height(380.dp), shape = AppTheme.shapes.lg)
                }
            }
            plans.isEmpty() -> EmptyState(
                title = "Aún no hay planes",
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.x10),
                message = "Crea el primer plan para poder dar de alta restaurantes.",
                icon = AppIcons.Tag,
                actionText = "Nuevo plan",
                onAction = { creating = true },
            )
            else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                OpGrid(plans, planColumns(maxWidth >= 920.dp, maxWidth >= 600.dp), equalHeight = true) { plan ->
                    PlanCard(
                        plan = plan,
                        tenantCount = store.tenants.count { it.planId == plan.id },
                        onEdit = { editing = plan },
                    )
                }
            }
        }
        if (store.loaded && plans.isNotEmpty()) {
            OpNotice("La comisión se cobra en cada pedido pagado con Mercado Pago (marketplace_fee) y se suma a la mensualidad.")
        }
    }

    val target = editing
    if (target != null) {
        PlanEditorDialog(plan = target, isNew = false, store = store, onDismiss = { editing = null })
    } else if (creating) {
        PlanEditorDialog(
            plan = remember { Plan(id = newId("plan"), name = "", priceCents = 0, feeBps = 150) },
            isNew = true,
            store = store,
            onDismiss = { creating = false },
        )
    }
}

private fun planColumns(wide: Boolean, medium: Boolean): Int = if (wide) 3 else if (medium) 2 else 1

@Composable
private fun PlanCard(plan: Plan, tenantCount: Int, onEdit: () -> Unit) {
    val colors = AppTheme.colors
    OpPanel(Modifier.fillMaxSize(), borderColor = if (plan.highlighted) colors.primary else colors.line) {
        Column(Modifier.fillMaxSize().padding(Space.x6), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                AppText(plan.name.ifBlank { "Sin nombre" }, Modifier.weight(1f), style = AppTheme.type.h3, maxLines = 1)
                if (plan.highlighted) TagBadge("Destacado", tone = Tone.PRIMARY)
            }
            if (plan.description.isNotBlank()) {
                AppText(plan.description, style = AppTheme.type.bodySm, color = colors.inkMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x1), verticalAlignment = Alignment.Bottom) {
                AppText(formatMoney(plan.priceCents), style = AppTheme.type.h1, maxLines = 1)
                AppText("/mes", Modifier.padding(bottom = Space.x1), style = AppTheme.type.body, color = colors.inkMuted)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                OpKeyValue("Comisión por pedido", formatBps(plan.feeBps), Modifier.weight(1f))
                OpKeyValue(
                    "Sucursales",
                    if (plan.maxLocations >= UNLIMITED_LOCATIONS) "Ilimitadas" else "Hasta ${plan.maxLocations}",
                    Modifier.weight(1f),
                )
            }
            AppDivider()
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                plan.features.forEach { feature ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.Top) {
                        OpIcon(AppIcons.Check, tint = colors.success, size = 16.dp, modifier = Modifier.padding(top = 2.dp))
                        AppText(feature, Modifier.weight(1f), style = AppTheme.type.bodySm)
                    }
                }
                if (plan.features.isEmpty()) {
                    AppText("Sin características listadas", style = AppTheme.type.bodySm, color = colors.inkSubtle)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    when (tenantCount) {
                        0 -> "Sin restaurantes"
                        1 -> "1 restaurante"
                        else -> "$tenantCount restaurantes"
                    },
                    Modifier.weight(1f),
                    style = AppTheme.type.bodySm,
                    color = colors.inkMuted,
                    maxLines = 1,
                )
                AppButton("Editar", onClick = onEdit, variant = ButtonVariant.SECONDARY, icon = AppIcons.Edit)
            }
        }
    }
}

private fun centsToPesosText(cents: Long): String =
    if (cents % 100 == 0L) (cents / 100).toString() else "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

private fun bpsToPercentText(bps: Int): String = formatBps(bps).removeSuffix("%")

@Composable
private fun PlanEditorDialog(plan: Plan, isNew: Boolean, store: OperatorStore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(plan.name) }
    var price by remember { mutableStateOf(if (isNew) "" else centsToPesosText(plan.priceCents)) }
    var fee by remember { mutableStateOf(bpsToPercentText(plan.feeBps)) }
    var description by remember { mutableStateOf(plan.description) }
    var features by remember { mutableStateOf(plan.features.joinToString("\n")) }
    var locations by remember { mutableStateOf(plan.maxLocations.toString()) }
    var highlighted by remember { mutableStateOf(plan.highlighted) }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val priceValue = price.trim().replace(",", "").toDoubleOrNull()
    val feeValue = fee.trim().replace(",", ".").toDoubleOrNull()
    val locationsValue = locations.trim().toIntOrNull()
    val nameError = if (name.isBlank()) "Escribe el nombre del plan" else null
    val priceError = if (priceValue == null || priceValue < 0 || priceValue > 1_000_000) "Escribe un precio válido en pesos" else null
    val feeError = if (feeValue == null || feeValue < 0 || feeValue > 30) "Entre 0 y 30%" else null
    val locationsError = if (locationsValue == null || locationsValue < 1) "Mínimo 1 sucursal" else null
    val valid = nameError == null && priceError == null && feeError == null && locationsError == null

    AppDialog(
        onDismiss = { if (!busy) onDismiss() },
        title = if (isNew) "Nuevo plan" else "Editar plan",
        maxWidth = 560.dp,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = dialogBodyMaxHeight()).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Nombre",
                    placeholder = "Ej. Pro",
                    error = nameError.takeIf { submitted },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x3), verticalAlignment = Alignment.Top) {
                    AppTextField(
                        value = price,
                        onValueChange = { v -> price = v.filter { it.isDigit() || it == '.' }.take(9) },
                        modifier = Modifier.weight(1f),
                        label = "Precio mensual (MXN)",
                        placeholder = "999",
                        leadingIcon = AppIcons.Cash,
                        error = priceError.takeIf { submitted },
                        keyboardType = KeyboardType.Decimal,
                    )
                    AppTextField(
                        value = fee,
                        onValueChange = { v -> fee = v.filter { it.isDigit() || it == '.' }.take(5) },
                        modifier = Modifier.weight(1f),
                        label = "Comisión por pedido (%)",
                        placeholder = "1.5",
                        error = feeError.takeIf { submitted },
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Descripción",
                    placeholder = "Para quién es este plan",
                    singleLine = false,
                    minLines = 2,
                )
                AppTextField(
                    value = features,
                    onValueChange = { features = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Características (una por línea)",
                    placeholder = "Menú digital con tu marca\nPagos con Mercado Pago",
                    singleLine = false,
                    minLines = 4,
                )
                AppTextField(
                    value = locations,
                    onValueChange = { v -> locations = v.filter { it.isDigit() }.take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Máximo de sucursales (99 = ilimitadas)",
                    placeholder = "1",
                    leadingIcon = AppIcons.Location,
                    error = locationsError.takeIf { submitted },
                    keyboardType = KeyboardType.Number,
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                        AppText("Destacado", style = AppTheme.type.title)
                        AppText(
                            "Se muestra como el plan recomendado en el sitio de venta.",
                            style = AppTheme.type.bodySm,
                            color = AppTheme.colors.inkMuted,
                        )
                    }
                    AppSwitch(checked = highlighted, onCheckedChange = { highlighted = it })
                }
            }
            DialogButtons { m ->
                val save: @Composable () -> Unit = {
                    AppButton(
                        if (isNew) "Crear plan" else "Guardar cambios",
                        onClick = {
                            submitted = true
                            if (valid && priceValue != null && feeValue != null && locationsValue != null) {
                                val updated = plan.copy(
                                    name = name.trim(),
                                    priceCents = (priceValue * 100).roundToLong(),
                                    description = description.trim(),
                                    features = features.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                    feeBps = (feeValue * 100).roundToInt(),
                                    highlighted = highlighted,
                                    maxLocations = locationsValue,
                                )
                                busy = true
                                scope.launch {
                                    val ok = store.savePlan(updated, isNew)
                                    busy = false
                                    if (ok) onDismiss()
                                }
                            }
                        },
                        modifier = m,
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
