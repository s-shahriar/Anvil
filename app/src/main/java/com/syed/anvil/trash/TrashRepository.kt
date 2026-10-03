package com.syed.anvil.trash

import android.content.Context
import com.syed.anvil.backend.HttpException
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.backend.Postgrest
import com.syed.anvil.backend.SupabaseAuth
import com.syed.anvil.ui.rich.HtmlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One entry of the recycle bin. */
class BinEntry(val id: String, val uid: String?, val group: String, val topic: String, val topicName: String?, val text: String, val deletedAt: String?)

/**
 * Soft delete for one module, mirroring the web apps: a question is hidden on the spot and the change is queued, so
 * it works offline. Only the owner account can actually trash, restore or purge (the database functions check), so a
 * request the server refuses as "not authorized" is dropped and the question comes back instead of retrying forever.
 */
class TrashRepository(
    context: Context,
    private val module: ModuleId,
    private val auth: SupabaseAuth,
    private val db: Postgrest,
    private val scope: CoroutineScope,
    /** Called after a restore reaches the server, so the module's content can be re-downloaded to include it again. */
    private val onRestored: () -> Unit,
) {
    private val queueFile = File(context.filesDir, "trash_ops_${module.key}.json")
    private val hiddenFile = File(context.filesDir, "trash_hidden_${module.key}.json")
    private val queue = runCatching { TrashQueue.fromJson(JSONObject(queueFile.readText())) }.getOrDefault(TrashQueue())
    /** Trashed on this device and acknowledged by the server, but the offline copy has not been refreshed since. */
    private val confirmed = LinkedHashSet<String>().apply {
        runCatching { JSONArray(hiddenFile.readText()).let { a -> for (i in 0 until a.length()) add(a.getString(i)) } }
    }
    private val lock = Any()
    private val _hidden = MutableStateFlow(computeHidden())
    /** Question ids the lists must not show. */
    val hidden: StateFlow<Set<String>> = _hidden
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var job: Job? = null

    init { if (!queue.isEmpty) kick() }

    private fun computeHidden(): Set<String> = confirmed + queue.pendingTrash()

    private fun persist() {
        queueFile.writeText(TrashQueue.toJson(queue.snapshot()).toString())
        hiddenFile.writeText(JSONArray(confirmed.toList()).toString())
        _hidden.value = computeHidden()
    }

    fun pendingCount(): Int = synchronized(lock) { queue.snapshot().size }
    fun pendingOp(id: String): TrashOp? = synchronized(lock) { queue.opFor(id) }
    fun isPendingTrash(id: String) = synchronized(lock) { queue.opFor(id) == TrashOp.TRASH }

    /** Move to the recycle bin. */
    fun trash(id: String) { synchronized(lock) { queue.add(id, TrashOp.TRASH); persist() }; kick() }

    /** Bring a question back: cancels a trash that has not been sent yet, otherwise asks the server. */
    fun restore(id: String) {
        synchronized(lock) {
            if (queue.opFor(id) == TrashOp.TRASH) { queue.add(id, TrashOp.RESTORE) /* cancels */ }
            else { queue.add(id, TrashOp.RESTORE); confirmed.remove(id) }
            persist()
        }
        kick()
    }

    /** Delete forever (only allowed for a question already in the bin). */
    fun purge(id: String) { synchronized(lock) { queue.add(id, TrashOp.PURGE); persist() }; kick() }

    /** The server's copy no longer contains what we trashed, so the "hidden since" list can be cleared. */
    fun onContentRefreshed() { synchronized(lock) { if (confirmed.isNotEmpty()) { confirmed.clear(); persist() } } }

    fun dismissError() { _error.value = null }

    fun kick() {
        if (job?.isActive == true) return
        job = scope.launch {
            delay(300)
            val backoff = longArrayOf(4_000, 12_000, 30_000, 60_000, 300_000)
            var attempt = 0
            while (true) {
                val batch = synchronized(lock) { queue.snapshot() }
                if (batch.isEmpty() || auth.session.value == null) return@launch
                var transient = false
                for ((id, op) in batch) {
                    val rpc = when (op) { TrashOp.TRASH -> "trash_question"; TrashOp.RESTORE -> "restore_question"; TrashOp.PURGE -> "purge_question" }
                    val failure = runCatching { db.rpc(rpc, JSONObject().put("p_id", id)) }.exceptionOrNull()
                    when {
                        failure == null -> {
                            synchronized(lock) {
                                queue.remove(mapOf(id to op))
                                when (op) { TrashOp.TRASH -> confirmed.add(id); TrashOp.RESTORE, TrashOp.PURGE -> confirmed.remove(id) }
                                persist()
                            }
                            if (op == TrashOp.RESTORE) onRestored()
                        }
                        failure is HttpException && failure.message.orEmpty().contains("not authorized", ignoreCase = true) -> {
                            // Not the owner account: the change can never succeed, so undo it locally.
                            synchronized(lock) { queue.remove(mapOf(id to op)); persist() }
                            _error.value = "Only the owner account can delete or restore questions."
                        }
                        else -> transient = true
                    }
                }
                if (!transient) { attempt = 0; continue }
                delay(backoff[minOf(attempt++, backoff.lastIndex)])
            }
        }
    }

    /** What the server currently holds in the bin (needs a connection). */
    suspend fun fetchBin(): List<BinEntry> = withContext(Dispatchers.IO) {
        when (module) {
            ModuleId.ICT -> db.selectAll(
                "questions", "select=id,uid,module,category_slug,question,payload,deleted_at&deleted_at=not.is.null&order=deleted_at.desc,id",
            ).map { r ->
                val payload = r.optJSONObject("payload")
                val text = payload?.optString("q")?.takeIf { it.isNotEmpty() } ?: r.optString("question")
                BinEntry(r.getString("id"), r.optString("uid").ifEmpty { null }, r.getString("module"), r.getString("category_slug"), null, text, r.optString("deleted_at"))
            }
            ModuleId.GENERAL -> db.selectAll(
                "questions", "select=id,uid,question,deleted_at,categories(slug,module,name)&deleted_at=not.is.null&order=deleted_at.desc,id",
            ).map { r ->
                val c = r.getJSONObject("categories")
                BinEntry(r.getString("id"), r.optString("uid").ifEmpty { null }, c.getString("module"), c.getString("slug"), c.optString("name").ifEmpty { null },
                    HtmlParser.plainText(r.optString("question")), r.optString("deleted_at"))
            }
        }
    }
}
