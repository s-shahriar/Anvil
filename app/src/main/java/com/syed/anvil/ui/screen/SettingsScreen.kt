package com.syed.anvil.ui.screen

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.syed.anvil.BuildConfig
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.content.SyncState
import com.syed.anvil.ui.AnvilViewModel
import com.syed.anvil.ui.ThemeMode
import com.syed.anvil.ui.component.UpdateCard
import com.syed.anvil.ui.component.formatBytes
import com.syed.anvil.ui.component.formatDate
import com.syed.anvil.ui.theme.LocalPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AnvilViewModel, activity: Activity, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = vm.themeMode == mode, onClick = { vm.setTheme(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
            }
            ModuleId.entries.forEach { id -> ModuleSection(vm, activity, id) }
            Section("Updates") { UpdateCard(vm, showWhenIdle = true) }
            Section("About") {
                Text("Anvil ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text("General and ICT quizzes in one place, working offline.", style = MaterialTheme.typography.bodyMedium, color = LocalPalette.current.text3)
            }
        }
    }
}

@Composable
private fun ModuleSection(vm: AnvilViewModel, activity: Activity, id: ModuleId) {
    val m = vm.module(id)
    val state by m.content.state.collectAsState()
    val sync by m.content.sync.collectAsState()
    val session by m.auth.session.collectAsState()
    val unsynced by m.progress.unsynced.collectAsState()
    val online by vm.online.collectAsState()
    val p = LocalPalette.current

    Section("${id.title} · offline data") {
        Text(
            when (val s = state) {
                is ContentState.Ready -> "${s.content.total} questions · ${formatBytes(m.content.cacheBytes)} · downloaded ${formatDate(m.content.cachedAt)}"
                ContentState.Empty -> "Not downloaded"
                ContentState.NotLoaded -> "Loading…"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val saved by vm.imagesSaved.collectAsState()
        ((state as? ContentState.Ready)?.content)?.let { c ->
            val total = remember(c) { m.imageUrls(c).size }
            if (total > 0) Text("Pictures: ${minOf(saved, total)} of $total saved", style = MaterialTheme.typography.bodySmall, color = p.text3)
        }
        (sync as? SyncState.Running)?.let { Text(it.message, style = MaterialTheme.typography.labelMedium, color = p.text3) }
        (sync as? SyncState.Failed)?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.refreshContent(id) }, enabled = online && sync !is SyncState.Running) {
                Text(if (state is ContentState.Ready) "Refresh" else "Download")
            }
            if (state is ContentState.Ready) OutlinedButton(onClick = { vm.clearContent(id) }) { Text("Remove") }
        }
        Text("Account", style = MaterialTheme.typography.titleSmall)
        val s = session
        if (s == null) {
            Text("Sign in to save Nailed / Important / Weak across devices.", style = MaterialTheme.typography.bodySmall, color = p.text3)
            if (m.config.googleWebClientId.isBlank()) {
                Text("Google sign-in isn't configured yet (set GOOGLE_WEB_CLIENT_ID_${id.name} in local.properties).", style = MaterialTheme.typography.bodySmall, color = p.warn)
            }
            Button(onClick = { vm.signIn(activity, id) }, enabled = online && m.config.googleWebClientId.isNotBlank()) { Text("Sign in with Google") }
        } else {
            Text(s.email ?: "Signed in", style = MaterialTheme.typography.bodyMedium)
            if (unsynced > 0) Text("$unsynced changes waiting to sync", style = MaterialTheme.typography.bodySmall, color = p.warn)
            TextButton(onClick = { vm.signOut(id) }) { Text("Sign out") }
        }
        vm.authError?.takeIf { it.startsWith(id.title) }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(LocalPalette.current.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
