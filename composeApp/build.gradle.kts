import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val isMacHost = System.getProperty("os.name").lowercase().contains("mac")

// The Android target is only configured when an Android SDK can be found, so the
// desktop / web / backend builds keep working on machines without it.
val hasAndroidSdk: Boolean = run {
    val fromEnv = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        .mapNotNull { System.getenv(it) }
        .any { it.isNotBlank() && File(it).isDirectory }
    val localProps = rootProject.file("local.properties")
    val fromLocalProps = localProps.isFile &&
        Properties().apply { localProps.inputStream().use { load(it) } }
            .getProperty("sdk.dir")?.let { File(it).isDirectory } == true
    fromEnv || fromLocalProps
}

if (hasAndroidSdk) {
    apply(plugin = "com.android.application")
}

kotlin {
    jvm("desktop")

    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    if (hasAndroidSdk) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }

    if (isMacHost) {
        listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation(compose.components.resources)
            implementation(libs.material.icons.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.okhttp)
            }
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        if (isMacHost) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        if (hasAndroidSdk) {
            getByName("androidMain").dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}

if (hasAndroidSdk) {
    extensions.configure<ApplicationExtension>("android") {
        namespace = "com.whitelabel.restaurantes"
        compileSdk = 36
        defaultConfig {
            applicationId = "com.whitelabel.restaurantes"
            minSdk = 24
            targetSdk = 36
            versionCode = 1
            versionName = "1.0.0"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        packaging {
            resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "app.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "restaurantes"
            packageVersion = "1.0.0"
        }
    }
}
