package mx.charly.canal

import dev.g000sha256.tdl.dto.*
import org.junit.Assert.*
import org.junit.Test

class TdlAsyncAckMapperTest {
    private val scope = AsyncRelayScope(7, "generation-a", -900)
    private fun message(id: Long = 101, outgoing: Boolean = true,
                        pending: Boolean = false, chat: Long = -900): Message = Message(
        id = id, senderId = MessageSenderUser(7), receiverId = null, chatId = chat,
        sendingState = if (pending) MessageSendingStatePending(123) else null,
        schedulingState = null, isOutgoing = outgoing, isPinned = false, isFromOffline = false,
        canBeSaved = true, hasTimestampedMedia = false, isChannelPost = false,
        isPaidStarSuggestedPost = false, isPaidGramSuggestedPost = false,
        containsUnreadMention = false, containsUnreadPollVotes = false,
        date = 1792695600, editDate = 0, forwardInfo = null, importInfo = null,
        interactionInfo = null, unreadReactions = emptyArray(), factCheck = null,
        suggestedPostInfo = null, replyTo = null, topicId = null, selfDestructType = null,
        selfDestructIn = 0.0, autoDeleteIn = 0.0, viaBotUserId = 0, guestBotCallerId = null,
        senderBusinessBotUserId = 0, senderBoostCount = 0, senderTag = "", paidMessageStarCount = 0,
        authorSignature = "", mediaAlbumId = 0, effectId = 0, restrictionInfo = null,
        summaryLanguageCode = "", content = MessageText(FormattedText("fixture", emptyArray()), null, null),
        replyMarkup = null, ephemeralMessageId = 0)
    private fun mapped(update: Update) = TdlAsyncAckMapper.map(scope.accountId, scope.sessionId, update)

    @Test fun quickAckIsNeverSuccessAndCanRepeat() {
        val update = UpdateMessageSendAcknowledged(-900, 23)
        assertEquals(AsyncRelayUpdateKind.ACKNOWLEDGED, mapped(update).single().kind)
        assertEquals(mapped(update), mapped(update))
    }
    @Test fun successUsesOldDestinationIdAndFinalDto() {
        val event = mapped(UpdateMessageSendSucceeded(message(), 23)).single()
        assertEquals(scope, event.scope); assertEquals(23L, event.oldMessageId)
        assertEquals(101L, event.finalMessageId); assertTrue(event.finalIsOutgoing)
        assertFalse(event.finalHasSendingState)
    }
    @Test fun finalStillPendingAndIncomingAreNotMadeValidByMapper() {
        assertTrue(mapped(UpdateMessageSendSucceeded(message(pending = true), 23)).single().finalHasSendingState)
        assertFalse(mapped(UpdateMessageSendSucceeded(message(outgoing = false), 23)).single().finalIsOutgoing)
    }
    @Test fun failureAndPermanentDeletionAreConservativeSignals() {
        assertEquals(AsyncRelayUpdateKind.FAILED,
            mapped(UpdateMessageSendFailed(message(), 23, dev.g000sha256.tdl.dto.Error(400, "fixture"))).single().kind)
        assertEquals(listOf(23L, 24L), mapped(UpdateDeleteMessages(-900, longArrayOf(23, 24), true, false)).map { it.oldMessageId })
        assertTrue(mapped(UpdateDeleteMessages(-900, longArrayOf(23), true, true)).isEmpty())
        assertTrue(mapped(UpdateDeleteMessages(-900, longArrayOf(23), false, false)).isEmpty())
    }
    @Test fun sendingIdAndNewMessageDoNotProveCompletion() {
        assertTrue(mapped(UpdateNewMessage(message(pending = true))).isEmpty())
    }
    @Test fun scopeAndPositiveNativeTemporaryIdsAreRequired() {
        assertTrue(TdlAsyncAckMapper.map(0, "s", UpdateMessageSendAcknowledged(-900, 23)).isEmpty())
        assertTrue(TdlAsyncAckMapper.map(7, "", UpdateMessageSendAcknowledged(-900, 23)).isEmpty())
        assertTrue(mapped(UpdateMessageSendAcknowledged(0, 23)).isEmpty())
        assertTrue(mapped(UpdateMessageSendAcknowledged(-900, -23)).isEmpty())
        assertEquals("other-session", TdlAsyncAckMapper.map(7, "other-session", UpdateMessageSendAcknowledged(-900, 23)).single().scope.sessionId)
    }
    @Test fun responsePreservesNullsOrderAndScopeForExactBinding() {
        val response = TdlAsyncAckMapper.response(scope, "request", arrayOf(message(201, pending = true), null, message(203, pending = true)))
        assertEquals(listOf(201L, null, 203L), response.messages!!.map { it?.temporaryMessageId })
        assertTrue(response.messages!![0]!!.hasSendingState)
        assertNull(TdlAsyncAckMapper.response(scope, "request", null).messages)
    }
    @Test fun synchronousNonPendingResponseDoesNotFabricateFinalAck() {
        val response = TdlAsyncAckMapper.response(scope, "request", arrayOf(message()))
        assertFalse(response.messages!!.single()!!.hasSendingState)
        assertEquals(-901L, TdlAsyncAckMapper.response(scope, "request", arrayOf(message(chat = -901))).messages!!.single()!!.destinationChatId)
    }
}
