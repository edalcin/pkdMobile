package `in`.dalc.pkdmobile.data

import android.content.Context
import android.text.Html
import android.text.TextUtils
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

/**
 * One item of the Fila de envio (spec §5). kind: `create` (body = POST /api/notes), `patch`
 * (body = PATCH fields) or `favorite` (body = {"favorite": wanted state}). failed = Não enviados.
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

    fun describe(): String = listOfNotNull(
        noteText(),
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

    @Transaction suspend fun replaceAll(notes: List<NoteEntity>, tags: List<TagEntity>) {
        clearNotes(); insertNotes(notes); clearTags(); insertTags(tags)
    }

    @Transaction suspend fun replaceTags(tags: List<TagEntity>) { clearTags(); insertTags(tags) }

    @Transaction suspend fun clearAll() { clearNotes(); clearTags(); clearOutbox() }
}

@Database(entities = [NoteEntity::class, TagEntity::class, OutboxEntity::class], version = 2, exportSchema = false)
abstract class PkdDb : RoomDatabase() {
    abstract fun dao(): NoteDao
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
            // v1 had only the cache, so dropping it on upgrade loses nothing.
            dao = Room.databaseBuilder(context, PkdDb::class.java, "pkd.db").fallbackToDestructiveMigration(true).build().dao()
        }
    }

    suspend fun refresh() {
        if (refreshing || !Session.loggedIn) return
        refreshing = true
        error = null
        try {
            val queueEmpty = flush()
            val notes = JSONArray(Api.request("GET", "/api/notes")).objects().map(::noteFromJson)
            val tags = JSONArray(Api.request("GET", "/api/tags")).objects().map {
                TagEntity(it.getString("name"), it.optString("color"), it.optString("text_color"))
            }
            // Items still in the queue live only in the cache: keep it until they go.
            if (queueEmpty) dao.replaceAll(notes, tags) else dao.replaceTags(tags)
        } catch (e: Exception) {
            error = e.userMessage()
        } finally {
            refreshing = false
        }
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

    /** Edit title/content/tags/favorite. Edits waiting in the queue for the same Nota merge into one item. */
    suspend fun edit(id: Long, fields: JSONObject) {
        dao.note(id)?.let { dao.upsert(it.with(fields)) }
        queueLock.withLock {
            val create = if (id < 0) dao.lastPending(id, "create") else null
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
                if (item.kind == "create") dao.deleteNote(item.noteId)
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
