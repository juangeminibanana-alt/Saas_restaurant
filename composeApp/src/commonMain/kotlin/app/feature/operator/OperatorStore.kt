package app.feature.operator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.shared.api.CreateTenantRequest
import app.shared.data.AppRepository
import app.shared.data.RepositoryException
import app.shared.model.OperatorMetrics
import app.shared.model.Plan
import app.shared.model.TenantStatus
import app.shared.model.TenantSummary
import app.ui.components.Toasts
import app.ui.components.Tone
import kotlin.coroutines.cancellation.CancellationException

private const val GENERIC_ERROR = "No pudimos completar la operación. Intenta de nuevo."

/** Estado de la consola: métricas, restaurantes y planes, con sus mutaciones. */
internal class OperatorStore(private val repo: AppRepository) {
    var metrics by mutableStateOf<OperatorMetrics?>(null)
        private set
    var tenants by mutableStateOf<List<TenantSummary>>(emptyList())
        private set
    var plans by mutableStateOf<List<Plan>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var loaded by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Id del restaurante recién creado: su fila entra animada y resaltada. */
    var highlightTenantId by mutableStateOf<String?>(null)

    val isRemote: Boolean get() = repo.isRemote

    suspend fun load() {
        loading = true
        error = null
        try {
            val m = repo.operatorMetrics()
            val t = repo.listTenants()
            val p = repo.listPlans()
            metrics = m
            tenants = t
            plans = p
            loaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: RepositoryException) {
            error = e.message ?: "No pudimos cargar la información."
        } catch (e: Exception) {
            error = "No pudimos cargar la información. Revisa tu conexión e intenta de nuevo."
        }
        loading = false
    }

    private suspend fun refreshQuietly(includeTenants: Boolean = false) {
        try {
            metrics = repo.operatorMetrics()
            if (includeTenants) tenants = repo.listTenants()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Silencioso: los datos visibles siguen siendo válidos.
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RepositoryException) {
        Toasts.show(e.message ?: GENERIC_ERROR, Tone.DANGER)
        null
    } catch (e: Exception) {
        Toasts.show(GENERIC_ERROR, Tone.DANGER)
        null
    }

    private fun replaceTenant(updated: TenantSummary) {
        tenants = tenants.map { if (it.id == updated.id) updated else it }
    }

    suspend fun setStatus(tenant: TenantSummary, status: TenantStatus): Boolean {
        val updated = attempt { repo.setTenantStatus(tenant.id, status) } ?: return false
        replaceTenant(updated)
        Toasts.show(
            if (status == TenantStatus.SUSPENDED) "${tenant.name} quedó suspendido" else "${tenant.name} está activo de nuevo",
            if (status == TenantStatus.SUSPENDED) Tone.WARNING else Tone.SUCCESS,
        )
        refreshQuietly()
        return true
    }

    suspend fun assignPlan(tenant: TenantSummary, plan: Plan): Boolean {
        val updated = attempt { repo.assignPlan(tenant.id, plan.id) } ?: return false
        replaceTenant(updated)
        Toasts.show("${tenant.name} ahora está en el plan ${plan.name}", Tone.SUCCESS)
        refreshQuietly()
        return true
    }

    suspend fun createTenant(request: CreateTenantRequest): TenantSummary? {
        val created = attempt { repo.createTenant(request) } ?: return null
        highlightTenantId = created.id
        tenants = listOf(created) + tenants.filter { it.id != created.id }
        refreshQuietly()
        return created
    }

    suspend fun savePlan(plan: Plan, isNew: Boolean): Boolean {
        val saved = attempt { repo.upsertPlan(plan) } ?: return false
        plans = if (plans.any { it.id == saved.id }) plans.map { if (it.id == saved.id) saved else it } else plans + saved
        tenants = tenants.map { if (it.planId == saved.id) it.copy(planName = saved.name) else it }
        Toasts.show(if (isNew) "Plan ${saved.name} creado" else "Plan ${saved.name} actualizado", Tone.SUCCESS)
        refreshQuietly(includeTenants = true)
        return true
    }
}
