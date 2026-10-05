package com.syed.slate.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syed.slate.ModuleServices
import com.syed.slate.backend.ModuleId
import com.syed.slate.backend.Postgrest
import com.syed.slate.content.ContentState
import com.syed.slate.content.LivemcqClassifier
import com.syed.slate.core.Uid
import com.syed.slate.ui.SlateViewModel
import com.syed.slate.ui.component.BarTitle
import com.syed.slate.ui.component.SlateTopBar
import com.syed.slate.ui.component.SlateLoader
import com.syed.slate.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.syed.slate.content.Subtopic
import com.syed.slate.progress.Flag
import com.syed.slate.progress.ProgressRepository
import com.syed.slate.ui.quiz.StudyCard

/**
 * LiveMCQ Admin — the port of the web's /admin for the owner account: import from a livefav JSON file,
 * classify by hand or in bulk, review the last imports, manage/move/delete. Real security is server-side:
 * the admin_livemcq_* RPCs are SECURITY DEFINER and reject anyone whose auth.uid() ≠ the owner, so this
 * screen only gates the UI on the same id.
 */
object LivemcqAdmin {
    const val OWNER_UID = "803521e1-00c9-4b8a-ab13-f6e6d126da2b"
    fun isOwner(userId: String?) = userId == OWNER_UID

    /** One stored LiveMCQ question, as the Manage / Last-import views see it. */
    class Row(
        val id: String,
        val favoriteId: String?,
        val question: String,
        val correctAnswerText: String?,
        val correctAnswer: String? = null,
        val subtopic: String?,
        val deleted: Boolean,
        val createdAt: Long,
        val slug: String,
        val catName: String,
    )

    /** Everything under `questions` joined to a livemcq category; newest first once in hand. */
    suspend fun fetchRows(db: Postgrest): List<Row> = withContext(Dispatchers.IO) {
        db.selectAll(
            "questions",
            "select=id,question,correct_answer,correct_answer_text,extra,deleted_at,created_at,categories!inner(slug,name,module)&categories.module=eq.livemcq&order=id",
        ).map { rowOf(it) }.sortedWith(compareByDescending<Row> { it.createdAt }.thenByDescending { it.favoriteId?.toIntOrNull() ?: -1 })
    }

    /** The one row behind a uid — the per-card "Topic" edit starts here. */
    suspend fun fetchByUid(db: Postgrest, uid: String): Row? = withContext(Dispatchers.IO) {
        db.selectAll(
            "questions",
            "select=id,question,correct_answer,correct_answer_text,extra,deleted_at,created_at,categories!inner(slug,name,module)&uid=eq.$uid&categories.module=eq.livemcq",
        ).firstOrNull()?.let { rowOf(it) }
    }

    private fun rowOf(r: JSONObject): Row {
        val extra = r.optJSONObject("extra")
        return Row(
            id = r.getString("id"),
            favoriteId = extra?.optString("favorite_id")?.takeIf { it.isNotEmpty() },
            question = r.optString("question"),
            correctAnswerText = r.optString("correct_answer_text").ifEmpty { null },
            correctAnswer = if (r.isNull("correct_answer")) null else r.optString("correct_answer").ifEmpty { null },
            subtopic = extra?.optString("subtopic")?.takeIf { it.isNotEmpty() },
            deleted = !r.isNull("deleted_at"),
            createdAt = runCatching { Instant.parse(r.optString("created_at")).toEpochMilli() }.getOrDefault(0L),
            slug = r.getJSONObject("categories").getString("slug"),
            catName = r.getJSONObject("categories").getString("name"),
        )
    }

    /** Every favorite_id already stored, INCLUDING recycle-binned rows, so an import never duplicates. */
    suspend fun fetchExistingFavoriteIds(db: Postgrest): Set<String> = withContext(Dispatchers.IO) {
        db.selectAll("questions", "select=extra,categories!inner(module)&categories.module=eq.livemcq&order=id")
            .mapNotNull { it.optJSONObject("extra")?.optString("favorite_id")?.takeIf { f -> f.isNotEmpty() } }.toSet()
    }

    // ── Import: the livefav JSON normalizer, as the web's extractRawItems / normalizeItem ────

    class NormItem(
        val favoriteId: String, val question: String, val explanation: String, val answer: Int,
        val options: List<String>, val gapWarning: Boolean, val answerOutOfRange: Boolean, val hasKey: Boolean,
    )

