package com.syed.slate.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syed.slate.content.QBlock
import com.syed.slate.ui.highlight.HText
import com.syed.slate.content.splitQuestionBlocks
import com.syed.slate.ui.rich.CodeTokens
import com.syed.slate.ui.rich.RemoteImage
import com.syed.slate.ui.rich.TokenKind
import com.syed.slate.ui.theme.LocalPalette
import com.syed.slate.ui.theme.Mono
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
private fun JSONArray.strings(): List<String> = List(length()) { optString(it) }

/** Code colours from ict-quiz, light and dark. The palette's own background tells which is in use. */
private class CodeColors(val bg: androidx.compose.ui.graphics.Color, val text: androidx.compose.ui.graphics.Color, val kw: androidx.compose.ui.graphics.Color,
                         val str: androidx.compose.ui.graphics.Color, val num: androidx.compose.ui.graphics.Color, val comment: androidx.compose.ui.graphics.Color,
                         val fn: androidx.compose.ui.graphics.Color, val type: androidx.compose.ui.graphics.Color)

private fun c(hex: Long) = androidx.compose.ui.graphics.Color(0xFF000000 or hex)
private val lightCode = CodeColors(c(0xE6EDF1), c(0x132029), c(0x22597A), c(0x2F7A56), c(0xA86E1F), c(0x7B8C98), c(0x6B4FA0), c(0x2E7599))
private val darkCode = CodeColors(c(0x0D1419), c(0xEEF3F6), c(0x86C1DC), c(0x8FCBA8), c(0xE3B566), c(0x6B7C88), c(0xB9A3E0), c(0x6FB3D2))

@Composable
fun CodeBlock(code: String, lang: String?, modifier: Modifier = Modifier) {
    val dark = LocalPalette.current.bg.luminance() < .5f
    val colors = if (dark) darkCode else lightCode
    val text = remember(code, lang, dark) {
        buildAnnotatedString {
            append(code.trimEnd())
            for (t in CodeTokens.tokenize(code.trimEnd(), lang)) {
                val col = when (t.kind) {
                    TokenKind.KEYWORD, TokenKind.META -> colors.kw; TokenKind.TYPE -> colors.type; TokenKind.STRING -> colors.str
                    TokenKind.NUMBER -> colors.num; TokenKind.COMMENT -> colors.comment; TokenKind.FUNCTION -> colors.fn
                }
                addStyle(SpanStyle(color = col), t.start, t.end)
            }
        }
    }
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(colors.bg).horizontalScroll(rememberScrollState()).padding(12.dp)) {
        Text(text, color = colors.text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 18.sp), softWrap = false)
    }
}

/** ASCII diagram: monospace, never wrapped, scrolls sideways. */
@Composable
fun DiagramBlock(text: String, modifier: Modifier = Modifier, uid: String? = null, block: String = "diagram", editable: Boolean = true) {
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(p.elevated).horizontalScroll(rememberScrollState()).padding(12.dp)) {
        // The web highlights the diagram's text as written (no trimming), so the block text is the original string.
        HText(uid, block, text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.sp), softWrap = false, editable = editable, raw = text)
    }
}

@Composable
private fun BlockLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalPalette.current.text3)
}

/** Bullet dot, as the web's `.written-dot`: a 7px circle over its own soft glow, aligned with the first text line. */
@Composable
private fun Dot() {
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.padding(top = 3.dp).size(16.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(color.copy(alpha = .22f)))
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    }
}

/**
 * Grid with equal-height cells. Columns share the width in proportion to how much text they hold (within limits), so a
 * short "yes/no" column doesn't take as much room as an explanation. It scrolls sideways only when even the minimum
 * widths don't fit.
 */
