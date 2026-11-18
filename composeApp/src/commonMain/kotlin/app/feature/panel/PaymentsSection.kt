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

    AppCard(Modifier.fillMaxWidth(), padding = Space.x4, elevated = true) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                IconBadge(AppIcons.Wallet)
                Column(Modifier.weight(1f)) {
                    AppText("Mercado Pago", style = AppTheme.type.title, maxLines = 1)
                    AppText(
                        if (status.connected) "Cuenta ${status.mpUserId ?: "vinculada"}" else "Aún no has vinculado tu cuenta",
                        style = if (status.connected) AppTheme.type.mono else AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1,
                    )
                }
                StatusPill(if (status.connected) "Conectado" else "Sin conectar", tone = if (status.connected) Tone.SUCCESS else Tone.WARNING)
            }
            AppDivider()
            AppText("El dinero de cada venta cae directo en tu cuenta", style = AppTheme.type.h2)
            AppText(
                "Tus clientes pagan desde tu menú y el cobro se acredita a tu nombre. Nadie retiene tu dinero: solo se descuenta la comisión por pedido.",
                modifier = Modifier.widthIn(max = 640.dp),
                color = c.inkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                if (status.connected) {
                    AppButton("Desconectar", onClick = onDisconnect, variant = ButtonVariant.SECONDARY, loading = busy)
                } else {
                    AppButton("Conectar con Mercado Pago", onClick = onConnect, loading = busy, trailingIcon = AppIcons.Forward, fullWidth = size == WindowSize.COMPACT)
                }
            }
        }
    }

    val commission: @Composable (Modifier) -> Unit = { modifier ->
        AppCard(modifier, padding = Space.x4) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SectionHeader("Comisión", label = "Según tu plan")
                Row {
                    val type = AppTheme.type
                    AppText(
                        formatBps(status.feeBps), modifier = Modifier.alignByBaseline(), maxLines = 1,
                        style = type.display.copy(fontFamily = type.monoLg.fontFamily, fontWeight = type.monoLg.fontWeight, letterSpacing = type.monoLg.letterSpacing),
                    )
                    Spacer(Modifier.width(Space.x2))
                    AppText("por pedido", modifier = Modifier.alignByBaseline(), style = type.bodySm, color = c.inkMuted)
                }
                AppDivider()
                KeyValueRow("En un pedido de \$500", "\$" + centsToPesosText(50_000L * status.feeBps / 10_000L))
                AppText("Se descuenta sola en cada cobro. Sin rentas ocultas ni cargos por retiro.", style = AppTheme.type.bodySm, color = c.inkSubtle)
            }
        }
    }
    val methods: @Composable (Modifier) -> Unit = { modifier ->
        AppCard(modifier, padding = Space.x4) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SectionHeader("Métodos aceptados", label = "${status.methods.size} activos")
                if (status.methods.isEmpty()) {
                    AppText("Aún no hay métodos habilitados.", style = AppTheme.type.bodySm, color = c.inkMuted)
                }
                status.methods.forEach { method ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        IconBadge(method.icon(), size = Space.x10)
                        AppText(method.label(), Modifier.weight(1f), style = AppTheme.type.title, maxLines = 1)
                        StatusDot(if (status.connected) c.positive else c.inkSubtle)
                    }
                }
            }
        }
    }

    if (size == WindowSize.EXPANDED) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            commission(Modifier.weight(1f).fillMaxHeight())
            methods(Modifier.weight(1.3f).fillMaxHeight())
        }
    } else {
        commission(Modifier.fillMaxWidth())
        methods(Modifier.fillMaxWidth())
    }

    PanelNotice(
        title = if (status.liveMode) "Modo producción" else "Modo de pruebas (sandbox)",
        text = if (status.liveMode) "Los cobros son reales y se acreditan en tu cuenta de Mercado Pago."
        else "Los cobros son simulados y no mueven dinero real. Úsalo para probar tu menú de punta a punta antes de vender.",
        tone = if (status.liveMode) Tone.SUCCESS else Tone.INFO,
    )
}
