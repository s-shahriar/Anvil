package com.syed.slate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syed.slate.ModuleServices
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.ui.theme.LocalPalette

/**
 * Wraps a module's pages and offers Undo for flag edits. Every Nailed / Important / Weak / note change raises one
 * notice (a newer edit replaces it). The edit itself is already saved locally and queued — Undo just puts it back.
 * The notice is a [Notices] pill, which never blocks the buttons under it (a snackbar did).
 */
@Composable
fun UndoHost(progress: ProgressRepository, content: @Composable () -> Unit) {
    content()
    val change by progress.lastChange.collectAsState()
    LaunchedEffect(change) {
        val c = change ?: return@LaunchedEffect
        Notices.post(Notice(c.label, actionLabel = "Undo", group = "undo", durationMs = 2_500) { progress.undo(c) })
        progress.dismissChange()
    }
}

/**
 * A thin strip under a module's top bar while edits are waiting to reach the server — the visible part of the sync
 * queue. Tapping it opens the full queue sheet; "Sync now" flushes the queues immediately instead of waiting for
 * the next backoff tick.
 */
@Composable
fun SyncStatusStrip(m: ModuleServices, online: Boolean, modifier: Modifier = Modifier, onOpen: () -> Unit = {}) {
    val pending by m.progress.unsynced.collectAsState()
    val session by m.auth.session.collectAsState()
    if (pending <= 0) return
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().background(p.warn.copy(alpha = .12f)).clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.CloudUpload, null, tint = p.warn)
        Column(Modifier.weight(1f)) {
            Text(
                if (session == null) "$pending change${if (pending == 1) "" else "s"} waiting — sign in to sync"
                else if (!online) "$pending change${if (pending == 1) "" else "s"} waiting for a connection"
                else "Syncing $pending change${if (pending == 1) "" else "s"}…",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (session != null && online) TextButton(onClick = { m.progress.retryNow(); m.trash.retryNow(); m.highlights.kick() }) { Text("Sync now") }
    }
}
