package com.syed.slate.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.Grade
import com.syed.slate.content.Item
import com.syed.slate.progress.Flag
import com.syed.slate.progress.FlagRules
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.ui.component.ConfirmTrashDialog
import com.syed.slate.ui.component.HandMirror
import com.syed.slate.ui.component.LocalTrash
import com.syed.slate.ui.highlight.HText
import com.syed.slate.ui.highlight.HtmlHText
import com.syed.slate.ui.rich.PlainQuestionText
import com.syed.slate.ui.rich.RichText
import com.syed.slate.ui.theme.LocalPalette

/** General content is HTML; ICT content is plain text (newlines matter, and MCQ prompts may carry code). */
@Composable
fun QuestionBody(module: ModuleId, item: Item, modifier: Modifier = Modifier) {
    if (module == ModuleId.ICT) PlainQuestionText(item.question, modifier, uid = item.uid) else HtmlHText(item.uid, "q", item.question, modifier)
}

@Composable
fun ContentText(module: ModuleId, text: String, modifier: Modifier = Modifier) {
    if (module == ModuleId.ICT) Text(text, modifier, style = MaterialTheme.typography.bodyMedium)
    else RichText(text, modifier, style = MaterialTheme.typography.bodyMedium)
}

enum class OptState { IDLE, CORRECT, WRONG, DIM }

@Composable
fun OptionRow(module: ModuleId, letter: String, text: String, state: OptState, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val (bg, border) = when (state) {
        OptState.CORRECT -> p.ok.copy(alpha = .16f) to p.ok
        OptState.WRONG -> p.bad.copy(alpha = .16f) to p.bad
        else -> p.surface to p.outline
    }
    Row(
        Modifier.fillMaxWidth().alpha(if (state == OptState.DIM) .55f else 1f)
            .clip(MaterialTheme.shapes.medium).background(bg).border(BorderStroke(1.5.dp, border), MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(
                when (state) { OptState.CORRECT -> p.ok; OptState.WRONG -> p.bad; else -> p.elevated },
            ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                OptState.CORRECT -> Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = p.surface)
                OptState.WRONG -> Icon(Icons.Filled.Close, null, Modifier.size(18.dp), tint = p.surface)
                else -> Text(letter.uppercase(), style = MaterialTheme.typography.labelLarge, color = p.text2)
            }
        }
        Box(Modifier.weight(1f)) { ContentText(module, text) }
    }
}

