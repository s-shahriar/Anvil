package com.syed.anvil

import android.app.Application
import com.syed.anvil.backend.Backends
import com.syed.anvil.backend.ModuleId
import com.syed.anvil.backend.Postgrest
import com.syed.anvil.backend.SupabaseAuth
import com.syed.anvil.content.ContentRepository
import com.syed.anvil.content.ImageStore
import com.syed.anvil.core.Connectivity
import com.syed.anvil.progress.ProgressRepository
import com.syed.anvil.update.UpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Everything one module needs: its own Supabase project, auth session, offline content and progress. */
class ModuleServices(app: Application, val id: ModuleId, private val scope: CoroutineScope, private val images: ImageStore) {
    val config = Backends.of(id)
    val auth = SupabaseAuth(app, config)
    val db = Postgrest(config, auth)
    val content: ContentRepository = ContentRepository(app, id, db) { fresh -> prefetchImages(fresh); trash.onContentRefreshed() }

    /** Every image this module's questions use. */
    fun imageUrls(c: com.syed.anvil.content.ModuleContent): Set<String> = c.allItems().flatMap { ImageStore.urlsIn(it) }.toSet()

    /** Pictures are fetched in the background so questions that use them still work offline; resumes if cut short. */
    fun prefetchImages(c: com.syed.anvil.content.ModuleContent) { scope.launch { images.prefetch(imageUrls(c)) } }
    val progress = ProgressRepository(app, id, auth, db, scope)
    val trash: com.syed.anvil.trash.TrashRepository = com.syed.anvil.trash.TrashRepository(app, id, auth, db, scope) { scope.launch { content.refresh() } }

    val highlights = com.syed.anvil.highlight.HighlightRepository(app, id, auth, db, scope)

    init { content.bindHidden(scope, trash.hidden) }
}

class AnvilApp : Application() {
    /** Outlives any screen, so queued saves finish even when the UI is gone. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var connectivity: Connectivity; private set
    lateinit var updates: UpdateService; private set
    lateinit var images: ImageStore; private set
    private val modules = HashMap<ModuleId, ModuleServices>()

    /** ICT » Practice: bundled with the app, loaded on first use. */
    val practice: List<com.syed.anvil.practice.Category> by lazy { com.syed.anvil.practice.PracticeData.load { assets.open(it) } }

    override fun onCreate() {
        super.onCreate()
        connectivity = Connectivity(this)
        updates = UpdateService(this)
        images = ImageStore(this)
        ModuleId.entries.forEach { modules[it] = ModuleServices(this, it, appScope, images) }
    }

    fun module(id: ModuleId): ModuleServices = modules.getValue(id)
}
