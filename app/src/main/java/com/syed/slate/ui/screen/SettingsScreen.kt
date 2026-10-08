package com.syed.slate.ui.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.syed.slate.ui.component.SlateLinearLoader
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syed.slate.BuildConfig
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.ContentState
import com.syed.slate.content.SyncState
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.ThemeMode
import com.syed.slate.ui.component.UpdateCard
import com.syed.slate.ui.component.formatBytes
import com.syed.slate.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SlateViewModel, activity: Activity, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { BarTitle("Settings", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Theme and hand together: two short labelled rows instead of two cards.
            Card {
                SettingRow("Theme") {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(vm.themeMode == mode, { vm.setTheme(mode) }, { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
                SettingRow("Hand", hint = "Mirrors bars and buttons to your thumb") {
                    FilterChip(!vm.leftHand, { vm.chooseLeftHand(false) }, { Text("Right") })
                    FilterChip(vm.leftHand, { vm.chooseLeftHand(true) }, { Text("Left") })
                }
            }
            ModuleId.entries.forEach { id -> ModuleCard(vm, activity, id) }
            UpdateCard(vm, showWhenIdle = true)
            Text(
                "Slate ${BuildConfig.VERSION_NAME} · General and ICT quizzes in one place, working offline.",
                Modifier.fillMaxWidth().padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = LocalPalette.current.text3, textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingRow(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.text3) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) { content() }
    }
}

@Composable
private fun ModuleCard(vm: SlateViewModel, activity: Activity, id: ModuleId) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val sync by m.content.sync.collectAsState()
    val last by m.content.lastRefresh.collectAsState()
    val session by m.auth.session.collectAsState()
    val flagsWaiting by m.progress.unsynced.collectAsState()
    val trashWaiting by m.trash.queueOps.collectAsState()
    val hlWaiting by m.highlights.unsynced.collectAsState()
    val waiting = flagsWaiting + trashWaiting.size + hlWaiting
    val online by vm.online.collectAsState()
    val saved by vm.imagesSaved.collectAsState()
    val p = LocalPalette.current
    val ready = (state as? ContentState.Ready)?.content
    var confirmRemove by remember { mutableStateOf(false) }

    Card {
        // Header: module, and one status chip that says whether your changes are safe on the server.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(if (id == ModuleId.GENERAL) Icons.AutoMirrored.Filled.MenuBook else Icons.Filled.Bolt, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(id.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            when {
                session == null && waiting > 0 -> StatusChip("$waiting not synced", p.warn)
                waiting > 0 && !online -> StatusChip("$waiting waiting · offline", p.warn)
                waiting > 0 -> StatusChip("Syncing $waiting", p.info)
                session != null -> StatusChip("All synced", p.ok)
            }
        }

        // The offline copy at a glance.
        if (ready != null) {
            val total = remember(ready) { m.imageUrls(ready).size }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Questions", "${ready.total}", Modifier.weight(1f))
                Stat("Size", formatBytes(m.content.cacheBytes), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Pictures", if (total > 0) "${minOf(saved, total)} / $total" else "—", Modifier.weight(1f))
                Stat("Updated", formatShortDate(m.content.cachedAt), Modifier.weight(1f))
            }
        } else {
            Text(if (state is ContentState.Empty) "Not downloaded yet." else "Loading…", style = MaterialTheme.typography.bodyMedium, color = p.text3)
        }

        // What the last refresh brought in (also announced right after it finishes).
        when (val s = sync) {
            is SyncState.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SlateLinearLoader(Modifier.fillMaxWidth())
                Text(s.message, style = MaterialTheme.typography.labelMedium, color = p.text3)
            }
            is SyncState.Failed -> Text("Refresh failed · ${s.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            SyncState.Idle -> last?.let { r ->
                val d = r.delta
                val what = when {
                    r.firstDownload -> "first download, ${d.added} questions"
                    d.isEmpty -> "nothing new"
                    else -> listOfNotNull(
                        d.added.takeIf { it > 0 }?.let { "$it new" },
                        d.updated.takeIf { it > 0 }?.let { "$it edited" },
                        d.removed.takeIf { it > 0 }?.let { "$it removed" },
                    ).joinToString(", ")
                }
                Text("Last refresh ${formatShortDate(r.at)} · $what", style = MaterialTheme.typography.bodySmall, color = if (d.added > 0 && !r.firstDownload) p.ok else p.text3)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.refreshContent(id) }, enabled = online && sync !is SyncState.Running) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (ready != null) "Refresh" else "Download")
            }
            if (ready != null) TextButton(onClick = { confirmRemove = true }) { Text("Remove copy") }
            if (!online) Text("Offline", style = MaterialTheme.typography.labelMedium, color = p.warn)
        }

        HorizontalDivider(color = p.outline)

        // Account: who is signed in, the pools this account is building, and sign in / out.
        val s = session
        if (s == null) {
            Text("Sign in to keep Nailed / Important / Weak on every device.", style = MaterialTheme.typography.bodySmall, color = p.text3)
            if (m.config.googleWebClientId.isBlank()) {
                Text("Google sign-in isn't configured yet (set GOOGLE_WEB_CLIENT_ID_${id.name} in local.properties).", style = MaterialTheme.typography.bodySmall, color = p.warn)
            }
            OutlinedButton(onClick = { vm.signIn(activity, id) }, enabled = online && m.config.googleWebClientId.isNotBlank()) { Text("Sign in with Google") }
        } else {
            val flags by m.progress.flags.collectAsState()
            val totals = remember(flags) { Triple(flags.values.count { it.nailed }, flags.values.count { it.important }, flags.values.count { it.weak }) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.email ?: "Signed in", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { vm.signOut(id) }) { Text("Sign out") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CountPill("Nailed", totals.first, p.ok)
                CountPill("Important", totals.second, p.imp)
                CountPill("Weak", totals.third, p.warn)
            }
        }
        vm.authError?.takeIf { it.startsWith(id.title) }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remove ${id.title}'s offline copy?") },
        text = { Text("The questions are deleted from this phone until you download them again. Your flags and notes are kept.") },
        confirmButton = { TextButton(onClick = { confirmRemove = false; vm.clearContent(id) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(MaterialTheme.shapes.small).background(p.elevated).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.text3)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Text(
        text, Modifier.clip(CircleShape).background(color.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1,
    )
}

@Composable
private fun CountPill(label: String, n: Int, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$n", style = MaterialTheme.typography.labelLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.text2)
    }
}

/** "Oct 8, 4:51 PM" — the year only when it isn't this year. */
private fun formatShortDate(ms: Long): String {
    if (ms <= 0) return "—"
    val sameYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) ==
        java.util.Calendar.getInstance().apply { timeInMillis = ms }.get(java.util.Calendar.YEAR)
    return java.text.SimpleDateFormat(if (sameYear) "MMM d, h:mm a" else "MMM d yyyy, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(LocalPalette.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
