package com.syed.slate

import com.syed.slate.ui.rich.Block
import com.syed.slate.ui.rich.HtmlParser
import com.syed.slate.ui.rich.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserTest {
    private fun paragraphs(html: String) = HtmlParser.parse(html).filterIsInstance<Block.Paragraph>()

    @Test fun superscriptKeepsItsStyle() {
        val p = paragraphs("If 6<sup>th</sup> March").single()
        assertEquals("If 6th March", p.text)
        assertEquals(Script.SUP, p.runs.first { it.text == "th" }.script)
    }

    @Test fun breaksAndParagraphsAreSeparate() {
        val p = paragraphs("<p><strong>Question:</strong> a <br><br><strong>Solution:</strong><br>b</p>\r\n<p>c</p>")
        assertEquals(2, p.size)
        assertEquals("Question: a\n\nSolution:\nb", p[0].text)
        assertEquals("c", p[1].text)
        assertTrue(p[0].runs.first().bold)
    }

    @Test fun whitespaceBetweenTagsCollapses() {
        assertEquals(listOf("one", "two"), paragraphs("<div>\r\n<p>one</p>\r\n<p>  two  </p>\r\n</div>").map { it.text })
    }

    @Test fun imagesBecomeTheirOwnBlock() {
        val b = HtmlParser.parse("""before <img src="https://x/y.png" width="10"> after""")
        assertEquals(3, b.size)
        assertEquals("https://x/y.png", (b[1] as Block.Image).url)
    }

    @Test fun entitiesAreDecoded() {
        assertEquals("a & b < c “q” é", HtmlParser.plainText("a &amp; b &lt; c &ldquo;q&rdquo; &#233;"))
    }

    @Test fun unknownTagsKeepTheirText() {
        assertEquals("0", HtmlParser.plainText("<span>0</span>"))
    }

    @Test fun plainBanglaPassesThrough() {
        assertEquals("নিচের কোন বানানগুচ্ছটি সঠিক?", HtmlParser.plainText("নিচের কোন বানানগুচ্ছটি সঠিক?"))
    }
}
