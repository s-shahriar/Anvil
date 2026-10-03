package com.syed.anvil.highlight

import android.content.Context
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.backend.Postgrest
import com.syed.anvil.backend.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** What a selection becomes: one block's worth of marked text. */
class Anchored(val block: String, val start: Int, val end: Int, val quote: String)

/**
 * A module's text highlights (`user_highlights`, the same rows the web apps use). Highlighting is instant and local; each
 * change is queued and pushed in the background, so it works offline and signed out. Ids are generated here (valid
 * UUIDs) so a new highlight never needs renaming after it reaches the server.
 */
class HighlightRepository(
    context: Context,
    module: ModuleId,
    private val auth: SupabaseAuth,
    private val db: Postgrest,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "highlights_${module.key}.json")
    private val items = LinkedHashMap<String, Highlight>()
    private val addIds = LinkedHashSet<String>()
    private val deleteIds = LinkedHashSet<String>()
    private val edits = LinkedHashMap<String, String>()
    private val lock = Any()
    private val _byUid = MutableStateFlow<Map<String, List<Highlight>>>(emptyMap())
    /** Every highlight, grouped by question uid. */
    val byUid: StateFlow<Map<String, List<Highlight>>> = _byUid
    private val _unsynced = MutableStateFlow(0)
    val unsynced: StateFlow<Int> = _unsynced
    private var job: Job? = null

    init {
        runCatching {
            val o = JSONObject(file.readText())
            o.getJSONArray("items").let { a -> for (i in 0 until a.length()) Highlight.fromJson(a.getJSONObject(i)).let { items[it.id] = it } }
            o.getJSONArray("adds").let { a -> for (i in 0 until a.length()) addIds.add(a.getString(i)) }
            o.getJSONArray("deletes").let { a -> for (i in 0 until a.length()) deleteIds.add(a.getString(i)) }
            o.getJSONObject("edits").let { e -> e.keys().forEach { edits[it] = e.getString(it) } }
        }
        publish()
        if (pending() > 0) kick()
    }

    private fun pending() = addIds.size + deleteIds.size + edits.size

    private fun publish() {
        _byUid.value = items.values.groupBy { it.uid }
        _unsynced.value = pending()
    }

    private fun persist() {
        file.writeText(JSONObject()
            .put("items", JSONArray(items.values.map { it.toJson() }))
            .put("adds", JSONArray(addIds.toList())).put("deletes", JSONArray(deleteIds.toList()))
            .put("edits", JSONObject(edits as Map<*, *>)).toString())
        publish()
    }

    fun forUid(uid: String): List<Highlight> = _byUid.value[uid].orEmpty()

    fun add(uid: String, anchors: List<Anchored>, color: String) {
        if (anchors.isEmpty()) return
        synchronized(lock) {
            for (a in anchors) {
                val h = Highlight(UUID.randomUUID().toString(), uid, a.block, a.start, a.end, a.quote, color)
                items[h.id] = h; addIds.add(h.id)
            }
            persist()
        }
        kick()
    }

    fun remove(ids: List<String>) {
        synchronized(lock) {
            for (id in ids) {
                items.remove(id); edits.remove(id)
                if (!addIds.remove(id)) deleteIds.add(id) // never reached the server: it simply vanishes
            }
            persist()
        }
        kick()
    }

    fun recolor(ids: List<String>, color: String) {
        synchronized(lock) {
            for (id in ids) {
                val h = items[id] ?: continue
                items[id] = h.copy(color = color)
                if (id !in addIds) edits[id] = color
            }
            persist()
        }
        kick()
    }

    /** Replaces local state with the server's, then re-applies what has not been sent yet. */
    suspend fun pull() {
        if (auth.session.value == null) return
        val rows = db.selectAll("user_highlights", "select=id,uid,block,start_off,end_off,quote,color&order=id")
        synchronized(lock) {
            val server = LinkedHashMap<String, Highlight>()
            for (r in rows) server[r.getString("id")] = Highlight(r.getString("id"), r.getString("uid"), r.getString("block"), r.getInt("start_off"), r.getInt("end_off"), r.getString("quote"), r.optString("color", DEFAULT_HIGHLIGHT_COLOR))
            deleteIds.forEach { server.remove(it) }
            edits.forEach { (id, c) -> server[id]?.let { server[id] = it.copy(color = c) } }
            addIds.forEach { id -> items[id]?.let { server[id] = it } }
            items.clear(); items.putAll(server)
            persist()
        }
    }

    fun kick() {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(500)
            val backoff = longArrayOf(4_000, 12_000, 30_000, 60_000, 300_000)
            var attempt = 0
            while (true) {
                val session = auth.session.value ?: return@launch
                val (adds, dels, eds) = synchronized(lock) { Triple(addIds.mapNotNull { items[it] }, deleteIds.toList(), edits.toMap()) }
                if (adds.isEmpty() && dels.isEmpty() && eds.isEmpty()) return@launch
                val ok = runCatching {
                    if (adds.isNotEmpty()) {
                        db.insert("user_highlights", JSONArray(adds.map {
                            JSONObject().put("id", it.id).put("user_id", session.userId).put("uid", it.uid).put("block", it.block)
                                .put("start_off", it.start).put("end_off", it.end).put("quote", it.quote).put("color", it.color)
                        }))
                        synchronized(lock) { adds.forEach { addIds.remove(it.id) }; persist() }
                    }
                    if (dels.isNotEmpty()) {
                        db.delete("user_highlights", "id=in.(${dels.joinToString(",")})")
                        synchronized(lock) { deleteIds.removeAll(dels.toSet()); persist() }
                    }
                    eds.entries.groupBy({ it.value }, { it.key }).forEach { (color, ids) ->
                        db.patch("user_highlights", "id=in.(${ids.joinToString(",")})", JSONObject().put("color", color))
                        synchronized(lock) { ids.forEach { id -> if (edits[id] == color) edits.remove(id) }; persist() }
                    }
                }.isSuccess
                if (ok) attempt = 0 else delay(backoff[minOf(attempt++, backoff.lastIndex)])
            }
        }
    }
}
