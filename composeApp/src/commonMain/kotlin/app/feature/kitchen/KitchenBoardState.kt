package app.feature.kitchen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.shared.api.RealtimeEvent
import app.shared.data.AppRepository
import app.shared.data.RepositoryException
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.util.nowMillis
import app.ui.components.Toasts
import app.ui.components.Tone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Las tres columnas del tablero y la acción que mueve un pedido a la siguiente. */
internal enum class KitchenStage(
    val status: OrderStatus,
    val next: OrderStatus,
    val title: String,
    val action: String,
    val emptyTitle: String,
    val emptyMessage: String,
) {
    NEW(
        status = OrderStatus.PAID,
        next = OrderStatus.PREPARING,
        title = "Nuevos",
        action = "Aceptar",
        emptyTitle = "Sin pedidos nuevos",
        emptyMessage = "Los pedidos pagados aparecen aquí al instante.",
    ),
    PREPARING(
        status = OrderStatus.PREPARING,
        next = OrderStatus.READY,
        title = "En preparación",
        action = "Marcar listo",
        emptyTitle = "Nada en preparación",
        emptyMessage = "Acepta un pedido nuevo para empezar a cocinar.",
    ),
    READY(
        status = OrderStatus.READY,
        next = OrderStatus.COMPLETED,
        title = "Listos",
        action = "Entregar",
        emptyTitle = "Nada por entregar",
        emptyMessage = "Los pedidos terminados esperan aquí su entrega.",
    ),
    ;

    companion object {
        fun of(status: OrderStatus): KitchenStage? = entries.firstOrNull { it.status == status }
    }
}

/** Umbrales del cronómetro de cada ticket (desde que se pagó). */
internal object KitchenTiming {
    const val WARNING_SECONDS = 8 * 60L
    const val DANGER_SECONDS = 15 * 60L
    const val EXIT_MS = 320
    const val GLOW_MS = 3_600L
    const val RECONNECT_MS = 3_000L

    /** 0 = a tiempo, 1 = atención, 2 = retrasado. */
    fun level(seconds: Long): Int = when {
        seconds >= DANGER_SECONDS -> 2
        seconds >= WARNING_SECONDS -> 1
        else -> 0
    }
}

/** Momento desde el que corre el reloj del pedido. */
internal val Order.kitchenSince: Long get() = paidAt ?: createdAt

internal fun Order.elapsedSeconds(now: Long): Long = ((now - kitchenSince).coerceAtLeast(0L)) / 1000L

/** "04:07" y, para esperas muy largas, "2 h 05 min". */
internal fun formatKitchenElapsed(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val minutes = s / 60
    return if (minutes >= 100) {
        "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
    } else {
        minutes.toString().padStart(2, '0') + ":" + (s % 60).toString().padStart(2, '0')
    }
}

/**
 * Estado del tablero: foto inicial de pedidos activos + eventos en vivo fusionados en un mapa por id.
 * Todo corre en el hilo de UI (corrutinas de composición), por eso no necesita candados.
 */
