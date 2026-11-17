package server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import server.config.AppConfig
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Acceso a PostgreSQL con dos roles:
 *  - `tenantTx`: rol de aplicación (NOBYPASSRLS). Cada transacción fija `app.tenant_id` y las políticas
 *    RLS (ENABLE + FORCE) garantizan que solo se vean/escriban filas de ese restaurante.
 *  - `adminTx`: rol BYPASSRLS para operador, búsquedas públicas por slug/id, webhooks, migraciones y seed.
 */
object Db {
    private val log = LoggerFactory.getLogger("Db")

    private fun pool(user: String, password: String, size: Int, name: String): HikariDataSource {
        val cfg = HikariConfig()
        cfg.jdbcUrl = AppConfig.databaseUrl
        cfg.username = user
        cfg.password = password
        cfg.maximumPoolSize = size
        cfg.minimumIdle = 1
        cfg.poolName = name
        cfg.isAutoCommit = false
        cfg.connectionTimeout = 10_000
        return HikariDataSource(cfg)
    }

    private val appPool: HikariDataSource by lazy { pool(AppConfig.dbAppUser, AppConfig.dbAppPassword, 12, "app-rls") }
    private val adminPool: HikariDataSource by lazy { pool(AppConfig.dbAdminUser, AppConfig.dbAdminPassword, 6, "admin") }

    private fun <T> runTx(c: Connection, block: (Connection) -> T): T {
        try {
            val result = block(c)
            c.commit()
            return result
        } catch (e: Throwable) {
            try { c.rollback() } catch (_: Throwable) {}
            throw e
        }
    }

    /** Transacción del rol con RLS forzado, limitada al tenant indicado (uuid). */
    suspend fun <T> tenantTx(tenantId: String, block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        appPool.connection.use { c ->
            c.autoCommit = false
            runTx(c) {
                c.prepareStatement("SELECT set_config('app.tenant_id', ?, true)").use { ps ->
                    ps.setString(1, tenantId)
                    ps.execute()
                }
                block(c)
            }
        }
    }

    /** Transacción del rol administrador (BYPASSRLS). */
    suspend fun <T> adminTx(block: (Connection) -> T): T = withContext(Dispatchers.IO) { adminTxBlocking(block) }

    fun <T> adminTxBlocking(block: (Connection) -> T): T = adminPool.connection.use { c ->
        c.autoCommit = false
        runTx(c, block)
    }

    /** Corre las migraciones del classpath en orden, saltando archivos inexistentes y versiones ya aplicadas. */
    fun migrate() {
        val migrations = listOf("V1__init.sql", "V2__payments.sql")
        adminTxBlocking { c ->
            c.exec("CREATE TABLE IF NOT EXISTS schema_version (version text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())")
        }
        for (file in migrations) {
            val version = file.substringBefore("__")
            val sql = Db::class.java.classLoader.getResourceAsStream("db/migration/$file")?.use { it.readBytes().toString(Charsets.UTF_8) }
            if (sql == null) {
                log.info("Migración {} no encontrada; se omite", file)
                continue
            }
            adminTxBlocking { c ->
                val applied = c.query("SELECT 1 FROM schema_version WHERE version = ?", version) { true }.isNotEmpty()
                if (!applied) {
                    log.info("Aplicando migración {}", file)
                    c.createStatement().use { it.execute(sql) }
                    c.exec("INSERT INTO schema_version(version) VALUES (?)", version)
                }
            }
        }
        // El rol de aplicación solo tiene DML; RLS hace el resto.
        adminTxBlocking { c ->
            val app = AppConfig.dbAppUser.replace("\"", "")
            c.createStatement().use { st ->
                st.execute("GRANT USAGE ON SCHEMA public TO \"$app\"")
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"$app\"")
                st.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO \"$app\"")
                st.execute("REVOKE ALL ON schema_version FROM \"$app\"")
            }
        }
    }
}

// ───────────────────────── Helpers JDBC ─────────────────────────

fun PreparedStatement.bind(params: Array<out Any?>) {
    params.forEachIndexed { i, p ->
        val idx = i + 1
        when (p) {
            null -> setObject(idx, null)
            is String -> setString(idx, p)
            is Int -> setInt(idx, p)
            is Long -> setLong(idx, p)
            is Boolean -> setBoolean(idx, p)
            is Double -> setDouble(idx, p)
            else -> setObject(idx, p)
        }
    }
}

fun Connection.exec(sql: String, vararg params: Any?): Int =
    prepareStatement(sql).use { ps ->
        ps.bind(params)
        ps.executeUpdate()
    }

fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { ps ->
        ps.bind(params)
        ps.executeQuery().use { rs ->
            val out = ArrayList<T>()
            while (rs.next()) out.add(map(rs))
            out
        }
    }

fun <T> Connection.queryOne(sql: String, vararg params: Any?, map: (ResultSet) -> T): T? =
    query(sql, *params, map = map).firstOrNull()

fun ResultSet.longOrNull(column: String): Long? {
    val v = getLong(column)
    return if (wasNull()) null else v
}
