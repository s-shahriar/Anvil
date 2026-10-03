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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syed.anvil.AnvilApp
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.Item
import com.syed.anvil.content.LongForm
import com.syed.anvil.content.ModuleContent
import com.syed.anvil.content.PoolSet
import com.syed.anvil.content.QuizPool
import com.syed.anvil.content.SearchText
import com.syed.anvil.content.SyncState
import com.syed.anvil.progress.Flag
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.component.Dot
import com.syed.anvil.ui.component.Pager
import com.syed.anvil.ui.component.formatDate
import com.syed.anvil.ui.rich.HtmlParser
import com.syed.anvil.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModuleNav(
    val onBack: () -> Unit,
    val onTopic: (group: String, topic: String) -> Unit,
    val onExam: (group: String) -> Unit,
    val onSaved: (kind: PoolSet, group: String) -> Unit,
    val onSearchHit: (Item) -> Unit,
    val onWritten: () -> Unit,
    val onPractice: (String) -> Unit,
    val onPracticeImportant: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(vm: AnvilViewModel, id: ModuleId, nav: ModuleNav) {
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
                navigationIcon = { IconButton(onClick = nav.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
                is ContentState.Ready -> ModuleBody(id, s.content, flags, nav)
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

private const val SEARCH_PAGE = 8
private const val PRACTICE = "practice"

@Composable
private fun ModuleBody(id: ModuleId, content: ModuleContent, flags: Map<String, Flag>, nav: ModuleNav) {
    val p = LocalPalette.current
    var selected by rememberSaveable { mutableStateOf(content.groups.firstOrNull()?.key) }
    // ICT has one more section that is not in the database: the bundled Linux and SQL drills.
    val inPractice = id == ModuleId.ICT && selected == PRACTICE
    val app = LocalContext.current.applicationContext as AnvilApp
    val practiceFlags = remember(flags) { flags.filter { it.key.startsWith("practice__") } }
    val group = content.groups.firstOrNull { it.key == selected } ?: content.groups.firstOrNull() ?: return
    val groupItems = remember(content, group) { group.topics.flatMap { content.items(group.key, it.slug) }.filter { it.isQuizzable || LongForm.isLongForm(it) } }
    val counts = remember(groupItems, flags) { QuizPool.counts(groupItems, flags, includeLongForm = true) }
    val topicNames = remember(group) { group.topics.associate { it.slug to it.name } }

    var query by rememberSaveable { mutableStateOf("") }
    var debounced by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(query) { delay(250); debounced = query; page = 0 }
    // Normalised once per group, off the main thread: a few thousand questions is real work.
    val haystacks by produceState<Map<String, String>>(emptyMap(), groupItems) {
        value = withContext(Dispatchers.Default) { groupItems.associate { it.id to SearchText.haystack(it) } }
    }
    val tokens = SearchText.tokens(debounced)
    val hits = remember(tokens, haystacks, groupItems) {
        if (tokens.isEmpty()) emptyList() else groupItems.filter { SearchText.matches(haystacks[it.id].orEmpty(), tokens) }
    }

    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (inPractice) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val saved = practiceFlags.values.count { it.important }
                val weak = practiceFlags.values.count { it.important && it.weak }
                Action(Icons.Filled.Bookmark, "Important", saved, p.imp, Modifier.weight(1f)) { nav.onPracticeImportant() }
                Column(Modifier.weight(1f).padding(14.dp)) { Text("$weak weak", style = MaterialTheme.typography.labelLarge, color = p.warn) }
            }
        } else item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Exams draw multiple-choice questions only; Written/Extra/Viva have none.
                if (!LongForm.isLongForm(group.key)) Action(Icons.Filled.Timer, "Exam", null, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { nav.onExam(group.key) }
                Action(Icons.Filled.Star, "Nailed", counts.getValue(PoolSet.NAILED), p.ok, Modifier.weight(1f)) { nav.onSaved(PoolSet.NAILED, group.key) }
                Action(Icons.Filled.Bookmark, "Important", counts.getValue(PoolSet.IMPORTANT), p.imp, Modifier.weight(1f)) { nav.onSaved(PoolSet.IMPORTANT, group.key) }
            }
        }
        if (content.writtenCards.isNotEmpty()) item {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.primaryContainer).clickable(onClick = nav.onWritten).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Written · Data", style = MaterialTheme.typography.titleMedium, color = p.onPrimaryContainer)
                    Text("লিখিত পরীক্ষার তথ্য সংকলন", style = MaterialTheme.typography.bodySmall, color = p.onPrimaryContainer)
                }
                Text("${content.writtenCards.size}", style = MaterialTheme.typography.labelLarge, color = p.onPrimaryContainer)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(content.groups, key = { it.key }) { g ->
                    FilterChip(selected = !inPractice && g.key == group.key, onClick = { selected = g.key; query = ""; debounced = "" }, label = { Text("${g.title} · ${g.count}") })
                }
                if (id == ModuleId.ICT) item(key = PRACTICE) {
                    FilterChip(selected = inPractice, onClick = { selected = PRACTICE; query = ""; debounced = "" },
                        label = { Text("Practice · ${app.practice.sumOf { c -> c.topics.sumOf { t -> t.practice.size } }}") })
                }
            }
        }
        if (!inPractice) item {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search ${group.title}") },
            )
        }
        if (inPractice) {
            items(app.practice, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).clickable { nav.onPractice(c.id) }.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${c.name} Practice", style = MaterialTheme.typography.titleMedium)
                        Text("${c.topics.size} topics · ${c.topics.sumOf { t -> t.practice.size }} drills", style = MaterialTheme.typography.labelMedium, color = p.text3)
                    }
                }
            }
        } else if (tokens.isNotEmpty()) {
            item { Text("${hits.size} results", style = MaterialTheme.typography.labelMedium, color = p.text3) }
            val pages = maxOf(1, (hits.size + SEARCH_PAGE - 1) / SEARCH_PAGE)
            items(hits.drop(page.coerceAtMost(pages - 1) * SEARCH_PAGE).take(SEARCH_PAGE), key = { it.id }) { hit ->
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).clickable { nav.onSearchHit(hit) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(topicNames[hit.topic] ?: hit.topic, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    val text = if (id == ModuleId.ICT) hit.question.lineSequence().first() else HtmlParser.plainText(hit.question)
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            item { Pager(page.coerceAtMost(pages - 1), pages) { page = it; scope.launch { list.scrollToItem(2) } } }
        } else {
            items(group.topics, key = { it.key }) { t ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface)
                        .clickable { nav.onTopic(t.group, t.slug) }.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("${t.count}", style = MaterialTheme.typography.labelLarge, color = p.text3)
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, count: Int?, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(LocalPalette.current.surface).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.padding(0.dp), tint = color); Text(label, style = MaterialTheme.typography.labelMedium)
        }
        Text(count?.toString() ?: "Start", style = MaterialTheme.typography.titleLarge, color = if (count == null) color else Color.Unspecified)
    }
}
