# Progreso — sesión del 17 de noviembre de 2026

Corte de la sesión: 01:00 am CDMX (hora límite 01:45). Rama: `ccr-4c70e3a3-vbrm7i`.

## Resumen

- **Línea de diseño lista para aprobar** en Claude Design (16 artboards):
  https://claude.ai/artifact/SJhxm4CSR3VvNPDXG4SoT7 — **pendiente de tu aprobación**.
- **Fase 1 funcionando de punta a punta** en la web (Wasm) contra el backend real: el comensal entra por
  link o QR, arma su carrito, paga (pago de prueba de Mercado Pago, sandbox) y el pedido aparece en la
  pantalla de cocina en tiempo real. Verificado en un navegador real (ver `docs/capturas/`).
- Las cinco apps existen y navegan con datos de demostración; el backend corre con PostgreSQL (RLS) y Redis.

## Aviso importante sobre esta sesión

A las ~00:31 CDMX los 10 agentes que construían en paralelo se cortaron por el aviso
**"You've hit your 5-hour limit · resets 8:20am (UTC)"**. No entregaron su reporte final, así que lo que
dejaron se integró y se probó a mano después. Consecuencia: lo marcado abajo como "sin ejercitar" compila,
pero nadie lo ha usado todavía.

## Qué está hecho

### Verificado funcionando (probado en esta sesión)

| Pieza | Cómo se verificó |
| --- | --- |
| Compila escritorio (JVM) y web (Wasm, desarrollo) | `:composeApp:compileKotlinDesktop` y `:composeApp:wasmJsBrowserDevelopmentExecutableDistribution` sin errores |
| Backend Ktor arranca, migra y siembra la base | `GET /api/health` → `{"ok":true,"redis":true,"paymentsDemo":true}` |
| Row-level security por tenant | Con el rol de la app, cada tenant ve solo sus filas (Brasa 723 pedidos / Nori 300) y **0 filas sin tenant**; RLS activado y forzado en las 11 tablas con `tenant_id` |
| Menú del comensal (teléfono y escritorio) | Navegador: portada, pestañas fijas, tarjetas con foto, carrito, mesa precargada desde `?mesa=4` |
| Pago de prueba → rastreo | Navegador: "Pagar" → hoja sandbox → "Simular pago aprobado" → pedido A-049 con línea de estado |
| Cocina en tiempo real | Sesión de cocina abierta recibió el pedido A-049 en vivo (WebSocket + Redis), contador 3 → 4 |
| Tematización por restaurante | Brasa (terracota, serif), Nori (oscuro, anguloso), Miga (verde, redondeado): mismo código |
| Panel: Resumen, Marca con vista previa en vivo, Menú, Enlaces y QR | Navegador; los QR se decodificaron y apuntan al link correcto (general y por mesa) |
| Consola del operador: Resumen con métricas | Navegador, con datos del backend |
| Sitio de venta: encabezado y hero | Navegador |
| Login por roles | Operador, dueño y cocina entran; cocina y caja ya pueden leer la marca (antes daba 403, corregido) |

### Escrito y compila, pero **sin ejercitar**

- Panel: Pedidos (incluye reembolso), Pagos (conexión Mercado Pago), Integraciones, Equipo.
- Consola del operador: Restaurantes (crear / suspender / cambiar plan), Planes, Suscripciones, Soporte, Credenciales.
- Sitio de venta: beneficios, planes, preguntas y flujo de registro.
- Comensal: "Mis pedidos", pedir de nuevo, detalle de platillo con modificadores, espera de pago externo.
- Backend: OAuth de Mercado Pago, preferencias con `marketplace_fee`, preapproval, webhooks con firma,
  reembolsos, registro de restaurantes, conectores Lalamove / Uber Eats / Rappi / DiDi Food (interfaces + mocks).
- App de escritorio: compila, pero **nunca se abrió una ventana** (este entorno no tiene pantalla).

### No hecho

