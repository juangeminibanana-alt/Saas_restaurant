package app.feature.site

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.shared.model.Menu
import app.shared.model.MenuItem
import app.shared.model.TenantPublic
import app.shared.util.formatMoney
import app.ui.components.AppButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BrandMark
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.DishImage
import app.ui.components.Skeleton
import app.ui.components.StatusPill
import app.ui.components.Tone
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize
import app.ui.theme.parseHexColor
import kotlinx.coroutines.delay

// ───────────────────────── Hero ─────────────────────────

@Composable
internal fun HeroSection(
    data: SiteData,
    onSignup: () -> Unit,
    onSampleMenu: () -> Unit,
    onOpenTenant: (slug: String) -> Unit,
) {
    val size = LocalWindowSize.current
    val topPad = when (size) {
        WindowSize.COMPACT -> Space.x8
        WindowSize.MEDIUM -> Space.x14
        WindowSize.EXPANDED -> Space.x18
    }
    SiteContainer(Modifier.padding(top = topPad)) {
        if (size == WindowSize.EXPANDED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x14),
            ) {
                HeroCopy(Modifier.weight(1f), centered = false, onSignup = onSignup, onSampleMenu = onSampleMenu)
                Reveal(delayMs = 220) { PhoneShowcase(data, onOpenTenant, Modifier.width(400.dp)) }
            }
        } else {
            HeroCopy(Modifier.fillMaxWidth(), centered = size == WindowSize.MEDIUM, onSignup = onSignup, onSampleMenu = onSampleMenu)
            Spacer(Modifier.height(Space.x14))
            Reveal(Modifier.fillMaxWidth(), delayMs = 220) {
                PhoneShowcase(data, onOpenTenant, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeroCopy(modifier: Modifier, centered: Boolean, onSignup: () -> Unit, onSampleMenu: () -> Unit) {
    val size = LocalWindowSize.current
    val brand = SiteConfig.brand
    val c = AppTheme.colors
    val align = if (centered) TextAlign.Center else TextAlign.Start
    val heroStyle = when (size) {
        WindowSize.COMPACT -> AppTheme.type.display.copy(fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp)
        WindowSize.MEDIUM -> AppTheme.type.display.copy(fontSize = 54.sp, lineHeight = 58.sp, letterSpacing = (-1.2).sp)
        WindowSize.EXPANDED -> AppTheme.type.display.copy(fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-1.6).sp)
    }
    Column(modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Reveal {
            Row(
                modifier = Modifier
                    .clip(AppTheme.shapes.pill)
                    .background(c.surface)
                    .border(1.dp, c.line, AppTheme.shapes.pill)
                    .padding(horizontal = Space.x3, vertical = Space.x2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                SiteIcon(AppIcons.Sparkle, tint = c.ink, size = 14.dp)
                AppText("100% marca blanca para restaurantes", style = AppTheme.type.label, color = c.inkMuted)
            }
        }
        Spacer(Modifier.height(Space.x5))
        Reveal(delayMs = 60) {
            AppText(brand.headline, modifier = Modifier.widthIn(max = 720.dp), style = heroStyle, textAlign = align)
        }
        Spacer(Modifier.height(Space.x5))
        Reveal(delayMs = 120) {
            AppText(
                text = brand.subheadline,
                modifier = Modifier.widthIn(max = 560.dp),
                style = leadStyle(),
                color = c.inkMuted,
                textAlign = align,
            )
        }
        Spacer(Modifier.height(Space.x8))
        if (size == WindowSize.COMPACT) {
            Reveal(Modifier.fillMaxWidth(), delayMs = 180) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    AppButton("Empieza gratis", onClick = onSignup, size = ButtonSize.LG, fullWidth = true)
                    AppButton(
                        "Ver un menú de ejemplo", onClick = onSampleMenu, variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.LG, icon = AppIcons.Eye, fullWidth = true,
                    )
                }
            }
        } else {
            Reveal(delayMs = 180) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3), verticalAlignment = Alignment.CenterVertically) {
                    AppButton("Empieza gratis", onClick = onSignup, size = ButtonSize.LG)
                    AppButton(
                        "Ver un menú de ejemplo", onClick = onSampleMenu, variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.LG, icon = AppIcons.Eye,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.x6))
        Reveal(delayMs = 240) {
            val points = listOf("Primer mes de prueba", "Sin instalar nada", "Pagos directo a tu cuenta")
            if (size == WindowSize.COMPACT) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) { points.forEach { HeroPoint(it) } }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) { points.forEach { HeroPoint(it) } }
            }
        }
    }
}

