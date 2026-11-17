package app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.platform.AndroidPlatform
import com.whitelabel.restaurantes.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPlatform.appContext = applicationContext
        // Android 13+: pide permiso para mostrar notificaciones (el sonido de la app suena aunque se niegue).
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        // La pantalla de cocina no debe apagarse sola.
        if (getString(R.string.start_route) == "/cocina") {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setContent {
            // Cada APK (sabor de Gradle) define con qué app abre y si es una app independiente.
            App(
                initialRoute = getString(R.string.start_route),
                standalone = getString(R.string.standalone) == "true",
            )
        }
    }
}
