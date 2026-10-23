package mx.charly.canal

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** CI emulator, UUID-owned synthetic stores only. Never creates a client, connects or sends. */
@RunWith(AndroidJUnit4::class)
class CompiledRelayOffDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private class Probe : AutoCloseable {
        var operations = 0
        var closes = 0
        override fun close() { closes++ }
    }

    @Test fun compiledOffSkipsEveryFactoryAndContextAccess() {
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        assertNull(TelegramSession.current)
        var accesses = 0
        var factories = 0
        val forbidden = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context { accesses++; error("OFF touched Context") }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                accesses++; error("OFF touched preferences")
            }
        }
        repeat(4) {
            val lane = CompiledRelayLane {
                factories++
                RelayPreferences(forbidden, 1L)
                RelayDatabase(forbidden, "must-not-be-created.db")
            }
            repeat(8) { lane.with { fail("OFF operated metadata") } }
            lane.close(); lane.close()
        }
        assertEquals(0, accesses)
        assertEquals(0, factories)
        val neverConstruct = CompiledRelayLane<Probe> { error("OFF shutdown constructed a bridge") }
        neverConstruct.close()
        neverConstruct.with { fail("OFF executed an event") }
    }

    /** seed/verify are invoked in separate instrumentation processes with am force-stop between them. */
    @Test fun restartKeepsExistingMetadataUntouched() {
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        assertNull(TelegramSession.current)
        val phase = InstrumentationRegistry.getArguments().getString("relay_off_restart_phase").orEmpty()
        require(phase in listOf("", "seed", "verify"))
        val manifestFile = File(context.filesDir, "compiled-relay-off-restart-fixture.json")
        var seedPid = Process.myPid()
        val fixture: Fixture
        val before: Map<String, String>
        if (phase == "verify") {
            val manifest = JSONObject(manifestFile.readText())
            assertEquals(context.packageName, manifest.getString("application_id"))
            fixture = Fixture(context, manifest.getString("prefix"), manifest.getLong("account"))
            seedPid = manifest.getInt("seed_pid")
            assertNotEquals("The runner must restart the target process", seedPid, Process.myPid())
            before = manifest.getJSONObject("hashes").let { hashes ->
                hashes.keys().asSequence().associateWith { hashes.getString(it) }
            }
            assertEquals(before, fixture.hashes())
        } else {
            fixture = Fixture(context)
            fixture.seed()
            before = fixture.hashes()
        }
        assertEquals("The preference XML and both closed SQLite files must exist", fixture.expectedFileNames(), before.keys)
        var completed = false
        try {
            var factories = 0
            repeat(3) {
                val lane = CompiledRelayLane {
                    factories++
                    fixture.routing().stop()
                    RelayDatabase(fixture.context, "observations.db").apply { recoverPending(fixture.account) }
                }
                repeat(4) { lane.with { it.checkpoint(fixture.account, fixture.source, 999L, 9999L) } }
                lane.close()
                assertDisabledSettings()
            }
            assertEquals(0, factories)
            assertEquals(before, fixture.hashes())
            fixture.assertLogicalPreservation()
            // Reading the fixtures after the equality check must preserve their exact bytes too.
            assertEquals(before, fixture.hashes())
            if (phase == "seed") {
                manifestFile.writeText(JSONObject().put("schema_version", 1)
                    .put("application_id", context.packageName).put("prefix", fixture.prefix)
                    .put("account", fixture.account).put("seed_pid", seedPid)
                    .put("hashes", JSONObject(before)).toString(2) + "\n")
            }
            File(context.filesDir, "compiled-relay-off-evidence.json").writeText(JSONObject()
                .put("schema_version", 1).put("runtime", "Android installed flavor; synthetic fixtures")
                .put("application_id", context.packageName).put("phase", phase.ifEmpty { "recreated_lanes" })
                .put("seed_pid", seedPid).put("current_pid", Process.myPid())
                .put("offline_rehearsal_only", BuildConfig.OFFLINE_REHEARSAL_ONLY)
                .put("compiled_real_output_enabled", BuildConfig.PICK_RELAY_REAL_ENABLED)
                .put("factory_calls", factories).put("recreated_lifecycles", 3)
                .put("unchanged_preferences_and_sqlite", true).put("preserved_file_count", before.size)
                .put("pending_observation_preserved", true).put("pending_ack_preserved", true)
                .put("unknown_observation_preserved", true)
                .put("enabled_photo_consent_and_recovery_pause_preserved", true)
                .put("settings_mutators_absent", true)
                .put("process_restart_verified", phase == "verify")
                .put("account_connected", TelegramSession.current != null).put("sent_real_messages", 0)
                .toString(2) + "\n")
            assertNull(TelegramSession.current)
            completed = true
        } finally {
            if (phase != "seed" || !completed) {
                fixture.cleanup()
                if (phase == "verify") manifestFile.delete()
            }
        }
    }

    @Test fun localPickNoticeStillCompletesWithoutBridgeOrNativeClient() {
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        assertNull(TelegramSession.current)
        val fixture = Fixture(context)
        try {
            fixture.seed()
            val before = fixture.hashes()
            val org = Organization(fixture.context, fixture.account)
            assertTrue(org.pickMode(fixture.source, PickWatchMode.DIRECT, 100L))
            val routing = fixture.routing()
            val epoch = Any()
            val date = System.currentTimeMillis() / 1000L + 10L
            var factories = 0
            val lane = CompiledRelayLane<Probe> { factories++; error("local notice initialized a bridge") }
            var callback: ((String?) -> Unit)? = null
            var notices = 0
            assertTrue(LocalPickDispatcher(org).submit(LocalPickEnvelope(fixture.source, 101L, date, 0L,
                PickWatchMode.DIRECT, routing.sourcePolicy(fixture.source), PickReference("Canal: Fuente sintética", date)), {
                LocalPickGuard(fixture.account, epoch, true, org.pickMode(fixture.source),
                    routing.sourcePolicy(fixture.source), org.noticeRevision(), routing.revision(),
                    org.pickEpoch(fixture.source), true, false, true, true)
            }, { callback = it }) { _, _, reserve ->
                lane.with { fail("OFF relay callback executed") }
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

    private fun assertDisabledSettings() {
        val activity = instrumentation.startActivitySync(Intent(context, RelaySettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RelaySettingsActivity
        try {
            instrumentation.runOnMainSync {
                val buttons = views(activity.window.decorView).filterIsInstance<Button>().map { it.text.toString() }
                assertFalse(buttons.any { it.contains("Elegir") || it.contains("Quitar") || it.contains("STOP") })
                assertNull(TelegramSession.current)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync() }
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    private class Fixture(private val base: Context,
                          val prefix: String = "relay-off-device-${UUID.randomUUID()}-",
                          val account: Long = (UUID.randomUUID().leastSignificantBits and ((1L shl 50) - 1L)) + 1L) {
        init { require(prefix.matches(Regex("relay-off-device-[a-f0-9-]{36}-")) && account > 0L) }
        val source = -45201L
        private val destination = -45202L
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
        private val key get() = RelayKey(account, source, "m:101")
        fun routing() = RelayPreferences(context, account)
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
            assertTrue(p.enabled); assertTrue(p.singlePhotoOnlyConsent(source)); assertNotNull(p.recoveryPause())
            assertEquals(100L, p.sourceBaseline(source)); assertEquals(1000L, p.enabledAt(source))
            RelayDatabase(context, "observations.db").use {
                assertEquals(RelayBaseline(100L, 1000L), it.baseline(account, source))
                assertEquals(RelayDeliveryState.PENDING, it.state(key))
                assertEquals(RelayDeliveryState.UNKNOWN, it.state(key.copy(groupId = "m:102")))
            }
            AsyncRelayDatabase(context, "ack.db").use { assertEquals(AsyncRelayPhase.HEADER_WAITING, it.read(key)?.phase) }
        }
        private fun files(): List<File> = listOf(File(base.applicationInfo.dataDir, "shared_prefs"), File(base.applicationInfo.dataDir, "databases"))
            .flatMap { it.listFiles().orEmpty().toList() }.filter { it.name.startsWith(prefix) }.sortedBy { it.path }
        fun expectedFileNames(): Set<String> = setOf(prefix + RelayPreferences.fileName(account) + ".xml",
            prefix + "observations.db", prefix + "ack.db")
        fun hashes(): Map<String, String> = files().filter { file ->
            file.name.startsWith(prefix + RelayPreferences.fileName(account) + ".xml") ||
                file.name.startsWith(prefix + "observations.db") || file.name.startsWith(prefix + "ack.db")
        }.associate { file -> file.name to MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) } }
        fun cleanup() {
            (names + RelayPreferences.fileName(account) + "canaldeck-org-$account").forEach {
                base.getSharedPreferences(prefix + it, Context.MODE_PRIVATE).edit().clear().commit()
            }
            base.deleteDatabase(prefix + "observations.db"); base.deleteDatabase(prefix + "ack.db")
            base.deleteDatabase(prefix + LocalPickDeliveryStore.FILE_NAME)
            files().forEach { it.delete() }
        }
    }
}
