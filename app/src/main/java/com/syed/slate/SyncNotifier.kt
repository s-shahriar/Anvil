package com.syed.slate

import com.syed.slate.content.SyncState
import com.syed.slate.core.Connectivity
import com.syed.slate.progress.ProgressRepository.SyncPhase
import com.syed.slate.ui.component.Notice
import com.syed.slate.ui.component.Notices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Tells the user what syncing is doing, the way the web apps' sync pill does — but only when it matters:
 *  - the connection drops / comes back (and how many changes are waiting),
 *  - a sync attempt fails (once per failure streak; it keeps retrying on its own),
 *  - a backlog that had to wait (offline or failed) finally lands: "N changes synced",
 *  - a content refresh finishes or fails, with what it added.
 * An ordinary online edit that lands in a second shows nothing, so quizzing stays quiet.
 */
class SyncNotifier(private val connectivity: Connectivity, private val modules: List<ModuleServices>) {

    private class Snap(val online: Boolean, val pending: Int, val failing: Boolean)

    private fun snap(): Snap {
        var pending = 0
        var failing = false
        for (m in modules) {
            pending += m.progress.unsynced.value + m.trash.queueOps.value.size + m.highlights.unsynced.value
            failing = failing || m.progress.phase.value == SyncPhase.RETRYING || m.trash.failure.value != null || m.highlights.failure.value != null
        }
        return Snap(connectivity.online.value, pending, failing)
    }

    fun start(scope: CoroutineScope) {
        val flows: List<Flow<Any?>> = listOf<Flow<Any?>>(connectivity.online) + modules.flatMap {
            listOf(it.progress.unsynced, it.progress.phase, it.trash.queueOps, it.trash.failure, it.highlights.unsynced, it.highlights.failure)
        }
        scope.launch {
            var prev = snap()
            var waited = prev.pending > 0 && (!prev.online || prev.failing) // a backlog left over from last time
            var peak = prev.pending
            combine(flows) { }.collect {
                val now = snap()
                if (now.pending > 0) peak = maxOf(peak, now.pending)
                when {
                    prev.online && !now.online -> {
                        Notices.post(Notice(
                            if (now.pending > 0) "Offline · ${changes(now.pending)} kept on this phone, they'll sync when you're back"
                            else "Offline · changes you make are kept on this phone",
                            Notice.Kind.WARN, group = "sync", durationMs = 4_000,
                        ))
                        if (now.pending > 0) waited = true
                    }
                    !prev.online && now.online -> {
                        if (now.pending > 0) Notices.post(Notice("Back online · syncing ${changes(now.pending)}…", Notice.Kind.INFO, group = "sync"))
                        else Notices.post(Notice("Back online", Notice.Kind.OK, group = "sync", durationMs = 2_000))
                    }
                }
                if (!now.online && now.pending > 0) waited = true
                if (now.online && now.failing && !prev.failing && now.pending > 0) {
                    waited = true
                    Notices.post(Notice("Sync failed · ${changes(now.pending)} waiting, retrying automatically", Notice.Kind.ERROR, group = "sync", durationMs = 4_000))
                }
                if (prev.pending > 0 && now.pending == 0) {
                    if (waited) Notices.post(Notice("Synced · ${changes(peak)} saved to your account", Notice.Kind.OK, group = "sync"))
                    waited = false; peak = 0
                }
                prev = now
            }
        }
        // Content refresh (the Refresh button / Settings): what came in, or why it failed.
        for (m in modules) {
            scope.launch {
                m.content.lastRefresh.drop(1).filterNotNull().collect { r ->
                    val d = r.delta
                    val text = when {
                        r.firstDownload -> "${m.id.title} downloaded · ${d.added} questions"
                        d.isEmpty -> "${m.id.title} is up to date · nothing new"
                        else -> "${m.id.title} updated · " + listOfNotNull(
                            d.added.takeIf { it > 0 }?.let { "$it new" },
                            d.updated.takeIf { it > 0 }?.let { "$it edited" },
                            d.removed.takeIf { it > 0 }?.let { "$it removed" },
                        ).joinToString(", ")
                    }
                    Notices.post(Notice(text, Notice.Kind.OK, group = "refresh-${m.id.key}", durationMs = 3_500))
                }
            }
            scope.launch {
                m.content.sync.drop(1).collect { s ->
                    if (s is SyncState.Failed) Notices.post(Notice(
                        "${m.id.title} refresh failed · ${if (connectivity.online.value) s.message else "no connection"}. The downloaded copy is still in use.",
                        Notice.Kind.ERROR, group = "refresh-${m.id.key}", durationMs = 4_500,
                    ))
                }
            }
        }
    }

    private fun changes(n: Int) = if (n == 1) "1 change" else "$n changes"
}
