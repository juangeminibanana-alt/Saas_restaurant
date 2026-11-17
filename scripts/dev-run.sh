#!/usr/bin/env bash
# Levanta todo en local (Linux/macOS): web Wasm + backend Ktor sirviendo la web en http://localhost:8080
# Requiere PostgreSQL y Redis (docker compose up -d, o scripts/dev-db.sh con un Postgres local).
set -euo pipefail
cd "$(dirname "$0")/.."

echo "▶ Compilando la web (Wasm, desarrollo)…"
./gradlew :composeApp:wasmJsBrowserDevelopmentExecutableDistribution --console=plain -q

echo "▶ Empaquetando el backend…"
./gradlew :backend:installDist --console=plain -q

export WEB_DIST="${WEB_DIST:-$PWD/composeApp/build/dist/wasmJs/developmentExecutable}"
export PORT="${PORT:-8080}"
echo "▶ Backend + web en http://localhost:$PORT  (índice de la demo: http://localhost:$PORT/#/demo)"
exec backend/build/install/backend/bin/backend
