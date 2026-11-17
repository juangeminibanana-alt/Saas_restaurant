package app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.data.createRepository
import app.platform.PlatformBackHandler
import app.platform.defaultApiBaseUrl
import app.platform.syncStaffAlerts
import app.shared.data.AppRepository
import app.shell.AppNavigator
import app.shell.DemoSwitcher
import app.shell.LocalNavigator
import app.shell.LocalRepo
import app.shell.Router
import app.shell.ServerSettingsButton
import app.ui.components.InstallImageLoader
import app.ui.components.LoadingState
import app.ui.components.ToastHost
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.WindowSize

/**
 * Raíz de la app. Decide la fuente de datos (backend real si responde, demostración local si no),
 * provee tema neutro, tamaño de ventana, repositorio y navegación, y pinta la ruta actual.
 */
@Composable
fun App(initialRoute: String = "/", standalone: Boolean = false) {
    InstallImageLoader()
    val navigator = remember { AppNavigator(initialRoute, standalone) }
    // Cambia cuando la persona guarda otra dirección de servidor: se vuelve a elegir la fuente de datos.
    var repoVersion by remember { mutableStateOf(0) }
    val repo by produceState<AppRepository?>(initialValue = null, repoVersion) {
        value = null
        value = createRepository()
    }

    AppTheme {
        // El fondo llega hasta los bordes; el contenido respeta barra de estado, muesca y barra de navegación.
        BoxWithConstraints(Modifier.fillMaxSize().background(AppTheme.colors.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
            val windowSize = when {
                maxWidth < 600.dp -> WindowSize.COMPACT
                maxWidth < 1024.dp -> WindowSize.MEDIUM
                else -> WindowSize.EXPANDED
            }
            CompositionLocalProvider(LocalWindowSize provides windowSize, LocalNavigator provides navigator) {
                val current = repo
                if (current == null) {
                    LoadingState(Modifier.fillMaxSize())
                } else {
                    CompositionLocalProvider(LocalRepo provides current) {
                        // Con servidor real y sesión de cocina, los pedidos nuevos se escuchan también en segundo plano.
                        LaunchedEffect(current) {
                            current.session.collect { s ->
                                syncStaffAlerts(if (current.isRemote) defaultApiBaseUrl() else null, s?.token, s?.role?.name)
                            }
                        }
                        // "Atrás" del sistema: regresa a la ruta anterior; en la raíz deja que el sistema cierre la app.
                        val currentRoute = navigator.route
                        PlatformBackHandler(enabled = currentRoute.isNotEmpty() && navigator.canGoBack) { navigator.back() }
                        // La clave reinicia las pantallas cuando cambia la fuente de datos.
                        key(repoVersion) { Router(navigator) }
                        if (standalone) {
                            ServerSettingsButton(isRemote = current.isRemote, onChanged = { repoVersion++ })
                        } else {
                            DemoSwitcher(navigator)
                        }
                    }
                }
                ToastHost()
            }
        }
    }
}
