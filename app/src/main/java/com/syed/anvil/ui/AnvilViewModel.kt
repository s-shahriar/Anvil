package com.syed.anvil.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syed.anvil.AnvilApp
import com.syed.anvil.backend.GoogleSignIn
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.content.ContentState
import com.syed.anvil.update.DownloadProgress
import com.syed.anvil.update.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The exam being taken. Held in memory only, so a relaunch returns to the setup screen, as on the web. */
class ExamSpec(val module: ModuleId, val items: List<com.syed.anvil.content.Item>, val label: String?)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: DownloadProgress) : UpdateState
    data class ReadyToInstall(val file: File, val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val file: File, val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class AnvilViewModel(private val app: Application) : AndroidViewModel(app) {
    private val anvil get() = app as AnvilApp
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val online get() = anvil.connectivity.online

    var themeMode by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
        private set

    fun setTheme(mode: ThemeMode) { themeMode = mode; prefs.edit().putString("theme", mode.name).apply() }

    fun module(id: ModuleId) = anvil.module(id)

    var exam: ExamSpec? = null

    init {
        // A new version is running: its APK from the last update is no longer needed.
        if (prefs.getString("lastVersion", null) != com.syed.anvil.BuildConfig.VERSION_NAME) {
            anvil.updates.clearStaleDownloads()
            prefs.edit().putString("lastVersion", com.syed.anvil.BuildConfig.VERSION_NAME).apply()
        }
        ModuleId.entries.forEach { id -> viewModelScope.launch { anvil.module(id).content.load() } }
        autoCheckForUpdate()
    }

    // ── offline content ──────────────────────────────────────────────────

    /**
     * Called when a module is opened. Shows the offline copy straight away; downloads when there is none yet,
     * or refreshes quietly when it is over a day old and a connection is available.
     */
    fun openModule(id: ModuleId) = viewModelScope.launch {
        val m = anvil.module(id)
        m.content.load()
        val stale = System.currentTimeMillis() - m.content.cachedAt > DAY_MS
        if (online.value && (m.content.state.value is ContentState.Empty || stale)) {
            m.content.refresh()
        }
        if (m.auth.session.value != null) runCatching { m.progress.pull() }
    }

    fun refreshContent(id: ModuleId) = viewModelScope.launch { anvil.module(id).content.refresh() }
    fun clearContent(id: ModuleId) = viewModelScope.launch { anvil.module(id).content.clear() }

    // ── accounts ─────────────────────────────────────────────────────────

    var authError by mutableStateOf<String?>(null)
        private set

    fun signIn(activity: Activity, id: ModuleId) = viewModelScope.launch {
        authError = null
        val m = anvil.module(id)
        runCatching {
            val t = GoogleSignIn.requestToken(activity, m.config.googleWebClientId)
            m.auth.signInWithGoogle(t.idToken, t.rawNonce)
            m.progress.kick() // edits made while signed out go up now
            m.progress.pull()
        }.onFailure { authError = "${id.title}: ${it.message ?: "Sign-in failed"}" }
    }

    fun signOut(id: ModuleId) = anvil.module(id).auth.signOut()

    // ── updates ──────────────────────────────────────────────────────────

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** At launch: at most once a day, and silent unless there really is an update. */
    private fun autoCheckForUpdate() {
        if (System.currentTimeMillis() - prefs.getLong("lastUpdateCheck", 0) < DAY_MS) return
        viewModelScope.launch {
            if (!online.value) return@launch
            runCatching { anvil.updates.checkForUpdate() }.onSuccess {
                prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
                if (it.available) update = UpdateState.Available(it)
            }
        }
    }

    fun checkForUpdate() {
        update = UpdateState.Checking
        viewModelScope.launch {
            runCatching { anvil.updates.checkForUpdate() }
                .onSuccess {
                    prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
                    update = if (it.available) UpdateState.Available(it) else UpdateState.UpToDate(it.currentVersion)
                }
                .onFailure { update = UpdateState.Failed(it.message ?: "Check failed") }
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            runCatching { anvil.updates.download(info) { update = UpdateState.Downloading(it) } }
                .onSuccess { update = UpdateState.ReadyToInstall(it, info) }
                .onFailure { update = UpdateState.Failed(it.message ?: "Download failed") }
        }
    }

    /** Opens the system installer, first sending the user to the "install unknown apps" switch if needed. */
    fun install(file: File, info: UpdateInfo) {
        if (!app.packageManager.canRequestPackageInstalls()) {
            update = UpdateState.NeedsPermission(file, info)
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }.onFailure { update = UpdateState.Failed(it.message ?: "Could not open the installer") }
    }

    fun dismissUpdate() { update = UpdateState.Idle }

    private companion object { const val DAY_MS = 24L * 60 * 60 * 1000 }
}
