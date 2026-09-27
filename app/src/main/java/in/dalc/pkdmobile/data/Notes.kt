package `in`.dalc.pkdmobile.data

import android.content.Context
import android.text.Html
import android.text.TextUtils
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.UUID

private const val TAG_SEP = "\u001f"

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Long, // negative = created in the app, still in the send queue
    val title: String,
    val bodyHtml: String,
    val tags: String, // names joined with U+001F
    val isFavorite: Boolean,
    val createdAt: String,
    val updatedAt: String,
) {
    fun tagList(): List<String> = if (tags.isEmpty()) emptyList() else tags.split(TAG_SEP)

    /** The app edits a Nota as plain text: 1st line = title, the rest = body. */
    fun text(): String = listOf(title, htmlToText(bodyHtml)).filter { it.isNotEmpty() }.joinToString("\n")

    /** The cache shows an edit at once, before the PKD has it. */
    fun with(fields: JSONObject) = copy(
        title = fields.optString("title", title),
        bodyHtml = fields.optString("content", bodyHtml),
        tags = fields.optJSONArray("tags")?.strings()?.joinToString(TAG_SEP) ?: tags,
        isFavorite = if (fields.has("favorite")) fields.getBoolean("favorite") else isFavorite,
    )
}

@Entity(tableName = "tags")
data class TagEntity(@PrimaryKey val name: String, val color: String, val textColor: String)

/** Memória (spec §2: create and view). pos = order of GET /api/memories (newest first); negative id = still in the queue. */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: Long,
    val memoryId: String, // MEM-…, empty until the PKD has it
    val title: String,
    val bodyHtml: String,
    val year: Int,
    val month: Int?,
    val day: Int?,
    val hour: Int?,
    val minute: Int?,
    val period: String,
    val pos: Int,
)

/** Node of the Árvore (GET /api/tree, metadata only). pos = pre-order index, so ORDER BY pos is the tree order. */
@Entity(tableName = "docs")
data class DocEntity(@PrimaryKey val id: Long, val parentId: Long?, val title: String, val icon: String, val pos: Int)

/**
 * An opened Documento (spec §5: bodies of opened Documentos, LRU). json = {"doc", "links", "urls", "attachments"}
 * as the PKD answers them.
 */
@Entity(tableName = "doc_bodies")
data class DocBodyEntity(@PrimaryKey val id: Long, val json: String, val openedAt: Long)

