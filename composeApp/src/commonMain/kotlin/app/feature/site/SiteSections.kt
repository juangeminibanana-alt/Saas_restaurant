package app.feature.site

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.platform.openExternalUrl
import app.shared.model.Plan
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.shell.AccentSwatch
import app.ui.components.AppButton
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.DishImage
import app.ui.components.EmptyState
import app.ui.components.ErrorState
import app.ui.components.LabelText
import app.ui.components.Skeleton
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.components.edgeTop
import app.ui.components.flatSurface
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize

private fun order(i: Int): String = (i + 1).toString().padStart(2, '0')

// ───────────────────────── 1. Producto ─────────────────────────

private class Feature(val icon: ImageVector, val title: String, val text: String)

@Composable
internal fun BenefitsSection(modifier: Modifier = Modifier) {
    val columns = when (LocalWindowSize.current) {
        WindowSize.COMPACT -> 1
        WindowSize.MEDIUM -> 2
        WindowSize.EXPANDED -> 3
    }
    val benefits = remember {
        listOf(
            Feature(
                AppIcons.Palette, "Tu marca en todo",
                "Tu nombre, tu color, tu fotografía y tu dominio. Tus clientes sólo ven tu restaurante, nunca el nombre de un tercero.",
            ),
            Feature(
                AppIcons.Qr, "Link y QR por mesa",
                "Comparte un link o imprime un QR para cada mesa. Tus comensales piden desde su teléfono, sin descargar nada.",
            ),
            Feature(
                AppIcons.CreditCard, "Cobra con Mercado Pago",
                "Tarjeta, OXXO, SPEI y saldo en cuenta. Cada pago llega directo a tu propia cuenta de Mercado Pago.",
            ),
            Feature(
                AppIcons.Chef, "Cocina en tiempo real",
                "Cada pedido pagado aparece al instante en la pantalla de cocina, con tiempos y estados claros para tu equipo.",
            ),
            Feature(
                AppIcons.Bike, "Reparto y marketplaces juntos",
                "Tus pedidos directos, los de reparto y los de apps de entrega se concentran en un mismo tablero.",
            ),
            Feature(
                AppIcons.Globe, "En teléfono, tablet, escritorio y web",
                "La misma experiencia en cualquier dispositivo, para tu equipo y para tus clientes.",
            ),
        )
    }
    SiteContainer(modifier) {
        SectionHeading(
            eyebrow = "Producto",
            index = "01",
            title = "Todo lo que tu restaurante necesita para vender en línea",
            subtitle = "Menú, pagos, cocina y reparto en un solo lugar, siempre con la identidad de tu restaurante.",
        )
        Spacer(Modifier.height(Space.x10))
        SiteGrid(count = benefits.size, columns = columns) { i, cellModifier ->
            val benefit = benefits[i]
            Reveal(cellModifier, delayMs = (i % columns) * 40) {
                val c = AppTheme.colors
                SitePanel(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        IconTile(benefit.icon)
                        Spacer(Modifier.weight(1f))
                        LabelText(order(i))
                    }
                    Spacer(Modifier.height(Space.x6))
                    AppText(benefit.title, style = AppTheme.type.h2)
                    Spacer(Modifier.height(Space.x2))
                    AppText(benefit.text, color = c.inkMuted)
                }
            }
        }
    }
}

// ───────────────────────── 2. Cómo funciona ─────────────────────────

@Composable
internal fun HowItWorksSection(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val expanded = LocalWindowSize.current == WindowSize.EXPANDED
    val steps = remember {
        listOf(
            Feature(
                AppIcons.Store, "Crea tu restaurante",
                "Regístrate en minutos con el nombre de tu negocio y empieza con tu primer mes de prueba.",
            ),
            Feature(
                AppIcons.Image, "Sube tu menú y tu marca",
                "Agrega platillos, fotos y precios. Elige el color de tu marca: el resto ya está diseñado.",
            ),
            Feature(
                AppIcons.Qr, "Comparte tu link o QR y vende",
                "Recibe pedidos pagados con Mercado Pago y míralos entrar a cocina en tiempo real.",
            ),
        )
    }
    SiteContainer(modifier) {
        SectionHeading(eyebrow = "Cómo funciona", index = "02", title = "De cero a tu primer pedido en tres pasos")
        Spacer(Modifier.height(Space.x10))
        // Sin tarjetas: tres columnas sobre el fondo, cada una colgada de un filete con su número en mono.
        SiteGrid(count = steps.size, columns = if (expanded) 3 else 1, gap = if (expanded) Space.x8 else Space.x6) { i, cellModifier ->
            val step = steps[i]
            Reveal(cellModifier, delayMs = i * 40) {
                Column(Modifier.fillMaxWidth().edgeTop(c.lineStrong).padding(top = Space.x4)) {
                    AppText(order(i), style = AppTheme.type.monoLg, color = c.inkSubtle)
                    Spacer(Modifier.height(Space.x6))
                    AppText(step.title, style = AppTheme.type.h2)
                    Spacer(Modifier.height(Space.x2))
                    AppText(step.text, color = c.inkMuted)
                }
            }
        }
    }
}

