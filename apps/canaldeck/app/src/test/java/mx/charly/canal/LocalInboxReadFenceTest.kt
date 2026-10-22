package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class LocalInboxReadFenceTest {
    @Test fun readUpdateBlocksDelayedLinkWhileChatRefreshStillHasTheOldBoundary() {
        val fence = LocalInboxReadFence()
        val chat = -111L
        val cachedReadBeforeRpc = 100L
        val pendingMessage = 101L
        assertTrue(pendingMessage > fence.boundary(1, chat, cachedReadBeforeRpc))
        fence.observe(1, chat, 102L)
        // A late callback or older refresh cannot reopen either read message.
        assertFalse(pendingMessage > fence.boundary(1, chat, cachedReadBeforeRpc))
        fence.observe(1, chat, 99L)
        assertEquals(102L, fence.boundary(1, chat, 100L))
        assertTrue(103L > fence.boundary(1, chat, 100L))
    }

    @Test fun anotherAccountOrChatDoesNotInheritAReadUpdate() {
        val fence = LocalInboxReadFence()
        fence.observe(1, -111, 102)
        assertEquals(0L, fence.boundary(2, -111, 0))
        assertEquals(0L, fence.boundary(1, -222, 0))
        assertEquals(110L, fence.boundary(1, -111, 110))
    }
}
