package app.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import com.whitelabel.restaurantes.R

private var nextNotificationId = 1000

/**
 * Aviso al usuario: sonido propio de la app + notificación del sistema.
 * El sonido lo reproduce la app (suena aunque no se haya dado permiso de notificaciones); el canal de la
 * notificación va sin sonido para que no suene doble, pero vibra.
 */
actual fun notifyUser(kind: NotifyKind, title: String, body: String, dedupKey: String?) {
    val context = AndroidPlatform.appContext ?: return
    // La pantalla abierta y el servicio en segundo plano escuchan el mismo evento: se avisa una sola vez.
    if (dedupKey != null && !app.alerts.AlertDedup.firstTime(dedupKey)) return
    runCatching { playSound(context, kind) }
    runCatching { postNotification(context, kind, title, body) }
}

private fun playSound(context: Context, kind: NotifyKind) {
    val sound = when (kind) {
        NotifyKind.NEW_ORDER -> R.raw.pedido_nuevo
        NotifyKind.ORDER_READY -> R.raw.pedido_listo
        NotifyKind.ORDER_UPDATE -> R.raw.pedido_aviso
    }
    val player = MediaPlayer.create(context, sound) ?: return
    player.setOnCompletionListener { it.release() }
    player.start()
}

private fun postNotification(context: Context, kind: NotifyKind, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val (channelId, channelName) = when (kind) {
        NotifyKind.NEW_ORDER -> "pedidos_nuevos" to "Pedidos nuevos"
        NotifyKind.ORDER_READY -> "pedido_listo" to "Pedido listo"
        NotifyKind.ORDER_UPDATE -> "pedido_avance" to "Avance del pedido"
    }
    if (Build.VERSION.SDK_INT >= 26) {
        val importance = if (kind == NotifyKind.ORDER_UPDATE) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = if (kind == NotifyKind.NEW_ORDER) longArrayOf(0, 220, 110, 220, 110, 380) else longArrayOf(0, 160, 90, 160)
        }
        manager.createNotificationChannel(channel)
    }

    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pending = launch?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    @Suppress("DEPRECATION")
    val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channelId) else Notification.Builder(context)
    val notification = builder
        .setSmallIcon(R.drawable.ic_notif)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .apply { if (pending != null) setContentIntent(pending) }
        .build()
    manager.notify(nextNotificationId++, notification)
}
