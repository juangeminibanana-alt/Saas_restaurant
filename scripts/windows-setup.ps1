# Prepara el proyecto en Windows dejando TODO en el disco D: (proyecto, cachés y SDKs).
# Uso (PowerShell):  powershell -ExecutionPolicy Bypass -File scripts\windows-setup.ps1
# Si aún no has clonado el repo, copia este archivo y ejecútalo: lo clona en D:\saas-restaurantes.

$ErrorActionPreference = "Stop"
$Root      = "D:\saas-restaurantes"
$Caches    = "D:\saas-restaurantes-cache"
$RepoUrl   = "https://github.com/juangeminibanana-alt/Saas_restaurant.git"
$Branch    = "ccr-4c70e3a3-vbrm7i"

New-Item -ItemType Directory -Force -Path $Caches, "$Caches\gradle", "$Caches\konan", "$Caches\android-sdk" | Out-Null

# Cachés y dependencias fuera de C:
[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", "$Caches\gradle", "User")
[Environment]::SetEnvironmentVariable("KONAN_DATA_DIR",   "$Caches\konan", "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME",     "$Caches\android-sdk", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "$Caches\android-sdk", "User")
$env:GRADLE_USER_HOME = "$Caches\gradle"
$env:KONAN_DATA_DIR   = "$Caches\konan"
$env:ANDROID_HOME     = "$Caches\android-sdk"
$env:ANDROID_SDK_ROOT = "$Caches\android-sdk"

if (-not (Test-Path "$Root\.git")) {
    git clone --branch $Branch $RepoUrl $Root
}
Set-Location $Root

Write-Host ""
Write-Host "Listo. Proyecto en $Root, cachés en $Caches (nada en C:)." -ForegroundColor Green
Write-Host ""
Write-Host "Ver la app de escritorio (modo demostración, sin backend):"
Write-Host "    .\gradlew.bat :composeApp:run"
Write-Host "Ver la versión web (Wasm) en http://localhost:8080 :"
Write-Host "    .\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun"
Write-Host "Backend completo (requiere Docker Desktop para PostgreSQL y Redis):"
Write-Host "    docker compose up -d ; .\gradlew.bat :backend:run"
Write-Host ""
Write-Host "Android: instala el SDK en $Caches\android-sdk (Android Studio > SDK Manager > ubicación del SDK)"
Write-Host "y vuelve a sincronizar; el target Android se activa solo al detectar el SDK."
Write-Host "iOS: el código está listo en composeApp/src/iosMain e iosApp/, pero solo compila en macOS."
