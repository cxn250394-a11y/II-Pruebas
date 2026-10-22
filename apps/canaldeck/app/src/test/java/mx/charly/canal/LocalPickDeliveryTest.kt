package mx.charly.canal

import android.content.Context
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LocalPickDeliveryTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val account = 981041L
    private val chat = -90041L

    @Before @After fun clean() {
        context.getSharedPreferences("canaldeck-org-$account", Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteDatabase(LocalPickDeliveryStore.FILE_NAME)
    }

    @Test fun reversedLinkCallbacksDoNotLoseTheLowerValidMessageId() {
        val organization = Organization(context, account)
        val date = System.currentTimeMillis() / 1000L + 10L
        assertTrue(organization.pickMode(chat, PickWatchMode.DIRECT, 100L))
        assertTrue(organization.acceptPick(chat, 102L, date, 0L))
        assertTrue("The callback for 101 may complete after 102", organization.acceptPick(chat, 101L, date, 0L))
        assertFalse(Organization(context, account).acceptPick(chat, 101L, date, 0L))
        assertFalse(Organization(context, account).acceptPick(chat, 102L, date, 0L))
    }

    @Test fun interleavedAlbumFragmentsCannotRenotifyTheSameAlbum() {
        val organization = Organization(context, account)
        val date = System.currentTimeMillis() / 1000L + 10L
        assertTrue(organization.pickMode(chat, PickWatchMode.DIRECT, 100L))
        assertTrue(organization.acceptPick(chat, 101L, date, 74L))
        assertTrue(organization.acceptPick(chat, 102L, date, 75L))
        assertFalse("A second fragment of album74 stays duplicate after album75", organization.acceptPick(chat, 103L, date, 74L))
        assertTrue(organization.acceptPick(chat, 104L, date, 0L))
    }

    private inner class Harness {
        val organization = Organization(context, account)
        val date = System.currentTimeMillis() / 1000L + 10L
        var epoch = Any()
        var liveAccount = account
        var live = true
        var permission = true
        var silent = false
        var unread = true
        var unchanged = true
        var sourceRevision = 0L
        var postAllowed = true
        var throwsAfterAttempt = false
        val callbacks = linkedMapOf<Long, (String?) -> Unit>()
        val posted = mutableListOf<Long>()
        init { assertTrue(organization.pickMode(chat, PickWatchMode.DIRECT, 100L)) }
        fun enqueue(id: Long, album: Long = 0L): Boolean {
            val reference = PickReference("Canal: Fuente sintética", date)
            val envelope = LocalPickEnvelope(chat, id, date, album, PickWatchMode.DIRECT, null, reference)
            return LocalPickDispatcher(organization).submit(envelope, {
                LocalPickGuard(liveAccount, epoch, live, organization.pickMode(chat), null,
                    organization.noticeRevision(), sourceRevision, organization.pickEpoch(chat), permission, silent, unread, unchanged)
            }, { callback -> callbacks[id] = callback }) { _, _, reserve ->
                LocalPickPublicationBoundary.post({ permission && postAllowed && unread && !silent }, reserve) {
                    if (throwsAfterAttempt) error("Synthetic notification boundary interrupted")
                    posted.add(id)
                }
            }
        }
        fun complete(id: Long) { requireNotNull(callbacks[id])(null) }
        fun state(id: Long, album: Long = 0L) = LocalPickDeliveryStore(context).use { it.state(account, chat, id, album) }
    }

    @Test fun realDispatcherHandlesAllThreeCallbackOrdersOnceWithoutMovingActivationBaseline() {
        val orders = listOf(listOf(101L, 102L, 103L), listOf(103L, 101L, 102L), listOf(102L, 103L, 101L))
        orders.forEach { order ->
            clean()
            val h = Harness()
            order.sorted().forEach { assertTrue(h.enqueue(it)) }
            order.forEach { h.complete(it) }
            assertEquals(order, h.posted)
            assertEquals(100L, h.organization.pickBaseline(chat))
            order.forEach { h.complete(it) }
            assertEquals(3, h.posted.size)
        }
    }

    @Test fun firstLivePickAfterActivationAndAfterReconnectIsNotConsumedByALazyLatestId() {
        val h = Harness()
        assertTrue(h.enqueue(101L))
        h.complete(101L)
        h.epoch = Any() // A new connection epoch invalidates pending work, not the owner's fixed baseline.
        assertTrue(h.enqueue(102L))
        h.complete(102L)
        assertEquals(listOf(101L, 102L), h.posted)
        assertEquals(100L, h.organization.pickBaseline(chat))
    }

    @Test fun permissionOffNeverRequestsALinkOrReservesAPickAndNoBlockedQueueExists() {
        val h = Harness()
        h.permission = false
        assertFalse(h.enqueue(101L))
        assertTrue(h.callbacks.isEmpty())
        assertNull(h.state(101L))
        assertEquals(100L, h.organization.pickBaseline(chat))
        h.permission = true
        assertTrue(h.posted.isEmpty()) // Changing permission alone cannot drain a backlog.
        assertTrue(h.enqueue(102L))
        h.complete(102L)
        assertEquals(listOf(102L), h.posted)
        assertNull(h.state(101L))
    }

    @Test fun revokedPermissionOrBlockedFinalBoundaryDoesNotConsumePendingCandidate() {
        val h = Harness()
        assertTrue(h.enqueue(101L))
        h.permission = false
        h.complete(101L)
        assertNull(h.state(101L))
        h.permission = true
        h.epoch = Any()
        assertTrue(h.enqueue(101L)) // A fresh explicit live attempt is possible: the blocked one was not consumed.
        h.postAllowed = false
        h.complete(101L)
        assertNull(h.state(101L))
        assertEquals(100L, h.organization.pickBaseline(chat))
        assertTrue(h.posted.isEmpty())
    }

    @Test fun globalPermissionDoesNotOverrideAnIndividuallyBlockedAndroidChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
        val id = "synthetic-local-picks-off"
        manager.createNotificationChannel(NotificationChannel(id, "Fixture", NotificationManager.IMPORTANCE_NONE))
        assertTrue(LocalNotificationAvailability.appAllowed(context, manager))
        assertFalse(LocalNotificationAvailability.allowed(context, manager, id))
        val h = Harness()
        h.permission = LocalNotificationAvailability.allowed(context, manager, id)
        assertFalse(h.enqueue(101L))
        assertNull(h.state(101L))
        assertTrue(h.posted.isEmpty())
    }

    @Test fun accountSessionQuietReadAndEditedOrDeletedChangesRejectPendingCallbacks() {
        val changes: List<(Harness) -> Unit> = listOf(
            { it.liveAccount = account + 1L }, { it.epoch = Any() }, { it.live = false },
            { it.silent = true }, { it.unread = false }, { it.unchanged = false }, { it.sourceRevision++ }
        )
        changes.forEach { change ->
            clean()
            val h = Harness()
            assertTrue(h.enqueue(101L))
            change(h)
            h.complete(101L)
            assertTrue(h.posted.isEmpty())
            assertNull(h.state(101L))
        }
    }

    @Test fun changingModeOffAndBackOrQuietOffAndBackInvalidatesTheOldConsent() {
        val h = Harness()
        assertTrue(h.enqueue(101L))
        assertTrue(h.organization.pickMode(chat, PickWatchMode.OFF))
        assertTrue(h.organization.pickMode(chat, PickWatchMode.DIRECT, 100L))
        h.complete(101L)
        assertNull(h.state(101L))
        assertTrue(h.enqueue(102L))
        h.organization.quietEnabled = true
        h.organization.quietEnabled = false
        h.complete(102L)
        assertNull(h.state(102L))
        assertTrue(h.posted.isEmpty())
    }

    @Test fun unsafeRevisionSentinelsFailClosedBeforeLinkOrReservation() {
        val h = Harness()
        h.sourceRevision = Long.MAX_VALUE
        assertFalse(h.enqueue(101L))
        assertTrue(h.callbacks.isEmpty())
        assertNull(h.state(101L))
    }

    @Test fun uncertainLocalAttemptSurvivesReopenAndCannotAutomaticallyRetry() {
        val h = Harness()
        assertTrue(h.enqueue(101L))
        h.throwsAfterAttempt = true
        h.complete(101L)
        assertEquals(LocalPickDeliveryState.UNKNOWN, h.state(101L))
        assertFalse(LocalPickDispatcher(Organization(context, account)).submit(
            LocalPickEnvelope(chat, 101L, h.date, 0L, PickWatchMode.DIRECT, null, PickReference("Canal: Fixture", h.date)),
            { LocalPickGuard(account, h.epoch, true, PickWatchMode.DIRECT, null, h.organization.noticeRevision(), 0L,
                h.organization.pickEpoch(chat), true, false, true, true) },
            { fail("No retry link request for UNKNOWN") }, { _, _, _ -> error("No retry publication") }))
    }

    @Test fun interleavedAlbumTombstonesAndTypedMessageIdsAreDurableAcrossReopen() {
        val org = Organization(context, account)
        val date = System.currentTimeMillis() / 1000L + 10L
        assertTrue(org.pickMode(chat, PickWatchMode.DIRECT, 10L))
        assertTrue(org.acceptPick(chat, 101L, date, 74L))
        assertTrue(org.acceptPick(chat, 102L, date, 75L))
        assertFalse(Organization(context, account).acceptPick(chat, 103L, date, 74L))
        assertTrue(org.acceptPick(chat, 74L, date, 0L)) // message74 is distinct from album74.
        assertFalse(org.acceptPick(chat, 101L, date, 0L)) // Same source-message alias cannot evade album dedup.
    }

    @Test fun upgradeKeepsTheLastLegacyAlbumAsAnUncertainTombstone() {
        val date = System.currentTimeMillis() / 1000L + 10L
        context.getSharedPreferences("canaldeck-org-$account", Context.MODE_PRIVATE).edit()
            .putInt("pick-mode-$chat", PickWatchMode.DIRECT.ordinal)
            .putLong("pick-enabled-at-$chat", date - 20L)
            .putLong("pick-last-id-$chat", 100L)
            .putLong("pick-last-album-$chat", 74L).commit()
        val org = Organization(context, account)
        assertFalse(org.acceptPick(chat, 101L, date, 74L))
        assertTrue(org.acceptPick(chat, 102L, date, 0L))
        assertEquals(LocalPickDeliveryState.UNKNOWN, LocalPickDeliveryStore(context).use { it.state(account, chat, 100L, 74L) })
    }
}
