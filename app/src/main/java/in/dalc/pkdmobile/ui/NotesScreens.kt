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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.dalc.pkdmobile.data.NoteEntity
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.TagEntity
import `in`.dalc.pkdmobile.data.htmlToText
import `in`.dalc.pkdmobile.data.textToHtml
import `in`.dalc.pkdmobile.data.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Notas feed (spec §3): cards in 2 columns, favorites first, pull-to-refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onOpen: (Long) -> Unit) {
    val notes by remember { Notes.dao.all() }.collectAsState(initial = null)
    val tags by remember { Notes.dao.tags() }.collectAsState(initial = emptyList())
    val tagMap = tags.associateBy { it.name }
    val scope = rememberCoroutineScope()
    var askLogout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notas") },
                actions = {
                    IconButton(onClick = { askLogout = true }) { Icon(Icons.AutoMirrored.Filled.Logout, "Sair") }
                },
            )
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
            text = { Text("O cache deste aparelho é apagado. No próximo login, o PKD pode pedir o código por e-mail.") },
            confirmButton = { TextButton(onClick = { askLogout = false; Notes.scope.launch { Notes.logout() } }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { askLogout = false }) { Text("Cancelar") } },
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
                if (note.isFavorite) Icon(Icons.Filled.Star, "Favorita", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
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
                    if (onRemove != null) Icon(Icons.Filled.Close, "Tirar $name", Modifier.padding(start = 4.dp).size(14.dp))
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
    val n = note ?: return
    val latest by rememberUpdatedState(n)
    var text by rememberSaveable(id) { mutableStateOf(n.text()) }
    var status by remember { mutableStateOf<String?>(null) }
    var newTag by rememberSaveable(id) { mutableStateOf("") }

    fun run(action: suspend () -> Unit) = Notes.scope.launch {
        status = "Salvando…"
        status = try {
            action(); "Salvo"
        } catch (e: Exception) {
            "Não salvo: ${e.userMessage()}"
        }
    }

    fun save(body: JSONObject) = run { Notes.patch(id, body) }

    fun saveText() {
        if (text == latest.text()) return
        val lines = text.trim().lines()
        val title = lines.first().trim()
        if (title.isEmpty()) { status = "A 1ª linha é o título."; return }
        val rest = lines.drop(1).joinToString("\n").trim('\n')
        save(JSONObject().put("title", title).put("content", if (rest.isBlank()) "" else textToHtml(rest)))
    }

    LaunchedEffect(text) { delay(1_000); saveText() }
    DisposableEffect(id) { onDispose { saveText() } } // leaving before the debounce still saves

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(status ?: "Nota", style = MaterialTheme.typography.titleSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = { run { Notes.toggleFavorite(id) } }) {
                        if (n.isFavorite) Icon(Icons.Filled.Star, "Tirar dos favoritos", tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Filled.StarBorder, "Favoritar")
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
                }) { Icon(Icons.Filled.Add, "Pôr Tag") }
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text("1ª linha = título") },
            )
        }
    }
}
