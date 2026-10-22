package mx.charly.canal

import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSendingStatePending
import dev.g000sha256.tdl.dto.Update
import dev.g000sha256.tdl.dto.UpdateDeleteMessages
import dev.g000sha256.tdl.dto.UpdateMessageSendAcknowledged
import dev.g000sha256.tdl.dto.UpdateMessageSendFailed
import dev.g000sha256.tdl.dto.UpdateMessageSendSucceeded

/** Pure DTO normalization for the pinned SDK. No client, subscription, output or retry here. */
object TdlAsyncAckMapper {
    fun map(accountId: Long, sessionId: String, update: Update): List<AsyncRelayUpdate> {
        if (accountId <= 0 || sessionId.isBlank()) return emptyList()
        fun scope(chatId: Long) = AsyncRelayScope(accountId, sessionId, chatId)
        val mapped = when (update) {
            is UpdateMessageSendAcknowledged -> listOf(AsyncRelayUpdate(
                scope(update.chatId), AsyncRelayUpdateKind.ACKNOWLEDGED, update.messageId))
            is UpdateMessageSendSucceeded -> listOf(AsyncRelayUpdate(
                scope(update.message.chatId), AsyncRelayUpdateKind.SUCCEEDED, update.oldMessageId,
                update.message.id, update.message.isOutgoing,
                update.message.sendingState != null || update.message.schedulingState != null))
            is UpdateMessageSendFailed -> listOf(AsyncRelayUpdate(
                scope(update.message.chatId), AsyncRelayUpdateKind.FAILED, update.oldMessageId))
            is UpdateDeleteMessages -> if (update.isPermanent && !update.fromCache) {
                update.messageIds.map { AsyncRelayUpdate(scope(update.chatId), AsyncRelayUpdateKind.FAILED, it) }
            } else emptyList()
            // UpdateNewMessage/sendingId and a cache eviction are not final acknowledgements.
            else -> emptyList()
        }
        // Native MessageId at the pinned version is positive, but otherwise opaque.
        return mapped.filter { it.scope.destinationChatId != 0L && it.oldMessageId > 0 }
    }

    /** Preserve every position and null. Never compact a partial forward response. */
    fun response(scope: AsyncRelayScope, requestId: String, messages: Array<Message?>?): AsyncRelayResponse =
        AsyncRelayResponse(scope, requestId, messages?.map { message -> message?.let {
            AsyncRelayQueuedMessage(it.id, it.chatId, it.isOutgoing,
                it.id > 0 && it.sendingState is MessageSendingStatePending && it.schedulingState == null)
        } })
}
