package mx.charly.canal

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic IDs only; no login, second TDLib client or Telegram output operation. */
@RunWith(AndroidJUnit4::class)
class RelaySettingsDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun privateOfflineSettingsCannotBootstrapAnAccountOrEnableRelay() {
        assertNull(TelegramSession.current)
        val activity = instrumentation.startActivitySync(
            Intent(context, RelaySettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as RelaySettingsActivity
        try {
            instrumentation.runOnMainSync {
                val all = views(activity.window.decorView)
                assertTrue(all.filterIsInstance<TextView>().any { it.text.toString().contains("salida real OFF") })
                assertFalse(all.filterIsInstance<Button>().any { it.text.toString().contains("Elegir mi canal") })
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                assertNull(TelegramSession.current)
                assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun selectedIdentitySurvivesReopenButDoesNotFollowAMatchingNameOrAccount() {
        val account = 980404L
        val prefs = RelayPreferences(context, account)
        try {
            val rows = listOf(
                RelayLoadedChat(-9901L, "Mismo nombre sintético", 10L),
                RelayLoadedChat(-9902L, "Mismo nombre sintético", 20L),
                RelayLoadedChat(-9903L, "Destino sintético", 30L)
            )
            val selection = RelaySettingsSelection(prefs)
            assertTrue(selection.selectDestination(account, rows, -9903L))
            assertTrue(selection.selectSource(account, rows, -9901L, RelaySourcePolicy.UNICOREY_MEX, 1000L))
            assertFalse(selection.selectSource(account + 1L, rows, -9902L, RelaySourcePolicy.UNICOREY_FOREIGN, 1000L))
            val reopened = RelayPreferences(context, account)
            assertEquals(RelaySourcePolicy.UNICOREY_MEX, reopened.sourcePolicy(-9901L))
            assertNull(reopened.sourcePolicy(-9902L))
            assertEquals(10L, reopened.sourceBaseline(-9901L))
            assertFalse(reopened.enabled)
            assertTrue(reopened.stop())
            assertEquals(-9903L, RelayPreferences(context, account).destinationChatId())
        } finally {
            context.getSharedPreferences(RelayPreferences.fileName(account), Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
