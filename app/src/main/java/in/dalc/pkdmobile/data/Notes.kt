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
import androidx.room.Upsert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

private const val TAG_SEP = "\u001f"

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: Long,
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
}

@Entity(tableName = "tags")
data class TagEntity(@PrimaryKey val name: String, val color: String, val textColor: String)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isFavorite DESC, createdAt DESC") fun all(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE id = :id") fun one(id: Long): Flow<NoteEntity?>
    @Query("SELECT * FROM tags") fun tags(): Flow<List<TagEntity>>
    @Upsert suspend fun upsert(note: NoteEntity)
    @Insert suspend fun insertNotes(notes: List<NoteEntity>)
    @Insert suspend fun insertTags(tags: List<TagEntity>)
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM tags") suspend fun clearTags()

    @Transaction suspend fun replaceAll(notes: List<NoteEntity>, tags: List<TagEntity>) {
        clearNotes(); insertNotes(notes); clearTags(); insertTags(tags)
    }

    @Transaction suspend fun clearAll() { clearNotes(); clearTags() }
}

@Database(entities = [NoteEntity::class, TagEntity::class], version = 1, exportSchema = false)
abstract class PkdDb : RoomDatabase() {
    abstract fun dao(): NoteDao
}

fun htmlToText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

/** Plain text → one `<p>` per line. ponytail: rich formatting from the PWA is lost when the app edits the Nota. */
fun textToHtml(text: String): String = text.lines().joinToString("") { "<p>${TextUtils.htmlEncode(it)}</p>" }

/** Cache of the Notas (Room). The server list is the truth: every refresh replaces the whole cache (spec §5). */
object Notes {
    lateinit var dao: NoteDao
        private set

    /** Outlives screens, so leaving the detail does not cancel a save. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        if (!::dao.isInitialized) dao = Room.databaseBuilder(context, PkdDb::class.java, "pkd.db").build().dao()
    }

    suspend fun refresh() {
        if (refreshing || !Session.loggedIn) return
        refreshing = true
        error = null
        try {
            val notes = JSONArray(Api.request("GET", "/api/notes")).objects().map(::noteFromJson)
            val tags = JSONArray(Api.request("GET", "/api/tags")).objects().map {
                TagEntity(it.getString("name"), it.optString("color"), it.optString("text_color"))
            }
            dao.replaceAll(notes, tags)
        } catch (e: Exception) {
            error = e.userMessage()
        } finally {
            refreshing = false
        }
    }

    /** PATCH /api/notes/{id} (title, content, tags); the cache takes the Nota the PKD answers. Throws on failure (no queue until slice 3). */
    suspend fun patch(id: Long, body: JSONObject) {
        dao.upsert(noteFromJson(JSONObject(Api.request("PATCH", "/api/notes/$id", body))))
    }

    suspend fun logout() {
        runCatching { Api.request("POST", "/api/logout") }
        dao.clearAll()
        Session.clear()
    }

    /** PATCH ignores `favorite`; the PKD toggles it at POST /api/documents/{id}/favorite. */
    suspend fun toggleFavorite(id: Long) {
        Api.request("POST", "/api/documents/$id/favorite")
        dao.upsert(noteFromJson(JSONObject(Api.request("GET", "/api/notes/$id"))))
    }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun noteFromJson(o: JSONObject): NoteEntity {
        val tags = o.optJSONArray("tags")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        return NoteEntity(
            id = o.getLong("id"),
            title = o.optString("title"),
            bodyHtml = o.optString("body_html"),
            tags = tags.joinToString(TAG_SEP),
            isFavorite = o.optBoolean("is_favorite"),
            createdAt = o.optString("created_at"),
            updatedAt = o.optString("updated_at"),
        )
    }
}
