package `in`.dalc.pkdmobile.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import `in`.dalc.pkdmobile.BuildConfig
import org.json.JSONObject
import java.net.HttpCookie
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * One PKD server, one session (spec §4). The cookies (`pkd_session`, `pkd_device`,
 * `pkd_csrf`) are kept encrypted with an Android Keystore AES-GCM key. The password is never kept.
 */
object Session {
    private const val KEY_ALIAS = "pkd_session_cookies"
    private lateinit var prefs: SharedPreferences
    private val cookies = mutableMapOf<String, String>()

    var baseUrl by mutableStateOf<String?>(null) // state: logout clears it after pkd_session is gone
        private set
    var loggedIn by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        baseUrl = prefs.getString("url", null)
        // A key that the Keystore lost (e.g. device restore) gives a decrypt error: treat as logged out.
        prefs.getString("cookies", null)?.let { runCatching { JSONObject(decrypt(it)) }.getOrNull() }?.let { o ->
            o.keys().forEach { cookies[it] = o.getString(it) }
        }
        loggedIn = "pkd_session" in cookies
    }

    fun setUrl(url: String) {
        baseUrl = url
        prefs.edit().putString("url", url).apply()
    }

    @Synchronized fun cookieHeader(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    @Synchronized fun csrf(): String? = cookies["pkd_csrf"]

    @Synchronized fun store(setCookieHeaders: List<String>) {
        for (header in setCookieHeaders) for (c in HttpCookie.parse(header)) {
            if (c.maxAge == 0L || c.value.isEmpty()) cookies.remove(c.name) else cookies[c.name] = c.value
        }
        save()
    }

    /** Session expired (401): keep the URL, the cache and `pkd_device`; ask the password again. */
    @Synchronized fun expire() {
        cookies.remove("pkd_session")
        save()
    }

    /** Logout: forget the server and every cookie. */
    @Synchronized fun clear() {
        cookies.clear()
        baseUrl = null
        prefs.edit().clear().apply()
        loggedIn = false
    }

    private fun save() {
        prefs.edit().putString("cookies", encrypt(JSONObject(cookies.toMap()).toString())).apply()
        loggedIn = "pkd_session" in cookies
    }

    /** `https://host[/path]` without the final slash, or null. Debug builds also accept `http://`. */
    fun normalizeUrl(raw: String): String? {
        val u = Uri.parse(raw.trim().trimEnd('/'))
        val schemeOk = u.scheme == "https" || (BuildConfig.DEBUG && u.scheme == "http")
        return if (schemeOk && !u.host.isNullOrEmpty() && u.query == null && u.fragment == null) u.toString() else null
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }
}
