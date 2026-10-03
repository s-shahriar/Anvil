package com.syed.anvil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.Item
import com.syed.anvil.content.TopicCatalog
import com.syed.anvil.progress.FlagRules
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.theme.LocalPalette

/**
 * A topic's questions, read straight from the offline copy. The quiz, study and exam modes arrive in the next
 * phase; for now this proves the data path (and the offline flag queue) end to end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(vm: AnvilViewModel, id: ModuleId, group: String, topic: String, onBack: () -> Unit) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val flags by m.progress.flags.collectAsState()
    val unsynced by m.progress.unsynced.collectAsState()
    val p = LocalPalette.current
    val content = (state as? ContentState.Ready)?.content
    val name = content?.groups?.firstOrNull { it.key == group }?.topics?.firstOrNull { it.slug == topic }?.name
        ?: TopicCatalog.prettify(topic)
    val items = content?.items(group, topic).orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${items.size} questions" + if (unsynced > 0) " · $unsynced waiting to sync" else "",
                            style = MaterialTheme.typography.labelMedium, color = p.text3,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                val flag = item.uid?.let { flags[it] }
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.surface).padding(16.dp)) {
                    Text(plain(item), style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    item.uid?.let { uid ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Marker(Icons.Filled.Star, "Nailed", flag?.nailed == true, p.ok) { m.progress.update(uid, FlagRules::toggleNailed) }
                            Marker(Icons.Filled.Bookmark, "Important", flag?.important == true, p.imp) { m.progress.update(uid, FlagRules::toggleImportant) }
                            Marker(Icons.Filled.LocalFireDepartment, "Weak", flag?.weak == true, p.warn) { m.progress.update(uid, FlagRules::toggleWeak) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Marker(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, label, tint = if (on) color else LocalPalette.current.text3.copy(alpha = .55f))
    }
}

/** General questions are HTML; ICT ones are plain text where only the first line is the prompt. */
private fun plain(item: Item): String {
    val raw = item.question
    return if ('<' in raw || '&' in raw) HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() else raw
}