// ───────────────────────── 3. Demos en vivo ─────────────────────────

private class DemoLink(val icon: ImageVector, val title: String, val text: String, val route: String)

@Composable
internal fun DemosSection(data: SiteData, onNavigate: (route: String) -> Unit, onRetry: () -> Unit) {
    val size = LocalWindowSize.current
    val expanded = size == WindowSize.EXPANDED
    val gutter = siteGutter()
    val tenants = data.tenants
    val error = data.tenantsError
    val links = remember {
        listOf(
            DemoLink(AppIcons.Chef, "Pantalla de cocina", "Pedidos entrando en tiempo real", "/cocina"),
            DemoLink(AppIcons.Dashboard, "Panel del restaurante", "Menú, marca, pedidos y pagos", "/panel"),
            DemoLink(AppIcons.Chart, "Consola del operador", "Restaurantes, planes y métricas", "/operador"),
            DemoLink(AppIcons.Eye, "Índice de la demo", "Todas las pantallas en un lugar", "/demo"),
        )
    }
    Column(Modifier.fillMaxWidth()) {
        SiteContainer {
            SectionHeading(
                eyebrow = "Demos en vivo",
                index = "03",
                title = "Tres restaurantes, tres marcas, un mismo sistema",
                subtitle = "Lo único que cambia entre ellos es su fotografía y su color. Ábrelos como los vería un comensal.",
            )
            Spacer(Modifier.height(Space.x10))
        }
        when {
            tenants == null && error != null -> SiteContainer { ErrorState(message = error, onRetry = onRetry) }
            tenants == null -> SiteContainer {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    repeat(if (expanded) 3 else 1) {
                        Skeleton(Modifier.weight(1f).height(280.dp), shape = AppTheme.shapes.lg)
                    }
                }
            }
            expanded -> SiteContainer {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    tenants.forEachIndexed { i, tenant ->
                        Reveal(Modifier.weight(1f), delayMs = i * 40) {
                            TenantCard(tenant, Modifier.fillMaxWidth()) { onNavigate("/r/${tenant.slug}") }
                        }
                    }
                }
            }
            else -> Reveal(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    tenants.forEach { tenant ->
                        TenantCard(tenant, Modifier.width(280.dp)) { onNavigate("/r/${tenant.slug}") }
                    }
                }
            }
        }
        SiteContainer {
            Spacer(Modifier.height(Space.x2))
            val columns = when (size) {
                WindowSize.COMPACT -> 1
                WindowSize.MEDIUM -> 2
                WindowSize.EXPANDED -> 4
            }
            SiteGrid(count = links.size, columns = columns) { i, cellModifier ->
                val link = links[i]
                Reveal(cellModifier, delayMs = (i % columns) * 40) {
                    DemoLinkTile(link, Modifier.fillMaxSize()) { onNavigate(link.route) }
                }
            }
        }
    }
}

/**
 * Mosaico de un restaurante de demostración: su fotografía y, en el pie, nombre, cocina y su acento como muestra
 * sólida con el código en mono. Es la prueba de la marca blanca: mismo mosaico, otro color.
 */
