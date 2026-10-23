package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

/** Author QA of the integrated callback port. No SDK, client, account or network is created. */
class SingleRelayIntegrationRegressionTest {
    private class Fixture(
        kind: RelayContentKind = RelayContentKind.PHOTO,
        caption: String = "Pick: Over 2.5 goles @1.80",
        policy: RelaySourcePolicy = RelaySourcePolicy.FANO_DIRECT,
        photoConsent: Boolean = false,
        mode: SingleRelayPortMode = SingleRelayPortMode.FAKE
    ) {
        var now = 1_792_695_600_000L
        val account = 701L
        val source = -100701L
        val destination = -100702L
        val scope = AsyncRelayScope(account, "qa-generation-1", destination)
        val observed = SingleRelayObserved(account, scope.sessionId, source, 101, now / 1000 - 1)
        val message = RelayMessage(account, source, 101, observed.originalUnix, caption, kind,
            canBeForwarded = true, permalink = "https://t.me/qa_source/101")
        val config = RelayConfig(account, destination, true,
            listOf(RelaySourceBinding(account, source, "Fano QA", policy, true)))
        val permission = RelayPermissions(account, source, destination, true, true,
            messageForwardable = mapOf(101L to true), messageProtected = mapOf(101L to false))
        val baseline = MemoryRelayLedger().apply { checkpoint(account, source, 100, this@Fixture.observed.originalUnix - 1) }
        val ledger = FakeAsyncRelayLedger()
        val port = ControlledPort(SingleRelayFreshState(
            AsyncRelayFreshState(scope, RelayBatch(listOf(message), synthetic = true), config, permission),
            connected = true, photoOnlyConsent = photoConsent), mode)
        val admission = SingleRelayAdmission {
            baseline.acceptObserved(it.key, listOf(it.messageId), it.originalUnix)
        }
        val controller = SingleRelayController(ledger, port, admission) { now }
        fun snapshot() = requireNotNull(controller.snapshot(observed.key))
        fun succeed(temporary: Long, final: Long, actualScope: AsyncRelayScope = scope) {
            controller.onUpdate(AsyncRelayUpdate(actualScope, AsyncRelayUpdateKind.SUCCEEDED,
                temporary, final, finalIsOutgoing = true, finalHasSendingState = false))
        }
        fun headerSuccess() = succeed(snapshot().requests.first().temporaryMessageIds.single(), 501)
        fun mediaSuccess() = succeed(snapshot().requests.last().temporaryMessageIds.single(), 601)
    }

