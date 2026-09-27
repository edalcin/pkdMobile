package `in`.dalc.pkdmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import `in`.dalc.pkdmobile.ui.PkdApp
import `in`.dalc.pkdmobile.ui.theme.PkdMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PkdMobileTheme {
                PkdApp()
            }
        }
    }
}
