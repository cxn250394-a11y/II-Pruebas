package mx.charly.canal

import dev.g000sha256.tdl.dto.*
import org.junit.Assert.*
import org.junit.Test

/** Only pinned DTO constructors and local routing; no client, account or provider API. */
class ExistingClientRelayAckRouterTest {
    private val oldScope = AsyncRelayScope(7, "generation-a", -900)
    private val nextScope = oldScope.copy(sessionId = "generation-b")

    private fun command(requestId: String, scope: AsyncRelayScope = oldScope, sourceId: Long = 10) =
        SingleRelayCommand(RelayKey(scope.accountId, -801, "m:$sourceId"),
            AsyncRelayRequest(requestId, AsyncRelayStage.HEADER, emptyList(), 1_792_695_630_000),
            scope, -801, "Fixture reference")
    private fun queued(command: SingleRelayCommand, temporaryId: Long) =
        AsyncRelayResponse(command.scope, command.request.requestId,
            listOf(AsyncRelayQueuedMessage(temporaryId, command.scope.destinationChatId)))

    private fun message(id: Long, chatId: Long = -900) = Message(
        id = id, senderId = MessageSenderUser(7), receiverId = null, chatId = chatId,
        sendingState = null, schedulingState = null, isOutgoing = true, isPinned = false,
        isFromOffline = false, canBeSaved = true, hasTimestampedMedia = false, isChannelPost = false,
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
    private fun succeeded(temporaryId: Long, finalId: Long, chatId: Long = -900) =
        UpdateMessageSendSucceeded(message(finalId, chatId), temporaryId)

    @Test fun earlyFinalsRemainUnscopedUntilEachExactQueuedBinding() {
        val router = ExistingClientRelayAckRouter()
        val a = command("header-a"); val b = command("header-b", sourceId = 11)
        assertTrue(router.begin(a)); assertTrue(router.begin(b))
        assertTrue(router.observe(7, succeeded(202, 502)).updates.isEmpty())
        assertTrue(router.observe(7, succeeded(101, 501)).updates.isEmpty())
        val boundA = router.bind(queued(a, 101))
        assertFalse(boundA.uncertain)
        assertEquals(listOf(101L), boundA.updates.map { it.oldMessageId })
        assertEquals(listOf(oldScope), boundA.updates.map { it.scope })
        val boundB = router.bind(queued(b, 202))
        assertEquals(listOf(202L), boundB.updates.map { it.oldMessageId })
        assertEquals(listOf(502L), boundB.updates.map { it.finalMessageId })
    }

    @Test fun knownOldAckAfterReconnectKeepsItsOriginalScope() {
        val router = ExistingClientRelayAckRouter()
        val old = command("old"); assertTrue(router.begin(old)); router.bind(queued(old, 101))
        assertFalse(router.disconnected().uncertain)
        assertTrue(router.paused)
        assertTrue(router.resume(7))
        val next = command("next", nextScope, 11)
        assertTrue(router.begin(next)); router.bind(queued(next, 202))
        val late = router.observe(7, succeeded(101, 501)).updates.single()
        assertEquals(oldScope, late.scope)
        assertNotEquals(nextScope, late.scope)
        assertEquals(nextScope, router.observe(7, succeeded(202, 502)).updates.single().scope)
    }

    @Test fun temporaryReuseAcrossGenerationPoisonsInsteadOfRetagging() {
        val router = ExistingClientRelayAckRouter()
        val old = command("old"); assertTrue(router.begin(old)); router.bind(queued(old, 101))
        val next = command("next", nextScope, 11); assertTrue(router.begin(next))
        val collision = router.bind(queued(next, 101))
        assertTrue(collision.uncertain); assertTrue(collision.updates.isEmpty()); assertTrue(router.stopped)
        assertFalse(router.begin(command("later", nextScope, 12)))
        assertTrue(router.observe(7, succeeded(101, 501)).updates.isEmpty())
    }

    @Test fun bindingCapacityStopsButEarlyLossIsLocalWithoutEvictingIdentity() {
        val boundedBindings = ExistingClientRelayAckRouter(maxBindings = 1)
        val a = command("a"); val b = command("b", sourceId = 11)
        assertTrue(boundedBindings.begin(a)); assertFalse(boundedBindings.bind(queued(a, 101)).uncertain)
        assertTrue(boundedBindings.begin(b)); assertTrue(boundedBindings.bind(queued(b, 202)).uncertain)
        assertTrue(boundedBindings.stopped)

        val boundedEarly = ExistingClientRelayAckRouter(maxEarly = 1)
        assertTrue(boundedEarly.begin(a))
        assertFalse(boundedEarly.observe(7, succeeded(101, 501)).uncertain)
        assertFalse(boundedEarly.observe(7, succeeded(101, 501)).uncertain) // duplicate uses no capacity
        assertFalse(boundedEarly.observe(7, succeeded(202, 502)).uncertain)
        assertFalse(boundedEarly.stopped)
        val lost = boundedEarly.bind(queued(a, 101))
        assertTrue(lost.uncertain); assertTrue(lost.updates.isEmpty())
    }

    @Test fun repeatedQuickAckIsNeverAFinalOrAnEarlyFinalBufferEntry() {
        val router = ExistingClientRelayAckRouter(maxEarly = 1)
        val a = command("a"); assertTrue(router.begin(a))
        repeat(5) { assertTrue(router.observe(7, UpdateMessageSendAcknowledged(-900, 101)).updates.isEmpty()) }
        assertTrue(router.bind(queued(a, 101)).updates.isEmpty())
        assertFalse(router.stopped)
        assertTrue(router.observe(7, UpdateMessageSendAcknowledged(-900, 101)).updates.isEmpty())
        assertEquals(AsyncRelayUpdateKind.SUCCEEDED, router.observe(7, succeeded(101, 501)).updates.single().kind)
    }

    @Test fun unknownIoOrDisconnectBeforeBindingPausesWithoutAbsolvingThePendingCall() {
        val a = command("a")
        val uncertain = ExistingClientRelayAckRouter(); assertTrue(uncertain.begin(a))
        assertTrue(uncertain.uncertain("a").uncertain)
        assertFalse(uncertain.begin(command("b", sourceId = 11)))
        assertTrue(uncertain.paused); assertFalse(uncertain.stopped)
        assertTrue(uncertain.hasQuarantinedRequests); assertFalse(uncertain.canResume)
        assertFalse(uncertain.resume(7))
        val late = uncertain.bind(queued(a, 101))
        assertTrue(late.uncertain); assertTrue(late.updates.isEmpty())
        assertFalse(uncertain.hasQuarantinedRequests); assertTrue(uncertain.canResume)
        assertTrue(uncertain.paused) // Exact binding discharges quarantine, never resumes output.

        val disconnected = ExistingClientRelayAckRouter(); assertTrue(disconnected.begin(a))
        assertTrue(disconnected.disconnected().uncertain)
        assertTrue(disconnected.paused); assertFalse(disconnected.stopped)
        assertFalse(disconnected.canResume); assertFalse(disconnected.resume(7))
    }

    @Test fun unrelatedDestinationOrNoPendingRequestNeverInventsAScope() {
        val router = ExistingClientRelayAckRouter()
        assertTrue(router.observe(7, succeeded(101, 501)).updates.isEmpty())
        val a = command("a"); assertTrue(router.begin(a))
        assertTrue(router.observe(7, succeeded(101, 501, -901)).updates.isEmpty())
        assertTrue(router.bind(queued(a, 101)).updates.isEmpty())
        assertFalse(router.stopped)
    }

    @Test fun positiveAccountChangePermanentlyStopsInsteadOfRetaggingAnOldAck() {
        val first = command("account-a")
        val changed = command("account-b", oldScope.copy(accountId = 8), 11)
        val fromBegin = ExistingClientRelayAckRouter()
        assertTrue(fromBegin.begin(first)); assertFalse(fromBegin.bind(queued(first, 101)).uncertain)
        assertFalse(fromBegin.begin(changed))
        assertTrue(fromBegin.stopped)
        val retagged = fromBegin.observe(8, succeeded(101, 999))
        assertTrue(retagged.uncertain); assertTrue(retagged.updates.isEmpty())
        assertFalse(fromBegin.begin(command("account-a-returned", sourceId = 12)))

        val fromObservedAccount = ExistingClientRelayAckRouter()
        assertTrue(fromObservedAccount.begin(first))
        assertFalse(fromObservedAccount.bind(queued(first, 101)).uncertain)
        val lateOldAck = fromObservedAccount.observe(8, succeeded(101, 999))
        assertTrue(lateOldAck.uncertain); assertTrue(lateOldAck.updates.isEmpty())
        assertTrue(fromObservedAccount.stopped)
        assertTrue(fromObservedAccount.observe(7, succeeded(101, 501)).updates.isEmpty())
    }

    @Test fun orphanEarlyAckCannotBeReusedByARequestThatDidNotExistAtItsArrival() {
        val router = ExistingClientRelayAckRouter()
        val first = command("first")
        assertTrue(router.begin(first))
        assertTrue(router.observe(7, succeeded(999, 777)).updates.isEmpty())
        val firstResult = router.bind(queued(first, 101))
        assertTrue(firstResult.updates.isEmpty())
        val later = command("later", nextScope, 11)
        if (router.begin(later)) {
            val result = router.bind(queued(later, 999))
            assertTrue("An old unbound update must not confirm a later request", result.updates.isEmpty())
        } else assertTrue("A conservative STOP is acceptable", router.stopped)
    }

    @Test fun foreignFloodAtOnePendingDestinationDoesNotPoisonAnotherDestination() {
        val router = ExistingClientRelayAckRouter(maxEarly = 1)
        val noisy = command("noisy", oldScope.copy(destinationChatId = -901))
        val own = command("own")
        assertTrue(router.begin(noisy)); assertTrue(router.begin(own))
        repeat(100) { index ->
            assertFalse(router.observe(7, succeeded(1_000L + index, 5_000L + index, -901)).uncertain)
        }
        assertFalse(router.observe(7, succeeded(101, 501)).uncertain)
        val exact = router.bind(queued(own, 101))
        assertFalse(exact.uncertain)
        assertEquals(listOf(501L), exact.updates.map { it.finalMessageId })
        assertTrue(router.bind(queued(noisy, 202)).uncertain)
        assertFalse("Foreign traffic must not poison the lifetime identity router", router.stopped)
    }

    @Test fun sameDestinationLossIsLocalUnknownAndDoesNotContaminateAFutureRequest() {
        val router = ExistingClientRelayAckRouter(maxEarly = 1)
        val affected = command("affected")
        assertTrue(router.begin(affected))
        assertFalse(router.observe(7, succeeded(999, 888)).uncertain)
        assertFalse(router.observe(7, succeeded(101, 501)).uncertain)
        val lost = router.bind(queued(affected, 101))
        assertTrue(lost.uncertain); assertTrue(lost.updates.isEmpty())
        assertFalse(router.stopped)
        val future = command("future", nextScope, 11)
        assertTrue(router.begin(future))
        assertFalse(router.observe(7, succeeded(202, 502)).uncertain)
        val exact = router.bind(queued(future, 202))
        assertFalse(exact.uncertain)
        assertEquals(listOf(nextScope), exact.updates.map { it.scope })
        assertEquals(listOf(502L), exact.updates.map { it.finalMessageId })
        assertEquals(oldScope, router.observe(7, succeeded(101, 501)).updates.single().scope)
    }

    @Test fun foreignTrafficWithoutAnyMatchingDestinationNeverUsesEarlyCapacity() {
        val router = ExistingClientRelayAckRouter(maxEarly = 1)
        val own = command("own")
        assertTrue(router.begin(own))
        repeat(100) { index ->
            assertFalse(router.observe(7, succeeded(1_000L + index, 5_000L + index, -901)).uncertain)
        }
        assertFalse(router.observe(7, succeeded(101, 501)).uncertain)
        val exact = router.bind(queued(own, 101))
        assertFalse(exact.uncertain)
        assertEquals(listOf(501L), exact.updates.map { it.finalMessageId })
        assertFalse(router.stopped)
    }

    @Test fun everyQuarantinedCallMustBindBeforeAnExplicitResumeCanAdmitFreshWork() {
        val router = ExistingClientRelayAckRouter()
        val a = command("a"); val b = command("b", sourceId = 11)
        assertTrue(router.begin(a)); assertTrue(router.begin(b))
        router.observe(7, succeeded(101, 501))
        assertTrue(router.disconnected().uncertain)
        assertTrue(router.paused); assertFalse(router.canResume)
        assertFalse(router.begin(command("fresh", nextScope, 12)))
        assertFalse(router.resume(7))
        val lateA = router.bind(queued(a, 101))
        assertTrue(lateA.uncertain); assertTrue(lateA.updates.isEmpty())
        assertFalse(router.canResume); assertFalse(router.resume(7))
        val lateB = router.bind(queued(b, 202))
        assertTrue(lateB.uncertain); assertTrue(lateB.updates.isEmpty())
        assertTrue(router.canResume); assertTrue(router.paused)
        assertFalse(router.begin(command("fresh", nextScope, 12)))
        assertTrue(router.resume(7)); assertFalse(router.paused)
        val fresh = command("fresh", nextScope, 12)
        assertTrue(router.begin(fresh)); assertFalse(router.bind(queued(fresh, 303)).uncertain)
        assertEquals(oldScope, router.observe(7, succeeded(101, 501)).updates.single().scope)
        assertEquals(nextScope, router.observe(7, succeeded(303, 503)).updates.single().scope)
    }

    @Test fun reconnectAndEarlyFinalsNeverAbsolveAnUnboundQuarantinedCall() {
        val router = ExistingClientRelayAckRouter(maxEarly = 1)
        val a = command("a"); assertTrue(router.begin(a))
        router.disconnected()
        repeat(100) {
            assertTrue(router.observe(7, succeeded(101, 501)).updates.isEmpty())
            assertTrue(router.disconnected().uncertain)
            assertFalse(router.resume(7))
        }
        assertTrue(router.hasQuarantinedRequests); assertFalse(router.canResume)
        assertFalse(router.stopped)
        assertFalse(router.begin(command("would-duplicate", nextScope, 11)))
    }

    @Test fun accountChangeWhilePausedStillPermanentlyStopsTheLifetimeRouter() {
        val router = ExistingClientRelayAckRouter()
        val a = command("a"); assertTrue(router.begin(a)); router.bind(queued(a, 101))
        router.disconnected()
        assertFalse(router.resume(8)); assertTrue(router.stopped)
        assertFalse(router.resume(7)); assertFalse(router.begin(command("later", nextScope, 11)))
        assertTrue(router.observe(7, succeeded(101, 501)).updates.isEmpty())
    }

    @Test fun quarantinedBindingCannotReuseASealedTemporaryIdentity() {
        val router = ExistingClientRelayAckRouter()
        val sealed = command("sealed"); assertTrue(router.begin(sealed)); router.bind(queued(sealed, 101))
        val pending = command("pending", sourceId = 11); assertTrue(router.begin(pending))
        router.disconnected()
        val collision = router.bind(queued(pending, 101))
        assertTrue(collision.uncertain); assertTrue(collision.updates.isEmpty()); assertTrue(router.stopped)
        assertFalse(router.canResume); assertFalse(router.resume(7))
    }

    @Test fun quarantineKeepsAllThirtyTwoPendingIdentitiesInsteadOfEvictingToMakeRoom() {
        val router = ExistingClientRelayAckRouter()
        val pending = (1..32).map { command("pending-$it", sourceId = it.toLong()) }
        pending.forEach { assertTrue(router.begin(it)) }
        assertFalse(router.begin(command("overflow", sourceId = 33)))
        router.disconnected()
        pending.forEachIndexed { index, command ->
            assertFalse(router.resume(7))
            val late = router.bind(queued(command, 1_000L + index))
            assertTrue(late.uncertain); assertTrue(late.updates.isEmpty())
        }
        assertTrue(router.canResume); assertTrue(router.paused); assertFalse(router.stopped)
        assertTrue(router.resume(7))
    }

    @Test fun provenNotInvokedCancellationReleasesQuarantineWithoutImplicitResume() {
        val router = ExistingClientRelayAckRouter()
        val sealed = command("sealed")
        assertTrue(router.begin(sealed)); assertFalse(router.bind(queued(sealed, 101)).uncertain)
        val notInvoked = command("not-invoked", sourceId = 11)
        assertTrue(router.begin(notInvoked))
        router.observe(7, succeeded(999, 777))
        assertTrue(router.disconnected().uncertain)
        assertFalse(router.canResume); assertTrue(router.hasQuarantinedRequests)
        assertTrue(router.cancelBeforeInvocation(notInvoked.request.requestId))
        assertFalse(router.hasQuarantinedRequests); assertTrue(router.canResume)
        assertTrue(router.paused); assertFalse(router.stopped)
        assertFalse(router.begin(command("future", nextScope, 12)))
        assertEquals(oldScope, router.observe(7, succeeded(101, 501)).updates.single().scope)
        assertTrue(router.resume(7))
        val future = command("future", nextScope, 12)
        assertTrue(router.begin(future))
        assertTrue(router.bind(queued(future, 999)).updates.isEmpty())
    }

    @Test fun missingOrAlreadyBoundCancellationDoesNotEraseIdentityOrStopTheRouter() {
        val router = ExistingClientRelayAckRouter()
        val sealed = command("sealed")
        assertTrue(router.begin(sealed)); router.bind(queued(sealed, 101))
        assertFalse(router.cancelBeforeInvocation("missing"))
        assertFalse(router.cancelBeforeInvocation(sealed.request.requestId))
        assertFalse(router.stopped); assertFalse(router.paused)
        assertEquals(oldScope, router.observe(7, succeeded(101, 501)).updates.single().scope)
        val later = command("later", nextScope, 11)
        assertTrue(router.begin(later))
        assertTrue(router.bind(queued(later, 101)).uncertain)
        assertTrue(router.stopped) // Cancellation never weakened the lifetime ID tombstone.
    }
}
