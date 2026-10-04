package com.syed.slate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syed.slate.ModuleServices
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.ContentState
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.trash.TrashOp
import com.syed.slate.ui.rich.HtmlParser
import com.syed.slate.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Provided inside a module: opens the sync-queue sheet from any top bar (null on shell screens). */
val LocalOpenSyncQueue = staticCompositionLocalOf<(() -> Unit)?> { null }

private enum class RowState { WAITING, FAILED, SYNCED }

/** One line of the drawer, whatever queue it came from. [undo] is null when the change can no longer be reversed. */
private class SyncRow(
    val key: String,
    val state: RowState,
    val icon: ImageVector,
    val tint: Color,
    val text: String,
    val action: String,
    val cat: String,
    val at: Long,
    val syncedAt: Long,
    val error: String? = null,
    val attempts: Int = 0,
    /** true = reversible now · false = shown disabled (deleted forever) · null = no Undo button. */
    val undoable: Boolean? = null,
    val undo: () -> Unit = {},
)

/**
 * The sync queue as a sheet — the port of the web apps' SyncDrawer. Every change kept on this device: Failed, Waiting,
 * and a session "Synced" receipt (a waiting row does not just vanish when it lands — it MOVES into Synced with the
 * time it landed, which is how you verify the writes went through). Each row carries the question's text, what
 * happened, when, and an Undo while the change is still in effect. "Retry now" cuts through the backoff.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncQueueSheet(m: ModuleServices, online: Boolean, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val flags by m.progress.flags.collectAsState()
    val queue by m.progress.queue.collectAsState()
    val done by m.progress.done.collectAsState()
    val external by m.progress.external.collectAsState()
    val phase by m.progress.phase.collectAsState()
    val retryAt by m.progress.retryAt.collectAsState()
    val savedAt by m.progress.savedAt.collectAsState()
    val progressError by m.progress.lastError.collectAsState()
    val attempts by m.progress.attempts.collectAsState()
    val trashOps by m.trash.queueOps.collectAsState()
    val trashDone by m.trash.done.collectAsState()
    val trashFailure by m.trash.failure.collectAsState()
    val hidden by m.trash.hidden.collectAsState()
    val highlightPending by m.highlights.unsynced.collectAsState()
    val session by m.auth.session.collectAsState()
    val contentState by m.content.state.collectAsState()

    // Question text + topic name by uid and by row id, off the main thread (the cache holds thousands of rows).
    val labels by produceState<Pair<Map<String, Pair<String, String>>, Map<String, Pair<String, String>>>>(emptyMap<String, Pair<String, String>>() to emptyMap(), contentState, queue, trashOps, done, trashDone) {
        val c = (contentState as? ContentState.Ready)?.content ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val byUid = HashMap<String, Pair<String, String>>(); val byId = HashMap<String, Pair<String, String>>()
            val wanted = HashSet<String>().apply {
                queue.forEach { add(it.uid) }; done.forEach { add(it.uid) }
                trashOps.keys.forEach { add(it) }; trashDone.forEach { add(it.id) }
            }
            val topicNames = HashMap<String, String>()
            c.groups.forEach { g -> g.topics.forEach { t -> topicNames["${t.group}/${t.slug}"] = t.name } }
            for (item in c.allItems() + m.content.hiddenItems().asSequence()) {
                if (item.uid !in wanted && item.id !in wanted) continue
                val topic = topicNames["${item.group}/${item.topic}"] ?: item.topic
                val text = (if (m.id == ModuleId.ICT) item.question.lineSequence().first() else HtmlParser.plainText(item.question)).trim()
                item.uid?.let { byUid[it] = text to topic }
                byId[item.id] = text to topic
            }
            byUid to byId
        }
    }
    val (byUid, byId) = labels

    // The web ticks once a second while the drawer is open so "just now" and the retry countdown stay honest.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); tick++ } }
    check(tick >= 0) // read so the sheet recomposes every second

    val rows = ArrayList<SyncRow>()
    val failedFlags = phase == ProgressRepository.SyncPhase.RETRYING && progressError != null
    for (c in queue) {
        val (text, topic) = byUid[c.uid] ?: ("Saved item" to "")
        val d = describeFlag(p, c.patch)
        rows += SyncRow(
            "q-${c.uid}", if (failedFlags) RowState.FAILED else RowState.WAITING, d.icon, d.tint, text, d.text, topic, c.at, 0,
            error = if (failedFlags) progressError else null, attempts = attempts,
            undoable = flagUndoable(c.patch, c.uid, flags).takeIf { c.patch.keys.any { k -> k != "note" } },
        ) { m.progress.undoQueued(c) }
    }
    for ((id, op) in trashOps) {
        val (text, topic) = byId[id] ?: ("Saved item" to "")
        val d = describeTrash(p, op)
        val failed = trashFailure != null
        rows += SyncRow(
            "t-$id", if (failed) RowState.FAILED else RowState.WAITING, d.icon, d.tint, text, d.text, topic, 0, 0,
            error = if (failed) trashFailure else null,
            undoable = if (op == TrashOp.PURGE) false else true,
        ) { m.trash.undoQueued(id, op) }
    }
    if (highlightPending > 0) {
        rows += SyncRow(
            "h", RowState.WAITING, Icons.Filled.EditNote, MaterialTheme.colorScheme.primary,
            "$highlightPending highlight edit${if (highlightPending == 1) "" else "s"}", "Highlights", "", 0, 0,
        )
    }
    for (c in done) {
        val (text, topic) = byUid[c.uid] ?: ("Saved item" to "")
        val d = describeFlag(p, c.patch)
        rows += SyncRow(
            "d-${c.uid}-${c.syncedAt}", RowState.SYNCED, d.icon, d.tint, text, d.text, topic, c.at, c.syncedAt,
            undoable = flagUndoable(c.patch, c.uid, flags).takeIf { c.patch.keys.any { k -> k != "note" } },
        ) { m.progress.undoQueued(ProgressRepository.QueuedChange(c.uid, c.patch, c.at)) }
    }
    for (c in trashDone) {
        val (text, topic) = byId[c.id] ?: ("Saved item" to "")
        val d = describeTrash(p, c.op)
        val inBin = c.id in hidden
        val canUndo = when (c.op) { TrashOp.TRASH -> inBin; TrashOp.RESTORE -> !inBin; TrashOp.PURGE -> false }
        rows += SyncRow(
            "td-${c.id}-${c.syncedAt}", RowState.SYNCED, d.icon, d.tint, text, d.text, topic, c.at, c.syncedAt,
            undoable = if (c.op == TrashOp.PURGE) false else if (canUndo) true else null,
        ) { m.trash.undoQueued(c.id, c.op) }
    }
    for (c in external) {
        rows += SyncRow(
            "x-${c.id}-${c.syncedAt}", RowState.SYNCED,
            if (c.kind == "move") Icons.Filled.DriveFileMove else Icons.Filled.Sell, if (c.kind == "move") p.primary else p.info,
            c.label, c.text, c.cat, c.syncedAt, c.syncedAt,
            undoable = if (c.undo != null) true else null,
        ) { scope.launch { runCatching { c.undo?.invoke() }.onSuccess { m.progress.dropExternal(c) } } }
    }

    val failed = rows.filter { it.state == RowState.FAILED }
    val waiting = rows.filter { it.state == RowState.WAITING }
    val synced = rows.filter { it.state == RowState.SYNCED }
    val pendingCount = queue.size + trashOps.size + highlightPending
    val retrySecs = if (retryAt > 0) ((retryAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0) else 0L

    // Same wording and tone rules as the web's SyncDrawer.
    var good = true
    var lead = Icons.Filled.Check
    var line = "Everything is synced"
    when {
        session == null && pendingCount > 0 -> { good = false; lead = Icons.Filled.CloudOff; line = "Signed out · $pendingCount waiting" }
        !online && pendingCount > 0 -> { good = false; lead = Icons.Filled.CloudOff; line = "Offline · $pendingCount waiting" }
        phase == ProgressRepository.SyncPhase.SYNCING && pendingCount > 0 -> { lead = Icons.Filled.Refresh; line = "Syncing $pendingCount…" }
        failed.isNotEmpty() -> { good = false; lead = Icons.Filled.Warning; line = if (retrySecs > 0) "Retrying in ${retrySecs}s" else "Last attempt failed" }
        pendingCount > 0 -> { good = false; lead = Icons.Filled.Schedule; line = "$pendingCount waiting to sync" }
        savedAt > 0 -> line = "All synced · ${ago(savedAt)}"
    }
    val tone = if (good) p.ok else if (failed.isNotEmpty()) p.bad else p.warn

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 420.dp)) {
            // Header: ☁ SYNC QUEUE ................ (x)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, null, Modifier.size(16.dp), tint = p.text2)
                Text("SYNC QUEUE", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.labelLarge, color = p.text2, letterSpacing = 1.5.sp)
                Box(Modifier.size(36.dp).clip(CircleShape).background(p.elevated).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", Modifier.size(18.dp), tint = p.text2)
                }
            }
            // State line + Retry now
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(lead, null, Modifier.size(15.dp), tint = tone)
                Text(line, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, color = tone)
                TextButton(
                    onClick = { m.progress.retryNow(); m.trash.retryNow(); m.highlights.kick() },
                    enabled = pendingCount > 0 && session != null,
                    modifier = Modifier.border(1.dp, p.outline, CircleShape),
                ) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(13.dp))
                    Text("Retry now", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(Modifier.fillMaxWidth().size(1.dp).background(p.outline))

            if (rows.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 36.dp, vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Check, null, Modifier.size(28.dp), tint = p.ok)
                    Text("Nothing waiting", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (savedAt > 0) "Last change saved ${ago(savedAt)}."
                        else "Nail, important, weak, delete, Recycle Bin and topic / sub-topic changes show up here until they reach the server.",
                        style = MaterialTheme.typography.bodyMedium, color = p.text3, textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    section("Failed", failed, p.bad, p)
                    section("Waiting", waiting, p.warn, p)
                    section("Synced", synced, p.ok, p)
                }
            }
            Box(Modifier.fillMaxWidth().size(1.dp).background(p.outline))
            Text(
                "Changes are kept on this device and sync automatically.",
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodySmall, color = p.text3, textAlign = TextAlign.Center,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String, rows: List<SyncRow>, tint: Color, p: com.syed.slate.ui.theme.Palette,
) {
    if (rows.isEmpty()) return
    item(key = "h-$title") {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = p.text3, letterSpacing = 1.2.sp)
            Text("${rows.size}", Modifier.padding(start = 8.dp).clip(CircleShape).background(tint.copy(alpha = .16f)).padding(horizontal = 7.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
    items(rows, key = { it.key }) { r -> SyncRowView(r, p) }
}

@Composable
private fun SyncRowView(r: SyncRow, p: com.syed.slate.ui.theme.Palette) {
    val whenText = if (r.state == RowState.SYNCED) "synced ${ago(r.syncedAt)}" else if (r.at > 0) ago(r.at) else ""
    val meta = buildString {
        append(r.action)
        if (r.cat.isNotEmpty()) append(" · ").append(r.cat)
        if (whenText.isNotEmpty()) append(" · ").append(whenText)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(r.tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(r.icon, null, Modifier.size(15.dp), tint = r.tint)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.labelSmall, color = p.text3)
            r.error?.let { Text(it + if (r.attempts > 1) " · ${r.attempts} attempts" else "", style = MaterialTheme.typography.labelSmall, color = p.bad, maxLines = 2) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (r.state) {
                RowState.SYNCED -> Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = p.ok)
                RowState.FAILED -> Icon(Icons.Filled.Warning, null, Modifier.size(14.dp), tint = p.bad)
                RowState.WAITING -> Icon(Icons.Filled.Schedule, null, Modifier.size(14.dp), tint = p.warn)
            }
            r.undoable?.let { enabled ->
                Row(
                    Modifier.clip(CircleShape).border(1.dp, p.outline, CircleShape).clickable(enabled = enabled, onClick = r.undo).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(12.dp), tint = if (enabled) p.text2 else p.text3.copy(alpha = .5f))
                    Text("Undo", Modifier.padding(start = 3.dp), style = MaterialTheme.typography.labelSmall, color = if (enabled) p.text2 else p.text3.copy(alpha = .5f))
                }
            }
        }
    }
}

private class Look(val icon: ImageVector, val tint: Color, val text: String)

/** The icon + colour mirror the buttons the change came from; direction shows too (filled/on vs struck/grey off). */
private fun describeFlag(p: com.syed.slate.ui.theme.Palette, patch: Map<String, Any?>): Look {
    val nailed = patch["nailed"] as? Boolean
    val important = patch["important"] as? Boolean
    val weak = patch["weak"] as? Boolean
    val parts = ArrayList<String>()
    if (nailed != null) parts += if (nailed) "Nailed" else "Un-nailed"
    if (weak == true) parts += "Marked weak"
    if (important != null) parts += if (important) "Marked important" else "Unmarked important"
    if (weak == false) parts += "Unmarked weak"
    val text = parts.joinToString(" · ").ifEmpty { "Note saved" }
    return when {
        nailed != null -> Look(if (nailed) Icons.Filled.Star else Icons.Filled.StarBorder, if (nailed) p.ok else p.text3, text)
        weak == true || (weak == false && important == null) -> Look(Icons.Filled.LocalFireDepartment, if (weak == true) p.warn else p.text3, text)
        important != null -> Look(if (important) Icons.Filled.Bookmark else Icons.Filled.BookmarkRemove, if (important) p.imp else p.text3, text)
        else -> Look(Icons.Filled.EditNote, p.text3, text)
    }
}

