package com.syed.slate

import com.syed.slate.content.Item
import com.syed.slate.content.LongForm
import com.syed.slate.content.QBlock
import com.syed.slate.content.splitQuestionBlocks
import com.syed.slate.ui.rich.CodeTokens
import com.syed.slate.ui.rich.TokenKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LongFormTest {
    private fun card(id: String, seg: String? = null, sub: String? = null, answer: JSONObject = JSONObject()) = Item(
        id, "written:$id", "written", "c_programming", 0,
        JSONObject().put("q", "Question $id").put("answer", answer).apply {
            seg?.let { put("segment", it) }; sub?.let { put("subsegment", it) }
        },
    )

    @Test fun questionBlocksFollowTheDataConvention() {
        val b = splitQuestionBlocks("Given the table:\n\n  a) one\n  b) two\nExplain.\n\n")
        assertEquals(
            listOf(QBlock.Para("Given the table:"), QBlock.Gap, QBlock.ListItem("a) one"), QBlock.ListItem("b) two"), QBlock.Para("Explain.")),
            b,
        )
        assertEquals(listOf(QBlock.Para("one line")), splitQuestionBlocks("one line"))
    }

    @Test fun segmentsLeaveTheRegularListAndSplitIntoSubgroups() {
        val l = LongForm.layout(listOf(card("1"), card("2", "Time Complexity", "Loops"), card("3", "Time Complexity", "Recursion"), card("4", "Time Complexity", "Loops")))
        assertEquals(listOf("1"), l.regular.map { it.id })
        val seg = l.segments.single()
        assertEquals(3, seg.count)
        assertEquals(listOf("Loops", "Recursion"), seg.subgroups.map { it.name })
        assertEquals(listOf("2", "4"), seg.subgroups[0].items.map { it.id })
    }

    @Test fun answerTextCoversEveryPart() {
        val a = JSONObject().put("summary", JSONArray().put("alpha").put("beta"))
            .put("points", JSONArray().put("gamma").put(JSONObject().put("sub", "delta")).put(JSONObject().put("code", "int x;").put("codeLang", "c")))
            .put("table", JSONObject().put("headers", JSONArray().put("H1")).put("rows", JSONArray().put(JSONArray().put("epsilon"))))
        val t = LongForm.answerText(card("1", answer = a))
        listOf("alpha", "beta", "gamma", "delta", "int x;", "H1", "epsilon").forEach { assertTrue(it, it in t) }
        assertTrue("codeLang is not content", " c " !in " $t ".replace("int x;", ""))
    }

    @Test fun missingOptionalFieldsAreNull() {
        assertNull(card("1").let { with(com.syed.slate.content.LongForm) { it.data.optString("segment").ifEmpty { null } } })
    }

    @Test fun cTokensCoverTheBasics() {
        val code = "#include <stdio.h>\nint main() { // hi\n  printf(\"x%d\", 42); return 0; }"
        val kinds = CodeTokens.tokenize(code, "c").associate { code.substring(it.start, it.end) to it.kind }
        assertEquals(TokenKind.META, kinds["#include <stdio.h>"])
        assertEquals(TokenKind.TYPE, kinds["int"])
        assertEquals(TokenKind.KEYWORD, kinds["return"])
        assertEquals(TokenKind.FUNCTION, kinds["printf"])
        assertEquals(TokenKind.STRING, kinds["\"x%d\""])
        assertEquals(TokenKind.NUMBER, kinds["42"])
        assertEquals(TokenKind.COMMENT, kinds["// hi"])
    }

    @Test fun sqlKeywordsAreCaseInsensitiveAndUseDashComments() {
        val code = "SELECT name FROM t WHERE id = 7 -- c\nand x = 'it''s'"
        val kinds = CodeTokens.tokenize(code, "sql").associate { code.substring(it.start, it.end) to it.kind }
        assertEquals(TokenKind.KEYWORD, kinds["SELECT"]); assertEquals(TokenKind.KEYWORD, kinds["and"])
        assertEquals(TokenKind.COMMENT, kinds["-- c"]); assertEquals(TokenKind.STRING, kinds["'it''s'"])
    }

    @Test fun unknownLanguageHasNoTokens() {
        assertTrue(CodeTokens.tokenize("anything 1", "python").isEmpty())
    }
}
