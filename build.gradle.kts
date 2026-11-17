import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
}

// Node.js / Yarn: use the tools installed on the machine instead of letting the Kotlin
// plugin download them (Yarn comes from GitHub releases, which CI/sandboxes often block).
// Requires `node` (>= 22) and `yarn` (1.x) on PATH. Pass -PkotlinToolsDownload=true to
// restore the default "download everything" behaviour.
val kotlinToolsDownload = providers.gradleProperty("kotlinToolsDownload").map { it.toBoolean() }.getOrElse(false)

allprojects {
    plugins.withType<WasmNodeJsPlugin>().configureEach {
        extensions.configure<WasmNodeJsEnvSpec> { download.set(kotlinToolsDownload) }
    }
    plugins.withType<NodeJsPlugin>().configureEach {
        extensions.configure<NodeJsEnvSpec> { download.set(kotlinToolsDownload) }
    }
}

plugins.withType<WasmYarnPlugin>().configureEach {
    extensions.configure<WasmYarnRootEnvSpec> { download.set(kotlinToolsDownload) }
    extensions.configure<WasmYarnRootExtension> {
        yarnLockMismatchReport = YarnLockMismatchReport.WARNING
        yarnLockAutoReplace = true
    }
}
plugins.withType<YarnPlugin>().configureEach {
    extensions.configure<YarnRootEnvSpec> { download.set(kotlinToolsDownload) }
    extensions.configure<YarnRootExtension> {
        yarnLockMismatchReport = YarnLockMismatchReport.WARNING
        yarnLockAutoReplace = true
    }
}
