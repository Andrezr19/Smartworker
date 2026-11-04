package cl.smartworker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.smartworker.app.ui.AppNav
import cl.smartworker.app.ui.theme.SmartWorkerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartWorkerTheme {
                AppNav()
            }
        }
    }
}
