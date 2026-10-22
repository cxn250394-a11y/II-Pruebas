package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class AsyncRelayEngineTest {
    private class Fixture(album: Boolean = false, policy: RelaySourcePolicy = RelaySourcePolicy.FANO_DIRECT, text: String = "Pick: Over 8.5 @1.80") {
        var now = 1_792_695_600_000L
        val scope = AsyncRelayScope(7001, "offline-session-1", -9001)
        val key = RelayKey(7001, -8001, if (album) "a:501" else "m:101")
        val messages = (if (album) listOf(101L, 102L) else listOf(101L)).map {
            RelayMessage(7001, -8001, it, now / 1000 - 1, text, RelayContentKind.PHOTO,
                if (album) 501 else 0, canBeForwarded = true, permalink = "https://t.me/synthetic_source/$it")
        }
        val config = RelayConfig(7001, -9001, true,
            listOf(RelaySourceBinding(7001, -8001, "Synthetic source", policy, true)))
        val permissions = RelayPermissions(7001, -8001, -9001, true, true, false,
            messages.associate { it.messageId to true }, messages.associate { it.messageId to false })
        val state = AsyncRelayFreshState(scope, RelayBatch(messages, synthetic = true), config, permissions)
        val transport = FakeAsyncRelayTransport(state)
        val ledger = FakeAsyncRelayLedger()
        val engine = AsyncRelayEngine(ledger, transport) { now }
        val proof = if (album) AlbumCompletenessEvidence.FakeManifest(7001, -8001, 501, messages.map { it.messageId }) else AlbumCompletenessEvidence.ProviderUnknown
        fun intent(media: Boolean = true) = AsyncRelayIntent(key, scope, messages.map { it.messageId }, media, now)
        fun start(media: Boolean = true) = engine.start(intent(media), proof)!!
        fun success(temp: Long, final: Long = temp + 1_000_000, s: AsyncRelayScope = scope) =
            AsyncRelayUpdate(s, AsyncRelayUpdateKind.SUCCEEDED, temp, final, true, false)
        fun completeHeader(): AsyncRelaySnapshot {
            val temp = ledger.read(key)!!.requests.first().temporaryMessageIds.single()
            return engine.onUpdate(key, success(temp))!!
        }
        fun completeMedia(): AsyncRelaySnapshot {
            val ids = ledger.read(key)!!.requests.last().temporaryMessageIds
            ids.reversed().forEach { engine.onUpdate(key, success(it)) }
            return ledger.read(key)!!
        }
    }

    @Test fun queuedHeaderAndQuickAckNeverForward() {
        val f = Fixture(); val s = f.start()
        assertEquals(AsyncRelayPhase.HEADER_WAITING, s.phase)
        f.engine.onUpdate(f.key, AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.ACKNOWLEDGED, s.requests.single().temporaryMessageIds.single()))
        assertEquals(1, f.transport.outputs.size)
        assertTrue(f.ledger.read(f.key)!!.requests.single().finalMessageIds.isEmpty())
    }

    @Test fun headerFinalThenExactOutOfOrderAlbumFinalsComplete() {
        val f = Fixture(album = true); f.start()
        val media = f.completeHeader()
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, media.phase)
        assertEquals(listOf(101L, 102L), media.requests.last().expectedSourceMessageIds)
        val ids = media.requests.last().temporaryMessageIds
        f.engine.onUpdate(f.key, f.success(ids.last()))
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, f.ledger.read(f.key)!!.phase)
        f.engine.onUpdate(f.key, f.success(ids.first()))
        assertEquals(AsyncRelayPhase.COMPLETE, f.ledger.read(f.key)!!.phase)
        assertEquals(listOf(AsyncRelayStage.HEADER, AsyncRelayStage.MEDIA), f.transport.outputs.map { it.stage })
    }

    @Test fun exactSuccessBeforeQueuedResponseIsBufferedDurably() {
        val f = Fixture()
        f.transport.beforeHeaderResponse = {
            val pending = f.ledger.read(f.key)!!
            assertFalse(pending.requests.single().responseBound)
            f.engine.onUpdate(f.key, f.success(100_000))
            assertEquals(1, f.ledger.read(f.key)!!.requests.single().earlyUpdates.size)
        }
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, f.start().phase)
        assertEquals(AsyncRelayPhase.COMPLETE, f.completeMedia().phase)
    }

    @Test fun mediaFinalBeforeResponseCompletesOnlyExactSet() {
        val f = Fixture(album = true); f.start()
        f.transport.beforeMediaResponse = {
            f.engine.onUpdate(f.key, f.success(100_002))
            f.engine.onUpdate(f.key, f.success(100_001))
        }
        assertEquals(AsyncRelayPhase.COMPLETE, f.completeHeader().phase)
    }

    @Test fun duplicateFinalIsIdempotentAndNeverRepeatsOutput() {
        val f = Fixture(); val temp = f.start().requests.single().temporaryMessageIds.single()
        repeat(3) { f.engine.onUpdate(f.key, f.success(temp)) }
        assertEquals(2, f.transport.outputs.size)
        f.completeMedia(); repeat(3) { f.engine.onUpdate(f.key, f.success(temp)) }
        assertEquals(2, f.transport.outputs.size)
    }

    @Test fun unknownScopeAndTemporaryIdsDoNotConfirm() {
        val f = Fixture(); val temp = f.start().requests.single().temporaryMessageIds.single()
        listOf(f.scope.copy(accountId = 99), f.scope.copy(sessionId = "other"), f.scope.copy(destinationChatId = -77)).forEach {
            f.engine.onUpdate(f.key, f.success(temp, s = it))
        }
        f.engine.onUpdate(f.key, f.success(temp + 999))
        assertEquals(AsyncRelayPhase.HEADER_WAITING, f.ledger.read(f.key)!!.phase)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun partialNullOrDuplicateQueuedMediaResponsesBecomeUnknown() {
        for (mode in 0..3) {
            val f = Fixture(album = true); f.start()
            f.transport.mediaResponseFactory = { r, s -> AsyncRelayResponse(s, r.requestId, when (mode) {
                0 -> null
                1 -> listOf(AsyncRelayQueuedMessage(21, s.destinationChatId))
                2 -> listOf(AsyncRelayQueuedMessage(21, s.destinationChatId), null)
                else -> listOf(AsyncRelayQueuedMessage(21, s.destinationChatId), AsyncRelayQueuedMessage(21, s.destinationChatId))
            }) }
            assertEquals(AsyncRelayPhase.UNKNOWN, f.completeHeader().phase)
            f.engine.onUpdate(f.key, f.success(21))
            f.engine.start(f.intent(), f.proof)
            assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(f.key)!!.phase)
            assertEquals(2, f.transport.outputs.size)
        }
    }

    @Test fun callbackWithoutPendingStateDoesNotSubstituteForFinalUpdate() {
        val f = Fixture()
        f.transport.headerResponseFactory = { r, s -> AsyncRelayResponse(s, r.requestId,
            listOf(AsyncRelayQueuedMessage(21, s.destinationChatId, hasSendingState = false))) }
        assertEquals(AsyncRelayPhase.UNKNOWN, f.start().phase)
    }

    @Test fun invalidQueuedResponseScopeRequestOrDestinationStops() {
        for (mode in 0..2) {
            val f = Fixture()
            f.transport.headerResponseFactory = { r, s -> AsyncRelayResponse(
                if (mode == 0) s.copy(sessionId = "wrong") else s,
                if (mode == 1) "wrong-request" else r.requestId,
                listOf(AsyncRelayQueuedMessage(21, if (mode == 2) -7 else s.destinationChatId))) }
            assertEquals(AsyncRelayPhase.UNKNOWN, f.start().phase)
        }
    }

    @Test fun failureAndInvalidFinalFieldsAreUnknown() {
        for (mode in 0..4) {
            val f = Fixture(); val temp = f.start().requests.single().temporaryMessageIds.single()
            val event = when (mode) {
                0 -> AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.FAILED, temp)
                1 -> f.success(temp, 0)
                2 -> f.success(temp).copy(finalIsOutgoing = false)
                3 -> f.success(temp).copy(finalHasSendingState = true)
                else -> f.success(0)
            }
            assertEquals(AsyncRelayPhase.UNKNOWN, f.engine.onUpdate(f.key, event)!!.phase)
            f.engine.onUpdate(f.key, f.success(temp))
            assertEquals(1, f.transport.outputs.size)
        }
    }

    @Test fun earlyFailureForMatchingTemporaryIdStops() {
        val f = Fixture()
        f.transport.beforeHeaderResponse = { f.engine.onUpdate(f.key, AsyncRelayUpdate(f.scope, AsyncRelayUpdateKind.FAILED, 100_000)) }
        assertEquals(AsyncRelayPhase.UNKNOWN, f.start().phase)
    }

    @Test fun unrelatedEarlyUpdateCannotAttachByTextOrOrder() {
        val f = Fixture()
        f.transport.beforeHeaderResponse = { f.engine.onUpdate(f.key, f.success(555)) }
        assertEquals(AsyncRelayPhase.HEADER_WAITING, f.start().phase)
        assertTrue(f.ledger.read(f.key)!!.requests.single().finalMessageIds.isEmpty())
    }

    @Test fun earlyBufferOverflowFailsClosed() {
        val f = Fixture()
        f.transport.beforeHeaderResponse = { repeat(65) { f.engine.onUpdate(f.key, f.success(50_000L + it)) } }
        assertEquals(AsyncRelayPhase.UNKNOWN, f.start().phase)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun conflictingFinalOrRepeatedFinalIdStopsAlbum() {
        for (sameTemp in listOf(true, false)) {
            val f = Fixture(album = true); f.start(); val s = f.completeHeader()
            val ids = s.requests.last().temporaryMessageIds
            f.engine.onUpdate(f.key, f.success(ids.first(), 900))
            f.engine.onUpdate(f.key, f.success(if (sameTemp) ids.first() else ids.last(), if (sameTemp) 901 else 900))
            assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(f.key)!!.phase)
        }
    }

    @Test fun clockTimeoutAndLateFinalNeverResume() {
        val f = Fixture(); val s = f.start(); f.now += 30_000
        assertEquals(AsyncRelayPhase.UNKNOWN, f.engine.expire(f.key)!!.phase)
        f.engine.onUpdate(f.key, f.success(s.requests.single().temporaryMessageIds.single()))
        f.engine.start(f.intent(), f.proof)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun processRecoveryIsExplicitTerminalAndNoReopenRetry() {
        val f = Fixture(); val s = f.start()
        val reopened = AsyncRelayEngine(f.ledger, f.transport) { f.now }
        reopened.start(f.intent(), f.proof)
        assertEquals(1, f.transport.outputs.size)
        assertEquals(1, f.ledger.recoverPending(f.scope.accountId, f.scope.sessionId))
        assertEquals(0, f.ledger.recoverPending(f.scope.accountId))
        reopened.onUpdate(f.key, f.success(s.requests.single().temporaryMessageIds.single()))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(f.key)!!.phase)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun missingTransientContextDoesNotResumeAfterHeaderAck() {
        val f = Fixture(); val s = f.start()
        AsyncRelayEngine(f.ledger, f.transport) { f.now }.onUpdate(f.key, f.success(s.requests.single().temporaryMessageIds.single()))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(f.key)!!.phase)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun stopSessionOrConsentChangeClosesWithoutRetry() {
        val f = Fixture(); val s = f.start(); f.engine.stop(f.key, "SESSION_CHANGED")
        f.engine.onUpdate(f.key, f.success(s.requests.single().temporaryMessageIds.single()))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(f.key)!!.phase)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun currentStateMustBeRevalidatedAfterHeader() {
        for (mode in 0..7) {
            val f = Fixture(); f.start()
            f.transport.freshState = when (mode) {
                0 -> f.state.copy(permissions = f.permissions.copy(canWriteDestination = false))
                1 -> f.state.copy(permissions = f.permissions.copy(messageForwardable = mapOf(101L to false)))
                2 -> f.state.copy(batch = f.state.batch.copy(messages = f.messages.map { it.copy(edited = true) }))
                3 -> f.state.copy(scope = f.scope.copy(sessionId = "new-session"))
                4 -> f.state.copy(config = f.config.copy(userEnabled = false))
                5 -> f.state.copy(configurationRevision = 1) // OFF -> ON cannot hide behind equal config.
                6 -> f.state.copy(config = f.config.copy(sources = f.config.sources.map { it.copy(policy = RelaySourcePolicy.MAZDITA_LEGACY) }))
                else -> null
            }
            assertEquals("case $mode", AsyncRelayPhase.UNKNOWN, f.completeHeader().phase)
            assertEquals(1, f.transport.outputs.size)
        }
    }

    @Test fun delayedHeaderUsesCurrentClockForOriginalTtl() {
        val f = Fixture()
        f.transport.freshState = f.state.copy(config = f.config.copy(maxAgeSeconds = 2))
        f.start(); f.now += 3_000
        assertEquals(AsyncRelayPhase.UNKNOWN, f.completeHeader().phase)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun protectedSourceGetsOnlyReferenceAndNoOriginalCopy() {
        val f = Fixture()
        f.transport.freshState = f.state.copy(permissions = f.permissions.copy(sourceHasProtectedContent = true))
        f.start(media = false)
        assertEquals(AsyncRelayPhase.COMPLETE, f.completeHeader().phase)
        assertEquals(listOf(AsyncRelayStage.HEADER), f.transport.outputs.map { it.stage })
    }

    @Test fun liveAlbumOrCallerCompletenessBooleansCannotAuthorizeOutput() {
        val f = Fixture(album = true)
        f.transport.freshState = f.state.copy(batch = f.state.batch.copy(completeAlbum = true, expectedAlbumSize = 2))
        assertNull(f.engine.start(f.intent()))
        f.transport.freshState = f.state.copy(batch = f.state.batch.copy(synthetic = false, completeAlbum = true, expectedAlbumSize = 2))
        assertNull(f.engine.start(f.intent(), f.proof))
        assertTrue(f.transport.outputs.isEmpty())
    }

    @Test fun wrongManifestOrMutableProofCannotChangePublication() {
        val f = Fixture(album = true)
        assertNull(f.engine.start(f.intent(), (f.proof as AlbumCompletenessEvidence.FakeManifest).copy(memberIds = listOf(101, 103))))
        val ids = mutableListOf(101L, 102L)
        val proof = f.proof.copy(memberIds = ids)
        f.engine.start(f.intent(), proof); ids[1] = 103
        assertEquals(AsyncRelayPhase.MEDIA_WAITING, f.completeHeader().phase)
        assertEquals(listOf(101L, 102L), f.transport.outputs.last().sourceMessageIds)
    }

    @Test fun ledgerDefensivelyCopiesIntentAndSnapshots() {
        val f = Fixture(); val ids = mutableListOf(101L)
        f.engine.start(f.intent().copy(sourceMessageIds = ids)); ids[0] = 999
        assertEquals(listOf(101L), f.ledger.read(f.key)!!.intent.sourceMessageIds)
        val read = f.ledger.read(f.key)!!
        try { (read.intent.sourceMessageIds as MutableList<Long>)[0] = 666 } catch (_: UnsupportedOperationException) { /* Immutable output is also safe. */ }
        assertEquals(listOf(101L), f.ledger.read(f.key)!!.intent.sourceMessageIds)
    }

    @Test fun overlappingAlbumAndSingleMessageAreNeverReclaimed() {
        val f = Fixture(album = true); f.start()
        val other = f.intent().copy(key = f.key.copy(groupId = "m:101"), sourceMessageIds = listOf(101))
        val request = AsyncRelayRequest("other-request", AsyncRelayStage.HEADER, emptyList(), f.now + 30_000)
        assertFalse(f.ledger.create(AsyncRelaySnapshot(other, AsyncRelayPhase.HEADER_WAITING, listOf(request))))
    }

    @Test fun allTwelveAuthorizedTipstersPassFakePhotoAckFlow() {
        PickBridgeRules.allowedTipsters.forEach { name ->
            val f = Fixture(policy = RelaySourcePolicy.UNICOREY_MEX, text = "Tipster: $name\nPick: Over 8.5 @1.80")
            f.start(); f.completeHeader()
            assertEquals(name, AsyncRelayPhase.COMPLETE, f.completeMedia().phase)
            assertEquals(2, f.transport.outputs.size)
        }
    }

    @Test fun unapprovedPolicyUnknownTipsterAndReplayNeverOutput() {
        for (policy in listOf(RelaySourcePolicy.MAZDITA_LEGACY, RelaySourcePolicy.DIRECT_OTHER)) {
            val f = Fixture(policy = policy); assertNull(f.engine.start(f.intent())); assertTrue(f.transport.outputs.isEmpty())
        }
        val f = Fixture(policy = RelaySourcePolicy.UNICOREY_MEX, text = "Tipster: Unknown synthetic\nPick: Over 8.5 @1.80")
        assertNull(f.engine.start(f.intent())); assertTrue(f.transport.outputs.isEmpty())
    }

    @Test fun requestIntentIsDurableBeforeEachOutputAndExceptionNeverRetries() {
        val f = Fixture()
        f.transport.beforeHeaderResponse = { r ->
            assertEquals(r.requestId, f.ledger.read(f.key)!!.requests.single().requestId)
            throw IllegalStateException("simulated crash window")
        }
        assertEquals(AsyncRelayPhase.UNKNOWN, f.start().phase)
        f.engine.start(f.intent())
        assertEquals(1, f.transport.outputs.size)
    }

    @Test(timeout = 2_000) fun reusedHeaderTemporaryIdInMediaIsUnknownWithoutLoop() {
        val f = Fixture(); val header = f.start().requests.single().temporaryMessageIds.single()
        f.transport.mediaResponseFactory = { r, s -> AsyncRelayResponse(s, r.requestId,
            listOf(AsyncRelayQueuedMessage(header, s.destinationChatId))) }
        assertEquals(AsyncRelayPhase.UNKNOWN, f.completeHeader().phase)
        assertEquals(2, f.transport.outputs.size)
    }

    @Test(timeout = 2_000) fun reusedHeaderFinalIdInMediaIsUnknownWithoutLoop() {
        val f = Fixture(); val header = f.start().requests.single().temporaryMessageIds.single()
        val media = f.completeHeader().requests.last().temporaryMessageIds.single()
        assertEquals(AsyncRelayPhase.UNKNOWN, f.engine.onUpdate(f.key, f.success(media, header + 1_000_000))!!.phase)
    }

    @Test fun changeDuringDurableHeaderClaimStopsBeforeOutput() {
        val f = Fixture()
        val ledger = object : AsyncRelayLedger by f.ledger {
            override fun create(snapshot: AsyncRelaySnapshot): Boolean {
                val created = f.ledger.create(snapshot)
                f.transport.freshState = f.state.copy(config = f.config.copy(userEnabled = false))
                return created
            }
        }
        val engine = AsyncRelayEngine(ledger, f.transport) { f.now }
        assertEquals(AsyncRelayPhase.UNKNOWN, engine.start(f.intent())!!.phase)
        assertTrue(f.transport.outputs.isEmpty())
    }

    @Test fun changeDuringDurableMediaIntentStopsBeforeMediaOutput() {
        val f = Fixture()
        val ledger = object : AsyncRelayLedger by f.ledger {
            override fun compareAndSet(expectedVersion: Long, next: AsyncRelaySnapshot): Boolean {
                val changed = f.ledger.compareAndSet(expectedVersion, next)
                if (changed && next.phase == AsyncRelayPhase.MEDIA_WAITING) {
                    f.transport.freshState = f.state.copy(configurationRevision = 1)
                }
                return changed
            }
        }
        val engine = AsyncRelayEngine(ledger, f.transport) { f.now }
        val header = engine.start(f.intent())!!.requests.single().temporaryMessageIds.single()
        assertEquals(AsyncRelayPhase.UNKNOWN, engine.onUpdate(f.key, f.success(header))!!.phase)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun sourceBindingAndAllOutgoingIdsMustRemainExact() {
        for (mode in 0..2) {
            val f = Fixture()
            f.transport.freshState = when (mode) {
                0 -> f.state.copy(config = f.config.copy(accountId = 7002))
                1 -> f.state.copy(batch = f.state.batch.copy(messages = f.messages.map { it.copy(outgoing = true) }))
                else -> f.state.copy(config = f.config.copy(destinationChatId = -8001))
            }
            assertNull(f.engine.start(f.intent()))
            assertTrue(f.transport.outputs.isEmpty())
        }
    }

    @Test fun invalidRevisionAndDeadlineOverflowFailBeforeOutput() {
        val f = Fixture()
        f.transport.freshState = f.state.copy(configurationRevision = Long.MAX_VALUE)
        assertNull(f.engine.start(f.intent().copy(configurationRevision = Long.MAX_VALUE)))
        f.transport.freshState = f.state
        f.now = Long.MAX_VALUE - 10
        assertNull(f.engine.start(f.intent()))
        assertTrue(f.transport.outputs.isEmpty())
    }

    @Test fun requestCorrelationCollisionMarksBothActiveIntentsUnknown() {
        val f = Fixture()
        fun initial(id: Long) = AsyncRelaySnapshot(f.intent().copy(key = f.key.copy(groupId = "m:$id"),
            sourceMessageIds = listOf(id)), AsyncRelayPhase.HEADER_WAITING,
            listOf(AsyncRelayRequest("shared-request-id", AsyncRelayStage.HEADER, emptyList(), f.now + 30_000)))
        val a = initial(101); val b = initial(102)
        assertTrue(f.ledger.create(a)); assertFalse(f.ledger.create(b))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(a.key)!!.phase)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(b.key)!!.phase)
        assertFalse(f.ledger.create(b))
    }

    @Test fun scopedTemporaryCollisionMarksBothActiveRequestsUnknown() {
        val f = Fixture()
        fun initial(id: Long) = AsyncRelaySnapshot(f.intent().copy(key = f.key.copy(groupId = "m:$id"),
            sourceMessageIds = listOf(id)), AsyncRelayPhase.HEADER_WAITING,
            listOf(AsyncRelayRequest("request-$id", AsyncRelayStage.HEADER, emptyList(), f.now + 30_000)))
        val a = initial(101); val b = initial(102)
        assertTrue(f.ledger.create(a)); assertTrue(f.ledger.create(b))
        fun bind(s: AsyncRelaySnapshot) = s.copy(version = 1, requests = listOf(s.requests.single().copy(
            responseBound = true, temporaryMessageIds = listOf(222))))
        assertTrue(f.ledger.compareAndSet(0, bind(a)))
        assertFalse(f.ledger.compareAndSet(0, bind(b)))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(a.key)!!.phase)
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(b.key)!!.phase)
    }

    @Test fun cancellationDuringHeaderCommitPreventsHeader() {
        val f = Fixture()
        val ledger = object : AsyncRelayLedger by f.ledger {
            override fun create(snapshot: AsyncRelaySnapshot): Boolean {
                val result = f.ledger.create(snapshot)
                f.ledger.recoverPending(f.scope.accountId)
                return result
            }
        }
        val result = AsyncRelayEngine(ledger, f.transport) { f.now }.start(f.intent())!!
        assertEquals(AsyncRelayPhase.UNKNOWN, result.phase)
        assertTrue(f.transport.outputs.isEmpty())
    }

    @Test fun cancellationDuringMediaCommitPreventsMedia() {
        val f = Fixture()
        val ledger = object : AsyncRelayLedger by f.ledger {
            override fun compareAndSet(expectedVersion: Long, next: AsyncRelaySnapshot): Boolean {
                val result = f.ledger.compareAndSet(expectedVersion, next)
                if (result && next.phase == AsyncRelayPhase.MEDIA_WAITING) f.ledger.recoverPending(f.scope.accountId)
                return result
            }
        }
        val engine = AsyncRelayEngine(ledger, f.transport) { f.now }
        val header = engine.start(f.intent())!!.requests.single().temporaryMessageIds.single()
        assertEquals(AsyncRelayPhase.UNKNOWN, engine.onUpdate(f.key, f.success(header))!!.phase)
        assertEquals(1, f.transport.outputs.size)
    }

    @Test fun commitDelayBeyondRequestDeadlinePreventsHeader() {
        val f = Fixture()
        val ledger = object : AsyncRelayLedger by f.ledger {
            override fun create(snapshot: AsyncRelaySnapshot): Boolean {
                val result = f.ledger.create(snapshot); f.now += 30_000; return result
            }
        }
        assertEquals(AsyncRelayPhase.UNKNOWN, AsyncRelayEngine(ledger, f.transport) { f.now }.start(f.intent())!!.phase)
        assertTrue(f.transport.outputs.isEmpty())
    }

    @Test fun partialAlbumFinalsThenTimeoutAreTerminalEvenWithLateRemainder() {
        val f = Fixture(album = true); f.start(); val media = f.completeHeader()
        val temps = media.requests.last().temporaryMessageIds
        f.engine.onUpdate(f.key, f.success(temps.first())); f.now += 30_000
        f.engine.expire(f.key); f.engine.onUpdate(f.key, f.success(temps.last()))
        assertEquals(AsyncRelayPhase.UNKNOWN, f.ledger.read(f.key)!!.phase)
        f.engine.start(f.intent(), f.proof)
        assertEquals(2, f.transport.outputs.size)
        assertEquals(0, f.transport.sentRealMessages)
    }
}
