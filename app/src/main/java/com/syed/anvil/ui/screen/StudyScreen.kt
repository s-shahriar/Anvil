package com.syed.anvil.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.Item
import com.syed.anvil.content.PoolSet
import com.syed.anvil.content.QuizPool
import com.syed.anvil.content.SearchText
import com.syed.anvil.content.Subtopics
import com.syed.anvil.content.TopicCatalog
import com.syed.anvil.progress.Flag
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.component.Pager
import com.syed.anvil.ui.quiz.StudyCard
import com.syed.anvil.ui.theme.LocalPalette
import kotlinx.coroutines.launch

private const val PAGE = 20

private enum class StudyFilter(val label: String) { ALL("সব"), IMPORTANT("Important"), WEAK("Weak") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(vm: AnvilViewModel, id: ModuleId, group: String, topic: String, focus: String?, onBack: () -> Unit) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val flags by m.progress.flags.collectAsState()
    val content = (state as? ContentState.Ready)?.content
    val name = content?.groups?.firstOrNull { it.key == group }?.topics?.firstOrNull { it.slug == topic }?.name ?: TopicCatalog.prettify(topic)
    val all = remember(content, group, topic) { content?.items(group, topic).orEmpty().filter { it.isQuizzable } }

    var filter by rememberSaveable { mutableStateOf(StudyFilter.ALL) }
    var showNailed by rememberSaveable { mutableStateOf(focus != null) }
    var query by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    // LiveMCQ topics can also be browsed by sub-topic (সন্ধি, সমাস …): a picker first, then that sub-topic's questions.
    val subList = remember(content, group, topic) { if (group == "livemcq") content?.subtopicsFor(topic).orEmpty() else emptyList() }
    var bySub by rememberSaveable { mutableStateOf(false) }
    var activeSub by rememberSaveable { mutableStateOf<String?>(null) }
    val haystacks = remember(all) { all.associate { it.id to SearchText.haystack(it) } }
    val tokens = SearchText.tokens(query)

    val subtopicCards = remember(all, flags, subList) {
        Subtopics.cards(all.filter { flags[it.uid]?.nailed != true }, subList)
    }
    val showPicker = bySub && activeSub == null && subList.isNotEmpty()
    val visible = remember(all, flags, filter, showNailed, tokens, bySub, activeSub) {
        all.filter { item ->
            val f = item.uid?.let(flags::get)
            (!bySub || activeSub == null || Subtopics.inSubtopic(item, activeSub!!, subList)) &&
                (showNailed || f?.nailed != true) &&
                when (filter) {
                    StudyFilter.ALL -> true
                    StudyFilter.IMPORTANT -> QuizPool.matches(PoolSet.IMPORTANT, f)
                    StudyFilter.WEAK -> QuizPool.matches(PoolSet.WEAK, f)
                } &&
                (tokens.isEmpty() || SearchText.matches(haystacks.getValue(item.id), tokens))
        }
    }
    val pages = maxOf(1, (visible.size + PAGE - 1) / PAGE)
    val shown = visible.drop(page.coerceAtMost(pages - 1) * PAGE).take(PAGE)
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Deep link from search: jump to the page holding the question, and scroll to it.
    LaunchedEffect(focus, visible) {
        val i = visible.indexOfFirst { it.uid == focus }
        if (focus != null && i >= 0) { page = i / PAGE; list.scrollToItem(1 + i % PAGE) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad), state = list,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        query, { query = it; page = 0 }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search in this topic") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudyFilter.entries.forEach { f -> FilterChip(filter == f, { filter = f; page = 0 }, { Text(f.label) }) }
                        FilterChip(showNailed, { showNailed = !showNailed; page = 0 }, { Text("Nailed") })
                    }
                    Text("${visible.size} of ${all.size} questions", style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.text3)
                }
            }
            if (subList.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!bySub, { bySub = false; activeSub = null; page = 0 }, { Text("সব একসাথে") })
                    FilterChip(bySub, { bySub = true; activeSub = null; page = 0 }, { Text("Sub-topic অনুযায়ী") })
                }
            }
            if (showPicker) {
                items(subtopicCards, key = { it.first.slug }) { (sub, n) ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(LocalPalette.current.surface)
                            .clickable { activeSub = sub.slug; page = 0 }.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sub.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                if (bySub && activeSub != null) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { activeSub = null; page = 0 }) { Text("‹ সব sub-topic") }
                        Text(
                            if (activeSub == Subtopics.NONE) Subtopics.NONE_NAME else subList.firstOrNull { it.slug == activeSub }?.name.orEmpty(),
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(shown, key = { it.id }) { item ->
                    StudyCard(id, item, item.uid?.let(flags::get) ?: Flag(), m.progress, highlighted = item.uid == focus)
                }
                // A new page starts at its first question, not wherever the old one was scrolled to.
                item { Pager(page.coerceAtMost(pages - 1), pages) { page = it; scope.launch { list.scrollToItem(0) } } }
            }
        }
    }
}
