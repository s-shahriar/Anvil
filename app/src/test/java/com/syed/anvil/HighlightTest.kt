package com.syed.anvil

import com.syed.anvil.highlight.Anchor
import com.syed.anvil.highlight.Highlight
import com.syed.anvil.highlight.HtmlAlign
import com.syed.anvil.ui.rich.Block
import com.syed.anvil.ui.rich.HtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighlightTest {
    private fun h(id: String, s: Int, e: Int, q: String, c: String = "mint") = Highlight(id, "u", "b", s, e, q, c)

    @Test fun offsetsStillPointingAtTheQuoteAreUsed() {
        assertEquals(4 until 9, Anchor.resolve("the quick brown fox", h("1", 4, 9, "quick")))
    }

    @Test fun shiftedTextIsFoundByQuoteOnlyWhenUnambiguous() {
        assertEquals(12 until 17, Anchor.resolve("a new intro quick brown", h("1", 4, 9, "quick")))
        val t = "some prefix, quick fox"; val at = t.indexOf("quick")
        assertEquals(at until at + 5, Anchor.resolve(t, h("1", 4, 9, "quick")))
        assertNull(Anchor.resolve("quick and quick again", h("1", 40, 45, "quick")))   // appears twice: orphaned
        assertNull(Anchor.resolve("nothing here", h("1", 0, 5, "quick")))               // gone: orphaned, never deleted
    }

    @Test fun overlappingAndTouchingMarksMergeAndKeepTheEarliestColour() {
        val bands = Anchor.bandsFor("0123456789", listOf(h("b", 4, 8, "4567", "rose"), h("a", 2, 5, "234", "amber"), h("c", 9, 10, "9", "mint")))
        assertEquals(2, bands.size)
        assertEquals(listOf("a", "b"), bands[0].ids); assertEquals(2, bands[0].start); assertEquals(8, bands[0].end); assertEquals("amber", bands[0].color)
    }

    @Test fun trimmingDropsEdgeWhitespace() {
        assertEquals(Triple(3, 8, "hello"), Anchor.trimmed("a  hello  b", 1, 10))
        assertNull(Anchor.trimmed("   ", 0, 3))
    }

    @Test fun htmlRawTextIsTheBrowsersTextContent() {
        assertEquals("<p>one</p>\r\n<p>two &amp; three</p>".let { HtmlAlign.rawText(it) }, "one\r\ntwo & three")
    }

    @Test fun displayedParagraphsAlignToRawOffsets() {
        val html = "<p>Hello   <b>big</b> world</p>\r\n<p>Second one</p>"
        val raw = HtmlAlign.rawText(html)
        val paras = HtmlParser.parse(html).filterIsInstance<Block.Paragraph>()
        val map = HtmlAlign.align(raw, paras)
        // "Hello big world": 'b' of "big" is raw index 9 ("Hello   " is 8 chars, then "big")
        assertEquals("Hello big world", paras[0].text)
        assertEquals('b', raw[map[0][paras[0].text.indexOf("big")]])
        assertEquals('S', raw[map[1][0]])
        assertEquals(raw.indexOf("Second"), map[1][0])
    }
}
