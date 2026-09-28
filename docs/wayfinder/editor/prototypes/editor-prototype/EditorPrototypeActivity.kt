package `in`.dalc.pkdmobile

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.Session
import `in`.dalc.pkdmobile.ui.theme.PkdMobileTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/**
 * PROTOTYPE — throwaway (ticket wayfinder/editor/02-prototype-editor.md). Debug-only: this whole
 * file lives in src/debug, so a release build never contains it. Loads the bundle built by
 * prototypes/editor/build.mjs from app/src/debug/assets/editor-prototype/.
 *
 * No launcher: `adb shell am start -n in.dalc.pkdmobile/.EditorPrototypeActivity`.
 * See docs/wayfinder/editor/prototypes/README-editor-prototype.md.
 */
class EditorPrototypeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(applicationContext)
        Notes.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PkdMobileTheme {
                EditorPrototypeScreen()
            }
        }
    }
}

/** Mirrors the JSON PrototypeBridge.onState sends from editor-prototype.js. */
private data class ToolbarState(
    val h1: Boolean = false, val h2: Boolean = false, val h3: Boolean = false,
    val bullet: Boolean = false, val ordered: Boolean = false, val task: Boolean = false,
    val bold: Boolean = false, val italic: Boolean = false, val link: Boolean = false,
    val undo: Boolean = false, val redo: Boolean = false,
)

// H1 H2 H3 · bullets · numerada · checklist · negrito · itálico · link · desfazer/refazer
// (map.md "Barra (inserir)"). Glyphs match Editor.svelte's own toolbar (pkd/frontend, no Boxicons there).
private val TOOLBAR_ITEMS = listOf(
    "h1" to "H1", "h2" to "H2", "h3" to "H3",
    "bullet" to "\u2630", "ordered" to "1.", "task" to "\u2611",
    "bold" to "B", "italic" to "I", "link" to "\uD83D\uDD17",
    "undo" to "\u21A9", "redo" to "\u21AA",
)

private fun ToolbarState.active(cmd: String): Boolean = when (cmd) {
    "h1" -> h1; "h2" -> h2; "h3" -> h3; "bullet" -> bullet; "ordered" -> ordered; "task" -> task
    "bold" -> bold; "italic" -> italic; "link" -> link; "undo" -> undo; "redo" -> redo
    else -> false
}

// Nota de exemplo (map.md "Preservar tudo"): título/subtítulo/seção, negrito/itálico, link externo,
// bullets, numerada, checklist marcada+desmarcada, link interno (DocLink), destaque, parágrafo
// centralizado, tabela, imagem inline (data: URI) e um bloco Mermaid — tudo que a PWA grava.
private const val SAMPLE_TITLE = "Nota de exemplo"
private val SAMPLE_HTML = """
<h1>Nota de exemplo</h1><h2>Subtítulo</h2><h3>Seção</h3>
<p>Texto com <strong>negrito</strong> e <em>itálico</em>, mais um <a href="https://example.com" target="_blank" rel="noopener noreferrer">link externo</a>.</p>
<ul><li><p>Item com marcador 1</p></li><li><p>Item com marcador 2</p></li></ul>
<ol><li><p>Primeiro</p></li><li><p>Segundo</p></li></ol>
<ul data-type="taskList">
<li data-type="taskItem" data-checked="true"><label><input type="checkbox" checked><span></span></label><div><p>Feito</p></div></li>
<li data-type="taskItem" data-checked="false"><label><input type="checkbox"><span></span></label><div><p>Por fazer</p></div></li>
</ul>
<p>Ver também <span data-doc-link="42" data-doc-title="Outro Documento" class="doc-link">Outro Documento</span>.</p>
<p><mark data-color="#fbbf24" style="background-color: #fbbf24; color: inherit">destaque</mark></p>
<p style="text-align: center">Parágrafo centralizado</p>
<table><tr><th>Coluna A</th><th>Coluna B</th></tr><tr><td>1</td><td>2</td></tr></table>
<p><img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=" width="120" alt="pixel"></p>
<pre><code class="language-mermaid">graph TD
A-->B</code></pre>
""".trimIndent()