    fun extractRawItems(parsed: Any?): List<Any?> = when {
        parsed is JSONArray -> List(parsed.length()) { parsed.opt(it) }
        parsed is JSONObject && parsed.optJSONArray("question_list") != null ->
            parsed.optJSONArray("question_list")!!.let { a -> List(a.length()) { a.opt(it) } }
        parsed is JSONObject -> listOf(parsed)
        else -> emptyList()
    }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

    fun normalizeItem(raw: JSONObject): NormItem {
        val favoriteId = sequenceOf(raw.str("favorite_id"), raw.str("favoriteId")).firstOrNull { it.isNotEmpty() }?.trim() ?: ""
        val question = raw.str("question")
        val explanation = sequenceOf(raw.str("explanation"), raw.str("exp")).firstOrNull { it.isNotEmpty() } ?: ""
        val answer = raw.optInt("answer", 0)
        val options = (raw.optJSONArray("options")?.let { a -> List(a.length()) { if (a.isNull(it)) "" else a.optString(it) } }
            ?: listOf("option1", "option2", "option3", "option4", "option5").map { raw.str(it) }).toMutableList()
        while (options.isNotEmpty() && options.last().isBlank()) options.removeAt(options.lastIndex)
        val hasKey = answer > 0 && answer <= options.size
        return NormItem(favoriteId, question, explanation, answer, options, options.any { it.isBlank() }, answer > 0 && answer > options.size, hasKey)
    }

    val LETTERS = listOf("a", "b", "c", "d", "e")

    /** The DB insert row; uid matches what the browser derives (`qid.js`), so flags stay aligned. */
    fun toInsertRow(item: NormItem, slug: String, subtopic: String?): JSONObject {
        val opts = JSONObject()
        item.options.forEachIndexed { i, o -> if (i < LETTERS.size) opts.put(LETTERS[i], o) }
        return JSONObject()
            .put("favorite_id", item.favoriteId)
            .put("slug", slug)
            .put("uid", Uid.general(item.question) ?: JSONObject.NULL)
            .put("question", item.question)
            .put("options", if (opts.length() > 0) opts else JSONObject.NULL)
            .put("correct_answer", if (item.hasKey) LETTERS[item.answer - 1] else JSONObject.NULL)
            .put("correct_answer_text", if (item.hasKey) item.options[item.answer - 1] else JSONObject.NULL)
            .put("explanation", item.explanation.ifEmpty { JSONObject.NULL })
            .put("subtopic", subtopic?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
    }

    // ── The admin RPCs, same contracts as livemcqAdmin.js ────────────────────────────────────

    suspend fun insert(db: Postgrest, rows: JSONArray): JSONObject =
        JSONObject(db.rpc("admin_livemcq_insert", JSONObject().put("rows", rows)))

    suspend fun deleteFavoriteIds(db: Postgrest, fids: List<String>): JSONObject =
        JSONObject(db.rpc("admin_livemcq_delete", JSONObject().put("fids", JSONArray(fids))))

    suspend fun setCategory(db: Postgrest, fids: List<String>, slug: String): JSONObject =
        JSONObject(db.rpc("admin_livemcq_set_category", JSONObject().put("fids", JSONArray(fids)).put("new_slug", slug)))

    suspend fun setSubtopic(db: Postgrest, fids: List<String>, subtopic: String?): JSONObject =
        JSONObject(db.rpc("admin_livemcq_set_subtopic", JSONObject().put("fids", JSONArray(fids)).put("new_subtopic", subtopic ?: JSONObject.NULL)))

    suspend fun addSubtopic(db: Postgrest, catSlug: String, name: String): JSONObject =
        JSONObject(db.rpc("admin_livemcq_add_subtopic", JSONObject().put("cat_slug", catSlug).put("sub_name", name)))
}


/** One import: rows inserted within 10 minutes of each other (the web's groupImports), newest first. */
class ImportBatch(val key: Long, val at: Long, val ids: Set<String>) { val count get() = ids.size }

private const val IMPORT_GAP_MS = 10 * 60 * 1000L

fun groupImports(rows: List<LivemcqAdmin.Row>): List<ImportBatch> {
    val dated = rows.filter { it.createdAt > 0 }.sortedBy { it.createdAt }
    val out = mutableListOf<Triple<Long, MutableSet<String>, LongArray>>() // key, ids, [last]
    for (r in dated) {
        val cur = out.lastOrNull()
        if (cur == null || r.createdAt - cur.third[0] > IMPORT_GAP_MS) out.add(Triple(r.createdAt, mutableSetOf(r.id), longArrayOf(r.createdAt)))
        else { cur.second.add(r.id); cur.third[0] = r.createdAt }
    }
    return out.reversed().map { ImportBatch(it.first, it.first, it.second) }
}

private val PAGE_SIZE = 50
private val STUDY_PAGE_SIZE = 20

private fun importWhen(ms: Long): String =
    java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.ENGLISH).format(java.util.Date(ms))

