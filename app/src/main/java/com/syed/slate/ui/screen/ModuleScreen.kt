package com.syed.slate.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.syed.slate.ui.component.SlateTopBar
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.syed.slate.SlateApp
import com.syed.slate.backend.ModuleId
import com.syed.slate.ui.component.HandMirror
import com.syed.slate.ui.component.LocalOpenSyncQueue
import com.syed.slate.ui.screen.AdminScreen
import com.syed.slate.ui.screen.LivemcqAdmin
import com.syed.slate.content.ContentState
import com.syed.slate.content.Item
import com.syed.slate.content.LongForm
import com.syed.slate.content.ModuleContent
import com.syed.slate.content.PoolSet
import com.syed.slate.content.QuizPool
import com.syed.slate.content.SearchText
import com.syed.slate.content.SyncState
import com.syed.slate.content.TopicCatalog
import com.syed.slate.progress.Flag
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.component.Dot
import com.syed.slate.ui.component.Pager
import com.syed.slate.ui.component.SyncStatusStrip
import com.syed.slate.ui.component.formatDate
import com.syed.slate.ui.rich.HtmlParser
import com.syed.slate.ui.theme.LocalPalette
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
    val onBin: () -> Unit,
    val onPractice: (String) -> Unit,
    val onPracticeImportant: () -> Unit,
    val onMath: () -> Unit,
    val onFinance: () -> Unit,
    val onEquation: (String) -> Unit,
    val onAdmin: () -> Unit = {},
)

