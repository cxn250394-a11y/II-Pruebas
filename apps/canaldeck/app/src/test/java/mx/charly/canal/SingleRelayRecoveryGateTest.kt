package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

/** Metadata-only fixtures: no TDLib client, account connection, send or automatic retry. */
class SingleRelayRecoveryGateTest {
    private class Store : SingleRelayRecoveryStore {
        val entries = mutableMapOf<Long, SingleRelayRecoveryToken>()
        var failRead = false
        var failWrite = false
        var writes = 0
        override fun read(accountId: Long): SingleRelayRecoveryToken? {
            check(!failRead)
            return entries[accountId]
        }
        override fun compareAndSet(accountId: Long, expected: SingleRelayRecoveryToken?, next: SingleRelayRecoveryToken?): Boolean {
            writes++
            if (failWrite || entries[accountId] != expected) return false
            if (next == null) entries.remove(accountId) else entries[accountId] = next
            return true
        }
    }
    private val before = AsyncRelayScope(7, "generation-before", -900)
    private val after = before.copy(sessionId = "generation-after")
    private fun context(scope: AsyncRelayScope = after, revision: Long = 12, enabled: Boolean = true) =
        ExistingClientRelayContext(scope, RelayConfig(scope.accountId, scope.destinationChatId, enabled,
            listOf(RelaySourceBinding(scope.accountId, -801, "Fixture source", RelaySourcePolicy.FANO_DIRECT, true))),
            revision, true)
    private data class Fixture(val store: Store, val gate: SingleRelayRecoveryGate, val token: SingleRelayRecoveryToken)
    private fun fixture(): Fixture {
        val store = Store()
        val gate = SingleRelayRecoveryGate(store, "client-lifetime-a")
        return Fixture(store, gate, gate.pause(before, 11)!!)
    }
    private fun consent(token: SingleRelayRecoveryToken, scope: AsyncRelayScope = after, revision: Long = 12) =
        SingleRelayRecoveryConsent(token, scope, revision)

    @Test fun reconnectObservationCannotClearPauseOrChangeStoredOwnerIntent() {
        val f = fixture()
        repeat(20) { f.gate.observeTransport(context()) }
        assertTrue(f.gate.isPaused(7))
        assertEquals(f.token, f.store.entries[7])
        assertEquals(1, f.store.writes)
    }

    @Test fun explicitMatchingConsentCanResumeFreshGenerationOnlyOnce() {
        val f = fixture(); val current = context(); var resumes = 0
        f.gate.observeTransport(current)
        assertTrue(f.gate.resume(consent(f.token), current, true) { resumes++; true })
        assertFalse(f.gate.isPaused(7))
        assertFalse(f.gate.resume(consent(f.token), current, true) { resumes++; true })
        assertEquals(1, resumes)
    }

    @Test fun restartWithAnotherClientLifetimeCannotClearOrRewriteDurablePause() {
        val f = fixture(); val restarted = SingleRelayRecoveryGate(f.store, "client-lifetime-b")
        restarted.observeTransport(context())
        assertTrue(restarted.isPaused(7))
        assertFalse(restarted.resume(consent(f.token), context(), true) { error("No router mutation") })
        assertEquals(f.token, restarted.pause(after, 12))
        assertEquals(f.token, f.store.entries[7])
    }

    @Test fun unsettledUnknownTemporaryIdCannotBeReleasedByConsent() {
        val f = fixture(); val current = context(); f.gate.observeTransport(current)
        assertFalse(f.gate.resume(consent(f.token), current, false) { error("Quarantined router") })
        assertTrue(f.gate.isPaused(7))
    }

    @Test fun staleNonceOrPauseGenerationCannotApproveNewPause() {
        val f = fixture(); val current = context()
        val newer = f.gate.pause(before, 11)!!
        assertNotEquals(f.token.nonce, newer.nonce)
        f.gate.observeTransport(current)
        assertFalse(f.gate.resume(consent(f.token), current, true) { error("Stale consent") })
        assertEquals(newer, f.gate.token(7))
    }

