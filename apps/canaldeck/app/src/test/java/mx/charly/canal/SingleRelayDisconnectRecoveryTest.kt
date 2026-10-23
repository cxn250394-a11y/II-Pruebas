package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

/** Composes the production gate, ACK router and controller with explicit synthetic ports only. */
class SingleRelayDisconnectRecoveryTest {
    private class Store : SingleRelayRecoveryStore {
        val records = mutableMapOf<Long, SingleRelayRecoveryToken>()
        override fun read(accountId: Long) = records[accountId]
        override fun compareAndSet(accountId: Long, expected: SingleRelayRecoveryToken?, next: SingleRelayRecoveryToken?): Boolean {
            if (records[accountId] != expected) return false
            if (next == null) records.remove(accountId) else records[accountId] = next
            return true
        }
    }

    private class Fixture {
        val now = 1_792_695_600_000L
        val source = -100741L
        val initialScope = AsyncRelayScope(741, "disconnect-before", -100742)
        val store = Store()
        var gate = SingleRelayRecoveryGate(store, "fake-client-lifetime-a")
        val router = ExistingClientRelayAckRouter()
        val ledger = FakeAsyncRelayLedger()
        var scope = initialScope
        var revision = 11L
        var connected = true
        var kind = RelayContentKind.PHOTO
        var deferQueue = false
        private var temporary = 10_000L
        val commands = mutableListOf<SingleRelayCommand>()
        val deferred = mutableListOf<Pair<SingleRelayCommand, (SingleRelayDispatchResult) -> Unit>>()
        val rejectedAdmissionKeys = mutableListOf<RelayKey>()
        private val config get() = RelayConfig(scope.accountId, scope.destinationChatId, true,
            listOf(RelaySourceBinding(scope.accountId, source, "Fano synthetic", RelaySourcePolicy.FANO_DIRECT, true)))
        fun context() = ExistingClientRelayContext(scope, config, revision, connected)
        val port = object : SingleRelayPort {
            override val mode = SingleRelayPortMode.FAKE
            override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) {
                if (!connected || gate.isPaused(scope.accountId)) { callback(null); return }
                val message = RelayMessage(observed.accountId, source, observed.messageId, observed.originalUnix,
                    "Pick: Over 1.5 @1.70", kind, canBeForwarded = true,
                    permalink = "https://t.me/synthetic_recovery/${observed.messageId}")
                callback(SingleRelayFreshState(AsyncRelayFreshState(scope, RelayBatch(listOf(message), synthetic = true),
                    config, RelayPermissions(scope.accountId, source, scope.destinationChatId, true, true,
                        messageForwardable = mapOf(observed.messageId to true),
                        messageProtected = mapOf(observed.messageId to false)), revision), true))
            }
            override fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit) {
                assertEquals(AsyncRelayStage.MEDIA, command.request.stage)
                assertTrue(requireNotNull(command.confirmedHeaderMessageId) > 0L)
                assertFalse(command.expectedHeaderText.isNullOrBlank())
                callback(connected && !gate.isPaused(scope.accountId))
            }
            override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
                if (!connected || gate.isPaused(scope.accountId) || !router.begin(command)) {
                    callback(SingleRelayDispatchResult.Rejected); return
                }
                commands += command
                if (deferQueue) deferred += command to callback
                else queued(command, callback, temporary++)
            }
        }
        val controller = SingleRelayController(ledger, port, SingleRelayAdmission { true }) { now }
        fun observed(id: Long, actualScope: AsyncRelayScope = scope) =
            SingleRelayObserved(actualScope.accountId, actualScope.sessionId, source, id, now / 1000 - 1)
        fun start(observed: SingleRelayObserved) {
            // Same fence as the bridge: readiness itself never admits fresh work while paused.
            if (!connected || gate.isPaused(observed.accountId)) rejectedAdmissionKeys += observed.key
            else controller.start(observed)
        }
        fun snapshot(observed: SingleRelayObserved) = requireNotNull(ledger.read(observed.key))
        private fun queued(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit, id: Long) {
            val response = AsyncRelayResponse(command.scope, command.request.requestId,
                listOf(AsyncRelayQueuedMessage(id, command.scope.destinationChatId)))
            val routed = router.bind(response)
            routed.updates.forEach(controller::onUpdate)
            callback(if (routed.uncertain) SingleRelayDispatchResult.Rejected else SingleRelayDispatchResult.Queued(response))
        }
        fun lateQueued(id: Long): ExistingClientRelayAckRouter.Routed {
            val (command, callback) = deferred.single()
            val response = AsyncRelayResponse(command.scope, command.request.requestId,
                listOf(AsyncRelayQueuedMessage(id, command.scope.destinationChatId)))
            val routed = router.bind(response)
            assertTrue(routed.updates.isEmpty())
            callback(if (routed.uncertain) SingleRelayDispatchResult.Rejected else SingleRelayDispatchResult.Queued(response))
            return routed
        }
        fun final(observed: SingleRelayObserved, temporaryId: Long, finalId: Long) {
            controller.onUpdate(AsyncRelayUpdate(AsyncRelayScope(observed.accountId, observed.sessionId,
                initialScope.destinationChatId), AsyncRelayUpdateKind.SUCCEEDED, temporaryId, finalId,
                finalIsOutgoing = true, finalHasSendingState = false))
        }
        fun disconnect(): SingleRelayRecoveryToken {
            val token = requireNotNull(gate.pause(scope, revision))
            assertEquals(token, store.records[scope.accountId]) // Fence precedes transient invalidation.
            controller.invalidate(scope.accountId, scope.sessionId, "NETWORK_DISCONNECTED")
            router.disconnected()
            connected = false
            gate.observeTransport(null)
            return token
        }
        fun reconnect() {
            scope = initialScope.copy(sessionId = "disconnect-after")
            revision = 12
            connected = true
            gate.observeTransport(context())
        }
        fun resume(token: SingleRelayRecoveryToken) = gate.resume(
            SingleRelayRecoveryConsent(token, scope, revision), context(), router.canResume) {
            router.resume(scope.accountId)
        }
        fun freshReference(id: Long): SingleRelayObserved {
            kind = RelayContentKind.TEXT
            deferQueue = false
            return observed(id).also(::start)
        }
    }

    @Test fun unboundHeaderRemainsUnknownUntilLateBindingAndExplicitFreshGenerationConsent() {
        val f = Fixture(); f.deferQueue = true
        val old = f.observed(101); f.start(old)
        assertEquals(1, f.commands.size)
        assertFalse(f.snapshot(old).requests.single().responseBound)
        val token = f.disconnect()
        val unknown = f.snapshot(old)
        assertEquals(AsyncRelayPhase.UNKNOWN, unknown.phase)
        assertTrue(f.router.hasQuarantinedRequests); assertFalse(f.router.stopped)

        f.reconnect()
        f.start(f.observed(102))
        assertNull(f.ledger.read(f.observed(102).key))
        assertFalse(f.resume(token)) // The unknown temporary identity has not been settled.
        assertEquals(1, f.commands.size)
        val late = f.lateQueued(90_001)
        assertTrue(late.uncertain); assertFalse(f.router.hasQuarantinedRequests)
        assertEquals(unknown, f.snapshot(old))
        f.final(old, 90_001, 501)
        assertEquals(unknown, f.snapshot(old)); assertEquals(1, f.commands.size)

        assertTrue(f.resume(token))
        val fresh = f.freshReference(103)
        assertEquals(2, f.commands.size)
        assertEquals(AsyncRelayStage.HEADER, f.commands.last().request.stage)
        val temp = f.snapshot(fresh).requests.single().temporaryMessageIds.single()
        f.final(fresh, temp, 503)
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot(fresh).phase)
        f.start(old); f.final(old, 90_001, 501)
        assertEquals(unknown, f.snapshot(old))
        assertEquals(2, f.commands.size)
        assertEquals(0, f.commands.count { it.request.stage == AsyncRelayStage.MEDIA })
    }

    @Test fun boundPendingPhotoStaysUnknownAfterLateFinalAndFreshReferenceNeverRetriesIt() {
        val f = Fixture(); val old = f.observed(201); f.start(old)
        val headerTemporary = f.snapshot(old).requests.single().temporaryMessageIds.single()
        f.final(old, headerTemporary, 601)
        assertEquals(2, f.commands.size)
        val photoTemporary = f.snapshot(old).requests.last().temporaryMessageIds.single()
        val token = f.disconnect(); val unknown = f.snapshot(old)
        assertEquals(AsyncRelayPhase.UNKNOWN, unknown.phase)
        assertFalse(f.router.hasQuarantinedRequests); assertFalse(f.router.stopped)
        f.reconnect(); f.final(old, photoTemporary, 602)
        assertEquals(unknown, f.snapshot(old)); assertEquals(2, f.commands.size)
        f.start(old)
        assertEquals(2, f.commands.size)
        assertTrue(f.resume(token))
        val fresh = f.freshReference(202)
        f.final(fresh, f.snapshot(fresh).requests.single().temporaryMessageIds.single(), 603)
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot(fresh).phase)
        assertEquals(unknown, f.snapshot(old)); assertEquals(3, f.commands.size)
        assertEquals(1, f.commands.count { it.request.stage == AsyncRelayStage.MEDIA })
        assertEquals(1, f.commands.count { it.key == old.key && it.request.stage == AsyncRelayStage.MEDIA })
    }

    @Test fun reconnectWithoutConsentAndWrongRevisionOrNonceCannotAdmitAnotherReference() {
        val f = Fixture(); val old = f.observed(301); f.start(old)
        val token = f.disconnect(); val unknown = f.snapshot(old)
        f.reconnect()
        repeat(5) { f.gate.observeTransport(f.context()); f.start(f.observed(302)) }
        assertTrue(f.gate.isPaused(old.accountId))
        assertNull(f.ledger.read(f.observed(302).key))
        assertEquals(1, f.commands.size); assertTrue(f.router.paused)
        val current = f.context()
        assertFalse(f.gate.resume(SingleRelayRecoveryConsent(token, f.scope, f.revision + 1),
            current, f.router.canResume) { error("Wrong revision must not touch the router") })
        assertFalse(f.gate.resume(SingleRelayRecoveryConsent(token.copy(nonce = "foreign-token"), f.scope, f.revision),
            current, f.router.canResume) { error("Wrong token must not touch the router") })
        assertEquals(token, f.store.records[old.accountId]); assertEquals(unknown, f.snapshot(old))
        assertEquals(1, f.commands.size); assertEquals(0, f.commands.count { it.request.stage == AsyncRelayStage.MEDIA })
    }

    @Test fun reconstructedGateKeepsDurablePauseAndCannotReplayAnIntentFromAnEarlierClientLifetime() {
        val f = Fixture(); val old = f.observed(401); f.start(old)
        val token = f.disconnect(); val unknown = f.snapshot(old)
        f.gate = SingleRelayRecoveryGate(f.store, "fake-client-lifetime-b")
        f.reconnect(); f.start(f.observed(402)); f.start(old)
        assertFalse(f.resume(token))
        assertTrue(f.gate.isPaused(old.accountId))
        assertEquals(token, f.store.records[old.accountId]); assertEquals(unknown, f.snapshot(old))
        assertNull(f.ledger.read(f.observed(402).key))
        assertEquals(1, f.commands.size); assertEquals(0, f.commands.count { it.request.stage == AsyncRelayStage.MEDIA })
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
    }
}
