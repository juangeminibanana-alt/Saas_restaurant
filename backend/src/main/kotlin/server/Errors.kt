package server

import io.ktor.http.HttpStatusCode

/** Error de negocio que StatusPages convierte en `ApiError` JSON con el status indicado. */
class ApiException(val status: HttpStatusCode, override val message: String, val code: String = "error") : RuntimeException(message)

fun badRequest(message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, message, "bad_request")
fun notFound(message: String): Nothing = throw ApiException(HttpStatusCode.NotFound, message, "not_found")
fun forbidden(message: String): Nothing = throw ApiException(HttpStatusCode.Forbidden, message, "forbidden")
fun conflict(message: String): Nothing = throw ApiException(HttpStatusCode.Conflict, message, "conflict")
