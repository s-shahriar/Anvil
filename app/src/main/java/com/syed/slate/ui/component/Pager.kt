package com.syed.slate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Previous / next with "page x of y". Renders nothing for a single page. */
@Composable
fun Pager(page: Int, pages: Int, onPage: (Int) -> Unit) {
    if (pages <= 1) return
    HandMirror { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Text("Previous") }
        Text("${page + 1} / $pages", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { onPage(page + 1) }, enabled = page < pages - 1) { Text("Next") }
    } }
}
