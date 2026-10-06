package com.syed.slate.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syed.slate.content.Item
import com.syed.slate.content.answer
import com.syed.slate.content.headerCode
import com.syed.slate.content.headerCodeLang
import com.syed.slate.content.verdict
import com.syed.slate.progress.Flag
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.ui.quiz.FlagBar
import com.syed.slate.ui.theme.LocalPalette

/**
 * One Written / Extra / Viva card. Collapsed it shows the question (plus a verdict chip and the listing it is about);
 * opened it shows the whole answer. The flag buttons stay within reach either way.
 */
@Composable
fun LongCard(
    item: Item,
    number: Int?,
    open: Boolean,
    flag: Flag,
    progress: ProgressRepository,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    topicLabel: String? = null,
    focused: Boolean = false,
) {
    val p = LocalPalette.current
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface)
            .border(if (focused) 2.dp else 0.dp, if (focused) primary else p.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        topicLabel?.let {
            Text(it, Modifier.clip(MaterialTheme.shapes.small).background(p.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = p.onPrimaryContainer)
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            number?.let { Text("Q$it", style = MaterialTheme.typography.labelLarge, color = primary, modifier = Modifier.padding(top = 3.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Collapsed, the question is plain text so a tap anywhere on it opens the card; open, it can be highlighted,
                // and a plain tap on it still folds the card (long-press selects as before).
                QuestionTextBlocks(item.question, uid = item.uid, editable = open, onTap = onToggle)
                item.verdict?.let {
                    Text(it, Modifier.clip(MaterialTheme.shapes.small).background(p.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = p.onPrimaryContainer)
                }
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand", tint = p.text3)
        }
        // The listing the card is about, visible while collapsed.
        item.headerCode?.let { CodeBlock(it, item.headerCodeLang) }
        if (open) item.answer?.let { WrittenBody(it, uid = item.uid) }
        item.uid?.let { FlagBar(flag, it, progress, itemId = item.id) }
    }
}