@Composable
private fun HeroPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
        SiteIcon(AppIcons.Check, tint = AppTheme.colors.success, size = 16.dp)
        AppText(text, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted)
    }
}

// ───────────────────────── Teléfono animado: una plataforma, tres marcas ─────────────────────────

@Composable
internal fun PhoneShowcase(data: SiteData, onOpenTenant: (slug: String) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val tenants = data.tenants.orEmpty()
    var index by remember { mutableIntStateOf(0) }
    var manualPicks by remember { mutableIntStateOf(0) }
    LaunchedEffect(tenants.size, manualPicks) {
        if (tenants.size > 1) {
            while (true) {
                delay(3500)
                index = (index + 1) % tenants.size
            }
        }
    }
    val current = if (tenants.isEmpty()) null else tenants[index % tenants.size]
    val accent by animateColorAsState(
        targetValue = parseHexColor(current?.theme?.primary) ?: c.ink,
        animationSpec = tween(700),
    )
    val floating = rememberInfiniteTransition()
    val drift by floating.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(400.dp)
                    .background(
                        Brush.radialGradient(listOf(accent.copy(alpha = if (c.isDark) 0.34f else 0.24f), accent.copy(alpha = 0f))),
                        CircleShape,
                    ),
            )
            PhoneFrame(Modifier.graphicsLayer { translationY = drift * 6.dp.toPx() }) {
                if (current == null) {
                    PhoneSkeleton()
                } else {
                    Crossfade(targetState = current, animationSpec = tween(700)) { tenant ->
                        AppTheme(brand = tenant.theme) {
                            MiniMenu(tenant = tenant, menu = data.menus[tenant.slug], onOpen = { onOpenTenant(tenant.slug) })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x4))
        if (tenants.size > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                tenants.forEachIndexed { i, _ ->
                    val active = i == index % tenants.size
                    val dotWidth by animateDpAsState(if (active) 24.dp else 8.dp, tween(Motion.SLOW, easing = Motion.standard))
                    Box(
                        modifier = Modifier.size(width = 36.dp, height = 44.dp).pressable {
                            index = i
                            manualPicks++
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(width = dotWidth, height = 8.dp).background(if (active) accent else c.lineStrong, CircleShape))
                    }
                }
            }
        }
        AppText(
            text = "Misma plataforma, tu propia marca",
            style = AppTheme.type.bodySm,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhoneFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = AppTheme.colors
    val bezel = if (c.isDark) c.lineStrong else c.ink
    val outer = RoundedCornerShape(44.dp)
    Box(
        modifier
            .size(width = 292.dp, height = 596.dp)
            .shadow(28.dp, outer)
            .background(bezel, outer)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)).background(c.surfaceSunken), content = content)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(width = 84.dp, height = 22.dp)
                .background(bezel, CircleShape),
        )
    }
}

@Composable
private fun PhoneSkeleton() {
    Column(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
        Skeleton(Modifier.fillMaxWidth().height(156.dp), shape = RectangleShape)
        Column(Modifier.padding(Space.x4), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            Skeleton(Modifier.width(150.dp).height(22.dp))
            Skeleton(Modifier.width(100.dp).height(14.dp))
            Spacer(Modifier.height(Space.x2))
            repeat(3) { Skeleton(Modifier.fillMaxWidth().height(72.dp), shape = AppTheme.shapes.md) }
        }
    }
}

