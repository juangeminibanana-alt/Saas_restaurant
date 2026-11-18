package app.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.shared.model.BrandTheme
import app.shared.model.Menu
import app.shared.model.MenuItem
import app.shared.util.formatMoney
import app.ui.components.AppButton
import app.ui.components.AppIconButton
import app.ui.components.AppIcons
import app.ui.components.AppText
import app.ui.components.BottomBar
import app.ui.components.BottomBarSummary
import app.ui.components.CategoryTabs
import app.ui.components.DishImage
import app.ui.components.LabelText
import app.ui.components.SectionHeader
import app.ui.components.Skeleton
import app.ui.components.StatusDot
import app.ui.components.edgeTop
import app.ui.components.flatSurface
import app.ui.components.photoScrim
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.LocalWindowSize
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize

/** Teléfono de referencia sobre el que se compone el menú del comensal (el de las maquetas de NOCHE). */
private val PreviewWidth = 390.dp
private val PreviewHeight = 844.dp
private val PreviewCover = 316.dp
private val PreviewTilePhoto = 240.dp
private val PreviewBezel = Space.x1

/**
 * Vista previa del menú del comensal dentro de un marco de teléfono, en NOCHE: el marco es un filete, sin
 * sombra ni brillo. El menú se compone a tamaño real (390 × 844) con los mismos componentes del sistema y se
 * reduce con [scale], así lo que se ve es exactamente lo que verá el comensal. No es interactiva: es una foto
 * viva de la marca ([theme] sólo aporta el acento y la portada).
 */
@Composable
fun MenuPreviewPhone(
    name: String,
    tagline: String,
    theme: BrandTheme,
    etaMinutes: Int,
    menu: Menu?,
    modifier: Modifier = Modifier,
    rating: Double = 4.8,
    isOpen: Boolean = true,
    scale: Float = 0.72f,
) {
    val colors = AppTheme.colors
    val shapes = AppTheme.shapes
    val width: Dp = PreviewWidth * scale
    val height: Dp = PreviewHeight * scale
    Box(
        modifier
            .size(width + PreviewBezel * 2, height + PreviewBezel * 2)
            .flatSurface(colors.surfaceRaised, colors.lineStrong, shapes.lg)
            .padding(PreviewBezel)
            .clearAndSetSemantics { },
    ) {
        Box(Modifier.fillMaxSize().clip(shapes.md).background(colors.canvas), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(PreviewWidth, PreviewHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                CompositionLocalProvider(
                    LocalWindowSize provides WindowSize.COMPACT,
                    LocalSurfaceLevel provides 0,
                ) {
                    AppTheme(brand = theme) {
                        MenuPreviewScreen(
                            name = name, tagline = tagline, coverUrl = theme.coverUrl,
                            etaMinutes = etaMinutes, rating = rating, isOpen = isOpen, menu = menu,
                        )
                    }
                }
                // Capa que se queda con los toques: la vista previa se mira, no se opera.
                Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
            }
        }
    }
}

/** La primera pantalla del menú del comensal tal como la define la maqueta de NOCHE. */
@Composable
private fun MenuPreviewScreen(
    name: String,
    tagline: String,
    coverUrl: String?,
    etaMinutes: Int,
    rating: Double,
    isOpen: Boolean,
    menu: Menu?,
) {
    val colors = AppTheme.colors
    val type = AppTheme.type
    val categories = remember(menu) { menu?.categories.orEmpty().sortedBy { it.sortOrder } }
    val dishes = remember(menu) {
        val all = menu?.items.orEmpty().filter { it.available }.sortedBy { it.sortOrder }
        (all.filter { "popular" in it.tags && it.imageUrl != null } + all.filter { it.imageUrl != null } + all).distinctBy { it.id }
    }
    val featured: MenuItem? = dishes.firstOrNull()
    val inOrder = dishes.take(2)
    val cover = coverUrl ?: featured?.imageUrl

    Column(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            // Portada con la identidad encima, sobre el velo.
            Box(Modifier.fillMaxWidth().height(PreviewCover)) {
                Box(Modifier.fillMaxSize().photoScrim(colors.canvas)) {
                    DishImage(cover, Modifier.fillMaxSize(), shape = RectangleShape, ring = false)
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Space.gutter)) {
                    if (tagline.isNotBlank()) LabelText(tagline, color = colors.inkMuted)
                    Spacer(Modifier.height(Space.x1))
                    AppText(name, style = type.display, maxLines = 2)
                    Spacer(Modifier.height(Space.x3))
                    Row(
                        Modifier.fillMaxWidth().edgeTop(colors.line).padding(top = Space.x3),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(if (isOpen) colors.positive else colors.inkSubtle)
                            Spacer(Modifier.width(Space.x2))
                            AppText(
                                if (isOpen) "Abierto" else "Cerrado",
                                style = type.bodySm.copy(fontWeight = FontWeight.Medium), maxLines = 1,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText("$etaMinutes-${etaMinutes + 10}", style = type.mono, maxLines = 1)
                            AppText(" min", style = type.bodySm, color = colors.inkMuted, maxLines = 1)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(rating.toString(), style = type.mono, maxLines = 1)
                            AppText(" / 5", style = type.bodySm, color = colors.inkMuted, maxLines = 1)
                        }
                    }
                }
            }
            // Buscar + categorías.
            Row(
                Modifier.fillMaxWidth().padding(start = Space.gutter, top = Space.x4, bottom = Space.x3),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                AppIconButton(AppIcons.Search, onClick = {})
                if (categories.isEmpty()) {
                    Skeleton(Modifier.weight(1f).height(Sizes.control), shape = AppTheme.shapes.md)
                } else {
                    CategoryTabs(
                        tabs = listOf("Destacados") + categories.map { it.name },
                        selectedIndex = 0, onSelect = {}, modifier = Modifier.weight(1f),
                    )
                }
            }
            // Mosaico destacado.
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter)) {
                SectionHeader("Esta semana", modifier = Modifier.padding(vertical = Space.x3), label = "Selección de la casa")
                if (featured == null) {
                    Skeleton(Modifier.fillMaxWidth().height(PreviewTilePhoto), shape = AppTheme.shapes.lg)
                } else {
                    Column(Modifier.fillMaxWidth().flatSurface(colors.surface, colors.lineStrong, AppTheme.shapes.lg)) {
                        DishImage(featured.imageUrl, Modifier.fillMaxWidth().height(PreviewTilePhoto), shape = RectangleShape, ring = false)
                        Column(Modifier.fillMaxWidth().edgeTop(colors.line).padding(Space.x3)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                                AppText(featured.name, Modifier.weight(1f).alignByBaseline(), style = type.title, maxLines = 1)
                                AppText(formatMoney(featured.priceCents), Modifier.alignByBaseline(), style = type.monoMd, maxLines = 1)
                            }
                            if (featured.description.isNotBlank()) {
                                AppText(featured.description, style = type.bodySm, color = colors.inkMuted, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        // Barra del pedido: el único lugar donde vive el acento.
        val total = inOrder.sumOf { it.priceCents }
        BottomBar {
            BottomBarSummary(
                label = if (inOrder.size == 1) "1 platillo" else "${inOrder.size.coerceAtLeast(2)} platillos",
                value = formatMoney(if (total > 0L) total else 41_300L),
            )
            AppButton("Ver pedido", onClick = {}, modifier = Modifier.weight(1f), trailingIcon = AppIcons.Forward)
        }
    }
}