/** Shared by the three tabs: the livemcq categories + sub-topic lists, as the admin sees them. */
@Stable
internal class AdminCatalog(
    val topics: List<Pair<String, String>>, // slug to name, in the app's order
    private val subs: (String) -> List<Subtopic>,
) {
    fun catName(slug: String) = topics.firstOrNull { it.first == slug }?.second ?: slug
    fun subList(slug: String) = subs(slug)
    fun subName(slug: String, sub: String) = subs(slug).firstOrNull { it.slug == sub }?.name ?: sub
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(vm: SlateViewModel, onBack: () -> Unit) {
    val m = vm.module(ModuleId.GENERAL)
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("import") } // import · recent · manage
    var rows by remember { mutableStateOf<List<LivemcqAdmin.Row>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var dataVersion by remember { mutableIntStateOf(0) }
    var visited by remember { mutableStateOf(setOf("import")) }
    var subExtras by remember { mutableStateOf(mapOf<String, List<Subtopic>>()) }
    val importState = remember { ImportState() }

    val session by m.auth.session.collectAsState()
    val owner = LivemcqAdmin.isOwner(session?.userId)
    val contentState by m.content.state.collectAsState()
    val content = (contentState as? ContentState.Ready)?.content
    val catalog = remember(content, subExtras) {
        val topics = content?.groups?.firstOrNull { it.key == "livemcq" }?.topics.orEmpty().map { it.slug to it.name }
        AdminCatalog(topics) { slug ->
            val base = content?.subtopicsFor(slug).orEmpty()
            base + subExtras[slug].orEmpty().filter { e -> base.none { it.slug == e.slug } }
        }
    }

    // Rows load the first time Manage / Last import is opened, and again when Import writes something.
    var fetchedVersion by remember { mutableIntStateOf(-1) }
    LaunchedEffect(owner, dataVersion, tab != "import") {
        if (!owner || tab == "import" || (rows != null && fetchedVersion == dataVersion)) return@LaunchedEffect
        try { rows = LivemcqAdmin.fetchRows(m.db); error = null; fetchedVersion = dataVersion } catch (e: Exception) { error = e.message ?: "Couldn't load" }
    }
    fun go(next: String) { tab = next; visited = visited + next }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SlateTopBar(
                title = { BarTitle("LiveMCQ Admin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        if (!owner) {
            Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (session == null) "Sign in required" else "Not authorized", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (session == null) "Sign in with the owner account to manage LiveMCQ content." else "This area is restricted to the owner account.",
                    style = MaterialTheme.typography.bodyMedium, color = p.text3,
                )
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                AdminTabBar(
                    listOf(
                        Triple("import", Icons.Filled.FileUpload, "Import & classify"),
                        Triple("recent", Icons.Filled.History, "Last import"),
                        Triple("manage", Icons.Filled.ManageSearch, "Manage & delete"),
                    ),
                    tab,
                ) { go(it) }
            }
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    "import" -> ImportPanel(m, importState, catalog,
                        onSubAdded = { cat, sub -> subExtras = subExtras + (cat to (subExtras[cat].orEmpty().filter { it.slug != sub.slug } + sub)) },
                        onInserted = { dataVersion++; scope.launch { m.content.refresh() } })
                    else -> ManagePanel(m, rows, error, { rows = it }, catalog, importsOnly = tab == "recent", dataVersion = dataVersion,
                        onSubAdded = { cat, sub -> subExtras = subExtras + (cat to (subExtras[cat].orEmpty().filter { it.slug != sub.slug } + sub)) })
                }
            }
        }
    }
}

