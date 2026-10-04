package com.syed.slate.ui.screen

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.syed.slate.ui.component.HandMirror
import com.syed.slate.R
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.ContentState
import com.syed.slate.content.SyncState
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.component.UpdateCard
import com.syed.slate.ui.theme.SlateTheme
import com.syed.slate.ui.theme.LocalPalette
import com.syed.slate.ui.theme.Scope

@Composable
fun HomeScreen(vm: SlateViewModel, dark: Boolean, onModule: (ModuleId) -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            // Home's header mirrors like the web's floating cluster: the gear lands on the other corner.
            HandMirror {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Slate", style = MaterialTheme.typography.displaySmall)
                        Text("Sharpen your preparation", style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text3)
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                }
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

/** Painted in the module's own palette, Magpie's ModuleCard shape: icon coin, name, blurb, status. */
@Composable
private fun ModuleCard(vm: SlateViewModel, id: ModuleId, dark: Boolean, onClick: () -> Unit) {
    val scope = if (id == ModuleId.GENERAL) Scope.GENERAL else Scope.ICT
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val sync by m.content.sync.collectAsState()
    SlateTheme(scope, dark) {
        val p = LocalPalette.current
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(230.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
            color = p.surface,
            border = BorderStroke(1.dp, p.outline),
        ) {
            Column(Modifier.padding(22.dp)) {
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (id == ModuleId.GENERAL) Icons.AutoMirrored.Filled.MenuBook else Icons.Filled.Bolt,
                        null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(id.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (id == ModuleId.GENERAL) "বাংলা, English ও সাধারণ জ্ঞান Practice"
                    else "Master Information & Communication Technology",
                    style = MaterialTheme.typography.bodyMedium, color = p.text3,
                )
                Spacer(Modifier.height(12.dp))
                when (val s = state) {
                    is ContentState.Ready -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(p.ok))
                        Text("${s.content.total} questions · ${s.content.groups.sumOf { it.topics.size }} topics · offline ready", style = MaterialTheme.typography.labelMedium, color = p.text2)
                    }
                    ContentState.Empty -> if (sync !is SyncState.Running) Text("Tap to download for offline use", style = MaterialTheme.typography.labelMedium, color = p.text3)
                    ContentState.NotLoaded -> Unit
                }
            }
        }
    }
}
