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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
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
import com.syed.slate.content.Subtopic
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

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            st.error = ""; st.result = null; st.summary = null; st.items = emptyList(); st.missing = emptySet(); st.hints = emptyMap(); st.subHints = emptyMap(); st.indexFor = null
            st.fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            st.busy = true
            try {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }.trim().removePrefix("﻿")
                val parsed: Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
                val raw = LivemcqAdmin.extractRawItems(parsed)
                if (raw.isEmpty()) throw IllegalStateException("No questions found in the file.")
                val existing = LivemcqAdmin.fetchExistingFavoriteIds(m.db)
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
            } catch (e: Exception) {
                st.error = e.message ?: e.toString(); st.fileName = ""
            }
            st.busy = false
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
                onInserted()
            } catch (e: Exception) { st.error = e.message ?: e.toString() }
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
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(p.primary).clickable(enabled = !st.busy) { pick.launch("*/*") }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.FileUpload, null, Modifier.size(16.dp), tint = p.onPrimary)
                        Text("Choose livefav JSON", color = p.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    if (st.fileName.isNotEmpty()) Text(st.fileName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = p.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(key = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (st.busy && items.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text("Reading…", Modifier.padding(start = 8.dp), color = p.text3)
                    }
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
                    st.summary?.let { s ->
                        Text(
                            buildString {
                                append("${s.total} in file · ${s.fresh} new")
                                if (s.dupInDb > 0) append(" · ${s.dupInDb} already in DB")
                                if (s.dupInFile > 0) append(" · ${s.dupInFile} dup in file")
                                if (s.badFid > 0) append(" · ${s.badFid} missing favorite_id")
                            },
                            style = MaterialTheme.typography.bodyMedium, color = p.text3,
                        )
                    }
                }
            }
            item(key = "toolbar") {
                if (items.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select all", Modifier.clickable { st.items = st.items.map { it.copy(picked = true) } }, style = MaterialTheme.typography.labelLarge, color = p.primary, fontWeight = FontWeight.SemiBold)
                        Text("·", color = p.text3)
                        Text("Clear", Modifier.clickable { st.items = st.items.map { it.copy(picked = false) } }, style = MaterialTheme.typography.labelLarge, color = p.primary, fontWeight = FontWeight.SemiBold)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hintable > 0) GhostButton("Apply $hintable suggestion${if (hintable == 1) "" else "s"}", Icons.Filled.AutoFixHigh) { applyAllHints() }
                        if (subHintable > 0) GhostButton("Apply $subHintable sub-topic suggestion${if (subHintable == 1) "" else "s"}", Icons.Filled.Sell) { applyAllSubHints() }
                    }
                    StyledSelect(
                        bulkSlug, catalog.topics, { applyBulk(it) }, Modifier.fillMaxWidth(),
                        placeholder = if (picked.isNotEmpty()) "Set category for ${picked.size} selected…" else "Select questions first…",
                        enabled = picked.isNotEmpty(),
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
    var showExp by remember(n.favoriteId) { mutableStateOf(false) }
    val bulkMin = LivemcqClassifier.BULK_APPLY_MIN
    val hintTaken = hint != null && item.slug == hint.slug
    // The sub-topic that rides along with the category suggestion — only while no category is picked.
    val pairedSub = if (item.slug.isEmpty() && subHint != null) subHint else null
    val edge = if (flagged) p.warn else if (item.picked) p.primary.copy(alpha = .55f) else p.outline

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).border(if (flagged) 2.dp else 1.dp, edge, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(if (item.picked) p.primary else p.elevated)
                    .border(1.5.dp, if (item.picked) p.primary else p.outline, RoundedCornerShape(7.dp)).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) { if (item.picked) Icon(Icons.Filled.Check, "Selected", Modifier.size(15.dp), tint = p.onPrimary) }
            Text("#$index", style = MaterialTheme.typography.labelLarge, color = p.text3, fontWeight = FontWeight.Bold)
            Chip("fav ${n.favoriteId}", p.text3, p.elevated)
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

        // One box, both fields: Apply settles topic and sub-topic together.
        if (hint != null && !hintTaken) SuggestionBox(
            hint, nameOf = { catalog.catName(it) }, label = null,
            subName = pairedSub?.let { catalog.subName(hint.slug, it.slug) }, subWeak = pairedSub != null && pairedSub.confidence < bulkMin,
        ) { onApplyHint(hint.slug, pairedSub?.slug ?: "") }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StyledSelect(item.slug, catalog.topics, onSlug, Modifier.weight(1f), placeholder = "Select category…", invalid = flagged)
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(p.primary.copy(alpha = if (busy) .4f else 1f)).clickable(enabled = !busy, onClick = onInsertOne).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = p.onPrimary)
                Text("Insert", color = p.onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        if (flagged) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Filled.ErrorOutline, null, Modifier.size(13.dp), tint = p.warn)
            Text(if (item.slug.isNotEmpty()) "Sub-topic is required." else "Category is required.", style = MaterialTheme.typography.labelMedium, color = p.warn)
        }
        if (item.slug.isNotEmpty() && subHint != null && item.subtopic != subHint.slug) SuggestionBox(
            subHint, nameOf = { catalog.subName(item.slug, it) }, label = "Sub-topic", subName = null, subWeak = false,
        ) { onSub(subHint.slug) }
        if (item.slug.isNotEmpty()) SubtopicPicker(
            item.slug, catalog, item.subtopic, onSub, Modifier.fillMaxWidth(), m = m, required = subRequired,
            invalid = flagged && subRequired && item.subtopic.isEmpty(), onAdded = { s -> onSubAdded(item.slug, s) },
        )
    }
}

/** A suggestion from the local tf-idf/kNN index — never auto-applied; it shows the neighbour it matched so it can be judged. */
@Composable
private fun SuggestionBox(
    hint: LivemcqClassifier.Suggestion, nameOf: (String) -> String, label: String?, subName: String?, subWeak: Boolean, onApply: () -> Unit,
) {
    val p = LocalPalette.current
    val color = when (hint.tier) { "strong" -> p.ok; "likely" -> p.primary; else -> p.warn }
    val pct = Math.round(hint.confidence * 100).toInt()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .1f)).border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (label != null) Icons.Filled.Sell else Icons.Filled.AutoFixHigh, null, Modifier.padding(top = 2.dp).size(14.dp), tint = color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildString {
                    if (label != null) append("$label · ")
                    append("${TIER_LABEL[hint.tier]} ${nameOf(hint.slug)}")
                    if (subName != null) append(" › $subName")
                    append(" · $pct% agreement")
                    if (hint.tier == "weak") append(" · low confidence")
                    if (subName != null && subWeak) append(" · sub-topic uncertain")
                },
                style = MaterialTheme.typography.labelLarge, color = p.text2,
            )
            Text("closest stored question: “${stripTags(hint.nearestQuestion).take(90)}”", style = MaterialTheme.typography.labelSmall, color = p.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hint.nearestSource != "livemcq") Text("matched against the ${hint.nearestSource} module, not LiveMCQ", style = MaterialTheme.typography.labelSmall, color = p.text3)
        }
        Text(
            "Apply", Modifier.clip(CircleShape).background(color).clickable(onClick = onApply).padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold,
        )
    }
}
