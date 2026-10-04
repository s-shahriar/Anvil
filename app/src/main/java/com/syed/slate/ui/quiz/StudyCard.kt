package com.syed.slate.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.Item
import com.syed.slate.content.correctAnswer
import com.syed.slate.content.explanation
import com.syed.slate.content.optionList
import com.syed.slate.progress.Flag
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.ui.theme.LocalPalette
import com.syed.slate.ui.theme.Mono

/**
 * A question to read through: tap an option to see the answer. A wrong pick opens the explanation; a right one
 * keeps it folded away until asked for.
 */
@Composable
fun StudyCard(
    module: ModuleId,
    item: Item,
    flag: Flag,
    progress: ProgressRepository,
    modifier: Modifier = Modifier,
    topicLabel: String? = null,
    highlighted: Boolean = false,
    number: Int? = null,
    /** Owner-only, LiveMCQ-only: opens the per-card topic / sub-topic fix (the web's QuestionEditButton). */
    onLiveMcqEdit: ((Item) -> Unit)? = null,
) {
    val p = LocalPalette.current
    var selected by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var explOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    val correct = item.correctAnswer
    val revealed = selected != null

    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface)
            .border(BorderStroke(if (highlighted) 2.dp else 0.dp, if (highlighted) MaterialTheme.colorScheme.primary else p.surface), MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        topicLabel?.let {
            Text(it, Modifier.clip(MaterialTheme.shapes.small).background(p.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = p.onPrimaryContainer)
        }
        number?.let { n ->
            Text("Q$n", style = MaterialTheme.typography.labelMedium.copy(fontFamily = Mono), color = MaterialTheme.colorScheme.primary)
        }
        QuestionBody(module, item)
        item.optionList().forEach { (letter, text) ->
            val state = when {
                !revealed -> OptState.IDLE
                letter == correct -> OptState.CORRECT
                letter == selected -> OptState.WRONG
                else -> OptState.DIM
            }
            OptionRow(module, letter, text, state, enabled = !revealed) {
                selected = letter
                explOpen = letter != correct
            }
        }
        if (revealed) {
            val expl = item.explanation
            // One toggle at a time: "show" while the explanation is folded, "hide" once it is open (hide also
            // clears the pick, as the web's Hide does). Cards without an explanation only offer hide.
            if (expl != null && explOpen) ExplanationBox(module, expl, selected == correct, uid = item.uid)
            if (expl != null && !explOpen) TextButton(onClick = { explOpen = true }) { Text("ব্যাখ্যা দেখাও") }
            else TextButton(onClick = { selected = null; explOpen = false }) { Text("লুকাও") }
        }
        item.uid?.let { uid ->
            flag.note?.let { n ->
                Text(n, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.elevated).padding(10.dp), style = MaterialTheme.typography.bodyMedium)
            }
            FlagBar(flag, uid, progress, itemId = item.id)
            // The web's owner-only QuestionEditButton: fix a misfiled LiveMCQ question on the spot.
            if (onLiveMcqEdit != null) TextButton(onClick = { onLiveMcqEdit?.invoke(item) }) { Text("Topic") }
        }
    }
}
