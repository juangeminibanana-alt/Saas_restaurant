# Convierte esta computadora en el servidor de las apps (Windows). NO necesita Docker:
# el servidor trae su propia base de datos (PostgreSQL embebido). Solo requiere Java 17 o superior.
# Uso, desde la carpeta del proyecto:  powershell -ExecutionPolicy Bypass -File scripts\servidor.ps1
# Deja esta ventana abierta mientras pruebas. Ctrl+C para detener.

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "No encuentro Java. Cierra esta ventana, abre una nueva de PowerShell y vuelve a ejecutar." -ForegroundColor Red
    Write-Host "Si sigue igual, instala Java con:  winget install --id EclipseAdoptium.Temurin.21.JDK -e" -ForegroundColor Red
    exit 1
}

# Todo en D: (cachés y datos) si existe ese disco; si no, dentro de la carpeta del proyecto.
if (Test-Path "D:\") {
    $Caches = "D:\saas-restaurantes-cache"
    New-Item -ItemType Directory -Force -Path "$Caches\gradle", "$Caches\pgdata" | Out-Null
    $env:GRADLE_USER_HOME = "$Caches\gradle"
    $env:EMBEDDED_DB_DIR = "$Caches\pgdata"
}
$env:EMBEDDED_DB = "true"

Write-Host "1/2  Abriendo el puerto 8080 en el firewall de Windows..." -ForegroundColor Cyan
try {
    if (-not (Get-NetFirewallRule -DisplayName "Restaurantes 8080" -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName "Restaurantes 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow | Out-Null
    }
    Write-Host "     Listo."
} catch {
    Write-Host "     No pude abrirlo desde aquí. Si el teléfono no conecta, abre PowerShell COMO ADMINISTRADOR y ejecuta una vez:" -ForegroundColor Yellow
    Write-Host "     New-NetFirewallRule -DisplayName 'Restaurantes 8080' -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow" -ForegroundColor Yellow
}

$ips = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.InterfaceAlias -notlike "*vEthernet*" -and $_.InterfaceAlias -notlike "*WSL*" -and $_.InterfaceAlias -notlike "*Loopback*" } |
    Select-Object -ExpandProperty IPAddress

Write-Host ""
Write-Host "En cada app del teléfono toca el botón de engrane y escribe esta dirección (teléfono en el mismo Wi-Fi):" -ForegroundColor Green
foreach ($ip in $ips) { Write-Host "     http://$($ip):8080" -ForegroundColor Green }
Write-Host ""
Write-Host "2/2  Arrancando el servidor. La primera vez tarda varios minutos (descarga dependencias)." -ForegroundColor Cyan
Write-Host "     Está listo cuando veas 'Responding at http://0.0.0.0:8080'. Deja esta ventana abierta; Ctrl+C para detener."
Write-Host "     Si Windows pregunta si permites el acceso a la red para Java o PostgreSQL, acepta."
.\gradlew.bat :backend:run
