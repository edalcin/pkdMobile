package `in`.dalc.pkdmobile.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.format.Formatter
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import `in`.dalc.pkdmobile.data.Api
import `in`.dalc.pkdmobile.data.DocEntity
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.Session
import `in`.dalc.pkdmobile.data.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

/** Emoji icons show as text; Boxicons classes (`bx-…`) wait for slice 8, so they get a generic icon. */
@Composable
private fun DocIcon(icon: String) {
    if (icon.isNotEmpty() && !icon.startsWith("bx")) Text(icon, Modifier.padding(end = 8.dp))
    else Icon(Icons.Filled.Description, null, Modifier.padding(end = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Documentos (spec §3): the Árvore, collapsible. No FAB (read-only in v1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(onOpen: (Long) -> Unit) {
    val docs by remember { Notes.dao.docs() }.collectAsState(initial = null)
    var expanded by rememberSaveable { mutableStateOf(longArrayOf()) }
    val scope = rememberCoroutineScope()

    // docs come in pre-order, so a parent always comes before its children.
    val rows = remember(docs, expanded) {
        val all = docs.orEmpty()
        val parents = all.mapNotNullTo(HashSet()) { it.parentId }
        val depth = HashMap<Long, Int>()
        val shown = HashSet<Long>()
        all.mapNotNull { d ->
            depth[d.id] = d.parentId?.let { (depth[it] ?: 0) + 1 } ?: 0
            val visible = d.parentId == null || (d.parentId in shown && d.parentId in expanded)
            if (visible) { shown += d.id; Triple(d, depth[d.id]!!, d.id in parents) } else null
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Documentos") }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = Notes.refreshing,
            onRefresh = { scope.launch { Notes.refresh() } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                Notes.error?.let { msg -> item { Text(msg, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) } }
                items(rows, key = { it.first.id }) { (d, level, hasChildren) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(d.id) }.padding(start = (8 + 20 * level).dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasChildren) {
                            val open = d.id in expanded
                            IconButton(onClick = { expanded = if (open) expanded.filter { it != d.id }.toLongArray() else expanded + d.id }) {
                                Icon(
                                    if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                    if (open) "Fechar ${d.title}" else "Abrir ${d.title}",
                                )
                            }
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                        DocIcon(d.icon)
                        Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (docs?.isEmpty() == true && !Notes.refreshing) {
                Text("Nenhum Documento ainda", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

private fun Color.hex() = String.format("#%06X", 0xFFFFFF and toArgb())

/** Documento detail (spec §3): path in the Árvore, body (read-only WebView), Subdocumentos, Associações. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(id: Long, onBack: () -> Unit, onOpenDoc: (Long) -> Unit, onOpenNote: (Long) -> Unit) {
    val docs by remember { Notes.dao.docs() }.collectAsState(initial = emptyList())
    val body by remember(id) { Notes.dao.docBody(id) }.collectAsState(initial = null)
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        try {
            Notes.openDoc(id)
        } catch (e: Exception) {
            error = e.userMessage() // the cached copy, if any, stays on screen
        } finally {
            loading = false
        }
    }

    val byId = remember(docs) { docs.associateBy { it.id } }
    val node = byId[id]
    val path = remember(byId, id) { generateSequence(byId[id]?.parentId) { byId[it]?.parentId }.mapNotNull { byId[it]?.title }.toList().reversed() }
    val children = remember(docs, id) { docs.filter { it.parentId == id } }
    val json = remember(body) { body?.let { JSONObject(it.json) } }
    val doc = json?.optJSONObject("doc")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(doc?.optString("title") ?: node?.title ?: "Documento", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (path.isNotEmpty()) {
                item { Text(path.joinToString(" › "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            error?.let { msg ->
                item { Text(if (doc != null) "$msg Mostrando a cópia deste aparelho." else msg, color = MaterialTheme.colorScheme.error) }
            }
            if (doc == null) {
                if (loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (doc.optBoolean("encrypted_locked")) {
                item { Text("Documento protegido. Para ler, desbloqueie na PWA.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { HtmlBody(doc.optString("body_html")) }
            }

            if (children.isNotEmpty()) {
                item { Section("Subdocumentos") }
                items(children, key = { "c${it.id}" }) { c -> LinkRow(c.title, icon = c.icon) { onOpenDoc(c.id) } }
            }
            val links = json?.optJSONArray("links")?.objects().orEmpty().filter { !it.optBoolean("related_trashed") }
            if (links.isNotEmpty()) {
                item { Section("Notas relacionadas") }
                items(links, key = { "l${it.optLong("id")}" }) { l ->
                    val target = l.getLong("related_id")
                    LinkRow(l.optString("related_title")) {
                        scope.launch {
                            when {
                                target in byId -> onOpenDoc(target)
                                Notes.dao.note(target) != null -> onOpenNote(target)
                                else -> Toast.makeText(context, "Não está no cache deste aparelho.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            val attachments = json?.optJSONArray("attachments")?.objects().orEmpty()
            if (attachments.isNotEmpty()) {
                item { Section("Arquivos") }
                items(attachments, key = { "a${it.optLong("id")}" }) { a -> AttachmentRow(context, a) }
            }
            val urls = json?.optJSONArray("urls")?.objects().orEmpty()
            if (urls.isNotEmpty()) {
                item { Section("Links externos") }
                items(urls, key = { "u${it.optLong("id")}" }) { u ->
                    LinkRow(u.optString("title").ifEmpty { u.optString("url") }) { openExternal(context, Uri.parse(u.optString("url"))) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Column(Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LinkRow(title: String, icon: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) DocIcon(icon)
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AttachmentRow(context: Context, a: JSONObject) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(a.optString("original_name"), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                Formatter.formatShortFileSize(context, a.optLong("size_bytes")),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { viewAttachment(context, a) }) { Text("Ver") }
        TextButton(onClick = { downloadAttachment(context, a) }) { Text("Baixar") }
    }
}

private fun safeName(a: JSONObject) = a.optString("original_name").ifEmpty { "arquivo-${a.optLong("id")}" }.replace(Regex("[/\\\\]"), "_")

/** Ver: fetch to cacheDir/arquivos and open in another app. ponytail: whole file in memory (PKD caps at 50 MB). */
private fun viewAttachment(context: Context, a: JSONObject) = Notes.scope.launch {
    try {
        val file = withContext(Dispatchers.IO) {
            val (_, bytes) = Api.getBytes("/api/attachments/${a.getLong("id")}")
            File(context.cacheDir, "arquivos").apply { mkdirs() }.resolve(safeName(a)).apply { writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.optString("mime_type").ifEmpty { "*/*" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum app abre este arquivo. Use Baixar.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, e.userMessage(), Toast.LENGTH_SHORT).show()
    }
}

/** Baixar: DownloadManager to Downloads, with the same cookies and Cloudflare Access headers as the API. */
private fun downloadAttachment(context: Context, a: JSONObject) {
    val request = DownloadManager.Request(Uri.parse("${Session.baseUrl}/api/attachments/${a.getLong("id")}"))
        .setTitle(a.optString("original_name"))
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName(a))
    Api.authHeaders().forEach { (k, v) -> request.addRequestHeader(k, v) }
    context.getSystemService(DownloadManager::class.java).enqueue(request)
    Toast.makeText(context, "Baixando para Downloads…", Toast.LENGTH_SHORT).show()
}

private fun openExternal(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum app abre este link.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Read-only body. No JavaScript. Requests to the PKD (inline images `/api/attachments/…`) go through
 * the app, so they carry the session cookie and the Cloudflare Access headers; a link opens outside.
 */
@Composable
private fun HtmlBody(html: String) {
    val c = MaterialTheme.colorScheme
    val page = """<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><style>
        body{margin:0;color:${c.onSurface.hex()};background:transparent;font-family:sans-serif;font-size:16px;line-height:1.5;overflow-wrap:anywhere}
        img,video{max-width:100%;height:auto} a{color:${c.primary.hex()}} pre,code{white-space:pre-wrap}
        table{border-collapse:collapse} td,th{border:1px solid ${c.outline.hex()};padding:4px}
        blockquote{border-left:3px solid ${c.outline.hex()};margin-left:0;padding-left:12px}
        </style></head><body>$html</body></html>"""
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        val base = Session.baseUrl ?: return null
                        val url = request.url.toString()
                        if (!url.startsWith(base)) return null
                        return try {
                            val (mime, bytes) = Api.getBytes(url.removePrefix(base))
                            WebResourceResponse(mime?.substringBefore(';') ?: "application/octet-stream", null, ByteArrayInputStream(bytes))
                        } catch (e: Exception) {
                            WebResourceResponse("text/plain", null, 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        openExternal(ctx, request.url)
                        return true
                    }
                }
            }
        },
        update = { if (it.tag != page) { it.tag = page; it.loadDataWithBaseURL(Session.baseUrl, page, "text/html", "utf-8", null) } },
        modifier = Modifier.fillMaxWidth(),
    )
}
