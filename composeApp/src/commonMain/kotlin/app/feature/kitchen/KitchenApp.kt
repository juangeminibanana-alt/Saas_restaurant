package app.feature.kitchen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.shared.data.RepositoryException
import app.shared.model.Role
import app.shared.model.TenantPublic
import app.shared.model.UserSession
import app.shell.LocalRepo
import app.shell.LoginGate
import app.ui.components.ErrorState
import app.ui.components.LoadingState
import app.ui.theme.AppTheme
import kotlinx.coroutines.CancellationException

/**
 * Pantalla de cocina en tiempo real. Entra cualquier pedido pagado (venta directa o marketplaces),
 * y el equipo lo mueve por Nuevos → En preparación → Listos → Entregado.
 */
@Composable
fun KitchenApp(onExit: () -> Unit) {
    LoginGate(
        allowedRoles = setOf(Role.KITCHEN, Role.OWNER, Role.MANAGER, Role.CASHIER),
        title = "Pantalla de cocina",
        subtitle = "Inicia sesión para ver los pedidos",
        demoAccounts = listOf(
            "Cocina · Brasa Demo" to "cocina@brasa.demo",
            "Cocina · Nori Demo" to "cocina@nori.demo",
            "Cocina · Miga Demo" to "cocina@miga.demo",
        ),
        onExit = onExit,
    ) { session ->
        KitchenRoot(session = session, onExit = onExit)
    }
}

@Composable
private fun KitchenRoot(session: UserSession, onExit: () -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()

    var brand by remember(session.userId) { mutableStateOf<TenantPublic?>(null) }
    var brandError by remember(session.userId) { mutableStateOf<String?>(null) }
    var brandAttempt by remember(session.userId) { mutableIntStateOf(0) }

    LaunchedEffect(session.userId, brandAttempt) {
        brandError = null
        try {
            brand = repo.getBrand()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            brandError = (e as? RepositoryException)?.message ?: "No se pudo cargar la información del restaurante."
        }
    }

    val board = remember(session.userId) { KitchenBoardState(repo, scope) }
    var boardAttempt by remember(session.userId) { mutableIntStateOf(0) }
    LaunchedEffect(board, boardAttempt) { board.run() }

    // Del restaurante sólo entra su acento; el resto lo fija el sistema.
    AppTheme(brand = brand?.theme) {
        Box(
            modifier = Modifier.fillMaxSize().background(AppTheme.colors.canvas),
            contentAlignment = Alignment.Center,
        ) {
            val tenant = brand
            val failure = brandError
            when {
                tenant != null -> KitchenScreen(
                    tenant = tenant,
                    session = session,
                    board = board,
                    onRetry = { boardAttempt++ },
                    onLogout = { repo.logout() },
                    onExit = onExit,
                )
                failure != null -> ErrorState(message = failure, onRetry = { brandAttempt++ })
                else -> LoadingState(message = "Preparando la cocina…")
            }
        }
    }
}
