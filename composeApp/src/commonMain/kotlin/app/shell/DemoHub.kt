package app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.shared.demo.DemoData
import app.shared.model.TenantPublic
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.DishImage
import app.ui.components.GlyphIcon
import app.ui.components.LabelText
import app.ui.components.SectionHeader
import app.ui.components.Skeleton
import app.ui.components.StatusDot
import app.ui.components.StatusPill
import app.ui.components.Tone
import app.ui.components.edgeTop
import app.ui.components.flatSurface
import app.ui.components.pressable
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize

private data class HubEntry(val title: String, val description: String, val route: String, val icon: ImageVector, val account: String?)

/** Ancho máximo del índice. */
private val HubMaxWidth = 1080.dp

/**
 * Índice de la demostración en NOCHE: los tres restaurantes ficticios (cada uno con su fotografía y su acento)
 * y las cuatro apps de operación como una lista de filas con filete. No hay acción principal: nada lleva brasa.
 */
@Composable
fun DemoHub(onOpen: (String) -> Unit) {
    val repo = LocalRepo.current
    val colors = AppTheme.colors
    val size = LocalWindowSize.current
    val compact = size == WindowSize.COMPACT
    val columns = when (size) { WindowSize.COMPACT -> 1; WindowSize.MEDIUM -> 2; WindowSize.EXPANDED -> 3 }
    val tenants by produceState<List<TenantPublic>?>(null) {
        value = try {
            val all = repo.listPublicTenants()
            // Vitrina: los restaurantes de demostración con portada (el resto se ve desde la consola del operador).
            all.filter { it.theme.coverUrl != null }.ifEmpty { all.take(3) }
        } catch (e: Throwable) { emptyList() }
    }
    val entries = listOf(
        HubEntry("Pantalla de cocina", "Pedidos de todos los canales en tiempo real, con tiempos y un toque para marcar listo.", Routes.KITCHEN, AppIcons.Chef, "cocina@brasa.demo"),
        HubEntry("Panel del restaurante", "Marca, menú, enlaces y QR, pagos con Mercado Pago, pedidos e integraciones.", Routes.PANEL, AppIcons.Store, "dueno@brasa.demo"),
        HubEntry("Consola del operador", "Restaurantes, planes, suscripciones y métricas globales del servicio.", Routes.OPERATOR, AppIcons.Dashboard, DemoData.OPERATOR_EMAIL),
        HubEntry("Sitio de venta", "Planes, registro y pago de la suscripción, con marca neutra configurable.", Routes.SITE, AppIcons.Globe, null),
    )

    Column(
        Modifier.fillMaxSize().background(colors.canvas).verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) Space.gutter else Space.x10, vertical = if (compact) Space.x8 else Space.x14),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = HubMaxWidth).fillMaxWidth()) {
            LabelText("Demostración", color = colors.inkMuted)
            Spacer(Modifier.height(Space.x1))
            AppText("Cinco apps, una sola plataforma", style = if (compact) AppTheme.type.h1 else AppTheme.type.display)
            Spacer(Modifier.height(Space.x3))
            AppText(
                "Marca blanca de punta a punta: cada restaurante ve únicamente su propia marca. Elige qué quieres revisar.",
                modifier = Modifier.widthIn(max = 640.dp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(Space.x4))
            StatusPill(
                if (repo.isRemote) "Conectado al servidor · en tiempo real" else "Datos de demostración · sin servidor",
                tone = if (repo.isRemote) Tone.SUCCESS else Tone.INFO,
            )

            Spacer(Modifier.height(Space.x10))
            SectionHeader("Menú del comensal", modifier = Modifier.padding(vertical = Space.x3), label = "Mismo código · tres marcas")
            val list = tenants
            if (list == null) {
                HubGrid(listOf(0, 1, 2), columns) { Skeleton(Modifier.fillMaxWidth().aspectRatio(1.2f), shape = AppTheme.shapes.lg) }
            } else {
                HubGrid(list, columns) { tenant -> TenantTile(tenant) { onOpen(Routes.diner(tenant.slug)) } }
            }

            Spacer(Modifier.height(Space.x8))
            SectionHeader(
                "Operación y administración",
                modifier = Modifier.padding(vertical = Space.x3),
                label = "Contraseña ${DemoData.DEMO_PASSWORD}",
            )
            Column(Modifier.fillMaxWidth().flatSurface(colors.surface, colors.line, AppTheme.shapes.lg)) {
                entries.forEachIndexed { index, entry ->
                    EntryRow(entry, first = index == 0) { onOpen(entry.route) }
                }
            }
            Spacer(Modifier.height(Space.x14))
        }
    }
}

