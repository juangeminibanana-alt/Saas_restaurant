package app.feature.site

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.platform.openExternalUrl
import app.shared.model.Plan
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.ui.components.AppButton
import app.ui.components.AppDivider
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BrandMark
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.DishImage
import app.ui.components.EmptyState
import app.ui.components.ErrorState
import app.ui.components.Skeleton
import app.ui.components.TagBadge
import app.ui.components.Tone
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize

// ───────────────────────── 3. Beneficios ─────────────────────────

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
                "Logo, colores, tipografía y dominio propios. Tus clientes solo ven tu restaurante, nunca el nombre de un tercero.",
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
                AppIcons.Bike, "Reparto y marketplaces en una sola pantalla",
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
            title = "Todo lo que tu restaurante necesita para vender en línea",
            subtitle = "Menú, pagos, cocina y reparto en un solo lugar, siempre con la identidad de tu restaurante.",
        )
        Spacer(Modifier.height(Space.x14))
        SiteGrid(count = benefits.size, columns = columns) { i, cellModifier ->
            val benefit = benefits[i]
            Reveal(cellModifier, delayMs = (i % columns) * 80) {
                val c = AppTheme.colors
                SitePanel(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.size(44.dp).background(c.primarySoft, AppTheme.shapes.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        SiteIcon(benefit.icon, tint = c.primary, size = 22.dp)
                    }
                    Spacer(Modifier.height(Space.x5))
                    AppText(benefit.title, style = AppTheme.type.h3)
                    Spacer(Modifier.height(Space.x2))
                    AppText(benefit.text, color = c.inkMuted)
                }
            }
        }
    }
}

// ───────────────────────── 4. Cómo funciona ─────────────────────────

@Composable
internal fun HowItWorksSection(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val size = LocalWindowSize.current
    val columns = if (size == WindowSize.EXPANDED) 3 else 1
    val steps = remember {
        listOf(
            Feature(
                AppIcons.Store, "Crea tu restaurante",
                "Regístrate en minutos con el nombre de tu negocio y empieza con tu primer mes de prueba.",
            ),
            Feature(
                AppIcons.Image, "Sube tu menú y tu marca",
                "Agrega platillos, fotos y precios. Elige tu color, tu tipografía y tu logo.",
            ),
            Feature(
                AppIcons.Qr, "Comparte tu link o QR y empieza a vender",
                "Recibe pedidos pagados con Mercado Pago y míralos entrar a cocina en tiempo real.",
            ),
        )
    }
    Box(modifier.fillMaxWidth().background(c.surfaceSunken).padding(vertical = sectionGap() * 0.8f)) {
        SiteContainer {
            SectionHeading(eyebrow = "Cómo funciona", title = "De cero a tu primer pedido en tres pasos")
            Spacer(Modifier.height(Space.x14))
            SiteGrid(count = steps.size, columns = columns) { i, cellModifier ->
                val step = steps[i]
                Reveal(cellModifier, delayMs = i * 100) {
                    SitePanel(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(c.ink, CircleShape), contentAlignment = Alignment.Center) {
                                AppText("${i + 1}", style = AppTheme.type.title, color = c.canvas)
                            }
                            Spacer(Modifier.width(Space.x3))
                            AppText("Paso ${i + 1}", style = AppTheme.type.label, color = c.inkSubtle)
                            Spacer(Modifier.weight(1f))
                            SiteIcon(step.icon, tint = c.inkSubtle, size = 24.dp)
                        }
                        Spacer(Modifier.height(Space.x5))
                        AppText(step.title, style = AppTheme.type.h3)
                        Spacer(Modifier.height(Space.x2))
                        AppText(step.text, color = c.inkMuted)
                    }
                }
            }
        }
    }
}

