package mx.charly.canal

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real Android SQLite/preferences, synthetic IDs; no TDLib session or Telegram connection. */
@RunWith(AndroidJUnit4::class)
class RelayPersistenceDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "relay-device-${UUID.randomUUID()}.db"
    private val account = 920041L
    private val source = -100401L
    private val destination = -100402L

    @After fun cleanup() {
        context.deleteDatabase(databaseName)
        listOf(account, account + 1).forEach {
            context.getSharedPreferences(RelayPreferences.fileName(it), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun androidReopenKeepsObservedAlbumAndPendingBecomesUnknown() {
        val album = RelayKey(account, source, "a:7001")
        RelayDatabase(context, databaseName).use { d ->
            d.checkpoint(account, source, 100, 1000)
            assertTrue(d.acceptObserved(album, listOf(101, 102), 1001))
            assertTrue(d.claim(album))
            d.increment(RelayCounterKey(account, source, 3600), RelayMetric.PREPARED)
        }
        RelayDatabase(context, databaseName).use { d ->
            assertEquals(RelayBaseline(100, 1000), d.baseline(account, source))
            assertFalse(d.acceptObserved(album, listOf(101, 102), 1001))
            assertEquals(1, d.recoverPending(account))
            assertEquals(RelayDeliveryState.UNKNOWN, d.state(album))
            assertFalse(d.claim(album))
            d.finish(album, RelayDeliveryState.CONFIRMED)
            assertEquals(RelayDeliveryState.UNKNOWN, d.state(album))
            assertEquals(1L, d.counters(RelayCounterKey(account, source, 3600))[RelayMetric.PREPARED])
            assertEquals(0L, d.counters(RelayCounterKey(account, source, 3600))[RelayMetric.REAL_CONFIRMED])
        }
    }

    @Test fun realSQLiteConcurrentReservationsOnlyOneWins() {
        val first = RelayDatabase(context, databaseName)
        val second = RelayDatabase(context, databaseName)
        val pool = Executors.newFixedThreadPool(2)
        try {
            first.writableDatabase; second.writableDatabase
            val latch = CountDownLatch(1)
            val key = RelayKey(account, source, "m:101")
            val answers = listOf(first, second).map { d -> pool.submit(Callable { latch.await(); d.claim(key) }) }
            latch.countDown()
            assertEquals(1, answers.count { it.get(10, TimeUnit.SECONDS) })
            assertEquals(RelayDeliveryState.PENDING, first.state(key))
        } finally {
            pool.shutdownNow(); first.close(); second.close()
        }
    }

    @Test fun androidRoutingIsOffAndStopRetainsCheckpoints() {
        val p = RelayPreferences(context, account)
        assertFalse(p.enabled)
        assertFalse(p.selectSource(source, PickWatchMode.RESELLER, 10, 1000))
        assertTrue(p.selectDestination(destination))
        assertTrue(p.selectSourcePolicy(source, RelaySourcePolicy.UNICOREY_MEX, 100, 1000))
        assertTrue(p.changeEnabled(true))
        assertTrue(p.stop())
        val reopen = RelayPreferences(context, account)
        assertFalse(reopen.enabled)
        assertEquals(destination, reopen.destinationChatId())
        assertEquals(100L, reopen.sourceBaseline(source))
        assertEquals(RelaySourcePolicy.UNICOREY_MEX, reopen.sourcePolicy(source))
        assertFalse(reopen.selectDestination(source))
        assertTrue(RelayPreferences(context, account + 1).sources().isEmpty())
    }

    @Test fun androidCoverageIsPrivateScopedAndCannotClaimRealConfirmations() {
        val firstWindow = RelayCounterKey(account, source, 3600)
        RelayDatabase(context, databaseName).use { d ->
            d.increment(firstWindow, RelayMetric.SEEN, 3)
            d.increment(firstWindow.copy(windowStartUnix = 7200), RelayMetric.SEEN, 2)
            d.increment(firstWindow.copy(accountId = account + 1), RelayMetric.SEEN, 9)
        }
        RelayDatabase(context, databaseName).use { d ->
            assertEquals(3L, d.counters(firstWindow)[RelayMetric.SEEN])
            assertEquals(2L, d.counters(firstWindow.copy(windowStartUnix = 7200))[RelayMetric.SEEN])
            assertEquals(9L, d.counters(firstWindow.copy(accountId = account + 1))[RelayMetric.SEEN])
            assertEquals(0L, d.counters(firstWindow)[RelayMetric.REAL_CONFIRMED])
            var refused = false
            try { d.increment(firstWindow, RelayMetric.REAL_CONFIRMED) } catch (_: IllegalArgumentException) { refused = true }
            assertTrue(refused)
            assertEquals(0L, d.counters(firstWindow)[RelayMetric.REAL_CONFIRMED])
        }
    }

    @Test fun fakeTdlibWithAndroidLedgerForwardsOnceAndSurvivesReopen() {
        val evidence = File(context.filesDir, "canaldeck-relay-evidence.json")
        evidence.delete()
        assertFalse("No stale evidence may survive into this test", evidence.exists())
        assertFalse("v0.4 real output must be blocked at compile time", BuildConfig.PICK_RELAY_REAL_ENABLED)
        val now = 1_800_000_100L
        val message = RelayMessage(account, source, 101, now - 1, "Synthetic Fano pick",
            kind = RelayContentKind.PHOTO, canBeForwarded = true,
            permalink = "https://t.me/synthetic_pick/101")
        val batch = RelayBatch(listOf(message), synthetic = true)
        val config = RelayConfig(account, destination, userEnabled = true,
            sources = listOf(RelaySourceBinding(account, source, "Fano fixture", RelaySourcePolicy.FANO_DIRECT, enabled = true)))
        val permissions = RelayPermissions(account, source, destination, canReadSource = true,
            canWriteDestination = true, messageForwardable = mapOf(101L to true), messageProtected = mapOf(101L to false))
        val fake = FakeRelayTdlib(permission = { permissions })
        var confirmation: RelayDeliveryState? = null
        RelayDatabase(context, databaseName).use { d ->
            d.checkpoint(account, source, 100, now - 10)
            val result = PickRelayEngine(d).deliverSynthetic(batch, config, fake, now)
            confirmation = result.deliveryState
            assertEquals(RelayDeliveryState.CONFIRMED, result.deliveryState)
            assertEquals(1, fake.headers.size)
            assertEquals(listOf(101L), fake.forwards.single().messageIds)
        }
        RelayDatabase(context, databaseName).use { d ->
            assertEquals(RelayDeliveryState.CONFIRMED, d.state(RelayKey(account, source, "m:101")))
            val replay = PickRelayEngine(d).deliverSynthetic(batch, config, fake, now)
            assertEquals(RelayAction.DROP, replay.plan.action)
            assertEquals(1, fake.headers.size)
            assertEquals(1, fake.forwards.size)
            assertEquals(0, fake.sentRealMessages)
            assertEquals(0L, d.counters(RelayCounterKey(account, source, now / 3600 * 3600))[RelayMetric.REAL_CONFIRMED])
            // Written only after the real Android assertions above. No IDs or content.
            evidence.writeText(JSONObject()
                .put("schema_version", 1)
                .put("runtime", "Android instrumentation Fake TDLib + SQLite")
                .put("sent_real_messages", fake.sentRealMessages)
                .put("fake_headers", fake.headers.size)
                .put("fake_forwards", fake.forwards.size)
                .put("fake_confirmation", requireNotNull(confirmation).name)
                .put("reopened_dedup", replay.plan.action == RelayAction.DROP)
                .put("compile_real_output_enabled", BuildConfig.PICK_RELAY_REAL_ENABLED)
                .toString(2) + "\n")
        }
    }

    @Test fun completeAlbumHasOneHeaderAndPermissionRevocationBecomesDurableUnknown() {
        val now = 1_800_000_100L
        val messages = listOf(101L, 102L).map { id ->
            RelayMessage(account, source, id, now - 1, "Synthetic album",
                kind = RelayContentKind.PHOTO, albumId = 7001, canBeForwarded = true,
                permalink = "https://t.me/synthetic_pick/$id")
        }
        val batch = RelayBatch(messages, completeAlbum = true, expectedAlbumSize = 2, synthetic = true)
        val config = RelayConfig(account, destination, userEnabled = true,
            sources = listOf(RelaySourceBinding(account, source, "Fano fixture", RelaySourcePolicy.FANO_DIRECT, enabled = true)))
        val permitted = RelayPermissions(account, source, destination, canReadSource = true,
            canWriteDestination = true, messageForwardable = mapOf(101L to true, 102L to true),
            messageProtected = mapOf(101L to false, 102L to false))
        val fake = FakeRelayTdlib(permission = { reads -> if (reads >= 2) permitted.copy(canWriteDestination = false) else permitted })
        RelayDatabase(context, databaseName).use { d ->
            d.checkpoint(account, source, 100, now - 10)
            val result = PickRelayEngine(d).deliverSynthetic(batch, config, fake, now)
            assertEquals(RelayDeliveryState.UNKNOWN, result.deliveryState)
            assertEquals(1, fake.headers.size)
            assertTrue(fake.forwards.isEmpty())
        }
        RelayDatabase(context, databaseName).use { d ->
            assertEquals(RelayDeliveryState.UNKNOWN, d.state(RelayKey(account, source, "a:7001")))
            assertEquals(0, d.recoverPending(account))
            assertFalse(d.claim(RelayKey(account, source, "a:7001")))
            assertEquals(0, fake.sentRealMessages)
        }
    }
}
