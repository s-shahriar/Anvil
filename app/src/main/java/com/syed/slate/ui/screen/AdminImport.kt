package com.syed.slate.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syed.slate.ModuleServices
import com.syed.slate.content.ContentState
import com.syed.slate.content.LivemcqClassifier
import com.syed.slate.content.LivemcqFavorites
import com.syed.slate.content.Subtopic
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.ui.component.SlateLoader
import com.syed.slate.ui.rich.RichText
import com.syed.slate.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class ImportItem(val norm: LivemcqAdmin.NormItem, val slug: String = "", val subtopic: String = "", val picked: Boolean = true)

internal class ImportSummary(val total: Int, val dupInDb: Int, val dupInFile: Int, val badFid: Int, val fresh: Int)

/** Everything the Import tab holds, kept outside the composable so switching tabs doesn't throw away a loaded file. */
@Stable
internal class ImportState {
    var items by mutableStateOf(listOf<ImportItem>())
    var fileName by mutableStateOf("")
    var summary by mutableStateOf<ImportSummary?>(null)
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
    var result by mutableStateOf<String?>(null)
    var missing by mutableStateOf(setOf<String>())
    var index by mutableStateOf<LivemcqClassifier.Index?>(null)
    var indexFor by mutableStateOf<Any?>(null)
    var hints by mutableStateOf(mapOf<String, LivemcqClassifier.Suggestion>())
    /** fid -> (category the sub-topic guess was scored against, the guess). */
    var subHints by mutableStateOf(mapOf<String, Pair<String, LivemcqClassifier.Suggestion?>>())

    // ── The LiveMCQ source (favourites fetched straight from livemcq.com, no file in between) ──
    /** The raw favourites of the last fetch, kept for "Save copy"; null when the items came from a file. */
    var fetched by mutableStateOf<List<JSONObject>?>(null)
    var login by mutableStateOf(false)
    var liveSignedIn by mutableStateOf(LivemcqFavorites.isSignedIn())
    var newOnly by mutableStateOf(true)
    var count by mutableStateOf("50")
    var progress by mutableStateOf<LivemcqFavorites.Progress?>(null)
    var peek by mutableStateOf<String?>(null)
    var copySaved by mutableStateOf<String?>(null)
}

