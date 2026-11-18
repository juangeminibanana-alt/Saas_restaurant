package app.feature.diner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.OrderType
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.shared.util.label
import app.ui.components.AppButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.BottomBar
import app.ui.components.DishImage
import app.ui.components.EmptyState
import app.ui.components.LabelText
import app.ui.components.OptionList
import app.ui.components.OptionRow
import app.ui.components.QuantityStepper
import app.ui.components.SectionHeader
import app.ui.components.SegmentedControl
import app.ui.components.edgeBottom
import app.ui.components.edgeTop
import app.ui.components.flatSurface
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space

/** Ancho del panel de pedido fijo en escritorio. */
private val CartPanelWidth = 360.dp

/** Pedido a pantalla completa (teléfono y tablet). */
@Composable
fun CartScreen(state: DinerState, tenant: TenantPublic, onPay: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DinerTopBar(title = "Tu pedido", subtitle = tenant.name, onBack = { state.screen = DinerScreen.MENU })
        CartBody(state, tenant, onPay, Modifier.fillMaxSize(), onBrowse = { state.screen = DinerScreen.MENU })
    }
}

/** Panel de pedido fijo a la derecha (escritorio): un mosaico de superficie 1 con filete que ocupa todo el alto. */
@Composable
fun CartPanel(state: DinerState, tenant: TenantPublic, onPay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier.width(CartPanelWidth).fillMaxHeight().padding(top = Space.x6, end = Space.x6, bottom = Space.x6)) {
        CompositionLocalProvider(LocalSurfaceLevel provides 1) {
            Column(Modifier.fillMaxSize().flatSurface(colors.surface, colors.line, AppTheme.shapes.lg)) {
                SectionHeader(
                    title = "Tu pedido",
                    modifier = Modifier.edgeBottom(colors.line).padding(horizontal = Space.gutter, vertical = Space.x4),
                    label = if (state.itemCount > 0) dishCount(state.itemCount) else null,
                )
                CartBody(state, tenant, onPay, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * Contenido compartido del pedido: renglones, tipo de pedido, propina, notas, pago y cuenta, con la barra
 * sólida al pie (total en mono a la izquierda, la brasa "Pagar" a la derecha).
 * El contenido se centra a [DinerPageWidth]; la barra va de borde a borde.
 */
@Composable
fun CartBody(state: DinerState, tenant: TenantPublic, onPay: () -> Unit, modifier: Modifier = Modifier, onBrowse: (() -> Unit)? = null) {
    if (state.cart.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                title = "Tu pedido está vacío",
                modifier = Modifier.fillMaxWidth().padding(Space.x2),
                message = "Agrega platillos del menú y aquí verás tu cuenta.",
                icon = AppIcons.Bag,
                actionText = if (onBrowse != null) "Ver el menú" else null,
                onAction = onBrowse,
            )
        }
        return
    }
    BoxWithConstraints(modifier) {
        // Margen de página, o lo que haga falta para centrar el contenido a su ancho máximo.
        val side: Dp = if (maxWidth > DinerPageWidth) (maxWidth - DinerPageWidth) / 2 + Space.gutter else Space.gutter
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = side, end = side, top = Space.x1, bottom = Space.x6),
            ) {
                itemsIndexed(items = state.cart, key = { _, line -> line.key }) { index, line ->
                    CartLineRow(line, state, last = index == state.cart.lastIndex, modifier = Modifier.animateItem())
                }
                item(key = "sec-type") { OrderTypeSection(state, tenant) }
                item(key = "sec-tip") { TipSection(state) }
                item(key = "sec-notes") {
                    AppTextField(
                        value = state.orderNotes,
                        onValueChange = { if (it.length <= 200) state.orderNotes = it },
                        modifier = Modifier.fillMaxWidth().padding(top = Space.x6),
                        label = "Notas para el restaurante",
                        placeholder = "Alergias, cubiertos, indicaciones…",
                        singleLine = false,
                        minLines = 2,
                    )
                }
                item(key = "sec-pay") { PaymentSection(state) }
                item(key = "sec-totals") { TotalsSection(state, tenant) }
            }
            BottomBar {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.widthIn(max = DinerPageWidth - Space.gutter * 2).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.x3),
                    ) {
                        Column(Modifier.width(BarSummaryWidth)) {
                            LabelText("Total")
                            AnimatedMoney(state.totalCents, style = AppTheme.type.monoLg)
                        }
                        AppButton(
                            text = "Pagar",
                            onClick = onPay,
                            modifier = Modifier.weight(1f),
                            icon = AppIcons.Lock,
                            loading = state.payPhase == PayPhase.CREATING,
                            enabled = state.payPhase == PayPhase.IDLE || state.payPhase == PayPhase.CREATING,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renglón del pedido: la misma fila del menú — miniatura de 80, nombre 16/20 y lo elegido debajo — con el
 * importe del renglón en mono y, a la derecha, quitar y la cantidad (ficha de brasa: ya es tu pedido).
 */
@Composable
private fun CartLineRow(line: CartLine, state: DinerState, last: Boolean, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val frame = framingFor(line.item.imageUrl, PhotoUse.THUMB)
    Row(
        modifier
            .fillMaxWidth()
            .then(if (last) Modifier else Modifier.edgeBottom(colors.line))
            .padding(vertical = Space.x3),
    ) {
        DishImage(
            line.item.imageUrl,
            modifier = Modifier.size(Sizes.thumb),
            contentDescription = null,
            alignment = frame.alignment,
            zoom = frame.zoom,
        )
        Spacer(Modifier.width(Space.x3))
        Column(Modifier.weight(1f).heightIn(min = Sizes.thumb), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth().pullUp(Space.x1)) {
                AppText(line.item.name, style = AppTheme.type.title, maxLines = 2)
                if (line.summary.isNotBlank()) {
                    AppText(line.summary, style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 3)
                }
            }
            Row(Modifier.fillMaxWidth().height(Sizes.controlSm), verticalAlignment = Alignment.CenterVertically) {
                AnimatedMoney(line.totalCents, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .size(Sizes.controlSm)
                        .semantics { contentDescription = "Quitar del pedido" }
                        .pressable(scale = 0.92f) { state.remove(line.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    DinerIcon(AppIcons.Delete, tint = colors.inkSubtle)
                }
                Spacer(Modifier.width(Space.x1))
                QuantityStepper(
                    quantity = line.quantity,
                    onChange = { state.setQuantity(line.key, it) },
                    min = 0,
                    max = 99,
                    compact = true,
                    inOrder = true,
                )
            }
        }
    }
}

@Composable
private fun OrderTypeSection(state: DinerState, tenant: TenantPublic) {
    val types = remember(tenant.orderTypes) { tenant.orderTypes.ifEmpty { OrderType.entries.toList() } }
    Column(Modifier.fillMaxWidth().padding(top = Space.x6)) {
        GroupHead("¿Cómo lo quieres?")
        if (types.size > 1) {
            SegmentedControl(
                options = types.map { it.label() },
                selectedIndex = types.indexOf(state.orderType).coerceAtLeast(0),
                onSelect = { index -> types.getOrNull(index)?.let { state.orderType = it } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.x4))
        }
        AnimatedContent(
            targetState = state.orderType,
            transitionSpec = { fadeIn(tween(Motion.BASE, easing = Motion.standard)).togetherWith(fadeOut(tween(Motion.FAST, easing = Motion.exit))) },
            label = "order-type-fields",
        ) { type ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                if (type == OrderType.DINE_IN) {
                    AppTextField(
                        value = state.tableLabel,
                        onValueChange = { state.tableLabel = it.take(12) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Mesa",
                        placeholder = "Número de mesa",
                        leadingIcon = AppIcons.Table,
                        error = if (state.showErrors && state.tableLabel.isBlank()) "Indica tu mesa" else null,
                    )
                } else {
                    AppTextField(
                        value = state.customerName,
                        onValueChange = { state.customerName = it.take(60) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Nombre",
                        placeholder = "¿A nombre de quién?",
                        leadingIcon = AppIcons.Person,
                        error = if (state.showErrors && state.customerName.isBlank()) "Escribe tu nombre" else null,
                    )
                    AppTextField(
                        value = state.customerPhone,
                        onValueChange = { value -> state.customerPhone = value.filter { it.isDigit() || it == ' ' || it == '+' }.take(16) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Teléfono",
                        placeholder = "10 dígitos",
                        leadingIcon = AppIcons.Phone,
                        keyboardType = KeyboardType.Phone,
                        error = if (state.showErrors && state.customerPhone.count { it.isDigit() } < 8) "Escribe un teléfono válido" else null,
                    )
                    if (type == OrderType.DELIVERY) {
                        AppTextField(
                            value = state.address,
                            onValueChange = { state.address = it.take(160) },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Dirección de entrega",
                            placeholder = "Calle, número, colonia y referencias",
                            leadingIcon = AppIcons.Location,
                            singleLine = false,
                            minLines = 2,
                            error = if (state.showErrors && state.address.isBlank()) "Escribe la dirección" else null,
                        )
                        InfoLine(AppIcons.Bike, "Envío a domicilio: " + formatMoney(DELIVERY_FEE_CENTS))
                    }
                }
            }
        }
    }
}

@Composable
private fun TipSection(state: DinerState) {
    Column(Modifier.fillMaxWidth().padding(top = Space.x6)) {
        GroupHead("Propina", label = "Opcional")
        SegmentedControl(
            options = TIP_OPTIONS.map { if (it == 0) "Sin propina" else "$it%" },
            selectedIndex = TIP_OPTIONS.indexOf(state.tipPercent).coerceAtLeast(0),
            onSelect = { index -> TIP_OPTIONS.getOrNull(index)?.let { state.tipPercent = it } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.x2))
        AppText("Va completa para el equipo.", style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
    }
}

@Composable
private fun PaymentSection(state: DinerState) {
    Column(Modifier.fillMaxWidth().padding(top = Space.x6)) {
        GroupHead("Método de pago", label = "Elige 1")
        OptionList {
            DINER_PAYMENT_METHODS.forEachIndexed { index, method ->
                OptionRow(
                    text = method.label(),
                    selected = state.paymentMethod == method,
                    onClick = { state.paymentMethod = method },
                    first = index == 0,
                )
            }
        }
        Spacer(Modifier.height(Space.x3))
        InfoLine(paymentIcon(state.paymentMethod), paymentHint(state.paymentMethod))
        InfoLine(AppIcons.Lock, "Pago seguro con Mercado Pago")
    }
}

/** La cuenta: cada concepto con su importe en mono. El total vive en la barra, siempre a la vista. */
@Composable
private fun TotalsSection(state: DinerState, tenant: TenantPublic) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Space.x6)) {
        if (!tenant.isOpen) {
            DinerNotice("El restaurante está cerrado. Prepararemos tu pedido al abrir.", colors.warning)
            Spacer(Modifier.height(Space.x4))
        }
        Column(Modifier.fillMaxWidth().edgeTop(colors.line).padding(top = Space.x3)) {
            TotalsRow("Subtotal · " + dishCount(state.itemCount), state.subtotalCents)
            if (state.deliveryFeeCents > 0) TotalsRow("Envío", state.deliveryFeeCents)
            if (state.tipCents > 0) TotalsRow("Propina (${state.tipPercent}%)", state.tipCents)
        }
    }
}
