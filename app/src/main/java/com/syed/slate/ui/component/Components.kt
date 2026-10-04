package com.syed.slate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.UpdateState
import java.text.DateFormat
import java.util.Date

fun formatBytes(b: Long): String = when {
    b >= 1 shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1 shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

fun formatDate(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

@Composable
fun Dot(color: Color, size: Int = 8) {
    androidx.compose.foundation.layout.Box(Modifier.size(size.dp).background(color, CircleShape))
}

/** Self-contained updater UI: used on Home (banner) and in Settings. */
@Composable
fun UpdateCard(vm: SlateViewModel, showWhenIdle: Boolean, modifier: Modifier = Modifier) {
    val s = vm.update
    if (s is UpdateState.Idle && !showWhenIdle) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val ink = MaterialTheme.colorScheme.onPrimaryContainer
            when (s) {
                UpdateState.Idle -> {
                    Text("Updates", style = MaterialTheme.typography.titleMedium, color = ink)
                    Button(onClick = vm::checkForUpdate) { Text("Check for updates") }
                }
                UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Checking for updates…", color = ink)
                }
                is UpdateState.UpToDate -> {
                    Text("You're on the latest version (${s.version})", color = ink)
                    TextButton(onClick = vm::checkForUpdate) { Text("Check again") }
                }
                is UpdateState.Available -> {
                    Text("Version ${s.info.latestVersion} is available", style = MaterialTheme.typography.titleMedium, color = ink)
                    s.info.notes?.takeIf { it.isNotBlank() }?.let { Text(it.take(400), style = MaterialTheme.typography.bodySmall, color = ink) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.downloadUpdate(s.info) }) { Text("Download") }
                        TextButton(onClick = vm::dismissUpdate) { Text("Later") }
                    }
                }
                is UpdateState.Downloading -> {
                    Text("Downloading update…", style = MaterialTheme.typography.titleMedium, color = ink)
                    LinearProgressIndicator(progress = { s.progress.fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        formatBytes(s.progress.bytesDownloaded) + (s.progress.totalBytes?.let { " / ${formatBytes(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = ink,
                    )
                }
                is UpdateState.ReadyToInstall -> {
                    Text("Version ${s.info.latestVersion} is ready", style = MaterialTheme.typography.titleMedium, color = ink)
                    Button(onClick = { vm.install(s.file, s.info) }) { Text("Install") }
                }
                is UpdateState.NeedsPermission -> {
                    Text("Allow Slate to install apps in the settings page that just opened, then come back and tap Install.", color = ink)
                    Button(onClick = { vm.install(s.file, s.info) }) { Text("Install") }
                }
                is UpdateState.Failed -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::checkForUpdate) { Text("Try again") }
                }
            }
        }
    }
}