private val TIER_LABEL = mapOf("strong" to "Likely", "likely" to "Probably", "weak" to "Maybe")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImportPanel(
    m: ModuleServices, st: ImportState, catalog: AdminCatalog,
    onSubAdded: (String, Subtopic) -> Unit, onInserted: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val content = (m.content.state.collectAsState().value as? ContentState.Ready)?.content
    var bulkSlug by remember { mutableStateOf("") }

    fun needsSub(slug: String) = slug.isNotEmpty() && catalog.subList(slug).isNotEmpty()
    fun isReady(it: ImportItem) = it.slug.isNotEmpty() && (!needsSub(it.slug) || it.subtopic.isNotEmpty())

    // The classifier: asked for only once a file is open; rebuilt when the corpus changes (after an insert / refresh).
    LaunchedEffect(content, st.items.isNotEmpty()) {
        if (content == null || st.items.isEmpty() || st.indexFor === content) return@LaunchedEffect
        val idx = withContext(Dispatchers.Default) { LivemcqClassifier.build(content) }
        st.index = idx; st.indexFor = content
        val items = st.items
        st.hints = withContext(Dispatchers.Default) {
            items.mapNotNull { it -> idx.suggest(it.norm.question, it.norm.options, it.norm.explanation)?.let { s -> it.norm.favoriteId to s } }.toMap()
        }
        st.subHints = emptyMap()
    }
    // Sub-topic guesses: scored against the picked category, or the suggested one before any is picked.
    LaunchedEffect(st.items, st.hints, st.index) {
        val idx = st.index ?: return@LaunchedEffect
        val items = st.items; val hints = st.hints; val cache = st.subHints
        val add = withContext(Dispatchers.Default) {
            val out = HashMap<String, Pair<String, LivemcqClassifier.Suggestion?>>()
            for (it in items) {
                val slug = it.slug.ifEmpty { hints[it.norm.favoriteId]?.slug ?: "" }
                val list = catalog.subList(slug)
                if (slug.isEmpty() || list.isEmpty()) continue
                if (cache[it.norm.favoriteId]?.first == slug) continue
                out[it.norm.favoriteId] = slug to idx.suggestSubtopic(it.norm.question, it.norm.options, it.norm.explanation, slug, list.map { s -> s.slug })
            }
            out
        }
        if (add.isNotEmpty()) st.subHints = st.subHints + add
    }

    fun reset() {
        st.error = ""; st.result = null; st.summary = null; st.items = emptyList(); st.missing = emptySet()
        st.hints = emptyMap(); st.subHints = emptyMap(); st.indexFor = null; st.fetched = null; st.copySaved = null; st.peek = null
    }

    /** File or LiveMCQ alike: normalize, drop what the DB (or the batch itself) already has, and list the rest. */
    fun ingest(raw: List<Any?>, existing: Set<String>) {
        val seen = HashSet<String>(); val next = ArrayList<ImportItem>()
        var dupInDb = 0; var dupInFile = 0; var badFid = 0
        for (r in raw) {
            val norm = LivemcqAdmin.normalizeItem(r as? JSONObject ?: continue)
            if (norm.favoriteId.isEmpty()) { badFid++; continue }
            if (norm.favoriteId in existing) { dupInDb++; continue }
            if (!seen.add(norm.favoriteId)) { dupInFile++; continue }
            next.add(ImportItem(norm))
        }
        st.items = next
        st.summary = ImportSummary(raw.size, dupInDb, dupInFile, badFid, next.size)
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            reset()
            st.fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            st.busy = true
            try {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }.trim().removePrefix("\uFEFF")
                val parsed: Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
                val raw = LivemcqAdmin.extractRawItems(parsed)
                if (raw.isEmpty()) throw IllegalStateException("No questions found in the file.")
                ingest(raw, LivemcqAdmin.fetchExistingFavoriteIds(m.db))
            } catch (e: Exception) {
                st.error = e.message ?: e.toString(); st.fileName = ""
            }
            st.busy = false
        }
    }

    fun fetchLive() {
        if (st.busy) return
        scope.launch {
            reset()
            st.busy = true; st.progress = null
            try {
                val existing = LivemcqAdmin.fetchExistingFavoriteIds(m.db)
                val baseline = existing.mapNotNull { it.toLongOrNull() }.maxOrNull()
                val want = if (st.newOnly) LivemcqFavorites.Scope.Since(baseline)
                    else LivemcqFavorites.Scope.Newest(st.count.toIntOrNull()?.coerceIn(1, 5000) ?: 50)
                val raw = LivemcqFavorites.fetchAll(want) { pr -> scope.launch(Dispatchers.Main.immediate) { st.progress = pr } }
                if (raw.isEmpty()) throw IllegalStateException(
                    if (st.newOnly) "No new favourites on LiveMCQ" + (baseline?.let { " — nothing above favourite $it, the newest stored." } ?: ".")
                    else "The LiveMCQ account has no favourites.",
                )
                ingest(raw, existing)
                st.fetched = raw
                st.fileName = "LiveMCQ · ${raw.size} fetched"
            } catch (e: LivemcqFavorites.NotSignedIn) {
                st.liveSignedIn = false; st.error = "The LiveMCQ session has expired — sign in again."
            } catch (e: Exception) {
                st.error = e.message ?: e.toString()
            }
            st.busy = false; st.progress = null
        }
    }

    fun checkAccount() {
        if (st.busy) return
        scope.launch {
            st.busy = true; st.error = ""; st.peek = null
            try {
                val pk = LivemcqFavorites.peek()
                val stored = LivemcqAdmin.fetchExistingFavoriteIds(m.db).mapNotNull { it.toLongOrNull() }.maxOrNull()
                st.peek = "${pk.total} favourites on LiveMCQ (${pk.pages} pages)" +
                    (pk.newest?.let { " · newest $it" } ?: "") + (stored?.let { " · newest stored $it" } ?: "")
            } catch (e: LivemcqFavorites.NotSignedIn) {
                st.liveSignedIn = false; st.error = "The LiveMCQ session has expired — sign in again."
            } catch (e: Exception) {
                st.error = e.message ?: e.toString()
            }
            st.busy = false
        }
    }

    fun saveCopy() {
        val raw = st.fetched ?: return
        scope.launch {
            try { st.copySaved = "Saved ${LivemcqFavorites.saveCopy(ctx, raw)}" } catch (e: Exception) { st.error = e.message ?: e.toString() }
        }
    }
    val items = st.items
    val picked = items.filter { it.picked }
    val pickedNoCat = picked.filter { it.slug.isEmpty() }
    val pickedNoSub = picked.filter { it.slug.isNotEmpty() && needsSub(it.slug) && it.subtopic.isEmpty() }
    val pickedReady = picked.filter { isReady(it) }
    val canPartial = pickedReady.isNotEmpty() && pickedReady.size < picked.size
    val bulkMin = LivemcqClassifier.BULK_APPLY_MIN

    fun patch(fid: String, f: (ImportItem) -> ImportItem) { st.items = st.items.map { if (it.norm.favoriteId == fid) f(it) else it } }
    fun clearMissing(fid: String) { if (fid in st.missing) st.missing = st.missing - fid }
    fun unflagReady(next: List<ImportItem>) {
        val ready = next.filter { isReady(it) }.map { it.norm.favoriteId }.toSet()
        st.missing = st.missing.filter { it !in ready }.toSet()
    }
    fun setSlug(fid: String, slug: String) { patch(fid) { it.copy(slug = slug, subtopic = "") }; if (slug.isNotEmpty() && !needsSub(slug)) clearMissing(fid) }
    fun setSub(fid: String, sub: String) { patch(fid) { it.copy(subtopic = sub) }; if (sub.isNotEmpty()) clearMissing(fid) }
    fun applyHint(fid: String, slug: String, sub: String = "") { patch(fid) { it.copy(slug = slug, subtopic = sub) }; if (sub.isNotEmpty() || !needsSub(slug)) clearMissing(fid) }

    fun applyAllHints() {
        val next = st.items.map { it ->
            val h = st.hints[it.norm.favoriteId]
            if (h == null || it.slug.isNotEmpty() || h.confidence < bulkMin) it
            else {
                val sh = st.subHints[it.norm.favoriteId]?.takeIf { x -> x.first == h.slug }?.second
                it.copy(slug = h.slug, subtopic = if (sh != null && sh.confidence >= bulkMin) sh.slug else "")
            }
        }
        st.items = next; unflagReady(next)
    }
    fun applyAllSubHints() {
        val next = st.items.map { it ->
            val sh = st.subHints[it.norm.favoriteId]?.takeIf { x -> x.first == it.slug }?.second
            if (sh != null && it.slug.isNotEmpty() && it.subtopic.isEmpty() && sh.confidence >= bulkMin) it.copy(subtopic = sh.slug) else it
        }
        st.items = next; unflagReady(next)
    }
    fun applyBulk(slug: String) {
        bulkSlug = ""
        if (slug.isEmpty()) return
        val next = st.items.map { if (it.picked) it.copy(slug = slug, subtopic = if (it.slug == slug) it.subtopic else "") else it }
        st.items = next; unflagReady(next)
    }

    suspend fun scrollTo(fid: String) {
        val i = st.items.indexOfFirst { it.norm.favoriteId == fid }
        if (i >= 0) listState.animateScrollToItem(HEADER_ITEMS + i)
    }
    fun jumpToBlank() {
        val inc = picked.filter { !isReady(it) }
        if (inc.isEmpty()) return
        st.missing = inc.map { it.norm.favoriteId }.toSet()
        scope.launch { scrollTo(inc.first().norm.favoriteId) }
    }

    fun insertSubset(subset: List<ImportItem>) {
        st.error = ""; st.result = null
        if (subset.isEmpty()) { st.error = "Nothing selected — tick at least one question to insert."; return }
        val blanks = subset.filter { it.slug.isEmpty() }
        val noSubs = subset.filter { it.slug.isNotEmpty() && needsSub(it.slug) && it.subtopic.isEmpty() }
        val bad = blanks + noSubs
        if (bad.isNotEmpty()) {
            st.missing = bad.map { it.norm.favoriteId }.toSet()
            st.error = if (subset.size == 1) {
                if (blanks.isNotEmpty()) "Category is required — this question has no category selected." else "Sub-topic is required — this category has sub-topics, so one must be chosen."
            } else {
                val parts = ArrayList<String>()
                if (blanks.isNotEmpty()) parts += "${blanks.size} with no category"
                if (noSubs.isNotEmpty()) parts += "${noSubs.size} with no sub-topic"
                "Incomplete — ${parts.joinToString(" and ")} of the ${subset.size} selected."
            }
            scope.launch { scrollTo(bad.first().norm.favoriteId) }
            return
        }
        scope.launch {
            st.busy = true
            try {
                val sent = subset.map { it.norm.favoriteId }.toSet()
                val rows = JSONArray()
                subset.forEach { rows.put(LivemcqAdmin.toInsertRow(it.norm, it.slug, if (it.subtopic == NO_SUB) "" else it.subtopic)) }
                val res = LivemcqAdmin.insert(m.db, rows)
                val skipped = res.optJSONArray("skipped_fids")
                st.result = "Inserted ${res.optInt("inserted")} · skipped ${res.optInt("skipped")}" +
                    (if (skipped != null && skipped.length() > 0) " (already present: ${List(skipped.length()) { skipped.optString(it) }.joinToString(", ")})" else "")
                st.missing = emptySet()
                st.items = st.items.filter { it.norm.favoriteId !in sent }
                val n = res.optInt("inserted")
                val byCat = subset.groupBy { catalog.catName(it.slug) }
                m.progress.recordExternal(ProgressRepository.ExternalChange(
                    id = "import-${System.nanoTime()}", uid = null, kind = "insert",
                    label = if (n == 1) stripTags(subset.first().norm.question).take(120) else "Imported $n question${if (n == 1) "" else "s"}",
                    text = "Imported $n question${if (n == 1) "" else "s"}" + (if (res.optInt("skipped") > 0) " · ${res.optInt("skipped")} skipped" else ""),
                    cat = byCat.entries.joinToString(", ") { "${it.key} ${it.value.size}" },
                    syncedAt = System.currentTimeMillis(), undo = null,
                ))
            } catch (e: Exception) {
                st.error = e.message ?: e.toString()
                m.progress.recordExternal(ProgressRepository.ExternalChange(
                    id = "import-${System.nanoTime()}", uid = null, kind = "insert",
                    label = "Import of ${subset.size} question${if (subset.size == 1) "" else "s"}", text = "Import failed",
                    cat = "", syncedAt = System.currentTimeMillis(), undo = null, error = st.error,
                ))
            }
            st.busy = false
        }
    }

    val hintable = items.count { val h = st.hints[it.norm.favoriteId]; it.slug.isEmpty() && h != null && h.confidence >= bulkMin }
    val subHintable = items.count {
        val sh = st.subHints[it.norm.favoriteId]?.takeIf { x -> x.first == it.slug }?.second
        needsSub(it.slug) && it.subtopic.isEmpty() && sh != null && sh.confidence >= bulkMin
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (items.isNotEmpty()) 110.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // HEADER_ITEMS items precede the question cards (keep in step with scrollTo).
            item(key = "upload") {
                if (st.fileName.isEmpty() && items.isEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveSourceCard(st, onFetch = { fetchLive() }, onCheck = { checkAccount() })
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).border(1.dp, p.outline, RoundedCornerShape(18.dp))
                            .clickable(enabled = !st.busy) { pick.launch("*/*") }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(p.primary.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.FileUpload, null, Modifier.size(20.dp), tint = p.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Or choose a livefav JSON", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("A file saved earlier; checked against the database the same way.", style = MaterialTheme.typography.bodySmall, color = p.text3)
                        }
                    }
                } else Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (st.fetched != null) Icons.Filled.CloudDownload else Icons.Filled.FileUpload, null, Modifier.size(18.dp), tint = p.primary)
                    Text(st.fileName.ifEmpty { "livefav" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (st.fetched != null && st.copySaved == null) Text(
                        "Save copy", Modifier.clip(CircleShape).border(1.dp, p.outline, CircleShape).clickable(enabled = !st.busy) { saveCopy() }.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = p.text2, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Change", Modifier.clip(CircleShape).border(1.dp, p.outline, CircleShape).clickable(enabled = !st.busy) { reset(); st.fileName = "" }.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = p.text2, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item(key = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.busy && items.isEmpty() && st.fileName.isNotEmpty()) SlateLoader(Modifier.fillMaxWidth().padding(vertical = 24.dp), label = "Reading the file…", size = 32.dp)
                    if (st.error.isNotEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bad.copy(alpha = .12f)).padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.ErrorOutline, null, Modifier.size(15.dp), tint = p.bad)
                        Text(st.error, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = p.bad)
                    }
                    st.result?.let { r ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.ok.copy(alpha = .12f)).padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = p.ok)
                            Text(r, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = p.ok)
                        }
                    }
                    st.copySaved?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = p.text3) }
                    st.summary?.let { sm ->
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(if (st.fetched != null) "${sm.total} fetched" else "${sm.total} in file", p.text2, p.elevated)
                            Chip("${sm.fresh} new", p.ok, p.ok.copy(alpha = .14f))
                            if (sm.dupInDb > 0) Chip("${sm.dupInDb} already in DB", p.text3, p.elevated)
                            if (sm.dupInFile > 0) Chip("${sm.dupInFile} dup in ${if (st.fetched != null) "batch" else "file"}", p.warn, p.warn.copy(alpha = .14f))
                            if (sm.badFid > 0) Chip("${sm.badFid} no favorite_id", p.bad, p.bad.copy(alpha = .14f))
                        }
                    }
                }
            }
            item(key = "toolbar") {
                if (items.isNotEmpty()) FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val all = picked.size == items.size
                    GhostButton(if (all) "Clear" else "Select all", Icons.Filled.Check) { st.items = st.items.map { it.copy(picked = !all) } }
                    if (hintable > 0) GhostButton("Suggest $hintable", Icons.Filled.AutoFixHigh) { applyAllHints() }
                    if (subHintable > 0) GhostButton("Sub-topics $subHintable", Icons.Filled.Sell) { applyAllSubHints() }
                    if (picked.isNotEmpty()) ChipSelect(
                        "Category for ${picked.size}", bulkSlug, catalog.topics, { applyBulk(it) }, p.text2, p.surface,
                    )
                }
            }
            items(items, key = { it.norm.favoriteId }) { it ->
                val fid = it.norm.favoriteId
                val h = st.hints[fid]
                val sh = st.subHints[fid]?.takeIf { x -> x.first == it.slug.ifEmpty { h?.slug ?: "" } }?.second
                ImportCard(
                    item = it, index = items.indexOf(it) + 1, hint = h, subHint = sh, catalog = catalog, m = m,
                    subRequired = needsSub(it.slug), flagged = fid in st.missing, busy = st.busy,
                    onSlug = { s -> setSlug(fid, s) }, onSub = { s -> setSub(fid, s) },
                    onApplyHint = { s, sub -> applyHint(fid, s, sub) },
                    onToggle = { patch(fid) { x -> x.copy(picked = !x.picked) } },
                    onInsertOne = { insertSubset(listOf(it)) },
                    onSubAdded = onSubAdded,
                )
            }
        }

        if (items.isNotEmpty()) Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(p.surface).border(1.dp, p.outline).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("${picked.size} of ${items.size} selected", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (pickedNoCat.isNotEmpty() || pickedNoSub.isNotEmpty()) Text(
                    listOfNotNull(
                        pickedNoCat.size.takeIf { it > 0 }?.let { "$it need a category" },
                        pickedNoSub.size.takeIf { it > 0 }?.let { "$it need a sub-topic" },
                    ).joinToString(" · ") + " — show me",
                    Modifier.clickable { jumpToBlank() }, style = MaterialTheme.typography.labelSmall, color = p.warn,
                ) else Text(if (picked.isNotEmpty()) "all selected are ready" else "nothing selected", style = MaterialTheme.typography.labelSmall, color = p.text3)
            }
            if (canPartial) ModalButton("Insert all ${picked.size}", false, enabled = !st.busy) { insertSubset(picked) }
            ModalButton(
                if (canPartial) "Insert ${pickedReady.size} ready" else "Insert ${picked.size}", true, tint = p.primary,
                enabled = picked.isNotEmpty() && !st.busy, busy = st.busy, icon = Icons.Filled.Check,
            ) { insertSubset(if (canPartial) pickedReady else picked) }
        }
    }
}

