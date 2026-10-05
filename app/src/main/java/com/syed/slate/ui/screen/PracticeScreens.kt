package com.syed.slate.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.syed.slate.ui.practice.accentBar
import com.syed.slate.ui.practice.practiceCard
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateTopBar
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
import com.syed.slate.SlateApp
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.PoolSet
import com.syed.slate.content.QuizPool
import com.syed.slate.practice.Category
import com.syed.slate.practice.Practice
import com.syed.slate.practice.Topic
import com.syed.slate.progress.Flag
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.practice.CommandPractice
import com.syed.slate.ui.practice.DrillFilter
import com.syed.slate.ui.practice.FilterBar
import com.syed.slate.ui.practice.ImpWeakButtons
import com.syed.slate.ui.practice.SampleTableBlocks
import com.syed.slate.ui.reader.DataTable
import com.syed.slate.ui.theme.LocalPalette
import com.syed.slate.ui.theme.Mono

private val tabs = listOf("Info", "Commands", "Practice")

/** One category (Linux or SQL): pick a topic, then read Info, browse Commands, or drill. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(vm: SlateViewModel, categoryId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SlateApp
    val blobVer by app.module(ModuleId.ICT).blobs.version.collectAsState()
    val cat = remember(blobVer) { app.practice }.firstOrNull { it.id == categoryId } ?: return
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
            SlateTopBar(
                title = { BarTitle("${cat.name} Practice", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TopicDropdown(cat, topic, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { topicId = it; tab = 0 }
            PracticeTabs(tab) { tab = it }
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

/** The web's `.practice-tab` pills: the active one is tinted with the accent and ringed in it. */
@Composable
private fun PracticeTabs(current: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEachIndexed { i, t ->
            val on = current == i
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) accent.copy(alpha = .14f) else p.surface)
                    .border(BorderStroke(if (on) 1.5.dp else 1.dp, if (on) accent else p.outline), RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(t, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (on) accent else p.text2) }
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
            readOnly = true, singleLine = true, colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedContainerColor = LocalPalette.current.surface, unfocusedContainerColor = LocalPalette.current.surface, unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .5f), focusedBorderColor = MaterialTheme.colorScheme.primary), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
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
    Column(Modifier.practiceCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(topic.name, Modifier.accentBar(MaterialTheme.colorScheme.primary).padding(start = 10.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        info.summary.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text2) }
        info.table?.let { DataTable(it.headers, it.rows) }
    }
}

@Composable
private fun SampleSection(data: com.syed.slate.practice.SampleTables) {
    var open by rememberSaveable { mutableStateOf(true) }
    val p = LocalPalette.current
    Column(Modifier.practiceCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = { open = !open }) { Text(if (open) "▾ Sample Data" else "▸ Sample Data", style = MaterialTheme.typography.titleSmall) }
            Text("— query গুলো এই rows-এর উপর চলে", style = MaterialTheme.typography.labelMedium, color = p.text3)
        }
        if (open) SampleTableBlocks(data)
    }
}

@Composable
private fun CommandsPanel(cat: Category, topic: Topic, flags: Map<String, Flag>, progress: com.syed.slate.progress.ProgressRepository) {
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
        Column(Modifier.practiceCard().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.prompt.isNotEmpty()) Text(c.prompt, Modifier.accentBar(MaterialTheme.colorScheme.primary).padding(start = 10.dp), style = MaterialTheme.typography.titleSmall)
            c.cmds.forEach {
                Text(
                    it, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .08f))
                        .border(BorderStroke(1.dp, p.outline.copy(alpha = .6f)), RoundedCornerShape(8.dp)).horizontalScroll(rememberScrollState()).padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp), color = MaterialTheme.colorScheme.primary, softWrap = false,
                )
            }
            if (c.desc.isNotEmpty()) Text(c.desc, style = MaterialTheme.typography.bodyMedium, color = p.text2)
            ImpWeakButtons(flags[id(c.key)] ?: Flag(), id(c.key), progress)
        }
    }
}

/** Every practice item you marked Important, across Linux and SQL, as one drill. */
@Composable
fun PracticeImportantScreen(vm: SlateViewModel, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as SlateApp
    val m = vm.module(ModuleId.ICT)
    val flags by m.progress.flags.collectAsState()
    val blobVer by app.module(ModuleId.ICT).blobs.version.collectAsState()
    val all = remember(blobVer) { app.practice.flatMap { c -> c.topics.flatMap { t -> Practice.drillsFor(c, t, tag = true) } } }
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
