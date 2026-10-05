package com.syed.slate.content

import android.content.Context
import com.syed.slate.backend.ModuleId
import com.syed.slate.backend.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The non-question content of one module (ICT: practice, equation; General: utility/finance, ...) from the
 * `content_blobs` table: one JSON payload per (kind, key). The whole table is small, so it is kept in one file in
 * app storage and the UI reads only from that, which keeps it working offline. [refresh] fetches just the rows whose
 * `updated_at` differs from the cached copy. [version] ticks whenever the cache changes, so screens can recompose.
 */
class BlobRepository(context: Context, module: ModuleId, private val db: Postgrest) {
    private class Row(val kind: String, val key: String, val sort: Int, val updatedAt: String, val payload: JSONObject)

    private val file = File(context.filesDir, "blobs_${module.key}.json")
    private val mutex = Mutex()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    // Read on first use, not at launch: the cache holds whole pre-rendered pages (about a megabyte).
    @Volatile private var loaded: Map<String, Row>? = null
    private var rows: Map<String, Row>
        get() = loaded ?: synchronized(this) { loaded ?: readCache().also { loaded = it } }
        set(v) { loaded = v }

    /** Rows of [kind] in their stored order, as (key, payload). */
    fun all(kind: String): List<Pair<String, JSONObject>> =
        rows.values.filter { it.kind == kind }.sortedWith(compareBy({ it.sort }, { it.key })).map { it.key to it.payload }

    fun payload(kind: String, key: String): JSONObject? = rows["$kind/$key"]?.payload

    private fun readCache(): Map<String, Row> = runCatching {
        if (!file.exists()) return emptyMap()
        val a = JSONArray(file.readText())
        (0 until a.length()).map { a.getJSONObject(it) }.associate { o ->
            val r = Row(o.getString("kind"), o.getString("key"), o.optInt("sort_order"), o.optString("updated_at"), o.getJSONObject("payload"))
            "${r.kind}/${r.key}" to r
        }
    }.getOrDefault(emptyMap())

    private fun writeCache(map: Map<String, Row>) {
        val a = JSONArray()
        map.values.forEach { r ->
            a.put(JSONObject().put("kind", r.kind).put("key", r.key).put("sort_order", r.sort).put("updated_at", r.updatedAt).put("payload", r.payload))
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(a.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    /** Downloads what changed since the cached copy. Failures leave the cache untouched (the caller may be offline). */
    suspend fun refresh(): Boolean = mutex.withLock {
        runCatching {
            val probe = db.selectAll("content_blobs", "select=kind,key,sort_order,updated_at&order=kind,key")
            val cached = rows
            val changed = probe.filter { p -> cached["${p.getString("kind")}/${p.getString("key")}"]?.updatedAt != p.getString("updated_at") }
            val next = HashMap<String, Row>()
            // Rows deleted on the server drop out; unchanged ones keep their cached payload (sort order may still move).
            for (p in probe) {
                val id = "${p.getString("kind")}/${p.getString("key")}"
                val old = cached[id]
                if (old != null && old.updatedAt == p.getString("updated_at")) {
                    next[id] = Row(old.kind, old.key, p.optInt("sort_order"), old.updatedAt, old.payload)
                }
            }
            for (kind in changed.map { it.getString("kind") }.distinct()) {
                val keys = changed.filter { it.getString("kind") == kind }.map { it.getString("key") }
                for (chunk in keys.chunked(20)) {
                    val fetched = db.selectAll(
                        "content_blobs",
                        "select=kind,key,sort_order,updated_at,payload&kind=eq.$kind&key=in.(${chunk.joinToString(",") { "\"$it\"" }})&order=key",
                    )
                    for (o in fetched) {
                        val r = Row(o.getString("kind"), o.getString("key"), o.optInt("sort_order"), o.getString("updated_at"), o.getJSONObject("payload"))
                        next["${r.kind}/${r.key}"] = r
                    }
                }
            }
            val moved = next.size != cached.size || next.any { (id, r) -> cached[id]?.sort != r.sort }
            if (changed.isNotEmpty() || moved) {
                withContext(Dispatchers.IO) { writeCache(next) }
                rows = next
                _version.value++
            }
            true
        }.getOrDefault(false)
    }
}
