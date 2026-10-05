package com.syed.slate.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateTopBar
import com.syed.slate.ui.reader.QuestionPeekBar
import kotlinx.coroutines.launch
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.ContentState
import com.syed.slate.content.Item
import com.syed.slate.content.LongForm
import com.syed.slate.content.PoolSet
import com.syed.slate.content.QuizPool
import com.syed.slate.content.SearchText
import com.syed.slate.content.TopicCatalog
import com.syed.slate.content.segment
import com.syed.slate.progress.Flag
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.reader.LongCard
import com.syed.slate.ui.theme.LocalPalette

private enum class ReaderFilter(val label: String) { ALL("সব"), IMPORTANT("Important"), WEAK("Weak") }

private sealed interface ListRow {
    data class SegmentCard(val name: String, val count: Int) : ListRow
    data class Label(val text: String, val key: String) : ListRow
    data class Card(val item: Item, val number: Int) : ListRow
}

/**
 * Written, Extra and Viva answers for one topic. Nailed cards are hidden, like in the web app. Questions tagged with
 * a segment sit behind entry cards and open on a page of their own ([segment]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    vm: SlateViewModel, id: ModuleId, group: String, topic: String, segment: String?, focus: String?,
    onBack: () -> Unit, onSegment: (String) -> Unit, onFocusSegment: (String) -> Unit,
) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val flags by m.progress.flags.collectAsState()
    val p = LocalPalette.current
    val content = (state as? ContentState.Ready)?.content
    val name = content?.groups?.firstOrNull { it.key == group }?.topics?.firstOrNull { it.slug == topic }?.name ?: TopicCatalog.prettify(topic)
    val all = remember(content, group, topic) { content?.items(group, topic).orEmpty() }

    var filter by rememberSaveable { mutableStateOf(ReaderFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    val haystacks = remember(all) { all.associate { it.id to SearchText.haystack(it) } }
    val tokens = SearchText.tokens(query)

    // Counts and filters are scoped to the segment page when one is open.
    val notNailed = remember(all, flags, segment) { all.filter { flags[it.uid]?.nailed != true && (segment == null || it.segment == segment) } }
    val importantCount = notNailed.count { QuizPool.matches(PoolSet.IMPORTANT, flags[it.uid]) }
    val weakCount = notNailed.count { QuizPool.matches(PoolSet.WEAK, flags[it.uid]) }
    val visible = remember(notNailed, flags, filter, tokens, segment) {
        notNailed.filter { item ->
            val f = flags[item.uid]
            when (filter) {
                    ReaderFilter.ALL -> true
                    ReaderFilter.IMPORTANT -> QuizPool.matches(PoolSet.IMPORTANT, f)
                    ReaderFilter.WEAK -> QuizPool.matches(PoolSet.WEAK, f)
                } && (tokens.isEmpty() || SearchText.matches(haystacks.getValue(item.id), tokens))
        }
    }

    val rows = remember(visible, segment) {
        val layout = LongForm.layout(visible)
        buildList {
            if (segment == null) {
                layout.segments.forEach { add(ListRow.SegmentCard(it.name, it.count)) }
                if (layout.regular.isNotEmpty()) add(ListRow.Label("${layout.regular.size} টি প্রশ্ন", "regular"))
                layout.regular.forEachIndexed { i, it -> add(ListRow.Card(it, i + 1)) }
            } else {
                layout.segments.forEach { seg -> seg.subgroups.forEach { sub ->
                    sub.name?.let { add(ListRow.Label(it, "sub-$it")) }
                    sub.items.forEachIndexed { i, it -> add(ListRow.Card(it, i + 1)) }
                } }
            }
        }
    }

    // Deep link from search: a segmented question is not on the topic page, so go to its segment first;
    // then open the card and scroll to it.
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(focus, all) {
        val target = all.firstOrNull { it.uid == focus } ?: return@LaunchedEffect
        if (segment == null && target.segment != null) { onFocusSegment(target.segment!!); return@LaunchedEffect }
        if (focus !in open) open = open + focus!!
    }
    LaunchedEffect(focus, rows) {
        val i = rows.indexOfFirst { it is ListRow.Card && it.item.uid == focus }
        if (focus != null && i >= 0) list.scrollToItem(i + 1)
    }

    // QuestionPeek (see QuestionPeekBar): shown while an open card's header has scrolled off but its body is
    // still on screen (the web stops 140px before the card ends so the bar never covers the last lines).
    val density = androidx.compose.ui.platform.LocalDensity.current
    val peek = remember(rows, open) {
        val headerPx = with(density) { 64.dp.toPx() }; val tailPx = with(density) { 140.dp.toPx() }
        derivedStateOf {
            val vis = list.layoutInfo.visibleItemsInfo
            val top = vis.firstOrNull { it.offset < -headerPx && it.offset + it.size > tailPx } ?: return@derivedStateOf null
            (rows.getOrNull(top.index - 1) as? ListRow.Card)?.takeIf { it.item.uid in open || it.item.id in open }?.let { it to top.index }
        }
    }
    val peekState by peek

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { BarTitle(segment ?: name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                Modifier.fillMaxSize(), state = list,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search in this topic") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(filter == ReaderFilter.ALL, { filter = ReaderFilter.ALL }, { Text("সব (${notNailed.size})") })
                        FilterChip(filter == ReaderFilter.IMPORTANT, { filter = ReaderFilter.IMPORTANT }, { Text("Important ($importantCount)") })
                        FilterChip(filter == ReaderFilter.WEAK, { filter = ReaderFilter.WEAK }, { Text("Weak ($weakCount)") })
                    }
                }
            }
            if (rows.isEmpty()) item {
                Text(
                    when {
                        filter == ReaderFilter.IMPORTANT -> "কোনো Important প্রশ্ন নেই।"
                        filter == ReaderFilter.WEAK -> "কোনো Weak প্রশ্ন নেই।"
                        tokens.isNotEmpty() -> "কিছু পাওয়া যায়নি।"
                        all.isNotEmpty() -> "সব প্রশ্ন nailed! 🎉"
                        else -> "এই topic-এ এখনো কোনো প্রশ্ন নেই।"
                    },
                    Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge,
                )
            }
            items(rows, key = { r -> when (r) { is ListRow.SegmentCard -> "seg-${r.name}"; is ListRow.Label -> "label-${r.key}"; is ListRow.Card -> r.item.id } }) { r ->
                when (r) {
                    is ListRow.SegmentCard -> Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.primaryContainer).clickable { onSegment(r.name) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = p.onPrimaryContainer)
                        Text("${r.count}", style = MaterialTheme.typography.labelLarge, color = p.onPrimaryContainer)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.onPrimaryContainer)
                    }
                    is ListRow.Label -> Text(r.text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    is ListRow.Card -> LongCard(
                        item = r.item, number = r.number, open = r.item.uid in open || r.item.id in open,
                        flag = flags[r.item.uid] ?: Flag(), progress = m.progress,
                        onToggle = {
                            val key = r.item.uid ?: r.item.id
                            open = if (key in open) open - key else open + key
                        },
                        focused = r.item.uid == focus,
                    )
                }
            }
        }
            QuestionPeekBar(
                question = peekState?.first?.item?.question, number = peekState?.first?.number,
                onJump = { peekState?.second?.let { i -> scope.launch { list.animateScrollToItem(i) } } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
