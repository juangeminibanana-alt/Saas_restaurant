package app.shell

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import app.shared.data.AppRepository

/** Única puerta de datos; la provee App() en la raíz. */
val LocalRepo: ProvidableCompositionLocal<AppRepository> =
    staticCompositionLocalOf { error("LocalRepo no está disponible: envuelve el contenido en App()") }

interface Navigator {
    val route: String
    fun navigate(route: String)
    fun back()
}

val LocalNavigator: ProvidableCompositionLocal<Navigator> =
    staticCompositionLocalOf { error("LocalNavigator no está disponible: envuelve el contenido en App()") }
