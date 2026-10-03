package com.syed.anvil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.PoolSet
import com.syed.anvil.content.QuizPool
import com.syed.anvil.content.TopicCatalog
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectScreen(
    vm: AnvilViewModel, id: ModuleId, group: String, topic: String,
    onBack: () -> Unit, onQuiz: (PoolSet) -> Unit, onStudy: () -> Unit,
) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val flags by m.progress.flags.collectAsState()
    val p = LocalPalette.current
    val content = (state as? ContentState.Ready)?.content
    val name = content?.groups?.firstOrNull { it.key == group }?.topics?.firstOrNull { it.slug == topic }?.name ?: TopicCatalog.prettify(topic)
    val items = remember(content, group, topic) { content?.items(group, topic).orEmpty() }
    val counts = remember(items, flags) { QuizPool.counts(items, flags) }
    var chooser by remember { mutableStateOf(false) }
    val quizzable = counts.getValue(PoolSet.ALL)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(name, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ModeCard(Icons.Filled.PlayArrow, "MCQ Mode", "প্রশ্ন একটি একটি করে উত্তর দাও। তাৎক্ষণিক ঠিক/ভুল ফিডব্যাক ও স্কোর।",
                "$quizzable questions", quizzable > 0) {
                // Only offer the pool chooser when there is something to choose between.
                if (counts.getValue(PoolSet.IMPORTANT) + counts.getValue(PoolSet.NAILED) > 0) chooser = true else onQuiz(PoolSet.ALL)
            }
            ModeCard(Icons.AutoMirrored.Filled.MenuBook, "Study Mode", "প্রশ্ন ও উত্তর পড়ো, নিজের গতিতে।", "${quizzable} questions", quizzable > 0, onStudy)
        }
    }

    if (chooser) {
        ModalBottomSheet(onDismissRequest = { chooser = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("কোন প্রশ্নগুলো থেকে পরীক্ষা দেবে?", style = MaterialTheme.typography.titleMedium)
                PoolOption(Icons.Filled.Checklist, "সব প্রশ্ন", "পুরো টপিক থেকে", counts.getValue(PoolSet.ALL), p.info) { chooser = false; onQuiz(PoolSet.ALL) }
                PoolOption(Icons.Filled.Bookmark, "শুধু Important", "যেগুলো Important করে রেখেছো", counts.getValue(PoolSet.IMPORTANT), p.imp) { chooser = false; onQuiz(PoolSet.IMPORTANT) }
                PoolOption(Icons.Filled.LocalFireDepartment, "শুধু Weak", "Important-এর মধ্যে যেগুলো এখনো পারো না", counts.getValue(PoolSet.WEAK), p.warn) { chooser = false; onQuiz(PoolSet.WEAK) }
                PoolOption(Icons.Filled.Star, "শুধু Nailed It", "যেগুলো আয়ত্তে এসেছে — ঝালিয়ে নাও", counts.getValue(PoolSet.NAILED), p.ok) { chooser = false; onQuiz(PoolSet.NAILED) }
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, sub: String, meta: String, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else .5f).clip(MaterialTheme.shapes.large).background(p.surface)
            .clickable(enabled = enabled, onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, Modifier.padding(4.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = p.text2)
            Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PoolOption(icon: ImageVector, title: String, sub: String, count: Int, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().alpha(if (count > 0) 1f else .45f).clip(MaterialTheme.shapes.medium).background(p.surface)
            .clickable(enabled = count > 0, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = color)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(if (count > 0) sub else "এখনো কোনো প্রশ্ন নেই", style = MaterialTheme.typography.bodySmall, color = p.text3)
        }
        Text("$count", style = MaterialTheme.typography.titleMedium, color = color)
    }
}
