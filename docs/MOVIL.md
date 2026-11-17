# Apps móviles (Android e iOS)

La app móvil es el mismo módulo `composeApp` (Compose Multiplatform). Requisito común: **JDK 21** (mínimo 17).

## Android

El target Android solo se activa cuando Gradle detecta un SDK de Android (`ANDROID_HOME`, `ANDROID_SDK_ROOT`
o `sdk.dir` en `local.properties`). Sin SDK, el resto del proyecto sigue compilando normal.

1. Instala Android Studio (o solo las *command-line tools*) y, desde el SDK Manager, estos paquetes:
   **Android SDK Platform 36**, **Build-Tools 36** y **Platform-Tools**.
2. Define `ANDROID_HOME` apuntando al SDK:
   - **Windows — instálalo en D:** elige `D:\Android\Sdk` como ubicación del SDK y después, en una terminal:
     ```bat
     setx ANDROID_HOME "D:\Android\Sdk"
     ```
     Abre una terminal nueva para que tome el valor. (Opcional, para no llenar C:: `setx GRADLE_USER_HOME "D:\gradle"`
     y `setx ANDROID_AVD_HOME "D:\Android\avd"`.)
   - macOS: `export ANDROID_HOME="$HOME/Library/Android/sdk"` · Linux: `export ANDROID_HOME="$HOME/Android/Sdk"`
   - Alternativa sin variable de entorno: archivo `local.properties` en la raíz con `sdk.dir=D\:\\Android\\Sdk`.
3. Compila:
   ```bash
   ./gradlew :composeApp:assembleDebug        # Windows: gradlew.bat :composeApp:assembleDebug
   ```
   El APK queda en `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.
   Para instalarlo en un emulador o teléfono conectado: `./gradlew :composeApp:installDebug`.

Servidor en desarrollo: el emulador llega a tu máquina por `http://10.0.2.2:8080` (valor por defecto en
`Platform.android.kt`). En un teléfono físico usa la IP de tu máquina en la red local. El manifiesto permite HTTP
sin cifrar (`usesCleartextTraffic`) solo para desarrollo; en producción usa HTTPS y quítalo.

## iOS (solo en Mac)

Los targets de iOS solo se declaran cuando Gradle corre en macOS. Necesitas Xcode 16 o más reciente y un JDK instalado.

1. Abre `iosApp/iosApp.xcodeproj` en Xcode.
2. En `iosApp/Configuration/Config.xcconfig` escribe tu `TEAM_ID` (el Team ID de tu cuenta de Apple Developer).
   Para el simulador puede quedarse vacío; para un iPhone físico es obligatorio.
3. Elige un simulador o dispositivo y presiona **Run**.

La fase de build *Compile Kotlin Framework* ejecuta
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` y enlaza el framework estático `ComposeApp`;
la primera compilación tarda varios minutos. Si Xcode no encuentra Java, instala un JDK o define `JAVA_HOME`.

Servidor en desarrollo: el simulador usa `http://localhost:8080`; en un iPhone físico usa la IP de tu Mac.
`Info.plist` solo permite HTTP hacia la red local (`NSAllowsLocalNetworking`); en producción se requiere HTTPS.

Pendiente antes de publicar: el ícono (`Assets.xcassets/AppIcon`) está vacío; agrega un PNG de 1024×1024.
Android tampoco tiene ícono propio todavía (usa el genérico del sistema).

## Builds de marca blanca por restaurante (a futuro)

Hoy se genera una sola app genérica ("Menú", `com.whitelabel.restaurantes`). Para publicar una app por
restaurante, cada build necesita tres cosas propias: **identificador**, **nombre** e **ícono**.

**Android (propiedades de Gradle).** Leer en `composeApp/build.gradle.kts` propiedades con valor por defecto:

```kotlin
val tenantAppId = providers.gradleProperty("tenant.appId").getOrElse("com.whitelabel.restaurantes")
val tenantAppName = providers.gradleProperty("tenant.appName").getOrElse("Menú")
val tenantSlug = providers.gradleProperty("tenant.slug").getOrElse("default")
defaultConfig {
    applicationId = tenantAppId
    resValue("string", "app_name", tenantAppName)   // y quitar app_name de strings.xml
}
sourceSets["main"].res.srcDir("brands/${tenantSlug}/android/res")   // íconos del restaurante
```

```bash
./gradlew :composeApp:assembleRelease -Ptenant.appId=com.ejemplo.mirestaurante -Ptenant.appName="Mi Restaurante" -Ptenant.slug=mi-restaurante
```

Cada restaurante lleva además su propia llave de firma (`signingConfigs`). El `namespace` no cambia.

**iOS (xcconfig).** Un archivo por restaurante, por ejemplo `iosApp/Configuration/mi-restaurante.xcconfig`, con
`TEAM_ID`, `BUNDLE_ID`, `APP_NAME` (nombre interno, solo ASCII), `APP_DISPLAY_NAME` (nombre visible bajo el ícono)
y `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon-mi-restaurante` apuntando a un ícono propio dentro de
`Assets.xcassets`. Se compila sin tocar el proyecto:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -xcconfig iosApp/Configuration/mi-restaurante.xcconfig archive
```

En ambos casos conviene que la misma configuración fije el restaurante con el que abre la app, para que cada
build muestre solo su marca.
