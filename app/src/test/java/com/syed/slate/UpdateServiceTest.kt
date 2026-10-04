package com.syed.slate

import com.syed.slate.update.UpdateService.Companion.compareVersions
import com.syed.slate.update.UpdateService.Companion.htmlToPlainText
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateServiceTest {
    @Test fun comparesDottedVersions() {
        assertEquals(1, compareVersions("0.2.0", "0.1.9"))
        assertEquals(-1, compareVersions("0.1.0", "0.1.1"))
        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertEquals(1, compareVersions("1.10.0", "1.9.9"))
    }

    @Test fun releaseNotesBecomePlainText() {
        assertEquals("• one\n• two", htmlToPlainText("&lt;ul&gt;&lt;li&gt;one&lt;/li&gt;&lt;li&gt;two&lt;/li&gt;&lt;/ul&gt;"))
    }
}