/** Menú del comensal en miniatura, dibujado con el tema de marca del restaurante. */
@Composable
private fun MiniMenu(tenant: TenantPublic, menu: Menu?, onOpen: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val dishes = remember(menu) {
        val all = menu?.items.orEmpty().filter { it.available }.sortedBy { it.sortOrder }
        (all.filter { "popular" in it.tags } + all).distinctBy { it.id }.take(3)
    }
    val categories = remember(menu) { menu?.categories.orEmpty().sortedBy { it.sortOrder }.take(3) }
    val cartTotal = dishes.take(2).sumOf { it.priceCents }

    Box(Modifier.fillMaxSize().background(c.canvas).pressable(scale = 0.99f) { onOpen() }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(156.dp)) {
                DishImage(url = tenant.theme.coverUrl, modifier = Modifier.fillMaxSize(), shape = RectangleShape)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.5f to c.canvas.copy(alpha = 0f), 1f to c.canvas),
                    ),
                )
            }
            Column(Modifier.padding(horizontal = Space.x4).offset(y = -Space.x6)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    BrandMark(name = tenant.name, logoUrl = tenant.theme.logoUrl, size = 52.dp)
                    Column(Modifier.padding(bottom = 2.dp)) {
                        AppText(tenant.name, style = t.h3, maxLines = 1)
                        AppText(tenant.cuisine, style = t.bodySm, color = c.inkMuted, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(Space.x3))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x1)) {
                    SiteIcon(AppIcons.Star, tint = c.warning, size = 14.dp)
                    AppText(tenant.rating.toString(), style = t.label)
                    Spacer(Modifier.width(Space.x2))
                    SiteIcon(AppIcons.Clock, tint = c.inkMuted, size = 14.dp)
                    AppText("${tenant.etaMinutes} min", style = t.label, color = c.inkMuted)
                    Spacer(Modifier.weight(1f))
                    StatusPill(
                        text = if (tenant.isOpen) "Abierto" else "Cerrado",
                        tone = if (tenant.isOpen) Tone.SUCCESS else Tone.NEUTRAL,
                    )
                }
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(Space.x3))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                        categories.forEachIndexed { i, category ->
                            val selected = i == 0
                            Box(
                                Modifier
                                    .clip(AppTheme.shapes.pill)
                                    .background(if (selected) c.primary else c.surfaceSunken)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                AppText(
                                    category.name,
                                    style = t.label,
                                    color = if (selected) c.onPrimary else c.inkMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.x3))
                Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                    if (menu == null) {
                        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(72.dp), shape = AppTheme.shapes.md) }
                    } else {
                        dishes.forEach { MiniDish(it) }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to c.canvas.copy(alpha = 0f), 0.55f to c.canvas))
                .padding(start = Space.x3, end = Space.x3, top = Space.x8, bottom = Space.x3),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(AppTheme.shapes.control)
                    .background(c.primary)
                    .padding(horizontal = Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SiteIcon(AppIcons.Bag, tint = c.onPrimary, size = 16.dp)
                Spacer(Modifier.width(Space.x2))
                AppText("Ver pedido", style = t.bodySm.copy(fontWeight = FontWeight.SemiBold), color = c.onPrimary)
                Spacer(Modifier.weight(1f))
                AppText(formatMoney(cartTotal), style = t.bodySm.copy(fontWeight = FontWeight.SemiBold), color = c.onPrimary)
            }
        }
    }
}

@Composable
private fun MiniDish(item: MenuItem) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val shape = AppTheme.shapes.md
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .padding(Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        DishImage(url = item.imageUrl, modifier = Modifier.size(56.dp), shape = AppTheme.shapes.sm)
        Column(Modifier.weight(1f)) {
            AppText(item.name, style = t.bodySm.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
            AppText(item.description, style = t.label, color = c.inkMuted, maxLines = 1)
            AppText(formatMoney(item.priceCents), style = t.bodySm.copy(fontWeight = FontWeight.SemiBold))
        }
        Box(Modifier.size(28.dp).background(c.primary, CircleShape), contentAlignment = Alignment.Center) {
            SiteIcon(AppIcons.Add, tint = c.onPrimary, size = 16.dp)
        }
    }
}
