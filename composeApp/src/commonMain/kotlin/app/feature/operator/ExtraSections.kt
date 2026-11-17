package app.feature.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.shared.model.TenantStatus
import app.shared.util.formatMoney
import app.shared.util.formatMoneyCompact
import app.ui.components.AppButton
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.LoadingState
import app.ui.components.PageHeader
import app.ui.components.StatCard
import app.ui.components.StatusPill
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize

// ───────────────────────── Suscripciones ─────────────────────────

@Composable
internal fun SubscriptionsSection(store: OperatorStore, onReload: () -> Unit) {
    val colors = AppTheme.colors
    val size = LocalWindowSize.current
    val compact = size == WindowSize.COMPACT
    val tenants = store.tenants
    val planPrice = store.plans.associate { it.id to it.priceCents }
    val active = tenants.filter { it.status == TenantStatus.ACTIVE }
    val mrr = active.sumOf { planPrice[it.planId] ?: it.mrrCents }

    OpPage {
        PageHeader("Suscripciones", subtitle = "Cobro mensual a cada restaurante con Mercado Pago (preapproval)") {
            AppButton("Actualizar", onClick = onReload, variant = ButtonVariant.SECONDARY, size = ButtonSize.SM, icon = AppIcons.Refresh)
        }
        if (tenants.isEmpty() && store.loading) {
            LoadingState(Modifier.fillMaxWidth().height(240.dp))
            return@OpPage
        }
        OpGrid(listOf(0, 1, 2, 3), columns = if (size == WindowSize.EXPANDED) 4 else 2) { i ->
            when (i) {
                0 -> StatCard("Ingreso recurrente mensual", formatMoneyCompact(mrr), Modifier.fillMaxWidth(), icon = AppIcons.Chart)
                1 -> StatCard("Suscripciones al corriente", active.size.toString(), Modifier.fillMaxWidth(), icon = AppIcons.Check)
                2 -> StatCard("En periodo de prueba", tenants.count { it.status == TenantStatus.TRIAL }.toString(), Modifier.fillMaxWidth(), icon = AppIcons.Clock)
                else -> StatCard("Cobros rechazados", tenants.count { it.status == TenantStatus.PAST_DUE }.toString(), Modifier.fillMaxWidth(), icon = AppIcons.Warning)
            }
        }
        Spacer(Modifier.height(Space.x5))
        OpPanel(Modifier.fillMaxWidth()) {
            tenants.forEachIndexed { index, t ->
                if (index > 0) AppDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                    TenantMonogram(t.name, t.primary)
                    Spacer(Modifier.width(Space.x3))
                    Column(Modifier.weight(1f)) {
                        AppText(t.name, style = AppTheme.type.title, maxLines = 1)
                        AppText(
                            "Plan ${t.planName} · ${nextBillingLabel(t.status, t.createdAt)}",
                            style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1,
                        )
                    }
                    if (!compact) {
                        AppText("Mercado Pago · preapproval", style = AppTheme.type.bodySm, color = colors.inkSubtle, maxLines = 1)
                        Spacer(Modifier.width(Space.x5))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AppText(formatMoney(planPrice[t.planId] ?: t.mrrCents) + "/mes", style = AppTheme.type.title, maxLines = 1)
                        Spacer(Modifier.height(Space.x1))
                        StatusPill(t.status.billingLabel(), tone = t.status.tone())
                    }
                }
            }
            AppDivider()
            Row(Modifier.fillMaxWidth().padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
                AppText("Total recurrente (suscripciones al corriente)", Modifier.weight(1f), color = colors.inkMuted)
                AppText(formatMoney(mrr) + "/mes", style = AppTheme.type.h3)
            }
        }
        Spacer(Modifier.height(Space.x4))
        OpNotice("Las fechas de cobro son una proyección; la fecha real la dicta la suscripción (preapproval) de Mercado Pago de cada restaurante.")
        Spacer(Modifier.height(Space.x8))
    }
}

// ───────────────────────── Soporte ─────────────────────────

private data class SupportTicket(val id: String, val subject: String, val tenant: String, val age: String, val open: Boolean, val urgent: Boolean = false)

private val SAMPLE_TICKETS = listOf(
    SupportTicket("#1042", "No me llega el depósito de un pedido pagado con OXXO", "Restaurante de ejemplo 4", "hace 18 min", open = true, urgent = true),
    SupportTicket("#1041", "¿Cómo imprimo los QR por mesa?", "Miga Demo", "hace 2 h", open = true),
    SupportTicket("#1039", "Quiero cambiar mi dominio propio", "Brasa Demo", "hace 5 h", open = true),
    SupportTicket("#1036", "Conectar Uber Eats a la pantalla de cocina", "Nori Demo", "ayer", open = false),
    SupportTicket("#1031", "Cambio de plan Esencial a Pro", "Restaurante de ejemplo 2", "hace 2 d", open = false),
)

