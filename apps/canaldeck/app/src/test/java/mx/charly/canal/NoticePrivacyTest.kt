package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class NoticePrivacyTest {
    @Test fun unknownOrProtectedContentNeverShowsText() {
        assertFalse(NoticePrivacy.allowText(true, false, false, false, false, true, false))
        assertFalse(NoticePrivacy.allowText(true, true, true, false, false, true, false))
        assertFalse(NoticePrivacy.allowText(true, true, false, true, false, true, false))
        assertFalse(NoticePrivacy.allowText(true, true, false, false, true, true, false))
        assertFalse(NoticePrivacy.allowText(true, true, false, false, false, false, false))
        assertFalse(NoticePrivacy.allowText(true, true, false, false, false, true, true))
        assertFalse(NoticePrivacy.allowText(false, true, false, false, false, true, false))
        assertTrue(NoticePrivacy.allowText(true, true, false, false, false, true, false))
    }

    @Test fun protectedTransitionDropsEarlierTextFromInbox() {
        val before = NoticePrivacy.appendRecent(emptyList(), "Texto sintético anterior", true)
        val after = NoticePrivacy.appendRecent(before, "Texto sintético protegido", false)
        assertEquals(listOf(NoticePrivacy.REDACTED), after)
        assertFalse(after.joinToString().contains("sintético"))
        assertEquals(5, (1..7).fold(emptyList<String>()) { lines, n -> NoticePrivacy.appendRecent(lines, "$n", true) }.size)
    }

    @Test fun staleConfirmationCannotReleaseNewerProtectionChange() {
        val fence = PrivacyFence(); val chat = 42L
        val oldVersion = fence.snapshot() // both GetChat and searchPublicChat began before the update
        fence.changed(chat, true)
        fence.confirmed(chat, oldVersion, false)
        assertTrue(fence.isPending(chat))
        assertFalse(fence.accepts(chat, oldVersion, false))
        val current = fence.snapshot()
        fence.confirmed(chat, current, false) // reply does not match protection update
        assertTrue(fence.isPending(chat))
        fence.confirmed(chat, current, true)
        assertFalse(fence.isPending(chat))
        assertFalse(fence.accepts(chat, current, false)) // an earlier request finishes after the good one
        assertTrue(fence.accepts(chat, current, true))
        val anotherRequest = fence.snapshot()
        fence.changed(43L, true)
        assertTrue(fence.accepts(chat, anotherRequest, true)) // another chat changed, not this one
        fence.changed(chat, false)
        assertTrue(fence.isPending(chat)) // failed fetch has no confirmation
        assertFalse(fence.isPending(44L))
    }

    @Test fun staleGetChatAndPublicSearchCannotOverwriteConfirmedProtection() {
        val fence = PrivacyFence(); val chats = mutableMapOf<Long, String>(); val id = 42L
        val oldGet = fence.snapshot(); val oldSearch = fence.snapshot()
        assertTrue(fence.store(id, oldGet, false, "antes", chats))
        fence.changed(id, true)
        val fresh = fence.snapshot()
        assertTrue(fence.store(id, fresh, true, "protegido", chats, confirmsProtection = true))
        assertFalse(fence.isPending(id))
        assertFalse(fence.store(id, oldGet, false, "get obsoleto", chats))
        assertFalse(fence.store(id, oldSearch, false, "búsqueda obsoleta", chats))
        assertEquals("protegido", chats[id])
        assertFalse(fence.store(id, fresh, false, "respuesta incoherente", chats))
        assertEquals("protegido", chats[id])
    }

    @Test fun unexpectedProtectedObservationFailsClosedEvenWithoutUpdateEvent() {
        val fence = PrivacyFence(); val id = 42L; val chats = mutableMapOf<Long, String>()
        val first = fence.snapshot()
        assertTrue(fence.store(id, first, false, "no protegido", chats, confirmsProtection = true))
        val oldQuery = fence.snapshot()
        // GetChat now reports protected, contradicting the last confirmed state.
        assertTrue(fence.observeProtected(id, oldQuery, previouslyProtected = false))
        assertTrue(fence.isPending(id))
        assertFalse(fence.store(id, oldQuery, false, "respuesta anterior", chats))
        val fresh = fence.snapshot()
        assertFalse(fence.store(id, fresh, false, "respuesta contradictoria reciente", chats, confirmsProtection = true))
        assertTrue(fence.isPending(id))
        assertFalse(fence.observeProtected(id, fresh, previouslyProtected = false))
        assertTrue(fence.store(id, fresh, true, "protegido", chats, confirmsProtection = true))
        assertFalse(fence.isPending(id))
        assertFalse(fence.store(id, oldQuery, false, "respuesta tardía", chats))
        assertEquals("protegido", chats[id])
    }

    @Test fun positiveContradictionAfterUnprotectedEventBlocksImmediately() {
        val fence = PrivacyFence(); val id = 7L
        fence.changed(id, false)
        val query = fence.snapshot()
        fence.confirmed(id, query, false)
        assertFalse(fence.isPending(id))
        assertTrue(fence.observeProtected(id, query, previouslyProtected = false))
        assertTrue(fence.isPending(id))
        assertFalse(fence.accepts(id, query, false))
        val newQuery = fence.snapshot()
        fence.confirmed(id, newQuery, false)
        assertTrue(fence.isPending(id))
        fence.changed(id, false) // only a later explicit change may permit unprotected content
        val afterUpdate = fence.snapshot()
        fence.confirmed(id, afterUpdate, false)
        assertFalse(fence.isPending(id))
    }
}
