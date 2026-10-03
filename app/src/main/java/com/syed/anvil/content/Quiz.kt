package com.syed.anvil.content

import com.syed.anvil.progress.Flag
import java.text.Normalizer
import kotlin.random.Random

/** Which of your questions a quiz draws from. */
enum class PoolSet(val key: String) {
    ALL("all"), IMPORTANT("important"), WEAK("weak"), NAILED("nailed");

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: ALL }
}

object QuizPool {
    /** Weak only counts when the question is also Important, as in the web apps. */
    fun matches(set: PoolSet, f: Flag?): Boolean = when (set) {
        PoolSet.ALL -> true
        PoolSet.IMPORTANT -> f?.important == true
        PoolSet.WEAK -> f?.weak == true && f.important
        PoolSet.NAILED -> f?.nailed == true
    }

    /** Quizzable questions in the pool, shuffled. Questions without a known answer are never included. */
    fun build(items: List<Item>, flags: Map<String, Flag>, set: PoolSet, random: Random = Random): List<Item> =
        items.filter { it.isQuizzable && matches(set, it.uid?.let(flags::get)) }.shuffled(random)

    fun counts(items: List<Item>, flags: Map<String, Flag>): Map<PoolSet, Int> {
        val q = items.filter { it.isQuizzable }
        return PoolSet.entries.associateWith { s -> q.count { matches(s, it.uid?.let(flags::get)) } }
    }
}

/** Options as (letter, html), in letter order. */
fun Item.optionList(): List<Pair<String, String>> {
    val o = data.optJSONObject("options") ?: return emptyList()
    return o.keys().asSequence().sorted().map { it to o.optString(it) }.filter { it.second.isNotEmpty() }.toList()
}

val Item.correctAnswer: String? get() = if (data.isNull("correct_answer")) null else data.optString("correct_answer").ifEmpty { null }
val Item.explanation: String? get() = data.optString("explanation").takeIf { it.isNotBlank() && it != "null" }

enum class Grade { EXCELLENT, GOOD, OK, LOW;
    companion object {
        fun of(pct: Int) = when { pct >= 80 -> EXCELLENT; pct >= 60 -> GOOD; pct >= 40 -> OK; else -> LOW }
    }
}

object SearchText {
    private val zeroWidth = Regex("[\\u200B-\\u200D\\uFEFF]")
    private val punct = Regex("[?।!,.;:'\"’‘“”()\\[\\]{}<>—–\\-_/\\\\|*•]+")
    private val spaces = Regex("\\s+")
    private val tags = Regex("<[^>]*>")

    /** NFC, no zero-width joiners, lowercase, punctuation (incl. the Bangla danda) turned into spaces. */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFC).replace(zeroWidth, "").lowercase()
            .replace(punct, " ").replace(spaces, " ").trim()

    fun tokens(query: String): List<String> = normalize(query).let { if (it.isEmpty()) emptyList() else it.split(' ') }

    /** Everything searchable about a question, normalised once. Tags are stripped cheaply: this is only for matching. */
    fun haystack(item: Item): String {
        val parts = ArrayList<String>(8)
        parts.add(item.data.optString("question")); item.explanation?.let(parts::add)
        item.data.optJSONObject("options")?.let { o -> o.keys().forEach { parts.add(o.optString(it)) } }
        return normalize(parts.joinToString(" ").replace(tags, " "))
    }

    /** Every token must appear, in any order. */
    fun matches(haystack: String, tokens: List<String>) = tokens.isNotEmpty() && tokens.all { it in haystack }
}
