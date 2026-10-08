package com.syed.slate

import com.syed.slate.content.Grade
import com.syed.slate.content.Item
import com.syed.slate.content.PoolSet
import com.syed.slate.content.QuizPool
import com.syed.slate.content.SearchText
import com.syed.slate.content.correctAnswer
import com.syed.slate.content.optionList
import com.syed.slate.progress.Flag
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTest {
    private fun item(uid: String, answer: String? = "a", options: Boolean = true) = Item(
        uid, uid, "bangla", "karak", 0,
        JSONObject().put("question", "q $uid")
            .apply { if (options) put("options", JSONObject().put("b", "two").put("a", "one").put("e", "five")) }
            .put("correct_answer", answer ?: JSONObject.NULL),
    )

    @Test fun questionsWithoutAnswerOrOptionsAreNotQuizzable() {
        assertTrue(item("a").isQuizzable)
        assertFalse(item("b", answer = null).isQuizzable)
        assertFalse(item("c", options = false).isQuizzable)
    }

    @Test fun optionsComeInLetterOrder() {
        assertEquals(listOf("a", "b", "e"), item("a").optionList().map { it.first })
        assertNull(item("x", answer = null).correctAnswer)
    }

    @Test fun poolFiltersByFlag() {
        val items = listOf(item("1"), item("2"), item("3"), item("4", answer = null))
        val flags = mapOf(
            "1" to Flag(important = true),
            "2" to Flag(important = true, weak = true),
            "3" to Flag(nailed = true),
            "4" to Flag(important = true),
        )
        fun uids(s: PoolSet) = QuizPool.build(items, flags, s).map { it.uid }.toSet()
        assertEquals(setOf("1", "2", "3"), uids(PoolSet.ALL))
        assertEquals(setOf("1", "2"), uids(PoolSet.IMPORTANT))
        assertEquals(setOf("2"), uids(PoolSet.WEAK))
        assertEquals(setOf("3"), uids(PoolSet.NAILED))
        assertEquals(mapOf(PoolSet.ALL to 3, PoolSet.IMPORTANT to 2, PoolSet.WEAK to 1, PoolSet.NAILED to 1), QuizPool.counts(items, flags))
    }

    @Test fun weakWithoutImportantIsIgnored() {
        assertFalse(QuizPool.matches(PoolSet.WEAK, Flag(weak = true, important = false)))
    }

    @Test fun gradeBands() {
        assertEquals(Grade.EXCELLENT, Grade.of(80)); assertEquals(Grade.GOOD, Grade.of(79))
        assertEquals(Grade.OK, Grade.of(40)); assertEquals(Grade.LOW, Grade.of(39))
    }

    @Test fun searchIgnoresPunctuationOrderAndCase() {
        val h = SearchText.normalize("মুক্তিযুদ্ধ বিষয়ক কাব্যগ্রন্থ কোনটি?")
        assertTrue(SearchText.matches(h, SearchText.tokens("কোনটি। মুক্তিযুদ্ধ")))
        assertFalse(SearchText.matches(h, SearchText.tokens("কাব্যগ্রন্থ ইংরেজি")))
        assertTrue(SearchText.matches(SearchText.normalize("Pellucid এর অর্থ কি?"), SearchText.tokens("PELLUCID")))
        assertFalse(SearchText.matches("anything", SearchText.tokens("  ")))
    }

    // ── Deck guarantees: every question exactly once, and a rebuild never reshuffles under you ──

    private fun row(id: String, uid: String, opts: String) = Item(
        id, uid, "bangla", "banan", 0,
        JSONObject().put("question", "কোনটি শুদ্ধ বানান?").put("options", JSONObject().put("a", opts).put("b", "x")).put("correct_answer", "a"),
    )

    @Test fun deckHoldsEveryQuestionExactlyOnce() {
        val items = (1..500).map { item("u$it") }
        val deck = QuizPool.build(items, emptyMap(), PoolSet.ALL, seed = 42)
        assertEquals(500, deck.size)
        assertEquals(items.map { it.id }.toSet(), deck.map { it.id }.toSet())
    }

    @Test fun sameSeedGivesSameOrderAndOtherSeedsShuffle() {
        val items = (1..200).map { item("u$it") }
        val a = QuizPool.build(items, emptyMap(), PoolSet.ALL, seed = 7).map { it.id }
        assertEquals(a, QuizPool.build(items.reversed(), emptyMap(), PoolSet.ALL, seed = 7).map { it.id })
        assertFalse(a == QuizPool.build(items, emptyMap(), PoolSet.ALL, seed = 8).map { it.id })
        assertFalse(a == items.map { it.id }) // actually shuffled
    }

    @Test fun deletingAQuestionKeepsTheRestInPlace() {
        val items = (1..100).map { item("u$it") }
        val before = QuizPool.build(items, emptyMap(), PoolSet.ALL, seed = 3).map { it.id }
        val gone = before[10]
        val after = QuizPool.build(items.filter { it.id != gone }, emptyMap(), PoolSet.ALL, seed = 3).map { it.id }
        assertEquals(before - gone, after)
    }

    @Test fun exactCopiesCountOnceButSameStemQuestionsAllStay() {
        // Same uid (stem hash) with different options = different questions; same uid AND options = a duplicate row.
        val items = listOf(row("1", "qX", "বানান ক"), row("2", "qX", "বানান খ"), row("3", "qX", "বানান ক"))
        val deck = QuizPool.build(items, emptyMap(), PoolSet.ALL, seed = 1)
        assertEquals(2, deck.size)
        assertEquals(setOf("বানান ক", "বানান খ"), deck.map { it.data.getJSONObject("options").getString("a") }.toSet())
        assertEquals(2, QuizPool.counts(items, emptyMap()).getValue(PoolSet.ALL))
    }

    @Test fun runningDeckIsNotRebuilt() {
        val items = (1..20).map { item("u$it") }
        val first = com.syed.slate.content.QuizDecks.get(99L) { QuizPool.build(items, emptyMap(), PoolSet.ALL, 99L) }
        // Content changed (one deleted): the running deck must come back unchanged.
        val again = com.syed.slate.content.QuizDecks.get(99L) { QuizPool.build(items.drop(1), emptyMap(), PoolSet.ALL, 99L) }
        assertTrue(first === again)
    }
}
