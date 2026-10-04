package com.syed.slate

import com.syed.slate.ui.screen.LivemcqAdmin
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminImportTest {
    @Test fun livefavArrayNormalizes() {
        val raw = JSONObject("""{"favorite_id":172669302,"question":"Q?","options":["a1","b1","c1","d1",""],"answer":2,"explanation":null}""")
        val n = LivemcqAdmin.normalizeItem(raw)
        assertEquals("172669302", n.favoriteId)
        assertEquals(listOf("a1", "b1", "c1", "d1"), n.options)
        assertTrue(n.hasKey); assertFalse(n.gapWarning); assertFalse(n.answerOutOfRange)
        assertEquals("", n.explanation)
        val row = LivemcqAdmin.toInsertRow(n, "lm_x", null)
        assertEquals("b", row.getString("correct_answer")) // lowercase, like the web and the database
        assertEquals("b1", row.getString("correct_answer_text"))
        assertEquals("a1", row.getJSONObject("options").getString("a"))
    }

    @Test fun rawApiShapeAndWarnings() {
        val raw = JSONObject("""{"favoriteId":"9","question":"Q","option1":"x","option2":"","option3":"z","answer":5,"exp":"why"}""")
        val n = LivemcqAdmin.normalizeItem(raw)
        assertTrue(n.gapWarning); assertTrue(n.answerOutOfRange); assertFalse(n.hasKey)
        assertEquals("why", n.explanation)
    }

    @Test fun extractsQuestionListWrapper() {
        assertEquals(2, LivemcqAdmin.extractRawItems(JSONObject("""{"question_list":[{},{}]}""")).size)
        assertEquals(1, LivemcqAdmin.extractRawItems(JSONObject("""{"question":"x"}""")).size)
        assertEquals(3, LivemcqAdmin.extractRawItems(JSONArray("[1,2,3]")).size)
    }
}
