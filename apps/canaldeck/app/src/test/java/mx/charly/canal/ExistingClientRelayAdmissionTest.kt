package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class ExistingClientRelayAdmissionTest {
    private val now = 1_792_695_600L
    private fun observed(id: Long = 102, date: Long = now + 1, session: String = "generation-a") =
        SingleRelayObserved(7, session, -801, id, date)

    @Test fun incomingCallbackCannotArmItsOwnSource() {
        val ledger = MemoryRelayLedger(); val fence = ExistingClientRelayAdmission(ledger)
        assertFalse(fence.admit(observed()))
        assertNull(ledger.baseline(7, -801))
    }
    @Test fun loadedBaselineIsFixedSoReorderedLiveCallbacksAreNotLost() {
        val ledger = MemoryRelayLedger(); val fence = ExistingClientRelayAdmission(ledger)
        fence.armLoaded(7, "generation-a", -801, 100, now)
        assertTrue(fence.admit(observed(105)))
        assertTrue(fence.admit(observed(102)))
        assertEquals(100L, ledger.baseline(7, -801)!!.latestMessageId)
        assertFalse(fence.admit(observed(105)))
    }
    @Test fun loadedChatRefreshDoesNotAdvanceAlreadyArmedEpoch() {
        val ledger = MemoryRelayLedger(); val fence = ExistingClientRelayAdmission(ledger)
        fence.armLoaded(7, "generation-a", -801, 100, now)
        fence.armLoaded(7, "generation-a", -801, 105, now + 1)
        assertTrue(fence.admit(observed(102)))
    }
    @Test fun activationSecondHistoryAndAlbumsStayHeld() {
        val ledger = MemoryRelayLedger(); val fence = ExistingClientRelayAdmission(ledger)
        fence.armLoaded(7, "generation-a", -801, 100, now)
        assertFalse(fence.admit(observed(101, now)))
        assertFalse(fence.admit(observed(99)))
        assertFalse(fence.admit(observed().copy(albumId = 900)))
        assertFalse(fence.admit(observed().copy(edited = true)))
        assertFalse(fence.admit(observed().copy(incoming = false)))
    }
    @Test fun reconnectRequiresLoadedNewEpochAndNeverRewindsBaselineOrDuplicates() {
        val ledger = MemoryRelayLedger(); val fence = ExistingClientRelayAdmission(ledger)
        fence.armLoaded(7, "generation-a", -801, 100, now)
        assertTrue(fence.admit(observed(102)))
        fence.reset()
        assertFalse(fence.admit(observed(103, session = "generation-b")))
        fence.armLoaded(7, "generation-b", -801, 90, now + 10)
        assertEquals(100L, ledger.baseline(7, -801)!!.latestMessageId)
        assertFalse(fence.admit(observed(103, now + 5, "generation-b")))
        assertFalse(fence.admit(observed(102, now + 11, "generation-b")))
        assertFalse(fence.admit(observed(104, now + 11, "generation-a")))
        assertTrue(fence.admit(observed(104, now + 11, "generation-b")))
    }
}
