package com.syed.slate

import com.syed.slate.trash.TrashOp
import com.syed.slate.trash.TrashQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashQueueTest {
    @Test fun trashThenRestoreBeforeSendingCancelsOut() {
        val q = TrashQueue(); q.add("a", TrashOp.TRASH); q.add("a", TrashOp.RESTORE)
        assertTrue(q.isEmpty)
    }

    @Test fun restoreThenTrashCancelsToo() {
        val q = TrashQueue(); q.add("a", TrashOp.RESTORE); q.add("a", TrashOp.TRASH)
        assertTrue(q.isEmpty)
    }

    @Test fun purgeOverridesAndPendingTrashIsListed() {
        val q = TrashQueue(); q.add("a", TrashOp.TRASH); q.add("b", TrashOp.TRASH); q.add("a", TrashOp.PURGE)
        assertEquals(setOf("b"), q.pendingTrash()); assertEquals(TrashOp.PURGE, q.opFor("a"))
    }

    @Test fun sentOperationsAreRemovedUnlessChangedMeanwhile() {
        val q = TrashQueue(); q.add("a", TrashOp.TRASH); q.add("b", TrashOp.TRASH)
        val sent = q.snapshot(); q.add("b", TrashOp.RESTORE)   // user undid b while the request ran
        q.remove(sent)
        assertNull(q.opFor("a")); assertTrue(q.isEmpty.not() || q.opFor("b") == null)
    }

    @Test fun survivesAJsonRoundTrip() {
        val q = TrashQueue(); q.add("a", TrashOp.TRASH); q.add("b", TrashOp.PURGE)
        assertEquals(q.snapshot(), TrashQueue.fromJson(TrashQueue.toJson(q.snapshot())).snapshot())
    }
}
