package app.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import app.alerts.AlertPrefs
import app.alerts.OrderAlertService
import com.whitelabel.restaurantes.R

/** Personal: escuchar pedidos nuevos en segundo plano. Solo en la app de cocina o con rol Cocina. */
actual fun syncStaffAlerts(baseUrl: String?, token: String?, role: String?) {
    val context = AndroidPlatform.appContext ?: return
    val kitchenApp = runCatching { context.getString(R.string.start_route) == "/cocina" }.getOrDefault(false)
    val enabled = !baseUrl.isNullOrBlank() && !token.isNullOrBlank() && (kitchenApp || role == "KITCHEN")
    context.getSharedPreferences(AlertPrefs.FILE, Context.MODE_PRIVATE).edit().apply {
        if (enabled) {
            putString(AlertPrefs.BASE, baseUrl)
            putString(AlertPrefs.TOKEN, token)
        } else {
            remove(AlertPrefs.TOKEN)
        }
    }.apply()
    refreshAlertService(context)
}

/** Comensal: seguir en segundo plano el pedido en curso (null = dejar de seguir). */
actual fun syncOrderAlerts(baseUrl: String?, orderId: String?, orderNumber: String?) {
    val context = AndroidPlatform.appContext ?: return
    val enabled = !baseUrl.isNullOrBlank() && !orderId.isNullOrBlank()
    context.getSharedPreferences(AlertPrefs.FILE, Context.MODE_PRIVATE).edit().apply {
        if (enabled) {
            putString(AlertPrefs.BASE, baseUrl)
            putString(AlertPrefs.ORDER_ID, orderId)
            putString(AlertPrefs.ORDER_NUMBER, orderNumber)
        } else {
            remove(AlertPrefs.ORDER_ID)
            remove(AlertPrefs.ORDER_NUMBER)
        }
    }.apply()
    refreshAlertService(context)
}

private fun refreshAlertService(context: Context) {
    val prefs = context.getSharedPreferences(AlertPrefs.FILE, Context.MODE_PRIVATE)
    val needed = prefs.getString(AlertPrefs.BASE, null) != null &&
        (prefs.getString(AlertPrefs.TOKEN, null) != null || prefs.getString(AlertPrefs.ORDER_ID, null) != null)
    val intent = Intent(context, OrderAlertService::class.java)
    runCatching {
        if (needed) {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        } else {
            // No se llama a stopService: si hay un arranque en curso, Android cerraría la app. El servicio se detiene
            // solo (si aún no existe, su arranque pendiente lee las preferencias vacías y se apaga).
            OrderAlertService.instance?.stopFromApp()
        }
    }
}
