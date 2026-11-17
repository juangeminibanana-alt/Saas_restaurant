package app.feature.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Temporary stub: the owner of this feature replaces the whole file. */
@Composable
fun PanelApp(onExit: () -> Unit, onOpenDiner: (slug: String) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Panel")
    }
}
