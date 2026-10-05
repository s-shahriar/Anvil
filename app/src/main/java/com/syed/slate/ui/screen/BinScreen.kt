package com.syed.slate.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.ContentState
import com.syed.slate.content.TopicCatalog
import com.syed.slate.trash.BinEntry
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateTopBar
import com.syed.slate.ui.rich.HtmlParser
import com.syed.slate.ui.theme.LocalPalette

/**
 * The recycle bin: what this phone has hidden but not yet told the server about, plus what the server holds (needs a
 * connection). Restore brings a question back; delete-forever removes it for good and asks twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinScreen(vm: SlateViewModel, id: ModuleId, onBack: () -> Unit) {
    val m = vm.module(id)
    val trash = m.trash
    val hidden by trash.hidden.collectAsState()
    val online by vm.online.collectAsState()
    val error by trash.error.collectAsState()
    val contentState by m.content.state.collectAsState()
    val p = LocalPalette.current
    var server by remember { mutableStateOf<List<BinEntry>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var purging by remember { mutableStateOf<String?>(null) } // id armed for a second tap
    val gone = remember { mutableStateOf(setOf<String>()) }   // restored / purged this visit: hide at once

    LaunchedEffect(online, id) {
        if (!online) return@LaunchedEffect
        loading = true; failure = null
        runCatching { trash.fetchBin() }.onSuccess { server = it }.onFailure { failure = it.message }
        loading = false
    }

    val names = remember(contentState) { (contentState as? ContentState.Ready)?.content?.groups?.flatMap { it.topics }?.associate { it.key to it.name }.orEmpty() }
    // On this phone but not on the server yet.
    val local = m.content.hiddenItems().filter { trash.isPendingTrash(it.id) }.map {
        BinEntry(it.id, it.uid, it.group, it.topic, names["${it.group}/${it.topic}"], if (id == ModuleId.ICT) it.question else HtmlParser.plainText(it.question), null)
    }
    val localIds = local.map { it.id }.toSet()
    val entries = (local + server.orEmpty().filter { it.id !in localIds }).filter { it.id !in gone.value && trash.pendingOp(it.id).let { op -> op != com.syed.slate.trash.TrashOp.RESTORE && op != com.syed.slate.trash.TrashOp.PURGE } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { BarTitle("Recycle bin · ${id.title}", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) com.syed.slate.ui.component.SlateLinearLoader(Modifier.fillMaxWidth())
            error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            failure?.let { Text("Couldn't load the bin: $it", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (!online) Text("Offline — only changes made on this phone are listed.", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = p.warn)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (entries.isEmpty() && !loading) item { Text("The recycle bin is empty.", style = MaterialTheme.typography.bodyLarge, color = p.text3) }
                items(entries, key = { it.id }) { e ->
                    val waiting = e.id in localIds
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${TopicCatalog.groupTitle(e.group)} · ${e.topicName ?: names["${e.group}/${e.topic}"] ?: TopicCatalog.prettify(e.topic)}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        )
                        Text(e.text.lineSequence().first().trim(), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(if (waiting) "Waiting to sync" else e.deletedAt?.take(16)?.replace('T', ' ').orEmpty(), style = MaterialTheme.typography.labelSmall, color = p.text3)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { trash.restore(e.id); gone.value = gone.value + e.id }) { Text("Restore") }
                            // A question the server has not seen trashed has nothing to delete there yet.
                            if (!waiting) OutlinedButton(
                                onClick = { if (purging == e.id) { trash.purge(e.id); gone.value = gone.value + e.id; purging = null } else purging = e.id },
                                enabled = online,
                            ) { Text(if (purging == e.id) "Sure? Delete forever" else "Delete forever", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}
