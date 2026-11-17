package server.payments

import server.config.AppConfig
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal fun hmacSha256Hex(secret: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

/** `state` del OAuth: "<base64url(tenantId|expira)>.<hmac>" firmado con el secreto del servidor. */
internal object OAuthState {
    private const val TTL_MS = 30L * 60 * 1000
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val dec = Base64.getUrlDecoder()
    private val key: String get() = "mp-oauth-state:" + AppConfig.jwtSecret

    fun sign(tenantId: String, now: Long = System.currentTimeMillis()): String {
        val payload = enc.encodeToString("$tenantId|${now + TTL_MS}".toByteArray(Charsets.UTF_8))
        return payload + "." + hmacSha256Hex(key, payload)
    }

    /** Devuelve el tenant id si la firma es válida y no expiró. */
    fun verify(state: String?, now: Long = System.currentTimeMillis()): String? {
        if (state.isNullOrBlank()) return null
        val payload = state.substringBefore('.', "")
        val sig = state.substringAfter('.', "")
        if (payload.isEmpty() || sig.isEmpty()) return null
        if (!constantTimeEquals(hmacSha256Hex(key, payload), sig)) return null
        val text = runCatching { String(dec.decode(payload), Charsets.UTF_8) }.getOrNull() ?: return null
        val tenantId = text.substringBefore('|')
        val exp = text.substringAfter('|', "").toLongOrNull() ?: return null
        return if (exp >= now && tenantId.isNotBlank()) tenantId else null
    }
}

/**
 * Firma de webhooks de Mercado Pago. Header `x-signature: ts=<ts>,v1=<hmac>`;
 * manifiesto = "id:<data.id>;request-id:<x-request-id>;ts:<ts>;" (se omite la parte que no venga).
 */
internal object MpWebhookSignature {
    fun manifest(dataId: String?, requestId: String?, ts: String): String = buildString {
        if (!dataId.isNullOrEmpty()) append("id:").append(dataId.lowercase()).append(';')
        if (!requestId.isNullOrEmpty()) append("request-id:").append(requestId).append(';')
        append("ts:").append(ts).append(';')
    }

    /** true si no hay secreto configurado (desarrollo) o si la firma coincide. */
    fun isValid(xSignature: String?, requestId: String?, dataId: String?): Boolean {
        val secret = AppConfig.mpWebhookSecret ?: return true
        if (xSignature.isNullOrBlank()) return false
        val parts = xSignature.split(',').mapNotNull {
            val k = it.substringBefore('=', "").trim()
            val v = it.substringAfter('=', "").trim()
            if (k.isEmpty() || v.isEmpty()) null else k to v
        }.toMap()
        val ts = parts["ts"] ?: return false
        val v1 = parts["v1"] ?: return false
        return constantTimeEquals(hmacSha256Hex(secret, manifest(dataId, requestId, ts)), v1.lowercase())
    }
}
