package app.data

import app.shared.api.ApiError
import app.shared.api.ApiRoutes
import app.shared.api.AppJson
import app.shared.api.AssignPlanRequest
import app.shared.api.CreateOrderRequest
import app.shared.api.CreateOrderResponse
import app.shared.api.CreateTenantRequest
import app.shared.api.LoginRequest
import app.shared.api.RealtimeEvent
import app.shared.api.SetAvailabilityRequest
import app.shared.api.SetTenantStatusRequest
import app.shared.api.SignupRequest
import app.shared.api.SignupResponse
import app.shared.api.UpdateBrandRequest
import app.shared.api.UpdateOrderStatusRequest
import app.shared.data.AppRepository
import app.shared.data.RepositoryException
import app.shared.model.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.serializer

private const val REQUEST_TIMEOUT_MS = 20_000L
private const val NO_CONNECTION = "Sin conexión con el servidor"
private const val MAX_BACKOFF_MS = 5_000L
private const val POLL_INTERVAL_MS = 4_000L

/**
 * Repositorio contra el backend: cada método corresponde 1:1 a una ruta de [ApiRoutes].
 * La sesión vive en memoria; el token viaja como "Authorization: Bearer".
 */
class RemoteRepository(baseUrl: String) : AppRepository {
    override val isRemote: Boolean = true

    private val baseUrl: String = baseUrl.trimEnd('/')
    private val wsBaseUrl: String = when {
        this.baseUrl.startsWith("https://") -> "wss://" + this.baseUrl.removePrefix("https://")
        this.baseUrl.startsWith("http://") -> "ws://" + this.baseUrl.removePrefix("http://")
        else -> "ws://" + this.baseUrl
    }

    private val client = HttpClient {
        install(ContentNegotiation) { json(AppJson) }
        install(WebSockets)
        expectSuccess = false
    }

    private val _session = MutableStateFlow<UserSession?>(null)
    override val session: StateFlow<UserSession?> = _session.asStateFlow()

    // ─────────────────────────────── HTTP ───────────────────────────────

