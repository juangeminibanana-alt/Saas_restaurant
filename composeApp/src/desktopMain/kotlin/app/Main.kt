package app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main(args: Array<String>) {
    val initialRoute = args.firstOrNull() ?: "/"
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Restaurantes",
            state = rememberWindowState(width = 1280.dp, height = 860.dp),
        ) {
            App(initialRoute)
        }
    }
}
