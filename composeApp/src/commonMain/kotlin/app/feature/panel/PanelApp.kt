package app.feature.panel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.shared.api.RealtimeEvent
import app.shared.model.Role
import app.shared.model.TenantPublic
import app.shared.model.UserSession
import app.shared.util.label
import app.shell.LocalRepo
import app.shell.LoginGate
import app.ui.components.AdminScaffold
import app.ui.components.AppButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BrandMark
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.NavItem
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlin.coroutines.cancellation.CancellationException

internal enum class PanelSection(val id: String, val label: String) {
    OVERVIEW("resumen", "Resumen"),
    ORDERS("pedidos", "Pedidos"),
    MENU("menu", "Menú"),
    BRAND("marca", "Marca"),
    LINKS("enlaces", "Enlaces y QR"),
    PAYMENTS("pagos", "Pagos"),
    INTEGRATIONS("integraciones", "Integraciones"),
    TEAM("equipo", "Equipo");

    val icon: ImageVector
        get() = when (this) {
            OVERVIEW -> AppIcons.Dashboard
            ORDERS -> AppIcons.Receipt
            MENU -> AppIcons.Menu
            BRAND -> AppIcons.Palette
            LINKS -> AppIcons.Qr
            PAYMENTS -> AppIcons.CreditCard
            INTEGRATIONS -> AppIcons.Plug
            TEAM -> AppIcons.Users
        }
}

internal fun sectionsFor(role: Role): List<PanelSection> = when (role) {
    Role.OWNER -> PanelSection.entries.toList()
    Role.MANAGER -> PanelSection.entries.filter { it != PanelSection.PAYMENTS }
    else -> listOf(PanelSection.ORDERS)
}

/** Raíz del panel del restaurante (dueño, gerente y caja). */
@Composable
fun PanelApp(onExit: () -> Unit, onOpenDiner: (slug: String) -> Unit) {
    AppTheme(brand = null) {
        LoginGate(
            allowedRoles = setOf(Role.OWNER, Role.MANAGER, Role.CASHIER),
            title = "Panel del restaurante",
            subtitle = "Administra tu marca, tu menú y tus pedidos",
            demoAccounts = listOf(
                "Dueño · Brasa Demo" to "dueno@brasa.demo",
                "Gerente · Brasa Demo" to "gerente@brasa.demo",
                "Caja · Brasa Demo" to "caja@brasa.demo",
                "Dueño · Nori Demo" to "dueno@nori.demo",
                "Dueño · Miga Demo" to "dueno@miga.demo",
            ),
            onExit = onExit,
        ) { session ->
            PanelShell(session = session, onOpenDiner = onOpenDiner)
        }
    }
}

@Composable
private fun PanelShell(session: UserSession, onOpenDiner: (String) -> Unit) {
    val repo = LocalRepo.current
    val sections = remember(session.role) { sectionsFor(session.role) }
    var selectedId by remember(session.userId) { mutableStateOf(sections.first().id) }
    var brand by remember(session.userId) { mutableStateOf<TenantPublic?>(null) }
    var activeOrders by remember(session.userId) { mutableStateOf<Int?>(null) }

    LaunchedEffect(session.userId) {
        brand = try {
            repo.getBrand()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }
    // Contador de pedidos activos para la insignia de navegación (se refresca con cada evento de cocina).
    LaunchedEffect(session.userId) {
        suspend fun refresh() {
            activeOrders = try {
                repo.listOrders(true).size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                activeOrders
            }
        }
        refresh()
        try {
            repo.kitchenEvents().collect { event ->
                when (event) {
                    is RealtimeEvent.OrderCreated, is RealtimeEvent.OrderUpdated -> refresh()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // El tiempo real es opcional: la lista se puede refrescar a mano.
        }
    }

    val slug = brand?.slug ?: session.tenantSlug.orEmpty()
    val restaurantName = brand?.name ?: session.tenantName ?: "Mi restaurante"
    val current = sections.firstOrNull { it.id == selectedId } ?: sections.first()
    val navItems = sections.map { s ->
        NavItem(id = s.id, label = s.label, icon = s.icon, badge = if (s == PanelSection.ORDERS) activeOrders?.takeIf { it > 0 } else null)
    }
    val compact = LocalWindowSize.current == WindowSize.COMPACT

    AdminScaffold(
        title = current.label,
        navItems = navItems,
        selectedId = current.id,
        onSelect = { selectedId = it },
        brand = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                BrandMark(name = restaurantName, logoUrl = brand?.theme?.logoUrl, size = 36.dp)
                Column {
                    AppText(restaurantName, style = AppTheme.type.title, maxLines = 1)
                    AppText("Panel del restaurante", style = AppTheme.type.label, color = AppTheme.colors.inkSubtle, maxLines = 1)
                }
            }
        },
        userName = session.name,
        userRole = session.role.label(),
        onLogout = { repo.logout() },
        actions = {
            if (slug.isNotBlank() && !compact) {
                AppButton("Ver mi menú", onClick = { onOpenDiner(slug) }, variant = ButtonVariant.SECONDARY, size = ButtonSize.SM, icon = AppIcons.Eye)
            }
        },
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn(tween(Motion.BASE)) togetherWith fadeOut(tween(Motion.FAST)) },
            label = "panel-section",
        ) { section ->
            when (section) {
                PanelSection.OVERVIEW -> OverviewSection(session = session, onOpenOrders = { selectedId = PanelSection.ORDERS.id })
                PanelSection.ORDERS -> OrdersSection()
                PanelSection.MENU -> MenuSection()
                PanelSection.BRAND -> BrandSection(onSaved = { brand = it }, onOpenDiner = onOpenDiner)
                PanelSection.LINKS -> LinksSection(brand = brand, fallbackSlug = slug, fallbackName = restaurantName, onOpenDiner = onOpenDiner)
                PanelSection.PAYMENTS -> PaymentsSection()
                PanelSection.INTEGRATIONS -> IntegrationsSection()
                PanelSection.TEAM -> TeamSection()
            }
        }
    }
}
