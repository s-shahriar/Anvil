package com.syed.slate.content

import org.json.JSONArray
import org.json.JSONObject

/** ICT's Written, Extra and Viva cards: one question with a structured, multi-part answer. */
object LongForm {
    val groups = setOf("written", "extra", "viva")

    fun isLongForm(item: Item) = item.group in groups
    fun isLongForm(group: String) = group in groups

    class SubGroup(val name: String?, val items: List<Item>)
    class Segment(val name: String, val subgroups: List<SubGroup>) { val count get() = subgroups.sumOf { it.items.size } }
    class Layout(val regular: List<Item>, val segments: List<Segment>)

    /**
     * Questions tagged with a `segment` leave the plain list and sit behind an entry card of their own (c_programming
     * keeps 25 of its 28 questions in segments); a `subsegment` splits a segment into labelled sub-groups.
     */
    fun layout(items: List<Item>): Layout {
        val regular = items.filter { it.segment == null }
        val names = items.mapNotNull { it.segment }.distinct()
        val segments = names.map { n ->
            val inSeg = items.filter { it.segment == n }
            val subs = inSeg.map { it.subsegment }.distinct().map { sn -> SubGroup(sn, inSeg.filter { it.subsegment == sn }) }
            Segment(n, subs)
        }
        return Layout(regular, segments)
    }

    /** Every piece of text in the answer, flattened, so search finds what is written inside a card. */
    fun answerText(item: Item): String {
        val sb = StringBuilder()
        fun walk(v: Any?) {
            when (v) {
                is String -> sb.append(v).append(' ')
                is JSONObject -> v.keys().forEach { k -> if (k != "codeLang" && k != "image") walk(v.opt(k)) }
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i))
            }
        }
        walk(item.data.opt("answer")); walk(item.data.opt("tags")); walk(item.data.opt("verdict"))
        return sb.toString()
    }
}

val Item.segment: String? get() = data.optString("segment").takeIf { it.isNotEmpty() && !data.isNull("segment") }
val Item.subsegment: String? get() = data.optString("subsegment").takeIf { it.isNotEmpty() && !data.isNull("subsegment") }
val Item.verdict: String? get() = data.optString("verdict").takeIf { it.isNotEmpty() && !data.isNull("verdict") }
val Item.headerCode: String? get() = data.optString("headerCode").takeIf { it.isNotEmpty() && !data.isNull("headerCode") }
val Item.headerCodeLang: String? get() = data.optString("headerCodeLang").takeIf { it.isNotEmpty() }
val Item.answer: JSONObject? get() = data.optJSONObject("answer")

/** A line of a question, as the web renders it: paragraph, hanging-indent list item, or a gap between blocks. */
sealed interface QBlock {
    data class Para(val text: String) : QBlock
    data class ListItem(val text: String) : QBlock
    data object Gap : QBlock
}

private val indented = Regex("^\\s{2,}\\S")

/** Blank line = gap, a line indented by two or more spaces = list item, anything else = paragraph. */
fun splitQuestionBlocks(text: String): List<QBlock> {
    val out = ArrayList<QBlock>()
    for (raw in text.split('\n')) {
        if (raw.isBlank()) { if (out.isNotEmpty() && out.last() !is QBlock.Gap) out.add(QBlock.Gap); continue }
        out.add(if (indented.containsMatchIn(raw)) QBlock.ListItem(raw.trim()) else QBlock.Para(raw.trim()))
    }
    while (out.isNotEmpty() && out.last() is QBlock.Gap) out.removeAt(out.lastIndex)
    return out
}
