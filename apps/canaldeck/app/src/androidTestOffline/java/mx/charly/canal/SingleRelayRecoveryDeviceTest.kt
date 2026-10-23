package mx.charly.canal

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Own UUID fixtures on Android SQLite/preferences. The sole output port is synthetic. */
@RunWith(AndroidJUnit4::class)
class SingleRelayRecoveryDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val account = (UUID.randomUUID().leastSignificantBits and ((1L shl 52) - 1L)) + 1L
    private val source = -77101L
    private val destination = -77102L
    private val databaseNames = mutableListOf<String>()
    private val opened = mutableListOf<AsyncRelayDatabase>()
    private fun preferences() = RelayPreferences(context, account)
    private fun store() = object : SingleRelayRecoveryStore {
        override fun read(accountId: Long): SingleRelayRecoveryToken? {
            require(accountId == account); return preferences().recoveryPause()
        }
        override fun compareAndSet(accountId: Long, expected: SingleRelayRecoveryToken?, next: SingleRelayRecoveryToken?): Boolean {
            require(accountId == account); return preferences().compareAndSetRecoveryPause(expected, next)
        }
    }
    private fun database(name: String): AsyncRelayDatabase {
        if (name !in databaseNames) databaseNames += name
        return AsyncRelayDatabase(context, name).also { opened += it }
    }
    private fun verifyOfflineInvariant() {
        assertTrue(BuildConfig.OFFLINE_REHEARSAL_ONLY)
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        assertNull(TelegramSession.current)
    }

    @After fun cleanupOwnFixtures() {
        opened.forEach { it.close() }
        databaseNames.forEach { context.deleteDatabase(it) }
        context.getSharedPreferences(RelayPreferences.fileName(account), Context.MODE_PRIVATE).edit().clear().commit()
    }

    private class FakePort(private val fresh: SingleRelayFreshState) : SingleRelayPort {
        override val mode = SingleRelayPortMode.FAKE
        val requests = mutableListOf<SingleRelayCommand>()
        var refreshes = 0
        private var temporary = 5000L
        override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) {
            refreshes++; callback(fresh)
        }
        override fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit) { callback(true) }
        override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
            requests += command
            callback(SingleRelayDispatchResult.Queued(AsyncRelayResponse(command.scope, command.request.requestId,
                listOf(AsyncRelayQueuedMessage(temporary++, command.scope.destinationChatId)))))
        }
    }

    private fun fresh(scope: AsyncRelayScope, now: Long, messageId: Long): SingleRelayFreshState {
        val message = RelayMessage(account, source, messageId, now / 1000L - 1L,
            "Pick: Over 1.5 @1.70", RelayContentKind.PHOTO, canBeForwarded = true,
            permalink = "https://t.me/recovery_fixture/$messageId")
        return SingleRelayFreshState(AsyncRelayFreshState(scope, RelayBatch(listOf(message), synthetic = true),
            RelayConfig(account, destination, true,
                listOf(RelaySourceBinding(account, source, "Fano ficticio", RelaySourcePolicy.FANO_DIRECT, true))),
            RelayPermissions(account, source, destination, true, true,
                messageForwardable = mapOf(messageId to true), messageProtected = mapOf(messageId to false)),
            configurationRevision = 7L), connected = true)
    }

    @Test fun headerAndMediaDisconnectPersistUnknownAndDurablePauseWithoutReplay() {
        verifyOfflineInvariant()
        val results = JSONArray()
        for ((index, stage) in listOf(AsyncRelayStage.HEADER, AsyncRelayStage.MEDIA).withIndex()) {
            val scope = AsyncRelayScope(account, "device-before-${stage.name}", destination)
            val now = 1_792_695_600_000L
            val observed = SingleRelayObserved(account, scope.sessionId, source, 201L + index, now / 1000L - 1L)
            val state = fresh(scope, now, observed.messageId)
            val name = "single-recovery-${UUID.randomUUID()}.db"
            val ledger = database(name)
            val fake = FakePort(state)
            val controller = SingleRelayController(ledger, fake, SingleRelayAdmission { true }) { now }
            controller.start(observed)
            val header = requireNotNull(controller.snapshot(observed.key))
            if (stage == AsyncRelayStage.MEDIA) controller.onUpdate(AsyncRelayUpdate(scope,
                AsyncRelayUpdateKind.SUCCEEDED, header.requests.first().temporaryMessageIds.single(), 6001L,
                finalIsOutgoing = true, finalHasSendingState = false))
            val beforeDisconnect = requireNotNull(controller.snapshot(observed.key))
            assertEquals(if (stage == AsyncRelayStage.HEADER) AsyncRelayPhase.HEADER_WAITING else AsyncRelayPhase.MEDIA_WAITING,
                beforeDisconnect.phase)
            val requestsBefore = fake.requests.size
            assertEquals(if (stage == AsyncRelayStage.HEADER) 1 else 2, requestsBefore)
            val oldTemporary = beforeDisconnect.requests.last().temporaryMessageIds.single()
            val pause = requireNotNull(SingleRelayRecoveryGate(store(), "device-client").pause(scope, 7L))
            controller.invalidate(account, scope.sessionId, "DEVICE_NETWORK_DISCONNECT")
            assertEquals(AsyncRelayPhase.UNKNOWN, controller.snapshot(observed.key)?.phase)
            ledger.close()

            val reopened = database(name)
            val replacementPort = FakePort(state)
            val replacement = SingleRelayController(reopened, replacementPort, SingleRelayAdmission { true }) { now }
            assertEquals(AsyncRelayPhase.UNKNOWN, replacement.snapshot(observed.key)?.phase)
            replacement.start(observed)
            replacement.onUpdate(AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED, oldTemporary, 7001L,
                finalIsOutgoing = true, finalHasSendingState = false))
            assertEquals(AsyncRelayPhase.UNKNOWN, replacement.snapshot(observed.key)?.phase)
            assertEquals(0, replacementPort.requests.size)
            assertEquals(0, replacementPort.refreshes)
            assertEquals(requestsBefore, fake.requests.size)
            assertEquals(pause, preferences().recoveryPause())
            assertTrue(SingleRelayRecoveryGate(store(), "device-reopened-client").isPaused(account))
            verifyOfflineInvariant()
            results.put(JSONObject().put("pending_stage", stage.name)
                .put("reopened_phase", replacement.snapshot(observed.key)?.phase?.name)
                .put("requests_before_disconnect", requestsBefore)
                .put("requests_after_reopen", replacementPort.requests.size)
                .put("refreshes_after_reopen", replacementPort.refreshes)
                .put("durable_pause_survived", preferences().recoveryPause() == pause)
                .put("late_ack_kept_unknown", replacement.snapshot(observed.key)?.phase == AsyncRelayPhase.UNKNOWN))
            // Each iteration owns the same synthetic account; clear only its matching fixture
            // pause so the next case has an independent old-session token in the same test.
            assertTrue(preferences().compareAndSetRecoveryPause(pause, null))
        }
        File(context.filesDir, "single-recovery-evidence.json").writeText(JSONObject()
            .put("schema_version", 1).put("runtime", "Android SQLite/preferences + SingleRelayController + Fake port")
            .put("compiled_real_output_enabled", BuildConfig.PICK_RELAY_REAL_ENABLED)
            .put("offline_rehearsal_only", BuildConfig.OFFLINE_REHEARSAL_ONLY)
            .put("account_connected", TelegramSession.current != null).put("sent_real_messages", 0)
            .put("cases", results).toString(2) + "\n")
    }

    @Test fun staleDurableCasAndOldClientConsentCannotClearRecoveryPause() {
        verifyOfflineInvariant()
        val scope = AsyncRelayScope(account, "device-old-session", destination)
        val gate = SingleRelayRecoveryGate(store(), "device-old-client")
        val first = requireNotNull(gate.pause(scope, 2L))
        val current = requireNotNull(gate.pause(scope.copy(sessionId = "device-old-session-2"), 3L))
        assertFalse(preferences().compareAndSetRecoveryPause(first, null))
        assertEquals(current, preferences().recoveryPause())
        val reopened = SingleRelayRecoveryGate(store(), "device-new-client")
        val freshScope = scope.copy(sessionId = "device-fresh-session")
        val config = RelayConfig(account, destination, true, emptyList())
        val transport = ExistingClientRelayContext(freshScope, config, 3L, true)
        reopened.observeTransport(transport)
        var routerResumes = 0
        assertFalse(reopened.resume(SingleRelayRecoveryConsent(current, freshScope, 3L), transport, true) {
            routerResumes++; true
        })
        assertEquals(0, routerResumes)
        assertEquals(current, preferences().recoveryPause())
        assertTrue(reopened.isPaused(account))
        verifyOfflineInvariant()
    }

    @Test fun recoveringPendingSqliteRowsKeepsProcessFenceNeedAcrossReopen() {
        verifyOfflineInvariant()
        val scope = AsyncRelayScope(account, "device-crashed-session", destination)
        val key = RelayKey(account, source, "m:301")
        val start = AsyncRelaySnapshot(AsyncRelayIntent(key, scope, listOf(301L), true, 1000L,
            configurationRevision = 4L), AsyncRelayPhase.HEADER_WAITING,
            listOf(AsyncRelayRequest("device-crash-header", AsyncRelayStage.HEADER, emptyList(), 31000L)))
        val name = "single-process-recovery-${UUID.randomUUID()}.db"
        val db = database(name)
        assertTrue(db.create(start))
        assertTrue(db.requiresProcessRecoveryFence(account))
        assertEquals(scope, db.processRecoveryScope(account))
        db.close()
        val recovered = database(name)
        assertEquals(1, recovered.recoverPending(account))
        assertEquals(AsyncRelayPhase.UNKNOWN, recovered.read(key)?.phase)
        assertTrue(recovered.requiresProcessRecoveryFence(account))
        assertEquals(scope, recovered.processRecoveryScope(account))
        recovered.close()
        assertTrue(database(name).requiresProcessRecoveryFence(account))

        // UNKNOWN can predate the crash for any reason; a timeout does not establish
        // that the SDK had no pending output or that its lost identity is reusable.
        val timeoutName = "single-timeout-recovery-${UUID.randomUUID()}.db"
        val timeoutDb = database(timeoutName)
        val timeoutKey = RelayKey(account, source, "m:302")
        val timeoutStart = start.copy(intent = start.intent.copy(key = timeoutKey, sourceMessageIds = listOf(302L)),
            requests = listOf(start.requests.single().copy(requestId = "device-timeout-header")))
        assertTrue(timeoutDb.create(timeoutStart))
        assertTrue(timeoutDb.compareAndSet(0L, timeoutStart.copy(phase = AsyncRelayPhase.UNKNOWN,
            version = 1L, reason = "REQUEST_TIMEOUT")))
        timeoutDb.close()
        val timeoutReopened = database(timeoutName)
        assertEquals(AsyncRelayPhase.UNKNOWN, timeoutReopened.read(timeoutKey)?.phase)
        assertEquals("REQUEST_TIMEOUT", timeoutReopened.read(timeoutKey)?.reason)
        assertTrue(timeoutReopened.requiresProcessRecoveryFence(account))
        assertEquals(scope, timeoutReopened.processRecoveryScope(account))
        assertEquals(0, timeoutReopened.recoverPending(account))
        verifyOfflineInvariant()
    }
}
