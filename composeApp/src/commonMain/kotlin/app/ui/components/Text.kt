package app.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.ui.theme.AppTheme
import app.ui.theme.LocalContentColor

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
        text = text,
        modifier = modifier,
        style = if (textAlign != null) style.copy(color = resolved, textAlign = textAlign) else style.copy(color = resolved),
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        maxLines = maxLines,
    )
}
