package `in`.dalc.pkdmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.Session
import `in`.dalc.pkdmobile.ui.PkdApp
import kotlinx.coroutines.launch
import `in`.dalc.pkdmobile.ui.theme.PkdMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        Notes.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PkdMobileTheme {
                PkdApp()
            }
        }
    }

    /** Spec §5: refresh on open and on return from background. */
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { Notes.refresh() }
    }
}