- **Mercado Pago real en sandbox**: no hay credenciales de prueba cargadas, así que todo corre en modo demostración
  (`paymentsDemo: true`). Falta `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET`
  (ver `docs/PAGOS_E_INTEGRACIONES.md`).
- **Android**: nunca se compiló (aquí no hay SDK). El target se activa solo al detectar el SDK.
- **iOS**: código y proyecto Xcode escritos (`iosApp/`), nunca compilados ni abiertos en Xcode.
- **Instalador de Windows** (`.msi`/`.exe`): configurado en Gradle, no se generó.
- **Web de producción**: solo build de desarrollo (≈19 MB de Wasm, primera carga lenta). El build optimizado
  necesita Binaryen, que este entorno no pudo descargar.
- **Pruebas automatizadas, revisión de código y de seguridad**: ninguna, por instrucción (esperan tu aprobación).
- **Pull request**: el repositorio estaba vacío y no hay rama base contra la cual abrirlo.

## Pendientes conocidos (detalles)

- El menú muestra "Cerrado" fuera del horario del restaurante, pero **aun así deja pedir** (hay un aviso en el carrito).
  Falta decidir si se bloquea.
- En cocina, un pedido creado con mesa "4" se ve como "En mesa · 4" en vez de "Mesa 4".
- Un mensaje de consola `IllegalStateException: Node 171 not found` apareció una vez al entrar a Panel → Menú;
  la pantalla se pintó bien. Sin diagnosticar.
- El backend siembra restaurantes extra (Comal, Marea, Verde…) para las métricas del operador; no tienen menú ni portada
  y por eso se ocultan del índice de la demo y del sitio.
- Las rutas web usan hash (`/#/r/brasa-demo`); para dominios propios convendrá pasar a rutas de path.
- El botón flotante redondo (casita) es solo una ayuda de revisión para volver al índice de la demo.
- Sin ícono de app en iOS ni Android; en Android el contenido puede quedar bajo las barras del sistema.
- Las cinco apps viven en un solo binario con rutas; separarlas en apps independientes es un cambio de empaquetado.
- Esta sesión corrió en la nube (Linux), no en `D:`. `scripts/windows-setup.ps1` deja proyecto y cachés en `D:`.

## Cómo correrlo y qué ver

### Windows (todo en `D:`)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows-setup.ps1
.\gradlew.bat :composeApp:run                         # escritorio, modo demostración, sin backend
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun # web en el navegador, modo demostración
```

### Con backend (pedidos reales y tiempo real entre dispositivos)

```bash
docker compose up -d      # PostgreSQL 16 + Redis 7   (o scripts/dev-db.sh con un Postgres local)
scripts/dev-run.sh        # compila la web y levanta todo en http://localhost:8080
```

### Recorrido sugerido (contraseña de todas las cuentas: `demo1234`)

1. `http://localhost:8080/#/demo` — índice con las cinco apps.
2. `/#/r/brasa-demo?mesa=4` — agrega platillos, "Ver pedido", "Pagar", "Simular pago aprobado".
3. En otra pestaña `/#/cocina` (cuenta `cocina@brasa.demo`) — el pedido entra solo en "Nuevos".
4. `/#/panel` (`dueno@brasa.demo`) → **Marca**: cambia color, fuente o radios y mira la vista previa en vivo.
5. `/#/r/nori-demo` y `/#/r/miga-demo` — el mismo menú con otra marca.
6. `/#/operador` (`operador@demo.mx`) y `/#/` (sitio de venta).

## Siguiente paso propuesto

1. Tu revisión de la línea de diseño y de las pantallas (dime qué cambiar).
2. Ejercitar lo marcado como "sin ejercitar" y corregir lo que falle.
3. Cargar credenciales de prueba de Mercado Pago y probar OAuth, pago real en sandbox y webhooks.
4. Builds de Android, iOS y Windows.
5. Solo con tu aprobación explícita: pruebas automatizadas, revisión de código, seguridad y optimización.