// ───────────────────────── 5. Demos en vivo ─────────────────────────

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
                title = "Tres restaurantes, tres marcas, una misma plataforma",
                subtitle = "Abre cada restaurante de demostración como lo vería un comensal y entra a las pantallas de operación.",
            )
            Spacer(Modifier.height(Space.x14))
        }
        when {
            tenants == null && error != null -> SiteContainer { ErrorState(message = error, onRetry = onRetry) }
            tenants == null -> SiteContainer {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                    repeat(if (expanded) 3 else 1) {
                        Skeleton(Modifier.weight(1f).height(280.dp), shape = AppTheme.shapes.lg)
                    }
                }
            }
            expanded -> SiteContainer {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                    tenants.forEachIndexed { i, tenant ->
                        Reveal(Modifier.weight(1f), delayMs = i * 90) {
                            TenantCard(tenant, Modifier.fillMaxWidth()) { onNavigate("/r/${tenant.slug}") }
                        }
                    }
                }
            }
            else -> Reveal(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.x4),
                ) {
                    tenants.forEach { tenant ->
                        TenantCard(tenant, Modifier.width(276.dp)) { onNavigate("/r/${tenant.slug}") }
                    }
                }
            }
        }
        SiteContainer {
            Spacer(Modifier.height(Space.x8))
            val columns = when (size) {
                WindowSize.COMPACT -> 1
                WindowSize.MEDIUM -> 2
                WindowSize.EXPANDED -> 4
            }
            SiteGrid(count = links.size, columns = columns, gap = Space.x4) { i, cellModifier ->
                val link = links[i]
                Reveal(cellModifier, delayMs = (i % columns) * 60) {
                    DemoLinkTile(link, Modifier.fillMaxSize()) { onNavigate(link.route) }
                }
            }
        }
    }
}

