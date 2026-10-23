package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class SingleRelayControllerTest {
    private class Port(var state: SingleRelayFreshState?) : SingleRelayPort {
        override val mode = SingleRelayPortMode.FAKE
        val commands = mutableListOf<SingleRelayCommand>()
        val refreshCallbacks = mutableListOf<(SingleRelayFreshState?) -> Unit>()
        val responses = mutableListOf<Pair<SingleRelayCommand, (SingleRelayDispatchResult) -> Unit>>()
        var deferRefresh = false
        var deferResponse = false
        var onDispatch: (SingleRelayCommand) -> Unit = {}
        var beforeRefresh: () -> Unit = {}
        var temporary = 1000L
        override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) {
            beforeRefresh()
            if (deferRefresh) refreshCallbacks += callback else callback(state)
        }
        override fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit) {
            assertEquals(AsyncRelayStage.MEDIA, command.request.stage)
            assertTrue(requireNotNull(command.confirmedHeaderMessageId) > 0L)
            assertFalse(command.expectedHeaderText.isNullOrBlank())
            callback(true) // Explicit synthetic fixture: the final attribution header remains present.
        }
        override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
            commands += command
            responses += command to callback
            onDispatch(command)
            if (!deferResponse) queued(responses.last(), temporary++)
        }
        fun queued(response: Pair<SingleRelayCommand, (SingleRelayDispatchResult) -> Unit>, temporaryId: Long) {
            val (command, callback) = response
            callback(SingleRelayDispatchResult.Queued(AsyncRelayResponse(command.scope, command.request.requestId,
                listOf(AsyncRelayQueuedMessage(temporaryId, command.scope.destinationChatId)))))
        }
    }

    private class Fixture(kind: RelayContentKind = RelayContentKind.PHOTO) {
        var now = 1_792_695_600_000L
        val scope = AsyncRelayScope(8001, "core-generation", -8003)
        val observed = SingleRelayObserved(scope.accountId, scope.sessionId, -8002, 201, now / 1000 - 1)
        val message = RelayMessage(scope.accountId, observed.sourceChatId, observed.messageId, observed.originalUnix,
            "Pick: Over 1.5 @1.70", kind, canBeForwarded = true, permalink = "https://t.me/core_fixture/201")
        val config = RelayConfig(scope.accountId, scope.destinationChatId, true,
            listOf(RelaySourceBinding(scope.accountId, observed.sourceChatId, "Fano fixture", RelaySourcePolicy.FANO_DIRECT, true)))
        val permission = RelayPermissions(scope.accountId, observed.sourceChatId, scope.destinationChatId, true, true,
            messageForwardable = mapOf(201L to true), messageProtected = mapOf(201L to false))
        val port = Port(SingleRelayFreshState(AsyncRelayFreshState(scope, RelayBatch(listOf(message), synthetic = true),
            config, permission), true))
        val ledger = FakeAsyncRelayLedger()
        val observedKeys = mutableSetOf<RelayKey>()
        val controller = SingleRelayController(ledger, port, SingleRelayAdmission { observedKeys.add(it.key) }) { now }
        fun snapshot() = requireNotNull(controller.snapshot(observed.key))
        fun success(old: Long, final: Long) = AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED, old, final,
            finalIsOutgoing = true, finalHasSendingState = false)
        fun header() = controller.onUpdate(success(snapshot().requests.first().temporaryMessageIds.single(), 2001))
        fun media() = controller.onUpdate(success(snapshot().requests.last().temporaryMessageIds.single(), 2002))
    }

    @Test fun delayedQueuedCallbackLeavesDurableIntentAndDoesNotBlockController() {
        val f = Fixture(); f.port.deferResponse = true
        f.controller.start(f.observed)
        assertEquals(AsyncRelayPhase.HEADER_WAITING, f.snapshot().phase)
        assertFalse(f.snapshot().requests.single().responseBound)
        assertEquals(1, f.port.commands.size)
        f.port.queued(f.port.responses.single(), 1000)
        assertTrue(f.snapshot().requests.single().responseBound)
        f.header()
        assertEquals(2, f.port.commands.size)
        assertFalse(f.snapshot().requests.last().responseBound)
    }

    @Test fun earlyFinalBindsOnlyToReturnedTemporaryAndNeverGuessesNextMessage() {
        val f = Fixture(); f.port.deferResponse = true
        f.controller.start(f.observed)
        f.controller.onUpdate(f.success(999, 2999))
        f.controller.onUpdate(f.success(1000, 2001))
        assertEquals(1, f.port.commands.size)
        assertFalse(f.snapshot().requests.single().responseBound)
        f.port.queued(f.port.responses.single(), 1000)
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, f.snapshot().phase)
        assertEquals(mapOf(1000L to 2001L), f.snapshot().requests.first().finalMessageIds)
        assertEquals(2, f.port.commands.size)
    }

    @Test fun repeatedRefreshAndQueuedCallbacksCannotDispatchTwice() {
        val f = Fixture(); f.port.deferRefresh = true
        f.controller.start(f.observed)
        val initial = f.port.refreshCallbacks.single()
        initial(f.port.state); initial(f.port.state)
        val beforeHeader = f.port.refreshCallbacks.last()
        beforeHeader(f.port.state); beforeHeader(f.port.state)
        assertEquals(1, f.port.commands.size)
        val response = f.port.responses.single()
        f.port.queued(response, 1000)
        f.controller.start(f.observed)
        assertEquals(1, f.port.commands.size)
        f.header()
        val beforeMedia = f.port.refreshCallbacks.last()
        beforeMedia(f.port.state); beforeMedia(f.port.state)
        assertEquals(1, f.port.commands.size)
        val afterHeaderRead = f.port.refreshCallbacks.last()
        assertNotSame(beforeMedia, afterHeaderRead)
        afterHeaderRead(f.port.state); afterHeaderRead(f.port.state)
        assertEquals(2, f.port.commands.size)
        f.port.queued(response, 1000) // Historical queued callback repeats after media starts.
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, f.snapshot().phase)
    }

    @Test fun configurationRevisionCatchesOffOnAbaBeforeOriginal() {
        val f = Fixture(); f.controller.start(f.observed)
        val state = f.port.state!!
        f.port.state = state.copy(state = state.state.copy(configurationRevision = 2))
        f.header()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals(1, f.port.commands.size)
    }

    @Test fun headerIntentIsDurableBeforeAnyOutputCallback() {
        val f = Fixture()
        f.port.onDispatch = { command ->
            val current = f.snapshot()
            assertEquals(command.request.requestId, current.requests.last().requestId)
            assertFalse(current.requests.last().responseBound)
        }
        f.controller.start(f.observed); f.header(); f.media()
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
    }

    @Test fun singlePhotoTemporaryCollisionWithHeaderTerminatesWithoutSpin() {
        val f = Fixture(); f.controller.start(f.observed)
        f.port.temporary = 1000
        f.header()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals(2, f.port.commands.size)
    }

    @Test fun finalIdCollisionAcrossStagesCannotComplete() {
        val f = Fixture(); f.controller.start(f.observed); f.header()
        f.controller.onUpdate(f.success(f.snapshot().requests.last().temporaryMessageIds.single(), 2001))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
    }

    @Test fun conflictingFinalForAConfirmedHeaderStopsMediaWithoutRetransmission() {
        val f = Fixture(); f.controller.start(f.observed)
        val headerTemporary = f.snapshot().requests.single().temporaryMessageIds.single()
        f.header()
        f.controller.onUpdate(f.success(headerTemporary, 2999))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals(2, f.port.commands.size)
    }

    @Test fun expirationClosesAnUnresolvedRequestAndLateCallbacksDoNotResume() {
        val f = Fixture(); f.port.deferResponse = true; f.controller.start(f.observed)
        f.now += SingleRelayController.TIMEOUT_MILLIS
        f.controller.expire()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        f.port.queued(f.port.responses.single(), 1000)
        f.controller.onUpdate(f.success(1000, 2001))
        f.controller.start(f.observed)
        assertEquals(1, f.port.commands.size)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
    }

    @Test fun preparationTimeoutCannotAdmitALateSnapshot() {
        val f = Fixture(); f.port.deferRefresh = true; f.controller.start(f.observed)
        f.now += SingleRelayController.TIMEOUT_MILLIS
        f.controller.expire()
        f.port.refreshCallbacks.single()(f.port.state)
        assertNull(f.controller.snapshot(f.observed.key))
        assertTrue(f.port.commands.isEmpty())
    }

    @Test fun rollbackOfClockClosesReferenceBeforeFinalCompletion() {
        val f = Fixture(RelayContentKind.TEXT); f.controller.start(f.observed)
        f.now--
        f.header()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
    }

    @Test fun protectedPhotoIsReferenceOnlyAndDoesNotInvokeForward() {
        val f = Fixture(); val state = f.port.state!!
        f.port.state = state.copy(state = state.state.copy(permissions = f.permission.copy(sourceHasProtectedContent = true)))
        f.controller.start(f.observed); f.header()
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
        assertEquals(listOf(AsyncRelayStage.HEADER), f.port.commands.map { it.request.stage })
    }

    @Test fun sourceAccountAndIdentityChangesBetweenRefreshesInvalidateIntent() {
        val f = Fixture(); f.controller.start(f.observed)
        val state = f.port.state!!
        f.port.state = state.copy(state = state.state.copy(scope = f.scope.copy(sessionId = "different-generation")))
        f.header()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals(1, f.port.commands.size)
    }

    @Test fun mediaCallbackForAnotherRequestIsUnknownNotAnotherAttempt() {
        val f = Fixture(); f.port.deferResponse = true; f.controller.start(f.observed)
        val command = f.port.commands.single()
        f.port.responses.single().second(SingleRelayDispatchResult.Queued(AsyncRelayResponse(command.scope, "wrong-request",
            listOf(AsyncRelayQueuedMessage(1000, command.scope.destinationChatId)))))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals(1, f.port.commands.size)
    }

    @Test fun callbackExceptionNeverReissuesTheIntent() {
        val f = Fixture(); f.port.onDispatch = { throw IllegalStateException("fixture") }
        f.controller.start(f.observed)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        f.controller.start(f.observed)
        assertEquals(1, f.port.commands.size)
    }

    @Test fun closingControllerWhileRefreshPendingDropsItsTransientContext() {
        val f = Fixture(); f.port.deferRefresh = true; f.controller.start(f.observed)
        f.controller.close()
        f.port.refreshCallbacks.single()(f.port.state)
        assertTrue(f.port.commands.isEmpty())
        assertNull(f.controller.snapshot(f.observed.key))
    }

    @Test fun destinationInScopeMustEqualTheOwnerSelectedDestination() {
        val f = Fixture(); val state = f.port.state!!
        f.port.state = state.copy(state = state.state.copy(scope = f.scope.copy(destinationChatId = -8999)))
        f.controller.start(f.observed)
        assertTrue(f.port.commands.isEmpty())
        assertNull(f.controller.snapshot(f.observed.key))
    }

    @Test fun fanoPhotoOnlyWithoutSourceLinkIsHeldEvenWithConsent() {
        val f = Fixture(); val state = f.port.state!!
        f.port.state = state.copy(photoOnlyConsent = true, state = state.state.copy(batch = state.state.batch.copy(
            messages = listOf(f.message.copy(text = "", permalink = null)))))
        f.controller.start(f.observed)
        assertTrue(f.port.commands.isEmpty())
        assertEquals("PHOTO_ONLY_REFERENCE_LINK_UNAVAILABLE", f.controller.decision(f.observed.key)?.reason)
    }

    @Test fun sessionRevokedDuringDurableReservationEndsUnknownBeforeAnyOutput() {
        val f = Fixture()
        lateinit var controller: SingleRelayController
        val intercepting = object : AsyncRelayLedger by f.ledger {
            override fun create(snapshot: AsyncRelaySnapshot): Boolean {
                controller.invalidate(f.scope.accountId, f.scope.sessionId, "SESSION_CHANGED")
                return f.ledger.create(snapshot)
            }
        }
        controller = SingleRelayController(intercepting, f.port, SingleRelayAdmission { true }) { f.now }
        controller.start(f.observed)
        assertEquals(AsyncRelayPhase.UNKNOWN, controller.snapshot(f.observed.key)?.phase)
        assertTrue(f.port.commands.isEmpty())
    }

    @Test fun callbacksAfterCloseDoNotReadTheDisposedLedger() {
        val f = Fixture(); f.port.deferResponse = true
        var storageClosed = false
        val guardedLedger = object : AsyncRelayLedger by f.ledger {
            override fun read(key: RelayKey): AsyncRelaySnapshot? {
                check(!storageClosed) { "Fixture SQLite handle is closed" }
                return f.ledger.read(key)
            }
        }
        val controller = SingleRelayController(guardedLedger, f.port, SingleRelayAdmission { true }) { f.now }
        controller.start(f.observed)
        controller.close()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(f.observed.key)?.phase)
        storageClosed = true
        f.port.queued(f.port.responses.single(), 1000)
        f.port.responses.single().second(SingleRelayDispatchResult.Rejected)
        controller.onUpdate(f.success(1000, 2001))
        controller.expire()
        assertEquals(1, f.port.commands.size)
    }
}
