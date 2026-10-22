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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AsyncRelayDatabaseTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val name = "async-relay-test-${UUID.randomUUID()}.db"
    private val opened = mutableListOf<AsyncRelayDatabase>()
    private val account = 950041L
    private val source = -100501L
    private val scope = AsyncRelayScope(account, "fixture-session-A", -100502L)
    private fun db() = AsyncRelayDatabase(context, name).also { opened += it }

    @After fun clean() { opened.forEach { it.close() }; context.deleteDatabase(name) }

    private fun initial(id: Long = 101, media: Boolean = false, selectedScope: AsyncRelayScope = scope): AsyncRelaySnapshot {
        val key = RelayKey(selectedScope.accountId, source, "m:$id")
        return AsyncRelaySnapshot(AsyncRelayIntent(key, selectedScope, listOf(id), media, 1000, configurationRevision = 7),
            AsyncRelayPhase.HEADER_WAITING, listOf(AsyncRelayRequest("header-$id-${selectedScope.sessionId}", AsyncRelayStage.HEADER, emptyList(), 31000)))
    }
    private fun bound(old: AsyncRelaySnapshot, ids: List<Long>): AsyncRelaySnapshot = old.copy(
        requests = old.requests.dropLast(1) + old.requests.last().copy(responseBound = true, temporaryMessageIds = ids),
        version = old.version + 1)
    private fun finals(old: AsyncRelaySnapshot, pairs: Map<Long, Long>, phase: AsyncRelayPhase = old.phase) = old.copy(
        requests = old.requests.dropLast(1) + old.requests.last().copy(finalMessageIds = pairs), phase = phase, version = old.version + 1)

    @Test fun immutableIntentIsDurableBeforeAnyQueuedResponseAndCrashCannotResume() {
        val first = db(); val start = initial()
        assertTrue(first.create(start))
        assertEquals(start, first.read(start.key))
        first.close()
        val reopened = db()
        assertEquals(start, reopened.read(start.key))
        assertEquals(1, reopened.recoverPending(account, scope.sessionId))
        val unknown = requireNotNull(reopened.read(start.key))
        assertEquals(AsyncRelayPhase.UNKNOWN, unknown.phase)
        assertEquals("PROCESS_RECOVERY_UNKNOWN", unknown.reason)
        assertFalse(reopened.create(start))
        assertFalse(reopened.compareAndSet(unknown.version, bound(unknown, listOf(-11))))
        assertEquals(0, reopened.recoverPending(account))
    }

    @Test fun queuedAndAcknowledgedAreNotFinalConfirmation() {
        val d = db(); val start = initial()
        assertTrue(d.create(start))
        val queued = bound(start, listOf(-11))
        assertTrue(d.compareAndSet(start.version, queued))
        assertEquals(AsyncRelayPhase.HEADER_WAITING, d.read(start.key)?.phase)
        val confirmed = finals(queued, mapOf(-11L to 501L), AsyncRelayPhase.COMPLETE)
        assertTrue(d.compareAndSet(queued.version, confirmed))
        assertEquals(confirmed, d.read(start.key))
        assertFalse(d.compareAndSet(confirmed.version, confirmed.copy(phase = AsyncRelayPhase.UNKNOWN, version = confirmed.version + 1)))
    }

    @Test fun wholeExpectedAlbumSetMustBeConfirmedAndHeaderHistorySurvives() {
        val d = db()
        val solo = initial(media = true)
        val start = solo.copy(intent = solo.intent.copy(key = solo.key.copy(groupId = "a:7001"), sourceMessageIds = listOf(101, 102)))
        assertTrue(d.create(start))
        val queuedHeader = bound(start, listOf(-11))
        assertTrue(d.compareAndSet(0, queuedHeader))
        val confirmedHeader = finals(queuedHeader, mapOf(-11L to 501L))
        assertTrue(d.compareAndSet(1, confirmedHeader))
        val mediaIntent = confirmedHeader.copy(phase = AsyncRelayPhase.MEDIA_WAITING, version = 3,
            requests = confirmedHeader.requests + AsyncRelayRequest("media-7001", AsyncRelayStage.MEDIA, listOf(101, 102), 61000))
        assertTrue(d.compareAndSet(2, mediaIntent))
        val queuedMedia = bound(mediaIntent, listOf(-21, -22))
        assertTrue(d.compareAndSet(3, queuedMedia))
        val partial = finals(queuedMedia, mapOf(-21L to 601L))
        assertTrue(d.compareAndSet(4, partial))
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, d.read(start.key)?.phase)
        assertFalse(d.compareAndSet(5, partial.copy(phase = AsyncRelayPhase.COMPLETE, version = 6)))
        val complete = finals(partial, mapOf(-21L to 601L, -22L to 602L), AsyncRelayPhase.COMPLETE)
        assertTrue(d.compareAndSet(5, complete))
        assertEquals(mapOf(-11L to 501L), d.read(start.key)?.requests?.first()?.finalMessageIds)
        assertEquals(AsyncRelayPhase.COMPLETE, d.read(start.key)?.phase)
    }

    @Test fun temporaryCollisionClosesBothUnresolvedOwnersAndCannotBeReassigned() {
        val d = db(); val a = initial(); val b = initial(201)
        assertTrue(d.create(a)); assertTrue(d.create(b))
        assertTrue(d.compareAndSet(0, bound(a, listOf(-77))))
        assertFalse(d.compareAndSet(0, bound(b, listOf(-77))))
        val unknownA = requireNotNull(d.read(a.key)); val unknownB = requireNotNull(d.read(b.key))
        assertEquals(AsyncRelayPhase.UNKNOWN, unknownA.phase)
        assertEquals(AsyncRelayPhase.UNKNOWN, unknownB.phase)
        assertFalse(d.compareAndSet(unknownA.version, finals(unknownA, mapOf(-77L to 800L), AsyncRelayPhase.COMPLETE)))
        val c = initial(301)
        assertTrue(d.create(c))
        assertFalse(d.compareAndSet(0, bound(c, listOf(-77))))
        assertEquals(AsyncRelayPhase.UNKNOWN, d.read(c.key)?.phase)
    }

    @Test fun finalIdCollisionDoesNotOverwriteCompletedEvidence() {
        val d = db(); val a = initial(); val b = initial(201)
        assertTrue(d.create(a)); assertTrue(d.create(b))
        val qa = bound(a, listOf(-1)); val qb = bound(b, listOf(-2))
        assertTrue(d.compareAndSet(0, qa)); assertTrue(d.compareAndSet(0, qb))
        val completed = finals(qa, mapOf(-1L to 901L), AsyncRelayPhase.COMPLETE)
        assertTrue(d.compareAndSet(1, completed))
        assertFalse(d.compareAndSet(1, finals(qb, mapOf(-2L to 901L), AsyncRelayPhase.COMPLETE)))
        assertEquals(completed, d.read(a.key))
        assertEquals(AsyncRelayPhase.UNKNOWN, d.read(b.key)?.phase)
    }

    @Test fun signedTemporaryIdsAreOpaqueAndScopesCannotCrossBind() {
        val d = db()
        val a = initial()
        val b = initial(201, selectedScope = scope.copy(sessionId = "fixture-session-B"))
        val c = initial(301, selectedScope = scope.copy(destinationChatId = -100503L))
        val other = initial(401, selectedScope = scope.copy(accountId = account + 1))
        listOf(a, b, c, other).forEach { start ->
            assertTrue(d.create(start))
            // Positive local IDs are not confused with positive server IDs; the kind is indexed.
            assertTrue(d.compareAndSet(0, bound(start, listOf(17))))
            assertEquals(AsyncRelayPhase.HEADER_WAITING, d.read(start.key)?.phase)
        }
        assertEquals(1, d.recoverPending(account, "fixture-session-B"))
        assertEquals(AsyncRelayPhase.UNKNOWN, d.read(b.key)?.phase)
        assertEquals(AsyncRelayPhase.HEADER_WAITING, d.read(a.key)?.phase)
        assertEquals(AsyncRelayPhase.HEADER_WAITING, d.read(other.key)?.phase)
    }

    @Test fun oldPublicationCannotReplayThroughAnotherAlbumDestinationOrSession() {
        val d = db(); val original = initial()
        val album = original.copy(intent = original.intent.copy(key = original.key.copy(groupId = "a:7001"),
            sourceMessageIds = listOf(101, 102), mediaRequired = true))
        assertTrue(d.create(album))
        assertFalse(d.create(initial(selectedScope = scope.copy(sessionId = "new-session", destinationChatId = -100599))))
        val overlap = initial(102).copy(intent = original.intent.copy(key = original.key.copy(groupId = "a:7002"),
            sourceMessageIds = listOf(102, 103), mediaRequired = true))
        assertFalse(d.create(overlap))
        assertTrue(d.create(initial(103)))
    }

    @Test fun competingCreatesAndCompareAndSetAreAtomicAcrossClients() {
        val a = db(); val b = db(); a.writableDatabase; b.writableDatabase
        val start = initial(); val pool = Executors.newFixedThreadPool(2)
        try {
            val createGate = CountDownLatch(1)
            val creates = listOf(a, b).map { d -> pool.submit(Callable { createGate.await(); d.create(start) }) }
            createGate.countDown()
            assertEquals(1, creates.count { it.get(10, TimeUnit.SECONDS) })
            val updateGate = CountDownLatch(1)
            val updates = listOf(a to -11L, b to -12L).map { (d, temp) -> pool.submit(Callable {
                updateGate.await(); d.compareAndSet(0, bound(start, listOf(temp)))
            }) }
            updateGate.countDown()
            assertEquals(1, updates.count { it.get(10, TimeUnit.SECONDS) })
            assertEquals(1L, a.read(start.key)?.version)
            assertEquals(1, a.read(start.key)?.requests?.single()?.temporaryMessageIds?.size)
        } finally { pool.shutdownNow() }
    }

    @Test fun callerMutationAndEarlyUpdateRoundTripCannotChangePersistedIntent() {
        val d = db(); val start = initial()
        val mutableSources = mutableListOf(101L)
        assertTrue(d.create(start.copy(intent = start.intent.copy(sourceMessageIds = mutableSources))))
        mutableSources[0] = 999
        assertEquals(listOf(101L), d.read(start.key)?.intent?.sourceMessageIds)
        val early = AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED, -11, 501, finalIsOutgoing = true, finalHasSendingState = false)
        val buffered = start.copy(version = 1, requests = listOf(start.requests.single().copy(earlyUpdates = listOf(early))))
        assertTrue(d.compareAndSet(0, buffered))
        d.close()
        assertEquals(early, db().read(start.key)?.requests?.single()?.earlyUpdates?.single())
    }

    @Test fun invalidTransitionsOrPayloadShapedFieldsCannotPersist() {
        val d = db(); val start = initial()
        assertFalse(d.create(start.copy(reason = "private caption https://t.me/private/1")))
        assertFalse(d.create(start.copy(intent = start.intent.copy(scope = scope.copy(sessionId = "https://t.me/private/1")))))
        assertTrue(d.create(start))
        assertFalse(d.compareAndSet(0, start.copy(intent = start.intent.copy(scope = scope.copy(destinationChatId = -5)), version = 1)))
        assertFalse(d.compareAndSet(99, bound(start, listOf(-11))))
        assertFalse(d.compareAndSet(0, bound(start, listOf(0))))
        assertEquals(start, d.read(start.key))
        d.readableDatabase.rawQuery("SELECT metadata FROM async_deliveries", null).use { c ->
            assertTrue(c.moveToFirst())
            val stored = c.getString(0)
            assertFalse(stored.contains("private caption"))
            assertFalse(stored.contains("https://"))
            assertFalse(stored.contains("caption"))
            assertFalse(stored.contains("header body"))
        }
    }
}
