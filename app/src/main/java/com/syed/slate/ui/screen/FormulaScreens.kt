package com.syed.slate.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.syed.slate.ui.component.SlateTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.TopicCatalog
import com.syed.slate.progress.FlagRules
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.theme.LocalPalette
import com.syed.slate.ui.theme.TopicColors
import com.syed.slate.ui.theme.isDark
import com.syed.slate.ui.web.FormulaPage
import com.syed.slate.ui.web.PageKind
import org.json.JSONArray
import org.json.JSONObject

class MathSection(val id: String, val label: String, val icon: String, val color: String)
class EquationGroup(val id: String, val title: String)
class EquationTopic(val id: String, val groups: List<EquationGroup>, val equations: Int)

/** The pre-rendered pages' small indexes, read from assets. */
object FormulaIndex {
    fun mathSections(ctx: android.content.Context): List<MathSection> =
        JSONArray(ctx.assets.open("web/math.sections.json").bufferedReader().use { it.readText() }).let { a ->
            List(a.length()) { a.getJSONObject(it).let { o -> MathSection(o.getString("id"), o.getString("label"), o.optString("icon"), o.optString("color")) } }
        }

    fun equationTopics(ctx: android.content.Context): List<EquationTopic> =
        JSONArray(ctx.assets.open("web/equation.index.json").bufferedReader().use { it.readText() }).let { a ->
            List(a.length()) { i ->
                val o: JSONObject = a.getJSONObject(i)
                val g = o.getJSONArray("groups")
                EquationTopic(o.getString("id"), List(g.length()) { j -> EquationGroup(g.getJSONObject(j).getString("id"), g.getJSONObject(j).getString("title")) }, o.getInt("equations"))
            }
        }
}

@Composable
private fun CoverBanner() {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(p.imp.copy(alpha = .10f)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.VisibilityOff, null, tint = p.imp)
        Text("সূত্র ঢাকা আছে — মনে করার চেষ্টা করে, তারপর ট্যাপ করে মিলিয়ে নিন।", style = MaterialTheme.typography.labelMedium)
    }
}

/** General » Utility » গণিত সূত্র সংকলন: 19 sections of formulas, with cover-and-recall and per-card Important stars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MathFormulasScreen(vm: SlateViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val m = vm.module(ModuleId.GENERAL)
    val flags by m.progress.flags.collectAsState()
    val p = LocalPalette.current
    val sections = remember { FormulaIndex.mathSections(ctx) }
    var cover by remember { mutableStateOf(vm.boolPref("mf-cover")) }
    var importantOnly by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(sections.first().id) }
    var target by remember { mutableStateOf<String?>(null) }
    var nonce by remember { mutableIntStateOf(0) }
    // Math cards are keyed "m<hash>"; question uids start with "q", so the prefix keeps the two apart.
    val important = remember(flags) { flags.filter { it.key.startsWith("m") && it.value.important }.keys }
    val chips = rememberLazyListState()
    LaunchedEffect(active) { sections.indexOfFirst { it.id == active }.takeIf { it >= 0 }?.let { chips.animateScrollToItem(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { Text("গণিত সূত্র সংকলন", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { cover = !cover; vm.setBoolPref("mf-cover", cover) }) {
                        Icon(if (cover) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (cover) "Show formulas" else "Cover formulas")
                    }
                    IconButton(onClick = { importantOnly = !importantOnly }) {
                        Icon(if (importantOnly) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Important only", tint = if (importantOnly) p.imp else LocalContentColorFallback())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (!importantOnly) LazyRow(state = chips, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sections, key = { it.id }) { s ->
                    val color = TopicColors.parse(s.color, p.isDark, p.primary)
                    FilterChip(
                        active == s.id, { target = s.id; nonce++ }, { Text("${s.icon} ${s.label}") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = .18f), selectedLabelColor = color),
                    )
                }
            }
            if (cover) CoverBanner()
            Box(Modifier.fillMaxSize()) {
                FormulaPage(
                    PageKind.MATH, "web/math.body.html", p, p.isDark, cover,
                    importantOnly = importantOnly, important = important, scrollTo = target, scrollNonce = nonce,
                    onToggleImportant = { uid -> m.progress.update(uid, FlagRules::toggleImportant) },
                    onSection = { active = it },
                )
                if (importantOnly && important.isEmpty()) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("এখনো কোনো কার্ড Important হিসেবে সেভ করা হয়নি।", style = MaterialTheme.typography.titleMedium)
                    Text("যেকোনো কার্ডের কোণায় ⭐ বাটনে ক্লিক করে Important করুন।", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = p.text3)
                }
            }
        }
    }
}

@Composable
private fun LocalContentColorFallback(): Color = androidx.compose.material3.LocalContentColor.current

/** ICT » Equation: formula sheets for one topic, each group a diagram plus its equations, with cover-and-recall. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquationScreen(vm: SlateViewModel, topicId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val topic = remember(topicId) { FormulaIndex.equationTopics(ctx).first { it.id == topicId } }
    var cover by remember { mutableStateOf(vm.boolPref("ict-eq-cover")) }
    var active by remember { mutableStateOf("eq-${topic.groups.first().id}") }
    var target by remember { mutableStateOf<String?>(null) }
    var nonce by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { Text("${TopicCatalog.ictTopicNames[topic.id] ?: topic.id} — Equations", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { cover = !cover; vm.setBoolPref("ict-eq-cover", cover) }) {
                        Icon(if (cover) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (cover) "Show equations" else "Cover equations")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (topic.groups.size > 1) LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(topic.groups, key = { it.id }) { g ->
                    FilterChip(active == "eq-${g.id}", { target = "eq-${g.id}"; nonce++ }, { Text(g.title) })
                }
            }
            if (cover) CoverBanner()
            val ctl = com.syed.slate.ui.highlight.LocalHighlights.current
            val saved by (ctl?.repo?.byUid ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, List<com.syed.slate.highlight.Highlight>>()) }).collectAsState()
            val onPage = remember(saved) { saved.filterKeys { it.startsWith("equation:") } }
            val owner = remember { Any() }
            var clearNonce by remember { mutableIntStateOf(0) }
            FormulaPage(
                PageKind.EQUATION, "web/equation_${topic.id}.body.html", p, p.isDark, cover,
                scrollTo = target, scrollNonce = nonce, onSection = { active = it },
                highlights = onPage, clearSelectionNonce = clearNonce,
                onSelection = { uid, anchors ->
                    ctl?.target = if (anchors.isEmpty()) ctl?.target?.takeIf { it.owner !== owner }
                    else com.syed.slate.ui.highlight.HlTarget.Add(owner, uid, anchors, anchors.joinToString(" ") { it.quote }) { clearNonce++ }
                },
                onMark = { _, ids, color -> ctl?.target = com.syed.slate.ui.highlight.HlTarget.Edit(owner, ids, color) { } },
            )
        }
    }
}
