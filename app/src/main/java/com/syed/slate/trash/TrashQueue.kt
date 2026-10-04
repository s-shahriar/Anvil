package com.syed.slate.trash

import org.json.JSONObject

enum class TrashOp { TRASH, RESTORE, PURGE }

/**
 * Recycle-bin writes waiting to reach the server, one per question (`questions.id`).
 *
 * Trash and restore are inverses: if a question is trashed and restored before either was sent, nothing needs to
 * happen, so the two cancel out. Delete-forever overrides whatever was pending.
 */
class TrashQueue(initial: Map<String, TrashOp> = emptyMap()) {
    private val ops = LinkedHashMap(initial)

    val isEmpty get() = ops.isEmpty()
    fun snapshot(): Map<String, TrashOp> = LinkedHashMap(ops)
    fun opFor(id: String): TrashOp? = ops[id]
    fun pendingTrash(): Set<String> = ops.filterValues { it == TrashOp.TRASH }.keys

    fun add(id: String, op: TrashOp) {
        val existing = ops[id]
        val inverse = (existing == TrashOp.TRASH && op == TrashOp.RESTORE) || (existing == TrashOp.RESTORE && op == TrashOp.TRASH)
        if (inverse) ops.remove(id) else ops[id] = op
    }

    /** Drops operations that were sent, unless the same question was changed again while the request was in flight. */
    fun remove(sent: Map<String, TrashOp>) {
        for ((id, op) in sent) if (ops[id] == op) ops.remove(id)
    }

    companion object {
        fun toJson(map: Map<String, TrashOp>) = JSONObject().also { o -> map.forEach { (id, op) -> o.put(id, op.name) } }
        fun fromJson(o: JSONObject) = TrashQueue(o.keys().asSequence().associateWith { TrashOp.valueOf(o.getString(it)) })
    }
}
