package mx.charly.canal

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Android SQLite and asynchronous Fake updates only; no credentials, login or TDLib client. */
@RunWith(AndroidJUnit4::class)
class AsyncRelayPersistenceDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "async-device-${UUID.randomUUID()}.db"
    private val opened = mutableListOf<AsyncRelayDatabase>()
    private fun database() = AsyncRelayDatabase(context, name).also { opened += it }
    @After fun cleanup() { opened.forEach { it.close() }; context.deleteDatabase(name) }

    private class Fixture {
        val now = 1_792_695_600_000L
        val scope = AsyncRelayScope(960041, "device-fixture-session", -100602)
        val key = RelayKey(scope.accountId, -100601, "a:7001")
        val messages = listOf(101L, 102L).map { id -> RelayMessage(scope.accountId, key.sourceChatId, id,
            now / 1000 - 1, "Pick: Over 8.5 @1.80", RelayContentKind.PHOTO, 7001,
            canBeForwarded = true, permalink = "https://t.me/synthetic_source/$id") }
        val config = RelayConfig(scope.accountId, scope.destinationChatId, true,
            listOf(RelaySourceBinding(scope.accountId, key.sourceChatId, "Synthetic Fano", RelaySourcePolicy.FANO_DIRECT, true)))
        val permission = RelayPermissions(scope.accountId, key.sourceChatId, scope.destinationChatId, true, true, false,
            messages.associate { it.messageId to true }, messages.associate { it.messageId to false })
        val state = AsyncRelayFreshState(scope, RelayBatch(messages, synthetic = true), config, permission)
        val proof = AlbumCompletenessEvidence.FakeManifest(scope.accountId, key.sourceChatId, 7001, listOf(101, 102))
        val intent = AsyncRelayIntent(key, scope, listOf(101, 102), true, now)
        fun success(temporary: Long, final: Long) = AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED,
            temporary, final, finalIsOutgoing = true, finalHasSendingState = false)
    }

    @Test fun exactFinalUpdatesCompleteWholeFakeAlbumAndReopenNeverDispatches() {
        val evidence = File(context.filesDir, "canaldeck-async-evidence.json")
        evidence.delete(); assertFalse(evidence.exists())
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        val f = Fixture(); val db = database(); val fake = FakeAsyncRelayTransport(f.state)
        val engine = AsyncRelayEngine(db, fake) { f.now }
        // A caller's completeness booleans do not prove a native incoming album.
        assertNull(engine.start(f.intent, AlbumCompletenessEvidence.ProviderUnknown))
        assertTrue(fake.requests.isEmpty())
        var intentPrecededOutput = false
        fake.beforeHeaderResponse = {
            val pending = requireNotNull(db.read(f.key))
            intentPrecededOutput = pending.phase == AsyncRelayPhase.HEADER_WAITING && !pending.requests.single().responseBound
            assertTrue(intentPrecededOutput)
        }
        val header = requireNotNull(engine.start(f.intent, f.proof))
        assertEquals(AsyncRelayPhase.HEADER_WAITING, header.phase)
        val headerTemporary = header.requests.single().temporaryMessageIds.single()
        val quick = requireNotNull(engine.onUpdate(f.key,
            AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.ACKNOWLEDGED, headerTemporary)))
        assertEquals(AsyncRelayPhase.HEADER_WAITING, quick.phase)
        assertEquals(1, fake.requests.size)
        val media = requireNotNull(engine.onUpdate(f.key, f.success(headerTemporary, 501)))
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, media.phase)
        val temporaries = media.requests.last().temporaryMessageIds
        assertEquals(2, temporaries.size)
        val onePhoto = requireNotNull(engine.onUpdate(f.key, f.success(temporaries.last(), 602)))
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, onePhoto.phase)
        val complete = requireNotNull(engine.onUpdate(f.key, f.success(temporaries.first(), 601)))
        assertEquals(AsyncRelayPhase.COMPLETE, complete.phase)
        assertEquals(temporaries.toSet(), complete.requests.last().finalMessageIds.keys)
        assertEquals(setOf(601L, 602L), complete.requests.last().finalMessageIds.values.toSet())
        assertEquals(listOf(AsyncRelayStage.HEADER, AsyncRelayStage.MEDIA), fake.requests.map { it.stage })
        assertEquals(listOf(101L, 102L), fake.requests.last().sourceMessageIds)
        assertEquals(0, fake.sentRealMessages)
        db.close()
        val reopened = database()
        assertEquals(0, reopened.recoverPending(f.scope.accountId))
        assertEquals(complete, reopened.read(f.key))
        val freshFake = FakeAsyncRelayTransport(f.state)
        assertEquals(AsyncRelayPhase.COMPLETE, AsyncRelayEngine(reopened, freshFake) { f.now }.start(f.intent, f.proof)?.phase)
        assertTrue(freshFake.requests.isEmpty())
        evidence.writeText(JSONObject()
            .put("schema_version", 1).put("runtime", "Android SQLite + asynchronous Fake TDLib")
            .put("compiled_real_output_enabled", BuildConfig.PICK_RELAY_REAL_ENABLED)
            .put("sent_real_messages", fake.sentRealMessages + freshFake.sentRealMessages)
            .put("intent_preceded_output", intentPrecededOutput)
            .put("provider_unknown_album_held", true).put("fake_manifest_only", true)
            .put("queued_was_final", false).put("quick_ack_was_final", false)
            .put("partial_final_was_complete", false)
            .put("fake_headers", fake.requests.count { it.stage == AsyncRelayStage.HEADER })
            .put("fake_media_requests", fake.requests.count { it.stage == AsyncRelayStage.MEDIA })
            .put("expected_photos", 2).put("final_photo_set_size", complete.requests.last().finalMessageIds.size)
            .put("reopened_phase", reopened.read(f.key)?.phase?.name)
            .put("new_requests_after_reopen", freshFake.requests.size).toString(2) + "\n")
    }

    @Test fun unresolvedHeaderAfterReopenIsUnknownAndLateSuccessCannotResume() {
        val f = Fixture(); val db = database(); val firstFake = FakeAsyncRelayTransport(f.state)
        val pending = requireNotNull(AsyncRelayEngine(db, firstFake) { f.now }.start(f.intent, f.proof))
        val oldTemporary = pending.requests.single().temporaryMessageIds.single()
        db.close()
        val reopened = database()
        assertEquals(1, reopened.recoverPending(f.scope.accountId, f.scope.sessionId))
        val afterCrash = FakeAsyncRelayTransport(f.state)
        val engine = AsyncRelayEngine(reopened, afterCrash) { f.now }
        assertEquals(AsyncRelayPhase.UNKNOWN, engine.start(f.intent, f.proof)?.phase)
        assertEquals(AsyncRelayPhase.UNKNOWN, engine.onUpdate(f.key, f.success(oldTemporary, 501))?.phase)
        assertTrue(afterCrash.requests.isEmpty())
        assertEquals(0, afterCrash.sentRealMessages)
    }

    @Test fun partialQueuedAlbumIsDurablyUnknownEvenAfterLateFinalAndReplay() {
        val f = Fixture(); val db = database(); val fake = FakeAsyncRelayTransport(f.state)
        val engine = AsyncRelayEngine(db, fake) { f.now }
        val pending = requireNotNull(engine.start(f.intent, f.proof))
        fake.mediaResponseFactory = { request, scope -> AsyncRelayResponse(scope, request.requestId,
            listOf(AsyncRelayQueuedMessage(-21, scope.destinationChatId), null)) }
        val unknown = requireNotNull(engine.onUpdate(f.key,
            f.success(pending.requests.single().temporaryMessageIds.single(), 501)))
        assertEquals(AsyncRelayPhase.UNKNOWN, unknown.phase)
        assertEquals(2, fake.requests.size)
        db.close()
        val reopened = database(); val again = FakeAsyncRelayTransport(f.state)
        val next = AsyncRelayEngine(reopened, again) { f.now }
        assertEquals(AsyncRelayPhase.UNKNOWN, next.start(f.intent, f.proof)?.phase)
        assertEquals(AsyncRelayPhase.UNKNOWN, next.onUpdate(f.key, f.success(-21, 601))?.phase)
        assertTrue(again.requests.isEmpty())
    }

    @Test fun finalBeforeQueuedResponseUsesPersistedExactCorrelation() {
        val f = Fixture(); val db = database(); val fake = FakeAsyncRelayTransport(f.state)
        val engine = AsyncRelayEngine(db, fake) { f.now }
        fake.beforeHeaderResponse = { request ->
            assertEquals(request.requestId, db.read(f.key)?.requests?.single()?.requestId)
            engine.onUpdate(f.key, f.success(100_000, 501))
            assertEquals(1, db.read(f.key)?.requests?.single()?.earlyUpdates?.size)
        }
        val media = requireNotNull(engine.start(f.intent, f.proof))
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, media.phase)
        assertEquals(mapOf(100_000L to 501L), media.requests.first().finalMessageIds)
        assertTrue(media.requests.first().earlyUpdates.isEmpty())
        assertEquals(2, fake.requests.size)
    }

    @Test fun competingAndroidWritersHaveOneWinnerAndNeverCrossScope() {
        val f = Fixture(); val a = database(); val b = database()
        a.writableDatabase; b.writableDatabase
        val start = AsyncRelaySnapshot(f.intent, AsyncRelayPhase.HEADER_WAITING,
            listOf(AsyncRelayRequest("device-header", AsyncRelayStage.HEADER, emptyList(), f.now + 30_000)))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val gate = CountDownLatch(1)
            val jobs = listOf(a, b).map { d -> pool.submit(Callable { gate.await(); d.create(start) }) }
            gate.countDown()
            assertEquals(1, jobs.count { it.get(10, TimeUnit.SECONDS) })
            val wrongScope = start.copy(intent = start.intent.copy(scope = f.scope.copy(sessionId = "other-session")), version = 1)
            assertFalse(b.compareAndSet(0, wrongScope))
            assertEquals(start, a.read(f.key))
        } finally { pool.shutdownNow() }
    }
}