@Composable
private fun TenantCard(tenant: TenantPublic, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    AppTheme(brand = tenant.theme) {
        val c = AppTheme.colors
        Column(
            modifier = modifier
                .pressable(scale = 0.99f) { onOpen() }
                .flatSurface(c.surface, c.lineStrong, AppTheme.shapes.lg),
        ) {
            DishImage(
                url = tenant.theme.coverUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                contentDescription = "Portada de ${tenant.name}",
                shape = RectangleShape,
                ring = false,
            )
            Column(Modifier.fillMaxWidth().edgeTop(c.line).padding(Space.x3)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AppText(tenant.name, style = AppTheme.type.title, maxLines = 1)
                        AppText(tenant.cuisine, style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1)
                    }
                    Spacer(Modifier.width(Space.x3))
                    AccentSwatch(c.primary, c.onPrimary, tenant.theme.primary)
                }
                Spacer(Modifier.height(Space.x3))
                Row(Modifier.fillMaxWidth().edgeTop(c.line).padding(top = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                    LabelText("Ver su menú", modifier = Modifier.weight(1f), color = c.ink)
                    SiteIcon(AppIcons.Forward, tint = c.ink)
                }
            }
        }
    }
}

@Composable
private fun DemoLinkTile(link: DemoLink, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = modifier
            .pressable(scale = 0.99f) { onOpen() }
            .flatSurface(c.surface, c.line, AppTheme.shapes.lg)
            .heightIn(min = Sizes.bar)
            .padding(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        IconTile(link.icon)
        Column(Modifier.weight(1f)) {
            AppText(link.title, style = AppTheme.type.titleSm, maxLines = 1)
            AppText(link.text, style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 2)
        }
        SiteIcon(AppIcons.Forward, tint = c.inkSubtle)
    }
}

// ───────────────────────── 4. Planes ─────────────────────────

@Composable
internal fun PricingSection(
    data: SiteData,
    modifier: Modifier = Modifier,
    onChoose: (Plan) -> Unit,
    onRetry: () -> Unit,
) {
    val size = LocalWindowSize.current
    val plans = data.plans
    val error = data.plansError
    SiteContainer(modifier) {
        SectionHeading(
            eyebrow = "Planes",
            index = "04",
            title = "Un plan para cada etapa de tu restaurante",
            subtitle = "Primer mes de prueba en todos los planes. Después, una mensualidad fija más una comisión " +
                "por cada pedido cobrado en línea.",
        )
        Spacer(Modifier.height(Space.x10))
        when {
            plans == null && error != null -> ErrorState(message = error, onRetry = onRetry)
            plans == null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                repeat(if (size == WindowSize.EXPANDED) 3 else 1) {
                    Skeleton(Modifier.weight(1f).height(460.dp), shape = AppTheme.shapes.lg)
                }
            }
            plans.isEmpty() -> EmptyState(
                title = "Aún no hay planes publicados",
                message = "Vuelve a intentarlo en unos minutos.",
                icon = AppIcons.Tag,
            )
            else -> {
                val columns = if (size == WindowSize.EXPANDED) plans.size.coerceIn(1, 3) else 1
                SiteGrid(
                    count = plans.size,
                    columns = columns,
                    modifier = if (columns == 1) Modifier.widthIn(max = 560.dp) else Modifier,
                ) { i, cellModifier ->
                    val plan = plans[i]
                    Reveal(cellModifier, delayMs = (i % columns) * 40) {
                        PlanCard(plan = plan, modifier = Modifier.fillMaxSize(), onChoose = { onChoose(plan) })
                    }
                }
                Spacer(Modifier.height(Space.x4))
                AppText(
                    text = "Precios en pesos mexicanos (MXN). La suscripción se cobra cada mes con Mercado Pago.",
                    modifier = Modifier.fillMaxWidth(),
                    style = AppTheme.type.bodySm,
                    color = AppTheme.colors.inkSubtle,
                )
            }
        }
    }
}

/**
 * Plan: nombre, precio en mono grande y comisión en mono. El destacado se marca con tinta ("Más popular") y un
 * filete más fuerte; nunca con brasa: la única acción en brasa de la página es "Empieza gratis".
 */
