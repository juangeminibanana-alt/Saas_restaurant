# Convierte esta computadora en el servidor de las apps (Windows).
# No necesita Docker ni compilar nada: usa el servidor ya compilado de dist\servidor y su propia
# base de datos (PostgreSQL embebido). Solo requiere Java 17 o superior.
# Uso, desde la carpeta del proyecto:  powershell -ExecutionPolicy Bypass -File scripts\servidor.ps1
# Deja esta ventana abierta mientras pruebas. Ctrl+C para detener.
# (Mensajes sin acentos a proposito: PowerShell 5.1 los muestra mal.)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "No encuentro Java. Cierra esta ventana, abre una nueva de PowerShell y vuelve a ejecutar." -ForegroundColor Red
    Write-Host "Si sigue igual, instala Java con:  winget install --id EclipseAdoptium.Temurin.21.JDK -e" -ForegroundColor Red
    exit 1
}

# Datos y caches en D: si existe ese disco; si no, dentro de la carpeta del proyecto.
$JavaOpts = @()
if (Test-Path "D:\") {
    $Caches = "D:\saas-restaurantes-cache"
    New-Item -ItemType Directory -Force -Path "$Caches\gradle", "$Caches\pgdata", "$Caches\tmp", "$Caches\pg-bin" | Out-Null
    $env:GRADLE_USER_HOME = "$Caches\gradle"
    $env:EMBEDDED_DB_DIR = "$Caches\pgdata"
    # Los archivos temporales y los binarios de la base de datos tambien van a D: (C: puede estar lleno).
    $JavaOpts = @("-Djava.io.tmpdir=$Caches\tmp", "-Dot.epg.working-dir=$Caches\pg-bin")
    # Borra los restos de un intento anterior que se quedo a medias en C:.
    $restos = Join-Path $env:TEMP "embedded-pg"
    if (Test-Path $restos) { Remove-Item -Recurse -Force $restos -ErrorAction SilentlyContinue }
}
$env:EMBEDDED_DB = "true"

Write-Host "1/2  Abriendo el puerto 8080 en el firewall de Windows..." -ForegroundColor Cyan
try {
    if (-not (Get-NetFirewallRule -DisplayName "Restaurantes 8080" -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName "Restaurantes 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow | Out-Null
    }
    Write-Host "     Listo."
} catch {
    Write-Host "     No pude abrirlo desde aqui. Si el telefono no conecta, abre PowerShell COMO ADMINISTRADOR y ejecuta una vez:" -ForegroundColor Yellow
    Write-Host "     New-NetFirewallRule -DisplayName 'Restaurantes 8080' -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow" -ForegroundColor Yellow
}

$ips = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.InterfaceAlias -notlike "*vEthernet*" -and $_.InterfaceAlias -notlike "*WSL*" -and $_.InterfaceAlias -notlike "*Loopback*" } |
    Select-Object -ExpandProperty IPAddress

Write-Host ""
Write-Host "En cada app del telefono toca el boton de engrane y escribe esta direccion (telefono en el mismo Wi-Fi):" -ForegroundColor Green
foreach ($ip in $ips) { Write-Host "     http://$($ip):8080" -ForegroundColor Green }
Write-Host ""
Write-Host "2/2  Arrancando el servidor..." -ForegroundColor Cyan
Write-Host "     Esta listo cuando veas 'Responding at http://0.0.0.0:8080'. Deja esta ventana abierta; Ctrl+C para detener."
Write-Host "     Si Windows pregunta si permites el acceso a la red para Java o PostgreSQL, acepta."
Write-Host ""

if (Test-Path "dist\servidor\lib") {
    # Servidor ya compilado: no descarga nada.
    java @JavaOpts -cp "dist\servidor\lib\*" server.MainKt
} else {
    # Sin el servidor compilado: se compila con Gradle (descarga dependencias la primera vez).
    .\gradlew.bat :backend:run
}