/** Tarjeta de un restaurante de demostración, dibujada con SU tema de marca. */
@Composable
private fun TenantCard(tenant: TenantPublic, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    AppTheme(brand = tenant.theme) {
        val c = AppTheme.colors
        val shape = AppTheme.shapes.lg
        Column(
            modifier = modifier
                .pressable { onOpen() }
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.line, shape),
        ) {
            Box(Modifier.fillMaxWidth().height(172.dp)) {
                DishImage(
                    url = tenant.theme.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = "Portada de ${tenant.name}",
                    shape = RectangleShape,
                )
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(c.primary))
            }
            Column(Modifier.fillMaxWidth().padding(Space.x4)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    BrandMark(name = tenant.name, logoUrl = tenant.theme.logoUrl, size = 44.dp)
                    Column(Modifier.weight(1f)) {
                        AppText(tenant.name, style = AppTheme.type.h3, maxLines = 1)
                        AppText(tenant.cuisine, style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(Space.x4))
                Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(c.primary, CircleShape))
                    Spacer(Modifier.width(Space.x2))
                    AppText(
                        text = (tenant.theme.primary ?: "Neutro").uppercase(),
                        style = AppTheme.type.label,
                        color = c.inkMuted,
                    )
                    Spacer(Modifier.weight(1f))
                    AppText("Ver menú", style = AppTheme.type.bodySm.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
                    Spacer(Modifier.width(Space.x1))
                    SiteIcon(AppIcons.Forward, tint = c.ink, size = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun DemoLinkTile(link: DemoLink, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val c = AppTheme.colors
    val shape = AppTheme.shapes.md
    Row(
        modifier = modifier
            .pressable { onOpen() }
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .heightIn(min = 72.dp)
            .padding(Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Box(Modifier.size(40.dp).background(c.surfaceSunken, AppTheme.shapes.sm), contentAlignment = Alignment.Center) {
            SiteIcon(link.icon, tint = c.ink, size = 20.dp)
        }
        Column(Modifier.weight(1f)) {
            AppText(link.title, style = AppTheme.type.title)
            AppText(link.text, style = AppTheme.type.bodySm, color = c.inkMuted)
        }
        SiteIcon(AppIcons.ChevronRight, tint = c.inkSubtle, size = 18.dp)
    }
}

// ───────────────────────── 6. Planes ─────────────────────────

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
            title = "Un plan para cada etapa de tu restaurante",
            subtitle = "Primer mes de prueba en todos los planes. Después, una mensualidad fija más una comisión " +
                "por cada pedido cobrado en línea.",
        )
        Spacer(Modifier.height(Space.x14))
        when {
            plans == null && error != null -> ErrorState(message = error, onRetry = onRetry)
            plans == null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
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
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    SiteGrid(
                        count = plans.size,
                        columns = columns,
                        modifier = if (columns == 1) Modifier.widthIn(max = 560.dp) else Modifier,
                    ) { i, cellModifier ->
                        val plan = plans[i]
                        Reveal(cellModifier, delayMs = (i % columns) * 90) {
                            PlanCard(
                                plan = plan,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = if (columns > 1 && !plan.highlighted) Space.x4 else 0.dp),
                                onChoose = { onChoose(plan) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.x6))
                AppText(
                    text = "Precios en pesos mexicanos (MXN). La suscripción se cobra cada mes con Mercado Pago.",
                    modifier = Modifier.fillMaxWidth(),
                    style = AppTheme.type.bodySm,
                    color = AppTheme.colors.inkSubtle,
                    textAlign = if (size == WindowSize.COMPACT) TextAlign.Start else TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, modifier: Modifier = Modifier, onChoose: () -> Unit) {
    val c = AppTheme.colors
    val shape = AppTheme.shapes.lg
    val highlighted = plan.highlighted
    Column(
        modifier = modifier
            .then(if (highlighted) Modifier.shadow(20.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(if (highlighted) c.surfaceRaised else c.surface)
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) c.primary else c.line, shape)
            .padding(Space.x6),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            AppText(plan.name, modifier = Modifier.weight(1f), style = AppTheme.type.h3)
            if (highlighted) TagBadge("Más popular", tone = Tone.PRIMARY)
        }
        if (plan.description.isNotBlank()) {
            Spacer(Modifier.height(Space.x2))
            AppText(plan.description, style = AppTheme.type.bodySm, color = c.inkMuted)
        }
        Spacer(Modifier.height(Space.x6))
        Row(verticalAlignment = Alignment.Bottom) {
            AppText(formatMoney(plan.priceCents), style = AppTheme.type.display)
            Spacer(Modifier.width(Space.x1))
            AppText("/mes", modifier = Modifier.padding(bottom = Space.x2), color = c.inkMuted)
        }
        Spacer(Modifier.height(Space.x1))
        AppText(
            text = "+ ${formatBps(plan.feeBps)} de comisión por pedido cobrado en línea",
            style = AppTheme.type.bodySm,
            color = c.inkMuted,
        )
        Spacer(Modifier.height(Space.x5))
        AppDivider()
        Spacer(Modifier.height(Space.x5))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            plan.features.forEach { feature ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    SiteIcon(AppIcons.Check, modifier = Modifier.padding(top = 2.dp), tint = c.success, size = 18.dp)
                    AppText(feature, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(Space.x8))
        Spacer(Modifier.weight(1f))
        AppButton(
            text = "Empezar con ${plan.name}",
            onClick = onChoose,
            variant = if (highlighted) ButtonVariant.PRIMARY else ButtonVariant.SECONDARY,
            size = ButtonSize.LG,
            fullWidth = true,
        )
    }
}

// ───────────────────────── 7. Preguntas, cierre y pie ─────────────────────────

@Composable
internal fun FaqSection(modifier: Modifier = Modifier) {
    val faqs = remember {
        listOf(
            "¿Mis clientes verán otra marca además de la mía?" to
                "No. La plataforma es 100% marca blanca: el menú, el link y el QR llevan solo el nombre, " +
                "los colores y el logo de tu restaurante.",
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
    SiteContainer(modifier, maxWidth = 820.dp) {
        SectionHeading(eyebrow = "Preguntas", title = "Preguntas frecuentes")
        Spacer(Modifier.height(Space.x10))
        Reveal(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                AppDivider()
                faqs.forEachIndexed { i, (question, answer) ->
                    FaqItem(question, answer, expanded = open == i) { open = if (open == i) -1 else i }
                }
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String, expanded: Boolean, onToggle: () -> Unit) {
    val c = AppTheme.colors
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(Motion.BASE, easing = Motion.standard))
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(scale = 0.995f) { onToggle() }
                .heightIn(min = 68.dp)
                .padding(vertical = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(question, modifier = Modifier.weight(1f), style = AppTheme.type.title)
            Spacer(Modifier.width(Space.x4))
            SiteIcon(AppIcons.ChevronDown, modifier = Modifier.graphicsLayer { rotationZ = rotation }, tint = c.inkMuted)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(Motion.SLOW, easing = Motion.standard)) + fadeIn(tween(Motion.SLOW)),
            exit = shrinkVertically(tween(Motion.BASE, easing = Motion.standard)) + fadeOut(tween(Motion.FAST)),
        ) {
            AppText(
                text = answer,
                modifier = Modifier.padding(end = Space.x10, bottom = Space.x5),
                color = c.inkMuted,
            )
        }
        AppDivider()
    }
}

@Composable
internal fun ClosingCta(onSignup: () -> Unit, onSampleMenu: () -> Unit) {
    val c = AppTheme.colors
    val size = LocalWindowSize.current
    val compact = size == WindowSize.COMPACT
    SiteContainer {
        Reveal(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppTheme.shapes.lg)
                    .background(c.ink)
                    .padding(
                        horizontal = if (compact) Space.x6 else Space.x14,
                        vertical = if (compact) Space.x14 else Space.x18,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppText(
                    text = "Tu restaurante, tu marca, tus pedidos.",
                    modifier = Modifier.widthIn(max = 720.dp),
                    style = if (size == WindowSize.EXPANDED) AppTheme.type.display else AppTheme.type.h1,
                    color = c.canvas,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.x4))
                AppText(
                    text = "Crea tu menú digital hoy y recibe tu primer pedido pagado esta misma semana.",
                    modifier = Modifier.widthIn(max = 560.dp),
                    style = leadStyle(),
                    color = c.canvas.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.x8))
                if (compact) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                        InverseButton("Empieza gratis", filled = true, modifier = Modifier.fillMaxWidth(), onClick = onSignup)
                        InverseButton("Ver un menú de ejemplo", filled = false, modifier = Modifier.fillMaxWidth(), onClick = onSampleMenu)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        InverseButton("Empieza gratis", filled = true, onClick = onSignup)
                        InverseButton("Ver un menú de ejemplo", filled = false, onClick = onSampleMenu)
                    }
                }
            }
        }
    }
}

/** Botón sobre fondo de tinta (colores invertidos respecto al tema). */
@Composable
private fun InverseButton(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val shape = AppTheme.shapes.control
    Box(
        modifier = modifier
            .pressable { onClick() }
            .heightIn(min = 52.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(c.canvas) else Modifier.border(1.dp, c.canvas.copy(alpha = 0.4f), shape))
            .padding(horizontal = Space.x6),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, style = AppTheme.type.title, color = if (filled) c.ink else c.canvas, maxLines = 1)
    }
}

@Composable
internal fun SiteFooter(onAnchor: (String) -> Unit, onLogin: () -> Unit) {
    val c = AppTheme.colors
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val brand = SiteConfig.brand
    Column(Modifier.fillMaxWidth()) {
        AppDivider()
        SiteContainer(Modifier.padding(vertical = Space.x10)) {
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
            AppDivider()
            Spacer(Modifier.height(Space.x4))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .pressable { openExternalUrl("mailto:${brand.supportEmail}") }
                        .heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    SiteIcon(AppIcons.Mail, tint = c.inkSubtle, size = 16.dp)
                    AppText(brand.supportEmail, style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                ThemeToggle()
            }
        }
    }
}