    private class ControlledPort(
        var fresh: SingleRelayFreshState?,
        override val mode: SingleRelayPortMode = SingleRelayPortMode.FAKE
    ) : SingleRelayPort {
        var refreshes = 0
        var deferRefresh = false
        var reject: AsyncRelayStage? = null
        val deferredRefreshes = mutableListOf<(SingleRelayFreshState?) -> Unit>()
        val commands = mutableListOf<SingleRelayCommand>()
        private var nextTemporary = 100_000L
        override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) {
            refreshes++
            if (deferRefresh) deferredRefreshes += callback else callback(fresh)
        }
        override fun checkHeaderExists(command: SingleRelayCommand, callback: (Boolean) -> Unit) {
            assertEquals(AsyncRelayStage.MEDIA, command.request.stage)
            assertTrue(requireNotNull(command.confirmedHeaderMessageId) > 0L)
            assertFalse(command.expectedHeaderText.isNullOrBlank())
            callback(true) // Explicit Fake destination state; no provider is queried.
        }
        override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
            commands += command
            if (reject == command.request.stage) callback(SingleRelayDispatchResult.Rejected)
            else callback(SingleRelayDispatchResult.Queued(AsyncRelayResponse(command.scope, command.request.requestId,
                List(command.request.expectedCount) { AsyncRelayQueuedMessage(nextTemporary++, command.scope.destinationChatId) })))
        }
    }

    @Test fun textIsAReferenceWithCanonicalLinkAndFinalAckNotAContentCopy() {
        val f = Fixture(kind = RelayContentKind.TEXT)
        f.controller.start(f.observed)
        assertEquals(AsyncRelayPhase.HEADER_WAITING, f.snapshot().phase)
        assertEquals(1, f.port.commands.size)
        assertTrue(f.port.commands.single().header!!.contains("https://t.me/qa_source/101"))
        assertTrue(f.port.commands.single().header!!.contains("Hora original:"))
        assertFalse(f.port.commands.single().header!!.contains(f.message.text))
        f.headerSuccess()
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
        assertEquals(listOf(AsyncRelayStage.HEADER), f.port.commands.map { it.request.stage })
    }

    @Test fun textWithoutUsableReferenceIsHeldBeforeAnyDispatch() {
        val f = Fixture(kind = RelayContentKind.TEXT)
        val original = f.port.fresh!!
        f.port.fresh = original.copy(state = original.state.copy(
            batch = original.state.batch.copy(messages = listOf(f.message.copy(permalink = null)))))
        f.controller.start(f.observed)
        assertTrue(f.port.commands.isEmpty())
        assertNull(f.controller.snapshot(f.observed.key))
    }

    @Test fun unicoreyCaptionKeepsVerifiedAttributionAndExactOriginalIdAcrossBothFinals() {
        val f = Fixture(caption = "Dr MLS\nPick: Over 2.5 goles @1.80", policy = RelaySourcePolicy.UNICOREY_MEX)
        f.controller.start(f.observed)
        assertTrue(f.port.commands.single().header!!.startsWith("Tipster: Dr MLS"))
        f.headerSuccess()
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, f.snapshot().phase)
        assertEquals(f.source, f.port.commands.last().sourceChatId)
        assertEquals(listOf(101L), f.port.commands.last().request.expectedSourceMessageIds)
        f.mediaSuccess()
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
        assertEquals(2, f.port.commands.size)
        assertEquals(listOf(101L), f.snapshot().intent.sourceMessageIds)
    }

    @Test fun fanoPhotoOnlyRequiresSeparateConsentAndNeverClaimsImageSemantics() {
        val denied = Fixture(caption = "")
        denied.controller.start(denied.observed)
        assertTrue(denied.port.commands.isEmpty())
        val allowed = Fixture(caption = "", photoConsent = true)
        allowed.controller.start(allowed.observed)
        val decision = requireNotNull(allowed.controller.decision(allowed.observed.key))
        assertTrue(decision.semantics.contains("UNKNOWN"))
        assertTrue(allowed.port.commands.single().header!!.contains("no verificado", ignoreCase = true))
        allowed.headerSuccess(); allowed.mediaSuccess()
        assertEquals(AsyncRelayPhase.COMPLETE, allowed.snapshot().phase)
    }

    @Test fun unicoreyPhotoOnlyCannotInventAnAuthorizedTipsterEvenWithPhotoConsent() {
        val f = Fixture(caption = "", policy = RelaySourcePolicy.UNICOREY_MEX, photoConsent = true)
        f.controller.start(f.observed)
        assertTrue(f.port.commands.isEmpty())
        assertNull(f.controller.snapshot(f.observed.key))
    }

    @Test fun nativeAlbumMetadataIsHeldBeforeAdmissionOrProviderQuery() {
        val f = Fixture()
        f.controller.start(f.observed.copy(albumId = 7001))
        assertEquals(0, f.port.refreshes)
        assertTrue(f.port.commands.isEmpty())
        assertTrue(f.baseline.observed.isEmpty())
    }

    @Test fun compiledClosedExistingClientPortCannotEvenRefresh() {
        val f = Fixture(mode = SingleRelayPortMode.EXISTING_CLIENT)
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        f.controller.start(f.observed)
        assertEquals(0, f.port.refreshes)
        assertTrue(f.port.commands.isEmpty())
        assertNull(f.controller.snapshot(f.observed.key))
    }

    @Test fun queuedAndQuickAckDoNotAuthorizeOriginalOrCompleteAnything() {
        val f = Fixture(); f.controller.start(f.observed)
        val pending = f.snapshot()
        val temporary = pending.requests.single().temporaryMessageIds.single()
        f.controller.onUpdate(AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.ACKNOWLEDGED, temporary))
        assertEquals(pending, f.snapshot())
        assertEquals(1, f.port.commands.size)
    }

    @Test fun rejectedHeaderIsStickyUnknownAcrossReplayAndLateSuccess() {
        val f = Fixture(); f.port.reject = AsyncRelayStage.HEADER
        f.controller.start(f.observed)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        f.controller.start(f.observed)
        f.succeed(100_000, 501)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals(1, f.port.commands.size)
    }

    @Test fun rejectedOriginalDoesNotRetryHeaderOrOriginal() {
        val f = Fixture(); f.port.reject = AsyncRelayStage.MEDIA
        f.controller.start(f.observed); f.headerSuccess()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        f.controller.start(f.observed)
        f.succeed(100_001, 601)
        assertEquals(2, f.port.commands.size)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
    }

    @Test fun permissionRevokedBetweenHeaderAndMediaStopsWithoutOriginal() {
        val f = Fixture(); f.controller.start(f.observed)
        val original = f.port.fresh!!
        f.port.fresh = original.copy(state = original.state.copy(
            permissions = original.state.permissions.copy(canWriteDestination = false)))
        f.headerSuccess()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals(1, f.port.commands.size)
    }

    @Test fun disconnectTerminatesPendingAndReconnectDoesNotDrainBacklogOrReplay() {
        val f = Fixture(); f.controller.start(f.observed)
        val pendingTemporary = f.snapshot().requests.single().temporaryMessageIds.single()
        f.controller.invalidate(f.account, f.scope.sessionId, "NETWORK_CHANGED")
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        f.baseline.checkpoint(f.account, f.source, 102, f.now / 1000)
        f.controller.start(f.observed.copy(messageId = 102, sessionId = "qa-generation-2"))
        f.controller.start(f.observed)
        f.succeed(pendingTemporary, 501)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.snapshot().phase)
        assertEquals(1, f.port.commands.size)
    }

    @Test fun lateRefreshFromAnInvalidatedGenerationCannotCreateAnIntent() {
        val f = Fixture(); f.port.deferRefresh = true
        f.controller.start(f.observed)
        assertTrue(f.port.deferredRefreshes.isNotEmpty())
        f.controller.invalidate(f.account, f.scope.sessionId, "SESSION_CHANGED")
        f.port.deferredRefreshes.toList().forEach { it(f.port.fresh) }
        assertTrue(f.port.commands.isEmpty())
        assertNull(f.controller.snapshot(f.observed.key))
    }

    @Test fun crossedScopeAckCannotAdvanceAndIdenticalFinalsAreIdempotent() {
        val f = Fixture(); f.controller.start(f.observed)
        val temp = f.snapshot().requests.single().temporaryMessageIds.single()
        val old = f.snapshot()
        f.succeed(temp, 501, f.scope.copy(sessionId = "other"))
        f.succeed(temp, 501, f.scope.copy(accountId = f.account + 1))
        f.succeed(temp, 501, f.scope.copy(destinationChatId = -100999))
        assertEquals(old, f.snapshot())
        assertEquals(1, f.port.commands.size)
        f.headerSuccess(); f.headerSuccess()
        assertEquals(2, f.port.commands.size)
        f.mediaSuccess(); f.mediaSuccess()
        f.controller.start(f.observed)
        assertEquals(AsyncRelayPhase.COMPLETE, f.snapshot().phase)
        assertEquals(2, f.port.commands.size)
    }

    @Test fun pendingAfterControllerReplacementIsRecoveredUnknownWithoutDispatch() {
        val f = Fixture(); f.controller.start(f.observed)
        val temporary = f.snapshot().requests.single().temporaryMessageIds.single()
        assertEquals(1, f.ledger.recoverPending(f.account))
        val replacement = SingleRelayController(f.ledger, f.port, f.admission) { f.now }
        replacement.start(f.observed)
        replacement.onUpdate(AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.SUCCEEDED,
            temporary, 501, finalIsOutgoing = true, finalHasSendingState = false))
        assertEquals(AsyncRelayPhase.UNKNOWN, replacement.snapshot(f.observed.key)!!.phase)
        assertEquals(1, f.port.commands.size)
    }
}
