package mx.charly.canal

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Android SQLite, synthetic identities and deferred Fake links; never creates a Telegram client. */
@RunWith(AndroidJUnit4::class)
class LocalPickDeliveryDeviceTest {
    /** Isolate all fixture databases/preferences even if a test package is installed beside existing data. */
    private class FixtureContext(base: Context) : ContextWrapper(base) {
        private val prefix = "local-pick-test-${UUID.randomUUID()}-"
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = baseContext.getSharedPreferences(prefix + name, mode)
        override fun getDatabasePath(name: String): File = baseContext.getDatabasePath(prefix + name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(prefix + name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
            baseContext.openOrCreateDatabase(prefix + name, mode, factory, handler)
        override fun deleteDatabase(name: String): Boolean = baseContext.deleteDatabase(prefix + name)
    }
    private lateinit var context: FixtureContext
    private val account = 988004L
    private val chat = -900004L
    private val date get() = System.currentTimeMillis() / 1000L + 10L
    @Before fun setup() { context = FixtureContext(InstrumentationRegistry.getInstrumentation().targetContext) }
    @After fun cleanup() {
        context.deleteDatabase(LocalPickDeliveryStore.FILE_NAME)
        context.getSharedPreferences("canaldeck-org-$account", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private inner class FakeBoundary {
        val organization = Organization(context, account)
        private val epoch = Any()
        var allowed = true
        var uncertain = false
        val links = linkedMapOf<Long, (String?) -> Unit>()
        val notices = mutableListOf<Long>()
        init { assertTrue(organization.pickMode(chat, PickWatchMode.DIRECT, 100L)) }
        fun submit(id: Long, album: Long = 0L) = LocalPickDispatcher(organization).submit(
            LocalPickEnvelope(chat, id, date, album, PickWatchMode.DIRECT, null, PickReference("Canal: Fixture sintética", date)),
            { LocalPickGuard(account, epoch, true, organization.pickMode(chat), null, organization.noticeRevision(), 0L,
                organization.pickEpoch(chat), allowed, false, true, true) },
            { links[id] = it }
        ) { _, _, reserve -> LocalPickPublicationBoundary.post({ allowed }, reserve) {
            if (uncertain) error("Fixture interrupted local notification")
            notices.add(id)
        } }
        fun complete(id: Long) { requireNotNull(links[id])(null) }
    }

    @Test fun reversedCallbacksAndInterleavedAlbumsRemainDistinctAcrossReopen() {
        val fake = FakeBoundary()
        assertTrue(fake.submit(101L, 74L))
        assertTrue(fake.submit(102L, 75L))
        assertTrue(fake.submit(104L))
        fake.complete(102L); fake.complete(104L); fake.complete(101L)
        assertEquals(listOf(102L, 104L, 101L), fake.notices)
        val reopened = Organization(context, account)
        assertFalse(reopened.acceptPick(chat, 103L, date, 74L))
        assertFalse(reopened.acceptPick(chat, 106L, date, 75L))
        assertTrue(reopened.acceptPick(chat, 105L, date, 0L))
        assertEquals(100L, reopened.pickBaseline(chat))
    }

    @Test fun blockedNotificationsNeverReserveOrCreateAnAutomaticBacklog() {
        val fake = FakeBoundary()
        fake.allowed = false
        assertFalse(fake.submit(101L))
        assertTrue(fake.links.isEmpty())
        assertNull(LocalPickDeliveryStore(context).use { it.state(account, chat, 101L, 0L) })
        fake.allowed = true
        assertTrue(fake.notices.isEmpty())
        assertTrue(fake.submit(102L)); fake.complete(102L)
        assertEquals(listOf(102L), fake.notices)
        assertNull(LocalPickDeliveryStore(context).use { it.state(account, chat, 101L, 0L) })
    }

    @Test fun offThenOnBeforeCallbackDoesNotReuseOldConsent() {
        val fake = FakeBoundary()
        assertTrue(fake.submit(101L))
        assertTrue(fake.organization.pickMode(chat, PickWatchMode.OFF))
        assertTrue(fake.organization.pickMode(chat, PickWatchMode.DIRECT, 100L))
        fake.complete(101L)
        assertTrue(fake.notices.isEmpty())
        assertNull(LocalPickDeliveryStore(context).use { it.state(account, chat, 101L, 0L) })
    }

    @Test fun uncertainAttemptPersistsWithoutAnAutomaticRetry() {
        val fake = FakeBoundary()
        assertTrue(fake.submit(101L))
        fake.uncertain = true; fake.complete(101L)
        assertEquals(LocalPickDeliveryState.UNKNOWN, LocalPickDeliveryStore(context).use { it.state(account, chat, 101L, 0L) })
        assertFalse(Organization(context, account).canAcceptPick(chat, 101L, date, 0L))
        fake.complete(101L)
        assertTrue(fake.notices.isEmpty())
    }
}