/** Section keys that are not database groups. */
private const val PRACTICE = "practice"
private const val EQUATION = "equation"
private const val WRITTEN = "written"
private const val UTILITY = "utility"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(vm: SlateViewModel, id: ModuleId, nav: ModuleNav) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val sync by m.content.sync.collectAsState()
    val online by vm.online.collectAsState()
    val flags by m.progress.flags.collectAsState()
    val session by m.auth.session.collectAsState()
    LaunchedEffect(id) { vm.openModule(id) }
    // null = the sub-module launcher (Magpie-style cards); otherwise the section's own page.
    var selected by rememberSaveable(id) { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = selected != null) { selected = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { Text(id.title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = { if (selected != null) selected = null else nav.onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = nav.onBin) { Icon(Icons.Filled.DeleteOutline, "Recycle bin") }
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
            val openQueue = LocalOpenSyncQueue.current
            SyncStatusStrip(m, online, onOpen = { openQueue?.invoke() })
            if (sync is SyncState.Running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text((sync as SyncState.Running).message, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
            (sync as? SyncState.Failed)?.let {
                Text("Couldn't update: ${it.message}", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            when (val s = state) {
                is ContentState.Ready -> {
                    val sel = selected
                    if (sel == null) SubModuleLauncher(id, s.content) { key ->
                        // General's Written is its own page; every other card opens in place.
                        if (id == ModuleId.GENERAL && key == WRITTEN) nav.onWritten() else selected = key
                    } else ModuleBody(id, s.content, flags, nav, sel, owner = LivemcqAdmin.isOwner(session?.userId))
                }
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

/**
 * The web app's shape, translated: General is three submodules (General / Vocabulary / Utility) behind a tab row;
 * each part then has a section dropdown (current section named on the trigger, all sections with counts in the menu)
 * and a vertical topic grid. ICT has the one dropdown: MCQ / Written / Extra / Viva / Equation / Practice.
 */
@Composable
private fun ModuleBody(id: ModuleId, content: ModuleContent, flags: Map<String, Flag>, nav: ModuleNav, selected: String, owner: Boolean = false) {
    val p = LocalPalette.current
    val groupKeys = content.groups.map { it.key }
    val section = selected
    val inVocab = id == ModuleId.GENERAL && section == "vocab"
    val inUtility = id == ModuleId.GENERAL && section == UTILITY
    val inPractice = id == ModuleId.ICT && section == PRACTICE
    val inEquation = id == ModuleId.ICT && section == EQUATION
    val app = LocalContext.current.applicationContext as SlateApp
    val equationTopics = remember { FormulaIndex.equationTopics(app) }
    val practiceFlags = remember(flags) { flags.filter { it.key.startsWith("practice__") } }
    val group = content.groups.firstOrNull { it.key == section }
    val groupItems = remember(content, group) { group?.topics?.flatMap { content.items(group.key, it.slug) }.orEmpty().filter { it.isQuizzable || LongForm.isLongForm(it) } }
    val counts = remember(groupItems, flags) { QuizPool.counts(groupItems, flags, includeLongForm = true) }
    val topicNames = remember(group) { group?.topics?.associate { it.slug to it.name }.orEmpty() }
    val quizzableGroup = group != null && !inUtility && !inPractice && !inEquation

    var query by rememberSaveable(selected) { mutableStateOf("") }
    var debounced by rememberSaveable(selected) { mutableStateOf("") }
    var page by rememberSaveable(selected) { mutableIntStateOf(0) }
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
        // The section's hero: the sub-module card's icon coin, its name and size.
        if (!inUtility) item(key = "section-hero") {
            val sectionCount = content.groups.firstOrNull { g -> g.key == section }?.count
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(sectionIcon(id, section), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column {
                    Text(subModuleTitle(id, section), style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(subModuleTagline(id, section), sectionCount?.let { "$it questions" }).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = p.text3,
                    )
                }
            }
        }
        if (inUtility) {
            item(key = "tool-math") {
                UtilityCard("গণিত সূত্র", "১২টি বিষয় — সম্পূর্ণ সূত্র সংকলন", nav.onMath, Modifier.fillMaxWidth())
            }
            item(key = "tool-finance") {
                UtilityCard("ফিন্যান্সিয়াল টার্ম", "Important terms at a glance", nav.onFinance, Modifier.fillMaxWidth())
            }
        } else if (inPractice) {
            item(key = "practice-actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val saved = practiceFlags.values.count { it.important }
                    val weak = practiceFlags.values.count { it.important && it.weak }
                    Action(Icons.Filled.Bookmark, "Important", saved, p.imp, Modifier.weight(1f)) { nav.onPracticeImportant() }
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium).background(p.warn.copy(alpha = .12f))
                            .border(1.dp, p.warn.copy(alpha = .35f), MaterialTheme.shapes.medium).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(16.dp), tint = p.warn)
                            Text("Weak", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("$weak", style = MaterialTheme.typography.titleLarge, color = p.warn)
                    }
                }
            }
            items(app.practice, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).clickable { nav.onPractice(c.id) }.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${c.name} Practice", style = MaterialTheme.typography.titleMedium)
                        Text("${c.topics.size} topics · ${c.topics.sumOf { t -> t.practice.size }} drills", style = MaterialTheme.typography.labelMedium, color = p.text3)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.text3)
                }
            }
        } else if (inEquation) {
            items(equationTopics, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).clickable { nav.onEquation(t.id) }.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(TopicCatalog.ictTopicNames[t.id] ?: t.id, style = MaterialTheme.typography.titleMedium)
                        Text("${t.groups.size} groups · ${t.equations} equations", style = MaterialTheme.typography.labelMedium, color = p.text3)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.text3)
                }
            }
        } else {
            // Quiz sections (including Vocabulary): quick actions, search, then the topic grid.
            if (quizzableGroup) item(key = "actions") {
                HandMirror {  // like the web's .study-card-actions: the order flips with the hand
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Exams draw multiple-choice questions only; Written/Extra/Viva have none.
                        if (!LongForm.isLongForm(group!!.key)) Action(Icons.Filled.Timer, "Exam", null, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { nav.onExam(group.key) }
                        Action(Icons.Filled.Star, "Nailed", counts.getValue(PoolSet.NAILED), p.ok, Modifier.weight(1f)) { nav.onSaved(PoolSet.NAILED, group.key) }
                        Action(Icons.Filled.Bookmark, "Important", counts.getValue(PoolSet.IMPORTANT), p.imp, Modifier.weight(1f)) { nav.onSaved(PoolSet.IMPORTANT, group.key) }
                    }
                }
            }
            item(key = "search") {                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search ${group?.title ?: ""}") },
                )
            }
            if (tokens.isNotEmpty()) {
                item(key = "hits") { Text("${hits.size} results", style = MaterialTheme.typography.labelMedium, color = p.text3) }
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
                item(key = "pager") { Pager(page.coerceAtMost(pages - 1), pages) { page = it; scope.launch { list.scrollToItem(2) } } }
            } else {
                // LiveMCQ Admin, for the owner only: import / classify / manage the section's questions.
                if (id == ModuleId.GENERAL && section == "livemcq" && owner) {
                    item(key = "admin") {
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.primaryContainer)
                                .clickable { nav.onAdmin() }.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("LiveMCQ Admin", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = p.onPrimaryContainer)
                            Text("import · classify · manage", style = MaterialTheme.typography.labelSmall, color = p.onPrimaryContainer)
                        }
                    }
                }
                items(group?.topics.orEmpty(), key = { it.key }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface)
                            .clickable { nav.onTopic(t.group, t.slug) }.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("${t.count}", style = MaterialTheme.typography.labelLarge, color = p.text3)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.text3)
                    }
                }
            }
        }
    }
}