@Composable
fun ExplanationBox(module: ModuleId, html: String, correct: Boolean?, modifier: Modifier = Modifier, uid: String? = null) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.elevated).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Lightbulb, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text("ব্যাখ্যা", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (correct != null) {
                Box(Modifier.weight(1f))
                Text(
                    if (correct) "✓ সঠিক" else "✗ ভুল", style = MaterialTheme.typography.labelLarge,
                    color = if (correct) p.ok else p.bad,
                )
            }
        }
        if (module == ModuleId.ICT) HText(uid, "explanation", html, style = MaterialTheme.typography.bodyMedium)
        else HtmlHText(uid, "explanation", html, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Nailed / Important / Weak / Note for one question; Weak only appears once a question is Important and not Nailed. */
@Composable
fun FlagBar(flag: Flag, uid: String, progress: ProgressRepository, modifier: Modifier = Modifier, labels: Boolean = false, itemId: String? = null, onDeleted: () -> Unit = {}, onTopicEdit: (() -> Unit)? = null) {
    val p = LocalPalette.current
    var peekOpen by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val trash = LocalTrash.current
    HandMirror {
        // Full width and end-aligned: the cluster sits on the same edge as the hand toggle (right-hand: right,
        // left-hand: the mirror puts End on the left).
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        FlagChip(Icons.Filled.Star, if (flag.nailed) "Nailed!" else "Nail It", flag.nailed, p.ok, labels) { progress.update(uid, FlagRules::toggleNailed) }
        FlagChip(Icons.Filled.Bookmark, if (flag.important) "Saved!" else "Important", flag.important, p.imp, labels) { progress.update(uid, FlagRules::toggleImportant) }
        if (flag.important && !flag.nailed) {
            FlagChip(Icons.Filled.LocalFireDepartment, if (flag.weak) "Weak!" else "Weak", flag.weak, p.warn, labels) { progress.update(uid, FlagRules::toggleWeak) }
        }
        // Hidden by default: a note is read through the chip (peek sheet), or written straight away when there is none yet.
        FlagChip(Icons.Filled.EditNote, "Note", flag.note != null, MaterialTheme.colorScheme.primary, labels) { if (flag.note != null) peekOpen = true else editorOpen = true }
        // The web's owner-only QuestionEditButton (LiveMCQ): move the question to another topic / sub-topic.
        if (onTopicEdit != null) FlagChip(Icons.AutoMirrored.Filled.Label, "Topic", false, MaterialTheme.colorScheme.primary, labels) { onTopicEdit() }
        if (trash != null && itemId != null) FlagChip(Icons.Filled.Delete, "Delete", false, p.bad, labels) { confirmTrash = true }
    } }
    if (confirmTrash && trash != null && itemId != null) ConfirmTrashDialog(onConfirm = { confirmTrash = false; trash.trash(itemId); onDeleted() }, onDismiss = { confirmTrash = false })
    if (peekOpen && flag.note != null) {
        NotePeekSheet(
            note = flag.note,
            onEdit = { peekOpen = false; editorOpen = true },
            onRemove = { peekOpen = false; progress.update(uid) { f -> FlagRules.setNote(f, null) } },
            onDismiss = { peekOpen = false },
        )
    }
    if (editorOpen) {
        NoteEditorSheet(
            initial = flag.note.orEmpty(),
            onSave = { progress.update(uid) { f -> FlagRules.setNote(f, it) }; editorOpen = false },
            onRemove = { progress.update(uid) { f -> FlagRules.setNote(f, null) }; editorOpen = false },
            onDismiss = { editorOpen = false },
        )
    }
}

@Composable
private fun FlagChip(icon: ImageVector, label: String, on: Boolean, color: Color, showLabel: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(MaterialTheme.shapes.small).background(if (on) color.copy(alpha = .16f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (on) color else p.outline), MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = if (on) color else p.text3)
        if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) color else p.text2)
    }
}

@Composable
fun ScoreRingScreen(score: Int, total: Int, title: String, label: String?, onRetry: () -> Unit, onHome: () -> Unit) {
    val p = LocalPalette.current
    val pct = if (total > 0) Math.round(score * 100f / total) else 0
    val grade = Grade.of(pct)
    val color = when (grade) { Grade.EXCELLENT -> p.ok; Grade.GOOD -> p.info; Grade.OK -> p.warn; Grade.LOW -> p.bad }
    val msg = when (grade) { Grade.EXCELLENT -> "Excellent!"; Grade.GOOD -> "Good job!"; Grade.OK -> "Keep practicing."; Grade.LOW -> "Don't give up!" }
    Column(
        Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.EmojiEvents, null, Modifier.size(48.dp), tint = color)
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.text3, textAlign = TextAlign.Center) }
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                val stroke = 12.dp.toPx(); val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(p.outline, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                drawArc(color, -90f, 360f * pct / 100f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$score", style = MaterialTheme.typography.headlineLarge, color = color, fontWeight = FontWeight.Bold)
                    Text("/$total", style = MaterialTheme.typography.titleMedium, color = p.text3)
                }
                Text("$pct%", style = MaterialTheme.typography.labelLarge, color = p.text3)
            }
        }
        Text(msg, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry) { Text("Try Again") }
            OutlinedButton(onClick = onHome) { Icon(Icons.Filled.Home, null, Modifier.size(18.dp)); Text("  Home") }
        }
    }
}
