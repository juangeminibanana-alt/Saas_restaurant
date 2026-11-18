package app.feature.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.shared.model.MetricPoint
import app.shared.model.OperatorMetrics
import app.shared.model.TenantSummary
import app.shared.util.formatMoneyCompact
import app.shared.util.label
import app.shared.util.relativeTime
import app.ui.components.AppButton
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BarChart
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.EmptyState
import app.ui.components.ErrorState
import app.ui.components.LineChart
import app.ui.components.LabelText
import app.ui.components.PageHeader
import app.ui.components.pressable
import app.ui.components.Skeleton
import app.ui.components.StatCard
import app.ui.components.StatusPill
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize

private data class OverviewStat(
    val label: String,
    val value: String,
    val icon: ImageVector? = null,
    val delta: String? = null,
    val deltaPositive: Boolean = true,
    val series: List<Double>? = null,
)

@Composable
internal fun OverviewSection(store: OperatorStore, onReload: () -> Unit, onOpenTenants: () -> Unit) {
    val metrics = store.metrics
    OpPage {
        PageHeader(
            title = "Resumen",
            subtitle = "Ingresos, restaurantes y actividad de los últimos 30 días",
        )
        when {
            metrics == null && store.error != null -> ErrorState(store.error ?: "", Modifier.fillMaxWidth(), onRetry = onReload)
            metrics == null -> OverviewSkeleton()
            else -> {
                StatGrid(metrics)
                ChartsRow(metrics)
                RecentTenants(store.tenants, onOpenTenants)
            }
        }
    }
}

@Composable
private fun StatGrid(m: OperatorMetrics) {
    val stats = listOf(
        // Etiquetas cortas (caben en media pantalla de teléfono) y sin ícono: la cifra en mono es el dato.
        OverviewStat(
            "Recurrente al mes", formatMoneyCompact(m.mrrCents),
            delta = formatSignedPct(m.mrrDeltaPct),
            deltaPositive = m.mrrDeltaPct >= 0,
            series = m.mrrSeries.map { it.value }.takeIf { it.size >= 2 },
        ),
        OverviewStat("Activos", formatCount(m.activeTenants)),
        OverviewStat("En prueba", formatCount(m.trialTenants)),
        OverviewStat("Cancelación", formatPct(m.churnPct)),
        OverviewStat("Activación", formatPct(m.activationPct)),
        OverviewStat("Ventas · 30 d", formatMoneyCompact(m.gmv30dCents)),
        OverviewStat("Comisiones · 30 d", formatMoneyCompact(m.platformFees30dCents)),
        OverviewStat("Pedidos · 30 d", formatCount(m.orders30d)),
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 880.dp -> 4
            maxWidth >= 300.dp -> 2
            else -> 1
        }
        // Bento de cifras: mosaicos de la misma altura por fila.
        OpGrid(stats, columns, equalHeight = true) { s ->
            StatCard(
                label = s.label,
                value = s.value,
                modifier = Modifier.fillMaxSize(),
                delta = s.delta,
                deltaPositive = s.deltaPositive,
                icon = s.icon,
                series = s.series,
            )
        }
    }
}

@Composable
private fun ChartsRow(m: OperatorMetrics) {
    val expanded = LocalWindowSize.current == WindowSize.EXPANDED
    if (expanded) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            MrrChart(m, Modifier.weight(1.6f))
            SignupsChart(m, Modifier.weight(1f))
        }
    } else {
        MrrChart(m, Modifier.fillMaxWidth())
        SignupsChart(m, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChartPanel(
    title: String,
    caption: String,
    headline: String,
    points: List<MetricPoint>,
    modifier: Modifier,
    chart: @Composable (Modifier) -> Unit,
) {
    OpPanel(modifier) {
        Column(Modifier.fillMaxWidth().padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                    AppText(title, style = AppTheme.type.h2)
                    AppText(caption, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
                }
                AppText(headline, style = AppTheme.type.monoLg, maxLines = 1)
            }
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    AppText("Aún no hay datos suficientes", style = AppTheme.type.bodySm, color = AppTheme.colors.inkSubtle)
                }
            } else {
                chart(Modifier.fillMaxWidth().height(220.dp))
            }
        }
    }
}

@Composable
private fun MrrChart(m: OperatorMetrics, modifier: Modifier) {
    ChartPanel(
        title = "Ingreso recurrente mensual",
        caption = "Suscripciones cobradas cada mes",
        headline = formatMoneyCompact(m.mrrCents),
        points = m.mrrSeries,
        modifier = modifier,
    ) { chartModifier -> LineChart(m.mrrSeries, chartModifier) }
}

@Composable
private fun SignupsChart(m: OperatorMetrics, modifier: Modifier) {
    val total = m.signupsSeries.sumOf { it.value }.toInt()
    ChartPanel(
        title = "Altas por mes",
        caption = "Restaurantes nuevos",
        headline = formatCount(total),
        points = m.signupsSeries,
        modifier = modifier,
    ) { chartModifier -> BarChart(m.signupsSeries, chartModifier) }
}

@Composable
private fun RecentTenants(tenants: List<TenantSummary>, onOpenTenants: () -> Unit) {
    val recent = tenants.sortedByDescending { it.createdAt }.take(5)
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    OpPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.x4, end = Space.x4, top = Space.x4, bottom = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                AppText("Restaurantes recientes", style = AppTheme.type.h2)
                AppText("Las altas más nuevas de la plataforma", style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
            }
            AppButton("Ver todos", onClick = onOpenTenants, variant = ButtonVariant.SECONDARY, size = ButtonSize.SM, trailingIcon = AppIcons.Forward)
        }
        AppDivider()
        if (recent.isEmpty()) {
            EmptyState(
                title = "Todavía no hay restaurantes",
                modifier = Modifier.fillMaxWidth().padding(Space.x6),
                message = "Crea el primero desde la sección Restaurantes.",
                icon = AppIcons.Store,
                actionText = "Ir a Restaurantes",
                onAction = onOpenTenants,
            )
        } else {
            recent.forEachIndexed { index, t ->
                if (index > 0) AppDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(scale = 0.995f, onClick = onOpenTenants)
                        .padding(horizontal = Space.x4, vertical = Space.x3),
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TenantMonogram(t.name, t.primary)
                    Column(Modifier.weight(1f)) {
                        AppText(t.name, style = AppTheme.type.title, maxLines = 1)
                        AppText("/r/${t.slug}", style = AppTheme.type.mono, color = AppTheme.colors.inkMuted, maxLines = 1)
                    }
                    if (!compact) {
                        LabelText(t.planName, Modifier.width(96.dp))
                        AppText(
                            relativeTime(t.createdAt),
                            Modifier.width(96.dp),
                            style = AppTheme.type.bodySm,
                            color = AppTheme.colors.inkSubtle,
                            maxLines = 1,
                        )
                    }
                    StatusPill(t.status.label(), tone = t.status.tone())
                }
            }
        }
    }
}

@Composable
private fun OverviewSkeleton() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 880.dp -> 4
            maxWidth >= 300.dp -> 2
            else -> 1
        }
        OpGrid((1..8).toList(), columns) {
            Skeleton(Modifier.fillMaxWidth().height(116.dp), shape = AppTheme.shapes.lg)
        }
    }
    Skeleton(Modifier.fillMaxWidth().height(300.dp), shape = AppTheme.shapes.lg)
    Skeleton(Modifier.fillMaxWidth().height(240.dp), shape = AppTheme.shapes.lg)
}
