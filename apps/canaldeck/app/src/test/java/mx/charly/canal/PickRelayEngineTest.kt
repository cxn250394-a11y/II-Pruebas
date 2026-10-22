package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class PickRelayEngineTest {
    private val f = RelayTestFixtures
    private fun fake(permissions: RelayPermissions = f.permissions()) = FakeRelayTdlib({ permissions })
    private fun process(ledger: MemoryRelayLedger = f.ledger(), transport: FakeRelayTdlib = fake(), batch: RelayBatch = f.batch(f.message), config: RelayConfig = f.config): RelayOutcome =
        PickRelayEngine(ledger).deliverSynthetic(batch, config, transport, f.now)

    @Test fun syntheticTextBridgeConfirmsOnlyFakeRequestsAndZeroRealSends() {
        val ledger = f.ledger(); val fake = fake()
        val result = process(ledger, fake)
        assertEquals(RelayDeliveryState.CONFIRMED, result.deliveryState)
        assertEquals(1, fake.headers.size)
        assertEquals(1, fake.forwards.size)
        assertEquals(listOf(101L), fake.forwards.single().messageIds)
        assertEquals(0, fake.sentRealMessages)
        assertEquals(0L, ledger.counters(RelayCounterKey(1, -111, f.now / 3600 * 3600))[RelayMetric.REAL_CONFIRMED])
    }
    @Test fun liveSnapshotNeverUsesSyntheticDeliveryEvenWithEnabledUserSwitch() {
        val fake = fake()
        val result = process(transport = fake, batch = f.batch(f.message, synthetic = false))
        assertEquals("SYNTHETIC_ONLY", result.plan.reason)
        assertTrue(fake.headers.isEmpty()); assertTrue(fake.forwards.isEmpty())
        assertEquals(0, fake.sentRealMessages)
    }
    @Test fun nonSyntheticTransportCannotBeInvokedByThisEngine() {
        var calls = 0
        val transport = object : RelayTransport {
            override val synthetic = false
            override fun refreshBatch(batch: RelayBatch): RelayBatch { calls++; error("No live access") }
            override fun currentPermissions(batch: RelayBatch, config: RelayConfig): RelayPermissions { calls++; error("No live access") }
            override fun sendHeader(destinationChatId: Long, header: String): RelayAck { calls++; error("No live access") }
            override fun forwardOriginals(destinationChatId: Long, sourceChatId: Long, messageIds: List<Long>): RelayAck { calls++; error("No live access") }
        }
        val out = PickRelayEngine(f.ledger()).deliverSynthetic(f.batch(f.message), f.config, transport, f.now)
        assertEquals("SYNTHETIC_ONLY", out.plan.reason); assertEquals(0, calls)
    }
    @Test fun realAppPreparationHasNoTransportAndNoFakeConfirmedState() {
        val ledger = f.ledger()
        val out = PickRelayEngine(ledger).prepare(f.batch(f.message, synthetic = false), f.config, f.permissions(), f.now)
        assertEquals(RelayAction.FORWARD_ALLOWED_MEDIA, out.plan.action)
        assertNull(out.deliveryState)
        assertNull(ledger.state(out.plan.key!!))
        assertEquals(1L, ledger.counters(RelayCounterKey(1,-111,f.now / 3600 * 3600))[RelayMetric.PREPARED])
    }
    @Test fun defaultOffStillDiagnosesCandidatesWithoutPreparingOutput() {
        val ledger = f.ledger()
        val message = f.message.copy(text = "Tipster: Dr MLS", hasProtectedContent = true)
        val config = f.config.copy(userEnabled = false, destinationChatId = null,
            sources = listOf(f.source.copy(policy = RelaySourcePolicy.UNICOREY_MEX)))
        val out = PickRelayEngine(ledger).prepare(f.batch(message, synthetic = false), config, f.permissions(), f.now)
        assertEquals(RelayAction.HOLD, out.plan.action)
        val counters = ledger.counters(RelayCounterKey(1,-111,f.now / 3600 * 3600))
        assertEquals(1L, counters[RelayMetric.SEEN]); assertEquals(1L, counters[RelayMetric.CANDIDATES])
        assertEquals(1L, counters[RelayMetric.VERIFIED_TIPSTER]); assertEquals(1L, counters[RelayMetric.PROTECTED])
        assertEquals(1L, counters[RelayMetric.UNIQUE]); assertEquals(0L, counters[RelayMetric.PREPARED]); assertTrue(ledger.deliveries.isEmpty())
    }
    @Test fun duplicateAndReplayCannotResendAfterConfirmation() {
        val ledger = f.ledger(); val fake = fake(); val engine = PickRelayEngine(ledger)
        engine.deliverSynthetic(f.batch(f.message), f.config, fake, f.now)
        val out = engine.deliverSynthetic(f.batch(f.message), f.config, fake, f.now)
        assertEquals(RelayAction.DROP, out.plan.action)
        assertEquals(1, fake.headers.size); assertEquals(1, fake.forwards.size)
        assertEquals(0, fake.sentRealMessages)
    }
    @Test fun userOffMeansNoFakeHeaderNorForward() {
        val fake = fake()
        val out = process(transport = fake, config = f.config.copy(userEnabled = false))
        assertEquals(RelayAction.HOLD, out.plan.action)
        assertTrue(fake.headers.isEmpty()); assertTrue(fake.forwards.isEmpty())
    }
    @Test fun unarmedSourceAndOldCheckpointCannotBeDelivered() {
        val fake = fake()
        assertEquals(RelayAction.DROP, process(MemoryRelayLedger(), fake).plan.action)
        val ledger = f.ledger().apply { checkpoint(1, -111, 105, f.now) }
        assertEquals(RelayAction.DROP, process(ledger, fake).plan.action)
        assertTrue(fake.headers.isEmpty())
    }
    @Test fun closingReopeningUsesNewBaselineAndNeverFlushesBacklog() {
        val ledger = f.ledger(); val firstFake = fake()
        process(ledger, firstFake)
        ledger.checkpoint(1, -111, 120, f.now + 10)
        val reopened = PickRelayEngine(ledger); val after = fake()
        val out = reopened.deliverSynthetic(f.batch(f.message.copy(messageId = 115, originalUnix = f.now + 9)), f.config, after, f.now + 10)
        assertEquals(RelayAction.DROP, out.plan.action)
        assertTrue(after.headers.isEmpty())
        assertEquals(1, firstFake.headers.size)
    }
    @Test fun samePublicationInDifferentSourcesHasDifferentIdentity() {
        val ledger = f.ledger().apply { checkpoint(1, -222, 100, f.now - 60) }
        val config = f.config.copy(sources = listOf(f.source, f.source.copy(chatId = -222)))
        val engine = PickRelayEngine(ledger)
        val a = fake(); val b = fake(f.permissions(sourceChat = -222))
        engine.deliverSynthetic(f.batch(f.message), config, a, f.now)
        engine.deliverSynthetic(f.batch(f.message.copy(sourceChatId = -222)), config, b, f.now)
        assertEquals(1, a.headers.size); assertEquals(1, b.headers.size)
        assertNotEquals(a.forwards.single().source, b.forwards.single().source)
    }
    @Test fun differentAccountsHaveSeparateIdempotencyKeys() {
        val ledger = f.ledger().apply { checkpoint(2, -111, 100, f.now - 60) }
        val engine = PickRelayEngine(ledger)
        val a = fake(); val b = fake(f.permissions(account = 2))
        val c2 = f.config.copy(accountId = 2, sources = listOf(f.source.copy(accountId = 2)))
        engine.deliverSynthetic(f.batch(f.message), f.config, a, f.now)
        engine.deliverSynthetic(f.batch(f.message.copy(accountId = 2)), c2, b, f.now)
        assertEquals(1, a.headers.size); assertEquals(1, b.headers.size)
        assertEquals(2, ledger.deliveries.size)
    }
    @Test fun completeAlbumProducesExactlyOneHeaderAndOneOriginalBatch() {
        val a = f.message.copy(kind = RelayContentKind.PHOTO, albumId = 17)
        val b = a.copy(messageId = 102)
        val fake = fake(f.permissions(listOf(a,b)))
        val out = process(transport = fake, batch = f.batch(a,b,album = true,size = 2))
        assertEquals(RelayDeliveryState.CONFIRMED, out.deliveryState)
        assertEquals(1, fake.headers.size); assertEquals(listOf(101L,102L), fake.forwards.single().messageIds)
        assertEquals(0, fake.sentRealMessages)
    }
    @Test fun partialAlbumProducesNoRequests() {
        val fake = fake()
        val out = process(transport = fake, batch = f.batch(f.message.copy(albumId = 17, kind = RelayContentKind.PHOTO)))
        assertEquals(RelayAction.HOLD, out.plan.action)
        assertTrue(fake.headers.isEmpty()); assertTrue(fake.forwards.isEmpty())
    }
    @Test fun protectedMessageOnlySendsMetadataHeaderAndNeverMedia() {
        val message = f.message.copy(text = "CONTENIDO PROTEGIDO SINTETICO", hasProtectedContent = true, kind = RelayContentKind.PHOTO)
        val fake = fake()
        val out = process(transport = fake, batch = f.batch(message))
        assertEquals(RelayAction.REFERENCE_ONLY, out.plan.action)
        assertEquals(1, fake.headers.size); assertTrue(fake.forwards.isEmpty())
        assertFalse(fake.headers.single().text.contains(message.text))
        assertEquals(0, fake.sentRealMessages)
    }
    @Test fun permissionRevokedBeforeHeaderStopsWithUnknownAndNoRequests() {
        val fake = FakeRelayTdlib({ read -> if (read == 0) f.permissions() else f.permissions().copy(canWriteDestination = false) })
        val out = process(transport = fake)
        assertEquals(RelayDeliveryState.UNKNOWN, out.deliveryState)
        assertTrue(fake.headers.isEmpty()); assertTrue(fake.forwards.isEmpty())
    }
    @Test fun protectionAppearsBeforeHeaderDowngradesToReferenceWithoutForwarding() {
        val fake = FakeRelayTdlib({ read -> if (read == 0) f.permissions() else f.permissions().copy(sourceHasProtectedContent = true) })
        val out = process(transport = fake)
        assertEquals(RelayDeliveryState.CONFIRMED, out.deliveryState)
        assertEquals(RelayAction.REFERENCE_ONLY, out.plan.action)
        assertEquals(1, fake.headers.size); assertTrue(fake.forwards.isEmpty())
    }
    @Test fun permissionRevokedBetweenHeaderAndMediaStopsWithoutForward() {
        val fake = FakeRelayTdlib({ read -> if (read < 2) f.permissions() else f.permissions().copy(messageForwardable = mapOf(101L to false)) })
        val out = process(transport = fake)
        assertEquals(RelayDeliveryState.UNKNOWN, out.deliveryState)
        assertEquals(1, fake.headers.size); assertTrue(fake.forwards.isEmpty())
    }
    @Test fun crashBetweenRequestAndAckIsUnknownAndNeverAutomaticallyRetried() {
        val ledger = f.ledger(); val fake = FakeRelayTdlib({ f.permissions() }, failAfterHeader = true)
        val first = process(ledger, fake)
        assertEquals(RelayDeliveryState.UNKNOWN, first.deliveryState)
        val reopened = PickRelayEngine(ledger); val retry = fake()
        val out = reopened.deliverSynthetic(f.batch(f.message), f.config, retry, f.now)
        assertEquals(RelayAction.DROP, out.plan.action)
        assertTrue(retry.headers.isEmpty())
        assertEquals(RelayDeliveryState.UNKNOWN, ledger.state(first.plan.key!!))
    }
    @Test fun unknownForwardAckAlsoCannotRetryOrDuplicateHeader() {
        val ledger = f.ledger(); val fake = FakeRelayTdlib({ f.permissions() }, forwardAck = RelayAck.UNKNOWN)
        val first = process(ledger, fake)
        assertEquals(RelayDeliveryState.UNKNOWN, first.deliveryState)
        process(ledger, fake)
        assertEquals(1, fake.headers.size); assertEquals(1, fake.forwards.size)
    }
    @Test fun unresolvedPendingSurvivesRestartAsUnknownWithoutReset() {
        val ledger = f.ledger(); val key = RelayKey(1,-111,"m:101")
        assertTrue(ledger.claim(key)); assertEquals(1, ledger.recoverPending(1))
        assertEquals(RelayDeliveryState.UNKNOWN, ledger.state(key))
        assertFalse(ledger.claim(key)); ledger.finish(key, RelayDeliveryState.CONFIRMED)
        assertEquals(RelayDeliveryState.UNKNOWN, ledger.state(key))
    }
    @Test fun changedRecordsBeforeHeaderNeverSendAnyRequest() {
        val changes = listOf<(RelayMessage) -> RelayMessage>(
            { it.copy(edited = true) }, { it.copy(outgoing = true) },
            { it.copy(permalink = "https://t.me/example_source/202") },
            { it.copy(originalUnix = it.originalUnix + 1) },
            { it.copy(albumId = 17) }, { it.copy(text = "Contenido modificado") }
        )
        for (change in changes) {
            val fake = FakeRelayTdlib({ f.permissions() }, refreshed = { _, batch -> batch.copy(messages = batch.messages.map(change)) })
            val out = process(transport = fake)
            assertEquals(RelayDeliveryState.UNKNOWN, out.deliveryState)
            assertTrue(fake.headers.isEmpty()); assertTrue(fake.forwards.isEmpty())
            assertEquals(0, fake.sentRealMessages)
        }
    }
    @Test fun changedRecordsAfterHeaderNeverForwardAndBecomeUnknown() {
        val changes = listOf<(RelayMessage) -> RelayMessage>(
            { it.copy(edited = true) }, { it.copy(outgoing = true) },
            { it.copy(permalink = "https://t.me/example_source/202") },
            { it.copy(originalUnix = it.originalUnix + 1) },
            { it.copy(albumId = 17) }, { it.copy(text = "Contenido modificado") }
        )
        for (change in changes) {
            val fake = FakeRelayTdlib({ f.permissions() }, refreshed = { read, batch ->
                if (read == 0) batch else batch.copy(messages = batch.messages.map(change))
            })
            val out = process(transport = fake)
            assertEquals(RelayDeliveryState.UNKNOWN, out.deliveryState)
            assertEquals(1, fake.headers.size); assertTrue(fake.forwards.isEmpty())
            assertEquals(0, fake.sentRealMessages)
        }
    }
    @Test fun changingSyntheticBatchToLiveBeforeDeliveryIsRejected() {
        val fake = FakeRelayTdlib({ f.permissions() }, refreshed = { _, batch -> batch.copy(synthetic = false) })
        assertEquals(RelayDeliveryState.UNKNOWN, process(transport = fake).deliveryState)
        assertTrue(fake.headers.isEmpty())
    }
    @Test fun malformedEmptyInputFailsClosedWithoutCounterErrors() {
        val out = PickRelayEngine(f.ledger()).prepare(f.batch(), RelayConfig(0), f.permissions(), 0)
        assertEquals(RelayAction.DROP, out.plan.action)
    }
}
