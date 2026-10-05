package com.syed.slate.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.syed.slate.ui.component.HandMirror
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.ContentState
import com.syed.slate.content.Item
import com.syed.slate.content.LongForm
import com.syed.slate.ui.reader.LongCard
import com.syed.slate.content.PoolSet
import com.syed.slate.content.QuizPool
import com.syed.slate.progress.Flag
import com.syed.slate.progress.FlagRules
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.component.Pager
import com.syed.slate.ui.quiz.StudyCard
import kotlinx.coroutines.launch

private const val PAGE = 20

/**
 * Everything you have marked Nailed or Important in one section ([group] = "all" for the whole module), grouped
 * by topic chips. Important also has an "only Weak" switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(vm: SlateViewModel, id: ModuleId, kind: PoolSet, group: String, onBack: () -> Unit) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val flags by m.progress.flags.collectAsState()
    val content = (state as? ContentState.Ready)?.content
    var topic by rememberSaveable { mutableStateOf<String?>(null) } // group/slug
    var weakOnly by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val uiScope = rememberCoroutineScope()
    var openCards by rememberSaveable { mutableStateOf(listOf<String>()) }

    val scope = remember(content, group) {
        content?.groups?.filter { group == "all" || it.key == group }.orEmpty()
    }
    val topicNames = remember(scope) { scope.flatMap { it.topics }.associate { it.key to it.name } }
    val set = if (kind == PoolSet.IMPORTANT && weakOnly) PoolSet.WEAK else kind
    val saved: List<Item> = remember(content, scope, flags, set) {
        scope.flatMap { g -> g.topics.flatMap { content!!.items(g.key, it.slug) } }
            .filter { (it.isQuizzable || LongForm.isLongForm(it)) && QuizPool.matches(set, it.uid?.let(flags::get)) }
    }
    val perTopic = remember(saved) { saved.groupingBy { "${it.group}/${it.topic}" }.eachCount() }
    val shownItems = remember(saved, topic) { if (topic == null) saved else saved.filter { "${it.group}/${it.topic}" == topic } }
    val pages = maxOf(1, (shownItems.size + PAGE - 1) / PAGE)
    val title = if (kind == PoolSet.NAILED) "Nailed It" else "Important"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { BarTitle("$title · ${saved.size}", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(topic == null, { topic = null; page = 0 }, { Text("All topics · ${saved.size}") }) }
                    items(perTopic.entries.toList(), key = { it.key }) { (key, n) ->
                        FilterChip(topic == key, { topic = key; page = 0 }, { Text("${topicNames[key] ?: key} · $n") })
                    }
                }
            }
            if (kind == PoolSet.IMPORTANT) item {
                // SpaceBetween is symmetric, so the mirror swaps the switch and the destructive button to opposite edges.
                HandMirror {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(weakOnly, { weakOnly = it; page = 0 }); Text("Only Weak")
                        }
                        if (topic != null) TextButton(onClick = { confirmClear = true }) { Text("Remove all") }
                    }
                }
            }
            if (shownItems.isEmpty()) item { Text("Nothing here yet.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge) }
            val first = page.coerceAtMost(pages - 1) * PAGE
            itemsIndexed(shownItems.drop(first).take(PAGE), key = { _, item -> item.id }) { i, item ->
                val label = topicNames["${item.group}/${item.topic}"]
                if (LongForm.isLongForm(item)) {
                    val key = item.uid ?: item.id
                    LongCard(item, null, key in openCards, item.uid?.let(flags::get) ?: Flag(), m.progress,
                        onToggle = { openCards = if (key in openCards) openCards - key else openCards + key }, topicLabel = label)
                } else {
                    StudyCard(id, item, item.uid?.let(flags::get) ?: Flag(), m.progress, topicLabel = label, number = first + i + 1)
                }
            }
            item { Pager(page.coerceAtMost(pages - 1), pages) { page = it; uiScope.launch { list.scrollToItem(0) } } }
        }
    }

    if (confirmClear) {
        val clearing = shownItems
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Remove all ${clearing.size} from Important?") },
            text = { Text("They stay in the topic; only the Important and Weak marks are removed.") },
            confirmButton = {
                Button(onClick = {
                    clearing.forEach { item -> item.uid?.let { uid -> m.progress.update(uid) { f -> f.copy(important = false, weak = false) } } }
                    confirmClear = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
