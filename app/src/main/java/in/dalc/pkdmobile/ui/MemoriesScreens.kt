package `in`.dalc.pkdmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.dalc.pkdmobile.data.MemoryEntity
import `in`.dalc.pkdmobile.data.Notes
import `in`.dalc.pkdmobile.data.htmlToText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

private val ptBR = Locale.forLanguageTag("pt-BR")

private val PERIODS = mapOf(
    "madrugada" to "Madrugada", "manha" to "Manhã", "almoco" to "Almoço", "tarde" to "Tarde",
    "lanche" to "Lanche", "jantar" to "Jantar", "noite" to "Noite",
)

private fun monthName(month: Int) = java.time.Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, ptBR).replaceFirstChar { it.uppercase(ptBR) }

private fun MemoryEntity.monthHeader() = if (month == null) "$year" else "${monthName(month)} $year"

/** "27 · dom", or "—" for a Memória with only month/year. */
private fun MemoryEntity.dayLabel(): String {
    if (month == null || day == null) return "—"
    val weekday = LocalDate.of(year, month, day).dayOfWeek.getDisplayName(TextStyle.SHORT, ptBR).trimEnd('.')
    return "$day · $weekday"
}

private fun MemoryEntity.timeLabel(): String? = when {
    hour != null -> "%02d:%02d".format(hour, minute ?: 0)
    period.isNotEmpty() -> PERIODS[period] ?: period
    else -> null
}

private fun MemoryEntity.fullDate(): String = when {
    month == null -> "$year"
    day == null -> "${monthName(month)} de $year"
    else -> LocalDate.of(year, month, day).let {
        "${it.dayOfWeek.getDisplayName(TextStyle.FULL, ptBR)}, $day de ${monthName(month).lowercase(ptBR)} de $year"
    }
} + (timeLabel()?.let { " · $it" } ?: "")

/**
 * Newest first, like the PKD (years, months, days descending; the coarser Memória first), then the
 * server order (pos). A Memória still in the queue has pos -1: first inside its day.
 */
private val memoryOrder = compareByDescending<MemoryEntity> { it.year }
    .thenByDescending { it.month ?: 99 }
    .thenByDescending { it.day ?: 99 }
    .thenBy { it.pos }

/** Memórias (spec §3): chronological list grouped by month. FAB → "Nova Memória". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(onOpen: (Long) -> Unit) {
    val memories by remember { Notes.dao.memories() }.collectAsState(initial = null)
    val groups = remember(memories) { memories.orEmpty().sortedWith(memoryOrder).groupBy { it.monthHeader() } }
    val scope = rememberCoroutineScope()
    var newMemory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Memórias") }) },
        floatingActionButton = { FloatingActionButton(onClick = { newMemory = true }) { Boxicons.Icon("bx-plus", "Nova Memória") } },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = Notes.refreshing,
            onRefresh = { scope.launch { Notes.refresh() } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Notes.error?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.error) } }
                groups.forEach { (header, list) ->
                    item(key = "h-$header") {
                        Text(
                            header, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        )
                    }
                    items(list, key = { it.id }) { MemoryRow(it) { onOpen(it.id) } }
                }
            }
            if (memories?.isEmpty() == true && !Notes.refreshing) {
                Text("Nenhuma Memória ainda", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (newMemory) NewMemorySheet(onDismiss = { newMemory = false })
}

@Composable
private fun MemoryRow(m: MemoryEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(m.dayLabel(), Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(m.timeLabel(), if (m.id < 0) "Na fila" else null).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Bottom sheet "Nova Memória" (spec §3): date, title, details. Save puts it in the Fila de envio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMemorySheet(onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pickDate by remember { mutableStateOf(false) }
    val d = LocalDate.parse(date)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nova Memória", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year))
            }
            OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(details, { details = it }, label = { Text("Detalhes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    Notes.scope.launch { Notes.createMemory(title, details, d.year, d.monthValue, d.dayOfMonth) }
                    onDismiss()
                },
                enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar") }
        }
    }

    if (pickDate) {
        // DatePicker works in UTC millis.
        val state = rememberDatePickerState(initialSelectedDateMillis = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}

/** Memória detail (spec §3): date, title, details, MEM-…. Read-only (editing is out of v1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailScreen(id: Long, onBack: () -> Unit) {
    val memory by remember(id) { Notes.dao.memory(id) }.collectAsState(initial = null)
    var seen by remember { mutableStateOf(false) }
    // A Memória created in the app gets its PKD id when the queue sends it; the temporary one goes away.
    if (memory == null && seen) LaunchedEffect(Unit) { onBack() }
    LaunchedEffect(memory != null) { if (memory != null) seen = true }
    val m = memory ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(m.memoryId.ifEmpty { "Na fila" }, style = MaterialTheme.typography.titleSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Boxicons.Icon("bx-arrow-back", "Voltar") } },
            )
        },
    ) { padding ->
        SelectionContainer {
            Column(
                Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(m.fullDate().replaceFirstChar { it.uppercase(ptBR) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(m.title, style = MaterialTheme.typography.headlineSmall)
                val details = remember(m.bodyHtml) { htmlToText(m.bodyHtml) }
                if (details.isNotEmpty()) Text(details)
            }
        }
    }
}