@Composable
fun DataTable(headers: List<String>?, rows: List<List<String>>, modifier: Modifier = Modifier, uid: String? = null, prefix: String? = null) {
    val p = LocalPalette.current
    val cols = maxOf(headers?.size ?: 0, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val weights = remember(headers, rows) {
        List(cols) { j ->
            val lens = rows.map { it.getOrNull(j).orEmpty().length } + (headers?.getOrNull(j)?.length ?: 0)
            lens.average().coerceIn(8.0, 45.0).toFloat()
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).border(1.dp, p.outline, MaterialTheme.shapes.small)) {
        // Each column gets the minimum, then the width that is left is shared by text length, so the table fits the
        // screen exactly when it can; only when the minimums alone are too wide does it scroll.
        val minCol = 96.dp
        val spare = (maxWidth - minCol * cols).coerceAtLeast(0.dp)
        val total = weights.sum()
        val widths: List<Dp> = weights.map { minCol + spare * (it / total) }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            @Composable fun line(cells: List<String>, header: Boolean, shaded: Boolean, rowIndex: Int = -1) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    for (j in 0 until cols) {
                        Box(
                            Modifier.width(widths[j]).fillMaxHeight().background(if (header) MaterialTheme.colorScheme.primary.copy(alpha = .10f).compositeOver(p.elevated) else if (shaded) p.elevated else p.surface)
                                .border(.5.dp, p.outline).padding(10.dp),
                        ) {
                            if (!header && uid != null && prefix != null) HText(uid, "$prefix.r$rowIndex.c$j", cells.getOrElse(j) { "" }, style = MaterialTheme.typography.bodyMedium)
                            else Text(cells.getOrElse(j) { "" },
                                style = if (header) MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.24.sp) else MaterialTheme.typography.bodyMedium,
                                color = if (header) p.text2 else androidx.compose.ui.graphics.Color.Unspecified)
                        }
                    }
                }
            }
            if (headers != null) line(headers, header = true, shaded = false)
            rows.forEachIndexed { i, r -> line(r, header = false, shaded = i % 2 == 1, rowIndex = i) }
        }
    }
}

/** An image shipped inside the app (`/written-images/x.png`) or fetched from a URL. */
@Composable
private fun AnswerImage(path: String) {
    if (path.startsWith("http")) { RemoteImage(path); return }
    val ctx = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, path) {
        value = runCatching { ctx.assets.open(path.trimStart('/')).use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small), contentScale = ContentScale.FillWidth) }
}

/**
 * The question: paragraphs, hanging-indent list items, and gaps, as the web renders it. A one-line question is block `q`;
 * otherwise each line is `q.<index>` (the index counts the gaps too, so it matches the web's saved highlights).
 */
@Composable
fun QuestionTextBlocks(text: String, modifier: Modifier = Modifier, uid: String? = null, editable: Boolean = true) {
    val blocks = remember(text) { splitQuestionBlocks(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        blocks.forEachIndexed { i, b ->
            val key = if (blocks.size <= 1) "q" else "q.$i"
            when (b) {
                is QBlock.Para -> HText(uid, key, b.text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), editable = editable)
                is QBlock.ListItem -> HText(uid, key, b.text, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyMedium, editable = editable)
                QBlock.Gap -> Spacer(Modifier.height(6.dp))
            }
        }
    }
}

