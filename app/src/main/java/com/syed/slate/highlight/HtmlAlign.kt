package com.syed.slate.highlight

import com.syed.slate.ui.rich.Block
import com.syed.slate.ui.rich.HtmlParser

/**
 * HTML blocks (General's questions and explanations) are saved against the browser's `textContent`: every text node,
 * entities decoded, tags gone, whitespace untouched. Slate draws them as separate collapsed-whitespace paragraphs, so
 * this lines the two up: for each paragraph, which character of the raw text each displayed character is.
 */
object HtmlAlign {
    private val tag = Regex("<[^>]*>")
    private fun isWs(c: Char) = c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\u000C'

    /** What the web calls the block's text: tags stripped, entities decoded, nothing collapsed. */
    fun rawText(html: String): String = HtmlParser.decodeEntities(tag.replace(html, ""))

    /** One entry per paragraph: the displayed character → raw offset map. Synthetic characters (a `<br>` newline, a list bullet) map to where they sit. */
    fun align(raw: String, paragraphs: List<Block.Paragraph>): List<IntArray> {
        var pos = 0
        return paragraphs.map { p ->
            while (pos < raw.length && isWs(raw[pos])) pos++ // whitespace between paragraphs
            val text = p.text
            IntArray(text.length) { i ->
                val d = text[i]
                when {
                    d == ' ' -> { val at = pos; while (pos < raw.length && isWs(raw[pos])) pos++; at.coerceAtMost(raw.length) }
                    pos < raw.length && raw[pos] == d -> pos++
                    else -> pos // synthetic: shown but not in the raw text
                }
            }
        }
    }
}
