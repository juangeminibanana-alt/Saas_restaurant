package app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import app.ui.theme.AppTheme

/** Bloque de carga con brillo animado; dale tamaño con el `modifier`. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = AppTheme.shapes.sm) {
    Box(modifier.clip(shape).shimmer())
}
