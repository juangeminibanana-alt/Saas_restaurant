package app.feature.operator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.shared.model.TenantStatus
import app.shared.model.TenantSummary
import app.shared.util.formatMoney
import app.shared.util.formatMoneyCompact
import app.shared.util.label
import app.shared.util.relativeTime
import app.shell.LocalNavigator
import app.ui.components.AppButton
import app.ui.components.AppChip
import app.ui.components.AppDivider
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.EmptyState
import app.ui.components.ErrorState
import app.ui.components.LabelText
import app.ui.components.PageHeader
import app.ui.components.Skeleton
import app.ui.components.StatusDot
import app.ui.components.StatusPill
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.components.flatSurface
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.delay

private class TenantActions(
    val onDetail: (TenantSummary) -> Unit,
    val onPlan: (TenantSummary) -> Unit,
    val onStatus: (TenantSummary) -> Unit,
    val onOpenPublic: (TenantSummary) -> Unit,
)

@Composable
internal fun TenantsSection(store: OperatorStore, onReload: () -> Unit) {
    val ws = LocalWindowSize.current
    val compact = ws == WindowSize.COMPACT
    val navigator = LocalNavigator.current

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<TenantStatus?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var planId by remember { mutableStateOf<String?>(null) }
    var statusId by remember { mutableStateOf<String?>(null) }

    val tenants = store.tenants
    val q = query.trim().lowercase()
    val current = filter
    val filtered = tenants.filter { t ->
        (current == null || t.status == current) &&
            (q.isEmpty() || t.name.lowercase().contains(q) || t.slug.contains(q) ||
                t.ownerName.lowercase().contains(q) || t.ownerEmail.lowercase().contains(q))
    }
    val actions = TenantActions(
        onDetail = { detailId = it.id },
        onPlan = { planId = it.id },
        onStatus = { statusId = it.id },
        onOpenPublic = { navigator.navigate("/r/${it.slug}") },
    )
    val activeCount = tenants.count { it.status == TenantStatus.ACTIVE }

    OpPage {
        PageHeader(
            title = "Restaurantes",
            subtitle = if (store.loaded) "${tenants.size} en la plataforma · $activeCount activos" else "Todos los restaurantes de la plataforma",
            actions = {
                AppButton(
                    if (compact) "Crear" else "Crear restaurante",
                    onClick = { showCreate = true },
                    icon = AppIcons.Add,
                    enabled = store.loaded,
                )
            },
        )

        val search: @Composable (Modifier) -> Unit = { m ->
            AppTextField(
                value = query,
                onValueChange = { query = it },
                modifier = m,
                placeholder = "Buscar por nombre, link o dueño",
                leadingIcon = AppIcons.Search,
            )
        }
        val chips: @Composable (Modifier) -> Unit = { m ->
            Row(
                m.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppChip("Todos · ${tenants.size}", selected = filter == null, onClick = { filter = null })
                TenantStatus.entries.forEach { s ->
                    val n = tenants.count { it.status == s }
                    AppChip("${s.label()} · $n", selected = filter == s, onClick = { filter = if (filter == s) null else s })
                }
            }
        }
        if (ws == WindowSize.EXPANDED) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                search(Modifier.width(320.dp))
                chips(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                search(Modifier.fillMaxWidth())
                chips(Modifier.fillMaxWidth())
            }
        }

        when {
            !store.loaded && store.error != null -> ErrorState(store.error ?: "", Modifier.fillMaxWidth(), onRetry = onReload)
            !store.loaded -> TenantsSkeleton(ws)
            tenants.isEmpty() -> EmptyState(
                title = "Aún no hay restaurantes",
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.x10),
                message = "Crea el primero y comparte su link público en minutos.",
                icon = AppIcons.Store,
                actionText = "Crear restaurante",
                onAction = { showCreate = true },
            )
            filtered.isEmpty() -> EmptyState(
                title = "Sin resultados",
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.x10),
                message = "Ningún restaurante coincide con tu búsqueda o filtro.",
                icon = AppIcons.Search,
                actionText = "Limpiar filtros",
                onAction = { query = ""; filter = null },
            )
            ws == WindowSize.EXPANDED -> TenantsTable(store, filtered, actions)
            else -> OpGrid(filtered, columns = if (ws == WindowSize.MEDIUM) 2 else 1) { t ->
                key(t.id) {
                    NewRowReveal(isNew = t.id == store.highlightTenantId, onSettled = { store.highlightTenantId = null }) { tint, fresh ->
                        TenantCard(t, tint, fresh, actions)
                    }
                }
            }
        }
    }

    if (showCreate) CreateTenantDialog(store, onDismiss = { showCreate = false })
    tenants.firstOrNull { it.id == detailId }?.let { t ->
        TenantDetailDialog(
            tenant = t,
            store = store,
            onDismiss = { detailId = null },
            onChangePlan = { detailId = null; planId = t.id },
            onToggleStatus = { detailId = null; statusId = t.id },
            onOpenPublic = { detailId = null; actions.onOpenPublic(t) },
        )
    }
    tenants.firstOrNull { it.id == planId }?.let { t ->
        ChangePlanDialog(t, store, onDismiss = { planId = null })
    }
    tenants.firstOrNull { it.id == statusId }?.let { t ->
        TenantStatusDialog(t, store, onDismiss = { statusId = null })
    }
}

