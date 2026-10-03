package com.syed.anvil.progress

import android.content.Context
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.backend.Postgrest
import com.syed.anvil.backend.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Nailed / Important / Weak / notes for one module.
 *
 * Reads and writes always hit local storage first, so flagging works offline and when signed out. Edits are
 * queued and pushed in bulk whenever there is a session and a connection; the queue survives restarts.
 */
class ProgressRepository(
    context: Context,
    val module: ModuleId,
    private val auth: SupabaseAuth,
    private val db: Postgrest,
    private val scope: CoroutineScope,
) {
    private val flagFile = File(context.filesDir, "progress_${module.key}.json")
    private val pendingFile = File(context.filesDir, "pending_${module.key}.json")
    private val pending = runCatching { PendingQueue.fromJson(JSONObject(pendingFile.readText())) }.getOrDefault(PendingQueue())
    private val _flags = MutableStateFlow(readFlags())
    val flags: StateFlow<Map<String, Flag>> = _flags
    private val _unsynced = MutableStateFlow(pending.snapshot().size)
    /** Number of questions with changes not yet saved to the server. */
    val unsynced: StateFlow<Int> = _unsynced
    private var flushJob: Job? = null
    private val io = Any()

    init { if (!pending.isEmpty) kick() }

    fun flag(uid: String): Flag = _flags.value[uid] ?: Flag()

    fun update(uid: String, change: (Flag) -> Flag) {
        synchronized(io) {
            val old = flag(uid)
            val new = change(old)
            val patch = FlagRules.diff(old, new)
            if (patch.isEmpty()) return
            _flags.value = if (new.isBlank) _flags.value - uid else _flags.value + (uid to new)
            pending.add(uid, patch)
            persist()
        }
        kick()
    }

    /** Replaces local state with the server's, then re-applies edits that have not been sent yet. */
    suspend fun pull() {
        if (auth.session.value == null) return
        val rows = db.selectAll("user_progress", "select=uid,nailed,important,weak,note&order=uid")
        synchronized(io) {
            val server = HashMap<String, Flag>()
            for (r in rows) {
                val f = Flag(
                    r.optBoolean("nailed"), r.optBoolean("important"), r.optBoolean("weak"),
                    if (r.isNull("note")) null else r.optString("note").ifEmpty { null },
                )
                if (!f.isBlank) server[r.getString("uid")] = f
            }
            for ((uid, patch) in pending.snapshot()) {
                val merged = FlagRules.apply(server[uid] ?: Flag(), patch)
                if (merged.isBlank) server.remove(uid) else server[uid] = merged
            }
            _flags.value = server
            persist()
        }
    }

    /** Debounced push with backoff; also called after sign-in and when the connection returns. */
    fun kick() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(400)
            val backoff = longArrayOf(4_000, 12_000, 30_000, 60_000, 300_000)
            var attempt = 0
            while (true) {
                val batch = synchronized(io) { pending.snapshot() }
                if (batch.isEmpty()) return@launch
                val session = auth.session.value
                if (session == null) return@launch // signed out: edits stay queued until sign-in calls kick()
                val ok = runCatching {
                    for (rows in PendingQueue.batches(batch, session.userId)) db.upsert("user_progress", rows, "user_id,uid")
                }.isSuccess
                if (ok) {
                    synchronized(io) { pending.remove(batch); persist() }
                    attempt = 0
                } else {
                    delay(backoff[minOf(attempt++, backoff.lastIndex)])
                }
            }
        }
    }

    private fun persist() {
        flagFile.writeText(JSONObject().also { o ->
            _flags.value.forEach { (uid, f) ->
                o.put(uid, JSONObject().put("n", f.nailed).put("i", f.important).put("w", f.weak).put("t", f.note ?: JSONObject.NULL))
            }
        }.toString())
        pendingFile.writeText(PendingQueue.toJson(pending.snapshot()).toString())
        _unsynced.value = pending.snapshot().size
    }

    private fun readFlags(): Map<String, Flag> = runCatching {
        val o = JSONObject(flagFile.readText())
        o.keys().asSequence().associateWith { uid ->
            o.getJSONObject(uid).let { Flag(it.optBoolean("n"), it.optBoolean("i"), it.optBoolean("w"), if (it.isNull("t")) null else it.optString("t")) }
        }
    }.getOrDefault(emptyMap())

    suspend fun clearLocal() = withContext(Dispatchers.IO) {
        synchronized(io) {
            _flags.value = emptyMap(); pending.remove(pending.snapshot()); persist()
        }
    }
}
