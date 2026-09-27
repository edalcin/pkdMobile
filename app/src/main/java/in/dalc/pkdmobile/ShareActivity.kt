package `in`.dalc.pkdmobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.Session
import `in`.dalc.pkdmobile.ui.theme.PkdMobileTheme
import kotlinx.coroutines.launch

/**
 * Share (spec §6): other apps send text/links (ACTION_SEND). A bottom sheet "Nova Nota" over the other
 * app; Salvar puts a Nota #captura in the Fila de envio. Outside any future biometric lock.
 */
class ShareActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        Notes.init(applicationContext)
        if (Session.baseUrl == null) {
            Toast.makeText(this, "Entre no pkdMobile antes de compartilhar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().trim()
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        // Many apps send the page title as the subject and the link as the text.
        val initial = listOf(subject, shared).filter { it.isNotEmpty() && (it != subject || !shared.startsWith(subject)) }.joinToString("\n")

        setContent {
            PkdMobileTheme {
                var text by rememberSaveable { mutableStateOf(initial) }
                ModalBottomSheet(onDismissRequest = { finish() }) {
                    Column(
                        Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Nova Nota #captura", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(text, { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                // Notes.scope outlives this activity: the queue goes on after finish().
                                Notes.scope.launch { Notes.capture(text) }
                                Toast.makeText(this@ShareActivity, "Nota salva no pkdMobile.", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                        ) { Text("Salvar") }
                    }
                }
            }
        }
    }
}
