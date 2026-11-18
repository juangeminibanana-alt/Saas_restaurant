package app.feature.diner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.platform.openExternalUrl
import app.shared.model.OrderStatus
import app.shared.model.TenantPublic
import app.shared.util.nowMillis
import app.shell.LocalRepo
import app.ui.components.AppButton
import app.ui.components.ButtonSize
import app.ui.components.ButtonVariant
import app.ui.components.ErrorState
import app.ui.components.Skeleton
import app.ui.components.Toasts
import app.ui.components.Tone
import app.ui.theme.AppTheme
import app.ui.theme.LocalWindowSize
import app.ui.theme.Motion
import app.ui.theme.Sizes
import app.ui.theme.Space
import app.ui.theme.WindowSize
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Menú digital del comensal (Fase 1), en el sistema NOCHE. Marca blanca: del restaurante sólo entran su
 * color de acento (`tenant.theme.primary`) y su fotografía; tipografía, radios y modo oscuro son del sistema.
 * `slug` admite sufijos: "brasa-demo?mesa=4" (QR por mesa) y "brasa-demo?order=<id>" (regreso de Mercado Pago).
 */
@Composable
fun DinerApp(slug: String, onExit: () -> Unit) {
    val repo = LocalRepo.current
    val route = remember(slug) { parseDinerSlug(slug) }
    val state = remember(route) { DinerState(route) }

    LaunchedEffect(route, state.reloadTick) {
        state.loading = true
        state.error = null
        try {
            val tenant = repo.getTenant(route.slug)
            val menu = repo.getMenu(route.slug)
            state.tenant = tenant
            state.menu = menu
            state.applyTenantDefaults(tenant)
            state.loadedAt = nowMillis()
            // Al volver a entrar al menú, refresca el estado de los pedidos de esta visita (la cocina pudo avanzarlos).
            for (previous in state.history.toList()) {
                try {
                    state.recordOrder(repo.getOrder(previous.id))
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    // Se queda con el último estado conocido.
                }
            }
            val orderId = route.orderId
            if (orderId != null && state.activeOrder == null) {
                try {
                    val order = repo.getOrder(orderId)
                    state.recordOrder(order)
                    state.activeOrder = order
                    state.checkoutUrl = order.payment?.checkoutUrl
                    state.screen = if (order.status == OrderStatus.PENDING_PAYMENT) DinerScreen.WAITING else DinerScreen.TRACKING
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Toasts.show("No encontramos ese pedido.", Tone.WARNING)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            state.error = e.message?.takeIf { it.isNotBlank() } ?: "No pudimos cargar el menú. Revisa tu conexión e inténtalo de nuevo."
        }
        state.loading = false
    }

    AppTheme(brand = state.tenant?.theme) {
        Box(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
            val tenant = state.tenant
            val error = state.error
            when {
                tenant != null -> DinerContent(state, tenant, onExit)
                error != null -> Column(
                    Modifier.fillMaxSize().padding(Space.x6),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ErrorState(message = error, onRetry = { state.reloadTick++ })
                    Spacer(Modifier.height(Space.x4))
                    AppButton("Salir", onClick = onExit, variant = ButtonVariant.GHOST, size = ButtonSize.SM)
                }
                else -> MenuSkeleton()
            }
        }
    }
}

@Composable
private fun DinerContent(state: DinerState, tenant: TenantPublic, onExit: () -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val listState: LazyListState = rememberLazyListState()
    val expanded = LocalWindowSize.current == WindowSize.EXPANDED

    // En escritorio el carrito vive en el panel lateral del menú.
    LaunchedEffect(expanded, state.screen) {
        if (expanded && state.screen == DinerScreen.CART) state.screen = DinerScreen.MENU
    }
    LaunchedEffect(state.scrollTopTick) {
        if (state.scrollTopTick > 0) listState.scrollToItem(0)
    }

    val pay: () -> Unit = pay@{
        if (state.payPhase != PayPhase.IDLE) return@pay
        val problem = state.validate()
        if (problem != null) {
            state.showErrors = true
            Toasts.show(problem, Tone.WARNING)
            return@pay
        }
        state.payPhase = PayPhase.CREATING
        scope.launch {
            try {
                val response = repo.createOrder(state.route.slug, state.buildRequest())
                state.recordOrder(response.order)
                val url = response.checkoutUrl
                if (url != null) {
                    state.activeOrder = response.order
                    state.checkoutUrl = url
                    state.payPhase = PayPhase.IDLE
                    openExternalUrl(url)
                    state.screen = DinerScreen.WAITING
                } else {
                    state.pendingOrder = response.order
                    state.payPhase = PayPhase.SANDBOX
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                state.payPhase = PayPhase.IDLE
                Toasts.show(e.message?.takeIf { it.isNotBlank() } ?: "No pudimos crear tu pedido. Inténtalo de nuevo.", Tone.DANGER)
            }
        }
    }

    val simulate: () -> Unit = simulate@{
        val order = state.pendingOrder ?: return@simulate
        if (state.payPhase != PayPhase.SANDBOX) return@simulate
        state.payPhase = PayPhase.PROCESSING
        scope.launch {
            try {
                delay(1_100)
                val paid = repo.simulatePayment(order.id)
                state.recordOrder(paid)
                state.payPhase = PayPhase.SUCCESS
                delay(1_500)
                state.activeOrder = paid
                state.clearCart()
                state.screen = DinerScreen.TRACKING
                state.payPhase = PayPhase.IDLE
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                state.payPhase = PayPhase.SANDBOX
                Toasts.show(e.message?.takeIf { it.isNotBlank() } ?: "No pudimos procesar el pago de prueba.", Tone.DANGER)
            }
        }
    }

    // Sigue el pedido en curso desde cualquier pantalla del menú: así llegan los avisos de "en preparación" y "listo".
    val trackedOrderId = state.activeOrder?.id
    LaunchedEffect(trackedOrderId) {
        val id = trackedOrderId
        if (id == null) {
            app.platform.syncOrderAlerts(null, null, null)
            return@LaunchedEffect
        }
        // Con servidor real, el pedido también se sigue en segundo plano (avisos con la app minimizada).
        app.platform.syncOrderAlerts(if (repo.isRemote) app.platform.defaultApiBaseUrl() else null, id, state.activeOrder?.number)
        try {
            repo.trackOrder(id).collect { updated -> state.recordOrder(updated) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            // Sin conexión momentánea: se conserva el último estado conocido.
        }
    }

    // "Atrás" del sistema dentro del menú: de carrito, rastreo o historial se vuelve al menú (sin perder el carrito).
    app.platform.PlatformBackHandler(enabled = state.screen != DinerScreen.MENU && state.payPhase == PayPhase.IDLE) {
        state.screen = DinerScreen.MENU
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        AnimatedContent(
            targetState = state.screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // Eje compartido horizontal: avanzar entra por la derecha, volver por la izquierda.
                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(Motion.SLOW, easing = Motion.standard)) { w -> dir * w / 12 } +
                    fadeIn(tween(Motion.SLOW, easing = Motion.standard)))
                    .togetherWith(
                        slideOutHorizontally(tween(Motion.BASE, easing = Motion.exit)) { w -> -dir * w / 12 } +
                            fadeOut(tween(Motion.BASE, easing = Motion.exit)),
                    )
            },
            label = "diner-screen",
        ) { screen ->
            when (screen) {
                DinerScreen.MENU -> MenuScreen(state, tenant, listState, onExit, pay)
                DinerScreen.CART -> CartScreen(state, tenant, pay)
                DinerScreen.WAITING -> WaitingPaymentScreen(state, tenant)
                DinerScreen.TRACKING -> TrackingScreen(state, tenant)
                DinerScreen.HISTORY -> HistoryScreen(state, tenant)
            }
        }
        DishSheet(state, viewportHeight)
        SandboxPaymentSheet(state, viewportHeight, simulate)
    }
}

/**
 * Esqueleto de carga con la misma retícula del menú: portada, fila de buscar + categorías, encabezado,
 * mosaico grande y dos filas. Superficies planas que respiran; nada brilla.
 */
@Composable
fun MenuSkeleton() {
    val compact = LocalWindowSize.current == WindowSize.COMPACT
    val gutter = dinerGutter(compact)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1180.dp).fillMaxSize()) {
            // Identidad: antetítulo, nombre y ficha de datos, al pie de donde irá la portada.
            Column(
                Modifier.fillMaxWidth().height(SkeletonCover).padding(horizontal = gutter),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Skeleton(Modifier.width(Space.x18 * 3).height(Space.x3), shape = AppTheme.shapes.xs)
                Spacer(Modifier.height(Space.x3))
                Skeleton(Modifier.width(Space.x18 * 3).height(Space.x8))
                Spacer(Modifier.height(Space.x6))
                Skeleton(Modifier.fillMaxWidth().height(Space.x3), shape = AppTheme.shapes.xs)
            }
            Row(Modifier.fillMaxWidth().padding(start = gutter, top = Space.x4, bottom = Space.x3), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                Skeleton(Modifier.size(Sizes.control), shape = AppTheme.shapes.md)
                Skeleton(Modifier.weight(1f).height(Sizes.control), shape = AppTheme.shapes.md)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
                Skeleton(Modifier.padding(vertical = Space.x3).width(Space.x18 * 2).height(Space.x6))
                Skeleton(Modifier.fillMaxWidth().height(SkeletonTile), shape = AppTheme.shapes.lg)
                repeat(2) {
                    Row(Modifier.fillMaxWidth().padding(top = Space.x6), horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        Skeleton(Modifier.size(Sizes.thumb), shape = AppTheme.shapes.md)
                        Column(Modifier.weight(1f)) {
                            Skeleton(Modifier.fillMaxWidth(0.6f).height(Space.x4))
                            Spacer(Modifier.height(Space.x2))
                            Skeleton(Modifier.fillMaxWidth(0.9f).height(Space.x3))
                        }
                    }
                }
            }
        }
    }
}

/** Alto de la portada y del mosaico grande en el esqueleto (los de la maqueta en un teléfono de 844). */
private val SkeletonCover = 316.dp
private val SkeletonTile = 304.dp
