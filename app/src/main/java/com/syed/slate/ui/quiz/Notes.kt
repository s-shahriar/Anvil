package com.syed.slate.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syed.slate.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/**
 * The web's NotePeek, as a bottom sheet: notes stay out of the card until the note chip is tapped. The sheet shows the
 * saved note with Edit and a tap-again-to-confirm Remove (a removed note can't be recovered).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotePeekSheet(note: String, onEdit: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { delay(3000); confirm = false } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.EditNote, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Your note", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = { if (confirm) onRemove() else confirm = true }) {
                    Icon(Icons.Filled.DeleteOutline, null, Modifier.size(18.dp), tint = p.bad)
                    Spacer(Modifier.width(4.dp))
                    Text(if (confirm) "Tap again to remove" else "Remove", color = p.bad)
                }
            }
            SelectionContainer { Text(note, style = MaterialTheme.typography.bodyLarge) }
            Spacer(Modifier.size(12.dp))
        }
    }
}

/** The web's NoteEditor: a bottom sheet with a textarea; Save stays disabled until the text changed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(initial: String, onSave: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf(initial) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(confirm) { if (confirm) { delay(3000); confirm = false } }
    val changed = text.trim() != initial.trim()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.EditNote, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Note", style = MaterialTheme.typography.titleSmall)
            }
            OutlinedTextField(
                text, { text = it }, Modifier.fillMaxWidth(), minLines = 4,
                placeholder = { Text("Add a private note for this question — a trick you fell for, a rule to remember…") },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (initial.isNotEmpty()) TextButton(onClick = { if (confirm) onRemove() else confirm = true }) {
                    Text(if (confirm) "Tap again to remove" else "Remove note", color = p.bad)
                }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(text) }, enabled = changed) { Text("Save") }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}
