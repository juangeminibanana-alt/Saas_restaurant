package app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.ui.theme.AppTheme
import app.ui.theme.LocalContentColor

/** Geist no trae el guion de no separación (U+2011) ni el guion tipográfico corto (U+2010): se dibujan como guion. */
private fun String.withGeistGlyphs(): String =
    if (indexOf('‑') < 0 && indexOf('‐') < 0) this else replace('‑', '-').replace('‐', '-')

/**
 * Texto base del sistema. Dentro de una superficie de color (botón, chip seleccionado…) el color
 * por defecto (tinta) se sustituye por el color de contenido heredado.
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.type.body,
    color: Color = AppTheme.colors.ink,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    val inherited = LocalContentColor.current
    val resolved = when {
        !color.isSpecified -> if (inherited.isSpecified) inherited else AppTheme.colors.ink
        color == AppTheme.colors.ink && inherited.isSpecified -> inherited
        else -> color
    }
    BasicText(
        text = text.withGeistGlyphs(),
        modifier = modifier,
        style = if (textAlign != null) style.copy(color = resolved, textAlign = textAlign) else style.copy(color = resolved),
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        maxLines = maxLines,
    )
}

/** Etiqueta de NOCHE ya compuesta: MAYÚSCULAS, +0.06em entre letras y el espacio entre palabras cerrado 0.25em. */
internal fun labelString(text: String): AnnotatedString = buildAnnotatedString {
    val upper = text.withGeistGlyphs().uppercase()
    append(upper)
    val tightSpace = SpanStyle(letterSpacing = (-0.19f).em)
    upper.forEachIndexed { i, ch -> if (ch == ' ') addStyle(tightSpace, i, i + 1) }
}

/**
 * Etiqueta de NOCHE: Geist Mono 11/16 en MAYÚSCULAS con tracking abierto. Para antetítulos, encabezados de
 * columna, nombres de campo, estados y conteos con unidad ("3 PLATILLOS", "ELIGE 1").
 * [dot] antepone un punto sólido de 6 px: en color de brasa avisa que falta una elección obligatoria
 * (y entonces la etiqueta va en tinta); en `positive`, "abierto".
 */
@Composable
fun LabelText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.inkSubtle,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    dot: Color? = null,
) {
    val base = AppTheme.type.monoLabel
    val style = if (textAlign != null) base.copy(color = color, textAlign = textAlign) else base.copy(color = color)
    val label = remember(text) { labelString(text) }
    if (dot == null) {
        BasicText(text = label, modifier = modifier, style = style, overflow = TextOverflow.Ellipsis, softWrap = maxLines > 1, maxLines = maxLines)
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
            BasicText(text = label, style = style, overflow = TextOverflow.Ellipsis, softWrap = maxLines > 1, maxLines = maxLines)
        }
    }
}

/** Punto sólido de estado (6 px): `positive` = abierto, brasa = pendiente, etc. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).background(color, CircleShape))
}
