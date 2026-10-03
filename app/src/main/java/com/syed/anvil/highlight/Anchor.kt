package com.syed.anvil.highlight

import org.json.JSONObject

val HIGHLIGHT_COLORS = listOf("mint", "amber", "rose", "violet")
const val DEFAULT_HIGHLIGHT_COLOR = "mint"

/**
 * One saved highlight, in the web apps' format: where it is (question uid + block key), which characters (offsets into
 * that block's plain text) and the exact quote, kept so it can be found again if the text shifts a little.
 */
data class Highlight(val id: String, val uid: String, val block: String, val start: Int, val end: Int, val quote: String, val color: String) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("uid", uid).put("block", block).put("start", start).put("end", end).put("quote", quote).put("color", color)

    companion object {
        fun fromJson(o: JSONObject) = Highlight(o.getString("id"), o.getString("uid"), o.getString("block"), o.getInt("start"), o.getInt("end"), o.getString("quote"), o.optString("color", DEFAULT_HIGHLIGHT_COLOR))
    }
}

/** A band of text to paint: merged highlights, remembering which ones it came from. */
data class Band(val start: Int, val end: Int, val ids: List<String>, val color: String)

/**
 * Ported from the web's textAnchor.js: if the stored offsets still point at the quote, use them; otherwise look for the
 * quote, and move there only if it occurs exactly once. Anything else is orphaned: not drawn, but never deleted, so a
 * wording change cannot silently destroy a mark (restoring the wording brings it back).
 */
object Anchor {
    private fun slice(s: String, start: Int, end: Int): String {
        val a = start.coerceIn(0, s.length); val b = end.coerceIn(0, s.length)
        return if (b <= a) "" else s.substring(a, b)
    }

    fun resolve(blockText: String, h: Highlight): IntRange? {
        if (h.quote.isEmpty()) return null
        if (slice(blockText, h.start, h.end) == h.quote) return h.start until h.end
        val first = blockText.indexOf(h.quote)
        if (first < 0) return null
        if (blockText.indexOf(h.quote, first + 1) >= 0) return null // ambiguous
        return first until first + h.quote.length
    }

    /** Non-overlapping, sorted bands. Overlapping or touching marks merge (like a PDF reader); the band takes its earliest mark's colour. */
    fun bandsFor(blockText: String, highlights: List<Highlight>): List<Band> {
        val placed = highlights.mapNotNull { h -> resolve(blockText, h)?.let { Band(it.first, it.last + 1, listOf(h.id), h.color) } }
            .sortedWith(compareBy({ it.start }, { it.end }))
        if (placed.isEmpty()) return emptyList()
        val merged = ArrayList<Band>(); merged.add(placed.first())
        for (r in placed.drop(1)) {
            val last = merged.last()
            if (r.start <= last.end) merged[merged.lastIndex] = last.copy(end = maxOf(last.end, r.end), ids = last.ids + r.ids)
            else merged.add(r)
        }
        return merged
    }

    /** Trim whitespace the drag picked up at the edges, so the mark hugs the words. Null if nothing visible is left. */
    fun trimmed(blockText: String, start: Int, end: Int): Triple<Int, Int, String>? {
        if (end <= start) return null
        val q = slice(blockText, start, end)
        val lead = q.length - q.trimStart().length; val trail = q.length - q.trimEnd().length
        val s = start + lead; val e = end - trail
        if (e <= s) return null
        return Triple(s, e, slice(blockText, s, e))
    }
}