internal fun openTicketCount(): Int = SAMPLE_TICKETS.count { it.open }

@Composable
internal fun SupportSection() {
    val colors = AppTheme.colors
    OpPage {
        PageHeader("Soporte", subtitle = "Conversaciones recientes con restaurantes")
        OpPanel(Modifier.fillMaxWidth()) {
            SAMPLE_TICKETS.forEachIndexed { index, ticket ->
                if (index > 0) AppDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                    AppText(ticket.id, Modifier.width(64.dp), style = monoStyle(), color = colors.inkSubtle)
                    Column(Modifier.weight(1f)) {
                        AppText(ticket.subject, style = AppTheme.type.title, maxLines = 2)
                        AppText("${ticket.tenant} · ${ticket.age}", style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
                    }
                    Spacer(Modifier.width(Space.x3))
                    StatusPill(
                        if (!ticket.open) "Resuelto" else if (ticket.urgent) "Urgente" else "Abierto",
                        tone = if (!ticket.open) Tone.SUCCESS else if (ticket.urgent) Tone.DANGER else Tone.WARNING,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.x4))
        OpNotice("Datos de ejemplo. El módulo de soporte se conecta en una fase posterior.")
        Spacer(Modifier.height(Space.x8))
    }
}

// ───────────────────────── Credenciales maestras ─────────────────────────

private data class CredentialGroup(val name: String, val purpose: String, val mode: String, val tone: Tone, val variables: List<String>)

private val CREDENTIAL_GROUPS = listOf(
    CredentialGroup(
        "Mercado Pago · app de la plataforma",
        "OAuth de cada restaurante, comisión por pedido (marketplace_fee), suscripciones (preapproval) y webhooks.",
        "Sandbox", Tone.WARNING,
        listOf("MP_CLIENT_ID", "MP_CLIENT_SECRET", "MP_ACCESS_TOKEN", "MP_WEBHOOK_SECRET", "MP_REDIRECT_URI"),
    ),
    CredentialGroup(
        "Lalamove", "Cotización y envío de repartidores para pedidos a domicilio.", "Sin configurar", Tone.NEUTRAL,
        listOf("LALAMOVE_API_KEY", "LALAMOVE_API_SECRET", "LALAMOVE_BASE_URL"),
    ),
    CredentialGroup(
        "Uber Eats", "Recepción de pedidos y sincronización de menú.", "Simulado", Tone.NEUTRAL,
        listOf("UBER_EATS_CLIENT_ID", "UBER_EATS_CLIENT_SECRET"),
    ),
    CredentialGroup(
        "Rappi", "Recepción de pedidos y sincronización de menú.", "Simulado", Tone.NEUTRAL,
        listOf("RAPPI_CLIENT_ID", "RAPPI_CLIENT_SECRET"),
    ),
    CredentialGroup(
        "DiDi Food", "Recepción de pedidos y sincronización de menú.", "Simulado", Tone.NEUTRAL,
        listOf("DIDI_APP_ID", "DIDI_APP_SECRET"),
    ),
)

@Composable
internal fun CredentialsSection(store: OperatorStore) {
    val colors = AppTheme.colors
    val size = LocalWindowSize.current
    OpPage {
        PageHeader("Credenciales maestras", subtitle = "Llaves de la plataforma para pagos, reparto y marketplaces")
        OpNotice(
            if (store.isRemote) "Las credenciales viven como variables de entorno del backend; aquí solo se indica cuáles usa cada servicio."
            else "Modo demostración local: no hay backend conectado. Las credenciales se configuran como variables de entorno del backend.",
            icon = AppIcons.Lock,
        )
        Spacer(Modifier.height(Space.x4))
        OpGrid(CREDENTIAL_GROUPS, columns = if (size == WindowSize.EXPANDED) 2 else 1) { group ->
            OpPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(Space.x5), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppText(group.name, Modifier.weight(1f), style = AppTheme.type.title)
                        TagBadge(group.mode, tone = group.tone)
                    }
                    AppText(group.purpose, style = AppTheme.type.bodySm, color = colors.inkMuted)
                    AppDivider()
                    group.variables.forEach { variable ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(variable, Modifier.weight(1f), style = monoStyle())
                            AppText("••••••••", style = monoStyle(), color = colors.inkSubtle)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x8))
    }
}
