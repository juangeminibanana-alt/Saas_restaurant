package app.shared.data

import app.shared.api.*
import app.shared.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Única puerta de datos de las apps. Dos implementaciones en composeApp:
 *  - DemoRepository: en memoria con DemoData (la app corre sin backend).
 *  - RemoteRepository: HTTP + WebSocket contra el backend Ktor (ver ApiRoutes).
 * Las funciones lanzan RepositoryException con mensaje en español listo para mostrar.
 */
interface AppRepository {
    /** true si los datos vienen del backend real; false en modo demostración local. */
    val isRemote: Boolean

    // ── Público (comensal)
    suspend fun listPublicTenants(): List<TenantPublic>
    suspend fun getTenant(slug: String): TenantPublic
    suspend fun getMenu(slug: String): Menu
    suspend fun createOrder(slug: String, request: CreateOrderRequest): CreateOrderResponse
    /** Sandbox/demo: aprueba el pago; el pedido pasa a PAID y entra a cocina. */
    suspend fun simulatePayment(orderId: String): Order
    suspend fun getOrder(orderId: String): Order
    /** Emite el pedido actual y cada cambio de estado (tiempo real). */
    fun trackOrder(orderId: String): Flow<Order>
    suspend fun listPlans(): List<Plan>
    suspend fun signup(request: SignupRequest): SignupResponse

    // ── Sesión
    val session: StateFlow<UserSession?>
    suspend fun login(email: String, password: String): UserSession
    fun logout()

    // ── Restaurante (tenant de la sesión)
    suspend fun getBrand(): TenantPublic
    suspend fun updateBrand(request: UpdateBrandRequest): TenantPublic
    suspend fun getAdminMenu(): Menu
    suspend fun upsertCategory(category: MenuCategory): MenuCategory
    suspend fun deleteCategory(id: String)
    suspend fun upsertItem(item: MenuItem): MenuItem
    suspend fun deleteItem(id: String)
    suspend fun setItemAvailability(id: String, available: Boolean): MenuItem
    suspend fun listOrders(activeOnly: Boolean): List<Order>
    suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Order
    suspend fun refundOrder(orderId: String): Order
    /** Pedidos que entran a cocina y sus cambios, de todos los canales. */
    fun kitchenEvents(): Flow<RealtimeEvent>
    suspend fun tenantStats(): TenantStats
    suspend fun listStaff(): List<StaffMember>
    suspend fun mercadoPagoStatus(): MpConnectionStatus
    suspend fun connectMercadoPago(): MpConnectionStatus
    suspend fun disconnectMercadoPago(): MpConnectionStatus
    suspend fun listIntegrations(): List<IntegrationStatus>
    suspend fun toggleIntegration(provider: IntegrationProvider): List<IntegrationStatus>

    // ── Operador del SaaS
    suspend fun operatorMetrics(): OperatorMetrics
    suspend fun listTenants(): List<TenantSummary>
    suspend fun createTenant(request: CreateTenantRequest): TenantSummary
    suspend fun setTenantStatus(tenantId: String, status: TenantStatus): TenantSummary
    suspend fun assignPlan(tenantId: String, planId: String): TenantSummary
    suspend fun upsertPlan(plan: Plan): Plan
}

class RepositoryException(message: String, val code: String = "error") : Exception(message)
