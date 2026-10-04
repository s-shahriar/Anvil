package com.syed.slate.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.syed.slate.trash.TrashRepository

/** The recycle bin of the module being shown; null outside a module, which hides every delete button. */
val LocalTrash = staticCompositionLocalOf<TrashRepository?> { null }

@Composable
fun ConfirmTrashDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to recycle bin?") },
        text = { Text("It disappears from the lists. You can restore it from the module's recycle bin.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
