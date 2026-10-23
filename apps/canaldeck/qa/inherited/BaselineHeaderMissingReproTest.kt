package mx.charly.canal

import org.junit.Assert.assertEquals
import org.junit.Test

/** Same test compiles against unchanged #11 models/controller and the corrected versions. */
class BaselineHeaderMissingReproTest {
    private interface HeaderProbe {
        fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit)
    }
    @Test fun aMissingFinalHeaderMustNotBeFollowedByAnOriginalPhoto() {
        val now = 1_792_695_600_000L
        val scope = AsyncRelayScope(8101, "baseline-header-generation", -8103)
        val observed = SingleRelayObserved(scope.accountId, scope.sessionId, -8102, 201, now / 1000 - 1)
        val message = RelayMessage(scope.accountId, observed.sourceChatId, observed.messageId, observed.originalUnix,
            "Pick: Over 1.5 @1.70", RelayContentKind.PHOTO, canBeForwarded = true,
            permalink = "https://t.me/header_fixture/201")
        val config = RelayConfig(scope.accountId, scope.destinationChatId, true,
            listOf(RelaySourceBinding(scope.accountId, observed.sourceChatId, "Fano fixture", RelaySourcePolicy.FANO_DIRECT, true)))
        val permission = RelayPermissions(scope.accountId, observed.sourceChatId, scope.destinationChatId, true, true,
            messageForwardable = mapOf(201L to true), messageProtected = mapOf(201L to false))
        val fresh = SingleRelayFreshState(AsyncRelayFreshState(scope, RelayBatch(listOf(message), synthetic = true), config, permission), true)
        val commands = mutableListOf<SingleRelayCommand>()
        var temporary = 1000L
        val port = object : SingleRelayPort, HeaderProbe {
            override val mode = SingleRelayPortMode.FAKE
            override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) = callback(fresh)
            // The destination final header is absent. This is a synthetic provider fact.
            override fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit) = callback(false)
            override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
                commands += command
                callback(SingleRelayDispatchResult.Queued(AsyncRelayResponse(scope, command.request.requestId,
                    listOf(AsyncRelayQueuedMessage(temporary++, scope.destinationChatId)))))
            }
        }
        val controller = SingleRelayController(FakeAsyncRelayLedger(), port, SingleRelayAdmission { true }) { now }
        controller.start(observed)
        controller.onUpdate(AsyncRelayUpdate(scope, AsyncRelayUpdateKind.SUCCEEDED, 1000, 2001,
            finalIsOutgoing = true, finalHasSendingState = false))
        assertEquals("Missing final header must leave only the header request; no orphan photo", 1, commands.size)
    }
}