/** Mosaico de un restaurante: su fotografía arriba y, en el pie, nombre, cocina y su acento como muestra sólida. */
@Composable
private fun TenantTile(tenant: TenantPublic, onClick: () -> Unit) {
    AppTheme(brand = tenant.theme) {
        val colors = AppTheme.colors
        Column(
            Modifier
                .fillMaxWidth()
                .pressable(scale = 0.99f, onClick = onClick)
                .flatSurface(colors.surface, colors.lineStrong, AppTheme.shapes.lg),
        ) {
            DishImage(
                tenant.theme.coverUrl,
                Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                contentDescription = tenant.name,
                shape = RectangleShape,
                ring = false,
            )
            Row(
                Modifier.fillMaxWidth().edgeTop(colors.line).padding(Space.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    AppText(tenant.name, style = AppTheme.type.title, maxLines = 1)
                    AppText(tenant.cuisine.ifBlank { tenant.tagline }, style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
                }
                Spacer(Modifier.width(Space.x3))
                AccentSwatch(colors.primary, colors.onPrimary, tenant.theme.primary)
                Spacer(Modifier.width(Space.x3))
                GlyphIcon(AppIcons.Forward, tint = colors.inkSubtle, size = Sizes.icon)
            }
        }
    }
}

/** El acento del restaurante como muestra sólida con su código en mono: es lo único que cambia entre marcas. */
@Composable
internal fun AccentSwatch(accent: Color, onAccent: Color, hex: String?, modifier: Modifier = Modifier) {
    Box(
        modifier.height(24.dp).background(accent, AppTheme.shapes.xs).padding(horizontal = Space.x2),
        contentAlignment = Alignment.Center,
    ) {
        LabelText((hex ?: "Acento").removePrefix("#"), color = onAccent)
    }
}

@Composable
private fun EntryRow(entry: HubEntry, first: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.edgeTop(colors.line))
            .pressable(scale = 0.995f, onClick = onClick)
            .defaultMinSize(minHeight = Sizes.bar)
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Sizes.control).flatSurface(colors.surfaceRaised, colors.line, AppTheme.shapes.sm),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(entry.icon, tint = colors.ink, size = Sizes.icon) }
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            AppText(entry.title, style = AppTheme.type.title, maxLines = 1)
            AppText(entry.description, style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 2)
            if (entry.account != null) AppText(entry.account, style = AppTheme.type.mono, color = colors.inkSubtle, maxLines = 1)
        }
        Spacer(Modifier.width(Space.x3))
        GlyphIcon(AppIcons.Forward, tint = colors.inkSubtle, size = Sizes.icon)
    }
}

@Composable
private fun <T> HubGrid(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    val cols = columns.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        items.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                row.forEach { item -> Box(Modifier.weight(1f)) { cell(item) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ───────────────────────── Pestaña de borde ─────────────────────────

/** Medidas de la pestaña: cabe en el margen de página (16), así no tapa contenido ni acciones. */
private val EdgeTabWidth = Space.gutter
private val EdgeTabHeight = Sizes.controlLg
private val EdgeTabIcon = 12.dp

/**
 * Control auxiliar pegado al borde de la pantalla, a media altura: una pestaña plana (superficie + filete) tan
 * ancha como el margen de página, de modo que nunca cubre una acción principal. En teléfono va al borde
 * izquierdo (a la derecha viven los "+" y las acciones); en pantallas anchas, al derecho (a la izquierda vive
 * la barra lateral). El área de toque es algo más ancha que la cara visible.
 */
@Composable
internal fun ShellEdgeTab(icon: ImageVector, contentDescription: String, onClick: () -> Unit, dot: Color? = null) {
    val colors = AppTheme.colors
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val radius = Space.x2
    val shape = if (compact) RoundedCornerShape(topEnd = radius, bottomEnd = radius) else RoundedCornerShape(topStart = radius, bottomStart = radius)
    Box(Modifier.fillMaxSize(), contentAlignment = if (compact) Alignment.CenterStart else Alignment.CenterEnd) {
        Box(
            Modifier
                .width(EdgeTabWidth + Space.x3)
                .height(EdgeTabHeight)
                .pressable(scale = 1f, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = if (compact) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Column(
                Modifier
                    // El filete del lado pegado al borde queda fuera de la pantalla.
                    .offset(x = if (compact) (-1).dp else 1.dp)
                    .width(EdgeTabWidth + 1.dp)
                    .height(EdgeTabHeight)
                    .flatSurface(colors.surface, colors.lineStrong, shape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.x1, Alignment.CenterVertically),
            ) {
                GlyphIcon(icon, tint = colors.inkMuted, size = EdgeTabIcon)
                if (dot != null) StatusDot(dot)
            }
        }
    }
}

/** Regreso al índice de la demo desde cualquier app: una pestaña en el margen, no un botón flotante. */
@Composable
fun DemoSwitcher(navigator: AppNavigator) {
    if (navigator.route == Routes.DEMO) return
    ShellEdgeTab(AppIcons.Home, contentDescription = "Volver al índice de la demostración", onClick = { navigator.navigate(Routes.DEMO) })
}
