# Reglas de trabajo para ingeniería en paralelo (léelas completas antes de escribir código)

Producto: SaaS multi-tenant **100% marca blanca** para restaurantes. Sin nombre ni logo de plataforma en ningún
lado; las únicas marcas visibles son los restaurantes ficticios de demostración. Textos de UI en español de México.
Identificadores de código en inglés.

Repo: `/home/user/Saas_restaurant` (Kotlin 2.2.20, Compose Multiplatform 1.9.0, Ktor 3.3.0, Gradle 8.14.3, JDK 21).
- `shared/` — KMP: modelos, DTOs, rutas, `AppRepository`, `DemoData`, utilidades. **Solo lectura** para ti (es el contrato). Léelo:
  `shared/src/commonMain/kotlin/app/shared/{model/Models.kt, api/Api.kt, data/AppRepository.kt, util/Format.kt, util/Time.kt, demo/DemoData.kt}`.
- `composeApp/` — app Compose Multiplatform (desktop JVM, web Wasm, Android, iOS). Contrato del sistema de diseño: `docs/DESIGN_SYSTEM_API.md`.
- `backend/` — Ktor + PostgreSQL (RLS) + Redis + WebSockets.

## Propiedad de archivos
Cada quien tiene rutas propias (te las dan en tu encargo). **Nunca** crees, edites ni borres fuera de tus rutas.
Otros ingenieros están escribiendo sus archivos AL MISMO TIEMPO en el mismo módulo: verás errores de compilación
en archivos ajenos — ignóralos, no los arregles. Si algo ajeno te falta, programa contra el contrato documentado.
No hagas `git add/commit/push` (lo hace el orquestador).

## Compilar: NO ejecutes Gradle para compilar
Hay dos bucles de compilación continuos que comparten resultado con todos:
- App (desktop JVM de `:composeApp`): `bash /tmp/compile/check.sh app <fragmento-de-ruta>`
- Backend (`:backend`): `bash /tmp/compile/check.sh backend <fragmento-de-ruta>`
`check.sh` espera a que termine una compilación que haya EMPEZADO después de tu llamada (1–4 min) y te imprime solo
los errores cuyos archivos contienen tu fragmento (p. ej. `feature/diner`, `ui/components/Buttons`, `server/payments`).
Escribe TODO tu código primero, con cuidado, y compila pocas veces (3–5 ciclos). No ejecutes `./gradlew` tú mismo
salvo que tu encargo lo permita expresamente; jamás `clean`, `--stop`, ni mates daemons.

## Código común (composeApp/src/commonMain)
- Kotlin común puro: nada de `java.*`, `System.*`, `String.format`, `synchronized`, `Dispatchers.IO`, reflexión.
  Hora: `app.shared.util.nowMillis()`. Dinero: `formatMoney(cents)`. Ids: `newId("x")`.
- Compose: `androidx.compose.*` (runtime, foundation, animation, ui, material3 disponible). Sin librerías de navegación ni ViewModel:
  estado con `remember`/`mutableStateOf`/clases de estado propias; corrutinas con `rememberCoroutineScope`/`LaunchedEffect`.
- Recursos: clase `app.resources.Res` (fuentes en `font/`, fotos demo en `files/demo/`).
- Íconos: solo `app.ui.components.AppIcons.*`. Imágenes de platillos: `DishImage(url)`.
- Datos: `val repo = LocalRepo.current` (`app.shell.LocalRepo`). Toda llamada puede lanzar `RepositoryException` → try/catch y
  `Toasts.show(mensaje, Tone.DANGER)` o `ErrorState`.
- Responsivo de verdad: `LocalWindowSize.current` (COMPACT teléfono, MEDIUM tablet, EXPANDED escritorio). Áreas táctiles ≥ 44dp.
- Calidad visual premium: mucho aire, jerarquía tipográfica clara, fotos grandes, animaciones fluidas (transiciones de pantalla,
  microinteracciones, skeletons). Usa SIEMPRE tokens (`AppTheme.colors/type/shapes`, `Space`, `Motion`), nunca colores o tamaños sueltos.
- Modo claro y oscuro deben verse bien los dos.

## Hora límite
Revisa `date -u +%H:%M` al empezar y cada cierto tiempo. **A las 07:02 UTC como máximo debes haber terminado y devuelto tu reporte**,
con CERO errores de compilación en tus rutas (si algo no alcanza, déjalo fuera o simplifícalo, pero que compile).
Sin pruebas automatizadas ni revisiones exhaustivas: lo mínimo para que compile y corra.

## Reporte final (texto breve)
Qué quedó hecho, qué quedó pendiente, y cualquier cosa que el integrador deba saber (firmas que cambiaste, supuestos).
