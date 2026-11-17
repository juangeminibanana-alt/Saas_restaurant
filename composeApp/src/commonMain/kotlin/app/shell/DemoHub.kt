package app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.shared.demo.DemoData
import app.shared.model.TenantPublic
import app.ui.components.AppCard
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BrandMark
import app.ui.components.DishImage
import app.ui.components.SectionHeader
import app.ui.components.StatusPill
import app.ui.components.Tone
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Space
import app.ui.theme.ThemeController
import app.ui.theme.WindowSize

private data class HubEntry(val title: String, val description: String, val route: String, val icon: ImageVector, val account: String?)

/** Índice de la demostración: las cinco apps, los restaurantes ficticios y las cuentas de prueba. */
@Composable
fun DemoHub(onOpen: (String) -> Unit) {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val size = LocalWindowSize.current
    val compact = size == WindowSize.COMPACT
    val columns = when (size) { WindowSize.COMPACT -> 1; WindowSize.MEDIUM -> 2; WindowSize.EXPANDED -> 3 }
    val tenants by produceState(emptyList<TenantPublic>()) {
        value = try { repo.listPublicTenants() } catch (e: Throwable) { emptyList() }
    }
    val entries = listOf(
        HubEntry("Pantalla de cocina", "Pedidos de todos los canales en tiempo real, con tiempos y un toque para marcar listo.", Routes.KITCHEN, AppIcons.Chef, "cocina@brasa.demo"),
        HubEntry("Panel del restaurante", "Marca, menú, enlaces y QR, pagos con Mercado Pago, pedidos e integraciones.", Routes.PANEL, AppIcons.Store, "dueno@brasa.demo"),
        HubEntry("Consola del operador", "Restaurantes, planes, suscripciones y métricas globales del SaaS.", Routes.OPERATOR, AppIcons.Dashboard, DemoData.OPERATOR_EMAIL),
        HubEntry("Sitio de venta", "Planes, registro y pago de la suscripción, con marca neutra configurable.", Routes.SITE, AppIcons.Globe, null),
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) Space.x4 else Space.x10, vertical = if (compact) Space.x6 else Space.x14),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 1080.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AppText("DEMOSTRACIÓN", style = AppTheme.type.label, color = colors.inkSubtle)
                    Spacer(Modifier.height(Space.x2))
                    AppText("Cinco apps, una sola plataforma", style = if (compact) AppTheme.type.h1 else AppTheme.type.display)
                }
                AppIconButton(
                    icon = if (colors.isDark) AppIcons.Sun else AppIcons.Moon,
                    onClick = { ThemeController.toggle(colors.isDark) },
                    contentDescription = "Cambiar entre claro y oscuro",
                )
            }
            Spacer(Modifier.height(Space.x3))
            AppText(
                "Marca blanca de punta a punta: cada restaurante ve únicamente su propia marca. Elige qué quieres revisar.",
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(Space.x4))
            StatusPill(
                if (repo.isRemote) "Conectado al backend · pedidos en tiempo real" else "Datos de demostración locales · sin backend",
                tone = if (repo.isRemote) Tone.SUCCESS else Tone.INFO,
            )

            Spacer(Modifier.height(Space.x10))
            SectionHeader("Menú digital del comensal", subtitle = "Lo que ve el cliente final por link o QR. Mismo código, tres marcas.")
            Spacer(Modifier.height(Space.x4))
            HubGrid(tenants, columns) { tenant ->
                AppTheme(brand = tenant.theme) {
                    AppCard(onClick = { onOpen(Routes.diner(tenant.slug)) }, padding = 0.dp, elevated = true) {
                        DishImage(
                            tenant.theme.coverUrl,
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                            contentDescription = tenant.name,
                            shape = AppTheme.shapes.md,
                        )
                        Row(Modifier.padding(Space.x4), verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(tenant.name, tenant.theme.logoUrl, size = 44.dp)
                            Spacer(Modifier.width(Space.x3))
                            Column(Modifier.weight(1f)) {
                                AppText(tenant.name, style = AppTheme.type.h3, maxLines = 1)
                                AppText(tenant.cuisine.ifBlank { tenant.tagline }, style = AppTheme.type.bodySm, color = AppTheme.colors.inkMuted, maxLines = 1)
                            }
                            HubArrow()
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.x10))
            SectionHeader("Operación y administración", subtitle = "Contraseña de todas las cuentas de prueba: ${DemoData.DEMO_PASSWORD}")
            Spacer(Modifier.height(Space.x4))
            HubGrid(entries, if (columns == 3) 2 else columns) { entry ->
                AppCard(onClick = { onOpen(entry.route) }, padding = Space.x5) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(AppTheme.shapes.sm).background(colors.primarySoft),
                            contentAlignment = Alignment.Center,
                        ) { HubIcon(entry.icon) }
                        Spacer(Modifier.width(Space.x4))
                        Column(Modifier.weight(1f)) {
                            AppText(entry.title, style = AppTheme.type.title)
                            Spacer(Modifier.height(Space.x1))
                            AppText(entry.description, style = AppTheme.type.bodySm, color = colors.inkMuted)
                            if (entry.account != null) {
                                Spacer(Modifier.height(Space.x2))
                                AppText(entry.account, style = AppTheme.type.label, color = colors.inkSubtle)
                            }
                        }
                        Spacer(Modifier.width(Space.x2))
                        HubArrow()
                    }
                }
            }
            Spacer(Modifier.height(Space.x14))
        }
    }
}

@Composable
private fun HubIcon(icon: ImageVector) {
    AppIconButton(icon = icon, onClick = {}, tint = AppTheme.colors.ink)
}

@Composable
private fun HubArrow() {
    AppIconButton(icon = AppIcons.ChevronRight, onClick = {}, tint = AppTheme.colors.inkSubtle)
}

@Composable
private fun <T> HubGrid(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
        items.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Botón flotante discreto para volver al índice de la demo desde cualquier app. */
@Composable
fun DemoSwitcher(navigator: AppNavigator) {
    if (navigator.route == Routes.DEMO) return
    val colors = AppTheme.colors
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    Box(
        Modifier.fillMaxSize().padding(Space.x2),
        contentAlignment = if (compact) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.9f))
                .border(1.dp, colors.line, CircleShape)
                .pressable(scale = 0.9f) { navigator.navigate(Routes.DEMO) },
            contentAlignment = Alignment.Center,
        ) {
            AppText("⌂", style = AppTheme.type.title, color = colors.inkMuted)
        }
    }
}
