package com.syed.slate.ui.practice

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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.syed.slate.ui.component.HandMirror
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syed.slate.content.PoolSet
import com.syed.slate.content.QuizPool
import com.syed.slate.practice.Drill
import com.syed.slate.practice.Practice
import com.syed.slate.practice.SampleTables
import com.syed.slate.progress.Flag
import com.syed.slate.progress.FlagRules
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.ui.reader.DataTable
import com.syed.slate.ui.theme.LocalPalette
import com.syed.slate.ui.theme.Mono

private val monoStyle @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontSize = 13.sp, lineHeight = 19.sp)

/** White card on the tinted page, hairline border: the surface every practice block sits on (the web's `.practice-cmd-row`). */
@Composable
fun Modifier.practiceCard(): Modifier {
    val p = LocalPalette.current
    return this.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface)
        .border(BorderStroke(1.dp, p.outline.copy(alpha = .55f)), MaterialTheme.shapes.medium)
}

@Composable
private fun FlagChip(icon: ImageVector, label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(MaterialTheme.shapes.small).background(if (on) color.copy(alpha = .16f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (on) color else p.outline), MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) { Icon(icon, label, Modifier.size(18.dp), tint = if (on) color else p.text3) }
}

/**
 * Important and Weak for a practice item, as the card-footer row every other question card uses (same chips, order,
 * sizes and mirroring as the quiz FlagBar). Weak implies Important and is offered only once it is marked.
 */
@Composable
fun ImpWeakButtons(flag: Flag, uid: String, progress: ProgressRepository, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    HandMirror {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
            FlagChip(Icons.Filled.Bookmark, "Important", flag.important, p.imp) { progress.update(uid, FlagRules::toggleImportant) }
            if (flag.important) FlagChip(Icons.Filled.LocalFireDepartment, "Weak", flag.weak, p.warn) { progress.update(uid, FlagRules::toggleWeak) }
        }
    }
}

/** The 3dp accent rule down the left of a prompt (the web's `.practice-cmd-q`). */
@Composable
fun Modifier.accentBar(color: Color): Modifier = this.drawBehind {
    drawRect(color, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
}

enum class DrillFilter(val label: String) { ALL("সব"), IMPORTANT("Important"), WEAK("Weak") }

@Composable
fun FilterBar(filter: DrillFilter, onFilter: (DrillFilter) -> Unit, all: Int, important: Int, weak: Int) {
    val p = LocalPalette.current
    @Composable
    fun chip(f: DrillFilter, text: String, color: Color, icon: ImageVector?) {
        val on = filter == f
        FilterChip(
            on, { onFilter(f) }, { Text(text, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.SemiBold else null) },
            leadingIcon = icon?.let { { Icon(it, null, Modifier.size(14.dp)) } },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = p.surface, selectedContainerColor = color.copy(alpha = .16f),
                labelColor = p.text2, selectedLabelColor = color, iconColor = p.text3, selectedLeadingIconColor = color,
            ),
            border = FilterChipDefaults.filterChipBorder(true, on, borderColor = p.outline, selectedBorderColor = color),
        )
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chip(DrillFilter.ALL, "সব ($all)", MaterialTheme.colorScheme.primary, null)
        chip(DrillFilter.IMPORTANT, "Important ($important)", p.imp, Icons.Filled.Bookmark)
        chip(DrillFilter.WEAK, "Weak ($weak)", p.warn, Icons.Filled.LocalFireDepartment)
    }
}

private fun cell(v: Any?) = v?.toString() ?: "NULL"

/** The tables the queries run on: one compact line each by default, rows on demand — kept above the typing box. */
@Composable
fun SchemaBar(data: SampleTables?) {
    if (data.isNullOrEmpty()) return
    val p = LocalPalette.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary.copy(alpha = .08f)).border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .3f)), MaterialTheme.shapes.small)) {
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

            Column(Modifier.practiceCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Text("প্রশ্ন ${view + 1} / $total", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("${solved.size} solved", style = MaterialTheme.typography.labelLarge, color = p.ok)
            }
            drill.tag?.let {
                Text(it, Modifier.clip(MaterialTheme.shapes.small).background(p.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = p.onPrimaryContainer)
            }
            SchemaBar(drill.sample)
            Text(
                drill.problem.prompt, Modifier.fillMaxWidth().accentBar(MaterialTheme.colorScheme.primary).padding(start = 10.dp),
                style = MaterialTheme.typography.titleMedium,
            )

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

            if (status == "correct") Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.ok.copy(alpha = .12f)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, null, tint = p.ok)
                Column { Text("সঠিক!", color = p.ok, style = MaterialTheme.typography.labelLarge); drill.problem.explain?.let { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            }
            if (status == "wrong") Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.bad.copy(alpha = .12f)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Error, null, tint = p.bad)
                Text("ঠিক হয়নি — আবার চেষ্টা করো।", color = p.bad, style = MaterialTheme.typography.labelLarge)
            }
            if (revealed) Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.primaryContainer.copy(alpha = .55f)).padding(12.dp),
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
            ImpWeakButtons(flagOf(drill), drill.id, progress)
            }
        }
    }
}
