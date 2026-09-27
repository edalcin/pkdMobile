package `in`.dalc.pkdmobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.userMessage
import kotlinx.coroutines.delay

private val kindTitle = mapOf(Notes.Kind.Nota to "Notas", Notes.Kind.Memoria to "Memórias", Notes.Kind.Documento to "Documentos")

/** Busca (spec §3): field + results grouped by type. Offline: local search with "resultados parciais". */
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
                list.groupBy { it.kind }.toSortedMap().forEach { (kind, group) ->
                    item(key = "h-$kind") {
                        Text(
                            "${kindTitle[kind]} (${group.size})", Modifier.padding(top = 16.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(group, key = { "${it.kind}-${it.id}" }) { hit ->
                        Text(
                            hit.title, Modifier.fillMaxWidth().clickable { onOpen(hit) }.padding(vertical = 10.dp),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