@Composable
private fun PlanCard(plan: Plan, modifier: Modifier = Modifier, onChoose: () -> Unit) {
    val c = AppTheme.colors
    val type = AppTheme.type
    SitePanel(modifier, padding = Space.x6, strong = plan.highlighted) {
        Row(Modifier.fillMaxWidth().heightIn(min = Space.x6), verticalAlignment = Alignment.CenterVertically) {
            LabelText(plan.name, modifier = Modifier.weight(1f), color = c.ink)
            if (plan.highlighted) TagBadge("Más popular", tone = Tone.PRIMARY)
        }
        Spacer(Modifier.height(Space.x5))
        Row {
            AppText(formatMoney(plan.priceCents), modifier = Modifier.alignByBaseline(), style = bigMono(), maxLines = 1)
            Spacer(Modifier.width(Space.x2))
            AppText("/ mes", modifier = Modifier.alignByBaseline(), style = type.bodySm, color = c.inkMuted, maxLines = 1)
        }
        Spacer(Modifier.height(Space.x2))
        Row {
            AppText("+ ${formatBps(plan.feeBps)}", modifier = Modifier.alignByBaseline(), style = type.mono, maxLines = 1)
            Spacer(Modifier.width(Space.x2))
            AppText("por pedido cobrado en línea", modifier = Modifier.alignByBaseline(), style = type.bodySm, color = c.inkMuted, maxLines = 1)
        }
        if (plan.description.isNotBlank()) {
            Spacer(Modifier.height(Space.x4))
            AppText(plan.description, style = type.bodySm, color = c.inkMuted)
        }
        Spacer(Modifier.height(Space.x5))
        AppDivider()
        Spacer(Modifier.height(Space.x5))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            plan.features.forEach { feature ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SiteIcon(AppIcons.Check, modifier = Modifier.padding(top = 2.dp), tint = c.inkSubtle, size = 16.dp)
                    AppText(feature, modifier = Modifier.weight(1f), style = type.title.copy(fontWeight = type.body.fontWeight))
                }
            }
        }
        Spacer(Modifier.height(Space.x8))
        Spacer(Modifier.weight(1f))
        AppButton(
            text = "Empezar con ${plan.name}",
            onClick = onChoose,
            variant = ButtonVariant.SECONDARY,
            fullWidth = true,
            trailingIcon = AppIcons.Forward,
        )
    }
}

// ───────────────────────── 5. Preguntas, cierre y pie ─────────────────────────

@Composable
internal fun FaqSection(modifier: Modifier = Modifier) {
    val faqs = remember {
        listOf(
            "¿Mis clientes verán otra marca además de la mía?" to
                "No. La plataforma es 100% marca blanca: el menú, el link y el QR llevan sólo el nombre, " +
                "el color y la fotografía de tu restaurante.",
            "¿Cómo recibo el dinero de mis ventas?" to
                "Conectas tu propia cuenta de Mercado Pago y cada pago se acredita directo en ella. " +
                "Tus clientes pueden pagar con tarjeta, en OXXO, por SPEI o con su saldo.",
            "¿Cuánto cuesta y qué comisión se cobra?" to
                "Pagas una mensualidad según tu plan, más una comisión por cada pedido cobrado en línea. " +
                "El primer mes es de prueba; después la suscripción se cobra cada mes con Mercado Pago.",
            "¿Necesito instalar algo o comprar equipo?" to
                "No. Funciona en el navegador y en teléfono, tablet y computadora. " +
                "La pantalla de cocina puede ser cualquier tablet o monitor que ya tengas.",
            "¿Puedo recibir pedidos de reparto y de otras apps?" to
                "Sí. Los pedidos directos, los de reparto y los de marketplaces se concentran en un solo tablero, " +
                "para que tu cocina vea todo en el mismo lugar.",
        )
    }
    var open by remember { mutableIntStateOf(0) }
    SiteContainer(modifier) {
        SectionHeading(eyebrow = "Preguntas", index = "05", title = "Preguntas frecuentes")
        Spacer(Modifier.height(Space.x8))
        Reveal(Modifier.fillMaxWidth()) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                faqs.forEachIndexed { i, (question, answer) ->
                    FaqItem(order(i), question, answer, expanded = open == i) { open = if (open == i) -1 else i }
                }
                AppDivider()
            }
        }
    }
}

@Composable
private fun FaqItem(number: String, question: String, answer: String, expanded: Boolean, onToggle: () -> Unit) {
    val c = AppTheme.colors
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(Motion.BASE, easing = Motion.standard))
    Column(Modifier.fillMaxWidth().edgeTop(c.line)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(scale = 1f) { onToggle() }
                .heightIn(min = Sizes.bar)
                .padding(vertical = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelText(number, modifier = Modifier.width(Space.x10))
            AppText(question, modifier = Modifier.weight(1f), style = AppTheme.type.title, color = if (expanded) c.ink else c.inkMuted)
            Spacer(Modifier.width(Space.x4))
            SiteIcon(AppIcons.ChevronDown, modifier = Modifier.graphicsLayer { rotationZ = rotation }, tint = c.inkSubtle)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(Motion.BASE, easing = Motion.standard)) + fadeIn(tween(Motion.BASE)),
            exit = shrinkVertically(tween(Motion.BASE, easing = Motion.standard)) + fadeOut(tween(Motion.FAST)),
        ) {
            AppText(
                text = answer,
                modifier = Modifier.padding(start = Space.x10, end = Space.x10, bottom = Space.x5),
                color = c.inkMuted,
            )
        }
    }
}

