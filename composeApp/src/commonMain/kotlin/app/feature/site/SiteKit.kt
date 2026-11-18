package app.feature.site

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.components.AppDivider
import app.ui.components.AppText
import app.ui.components.GlyphIcon
import app.ui.components.LabelText
import app.ui.components.flatSurface
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize

// ───────────────────────── Scroll / aparición de secciones ─────────────────────────

/**
 * Estado de scroll de la página + alto visible. También sabe dónde están las llamadas a la acción en brasa
 * del contenido (portada y cierre): mientras alguna está a la vista, la del encabezado se apaga, de modo que
 * en pantalla siempre hay UNA sola acción en brasa.
 */
internal class SiteScroll(val state: ScrollState, val viewportPx: Float, val headerPx: Float) {
    private val accents = mutableStateMapOf<String, ClosedFloatingPointRange<Float>>()

    fun placeAccent(id: String, top: Float, bottom: Float) {
        val current = accents[id]
        if (current == null || current.start != top || current.endInclusive != bottom) accents[id] = top..bottom
    }

    /** true si alguna acción en brasa del contenido se ve ahora mismo (bajo el encabezado fijo). */
    val accentInView: Boolean
        get() {
            val top = state.value + headerPx
            val bottom = state.value + viewportPx
            return accents.values.any { it.endInclusive > top && it.start < bottom }
        }
}

internal val LocalSiteScroll = compositionLocalOf<SiteScroll?> { null }

/** Registra una acción en brasa del contenido para que el encabezado sepa cuándo ceder la suya. */
@Composable
internal fun Modifier.siteAccent(id: String): Modifier {
    val env = LocalSiteScroll.current ?: return this
    return onGloballyPositioned { coords ->
        val top = coords.positionInRoot().y + env.state.value
        env.placeAccent(id, top, top + coords.size.height)
    }
}

/** Aparece con un fundido corto y 12 px de recorrido la primera vez que entra en la ventana visible. Sin rebote. */
@Composable
internal fun Reveal(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable BoxScope.() -> Unit,
) {
    val env = LocalSiteScroll.current
    var contentY by remember { mutableFloatStateOf(Float.NaN) }
    var shown by remember { mutableStateOf(env == null) }
    val reached by remember(env) {
        derivedStateOf {
            env == null || (!contentY.isNaN() && contentY < env.state.value + env.viewportPx * 0.96f)
        }
    }
    LaunchedEffect(reached) { if (reached) shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.SLOW, delayMillis = delayMs, easing = Motion.standard),
    )
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (!shown && env != null) contentY = coords.positionInRoot().y + env.state.value
            }
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * Space.x3.toPx()
            },
        content = content,
    )
}

/** Registra la posición vertical (en el contenido) de una sección para los enlaces ancla del encabezado. */
internal fun Modifier.siteAnchor(id: String, anchors: MutableMap<String, Float>, scroll: ScrollState): Modifier =
    onGloballyPositioned { coords -> anchors[id] = coords.positionInRoot().y + scroll.value }

// ───────────────────────── Métricas responsivas ─────────────────────────

/** Ancho máximo del contenido del sitio. */
internal val SiteMaxWidth = 1180.dp

@Composable
internal fun siteGutter(): Dp = when (LocalWindowSize.current) {
    WindowSize.COMPACT -> Space.gutter
    WindowSize.MEDIUM -> Space.x8
    WindowSize.EXPANDED -> Space.x10
}

@Composable
internal fun sectionGap(): Dp = when (LocalWindowSize.current) {
    WindowSize.COMPACT -> Space.x14
    WindowSize.MEDIUM -> Space.x18
    WindowSize.EXPANDED -> Space.x18 + Space.x6
}

/**
 * Titular de portada: Geist seminegrita, el único texto del producto que sube de 40 (56 en escritorio, 48 en
 * tablet), con el mismo tracking cerrado del nombre del restaurante.
 */
