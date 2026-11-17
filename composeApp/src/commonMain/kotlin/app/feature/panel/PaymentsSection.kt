package app.feature.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.platform.openExternalUrl
import app.shared.model.MpConnectionStatus
import app.shared.model.PaymentMethod
import app.shared.util.label
import app.shell.LocalRepo
import app.ui.components.*
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private fun PaymentMethod.icon(): ImageVector = when (this) {
    PaymentMethod.CARD -> AppIcons.CreditCard
    PaymentMethod.OXXO -> AppIcons.Cash
    PaymentMethod.SPEI -> AppIcons.Bank
    PaymentMethod.MP_WALLET -> AppIcons.Wallet
    PaymentMethod.PAYMENT_LINK -> AppIcons.Link
}

@Composable
internal fun PaymentsSection() {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoader { repo.mercadoPagoStatus() }
    var busy by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    fun act(fallback: String, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toasts.show(errorText(e, fallback), Tone.DANGER)
            } finally {
                busy = false
            }
        }
    }

    val connect: () -> Unit = {
        act("No pudimos conectar con Mercado Pago") {
            val status = repo.connectMercadoPago()
            loader.set(status)
            val url = status.oauthUrl
            if (url != null) {
                openExternalUrl(url)
                if (!status.connected) Toasts.show("Termina la autorización en la ventana de Mercado Pago", Tone.INFO)
            }
            if (status.connected) Toasts.show("Mercado Pago quedó conectado. Ya puedes cobrar.", Tone.SUCCESS)
        }
    }
    val disconnect: () -> Unit = {
        act("No pudimos desconectar la cuenta") {
            loader.set(repo.disconnectMercadoPago())
            confirmDisconnect = false
            Toasts.show("Cuenta de Mercado Pago desconectada", Tone.NEUTRAL)
        }
    }

    PanelPage(maxWidth = 980.dp) {
        PageHeader("Pagos", subtitle = "Cobra en línea y recibe el dinero directo en tu cuenta")
        when (val state = loader.state) {
            Load.Loading -> {
                SkeletonRows(rows = 3)
                SkeletonRows(rows = 5)
            }
            is Load.Failed -> ErrorState(state.message, onRetry = { loader.reload() })
            is Load.Ready -> PaymentsContent(status = state.value, busy = busy, onConnect = connect, onDisconnect = { confirmDisconnect = true })
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = "Desconectar Mercado Pago",
            message = "Tu menú dejará de aceptar pagos en línea hasta que vuelvas a conectar una cuenta.",
            confirmText = "Desconectar", loading = busy,
            onConfirm = disconnect,
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun PaymentsContent(status: MpConnectionStatus, busy: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val size = LocalWindowSize.current
    val c = AppTheme.colors

    AppCard(Modifier.fillMaxWidth(), padding = Space.x5, elevated = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                IconBadge(AppIcons.Wallet, tone = if (status.connected) Tone.SUCCESS else Tone.NEUTRAL, size = 52.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText("Mercado Pago", style = AppTheme.type.h3)
                    AppText(
                        if (status.connected) "Cuenta ${status.mpUserId ?: "vinculada"}" else "Aún no has vinculado tu cuenta",
                        style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1,
                    )
                }
                if (size != WindowSize.COMPACT) {
                    StatusPill(if (status.connected) "Conectado" else "Sin conectar", tone = if (status.connected) Tone.SUCCESS else Tone.WARNING)
                }
            }
            if (size == WindowSize.COMPACT) {
                StatusPill(if (status.connected) "Conectado" else "Sin conectar", tone = if (status.connected) Tone.SUCCESS else Tone.WARNING)
            }
            AppText("El dinero de cada venta cae directo en tu cuenta de Mercado Pago", style = AppTheme.type.title)
            AppText(
                "Tus clientes pagan desde tu menú y el cobro se acredita a tu nombre. Nadie retiene tu dinero: solo se descuenta la comisión por pedido.",
                color = c.inkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                if (status.connected) {
                    AppButton("Desconectar", onClick = onDisconnect, variant = ButtonVariant.SECONDARY, loading = busy)
                } else {
                    AppButton("Conectar con Mercado Pago", onClick = onConnect, icon = AppIcons.Plug, loading = busy, size = ButtonSize.LG)
                }
            }
        }
    }

    val commission: @Composable (Modifier) -> Unit = { modifier ->
        AppCard(modifier, padding = Space.x5) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SectionHeader("Comisión", subtitle = "Según tu plan actual")
                AppText(formatBps(status.feeBps), style = AppTheme.type.display)
                AppText("por pedido", style = AppTheme.type.title, color = c.inkMuted)
                AppDivider()
                KeyValueRow("En un pedido de \$500", "\$" + centsToPesosText(50_000L * status.feeBps / 10_000L))
                AppText("Se descuenta sola en cada cobro. Sin rentas ocultas ni cargos por retiro.", style = AppTheme.type.bodySm, color = c.inkSubtle)
            }
        }
    }
    val methods: @Composable (Modifier) -> Unit = { modifier ->
        AppCard(modifier, padding = Space.x5) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SectionHeader("Métodos aceptados", subtitle = "Lo que tus clientes pueden usar al pagar")
                if (status.methods.isEmpty()) {
                    AppText("Aún no hay métodos habilitados.", style = AppTheme.type.bodySm, color = c.inkMuted)
                }
                status.methods.forEach { method ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        IconBadge(method.icon(), size = 36.dp)
                        AppText(method.label(), Modifier.weight(1f), maxLines = 1)
                        PanelIcon(AppIcons.Check, tint = if (status.connected) c.success else c.inkSubtle)
                    }
                }
            }
        }
    }

    if (size == WindowSize.EXPANDED) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            commission(Modifier.weight(1f))
            methods(Modifier.weight(1.3f))
        }
    } else {
        commission(Modifier.fillMaxWidth())
        methods(Modifier.fillMaxWidth())
    }

    Row(
        Modifier.fillMaxWidth().clip(AppTheme.shapes.md).background(if (status.liveMode) c.successSoft else c.infoSoft).padding(Space.x4),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        PanelIcon(AppIcons.Info, tint = if (status.liveMode) c.success else c.info, size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(if (status.liveMode) "Modo producción" else "Modo de pruebas (sandbox)", style = AppTheme.type.title)
            AppText(
                if (status.liveMode) "Los cobros son reales y se acreditan en tu cuenta de Mercado Pago."
                else "Los cobros son simulados y no mueven dinero real. Úsalo para probar tu menú de punta a punta antes de vender.",
                style = AppTheme.type.bodySm, color = c.inkMuted,
            )
        }
    }
}