private fun describeTrash(p: com.syed.slate.ui.theme.Palette, op: TrashOp) = when (op) {
    TrashOp.TRASH -> Look(Icons.Filled.Delete, p.bad, "Moved to Recycle Bin")
    TrashOp.RESTORE -> Look(Icons.Filled.RestoreFromTrash, p.ok, "Restored from Recycle Bin")
    TrashOp.PURGE -> Look(Icons.Filled.Delete, p.bad, "Deleted forever")
}

/** Undo only while the change is still in effect — once something later reversed it, its row stops offering Undo. */
private fun flagUndoable(patch: Map<String, Any?>, uid: String, flags: Map<String, com.syed.slate.progress.Flag>): Boolean? {
    val f = flags[uid] ?: com.syed.slate.progress.Flag()
    val inEffect = (patch["nailed"] as? Boolean)?.let { it == f.nailed } != false &&
        (patch["important"] as? Boolean)?.let { it == f.important } != false &&
        (patch["weak"] as? Boolean)?.let { it == f.weak } != false
    return if (inEffect) true else null
}

/** The web's ago(): just now / Xm ago / Xh ago / Xd ago (under 45 s reads "just now"). */
internal fun ago(thenMs: Long): String {
    if (thenMs <= 0L) return "just now"
    val s = (System.currentTimeMillis() - thenMs) / 1000
    return when {
        s < 45 -> "just now"
        s < 3600 -> "${(s + 30) / 60}m ago"
        s < 86400 -> "${(s + 1800) / 3600}h ago"
        else -> "${(s + 43200) / 86400}d ago"
    }
}
