package app.shared.util

import app.shared.model.*

/** "$1,234.50" / "$185" — sin APIs de plataforma para que funcione igual en todos los targets. */
fun formatMoney(cents: Long, currency: String = "MXN"): String {
    val negative = cents < 0
    val abs = if (negative) -cents else cents
    val whole = abs / 100
    val frac = (abs % 100).toInt()
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    val decimals = if (frac == 0) "" else "." + frac.toString().padStart(2, '0')
    return (if (negative) "-" else "") + "$" + grouped + decimals
}

/** Compacto para métricas: "$128.4k", "$1.2M". */
fun formatMoneyCompact(cents: Long): String {
    val pesos = cents / 100.0
    fun one(v: Double): String { val t = (v * 10).toLong(); return if (t % 10 == 0L) (t / 10).toString() else "${t / 10}.${t % 10}" }
    return when {
        pesos >= 1_000_000 -> "$" + one(pesos / 1_000_000) + "M"
        pesos >= 10_000 -> "$" + one(pesos / 1_000) + "k"
        else -> formatMoney(cents)
    }
}

fun OrderStatus.label(): String = when (this) {
    OrderStatus.PENDING_PAYMENT -> "Pago pendiente"
    OrderStatus.PAID -> "Nuevo"
    OrderStatus.PREPARING -> "En preparación"
    OrderStatus.READY -> "Listo"
    OrderStatus.COMPLETED -> "Entregado"
    OrderStatus.CANCELLED -> "Cancelado"
    OrderStatus.REFUNDED -> "Reembolsado"
}

fun OrderType.label(): String = when (this) {
    OrderType.DINE_IN -> "En mesa"
    OrderType.PICKUP -> "Para llevar"
    OrderType.DELIVERY -> "A domicilio"
}

fun OrderChannel.label(): String = when (this) {
    OrderChannel.DIRECT -> "Directo"
    OrderChannel.UBER_EATS -> "Uber Eats"
    OrderChannel.RAPPI -> "Rappi"
    OrderChannel.DIDI_FOOD -> "DiDi Food"
}

fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.CARD -> "Tarjeta de crédito o débito"
    PaymentMethod.OXXO -> "Efectivo en OXXO"
    PaymentMethod.SPEI -> "Transferencia SPEI"
    PaymentMethod.MP_WALLET -> "Saldo en Mercado Pago"
    PaymentMethod.PAYMENT_LINK -> "Link de pago"
}

fun Role.label(): String = when (this) {
    Role.OPERATOR -> "Operador"
    Role.OWNER -> "Dueño"
    Role.MANAGER -> "Gerente"
    Role.CASHIER -> "Cajero"
    Role.KITCHEN -> "Cocina"
}

fun TenantStatus.label(): String = when (this) {
    TenantStatus.TRIAL -> "Prueba"
    TenantStatus.ACTIVE -> "Activo"
    TenantStatus.PAST_DUE -> "Pago vencido"
    TenantStatus.SUSPENDED -> "Suspendido"
}

/** Slug seguro para URL a partir de un nombre. */
fun slugify(text: String): String {
    val map = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')
    val sb = StringBuilder()
    for (ch in text.lowercase()) {
        val c = map[ch] ?: ch
        when {
            c in 'a'..'z' || c in '0'..'9' -> sb.append(c)
            sb.isNotEmpty() && sb.last() != '-' -> sb.append('-')
        }
    }
    return sb.toString().trim('-')
}
