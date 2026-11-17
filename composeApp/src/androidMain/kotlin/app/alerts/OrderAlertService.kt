package app.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import app.platform.NotifyKind
import app.platform.notifyUser
import app.shared.api.ApiRoutes
import app.shared.api.AppJson
import app.shared.api.RealtimeEvent
import app.shared.model.Order
import app.shared.model.OrderStatus
import app.shared.util.label
import com.whitelabel.restaurantes.R
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.encodeURLParameter
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Lo que el servicio debe escuchar; lo escribe la app (ver Alerts.android.kt). */
internal object AlertPrefs {
    const val FILE = "order_alerts"
    const val BASE = "base"
    const val TOKEN = "token"            // sesión de personal: escuchar pedidos nuevos del restaurante
    const val ORDER_ID = "order_id"      // comensal: seguir el estado de este pedido
    const val ORDER_NUMBER = "order_number"
    const val ORDER_SEEN = "order_seen"   // "<orderId>:<estado>" del último estado visto (para avisar solo cambios)
}

/** Evita avisar dos veces lo mismo (la pantalla abierta y el servicio escuchan el mismo evento). */
internal object AlertDedup {
    private val seen = LinkedHashSet<String>()

    @Synchronized
    fun firstTime(key: String): Boolean {
        if (!seen.add(key)) return false
        if (seen.size > 400) seen.remove(seen.first())
        return true
    }
}

/**
 * Servicio en primer plano que mantiene la conexión en tiempo real con el servidor aunque la app no esté
 * en pantalla (Android congela las apps en segundo plano). Así:
 *  - cocina recibe el aviso de "pedido nuevo" con su sonido, y
 *  - el comensal recibe "en preparación" y "pedido listo" mientras tiene un pedido en curso.
 * Sustituye al push remoto mientras no haya Firebase configurado.
 */
class OrderAlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    // Ping cada 20 s: sin él, una conexión que muere en silencio (cambio de Wi-Fi) nunca se detecta ni se reintenta.
    private val client by lazy {
        HttpClient(OkHttp) {
            engine { config { pingInterval(20, TimeUnit.SECONDS) } }
            install(WebSockets)
        }
    }
    private var lastStartId = 0

    private var kitchenJob: Job? = null
    private var kitchenKey: String? = null
    private var orderJob: Job? = null
    private var orderKey: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Si Android recrea solo el servicio (sin actividad), notifyUser necesita este contexto para sonar y avisar.
        if (app.platform.AndroidPlatform.appContext == null) app.platform.AndroidPlatform.appContext = applicationContext
        instance = this
    }

    /** La app pide detenerlo: se hace desde el propio servicio para no chocar con un arranque en curso. */
    fun stopFromApp() {
        main.post { stopEverything() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(AlertPrefs.FILE, Context.MODE_PRIVATE)
        val base = prefs.getString(AlertPrefs.BASE, null)
        val token = prefs.getString(AlertPrefs.TOKEN, null)
        val orderId = prefs.getString(AlertPrefs.ORDER_ID, null)
        val orderNumber = prefs.getString(AlertPrefs.ORDER_NUMBER, null)

        lastStartId = startId
        // Obligatorio: pasar a primer plano de inmediato tras startForegroundService(). Si el sistema no lo
        // permite (reinicio en segundo plano en Android 12+), se detiene sin tumbar la app.
        val foreground = runCatching { startInForeground(statusText(token != null, orderId != null, orderNumber)) }.isSuccess
        if (!foreground) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (base == null || (token == null && orderId == null)) {
            stopEverything()
            return START_NOT_STICKY
        }

        val newKitchenKey = token?.let { "$base|$it" }
        if (newKitchenKey != kitchenKey) {
            kitchenJob?.cancel()
            kitchenKey = newKitchenKey
            kitchenJob = if (token != null) scope.launch { listenKitchen(base, token) } else null
        }
        val newOrderKey = orderId?.let { "$base|$it" }
        if (newOrderKey != orderKey) {
            orderJob?.cancel()
            orderKey = newOrderKey
            orderJob = if (orderId != null) {
                scope.launch {
                    // Un pedido que nunca se cierra no debe dejar el servicio encendido para siempre.
                    val timedOut = withTimeoutOrNull(MAX_ORDER_TRACKING_MS) { listenOrder(base, orderId) } == null
                    if (timedOut) main.post { if (orderKey == newOrderKey) stopTrackingOrder() }
                }
            } else {
                null
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        runCatching { client.close() }
        super.onDestroy()
    }

    private fun stopEverything() {
        kitchenJob?.cancel(); kitchenJob = null; kitchenKey = null
        orderJob?.cancel(); orderJob = null; orderKey = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        // Solo se detiene si no hay un arranque más reciente pendiente (evita cerrar un servicio recién pedido).
        stopSelf(lastStartId)
    }

    /** Deja de seguir el pedido del comensal; apaga el servicio si no queda nada que escuchar. */
    private fun stopTrackingOrder() {
        getSharedPreferences(AlertPrefs.FILE, Context.MODE_PRIVATE).edit()
            .remove(AlertPrefs.ORDER_ID).remove(AlertPrefs.ORDER_NUMBER).remove(AlertPrefs.ORDER_SEEN).apply()
        orderJob?.cancel(); orderJob = null; orderKey = null
        if (kitchenJob == null) stopEverything() else updateStatus(statusText(true, false, null))
    }

    // ── Conexiones

    private fun wsUrl(base: String, path: String): String = when {
        base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
        base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
        else -> "ws://$base"
    } + path

    /** Mantiene abierta la conexión y reintenta con espera creciente (1 s … 15 s). */
    private suspend fun keepListening(url: String, onText: (String) -> Unit) {
        var backoff = 1_000L
        while (currentCoroutineContext().isActive) {
            val startedAt = System.currentTimeMillis()
            try {
                client.webSocket(urlString = url) {
                    for (frame in incoming) {
                        if (frame is Frame.Text) onText(frame.readText())
                    }
                }
            } catch (ce: CancellationException) {
                // Solo termina si cancelaron al servicio; si fue el servidor quien cerró, se reintenta.
                currentCoroutineContext().ensureActive()
            } catch (_: Throwable) {
                // Sin red o servidor apagado: se reintenta.
            }
            // La espera solo vuelve a 1 s si la conexión duró de verdad (un rechazo inmediato no reintenta cada segundo).
            if (System.currentTimeMillis() - startedAt > 10_000L) backoff = 1_000L
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(15_000L)
        }
    }

    private fun decode(text: String): RealtimeEvent? =
        runCatching { AppJson.decodeFromString(RealtimeEvent.serializer(), text) }.getOrNull()

    /** Personal del restaurante: cada pedido pagado que entra a cocina. */
    private suspend fun listenKitchen(base: String, token: String) {
        val url = wsUrl(base, ApiRoutes.WS_TENANT_ORDERS + "?token=" + token.encodeURLParameter())
        keepListening(url) { text ->
            val event = decode(text)
            if (event is RealtimeEvent.OrderCreated) alertNewOrder(event.order)
        }
    }

    private fun alertNewOrder(order: Order) {
        val where = order.tableLabel.ifBlank { order.customerName }.let { if (it.isBlank()) "" else " · $it" }
        val body = "${order.type.label()}$where · ${order.lines.sumOf { it.quantity }} platillos"
        main.post { notifyUser(NotifyKind.NEW_ORDER, "Nuevo pedido ${order.number}", body, "new:${order.id}") }
    }

    /** Comensal: cambios de estado de su pedido en curso. Termina solo cuando el pedido se cierra. */
    private suspend fun listenOrder(base: String, orderId: String) {
        val url = wsUrl(base, ApiRoutes.wsPublicOrder(orderId))
        keepListening(url) { text ->
            val order = when (val event = decode(text)) {
                is RealtimeEvent.OrderUpdated -> event.order
                is RealtimeEvent.OrderCreated -> event.order
                null -> null
            } ?: return@keepListening
            if (order.id != orderId) return@keepListening
            alertOrderStatus(order)
        }
    }

    private fun alertOrderStatus(order: Order) {
        // El servidor manda el estado actual al conectar: se avisa solo cuando cambia respecto al último visto.
        val prefs = getSharedPreferences(AlertPrefs.FILE, Context.MODE_PRIVATE)
        val seen = "${order.id}:${order.status.name}"
        val previous = prefs.getString(AlertPrefs.ORDER_SEEN, null)
        prefs.edit().putString(AlertPrefs.ORDER_SEEN, seen).apply()
        val changed = previous != null && previous.startsWith("${order.id}:") && previous != seen

        val key = "st:${order.id}:${order.status}"
        if (changed) {
            when (order.status) {
                OrderStatus.PREPARING -> main.post { notifyUser(NotifyKind.ORDER_UPDATE, "Pedido ${order.number}", "Ya lo estamos preparando.", key) }
                OrderStatus.READY -> main.post { notifyUser(NotifyKind.ORDER_READY, "¡Tu pedido ${order.number} está listo!", "Ya puedes disfrutarlo.", key) }
                OrderStatus.COMPLETED -> main.post { notifyUser(NotifyKind.ORDER_UPDATE, "Pedido ${order.number} entregado", "¡Buen provecho!", key) }
                else -> Unit
            }
        }
        val closed = order.status == OrderStatus.COMPLETED || order.status == OrderStatus.CANCELLED || order.status == OrderStatus.REFUNDED
        main.post {
            // Ignora un evento atrasado de un pedido que ya no es el que se sigue.
            if (orderKey?.endsWith("|${order.id}") != true) return@post
            if (closed) {
                stopTrackingOrder()
            } else {
                updateStatus(statusText(kitchenJob != null, true, "${order.number} · ${order.status.label()}"))
            }
        }
    }

    // ── Notificación permanente del servicio (silenciosa)

    private fun statusText(kitchen: Boolean, order: Boolean, orderLabel: String?): String = when {
        kitchen && order -> "Recibiendo pedidos · siguiendo tu pedido ${orderLabel ?: ""}".trim()
        kitchen -> "Recibiendo pedidos en tiempo real"
        order -> "Siguiendo tu pedido ${orderLabel ?: ""}".trim()
        else -> "Conectando…"
    }

    private fun buildStatus(text: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(STATUS_CHANNEL, "Conexión en tiempo real", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launch?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, STATUS_CHANNEL) else Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (pending != null) setContentIntent(pending) }
            .build()
    }

    private fun startInForeground(text: String) {
        val notification = buildStatus(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(STATUS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(STATUS_ID, notification)
        }
    }

    private fun updateStatus(text: String) {
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(STATUS_ID, buildStatus(text))
        }
    }

    internal companion object {
        private const val STATUS_CHANNEL = "conexion_tiempo_real"
        private const val STATUS_ID = 42
        private const val MAX_ORDER_TRACKING_MS = 3 * 60 * 60_000L

        /** Servicio vivo en este proceso (para pedirle que se detenga sin llamar a stopService desde fuera). */
        @Volatile
        var instance: OrderAlertService? = null
    }
}
