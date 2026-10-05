package com.syed.slate.ui

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
import com.syed.slate.SlateApp
import com.syed.slate.backend.GoogleSignIn
import com.syed.slate.backend.ModuleId
import com.syed.slate.content.CACHE_VERSION
import com.syed.slate.content.ContentState
import com.syed.slate.update.DownloadProgress
import com.syed.slate.update.UpdateInfo
import kotlinx.coroutines.launch
import java.io.File

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The exam being taken. Held in memory only, so a relaunch returns to the setup screen, as on the web. */
class ExamSpec(val module: ModuleId, val items: List<com.syed.slate.content.Item>, val label: String?)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: DownloadProgress) : UpdateState
    data object Verifying : UpdateState
    data class ReadyToInstall(val file: File, val info: UpdateInfo) : UpdateState
    data class NeedsPermission(val file: File, val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class SlateViewModel(private val app: Application) : AndroidViewModel(app) {
    private val slate get() = app as SlateApp
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val online get() = slate.connectivity.online

    var themeMode by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
        private set

    var leftHand by mutableStateOf(prefs.getBoolean("leftHand", false))
        private set

    fun chooseLeftHand(v: Boolean) { leftHand = v; prefs.edit().putBoolean("leftHand", v).apply() }

    fun boolPref(key: String) = prefs.getBoolean(key, false)
    fun setBoolPref(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()

    fun setTheme(mode: ThemeMode) { themeMode = mode; prefs.edit().putString("theme", mode.name).apply() }

    fun module(id: ModuleId) = slate.module(id)

    var exam: ExamSpec? = null

    init {
        // A new version is running: its APK from the last update is no longer needed.
        if (prefs.getString("lastVersion", null) != com.syed.slate.BuildConfig.VERSION_NAME) {
            slate.updates.clearStaleDownloads()
            prefs.edit().putString("lastVersion", com.syed.slate.BuildConfig.VERSION_NAME).apply()
        }
        ModuleId.entries.forEach { id -> viewModelScope.launch { slate.module(id).content.load() } }
        autoCheckForUpdate()
    }

    // ── offline content ──────────────────────────────────────────────────

    /**
     * Called when a module is opened. Shows the offline copy straight away; the network is touched only when
     * there is no copy yet. Updating is the Refresh button's job (a delta check, never a full re-download).
     */
    fun openModule(id: ModuleId) = viewModelScope.launch {
        val m = slate.module(id)
        m.content.load()
        if (online.value) {
            // Opening a module never scans the server: it shows the offline copy. Only a missing or outdated copy is
            // downloaded here; everything else is updated by the Refresh button (a delta check) in the module / Settings.
            val outdated = ((m.content.state.value as? ContentState.Ready)?.content?.version ?: 0) < CACHE_VERSION
            if (m.content.state.value is ContentState.Empty || outdated) m.content.refresh()
        }
        // Practice / utility rows: a cheap probe, fetches only what changed.
        if (online.value) launch { m.blobs.refresh() }
        // A download that was cut short (app closed, connection lost) is topped up here.
        if (online.value) (m.content.state.value as? ContentState.Ready)?.let { m.prefetchImages(it.content) }
        if (m.auth.session.value != null) { runCatching { m.progress.pull() }; runCatching { m.highlights.pull() } }
    }

    val imagesSaved get() = slate.images.cachedCount

    fun refreshContent(id: ModuleId) = viewModelScope.launch { launch { slate.module(id).blobs.refresh() }; slate.module(id).content.refresh() }
    fun clearContent(id: ModuleId) = viewModelScope.launch { slate.module(id).content.clear() }

    // ── accounts ─────────────────────────────────────────────────────────

    var authError by mutableStateOf<String?>(null)
        private set

    fun signIn(activity: Activity, id: ModuleId) = viewModelScope.launch {
        authError = null
        val m = slate.module(id)
        runCatching {
            val t = GoogleSignIn.requestToken(activity, m.config.googleWebClientId)
            m.auth.signInWithGoogle(t.idToken, t.rawNonce)
            m.progress.kick() // edits made while signed out go up now
            m.highlights.kick(); m.trash.kick()
            m.progress.pull(); m.highlights.pull()
        }.onFailure { authError = "${id.title}: ${it.message ?: "Sign-in failed"}" }
    }

    fun signOut(id: ModuleId) = slate.module(id).auth.signOut()

    // ── updates ──────────────────────────────────────────────────────────

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** At launch: at most once a day, and silent unless there really is an update. */
    private fun autoCheckForUpdate() {
        if (System.currentTimeMillis() - prefs.getLong("lastUpdateCheck", 0) < DAY_MS) return
        viewModelScope.launch {
            if (!online.value) return@launch
            runCatching { slate.updates.checkForUpdate() }.onSuccess {
                prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
                if (it.available) update = UpdateState.Available(it)
            }
        }
    }

    fun checkForUpdate() {
        update = UpdateState.Checking
        viewModelScope.launch {
            runCatching { slate.updates.checkForUpdate() }
                .onSuccess {
                    prefs.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply()
                    update = if (it.available) UpdateState.Available(it) else UpdateState.UpToDate(it.currentVersion)
                }
                .onFailure { update = UpdateState.Failed(it.message ?: "Check failed") }
        }
    }

    private var downloadJob: kotlinx.coroutines.Job? = null

    fun downloadUpdate(info: UpdateInfo) {
        if (downloadJob?.isActive == true) return // a second tap must not start a second writer on the same file
        // Show it at once: connecting can take seconds on a slow link, and silence reads as a frozen app.
        update = UpdateState.Downloading(DownloadProgress(0, null, 0))
        downloadJob = viewModelScope.launch {
            runCatching { slate.updates.download(info, { update = UpdateState.Downloading(it) }, { update = UpdateState.Verifying }) }
                .onSuccess { update = UpdateState.ReadyToInstall(it, info) }
                .onFailure { if (it !is kotlinx.coroutines.CancellationException) update = UpdateState.Failed(it.message ?: "Download failed") }
        }
    }

    /** Stops a running download; the partial file is kept, so the next Download resumes from it. */
    fun cancelDownload() {
        downloadJob?.cancel(); downloadJob = null
        update = UpdateState.Idle
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
