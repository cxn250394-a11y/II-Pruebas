package mx.charly.canal

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/** Durable Android stores only; no TDLib session, account or output port. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SingleRelayRecoveryPersistenceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val account = (UUID.randomUUID().leastSignificantBits and ((1L shl 52) - 1L)) + 1L
    private val source = -44101L
    private val destination = -44102L
    private val scope = AsyncRelayScope(account, "persistence-before", destination)
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
    private fun database(name: String = "recovery-persistence-${UUID.randomUUID()}.db"): AsyncRelayDatabase {
        if (name !in databaseNames) databaseNames += name
        return AsyncRelayDatabase(context, name).also { opened += it }
    }
    private fun configured(): RelayPreferences = preferences().also {
        assertTrue(it.selectDestination(destination))
        assertTrue(it.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 100L, 1000L))
    }
    private fun initial(id: Long = 101L): AsyncRelaySnapshot {
        val key = RelayKey(account, source, "m:$id")
        return AsyncRelaySnapshot(AsyncRelayIntent(key, scope, listOf(id), false, 1000L,
            configurationRevision = 7L), AsyncRelayPhase.HEADER_WAITING,
            listOf(AsyncRelayRequest("header-$id", AsyncRelayStage.HEADER, emptyList(), 31000L)))
    }

    @After fun cleanupOwnFixtures() {
        opened.forEach { it.close() }
        databaseNames.forEach { context.deleteDatabase(it) }
        context.getSharedPreferences(RelayPreferences.fileName(account), Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun pauseSurvivesNewPreferenceWrapperAndNewGateWithoutTransportOpeningIt() {
        val p = configured()
        assertTrue(p.changeEnabled(true))
        val gate = SingleRelayRecoveryGate(store(), "client-before")
        val token = requireNotNull(gate.pause(scope, p.revision()))
        assertEquals(token, preferences().recoveryPause())
        val reopened = SingleRelayRecoveryGate(store(), "client-after")
        assertTrue(reopened.isPaused(account))
        assertEquals(token, reopened.token(account))
        reopened.observeTransport(null)
        assertTrue(reopened.isPaused(account))
        assertTrue(preferences().enabled) // User intent survives; the independent fence stays closed.
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
    }

    @Test fun staleCasCannotReplaceOrRemoveANewerDurablePause() {
        val gate = SingleRelayRecoveryGate(store(), "client-cas")
        val first = requireNotNull(gate.pause(scope, 1L))
        val second = requireNotNull(gate.pause(scope.copy(sessionId = "persistence-next"), 2L))
        assertNotEquals(first.nonce, second.nonce)
        assertFalse(preferences().compareAndSetRecoveryPause(first, null))
        assertFalse(preferences().compareAndSetRecoveryPause(first, first.copy(nonce = "stale-replacement")))
        assertEquals(second, preferences().recoveryPause())
        assertTrue(preferences().compareAndSetRecoveryPause(second, null))
        assertNull(preferences().recoveryPause())
    }

    @Test fun ownerSwitchAndRouteChangesNeverClearTheIndependentFence() {
        val p = configured()
        val gate = SingleRelayRecoveryGate(store(), "client-routing")
        val token = requireNotNull(gate.pause(scope, p.revision()))
        assertTrue(p.changeEnabled(true))
        assertTrue(p.changeEnabled(false))
        assertTrue(p.changeEnabled(true))
        assertTrue(p.selectDestination(destination - 1L))
        assertTrue(p.changeEnabled(true))
        assertEquals(token, preferences().recoveryPause())
        assertTrue(SingleRelayRecoveryGate(store(), "another-client").isPaused(account))
        assertEquals(100L, preferences().sourceBaseline(source))
        assertEquals(1000L, preferences().enabledAt(source))
    }

    @Test fun recoveryCasDoesNotMutateRoutingRevisionPhotoConsentOrBaselines() {
        val p = configured()
        assertTrue(p.setSinglePhotoOnlyConsent(source, true))
        assertTrue(p.changeEnabled(true))
        val revision = p.revision()
        val token = requireNotNull(SingleRelayRecoveryGate(store(), "client-revision").pause(scope, revision))
        assertEquals(revision, preferences().revision())
        assertTrue(preferences().singlePhotoOnlyConsent(source))
        assertTrue(preferences().enabled)
        assertTrue(preferences().compareAndSetRecoveryPause(token, null))
        assertEquals(revision, preferences().revision())
        assertEquals(100L, preferences().sourceBaseline(source))
        assertEquals(1000L, preferences().enabledAt(source))
        assertTrue(preferences().singlePhotoOnlyConsent(source))
        assertTrue(preferences().enabled)
    }

    @Test fun corruptDurableFenceFailsClosedAndIsNotErasedByReading() {
        val raw = context.getSharedPreferences(RelayPreferences.fileName(account), Context.MODE_PRIVATE)
        assertTrue(raw.edit().putString("single-recovery-pause", "{not-valid-json").commit())
        val gate = SingleRelayRecoveryGate(store(), "client-corrupt")
        assertTrue(gate.isPaused(account))
        assertNull(gate.token(account))
        gate.observeTransport(null)
        assertTrue(gate.isPaused(account))
        assertNull(gate.pause(scope, 0L))
        assertEquals("{not-valid-json", raw.getString("single-recovery-pause", null))
    }

    @Test fun processRecoveryCannotEraseFenceNeedWhileCompletedDeliveryDoesNotCreateOne() {
        val pendingName = "recovery-pending-${UUID.randomUUID()}.db"
        val first = database(pendingName)
        val start = initial()
        assertTrue(first.create(start))
        assertTrue(first.requiresProcessRecoveryFence(account))
        assertEquals(scope, first.processRecoveryScope(account))
        first.close()
        val reopened = database(pendingName)
        assertTrue(reopened.requiresProcessRecoveryFence(account))
        assertEquals(1, reopened.recoverPending(account))
        assertEquals(AsyncRelayPhase.UNKNOWN, reopened.read(start.key)?.phase)
        assertEquals("PROCESS_RECOVERY_UNKNOWN", reopened.read(start.key)?.reason)
        assertTrue(reopened.requiresProcessRecoveryFence(account))
        assertEquals(scope, reopened.processRecoveryScope(account))
        assertEquals(0, reopened.recoverPending(account))
        reopened.close()
        assertTrue(database(pendingName).requiresProcessRecoveryFence(account))

        val complete = database()
        val reference = initial(102L)
        assertTrue(complete.create(reference))
        val bound = reference.copy(requests = listOf(reference.requests.single().copy(responseBound = true,
            temporaryMessageIds = listOf(2001L))), version = 1L)
        assertTrue(complete.compareAndSet(0L, bound))
        val confirmed = bound.copy(phase = AsyncRelayPhase.COMPLETE,
            requests = listOf(bound.requests.single().copy(finalMessageIds = mapOf(2001L to 3001L))), version = 2L)
        assertTrue(complete.compareAndSet(1L, confirmed))
        assertFalse(complete.requiresProcessRecoveryFence(account))
        assertNull(complete.processRecoveryScope(account))
        assertEquals(0, complete.recoverPending(account))
    }

    @Test fun timeoutUnknownStillRequiresOldIdentityFenceAfterDatabaseReopen() {
        val name = "recovery-timeout-${UUID.randomUUID()}.db"
        val first = database(name)
        val start = initial(103L)
        assertTrue(first.create(start))
        val unknown = start.copy(phase = AsyncRelayPhase.UNKNOWN, version = 1L, reason = "REQUEST_TIMEOUT")
        assertTrue(first.compareAndSet(0L, unknown))
        assertEquals(0, first.recoverPending(account))
        first.close()
        val reopened = database(name)
        assertEquals(unknown, reopened.read(start.key))
        assertTrue(reopened.requiresProcessRecoveryFence(account))
        assertEquals(scope, reopened.processRecoveryScope(account))
        assertEquals(0, reopened.recoverPending(account))
        assertTrue(reopened.requiresProcessRecoveryFence(account))
    }
}