    @Test fun wrongAccountOrDestinationOrSessionCannotBorrowConsent() {
        val f = fixture(); val current = context(); f.gate.observeTransport(current)
        listOf(after.copy(accountId = 8), after.copy(destinationChatId = -901), after.copy(sessionId = "other"))
            .forEach { scope ->
                assertFalse(f.gate.resume(consent(f.token, scope), current, true) { error("Scope mismatch") })
            }
        assertTrue(f.gate.isPaused(7))
    }

    @Test fun sameOldSessionIsNotAFreshGenerationEvenWithExplicitConsent() {
        val f = fixture(); val old = context(before); f.gate.observeTransport(old)
        assertFalse(f.gate.resume(consent(f.token, before), old, true) { error("Old callbacks stay revoked") })
    }

    @Test fun changedRevisionOrOlderRevisionCannotUseAnEarlierConsent() {
        val f = fixture(); val current = context(); f.gate.observeTransport(current)
        assertFalse(f.gate.resume(consent(f.token, revision = 13), current, true) { error("Revision changed") })
        val older = context(revision = 10); f.gate.observeTransport(older)
        assertFalse(f.gate.resume(consent(f.token, revision = 10), older, true) { error("Rollback") })
    }

    @Test fun disabledDisconnectedOrUnobservedTransportCannotResume() {
        val f = fixture(); val current = context()
        assertFalse(f.gate.resume(consent(f.token), current, true) { error("No transport observation") })
        val disabled = context(enabled = false); f.gate.observeTransport(disabled)
        assertFalse(f.gate.resume(consent(f.token), disabled, true) { error("Owner output OFF") })
        val disconnected = current.copy(connected = false); f.gate.observeTransport(disconnected)
        assertFalse(f.gate.resume(consent(f.token), disconnected, true) { error("Disconnected") })
    }

    @Test fun transportLossAfterObservationInvalidatesConsentWithoutRouterMutation() {
        val f = fixture(); val current = context(); f.gate.observeTransport(current)
        f.gate.observeTransport(null)
        assertFalse(f.gate.resume(consent(f.token), current, true) { error("Transport was lost") })
    }

    @Test fun rejectedRouterResumePreservesDurablePause() {
        val f = fixture(); val current = context(); f.gate.observeTransport(current)
        assertFalse(f.gate.resume(consent(f.token), current, true) { false })
        assertEquals(f.token, f.store.entries[7]); assertTrue(f.gate.isPaused(7))
    }

    @Test fun failedDurableClearAfterRouterResumeRemainsBlockedAndNeverRetries() {
        val f = fixture(); val current = context(); f.gate.observeTransport(current)
        f.store.failWrite = true; var resumes = 0
        assertFalse(f.gate.resume(consent(f.token), current, true) { resumes++; true })
        assertTrue(f.gate.isPaused(7)); assertEquals(f.token, f.store.entries[7])
        assertFalse(f.gate.resume(consent(f.token), current, true) { resumes++; true })
        assertEquals(1, resumes)
    }

    @Test fun failedStorageReadOrPauseWriteFailsClosed() {
        val reads = Store().apply { failRead = true }
        val readGate = SingleRelayRecoveryGate(reads, "client-a")
        assertTrue(readGate.isPaused(7)); assertNull(readGate.pause(before, 11))
        val writes = Store().apply { failWrite = true }
        val writeGate = SingleRelayRecoveryGate(writes, "client-a")
        assertNull(writeGate.pause(before, 11)); assertTrue(writeGate.isPaused(7))
    }

    @Test fun malformedDurableTokenCannotMakeTheGateAppearUnpaused() {
        val f = fixture()
        f.store.entries[7] = f.token.copy(previousScope = before.copy(accountId = 8))
        assertTrue(f.gate.isPaused(7)); assertNull(f.gate.token(7))
        f.gate.observeTransport(context())
        assertFalse(f.gate.resume(consent(f.token), context(), true) { error("Invalid durable fence") })
    }
}
