package com.syed.slate.highlight

import android.content.Context
import com.syed.slate.backend.ModuleId
import com.syed.slate.backend.Postgrest
import com.syed.slate.backend.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.syed.slate.core.writeAtomic
import java.io.File
import java.util.UUID

/** What a selection becomes: one block's worth of marked text. */
class Anchored(val block: String, val start: Int, val end: Int, val quote: String)

enum class HlKind { ADD, REMOVE, RECOLOR }

/** One highlight change, as the sync queue shows it. [h] is the highlight as it is now (as it was, for REMOVE); [from] is the colour before a RECOLOR. */
class HlOp(val h: Highlight, val kind: HlKind, val from: String?, val at: Long) {
    val id get() = h.id
}

/** A change that reached the server this session (the queue's "Synced" receipt). */
class HlDone(val op: HlOp, val syncedAt: Long)

/**
 * A module's text highlights (`user_highlights`, the same rows the web apps use). Highlighting is instant and local; each
 * change is queued and pushed in the background, so it works offline and signed out. Ids are generated here (valid
 * UUIDs) so a new highlight never needs renaming after it reaches the server. Every pending change is also exposed as an
 * [HlOp] (with enough to reverse it) and, once sent, as an [HlDone], so the sync-queue sheet can list and undo them.
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
    /** Deleted on this device, not yet on the server; the highlight itself is kept so the delete can be undone. */
    private val deleted = LinkedHashMap<String, Highlight>()
    private val edits = LinkedHashMap<String, String>()
    /** Deletes an older build saved as bare ids; they still go out, they just cannot be undone. Declared before `init`, which fills it. */
    private val legacyDeletes = LinkedHashSet<String>()
    /** The colour a pending recolour started from, so undoing it can cancel the edit instead of queueing another. */
    private val editFrom = HashMap<String, String>()
    private val stamps = HashMap<String, Long>()
    /** Unsaved work: nothing here is queued or sent until [save]. New highlights, committed ones hidden, committed ones recoloured. */
    private val dAdds = LinkedHashMap<String, Highlight>()
    private val dDeletes = LinkedHashSet<String>()
    private val dEdits = LinkedHashMap<String, String>()
    private val lock = Any()
    private val _byUid = MutableStateFlow<Map<String, List<Highlight>>>(emptyMap())
    /** Every highlight, grouped by question uid. */
    val byUid: StateFlow<Map<String, List<Highlight>>> = _byUid
    private val _draftCount = MutableStateFlow(0)
    /** How many highlight changes are waiting for Save. */
    val draftCount: StateFlow<Int> = _draftCount
    private val _unsynced = MutableStateFlow(0)
    val unsynced: StateFlow<Int> = _unsynced
    private val _queue = MutableStateFlow<List<HlOp>>(emptyList())
    /** Changes waiting to be sent, oldest first. */
    val queue: StateFlow<List<HlOp>> = _queue
    private val _done = MutableStateFlow<List<HlDone>>(emptyList())
    val done: StateFlow<List<HlDone>> = _done
    private val _failure = MutableStateFlow<String?>(null)
    /** Why the last push failed (null while things are fine). */
    val failure: StateFlow<String?> = _failure
    private var job: Job? = null

    init {
        runCatching {
            val o = JSONObject(file.readText())
            o.getJSONArray("items").let { a -> for (i in 0 until a.length()) Highlight.fromJson(a.getJSONObject(i)).let { items[it.id] = it } }
            o.getJSONArray("adds").let { a -> for (i in 0 until a.length()) addIds.add(a.getString(i)) }
            o.optJSONArray("deletedItems")?.let { a -> for (i in 0 until a.length()) Highlight.fromJson(a.getJSONObject(i)).let { deleted[it.id] = it } }
            o.optJSONObject("editFrom")?.let { e -> e.keys().forEach { editFrom[it] = e.getString(it) } }
            o.optJSONObject("stamps")?.let { e -> e.keys().forEach { stamps[it] = e.getLong(it) } }
            o.getJSONObject("edits").let { e -> e.keys().forEach { edits[it] = e.getString(it) } }
            o.optJSONObject("draft")?.let { d ->
                d.optJSONArray("adds")?.let { a -> for (i in 0 until a.length()) Highlight.fromJson(a.getJSONObject(i)).let { dAdds[it.id] = it } }
                d.optJSONArray("deletes")?.let { a -> for (i in 0 until a.length()) dDeletes.add(a.getString(i)) }
                d.optJSONObject("edits")?.let { e -> e.keys().forEach { dEdits[it] = e.getString(it) } }
            }
            // An older file kept deletes as bare ids; they still go out, they just cannot be undone.
            o.optJSONArray("deletes")?.let { a -> for (i in 0 until a.length()) a.getString(i).let { id -> if (id !in deleted) legacyDeletes.add(id) } }
        }
        publish()
        if (pending() > 0) kick()
    }

    private fun pending() = addIds.size + deleted.size + legacyDeletes.size + edits.size

    private fun opsNow(): List<HlOp> {
        val out = ArrayList<HlOp>()
        for (id in addIds) items[id]?.let { out += HlOp(it, HlKind.ADD, null, stamps[id] ?: 0) }
        for ((id, h) in deleted) out += HlOp(h, HlKind.REMOVE, null, stamps[id] ?: 0)
        for ((id, c) in edits) items[id]?.let { out += HlOp(it.copy(color = c), HlKind.RECOLOR, editFrom[id], stamps[id] ?: 0) }
        return out.sortedBy { it.at }
    }

    private fun publish() {
        // What the screen shows: the committed highlights with the draft laid over them.
        val view = ArrayList<Highlight>(items.size + dAdds.size)
        for (h in items.values) { if (h.id in dDeletes) continue; view += dEdits[h.id]?.let { h.copy(color = it) } ?: h }
        view += dAdds.values
        _draftCount.value = dAdds.size + dDeletes.size + dEdits.size
        _byUid.value = view.groupBy { it.uid }
        _unsynced.value = pending()
        _queue.value = opsNow()
    }

    private fun persist() {
        file.writeAtomic(JSONObject()
            .put("items", JSONArray(items.values.map { it.toJson() }))
            .put("adds", JSONArray(addIds.toList()))
            .put("deletedItems", JSONArray(deleted.values.map { it.toJson() }))
            .put("deletes", JSONArray(legacyDeletes.toList()))
            .put("edits", JSONObject(edits as Map<*, *>))
            .put("editFrom", JSONObject(editFrom as Map<*, *>))
            .put("stamps", JSONObject(stamps as Map<*, *>))
            .put("draft", JSONObject().put("adds", JSONArray(dAdds.values.map { it.toJson() })).put("deletes", JSONArray(dDeletes.toList())).put("edits", JSONObject(dEdits as Map<*, *>)))
            .toString())
        publish()
    }

    fun forUid(uid: String): List<Highlight> = _byUid.value[uid].orEmpty()

    /** A new highlight. It is only a draft until [save]. */
    fun add(uid: String, anchors: List<Anchored>, color: String) {
        if (anchors.isEmpty()) return
        synchronized(lock) {
            for (a in anchors) { val h = Highlight(UUID.randomUUID().toString(), uid, a.block, a.start, a.end, a.quote, color); dAdds[h.id] = h }
            persist()
        }
    }

    /** Removes highlights from view. Unsaved ones simply vanish; saved ones are hidden until [save] makes the delete real. */
    fun remove(ids: List<String>) {
        synchronized(lock) {
            for (id in ids) {
                if (dAdds.remove(id) != null) continue
                if (id in items) { dDeletes.add(id); dEdits.remove(id) }
            }
            persist()
        }
    }

    /** Recolours highlights in the draft; [save] makes it real. */
    fun recolor(ids: List<String>, color: String) {
        synchronized(lock) {
            for (id in ids) {
                val draft = dAdds[id]
                if (draft != null) { dAdds[id] = draft.copy(color = color); continue }
                val h = items[id] ?: continue
                if (id in dDeletes) continue
                if (h.color == color) dEdits.remove(id) else dEdits[id] = color
            }
            persist()
        }
    }

    /** Commits the draft to the sync queue (offline-tolerant, shown in the sync sheet) and sends it. */
    fun save() {
        synchronized(lock) {
            if (dAdds.isEmpty() && dDeletes.isEmpty() && dEdits.isEmpty()) return
            val now = System.currentTimeMillis()
            for (h in dAdds.values) { items[h.id] = h; addIds.add(h.id); stamps[h.id] = now }
            val deletes = dDeletes.toList(); val recolours = dEdits.toMap()
            dAdds.clear(); dDeletes.clear(); dEdits.clear()
            removeNow(deletes); for ((id, c) in recolours) recolorNow(listOf(id), c)
            persist()
        }
        kick()
    }

    /** Drops the draft without saving anything. */
    fun discard() {
        synchronized(lock) { dAdds.clear(); dDeletes.clear(); dEdits.clear(); persist() }
    }

    // The queue-level operations: they change what will be sent. Used by [save] and by Undo in the sync sheet.
    private fun removeNow(ids: List<String>) {
        val now = System.currentTimeMillis()
        for (id in ids) {
            val h = items.remove(id) ?: continue
            edits.remove(id); editFrom.remove(id)
            if (addIds.remove(id)) stamps.remove(id) // never reached the server: it simply vanishes
            else { deleted[id] = h; stamps[id] = now }
        }
    }

    private fun recolorNow(ids: List<String>, color: String) {
        val now = System.currentTimeMillis()
        for (id in ids) {
            val h = items[id] ?: continue
            if (h.color == color) continue
            items[id] = h.copy(color = color)
            if (id in addIds) continue // still an insert: it will go out with the new colour
            val origin = editFrom.getOrPut(id) { h.color }
            if (origin == color) { edits.remove(id); editFrom.remove(id); stamps.remove(id) } // back to what the server has
            else { edits[id] = color; stamps[id] = now }
        }
    }

    /** Brings a removed highlight back: cancels its pending delete, or re-inserts it if the delete already went through. */
    private fun restore(h: Highlight) {
        synchronized(lock) {
            if (h.id in items) return
            items[h.id] = h
            if (deleted.remove(h.id) == null && !legacyDeletes.remove(h.id)) { addIds.add(h.id); stamps[h.id] = System.currentTimeMillis() } else stamps.remove(h.id)
            persist()
        }
        kick()
    }

    /** Whether [op] still describes the highlight's state, i.e. whether undoing it makes sense. */
    fun canUndo(op: HlOp): Boolean = synchronized(lock) {
        when (op.kind) {
            HlKind.ADD -> op.id in items
            HlKind.REMOVE -> op.id !in items
            HlKind.RECOLOR -> items[op.id]?.color == op.h.color && op.from != null
        }
    }

    fun undo(op: HlOp) {
        if (!canUndo(op)) return
        when (op.kind) {
            HlKind.ADD -> { synchronized(lock) { removeNow(listOf(op.id)); persist() }; kick() }
            HlKind.REMOVE -> restore(op.h)
            HlKind.RECOLOR -> { synchronized(lock) { recolorNow(listOf(op.id), op.from!!); persist() }; kick() }
        }
    }

    /** Replaces local state with the server's, then re-applies what has not been sent yet. */
    /** Adds / deletes that reached the server, numbered, so a [pull] that overlapped them keeps them. */
    private var flushGen = 0L
    private val landedAdds = ArrayDeque<Pair<Long, List<Highlight>>>()
    private val landedDels = ArrayDeque<Pair<Long, List<String>>>()

    suspend fun pull() {
        if (auth.session.value == null) return
        val startGen = synchronized(lock) { flushGen }
        val rows = db.selectAll("user_highlights", "select=id,uid,block,start_off,end_off,quote,color&order=id")
        synchronized(lock) {
            val server = LinkedHashMap<String, Highlight>()
            for (r in rows) server[r.getString("id")] = Highlight(r.getString("id"), r.getString("uid"), r.getString("block"), r.getInt("start_off"), r.getInt("end_off"), r.getString("quote"), r.optString("color", DEFAULT_HIGHLIGHT_COLOR))
            // Sent while the rows were being read: they may be missing from (or still in) them, and are no longer queued.
            landedAdds.filter { it.first > startGen }.forEach { (_, hs) -> hs.forEach { h -> if (h.id !in server) items[h.id]?.let { server[h.id] = it } } }
            landedDels.filter { it.first > startGen }.forEach { (_, ids) -> ids.forEach { server.remove(it) } }
            deleted.keys.forEach { server.remove(it) }; legacyDeletes.forEach { server.remove(it) }
            edits.forEach { (id, c) -> server[id]?.let { server[id] = it.copy(color = c) } }
            addIds.forEach { id -> items[id]?.let { server[id] = it } }
            items.clear(); items.putAll(server)
            persist()
        }
    }

    /** Clears any backoff wait and flushes immediately (connection back, or the drawer's "Retry now"). */
    fun retryNow() { job?.cancel(); job = null; kick() }

    fun kick() {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(500)
            val backoff = longArrayOf(4_000, 12_000, 30_000, 60_000, 300_000)
            var attempt = 0
            while (true) {
                val session = auth.session.value ?: return@launch
                val (adds, dels, eds) = synchronized(lock) { Triple(addIds.mapNotNull { items[it] }, deleted.keys.toList() + legacyDeletes, edits.toMap()) }
                if (adds.isEmpty() && dels.isEmpty() && eds.isEmpty()) return@launch
                val ok = runCatching {
                    if (adds.isNotEmpty()) {
                        // Upsert by id: if an earlier attempt landed but its reply was lost, the retry must not collide with itself.
                        db.upsert("user_highlights", JSONArray(adds.map {
                            JSONObject().put("id", it.id).put("user_id", session.userId).put("uid", it.uid).put("block", it.block)
                                .put("start_off", it.start).put("end_off", it.end).put("quote", it.quote).put("color", it.color)
                        }), "id")
                        val receipts = synchronized(lock) { adds.map { h -> HlOp(items[h.id] ?: h, HlKind.ADD, null, stamps[h.id] ?: 0) }.also { adds.forEach { addIds.remove(it.id); stamps.remove(it.id) }; persist(); landedAdds.addLast(++flushGen to adds); while (landedAdds.size > 20) landedAdds.removeFirst() } }
                        receipt(receipts)
                    }
                    if (dels.isNotEmpty()) {
                        db.delete("user_highlights", "id=in.(${dels.joinToString(",")})")
                        val receipts = synchronized(lock) { dels.mapNotNull { id -> deleted[id]?.let { HlOp(it, HlKind.REMOVE, null, stamps[id] ?: 0) } }.also { deleted.keys.removeAll(dels.toSet()); legacyDeletes.removeAll(dels.toSet()); dels.forEach { stamps.remove(it) }; persist(); landedDels.addLast(++flushGen to dels); while (landedDels.size > 20) landedDels.removeFirst() } }
                        receipt(receipts)
                    }
                    eds.entries.groupBy({ it.value }, { it.key }).forEach { (color, ids) ->
                        db.patch("user_highlights", "id=in.(${ids.joinToString(",")})", JSONObject().put("color", color))
                        val receipts = synchronized(lock) {
                            ids.mapNotNull { id -> items[id]?.takeIf { edits[id] == color }?.let { HlOp(it.copy(color = color), HlKind.RECOLOR, editFrom[id], stamps[id] ?: 0) } }
                                .also { ids.forEach { id -> if (edits[id] == color) { edits.remove(id); editFrom.remove(id); stamps.remove(id) } }; persist() }
                        }
                        receipt(receipts)
                    }
                }.onFailure { _failure.value = it.message ?: "Could not reach the server" }.isSuccess
                if (ok) { attempt = 0; _failure.value = null } else delay(backoff[minOf(attempt++, backoff.lastIndex)])
            }
        }
    }

    private fun receipt(ops: List<HlOp>) {
        if (ops.isEmpty()) return
        val now = System.currentTimeMillis()
        _done.value = (ops.map { HlDone(it, now) } + _done.value).take(50)
    }
}
