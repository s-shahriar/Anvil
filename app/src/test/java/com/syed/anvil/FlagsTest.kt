package com.syed.anvil

import com.syed.anvil.progress.Flag
import com.syed.anvil.progress.FlagRules
import com.syed.anvil.progress.PendingQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagsTest {
    @Test fun weakImpliesImportant() {
        val f = FlagRules.toggleWeak(Flag())
        assertTrue(f.weak && f.important)
    }

    @Test fun unmarkingImportantClearsWeak() {
        val f = FlagRules.toggleImportant(FlagRules.toggleWeak(Flag()))
        assertFalse(f.important); assertFalse(f.weak)
    }

    @Test fun nailingClearsWeakButKeepsImportant() {
        val f = FlagRules.toggleNailed(FlagRules.toggleWeak(Flag()))
        assertTrue(f.nailed && f.important); assertFalse(f.weak)
    }

    @Test fun emptyNoteBecomesNull() {
        assertEquals(null, FlagRules.setNote(Flag(note = "x"), "   ").note)
    }

    @Test fun diffOnlyHasChangedColumns() {
        val old = Flag(important = true)
        val patch = FlagRules.diff(old, FlagRules.toggleNailed(old))
        assertEquals(mapOf<String, Any?>("nailed" to true), patch)
        assertEquals(FlagRules.toggleNailed(old), FlagRules.apply(old, patch))
    }

    @Test fun laterEditsWinPerColumn() {
        val q = PendingQueue()
        q.add("u1", mapOf("nailed" to true))
        q.add("u1", mapOf("nailed" to false, "note" to "hi"))
        assertEquals(mapOf<String, Any?>("nailed" to false, "note" to "hi"), q.patchFor("u1"))
    }

    @Test fun sentEntriesAreDroppedUnlessEditedMeanwhile() {
        val q = PendingQueue()
        q.add("a", mapOf("nailed" to true)); q.add("b", mapOf("important" to true))
        val sent = q.snapshot()
        q.add("b", mapOf("important" to false))
        q.remove(sent)
        assertEquals(null, q.patchFor("a"))
        assertEquals(mapOf<String, Any?>("important" to false), q.patchFor("b"))
    }

    @Test fun batchesGroupRowsByColumnSet() {
        val b = PendingQueue.batches(
            mapOf(
                "a" to mapOf("nailed" to true),
                "b" to mapOf("nailed" to false),
                "c" to mapOf("important" to true, "weak" to true),
            ),
            "user-1",
        )
        assertEquals(2, b.size)
        assertEquals(listOf(2, 1), b.map { it.length() }.sortedDescending())
    }
}
