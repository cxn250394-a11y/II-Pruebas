package mx.charly.canal

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28])
class AsyncOfflineDemoTest {
    private class FixtureContext(base: Context) : ContextWrapper(base) {
        private val prefix = "offline-demo-test-${UUID.randomUUID()}-"
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
    @Before fun setup() { context = FixtureContext(RuntimeEnvironment.getApplication()) }
    @After fun cleanup() {
        context.deleteDatabase(AsyncOfflineDemo.DATABASE_NAME)
        context.getSharedPreferences(AsyncOfflineDemo.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun ownerStepsUseAsyncEngineAndDoNotAdvanceOnQuickAckOrOnePhoto() {
        AsyncOfflineDemo(context).use { demo ->
            assertEquals(AsyncOfflineDemo.Step.IDLE, demo.step)
            assertTrue(demo.newScenario())
            assertEquals(AsyncOfflineDemo.Step.PARTIAL_HOLD, demo.step)
            assertNull(demo.snapshot)
            assertEquals(0, demo.requestCount)
            assertTrue(demo.advance())
            assertEquals(AsyncRelayPhase.HEADER_WAITING, demo.snapshot!!.phase)
            assertEquals(1, demo.requestCount)
            val version = demo.snapshot!!.version
            assertTrue(demo.advance())
            assertEquals(AsyncOfflineDemo.Step.QUICK_ACK_WAITING, demo.step)
            assertEquals(version, demo.snapshot!!.version)
            assertEquals(1, demo.requestCount)
            assertTrue(demo.advance())
            assertEquals(AsyncRelayPhase.MEDIA_WAITING, demo.snapshot!!.phase)
            assertEquals(2, demo.requestCount)
            assertEquals(0, demo.confirmedPhotos)
            assertTrue(demo.advance())
            assertEquals(AsyncRelayPhase.MEDIA_WAITING, demo.snapshot!!.phase)
            assertEquals(1, demo.confirmedPhotos)
            assertTrue(demo.advance())
            assertEquals(AsyncRelayPhase.COMPLETE, demo.snapshot!!.phase)
            assertEquals(2, demo.confirmedPhotos)
            assertFalse(demo.advance())
        }
        assertNull(TelegramSession.current)
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
    }

    @Test fun pendingSurvivesActualControllerCloseAsUnknownWithoutRetry() {
        val key = AsyncOfflineDemo(context).use { demo ->
            assertTrue(demo.newScenario()); assertTrue(demo.advance())
            demo.snapshot!!.key
        }
        AsyncOfflineDemo(context).use { reopened ->
            assertEquals(key, reopened.snapshot!!.key)
            assertEquals(AsyncOfflineDemo.Step.UNKNOWN, reopened.step)
            assertEquals(1, reopened.requestCount)
            assertFalse(reopened.advance())
            reopened.reopen()
            assertEquals(AsyncRelayPhase.UNKNOWN, reopened.snapshot!!.phase)
            assertEquals(0, reopened.newRequestsAfterReopen)
            assertFalse(reopened.advance())
            assertTrue(reopened.newScenario()); assertTrue(reopened.advance())
            assertNotEquals(key, reopened.snapshot!!.key)
            AsyncRelayDatabase(context, AsyncOfflineDemo.DATABASE_NAME).use { db ->
                assertEquals(AsyncRelayPhase.UNKNOWN, db.read(key)!!.phase)
            }
        }
    }

    @Test fun explicitCutAndCompleteStayTerminalAcrossReopen() {
        AsyncOfflineDemo(context).use { demo ->
            assertTrue(demo.newScenario()); assertTrue(demo.advance()); assertTrue(demo.advance()); assertTrue(demo.advance())
            demo.stopScenario()
            val unknown = demo.snapshot!!
            assertEquals(AsyncRelayPhase.UNKNOWN, unknown.phase)
            demo.reopen()
            assertEquals(unknown, demo.snapshot)
            assertEquals(0, demo.newRequestsAfterReopen)
            assertTrue(demo.newScenario())
            repeat(5) { assertTrue(demo.advance()) }
            val complete = demo.snapshot!!
            demo.reopen()
            assertEquals(complete, demo.snapshot)
            assertEquals(AsyncOfflineDemo.Step.COMPLETE, demo.step)
            assertEquals(0, demo.newRequestsAfterReopen)
        }
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun click(activity: AsyncOfflineDemoActivity, label: String) = views(activity.window.decorView)
        .filterIsInstance<Button>().first { it.text.toString() == label }.performClick()
    private fun hasText(activity: AsyncOfflineDemoActivity, text: String) = views(activity.window.decorView)
        .filterIsInstance<TextView>().any { it.text.toString().contains(text) }

    @Test fun homeRoutesToPrivateDemoWithoutCredentialsAndActivityCanRehearse() {
        val home = Robolectric.buildActivity(CanalActivity::class.java).create()
        try {
            views(home.get().window.decorView).filterIsInstance<Button>()
                .first { it.text.toString() == "Ensayo offline · sin cuenta" }.performClick()
            val intent = shadowOf(home.get()).nextStartedActivity
            assertEquals(AsyncOfflineDemoActivity::class.java.name, intent.component!!.className)
            @Suppress("DEPRECATION")
            assertFalse(home.get().packageManager.getActivityInfo(intent.component!!, 0).exported)
        } finally { home.destroy() }
        val screen = Robolectric.buildActivity(AsyncOfflineDemoActivity::class.java).create()
        try {
            val activity = screen.get()
            assertTrue(hasText(activity, "Picks ficticios · 0 envíos reales"))
            click(activity, "Nuevo ensayo ficticio")
            assertTrue(hasText(activity, "Álbum incompleto · retenido"))
            click(activity, "Completar álbum ficticio")
            click(activity, "Simular acuse rápido")
            assertTrue(hasText(activity, "Acuse rápido recibido · sigue pendiente"))
            click(activity, "Confirmar encabezado ficticio")
            click(activity, "Confirmar primera foto ficticia")
            assertTrue(hasText(activity, "1 de 2 fotos confirmadas · sigue pendiente"))
            click(activity, "Simular corte · conservar incierto")
            click(activity, "Comprobar reapertura · sin reintentar")
            assertTrue(hasText(activity, "UNKNOWN / incierto · no se reintenta"))
            assertTrue(hasText(activity, "nuevas solicitudes: 0"))
            assertNull(TelegramSession.current)
        } finally { screen.destroy() }
    }
}