/** Cierre: un mosaico plano con el titular y, otra vez, la única acción en brasa. */
@Composable
internal fun ClosingCta(onSignup: () -> Unit, onSampleMenu: () -> Unit) {
    val c = AppTheme.colors
    val size = LocalWindowSize.current
    val compact = size == WindowSize.COMPACT
    SiteContainer {
        Reveal(Modifier.fillMaxWidth()) {
            SitePanel(Modifier.fillMaxWidth(), padding = if (compact) Space.x5 else Space.x10, strong = true) {
                Eyebrow("Empieza hoy")
                Spacer(Modifier.height(Space.x3))
                AppText(
                    text = "Tu restaurante, tu marca, tus pedidos.",
                    modifier = Modifier.widthIn(max = 640.dp),
                    style = if (compact) AppTheme.type.h1 else heroStyle(),
                )
                Spacer(Modifier.height(Space.x3))
                AppText(
                    text = "Crea tu menú digital hoy y recibe tu primer pedido pagado esta misma semana.",
                    modifier = Modifier.widthIn(max = 520.dp),
                    style = leadStyle(),
                    color = c.inkMuted,
                )
                Spacer(Modifier.height(Space.x8))
                if (compact) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                        AppButton(
                            "Empieza gratis", onClick = onSignup, modifier = Modifier.siteAccent("cierre"),
                            size = ButtonSize.LG, fullWidth = true, trailingIcon = AppIcons.Forward,
                        )
                        AppButton("Ver un menú de ejemplo", onClick = onSampleMenu, variant = ButtonVariant.SECONDARY, size = ButtonSize.LG, fullWidth = true)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        AppButton(
                            "Empieza gratis", onClick = onSignup, modifier = Modifier.siteAccent("cierre"),
                            size = ButtonSize.LG, trailingIcon = AppIcons.Forward,
                        )
                        AppButton("Ver un menú de ejemplo", onClick = onSampleMenu, variant = ButtonVariant.SECONDARY, size = ButtonSize.LG)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SiteFooter(onAnchor: (String) -> Unit, onLogin: () -> Unit) {
    val c = AppTheme.colors
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val brand = SiteConfig.brand
    Column(Modifier.fillMaxWidth().edgeTop(c.line)) {
        SiteContainer(Modifier.padding(top = Space.x8, bottom = Space.x10)) {
            if (compact) {
                BrandSlot()
                Spacer(Modifier.height(Space.x3))
                AppText("Menú digital, pedidos y pagos con tu propia marca.", style = AppTheme.type.bodySm, color = c.inkMuted)
                Spacer(Modifier.height(Space.x4))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    NavLink("Producto", { onAnchor("producto") }, Modifier.padding(end = Space.x1))
                    NavLink("Planes", { onAnchor("planes") })
                    NavLink("Preguntas", { onAnchor("preguntas") })
                    NavLink("Iniciar sesión", onLogin)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BrandSlot()
                        Spacer(Modifier.height(Space.x3))
                        AppText(
                            "Menú digital, pedidos y pagos con tu propia marca.",
                            style = AppTheme.type.bodySm,
                            color = c.inkMuted,
                        )
                    }
                    NavLink("Producto", { onAnchor("producto") })
                    NavLink("Planes", { onAnchor("planes") })
                    NavLink("Preguntas", { onAnchor("preguntas") })
                    NavLink("Iniciar sesión", onLogin)
                }
            }
            Spacer(Modifier.height(Space.x6))
            Row(
                Modifier.fillMaxWidth().edgeTop(c.line).padding(top = Space.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabelText("Soporte", modifier = Modifier.weight(1f))
                Box(
                    Modifier.pressable { openExternalUrl("mailto:${brand.supportEmail}") }.heightIn(min = Space.tap),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(brand.supportEmail, style = AppTheme.type.mono, color = c.inkMuted, maxLines = 1)
                }
            }
        }
    }
}
