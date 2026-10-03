package `in`.dalc.pkdmobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.userMessage
import kotlinx.coroutines.delay

private val kindIcon = mapOf(Notes.Kind.Nota to "bx-note", Notes.Kind.Memoria to "bx-history", Notes.Kind.Documento to "bx-file")
private val kindName = mapOf(Notes.Kind.Nota to "Nota", Notes.Kind.Memoria to "Memória", Notes.Kind.Documento to "Documento")

/**
 * Busca (spec §3): field + results in the PKD rank order (as the PWA), each with the icon of its type. Grouping by
 * type put every Nota above the best Documentos. Offline: local search with "resultados parciais".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onOpen: (Notes.Hit) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Notes.Hit>?>(null) }
    var partial by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { hits = null; error = null; return@LaunchedEffect }
        delay(400) // typing: search only when the text stops changing
        busy = true
        try {
            val (list, isPartial) = Notes.search(q)
            hits = list; partial = isPartial; error = null
        } catch (e: Exception) {
            error = e.userMessage()
        } finally {
            busy = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Busca") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Buscar Notas, Memórias e Documentos") },
                    leadingIcon = { Boxicons.Icon("bx-search", null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) }
            error?.let { item { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) } }
            if (partial && hits != null) {
                item {
                    Text(
                        "Sem conexão: resultados parciais, só com o que está neste aparelho.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            hits?.let { list ->
                if (list.isEmpty() && !busy) item { Text("Nada encontrado.", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(list, key = { "${it.kind}-${it.id}" }) { hit ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(hit) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Boxicons.Icon(kindIcon.getValue(hit.kind), kindName[hit.kind], Modifier.padding(end = 12.dp), size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(hit.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
