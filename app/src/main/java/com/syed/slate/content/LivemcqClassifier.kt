package com.syed.slate.content

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The LiveMCQ category suggester — a faithful Kotlin port of general-quiz's `livemcqClassify.js` /
 * `livemcqKnowledge.js` / `livemcqTraining.js`. No AI, no network: a tf-idf / k-nearest-neighbour
 * ranker over every already-classified question in the offline cache, reporting which category a new
 * question's nearest neighbours sit in. A suggestion is only ever a hint — nothing auto-assigns.
 *
 * A document is indexed from three weighted fields (question ×1, explanation ×0.7, options ×0.4),
 * each in its own feature space; Bengali leading-edge stems at lengths 3 and 5; maths notation and the
 * analogy `A :: B` shape as extra features. Neighbours vote with sim², damped by category size^0.4.
 * Tuning constants are the web's 2026-09-30 grid-search values (94.6% top-1 on the live corpus).
 * Sub-topic indexes (one per category, over that category's rows relabelled by sub-topic) run on the
 * web's own SUBTOPIC_DEFAULTS tuning.
 */
object LivemcqClassifier {

    const val BULK_APPLY_MIN = 0.6

    /** Confidence tier: `strong`/`likely` are bulk-apply eligible; `weak` shows but needs a tap. */
    fun tierOf(confidence: Double) = when {
        confidence >= 0.85 -> "strong"
        confidence >= BULK_APPLY_MIN -> "likely"
        else -> "weak"
    }

    // ── Tokenizer ─────────────────────────────────────────────────────────────────────────
    private val TOKEN_RE = Regex("[ঀ-৿]{2,}|[a-z][a-z0-9]+")
    private val BENGALI_RE = Regex("[ঀ-৿]")
    private val SYMBOL_RE = Regex(
        "[θπ√∠△°%∞≤≥≠±×÷²³∴∵∑∫αβ!]|\\b(?:sin|cos|tan|cot|sec|cosec|log|ln)(?=[θαβ²(\\s\\d]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val ANALOGY_RE = Regex("::|\\b[A-Z]{3,}\\s*:\\s*[A-Z]{3,}")
    private const val ANALOGY_KEY = "\$analogy"
    private val TRIG_SYMBOLS = hashSetOf("θ", "sin", "cos", "tan", "cot", "sec", "cosec")
    private const val W_ANALOGY = 1.5

    private val STOP = hashSetOf(
        "কি", "কী", "কোন", "কোনটি", "কোনটির", "এর", "এবং", "এই", "সেই", "যে", "তার",
        "জন্য", "মধ্যে", "থেকে", "দিয়ে", "সাথে", "একটি", "কত", "কার", "নিচের",
        "নিম্নের", "নিম্নে", "হয়", "নয়", "করা", "হলো", "হল", "কোনটিই", "নিচে",
        "উক্ত", "দেওয়া", "আছে", "ছিল", "কোনো", "কোনটা", "সঠিক", "বাক্যটি",
        "what", "which", "who", "whom", "whose", "where", "when", "the", "and", "for",
        "from", "with", "that", "this", "these", "those", "are", "was", "were", "is",
        "be", "been", "has", "have", "had", "does", "do", "did", "not", "following",
        "correct", "choose", "select", "option", "answer", "question", "none", "above",
        "sentence", "word", "words", "meaning", "best", "most", "can", "will", "would",
    )

    private fun stripTags(s: String?) = (s ?: "").replace(Regex("<[^>]+>"), " ")

    private fun tokenize(text: String?, stemLen: List<Int>): List<String> {
        if (text == null) return emptyList()
        val s = Normalizer.normalize(stripTags(text), Normalizer.Form.NFC).lowercase()
        val out = ArrayList<String>()
        for (m in TOKEN_RE.findAll(s)) {
            val token = m.value
            if (token in STOP) continue
            out.add(token)
            if (!BENGALI_RE.containsMatchIn(token)) continue
            // Stems are emitted ALONGSIDE the full token, each with its own length marker, so an
            // exact match still outweighs a stem match.
            for (n in stemLen) if (token.length >= n + 1) out.add(token.slice(0 until n) + "~" + n)
        }
        return out
    }

    // ── Corpus ────────────────────────────────────────────────────────────────────────────
    class Doc(
        val question: String,
        val options: List<String>,
        val explanation: String?,
        val slug: String,
        val weight: Double = 1.0,
        val source: String = "livemcq",
    )

    class Suggestion(
        val slug: String,
        val confidence: Double,
        val tier: String,
        val nearestQuestion: String,
        val nearestSlug: String,
        val nearestSource: String,
        val nearestSim: Double,
    )

    // Cross-module rows enter at a reduced weight, relabelled into a LiveMCQ category or left out
    // (livemcqTraining.js): gk_lang_misc and vocab_* are intentionally excluded.
    private val MODULE_TO_LIVEMCQ = mapOf(
        "bangla" to "lm_bangla_byakoron",
        "sahitya" to "lm_bangla_sahitya",
        "english" to "lm_english_grammar",
    )
    private val CATEGORY_TO_LIVEMCQ = mapOf(
        "gk_bd_affairs" to "lm_bd_affairs",
        "gk_intl_affairs" to "lm_intl_affairs",
        "gk_science" to "lm_science",
        "gk_ict" to "lm_ict",
    )
    private const val CROSS_MODULE_WEIGHT = 0.25
    private const val SUBTOPIC_CROSS_WEIGHT = 0.25

    // Two split categories have a sibling module whose topics ARE the sub-topics, same slugs.
    private val SUBTOPIC_SIBLINGS = mapOf(
        "bangla" to setOf(
            "dhwoni_o_borno", "dhwoni_poriborton", "notwo_bidhan", "sondhi", "uposhorgo",
            "prokiti_protoy", "somas", "karak", "pod", "shobdo", "poribhasha", "banan_bakko",
            "somarthok_shobdo",
        ),
        "english" to setOf(
            "parts_of_speech", "tense", "right_form_of_verbs", "subject_verb", "voice",
            "narration", "transformation", "tag_question", "preposition", "determiner",
            "error_correct",
        ),
    )

    /** One corpus row: category label `cat`, optional sub-topic label `sub`, foreign module or null. */
    private class Row(val doc: Doc, val cat: String, val sub: String?, val foreign: String?)

    /** The corpus from the offline cache (the web's corpusFromModules + livemcqTraining labels). */
    private fun corpus(content: ModuleContent): List<Row> {
        val rows = ArrayList<Row>()
        for (g in content.groups) {
            val native = g.key == "livemcq"
            for (topic in g.topics) {
                val cat = if (native) topic.slug else MODULE_TO_LIVEMCQ[g.key] ?: CATEGORY_TO_LIVEMCQ[topic.slug] ?: continue
                val subLabel = if (native) null else SUBTOPIC_SIBLINGS[g.key]?.takeIf { topic.slug in it }?.let { topic.slug }
                for (item in content.items(g.key, topic.slug)) {
                    if (item.question.isBlank()) continue
                    val sub = if (native) Subtopics.of(item) else subLabel
                    rows.add(Row(
                        Doc(item.question, item.optionList().map { it.second }, item.explanation, cat,
                            if (native) 1.0 else CROSS_MODULE_WEIGHT, if (native) "livemcq" else g.key),
                        cat, sub, if (native) null else g.key,
                    ))
                }
            }
        }
        return rows
    }

    // ── The index ─────────────────────────────────────────────────────────────────────────
    internal class Built(
        val size: Int,
        val suggestFn: (question: String, options: List<String>, explanation: String?) -> Suggestion?,
    )

    class Index internal constructor(
        val size: Int,
        private val suggestFn: (String, List<String>, String?) -> Suggestion?,
        private val subIndexes: Map<String, Built>,
    ) {
        fun suggest(question: String, options: List<String>, explanation: String?): Suggestion? =
            suggestFn(question, options, explanation)

        /** Sub-topic guess inside one category; a guess naming a sub-topic not in [allowed] is dropped. */
        fun suggestSubtopic(
            question: String, options: List<String>, explanation: String?,
            categorySlug: String, allowed: List<String>,
        ): Suggestion? {
            val s = subIndexes[categorySlug]?.suggestFn?.invoke(question, options, explanation) ?: return null
            if (allowed.isNotEmpty() && s.slug !in allowed) return null
            return s
        }
    }

    /** Build the full index (categories + per-category sub-topics) from the corpus. Runs on the caller's thread — dispatch first. */
    fun build(content: ModuleContent): Index = buildDocsIndexed(corpus(content))

    private fun buildDocsIndexed(rows: List<Row>): Index {
        val main = buildTuned(rows.map { r ->
            Doc(r.doc.question, r.doc.options, r.doc.explanation, r.cat, if (r.foreign != null) CROSS_MODULE_WEIGHT else 1.0, r.foreign ?: "livemcq")
        }, subTuned = false)
        val byCat = HashMap<String, MutableList<Doc>>()
        for (r in rows) r.sub?.let { sub ->
            byCat.getOrPut(r.cat) { ArrayList() }.add(
                Doc(r.doc.question, r.doc.options, r.doc.explanation, sub,
                    if (r.foreign != null) SUBTOPIC_CROSS_WEIGHT else 1.0, r.foreign ?: "livemcq"),
            )
        }
        val subs = byCat.mapValues { (_, ds) -> buildTuned(ds, subTuned = true) }
        return Index(main.size, main.suggestFn, subs)
    }

    /** tf-idf / kNN index over labelled docs; [subTuned] selects the sub-topic settings. */
    private fun buildTuned(docs: List<Doc>, subTuned: Boolean): Built {
        val wE = if (subTuned) 0.55 else 0.7
        val wO = 0.4
        val wSym = if (subTuned) 0.75 else 0.0
        val stems = if (subTuned) listOf(4) else listOf(3, 5)
        val minConf = if (subTuned) 0.25 else 0.34

        val slugs = ArrayList<String>(docs.size)
        val questions = ArrayList<String>(docs.size)
        val sources = ArrayList<String>(docs.size)
        val weights = ArrayList<Double>(docs.size)
        val tfs = ArrayList<Map<String, Double>>(docs.size)
        val df = HashMap<String, Int>()

        for (d in docs) {
            val tf = featureFreq(d.question, d.options, d.explanation, wE, wO, wSym, stems)
            if (tf.isEmpty()) continue
            slugs.add(d.slug); questions.add(d.question); sources.add(d.source); weights.add(d.weight); tfs.add(tf)
            for (t in tf.keys) df[t] = (df[t] ?: 0) + 1
        }
        val n = tfs.size

        // Tokens seen in one document only are proper nouns, digits and typos — dropping them
        // costs nothing measurable and cuts the index by ~55%.
        val idf = HashMap<String, Double>(df.size)
        for ((t, c) in df) if (c >= MIN_DF) idf[t] = ln(1.0 + n.toDouble() / c)

        val inverted = HashMap<String, MutableList<Pair<Int, Double>>>()
        for (i in 0 until n) {
            val tf = tfs[i]
            var sq = 0.0
            val vec = HashMap<String, Double>(tf.size)
            for ((t, f) in tf) {
                val w = f * (idf[t] ?: 0.0)
                if (w <= 0) continue
                vec[t] = w
                sq += w * w
            }
            val norm = sqrt(sq).takeIf { it > 0 } ?: 1.0
            for ((t, w) in vec) inverted.getOrPut(t) { ArrayList() }.add(i to w / norm)
        }

        // Weighted document count per category, damping big categories in the vote.
        val prior = HashMap<String, Double>()
        for (i in 0 until n) prior[slugs[i]] = (prior[slugs[i]] ?: 0.0) + weights[i]

        fun suggest(questionText: String, options: List<String>, explanation: String?): Suggestion? {
            if (n == 0) return null
            val qtf = featureFreq(questionText, options, explanation, wE, wO, wSym, stems)
            if (qtf.isEmpty()) return null
            var sq = 0.0
            val q = HashMap<String, Double>(qtf.size)
            for ((t, f) in qtf) {
                val w = f * (idf[t] ?: 0.0)
                if (w <= 0) continue
                q[t] = w
                sq += w * w
            }
            if (q.isEmpty()) return null
            val qnorm = sqrt(sq).takeIf { it > 0 } ?: 1.0

            val scores = HashMap<Int, Double>()
            for ((t, w) in q) {
                val postings = inverted[t] ?: continue
                val qw = w / qnorm
                for ((i, dw) in postings) scores[i] = (scores[i] ?: 0.0) + qw * dw
            }
            if (scores.isEmpty()) return null

            val ranked = scores.entries.sortedByDescending { it.value }.take(K)
            val bestSim = ranked.first().value
            if (bestSim < MIN_SIM) return null

            // Neighbours vote with sim², the document's own weight, and against category size.
            val byCat = HashMap<String, Double>()
            var total = 0.0
            for ((i, sim) in ranked) {
                val w = sim.pow(SIM_POWER) * weights[i] / (prior[slugs[i]] ?: 1.0).pow(PRIOR_ALPHA)
                byCat[slugs[i]] = (byCat[slugs[i]] ?: 0.0) + w
                total += w
            }
            val order = byCat.entries.sortedByDescending { it.value }
            val top = order.first()
            val confidence = if (total > 0) top.value / total else 0.0
            if (confidence < minConf) return null

            val bestIdx = ranked.first().key
            return Suggestion(
                slug = top.key, confidence = confidence, tier = tierOf(confidence),
                nearestQuestion = questions[bestIdx], nearestSlug = slugs[bestIdx],
                nearestSource = sources[bestIdx], nearestSim = bestSim,
            )
        }
        return Built(n, ::suggest)
    }

    private const val K = 20
    private const val SIM_POWER = 2.0
    private const val PRIOR_ALPHA = 0.4
    private const val MIN_DF = 2
    private const val MIN_SIM = 0.08
    private const val MIN_CONF = 0.34
    private const val W_QUESTION = 1.0

    /** Weighted term frequency; option/explanation tokens are namespaced so they never merge. */
    private fun featureFreq(
        question: String?, options: List<String>, explanation: String?,
        wExplanation: Double, wOptions: Double, wSymbol: Double, stemLen: List<Int>,
    ): Map<String, Double> {
        val tf = HashMap<String, Freq>()
        fun field(text: String?, prefix: String, weight: Double) {
            if (text.isNullOrEmpty()) return
            for (t in tokenize(text, stemLen)) {
                val key = prefix + t
                val cur = tf[key]
                tf[key] = if (cur != null) Freq(cur.n + 1, cur.w) else Freq(1, weight)
            }
        }
        field(question, "", W_QUESTION)
        field(explanation, "e:", wExplanation)
        if (options.isNotEmpty()) field(options.joinToString("   "), "o:", wOptions)
        if (wSymbol > 0) {
            // One notation space across all three fields: a θ in the working is the same evidence
            // as a θ in the question. Trig names share one extra feature (#trig).
            val text = listOf(question, explanation, options.joinToString(" ")).map { stripTags(it) }.joinToString(" ")
            for (m in SYMBOL_RE.findAll(Normalizer.normalize(text, Normalizer.Form.NFC))) {
                val sym = m.value.lowercase()
                bump(tf, "#$sym", wSymbol)
                if (sym in TRIG_SYMBOLS) bump(tf, "#trig", wSymbol)
            }
        }
        // Case- and punctuation-sensitive, on the raw question: analogy items are মানসিক দক্ষতা,
        // and stripped of punctuation a bag of words has nothing to go on.
        if (question != null && ANALOGY_RE.containsMatchIn(stripTags(question))) tf[ANALOGY_KEY] = Freq(1, W_ANALOGY)

        // Sublinear term frequency, then scale by the field the token came from.
        val out = HashMap<String, Double>(tf.size)
        for ((t, f) in tf) out[t] = (1 + ln(f.n.toDouble())) * f.w
        return out
    }

    private fun bump(tf: HashMap<String, Freq>, key: String, w: Double) {
        val cur = tf[key]
        tf[key] = if (cur != null) Freq(cur.n + 1, cur.w) else Freq(1, w)
    }

    private class Freq(val n: Int, val w: Double)
}
