package app.feature.diner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.shared.model.OrderType
import app.shared.model.PaymentMethod
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.shared.util.label
import app.ui.components.AppButton
import app.ui.components.AppChip
import app.ui.components.AppDivider
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.AppTextField
import app.ui.components.ButtonSize
import app.ui.components.DishImage
import app.ui.components.EmptyState
import app.ui.components.QuantityStepper
import app.ui.components.SegmentedControl
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space

/** Carrito a pantalla completa (teléfono y tablet). */
@Composable
fun CartScreen(state: DinerState, tenant: TenantPublic, onPay: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        DinerTopBar(title = "Tu pedido", subtitle = tenant.name, onBack = { state.screen = DinerScreen.MENU })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            CartBody(state, tenant, onPay, Modifier.widthIn(max = 640.dp).fillMaxSize(), onBrowse = { state.screen = DinerScreen.MENU })
        }
    }
}

/** Panel de pedido fijo a la derecha (escritorio, 360dp). */
@Composable
fun CartPanel(state: DinerState, tenant: TenantPublic, onPay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.lg
    Column(
        modifier.width(360.dp).fillMaxHeight().padding(top = TOP_BAR_HEIGHT + Space.x4, end = Space.x6, bottom = Space.x6),
    ) {
        Column(Modifier.fillMaxSize().clip(shape).background(colors.surface).border(1.dp, colors.line, shape)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x4), verticalAlignment = Alignment.CenterVertically) {
                DinerIcon(AppIcons.Bag, tint = colors.ink, size = 20.dp)
                Spacer(Modifier.width(Space.x2))
                AppText("Tu pedido", modifier = Modifier.weight(1f), style = AppTheme.type.h3)
                if (state.itemCount > 0) {
                    AppText(
                        if (state.itemCount == 1) "1 platillo" else "${state.itemCount} platillos",
                        style = AppTheme.type.label,
                        color = colors.inkMuted,
                    )
                }
            }
            AppDivider()
            CartBody(state, tenant, onPay, Modifier.fillMaxSize())
        }
    }
}

/** Contenido compartido del carrito: renglones, tipo de pedido, propina, pago y totales. */
@Composable
fun CartBody(state: DinerState, tenant: TenantPublic, onPay: () -> Unit, modifier: Modifier = Modifier, onBrowse: (() -> Unit)? = null) {
    val colors = AppTheme.colors
    if (state.cart.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                title = "Tu pedido está vacío",
                modifier = Modifier.fillMaxWidth().padding(Space.x6),
                message = "Agrega platillos del menú y aquí verás tu cuenta.",
                icon = AppIcons.Bag,
                actionText = if (onBrowse != null) "Ver el menú" else null,
                onAction = onBrowse,
            )
        }
    } else {
        Column(modifier) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Space.x4, vertical = Space.x4),
            ) {
                items(items = state.cart, key = { it.key }) { line ->
                    Column(Modifier.animateItem()) {
                        CartLineRow(line, state)
                        AppDivider(Modifier.padding(vertical = Space.x3))
                    }
                }
                item(key = "sec-type") { OrderTypeSection(state, tenant) }
                item(key = "sec-tip") { TipSection(state) }
                item(key = "sec-notes") {
                    Column(Modifier.fillMaxWidth().padding(top = Space.x5)) {
                        AppTextField(
                            value = state.orderNotes,
                            onValueChange = { if (it.length <= 200) state.orderNotes = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Notas para el restaurante",
                            placeholder = "Alergias, cubiertos, indicaciones…",
                            singleLine = false,
                            minLines = 2,
                        )
                    }
                }
                item(key = "sec-pay") { PaymentSection(state) }
                item(key = "sec-totals") { TotalsSection(state, tenant) }
            }
            AppDivider()
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = Space.x4, vertical = Space.x3)) {
                AppButton(
                    text = "Pagar " + formatMoney(state.totalCents),
                    onClick = onPay,
                    size = ButtonSize.LG,
                    icon = AppIcons.Lock,
                    loading = state.payPhase == PayPhase.CREATING,
                    enabled = state.payPhase == PayPhase.IDLE || state.payPhase == PayPhase.CREATING,
                    fullWidth = true,
                )
            }
        }
    }
}

@Composable
private fun CartLineRow(line: CartLine, state: DinerState) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        DishImage(line.item.imageUrl, modifier = Modifier.size(68.dp), contentDescription = line.item.name, shape = AppTheme.shapes.sm)
        Spacer(Modifier.width(Space.x3))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                AppText(line.item.name, modifier = Modifier.weight(1f).padding(top = Space.x1), style = AppTheme.type.title, maxLines = 2)
                AppIconButton(AppIcons.Delete, onClick = { state.remove(line.key) }, contentDescription = "Quitar del pedido", tint = colors.inkSubtle)
            }
            if (line.summary.isNotBlank()) {
                AppText(line.summary, style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 3)
            }
            Spacer(Modifier.height(Space.x2))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                QuantityStepper(quantity = line.quantity, onChange = { state.setQuantity(line.key, it) }, min = 0, max = 99, compact = true)
                Spacer(Modifier.weight(1f))
                AnimatedMoney(line.totalCents, style = AppTheme.type.title)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = Space.x5, bottom = Space.x3)) {
        AppText(title, style = AppTheme.type.title)
        if (subtitle != null) AppText(subtitle, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
    }
}