/**
 * One item of the Fila de envio (spec §5). kind: `create` (body = POST /api/notes), `patch` (body = PATCH
 * fields), `favorite` (body = {"favorite": wanted state}) or `memory` (body = POST /api/memories). failed = Não enviados.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val noteId: Long,
    val kind: String,
    val body: String,
    val failed: Boolean = false,
    val error: String? = null,
) {
    private val json get() = JSONObject(body)

    /** Title + body, when the item has them (a create, or a text edit). */
    fun noteText(): String? = json.optString("title").ifEmpty { null }?.let { title ->
        listOf(title, htmlToText(json.optString("content"))).filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** Recriar opens the "Nova Nota" sheet, so only Nota items can use it. */
    fun isNote() = kind != "memory"

    fun describe(): String = listOfNotNull(
        if (kind == "memory") "Memória" else null,
        noteText(),
        json.optJSONObject("date")?.let { d -> "Data: %02d/%02d/%d".format(d.optInt("day"), d.optInt("month"), d.optInt("year")) },
        json.optJSONArray("tags")?.let { "Tags: " + it.strings().joinToString(", ") { t -> "#$t" } },
        if (json.has("favorite")) "Favorita: " + if (json.getBoolean("favorite")) "sim" else "não" else null,
    ).joinToString("\n")
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isFavorite DESC, createdAt DESC") fun all(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE id = :id") fun one(id: Long): Flow<NoteEntity?>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun note(id: Long): NoteEntity?
    @Query("SELECT MIN(id) FROM notes") suspend fun minId(): Long?
    @Query("SELECT * FROM tags") fun tags(): Flow<List<TagEntity>>
    @Upsert suspend fun upsert(note: NoteEntity)
    @Insert suspend fun insertNotes(notes: List<NoteEntity>)
    @Insert suspend fun insertTags(tags: List<TagEntity>)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: Long)
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM tags") suspend fun clearTags()

    @Query("SELECT * FROM memories ORDER BY pos") fun memories(): Flow<List<MemoryEntity>>
    @Query("SELECT * FROM memories WHERE id = :id") fun memory(id: Long): Flow<MemoryEntity?>
    @Query("SELECT MIN(id) FROM memories") suspend fun minMemoryId(): Long?
    @Upsert suspend fun upsertMemory(memory: MemoryEntity)
    @Insert suspend fun insertMemories(memories: List<MemoryEntity>)
    @Query("DELETE FROM memories WHERE id = :id") suspend fun deleteMemory(id: Long)
    @Query("DELETE FROM memories") suspend fun clearMemories()

    @Query("SELECT * FROM outbox WHERE failed = 0 ORDER BY seq") suspend fun pending(): List<OutboxEntity>
    @Query("SELECT COUNT(*) FROM outbox WHERE failed = 0") fun pendingCount(): Flow<Int>
    @Query("SELECT * FROM outbox WHERE failed = 1 ORDER BY seq") fun failed(): Flow<List<OutboxEntity>>
    @Query("SELECT COUNT(*) FROM outbox") suspend fun outboxCount(): Int
    @Query("SELECT * FROM outbox WHERE failed = 0 AND noteId = :id AND kind = :kind ORDER BY seq DESC LIMIT 1")
    suspend fun lastPending(id: Long, kind: String): OutboxEntity?
    /** null = nothing in the queue for this Nota; false = waiting; true = Não enviado. */
    @Query("SELECT failed FROM outbox WHERE noteId = :id ORDER BY seq DESC LIMIT 1") suspend fun queueState(id: Long): Boolean?
    @Insert suspend fun insertOutbox(item: OutboxEntity)
    @Update suspend fun updateOutbox(item: OutboxEntity)
    @Query("DELETE FROM outbox WHERE seq = :seq") suspend fun deleteOutbox(seq: Long)
    @Query("DELETE FROM outbox") suspend fun clearOutbox()

    @Query("SELECT * FROM docs ORDER BY pos") fun docs(): Flow<List<DocEntity>>
    @Query("SELECT * FROM notes") suspend fun notesOnce(): List<NoteEntity>
    @Query("SELECT * FROM memories") suspend fun memoriesOnce(): List<MemoryEntity>
    @Query("SELECT * FROM docs") suspend fun docsOnce(): List<DocEntity>
    @Insert suspend fun insertDocs(docs: List<DocEntity>)
    @Query("DELETE FROM docs") suspend fun clearDocs()
    @Query("SELECT * FROM doc_bodies WHERE id = :id") fun docBody(id: Long): Flow<DocBodyEntity?>
    @Upsert suspend fun upsertDocBody(body: DocBodyEntity)
    /** ponytail: LRU of the 50 most recently opened Documentos; a size in bytes if bodies get big. */
    @Query("DELETE FROM doc_bodies WHERE id NOT IN (SELECT id FROM doc_bodies ORDER BY openedAt DESC LIMIT 50)")
    suspend fun trimDocBodies()
    @Query("DELETE FROM doc_bodies") suspend fun clearDocBodies()

    @Transaction suspend fun replaceAll(notes: List<NoteEntity>, memories: List<MemoryEntity>, docs: List<DocEntity>, tags: List<TagEntity>) {
        clearNotes(); insertNotes(notes); clearMemories(); insertMemories(memories)
        clearDocs(); insertDocs(docs); clearTags(); insertTags(tags)
    }

    @Transaction suspend fun replaceTreeAndTags(docs: List<DocEntity>, tags: List<TagEntity>) {
        clearDocs(); insertDocs(docs); clearTags(); insertTags(tags)
    }

    @Transaction suspend fun clearAll() { clearNotes(); clearMemories(); clearDocs(); clearDocBodies(); clearTags(); clearOutbox() }
}

@Database(
    entities = [NoteEntity::class, TagEntity::class, OutboxEntity::class, MemoryEntity::class, DocEntity::class, DocBodyEntity::class],
    version = 4, exportSchema = false,
)
abstract class PkdDb : RoomDatabase() {
    abstract fun dao(): NoteDao
}

/** v4 adds the Árvore and the opened Documentos. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `docs` (`id` INTEGER NOT NULL, `parentId` INTEGER, `title` TEXT NOT NULL, " +
                "`icon` TEXT NOT NULL, `pos` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `doc_bodies` (`id` INTEGER NOT NULL, `json` TEXT NOT NULL, " +
                "`openedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

/** v3 adds Memórias. A real migration: the v2 outbox can hold edits the PKD does not have yet. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `memories` (`id` INTEGER NOT NULL, `memoryId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`bodyHtml` TEXT NOT NULL, `year` INTEGER NOT NULL, `month` INTEGER, `day` INTEGER, `hour` INTEGER, " +
                "`minute` INTEGER, `period` TEXT NOT NULL, `pos` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

// U+FFFC is the placeholder Html.fromHtml leaves for <img>; the card shows it as a box.
fun htmlToText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().replace("\uFFFC", "").trim()

/** Plain text → one `<p>` per line. ponytail: rich formatting from the PWA is lost when the app edits the Nota. */
fun textToHtml(text: String): String = text.lines().joinToString("") { "<p>${TextUtils.htmlEncode(it)}</p>" }