/** Manage & delete (every row) and Last import (one import, as study cards) share this panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagePanel(
    m: ModuleServices, rowsIn: List<LivemcqAdmin.Row>?, errorIn: String?, setRows: (List<LivemcqAdmin.Row>) -> Unit,
    catalog: AdminCatalog, importsOnly: Boolean, dataVersion: Int, onSubAdded: (String, Subtopic) -> Unit,
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var q by rememberSaveable(importsOnly) { mutableStateOf("") }
    var cat by rememberSaveable(importsOnly) { mutableStateOf("") }
    var subFilter by rememberSaveable(importsOnly) { mutableStateOf("") }
    var page by rememberSaveable(importsOnly) { mutableIntStateOf(0) }
    var batchKey by rememberSaveable { mutableStateOf(0L) } // 0 = the newest
    var confirm by remember { mutableStateOf<LivemcqAdmin.Row?>(null) }
    var moving by remember { mutableStateOf<LivemcqAdmin.Row?>(null) }
    var subMoving by remember { mutableStateOf<LivemcqAdmin.Row?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var moveBusy by remember { mutableStateOf(false) }
    var subBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val flags by m.progress.flags.collectAsState()
    val contentState by m.content.state.collectAsState()

    val rows = rowsIn
    val rowsNow = rememberUpdatedState(rowsIn)
    if (errorIn != null) { Text(errorIn, Modifier.padding(16.dp), color = p.bad); return }
    if (rows == null) {
        SlateLoader(Modifier.fillMaxWidth().padding(top = 80.dp), label = "Loading questions…")
        return
    }

    val imports = remember(rows, importsOnly) { if (importsOnly) groupImports(rows) else emptyList() }
    val active = if (importsOnly) imports.firstOrNull { it.key == batchKey } ?: imports.firstOrNull() else null
    if (importsOnly && active == null) { Text("No imports yet.", Modifier.padding(16.dp), color = p.text3); return }

    val importCats = remember(active, rows) {
        if (active == null) null else {
            val n = HashMap<String, Int>(); for (r in rows) if (r.id in active.ids) n[r.slug] = (n[r.slug] ?: 0) + 1
            catalog.topics.filter { n[it.first] != null }.map { it.first to "${it.second} (${n[it.first]})" }
        }
    }

    // Last import reads as study cards, so it needs each question whole; the app's LiveMCQ cache has them by row id.
    var triedRefresh by remember(active?.key, dataVersion) { mutableStateOf(false) }
    val itemById = remember(contentState, importsOnly) {
        if (!importsOnly) emptyMap() else {
            val c = (contentState as? ContentState.Ready)?.content
            c?.allItems()?.filter { it.group == "livemcq" }?.associateBy { it.id }.orEmpty()
        }
    }
    LaunchedEffect(active?.key, itemById.size, dataVersion) {
        if (active != null && !triedRefresh && active.ids.any { it !in itemById }) { triedRefresh = true; m.content.refresh() }
    }

    val filtered = remember(rows, q, cat, subFilter, active) {
        val needle = q.trim().lowercase()
        rows.filter { r ->
            if (active != null && r.id !in active.ids) return@filter false
            if (cat.isNotEmpty() && r.slug != cat) return@filter false
            if (cat.isNotEmpty() && subFilter.isNotEmpty() && (if (subFilter == NO_SUB) r.subtopic != null else r.subtopic != subFilter)) return@filter false
            if (needle.isEmpty()) true else (r.favoriteId?.contains(needle) == true) || stripTags(r.question).lowercase().contains(needle)
        }
    }
    val pageSize = if (importsOnly) STUDY_PAGE_SIZE else PAGE_SIZE
    val pageCount = maxOf(1, (filtered.size + pageSize - 1) / pageSize)
    val curPage = minOf(page, pageCount - 1)
    val start = curPage * pageSize
    val shown = filtered.drop(start).take(pageSize)
    val total = active?.count ?: rows.size

    fun record(row: LivemcqAdmin.Row, kind: String, text: String, toCat: String, error: String? = null, undo: (suspend () -> Unit)? = null) {
        m.progress.recordExternal(ProgressRepository.ExternalChange(
            id = "${row.id}-${System.nanoTime()}", uid = null, kind = kind, label = stripTags(row.question).take(120).ifEmpty { "(image-only)" },
            text = text, cat = toCat, syncedAt = System.currentTimeMillis(), undo = undo, error = error,
        ))
    }

    // Category change only — the RPC writes questions.category_id and renumbers the two categories; the sub-topic is cleared.
    suspend fun moveTo(row: LivemcqAdmin.Row, slug: String, restoreSub: String? = null) {
        val fid = row.favoriteId ?: return
        val fromSlug = row.slug; val fromSub = row.subtopic
        LivemcqAdmin.setCategory(m.db, listOf(fid), slug)
        if (restoreSub != null) LivemcqAdmin.setSubtopic(m.db, listOf(fid), restoreSub)
        setRows((rowsNow.value ?: rows).map { r -> if (r.id == row.id) LivemcqAdmin.Row(r.id, r.favoriteId, r.question, r.correctAnswerText, r.correctAnswer, restoreSub, r.deleted, r.createdAt, slug, catalog.catName(slug)) else r })
        notice = "Moved fav $fid → ${catalog.catName(slug)}"
        val cur = LivemcqAdmin.Row(row.id, row.favoriteId, row.question, row.correctAnswerText, row.correctAnswer, restoreSub, row.deleted, row.createdAt, slug, catalog.catName(slug))
        record(row, "move", "Topic: ${catalog.catName(fromSlug)} → ${catalog.catName(slug)}", catalog.catName(slug)) {
            moveTo(cur, fromSlug, fromSub)
        }
        scope.launch { m.content.refresh() }
    }

    suspend fun subTo(row: LivemcqAdmin.Row, sub: String?) {
        val fid = row.favoriteId ?: return
        if ((sub ?: "") == (row.subtopic ?: "")) return
        LivemcqAdmin.setSubtopic(m.db, listOf(fid), sub)
        setRows((rowsNow.value ?: rows).map { r -> if (r.id == row.id) LivemcqAdmin.Row(r.id, r.favoriteId, r.question, r.correctAnswerText, r.correctAnswer, sub, r.deleted, r.createdAt, r.slug, r.catName) else r })
        notice = if (sub != null) "fav $fid → ${catalog.subName(row.slug, sub)}" else "fav $fid: sub-topic removed"
        val cur = LivemcqAdmin.Row(row.id, row.favoriteId, row.question, row.correctAnswerText, row.correctAnswer, sub, row.deleted, row.createdAt, row.slug, row.catName)
        val from = row.subtopic
        record(row, "subtopic", "Sub-topic: ${from?.let { catalog.subName(row.slug, it) } ?: "none"} → ${sub?.let { catalog.subName(row.slug, it) } ?: "none"}", row.catName) {
            subTo(cur, from)
        }
        scope.launch { m.content.refresh() }
    }

    val studyList = importsOnly && itemById.isNotEmpty()
    val onMove: (LivemcqAdmin.Row) -> Unit = { moving = it }
    val onSub: (LivemcqAdmin.Row) -> Unit = { subMoving = it }
    val onDel: (LivemcqAdmin.Row) -> Unit = { confirm = it }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (active != null) item(key = "import-bar") {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${if (active === imports.first()) "Last import" else "Import"} · ${importWhen(active.at)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val nc = importCats?.size ?: 0
                Text("${active.count} question${if (active.count == 1) "" else "s"} across $nc categor${if (nc == 1) "y" else "ies"}", style = MaterialTheme.typography.bodyMedium, color = p.text3)
                if (imports.size > 1) StyledSelect(
                    active.key.toString(), imports.take(30).mapIndexed { i, b -> b.key.toString() to "${if (i == 0) "Latest · " else ""}${importWhen(b.at)} · ${b.count}" },
                    { batchKey = it.toLong(); cat = ""; subFilter = ""; page = 0 }, Modifier.wrapContentWidth(),
                )
            }
        }
        item(key = "search") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(p.elevated).border(1.dp, p.outline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, Modifier.size(16.dp), tint = p.text3)
                    androidx.compose.foundation.text.BasicTextField(
                        q, { q = it; page = 0 }, Modifier.weight(1f).padding(start = 8.dp), singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = p.text),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(p.primary),
                        decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search text or favorite_id…", style = MaterialTheme.typography.bodyMedium, color = p.text3, maxLines = 1); inner() } },
                    )
                }
                StyledSelect(cat, importCats ?: catalog.topics, { cat = it; subFilter = ""; page = 0 }, Modifier.weight(1f), allLabel = "All categories")
            }
        }
        if (cat.isNotEmpty() && catalog.subList(cat).isNotEmpty()) item(key = "subfilter") {
            StyledSelect(subFilter, catalog.subList(cat).map { it.slug to it.name } + (NO_SUB to "No sub-topic"), { subFilter = it; page = 0 }, Modifier.fillMaxWidth(), allLabel = "All sub-topics")
        }
        if (notice.isNotEmpty()) item(key = "notice") {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.ok.copy(alpha = .12f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, Modifier.size(15.dp), tint = p.ok)
                Text(notice, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = p.ok)
                Icon(Icons.Filled.Close, "Dismiss", Modifier.size(16.dp).clickable { notice = "" }, tint = p.ok)
            }
        }
        if (err.isNotEmpty()) item(key = "err") {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.bad.copy(alpha = .12f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, Modifier.size(15.dp), tint = p.bad)
                Text(err, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = p.bad)
                Icon(Icons.Filled.Close, "Dismiss", Modifier.size(16.dp).clickable { err = "" }, tint = p.bad)
            }
        }
        item(key = "count") {
            val line = if (filtered.isEmpty()) AnnotatedString("No matches of $total") else buildAnnotatedString {
                append("Showing "); pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = p.text2)); append("${start + 1}–${start + shown.size}"); pop()
                append(" of ${filtered.size}"); if (filtered.size != total) append(" (filtered from $total)"); append(" · newest first")
                if (importsOnly) append(" · tap an option to reveal the answer")
            }
            Text(line, style = MaterialTheme.typography.bodyMedium, color = p.text3)
        }
        if (importsOnly && itemById.isEmpty()) item(key = "loadq") {
            SlateLoader(Modifier.fillMaxWidth().padding(vertical = 24.dp), label = "Loading questions…", size = 32.dp)
        }
        itemsIndexed(shown, key = { _, r -> r.id }) { i, r ->
            val item = if (studyList) itemById[r.id] else null
            if (item != null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaChips(r, catalog, onMove, onSub, Modifier.weight(1f))
                        SquareIconButton(Icons.Filled.DriveFileMove, p.text2, "Change category", r.favoriteId != null) { onMove(r) }
                        SquareIconButton(Icons.Filled.Delete, p.bad, "Delete question", r.favoriteId != null) { onDel(r) }
                    }
                    StudyCard(ModuleId.GENERAL, item, item.uid?.let { flags[it] } ?: Flag(), m.progress, number = start + i + 1, topicLabel = r.catName)
                }
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaChips(r, catalog, onMove, onSub, Modifier.fillMaxWidth())
                    Text(stripTags(r.question).take(220).ifEmpty { "(image-only)" }, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (r.correctAnswer != null) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(p.ok), contentAlignment = Alignment.Center) {
                                    Text(r.correctAnswer, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Text(stripTags(r.correctAnswerText).take(120).ifEmpty { "(no answer text)" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = p.ok, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        SquareIconButton(Icons.Filled.DriveFileMove, p.text2, "Change category", r.favoriteId != null, 36.dp) { onMove(r) }
                        SquareIconButton(Icons.Filled.Delete, p.bad, "Delete question", r.favoriteId != null, 36.dp) { onDel(r) }
                    }
                }
            }
        }
        item(key = "pager") { AdminPager(curPage, pageCount) { page = it; scope.launch { listState.scrollToItem(0) } } }
    }

    confirm?.let { r ->
        AdminModal(
            Icons.Filled.Warning, p.bad, "Delete this question?", deleting, { confirm = null },
            meta = listOf(r.catName to true, "fav ${r.favoriteId}" to false), snippet = stripTags(r.question).take(160),
            warn = "This permanently removes it from the database. This cannot be undone.",
        ) {
            ModalButton("Cancel", false, enabled = !deleting) { confirm = null }
            ModalButton("Delete", true, tint = p.bad, busy = deleting, icon = Icons.Filled.Delete) {
                scope.launch {
                    deleting = true
                    try {
                        LivemcqAdmin.deleteFavoriteIds(m.db, listOf(r.favoriteId ?: ""))
                        setRows((rowsNow.value ?: rows).filter { it.id != r.id }); scope.launch { m.content.refresh() }
                        record(r, "delete", "Deleted permanently", r.catName)
                    } catch (e: Exception) { err = e.message ?: "Delete failed"; record(r, "delete", "Delete failed", r.catName, error = err) }
                    deleting = false; confirm = null
                }
            }
        }
    }
    moving?.let { r ->
        var slug by remember(r.id) { mutableStateOf(r.slug) }
        val changed = slug.isNotEmpty() && slug != r.slug
        AdminModal(
            Icons.Filled.DriveFileMove, p.info, "Change category", moveBusy, { moving = null },
            meta = listOf(r.catName to true, "fav ${r.favoriteId}" to false), snippet = stripTags(r.question).take(160),
            body = { StyledSelect(slug, catalog.topics, { slug = it }, Modifier.fillMaxWidth(), placeholder = "Select category…") },
            warn = "Only the category changes. The question, options, answer, explanation and favorite_id are untouched, and Nailed / Important flags follow the question. Its sub-topic is cleared, since sub-topic lists belong to a category.",
        ) {
            ModalButton("Cancel", false, enabled = !moveBusy) { moving = null }
            ModalButton("Move", true, tint = p.info, enabled = changed, busy = moveBusy, icon = Icons.Filled.DriveFileMove) {
                scope.launch {
                    moveBusy = true; err = ""
                    try { moveTo(r, slug) } catch (e: Exception) { err = e.message ?: "Move failed"; record(r, "move", "Move to ${catalog.catName(slug)} failed", r.catName, error = err) }
                    moveBusy = false; moving = null
                }
            }
        }
    }
    subMoving?.let { r ->
        var sub by remember(r.id) { mutableStateOf(r.subtopic ?: "") }
        val chosen = if (sub == NO_SUB) "" else sub
        val changed = chosen != (r.subtopic ?: "")
        AdminModal(
            Icons.Filled.Sell, p.warn, "Sub-topic", subBusy, { subMoving = null },
            meta = listOf(r.catName to true, "fav ${r.favoriteId}" to false), snippet = stripTags(r.question).take(160),
            body = {
                SubtopicPicker(r.slug, catalog, sub, { sub = it }, Modifier.fillMaxWidth(), m = m, onAdded = { s -> onSubAdded(r.slug, s) })
            },
            warn = "Only the sub-topic changes (leave it empty to remove it). The question, answer, category and Nailed / Important flags are untouched.",
        ) {
            ModalButton("Cancel", false, enabled = !subBusy) { subMoving = null }
            ModalButton("Save", true, tint = p.primary, enabled = changed, busy = subBusy, icon = Icons.Filled.Check) {
                scope.launch {
                    subBusy = true; err = ""
                    try { subTo(r, chosen.ifEmpty { null }) } catch (e: Exception) { err = e.message ?: "Save failed"; record(r, "subtopic", "Sub-topic change failed", r.catName, error = err) }
                    subBusy = false; subMoving = null
                }
            }
        }
    }
}

/** The category chip (tap → move), sub-topic chip (tap → sub-topic), fav id and warning chips of one row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaChips(r: LivemcqAdmin.Row, catalog: AdminCatalog, onMove: (LivemcqAdmin.Row) -> Unit, onSub: (LivemcqAdmin.Row) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(r.catName, p.text2, p.elevated, trailing = Icons.Filled.ExpandMore, onClick = if (r.favoriteId != null) ({ onMove(r) }) else null)
        if (r.subtopic != null || catalog.subList(r.slug).isNotEmpty()) Chip(
            r.subtopic?.let { catalog.subName(r.slug, it) } ?: "no sub-topic",
            if (r.subtopic != null) p.warn else p.text3, if (r.subtopic != null) p.warn.copy(alpha = .14f) else p.elevated,
            icon = Icons.Filled.Sell, onClick = if (r.favoriteId != null) ({ onSub(r) }) else null,
        )
        Chip("fav ${r.favoriteId ?: "—"}", p.text3, p.elevated)
        if (r.correctAnswer == null) Chip("no key", p.warn, p.warn.copy(alpha = .14f))
        if (r.deleted) Chip("recycle-binned", p.bad, p.bad.copy(alpha = .14f))
    }
}

/**
 * Sub-topic for one question. Lists are per category and live in the DB, so "+ নতুন sub-topic…" creates one in place
 * (owner-gated RPC) and selects it. A category with no list yet shows just an add link.
 */
