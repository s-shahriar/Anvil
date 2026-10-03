package com.syed.anvil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syed.anvil.AnvilApp
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.PoolSet
import com.syed.anvil.content.QuizPool
import com.syed.anvil.practice.Category
import com.syed.anvil.practice.Practice
import com.syed.anvil.practice.Topic
import com.syed.anvil.progress.Flag
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.practice.CommandPractice
import com.syed.anvil.ui.practice.DrillFilter
import com.syed.anvil.ui.practice.FilterBar
import com.syed.anvil.ui.practice.ImpWeakButtons
import com.syed.anvil.ui.practice.SampleTableBlocks
import com.syed.anvil.ui.reader.DataTable
import com.syed.anvil.ui.theme.LocalPalette
import com.syed.anvil.ui.theme.Mono

private val tabs = listOf("Info", "Commands", "Practice")

/** One category (Linux or SQL): pick a topic, then read Info, browse Commands, or drill. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(vm: AnvilViewModel, categoryId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as AnvilApp
    val cat = app.practice.firstOrNull { it.id == categoryId } ?: return
    val m = vm.module(ModuleId.ICT)
    val flags by m.progress.flags.collectAsState()
    var topicId by rememberSaveable { mutableStateOf(cat.topics.first().id) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val topic = cat.topics.firstOrNull { it.id == topicId } ?: cat.topics.first()
    val sample = cat.sampleFor(topic)
    val drills = remember(cat, topic) { Practice.drillsFor(cat, topic) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("${cat.name} Practice", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TopicDropdown(cat, topic, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { topicId = it; tab = 0 }
            PrimaryTabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (tab) {
                    0 -> InfoPanel(topic)
                    1 -> {
                        sample?.let { SampleSection(it) }
                        CommandsPanel(cat, topic, flags, m.progress)
                    }
                    else -> CommandPractice(drills, flags, m.progress)
                }
            }
        }
    }
}

/** Topics grouped under "SET A" / "SET B" headers when the category has sets (SQL); one flat list otherwise (Linux). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicDropdown(cat: Category, current: Topic, modifier: Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val groups = cat.topics.groupBy { it.set.orEmpty() }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        OutlinedTextField(
            (current.set?.let { "SET $it · " }.orEmpty()) + current.name, {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true, singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
        )
        ExposedDropdownMenu(open, { open = false }) {
            groups.forEach { (set, topics) ->
                if (set.isNotEmpty()) Text("SET $set", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.text3)
                topics.forEach { t -> DropdownMenuItem({ Text(t.name) }, { onSelect(t.id); open = false }) }
            }
        }
    }
}

@Composable
private fun InfoPanel(topic: Topic) {
    val info = topic.info ?: return
    Text(topic.name, style = MaterialTheme.typography.titleLarge)
    info.summary.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    info.table?.let { DataTable(it.headers, it.rows) }
}

@Composable
private fun SampleSection(data: com.syed.anvil.practice.SampleTables) {
    var open by rememberSaveable { mutableStateOf(true) }
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.surface).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = { open = !open }) { Text(if (open) "▾ Sample Data" else "▸ Sample Data", style = MaterialTheme.typography.titleSmall) }
            Text("— query গুলো এই rows-এর উপর চলে", style = MaterialTheme.typography.labelMedium, color = p.text3)
        }
        if (open) SampleTableBlocks(data)
    }
}

@Composable
private fun CommandsPanel(cat: Category, topic: Topic, flags: Map<String, Flag>, progress: com.syed.anvil.progress.ProgressRepository) {
    val p = LocalPalette.current
    val list = remember(topic) { Practice.buildCommandList(topic.commands, topic.practice) }
    var filter by rememberSaveable { mutableStateOf(DrillFilter.ALL) }
    if (list.isEmpty()) return
    fun id(key: String) = Practice.cmdId(cat.id, topic.id, key)
    val important = list.count { QuizPool.matches(PoolSet.IMPORTANT, flags[id(it.key)]) }
    val weak = list.count { QuizPool.matches(PoolSet.WEAK, flags[id(it.key)]) }
    val visible = list.filter {
        when (filter) {
            DrillFilter.ALL -> true
            DrillFilter.IMPORTANT -> QuizPool.matches(PoolSet.IMPORTANT, flags[id(it.key)])
            DrillFilter.WEAK -> QuizPool.matches(PoolSet.WEAK, flags[id(it.key)])
        }
    }
    FilterBar(filter, { filter = it }, list.size, important, weak)
    if (visible.isEmpty()) Text(if (filter == DrillFilter.WEAK) "কোনো Weak command নেই।" else "কোনো important command নেই — 🔖 চিহ্নে ট্যাপ করে যোগ করো।", style = MaterialTheme.typography.bodyMedium, color = p.text3)
    visible.forEach { c ->
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.prompt.isNotEmpty()) Text(c.prompt, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.cmds.forEach {
                        Text(
                            it, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.elevated).horizontalScroll(rememberScrollState()).padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp), softWrap = false,
                        )
                    }
                }
                ImpWeakButtons(flags[id(c.key)] ?: Flag(), id(c.key), progress)
            }
            if (c.desc.isNotEmpty()) Text(c.desc, style = MaterialTheme.typography.bodyMedium, color = p.text2)
        }
    }
}

/** Every practice item you marked Important, across Linux and SQL, as one drill. */
@Composable
fun PracticeImportantScreen(vm: AnvilViewModel, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as AnvilApp
    val m = vm.module(ModuleId.ICT)
    val flags by m.progress.flags.collectAsState()
    val all = remember { app.practice.flatMap { c -> c.topics.flatMap { t -> Practice.drillsFor(c, t, tag = true) } } }
    // Items un-marked mid-drill drop out of the list, as on the web.
    val drills = all.filter { QuizPool.matches(PoolSet.IMPORTANT, flags[it.id]) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Important · Practice", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            CommandPractice(drills, flags, m.progress, showFilter = false)
        }
    }
}
