package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class AlbumCompletenessGateTest {
    private fun photo(id: Long, album: Long = 7) = RelayMessage(1, -111, id, 1_800_000_001,
        kind = RelayContentKind.PHOTO, albumId = album, canBeForwarded = true)
    private val manifest = AlbumCompletenessEvidence.FakeManifest(1, -111, 7, listOf(101, 102))

    @Test fun liveCallerCountAndTrueBooleanNeverEstablishCompleteness() {
        for (size in listOf(1, 2, 10, 11)) {
            val batch = RelayBatch((101L until 101L + size).map { photo(it) }, completeAlbum = true, expectedAlbumSize = size)
            val result = AlbumCompletenessGate.reconstruct(batch)
            assertTrue(result.hold)
            assertEquals("LIVE_ALBUM_COMPLETENESS_UNPROVEN", result.reason)
            assertFalse(result.batch.completeAlbum)
            assertNull(result.batch.expectedAlbumSize)
        }
    }

    @Test fun evenAnExactFakeManifestCannotAuthorizeALiveAlbum() {
        val result = AlbumCompletenessGate.reconstruct(RelayBatch(listOf(photo(101), photo(102))), manifest)
        assertTrue(result.hold)
        assertFalse(result.batch.completeAlbum)
    }

    @Test fun exactSyntheticManifestReconstructsOutOfOrderFixture() {
        val result = AlbumCompletenessGate.reconstruct(RelayBatch(listOf(photo(102), photo(101)), synthetic = true), manifest)
        assertFalse(result.hold)
        assertTrue(result.batch.completeAlbum)
        assertEquals(listOf(101L, 102L), result.batch.messages.map { it.messageId })
        assertEquals(2, result.batch.expectedAlbumSize)
    }

    @Test fun missingExtraDuplicateOrWrongScopeCannotUseASyntheticManifest() {
        val batches = listOf(listOf(photo(101)), listOf(photo(101), photo(102), photo(103)),
            listOf(photo(101), photo(101)), listOf(photo(101), photo(102).copy(sourceChatId = -222)),
            listOf(photo(101), photo(102).copy(albumId = 8)), listOf(photo(101), photo(102).copy(edited = true)))
        batches.forEach { assertTrue(AlbumCompletenessGate.reconstruct(RelayBatch(it, synthetic = true), manifest).hold) }
        val synthetic = RelayBatch(listOf(photo(101), photo(102)), synthetic = true)
        listOf(manifest.copy(accountId = 2), manifest.copy(sourceChatId = -222), manifest.copy(albumId = 8),
            manifest.copy(memberIds = listOf(101, 101))).forEach {
            assertTrue(AlbumCompletenessGate.reconstruct(synthetic, it).hold)
        }
    }

    @Test fun singlePhotographDoesNotNeedAnAlbumWitness() {
        val result = AlbumCompletenessGate.reconstruct(RelayBatch(listOf(photo(101, album = 0))))
        assertFalse(result.hold)
        assertFalse(result.batch.completeAlbum)
        assertEquals("SINGLE_MESSAGE_NO_ALBUM", result.reason)
    }

    @Test fun tenObservedPartsRepeatedSnapshotsAndHistoryFlagsRemainUnproven() {
        val index = LiveAlbumObservationIndex()
        (101L..110L).reversed().forEach { index.observe(1, photo(it)) }
        val key = LiveAlbumKey(1, 1, -111, 7)
        repeat(20) {
            assertFalse(index.inspect(key)!!.complete)
            assertEquals(10, index.inspect(key)!!.observedIds.size)
        }
        assertTrue(AlbumCompletenessGate.reconstruct(RelayBatch((101L..110L).map { photo(it) },
            completeAlbum = true, expectedAlbumSize = 10, liveUpdate = false)).hold)
    }

    @Test fun duplicateObservationsDoNotInflateMembershipAndSessionsNeverMix() {
        val index = LiveAlbumObservationIndex()
        index.observe(1, photo(101)); index.observe(1, photo(101)); index.observe(2, photo(102))
        assertEquals(listOf(101L), index.inspect(LiveAlbumKey(1, 1, -111, 7))!!.observedIds)
        assertEquals(listOf(102L), index.inspect(LiveAlbumKey(1, 2, -111, 7))!!.observedIds)
        index.invalidate(1, 1, -111, 101)
        assertEquals("LIVE_ALBUM_CHANGED_OR_UNSUPPORTED", index.inspect(LiveAlbumKey(1, 1, -111, 7))!!.reason)
        assertEquals("LIVE_ALBUM_COMPLETENESS_UNPROVEN", index.inspect(LiveAlbumKey(1, 2, -111, 7))!!.reason)
    }

    @Test fun capacityLimitsNeverFlushAnAlbumAsComplete() {
        val index = LiveAlbumObservationIndex(maxAlbums = 1, maxMembers = 2)
        index.observe(1, photo(101)); index.observe(1, photo(102))
        assertEquals("LIVE_ALBUM_OBSERVATION_LIMIT", index.observe(1, photo(103)).reason)
        assertFalse(index.observe(1, photo(103)).complete)
        assertEquals("LIVE_ALBUM_OBSERVATION_LIMIT", index.observe(1, photo(104, album = 8)).reason)
    }
}
