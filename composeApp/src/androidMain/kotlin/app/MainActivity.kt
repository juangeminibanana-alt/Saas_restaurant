package app

import android.os.Bundle
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
        setContent {
            // Cada APK (sabor de Gradle) define con qué app abre y si es una app independiente.
            App(
                initialRoute = getString(R.string.start_route),
                standalone = getString(R.string.standalone) == "true",
            )
        }
    }
}
