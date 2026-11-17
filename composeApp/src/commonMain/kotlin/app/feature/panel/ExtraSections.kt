package app.feature.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.shared.model.IntegrationProvider
import app.shared.model.UserSession
import app.shared.util.formatMoney
import app.shared.util.label
import app.shell.LocalRepo
import app.ui.components.AppButton
import app.ui.components.AppCard
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppSwitch
import app.ui.components.AppText
import app.ui.components.Avatar
import app.ui.components.BarChart
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.EmptyState
import app.ui.components.ErrorState
import app.ui.components.PageHeader
import app.ui.components.SectionHeader
import app.ui.components.StatCard
import app.ui.components.StatusPill
import app.ui.components.TagBadge
import app.ui.components.Toasts
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

// ───────────────────────── Resumen ─────────────────────────

@Composable
internal fun OverviewSection(session: UserSession, onOpenOrders: () -> Unit) {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val stats = rememberLoader { repo.tenantStats() }
    val active = rememberLoader { repo.listOrders(activeOnly = true) }
    val size = LocalWindowSize.current
    val columns = if (size == WindowSize.EXPANDED) 4 else 2

    PanelPage {
        PageHeader(
            "Hola, ${session.name.substringBefore(' ')}",
            subtitle = "Así va ${session.tenantName ?: "tu restaurante"} hoy",
        )
        when (val s = stats.state) {
            is Load.Loading -> SkeletonCards(4, columns)
            is Load.Failed -> ErrorState(s.message, onRetry = { stats.reload() })
            is Load.Ready -> {
                val v = s.value
                val series = v.salesSeries.map { it.value }
                SimpleGrid(items = listOf(0, 1, 2, 3), columns = columns) { i ->
                    when (i) {
                        0 -> StatCard("Ventas de hoy", formatMoney(v.salesTodayCents), Modifier.fillMaxWidth(), icon = AppIcons.Cash, series = series)
                        1 -> StatCard("Pedidos de hoy", v.ordersToday.toString(), Modifier.fillMaxWidth(), icon = AppIcons.Receipt)
                        2 -> StatCard("Ticket promedio", formatMoney(v.avgTicketCents), Modifier.fillMaxWidth(), icon = AppIcons.Tag)
                        else -> StatCard("Tiempo de preparación", "${v.avgPrepMinutes} min", Modifier.fillMaxWidth(), icon = AppIcons.Clock)
                    }
                }
                Spacer(Modifier.height(Space.x5))
                SimpleGrid(items = listOf(0, 1), columns = if (size == WindowSize.EXPANDED) 2 else 1) { i ->
                    if (i == 0) {
                        AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
                            AppText("Ventas de los últimos días", style = AppTheme.type.title)
                            Spacer(Modifier.height(Space.x4))
                            if (v.salesSeries.isEmpty()) {
                                AppText("Aún no hay ventas registradas.", color = colors.inkMuted)
                            } else {
                                BarChart(v.salesSeries, Modifier.fillMaxWidth().height(220.dp))
                            }
                        }
                    } else {
                        AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
                            AppText("Lo más pedido", style = AppTheme.type.title)
                            Spacer(Modifier.height(Space.x3))
                            if (v.topItems.isEmpty()) {
                                AppText("Aquí verás tus platillos más vendidos.", color = colors.inkMuted)
                            }
                            v.topItems.take(6).forEachIndexed { index, item ->
                                if (index > 0) AppDivider()
                                Row(Modifier.fillMaxWidth().padding(vertical = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                                    AppText("${index + 1}", Modifier.width(28.dp), style = AppTheme.type.title, color = colors.inkSubtle)
                                    AppText(item.name, Modifier.weight(1f), maxLines = 1)
                                    AppText("${item.quantity} vendidos", style = AppTheme.type.bodySm, color = colors.inkMuted)
                                    Spacer(Modifier.width(Space.x4))
                                    AppText(formatMoney(item.revenueCents), style = AppTheme.type.title)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.x5))
        AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText("Pedidos en curso", Modifier.weight(1f), style = AppTheme.type.title)
                AppButton("Ver todos", onClick = onOpenOrders, variant = ButtonVariant.GHOST, size = ButtonSize.SM)
            }
            Spacer(Modifier.height(Space.x2))
            when (val s = active.state) {
                is Load.Loading -> SkeletonRows(3)
                is Load.Failed -> ErrorState(s.message, onRetry = { active.reload() })
                is Load.Ready -> {
                    if (s.value.isEmpty()) {
                        AppText("No hay pedidos en curso en este momento.", color = colors.inkMuted)
                    }
                    s.value.take(5).forEachIndexed { index, order ->
                        if (index > 0) AppDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                            AppText(order.number, Modifier.width(72.dp), style = AppTheme.type.title)
                            Column(Modifier.weight(1f)) {
                                AppText(order.customerName.ifBlank { order.type.label() }, maxLines = 1)
                                AppText("${order.type.label()} · ${order.channel.label()}", style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
                            }
                            StatusPill(order.status.label(), tone = order.status.tone())
                            Spacer(Modifier.width(Space.x4))
                            AppText(formatMoney(order.totalCents), style = AppTheme.type.title)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x8))
    }
}

// ───────────────────────── Integraciones ─────────────────────────

private fun IntegrationProvider.icon(): ImageVector = when (this) {
    IntegrationProvider.MERCADO_PAGO -> AppIcons.CreditCard
    IntegrationProvider.LALAMOVE -> AppIcons.Bike
    IntegrationProvider.UBER_EATS, IntegrationProvider.RAPPI, IntegrationProvider.DIDI_FOOD -> AppIcons.Bag
}

private fun modeLabel(mode: String): String = when (mode.lowercase()) {
    "live" -> "Producción"
    "sandbox" -> "Sandbox"
    else -> "Simulado"
}

@Composable
internal fun IntegrationsSection() {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors
    val loader = rememberLoader { repo.listIntegrations() }
    var busy by remember { mutableStateOf<IntegrationProvider?>(null) }

    PanelPage(maxWidth = 980.dp) {
        PageHeader("Integraciones", subtitle = "Pagos, reparto y marketplaces conectados a tu cocina")
        when (val s = loader.state) {
            is Load.Loading -> SkeletonRows(5)
            is Load.Failed -> ErrorState(s.message, onRetry = { loader.reload() })
            is Load.Ready -> {
                if (s.value.isEmpty()) {
                    EmptyState("Sin integraciones disponibles", message = "Pronto podrás conectar más servicios.", icon = AppIcons.Plug)
                }
                s.value.groupBy { it.category.ifBlank { "Otros" } }.forEach { (category, list) ->
                    SectionHeader(category)
                    Spacer(Modifier.height(Space.x3))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                        list.forEach { integration ->
                            AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(integration.provider.icon(), tone = if (integration.connected) Tone.SUCCESS else Tone.NEUTRAL)
                                    Spacer(Modifier.width(Space.x4))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AppText(integration.name, style = AppTheme.type.title)
                                            Spacer(Modifier.width(Space.x2))
                                            TagBadge(modeLabel(integration.mode), tone = if (integration.mode == "live") Tone.SUCCESS else Tone.WARNING)
                                        }
                                        if (integration.description.isNotBlank()) {
                                            Spacer(Modifier.height(Space.x1))
                                            AppText(integration.description, style = AppTheme.type.bodySm, color = colors.inkMuted)
                                        }
                                    }
                                    Spacer(Modifier.width(Space.x4))
                                    AppSwitch(
                                        checked = integration.connected,
                                        onCheckedChange = {
                                            if (busy == null) {
                                                busy = integration.provider
                                                scope.launch {
                                                    try {
                                                        loader.set(repo.toggleIntegration(integration.provider))
                                                        Toasts.show(
                                                            if (integration.connected) "${integration.name} desconectado." else "${integration.name} conectado.",
                                                            Tone.SUCCESS,
                                                        )
                                                    } catch (e: CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        Toasts.show(errorText(e, "No pudimos cambiar la integración."), Tone.DANGER)
                                                    } finally {
                                                        busy = null
                                                    }
                                                }
                                            }
                                        },
                                        enabled = busy == null,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.x6))
                }
                AppText(
                    "Los conectores de reparto y marketplaces están preparados y funcionan en modo simulado hasta cargar las credenciales de cada servicio.",
                    style = AppTheme.type.bodySm, color = colors.inkSubtle,
                )
            }
        }
        Spacer(Modifier.height(Space.x8))
    }
}

// ───────────────────────── Equipo ─────────────────────────

@Composable
internal fun TeamSection() {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val loader = rememberLoader { repo.listStaff() }
    PanelPage(maxWidth = 820.dp) {
        PageHeader("Equipo", subtitle = "Cada rol ve solo lo suyo")
        when (val s = loader.state) {
            is Load.Loading -> SkeletonRows(4)
            is Load.Failed -> ErrorState(s.message, onRetry = { loader.reload() })
            is Load.Ready -> {
                AppCard(Modifier.fillMaxWidth(), padding = Space.x2) {
                    s.value.forEachIndexed { index, member ->
                        if (index > 0) AppDivider()
                        Row(Modifier.fillMaxWidth().padding(Space.x3), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(member.name, size = 40.dp)
                            Spacer(Modifier.width(Space.x3))
                            Column(Modifier.weight(1f)) {
                                AppText(member.name, style = AppTheme.type.title, maxLines = 1)
                                AppText(member.email, style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
                            }
                            StatusPill(member.role.label(), tone = member.role.tone(), dot = false)
                        }
                    }
                }
                Spacer(Modifier.height(Space.x5))
                AppCard(Modifier.fillMaxWidth(), padding = Space.x5) {
                    AppText("Qué puede hacer cada rol", style = AppTheme.type.title)
                    Spacer(Modifier.height(Space.x3))
                    listOf(
                        "Dueño" to "Todo: marca, menú, pagos, integraciones, pedidos y equipo.",
                        "Gerente" to "Marca, menú, pedidos e integraciones. No ve los pagos.",
                        "Cajero" to "Pedidos: consultar, cambiar estado y reembolsar.",
                        "Cocina" to "Pantalla de cocina: aceptar, preparar y marcar listo.",
                    ).forEach { (role, what) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.x2)) {
                            AppText(role, Modifier.width(88.dp), style = AppTheme.type.title)
                            AppText(what, Modifier.weight(1f), color = colors.inkMuted)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x8))
    }
}
