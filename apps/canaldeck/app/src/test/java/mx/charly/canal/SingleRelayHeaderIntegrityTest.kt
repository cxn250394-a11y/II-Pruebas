package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

/** Destination header and source permission races; synthetic ports only, no SDK/client. */
class SingleRelayHeaderIntegrityTest {
    private class Port(var fresh: SingleRelayFreshState) : SingleRelayPort {
        override val mode = SingleRelayPortMode.FAKE
        val commands = mutableListOf<SingleRelayCommand>()
        val headerReads = mutableListOf<SingleRelayCommand>()
        val headerCallbacks = mutableListOf<(Boolean) -> Unit>()
        val freshCallbacks = mutableListOf<(SingleRelayFreshState?) -> Unit>()
        var headerExists = true
        var deferHeaderRead = false
        var deferRefresh = false
        var throwHeaderRead = false
        private var temporary = 1000L
        override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) {
            if (deferRefresh) freshCallbacks += callback else callback(fresh)
        }
        override fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit) {
            headerReads += command
            if (throwHeaderRead) throw IllegalStateException("synthetic read failure")
            if (deferHeaderRead) headerCallbacks += callback else callback(headerExists)
        }
        override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
            commands += command
            callback(SingleRelayDispatchResult.Queued(AsyncRelayResponse(command.scope,
                command.request.requestId, listOf(AsyncRelayQueuedMessage(temporary++, command.scope.destinationChatId)))))
        }
    }

    private class Fixture(kind: RelayContentKind = RelayContentKind.PHOTO) {
        var now = 1_792_695_600_000L
        val scope = AsyncRelayScope(8101, "header-integrity-generation", -8103)
        val observed = SingleRelayObserved(scope.accountId, scope.sessionId, -8102, 201, now / 1000 - 1)
        val message = RelayMessage(scope.accountId, observed.sourceChatId, observed.messageId, observed.originalUnix,
            "Pick: Over 1.5 @1.70", kind, canBeForwarded = true, permalink = "https://t.me/header_fixture/201")
        val config = RelayConfig(scope.accountId, scope.destinationChatId, true,
            listOf(RelaySourceBinding(scope.accountId, observed.sourceChatId, "Fano fixture", RelaySourcePolicy.FANO_DIRECT, true)))
        val permission = RelayPermissions(scope.accountId, observed.sourceChatId, scope.destinationChatId, true, true,
            messageForwardable = mapOf(201L to true), messageProtected = mapOf(201L to false))
        val port = Port(SingleRelayFreshState(AsyncRelayFreshState(scope, RelayBatch(listOf(message), synthetic = true),
            config, permission), true))
        val ledger = FakeAsyncRelayLedger()
        val controller = SingleRelayController(ledger, port, SingleRelayAdmission { true }) { now }
        fun snapshot() = requireNotNull(controller.snapshot(observed.key))
        fun success(temporary: Long, final: Long) = AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED,
            temporary, final, finalIsOutgoing = true, finalHasSendingState = false)
        fun header() = controller.onUpdate(success(1000, 2001))
        fun media() = controller.onUpdate(success(1001, 2002))
        fun assertUnknownWithHeaderOnly() {
            assertEquals(AsyncRelayPhase.UNKNOWN, snapshot().phase)
            assertEquals(listOf(AsyncRelayStage.HEADER), port.commands.map { it.request.stage })
            controller.start(observed)
            assertEquals(1, port.commands.size)
        }
    }

    @Test fun authoritativeHeaderAbsenceStopsBeforeForwardingTheOriginal() {
        val f = Fixture(); f.port.headerExists = false
        f.controller.start(f.observed); f.header()
        f.assertUnknownWithHeaderOnly()
        assertEquals("FINAL_HEADER_MISSING_OR_CHANGED", f.snapshot().reason)
        assertEquals(2001L, f.port.headerReads.single().confirmedHeaderMessageId)
        assertEquals(f.port.commands.single().header, f.port.headerReads.single().expectedHeaderText)
        assertNull(f.port.headerReads.single().header)
    }

    @Test fun finalHeaderDeletionWhileSourceRefreshIsPendingCancelsMediaAndLateCallback() {
        val f = Fixture(); f.controller.start(f.observed)
        f.port.deferRefresh = true; f.header()
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, f.snapshot().phase)
        f.controller.onDestinationDeleted(f.scope, listOf(2001L))
        f.port.freshCallbacks.single()(f.port.fresh)
        f.assertUnknownWithHeaderOnly()
        assertTrue(f.port.headerReads.isEmpty())
        assertEquals("FINAL_HEADER_DELETED", f.snapshot().reason)
    }

    @Test fun positiveHeaderReadCannotReviveADeletedContext() {
        val f = Fixture(); f.port.deferHeaderRead = true
        f.controller.start(f.observed); f.header()
        f.controller.onDestinationDeleted(f.scope, listOf(2001L))
        f.port.headerCallbacks.single()(true)
        f.port.headerCallbacks.single()(true)
        f.assertUnknownWithHeaderOnly()
    }

    @Test fun sourcePermissionChangeDuringHeaderReadIsRecheckedBeforeMedia() {
        val f = Fixture(); f.port.deferHeaderRead = true
        f.controller.start(f.observed); f.header()
        f.port.fresh = f.port.fresh.copy(state = f.port.fresh.state.copy(
            permissions = f.permission.copy(canWriteDestination = false)))
        f.port.headerCallbacks.single()(true)
        f.assertUnknownWithHeaderOnly()
        assertEquals("CURRENT_CONSENT_PERMISSION_SESSION_OR_CONTENT_CHANGED", f.snapshot().reason)
    }

    @Test fun deletionAfterPositiveReadBeforeFinalRefreshStillCancelsMedia() {
        val f = Fixture(); f.port.deferHeaderRead = true
        f.controller.start(f.observed); f.header()
        f.port.deferRefresh = true
        f.port.headerCallbacks.single()(true)
        f.controller.onDestinationDeleted(f.scope, listOf(2001L))
        f.port.freshCallbacks.single()(f.port.fresh)
        f.assertUnknownWithHeaderOnly()
    }

    @Test fun repeatedPositiveReadDispatchesOneOriginalAndFinalAckCompletes() {
        val f = Fixture(); f.port.deferHeaderRead = true
        f.controller.start(f.observed); f.header()
        val positive = f.port.headerCallbacks.single()
        positive(true); positive(true)
        assertEquals(2, f.port.commands.size)
        assertEquals(2001L, f.port.commands.last().confirmedHeaderMessageId)
        assertEquals(f.port.commands.first().header, f.port.commands.last().expectedHeaderText)
        f.media()
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
        positive(true)
        assertEquals(2, f.port.commands.size)
    }

    @Test fun unrelatedAccountSessionDestinationAndIdsDoNotCancelTheHeader() {
        val f = Fixture(); f.port.deferHeaderRead = true
        f.controller.start(f.observed); f.header()
        f.controller.onDestinationDeleted(f.scope.copy(accountId = 8199), listOf(2001L))
        f.controller.onDestinationDeleted(f.scope.copy(sessionId = "other-generation"), listOf(2001L))
        f.controller.onDestinationDeleted(f.scope.copy(destinationChatId = -8199), listOf(2001L))
        f.controller.onDestinationDeleted(f.scope, listOf(2999L, 0L, -1L))
        f.port.headerCallbacks.single()(true); f.media()
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
        assertEquals(2, f.port.commands.size)
    }

    @Test fun deletionAfterCompleteNeverReopensTheIntentOrDispatchesAnotherOriginal() {
        val f = Fixture(); f.controller.start(f.observed); f.header(); f.media()
        val complete = f.snapshot()
        f.controller.onDestinationDeleted(f.scope, listOf(2001L, 2002L))
        f.controller.start(f.observed)
        assertEquals(complete, f.snapshot())
        assertEquals(2, f.port.commands.size)
    }

    @Test fun headerDeletedAfterForwardWasQueuedIsUnknownWithoutPretendingToUndoTheProvider() {
        val f = Fixture(); f.controller.start(f.observed); f.header()
        assertEquals(2, f.port.commands.size) // The provider already accepted the original request.
        f.controller.onDestinationDeleted(f.scope, listOf(2001L))
        f.media() // A late final ACK cannot turn the invalidated pair into COMPLETE.
        f.controller.start(f.observed)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals("FINAL_HEADER_DELETED", f.snapshot().reason)
        assertEquals(2, f.port.commands.size) // No rollback, replacement header or repeated photo.
    }

    @Test fun destinationReadFailureIsUnknownAndDoesNotRetry() {
        val f = Fixture(); f.port.throwHeaderRead = true
        f.controller.start(f.observed); f.header()
        f.assertUnknownWithHeaderOnly()
        assertEquals("FINAL_HEADER_READ_FAILED", f.snapshot().reason)
        assertEquals(1, f.port.headerReads.size)
    }

    @Test fun deadlineDuringHeaderReadClosesIntentBeforeALatePositiveAnswer() {
        val f = Fixture(); f.port.deferHeaderRead = true
        f.controller.start(f.observed); f.header()
        f.now += SingleRelayController.TIMEOUT_MILLIS
        f.port.headerCallbacks.single()(true)
        f.assertUnknownWithHeaderOnly()
        assertEquals("REQUEST_TIMEOUT", f.snapshot().reason)
    }

    @Test fun textReferenceNeedsOnlyItsOwnFinalAckAndNeverReadsOrForwardsMedia() {
        val f = Fixture(RelayContentKind.TEXT)
        f.controller.start(f.observed); f.header()
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
        assertTrue(f.port.headerReads.isEmpty())
        assertEquals(listOf(AsyncRelayStage.HEADER), f.port.commands.map { it.request.stage })
    }

    @Test fun portWithoutAuthoritativeHeaderReaderFailsClosed() {
        val f = Fixture()
        val unverifying = object : SingleRelayPort {
            override val mode = SingleRelayPortMode.FAKE
            override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) =
                f.port.refresh(observed, callback)
            override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) =
                f.port.dispatch(command, callback)
        }
        val controller = SingleRelayController(f.ledger, unverifying, SingleRelayAdmission { true }) { f.now }
        controller.start(f.observed)
        controller.onUpdate(f.success(1000, 2001))
        assertEquals(AsyncRelayPhase.UNKNOWN, controller.snapshot(f.observed.key)?.phase)
        assertEquals(1, f.port.commands.size)
    }
}
