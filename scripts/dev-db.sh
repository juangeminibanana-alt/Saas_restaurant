#!/usr/bin/env bash
# Bootstrap local e idempotente de PostgreSQL para desarrollo: roles + base de datos.
#   saas_admin  -> dueño de los objetos del esquema, BYPASSRLS (migraciones, operador, webhooks, seed)
#   saas_app    -> rol de la aplicación, NOSUPERUSER NOBYPASSRLS (RLS forzado por tenant)
# Uso: bash scripts/dev-db.sh   (requiere poder ejecutar psql como el usuario postgres)
set -euo pipefail

DB_NAME="${DB_NAME:-saas}"
DB_ADMIN_USER="${DB_ADMIN_USER:-saas_admin}"
DB_ADMIN_PASSWORD="${DB_ADMIN_PASSWORD:-saas_admin_dev}"
DB_APP_USER="${DB_APP_USER:-saas_app}"
DB_APP_PASSWORD="${DB_APP_PASSWORD:-saas_app_dev}"

if [ -n "${PGHOST:-}" ] || [ "$(id -un)" = "postgres" ]; then
  run_psql() { psql -v ON_ERROR_STOP=1 "$@"; }
else
  run_psql() { su postgres -c "psql -v ON_ERROR_STOP=1 $(printf '%q ' "$@")"; }
fi

run_psql -d postgres <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_ADMIN_USER}') THEN
    CREATE ROLE ${DB_ADMIN_USER} LOGIN NOSUPERUSER BYPASSRLS PASSWORD '${DB_ADMIN_PASSWORD}';
  ELSE
    ALTER ROLE ${DB_ADMIN_USER} LOGIN NOSUPERUSER BYPASSRLS PASSWORD '${DB_ADMIN_PASSWORD}';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_APP_USER}') THEN
    CREATE ROLE ${DB_APP_USER} LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '${DB_APP_PASSWORD}';
  ELSE
    ALTER ROLE ${DB_APP_USER} LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '${DB_APP_PASSWORD}';
  END IF;
END
\$\$;
SQL

if [ "$(run_psql -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'")" != "1" ]; then
  run_psql -d postgres -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_ADMIN_USER} ENCODING 'UTF8'"
fi

run_psql -d "${DB_NAME}" <<SQL
ALTER DATABASE ${DB_NAME} OWNER TO ${DB_ADMIN_USER};
GRANT CONNECT ON DATABASE ${DB_NAME} TO ${DB_APP_USER};
GRANT USAGE ON SCHEMA public TO ${DB_APP_USER};
GRANT ALL ON SCHEMA public TO ${DB_ADMIN_USER};
ALTER DEFAULT PRIVILEGES FOR ROLE ${DB_ADMIN_USER} IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${DB_APP_USER};
ALTER DEFAULT PRIVILEGES FOR ROLE ${DB_ADMIN_USER} IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ${DB_APP_USER};
SQL

echo "OK: base '${DB_NAME}', roles '${DB_ADMIN_USER}' (BYPASSRLS) y '${DB_APP_USER}' (RLS forzado)."