private fun sectionIcon(id: ModuleId, key: String): ImageVector = when (key) {
    "bangla", "english" -> Icons.Filled.Translate
    "sahitya" -> Icons.AutoMirrored.Filled.MenuBook
    "gk" -> Icons.Filled.Public
    "livemcq", "mcq" -> Icons.Filled.Psychology
    "extra" -> Icons.Filled.AutoAwesome
    "viva" -> Icons.Filled.Mic
    "practice" -> Icons.Filled.Code
    "vocab" -> Icons.Filled.Spellcheck
    UTILITY -> Icons.Filled.Build
    EQUATION -> Icons.Filled.Functions
    PRACTICE -> Icons.Filled.Code
    else -> Icons.Filled.EditNote // written
}

private fun subModuleTitle(id: ModuleId, key: String): String = when (key) {
    "bangla" -> "বাংলা ব্যাকরণ"; "english" -> "English Grammar"; "sahitya" -> "বাংলা সাহিত্য"
    "gk" -> "সাধারণ জ্ঞান"; "livemcq" -> "LiveMCQ"; WRITTEN -> "Written"; "vocab" -> "Vocabulary"; UTILITY -> "Utility"
    EQUATION -> "Equation"; PRACTICE -> "Practice"
    else -> TopicCatalog.groupTitle(key)
}

private fun subModuleTagline(id: ModuleId, key: String): String? = when (key) {
    "bangla" -> "ধ্বনি, শব্দ, বাক্য ও অলংকার"; "english" -> "Grammar, usage & idioms"; "sahitya" -> "কবি, গ্রন্থ ও সাহিত্যের ইতিহাস"
    "gk" -> "Bangladesh, world & current affairs"; "livemcq" -> "Live-exam favourites by topic"; WRITTEN -> "Notes & written answers"
    "vocab" -> "Words, meanings & usage"; UTILITY -> "Formulas & financial terms"
    "mcq" -> "Objective questions by topic"; "written" -> "Long answers & explanations"; "extra" -> "Extra questions & notes"
    "viva" -> "Oral questions & answers"; EQUATION -> "Cover & recall formulas"; PRACTICE -> "Linux & SQL command drills"
    else -> null
}

/** Magpie's module grid, one level down: every sub-module of General / ICT as a card (icon coin, name, blurb). */
@Composable
private fun SubModuleLauncher(id: ModuleId, content: ModuleContent, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    val groupKeys = content.groups.map { it.key }
    val keys: List<String> = if (id == ModuleId.GENERAL) {
        listOf("bangla", "english", "sahitya", "gk", "livemcq").filter { groupKeys.contains(it) } +
            listOf(WRITTEN) + (if (groupKeys.contains("vocab")) listOf("vocab") else emptyList()) + listOf(UTILITY)
    } else {
        listOf("mcq", "written", "extra", "viva").filter { groupKeys.contains(it) } + listOf(EQUATION, PRACTICE)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            if (id == ModuleId.GENERAL) "বাংলা, English ও সাধারণ জ্ঞান Practice" else "Master Information & Communication Technology",
            style = MaterialTheme.typography.bodyMedium, color = p.text3,
        )
        keys.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { key ->
                    val count = content.groups.firstOrNull { it.key == key }?.count
                    Surface(
                        onClick = { onOpen(key) }, modifier = Modifier.weight(1f).height(168.dp),
                        shape = RoundedCornerShape(28.dp), color = p.surface, border = BorderStroke(1.dp, p.outline),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Icon(sectionIcon(id, key), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(subModuleTitle(id, key), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(subModuleTagline(id, key), count?.let { "$it questions" }).joinToString("\n"),
                                style = MaterialTheme.typography.labelMedium, color = p.text3, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, count: Int?, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(LocalPalette.current.surface).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
        }
        Text(count?.toString() ?: "Start", style = MaterialTheme.typography.titleLarge, color = if (count == null) color else Color.Unspecified)
    }
}

@Composable
private fun UtilityCard(title: String, sub: String, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.clip(MaterialTheme.shapes.medium).background(p.primaryContainer).clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.onPrimaryContainer)
        Text(sub, style = MaterialTheme.typography.labelMedium, color = p.onPrimaryContainer)
    }
}
