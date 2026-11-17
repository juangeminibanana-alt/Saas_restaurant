# Progreso — sesión del 17 de noviembre de 2026

Corte de la sesión: 01:30 am CDMX (hora límite 01:45). Rama: `ccr-4c70e3a3-vbrm7i`.

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
| Backend por API (20 llamadas con `curl`) | Operador: métricas, listar, **crear** y **suspender** restaurante (el menú público del suspendido responde 403); permisos por rol (dueño no entra a operador, caja no edita marca, sin token 401); Mercado Pago en modo demostración (estado y conectar); integraciones; pedido simulado de Uber Eats que entra a cocina; registro de restaurante; planes; estadísticas; cambio de estado y reembolso de un pedido |

### Escrito y compila, pero **sin ejercitar**

- Panel: Pedidos (incluye reembolso), Pagos (conexión Mercado Pago), Integraciones, Equipo.
- Consola del operador: Restaurantes (crear / suspender / cambiar plan), Planes, Suscripciones, Soporte, Credenciales.
- Sitio de venta: beneficios, planes, preguntas y flujo de registro.
- Comensal: "Mis pedidos", pedir de nuevo, detalle de platillo con modificadores, espera de pago externo.
- Backend contra Mercado Pago real: OAuth, preferencias con `marketplace_fee`, preapproval, validación de firma
  del webhook y reembolso real (en modo demostración sí responden; ver tabla de arriba). Cliente de Lalamove y
  conectores de Uber Eats / Rappi / DiDi Food: interfaces y mocks, sin llamadas reales.
- Las **pantallas** del panel y del operador que usan esos endpoints (la API sí quedó probada).
- App de escritorio: compila, pero **nunca se abrió una ventana** (este entorno no tiene pantalla).

### No hecho

- **Mercado Pago real en sandbox**: no hay credenciales de prueba cargadas, así que todo corre en modo demostración
  (`paymentsDemo: true`). Falta `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET`
  (ver `docs/PAGOS_E_INTEGRACIONES.md`).
- **Android en dispositivo**: los APK compilan pero no se han ejecutado en teléfono ni emulador (ver sección Android).
- **iOS**: código y proyecto Xcode escritos (`iosApp/`), nunca compilados ni abiertos en Xcode.
- **Instalador de Windows** (`.msi`/`.exe`): configurado en Gradle, no se generó.
- **Web de producción**: solo build de desarrollo (≈19 MB de Wasm, primera carga lenta). El build optimizado
  necesita Binaryen, que este entorno no pudo descargar.
- **Pruebas automatizadas, revisión de código y de seguridad**: ninguna, por instrucción (esperan tu aprobación).
- **Pull request**: el repositorio estaba vacío y no hay rama base contra la cual abrirlo.

## Android: seis APK (agregado al final de la sesión)

Están en `dist/apk/` (debug, ~18 MB cada uno). Se regeneran con el SDK de Android instalado y
`./gradlew :composeApp:assembleDebug` (un sabor de Gradle por app).

| APK | Nombre en el teléfono | Abre en | Id de la app |
| --- | --- | --- | --- |
| `comensal.apk` | Menú | Menú de Brasa Demo | `com.whitelabel.restaurantes.comensal` |
| `cocina.apk` | Cocina | Pantalla de cocina | `com.whitelabel.restaurantes.cocina` |
| `panel.apk` | Panel | Panel del restaurante | `com.whitelabel.restaurantes.panel` |
| `operador.apk` | Operador | Consola del operador | `com.whitelabel.restaurantes.operador` |
| `sitio.apk` | Sitio de venta | Sitio con planes y registro | `com.whitelabel.restaurantes.sitio` |
| `demo-todo-en-uno.apk` | Demo restaurantes | Índice con las cinco apps | `com.whitelabel.restaurantes` |

**Estado real:**

- Los seis **compilan** y están bien armados (firma de depuración, fotos y fuentes dentro).
  **Ninguno se ha abierto en un teléfono ni en un emulador.**
- **Las cinco apps separadas no comparten datos por sí solas.** Sin servidor, cada una usa datos de demostración
  en su propia memoria: el pedido del comensal no aparece en la app de cocina.
- Para conectarlas: botón de engrane dentro de cada app → dirección del servidor (por ejemplo
  `http://192.168.1.50:8080`, misma red Wi‑Fi). En un emulador en la misma computadora que el servidor funciona
  sin configurar nada (`10.0.2.2:8080`). **El botón de engrane y la conexión desde Android no se han probado.**
- `demo-todo-en-uno.apk` permite probar el flujo completo sin servidor porque todo vive en una sola app.
- El botón "atrás" del sistema regresa a la pantalla anterior, cierra hojas y vuelve del carrito al menú
  (compila; sin probar en dispositivo).

**Flujo en modo demostración, verificado en navegador a tamaño de teléfono (390×844, sin backend):**
índice → menú de Brasa → 2 platillos → pagar → pago de prueba → rastreo → cocina recibe el pedido en "Nuevos" →
"Aceptar" lo pasa a "En preparación" → "Mis pedidos" del comensal muestra "En preparación". Se corrigió un fallo:
el historial del comensal se perdía al salir y volver a entrar al menú.

**Detalles visuales vistos a tamaño de teléfono (sin corregir, no bloquean):**

- Entrando al menú sin `?mesa=`, "Pagar" pide escribir la mesa antes de continuar.
- El botón flotante redondo tapa texto en carrito, rastreo y en la hoja de pago.
- Panel: etiquetas de los indicadores cortadas ("Ventas de…") y el botón "Guardar cambios" cortado.
- Consola del operador: el sexto elemento de la barra inferior queda cortado.
- Cocina: un pedido nuevo cae al final de "Nuevos" y hay que desplazarse para verlo.
- Sin ícono de app; la barra de estado puede quedar con íconos oscuros sobre fondo oscuro al cambiar de tema.

## Servidor en una PC sin Docker (agregado a las 02:25 am CDMX)

El backend puede traer su propia base de datos: con `EMBEDDED_DB=true` arranca un PostgreSQL real embebido
(mismo motor, con row-level security) y, si no hay Redis, usa un bus en memoria. Solo hace falta Java.

- Windows: `powershell -ExecutionPolicy Bypass -File scripts\servidor.ps1` (abre el puerto 8080, muestra la
  dirección para el engrane de las apps y arranca el servidor). **Sin probar en Windows.**
- Verificado en Linux en esta sesión: arranca en ~4 s sin Docker ni Redis, migra y siembra, el flujo
  pedido → pago de prueba → cocina funciona por API, y los datos sobreviven a un reinicio.
- Docker (`docker compose up -d`) sigue siendo una opción, ya no un requisito.

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
- Sin ícono de app en iOS ni Android.
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
