package app.feature.site

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shell.AccentSwatch
import app.shell.MenuPreviewPhone
import app.ui.components.AppButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.LabelText
import app.ui.components.SegmentedControl
import app.ui.components.Skeleton
import app.ui.components.edgeTop
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlinx.coroutines.delay

// ───────────────────────── Portada ─────────────────────────

/** Escala del teléfono de la portada (el menú se compone a 390 × 844 y se reduce). */
private const val ShowcaseScale = 0.7f
private val ShowcaseWidth: Dp = 390.dp * ShowcaseScale + Space.x2
private val ShowcaseHeight: Dp = 844.dp * ShowcaseScale + Space.x2

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
        WindowSize.EXPANDED -> Space.x14
    }
    SiteContainer(Modifier.padding(top = topPad)) {
        if (size == WindowSize.EXPANDED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x18),
            ) {
                HeroCopy(Modifier.weight(1f), onSignup = onSignup, onSampleMenu = onSampleMenu)
                Reveal(delayMs = 120) { PhoneShowcase(data, onOpenTenant) }
            }
        } else {
            HeroCopy(Modifier.fillMaxWidth(), onSignup = onSignup, onSampleMenu = onSampleMenu)
            Spacer(Modifier.height(Space.x14))
            Reveal(Modifier.fillMaxWidth(), delayMs = 120) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PhoneShowcase(data, onOpenTenant) }
            }
        }
    }
}

@Composable
private fun HeroCopy(modifier: Modifier, onSignup: () -> Unit, onSampleMenu: () -> Unit) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val brand = SiteConfig.brand
    val c = AppTheme.colors
    Column(modifier) {
        Reveal { Eyebrow("Menú digital · marca blanca") }
        Spacer(Modifier.height(Space.x3))
        Reveal(delayMs = 40) {
            // A 56 en escritorio el titular cabe en dos renglones sin dejar una palabra sola.
            AppText(brand.headline, modifier = Modifier.widthIn(max = if (LocalWindowSize.current == WindowSize.EXPANDED) 780.dp else 640.dp), style = heroStyle())
        }
        Spacer(Modifier.height(Space.x5))
        Reveal(delayMs = 80) {
            AppText(text = brand.subheadline, modifier = Modifier.widthIn(max = 520.dp), style = leadStyle(), color = c.inkMuted)
        }
        Spacer(Modifier.height(Space.x8))
        if (compact) {
            Reveal(Modifier.fillMaxWidth(), delayMs = 120) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                    AppButton(
                        "Empieza gratis", onClick = onSignup, modifier = Modifier.siteAccent("hero"),
                        size = ButtonSize.LG, fullWidth = true, trailingIcon = AppIcons.Forward,
                    )
                    AppButton(
                        "Ver un menú de ejemplo", onClick = onSampleMenu, variant = ButtonVariant.SECONDARY,
                        size = ButtonSize.LG, fullWidth = true,
                    )
                }
            }
        } else {
            Reveal(delayMs = 120) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2), verticalAlignment = Alignment.CenterVertically) {
                    AppButton(
                        "Empieza gratis", onClick = onSignup, modifier = Modifier.siteAccent("hero"),
                        size = ButtonSize.LG, trailingIcon = AppIcons.Forward,
                    )
                    AppButton("Ver un menú de ejemplo", onClick = onSampleMenu, variant = ButtonVariant.SECONDARY, size = ButtonSize.LG)
                }
            }
        }
        Spacer(Modifier.height(Space.x10))
        Reveal(Modifier.fillMaxWidth(), delayMs = 160) { HeroFacts(compact) }
    }
}

/** Tres hechos bajo un filete, como la ficha de datos del menú: número de orden en mono, texto en sans. */
@Composable
private fun HeroFacts(compact: Boolean) {
    val c = AppTheme.colors
    val facts = listOf("Primer mes de prueba", "Sin instalar nada", "Pagos directo a tu cuenta")
    if (compact) {
        Column(Modifier.fillMaxWidth()) {
            facts.forEachIndexed { i, fact ->
                Row(
                    Modifier.fillMaxWidth().edgeTop(c.line).padding(vertical = Space.x3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    LabelText("0${i + 1}")
                    AppText(fact, style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1)
                }
            }
        }
    } else {
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().edgeTop(c.line).padding(top = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            facts.forEachIndexed { i, fact ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                    LabelText("0${i + 1}")
                    AppText(fact, style = AppTheme.type.bodySm, color = c.inkMuted, maxLines = 1)
                }
            }
        }
    }
}

// ───────────────────────── Teléfono: una plataforma, tres marcas ─────────────────────────

/**
 * El menú real de cada restaurante de demostración dentro de un marco de teléfono plano. Lo único que cambia
 * de uno a otro es la fotografía y el acento; el segmentado de abajo (selección en tinta) elige cuál se ve.
 */
@Composable
internal fun PhoneShowcase(data: SiteData, onOpenTenant: (slug: String) -> Unit, modifier: Modifier = Modifier) {
    val tenants = data.tenants.orEmpty()
    var index by remember { mutableIntStateOf(0) }
    var manualPicks by remember { mutableIntStateOf(0) }
    LaunchedEffect(tenants.size, manualPicks) {
        if (tenants.size > 1) {
            while (true) {
                delay(if (manualPicks > 0) 9_000 else 4_500)
                index = (index + 1) % tenants.size
            }
        }
    }
    val current = if (tenants.isEmpty()) null else tenants[index % tenants.size]

    Column(modifier.width(ShowcaseWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        if (current == null) {
            Skeleton(Modifier.size(ShowcaseWidth, ShowcaseHeight), shape = AppTheme.shapes.lg)
        } else {
            Crossfade(targetState = current, animationSpec = tween(Motion.SLOW)) { tenant ->
                MenuPreviewPhone(
                    name = tenant.name,
                    tagline = tenant.tagline.ifBlank { tenant.cuisine },
                    theme = tenant.theme,
                    etaMinutes = tenant.etaMinutes,
                    menu = data.menus[tenant.slug],
                    modifier = Modifier.pressable(scale = 0.99f) { onOpenTenant(tenant.slug) },
                    rating = tenant.rating,
                    isOpen = tenant.isOpen,
                    scale = ShowcaseScale,
                )
            }
        }
        Spacer(Modifier.height(Space.x3))
        if (tenants.size > 1) {
            SegmentedControl(
                options = tenants.map { it.name.substringBefore(' ') },
                selectedIndex = index % tenants.size,
                onSelect = {
                    index = it
                    manualPicks++
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.x3))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LabelText("Mismo sistema · su acento", modifier = Modifier.weight(1f))
            if (current != null) {
                AppTheme(brand = current.theme) {
                    AccentSwatch(AppTheme.colors.primary, AppTheme.colors.onPrimary, current.theme.primary)
                }
            }
        }
    }
}
