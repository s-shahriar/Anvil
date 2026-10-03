package com.syed.anvil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.ModuleContent
import com.syed.anvil.content.SyncState
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.component.Dot
import com.syed.anvil.ui.component.formatDate
import com.syed.anvil.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(vm: AnvilViewModel, id: ModuleId, onBack: () -> Unit, onTopic: (group: String, topic: String) -> Unit) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val sync by m.content.sync.collectAsState()
    val online by vm.online.collectAsState()
    val flags by m.progress.flags.collectAsState()
    LaunchedEffect(id) { vm.openModule(id) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(id.title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.refreshContent(id) }, enabled = online && sync !is SyncState.Running) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (!online) OfflineBanner(state, m.content.cachedAt)
            if (sync is SyncState.Running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text((sync as SyncState.Running).message, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
            (sync as? SyncState.Failed)?.let {
                Text("Couldn't update: ${it.message}", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            when (val s = state) {
                is ContentState.Ready -> ModuleBody(s.content, flags, onTopic)
                ContentState.Empty -> if (sync !is SyncState.Running) EmptyState(online) { vm.refreshContent(id) }
                ContentState.NotLoaded -> Unit
            }
        }
    }
}

@Composable
private fun OfflineBanner(state: ContentState, cachedAt: Long) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(p.warn.copy(alpha = .16f)).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.WifiOff, null, tint = p.warn)
        Text(
            if (state is ContentState.Ready) "Offline — using the copy downloaded ${formatDate(cachedAt)}" else "Offline",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EmptyState(online: Boolean, onDownload: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nothing downloaded yet", style = MaterialTheme.typography.titleLarge)
        Text(
            if (online) "Download this module once and it works without a connection." else "Connect to the internet to download this module for offline use.",
            Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onDownload, enabled = online) { Text("Download") }
    }
}

@Composable
private fun ModuleBody(content: ModuleContent, flags: Map<String, com.syed.anvil.progress.Flag>, onTopic: (String, String) -> Unit) {
    val p = LocalPalette.current
    var selected by remember(content) { mutableStateOf(content.groups.firstOrNull()?.key) }
    val group = content.groups.firstOrNull { it.key == selected } ?: content.groups.firstOrNull()
    val uids = remember(content) { content.allItems().mapNotNull { it.uid }.toHashSet() }
    val mine = flags.filterKeys { it in uids }.values

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Counter("Nailed", mine.count { it.nailed }, p.ok, Modifier.weight(1f))
                Counter("Important", mine.count { it.important }, p.imp, Modifier.weight(1f))
                Counter("Weak", mine.count { it.weak }, p.warn, Modifier.weight(1f))
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(content.groups, key = { it.key }) { g ->
                    FilterChip(selected = g.key == group?.key, onClick = { selected = g.key }, label = { Text("${g.title} · ${g.count}") })
                }
            }
        }
        items(group?.topics.orEmpty(), key = { it.key }) { t ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface)
                    .clickable { onTopic(t.group, t.slug) }.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${t.count}", style = MaterialTheme.typography.labelLarge, color = p.text3)
            }
        }
    }
}

@Composable
private fun Counter(label: String, n: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(LocalPalette.current.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(color); Text(label, style = MaterialTheme.typography.labelMedium)
        }
        Text("$n", style = MaterialTheme.typography.headlineMedium)
    }
}
