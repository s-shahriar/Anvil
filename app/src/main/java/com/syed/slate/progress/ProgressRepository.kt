package com.syed.slate.progress

import android.content.Context
import com.syed.slate.backend.ModuleId
import com.syed.slate.backend.Postgrest
import com.syed.slate.backend.SupabaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.syed.slate.core.writeAtomic
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

    /** One flag edit the UI can offer to undo: what changed, and what it was before. */
    class FlagChange(val uid: String, val label: String, val old: Flag)

    private val _lastChange = MutableStateFlow<FlagChange?>(null)
    /** The most recent edit, for the undo snackbar. Null once dismissed. */
    val lastChange: StateFlow<FlagChange?> = _lastChange

    /** What the flush loop is doing right now, for the sync-queue drawer. */
    enum class SyncPhase { IDLE, SIGNED_OUT, SYNCING, RETRYING }

    /** One queued edit: the question it touches, the columns waiting, and when it was made. */
    class QueuedChange(val uid: String, val patch: Map<String, Any?>, val at: Long)

    private val tsFile = File(context.filesDir, "pending_ts_${module.key}.json")
    private val ts = runCatching { JSONObject(tsFile.readText()) }.getOrDefault(JSONObject())
    private val _queue = MutableStateFlow(emptyList<QueuedChange>())
    /** Edits waiting to reach the server, newest first. */
    val queue: StateFlow<List<QueuedChange>> = _queue

    private val _phase = MutableStateFlow(SyncPhase.IDLE)
    val phase: StateFlow<SyncPhase> = _phase
    private val _retryAt = MutableStateFlow(0L)
    /** Wall clock of the next automatic retry while [SyncPhase.RETRYING]. */
    val retryAt: StateFlow<Long> = _retryAt
    private val _savedAt = MutableStateFlow(0L)
    /** When a backlog last landed on the server, and how many questions it covered. */
    val savedAt: StateFlow<Long> = _savedAt
    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount

    /** A flag edit that reached the server this session — the drawer's "Synced" receipt (not an audit log). */
    class DoneChange(val uid: String, val patch: Map<String, Any?>, val at: Long, val syncedAt: Long)

    private val _done = MutableStateFlow(emptyList<DoneChange>())
    val done: StateFlow<List<DoneChange>> = _done

    /** Why the last attempt failed and how many times in a row, while [SyncPhase.RETRYING]. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    private val _attempts = MutableStateFlow(0)
    val attempts: StateFlow<Int> = _attempts

    /** A topic / sub-topic change made from the admin screens. They go straight to the server (online only), so they
     *  appear in the drawer already synced; [undo] sends the inverse, like the web's Undo on those rows. */
    class ExternalChange(
        val id: String, val uid: String?, val kind: String, val label: String, val text: String, val cat: String,
        val syncedAt: Long, val undo: (suspend () -> Unit)?, val error: String? = null,
    )

    private val _external = MutableStateFlow(emptyList<ExternalChange>())
    val external: StateFlow<List<ExternalChange>> = _external
    fun recordExternal(c: ExternalChange) { _external.value = (listOf(c) + _external.value).take(50) }
    fun dropExternal(c: ExternalChange) { _external.value = _external.value - c }

    private fun refreshQueue() {
        _queue.value = pending.snapshot().map { (u, p) -> QueuedChange(u, p, ts.optLong(u, 0L)) }
            .sortedByDescending { it.at }
    }

    init { refreshQueue(); if (!pending.isEmpty) kick() }

    /** One-line description of a queued patch, for the drawer row. */
    fun describe(patch: Map<String, Any?>): String = when {
        "nailed" in patch -> if (patch["nailed"] == true) "Nailed it" else "Un-nailed"
        "important" in patch -> if (patch["important"] == true) "Marked important" else "Removed important"
        "weak" in patch -> if (patch["weak"] == true) "Marked weak" else "Removed weak"
        else -> "Note saved"
    }

    /** Notes cannot be inverted (the previous text is unknown), so note-only entries offer no undo. */
    fun undoable(patch: Map<String, Any?>) = patch.keys.any { it != "note" }

    fun flag(uid: String): Flag = _flags.value[uid] ?: Flag()

    fun update(uid: String, change: (Flag) -> Flag) {
        val old: Flag
        val new: Flag
        val patch: Map<String, Any?>
        synchronized(io) {
            old = flag(uid)
            new = change(old)
            patch = FlagRules.diff(old, new)
            if (patch.isEmpty()) return
            _flags.value = if (new.isBlank) _flags.value - uid else _flags.value + (uid to new)
            pending.add(uid, patch)
            ts.put(uid, System.currentTimeMillis())
            persist()
        }
        _lastChange.value = FlagChange(uid, label(patch, new), old)
        kick()
    }

    /** Puts a flag back the way it was before one edit, without raising another undo event. */
    fun undo(change: FlagChange) {
        synchronized(io) {
            val current = flag(change.uid)
            val patch = FlagRules.diff(current, change.old)
            if (patch.isEmpty()) return
            _flags.value = if (change.old.isBlank) _flags.value - change.uid else _flags.value + (change.uid to change.old)
            pending.add(change.uid, patch)
            persist()
        }
        kick()
    }

    /** Inverts a queued edit column by column and queues the reversal, exactly like the web's drawer Undo. */
    fun undoQueued(c: QueuedChange) {
        update(c.uid) { f ->
            var nf = f
            for ((k, v) in c.patch) when (k) {
                "nailed" -> nf = nf.copy(nailed = v != true)
                "important" -> nf = nf.copy(important = v != true)
                "weak" -> nf = nf.copy(weak = v != true)
            }
            nf
        }
    }

    fun dismissChange() { _lastChange.value = null }

    /** The intent of an edit, from what actually changed; Weak/Nailed win because they carry the smaller toggles with them. */
    private fun label(patch: Map<String, Any?>, new: Flag) = when {
        "weak" in patch -> if (new.weak) "Weak করা হলো" else "Weak সরানো হলো"
        "nailed" in patch -> if (new.nailed) "Nailed it" else "Un-nailed"
        "important" in patch -> if (new.important) "Important করা হলো" else "Important সরানো হলো"
        else -> "নোট সেভ হলো"
    }

    /** Batches that reached the server, numbered, so a [pull] that overlapped them can put them back. */
    private var flushGen = 0L
    private val landed = ArrayDeque<Pair<Long, Map<String, Map<String, Any?>>>>()

    /** Replaces local state with the server's, then re-applies edits that have not been sent yet. */
    suspend fun pull() {
        if (auth.session.value == null) return
        val startGen = synchronized(io) { flushGen }
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
            // A batch that landed while the rows were being read may be missing from them, and it is no longer
            // pending either: without this the edit would vanish from the phone until the next pull.
            val overlapped = landed.filter { it.first > startGen }.map { it.second } + pending.snapshot()
            for ((uid, patch) in overlapped.flatMap { it.entries }.map { it.key to it.value }) {
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
                if (batch.isEmpty()) { _phase.value = SyncPhase.IDLE; return@launch }
                if (auth.session.value == null) { _phase.value = SyncPhase.SIGNED_OUT; return@launch } // edits stay queued until sign-in calls kick()
                _phase.value = SyncPhase.SYNCING
                val failure = runCatching {
                    for (rows in PendingQueue.batches(batch, auth.session.value!!.userId)) db.upsert("user_progress", rows, "user_id,uid")
                }.exceptionOrNull()
                if (failure == null) {
                    val syncedAt = System.currentTimeMillis()
                    val stamps = synchronized(io) { batch.keys.associateWith { ts.optLong(it, syncedAt) } }
                    synchronized(io) {
                        pending.remove(batch); persist()
                        landed.addLast(++flushGen to batch); while (landed.size > 20) landed.removeFirst()
                    }
                    _done.value = (batch.map { (u, pt) -> DoneChange(u, pt, stamps[u] ?: syncedAt, syncedAt) } + _done.value).take(60)
                    _savedAt.value = syncedAt; _savedCount.value = batch.size
                    _lastError.value = null; _attempts.value = 0
                    attempt = 0
                } else {
                    val wait = backoff[minOf(attempt++, backoff.lastIndex)]
                    _attempts.value = attempt
                    _lastError.value = failure?.message ?: "Couldn't reach the server"
                    _retryAt.value = System.currentTimeMillis() + wait
                    _phase.value = SyncPhase.RETRYING
                    delay(wait)
                }
            }
        }
    }

    /** Clears any backoff wait and flushes immediately — the drawer's "Retry now". */
    fun retryNow() {
        flushJob?.cancel(); flushJob = null
        kick()
    }

    private fun persist() {
        flagFile.writeAtomic(JSONObject().also { o ->
            _flags.value.forEach { (uid, f) ->
                o.put(uid, JSONObject().put("n", f.nailed).put("i", f.important).put("w", f.weak).put("t", f.note ?: JSONObject.NULL))
            }
        }.toString())
        val snap = pending.snapshot()
        pendingFile.writeAtomic(PendingQueue.toJson(snap).toString())
        // Timestamps only for entries still queued; sent ones are pruned.
        val tsOut = JSONObject()
        snap.keys.forEach { uid -> if (ts.has(uid)) tsOut.put(uid, ts.getLong(uid)) }
        tsFile.writeAtomic(tsOut.toString())
        _unsynced.value = snap.size
        refreshQueue()
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
