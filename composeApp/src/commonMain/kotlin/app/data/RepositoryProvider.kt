package app.data

import app.platform.defaultApiBaseUrl
import app.shared.api.ApiRoutes
import app.shared.data.AppRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Si es true, createRepository() devuelve siempre el repositorio de demostración (sin sondear el backend). */
var forceDemoData: Boolean = false

private const val HEALTH_TIMEOUT_MS = 1_500L

/**
 * Devuelve RemoteRepository si el backend responde 200 en /api/health dentro de 1.5 s;
 * ante cualquier falla (sin red, timeout, otro status) devuelve el DemoRepository en memoria.
 */
suspend fun createRepository(): AppRepository {
    if (forceDemoData) return DemoRepository
    val baseUrl = try {
        defaultApiBaseUrl().trim().trimEnd('/')
    } catch (e: Throwable) {
        return DemoRepository
    }
    return if (backendAnswers(baseUrl)) RemoteRepository(baseUrl) else DemoRepository
}

private suspend fun backendAnswers(baseUrl: String): Boolean {
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return false
    val client = try {
        HttpClient { expectSuccess = false }
    } catch (e: Throwable) {
        return false
    }
    return try {
        withTimeoutOrNull(HEALTH_TIMEOUT_MS) {
            val response = client.get(baseUrl + ApiRoutes.HEALTH)
            // Un servidor de archivos estáticos puede contestar 200 con el index.html: eso no es el backend.
            response.status == HttpStatusCode.OK && !response.bodyAsText().trimStart().startsWith("<")
        } ?: false
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()   // si cancelaron a quien llama, se propaga
        false
    } catch (e: Throwable) {
        false
    } finally {
        try {
            client.close()
        } catch (e: Throwable) {
            // nada que hacer
        }
    }
}
