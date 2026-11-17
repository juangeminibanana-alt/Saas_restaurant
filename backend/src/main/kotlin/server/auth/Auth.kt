package server.auth

import app.shared.api.ApiError
import app.shared.model.Role
import app.shared.model.UserSession
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import server.ApiException
import server.config.AppConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// ───────────────────────── Contraseñas (PBKDF2WithHmacSHA256 del JDK) ─────────────────────────

object Passwords {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Formato: pbkdf2_sha256$iteraciones$saltB64$hashB64 */
    fun hash(password: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val hash = pbkdf2(password, salt, ITERATIONS)
        val enc = Base64.getEncoder()
        return "pbkdf2_sha256$" + ITERATIONS + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(hash)
    }

    fun verify(password: String, stored: String): Boolean = try {
        val parts = stored.split("$")
        if (parts.size != 4 || parts[0] != "pbkdf2_sha256") false
        else {
            val dec = Base64.getDecoder()
            val expected = dec.decode(parts[3])
            val actual = pbkdf2(password, dec.decode(parts[2]), parts[1].toInt())
            MessageDigest.isEqual(expected, actual)
        }
    } catch (_: Exception) {
        false
    }
}

// ───────────────────────── JWT HS256 ─────────────────────────

object Tokens {
    private const val TTL_MS = 12L * 60 * 60 * 1000
    private val algorithm: Algorithm = Algorithm.HMAC256(AppConfig.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(AppConfig.jwtIssuer).build()

    fun issue(session: UserSession): String {
        val b = JWT.create()
            .withIssuer(AppConfig.jwtIssuer)
            .withSubject(session.userId)
            .withClaim("userId", session.userId)
            .withClaim("name", session.name)
            .withClaim("email", session.email)
            .withClaim("role", session.role.name)
            .withExpiresAt(Date(System.currentTimeMillis() + TTL_MS))
        session.tenantId?.let { b.withClaim("tenantId", it) }
        session.tenantSlug?.let { b.withClaim("tenantSlug", it) }
        session.tenantName?.let { b.withClaim("tenantName", it) }
        return b.sign(algorithm)
    }

    fun sessionOf(payload: Payload, token: String = ""): UserSession? {
        val userId = payload.getClaim("userId").asString() ?: return null
        val role = payload.getClaim("role").asString()?.let { r -> Role.entries.firstOrNull { it.name == r } } ?: return null
        val tenantId = payload.getClaim("tenantId").asString()
        if (role != Role.OPERATOR && tenantId.isNullOrBlank()) return null
        return UserSession(
            userId = userId,
            name = payload.getClaim("name").asString() ?: "",
            email = payload.getClaim("email").asString() ?: "",
            role = role,
            tenantId = tenantId,
            tenantSlug = payload.getClaim("tenantSlug").asString(),
            tenantName = payload.getClaim("tenantName").asString(),
            token = token,
        )
    }

    /** Verifica un token suelto (WebSocket con ?token=). */
    fun verify(token: String): UserSession? = try {
        sessionOf(verifier.verify(token), token)
    } catch (_: Exception) {
        null
    }
}

const val JWT_AUTH = "jwt"

fun Application.installAuth() {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "api"
            verifier(Tokens.verifier)
            validate { credential -> if (Tokens.sessionOf(credential.payload) != null) JWTPrincipal(credential.payload) else null }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("Tu sesión expiró. Inicia sesión de nuevo.", "unauthorized"))
            }
        }
    }
}

class RoleGuardConfig {
    var roles: Set<Role> = emptySet()
}

private val RoleGuard = createRouteScopedPlugin("RoleGuard", ::RoleGuardConfig) {
    val allowed = pluginConfig.roles
    on(AuthenticationChecked) { call ->
        val principal = call.principal<JWTPrincipal>() ?: return@on
        val session = Tokens.sessionOf(principal.payload)
        if (session == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiError("Sesión inválida.", "unauthorized"))
        } else if (allowed.isNotEmpty() && session.role !in allowed) {
            call.respond(HttpStatusCode.Forbidden, ApiError("No tienes permiso para hacer esto.", "forbidden"))
        }
    }
}

private class RoleRouteSelector(private val label: String) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(roles $label)"
}

/** JWT bearer obligatorio; el rol debe estar en `roles` (vacío = cualquier rol). */
fun Route.authenticated(vararg roles: Role, build: Route.() -> Unit) {
    authenticate(JWT_AUTH) {
        val guarded = createChild(RoleRouteSelector(roles.joinToString(",") { it.name }))
        guarded.install(RoleGuard) { this.roles = roles.toSet() }
        guarded.build()
    }
}

/** Sesión del usuario autenticado (tenantId no nulo para roles de restaurante). */
val ApplicationCall.user: UserSession
    get() {
        val principal = principal<JWTPrincipal>() ?: throw ApiException(HttpStatusCode.Unauthorized, "Inicia sesión para continuar.", "unauthorized")
        return Tokens.sessionOf(principal.payload) ?: throw ApiException(HttpStatusCode.Unauthorized, "Sesión inválida.", "unauthorized")
    }

/** tenantId del usuario (uuid) o 403 si es el operador. */
val ApplicationCall.tenantId: String
    get() = user.tenantId ?: throw ApiException(HttpStatusCode.Forbidden, "Esta sección es solo para restaurantes.", "forbidden")
