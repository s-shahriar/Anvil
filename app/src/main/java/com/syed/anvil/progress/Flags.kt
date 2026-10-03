package com.syed.anvil.progress

import org.json.JSONArray
import org.json.JSONObject

/** Per-question study state: the three tiers and a free-text note. */
data class Flag(
    val nailed: Boolean = false,
    val important: Boolean = false,
    val weak: Boolean = false,
    val note: String? = null,
) {
    val isBlank get() = !nailed && !important && !weak && note == null
}

/** The web apps' rules, so a flag set on the phone means the same thing in the browser. */
object FlagRules {
    /** Nailing a question clears Weak. */
    fun toggleNailed(f: Flag) = if (f.nailed) f.copy(nailed = false) else f.copy(nailed = true, weak = false)

    /** Un-marking Important also clears Weak. */
    fun toggleImportant(f: Flag) = if (f.important) f.copy(important = false, weak = false) else f.copy(important = true)

    /** Weak is a subset of Important. */
    fun toggleWeak(f: Flag) = if (f.weak) f.copy(weak = false) else f.copy(weak = true, important = true)

    /** An empty note is stored as null. */
    fun setNote(f: Flag, note: String?) = f.copy(note = note?.trim()?.ifEmpty { null })

    /** Only the columns that changed, so untouched columns are never overwritten server-side. */
    fun diff(old: Flag, new: Flag): Map<String, Any?> = buildMap {
        if (old.nailed != new.nailed) put("nailed", new.nailed)
        if (old.important != new.important) put("important", new.important)
        if (old.weak != new.weak) put("weak", new.weak)
        if (old.note != new.note) put("note", new.note)
    }

    fun apply(f: Flag, patch: Map<String, Any?>) = Flag(
        nailed = patch["nailed"] as? Boolean ?: f.nailed,
        important = patch["important"] as? Boolean ?: f.important,
        weak = patch["weak"] as? Boolean ?: f.weak,
        note = if (patch.containsKey("note")) patch["note"] as String? else f.note,
    )
}

/** Writes waiting to reach the server. Later edits to the same question win, column by column. */
class PendingQueue(initial: Map<String, Map<String, Any?>> = emptyMap()) {
    private val map = LinkedHashMap<String, Map<String, Any?>>(initial)

    val isEmpty get() = map.isEmpty()
    fun snapshot(): Map<String, Map<String, Any?>> = LinkedHashMap(map)
    fun patchFor(uid: String): Map<String, Any?>? = map[uid]

    fun add(uid: String, patch: Map<String, Any?>) {
        map[uid] = (map[uid].orEmpty()) + patch
    }

    /** Drops entries that were sent, unless they were edited again while the request was in flight. */
    fun remove(sent: Map<String, Map<String, Any?>>) {
        for ((uid, patch) in sent) if (map[uid] == patch) map.remove(uid)
    }

    companion object {
        /**
         * Upsert bodies. PostgREST needs every row of a request to carry the same keys, so rows are grouped by
         * which columns they touch and each group is sent on its own.
         */
        fun batches(pending: Map<String, Map<String, Any?>>, userId: String): List<JSONArray> =
            pending.entries.groupBy { it.value.keys.sorted() }.values.map { group ->
                JSONArray().also { arr ->
                    group.forEach { (uid, patch) ->
                        val row = JSONObject().put("user_id", userId).put("uid", uid)
                        patch.forEach { (k, v) -> row.put(k, v ?: JSONObject.NULL) }
                        arr.put(row)
                    }
                }
            }

        fun toJson(map: Map<String, Map<String, Any?>>) = JSONObject().also { o ->
            map.forEach { (uid, patch) ->
                o.put(uid, JSONObject().also { p -> patch.forEach { (k, v) -> p.put(k, v ?: JSONObject.NULL) } })
            }
        }

        fun fromJson(o: JSONObject) = PendingQueue(
            o.keys().asSequence().associateWith { uid ->
                o.getJSONObject(uid).let { p ->
                    p.keys().asSequence().associateWith { k -> if (p.isNull(k)) null else p.get(k) }
                }
            },
        )
    }
}