@Composable
internal fun SubtopicPicker(
    categorySlug: String, catalog: AdminCatalog, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    m: ModuleServices, required: Boolean = false, invalid: Boolean = false, onAdded: (Subtopic) -> Unit,
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val list = catalog.subList(categorySlug)

    fun create() {
        val nm = name.trim(); if (nm.isEmpty() || busy) return
        scope.launch {
            busy = true; err = ""
            try {
                val out = LivemcqAdmin.addSubtopic(m.db, categorySlug, nm)
                val s = Subtopic(out.optString("slug", nm), out.optString("name", nm))
                onAdded(s); onChange(s.slug); adding = false; name = ""
            } catch (e: Exception) { err = e.message ?: "Couldn't add" }
            busy = false
        }
    }

    if (adding) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    name, { name = it }, Modifier.weight(1f), singleLine = true, enabled = !busy,
                    placeholder = { Text("নতুন sub-topic — ${catalog.catName(categorySlug)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
                ModalButton("Add", true, tint = p.primary, enabled = name.isNotBlank(), busy = busy, icon = Icons.Filled.Add) { create() }
                SquareIconButton(Icons.Filled.Close, p.text2, "Cancel", !busy) { adding = false; err = "" }
            }
            if (err.isNotEmpty()) Text(err, style = MaterialTheme.typography.bodySmall, color = p.bad)
        }
        return
    }
    if (list.isEmpty()) {
        Row(
            modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, p.outline, RoundedCornerShape(12.dp)).clickable { adding = true }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Add, null, Modifier.size(14.dp), tint = p.text2)
            Text("sub-topic যোগ করুন", style = MaterialTheme.typography.labelLarge, color = p.text2)
        }
        return
    }
    StyledSelect(
        value, list.map { it.slug to it.name } + (NO_SUB to "কোনো sub-topic নয়") + (NEW_SUB to "+ নতুন sub-topic…"),
        { v -> if (v == NEW_SUB) adding = true else onChange(v) }, modifier,
        placeholder = if (required) "Sub-topic বেছে নিন…" else "Sub-topic (ঐচ্ছিক)…", optional = !required, invalid = invalid,
    )
}

