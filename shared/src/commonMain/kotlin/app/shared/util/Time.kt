@file:OptIn(kotlin.time.ExperimentalTime::class)

package app.shared.util

import kotlin.random.Random

/** Epoch millis, igual en todos los targets (JVM, Wasm, iOS, Android). */
fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

/** Minutos transcurridos desde `epochMs` (nunca negativo). */
fun minutesSince(epochMs: Long, now: Long = nowMillis()): Int = (((now - epochMs).coerceAtLeast(0)) / 60_000L).toInt()

/** "ahora", "hace 5 min", "hace 2 h", "hace 3 d". */
fun relativeTime(epochMs: Long, now: Long = nowMillis()): String {
    val min = minutesSince(epochMs, now)
    return when {
        min < 1 -> "ahora"
        min < 60 -> "hace $min min"
        min < 60 * 24 -> "hace ${min / 60} h"
        else -> "hace ${min / (60 * 24)} d"
    }
}

/** "04:07" a partir de segundos transcurridos (para cronómetros de cocina). */
fun formatElapsed(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val m = s / 60
    return m.toString().padStart(2, '0') + ":" + (s % 60).toString().padStart(2, '0')
}

private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

/** Id corto y único suficiente para el cliente: "itm-k3f9a2x1". */
fun newId(prefix: String): String = prefix + "-" + (1..10).map { ID_ALPHABET[Random.nextInt(ID_ALPHABET.length)] }.joinToString("")