/** The lazy list's header items (upload, status, toolbar) before the first question card. */
private const val HEADER_ITEMS = 3

@Composable
private fun GhostButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.outline, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = p.text2)
        Text(text, style = MaterialTheme.typography.labelMedium, color = p.text2, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportCard(
    item: ImportItem, index: Int, hint: LivemcqClassifier.Suggestion?, subHint: LivemcqClassifier.Suggestion?, catalog: AdminCatalog,
    m: ModuleServices, subRequired: Boolean, flagged: Boolean, busy: Boolean,
    onSlug: (String) -> Unit, onSub: (String) -> Unit, onApplyHint: (String, String) -> Unit,
    onToggle: () -> Unit, onInsertOne: () -> Unit, onSubAdded: (String, Subtopic) -> Unit,
) {
    val p = LocalPalette.current
    val n = item.norm
    var open by remember(n.favoriteId) { mutableStateOf(false) }
    var showExp by remember(n.favoriteId) { mutableStateOf(false) }
    val expanded = open || flagged
    val bulkMin = LivemcqClassifier.BULK_APPLY_MIN
    val hintTaken = hint != null && item.slug == hint.slug
    // The sub-topic that rides along with the category suggestion — only while no category is picked.
    val pairedSub = if (item.slug.isEmpty() && subHint != null) subHint else null
    val edge = if (flagged) p.warn else if (item.picked) p.primary.copy(alpha = .55f) else p.outline
    val hasWarning = !n.hasKey || n.gapWarning || n.answerOutOfRange

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).border(if (flagged) 2.dp else 1.dp, edge, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Collapsed header: tick · #n · the question (two lines) · chevron.
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(if (item.picked) p.primary else p.elevated)
                    .border(1.5.dp, if (item.picked) p.primary else p.outline, RoundedCornerShape(8.dp)).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) { if (item.picked) Icon(Icons.Filled.Check, "Selected", Modifier.size(16.dp), tint = p.onPrimary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${stripTags(n.question).ifEmpty { "(image-only)" }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    maxLines = if (expanded) 6 else 2, overflow = TextOverflow.Ellipsis,
                )
                Text("#$index · fav ${n.favoriteId}", style = MaterialTheme.typography.labelSmall, color = p.text3)
            }
            if (hasWarning) Icon(Icons.Filled.Warning, "Needs a look", Modifier.size(18.dp), tint = p.warn)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (expanded) "Collapse" else "Expand", Modifier.size(22.dp), tint = p.text3)
        }

        // One compact control row: category, sub-topic (when the category has them) and the single suggestion.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            ChipSelect(
                if (item.slug.isEmpty()) "Category" else catalog.catName(item.slug), item.slug, catalog.topics, onSlug,
                if (item.slug.isEmpty()) (if (flagged) p.warn else p.text3) else p.text2,
                if (item.slug.isEmpty()) (if (flagged) p.warn.copy(alpha = .14f) else p.elevated) else p.elevated,
            )
            if (item.slug.isNotEmpty() && subRequired) {
                val sel = item.subtopic.takeIf { it.isNotEmpty() && it != NO_SUB }
                ChipSelect(
                    sel?.let { catalog.subName(item.slug, it) } ?: if (item.subtopic == NO_SUB) "no sub-topic" else "Sub-topic",
                    item.subtopic, catalog.subList(item.slug).map { it.slug to it.name } + (NO_SUB to "কোনো sub-topic নয়"), onSub,
                    if (sel != null) p.warn else if (flagged) p.warn else p.text3,
                    if (sel != null || flagged) p.warn.copy(alpha = .14f) else p.elevated, icon = Icons.Filled.Sell,
                )
            }
            if (hint != null && !hintTaken) SuggestionChip(
                hint, nameOf = { catalog.catName(it) }, label = null,
                subName = pairedSub?.let { catalog.subName(hint.slug, it.slug) }, subWeak = pairedSub != null && pairedSub.confidence < bulkMin,
            ) { onApplyHint(hint.slug, pairedSub?.slug ?: "") }
            if (item.slug.isNotEmpty() && subHint != null && item.subtopic != subHint.slug) SuggestionChip(
                subHint, nameOf = { catalog.subName(item.slug, it) }, label = "Sub-topic", subName = null, subWeak = false,
            ) { onSub(subHint.slug) }
        }
        if (flagged) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Filled.ErrorOutline, null, Modifier.size(13.dp), tint = p.warn)
            Text(if (item.slug.isNotEmpty()) "Sub-topic is required." else "Category is required.", style = MaterialTheme.typography.labelMedium, color = p.warn)
        }

        if (expanded) {
            if (hasWarning) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!n.hasKey) Chip("no correct answer → null", p.warn, p.warn.copy(alpha = .14f), icon = Icons.Filled.Warning)
                if (n.gapWarning) Chip("empty option before a filled one", p.bad, p.bad.copy(alpha = .14f), icon = Icons.Filled.Warning)
                if (n.answerOutOfRange) Chip("answer index out of range", p.bad, p.bad.copy(alpha = .14f), icon = Icons.Filled.Warning)
            }
            RichText(n.question, style = MaterialTheme.typography.bodyLarge)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                n.options.forEachIndexed { i, o ->
                    val correct = n.hasKey && i == n.answer - 1
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (correct) p.ok.copy(alpha = .12f) else p.elevated)
                            .border(1.dp, if (correct) p.ok.copy(alpha = .5f) else p.outline, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (correct) p.ok else p.surface), contentAlignment = Alignment.Center) {
                            Text(LivemcqAdmin.LETTERS.getOrElse(i) { "?" }.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (correct) Color.White else p.text2)
                        }
                        RichText(o, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (correct) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = p.ok)
                    }
                }
            }
            if (n.explanation.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ব্যাখ্যা ${if (showExp) "▴" else "▾"}", Modifier.clickable { showExp = !showExp }, style = MaterialTheme.typography.labelLarge, color = p.primary, fontWeight = FontWeight.SemiBold)
                if (showExp) RichText(n.explanation, style = MaterialTheme.typography.bodyMedium, color = p.text2)
            }
            if (item.slug.isNotEmpty() && subRequired) SubtopicPicker(
                item.slug, catalog, item.subtopic, onSub, Modifier.fillMaxWidth(), m = m, required = true,
                invalid = flagged && item.subtopic.isEmpty(), onAdded = { s -> onSubAdded(item.slug, s) },
            )
            if (hint != null) Text(
                "closest stored question: “${stripTags(hint.nearestQuestion).take(90)}”" +
                    (if (hint.nearestSource != "livemcq") " (from the ${hint.nearestSource} module)" else ""),
                style = MaterialTheme.typography.labelSmall, color = p.text3, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ModalButton("Insert this one", true, tint = p.primary, enabled = !busy, icon = Icons.Filled.Check, onClick = onInsertOne)
            }
        }
    }
}

