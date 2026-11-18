package app.feature.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.shared.model.IntegrationProvider
import app.shared.model.Role
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
import app.ui.components.LabelText
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
                SimpleGrid(items = listOf(0, 1, 2, 3), columns = columns, equalHeight = true) { i ->
                    when (i) {
                        // Etiquetas cortas: caben enteras en media pantalla de teléfono.
                        0 -> StatCard("Ventas hoy", formatMoney(v.salesTodayCents), Modifier.fillMaxSize(), series = series)
                        1 -> StatCard("Pedidos hoy", v.ordersToday.toString(), Modifier.fillMaxSize())
                        2 -> StatCard("Ticket medio", formatMoney(v.avgTicketCents), Modifier.fillMaxSize())
                        else -> StatCard("Preparación", "${v.avgPrepMinutes} min", Modifier.fillMaxSize())
                    }
                }
                SimpleGrid(items = listOf(0, 1), columns = if (size == WindowSize.EXPANDED) 2 else 1, equalHeight = true) { i ->
                    if (i == 0) {
                        AppCard(Modifier.fillMaxSize(), padding = Space.x4) {
                            SectionHeader("Ventas", label = "Últimos días")
                            Spacer(Modifier.height(Space.x4))
                            if (v.salesSeries.isEmpty()) {
                                AppText("Aún no hay ventas registradas.", color = colors.inkMuted)
                            } else {
                                BarChart(v.salesSeries, Modifier.fillMaxWidth().height(220.dp))
                            }
                        }
                    } else {
                        AppCard(Modifier.fillMaxSize(), padding = Space.x4) {
                            SectionHeader("Lo más pedido", label = "Vendidos · ingreso")
                            Spacer(Modifier.height(Space.x2))
                            if (v.topItems.isEmpty()) {
                                AppText("Aquí verás tus platillos más vendidos.", color = colors.inkMuted)
                            }
                            v.topItems.take(6).forEachIndexed { index, item ->
                                if (index > 0) AppDivider()
                                Row(Modifier.fillMaxWidth().padding(vertical = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                                    AppText((index + 1).toString().padStart(2, '0'), Modifier.width(Space.x8), style = AppTheme.type.mono, color = colors.inkSubtle)
                                    AppText(item.name, Modifier.weight(1f), style = AppTheme.type.title, maxLines = 1)
                                    AppText(item.quantity.toString(), style = AppTheme.type.mono, color = colors.inkMuted, maxLines = 1)
                                    AppText(formatMoney(item.revenueCents), Modifier.width(Space.x18 + Space.x2), style = AppTheme.type.mono, textAlign = TextAlign.End, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        AppCard(Modifier.fillMaxWidth(), padding = Space.x4) {
            SectionHeader(
                "Pedidos en curso",
                action = { AppButton("Ver todos", onClick = onOpenOrders, variant = ButtonVariant.SECONDARY, size = ButtonSize.SM, trailingIcon = AppIcons.Forward) },
            )
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
                            AppText(order.number, Modifier.width(Space.x14), style = AppTheme.type.mono, maxLines = 1)
                            Column(Modifier.weight(1f)) {
                                AppText(order.customerName.ifBlank { order.type.label() }, style = AppTheme.type.title, maxLines = 1)
                                AppText("${order.type.label()} · ${order.channel.label()}", style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
                            }
                            Spacer(Modifier.width(Space.x2))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                                AppText(formatMoney(order.totalCents), style = AppTheme.type.mono, maxLines = 1)
                                StatusPill(order.status.label(), tone = order.status.tone())
                            }
                        }
                    }
                }
            }
        }
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
                    SectionHeader(category, modifier = Modifier.padding(top = Space.x3), label = "${list.count { it.connected }} de ${list.size} activas")
                    // Un solo bloque por categoría: filas separadas por filetes.
                    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                        list.forEachIndexed { rowIndex, integration ->
                            if (rowIndex > 0) AppDivider()
                            Box(Modifier.fillMaxWidth().padding(Space.x4)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(integration.provider.icon())
                                    Spacer(Modifier.width(Space.x3))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AppText(integration.name, Modifier.weight(1f, fill = false), style = AppTheme.type.title, maxLines = 1)
                                            Spacer(Modifier.width(Space.x2))
                                            TagBadge(modeLabel(integration.mode))
                                        }
                                        if (integration.description.isNotBlank()) {
                                            Spacer(Modifier.height(Space.x1))
                                            AppText(integration.description, style = AppTheme.type.bodySm, color = colors.inkMuted)
                                        }
                                    }
                                    Spacer(Modifier.width(Space.x3))
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
                }
                AppText(
                    "Los conectores de reparto y marketplaces están preparados y funcionan en modo simulado hasta cargar las credenciales de cada servicio.",
                    style = AppTheme.type.bodySm, color = colors.inkSubtle,
                )
            }
        }
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
                AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
                    s.value.forEachIndexed { index, member ->
                        if (index > 0) AppDivider()
                        Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(member.name, size = 40.dp)
                            Spacer(Modifier.width(Space.x3))
                            Column(Modifier.weight(1f)) {
                                AppText(member.name, style = AppTheme.type.title, maxLines = 1)
                                AppText(member.email, style = AppTheme.type.mono, color = colors.inkMuted, maxLines = 1)
                            }
                            Spacer(Modifier.width(Space.x2))
                            TagBadge(member.role.label(), tone = if (member.role == Role.OWNER) Tone.PRIMARY else Tone.NEUTRAL)
                        }
                    }
                }
                AppCard(Modifier.fillMaxWidth(), padding = Space.x4) {
                    SectionHeader("Qué puede hacer cada rol")
                    Spacer(Modifier.height(Space.x2))
                    listOf(
                        "Dueño" to "Todo: marca, menú, pagos, integraciones, pedidos y equipo.",
                        "Gerente" to "Marca, menú, pedidos e integraciones. No ve los pagos.",
                        "Cajero" to "Pedidos: consultar, cambiar estado y reembolsar.",
                        "Cocina" to "Pantalla de cocina: aceptar, preparar y marcar listo.",
                    ).forEach { (role, what) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Space.x2)) {
                            LabelText(role, Modifier.width(Space.x18 + Space.x4).padding(top = 2.dp), color = colors.ink)
                            AppText(what, Modifier.weight(1f), style = AppTheme.type.bodySm, color = colors.inkMuted)
                        }
                    }
                }
            }
        }
    }
}
