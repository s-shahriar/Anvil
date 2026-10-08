package com.syed.slate.content

import android.content.Context
import com.syed.slate.backend.ModuleId
import com.syed.slate.backend.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

sealed interface ContentState {
    data object NotLoaded : ContentState
    /** Nothing cached yet: the first download needs a connection. */
    data object Empty : ContentState
    class Ready(val content: ModuleContent) : ContentState
}

/** What the last refresh changed in `questions`: shown after a sync ("12 new"). A first download counts all as added. */
data class Delta(val added: Int, val updated: Int, val removed: Int) {
    val isEmpty get() = added == 0 && updated == 0 && removed == 0
}

sealed interface SyncState {
    data object Idle : SyncState
    class Running(val message: String) : SyncState
    class Failed(val message: String) : SyncState
}

/**
 * Offline-first content for one module.
 *
 * The whole module (a few MB) is stored as a JSONL file in app storage. Everything the UI reads comes from
 * that file, so the app works with no network once a module has been downloaded. [refresh] replaces it
 * atomically, so a download that dies half-way never damages the copy being read.
 */
class ContentRepository(
    context: Context,
    val module: ModuleId,
    private val db: Postgrest,
    /** Runs after a successful download, e.g. to fetch the images the new content references. */
    private val onRefreshed: (ModuleContent) -> Unit = {},
) {
    private val file = File(context.filesDir, "content_${module.key}.jsonl")
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ContentState>(ContentState.NotLoaded)
    val state: StateFlow<ContentState> = _state
    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val sync: StateFlow<SyncState> = _sync

    /** The outcome of the last successful refresh: what it added, updated and removed. */
    class Refreshed(val delta: Delta, val firstDownload: Boolean, val at: Long)
    private val refreshPrefs = context.getSharedPreferences("refresh_${module.key}", Context.MODE_PRIVATE)
    private val _lastRefresh = MutableStateFlow(
        refreshPrefs.getLong("at", 0L).takeIf { it > 0 }?.let {
            Refreshed(Delta(refreshPrefs.getInt("added", 0), refreshPrefs.getInt("updated", 0), refreshPrefs.getInt("removed", 0)), refreshPrefs.getBoolean("first", false), it)
        },
    )
    /** The last successful refresh (kept across restarts); a new value means a refresh just finished. */
    val lastRefresh: StateFlow<Refreshed?> = _lastRefresh

    /** The complete content; [state] shows it minus the questions in the recycle bin. */
    private var full: ModuleContent? = null
    private var hiddenIds: Set<String> = emptySet()

    private fun publish() {
        val f = full ?: return
        _state.value = ContentState.Ready(if (hiddenIds.isEmpty()) f else f.without(hiddenIds))
    }

    /** Follows the recycle bin: questions with these ids disappear from every list at once. */
    fun bindHidden(scope: kotlinx.coroutines.CoroutineScope, ids: StateFlow<Set<String>>) {
        scope.launch { ids.collect { hiddenIds = it; withContext(Dispatchers.Default) { publish() } } }
    }

    /** Questions hidden locally, for the recycle bin screen. */
    fun hiddenItems(): List<Item> = full?.allItems()?.filter { it.id in hiddenIds }?.toList().orEmpty()

    val cacheBytes: Long get() = if (file.exists()) file.length() else 0L
    val cachedAt: Long get() = if (file.exists()) file.lastModified() else 0L

    /** Reads the offline copy, if there is one. Never touches the network. */
    suspend fun load() = mutex.withLock {
        if (_state.value is ContentState.Ready) return@withLock
        val loaded = withContext(Dispatchers.IO) {
            // A corrupt cache is treated as no cache; the next download rewrites it.
            runCatching { if (file.exists()) readCache() else null }.getOrElse { file.delete(); null }
        }
        full = loaded
        if (loaded != null) publish() else _state.value = ContentState.Empty
    }

    /**
     * Brings the offline copy up to date. The first download pulls everything; after that only what actually
     * changed: a light probe (uid + placement of every live row) is compared against the cache, and just the
     * differing rows are fetched. Uids are hashes of the content, so any edit shows up as a new uid.
     */
    suspend fun refresh() = mutex.withLock {
        _sync.value = SyncState.Running("Checking for changes…")
        try {
            val cached = full
            lastDelta = null
            val fresh = if (cached == null || cached.version < CACHE_VERSION) download() else delta(cached)
            withContext(Dispatchers.IO) { writeCache(fresh) }
            full = fresh; publish()
            // A version upgrade re-downloads everything once; report it as a download, not as thousands of "new".
            val r = Refreshed(lastDelta ?: Delta(fresh.allItems().count(), 0, 0), firstDownload = lastDelta == null, at = System.currentTimeMillis())
            refreshPrefs.edit().putInt("added", r.delta.added).putInt("updated", r.delta.updated).putInt("removed", r.delta.removed)
                .putBoolean("first", r.firstDownload).putLong("at", r.at).apply()
            _lastRefresh.value = r
            _sync.value = SyncState.Idle
            onRefreshed(fresh)
        } catch (e: Exception) {
            _sync.value = SyncState.Failed(e.message ?: "Download failed")
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        full = null
        _state.value = ContentState.Empty
        _sync.value = SyncState.Idle
    }

    // ── network ──────────────────────────────────────────────────────────

    private suspend fun download(): ModuleContent {
        val now = System.currentTimeMillis()
        return when (module) {
            ModuleId.GENERAL -> downloadGeneral(now)
            ModuleId.ICT -> downloadIct(now)
        }
    }

    private fun progress(what: String, n: Int) { _sync.value = SyncState.Running("$what · $n") }

    private suspend fun downloadGeneral(now: Long): ModuleContent {
        val cats = db.selectAll("categories", "select=slug,module,name,sort_order&order=sort_order,slug")
        val names = HashMap<String, String>(); val sort = HashMap<String, Int>()
        for (c in cats) {
            val key = "${c.getString("module")}/${c.getString("slug")}"
            names[key] = c.optString("name"); sort[key] = c.optInt("sort_order")
        }
        val subs = db.selectAll("subtopics", "select=slug,name,sort_order,categories(slug)&order=sort_order,id")
        val wcats = db.selectAll("written_categories", "select=id,name,color,sort_order&topic=eq.data&order=sort_order")
        val wcards = db.selectAll("written_cards", "select=category_id,serial,icon,title,subtitle,body,tip,issues,benefits,sort_order&topic=eq.data&order=sort_order")
        val rows = db.selectAll(
            "questions",
            "select=$GENERAL_COLS&deleted_at=is.null&order=id",
        ) { progress("Questions", it) }
        val items = rows.map(::toGeneralItem)
        return TopicCatalog.build(module, items, names, sort, subs, now, wcats, wcards)
    }

    private suspend fun downloadIct(now: Long): ModuleContent {
        val rows = db.selectAll(
            "questions",
            "select=$ICT_COLS&deleted_at=is.null" +
                "&module=in.(mcq,written,extra,viva)&order=id",
        ) { progress("Questions", it) }
        val items = rows.map { r -> toIctItem(r) }
        return TopicCatalog.build(module, items, emptyMap(), emptyMap(), emptyList(), now)
    }

    private fun toIctItem(r: org.json.JSONObject): Item {
        val data = r.optJSONObject("payload") ?: org.json.JSONObject()
        // MCQ payloads carry their own text; the column is the fallback for the others.
        if (!data.has("question") && !data.has("q")) data.put("question", r.optString("question"))
        return Item(r.getString("id"), r.optString("uid").ifEmpty { null }, r.getString("module"), r.getString("category_slug"), r.optInt("sort_order"), data,
            r.optString("updated_at").ifEmpty { null })
    }

    private fun toGeneralItem(r: org.json.JSONObject): Item {
        val cat = r.getJSONObject("categories"); r.remove("categories")
        val upd = r.optString("updated_at").ifEmpty { null }; r.remove("updated_at")
        return Item(r.getString("id"), r.optString("uid").ifEmpty { null }, cat.getString("module"), cat.getString("slug"), r.optInt("sort_order"), r, upd)
    }

    // ── delta ────────────────────────────────────────────────────────────

    /** One live row's identity and placement, all that is needed to tell changed from unchanged. */
    @Volatile private var lastDelta: Delta? = null

    private companion object {
        const val GENERAL_COLS = "id,uid,type,question,options,correct_answer,correct_answer_text,explanation,extra,sort_order,updated_at,categories!inner(slug,module)"
        const val ICT_COLS = "id,uid,module,category_slug,question,payload,sort_order,updated_at"
    }

    private class Probe(val id: String, val uid: String, val group: String, val topic: String, val sort: Int, val updatedAt: String?)

    private suspend fun probe(): List<Probe> = when (module) {
        ModuleId.GENERAL -> db.selectAll(
            "questions", "select=id,uid,sort_order,updated_at,categories!inner(slug,module)&deleted_at=is.null&order=id",
        ) { progress("Checking", it) }.map { r ->
            val c = r.getJSONObject("categories")
            Probe(r.getString("id"), r.getString("uid"), c.getString("module"), c.getString("slug"), r.optInt("sort_order"), r.optString("updated_at").ifEmpty { null })
        }
        ModuleId.ICT -> db.selectAll(
            "questions", "select=id,uid,module,category_slug,sort_order,updated_at&deleted_at=is.null&module=in.(mcq,written,extra,viva)&order=id",
        ) { progress("Checking", it) }.map { r -> Probe(r.getString("id"), r.getString("uid"), r.getString("module"), r.getString("category_slug"), r.optInt("sort_order"), r.optString("updated_at").ifEmpty { null }) }
    }

    /** Full rows for exactly these row ids, in PostgREST-friendly chunks. */
    private suspend fun fetchByIds(ids: List<String>): List<org.json.JSONObject> {
        val out = ArrayList<org.json.JSONObject>()
        for (chunk in ids.chunked(150)) {
            val filter = "id=in.(${chunk.joinToString(",")})"
            val rows = when (module) {
                ModuleId.GENERAL -> db.selectAll(
                    "questions",
                    "select=$GENERAL_COLS&deleted_at=is.null&$filter&order=id",
                )
                ModuleId.ICT -> db.selectAll(
                    "questions",
                    "select=$ICT_COLS&deleted_at=is.null&$filter&order=id",
                )
            }
            out += rows
        }
        return out
    }

    /**
     * Diffs the server against [cached] and returns the merged content. Metadata tables (categories, subtopics,
     * the written cards) are a few dozen rows and are simply refetched; only `questions` is diffed, since that is
     * the table with megabytes in it.
     */
    private suspend fun delta(cached: ModuleContent): ModuleContent {
        val now = System.currentTimeMillis()
        val live = probe()
        // Keyed by the row id, which is unique. A uid is a hash of the question text and is NOT unique (General has
        // different questions sharing a stem, e.g. "কোনটি শুদ্ধ বানান?"), so diffing by uid could not see one of them
        // being trashed and kept it in the offline copy for good.
        val liveIds = live.mapTo(HashSet()) { it.id }
        val oldById = cached.allItems().associateBy { it.id }

        // A row counts as changed when it is new, it was edited (updated_at moved: catches answer / explanation / option
        // edits that keep the uid, which only hashes the question text), its uid changed, or it moved.
        val changedIds = live.filter { pr ->
            val old = oldById[pr.id]
            old == null || old.updatedAt != pr.updatedAt || old.uid != pr.uid || old.group != pr.group || old.topic != pr.topic || old.sort != pr.sort
        }
            .mapTo(LinkedHashSet()) { it.id }
        val removedIds = oldById.keys - liveIds
        lastDelta = Delta(added = live.count { it.id !in oldById }, updated = changedIds.count { it in oldById }, removed = removedIds.size)

        val items = if (changedIds.isEmpty() && removedIds.isEmpty()) {
            cached.allItems().toList() // nothing moved in `questions`; only the small metadata tables are refetched
        } else {
            progress("Updating", 0)
            val fetched = fetchByIds(changedIds.toList())
            val newItems = fetched.map { r -> if (module == ModuleId.GENERAL) toGeneralItem(r) else toIctItem(r) }
            val kept = cached.allItems().filter { it.id in liveIds && it.id !in changedIds }
            (kept + newItems).toList()
        }

        return when (module) {
            ModuleId.GENERAL -> {
                val cats = db.selectAll("categories", "select=slug,module,name,sort_order&order=sort_order,slug")
                val names = HashMap<String, String>(); val sort = HashMap<String, Int>()
                for (c in cats) {
                    val key = "${c.getString("module")}/${c.getString("slug")}"
                    names[key] = c.optString("name"); sort[key] = c.optInt("sort_order")
                }
                val subs = db.selectAll("subtopics", "select=slug,name,sort_order,categories(slug)&order=sort_order,id")
                val wcats = db.selectAll("written_categories", "select=id,name,color,sort_order&topic=eq.data&order=sort_order")
                val wcards = db.selectAll("written_cards", "select=category_id,serial,icon,title,subtitle,body,tip,issues,benefits,sort_order&topic=eq.data&order=sort_order")
                TopicCatalog.build(module, items, names, sort, subs, now, wcats, wcards)
            }
            ModuleId.ICT -> TopicCatalog.build(module, items, emptyMap(), emptyMap(), emptyList(), now)
        }
    }

    // ── disk ─────────────────────────────────────────────────────────────

    private fun writeCache(c: ModuleContent) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.bufferedWriter().use { w ->
            val names = JSONObject(); val sort = JSONObject()
            c.groups.forEach { g -> g.topics.forEachIndexed { i, t -> names.put(t.key, t.name); sort.put(t.key, i) } }
            w.write(
                JSONObject().put("syncedAt", c.syncedAt).put("names", names).put("sort", sort)
                    .put("subtopics", JSONArray(c.subtopics)).put("v", CACHE_VERSION)
                    .put("wcats", JSONArray(c.writtenCategories)).put("wcards", JSONArray(c.writtenCards)).toString(),
            )
            w.newLine()
            for (it in c.allItems()) {
                w.write(
                    JSONObject().put("id", it.id).put("uid", it.uid ?: JSONObject.NULL).put("g", it.group)
                        .put("t", it.topic).put("s", it.sort).put("d", it.data).put("u", it.updatedAt ?: JSONObject.NULL).toString(),
                )
                w.newLine()
            }
        }
        if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "Could not save the offline copy" } }
    }

    private fun readCache(): ModuleContent {
        file.bufferedReader().use { r ->
            val meta = JSONObject(r.readLine())
            val names = HashMap<String, String>(); val sort = HashMap<String, Int>()
            meta.getJSONObject("names").let { o -> o.keys().forEach { names[it] = o.getString(it) } }
            meta.getJSONObject("sort").let { o -> o.keys().forEach { sort[it] = o.getInt(it) } }
            val subs = meta.optJSONArray("subtopics")?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
            val items = ArrayList<Item>()
            r.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val o = JSONObject(line)
                items.add(Item(o.getString("id"), o.optString("uid").takeIf { !o.isNull("uid") && it.isNotEmpty() }, o.getString("g"), o.getString("t"), o.getInt("s"), o.getJSONObject("d"), if (o.isNull("u")) null else o.optString("u").ifEmpty { null }))
            }
            fun list(key: String) = meta.optJSONArray(key)?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
            return TopicCatalog.build(module, items, names, sort, subs, meta.getLong("syncedAt"), list("wcats"), list("wcards"), meta.optInt("v", 1))
        }
    }
}