/** Category + sub-topic picker for the study card's per-card Topic edit (StudyScreen). */
@Composable
fun LivemcqClassifySheet(
    title: String, currentSlug: String, currentSub: String?, m: ModuleServices, busy: Boolean,
    onDismiss: () -> Unit, onApply: (String, String?) -> Unit,
) {
    val p = LocalPalette.current
    val content = (m.content.state.collectAsState().value as? ContentState.Ready)?.content
    val topics = remember(content) { content?.groups?.firstOrNull { it.key == "livemcq" }?.topics.orEmpty().map { it.slug to it.name } }
    var extras by remember { mutableStateOf(mapOf<String, List<Subtopic>>()) }
    val catalog = remember(content, extras) {
        AdminCatalog(topics) { slug -> content?.subtopicsFor(slug).orEmpty() + extras[slug].orEmpty() }
    }
    var slug by remember { mutableStateOf(currentSlug) }
    var sub by remember { mutableStateOf(currentSub ?: "") }
    val chosen = if (sub == NO_SUB) "" else sub
    val changed = slug != currentSlug || chosen != (currentSub ?: "")
    AdminModal(
        Icons.Filled.Sell, p.warn, title, busy, onDismiss,
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StyledSelect(slug, topics, { slug = it; if (it != currentSlug) sub = "" else sub = currentSub ?: "" }, Modifier.fillMaxWidth(), placeholder = "Select category…")
                SubtopicPicker(slug, catalog, sub, { sub = it }, Modifier.fillMaxWidth(), m = m, onAdded = { s -> extras = extras + (slug to (extras[slug].orEmpty() + s)) })
            }
        },
        warn = "Changing the category clears the sub-topic. The question, answer and Nailed / Important flags are untouched.",
    ) {
        ModalButton("Cancel", false, enabled = !busy, onClick = onDismiss)
        ModalButton("Save", true, enabled = changed, busy = busy, icon = Icons.Filled.Check) { onApply(slug, chosen.ifEmpty { null }) }
    }
}
