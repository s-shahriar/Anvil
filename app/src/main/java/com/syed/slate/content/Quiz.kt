package com.syed.slate.content

import com.syed.slate.progress.Flag
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

    /**
     * Quizzable questions in the pool, each exactly once, shuffled. Questions without a known answer are never
     * included. Exact copies (same uid, options and answer — duplicate rows in the DB) count once; questions that
     * merely share a stem (same uid, different options) are different questions and all stay.
     *
     * With a [seed] the order is deterministic: every question gets a rank from (seed, row id), so rebuilding the
     * same pool gives the same order, and adding or removing one question never moves the others.
     */
    fun build(items: List<Item>, flags: Map<String, Flag>, set: PoolSet, seed: Long = Random.nextLong()): List<Item> =
        order(items.filter { it.isQuizzable && matches(set, it.uid?.let(flags::get)) }.distinctBy { it.copyKey() }, seed)

    /** [items] in a seeded random order that is stable per question (see [build]). */
    fun order(items: List<Item>, seed: Long): List<Item> =
        items.sortedWith(compareBy<Item> { rank(seed, it.id) }.thenBy { it.id })

    private fun Item.copyKey(): String =
        "$uid\u0000${data.optJSONObject("options")}\u0000${data.optString("correct_answer")}"

    private fun rank(seed: Long, id: String): Long {
        // SplitMix64 finaliser over (seed, id): well spread, and the same on every run.
        var z = seed + id.hashCode().toLong() * -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Pool sizes. Long-form cards (Written/Extra/Viva) are never quizzed but are flagged just the same, so [includeLongForm] counts them. */
    fun counts(items: List<Item>, flags: Map<String, Flag>, includeLongForm: Boolean = false): Map<PoolSet, Int> {
        val q = items.filter { it.isQuizzable || (includeLongForm && LongForm.isLongForm(it)) }.distinctBy { it.copyKey() }
        return PoolSet.entries.associateWith { s -> q.count { matches(s, it.uid?.let(flags::get)) } }
    }
}

/**
 * Running quiz decks by session seed. A deck is built once and then read back from here, so nothing that happens
 * during the quiz — deleting a question, a content refresh, a flag change, the activity being recreated — can
 * rebuild it and reshuffle the questions under the current position (which made some repeat and others never
 * show). Lost only with the process; then [QuizPool.build] with the same seed rebuilds the same order.
 */
object QuizDecks {
    private val decks = object : LinkedHashMap<Long, List<Item>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<Item>>) = size > 8
    }

    @Synchronized fun get(seed: Long, build: () -> List<Item>): List<Item> = decks.getOrPut(seed, build)
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
        parts.add(item.question); item.explanation?.let(parts::add)
        if (LongForm.isLongForm(item)) parts.add(LongForm.answerText(item))
        item.data.optJSONObject("options")?.let { o -> o.keys().forEach { parts.add(o.optString(it)) } }
        return normalize(parts.joinToString(" ").replace(tags, " "))
    }

    /** Every token must appear, in any order. */
    fun matches(haystack: String, tokens: List<String>) = tokens.isNotEmpty() && tokens.all { it in haystack }
}