/**
 * Revela con una animación corta la fila recién creada y la marca unos segundos con la etiqueta "Nuevo".
 * Sin tintes ni brillos: el primer parámetro del contenido se conserva y siempre es transparente.
 */
@Composable
private fun NewRowReveal(isNew: Boolean, onSettled: () -> Unit, content: @Composable (Color, Boolean) -> Unit) {
    val reveal = remember { MutableTransitionState(!isNew).apply { targetState = true } }
    var fresh by remember { mutableStateOf(isNew) }
    LaunchedEffect(Unit) {
        if (isNew) {
            delay(3900)
            fresh = false
            onSettled()
        }
    }
    AnimatedVisibility(
        visibleState = reveal,
        enter = expandVertically(tween(Motion.SLOW, easing = Motion.standard), expandFrom = Alignment.Top) +
            fadeIn(tween(Motion.SLOW, delayMillis = Motion.FAST)),
    ) {
        content(Color.Transparent, fresh)
    }
}

// ───────────────────────── Tabla (escritorio) ─────────────────────────

@Composable
private fun TenantsTable(store: OperatorStore, tenants: List<TenantSummary>, actions: TenantActions) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 940.dp
        val cName = OpColumn("Restaurante", 2.4f)
        val cPlan = OpColumn("Plan", 1f)
        val cStatus = OpColumn("Estado", 1.3f)
        val cMp = OpColumn(if (wide) "Mercado Pago" else "Pagos", if (wide) 1.2f else 0.7f)
        val cOrders = OpColumn("Pedidos 30 d", 0.9f, end = true)
        val cGmv = OpColumn("Ventas 30 d", 1f, end = true)
        val cMrr = OpColumn("MRR", 0.9f, end = true)
        val cCreated = OpColumn("Alta", 0.9f, end = true)
        val columns = listOfNotNull(cName, cPlan, cStatus, cMp, cOrders, cGmv, cMrr, cCreated.takeIf { wide })

        OpPanel(Modifier.fillMaxWidth()) {
            OpTableHeader(columns, trailing = 44.dp)
            tenants.forEachIndexed { index, t ->
                key(t.id) {
                    NewRowReveal(isNew = t.id == store.highlightTenantId, onSettled = { store.highlightTenantId = null }) { tint, fresh ->
                        Column(Modifier.fillMaxWidth()) {
                            if (index > 0) AppDivider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(tint)
                                    .pressable(scale = 0.998f, onClick = { actions.onDetail(t) })
                                    .padding(start = Space.x4, end = Space.x4, top = Space.x3, bottom = Space.x3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OpCell(cName) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3), verticalAlignment = Alignment.CenterVertically) {
                                        TenantMonogram(t.name, t.primary)
                                        Column(Modifier.weight(1f, fill = false)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                                                AppText(t.name, Modifier.weight(1f, fill = false), style = AppTheme.type.title, maxLines = 1)
                                                if (fresh) TagBadge("Nuevo", tone = Tone.PRIMARY)
                                            }
                                            AppText("/r/${t.slug}", style = AppTheme.type.mono, color = AppTheme.colors.inkMuted, maxLines = 1)
                                        }
                                    }
                                }
                                OpCell(cPlan) { AppText(t.planName, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1) }
                                OpCell(cStatus) { StatusPill(t.status.label(), tone = t.status.tone()) }
                                OpCell(cMp) { MpIndicator(t.mpConnected, showLabel = wide) }
                                OpCell(cOrders) { AppText(formatCount(t.orders30d), style = AppTheme.type.mono, maxLines = 1) }
                                OpCell(cGmv) { AppText(formatMoneyCompact(t.gmv30dCents), style = AppTheme.type.mono, maxLines = 1) }
                                OpCell(cMrr) { AppText(formatMoney(t.mrrCents), style = AppTheme.type.mono, maxLines = 1) }
                                if (wide) {
                                    OpCell(cCreated) {
                                        AppText(relativeTime(t.createdAt), style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
                                    }
                                }
                                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                    TenantRowMenu(t, actions)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MpIndicator(connected: Boolean, showLabel: Boolean = true) {
    // El estado es un punto sólido: verde = cuenta conectada.
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (connected) AppTheme.colors.positive else AppTheme.colors.inkSubtle)
        if (showLabel) {
            AppText(
                if (connected) "Conectado" else "Sin conectar",
                style = AppTheme.type.bodySm,
                color = if (connected) AppTheme.colors.ink else AppTheme.colors.inkMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TenantRowMenu(t: TenantSummary, actions: TenantActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppIconButton(
            AppIcons.MoreVert,
            onClick = { open = true },
            contentDescription = "Acciones de ${t.name}",
            tint = AppTheme.colors.inkMuted,
            plain = true,
        )
        if (open) {
            val offsetY = with(LocalDensity.current) { 44.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, offsetY),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                val shown = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = shown,
                    enter = fadeIn(tween(Motion.FAST)),
                ) {
                    // Menú flotante plano: superficie 2 con filete fuerte. Sin sombra.
                    Column(
                        Modifier
                            .padding(Space.x3)
                            .flatSurface(AppTheme.colors.surfaceRaised, AppTheme.colors.lineStrong, AppTheme.shapes.md)
                            .width(232.dp)
                            .padding(vertical = Space.x1),
                    ) {
                        val suspended = t.status == TenantStatus.SUSPENDED
                        MenuRow("Ver detalle", AppIcons.Eye) { open = false; actions.onDetail(t) }
                        MenuRow("Cambiar plan", AppIcons.Tag) { open = false; actions.onPlan(t) }
                        MenuRow("Abrir menú público", AppIcons.ExternalLink) { open = false; actions.onOpenPublic(t) }
                        AppDivider(Modifier.padding(vertical = Space.x1))
                        MenuRow(
                            if (suspended) "Reactivar" else "Suspender",
                            if (suspended) AppIcons.Refresh else AppIcons.Lock,
                            danger = !suspended,
                        ) { open = false; actions.onStatus(t) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) AppTheme.colors.danger else AppTheme.colors.ink
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .pressable(scale = 0.98f, onClick = onClick)
            .padding(horizontal = Space.x4),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OpIcon(icon, tint = if (danger) color else AppTheme.colors.inkMuted)
        AppText(text, style = AppTheme.type.titleSm, color = color, maxLines = 1)
    }
}

// ───────────────────────── Tarjetas (teléfono / tablet) ─────────────────────────

@Composable
private fun TenantCard(t: TenantSummary, tint: Color, fresh: Boolean, actions: TenantActions) {
    val suspended = t.status == TenantStatus.SUSPENDED
    OpPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(tint)
                .pressable(scale = 0.99f, onClick = { actions.onDetail(t) })
                .padding(Space.x4),
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3), verticalAlignment = Alignment.CenterVertically) {
                TenantMonogram(t.name, t.primary, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                        AppText(t.name, Modifier.weight(1f, fill = false), style = AppTheme.type.title, maxLines = 1)
                        if (fresh) TagBadge("Nuevo", tone = Tone.PRIMARY)
                    }
                    AppText("/r/${t.slug}", style = AppTheme.type.mono, color = AppTheme.colors.inkMuted, maxLines = 1)
                }
                StatusPill(t.status.label(), tone = t.status.tone())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                CardFigure("Pedidos 30 d", formatCount(t.orders30d), Modifier.weight(1f))
                CardFigure("Ventas 30 d", formatMoneyCompact(t.gmv30dCents), Modifier.weight(1f))
                CardFigure("MRR", formatMoney(t.mrrCents), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MpIndicator(t.mpConnected)
                Spacer(Modifier.weight(1f))
                LabelText("Plan ${t.planName} · alta ${relativeTime(t.createdAt)}")
            }
            AppDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                AppButton(
                    "Cambiar plan",
                    onClick = { actions.onPlan(t) },
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.SECONDARY,
                    size = ButtonSize.SM,
                )
                AppButton(
                    if (suspended) "Reactivar" else "Suspender",
                    onClick = { actions.onStatus(t) },
                    modifier = Modifier.weight(1f),
                    variant = if (suspended) ButtonVariant.SECONDARY else ButtonVariant.DANGER,
                    size = ButtonSize.SM,
                )
            }
        }
    }
}

@Composable
private fun CardFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.x1)) {
        LabelText(label)
        AppText(value, style = AppTheme.type.monoMd, maxLines = 1)
    }
}

@Composable
private fun TenantsSkeleton(ws: WindowSize) {
    if (ws == WindowSize.EXPANDED) {
        OpPanel(Modifier.fillMaxWidth()) {
            repeat(6) { i ->
                if (i > 0) AppDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x4),
                    horizontalArrangement = Arrangement.spacedBy(Space.x4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Skeleton(Modifier.width(36.dp).height(36.dp))
                    Skeleton(Modifier.weight(2f).height(16.dp))
                    Skeleton(Modifier.weight(1f).height(16.dp))
                    Skeleton(Modifier.weight(1f).height(16.dp))
                    Skeleton(Modifier.weight(1f).height(16.dp))
                }
            }
        }
    } else {
        OpGrid((1..4).toList(), columns = if (ws == WindowSize.MEDIUM) 2 else 1) {
            Skeleton(Modifier.fillMaxWidth().height(196.dp), shape = AppTheme.shapes.lg)
        }
    }
}