/** Plain text → PKD fields (1st line = title), or null when the 1st line is empty. */
fun textToFields(text: String): JSONObject? {
    val lines = text.trim().lines()
    val title = lines.first().trim().ifEmpty { return null }
    val rest = lines.drop(1).joinToString("\n").trim('\n')
    return JSONObject().put("title", title).put("content", if (rest.isBlank()) "" else textToHtml(rest))
}

private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

private fun JSONObject.mergedWith(other: JSONObject) = JSONObject(toString()).also { m -> other.keys().forEach { m.put(it, other.get(it)) } }

/**
 * Cache of the Notas (Room) + Fila de envio. Every change goes first to the cache and the queue,
 * then the queue goes to the PKD in order. ponytail: no WorkManager — the spec has no background
 * sync, so the queue goes on each change and on each refresh (open, return, pull-to-refresh).
 */
object Notes {
    lateinit var dao: NoteDao
        private set

    /** Outlives screens, so leaving the detail does not cancel a save. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queueLock = Mutex()

    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        if (!::dao.isInitialized) {
            // v1 had only the cache, so dropping it on upgrade loses nothing; from v2 on, migrate (the outbox matters).
            dao = Room.databaseBuilder(context, PkdDb::class.java, "pkd.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationFrom(true, 1)
                .build().dao()
        }
    }

    suspend fun refresh() {
        if (refreshing || !Session.loggedIn) return
        refreshing = true
        error = null
        try {
            val queueEmpty = flush()
            val notes = JSONArray(Api.request("GET", "/api/notes")).objects().map(::noteFromJson)
            val memories = JSONArray(Api.request("GET", "/api/memories")).objects().mapIndexed { pos, o ->
                MemoryEntity(
                    id = o.getLong("id"), memoryId = o.optString("memory_id"), title = o.optString("title"),
                    bodyHtml = o.optString("body_html"), year = o.getInt("year"), month = o.optIntOrNull("month"),
                    day = o.optIntOrNull("day"), hour = o.optIntOrNull("hour"), minute = o.optIntOrNull("minute"),
                    period = o.optString("period"), pos = pos,
                )
            }
            val tags = JSONArray(Api.request("GET", "/api/tags")).objects().map {
                TagEntity(it.getString("name"), it.optString("color"), it.optString("text_color"))
            }
            val docs = mutableListOf<DocEntity>()
            fun walk(nodes: JSONArray, parentId: Long?) {
                for (o in nodes.objects()) {
                    docs += DocEntity(o.getLong("id"), parentId, o.optString("title"), o.optString("icon"), docs.size)
                    o.optJSONArray("children")?.let { walk(it, o.getLong("id")) }
                }
            }
            walk(JSONArray(Api.request("GET", "/api/tree")), null)
            // Items still in the queue live only in the cache: keep it until they go.
            if (queueEmpty) dao.replaceAll(notes, memories, docs, tags) else dao.replaceTreeAndTags(docs, tags)
        } catch (e: Exception) {
            error = e.userMessage()
        } finally {
            refreshing = false
        }
    }

    /**
     * Share (spec §6): Nota #captura. Goes to POST /api/capture, which fetches the Open Graph title of
     * the link; offline, the Nota shows the raw link until the queue sends it.
     */
    suspend fun capture(text: String) {
        val url = Patterns.WEB_URL.matcher(text).let { if (it.find()) it.group() else "" }
        val lines = text.trim().lines()
        // A 1st line that is only the link: let the PKD use the page title.
        val title = lines.first().trim().takeIf { it != url }.orEmpty()
        val rest = lines.drop(1).joinToString("\n").trim()
        val content = if (rest.isEmpty()) "" else textToHtml(rest)
        val tempId = minOf(dao.minId() ?: 0, 0) - 1
        val now = Instant.now().toString()
        dao.upsert(NoteEntity(tempId, title.ifEmpty { url.ifEmpty { "Captura" } }, content, "captura", false, now, now))
        val body = JSONObject().put("title", title).put("content", content).put("url", url)
            .put("idempotency_key", UUID.randomUUID().toString())
        queueLock.withLock { dao.insertOutbox(OutboxEntity(noteId = tempId, kind = "capture", body = body.toString())) }
        flush()
    }

