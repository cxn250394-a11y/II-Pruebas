package mx.charly.canal

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PickSourcePreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val account = 990034L
    private val chat = -100123456L

    @After fun cleanup() {
        context.getSharedPreferences("canaldeck-org-$account", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun offDefaultAndNoUninitializedReplays() {
        val p = Organization(context, account)
        val now = System.currentTimeMillis() / 1000L
        assertEquals(PickWatchMode.OFF, p.pickMode(chat))
        assertFalse(p.acceptPick(chat, 10L, now, 0L))
        assertFalse(p.acceptPick(chat, 0L, now, 0L))
    }

    @Test fun newEnabledChatSkipsPriorMessages() {
        val p = Organization(context, account)
        val now = System.currentTimeMillis() / 1000L
        assertTrue(p.pickMode(chat, PickWatchMode.RESELLER, latestMessageId = 100L))
        assertFalse(p.acceptPick(chat, 99L, now, 0L))
        assertFalse(p.acceptPick(chat, 100L, now, 0L))
        assertTrue(p.acceptPick(chat, 101L, now, 0L))
        assertFalse(p.acceptPick(chat, 101L, now, 0L))
    }

    @Test fun survivesRestartAndDeduplicatesAlbums() {
        val now = System.currentTimeMillis() / 1000L
        val first = Organization(context, account)
        assertTrue(first.pickMode(chat, PickWatchMode.CANAL, latestMessageId = 100L))
        assertTrue(first.acceptPick(chat, 101L, now, albumId = 74L))
        val afterRestart = Organization(context, account)
        assertEquals(PickWatchMode.CANAL, afterRestart.pickMode(chat))
        assertFalse(afterRestart.acceptPick(chat, 101L, now, 74L))
        assertFalse(afterRestart.acceptPick(chat, 102L, now, 74L))
        assertTrue(afterRestart.acceptPick(chat, 103L, now, 0L))
    }

    @Test fun noReplayAfterUserDisablesAndReenables() {
        val p = Organization(context, account)
        val now = System.currentTimeMillis() / 1000L
        assertTrue(p.pickMode(chat, PickWatchMode.CANAL, 10L))
        assertTrue(p.acceptPick(chat, 11L, now, 0L))
        assertTrue(p.pickMode(chat, PickWatchMode.OFF))
        assertFalse(p.acceptPick(chat, 12L, now, 0L))
        assertTrue(p.pickMode(chat, PickWatchMode.RESELLER, 12L))
        assertFalse(p.acceptPick(chat, 12L, now, 0L))
        assertTrue(p.acceptPick(chat, 13L, now, 0L))
    }

    @Test fun isolatedByAccountAndChatId() {
        val now = System.currentTimeMillis() / 1000L
        val p = Organization(context, account)
        assertTrue(p.pickMode(chat, PickWatchMode.RESELLER, 10L))
        assertEquals(PickWatchMode.OFF, Organization(context, account + 1).pickMode(chat))
        assertEquals(PickWatchMode.OFF, p.pickMode(chat - 1))
        assertTrue(p.acceptPick(chat, 11L, now, 0L))
        assertFalse(p.acceptPick(chat - 1, 11L, now, 0L))
    }
}
