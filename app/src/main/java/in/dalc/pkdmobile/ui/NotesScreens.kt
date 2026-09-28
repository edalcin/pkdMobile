package `in`.dalc.pkdmobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.dalc.pkdmobile.data.NoteEntity
import `in`.dalc.pkdmobile.data.hasContent
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.TagEntity
import `in`.dalc.pkdmobile.data.htmlToText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Notas feed (spec §3): cards in 2 columns, favorites first, pull-to-refresh, FAB → "Nova Nota". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onOpen: (Long) -> Unit, onUnsent: () -> Unit, onNewNote: () -> Unit) {
    val notes by remember { Notes.dao.all() }.collectAsState(initial = null)
    val tags by remember { Notes.dao.tags() }.collectAsState(initial = emptyList())
    val pending by remember { Notes.dao.pendingCount() }.collectAsState(initial = 0)
    val failed by remember { Notes.dao.failed() }.collectAsState(initial = emptyList())
    val tagMap = tags.associateBy { it.name }
    val scope = rememberCoroutineScope()
    var askLogout by remember { mutableStateOf(false) }
    var logoutWarning by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pending > 0) "Notas · $pending na fila" else "Notas") },
                actions = {
                    if (failed.isNotEmpty()) {
                        IconButton(onClick = onUnsent) {
                            BadgedBox(badge = { Badge { Text("${failed.size}") } }) { Boxicons.Icon("bx-error-circle", "Não enviados") }
                        }
                    }
                    IconButton(onClick = {
                        scope.launch { logoutWarning = Notes.dao.outboxCount(); askLogout = true }
                    }) { Boxicons.Icon("bx-log-out", "Sair") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewNote) { Boxicons.Icon("bx-plus", "Nova Nota") }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = Notes.refreshing,
            onRefresh = { scope.launch { Notes.refresh() } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                Notes.error?.let { msg ->
                    item(span = StaggeredGridItemSpan.FullLine) { Text(msg, color = MaterialTheme.colorScheme.error) }
                }
                items(notes.orEmpty(), key = { it.id }) { NoteCard(it, tagMap) { onOpen(it.id) } }
            }
            if (notes?.isEmpty() == true && !Notes.refreshing) {
                Text("Nenhuma Nota ainda", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (askLogout) {
        AlertDialog(
            onDismissRequest = { askLogout = false },
            title = { Text("Sair do PKD?") },
            text = {
                Text(
                    (if (logoutWarning > 0) "$logoutWarning item(ns) da fila ainda não foram para o PKD e serão perdidos. " else "") +
                        "O cache deste aparelho é apagado. No próximo login, o PKD pode pedir o código por e-mail.",
                )
            },
            confirmButton = { TextButton(onClick = { askLogout = false; Notes.scope.launch { Notes.logout() } }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { askLogout = false }) { Text("Cancelar") } },
        )
    }
}

/** Nova Nota / Recriar (ADR 0002): editor rico, título opcional (derivado do corpo se vazio ao salvar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteScreen(initialTitle: String, initialHtml: String, autoFocusBody: Boolean = false, onBack: () -> Unit, onSaved: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var html by remember { mutableStateOf(initialHtml) }
    var readOnly by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova Nota") },
                navigationIcon = { IconButton(onClick = onBack) { Boxicons.Icon("bx-arrow-back", "Cancelar") } },
                actions = {
                    IconButton(
                        onClick = { scope.launch { if (Notes.createRich(title, html)) onSaved() } },
                        enabled = hasContent(title, html) && !readOnly,
                    ) { Boxicons.Icon("bx-check", "Salvar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).imePadding().fillMaxSize()) {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                placeholder = { Text("Título (opcional)") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            RichNoteEditor(
                contentKey = Unit,
                initialHtml = initialHtml,
                onChange = { html = it },
                onOpenDoc = {}, // rascunho ainda não salvo: sem navegação
                onLossCheck = { ok -> readOnly = !ok },
                autoFocus = autoFocusBody,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Não enviados (spec §5): items the PKD refused. Copy, recreate (edit and send again) or discard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnsentScreen(onBack: () -> Unit, onRecriar: (Long) -> Unit) {
    val items by remember { Notes.dao.failed() }.collectAsState(initial = emptyList())
    val clipboard = LocalClipboardManager.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Não enviados") },
                navigationIcon = { IconButton(onClick = onBack) { Boxicons.Icon("bx-arrow-back", "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (items.isEmpty()) item { Text("Nada aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(items, key = { it.seq }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.describe(), maxLines = 8, overflow = TextOverflow.Ellipsis)
                        Text("O PKD recusou: ${item.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(item.describe())) }) { Text("Copiar") }
                            if (item.isNote() && item.noteFields() != null) TextButton(onClick = { onRecriar(item.seq) }) { Text("Recriar") }
                            TextButton(onClick = { Notes.scope.launch { Notes.discard(item) } }) { Text("Descartar") }
                        }
                    }
                }
            }
        }
    }
}

/** Recriar (Não enviados): abre o editor rico já preenchido com o título e o HTML do item recusado. */
@Composable
fun RecriarScreen(seq: Long, onBack: () -> Unit) {
    val items by remember { Notes.dao.failed() }.collectAsState(initial = null)
    val item = items?.firstOrNull { it.seq == seq }
    if (items != null && item == null) LaunchedEffect(Unit) { onBack() } // já enviado ou descartado
    val fields = item?.noteFields() ?: return
    val (title, html) = fields
    NewNoteScreen(
        initialTitle = title, initialHtml = html, onBack = onBack,
        onSaved = { Notes.scope.launch { Notes.discard(item) }; onBack() },
    )
}

@Composable
private fun NoteCard(note: NoteEntity, tagMap: Map<String, TagEntity>, onClick: () -> Unit) {
    val excerpt = remember(note.bodyHtml) { htmlToText(note.bodyHtml) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    note.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (note.isFavorite) Boxicons.Icon("bxs-star", "Favorita", size = 16.dp, tint = MaterialTheme.colorScheme.primary)
            }
            if (note.id < 0) Text("Na fila", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (excerpt.isNotEmpty()) {
                Text(
                    excerpt, style = MaterialTheme.typography.bodySmall, maxLines = 6,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TagChips(note.tagList(), tagMap)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChips(names: List<String>, tagMap: Map<String, TagEntity>, onRemove: ((String) -> Unit)? = null) {
    if (names.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEach { name ->
            val tag = tagMap[name]
            Surface(
                color = tag?.color.toColor() ?: MaterialTheme.colorScheme.surfaceVariant,
                contentColor = tag?.textColor.toColor() ?: MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(50),
                modifier = if (onRemove != null) Modifier.clickable { onRemove(name) } else Modifier,
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = if (onRemove != null) 6.dp else 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#$name", style = MaterialTheme.typography.labelSmall)
                    if (onRemove != null) Boxicons.Icon("bx-x", "Tirar $name", Modifier.padding(start = 4.dp), size = 14.dp)
                }
            }
        }
    }
}

private fun String?.toColor(): Color? = runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrNull()

/** Nota detail (ADR 0002): editor rico (WebView + barra nativa), Tags, favoritar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(id: Long, onBack: () -> Unit, onOpenDoc: (Long) -> Unit, onOpenNote: (Long) -> Unit) {
    val note by remember(id) { Notes.dao.one(id) }.collectAsState(initial = null)
    val tags by remember { Notes.dao.tags() }.collectAsState(initial = emptyList())
    var seen by remember { mutableStateOf(false) }
    // A Nota created in the app gets its PKD id when the queue sends it; the temporary one goes away.
    if (note == null && seen) LaunchedEffect(Unit) { onBack() }
    LaunchedEffect(note != null) { if (note != null) seen = true }
    val n = note ?: return
    val latest by rememberUpdatedState(n)
    var title by rememberSaveable(id) { mutableStateOf(n.title) }
    var lastHtml by remember(id) { mutableStateOf(n.bodyHtml) }
    var readOnly by remember(id) { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var newTag by rememberSaveable(id) { mutableStateOf("") }
    var askDelete by remember { mutableStateOf(false) }

    fun save(fields: JSONObject) = Notes.scope.launch {
        Notes.edit(id, fields)
        status = when (Notes.dao.queueState(id)) {
            null -> "Salvo"
            false -> "Na fila (sem conexão)"
            true -> "Não enviado — ver Não enviados"
        }
    }

    fun saveIfChanged() {
        if (readOnly) return
        val fields = JSONObject()
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) title = latest.title // o PKD exige título: nunca manda vazio
        else if (trimmedTitle != latest.title) fields.put("title", trimmedTitle)
        if (lastHtml != latest.bodyHtml) fields.put("content", lastHtml)
        if (fields.length() > 0) save(fields)
    }

    LaunchedEffect(title, lastHtml) { delay(1_000); saveIfChanged() }
    DisposableEffect(id) { onDispose { saveIfChanged() } } // leaving before the debounce still saves

    fun addTag(name: String) {
        val clean = name.trim().removePrefix("#")
        if (clean.isNotEmpty() && clean !in n.tagList()) save(JSONObject().put("tags", JSONArray(n.tagList() + clean)))
        newTag = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(status ?: "Nota", style = MaterialTheme.typography.titleSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Boxicons.Icon("bx-arrow-back", "Voltar") } },
                actions = {
                    IconButton(onClick = { askDelete = true }) { Boxicons.Icon("bx-trash", "Apagar Nota") }
                    IconButton(onClick = { save(JSONObject().put("favorite", !n.isFavorite)) }) {
                        if (n.isFavorite) Boxicons.Icon("bxs-star", "Tirar dos favoritos", tint = MaterialTheme.colorScheme.primary)
                        else Boxicons.Icon("bx-star", "Favoritar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).imePadding().fillMaxSize()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val tagMap = tags.associateBy { it.name }
                TagChips(n.tagList(), tagMap) { name -> save(JSONObject().put("tags", JSONArray(n.tagList() - name))) }
                OutlinedTextField(
                    value = newTag, onValueChange = { newTag = it }, singleLine = true,
                    label = { Text("Nova Tag") }, modifier = Modifier.fillMaxWidth(),
                )
                if (newTag.isNotBlank()) {
                    val needle = Notes.fold(newTag.trim().removePrefix("#"))
                    val existing = n.tagList().toSet()
                    val suggestions = tags.filter { it.name !in existing && Notes.fold(it.name).contains(needle) }
                        .sortedWith(compareByDescending<TagEntity> { it.count }.thenBy { it.name })
                    val exactMatch = tags.any { Notes.fold(it.name) == needle }
                    Column {
                        suggestions.forEach { tag ->
                            TextButton(onClick = { addTag(tag.name) }, modifier = Modifier.fillMaxWidth()) {
                                Text("#${tag.name} (${tag.count})", modifier = Modifier.fillMaxWidth())
                            }
                        }
                        if (!exactMatch) {
                            val clean = newTag.trim().removePrefix("#")
                            TextButton(onClick = { addTag(clean) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Criar «$clean»", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    placeholder = { Text("Título") }, modifier = Modifier.fillMaxWidth(),
                )
            }
            RichNoteEditor(
                contentKey = id,
                initialHtml = n.bodyHtml,
                onChange = { lastHtml = it },
                onOpenDoc = { docId -> Notes.scope.launch { if (Notes.dao.note(docId) != null) onOpenNote(docId) else onOpenDoc(docId) } },
                onLossCheck = { ok -> readOnly = !ok },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Mover «${n.title}» para a lixeira?") },
            confirmButton = {
                TextButton(onClick = { askDelete = false; Notes.scope.launch { Notes.delete(id) } }) { Text("Mover") }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancelar") } },
        )
    }
}