    /** New Nota from plain text; returns false when the 1st line (title) is empty. */
    suspend fun create(text: String): Boolean {
        val fields = textToFields(text) ?: return false
        val tempId = minOf(dao.minId() ?: 0, 0) - 1
        val now = Instant.now().toString()
        dao.upsert(NoteEntity(tempId, "", "", "", false, now, now).with(fields))
        queueLock.withLock {
            dao.insertOutbox(OutboxEntity(noteId = tempId, kind = "create", body = fields.put("idempotency_key", UUID.randomUUID().toString()).toString()))
        }
        flush()
        return true
    }

    /** New Memória (title, details, date). No edit in v1 (spec §9). */
    suspend fun createMemory(title: String, details: String, year: Int, month: Int, day: Int) {
        val tempId = minOf(dao.minMemoryId() ?: 0, 0) - 1
        val content = if (details.isBlank()) "" else textToHtml(details.trim())
        dao.upsertMemory(MemoryEntity(tempId, "", title.trim(), content, year, month, day, null, null, "", -1))
        val body = JSONObject().put("title", title.trim()).put("content", content)
            .put("date", JSONObject().put("year", year).put("month", month).put("day", day))
            .put("idempotency_key", UUID.randomUUID().toString())
        queueLock.withLock { dao.insertOutbox(OutboxEntity(noteId = tempId, kind = "memory", body = body.toString())) }
        flush()
    }

    /** Edit title/content/tags/favorite. Edits waiting in the queue for the same Nota merge into one item. */
    suspend fun edit(id: Long, fields: JSONObject) {
        dao.note(id)?.let { dao.upsert(it.with(fields)) }
        queueLock.withLock {
            val create = if (id < 0) dao.lastPending(id, "create") ?: dao.lastPending(id, "capture") else null
            if (create != null) {
                dao.updateOutbox(create.copy(body = JSONObject(create.body).mergedWith(fields).toString()))
            } else {
                val favorite = if (fields.has("favorite")) fields.remove("favorite") as Boolean else null
                if (fields.length() > 0) enqueue(id, "patch", fields)
                if (favorite != null) enqueue(id, "favorite", JSONObject().put("favorite", favorite))
            }
        }
        flush()
    }

    private suspend fun enqueue(id: Long, kind: String, fields: JSONObject) {
        val last = dao.lastPending(id, kind)
        if (last != null) dao.updateOutbox(last.copy(body = JSONObject(last.body).mergedWith(fields).toString()))
        else dao.insertOutbox(OutboxEntity(noteId = id, kind = kind, body = fields.toString()))
    }

    /**
     * Sends the queue in order. Returns true when it is empty. Stops at the first network/5xx
     * error or 401 (tries again on the next change or refresh); a definitive 4xx moves the item to Não enviados.
     */
    suspend fun flush(): Boolean = queueLock.withLock {
        for (item in dao.pending()) {
            try {
                send(item)
                dao.deleteOutbox(item.seq)
            } catch (e: ApiException) {
                if (e.code !in 400..499 || e.code == 401 || e.code == 429) return@withLock false
                dao.updateOutbox(item.copy(failed = true, error = e.userMessage()))
                if (item.kind == "create" || item.kind == "capture") dao.deleteNote(item.noteId)
                if (item.kind == "memory") dao.deleteMemory(item.noteId)
            } catch (e: IOException) {
                return@withLock false
            }
        }
        true
    }

