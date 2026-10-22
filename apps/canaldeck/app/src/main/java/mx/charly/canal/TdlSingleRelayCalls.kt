package mx.charly.canal

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*

/**
 * Dormant official operations on the exact client supplied by TelegramSession.
 * The port and these methods independently enforce the compiled OFF fuse.
 */
internal class TdlSingleRelayCalls(
    private val client: TdlClient,
    private val adapter: TdlRelayAdapter,
    private val stillAuthorized: (AsyncRelayScope) -> Boolean
) : ExistingClientRelayCalls {
    override suspend fun snapshot(observed: SingleRelayObserved, config: RelayConfig): Pair<RelayBatch, RelayPermissions>? {
        RelayRealGate.requireOpen(config.userEnabled)
        val scope = AsyncRelayScope(observed.accountId, observed.sessionId, config.destinationChatId ?: 0L)
        if (!stillAuthorized(scope) || observed.albumId != 0L) return null
        return adapter.snapshotSingle(observed, config)
    }

    override suspend fun queue(command: SingleRelayCommand): AsyncRelayResponse? {
        // No native call can be made by this candidate, even through a direct invocation.
        RelayRealGate.requireOpen(true)
        if (!stillAuthorized(command.scope)) return null
        val request = command.request
        return when (request.stage) {
            AsyncRelayStage.HEADER -> {
                val header = command.header?.takeIf { it.isNotBlank() } ?: return null
                val response = client.sendMessage(chatId = command.scope.destinationChatId,
                    topicId = null, replyTo = null, options = options(), inputMessageContent = headerContent(header))
                if (response is TdlResult.Success)
                    TdlAsyncAckMapper.response(command.scope, request.requestId, arrayOf(response.result)) else null
            }
            AsyncRelayStage.MEDIA -> {
                if (request.expectedSourceMessageIds.size != 1 || command.header != null) return null
                val response = client.forwardMessages(chatId = command.scope.destinationChatId,
                    topicId = null, fromChatId = command.sourceChatId,
                    messageIds = request.expectedSourceMessageIds.toLongArray(), options = options(),
                    sendCopy = false, removeCaption = false)
                if (response is TdlResult.Success)
                    TdlAsyncAckMapper.response(command.scope, request.requestId, response.result.messages) else null
            }
        }
    }

    companion object {
        /** No scheduled, paid, copied or preview-generated media. */
        internal fun options() = MessageSendOptions(suggestedPostInfo = null,
            disableNotification = true, fromBackground = true, protectContent = false,
            allowPaidBroadcast = false, paidMessageStarCount = 0, updateOrderOfInstalledStickerSets = false,
            schedulingState = null, effectId = 0, sendingId = 0, onlyPreview = false)

        internal fun headerContent(header: String) = InputMessageText(
            FormattedText(header, emptyArray()),
            LinkPreviewOptions(isDisabled = true, url = "", forceSmallMedia = false,
                forceLargeMedia = false, showAboveText = false), clearDraft = false)
    }
}
