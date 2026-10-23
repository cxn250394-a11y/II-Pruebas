package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

/**
 * A manually deferred Fake submission uses the real controller lease after an asynchronous
 * boundary. This proves the shared guard, not an invocation of the compiled-OFF TDLib port.
 */
class SingleRelayCommandLeaseTest {
    private class DeferredPort(var state: SingleRelayFreshState) : SingleRelayPort {
        override val mode = SingleRelayPortMode.FAKE
        data class Pending(val command: SingleRelayCommand, val callback: (SingleRelayDispatchResult) -> Unit)
        val pending = mutableListOf<Pending>()
        val submitted = mutableListOf<SingleRelayCommand>()
        var lease: (SingleRelayCommand) -> Boolean = { false }
        private var temporary = 1000L
        override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) = callback(state)
        override fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit) = callback(true)
        override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
            pending += Pending(command, callback)
        }
        fun releaseOne() {
            val deferred = pending.removeAt(0)
            if (!lease(deferred.command)) { deferred.callback(SingleRelayDispatchResult.Rejected); return }
            submitted += deferred.command
            deferred.callback(SingleRelayDispatchResult.Queued(AsyncRelayResponse(deferred.command.scope,
                deferred.command.request.requestId, listOf(AsyncRelayQueuedMessage(temporary++,
                    deferred.command.scope.destinationChatId)))))
        }
    }
    private class Fixture {
        var now = 1_792_695_600_000L
        val scope = AsyncRelayScope(9101, "lease-fixture-generation", -9103)
        val observed = SingleRelayObserved(scope.accountId, scope.sessionId, -9102, 201, now / 1000 - 1)
        private val message = RelayMessage(scope.accountId, observed.sourceChatId, observed.messageId,
            observed.originalUnix, "Pick: Over 1.5 @1.70", RelayContentKind.PHOTO,
            canBeForwarded = true, permalink = "https://t.me/lease_fixture/201")
        private val config = RelayConfig(scope.accountId, scope.destinationChatId, true,
            listOf(RelaySourceBinding(scope.accountId, observed.sourceChatId, "Fano fixture", RelaySourcePolicy.FANO_DIRECT, true)))
        private val permissions = RelayPermissions(scope.accountId, observed.sourceChatId, scope.destinationChatId,
            true, true, messageForwardable = mapOf(201L to true), messageProtected = mapOf(201L to false))
        val port = DeferredPort(SingleRelayFreshState(AsyncRelayFreshState(scope,
            RelayBatch(listOf(message), synthetic = true), config, permissions), true))
        val ledger = FakeAsyncRelayLedger()
        val controller = SingleRelayController(ledger, port, SingleRelayAdmission { true }) { now }
        init { port.lease = controller::isRequestActive }
        fun snapshot() = requireNotNull(controller.snapshot(observed.key))
        fun headerConfirmed() {
            port.releaseOne()
            controller.onUpdate(AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED, 1000, 2001,
                finalIsOutgoing = true, finalHasSendingState = false))
        }
    }

    @Test fun currentExactRequestCanCompleteButOldAndAlteredLeasesCannotBeBorrowed() {
        val f = Fixture(); f.controller.start(f.observed)
        val header = f.port.pending.single().command
        assertTrue(f.controller.isRequestActive(header))
        assertTrue(f.controller.isRequestActive(header, 0L))
        assertFalse(f.controller.isRequestActive(header, 2L)) // OFF -> ON revision changed during a native await.
        assertFalse(f.controller.isRequestActive(header.copy(scope = f.scope.copy(sessionId = "other"))))
        assertFalse(f.controller.isRequestActive(header.copy(request = header.request.copy(requestId = "different"))))
        assertFalse(f.controller.isRequestActive(header.copy(key = header.key.copy(groupId = "m:202"))))
        f.headerConfirmed()
        assertFalse(f.controller.isRequestActive(header))
        val media = f.port.pending.single().command
        assertTrue(f.controller.isRequestActive(media))
        assertTrue(f.controller.isRequestActive(media, 0L))
        assertFalse(f.controller.isRequestActive(media, 2L))
        assertFalse(f.controller.isRequestActive(media.copy(request = media.request.copy(expectedSourceMessageIds = listOf(202L)))))
        assertFalse(f.controller.isRequestActive(media.copy(request = media.request.copy(deadlineMillis = media.request.deadlineMillis + 1))))
        assertFalse(f.controller.isRequestActive(media.copy(request = media.request.copy(responseBound = true))))
        f.port.releaseOne()
        assertFalse(f.controller.isRequestActive(media))
        f.controller.onUpdate(AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.SUCCEEDED, 1001, 2002,
            finalIsOutgoing = true, finalHasSendingState = false))
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
        assertFalse(f.controller.isRequestActive(media))
        assertEquals(listOf(AsyncRelayStage.HEADER, AsyncRelayStage.MEDIA), f.port.submitted.map { it.request.stage })
    }

    @Test fun sourceMutationBeforeDeferredHeaderSubmissionRevokesTheCommandLease() {
        val f = Fixture(); f.controller.start(f.observed)
        val header = f.port.pending.single().command
        f.controller.stop(f.observed.key, "SOURCE_MUTATED")
        assertFalse(f.controller.isRequestActive(header))
        f.port.releaseOne()
        assertTrue(f.port.submitted.isEmpty())
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        f.controller.start(f.observed)
        assertTrue(f.port.pending.isEmpty())
    }

    @Test fun observedHeaderDeletionBeforeDeferredMediaSubmissionCannotSubmitAnOriginal() {
        val f = Fixture(); f.controller.start(f.observed); f.headerConfirmed()
        val media = f.port.pending.single().command
        assertTrue(f.controller.isRequestActive(media))
        f.controller.onDestinationDeleted(f.scope, listOf(2001L))
        assertFalse(f.controller.isRequestActive(media))
        f.port.releaseOne()
        assertEquals(listOf(AsyncRelayStage.HEADER), f.port.submitted.map { it.request.stage })
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals("FINAL_HEADER_DELETED", f.snapshot().reason)
    }

    @Test fun disconnectOrTimeoutAfterMediaPreparationRevokesLeaseWithoutRetry() {
        for (disconnect in listOf(true, false)) {
            val f = Fixture(); f.controller.start(f.observed); f.headerConfirmed()
            val media = f.port.pending.single().command
            assertTrue(f.controller.isRequestActive(media))
            if (disconnect) f.controller.invalidate(f.scope.accountId, f.scope.sessionId, "CONNECTION_CHANGED")
            else { f.now += SingleRelayController.TIMEOUT_MILLIS * 2; f.controller.expire() }
            assertFalse(f.controller.isRequestActive(media))
            f.port.releaseOne()
            assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
            assertEquals(listOf(AsyncRelayStage.HEADER), f.port.submitted.map { it.request.stage })
            f.controller.start(f.observed)
            assertTrue(f.port.pending.isEmpty())
        }
    }
}