/** The answer, in the order the web shows it: code, image, summary, points, diagram, table, mistakes, mnemonic, extended. */
@Composable
fun WrittenBody(a: JSONObject, modifier: Modifier = Modifier, uid: String? = null) {
    val p = LocalPalette.current
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        a.str("code")?.let { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { BlockLabel(a.str("codeLang") ?: "Code"); CodeBlock(it, a.str("codeLang")) } }
        a.str("image")?.let { AnswerImage(it) }

        // সংক্ষেপ — the summary, a string or a list of lines. Web `.written-summary`:
        // topic-coloured hairline border, small tracked label, lines divided by hairlines.
        a.opt("summary")?.let { s ->
            val lines = if (s is JSONArray) s.strings() else listOf(s.toString())
            if (lines.isNotEmpty()) Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(p.primaryContainer.copy(alpha = .55f))
                    .border(1.dp, primary.copy(alpha = .25f), MaterialTheme.shapes.medium)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "সংক্ষেপ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.1.sp),
                    color = primary,
                )
                if (s is JSONArray) lines.forEachIndexed { i, l ->
                    if (i > 0) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                    HText(uid, "summary.$i", l, style = MaterialTheme.typography.bodyMedium)
                }
                else HText(uid, "summary", lines.first(), style = MaterialTheme.typography.bodyMedium)
            }
        }

        a.optJSONArray("points")?.let { Points(it, uid) }

        a.str("diagram")?.let { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { BlockLabel("Diagram"); DiagramBlock(it, uid = uid, block = "diagram") } }

        a.optJSONObject("table")?.takeIf { (it.optJSONArray("rows")?.length() ?: 0) > 0 }?.let { t ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BlockLabel("তুলনা")
                DataTable(t.optJSONArray("headers")?.strings(), rows(t.getJSONArray("rows")), uid = uid, prefix = "table")
            }
        }

        a.optJSONArray("mistakes")?.takeIf { it.length() > 0 }?.let { m ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BlockLabel("সাধারণ ভুল")
                DataTable(listOf("❌ ভুল ধারণা", "✅ আসল কথা"), rows(m), uid = uid, prefix = "mistakes")
            }
        }

        a.str("mnemonic")?.let {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.elevated).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Psychology, null, Modifier.size(20.dp), tint = primary)
                HText(uid, "mnemonic", it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        a.optJSONObject("extended")?.let { Extended(it, uid) }
    }
}

private fun rows(a: JSONArray): List<List<String>> = List(a.length()) { i -> a.optJSONArray(i)?.strings() ?: listOf(a.optString(i)) }

/**
 * `points` is mostly bullets, but an item can be a `{sub}` indented bullet, a `{code}` block or a `{diagram}` block.
 * Blocks break the list in two, so a diagram sits right after the part it illustrates.
 */
@Composable
private fun Points(points: JSONArray, uid: String?) {
    // Web `.written-points` gap: 9px.
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        for (i in 0 until points.length()) {
            val pt = points.get(i)
            when {
                pt is JSONObject && pt.str("code") != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockLabel(pt.str("label") ?: pt.str("codeLang") ?: "Code"); CodeBlock(pt.getString("code"), pt.str("codeLang"))
                }
                pt is JSONObject && pt.str("diagram") != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BlockLabel(pt.str("label") ?: "Diagram"); DiagramBlock(pt.getString("diagram"), uid = uid, block = "points.$i.diagram")
                }
                // Web `.written-subpoint`: a 2px bar in the topic colour at 40%, 3px out from the edge,
                // text indented 12px past it and slightly dimmed — not a dash.
                pt is JSONObject && pt.str("sub") != null -> Row(
                    Modifier.padding(start = 3.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(2.dp).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.primary.copy(alpha = .4f)))
                    HText(
                        uid, "points.$i", pt.getString("sub"), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .88f),
                    )
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Dot(); HText(uid, "points.$i", pt.toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Extended(e: JSONObject, uid: String?) {
    val p = LocalPalette.current
    val primary = MaterialTheme.colorScheme.primary
    var open by rememberSaveable(e.str("title")) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).border(1.dp, p.outline, MaterialTheme.shapes.medium).clickable { open = !open }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = primary)
            Text(e.str("title") ?: "More", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (open) {
            e.optJSONArray("points")?.strings()?.forEachIndexed { i, t -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Dot(); HText(uid, "ext.points.$i", t, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium) } }
            e.optJSONArray("table")?.takeIf { it.length() > 0 }?.let { DataTable(e.optJSONArray("tableHeaders")?.strings(), rows(it), uid = uid, prefix = "ext.table") }
            e.str("diagram")?.let { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { BlockLabel("Diagram"); DiagramBlock(it, uid = uid, block = "ext.diagram") } }
        }
    }
}
