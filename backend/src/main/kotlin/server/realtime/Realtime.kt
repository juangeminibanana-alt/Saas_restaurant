package server.realtime

import app.shared.api.AppJson
import app.shared.api.RealtimeEvent
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import server.config.AppConfig
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Bus de tiempo real. Con Redis: PUBLISH a "kitchen:{tenantId}" / "order:{orderId}" y UNA conexión
 * suscriptora (PSUBSCRIBE) que reparte a las sesiones WebSocket del proceso. Sin Redis: bus en memoria.
 * También expone colas simples (LPUSH/BRPOP) para trabajos en segundo plano.
 */
object Realtime {
    private val log = LoggerFactory.getLogger("Realtime")

    private val listeners = ConcurrentHashMap<String, CopyOnWriteArraySet<(String) -> Unit>>()
    private val memoryQueues = ConcurrentHashMap<String, LinkedBlockingDeque<String>>()
    private val queueConnections = ConcurrentHashMap<String, StatefulRedisConnection<String, String>>()

    @Volatile private var client: RedisClient? = null
    @Volatile private var commands: StatefulRedisConnection<String, String>? = null
    @Volatile private var subscriber: StatefulRedisPubSubConnection<String, String>? = null
    @Volatile private var started = false

    val redisAvailable: Boolean get() = commands != null

    fun kitchenChannel(tenantId: String) = "kitchen:$tenantId"
    fun orderChannel(orderId: String) = "order:$orderId"

    @Synchronized
    fun start() {
        if (started) return
        started = true
        try {
            val uri = RedisURI.create(AppConfig.redisUrl)
            uri.timeout = Duration.ofSeconds(3)
            val c = RedisClient.create(uri)
            val cmd = c.connect()
            cmd.sync().ping()
            val sub = c.connectPubSub()
            sub.addListener(object : RedisPubSubAdapter<String, String>() {
                override fun message(pattern: String, channel: String, message: String) = dispatch(channel, message)
            })
            sub.sync().psubscribe("kitchen:*", "order:*")
            client = c
            commands = cmd
            subscriber = sub
            log.info("Redis conectado en {} (pub/sub activo)", AppConfig.redisUrl.substringAfter('@'))
        } catch (e: Exception) {
            log.warn("Redis no disponible ({}). Se usa el bus en memoria: el tiempo real solo funciona dentro de este proceso.", e.message)
            try { client?.shutdown() } catch (_: Exception) {}
            client = null
            commands = null
            subscriber = null
        }
    }

    fun stop() {
        try { queueConnections.values.forEach { it.close() } } catch (_: Exception) {}
        try { subscriber?.close() } catch (_: Exception) {}
        try { commands?.close() } catch (_: Exception) {}
        try { client?.shutdown() } catch (_: Exception) {}
    }

    private fun dispatch(channel: String, message: String) {
        listeners[channel]?.forEach { listener ->
            try { listener(message) } catch (e: Exception) { log.debug("Listener falló: {}", e.message) }
        }
    }

    /** Publica texto en un canal (Redis o memoria). */
    fun publish(channel: String, payload: String) {
        val cmd = commands
        if (cmd != null) {
            try {
                cmd.async().publish(channel, payload)
                return
            } catch (e: Exception) {
                log.warn("PUBLISH falló ({}); se entrega en memoria", e.message)
            }
        }
        dispatch(channel, payload)
    }

    fun publishEvent(channel: String, event: RealtimeEvent) =
        publish(channel, AppJson.encodeToString(RealtimeEvent.serializer(), event))

    /** Registra un listener en proceso; cerrar el resultado lo da de baja. */
    fun subscribe(channel: String, listener: (String) -> Unit): AutoCloseable {
        val set = listeners.computeIfAbsent(channel) { CopyOnWriteArraySet() }
        set.add(listener)
        return AutoCloseable {
            set.remove(listener)
            if (set.isEmpty()) listeners.remove(channel, set)
        }
    }

    // ───────────────────────── Colas ─────────────────────────

    /** Redis LPUSH (o cola en memoria si no hay Redis). */
    fun publishQueue(queue: String, payload: String) {
        val cmd = commands
        if (cmd != null) {
            try {
                cmd.async().lpush(queue, payload)
                return
            } catch (e: Exception) {
                log.warn("LPUSH falló ({}); se encola en memoria", e.message)
            }
        }
        memoryQueues.computeIfAbsent(queue) { LinkedBlockingDeque() }.offerFirst(payload)
    }

    /** Redis BRPOP con timeout (o cola en memoria). Devuelve null si no llegó nada. */
    suspend fun popQueue(queue: String, timeoutSeconds: Long): String? = withContext(Dispatchers.IO) {
        val seconds = timeoutSeconds.coerceIn(1, 30)
        val c = client
        if (c != null && commands != null) {
            try {
                val conn = queueConnections.computeIfAbsent(queue) { c.connect().also { it.timeout = Duration.ofSeconds(45) } }
                val kv = synchronized(conn) { conn.sync().brpop(seconds, queue) }
                return@withContext if (kv != null && kv.hasValue()) kv.value else null
            } catch (e: Exception) {
                log.warn("BRPOP falló ({})", e.message)
                queueConnections.remove(queue)?.let { try { it.close() } catch (_: Exception) {} }
                Thread.sleep(500)
                return@withContext null
            }
        }
        memoryQueues.computeIfAbsent(queue) { LinkedBlockingDeque() }.pollLast(seconds, TimeUnit.SECONDS)
    }
}
