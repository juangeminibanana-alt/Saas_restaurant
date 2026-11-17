plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("server.MainKt")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.postgresql)
    // PostgreSQL embebido: el servidor corre en una PC con solo Java (sin Docker). Se activa con EMBEDDED_DB=true.
    implementation("io.zonky.test:embedded-postgres:2.2.2")
    implementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:16.16.0"))
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-windows-amd64")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-amd64")
    runtimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8")
    implementation(libs.hikari)
    implementation(libs.lettuce)
    implementation(libs.logback)
    implementation(libs.kotlinx.coroutines.core)
}
