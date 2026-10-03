package com.syed.anvil

import com.syed.anvil.content.Grade
import com.syed.anvil.content.Item
import com.syed.anvil.content.PoolSet
import com.syed.anvil.content.QuizPool
import com.syed.anvil.content.SearchText
import com.syed.anvil.content.correctAnswer
import com.syed.anvil.content.optionList
import com.syed.anvil.progress.Flag
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
}