@Stable
internal class KitchenBoardState(
    private val repo: AppRepository,
    private val scope: CoroutineScope,
) {
    private val orders = mutableStateMapOf<String, Order>()

    /** Pedidos que van de salida (animación de entrega) antes de quitarse del mapa. */
    private val leaving = mutableStateMapOf<String, Boolean>()

    /** Pedidos recién llegados: brillan unos segundos. */
    private val fresh = mutableStateMapOf<String, Boolean>()

    /** Pedidos con una acción en curso contra el servidor. */
    private val busy = mutableStateMapOf<String, Boolean>()

    // Datos auxiliares que no pintan nada (no son estado observable).
    private val closed = mutableSetOf<String>()
    private val touchedAt = mutableMapOf<String, Long>()
    private val entered = mutableSetOf<String>()

    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var live by mutableStateOf(false)
        private set

    val isEmpty: Boolean get() = orders.isEmpty()

    /** Pedidos por columna (índice = KitchenStage.ordinal), del más antiguo al más reciente. */
    fun columns(): List<List<Order>> {
        val all = orders.values.toList()
        return KitchenStage.entries.map { stage ->
            all.filter { it.status == stage.status }
                .sortedWith(compareBy<Order> { it.kitchenSince }.thenBy { it.number })
        }
    }

    fun isLeaving(id: String): Boolean = leaving[id] == true
    fun isFresh(id: String): Boolean = fresh[id] == true
    fun isBusy(id: String): Boolean = busy[id] == true

    /** true solo la primera vez que un ticket aparece en una columna (para animar su entrada una vez). */
    fun firstAppearance(key: String): Boolean = entered.add(key)

    /** Carga inicial + escucha en vivo con reconexión. Se cancela con la composición. */
    suspend fun run() = coroutineScope {
        launch { collectLive() }
        loadSnapshot(initial = true)
    }

    fun advance(order: Order) {
        val stage = KitchenStage.of(order.status) ?: return
        val id = order.id
        if (isBusy(id) || isLeaving(id)) return
        busy[id] = true
        scope.launch {
            try {
                merge(repo.updateOrderStatus(id, stage.next))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toasts.show(
                    (e as? RepositoryException)?.message ?: "No se pudo actualizar el pedido. Intenta de nuevo.",
                    Tone.DANGER,
                )
            } finally {
                busy.remove(id)
            }
        }
    }

    private suspend fun loadSnapshot(initial: Boolean) {
        if (initial) {
            loading = true
            error = null
        }
        val startedAt = nowMillis()
        try {
            val snapshot = repo.listOrders(activeOnly = true)
            mergeSnapshot(snapshot, startedAt)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (initial) error = (e as? RepositoryException)?.message ?: "No se pudieron cargar los pedidos."
        } finally {
            if (initial) loading = false
        }
    }

    private suspend fun collectLive() {
        while (true) {
            try {
                live = true
                repo.kitchenEvents().collect { event ->
                    live = true
                    when (event) {
                        is RealtimeEvent.OrderCreated -> merge(event.order)
                        is RealtimeEvent.OrderUpdated -> merge(event.order)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Se cayó la conexión: se reintenta abajo.
            }
            live = false
            delay(KitchenTiming.RECONNECT_MS)
            loadSnapshot(initial = false)
        }
    }

    /** Aplica un pedido que llegó por evento o como respuesta de una acción. */
    private fun merge(order: Order) {
        val id = order.id
        touchedAt[id] = nowMillis()
        if (KitchenStage.of(order.status) == null) {
            dismiss(id)
            return
        }
        closed.remove(id)
        leaving.remove(id)
        val isNewToKitchen = orders[id] == null && order.status == OrderStatus.PAID
        orders[id] = order
        if (isNewToKitchen) flash(id)
    }

    private fun mergeSnapshot(snapshot: List<Order>, requestedAt: Long) {
        val ids = HashSet<String>()
        for (order in snapshot) {
            val id = order.id
            ids.add(id)
            if (id in closed || KitchenStage.of(order.status) == null) continue
            val current = orders[id]
            // Un evento en vivo más avanzado gana sobre la foto.
            if (current == null || order.status.ordinal >= current.status.ordinal) orders[id] = order
        }
        // Lo que ya no está activo en el servidor (y no cambió mientras pedíamos la foto) sale del tablero.
        for (id in orders.keys.toList()) {
            if (id !in ids && (touchedAt[id] ?: 0L) < requestedAt) dismiss(id)
        }
    }

    private fun dismiss(id: String) {
        closed.add(id)
        if (orders[id] == null || isLeaving(id)) return
        leaving[id] = true
        scope.launch {
            delay(KitchenTiming.EXIT_MS.toLong())
            if (isLeaving(id)) {
                orders.remove(id)
                leaving.remove(id)
                fresh.remove(id)
                touchedAt.remove(id)
            }
        }
    }

    private fun flash(id: String) {
        fresh[id] = true
        scope.launch {
            delay(KitchenTiming.GLOW_MS)
            fresh.remove(id)
        }
    }
}
