package server.payments

import server.db.Db
import java.sql.Connection
import java.sql.Timestamp
import java.sql.Types

/** Cuenta de Mercado Pago de un restaurante (fila de mp_connections). */
data class MpConnection(
    val tenantId: String,
    val mpUserId: String,
    val accessToken: String,
    val refreshToken: String?,
    val publicKey: String?,
    val liveMode: Boolean,
    val expiresAt: Long?,               // epoch millis
) {
    /** Token de prueba ⇒ se usa sandbox_init_point. */
    val isTestToken: Boolean get() = accessToken.startsWith("TEST-") || !liveMode
}

data class TenantPayInfo(val id: String, val slug: String, val name: String, val feeBps: Int)

/** Acceso a datos de pagos. Tablas propias: mp_connections, webhook_events. */
internal object MpStore {

    // ── mp_connections ──

    fun read(c: Connection, tenantId: String): MpConnection? =
        c.prepareStatement(
            "SELECT tenant_id::text, mp_user_id, access_token, refresh_token, public_key, live_mode, expires_at " +
                "FROM mp_connections WHERE tenant_id::text = ?",
        ).use { ps ->
            ps.setString(1, tenantId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else MpConnection(
                    tenantId = rs.getString(1),
                    mpUserId = rs.getString(2),
                    accessToken = rs.getString(3),
                    refreshToken = rs.getString(4),
                    publicKey = rs.getString(5),
                    liveMode = rs.getBoolean(6),
                    expiresAt = rs.getTimestamp(7)?.time,
                )
            }
        }

    fun write(c: Connection, conn: MpConnection) {
        c.prepareStatement(
            "INSERT INTO mp_connections (tenant_id, mp_user_id, access_token, refresh_token, public_key, live_mode, expires_at, updated_at) " +
                "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, now()) " +
                "ON CONFLICT (tenant_id) DO UPDATE SET mp_user_id = EXCLUDED.mp_user_id, access_token = EXCLUDED.access_token, " +
                "refresh_token = EXCLUDED.refresh_token, public_key = EXCLUDED.public_key, live_mode = EXCLUDED.live_mode, " +
                "expires_at = EXCLUDED.expires_at, updated_at = now()",
        ).use { ps ->
            ps.setString(1, conn.tenantId)
            ps.setString(2, conn.mpUserId)
            ps.setString(3, conn.accessToken)
            ps.setString(4, conn.refreshToken)
            ps.setString(5, conn.publicKey)
            ps.setBoolean(6, conn.liveMode)
            if (conn.expiresAt != null) ps.setTimestamp(7, Timestamp(conn.expiresAt)) else ps.setNull(7, Types.TIMESTAMP)
            ps.executeUpdate()
        }
    }

    fun remove(c: Connection, tenantId: String) {
        c.prepareStatement("DELETE FROM mp_connections WHERE tenant_id::text = ?").use { ps ->
            ps.setString(1, tenantId)
            ps.executeUpdate()
        }
    }

    /** Rutas del restaurante: pasan por el rol con RLS. */
    suspend fun getForTenant(tenantId: String): MpConnection? = Db.tenantTx(tenantId) { read(it, tenantId) }
    suspend fun upsert(conn: MpConnection) = Db.tenantTx(conn.tenantId) { write(it, conn) }
    suspend fun delete(tenantId: String) = Db.tenantTx(tenantId) { remove(it, tenantId) }

    /** Webhooks / callback OAuth / creación de checkout: sin sesión de restaurante. */
    suspend fun getAdmin(tenantId: String): MpConnection? = Db.adminTx { read(it, tenantId) }
    suspend fun upsertAdmin(conn: MpConnection) = Db.adminTx { write(it, conn) }

    // ── tenants / plans (tablas de core) ──

    suspend fun tenantInfo(tenantId: String): TenantPayInfo? = Db.adminTx { c ->
        c.prepareStatement(
            "SELECT t.id::text, t.slug, t.name, COALESCE(p.fee_bps, 150) FROM tenants t " +
                "LEFT JOIN plans p ON p.id = t.plan_id WHERE t.id::text = ?",
        ).use { ps ->
            ps.setString(1, tenantId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) null else TenantPayInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4))
            }
        }
    }

    suspend fun setPreapprovalId(tenantId: String, preapprovalId: String) = Db.adminTx { c ->
        c.prepareStatement("UPDATE tenants SET mp_preapproval_id = ? WHERE id::text = ?").use { ps ->
            ps.setString(1, preapprovalId)
            ps.setString(2, tenantId)
            ps.executeUpdate()
        }
    }

    /** Actualiza el estado del tenant por su id o, si no viene, por el id de la suscripción. */
    suspend fun setTenantStatus(tenantId: String?, preapprovalId: String, status: String): Int = Db.adminTx { c ->
        c.prepareStatement(
            "UPDATE tenants SET status = ? WHERE (id::text = ? OR mp_preapproval_id = ?) AND status <> 'SUSPENDED'",
        ).use { ps ->
            ps.setString(1, status)
            ps.setString(2, tenantId ?: "")
            ps.setString(3, preapprovalId)
            ps.executeUpdate()
        }
    }

    // ── webhook_events ──

    /** Guarda el evento de forma idempotente. Devuelve true si aún hay que procesarlo. */
    suspend fun recordEvent(eventKey: String, tenantId: String?, topic: String, dataId: String, payload: String): Boolean =
        Db.adminTx { c ->
            c.prepareStatement(
                "INSERT INTO webhook_events (provider, event_key, tenant_id, topic, data_id, payload) " +
                    "VALUES ('mercadopago', ?, ?::uuid, ?, ?, ?::jsonb) ON CONFLICT (provider, event_key) DO NOTHING",
            ).use { ps ->
                ps.setString(1, eventKey)
                ps.setString(2, tenantId)
                ps.setString(3, topic)
                ps.setString(4, dataId)
                ps.setString(5, payload)
                ps.executeUpdate()
            }
            c.prepareStatement(
                "SELECT processed_at IS NULL FROM webhook_events WHERE provider = 'mercadopago' AND event_key = ?",
            ).use { ps ->
                ps.setString(1, eventKey)
                ps.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
        }

    suspend fun markEventProcessed(eventKey: String, result: String) = Db.adminTx { c ->
        c.prepareStatement(
            "UPDATE webhook_events SET processed_at = now(), result = ?, attempts = attempts + 1 " +
                "WHERE provider = 'mercadopago' AND event_key = ?",
        ).use { ps ->
            ps.setString(1, result.take(500))
            ps.setString(2, eventKey)
            ps.executeUpdate()
        }
    }

    suspend fun markEventFailed(eventKey: String, error: String) = Db.adminTx { c ->
        c.prepareStatement(
            "UPDATE webhook_events SET result = ?, attempts = attempts + 1 WHERE provider = 'mercadopago' AND event_key = ?",
        ).use { ps ->
            ps.setString(1, ("error: $error").take(500))
            ps.setString(2, eventKey)
            ps.executeUpdate()
        }
    }
}