/** Checks each round-trip-fragile feature from docs/research/bundle-editor.md §4 against the current HTML. */
private fun survivalReport(html: String): String {
    fun line(label: String, ok: Boolean) = "${if (ok) "\u2713" else "\u2717"} $label"
    return listOf(
        line("Tabela", "<table" in html),
        line("Imagem", "<img" in html),
        line("Destaque (mark)", "<mark" in html),
        line("Checklist (data-checked)", "data-checked=" in html),
        line("Link interno (doc-link)", "data-doc-link" in html),
        line("Mermaid", "language-mermaid" in html),
        line("Alinhamento de texto", "text-align" in html),
    ).joinToString("\n")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EditorPrototypeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkTheme = isSystemInDarkTheme()

    var variant by rememberSaveable { mutableStateOf("A") }
    var title by rememberSaveable { mutableStateOf("") }
    var pageReady by remember { mutableStateOf(false) }
    var toolbarState by remember { mutableStateOf(ToolbarState()) }
    var htmlReport by remember { mutableStateOf<Pair<String, String>?>(null) } // report, raw html
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun runCommand(cmd: String) {
        webViewRef?.evaluateJavascript("window.editorCommand('$cmd')", null)
    }

    fun setToolbarMode(v: String) {
        webViewRef?.evaluateJavascript("window.setToolbarMode('${if (v == "B") "web" else "native"}')", null)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = variant == "A", onClick = { variant = "A"; setToolbarMode("A") }, label = { Text("A \u00b7 barra nativa") })
            FilterChip(selected = variant == "B", onClick = { variant = "B"; setToolbarMode("B") }, label = { Text("B \u00b7 barra na WebView") })
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("T\u00edtulo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp, 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = {
                title = SAMPLE_TITLE
                webViewRef?.evaluateJavascript("window.setContent(${JSONObject.quote(SAMPLE_HTML)})", null)
            }) { Text("Carregar amostra") }

            Button(onClick = {
                scope.launch {
                    if (Session.baseUrl == null) {
                        Toast.makeText(context, "Entre no pkdMobile antes de carregar uma Nota.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val note = Notes.dao.all().first().firstOrNull()
                    if (note == null) {
                        Toast.makeText(context, "Nenhuma Nota no cache.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    title = note.title
                    webViewRef?.evaluateJavascript("window.setContent(${JSONObject.quote(note.bodyHtml)})", null)
                }
            }) { Text("Carregar Nota do cache") }

            Button(onClick = {
                webViewRef?.evaluateJavascript("window.getHTML()") { result ->
                    val html = runCatching { JSONTokener(result).nextValue() as String }.getOrDefault("")
                    htmlReport = survivalReport(html) to html
                }
            }) { Text("Ver HTML") }
        }

        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(
                            PrototypeBridge(
                                context = ctx,
                                mainHandler = mainHandler,
                                onState = { toolbarState = it },
                            ),
                            "PrototypeBridge",
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                pageReady = true
                                setToolbarMode(variant)
                            }
                        }
                        val theme = if (darkTheme) "dark" else "light"
                        loadUrl("file:///android_asset/editor-prototype/editor.html?theme=$theme")
                    }.also { webViewRef = it }
                },
            )
        }

        if (variant == "A") {
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TOOLBAR_ITEMS.forEach { (cmd, glyph) ->
                    val active = toolbarState.active(cmd)
                    Box(
                        Modifier
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { runCommand(cmd) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            glyph,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (cmd == "bold") FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }

    htmlReport?.let { (report, html) ->
        AlertDialog(
            onDismissRequest = { htmlReport = null },
            confirmButton = { Button(onClick = { htmlReport = null }) { Text("Fechar") } },
            title = { Text("HTML atual") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(report, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(html, style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}


/** JS -> Android bridge (window.PrototypeBridge.* calls from editor-prototype.js). */
private class PrototypeBridge(
    private val context: android.content.Context,
    private val mainHandler: Handler,
    private val onState: (ToolbarState) -> Unit,
) {
    @JavascriptInterface
    fun onState(json: String) {
        val o = JSONObject(json)
        val state = ToolbarState(
            h1 = o.optBoolean("h1"), h2 = o.optBoolean("h2"), h3 = o.optBoolean("h3"),
            bullet = o.optBoolean("bullet"), ordered = o.optBoolean("ordered"), task = o.optBoolean("task"),
            bold = o.optBoolean("bold"), italic = o.optBoolean("italic"), link = o.optBoolean("link"),
            undo = o.optBoolean("undo"), redo = o.optBoolean("redo"),
        )
        mainHandler.post { onState(state) }
    }

    @JavascriptInterface
    fun onChange(html: String) {
        // ponytail: prototype doesn't persist — "Ver HTML" reads the live editor instead.
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        mainHandler.post {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    @JavascriptInterface
    fun openDocLink(docId: String) {
        mainHandler.post {
            Toast.makeText(context, "abriria Documento $docId", Toast.LENGTH_SHORT).show()
        }
    }
}
