package com.syed.anvil.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syed.anvil.content.PoolSet
import com.syed.anvil.content.QuizPool
import com.syed.anvil.practice.Drill
import com.syed.anvil.practice.Practice
import com.syed.anvil.practice.SampleTables
import com.syed.anvil.progress.Flag
import com.syed.anvil.progress.FlagRules
import com.syed.anvil.progress.ProgressRepository
import com.syed.anvil.ui.reader.DataTable
import com.syed.anvil.ui.theme.LocalPalette
import com.syed.anvil.ui.theme.Mono

private val monoStyle @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp)

/** Important and Weak for a practice item (there is no Nailed here). Weak implies Important, as everywhere. */
@Composable
fun ImpWeakButtons(flag: Flag, uid: String, progress: ProgressRepository) {
    val p = LocalPalette.current
    Row {
        IconButton(onClick = { progress.update(uid, FlagRules::toggleImportant) }) {
            Icon(if (flag.important) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Important", tint = if (flag.important) p.imp else p.text3)
        }
        IconButton(onClick = { progress.update(uid, FlagRules::toggleWeak) }) {
            Icon(Icons.Filled.LocalFireDepartment, "Weak", tint = if (flag.weak) p.warn else p.text3.copy(alpha = .5f))
        }
    }
}

enum class DrillFilter(val label: String) { ALL("সব"), IMPORTANT("Important"), WEAK("Weak") }

@Composable
fun FilterBar(filter: DrillFilter, onFilter: (DrillFilter) -> Unit, all: Int, important: Int, weak: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(filter == DrillFilter.ALL, { onFilter(DrillFilter.ALL) }, { Text("সব ($all)") })
        FilterChip(filter == DrillFilter.IMPORTANT, { onFilter(DrillFilter.IMPORTANT) }, { Text("Important ($important)") })
        FilterChip(filter == DrillFilter.WEAK, { onFilter(DrillFilter.WEAK) }, { Text("Weak ($weak)") })
    }
}

private fun cell(v: Any?) = v?.toString() ?: "NULL"

/** The tables the queries run on: one compact line each by default, rows on demand — kept above the typing box. */
@Composable
fun SchemaBar(data: SampleTables?) {
    if (data.isNullOrEmpty()) return
    val p = LocalPalette.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.elevated)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.TableChart, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Tables", style = MaterialTheme.typography.labelLarge)
            Text(if (expanded) "rows সহ" else "ট্যাপ করলে rows দেখাবে", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = p.text3)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (!expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                data.forEach { (name, t) ->
                    Text("$name (${t.columns.joinToString(", ")})", Modifier.horizontalScroll(rememberScrollState()), style = monoStyle.copy(fontSize = 12.sp), softWrap = false)
                }
            }
        } else SampleTableBlocks(data, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
}

@Composable
fun SampleTableBlocks(data: SampleTables, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        data.forEach { (name, t) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = monoStyle.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.primary)
                DataTable(t.columns, t.rows.map { r -> r.map(::cell) })
            }
        }
    }
}

/**
 * The typing drill. A multi-line field (Enter is a new line, so multi-line SQL can be typed) and a Check button.
 * Solved counts live only for the session, as on the web.
 */
