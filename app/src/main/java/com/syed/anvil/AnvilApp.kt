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
class ModuleServices(app: Application, val id: ModuleId, scope: CoroutineScope, images: ImageStore) {
    val config = Backends.of(id)
    val auth = SupabaseAuth(app, config)
    val db = Postgrest(config, auth)
    val content = ContentRepository(app, id, db) { fresh ->
        // Pictures are fetched in the background so questions that use them still work offline.
        scope.launch { images.prefetch(fresh.allItems().flatMap { ImageStore.urlsIn(it) }.toSet()) }
    }
    val progress = ProgressRepository(app, id, auth, db, scope)
}

class AnvilApp : Application() {
    /** Outlives any screen, so queued saves finish even when the UI is gone. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var connectivity: Connectivity; private set
    lateinit var updates: UpdateService; private set
    lateinit var images: ImageStore; private set
    private val modules = HashMap<ModuleId, ModuleServices>()

    override fun onCreate() {
        super.onCreate()
        connectivity = Connectivity(this)
        updates = UpdateService(this)
        images = ImageStore(this)
        ModuleId.entries.forEach { modules[it] = ModuleServices(this, it, appScope, images) }
    }

    fun module(id: ModuleId): ModuleServices = modules.getValue(id)
}
