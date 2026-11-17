package app.feature.site

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.data.AppRepository
import app.shared.data.RepositoryException
import app.shared.model.Menu
import app.shared.model.Plan
import app.shared.model.TenantPublic
import app.shell.LocalRepo
import app.ui.components.AppBottomSheet
import app.ui.components.AppButton
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BrandMark
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.ThemeController
import app.ui.theme.WindowSize
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

// ───────────────────────── Marca del sitio (neutra y configurable) ─────────────────────────

/**
 * Marca con la que se presenta el sitio de venta. La plataforma es marca blanca: no hay nombre
 * ni logo propios; quien opera el SaaS configura aquí los suyos.
 */
data class SiteBrand(
    val name: String = "Tu marca",
    val logoUrl: String? = null,
    val headline: String,
    val subheadline: String,
    val supportEmail: String,
)

object SiteConfig {
    var brand: SiteBrand by mutableStateOf(
        SiteBrand(
            headline = "El menú digital de tu restaurante, con tu marca.",
            subheadline = "Comparte tu menú con un link o un QR, cobra con Mercado Pago directo a tu cuenta " +
                "y recibe cada pedido en cocina en tiempo real.",
            supportEmail = "soporte@tudominio.com",
        ),
    )
}

// ───────────────────────── Datos de la página ─────────────────────────

internal class SiteData {
    var tenants by mutableStateOf<List<TenantPublic>?>(null)
    var menus by mutableStateOf<Map<String, Menu>>(emptyMap())
    var plans by mutableStateOf<List<Plan>?>(null)
    var tenantsError by mutableStateOf<String?>(null)
    var plansError by mutableStateOf<String?>(null)

    suspend fun load(repo: AppRepository) {
        tenantsError = null
        plansError = null
        try {
            plans = repo.listPlans()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            plansError = (e as? RepositoryException)?.message ?: "No pudimos cargar los planes. Intenta de nuevo."
        }
        try {
            val list = repo.listPublicTenants().take(3)
            tenants = list
            for (tenant in list) {
                try {
                    menus = menus + (tenant.slug to repo.getMenu(tenant.slug))
                } catch (e: CancellationException) {
                    throw e
                } catch (ignored: Throwable) {
                    menus = menus + (tenant.slug to Menu())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            tenantsError = (e as? RepositoryException)?.message ?: "No pudimos cargar los restaurantes de ejemplo."
        }
    }
}

// ───────────────────────── Raíz ─────────────────────────

private val HeaderHeight = 68.dp

@Composable
fun SiteApp(onNavigate: (route: String) -> Unit) {
    AppTheme {
        SitePage(onNavigate)
    }
}

@Composable
private fun SitePage(onNavigate: (route: String) -> Unit) {
    val repo = LocalRepo.current
    val c = AppTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val data = remember { SiteData() }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(repo, reload) { data.load(repo) }

    val anchors = remember { mutableMapOf<String, Float>() }
    val headerPx = with(density) { HeaderHeight.toPx() }
    val goTo: (String) -> Unit = { id ->
        val y = anchors[id]
        if (y != null) {
            scope.launch {
                scroll.animateScrollTo((y - headerPx).toInt().coerceAtLeast(0), tween(700, easing = Motion.standard))
            }
        }
    }

    var signupOpen by remember { mutableStateOf(false) }
    var signupPlanId by remember { mutableStateOf<String?>(null) }
    var signupRun by remember { mutableIntStateOf(0) }
    val openSignup: (String?) -> Unit = { planId ->
        signupPlanId = planId
        signupRun++
        signupOpen = true
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.canvas)) {
        val viewportPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else 2400f
        val viewportHeight: Dp = if (constraints.hasBoundedHeight) maxHeight else 720.dp
        val env = remember(scroll, viewportPx) { SiteScroll(scroll, viewportPx) }
        val gap = sectionGap()

        CompositionLocalProvider(LocalSiteScroll provides env) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Spacer(Modifier.height(HeaderHeight))
                HeroSection(
                    data = data,
                    onSignup = { openSignup(null) },
                    onSampleMenu = { onNavigate("/r/brasa-demo") },
                    onOpenTenant = { slug -> onNavigate("/r/$slug") },
                )
                Spacer(Modifier.height(gap))
                BenefitsSection(Modifier.siteAnchor("producto", anchors, scroll))
                Spacer(Modifier.height(gap))
                HowItWorksSection()
                Spacer(Modifier.height(gap))
                DemosSection(data = data, onNavigate = onNavigate, onRetry = { reload++ })
                Spacer(Modifier.height(gap))
                PricingSection(
                    data = data,
                    modifier = Modifier.siteAnchor("planes", anchors, scroll),
                    onChoose = { plan -> openSignup(plan.id) },
                    onRetry = { reload++ },
                )
                Spacer(Modifier.height(gap))
                FaqSection(Modifier.siteAnchor("preguntas", anchors, scroll))
                Spacer(Modifier.height(gap))
                ClosingCta(onSignup = { openSignup(null) }, onSampleMenu = { onNavigate("/r/brasa-demo") })
                Spacer(Modifier.height(gap))
                SiteFooter(onAnchor = goTo, onLogin = { onNavigate("/panel") })
            }
        }

