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
    private val stillAuthorized: (AsyncRelayScope) -> Boolean,
    private val commandActive: (SingleRelayCommand) -> Boolean = { false }
) : ExistingClientRelayCalls {
    override suspend fun snapshot(observed: SingleRelayObserved, config: RelayConfig): Pair<RelayBatch, RelayPermissions>? {
        RelayRealGate.requireOpen(config.userEnabled)
        val scope = AsyncRelayScope(observed.accountId, observed.sessionId, config.destinationChatId ?: 0L)
        if (!stillAuthorized(scope) || observed.albumId != 0L) return null
        return adapter.snapshotSingle(observed, config)
    }

    override suspend fun queue(command: SingleRelayCommand): AsyncRelayResponse? =
        (queueResult(command) as? ExistingClientRelayQueueResult.Queued)?.response

    override suspend fun queueResult(command: SingleRelayCommand): ExistingClientRelayQueueResult {
        // No native call can be made by this candidate, even through a direct invocation.
        RelayRealGate.requireOpen(true)
        if (!stillAuthorized(command.scope) || !commandActive(command)) return ExistingClientRelayQueueResult.NotInvoked
        val request = command.request
        return when (request.stage) {
            AsyncRelayStage.HEADER -> {
                val header = command.header?.takeIf { it.isNotBlank() } ?: return ExistingClientRelayQueueResult.NotInvoked
                if (!commandActive(command)) return ExistingClientRelayQueueResult.NotInvoked
                val response = client.sendMessage(chatId = command.scope.destinationChatId,
                    topicId = null, replyTo = null, options = options(), inputMessageContent = headerContent(header))
                val mapped = if (response is TdlResult.Success)
                    TdlAsyncAckMapper.response(command.scope, request.requestId, arrayOf(response.result)) else null
                mapped?.let { ExistingClientRelayQueueResult.Queued(it) } ?: ExistingClientRelayQueueResult.Uncertain
            }
            AsyncRelayStage.MEDIA -> {
                val headerPresent = try { headerExists(command) } catch (_: Exception) { false }
                if (request.expectedSourceMessageIds.size != 1 || command.header != null || !headerPresent ||
                    !stillAuthorized(command.scope) || !commandActive(command)) return ExistingClientRelayQueueResult.NotInvoked
                // Preserve the original sender/caption and attach the single original to its
                // confirmed attribution header. This is not a Telegram transaction: deletion
                // after the final read remains an explicitly documented live-operation gate.
                val response = client.sendMessage(chatId = command.scope.destinationChatId,
                    topicId = null, replyTo = headerReply(requireNotNull(command.confirmedHeaderMessageId)),
                    options = options(), inputMessageContent = originalContent(command.sourceChatId,
                        request.expectedSourceMessageIds.single()))
                val mapped = if (response is TdlResult.Success)
                    TdlAsyncAckMapper.response(command.scope, request.requestId, arrayOf(response.result)) else null
                mapped?.let { ExistingClientRelayQueueResult.Queued(it) } ?: ExistingClientRelayQueueResult.Uncertain
            }
        }
    }

    override suspend fun headerExists(command: SingleRelayCommand): Boolean {
        RelayRealGate.requireOpen(true)
        if (!stillAuthorized(command.scope) || !commandActive(command) || command.request.stage != AsyncRelayStage.MEDIA ||
            (command.confirmedHeaderMessageId ?: 0L) <= 0L || command.expectedHeaderText.isNullOrBlank()) return false
        val result = client.getMessage(command.scope.destinationChatId, command.confirmedHeaderMessageId!!)
        if (result !is TdlResult.Success || !stillAuthorized(command.scope) || !commandActive(command)) return false
        val message = result.result
        if (message.id != command.confirmedHeaderMessageId || message.chatId != command.scope.destinationChatId ||
            !message.isOutgoing || message.sendingState != null ||
            (message.content as? MessageText)?.text?.text != command.expectedHeaderText) return false
        val properties = client.getMessageProperties(message.chatId, message.id)
        return properties is TdlResult.Success && properties.result.canBeReplied && stillAuthorized(command.scope) && commandActive(command)
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

        internal fun headerReply(finalHeaderId: Long): InputMessageReplyToMessage {
            require(finalHeaderId > 0L)
            return InputMessageReplyToMessage(finalHeaderId, quote = null, checklistTaskId = 0, pollOptionId = "")
        }

        internal fun originalContent(sourceChatId: Long, messageId: Long): InputMessageForwarded {
            require(sourceChatId != 0L && messageId > 0L)
            return InputMessageForwarded(sourceChatId, messageId, inGameShare = false,
                replaceVideoStartTimestamp = false, newVideoStartTimestamp = 0, copyOptions = null)
        }
    }
}