    /** Ejecuta la petición y devuelve el cuerpo como texto; traduce errores a RepositoryException en español. */
    private suspend fun send(method: HttpMethod, path: String, auth: Boolean, configure: HttpRequestBuilder.() -> Unit): String {
        val token = if (auth) {
            _session.value?.token?.takeIf { it.isNotBlank() } ?: throw RepositoryException("Inicia sesión para continuar", "unauthorized")
        } else {
            null
        }
        val result: Pair<HttpStatusCode, String>? = try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                val response = client.request(baseUrl + path) {
                    this.method = method
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                    configure()
                }
                response.status to response.bodyAsText()
            }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()   // si quien llama fue cancelado, se propaga
            null
        } catch (e: Throwable) {
            null
        }
        if (result == null) throw RepositoryException(NO_CONNECTION, "network")
        val (status, text) = result
        if (!status.isSuccess()) {
            val error = try {
                AppJson.decodeFromString(ApiError.serializer(), text)
            } catch (e: Exception) {
                null
            }
            if (auth && status == HttpStatusCode.Unauthorized) _session.value = null
            throw RepositoryException(
                error?.message?.takeIf { it.isNotBlank() } ?: defaultMessage(status),
                error?.code ?: status.value.toString(),
            )
        }
        return text
    }

    private fun defaultMessage(status: HttpStatusCode): String = when (status.value) {
        401 -> "Inicia sesión para continuar"
        403 -> "No tienes permiso para esto"
        404 -> "No encontramos lo que buscas"
        else -> "Algo salió mal, intenta de nuevo"
    }

    private inline fun <reified T> decode(text: String): T = try {
        AppJson.decodeFromString(serializer<T>(), text)
    } catch (e: Exception) {
        throw RepositoryException("Respuesta inesperada del servidor", "decode")
    }

    private inline fun <reified B> HttpRequestBuilder.jsonBody(body: B) {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        setBody(AppJson.encodeToString(serializer<B>(), body))
    }

    private suspend inline fun <reified T> get(path: String, auth: Boolean = false): T =
        decode(send(HttpMethod.Get, path, auth) {})

    private suspend inline fun <reified T> post(path: String, auth: Boolean = false): T =
        decode(send(HttpMethod.Post, path, auth) {})

    private suspend inline fun <reified B, reified T> postJson(path: String, body: B, auth: Boolean = false): T =
        decode(send(HttpMethod.Post, path, auth) { jsonBody(body) })

    private suspend inline fun <reified B, reified T> putJson(path: String, body: B, auth: Boolean = true): T =
        decode(send(HttpMethod.Put, path, auth) { jsonBody(body) })

    private suspend fun delete(path: String) {
        send(HttpMethod.Delete, path, true) {}
    }

    // ─────────────────────────────── Público (comensal) ───────────────────────────────

    override suspend fun listPublicTenants(): List<TenantPublic> = get(ApiRoutes.PUBLIC_TENANTS)

    override suspend fun getTenant(slug: String): TenantPublic = get(ApiRoutes.publicTenant(slug))

    override suspend fun getMenu(slug: String): Menu = get(ApiRoutes.publicMenu(slug))

    override suspend fun createOrder(slug: String, request: CreateOrderRequest): CreateOrderResponse =
        postJson(ApiRoutes.publicOrders(slug), request)

    override suspend fun simulatePayment(orderId: String): Order = post(ApiRoutes.simulatePayment(orderId))

    override suspend fun getOrder(orderId: String): Order = get(ApiRoutes.publicOrder(orderId))

    override fun trackOrder(orderId: String): Flow<Order> = channelFlow {
        val out = this
        var last: Order? = null
        suspend fun push(order: Order) {
            if (order != last) {
                last = order
                out.send(order)
            }
        }
        suspend fun refresh() {
            try {
                push(getOrder(orderId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // se reintenta en la siguiente vuelta
            }
        }
        // Primer valor: si el pedido no existe se avisa; si solo falta red, se sigue intentando.
        try {
            push(getOrder(orderId))
        } catch (e: RepositoryException) {
            if (e.code != "network") throw e
        }
        var backoff = 500L
        while (isActive) {
            var opened = false
            try {
                client.webSocket(wsBaseUrl + ApiRoutes.wsPublicOrder(orderId)) {
                    opened = true
                    backoff = 500L
                    refresh()   // por si hubo cambios mientras no había socket
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        when (val event = decodeEvent(frame.readText())) {
                            is RealtimeEvent.OrderCreated -> if (event.order.id == orderId) push(event.order)
                            is RealtimeEvent.OrderUpdated -> if (event.order.id == orderId) push(event.order)
                            null -> Unit
                        }
                    }
                }
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (e: Throwable) {
                // socket caído o no disponible
            }
            if (opened) {
                // Reconexión automática con espera creciente.
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            } else {
                // No se pudo abrir el socket: sondeo cada 4 s y se vuelve a intentar el socket después.
                repeat(5) {
                    delay(POLL_INTERVAL_MS)
                    refresh()
                }
            }
        }
    }

    override suspend fun listPlans(): List<Plan> = get(ApiRoutes.PUBLIC_PLANS)

    override suspend fun signup(request: SignupRequest): SignupResponse {
        val response: SignupResponse = postJson(ApiRoutes.PUBLIC_SIGNUP, request)
        _session.value = response.session
        return response
    }

    // ─────────────────────────────── Sesión ───────────────────────────────

    override suspend fun login(email: String, password: String): UserSession {
        val newSession: UserSession = postJson(ApiRoutes.LOGIN, LoginRequest(email.trim(), password))
        _session.value = newSession
        return newSession
    }

    override fun logout() {
        _session.value = null
    }

    // ─────────────────────────────── Restaurante ───────────────────────────────

    override suspend fun getBrand(): TenantPublic = get(ApiRoutes.TENANT_BRAND, auth = true)

    override suspend fun updateBrand(request: UpdateBrandRequest): TenantPublic = putJson(ApiRoutes.TENANT_BRAND, request)

    override suspend fun getAdminMenu(): Menu = get(ApiRoutes.TENANT_MENU, auth = true)

    override suspend fun upsertCategory(category: MenuCategory): MenuCategory {
        val withId = if (category.id.isBlank()) category.copy(id = app.shared.util.newId("cat")) else category
        return putJson(ApiRoutes.tenantCategory(withId.id), withId)
    }

    override suspend fun deleteCategory(id: String) = delete(ApiRoutes.tenantCategory(id))

    override suspend fun upsertItem(item: MenuItem): MenuItem {
        val withId = if (item.id.isBlank()) item.copy(id = app.shared.util.newId("itm")) else item
        return putJson(ApiRoutes.tenantItem(withId.id), withId)
    }

    override suspend fun deleteItem(id: String) = delete(ApiRoutes.tenantItem(id))

    override suspend fun setItemAvailability(id: String, available: Boolean): MenuItem =
        putJson(ApiRoutes.tenantItemAvailability(id), SetAvailabilityRequest(available))

    override suspend fun listOrders(activeOnly: Boolean): List<Order> =
        get(ApiRoutes.TENANT_ORDERS + "?active=" + activeOnly, auth = true)

    override suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Order =
        putJson(ApiRoutes.tenantOrderStatus(orderId), UpdateOrderStatusRequest(status))

    override suspend fun refundOrder(orderId: String): Order = post(ApiRoutes.tenantOrderRefund(orderId), auth = true)

    override fun kitchenEvents(): Flow<RealtimeEvent> = channelFlow {
        val out = this
        var backoff = 500L
        while (isActive) {
            val token = _session.value?.token?.takeIf { it.isNotBlank() }
                ?: throw RepositoryException("Inicia sesión para continuar", "unauthorized")
            try {
                client.webSocket(wsBaseUrl + ApiRoutes.WS_TENANT_ORDERS + "?token=" + token.encodeURLParameter()) {
                    backoff = 500L
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val event = decodeEvent(frame.readText())
                        if (event != null) out.send(event)
                    }
                }
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (e: Throwable) {
                // socket caído: se reconecta abajo
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun decodeEvent(text: String): RealtimeEvent? = try {
        AppJson.decodeFromString(RealtimeEvent.serializer(), text)
    } catch (e: Exception) {
        null
    }

    override suspend fun tenantStats(): TenantStats = get(ApiRoutes.TENANT_STATS, auth = true)

    override suspend fun listStaff(): List<StaffMember> = get(ApiRoutes.TENANT_STAFF, auth = true)

    override suspend fun mercadoPagoStatus(): MpConnectionStatus = get(ApiRoutes.TENANT_MP, auth = true)

    override suspend fun connectMercadoPago(): MpConnectionStatus = post(ApiRoutes.TENANT_MP_CONNECT, auth = true)

    override suspend fun disconnectMercadoPago(): MpConnectionStatus = post(ApiRoutes.TENANT_MP_DISCONNECT, auth = true)

    override suspend fun listIntegrations(): List<IntegrationStatus> = get(ApiRoutes.TENANT_INTEGRATIONS, auth = true)

    override suspend fun toggleIntegration(provider: IntegrationProvider): List<IntegrationStatus> =
        post(ApiRoutes.tenantIntegrationToggle(provider), auth = true)

    // ─────────────────────────────── Operador ───────────────────────────────

    override suspend fun operatorMetrics(): OperatorMetrics = get(ApiRoutes.OPERATOR_METRICS, auth = true)

    override suspend fun listTenants(): List<TenantSummary> = get(ApiRoutes.OPERATOR_TENANTS, auth = true)

    override suspend fun createTenant(request: CreateTenantRequest): TenantSummary =
        postJson(ApiRoutes.OPERATOR_TENANTS, request, auth = true)

    override suspend fun setTenantStatus(tenantId: String, status: TenantStatus): TenantSummary =
        putJson(ApiRoutes.operatorTenantStatus(tenantId), SetTenantStatusRequest(status))

    override suspend fun assignPlan(tenantId: String, planId: String): TenantSummary =
        putJson(ApiRoutes.operatorTenantPlan(tenantId), AssignPlanRequest(planId))

    override suspend fun upsertPlan(plan: Plan): Plan {
        val withId = if (plan.id.isBlank()) plan.copy(id = app.shared.util.newId("plan")) else plan
        return putJson(ApiRoutes.operatorPlan(withId.id), withId)
    }
}