@Composable
private fun OrderTypeSection(state: DinerState, tenant: TenantPublic) {
    val colors = AppTheme.colors
    val types = remember(tenant.orderTypes) { tenant.orderTypes.ifEmpty { OrderType.entries.toList() } }
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("¿Cómo lo quieres?")
        if (types.size > 1) {
            SegmentedControl(
                options = types.map { it.label() },
                selectedIndex = types.indexOf(state.orderType).coerceAtLeast(0),
                onSelect = { index -> types.getOrNull(index)?.let { state.orderType = it } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.x3))
        }
        AnimatedContent(
            targetState = state.orderType,
            transitionSpec = {
                (fadeIn(tween(Motion.BASE, easing = Motion.standard)) + slideInVertically(tween(Motion.BASE, easing = Motion.standard)) { it / 6 })
                    .togetherWith(fadeOut(tween(Motion.FAST, easing = Motion.exit)))
            },
            label = "order-type-fields",
        ) { type ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DinerIcon(AppIcons.Bike, tint = colors.inkMuted, size = 18.dp)
                            Spacer(Modifier.width(Space.x2))
                            AppText("Envío a domicilio: " + formatMoney(DELIVERY_FEE_CENTS), style = AppTheme.type.bodySm, color = colors.inkMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipSection(state: DinerState) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Propina", "Opcional. Va completa para el equipo.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            TIP_OPTIONS.forEach { percent ->
                AppChip(
                    text = if (percent == 0) "Sin propina" else "$percent%",
                    selected = state.tipPercent == percent,
                    onClick = { state.tipPercent = percent },
                )
            }
        }
    }
}

@Composable
private fun PaymentSection(state: DinerState) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Método de pago")
        Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            DINER_PAYMENT_METHODS.forEach { method ->
                PaymentMethodRow(method, selected = state.paymentMethod == method, onClick = { state.paymentMethod = method })
            }
        }
        Spacer(Modifier.height(Space.x3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DinerIcon(AppIcons.Lock, tint = colors.success, size = 16.dp)
            Spacer(Modifier.width(Space.x2))
            AppText("Pago seguro con Mercado Pago", style = AppTheme.type.bodySm, color = colors.inkMuted)
        }
    }
}

@Composable
private fun PaymentMethodRow(method: PaymentMethod, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.md
    val stroke by animateColorAsState(if (selected) colors.primary else colors.line, tween(Motion.BASE), label = "pm-stroke")
    val fill by animateColorAsState(if (selected) colors.primarySoft else colors.surface, tween(Motion.BASE), label = "pm-fill")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(fill)
            .border(if (selected) 1.5.dp else 1.dp, stroke, shape)
            .pressable(scale = 0.99f) { onClick() }
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(AppTheme.shapes.sm).background(colors.surfaceSunken), contentAlignment = Alignment.Center) {
            DinerIcon(paymentIcon(method), tint = if (selected) colors.primary else colors.inkMuted, size = 22.dp)
        }
        Spacer(Modifier.width(Space.x3))
        Column(Modifier.weight(1f)) {
            AppText(method.label(), style = AppTheme.type.title, maxLines = 1)
            AppText(paymentHint(method), style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 2)
        }
        Spacer(Modifier.width(Space.x3))
        SelectionMark(checked = selected, round = true)
    }
}

@Composable
private fun TotalsSection(state: DinerState, tenant: TenantPublic) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Space.x5)) {
        if (!tenant.isOpen) {
            Row(
                Modifier.fillMaxWidth().clip(AppTheme.shapes.md).background(colors.warningSoft).padding(Space.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DinerIcon(AppIcons.Clock, tint = colors.warning, size = 18.dp)
                Spacer(Modifier.width(Space.x2))
                AppText("El restaurante está cerrado. Prepararemos tu pedido al abrir.", style = AppTheme.type.bodySm, color = colors.ink)
            }
            Spacer(Modifier.height(Space.x4))
        }
        TotalsRow("Subtotal", state.subtotalCents)
        if (state.deliveryFeeCents > 0) TotalsRow("Envío", state.deliveryFeeCents)
        if (state.tipCents > 0) TotalsRow("Propina (${state.tipPercent}%)", state.tipCents)
        AppDivider(Modifier.padding(vertical = Space.x2))
        TotalsRow("Total", state.totalCents, strong = true)
        Spacer(Modifier.height(Space.x2))
    }
}

/** Barra flotante del carrito (teléfono/tablet): contador que rebota al agregar, total y "Ver pedido". */
@Composable
fun FloatingCartBar(state: DinerState, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val colors = AppTheme.colors
    val bump = remember { Animatable(1f) }
    LaunchedEffect(state.addPulse) {
        if (state.addPulse > 0) {
            bump.snapTo(1.4f)
            bump.animateTo(1f, Motion.bouncy())
        }
    }
    AnimatedVisibility(
        visible = state.itemCount > 0,
        modifier = modifier,
        enter = slideInVertically(tween(Motion.SLOW, easing = Motion.standard)) { it } + fadeIn(tween(Motion.BASE)),
        exit = slideOutVertically(tween(Motion.BASE, easing = Motion.exit)) { it } + fadeOut(tween(Motion.BASE)),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x4), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .shadow(12.dp, AppTheme.shapes.control)
                    .clip(AppTheme.shapes.control)
                    .background(colors.primary)
                    .pressable { onOpen() }
                    .heightIn(min = 60.dp)
                    .padding(horizontal = Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.onPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(state.itemCount.toString(), style = AppTheme.type.label, color = colors.primary, maxLines = 1)
                }
                Spacer(Modifier.width(Space.x3))
                AppText("Ver pedido", modifier = Modifier.weight(1f), style = AppTheme.type.title, color = colors.onPrimary, maxLines = 1)
                AnimatedMoney(state.totalCents, style = AppTheme.type.title, color = colors.onPrimary)
                Spacer(Modifier.width(Space.x2))
                DinerIcon(AppIcons.ChevronRight, tint = colors.onPrimary, size = 18.dp)
            }
        }
    }
}