@Composable
internal fun heroStyle(): TextStyle {
    val display = AppTheme.type.display
    return when (LocalWindowSize.current) {
        WindowSize.COMPACT -> display
        WindowSize.MEDIUM -> display.copy(fontSize = 48.sp, lineHeight = 52.sp)
        WindowSize.EXPANDED -> display.copy(fontSize = 56.sp, lineHeight = 60.sp)
    }
}

/** Párrafo de apoyo: texto corrido 16/24 en gris. */
@Composable
internal fun leadStyle(): TextStyle = AppTheme.type.body

/** Cifra grande en mono (precio de un plan): Geist Mono al tamaño del titular 40/44. */
@Composable
internal fun bigMono(): TextStyle {
    val type = AppTheme.type
    return type.display.copy(fontFamily = type.monoLg.fontFamily, fontWeight = type.monoLg.fontWeight, letterSpacing = type.monoLg.letterSpacing)
}

// ───────────────────────── Piezas base ─────────────────────────

@Composable
internal fun SiteContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = SiteMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = siteGutter()
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = maxWidth + gutter * 2).fillMaxWidth().padding(horizontal = gutter),
            content = content,
        )
    }
}

@Composable
internal fun SiteIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.ink, size: Dp = Sizes.icon) {
    GlyphIcon(icon, tint = tint, modifier = modifier, size = size)
}

/** Antetítulo: etiqueta en mono MAYÚSCULAS. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    LabelText(text, modifier = modifier, color = AppTheme.colors.inkMuted)
}

/**
 * Cabecera de sección del sitio: antetítulo en mono a la izquierda y su número de orden a la derecha sobre un
 * filete; debajo el título (40/44; 32/36 en teléfono) y, si hay, un párrafo de apoyo. Siempre a la izquierda.
 */
@Composable
internal fun SectionHeading(eyebrow: String, title: String, modifier: Modifier = Modifier, subtitle: String? = null, index: String? = null) {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val colors = AppTheme.colors
    Reveal(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.x3), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(eyebrow, Modifier.weight(1f))
                if (index != null) LabelText(index)
            }
            AppDivider()
            Spacer(Modifier.height(if (compact) Space.x5 else Space.x8))
            AppText(
                text = title,
                modifier = Modifier.widthIn(max = 760.dp),
                style = if (compact) AppTheme.type.h1 else AppTheme.type.display,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.x3))
                AppText(text = subtitle, modifier = Modifier.widthIn(max = 600.dp), style = leadStyle(), color = colors.inkMuted)
            }
        }
    }
}

/** Mosaico base del sitio: superficie 1 con filete, radio 16. [strong] refuerza el filete (plan destacado). */
@Composable
internal fun SitePanel(
    modifier: Modifier = Modifier,
    padding: Dp = Space.x5,
    strong: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    CompositionLocalProvider(LocalSurfaceLevel provides 1) {
        Column(
            modifier = modifier.flatSurface(c.surface, if (strong) c.lineStrong else c.line, AppTheme.shapes.lg).padding(padding),
            content = content,
        )
    }
}

/** Cuadro de ícono de 44: superficie 2 con filete, ícono en tinta. */
@Composable
internal fun IconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(
        modifier.widthIn(min = Sizes.control).height(Sizes.control).flatSurface(c.surfaceRaised, c.line, AppTheme.shapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        SiteIcon(icon, Modifier.padding(horizontal = Space.x3), tint = c.ink)
    }
}

/** Cuadrícula simple de `columns` columnas con celdas de la misma altura por fila. Separación de mosaico: 8. */
@Composable
internal fun SiteGrid(
    count: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    gap: Dp = Space.x2,
    cell: @Composable (index: Int, cellModifier: Modifier) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (columns <= 1) {
            for (i in 0 until count) cell(i, Modifier.fillMaxWidth())
        } else {
            (0 until count).chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    rowItems.forEach { i -> cell(i, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** "1.5%" a partir de puntos base. */
internal fun formatBps(bps: Int): String {
    val whole = bps / 100
    val frac = bps % 100
    return if (frac == 0) "$whole%" else "$whole." + frac.toString().padStart(2, '0').trimEnd('0') + "%"
}
