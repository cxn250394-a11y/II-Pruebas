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

/** The integration controller and real Android SQLite; the only output port is a recording Fake. */
@RunWith(AndroidJUnit4::class)
/** Fake integration evidence for the offline artifact, with no account or provider calls. */
class SingleRelayIntegrationDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString()
    private val relayName = "single-ingress-device-$id.db"
    private val asyncName = "single-async-device-$id.db"
    private val opened = mutableListOf<AutoCloseable>()
    private fun relayDatabase() = RelayDatabase(context, relayName).also { opened += it }
    private fun asyncDatabase() = AsyncRelayDatabase(context, asyncName).also { opened += it }
    @After fun cleanup() {
        opened.forEach { it.close() }
        context.deleteDatabase(relayName); context.deleteDatabase(asyncName)
    }

    private class Fixture(kind: RelayContentKind = RelayContentKind.PHOTO) {
        val now = 1_792_695_600_000L
        val scope = AsyncRelayScope(970041, "single-device-generation", -100802)
        val observed = SingleRelayObserved(scope.accountId, scope.sessionId, -100801, 101, now / 1000 - 1)
        val key get() = observed.key
        val message = RelayMessage(scope.accountId, observed.sourceChatId, observed.messageId,
            observed.originalUnix, "Pick: Over 2.5 goles @1.80", kind,
            canBeForwarded = true, permalink = "https://t.me/single_fixture/101")
        val fresh = SingleRelayFreshState(AsyncRelayFreshState(scope,
            RelayBatch(listOf(message), synthetic = true),
            RelayConfig(scope.accountId, scope.destinationChatId, true,
                listOf(RelaySourceBinding(scope.accountId, observed.sourceChatId, "Fano ficticio", RelaySourcePolicy.FANO_DIRECT, true))),
            RelayPermissions(scope.accountId, observed.sourceChatId, scope.destinationChatId, true, true,
                messageForwardable = mapOf(101L to true), messageProtected = mapOf(101L to false))), true)
        fun final(temporary: Long, actual: Long) = AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED,
            temporary, actual, finalIsOutgoing = true, finalHasSendingState = false)
    }

    private class DeviceFakePort(private val fresh: SingleRelayFreshState) : SingleRelayPort {
        override val mode = SingleRelayPortMode.FAKE
        val requests = mutableListOf<SingleRelayCommand>()
        var refreshes = 0
        val sentRealMessages: Int get() = 0
        private var temporary = 100_000L
        override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) {
            refreshes++; callback(fresh)
        }
        override fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit) {
            assertEquals(AsyncRelayStage.MEDIA, command.request.stage)
            assertTrue(requireNotNull(command.confirmedHeaderMessageId) > 0L)
            assertFalse(command.expectedHeaderText.isNullOrBlank())
            callback(true) // Only this explicit synthetic fixture attests its Fake header.
        }
        override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
            requests += command
            callback(SingleRelayDispatchResult.Queued(AsyncRelayResponse(command.scope, command.request.requestId,
                listOf(AsyncRelayQueuedMessage(temporary++, command.scope.destinationChatId)))))
        }
    }

    private fun admission(db: RelayDatabase, f: Fixture) = ExistingClientRelayAdmission(db).apply {
        armLoaded(f.scope.accountId, f.scope.sessionId, f.observed.sourceChatId, 100, f.observed.originalUnix - 1)
    }

    @Test fun captionedSinglePhotoRequiresBothFinalsAndReopensWithoutDuplicateOutput() {
        val evidence = File(context.filesDir, "canaldeck-single-evidence.json")
        evidence.delete(); assertFalse(evidence.exists())
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        assertTrue(BuildConfig.OFFLINE_REHEARSAL_ONLY)
        val f = Fixture(); val baseline = relayDatabase(); val ledger = asyncDatabase()
        val fence = admission(baseline, f); val fake = DeviceFakePort(f.fresh)
        val controller = SingleRelayController(ledger, fake, fence) { f.now }
        controller.start(f.observed)
        val header = requireNotNull(controller.snapshot(f.key))
        assertEquals(AsyncRelayPhase.HEADER_WAITING, header.phase)
        assertEquals(1, fake.requests.size)
        val headerTemporary = header.requests.single().temporaryMessageIds.single()
        assertTrue(fake.requests.single().header!!.startsWith("Canal: Fano ficticio"))
        controller.onUpdate(AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.ACKNOWLEDGED, headerTemporary))
        assertEquals(header, controller.snapshot(f.key))
        assertEquals(1, fake.requests.size)
        controller.onUpdate(f.final(headerTemporary, 501))
        val media = requireNotNull(controller.snapshot(f.key))
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, media.phase)
        assertEquals(2, fake.requests.size)
        assertEquals(f.observed.sourceChatId, fake.requests.last().sourceChatId)
        assertEquals(listOf(f.observed.messageId), fake.requests.last().request.expectedSourceMessageIds)
        assertEquals(501L, requireNotNull(fake.requests.last().confirmedHeaderMessageId))
        assertEquals(fake.requests.first().header, fake.requests.last().expectedHeaderText)
        val mediaTemporary = media.requests.last().temporaryMessageIds.single()
        controller.onUpdate(f.final(mediaTemporary, 601))
        val complete = requireNotNull(controller.snapshot(f.key))
        assertEquals(AsyncRelayPhase.COMPLETE, complete.phase)
        assertEquals(mapOf(mediaTemporary to 601L), complete.requests.last().finalMessageIds)
        controller.onUpdate(f.final(headerTemporary, 501))
        controller.onUpdate(f.final(mediaTemporary, 601))
        controller.start(f.observed)
        assertEquals(2, fake.requests.size)
        ledger.close(); baseline.close()
        val reopenedLedger = asyncDatabase(); val reopenedBaseline = relayDatabase()
        assertEquals(0, reopenedLedger.recoverPending(f.scope.accountId))
        val again = DeviceFakePort(f.fresh)
        val reopened = SingleRelayController(reopenedLedger, again, admission(reopenedBaseline, f)) { f.now }
        reopened.start(f.observed)
        assertEquals(complete, reopened.snapshot(f.key))
        assertTrue(again.requests.isEmpty())
        assertEquals(0, again.refreshes)
        assertEquals(0, fake.sentRealMessages + again.sentRealMessages)
        evidence.writeText(JSONObject()
            .put("schema_version", 1).put("runtime", "Android SQLite + SingleRelayController + Fake callback port")
            .put("compiled_real_output_enabled", BuildConfig.PICK_RELAY_REAL_ENABLED)
            .put("offline_rehearsal_only", BuildConfig.OFFLINE_REHEARSAL_ONLY)
            .put("sent_real_messages", fake.sentRealMessages + again.sentRealMessages)
            .put("queued_was_final", false).put("quick_ack_was_final", false)
            .put("source_original_count", complete.intent.sourceMessageIds.size)
            .put("exact_source_original", fake.requests.last().request.expectedSourceMessageIds == complete.intent.sourceMessageIds)
            .put("final_original_mapping_count", complete.requests.last().finalMessageIds.size)
            .put("fake_headers", fake.requests.count { it.request.stage == AsyncRelayStage.HEADER })
            .put("fake_original_requests", fake.requests.count { it.request.stage == AsyncRelayStage.MEDIA })
            .put("reopened_phase", reopened.snapshot(f.key)?.phase?.name)
            .put("new_requests_after_reopen", again.requests.size)
            .put("duplicate_requests", fake.requests.size - 2).toString(2) + "\n")
    }

    @Test fun disconnectedPendingPersistsUnknownAndNeitherReconnectNorLateAckResends() {
        val f = Fixture(); val baseline = relayDatabase(); val ledger = asyncDatabase()
        val fence = admission(baseline, f); val fake = DeviceFakePort(f.fresh)
        val controller = SingleRelayController(ledger, fake, fence) { f.now }
        controller.start(f.observed)
        val temporary = requireNotNull(controller.snapshot(f.key)).requests.single().temporaryMessageIds.single()
        controller.invalidate(f.scope.accountId, f.scope.sessionId, "NETWORK_CHANGED")
        fence.reset()
        assertEquals(AsyncRelayPhase.UNKNOWN, controller.snapshot(f.key)?.phase)
        ledger.close(); baseline.close()
        val reopened = asyncDatabase(); val reopenedBaseline = relayDatabase()
        val reconnected = admission(reopenedBaseline, f)
        reconnected.reset()
        reconnected.armLoaded(f.scope.accountId, "generation-after-reconnect", f.observed.sourceChatId,
            102, f.now / 1000)
        val again = DeviceFakePort(f.fresh)
        val replacement = SingleRelayController(reopened, again, reconnected) { f.now }
        replacement.start(f.observed)
        replacement.start(f.observed.copy(messageId = 102, sessionId = "generation-after-reconnect"))
        replacement.onUpdate(f.final(temporary, 501))
        assertEquals(AsyncRelayPhase.UNKNOWN, replacement.snapshot(f.key)?.phase)
        assertEquals(1, fake.requests.size)
        assertTrue(again.requests.isEmpty())
        assertEquals(0, fake.sentRealMessages + again.sentRealMessages)
    }

    @Test fun singleTextCompletesOneReferenceOnlyAfterItsFinalAck() {
        val f = Fixture(RelayContentKind.TEXT); val baseline = relayDatabase(); val ledger = asyncDatabase()
        val fake = DeviceFakePort(f.fresh)
        val controller = SingleRelayController(ledger, fake, admission(baseline, f)) { f.now }
        controller.start(f.observed)
        val pending = requireNotNull(controller.snapshot(f.key))
        assertEquals(AsyncRelayPhase.HEADER_WAITING, pending.phase)
        assertTrue(fake.requests.single().header!!.contains("https://t.me/single_fixture/101"))
        controller.onUpdate(f.final(pending.requests.single().temporaryMessageIds.single(), 501))
        assertEquals(AsyncRelayPhase.COMPLETE, controller.snapshot(f.key)?.phase)
        assertEquals(listOf(AsyncRelayStage.HEADER), fake.requests.map { it.request.stage })
        assertEquals(0, fake.sentRealMessages)
    }
}