    private suspend fun send(item: OutboxEntity) {
        val body = JSONObject(item.body)
        when (item.kind) {
            "create" -> {
                val note = noteFromJson(JSONObject(Api.request("POST", "/api/notes", body)))
                dao.deleteNote(item.noteId)
                dao.upsert(note)
            }
            "capture" -> {
                val note = noteFromJson(JSONObject(Api.request("POST", "/api/capture", body)))
                dao.deleteNote(item.noteId)
                dao.upsert(note)
            }
            // The new Memória gets its place in the list on the next refresh; until then it keeps the temp one.
            "memory" -> {
                val d = JSONObject(Api.request("POST", "/api/memories", body))
                dao.deleteMemory(item.noteId)
                dao.upsertMemory(
                    MemoryEntity(
                        id = d.getLong("id"), memoryId = d.optString("memory_id"), title = d.optString("title"),
                        bodyHtml = d.optString("body_html"), year = d.getInt("assoc_year"), month = d.optIntOrNull("assoc_month"),
                        day = d.optIntOrNull("assoc_day"), hour = d.optIntOrNull("memory_hour"),
                        minute = d.optIntOrNull("memory_minute"), period = d.optString("memory_period"), pos = -1,
                    ),
                )
            }
            "patch" -> dao.upsert(noteFromJson(JSONObject(Api.request("PATCH", "/api/notes/${item.noteId}", body))))
            // PATCH ignores `favorite`; the PKD only toggles it, so toggle only when the state differs.
            "favorite" -> {
                val current = JSONObject(Api.request("GET", "/api/notes/${item.noteId}"))
                if (current.optBoolean("is_favorite") != body.getBoolean("favorite")) {
                    Api.request("POST", "/api/documents/${item.noteId}/favorite")
                    dao.upsert(noteFromJson(JSONObject(Api.request("GET", "/api/notes/${item.noteId}"))))
                } else {
                    dao.upsert(noteFromJson(current))
                }
            }
        }
    }

    suspend fun discard(item: OutboxEntity) = queueLock.withLock { dao.deleteOutbox(item.seq) }

    /**
     * Fetches a Documento + its Associações into the LRU cache (read-only, spec §2). A protected
     * Documento without unlock comes with encrypted_locked = true and no body; its Arquivos answer 403.
     */
    suspend fun openDoc(id: Long) {
        val doc = JSONObject(Api.request("GET", "/api/documents/$id"))
        // Go answers `null` for an empty list.
        fun array(text: String) = if (text.isBlank() || text.trim() == "null") JSONArray() else JSONArray(text)
        val links = JSONObject(Api.request("GET", "/api/documents/$id/links")).optJSONArray("related") ?: JSONArray()
        val urls = array(Api.request("GET", "/api/documents/$id/urls"))
        val attachments = if (doc.optBoolean("encrypted_locked")) JSONArray() else array(Api.request("GET", "/api/documents/$id/attachments"))
        val json = JSONObject().put("doc", doc).put("links", links).put("urls", urls).put("attachments", attachments)
        dao.upsertDocBody(DocBodyEntity(id, json.toString(), System.currentTimeMillis()))
        dao.trimDocBodies()
    }

    enum class Kind { Nota, Memoria, Documento }

    data class Hit(val id: Long, val title: String, val kind: Kind)

    /**
     * Busca (spec §3/§5): the PKD hybrid search (`GET /api/tree?q=`, Notas and Memórias flagged). Without a
     * connection, a local search over the cache (titles; bodies of Notas and Memórias) — partial = true.
     */
    suspend fun search(q: String): Pair<List<Hit>, Boolean> = try {
        val list = JSONArray(Api.request("GET", "/api/tree?q=" + java.net.URLEncoder.encode(q, "UTF-8"))).objects().map {
            val kind = when {
                it.optBoolean("is_note") -> Kind.Nota
                it.optBoolean("is_memory") -> Kind.Memoria
                else -> Kind.Documento
            }
            Hit(it.getLong("id"), it.optString("title"), kind)
        }
        list to false
    } catch (e: ApiException) {
        throw e
    } catch (e: IOException) {
        localSearch(q) to true
    }

    private suspend fun localSearch(q: String): List<Hit> {
        val needle = fold(q)
        fun match(vararg s: String) = s.any { fold(it).contains(needle) }
        return dao.notesOnce().filter { match(it.title, htmlToText(it.bodyHtml)) }.map { Hit(it.id, it.title, Kind.Nota) } +
            dao.memoriesOnce().filter { match(it.title, htmlToText(it.bodyHtml)) }.map { Hit(it.id, it.title, Kind.Memoria) } +
            dao.docsOnce().filter { match(it.title) }.map { Hit(it.id, it.title, Kind.Documento) }
    }

    /** Lowercase without accents, so "memoria" finds "Memória". */
    private fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

    suspend fun logout() {
        runCatching { Api.request("POST", "/api/logout") }
        dao.clearAll()
        Session.clear()
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun noteFromJson(o: JSONObject) = NoteEntity(
        id = o.getLong("id"),
        title = o.optString("title"),
        bodyHtml = o.optString("body_html"),
        tags = o.optJSONArray("tags")?.strings().orEmpty().joinToString(TAG_SEP),
        isFavorite = o.optBoolean("is_favorite"),
        createdAt = o.optString("created_at"),
        updatedAt = o.optString("updated_at"),
    )
}
