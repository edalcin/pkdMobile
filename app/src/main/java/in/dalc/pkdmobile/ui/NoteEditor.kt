package `in`.dalc.pkdmobile.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

// WebViewAssetLoader.DEFAULT_DOMAIN + o path handler registrado abaixo (/assets/ → android_asset/).
private const val EDITOR_BASE_URL = "https://${WebViewAssetLoader.DEFAULT_DOMAIN}/assets/editor/editor.html"

/** Mirrors the JSON AndroidEditor.onState receives from assets/editor/editor.html (bundle vendored per ADR 0002). */
private data class ToolbarState(
    val h1: Boolean = false, val h2: Boolean = false, val h3: Boolean = false,
    val bullet: Boolean = false, val ordered: Boolean = false, val task: Boolean = false,
    val bold: Boolean = false, val italic: Boolean = false, val link: Boolean = false,
    val undo: Boolean = false, val redo: Boolean = false,
)

private fun ToolbarState.active(cmd: String) = when (cmd) {
    "h1" -> h1; "h2" -> h2; "h3" -> h3; "bullet" -> bullet; "ordered" -> ordered
    "task" -> task; "bold" -> bold; "italic" -> italic; "link" -> link
    "undo" -> undo; "redo" -> redo; else -> false
}

/** H1 · H2 · H3 · bullets · numerada · checklist · negrito · itálico · link · desfazer/refazer (ADR 0002 "Barra"). */
private data class ToolbarButton(val cmd: String, val icon: String?, val text: String?, val label: String)

// No dedicated Boxicons glyph for H1/H2/H3 (only a generic bx-heading): text labels, como a própria PWA.
private val TOOLBAR_BUTTONS = listOf(
    ToolbarButton("h1", null, "H1", "Título 1"),
    ToolbarButton("h2", null, "H2", "Título 2"),
    ToolbarButton("h3", null, "H3", "Título 3"),
    ToolbarButton("bullet", "bx-list-ul", null, "Lista com marcadores"),
    ToolbarButton("ordered", "bx-list-ol", null, "Lista numerada"),
    ToolbarButton("task", "bx-list-check", null, "Lista de tarefas"),
    ToolbarButton("bold", "bx-bold", null, "Negrito"),
    ToolbarButton("italic", "bx-italic", null, "Itálico"),
    ToolbarButton("link", "bx-link", null, "Link"),
    ToolbarButton("undo", "bx-undo", null, "Desfazer"),
    ToolbarButton("redo", "bx-redo", null, "Refazer"),
)

@Composable
private fun ToolbarButtonView(button: ToolbarButton, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (button.icon != null) Boxicons.Icon(button.icon, button.label, tint = tint)
        else Text(button.text!!, color = tint, fontWeight = FontWeight.Bold)
    }
}

/** JS → Kotlin bridge (window.AndroidEditor.*, contract in docs/adr/0002-editor-rico-webview-tiptap.md). */
private class AndroidEditorBridge(
    private val mainHandler: Handler,
    private val notifyReady: () -> Unit,
    private val notifyState: (ToolbarState) -> Unit,
    private val notifyChange: (String) -> Unit,
    private val notifyOpenLink: (String) -> Unit,
    private val notifyOpenDoc: (String) -> Unit,
    private val notifyLossCheck: (Boolean) -> Unit,
) {
    @JavascriptInterface
    fun onReady() { mainHandler.post(notifyReady) }

    @JavascriptInterface
    fun onState(json: String) {
        val o = JSONObject(json)
        val state = ToolbarState(
            h1 = o.optBoolean("h1"), h2 = o.optBoolean("h2"), h3 = o.optBoolean("h3"),
            bullet = o.optBoolean("bullet"), ordered = o.optBoolean("ordered"), task = o.optBoolean("task"),
            bold = o.optBoolean("bold"), italic = o.optBoolean("italic"), link = o.optBoolean("link"),
            undo = o.optBoolean("undo"), redo = o.optBoolean("redo"),
        )
        mainHandler.post { notifyState(state) }
    }

    @JavascriptInterface
    fun onChange(html: String) { mainHandler.post { notifyChange(html) } }

    @JavascriptInterface
    fun onOpenLink(url: String) { mainHandler.post { notifyOpenLink(url) } }

    @JavascriptInterface
    fun onOpenDoc(id: String) { mainHandler.post { notifyOpenDoc(id) } }

    @JavascriptInterface
    fun onLossCheck(json: String) {
        val ok = JSONObject(json).optBoolean("ok")
        mainHandler.post { notifyLossCheck(ok) }
    }
}

private fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Corpo rico de uma Nota (ADR 0002): WebView com o bundle vendorizado do PKD (`assets/editor/`, mesmas
 * extensões TipTap da PWA) + barra nativa acima do teclado. Usado no detalhe da Nota, na Nova Nota (FAB)
 * e no Recriar de Não enviados.
 *
 * `contentKey` só muda quando o conteúdo deve recarregar (id da Nota, ou o item de Não enviados no
 * Recriar): emissões do Flow com a mesma key nunca reescrevem o que o usuário está digitando.
 * `onLossCheck(false)`: a Nota tem algo que o bundle não conhece — o próprio bundle já entra em modo
 * leitura; aqui só escondemos a barra e o chamador não deve salvar.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichNoteEditor(
    contentKey: Any,
    initialHtml: String,
    onChange: (String) -> Unit,
    onOpenDoc: (Long) -> Unit,
    onLossCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    var ready by remember(contentKey) { mutableStateOf(false) }
    var readOnly by remember(contentKey) { mutableStateOf(false) }
    var toolbarState by remember(contentKey) { mutableStateOf(ToolbarState()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // setContent() below runs a docChanged transaction to load the Nota (even with emitUpdate:
    // false), so the bundle's onTransaction still fires one onChange echo of the loaded HTML. That
    // echo is not a user edit: swallow exactly the first onChange after each (re)load, or opening a
    // Nota without editing would enqueue a spurious PATCH (round-tripped HTML rarely byte-equals the
    // stored HTML, e.g. table colspan/rowspan get filled in).
    // ponytail: empty initial HTML (Nova Nota) may give no echo, so no swallow there — else the first
    // keystroke is lost. Upgrade: a load-sequence id in the bridge if the bundle echo changes.
    var suppressNextChange by remember(contentKey) { mutableStateOf(false) }

    LaunchedEffect(contentKey, ready) {
        if (!ready) return@LaunchedEffect
        suppressNextChange = initialHtml.isNotEmpty()
        webView?.evaluateJavascript("window.pkdEditor.setContent(${JSONObject.quote(initialHtml)})", null)
        if (autoFocus) webView?.evaluateJavascript("document.querySelector('.ProseMirror') && document.querySelector('.ProseMirror').focus()", null)
    }
    LaunchedEffect(darkTheme) {
        webView?.evaluateJavascript("window.pkdEditor.setTheme('${if (darkTheme) "dark" else "light"}')", null)
    }

    Column(modifier) {
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(
                            AndroidEditorBridge(
                                mainHandler = mainHandler,
                                notifyReady = { ready = true },
                                notifyState = { toolbarState = it },
                                notifyChange = { html -> if (suppressNextChange) suppressNextChange = false else onChange(html) },
                                notifyOpenLink = { url -> openExternal(context, url) },
                                notifyOpenDoc = { id -> id.toLongOrNull()?.let(onOpenDoc) },
                                notifyLossCheck = { ok -> readOnly = !ok; onLossCheck(ok) },
                            ),
                            "AndroidEditor",
                        )
                        val assetLoader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                            .build()
                        webViewClient = object : WebViewClient() {
                            // O bundle usa <script type="module">: file:// tem origin "null" e o WebView
                            // bloqueia módulos ES por CORS. WebViewAssetLoader serve em https://…, que não
                            // tem essa restrição (androidx.webkit; a WebView de Documento continua sem JS).
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                assetLoader.shouldInterceptRequest(request.url)

                            // Só a navegação para o próprio bundle fica na WebView; qualquer outra
                            // (não deveria acontecer — link/DocLink nunca navegam, ver main.js) abre fora.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.url.toString().startsWith(EDITOR_BASE_URL)) return false
                                openExternal(ctx, request.url.toString())
                                return true
                            }
                        }
                        loadUrl("$EDITOR_BASE_URL?theme=${if (darkTheme) "dark" else "light"}")
                    }.also { webView = it }
                },
            )
        }
        if (readOnly) {
            Text(
                "Esta Nota tem conteúdo que o app ainda não edita. Edite na PWA.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        } else {
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TOOLBAR_BUTTONS.forEach { button ->
                    ToolbarButtonView(button, toolbarState.active(button.cmd)) {
                        webView?.evaluateJavascript("window.pkdEditor.command('${button.cmd}')", null)
                    }
                }
            }
        }
    }
}
