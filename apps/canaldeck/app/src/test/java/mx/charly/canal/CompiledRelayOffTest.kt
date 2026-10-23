package mx.charly.canal

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** The actual production lane gate, Android stores and Fake local callbacks; no native TDLib client. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompiledRelayOffTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private class Probe : AutoCloseable {
        var operations = 0
        var closes = 0
        override fun close() { closes++ }
    }

    @Test fun offSkipsFactoryOperationsAndCloseAcrossRecreatedLanes() {
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        var factories = 0
        val probe = Probe()
        repeat(4) {
            val lane = CompiledRelayLane { factories++; probe }
            repeat(5) { lane.with { it.operations++ } }
            lane.close(); lane.close()
        }
        assertEquals(0, factories)
        assertEquals(0, probe.operations)
        assertEquals(0, probe.closes)
        assertNull(TelegramSession.current)
    }

    @Test fun throwingFactoryIsNeverInvokedByEventsOrShutdown() {
        var factories = 0
        val lane = CompiledRelayLane<Probe> { factories++; error("OFF must precede the factory") }
        lane.close()
        repeat(10) { lane.with { fail("OFF must precede the operation") } }
        lane.close()
        assertEquals(0, factories)
    }

    @Test fun offCannotAccessContextOrCreateFreshPreferenceAndDatabaseFiles() {
        val fixture = Fixture(context)
        var accesses = 0
        val forbidden = object : ContextWrapper(fixture.context) {
            override fun getApplicationContext(): Context { accesses++; error("application context accessed") }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                accesses++; error("preferences accessed")
            }
        }
        val before = fixture.files()
        try {
            repeat(3) {
                val lane = CompiledRelayLane {
                    RelayPreferences(forbidden, fixture.account)
                    RelayDatabase(forbidden, "observations.db")
                }
                lane.with { it.checkpoint(fixture.account, fixture.source, 999L, 9999L) }
                lane.close()
            }
            assertEquals(0, accesses)
            assertEquals(before, fixture.files())
            assertTrue(fixture.files().isEmpty())
        } finally { fixture.cleanup() }
    }

    @Test fun existingEnabledConsentCheckpointsAndPendingSqliteRemainByteIdentical() {
        val fixture = Fixture(context)
        try {
            fixture.seed()
            val before = fixture.hashes()
            val preferenceValues = fixture.preferences().all.mapValues { (_, value) ->
                if (value is Set<*>) value.toSet() else value
            }
            assertFalse(preferenceValues.isEmpty())
            listOf("observations.db", "ack.db").forEach { name ->
                val file = fixture.context.getDatabasePath(name)
                assertTrue("The seeded SQLite file must exist: $name", file.isFile)
                assertTrue(before.containsKey(file.name))
            }
            var factories = 0
            repeat(4) {
                val lane = CompiledRelayLane {
                    factories++
                    fixture.routing().stop()
                    RelayDatabase(fixture.context, "observations.db").apply { recoverPending(fixture.account) }
                }
                lane.with { it.checkpoint(fixture.account, fixture.source, 999L, 9999L) }
                lane.close()
            }
            assertEquals(0, factories)
            assertEquals(before, fixture.hashes())
            assertEquals(preferenceValues, fixture.preferences().all)
            fixture.assertLogicalPreservation()
        } finally { fixture.cleanup() }
    }

    @Test fun disabledSettingsLifecycleCannotExposeMutatorsOrChangeExistingData() {
        val fixture = Fixture(context)
        try {
            fixture.seed()
            val before = fixture.hashes()
            repeat(3) {
                val controller = Robolectric.buildActivity(RelaySettingsActivity::class.java).setup()
                try {
                    val buttons = views(controller.get().window.decorView).filterIsInstance<Button>().map { it.text.toString() }
                    assertFalse(buttons.any { it.contains("Elegir") || it.contains("Quitar") || it.contains("STOP") })
                    assertNull(TelegramSession.current)
                    controller.pause().stop().start().resume().visible()
                } finally { controller.pause().stop().destroy() }
            }
            assertEquals(before, fixture.hashes())
            fixture.assertLogicalPreservation()
        } finally { fixture.cleanup() }
    }

    @Test fun invalidExistingPreferenceTypesAndRecoveryJsonAreNeverReadOrRepairedByOffLane() {
        val fixture = Fixture(context)
        try {
            fixture.seed()
            val raw = fixture.preferences()
            assertTrue(raw.edit().putString("revision", "wrong-type")
                .putString("single-recovery-pause", "{malformed-json").commit())
            val before = fixture.hashes()
            val preferenceValues = raw.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
            var factories = 0
            repeat(3) {
                val lane = CompiledRelayLane {
                    factories++
                    fixture.routing().stop()
                    RelayDatabase(fixture.context, "observations.db")
                }
                lane.with { it.recoverPending(fixture.account) }
                lane.close()
                val controller = Robolectric.buildActivity(RelaySettingsActivity::class.java).setup()
                try {
                    val buttons = views(controller.get().window.decorView).filterIsInstance<Button>().map { it.text.toString() }
                    assertFalse(buttons.any { it.contains("Elegir") || it.contains("Quitar") || it.contains("STOP") })
                    assertNull(TelegramSession.current)
                } finally { controller.pause().stop().destroy() }
            }
            assertEquals(0, factories)
            assertEquals(preferenceValues, raw.all)
            assertEquals("wrong-type", raw.getString("revision", null))
            assertEquals("{malformed-json", raw.getString("single-recovery-pause", null))
            assertEquals(before, fixture.hashes())
        } finally { fixture.cleanup() }
    }

    @Test fun independentLocalPickDispatcherStillPublishesOnceWithOffBridge() {
        val fixture = Fixture(context)
        try {
            fixture.seed()
            val before = fixture.hashes()
            val org = Organization(fixture.context, fixture.account)
            assertTrue(org.pickMode(fixture.source, PickWatchMode.DIRECT, 100L))
            val epoch = Any()
            val date = System.currentTimeMillis() / 1000L + 10L
            val routing = fixture.routing()
            val policy = routing.sourcePolicy(fixture.source)
            var factories = 0
            val lane = CompiledRelayLane<Probe> { factories++; error("local notice must not build relay") }
            var callback: ((String?) -> Unit)? = null
            var notices = 0
            val envelope = LocalPickEnvelope(fixture.source, 101L, date, 0L,
                PickWatchMode.DIRECT, policy, PickReference("Canal: Fuente sintética", date))
            assertTrue(LocalPickDispatcher(org).submit(envelope, {
                LocalPickGuard(fixture.account, epoch, true, org.pickMode(fixture.source),
                    routing.sourcePolicy(fixture.source), org.noticeRevision(), routing.revision(),
                    org.pickEpoch(fixture.source), true, false, true, true)
            }, { callback = it }) { _, _, reserve ->
                lane.with { fail("relay callback must remain OFF") }
                LocalPickPublicationBoundary.post({ true }, reserve) { notices++ }
            })
            requireNotNull(callback)(null); requireNotNull(callback)(null)
            lane.close()
            assertEquals(1, notices)
            assertEquals(0, factories)
            assertEquals(before, fixture.hashes())
            assertNull(TelegramSession.current)
        } finally { fixture.cleanup() }
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private class Fixture(private val base: Context) {
        private val prefix = "relay-off-jvm-${UUID.randomUUID()}-"
        val account = (UUID.randomUUID().leastSignificantBits and ((1L shl 50) - 1L)) + 1L
        val source = -45101L
        private val destination = -45102L
        private val names = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                names += name
                return base.getSharedPreferences(prefix + name, mode)
            }
            override fun getDatabasePath(name: String): File = base.getDatabasePath(prefix + name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                base.openOrCreateDatabase(prefix + name, mode, factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
                base.openOrCreateDatabase(prefix + name, mode, factory, handler)
            override fun deleteDatabase(name: String): Boolean = base.deleteDatabase(prefix + name)
        }
        fun routing() = RelayPreferences(context, account)
        fun preferences(): SharedPreferences = context.getSharedPreferences(RelayPreferences.fileName(account), Context.MODE_PRIVATE)
        private val key get() = RelayKey(account, source, "m:101")
        fun seed() {
            val p = routing()
            check(p.selectDestination(destination))
            check(p.selectSourcePolicy(source, RelaySourcePolicy.FANO_DIRECT, 100L, 1000L))
            check(p.setSinglePhotoOnlyConsent(source, true)); check(p.changeEnabled(true))
            check(p.compareAndSetRecoveryPause(null, SingleRelayRecoveryToken(
                AsyncRelayScope(account, "before-restart", destination), p.revision(), "fixture-nonce", "fixture-client")))
            RelayDatabase(context, "observations.db").use {
                it.checkpoint(account, source, 100L, 1000L); check(it.claim(key))
                val unknown = key.copy(groupId = "m:102")
                check(it.claim(unknown)); it.finish(unknown, RelayDeliveryState.UNKNOWN)
            }
            AsyncRelayDatabase(context, "ack.db").use {
                check(it.create(AsyncRelaySnapshot(AsyncRelayIntent(key,
                    AsyncRelayScope(account, "before-restart", destination), listOf(101L), false, 1000L),
                    AsyncRelayPhase.HEADER_WAITING,
                    listOf(AsyncRelayRequest("fixture-header", AsyncRelayStage.HEADER, emptyList(), 31000L)))))
            }
        }
        fun assertLogicalPreservation() {
            val p = routing()
            assertTrue(p.enabled); assertTrue(p.singlePhotoOnlyConsent(source))
            assertEquals(100L, p.sourceBaseline(source)); assertEquals(1000L, p.enabledAt(source))
            assertNotNull(p.recoveryPause())
            RelayDatabase(context, "observations.db").use {
                assertEquals(RelayBaseline(100L, 1000L), it.baseline(account, source))
                assertEquals(RelayDeliveryState.PENDING, it.state(key))
                assertEquals(RelayDeliveryState.UNKNOWN, it.state(key.copy(groupId = "m:102")))
            }
            AsyncRelayDatabase(context, "ack.db").use { assertEquals(AsyncRelayPhase.HEADER_WAITING, it.read(key)?.phase) }
        }
        fun files(): List<File> = listOf(File(base.applicationInfo.dataDir, "shared_prefs"), File(base.applicationInfo.dataDir, "databases"))
            .flatMap { it.listFiles().orEmpty().toList() }.filter { it.name.startsWith(prefix) }.sortedBy { it.path }
        fun hashes(): Map<String, String> = files().filter { file ->
            file.name.startsWith(prefix + RelayPreferences.fileName(account) + ".xml") ||
                file.name.startsWith(prefix + "observations.db") || file.name.startsWith(prefix + "ack.db")
        }.associate { file -> file.name to
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) } }
        fun cleanup() {
            names.forEach { base.getSharedPreferences(prefix + it, Context.MODE_PRIVATE).edit().clear().commit() }
            base.deleteDatabase(prefix + "observations.db"); base.deleteDatabase(prefix + "ack.db")
            base.deleteDatabase(prefix + LocalPickDeliveryStore.FILE_NAME)
            files().forEach { it.delete() }
        }
    }
}
