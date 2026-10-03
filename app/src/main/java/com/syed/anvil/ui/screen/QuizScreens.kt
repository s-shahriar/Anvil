package com.syed.anvil.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.PoolSet
import com.syed.anvil.content.QuizPool
import com.syed.anvil.content.TopicCatalog
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.ExamSpec
import com.syed.anvil.ui.quiz.QuizSession
import com.syed.anvil.ui.theme.LocalPalette

private val poolLabel = mapOf(PoolSet.IMPORTANT to "Important", PoolSet.WEAK to "Weak", PoolSet.NAILED to "Nailed")

@Composable
fun QuizScreen(vm: AnvilViewModel, id: ModuleId, group: String, topic: String, set: PoolSet, onBack: () -> Unit, onHome: () -> Unit) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val content = (state as? ContentState.Ready)?.content ?: return
    val name = content.groups.firstOrNull { it.key == group }?.topics?.firstOrNull { it.slug == topic }?.name ?: TopicCatalog.prettify(topic)
    // Built once per quiz: flags changed during the quiz must not reshuffle or shorten it.
    val pool = remember(content, group, topic, set) { QuizPool.build(content.items(group, topic), m.progress.flags.value, set) }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        QuizSession(
            services = m, questions = pool, topicName = { null }, pill = name,
            resultTitle = poolLabel[set]?.let { "$it Quiz Complete!" } ?: "Quiz Complete!",
            emptyMessage = "$name-এ এখনো কোনো ${poolLabel[set] ?: ""} প্রশ্ন নেই।",
            onBack = onBack, onHome = onHome,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamConfigScreen(vm: AnvilViewModel, id: ModuleId, initialGroup: String?, onBack: () -> Unit, onStart: () -> Unit) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val flags by m.progress.flags.collectAsState()
    val p = LocalPalette.current
    val content = (state as? ContentState.Ready)?.content
    var groupKey by rememberSaveable { mutableStateOf(initialGroup ?: content?.groups?.firstOrNull()?.key ?: "") }
    var choice by rememberSaveable { mutableStateOf("all") } // all | important | weak | topic:<slug>
    var count by rememberSaveable { mutableIntStateOf(10) }
    val group = content?.groups?.firstOrNull { it.key == groupKey } ?: content?.groups?.firstOrNull()

    // Important / Weak are scoped to the chosen group, like the web app.
    val groupItems = remember(content, group) { group?.topics.orEmpty().flatMap { content!!.items(group!!.key, it.slug) } }
    val set = when {
        choice == "important" -> PoolSet.IMPORTANT
        choice == "weak" -> PoolSet.WEAK
        else -> PoolSet.ALL
    }
    val items = remember(content, group, choice, flags) {
        if (choice.startsWith("topic:")) content!!.items(group!!.key, choice.removePrefix("topic:")) else groupItems
    }
    val pool = remember(items, flags, set) { QuizPool.build(items, flags, set) }
    val counts = remember(groupItems, flags) { QuizPool.counts(groupItems, flags) }
    val maxCount = pool.size
    val safeCount = if (maxCount == 0) 0 else count.coerceIn(1, maxCount)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Exam Mode", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (content == null || group == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Dropdown("Subject group", content.groups.map { it.key to "${it.title} · ${it.count}" }, group.key) { groupKey = it; choice = "all" }
            Dropdown(
                "Topic",
                buildList {
                    add("all" to "All topics (${counts.getValue(PoolSet.ALL)} Q)")
                    add("important" to "Important Questions (${counts.getValue(PoolSet.IMPORTANT)} Q)")
                    add("weak" to "Weak Questions (${counts.getValue(PoolSet.WEAK)} Q)")
                    group.topics.forEach { add("topic:${it.slug}" to "${it.name} (${it.count})") }
                },
                choice,
            ) { choice = it }
            Text("Number of questions", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { count = (safeCount - 5).coerceAtLeast(1) }, enabled = safeCount > 1) { Text("−5") }
                IconButton(onClick = { count = safeCount - 1 }, enabled = safeCount > 1) { Icon(Icons.Filled.Remove, "Fewer") }
                Text("$safeCount", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { count = safeCount + 1 }, enabled = safeCount < maxCount) { Icon(Icons.Filled.Add, "More") }
                OutlinedButton(onClick = { count = (safeCount + 5).coerceAtMost(maxCount) }, enabled = safeCount < maxCount) { Text("+5") }
            }
            Text("$maxCount available", style = MaterialTheme.typography.labelMedium, color = p.text3)
            Button(
                onClick = {
                    val label = when {
                        choice == "important" -> "Important Questions"
                        choice == "weak" -> "Weak Questions"
                        choice.startsWith("topic:") -> group.topics.firstOrNull { it.slug == choice.removePrefix("topic:") }?.name
                        else -> group.title
                    }
                    vm.exam = ExamSpec(id, pool.take(safeCount), label)
                    onStart()
                },
                Modifier.fillMaxWidth(), enabled = maxCount > 0,
            ) { Text("Start Exam — $safeCount Questions") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            options.firstOrNull { it.first == selected }?.second.orEmpty(), {}, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
        )
        ExposedDropdownMenu(open, { open = false }) {
            options.forEach { (k, v) -> DropdownMenuItem({ Text(v) }, { onSelect(k); open = false }) }
        }
    }
}

@Composable
fun ExamRunScreen(vm: AnvilViewModel, onBack: () -> Unit, onHome: () -> Unit) {
    val spec = vm.exam
    if (spec == null) { onBack(); return }
    val m = vm.module(spec.module)
    val state by m.content.state.collectAsState()
    val content = (state as? ContentState.Ready)?.content
    val names = remember(content) { content?.groups?.flatMap { it.topics }?.associate { it.key to it.name }.orEmpty() }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        QuizSession(
            services = m, questions = spec.items, topicName = { names["${it.group}/${it.topic}"] },
            pill = "Exam · ${spec.label ?: ""}", resultTitle = "Exam Complete!", resultLabel = spec.label,
            showTopicTag = true, stoppable = true, onBack = onBack, onHome = onHome,
        )
    }
}
