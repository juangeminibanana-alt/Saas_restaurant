package app.data

import app.shared.api.*
import app.shared.data.AppRepository
import app.shared.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Esqueleto temporal: se reemplaza por la implementación completa. */
object DemoRepository : AppRepository {
    override val isRemote: Boolean = false
    private val _session = MutableStateFlow<UserSession?>(null)
    override val session: StateFlow<UserSession?> = _session
    override suspend fun listPublicTenants(): List<TenantPublic> = TODO()
    override suspend fun getTenant(slug: String): TenantPublic = TODO()
    override suspend fun getMenu(slug: String): Menu = TODO()
    override suspend fun createOrder(slug: String, request: CreateOrderRequest): CreateOrderResponse = TODO()
    override suspend fun simulatePayment(orderId: String): Order = TODO()
    override suspend fun getOrder(orderId: String): Order = TODO()
    override fun trackOrder(orderId: String): Flow<Order> = emptyFlow()
    override suspend fun listPlans(): List<Plan> = TODO()
    override suspend fun signup(request: SignupRequest): SignupResponse = TODO()
    override suspend fun login(email: String, password: String): UserSession = TODO()
    override fun logout() { _session.value = null }
    override suspend fun getBrand(): TenantPublic = TODO()
    override suspend fun updateBrand(request: UpdateBrandRequest): TenantPublic = TODO()
    override suspend fun getAdminMenu(): Menu = TODO()
    override suspend fun upsertCategory(category: MenuCategory): MenuCategory = TODO()
    override suspend fun deleteCategory(id: String) { TODO() }
    override suspend fun upsertItem(item: MenuItem): MenuItem = TODO()
    override suspend fun deleteItem(id: String) { TODO() }
    override suspend fun setItemAvailability(id: String, available: Boolean): MenuItem = TODO()
    override suspend fun listOrders(activeOnly: Boolean): List<Order> = TODO()
    override suspend fun updateOrderStatus(orderId: String, status: OrderStatus): Order = TODO()
    override suspend fun refundOrder(orderId: String): Order = TODO()
    override fun kitchenEvents(): Flow<RealtimeEvent> = emptyFlow()
    override suspend fun tenantStats(): TenantStats = TODO()
    override suspend fun listStaff(): List<StaffMember> = TODO()
    override suspend fun mercadoPagoStatus(): MpConnectionStatus = TODO()
    override suspend fun connectMercadoPago(): MpConnectionStatus = TODO()
    override suspend fun disconnectMercadoPago(): MpConnectionStatus = TODO()
    override suspend fun listIntegrations(): List<IntegrationStatus> = TODO()
    override suspend fun toggleIntegration(provider: IntegrationProvider): List<IntegrationStatus> = TODO()
    override suspend fun operatorMetrics(): OperatorMetrics = TODO()
    override suspend fun listTenants(): List<TenantSummary> = TODO()
    override suspend fun createTenant(request: CreateTenantRequest): TenantSummary = TODO()
    override suspend fun setTenantStatus(tenantId: String, status: TenantStatus): TenantSummary = TODO()
    override suspend fun assignPlan(tenantId: String, planId: String): TenantSummary = TODO()
    override suspend fun upsertPlan(plan: Plan): Plan = TODO()
}
