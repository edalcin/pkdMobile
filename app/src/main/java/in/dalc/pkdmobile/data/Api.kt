package `in`.dalc.pkdmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val code: Int, message: String) : IOException(message)

/** User-facing message in pt-BR. */
fun Throwable.userMessage(): String = when {
    this is ApiException && code == 401 -> "Sessão expirada. Entre de novo."
    this is ApiException && code == 429 -> "Muitas tentativas. Tente mais tarde."
    this is ApiException -> message ?: "Erro $code"
    this is IOException -> "Sem conexão com o PKD."
    else -> message ?: toString()
}

/** PKD REST client: session cookies + `X-CSRF-Token` on mutations (double-submit, PKD `middleware_csrf.go`). */
object Api {
    suspend fun request(method: String, path: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val base = Session.baseUrl ?: throw ApiException(401, "Sem servidor")
        // The PKD sets pkd_csrf on any GET; get one before the first mutation.
        if (method != "GET" && Session.csrf() == null) request("GET", "/healthz")
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            Session.cookieHeader().takeIf { it.isNotEmpty() }?.let { c.setRequestProperty("Cookie", it) }
            if (method != "GET") Session.csrf()?.let { c.setRequestProperty("X-CSRF-Token", it) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val setCookies = c.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }
            if (setCookies.isNotEmpty()) Session.store(setCookies)
            val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 && !path.startsWith("/api/login")) Session.expire()
            if (code >= 400) throw ApiException(code, text.trim().ifEmpty { "HTTP $code" })
            text
        } finally {
            c.disconnect()
        }
    }

    /** Returns the 2FA challenge id, or null when the login is complete (trusted device or 2FA off). */
    suspend fun login(password: String): String? {
        val r = request("POST", "/api/login", JSONObject().put("password", password))
        return if (r.isBlank()) null else JSONObject(r).optString("challenge_id").ifEmpty { null }
    }

    suspend fun login2fa(challengeId: String, code: String) {
        request("POST", "/api/login/2fa", JSONObject().put("challenge_id", challengeId).put("code", code))
    }
}
