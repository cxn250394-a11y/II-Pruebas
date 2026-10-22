package mx.charly.canal

import android.Manifest
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
/** These constructor-denial assertions belong only to the offline product flavor. */
class OfflineBuildIsolationTest {
    @Test fun candidateHasSeparateDataAndNoInternetPermission() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals("mx.charly.canal.offline", app.packageName)
        assertEquals(PackageManager.PERMISSION_DENIED,
            app.packageManager.checkPermission(Manifest.permission.INTERNET, app.packageName))
        assertTrue(BuildConfig.OFFLINE_REHEARSAL_ONLY)
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
    }

    @Test fun noSessionCanBeConstructedByTheOfflineCandidate() {
        assertNull(TelegramSession.current)
        val error = assertThrows(IllegalStateException::class.java) {
            TelegramSession.start(RuntimeEnvironment.getApplication())
        }
        assertEquals("OFFLINE_REHEARSAL_ONLY", error.message)
        assertNull(TelegramSession.current)
    }
}
