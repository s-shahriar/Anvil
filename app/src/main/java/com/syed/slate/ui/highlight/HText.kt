package com.syed.slate.ui.highlight

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.syed.slate.highlight.Anchor
import com.syed.slate.highlight.Anchored
import com.syed.slate.highlight.HIGHLIGHT_COLORS
import com.syed.slate.highlight.HighlightRepository
import com.syed.slate.highlight.HtmlAlign
import com.syed.slate.ui.rich.Block
import com.syed.slate.ui.rich.HtmlParser
import com.syed.slate.ui.rich.RemoteImage
import com.syed.slate.ui.rich.annotate
import com.syed.slate.ui.theme.Highlights
import com.syed.slate.ui.theme.LocalPalette
import com.syed.slate.ui.theme.isDark

/** What the colour bar is acting on. [owner] is the text field that raised it, so a tap elsewhere can dismiss it. */
sealed interface HlTarget {
    val owner: Any
    val clear: () -> Unit
    class Add(override val owner: Any, val uid: String, val anchors: List<Anchored>, val quote: String, override val clear: () -> Unit) : HlTarget
    class Edit(override val owner: Any, val ids: List<String>, val color: String, override val clear: () -> Unit) : HlTarget
}

class HighlightController(val repo: HighlightRepository) {
    var target by mutableStateOf<HlTarget?>(null)
    var lastColor by mutableStateOf("mint")
    /** Ends the text-selection session too (drops focus), so the system's selection bubble goes away with the bar. */
    var endSelection: () -> Unit = {}
    fun dismiss() { target?.clear?.invoke(); target = null; endSelection() }
}

val LocalHighlights = staticCompositionLocalOf<HighlightController?> { null }

/** Swallows the system's own copy/paste bubble: selecting text raises Slate's colour bar instead. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {}
}

/** Wraps a module's screens: provides the highlight controller and draws the colour bar over the content. */
@Composable
fun HighlightHost(repo: HighlightRepository, content: @Composable () -> Unit) {
    val controller = remember(repo) { HighlightController(repo) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    controller.endSelection = { focus.clearFocus() }
    val clipboard = LocalClipboardManager.current
    CompositionLocalProvider(LocalHighlights provides controller, LocalTextToolbar provides NoTextToolbar) {
        Box(Modifier.fillMaxSize()) {
            content()
            val t = controller.target
            AnimatedVisibility(t != null, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                val shown = remember(t) { t } ?: return@AnimatedVisibility
                HighlightBar(shown, controller, clipboard)
            }
        }
    }
}

@Composable
private fun HighlightBar(t: HlTarget, c: HighlightController, clipboard: ClipboardManager) {
    val p = LocalPalette.current
    Surface(
        Modifier.navigationBarsPadding().padding(12.dp), shape = MaterialTheme.shapes.extraLarge, color = p.surface, shadowElevation = 8.dp, tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val active = if (t is HlTarget.Edit) t.color else c.lastColor
            HIGHLIGHT_COLORS.forEach { name ->
                val fill = (if (p.isDark) Highlights.dark(name) else Highlights.light(name))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(fill.fill.copy(alpha = 1f)).border(if (active == name) 3.dp else 1.dp, if (active == name) MaterialTheme.colorScheme.primary else p.outline, CircleShape)
                        .clickable {
                            c.lastColor = name
                            when (t) {
                                is HlTarget.Add -> c.repo.add(t.uid, t.anchors, name)
                                is HlTarget.Edit -> c.repo.recolor(t.ids, name)
                            }
                            c.dismiss()
                        },
                )
            }
            if (t is HlTarget.Edit) IconButton(onClick = { c.repo.remove(t.ids); c.dismiss() }) { Icon(Icons.Filled.DeleteOutline, "Remove highlight", tint = p.bad) }
            if (t is HlTarget.Add) IconButton(onClick = { clipboard.setText(AnnotatedString(t.quote)); c.dismiss() }) { Icon(Icons.Filled.ContentCopy, "Copy") }
            IconButton(onClick = { c.dismiss() }) { Icon(Icons.Filled.Close, "Close") }
        }
    }
}

/**
 * Text that can be highlighted. It is a read-only text field, which gives exact selection offsets (a plain Text does
 * not); long-press selects, a tap on an existing mark offers recolour/remove.
 *
 * [raw] is the block's text as the web apps saw it (`textContent`), against which saved offsets are measured. For plain
 * blocks it is [text]; for a paragraph of an HTML block, [toRaw] maps each displayed character to its raw offset.
 */
