package com.syed.anvil.ui.quiz

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
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.Item
import com.syed.anvil.content.correctAnswer
import com.syed.anvil.content.explanation
import com.syed.anvil.content.optionList
import com.syed.anvil.progress.Flag
import com.syed.anvil.progress.ProgressRepository
import com.syed.anvil.ui.theme.LocalPalette

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
            if (expl != null) {
                if (explOpen) ExplanationBox(module, expl, selected == correct, uid = item.uid)
                else TextButton(onClick = { explOpen = true }) { Text("ব্যাখ্যা দেখাও") }
            }
            TextButton(onClick = { selected = null; explOpen = false }) { Text("লুকাও") }
        }
        item.uid?.let { uid ->
            flag.note?.let { n ->
                Text(n, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.elevated).padding(10.dp), style = MaterialTheme.typography.bodyMedium)
            }
            FlagBar(flag, uid, progress, itemId = item.id)
        }
    }
}
