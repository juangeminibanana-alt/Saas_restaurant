package server.db

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.slf4j.LoggerFactory
import java.io.File

/**
 * PostgreSQL real embebido (mismo motor, con row-level security): permite correr el servidor en cualquier
 * computadora que tenga Java, sin Docker ni instalar una base de datos. Se activa con EMBEDDED_DB=true.
 *
 * Los datos se guardan en EMBEDDED_DB_DIR (por defecto ./.data/postgres) y sobreviven entre arranques.
 * Debe llamarse ANTES de leer AppConfig, porque fija DATABASE_URL para este proceso.
 */
object EmbeddedDb {
    private val log = LoggerFactory.getLogger("EmbeddedDb")
    private var instance: EmbeddedPostgres? = null

    private fun setting(name: String, default: String): String =
        (System.getProperty(name) ?: System.getenv(name))?.trim()?.takeIf { it.isNotEmpty() } ?: default

    fun startIfRequested() {
        val flag = setting("EMBEDDED_DB", "false").lowercase()
        if (flag != "true" && flag != "1") return

        val dataDir = File(setting("EMBEDDED_DB_DIR", ".data/postgres")).absoluteFile
        val port = setting("EMBEDDED_DB_PORT", "54329").toIntOrNull() ?: 54329
        dataDir.mkdirs()
        log.info("Iniciando la base de datos embebida en {} (puerto {}). La primera vez tarda un poco…", dataDir, port)

        val pg = EmbeddedPostgres.builder()
            .setDataDirectory(dataDir)
            .setCleanDataDirectory(false)
            .setPort(port)
            .start()
        instance = pg
        Runtime.getRuntime().addShutdownHook(Thread { try { pg.close() } catch (_: Exception) {} })

        bootstrap(pg)
        System.setProperty("DATABASE_URL", "jdbc:postgresql://localhost:$port/${setting("DB_NAME", "saas")}")
        log.info("Base de datos embebida lista.")
    }

    /** Equivalente a scripts/dev-db.sh: roles (admin con BYPASSRLS, app con RLS forzado) y base de datos. Idempotente. */
    private fun bootstrap(pg: EmbeddedPostgres) {
        val db = setting("DB_NAME", "saas")
        val admin = setting("DB_ADMIN_USER", "saas_admin")
        val adminPassword = setting("DB_ADMIN_PASSWORD", "saas_admin_dev").replace("'", "''")
        val app = setting("DB_APP_USER", "saas_app")
        val appPassword = setting("DB_APP_PASSWORD", "saas_app_dev").replace("'", "''")

        pg.postgresDatabase.connection.use { c ->
            c.createStatement().use { st ->
                st.execute(
                    """
                    DO ${'$'}${'$'}
                    BEGIN
                      IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$admin') THEN
                        CREATE ROLE $admin LOGIN NOSUPERUSER BYPASSRLS PASSWORD '$adminPassword';
                      ELSE
                        ALTER ROLE $admin LOGIN NOSUPERUSER BYPASSRLS PASSWORD '$adminPassword';
                      END IF;
                      IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$app') THEN
                        CREATE ROLE $app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '$appPassword';
                      ELSE
                        ALTER ROLE $app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '$appPassword';
                      END IF;
                    END
                    ${'$'}${'$'};
                    """.trimIndent(),
                )
                val exists = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '$db'").use { it.next() }
                if (!exists) st.execute("CREATE DATABASE $db OWNER $admin ENCODING 'UTF8'")
            }
        }
        pg.getDatabase("postgres", db).connection.use { c ->
            c.createStatement().use { st ->
                st.execute("ALTER DATABASE $db OWNER TO $admin")
                st.execute("GRANT CONNECT ON DATABASE $db TO $app")
                st.execute("GRANT USAGE ON SCHEMA public TO $app")
                st.execute("GRANT ALL ON SCHEMA public TO $admin")
                st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE $admin IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $app")
                st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE $admin IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO $app")
            }
        }
    }
}
