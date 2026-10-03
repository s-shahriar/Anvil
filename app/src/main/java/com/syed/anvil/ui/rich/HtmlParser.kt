package com.syed.anvil.ui.rich

enum class Script { NONE, SUP, SUB }

/** A stretch of text with one style. */
data class Run(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val script: Script = Script.NONE,
)

sealed interface Block {
    class Paragraph(val runs: List<Run>) : Block {
        val text get() = runs.joinToString("") { it.text }
    }
    class Image(val url: String) : Block
}

/**
 * Turns the small HTML subset the quiz content uses (b, strong, em, u, s, sup, sub, a, br, p, div, span, h4, img,
 * lists) into styled paragraphs and images. Pure Kotlin, so it is unit-tested without Android.
 *
 * Whitespace collapses as in a browser, so the `\r\n` the editors leave between tags never shows. Only `<br>`
 * makes a line break inside a paragraph.
 */
object HtmlParser {
    private val tag = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>")
    private val srcAttr = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("[ \\t\\r\\n\\u000C]+")
    private val blockTags = setOf("p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "tr", "blockquote", "section")

    fun parse(html: String): List<Block> {
        val blocks = ArrayList<Block>()
        var runs = ArrayList<Run>()
        var bold = 0; var italic = 0; var underline = 0; var strike = 0; var sup = 0; var sub = 0

        fun flush() {
            // Trim the paragraph's edges, including a trailing <br>.
            while (runs.isNotEmpty() && runs.last().text.isBlank()) runs.removeAt(runs.lastIndex)
            if (runs.isNotEmpty()) {
                val last = runs.last(); runs[runs.lastIndex] = last.copy(text = last.text.trimEnd())
                val first = runs.first(); runs[0] = first.copy(text = first.text.trimStart())
                blocks.add(Block.Paragraph(runs))
            }
            runs = ArrayList()
        }

        fun add(text: String) {
            if (text.isEmpty()) return
            val script = if (sup > 0) Script.SUP else if (sub > 0) Script.SUB else Script.NONE
            runs.add(Run(text, bold > 0, italic > 0, underline > 0, strike > 0, script))
        }

        fun addText(raw: String) {
            var t = decodeEntities(raw).replace(spaces, " ")
            // No leading space at the start of a paragraph or right after a line break.
            if (runs.isEmpty() || runs.last().text.endsWith("\n")) t = t.trimStart()
            // And never two spaces in a row across run boundaries.
            if (t.startsWith(" ") && runs.lastOrNull()?.text?.endsWith(" ") == true) t = t.trimStart()
            add(t)
        }

        var pos = 0
        for (m in tag.findAll(html)) {
            if (m.range.first > pos) addText(html.substring(pos, m.range.first))
            pos = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            val d = if (closing) -1 else 1
            when (name) {
                "b", "strong" -> bold = (bold + d).coerceAtLeast(0)
                "em", "i" -> italic = (italic + d).coerceAtLeast(0)
                "u", "a" -> underline = (underline + d).coerceAtLeast(0)
                "s", "strike", "del" -> strike = (strike + d).coerceAtLeast(0)
                "sup" -> sup = (sup + d).coerceAtLeast(0)
                "sub" -> sub = (sub + d).coerceAtLeast(0)
                "br" -> if (!closing) {
                    // "text <br>" renders as "text\n", not "text \n".
                    runs.lastOrNull()?.takeIf { it.text.endsWith(" ") }?.let { runs[runs.lastIndex] = it.copy(text = it.text.trimEnd(' ')) }
                    add("\n")
                }
                "img" -> if (!closing) {
                    flush()
                    srcAttr.find(m.groupValues[3])?.groupValues?.get(1)?.let { blocks.add(Block.Image(decodeEntities(it))) }
                }
                "li" -> { flush(); if (!closing) add("• ") }
                in blockTags -> {
                    flush()
                    if (name == "h4" || name.startsWith("h") && name.length == 2) bold = (bold + d).coerceAtLeast(0)
                }
                else -> Unit // span and anything unknown: keep the text, ignore the tag
            }
        }
        if (pos < html.length) addText(html.substring(pos))
        flush()
        return blocks
    }

    /** The visible text with paragraphs separated by newlines — for search and previews. */
    fun plainText(html: String): String =
        parse(html).filterIsInstance<Block.Paragraph>().joinToString("\n") { it.text }

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
        "times" to "×", "divide" to "÷", "deg" to "°", "plusmn" to "±", "laquo" to "«", "raquo" to "»",
    )
    private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    fun decodeEntities(s: String): String = if ('&' !in s) s else entity.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[e]
        } ?: m.value
    }
}
