package app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier, title: String? = null, maxWidth: Dp = 520.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) { content() }
}

@Composable
fun AppBottomSheet(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, fullHeight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if (visible) Column(modifier) { content() }
}

object Toasts { fun show(message: String, tone: Tone = Tone.NEUTRAL) {} }

@Composable
fun ToastHost(modifier: Modifier = Modifier) { Box(modifier) }
