package `in`.dalc.pkdmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import `in`.dalc.pkdmobile.data.Api
import `in`.dalc.pkdmobile.data.ApiException
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.Session
import `in`.dalc.pkdmobile.data.userMessage
import kotlinx.coroutines.launch

/** Login (spec §4): URL (https only) + password, then the e-mail 2FA code when the device is new. */
@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    val fixedUrl = Session.baseUrl // session expired: same server; changing it requires logout
    var url by rememberSaveable { mutableStateOf(fixedUrl ?: "https://") }
    var password by remember { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var challenge by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() = scope.launch {
        busy = true
        error = null
        try {
            val ch = challenge
            if (ch == null) {
                Session.setUrl(Session.normalizeUrl(url) ?: throw ApiException(0, "Use uma URL https://"))
                challenge = Api.login(password)
                password = ""
            } else {
                Api.login2fa(ch, code.trim())
            }
        } catch (e: Exception) {
            error = if (e is ApiException && e.code == 401) {
                if (challenge == null) "Senha incorreta." else "Código incorreto ou vencido."
            } else e.userMessage()
        } finally {
            busy = false
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.systemBarsPadding().imePadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            Text("PKD", style = MaterialTheme.typography.headlineLarge)
            if (fixedUrl != null && challenge == null) {
                Text("A sessão expirou. Digite a senha de novo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("URL do PKD") }, singleLine = true,
                enabled = fixedUrl == null && challenge == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (challenge == null) {
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Senha") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Enviamos um código para o seu e-mail.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = code, onValueChange = { code = it },
                    label = { Text("Código") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (challenge == null) "Entrar" else "Confirmar")
            }
            if (fixedUrl != null) {
                TextButton(onClick = { scope.launch { Notes.logout() } }) { Text("Sair e trocar de servidor") }
            }
        }
    }
}
