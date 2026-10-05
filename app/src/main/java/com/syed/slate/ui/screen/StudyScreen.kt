package com.syed.slate.ui.screen

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import com.syed.slate.ui.screen.LivemcqAdmin
import com.syed.slate.ui.screen.LivemcqClassifySheet
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.ContentState
import com.syed.slate.content.Item
import com.syed.slate.content.PoolSet
import com.syed.slate.content.QuizPool
import com.syed.slate.content.SearchText
import com.syed.slate.content.Subtopics
import com.syed.slate.content.TopicCatalog
import com.syed.slate.progress.Flag
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.component.Pager
import com.syed.slate.ui.quiz.StudyCard
import com.syed.slate.ui.theme.LocalPalette
import kotlinx.coroutines.launch

private const val PAGE = 20

/** One filter at a time, as the web's: সব / Important / Weak list the un-nailed questions; Nailed lists the nailed ones. */
private enum class StudyFilter(val label: String) { ALL("সব"), IMPORTANT("Important"), WEAK("Weak"), NAILED("Nailed") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(vm: SlateViewModel, id: ModuleId, group: String, topic: String, focus: String?, onBack: () -> Unit) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val flags by m.progress.flags.collectAsState()
    val content = (state as? ContentState.Ready)?.content
    val name = content?.groups?.firstOrNull { it.key == group }?.topics?.firstOrNull { it.slug == topic }?.name ?: TopicCatalog.prettify(topic)
    val all = remember(content, group, topic) { content?.items(group, topic).orEmpty().filter { it.isQuizzable } }

    var filter by rememberSaveable { mutableStateOf(StudyFilter.ALL) }
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
    val scoped = remember(all, flags, bySub, activeSub) {
        all.filter { item -> !bySub || activeSub == null || Subtopics.inSubtopic(item, activeSub!!, subList) }
    }
    fun StudyFilter.accepts(f: Flag?): Boolean = when (this) {
        StudyFilter.NAILED -> f?.nailed == true
        StudyFilter.ALL -> f?.nailed != true
        StudyFilter.IMPORTANT -> f?.nailed != true && QuizPool.matches(PoolSet.IMPORTANT, f)
        StudyFilter.WEAK -> f?.nailed != true && QuizPool.matches(PoolSet.WEAK, f)
    }
    val counts = remember(scoped, flags) { StudyFilter.entries.associateWith { fl -> scoped.count { fl.accepts(it.uid?.let(flags::get)) } } }
    val visible = remember(scoped, flags, filter, tokens) {
        scoped.filter { item ->
            filter.accepts(item.uid?.let(flags::get)) && (tokens.isEmpty() || SearchText.matches(haystacks.getValue(item.id), tokens))
        }
    }
    val pages = maxOf(1, (visible.size + PAGE - 1) / PAGE)
    val shown = visible.drop(page.coerceAtMost(pages - 1) * PAGE).take(PAGE)
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val session by m.auth.session.collectAsState()

    // Owner-only LiveMCQ per-card topic / sub-topic fix (the web's QuestionEditButton).
    var editItem by remember { mutableStateOf<com.syed.slate.content.Item?>(null) }
    var editBusy by remember { mutableStateOf(false) }
    val toastCtx = LocalContext.current
    if (editItem != null) {
        val uid = editItem!!.uid
        var row by remember(editItem) { mutableStateOf<LivemcqAdmin.Row?>(null) }
        var loadError by remember(editItem) { mutableStateOf<String?>(null) }
        LaunchedEffect(editItem) {
            try { row = uid?.let { LivemcqAdmin.fetchByUid(m.db, it) } } catch (e: Exception) { loadError = e.message }
        }
        fun toast(s: String) = android.widget.Toast.makeText(toastCtx, s, android.widget.Toast.LENGTH_SHORT).show()
        when {
            loadError != null -> AlertDialog(onDismissRequest = { editItem = null }, title = { Text("Couldn't load") }, text = { Text(loadError ?: "") }, confirmButton = { TextButton(onClick = { editItem = null }) { Text("OK") } })
            row != null -> LivemcqClassifySheet(
                title = "Topic / sub-topic", currentSlug = row!!.slug, currentSub = row!!.subtopic, m = m, busy = editBusy,
                onDismiss = { editItem = null },
            ) { slug, sub ->
                val fid = row!!.favoriteId
                if (fid == null) { editItem = null; return@LivemcqClassifySheet }
                scope.launch {
                    editBusy = true
                    try {
                        if (slug != row!!.slug) LivemcqAdmin.setCategory(m.db, listOf(fid), slug)
                        if (sub != row!!.subtopic) LivemcqAdmin.setSubtopic(m.db, listOf(fid), sub)
                        editItem = null
                        toast("Saved")
                        if (slug != row!!.slug) m.content.refresh() // the question moved: the module's lists must follow
                    } catch (e: Exception) { toast(e.message ?: "Failed") }
                    editBusy = false
                }
            }
        }
    }

    // A deep link to a nailed question opens the Nailed list so the card is there to be found.
    LaunchedEffect(focus) { if (focus != null && flags[focus]?.nailed == true) filter = StudyFilter.NAILED }

    // Deep link from search: jump to the page holding the question, and scroll to it.
    LaunchedEffect(focus, visible) {
        val i = visible.indexOfFirst { it.uid == focus }
        if (focus != null && i >= 0) { page = i / PAGE; list.scrollToItem(1 + i % PAGE) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { BarTitle(name, style = MaterialTheme.typography.titleLarge) },
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
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StudyFilter.entries.forEach { f ->
                            FilterChip(filter == f, { filter = f; page = 0 }, { Text("${f.label} (${counts[f] ?: 0})", maxLines = 1, softWrap = false) })
                        }
                    }
                    Text("${visible.size} of ${scoped.size} questions", style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.text3)
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
                val first = page.coerceAtMost(pages - 1) * PAGE
                itemsIndexed(shown, key = { _, item -> item.id }) { i, item ->
                    StudyCard(id, item, item.uid?.let(flags::get) ?: Flag(), m.progress, highlighted = item.uid == focus, number = first + i + 1,
                        onLiveMcqEdit = if (group == "livemcq" && LivemcqAdmin.isOwner(session?.userId)) { it -> editItem = it } else null)
                }
                // A new page starts at its first question, not wherever the old one was scrolled to.
                item { Pager(page.coerceAtMost(pages - 1), pages) { page = it; scope.launch { list.scrollToItem(0) } } }
            }
        }
    }
}
