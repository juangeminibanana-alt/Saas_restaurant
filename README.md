# SaaS marca blanca para restaurantes

Plataforma multi-tenant **100% marca blanca**: sin nombre, logo ni identidad propia visible. Cada restaurante ve
únicamente su marca (logo, color, tipografía, radios, dominio). Un solo código Kotlin para Android, iOS, Windows
(escritorio) y web (Wasm), con backend Ktor.

> Estado y pendientes al día: [`PROGRESO.md`](PROGRESO.md).

## Qué incluye

| App | Ruta | Usuario |
| --- | --- | --- |
| Sitio de venta (marca neutra configurable) | `/` | Prospectos |
| Menú digital del comensal (link y QR) | `/r/{slug}` · `/r/{slug}?mesa=4` | Cliente final |
| Pantalla de cocina en tiempo real | `/cocina` | Cocina |
| Panel del restaurante | `/panel` | Dueño y staff |
| Consola del operador | `/operador` | Dueño del SaaS |
| Índice de la demo | `/demo` | Revisión |

En web las rutas van con hash (`http://localhost:8080/#/r/brasa-demo`); en escritorio se pasan como argumento.

## Arquitectura

```
shared/      KMP · modelos, DTOs, rutas HTTP, AppRepository, DemoData (contrato único cliente ⇄ servidor)
composeApp/  Compose Multiplatform · sistema de diseño tematizable + las cinco apps
             targets: desktop (JVM/Windows), wasmJs (web), android, ios
backend/     Ktor · PostgreSQL con row-level security por tenant · Redis (pub/sub y colas) · WebSockets
             Mercado Pago (OAuth por restaurante, marketplace_fee, preapproval, webhooks)
             conectores preparados: Lalamove, Uber Eats, Rappi, DiDi Food
iosApp/      proyecto Xcode (solo compila en macOS)
docs/        línea de diseño → tokens (DESIGN_SYSTEM_API.md), pagos e integraciones, móvil
```

La app tiene dos fuentes de datos intercambiables detrás de `AppRepository`:

- **Demostración local** (`DemoRepository`): todo en memoria con restaurantes ficticios. La app corre sola, sin backend.
- **Backend real** (`RemoteRepository`): HTTP + WebSocket. Se elige automáticamente si `/api/health` responde.

## Cómo correrlo

### Windows (todo en `D:`)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows-setup.ps1   # clona en D:\saas-restaurantes y pone cachés en D:
.\gradlew.bat :composeApp:run                                       # app de escritorio en modo demostración
.\gradlew.bat :composeApp:run --args="/r/brasa-demo"                # abre directo el menú de un restaurante
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun               # versión web en el navegador
```

### Backend completo (pedidos reales, tiempo real entre dispositivos)

```bash
docker compose up -d            # PostgreSQL 16 + Redis 7
./gradlew :backend:run          # http://localhost:8080  (siembra los datos de demostración al primer arranque)
# o todo junto (web + backend en un solo puerto):
scripts/dev-run.sh
```

### Cuentas de demostración (contraseña `demo1234`)

| Rol | Correo |
| --- | --- |
| Operador del SaaS | `operador@demo.mx` |
| Dueño · Brasa Demo | `dueno@brasa.demo` |
| Gerente · Brasa Demo | `gerente@brasa.demo` |
| Caja · Brasa Demo | `caja@brasa.demo` |
| Cocina · Brasa Demo | `cocina@brasa.demo` |
| Dueño · Nori Demo / Miga Demo | `dueno@nori.demo` / `dueno@miga.demo` |

Restaurantes ficticios: **Brasa Demo** (parrilla, terracota, serif), **Nori Demo** (sushi, oscuro, anguloso),
**Miga Demo** (panadería, verde, redondeado). El mismo código, tres marcas.

## Tematización por tenant

`BrandTheme` (color primario, fuente display y de cuerpo, estilo de radios, modo claro/oscuro, logo y portada) se
guarda por restaurante como JSON y `AppTheme(brand)` lo traduce a tokens de Compose en tiempo de ejecución.
No hay nada de marca en el código: cambiarla es editar datos desde el panel.

## Créditos

Fotos de demostración de Unsplash (Licencia Unsplash) y fuentes bajo SIL OFL — ver `docs/CREDITOS.md`.