@Composable
fun HText(
    uid: String?,
    block: String,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    editable: Boolean = true,
    base: AnnotatedString? = null,
    raw: String = text,
    toRaw: IntArray? = null,
    softWrap: Boolean = true,
) {
    val ctl = LocalHighlights.current
    val byUid by (ctl?.repo?.byUid ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }).collectAsState()
    val dark = LocalPalette.current.isDark
    val marks = remember(byUid, uid, block) { uid?.let { byUid[it] }.orEmpty().filter { it.block == block } }
    val bands = remember(raw, marks) { Anchor.bandsFor(raw, marks) }
    // Each band as a range of the DISPLAYED text.
    val shown = remember(bands, toRaw, text) {
        bands.mapNotNull { b ->
            if (toRaw == null) { val s = b.start.coerceIn(0, text.length); val e = b.end.coerceIn(0, text.length); if (e > s) Triple(s, e, b) else null }
            else {
                val s = toRaw.indexOfFirst { it >= b.start && it < b.end }
                if (s < 0) null else Triple(s, toRaw.indexOfLast { it >= b.start && it < b.end } + 1, b)
            }
        }
    }
    val annotated = remember(text, base, shown, dark) {
        buildAnnotatedString {
            append(base ?: AnnotatedString(text))
            shown.forEach { (s, e, b) -> addStyle(SpanStyle(background = (if (dark) Highlights.dark(b.color) else Highlights.light(b.color)).fill), s, e) }
        }
    }
    val resolved = style.copy(color = if (color != Color.Unspecified) color else LocalContentColor.current)
    if (!editable || uid == null || ctl == null) { Text(annotated, modifier, style = resolved, softWrap = softWrap); return }

    val owner = remember { Any() }
    var tfv by remember(annotated) { mutableStateOf(TextFieldValue(annotated)) }
    val clear = { tfv = tfv.copy(selection = TextRange.Zero) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var clearJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Applied right at the field, so the system's own copy/translate bubble never competes with Slate's colour bar.
    CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
    BasicTextField(
        value = tfv,
        onValueChange = { nv ->
            tfv = tfv.copy(selection = nv.selection)
            val sel = nv.selection
            clearJob?.cancel(); clearJob = null
            if (!sel.collapsed) {
                val a = minOf(sel.start, sel.end).coerceIn(0, text.length); val b = maxOf(sel.start, sel.end).coerceIn(0, text.length)
                if (b > a) {
                    val rs = if (toRaw == null) a else toRaw[a]
                    val re = if (toRaw == null) b else toRaw[b - 1] + 1
                    // A selection inside an existing mark edits that mark instead of adding a new one.
                    val inside = shown.firstOrNull { (s, e, _) -> a >= s && b <= e }
                    val anchor = Anchor.trimmed(raw, rs, re)
                    ctl.target = when {
                        inside != null -> HlTarget.Edit(owner, inside.third.ids, inside.third.color, clear)
                        anchor != null -> HlTarget.Add(owner, uid, listOf(Anchored(block, anchor.first, anchor.second, anchor.third)), anchor.third, clear)
                        else -> null
                    }
                }
            } else {
                val at = sel.start
                val inside = shown.firstOrNull { (s, e, _) -> at in s until e }
                when {
                    inside != null -> ctl.target = HlTarget.Edit(owner, inside.third.ids, inside.third.color, clear)
                    // The field reports a collapsed selection in passing while a selection is being made or its handles are
                    // grabbed. Clearing at once made the bar flash and vanish just as the handles appeared, so a collapse
                    // only ends the selection if it is still collapsed a moment later.
                    ctl.target?.owner === owner -> clearJob = scope.launch {
                        delay(450)
                        if (ctl.target?.owner === owner && tfv.selection.collapsed) ctl.target = null
                    }
                    // A tap in some other text while a bar is up: that selection is over.
                    ctl.target != null -> ctl.dismiss()
                }
            }
        },
        readOnly = true, textStyle = resolved, cursorBrush = SolidColor(Color.Transparent), modifier = modifier,
    )
    }
}

/** An HTML block (General's question or explanation): paragraphs and images, with highlights spanning them as the web does. */
@Composable
fun HtmlHText(uid: String?, block: String, html: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified, editable: Boolean = true) {
    val blocks = remember(html) { HtmlParser.parse(html) }
    val raw = remember(html) { HtmlAlign.rawText(html) }
    val maps = remember(html) { HtmlAlign.align(raw, blocks.filterIsInstance<Block.Paragraph>()) }
    // Web `.rich p` margin (0.35em top and bottom) works out to ~10dp between paragraphs.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var pi = 0
        blocks.forEach { b ->
            when (b) {
                is Block.Paragraph -> HText(uid, block, b.text, style = style, color = color, editable = editable, base = annotate(b), raw = raw, toRaw = maps[pi++])
                is Block.Image -> RemoteImage(b.url)
            }
        }
    }
}
