package mx.charly.canal

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import java.util.UUID

/** Emulator CI only: both debug APKs installed; no account/session/client or real outputs. */
@RunWith(AndroidJUnit4::class)
class VariantIsolationDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val offline get() = BuildConfig.OFFLINE_REHEARSAL_ONLY
    private val expectedPackage get() = if (offline) "mx.charly.canal.offline" else "mx.charly.canal"
    private val siblingPackage get() = if (offline) "mx.charly.canal" else "mx.charly.canal.offline"
    private val internetGranted get() = context.packageManager.checkPermission(
        Manifest.permission.INTERNET, context.packageName) == PackageManager.PERMISSION_GRANTED
    @Suppress("DEPRECATION")
    private fun sibling(): ApplicationInfo = context.packageManager.getApplicationInfo(siblingPackage, 0)

    @Suppress("DEPRECATION")
    @Test fun packagePermissionsAndHomeMatchFlavorWithoutConnecting() {
        assertEquals(expectedPackage, context.packageName)
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        assertEquals(!offline, internetGranted)
        assertEquals(if (offline) "CanalDeck Offline" else "CanalDeck",
            context.packageManager.getApplicationLabel(context.applicationInfo).toString())
        assertNull(TelegramSession.current)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context, CanalActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CanalActivity
        try {
            instrumentation.runOnMainSync {
                val buttons = views(activity.window.decorView).filterIsInstance<Button>().map { it.text.toString() }
                assertEquals(offline, buttons.any { it.contains("Ensayo offline") })
                assertEquals(!offline, buttons.any { it.contains("conectar mi cuenta") })
                assertNull(TelegramSession.current)
            }
            if (offline) assertFalse(context.packageManager.getActivityInfo(
                ComponentName(context, AsyncOfflineDemoActivity::class.java), 0).exported)
            else assertThrows(PackageManager.NameNotFoundException::class.java) {
                context.packageManager.getActivityInfo(ComponentName(context, AsyncOfflineDemoActivity::class.java), 0)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun coinstalledVariantsHaveDistinctUidAndPrivateDataRoots() {
        val other = sibling()
        assertEquals(context.applicationInfo.uid, Process.myUid())
        assertNotEquals(context.applicationInfo.uid, other.uid)
        assertNotEquals(context.applicationInfo.dataDir, other.dataDir)
        assertTrue(context.filesDir.absolutePath.startsWith(context.applicationInfo.dataDir + "/"))
        assertFalse(File(other.dataDir).canRead())
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(0, other.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertNull(TelegramSession.current)
    }

    @Test fun privateFixturesRoundTripWithoutCrossingIntoSiblingOrStartingSession() {
        val other = sibling()
        assertNotEquals(context.applicationInfo.uid, other.uid)
        assertNotEquals(context.applicationInfo.dataDir, other.dataDir)
        val token = "qa-variant-" + UUID.randomUUID()
        val prefs = context.getSharedPreferences(token, Context.MODE_PRIVATE)
        val databaseName = "$token.db"
        val file = File(context.filesDir, "$token.txt")
        val vaultName = "$token-vault"
        try {
            assertNull(prefs.getString("fixture", null))
            assertTrue(prefs.edit().putString("fixture", context.packageName).commit())
            assertEquals(context.packageName, prefs.getString("fixture", null))
            val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
            try {
                db.execSQL("CREATE TABLE fixture (value TEXT NOT NULL)")
                db.execSQL("INSERT INTO fixture(value) VALUES (?)", arrayOf(context.packageName))
                db.rawQuery("SELECT value FROM fixture", null).use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(context.packageName, cursor.getString(0))
                }
            } finally { db.close() }
            assertTrue(context.getDatabasePath(databaseName).absolutePath.startsWith(context.applicationInfo.dataDir + "/"))
            file.writeText(context.packageName)
            assertEquals(context.packageName, file.readText())
            assertFalse(File(other.dataDir, "files/${file.name}").canRead())
            val vault = Vault(context)
            vault.put(vaultName, "synthetic fixture: ${context.packageName}")
            assertEquals("synthetic fixture: ${context.packageName}", vault.get(vaultName))
            assertFalse(File(other.dataDir, "shared_prefs/vault.xml").canRead())
            assertFalse(File(other.dataDir, "databases/$databaseName").canRead())
            assertEquals(!offline, internetGranted)
            assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
            assertNull(TelegramSession.current)
            File(context.filesDir, "variant-isolation-evidence.json").writeText(JSONObject()
                .put("schema_version", 1).put("runtime", "Android installed product flavors")
                .put("application_id", context.packageName).put("sibling_application_id", siblingPackage)
                .put("offline_rehearsal_only", offline).put("internet_permission", internetGranted)
                .put("compiled_real_output_enabled", BuildConfig.PICK_RELAY_REAL_ENABLED)
                .put("different_uid", context.applicationInfo.uid != other.uid)
                .put("different_data_dir", context.applicationInfo.dataDir != other.dataDir)
                .put("sibling_private_dir_readable", File(other.dataDir).canRead())
                .put("own_preferences_round_trip", true).put("own_sqlite_round_trip", true)
                .put("own_file_round_trip", true).put("own_vault_round_trip", true)
                .put("account_connected", TelegramSession.current != null)
                .put("sent_real_messages", 0).toString(2) + "\n")
        } finally {
            prefs.edit().clear().commit()
            context.deleteDatabase(databaseName)
            file.delete()
            context.getSharedPreferences("vault", Context.MODE_PRIVATE).edit().remove(vaultName).commit()
        }
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
}