@Composable
fun CommandPractice(drills: List<Drill>, flags: Map<String, Flag>, progress: ProgressRepository, showFilter: Boolean = true, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var filter by rememberSaveable { mutableStateOf(DrillFilter.ALL) }
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var solved by rememberSaveable { mutableStateOf(listOf<String>()) }

    fun flagOf(d: Drill) = flags[d.id] ?: Flag()
    val importantCount = drills.count { QuizPool.matches(PoolSet.IMPORTANT, flags[it.id]) }
    val weakCount = drills.count { QuizPool.matches(PoolSet.WEAK, flags[it.id]) }
    val pool = drills.filter {
        when (filter) {
            DrillFilter.ALL -> true
            DrillFilter.IMPORTANT -> QuizPool.matches(PoolSet.IMPORTANT, flags[it.id])
            DrillFilter.WEAK -> QuizPool.matches(PoolSet.WEAK, flags[it.id])
        }
    }
    val total = pool.size
    val view = if (total == 0) 0 else idx.coerceIn(0, total - 1)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showFilter) FilterBar(filter, { filter = it; idx = 0 }, drills.size, importantCount, weakCount)
        if (total == 0) {
            Text(
                when (filter) {
                    DrillFilter.IMPORTANT -> "কোনো important practice নেই — 🔖 চিহ্নে ট্যাপ করে যোগ করো।"
                    DrillFilter.WEAK -> "কোনো Weak practice নেই।"
                    DrillFilter.ALL -> "এই topic-এ এখনো practice নেই।"
                },
                style = MaterialTheme.typography.bodyMedium, color = p.text3,
            )
            return@Column
        }
        val drill = pool[view]
        // A fresh state for each drill, so a half-typed answer never follows you to the next one.
        key(drill.id) {
            var input by rememberSaveable { mutableStateOf("") }
            var status by rememberSaveable { mutableStateOf("idle") } // idle | correct | wrong
            var revealed by rememberSaveable { mutableStateOf(false) }
            val go = { to: Int -> idx = ((to % total) + total) % total }

            Row {
                Text("প্রশ্ন ${view + 1} / $total", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("${solved.size} solved", style = MaterialTheme.typography.labelLarge, color = p.ok)
            }
            drill.tag?.let {
                Text(it, Modifier.clip(MaterialTheme.shapes.small).background(p.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = p.onPrimaryContainer)
            }
            SchemaBar(drill.sample)
            Row(verticalAlignment = Alignment.Top) {
                Text(drill.problem.prompt, Modifier.weight(1f).padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
                ImpWeakButtons(flagOf(drill), drill.id, progress)
            }

            val border = when (status) { "correct" -> p.ok; "wrong" -> p.bad; else -> p.outline }
            OutlinedTextField(
                input, { input = it; if (status == "wrong") status = "idle" }, Modifier.fillMaxWidth(),
                readOnly = status == "correct", textStyle = monoStyle,
                prefix = { Text("$ ", style = monoStyle, color = MaterialTheme.colorScheme.primary) },
                placeholder = { Text("command লিখে Check চাপো…", style = MaterialTheme.typography.bodyMedium) },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                minLines = 1, maxLines = 8, isError = status == "wrong",
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = border, unfocusedBorderColor = border),
            )

            if (status == "correct") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = p.ok)
                Column { Text("সঠিক!", color = p.ok, style = MaterialTheme.typography.labelLarge); drill.problem.explain?.let { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            }
            if (status == "wrong") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Error, null, tint = p.bad)
                Text("ঠিক হয়নি — আবার চেষ্টা করো।", color = p.bad, style = MaterialTheme.typography.labelLarge)
            }
            if (revealed) Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.elevated).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Lightbulb, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("উত্তর:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                drill.problem.answers.ifEmpty { listOf(drill.problem.accept.first()) }.forEach {
                    Text(it, Modifier.horizontalScroll(rememberScrollState()), style = monoStyle, softWrap = false)
                }
                drill.problem.explain?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status == "correct") Button(onClick = { go(view + 1) }) { Text("পরের প্রশ্ন") }
                else Button(onClick = {
                    if (input.isBlank()) return@Button
                    if (Practice.checkAnswer(input, drill.problem.accept, drill.caseInsensitive)) {
                        status = "correct"; if (drill.id !in solved) solved = solved + drill.id
                    } else status = "wrong"
                }) { Text("Check") }
                TextButton(onClick = { revealed = !revealed }) { Text(if (revealed) "উত্তর লুকাও" else "উত্তর দেখাও") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { go(view - 1) }, enabled = total > 1) { Text("‹ আগের") }
                OutlinedButton(onClick = { go(view + 1) }, enabled = total > 1) { Text("Skip ›") }
            }
        }
    }
}
