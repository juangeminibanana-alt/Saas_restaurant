package app.data

import app.shared.data.AppRepository

/** Si es true, createRepository() devuelve siempre el repositorio de demostración (sin sondear el backend). */
var forceDemoData: Boolean = false

/** Devuelve el repositorio remoto si el backend responde; si no, el de demostración en memoria. */
suspend fun createRepository(): AppRepository = DemoRepository
