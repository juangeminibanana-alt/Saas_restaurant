package app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.platform.AndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidPlatform.appContext = applicationContext
        setContent {
            // En el teléfono se abre el índice de la demo para poder entrar a las cinco apps.
            App(initialRoute = "/demo")
        }
    }
}
