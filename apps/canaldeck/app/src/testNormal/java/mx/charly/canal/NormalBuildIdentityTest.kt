package mx.charly.canal

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
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

/** No session construction, account identity, credentials or SDK operations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NormalBuildIdentityTest {
    @Test fun principalIdentityAndInternetAreRestoredWithRelayStillCompiledOff() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals("mx.charly.canal", app.packageName)
        assertFalse(BuildConfig.OFFLINE_REHEARSAL_ONLY)
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
        assertEquals(PackageManager.PERMISSION_GRANTED,
            app.packageManager.checkPermission(Manifest.permission.INTERNET, app.packageName))
        assertEquals("CanalDeck", app.packageManager.getApplicationLabel(app.applicationInfo).toString())
        assertNull(TelegramSession.current)
    }

    @Test fun principalManifestDoesNotRegisterTheLaboratoryActivity() {
        val app = RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        val error = assertThrows(PackageManager.NameNotFoundException::class.java) {
            app.packageManager.getActivityInfo(ComponentName(app, AsyncOfflineDemoActivity::class.java), 0)
        }
        assertNotNull(error)
        assertNull(TelegramSession.current)
    }

    @Test fun principalHomeShowsAccountEntryWithoutOpeningAnAccountOrTheLab() {
        val screen = Robolectric.buildActivity(CanalActivity::class.java).create()
        try {
            val labels = views(screen.get().window.decorView).filterIsInstance<Button>().map { it.text.toString() }
            assertTrue(labels.any { it.contains("conectar mi cuenta") })
            assertFalse(labels.any { it.contains("Ensayo offline") })
            assertNull(TelegramSession.current)
        } finally { screen.destroy() }
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
}
