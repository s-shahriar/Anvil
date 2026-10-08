package com.syed.slate

import android.app.Application
import com.syed.slate.backend.Backends
import com.syed.slate.backend.ModuleId
import com.syed.slate.backend.Postgrest
import com.syed.slate.backend.SupabaseAuth
import com.syed.slate.content.BlobRepository
import com.syed.slate.content.ContentRepository
import com.syed.slate.content.ImageStore
import com.syed.slate.core.Connectivity
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.update.UpdateService
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
    fun imageUrls(c: com.syed.slate.content.ModuleContent): Set<String> = c.allItems().flatMap { ImageStore.urlsIn(it) }.toSet()

    /** Pictures are fetched in the background so questions that use them still work offline; resumes if cut short. */
    fun prefetchImages(c: com.syed.slate.content.ModuleContent) { scope.launch { images.prefetch(imageUrls(c)) } }
    /** Practice, equations, utility pages: small JSON rows from `content_blobs`, cached for offline use. */
    val blobs = BlobRepository(app, id, db)
    val progress = ProgressRepository(app, id, auth, db, scope)
    val trash: com.syed.slate.trash.TrashRepository = com.syed.slate.trash.TrashRepository(app, id, auth, db, scope) { scope.launch { content.refresh() } }

    val highlights = com.syed.slate.highlight.HighlightRepository(app, id, auth, db, scope)

    init { content.bindHidden(scope, trash.hidden) }

    /** Pushes every queue now, skipping any backoff wait: flags, recycle-bin ops and highlights. */
    fun flushNow() { progress.retryNow(); trash.retryNow(); highlights.retryNow() }
}

class SlateApp : Application() {
    /** Outlives any screen, so queued saves finish even when the UI is gone. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var connectivity: Connectivity; private set
    lateinit var updates: UpdateService; private set
    lateinit var images: ImageStore; private set
    private val modules = HashMap<ModuleId, ModuleServices>()

    /** ICT » Practice, from the ICT project's `content_blobs` (parsed again only when the cache changes). */
    val practice: List<com.syed.slate.practice.Category> get() {
        val blobs = module(ModuleId.ICT).blobs
        val v = blobs.version.value
        practiceCache?.takeIf { it.first == v }?.let { return it.second }
        return com.syed.slate.practice.PracticeData.fromBlobs(blobs).also { practiceCache = v to it }
    }
    private var practiceCache: Pair<Int, List<com.syed.slate.practice.Category>>? = null

    override fun onCreate() {
        super.onCreate()
        connectivity = Connectivity(this)
        updates = UpdateService(this)
        images = ImageStore(this)
        ModuleId.entries.forEach { modules[it] = ModuleServices(this, it, appScope, images) }
        // The moment the connection comes back, everything queued while offline (or stuck in a retry wait of up to
        // five minutes) is sent, instead of waiting for the next backoff tick.
        appScope.launch {
            var was = connectivity.online.value
            connectivity.online.collect { now -> if (now && !was) modules.values.forEach { it.flushNow() }; was = now }
        }
        SyncNotifier(connectivity, ModuleId.entries.map { modules.getValue(it) }).start(appScope)
    }

    fun module(id: ModuleId): ModuleServices = modules.getValue(id)
}