/** A suggestion from the local tf-idf/kNN index — never auto-applied; one small chip, tap to apply. */
@Composable
private fun SuggestionChip(
    hint: LivemcqClassifier.Suggestion, nameOf: (String) -> String, label: String?, subName: String?, subWeak: Boolean, onApply: () -> Unit,
) {
    val p = LocalPalette.current
    val color = when (hint.tier) { "strong" -> p.ok; "likely" -> p.primary; else -> p.warn }
    val pct = Math.round(hint.confidence * 100).toInt()
    Chip(
        buildString {
            if (label != null) append("$label · ")
            append("${TIER_LABEL[hint.tier]} ${nameOf(hint.slug)}")
            if (subName != null) append(" › $subName")
            append(" · $pct%")
            if (subName != null && subWeak) append(" ?")
        },
        color, color.copy(alpha = .14f), icon = Icons.Filled.AutoFixHigh, onClick = onApply,
    )
}

/**
 * The LiveMCQ source: favourites straight off livemcq.com (formerly Magpie's export module). "New" reads pages
 * newest-first and stops at the highest favorite_id already stored, so the database itself is the baseline.
 */
@Composable
private fun LiveSourceCard(st: ImportState, onFetch: () -> Unit, onCheck: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).border(1.dp, p.outline, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(p.primary.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CloudDownload, null, Modifier.size(22.dp), tint = p.primary)
            }
            Column(Modifier.weight(1f)) {
                Text("Fetch from LiveMCQ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (st.liveSignedIn) "Signed in · your favourites, checked against the database" else "Sign in once; the session stays on this phone",
                    style = MaterialTheme.typography.bodySmall, color = if (st.liveSignedIn) p.ok else p.text3,
                )
            }
            if (st.liveSignedIn) Text(
                "Sign out", Modifier.clip(CircleShape).clickable(enabled = !st.busy) { LivemcqFavorites.signOut { st.liveSignedIn = false } }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = p.text3, fontWeight = FontWeight.SemiBold,
            )
        }
        if (!st.liveSignedIn) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ModalButton("Sign in to LiveMCQ", true, tint = p.primary, icon = Icons.AutoMirrored.Filled.Login) { st.login = true }
            }
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScopeOption("New since last import", "Above the newest stored", st.newOnly, !st.busy, Modifier.weight(1f)) { st.newOnly = true }
            ScopeOption("Newest by count", "Stored or not", !st.newOnly, !st.busy, Modifier.weight(1f)) { st.newOnly = false }
        }
        if (!st.newOnly) OutlinedTextField(
            value = st.count, onValueChange = { v -> st.count = v.filter { it.isDigit() }.take(4) },
            label = { Text("How many") }, singleLine = true, enabled = !st.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        )
        st.progress?.let { pr ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { if (pr.pages > 0) pr.page.toFloat() / pr.pages else 0f }, Modifier.fillMaxWidth(),
                    color = p.primary, trackColor = p.primary.copy(alpha = .16f),
                )
                Text("Page ${pr.page} of ${pr.pages} · ${pr.questions} question${if (pr.questions == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = p.text3)
            }
        }
        st.peek?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = p.text2) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Check account", Modifier.clip(CircleShape).clickable(enabled = !st.busy, onClick = onCheck).padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, color = p.primary, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            ModalButton(if (st.busy) "Fetching…" else "Fetch favourites", true, tint = p.primary, enabled = !st.busy, busy = st.busy, icon = Icons.Filled.CloudDownload, onClick = onFetch)
        }
    }
}

@Composable
private fun ScopeOption(title: String, detail: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) p.primary.copy(alpha = .10f) else p.elevated)
            .border(if (selected) 2.dp else 1.dp, if (selected) p.primary else p.outline, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (selected) p.primary else p.text2)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = p.text3)
    }
}
