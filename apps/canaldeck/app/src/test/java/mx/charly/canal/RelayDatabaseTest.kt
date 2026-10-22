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
class RelayDatabaseTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val name = "relay-test-${UUID.randomUUID()}.db"
    private val opened = mutableListOf<RelayDatabase>()
    private val account = 91001L
    private val source = -10011L
    private fun db() = RelayDatabase(context, name).also { opened += it }
    private fun key(id: String = "m:101", a: Long = account, s: Long = source) = RelayKey(a, s, id)

    @After fun cleanup() { opened.forEach { it.close() }; context.deleteDatabase(name) }

    @Test fun unarmedOrHistoricalMessagesCannotBecomeObserved() {
        val d = db()
        assertFalse(d.acceptObserved(key(), listOf(101), 1001))
        d.checkpoint(account, source, 100, 1000)
        assertFalse(d.acceptObserved(key("m:100"), listOf(100), 1001))
        assertFalse(d.acceptObserved(key(), listOf(101), 999))
        assertFalse(d.acceptObserved(key(), listOf(101), 1000))
        assertTrue(d.acceptObserved(key(), listOf(101), 1001))
        assertFalse(d.acceptObserved(key(), listOf(101), 1001))
    }

    @Test fun observedDedupSurvivesReopenAndIsSourceAccountScoped() {
        val first = db()
        first.checkpoint(account, source, 100, 1000)
        assertTrue(first.acceptObserved(key("a:12"), listOf(101, 102), 1001))
        first.close()
        val again = db()
        assertFalse(again.acceptObserved(key("a:12"), listOf(101, 102), 1001))
        again.checkpoint(account, source - 1, 100, 1000)
        assertTrue(again.acceptObserved(key("a:12", s = source - 1), listOf(101, 102), 1001))
        again.checkpoint(account + 1, source, 100, 1000)
        assertTrue(again.acceptObserved(key("a:12", a = account + 1), listOf(101, 102), 1001))
    }

    @Test fun checkpointNeverRewindsAfterReconnectOrClockRollback() {
        val d = db()
        d.checkpoint(account, source, 100, 1000)
        d.checkpoint(account, source, 50, 900)
        assertEquals(RelayBaseline(100, 1000), d.baseline(account, source))
        d.checkpoint(account, source, 200, 1100)
        assertEquals(RelayBaseline(200, 1100), d.baseline(account, source))
        assertFalse(d.acceptObserved(key("m:150"), listOf(150), 1200))
    }

    @Test fun albumMembersCannotReplayUnderAnotherWrapperAndRejectIsAtomic() {
        val first = db()
        first.checkpoint(account, source, 100, 1000)
        assertTrue(first.acceptObserved(key("a:12"), listOf(101, 102), 1001))
        first.close()
        val again = db()
        assertFalse(again.acceptObserved(key("m:101"), listOf(101), 1001))
        assertFalse(again.acceptObserved(key("a:13"), listOf(103, 101), 1001))
        // Rejecting a partially overlapping album did not reserve its new member.
        assertTrue(again.acceptObserved(key("m:103"), listOf(103), 1001))
    }

    @Test fun allDeliveryStatesArePersistentAndReservationsNeverOverwrite() {
        val first = db()
        assertTrue(first.claim(key()))
        assertFalse(first.claim(key()))
        assertEquals(RelayDeliveryState.PENDING, first.state(key()))
        first.finish(key(), RelayDeliveryState.CONFIRMED)
        first.close()
        val again = db()
        assertEquals(RelayDeliveryState.CONFIRMED, again.state(key()))
        assertFalse(again.claim(key()))
        again.finish(key(), RelayDeliveryState.UNKNOWN)
        assertEquals(RelayDeliveryState.CONFIRMED, again.state(key()))
    }

    @Test fun crashBetweenPlanAndAckBecomesUnknownAndCannotRetry() {
        val first = db()
        assertTrue(first.claim(key()))
        first.close()
        val afterCrash = db()
        assertEquals(1, afterCrash.recoverPending(account))
        assertEquals(RelayDeliveryState.UNKNOWN, afterCrash.state(key()))
        assertFalse(afterCrash.claim(key()))
        afterCrash.finish(key(), RelayDeliveryState.CONFIRMED)
        assertEquals(RelayDeliveryState.UNKNOWN, afterCrash.state(key()))
        assertEquals(0, afterCrash.recoverPending(account))
    }

    @Test fun recoveryDoesNotTouchAnotherAccountOrItsCheckpoint() {
        val d = db()
        d.checkpoint(account, source, 100, 1000)
        assertTrue(d.claim(key()))
        assertTrue(d.claim(key(a = account + 1)))
        assertEquals(1, d.recoverPending(account))
        assertEquals(RelayDeliveryState.PENDING, d.state(key(a = account + 1)))
        assertEquals(RelayBaseline(100, 1000), d.baseline(account, source))
    }

    @Test fun twoDatabaseClientsCanOnlyReserveOnce() {
        val first = db(); val second = db()
        first.writableDatabase; second.writableDatabase
        val pool = Executors.newFixedThreadPool(2)
        try {
            val gate = CountDownLatch(1)
            val tasks = listOf(first, second).map { d -> pool.submit(Callable { gate.await(); d.claim(key()) }) }
            gate.countDown()
            assertEquals(1, tasks.count { it.get(10, TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }

    @Test fun countersPersistAndSeparateWindowsChatsAccountsWithoutRealClaims() {
        val d = db(); val k = RelayCounterKey(account, source, 3600)
        d.increment(k, RelayMetric.SEEN, 4)
        d.increment(k, RelayMetric.CANDIDATES)
        d.increment(k, RelayMetric.UNIQUE)
        d.increment(k, RelayMetric.PREPARED)
        d.increment(k.copy(windowStartUnix = 7200), RelayMetric.SEEN, 7)
        d.increment(k.copy(sourceChatId = source - 1), RelayMetric.SEEN, 9)
        d.increment(k.copy(accountId = account + 1), RelayMetric.SEEN, 11)
        d.close()
        val again = db()
        assertEquals(4L, again.counters(k)[RelayMetric.SEEN])
        assertEquals(1L, again.counters(k)[RelayMetric.PREPARED])
        assertEquals(0L, again.counters(k)[RelayMetric.REAL_CONFIRMED])
        assertEquals(listOf(7200L, 3600L), again.counterWindows(account, source))
        assertEquals(7L, again.counters(k.copy(windowStartUnix = 7200))[RelayMetric.SEEN])
        assertEquals(9L, again.counters(k.copy(sourceChatId = source - 1))[RelayMetric.SEEN])
        assertEquals(11L, again.counters(k.copy(accountId = account + 1))[RelayMetric.SEEN])
        assertThrows(IllegalArgumentException::class.java) { again.increment(k, RelayMetric.REAL_CONFIRMED) }
        assertEquals(0L, again.counters(k)[RelayMetric.REAL_CONFIRMED])
    }

    @Test fun invalidKeysAndIncompleteMessageIdentityDoNotPolluteStore() {
        val d = db()
        d.checkpoint(account, source, 100, 1000)
        assertFalse(d.acceptObserved(key(), emptyList(), 1001))
        assertFalse(d.acceptObserved(key(), listOf(102), 1001))
        assertFalse(d.acceptObserved(key("a:12"), listOf(101, 101), 1001))
        assertThrows(IllegalArgumentException::class.java) { d.claim(key("caption private message")) }
        assertThrows(IllegalArgumentException::class.java) { d.claim(key(a = 0)) }
        assertThrows(IllegalArgumentException::class.java) { d.claim(key(s = 0)) }
        assertThrows(IllegalArgumentException::class.java) { d.finish(key(), RelayDeliveryState.PENDING) }
    }

    @Test fun schemaStoresOnlyIdentifiersStatesAndAggregates() {
        val d = db()
        val expected = mapOf(
            "baselines" to setOf("account", "source", "latest", "not_before"),
            "observed" to setOf("account", "source", "unit_key"),
            "deliveries" to setOf("account", "source", "unit_key", "state"),
            "coverage" to setOf("account", "source", "window_start", "metric", "value")
        )
        expected.forEach { (table, columns) ->
            d.readableDatabase.rawQuery("PRAGMA table_info($table)", null).use { c ->
                val actual = mutableSetOf<String>()
                while (c.moveToNext()) actual += c.getString(1)
                assertEquals(columns, actual)
            }
        }
    }
}
