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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
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
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.OutboxEntity
import `in`.dalc.pkdmobile.data.TagEntity
import `in`.dalc.pkdmobile.data.htmlToText
import `in`.dalc.pkdmobile.data.textToFields
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Notas feed (spec §3): cards in 2 columns, favorites first, pull-to-refresh, FAB → "Nova Nota". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onOpen: (Long) -> Unit, onUnsent: () -> Unit) {
    val notes by remember { Notes.dao.all() }.collectAsState(initial = null)
    val tags by remember { Notes.dao.tags() }.collectAsState(initial = emptyList())
    val pending by remember { Notes.dao.pendingCount() }.collectAsState(initial = 0)
    val failed by remember { Notes.dao.failed() }.collectAsState(initial = emptyList())
    val tagMap = tags.associateBy { it.name }
    val scope = rememberCoroutineScope()
    var askLogout by remember { mutableStateOf(false) }
    var logoutWarning by remember { mutableStateOf(0) }
    var newNote by remember { mutableStateOf(false) }

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
            FloatingActionButton(onClick = { newNote = true }) { Boxicons.Icon("bx-plus", "Nova Nota") }
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

    if (newNote) NewNoteSheet("", onDismiss = { newNote = false })
}

/** Bottom sheet "Nova Nota": 1st line = title. Save puts the Nota in the Fila de envio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteSheet(initial: String, onDismiss: () -> Unit, onSaved: () -> Unit = {}) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val canSave = text.trim().lines().first().isNotBlank()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nova Nota", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, minLines = 5,
                placeholder = { Text("1ª linha = título") }, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { Notes.scope.launch { Notes.create(text) }; onSaved(); onDismiss() },
                enabled = canSave, modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar") }
        }
    }
}

/** Não enviados (spec §5): items the PKD refused. Copy, recreate (edit and send again) or discard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnsentScreen(onBack: () -> Unit) {
    val items by remember { Notes.dao.failed() }.collectAsState(initial = emptyList())
    val clipboard = LocalClipboardManager.current
    var recreate by remember { mutableStateOf<OutboxEntity?>(null) }
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
                            if (item.isNote() && item.noteText() != null) TextButton(onClick = { recreate = item }) { Text("Recriar") }
                            TextButton(onClick = { Notes.scope.launch { Notes.discard(item) } }) { Text("Descartar") }
                        }
                    }
                }
            }
        }
    }
    recreate?.let { item ->
        NewNoteSheet(
            item.noteText().orEmpty(),
            onDismiss = { recreate = null },
            onSaved = { Notes.scope.launch { Notes.discard(item) } },
        )
    }
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

/** Nota detail (spec §3): plain text with auto-save (1st line = title), Tags, favorite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(id: Long, onBack: () -> Unit) {
    val note by remember(id) { Notes.dao.one(id) }.collectAsState(initial = null)
    val tags by remember { Notes.dao.tags() }.collectAsState(initial = emptyList())
    var seen by remember { mutableStateOf(false) }
    // A Nota created in the app gets its PKD id when the queue sends it; the temporary one goes away.
    if (note == null && seen) LaunchedEffect(Unit) { onBack() }
    LaunchedEffect(note != null) { if (note != null) seen = true }
    val n = note ?: return
    val latest by rememberUpdatedState(n)
    var text by rememberSaveable(id) { mutableStateOf(n.text()) }
    var status by remember { mutableStateOf<String?>(null) }
    var newTag by rememberSaveable(id) { mutableStateOf("") }

    fun save(fields: JSONObject) = Notes.scope.launch {
        Notes.edit(id, fields)
        status = when (Notes.dao.queueState(id)) {
            null -> "Salvo"
            false -> "Na fila (sem conexão)"
            true -> "Não enviado — ver Não enviados"
        }
    }

    fun saveText() {
        if (text == latest.text()) return
        save(textToFields(text) ?: run { status = "A 1ª linha é o título."; return })
    }

    LaunchedEffect(text) { delay(1_000); saveText() }
    DisposableEffect(id) { onDispose { saveText() } } // leaving before the debounce still saves

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(status ?: "Nota", style = MaterialTheme.typography.titleSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Boxicons.Icon("bx-arrow-back", "Voltar") } },
                actions = {
                    IconButton(onClick = { save(JSONObject().put("favorite", !n.isFavorite)) }) {
                        if (n.isFavorite) Boxicons.Icon("bxs-star", "Tirar dos favoritos", tint = MaterialTheme.colorScheme.primary)
                        else Boxicons.Icon("bx-star", "Favoritar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val tagMap = tags.associateBy { it.name }
            TagChips(n.tagList(), tagMap) { name -> save(JSONObject().put("tags", JSONArray(n.tagList() - name))) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTag, onValueChange = { newTag = it }, singleLine = true,
                    label = { Text("Nova Tag") }, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val name = newTag.trim().removePrefix("#")
                    if (name.isNotEmpty() && name !in n.tagList()) save(JSONObject().put("tags", JSONArray(n.tagList() + name)))
                    newTag = ""
                }) { Boxicons.Icon("bx-plus", "Pôr Tag") }
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("1ª linha = título") },
            )
        }
    }
}
