package app.feature.site

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ui.components.AppText
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Space
import app.ui.theme.WindowSize

// ───────────────────────── Scroll / aparición de secciones ─────────────────────────

/** Estado de scroll de la página + alto visible, para animar la entrada de cada bloque. */
internal class SiteScroll(val state: ScrollState, val viewportPx: Float)

internal val LocalSiteScroll = compositionLocalOf<SiteScroll?> { null }

/** Aparece con fade + deslizamiento la primera vez que entra en la ventana visible. */
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
            env == null || (!contentY.isNaN() && contentY < env.state.value + env.viewportPx * 0.94f)
        }
    }
    LaunchedEffect(reached) { if (reached) shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.HERO, delayMillis = delayMs, easing = Motion.standard),
    )
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (!shown && env != null) contentY = coords.positionInRoot().y + env.state.value
            }
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 32.dp.toPx()
            },
        content = content,
    )
}

/** Registra la posición vertical (en el contenido) de una sección para los enlaces ancla del encabezado. */
internal fun Modifier.siteAnchor(id: String, anchors: MutableMap<String, Float>, scroll: ScrollState): Modifier =
    onGloballyPositioned { coords -> anchors[id] = coords.positionInRoot().y + scroll.value }

// ───────────────────────── Métricas responsivas ─────────────────────────

@Composable
internal fun siteGutter(): Dp = when (LocalWindowSize.current) {
    WindowSize.COMPACT -> Space.x5
    WindowSize.MEDIUM -> Space.x8
    WindowSize.EXPANDED -> Space.x10
}

@Composable
internal fun sectionGap(): Dp = when (LocalWindowSize.current) {
    WindowSize.COMPACT -> Space.x18
    WindowSize.MEDIUM -> 96.dp
    WindowSize.EXPANDED -> 120.dp
}

/** Párrafo de apoyo, un punto más grande que el cuerpo. */
@Composable
internal fun leadStyle(): TextStyle =
    AppTheme.type.title.copy(fontWeight = FontWeight.Normal, lineHeight = 26.sp)

// ───────────────────────── Piezas base ─────────────────────────

@Composable
internal fun SiteContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 1180.dp,
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
internal fun SiteIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.ink, size: Dp = 20.dp) {
    Image(imageVector = icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    AppText(
        text = text.uppercase(),
        modifier = modifier,
        style = AppTheme.type.label.copy(letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold),
        color = AppTheme.colors.inkSubtle,
    )
}

@Composable
internal fun SectionHeading(eyebrow: String, title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val size = LocalWindowSize.current
    val centered = size != WindowSize.COMPACT
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Reveal(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(Space.x3))
            AppText(
                text = title,
                modifier = Modifier.widthIn(max = 780.dp),
                style = if (size == WindowSize.EXPANDED) AppTheme.type.display else AppTheme.type.h1,
                textAlign = align,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(Space.x4))
                AppText(
                    text = subtitle,
                    modifier = Modifier.widthIn(max = 640.dp),
                    style = leadStyle(),
                    color = AppTheme.colors.inkMuted,
                    textAlign = align,
                )
            }
        }
    }
}

/** Superficie con borde fino: la tarjeta base del sitio. */
@Composable
internal fun SitePanel(
    modifier: Modifier = Modifier,
    padding: Dp = Space.x6,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val shape = AppTheme.shapes.lg
    Column(
        modifier = modifier.clip(shape).background(c.surface).border(1.dp, c.line, shape).padding(padding),
        content = content,
    )
}

/** Cuadrícula simple de `columns` columnas con celdas de la misma altura por fila. */
@Composable
internal fun SiteGrid(
    count: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    gap: Dp = Space.x5,
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
