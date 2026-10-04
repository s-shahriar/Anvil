package com.syed.slate

import com.syed.slate.content.Item
import com.syed.slate.content.Subtopic
import com.syed.slate.content.Subtopics
import com.syed.slate.ui.theme.TopicColors
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtopicsTest {
    private fun row(slug: String, name: String, sort: Int, cat: String) =
        JSONObject().put("slug", slug).put("name", name).put("sort_order", sort).put("categories", JSONObject().put("slug", cat))
    private fun q(id: String, sub: String?) = Item(id, id, "livemcq", "lm_bangla_byakoron", 0,
        JSONObject().put("question", id).apply { sub?.let { put("extra", JSONObject().put("subtopic", it)) } })

    @Test fun listIsPerTopicAndOrderedBySortOrder() {
        val rows = listOf(row("somas", "সমাস", 2, "lm_bangla_byakoron"), row("sondhi", "সন্ধি", 1, "lm_bangla_byakoron"), row("x", "X", 1, "lm_math"))
        assertEquals(listOf("sondhi", "somas"), Subtopics.forTopic(rows, "lm_bangla_byakoron").map { it.slug })
    }

    @Test fun cardsCountEmptyHiddenAndOtherCollectsTheRest() {
        val list = listOf(Subtopic("sondhi", "সন্ধি"), Subtopic("somas", "সমাস"), Subtopic("empty", "ফাঁকা"))
        val items = listOf(q("1", "sondhi"), q("2", "sondhi"), q("3", "somas"), q("4", null), q("5", "gone"))
        val cards = Subtopics.cards(items, list).map { it.first.slug to it.second }
        assertEquals(listOf("sondhi" to 2, "somas" to 1, Subtopics.NONE to 2), cards)
        assertTrue(Subtopics.inSubtopic(items[3], Subtopics.NONE, list))
        assertTrue(Subtopics.inSubtopic(items[4], Subtopics.NONE, list))
        assertTrue(!Subtopics.inSubtopic(items[0], Subtopics.NONE, list))
    }

    @Test fun topicColorsParseAndDiffer() {
        val fb = Color.Magenta
        assertEquals(TopicColors.of(8, false), TopicColors.parse("var(--topic-8)", false, fb))
        assertEquals(Color(0xFF123456), TopicColors.parse("#123456", false, fb))
        assertEquals(fb, TopicColors.parse("rebeccapurple", false, fb))
        assertNotEquals(TopicColors.of(1, false), TopicColors.of(2, false))
        assertNotEquals(TopicColors.of(3, false), TopicColors.of(3, true)) // lighter in dark mode
        assertEquals(TopicColors.of(1, true), TopicColors.of(13, true))    // wraps
    }
}