        SiteHeader(
            scrolled = scroll.value > 8,
            onAnchor = goTo,
            onLogin = { onNavigate("/panel") },
            onSignup = { openSignup(null) },
        )

        AppBottomSheet(visible = signupOpen, onDismiss = { signupOpen = false }) {
            key(signupRun) {
                SignupFlow(
                    plans = data.plans,
                    plansError = data.plansError,
                    initialPlanId = signupPlanId,
                    maxHeight = viewportHeight * 0.86f,
                    onRetryPlans = { reload++ },
                    onClose = { signupOpen = false },
                    onNavigate = { route ->
                        signupOpen = false
                        onNavigate(route)
                    },
                )
            }
        }
    }
}

// ───────────────────────── Encabezado fijo ─────────────────────────

/** Espacio de marca: logo configurado o monograma neutro + nombre. */
@Composable
internal fun BrandSlot(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val brand = SiteConfig.brand
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
        BrandMark(name = brand.name, logoUrl = brand.logoUrl, size = size)
        AppText(brand.name, style = AppTheme.type.title, maxLines = 1)
    }
}

@Composable
internal fun ThemeToggle(modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    AppIconButton(
        icon = if (c.isDark) AppIcons.Sun else AppIcons.Moon,
        onClick = { ThemeController.toggle(c.isDark) },
        modifier = modifier,
        contentDescription = if (c.isDark) "Cambiar a modo claro" else "Cambiar a modo oscuro",
        tint = c.inkMuted,
    )
}

@Composable
internal fun NavLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.pressable { onClick() }.heightIn(min = 44.dp).padding(horizontal = Space.x3),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium), color = AppTheme.colors.inkMuted)
    }
}

@Composable
private fun SiteHeader(scrolled: Boolean, onAnchor: (String) -> Unit, onLogin: () -> Unit, onSignup: () -> Unit) {
    val c = AppTheme.colors
    val size = LocalWindowSize.current
    val gutter = siteGutter()
    val background by animateColorAsState(
        targetValue = c.canvas.copy(alpha = if (scrolled) 0.96f else 0f),
        animationSpec = tween(Motion.BASE),
    )
    val lineAlpha by animateFloatAsState(if (scrolled) 1f else 0f, tween(Motion.BASE))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .background(background)
            .pointerInput(Unit) {},
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 1180.dp + gutter * 2)
                .fillMaxSize()
                .padding(horizontal = gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandSlot()
            Spacer(Modifier.weight(1f))
            if (size == WindowSize.EXPANDED) {
                NavLink("Producto", { onAnchor("producto") })
                NavLink("Planes", { onAnchor("planes") })
                NavLink("Preguntas", { onAnchor("preguntas") })
                Spacer(Modifier.width(Space.x3))
            }
            if (size == WindowSize.COMPACT) {
                AppIconButton(icon = AppIcons.Person, onClick = onLogin, contentDescription = "Iniciar sesión")
                Spacer(Modifier.width(Space.x1))
                AppButton("Empieza gratis", onClick = onSignup, size = ButtonSize.SM)
            } else {
                ThemeToggle()
                Spacer(Modifier.width(Space.x1))
                AppButton("Iniciar sesión", onClick = onLogin, variant = ButtonVariant.GHOST)
                Spacer(Modifier.width(Space.x2))
                AppButton("Empieza gratis", onClick = onSignup)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .graphicsLayer { alpha = lineAlpha }
                .background(c.line),
        )
    }
}
