# Convierte esta computadora en el servidor de las apps (Windows).
# Uso, desde la carpeta del proyecto:  powershell -ExecutionPolicy Bypass -File scripts\servidor.ps1
# Requiere: Docker Desktop encendido y JDK 17 o superior. Deja esta ventana abierta mientras pruebas.

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

# Cachés de Gradle en D: para no llenar C:
$Caches = "D:\saas-restaurantes-cache"
if (Test-Path "D:\") {
    New-Item -ItemType Directory -Force -Path "$Caches\gradle" | Out-Null
    $env:GRADLE_USER_HOME = "$Caches\gradle"
}

foreach ($cmd in "docker", "java") {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Host "Falta instalar '$cmd'. Revisa los pasos de instalación y vuelve a ejecutar." -ForegroundColor Red
        exit 1
    }
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker Desktop no está encendido. Ábrelo, espera a que diga 'Engine running' y vuelve a ejecutar." -ForegroundColor Red
    exit 1
}

Write-Host "1/3  Levantando la base de datos (PostgreSQL) y Redis..." -ForegroundColor Cyan
docker compose up -d
$pg = docker compose ps -q postgres
for ($i = 0; $i -lt 60; $i++) {
    $estado = docker inspect --format "{{.State.Health.Status}}" $pg 2>$null
    if ($estado -eq "healthy") { break }
    Start-Sleep -Seconds 2
}

Write-Host "2/3  Abriendo el puerto 8080 en el firewall de Windows..." -ForegroundColor Cyan
try {
    if (-not (Get-NetFirewallRule -DisplayName "Restaurantes 8080" -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName "Restaurantes 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow | Out-Null
    }
} catch {
    Write-Host "     No pude abrirlo (hace falta PowerShell como administrador). Si el teléfono no conecta, ejecuta esto una vez como administrador:" -ForegroundColor Yellow
    Write-Host "     New-NetFirewallRule -DisplayName 'Restaurantes 8080' -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow" -ForegroundColor Yellow
}

$ips = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.InterfaceAlias -notlike "*vEthernet*" -and $_.InterfaceAlias -notlike "*WSL*" -and $_.InterfaceAlias -notlike "*Loopback*" } |
    Select-Object -ExpandProperty IPAddress

Write-Host ""
Write-Host "En cada app del teléfono toca el botón de engrane y escribe esta dirección (teléfono en el mismo Wi-Fi):" -ForegroundColor Green
foreach ($ip in $ips) { Write-Host "     http://$($ip):8080" -ForegroundColor Green }
Write-Host ""
Write-Host "3/3  Arrancando el servidor. La primera vez tarda varios minutos (descarga dependencias)." -ForegroundColor Cyan
Write-Host "     Está listo cuando veas 'Responding at http://0.0.0.0:8080'. Deja esta ventana abierta; Ctrl+C para detener."
.\gradlew.bat :backend:run
