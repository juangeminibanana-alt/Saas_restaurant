package app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ui.theme.AppTheme
import app.ui.theme.LocalSurfaceLevel
import app.ui.theme.Space

/**
 * Barra inferior de NOCHE: sólida (superficie 1), con un filete arriba, sin sombra ni desenfoque.
 * 72 px de alto con un control de 48 dentro: margen lateral 16, 12 arriba y abajo, 12 entre elementos.
 * Respeta el área segura inferior. Patrón: a la izquierda un bloque de 120 ([BottomBarSummary] o un
 * `QuantityStepper` grande) y a la derecha la acción con `Modifier.weight(1f)`.
 * Los controles que contiene se pintan en superficie 2.
 */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = AppTheme.colors
    CompositionLocalProvider(LocalSurfaceLevel provides 1) {
        Row(
            modifier
                .fillMaxWidth()
                .background(colors.surface)
                .edgeTop(colors.line)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = Space.gutter, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
            content = content,
        )
    }
}

/** Resumen a la izquierda de la barra: etiqueta en mono ("3 PLATILLOS") sobre el total en mono 20/24. Ancho fijo de 120. */
@Composable
fun BottomBarSummary(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.width(120.dp)) {
        LabelText(label)
        AppText(value, style = AppTheme.type.monoLg, maxLines = 1)
    }
}
