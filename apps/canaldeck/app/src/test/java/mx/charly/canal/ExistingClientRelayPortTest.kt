package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

/** Uses the production gateway class, but never constructs a TdlClient or account. */
class ExistingClientRelayPortTest {
    private class RecordingCalls : ExistingClientRelayCalls {
        var reads = 0; var headerReads = 0; var outputs = 0
        override suspend fun snapshot(observed: SingleRelayObserved, config: RelayConfig): Pair<RelayBatch, RelayPermissions>? {
            reads++; error("The compiled OFF port must not query a provider")
        }
        override suspend fun queue(command: SingleRelayCommand): AsyncRelayResponse? {
            outputs++; error("The compiled OFF port must not submit an output")
        }
        override suspend fun headerExists(command: SingleRelayCommand): Boolean {
            headerReads++; error("The compiled OFF port must not read a destination header")
        }
    }
    private val observed = SingleRelayObserved(7, "test-generation", -801, 101, 1_792_695_600)
    private val scope = AsyncRelayScope(7, "test-generation", -802)

    @Test fun compiledOffRefreshRejectsBeforeContextLaunchOrAnyProviderRead() {
        val calls = RecordingCalls(); var contexts = 0; var launches = 0; var callbacks = 0
        val port = ExistingClientRelayPort(calls, { contexts++; error("No config access required while compiled OFF") },
            { launches++; error("No coroutine should launch") })
        assertEquals(SingleRelayPortMode.EXISTING_CLIENT, port.mode)
        port.refresh(observed) { callbacks++; assertNull(it) }
        assertEquals(1, callbacks); assertEquals(0, calls.reads); assertEquals(0, calls.outputs)
        assertEquals(0, contexts); assertEquals(0, launches); assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
    }

    @Test fun compiledOffHeaderAndMediaRejectExactlyOnceWithoutNativeOrFakeOutputs() {
        val calls = RecordingCalls(); var launches = 0; var callbacks = 0
        val port = ExistingClientRelayPort(calls, { error("Compiled OFF") }, { launches++; error("Compiled OFF") })
        listOf(AsyncRelayStage.HEADER, AsyncRelayStage.MEDIA).forEach { stage ->
            val request = AsyncRelayRequest("request-${stage.name}", stage,
                if (stage == AsyncRelayStage.MEDIA) listOf(101L) else emptyList(), 1_792_695_630_000)
            port.dispatch(SingleRelayCommand(observed.key, request, scope, -801,
                if (stage == AsyncRelayStage.HEADER) "Fixture source reference" else null)) {
                callbacks++; assertEquals(SingleRelayDispatchResult.Rejected, it)
            }
        }
        assertEquals(2, callbacks); assertEquals(0, launches)
        assertEquals(0, calls.reads); assertEquals(0, calls.outputs)
    }

    @Test fun nativeDtoBuildersDisablePreviewSchedulingAndPaymentWithoutCallingAnApi() {
        val options = TdlSingleRelayCalls.options()
        assertFalse(options.allowPaidBroadcast); assertEquals(0L, options.paidMessageStarCount)
        assertNull(options.schedulingState); assertEquals(0, options.sendingId)
        val header = TdlSingleRelayCalls.headerContent("Fixture reference")
        assertEquals("Fixture reference", header.text.text)
        assertTrue(header.linkPreviewOptions!!.isDisabled); assertEquals("", header.linkPreviewOptions!!.url)
        assertFalse(header.clearDraft)
    }

    @Test fun compiledOffHeaderReadRejectsBeforeContextLaunchOrProviderRead() {
        val calls = RecordingCalls(); var contexts = 0; var launches = 0; var callbacks = 0
        val port = ExistingClientRelayPort(calls, { contexts++; error("Compiled OFF") },
            { launches++; error("Compiled OFF") })
        val command = SingleRelayCommand(observed.key,
            AsyncRelayRequest("media-header-read", AsyncRelayStage.MEDIA, listOf(observed.messageId), 1_792_695_630_000),
            scope, observed.sourceChatId, confirmedHeaderMessageId = 501, expectedHeaderText = "Fixture attribution")
        port.checkHeaderExists(command) { callbacks++; assertFalse(it) }
        assertEquals(1, callbacks); assertEquals(0, contexts); assertEquals(0, launches)
        assertEquals(0, calls.reads); assertEquals(0, calls.headerReads); assertEquals(0, calls.outputs)
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
    }

    @Test fun forwardedOriginalBuilderRetainsExactSourceAndHasNoCopyOrCaptionOverride() {
        val original = TdlSingleRelayCalls.originalContent(observed.sourceChatId, observed.messageId)
        assertEquals(observed.sourceChatId, original.fromChatId)
        assertEquals(observed.messageId, original.messageId)
        assertNull(original.copyOptions) // Forward as usual; no copied bytes or rewritten caption.
        assertFalse(original.inGameShare); assertFalse(original.replaceVideoStartTimestamp)
        assertEquals(0, original.newVideoStartTimestamp)
    }

    @Test fun mediaReplyBuilderTargetsOnlyTheConfirmedAttributionHeader() {
        val reply = TdlSingleRelayCalls.headerReply(501)
        assertEquals(501L, reply.messageId)
        assertNull(reply.quote); assertEquals(0, reply.checklistTaskId); assertEquals("", reply.pollOptionId)
    }

    @Test fun originalAndReplyBuildersRejectMissingSourceOrNonpositiveMessageIds() {
        assertThrows(IllegalArgumentException::class.java) { TdlSingleRelayCalls.originalContent(0, 101) }
        assertThrows(IllegalArgumentException::class.java) { TdlSingleRelayCalls.originalContent(-801, 0) }
        assertThrows(IllegalArgumentException::class.java) { TdlSingleRelayCalls.originalContent(-801, -101) }
        assertThrows(IllegalArgumentException::class.java) { TdlSingleRelayCalls.headerReply(0) }
        assertThrows(IllegalArgumentException::class.java) { TdlSingleRelayCalls.headerReply(-501) }
    }

    @Test fun rejectedConsumerExceptionDoesNotTriggerAnotherCallbackOrRetry() {
        val calls = RecordingCalls(); var callbacks = 0
        val port = ExistingClientRelayPort(calls, { error("Compiled OFF") }, { error("Compiled OFF") })
        val command = SingleRelayCommand(observed.key,
            AsyncRelayRequest("header-once", AsyncRelayStage.HEADER, emptyList(), 1_792_695_630_000),
            scope, -801, "Fixture reference")
        assertThrows(IllegalStateException::class.java) {
            port.dispatch(command) { callbacks++; error("Consumer deliberately stopped") }
        }
        assertEquals(1, callbacks); assertEquals(0, calls.outputs); assertEquals(0, calls.reads)
    }
}
