package app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.shared.model.MetricPoint
import app.ui.theme.AppTheme
import app.ui.theme.Motion
import app.ui.theme.Space
import kotlin.math.max
import kotlin.math.min

// ───────────────────────── Tarjeta de métrica ─────────────────────────

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaPositive: Boolean = true,
    icon: ImageVector? = null,
    series: List<Double>? = null,
) {
    val colors = AppTheme.colors
    AppCard(modifier = modifier, padding = Space.x5) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppText(label, modifier = Modifier.weight(1f), style = AppTheme.type.bodySm, color = colors.inkMuted, maxLines = 1)
            if (icon != null) {
                Spacer(Modifier.width(Space.x2))
                GlyphTile(icon, tint = colors.primary, background = colors.primarySoft, size = 32.dp)
            }
        }
        Spacer(Modifier.height(Space.x3))
        AppText(value, style = AppTheme.type.h2, maxLines = 1)
        if (delta != null || (series != null && series.size >= 2)) {
            Spacer(Modifier.height(Space.x3))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (delta != null) {
                    DeltaPill(delta, deltaPositive)
                    Spacer(Modifier.width(Space.x3))
                }
                if (series != null && series.size >= 2) {
                    Sparkline(
                        series,
                        modifier = Modifier.weight(1f).height(32.dp),
                        color = if (delta == null) colors.primary else if (deltaPositive) colors.success else colors.danger,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DeltaPill(text: String, positive: Boolean) {
    val colors = AppTheme.colors
    val tone = if (positive) colors.success else colors.danger
    val soft = if (positive) colors.successSoft else colors.dangerSoft
    Row(
        Modifier.background(soft, AppTheme.shapes.pill).padding(start = 6.dp, end = Space.x2, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Flecha dibujada: arriba-derecha si sube, abajo-derecha si baja.
        Canvas(Modifier.width(12.dp).height(12.dp)) {
            val w = size.width
            val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val path = Path()
            if (positive) {
                path.moveTo(w * 0.25f, w * 0.75f); path.lineTo(w * 0.75f, w * 0.25f)
                path.moveTo(w * 0.36f, w * 0.25f); path.lineTo(w * 0.75f, w * 0.25f); path.lineTo(w * 0.75f, w * 0.64f)
            } else {
                path.moveTo(w * 0.25f, w * 0.25f); path.lineTo(w * 0.75f, w * 0.75f)
                path.moveTo(w * 0.36f, w * 0.75f); path.lineTo(w * 0.75f, w * 0.75f); path.lineTo(w * 0.75f, w * 0.36f)
            }
            drawPath(path, tone, style = stroke)
        }
        Spacer(Modifier.width(3.dp))
        AppText(text, style = AppTheme.type.label, color = tone, maxLines = 1)
    }
}

// ───────────────────────── Geometría compartida ─────────────────────────

/** Curva suave que pasa por todos los puntos (Catmull-Rom → Bézier, con tensión contenida para no rebasar). */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    for (i in 0 until points.size - 1) {
        val p0 = points[max(i - 1, 0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[min(i + 2, points.size - 1)]
        val t = 0.18f
        val c1 = Offset(p1.x + (p2.x - p0.x) * t, (p1.y + (p2.y - p0.y) * t).coerceIn(minY, maxY))
        val c2 = Offset(p2.x - (p3.x - p1.x) * t, (p2.y - (p3.y - p1.y) * t).coerceIn(minY, maxY))
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}

private fun plotPoints(values: List<Double>, left: Float, top: Float, width: Float, height: Float, zeroBased: Boolean): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val maxV = values.max()
    val minV = if (zeroBased) min(0.0, values.min()) else values.min()
    val range = (maxV - minV).takeIf { it > 1e-9 } ?: 1.0
    val flat = maxV - minV <= 1e-9
    val stepX = if (values.size > 1) width / (values.size - 1) else 0f
    return values.mapIndexed { i, v ->
        val f = if (flat) 0.5f else ((v - minV) / range).toFloat()
        Offset(left + stepX * i, top + height * (1f - f))
    }
}

private fun DrawScope.drawSeries(points: List<Offset>, color: Color, progress: Float, baseline: Float, strokeWidth: Float, areaAlpha: Float, endDot: Boolean, haloColor: Color) {
    if (points.size < 2) return
    val line = smoothPath(points)
    val area = Path().apply {
        addPath(line)
        lineTo(points.last().x, baseline)
        lineTo(points.first().x, baseline)
        close()
    }
    val top = points.minOf { it.y }
    val revealRight = points.first().x + (points.last().x - points.first().x) * progress
    clipRect(left = 0f, top = 0f, right = revealRight + strokeWidth, bottom = size.height) {
        drawPath(
            area,
            Brush.verticalGradient(listOf(color.copy(alpha = areaAlpha), color.copy(alpha = 0f)), startY = top, endY = baseline),
            alpha = progress.coerceIn(0f, 1f),
        )
        drawPath(line, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    if (endDot && progress > 0.96f) {
        val k = ((progress - 0.96f) / 0.04f).coerceIn(0f, 1f)
        val end = points.last()
        drawCircle(color.copy(alpha = 0.18f * k), radius = strokeWidth * 3.6f * k, center = end)
        drawCircle(haloColor, radius = strokeWidth * 2.1f * k, center = end)
        drawCircle(color, radius = strokeWidth * 1.35f * k, center = end)
    }
}

@Composable
private fun rememberReveal(key: Any?): Animatable<Float, *> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (progress.value >= 1f) return@LaunchedEffect
        progress.animateTo(1f, tween(Motion.HERO + 180, easing = Motion.standard))
    }
    return progress
}

// ───────────────────────── Sparkline ─────────────────────────

@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary) {
    val progress = rememberReveal(Unit)
    val halo = AppTheme.colors.surface
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        if (values.size < 2) return@Canvas
        val stroke = 1.75.dp.toPx()
        val pad = stroke * 2.4f
        val pts = plotPoints(values, pad, pad, size.width - pad * 2, size.height - pad * 2, zeroBased = false)
        drawSeries(pts, color, progress.value, baseline = size.height, strokeWidth = stroke, areaAlpha = 0.16f, endDot = true, haloColor = halo)
    }
}

// ───────────────────────── Barras ─────────────────────────

@Composable
fun BarChart(points: List<MetricPoint>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary) {
    val colors = AppTheme.colors
    val progress = rememberReveal(Unit)
    val measurer = rememberTextMeasurer()
    val labelStyle: TextStyle = AppTheme.type.label.copy(color = colors.inkSubtle)
    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        if (points.isEmpty()) return@Canvas
        val labelBand = 26.dp.toPx()
        val topPad = 8.dp.toPx()
        val chartH = size.height - labelBand - topPad
        val maxV = max(points.maxOf { it.value }, 1e-9)

        // Retícula: filetes horizontales.
        val hair = 1.dp.toPx()
        for (i in 0..3) {
            val y = topPad + chartH * i / 3f
            drawLine(if (i == 3) colors.lineStrong else colors.line, Offset(0f, y), Offset(size.width, y), strokeWidth = hair)
        }

        val slot = size.width / points.size
        val barW = min(slot * 0.58f, 44.dp.toPx())
        val radius = min(barW / 2f, 8.dp.toPx())
        val labelEvery = max(1, kotlin.math.ceil(points.size / (size.width / 44.dp.toPx()).toDouble()).toInt())
        points.forEachIndexed { i, p ->
            // Cascada: cada barra arranca un poco después de la anterior.
            val delay = if (points.size > 1) 0.35f * i / (points.size - 1) else 0f
            val local = ((progress.value - delay) / (1f - 0.35f)).coerceIn(0f, 1f)
            val eased = 1f - (1f - local) * (1f - local) * (1f - local)
            val h = (chartH * (p.value / maxV).toFloat().coerceIn(0f, 1f) * eased)
            val cx = slot * i + slot / 2f
            val baseline = topPad + chartH
            if (h > 0.5f) {
                val rect = Rect(cx - barW / 2f, baseline - h, cx + barW / 2f, baseline)
                val r = CornerRadius(min(radius, h), min(radius, h))
                val path = Path().apply { addRoundRect(RoundRect(rect, topLeft = r, topRight = r, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero)) }
                drawPath(path, Brush.verticalGradient(listOf(color, color.copy(alpha = 0.72f)), startY = rect.top, endY = rect.bottom))
            }
            if (i % labelEvery == 0) {
                val layout = measurer.measure(p.label, labelStyle, maxLines = 1)
                val x = (cx - layout.size.width / 2f).coerceIn(0f, max(0f, size.width - layout.size.width))
                drawText(layout, topLeft = Offset(x, baseline + 8.dp.toPx()), alpha = eased.coerceIn(0.01f, 1f))
            }
        }
    }
}

// ───────────────────────── Línea con área ─────────────────────────

@Composable
fun LineChart(points: List<MetricPoint>, modifier: Modifier = Modifier, color: Color = AppTheme.colors.primary) {
    val colors = AppTheme.colors
    val progress = rememberReveal(Unit)
    val measurer = rememberTextMeasurer()
    val labelStyle: TextStyle = AppTheme.type.label.copy(color = colors.inkSubtle)
    val halo = colors.surface
    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        if (points.isEmpty()) return@Canvas
        val labelBand = 26.dp.toPx()
        val topPad = 12.dp.toPx()
        val sidePad = 8.dp.toPx()
        val chartH = size.height - labelBand - topPad
        val baseline = topPad + chartH

        val hair = 1.dp.toPx()
        for (i in 0..3) {
            val y = topPad + chartH * i / 3f
            drawLine(if (i == 3) colors.lineStrong else colors.line, Offset(0f, y), Offset(size.width, y), strokeWidth = hair)
        }

        val stroke = 2.25.dp.toPx()
        val pts = plotPoints(points.map { it.value }, sidePad, topPad + stroke, size.width - sidePad * 2, chartH - stroke * 2, zeroBased = true)
        if (pts.size == 1) {
            drawCircle(color, radius = stroke * 1.6f, center = Offset(size.width / 2f, pts[0].y))
        } else {
            drawSeries(pts, color, progress.value, baseline = baseline, strokeWidth = stroke, areaAlpha = 0.22f, endDot = true, haloColor = halo)
        }

        // Etiquetas del eje X: tantas como quepan, siempre incluyendo la primera y la última.
        val maxLabels = max(2, (size.width / 72.dp.toPx()).toInt())
        val step = max(1, kotlin.math.ceil((points.size - 1) / (maxLabels - 1).toDouble()).toInt())
        val labelAlpha = progress.value.coerceIn(0.01f, 1f)
        var lastRight = -1f
        for (i in points.indices) {
            val isLast = i == points.size - 1
            if (i % step != 0 && !isLast) continue
            val layout = measurer.measure(points[i].label, labelStyle, maxLines = 1)
            val anchor = if (pts.size == 1) size.width / 2f else pts[i].x
            val x = (anchor - layout.size.width / 2f).coerceIn(0f, max(0f, size.width - layout.size.width))
            if (x < lastRight + 6.dp.toPx() && !isLast) continue
            if (isLast && x < lastRight + 6.dp.toPx()) continue
            drawText(layout, topLeft = Offset(x, baseline + 8.dp.toPx()), alpha = labelAlpha)
            lastRight = x + layout.size.width
        }
    }
}

@Suppress("unused")
private fun unusedSize(): Size = Size.Zero

@Suppress("unused")
@Composable
private fun UnusedBox() { Box(Modifier) }
