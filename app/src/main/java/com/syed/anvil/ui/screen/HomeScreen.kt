package com.syed.anvil.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.syed.anvil.R
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.SyncState
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.component.UpdateCard
import com.syed.anvil.ui.theme.AnvilTheme
import com.syed.anvil.ui.theme.LocalPalette
import com.syed.anvil.ui.theme.Scope

@Composable
fun HomeScreen(vm: AnvilViewModel, dark: Boolean, onModule: (ModuleId) -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Anvil", style = MaterialTheme.typography.displaySmall)
                    Text("Forge your preparation", style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text3)
                }
                IconButton(onClick = { vm.chooseLeftHand(!vm.leftHand) }) { Icon(Icons.Filled.PanTool, if (vm.leftHand) "Left-hand layout: switch to right" else "Right-hand layout: switch to left", tint = if (vm.leftHand) MaterialTheme.colorScheme.primary else LocalPalette.current.text3) }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
            }
            UpdateCard(vm, showWhenIdle = false)
            Spacer(Modifier.height(4.dp))
            ModuleId.entries.forEach { id ->
                ModuleCard(vm, id, dark) { onModule(id) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Painted in the module's own palette, so the card previews the scheme you are about to enter. */
@Composable
private fun ModuleCard(vm: AnvilViewModel, id: ModuleId, dark: Boolean, onClick: () -> Unit) {
    val scope = if (id == ModuleId.GENERAL) Scope.GENERAL else Scope.ICT
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val sync by m.content.sync.collectAsState()
    AnvilTheme(scope, dark) {
        val p = LocalPalette.current
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(p.primaryContainer)
                .clickable(onClick = onClick).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(id.title, style = MaterialTheme.typography.headlineLarge, color = p.onPrimaryContainer)
            Text(id.tagline, style = MaterialTheme.typography.bodyMedium, color = p.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            val status = when {
                sync is SyncState.Running -> (sync as SyncState.Running).message
                state is ContentState.Ready -> (state as ContentState.Ready).content.let { "${it.total} questions · ${it.groups.sumOf { g -> g.topics.size }} topics · offline ready" }
                state is ContentState.Empty -> "Tap to download for offline use"
                else -> "Loading…"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (state is ContentState.Ready) p.ok else p.text3))
                Text(status, style = MaterialTheme.typography.labelLarge, color = p.onPrimaryContainer)
            }
        }
    }
}
