package mx.charly.canal

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RelaySettingsSelectionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val account = 980004L
    private val preferences get() = RelayPreferences(context, account)
    private val rows = listOf(
        RelayLoadedChat(-7001L, "Mismo nombre", 100L),
        RelayLoadedChat(-7002L, "Mismo nombre", 200L),
        RelayLoadedChat(-7003L, "Destino sintético", 300L)
    )

    @After fun clean() {
        context.getSharedPreferences(RelayPreferences.fileName(account), Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun destinationMustBeLoadedInTheSameConnectedAccount() {
        val ui = RelaySettingsSelection(preferences)
        assertFalse(ui.selectDestination(account + 1L, rows, -7003L))
        assertFalse(ui.selectDestination(account, rows, -9999L))
        assertEquals(0L, preferences.destinationChatId())
        assertTrue(ui.selectDestination(account, rows, -7003L))
        assertEquals(-7003L, preferences.destinationChatId())
        assertFalse(preferences.enabled)
    }

    @Test fun sameTitlesNeverSelectAnotherIdentityOrItsPolicy() {
        val ui = RelaySettingsSelection(preferences)
        assertTrue(ui.selectSource(account, rows, -7001L, RelaySourcePolicy.FANO_DIRECT, 1000L))
        assertEquals(RelaySourcePolicy.FANO_DIRECT, preferences.sourcePolicy(-7001L))
        assertEquals(PickWatchMode.OFF, preferences.sourceRole(-7002L))
        assertNull(preferences.sourcePolicy(-7002L))
        assertEquals(100L, preferences.sourceBaseline(-7001L))
        assertEquals(1000L, preferences.enabledAt(-7001L))
        assertFalse(preferences.enabled)
    }

    @Test fun sourceMustBeLoadedAndCannotUseAnotherSessionOrAnUnsupportedFamily() {
        val ui = RelaySettingsSelection(preferences)
        assertFalse(ui.selectSource(account + 1, rows, -7001L, RelaySourcePolicy.FANO_DIRECT, 1000L))
        assertFalse(ui.selectSource(account, rows, -9999L, RelaySourcePolicy.FANO_DIRECT, 1000L))
        assertFalse(ui.selectSource(account, rows, -7001L, RelaySourcePolicy.MAZDITA_LEGACY, 1000L))
        assertFalse(ui.selectSource(account, rows, -7001L, RelaySourcePolicy.DIRECT_OTHER, 1000L))
        assertTrue(preferences.sources().isEmpty())
    }

    @Test fun eachUnicoFamilyIsAnExplicitDistinctBindingAndNoLoopIsAccepted() {
        val ui = RelaySettingsSelection(preferences)
        assertTrue(ui.selectDestination(account, rows, -7003L))
        assertTrue(ui.selectSource(account, rows, -7001L, RelaySourcePolicy.UNICOREY_MEX, 1000L))
        assertTrue(ui.selectSource(account, rows, -7002L, RelaySourcePolicy.UNICOREY_FOREIGN, 1000L))
        assertEquals(RelaySourcePolicy.UNICOREY_MEX, preferences.sourcePolicy(-7001L))
        assertEquals(RelaySourcePolicy.UNICOREY_FOREIGN, preferences.sourcePolicy(-7002L))
        assertFalse(ui.selectSource(account, rows, -7003L, RelaySourcePolicy.UNICOREY_KINGS, 1000L))
        assertFalse(ui.selectDestination(account, rows, -7001L))
        assertFalse(preferences.enabled)
    }

    @Test fun stopAndReopenKeepIdentitiesAndHighWaterMarksWithoutEnablingOutput() {
        val ui = RelaySettingsSelection(preferences)
        assertTrue(ui.selectDestination(account, rows, -7003L))
        assertTrue(ui.selectSource(account, rows, -7001L, RelaySourcePolicy.UNICOREY_POPULAR, 1000L))
        assertTrue(preferences.stop())
        val reopened = RelayPreferences(context, account)
        assertEquals(-7003L, reopened.destinationChatId())
        assertEquals(100L, reopened.sourceBaseline(-7001L))
        assertEquals(1000L, reopened.enabledAt(-7001L))
        assertEquals(RelaySourcePolicy.UNICOREY_POPULAR, reopened.sourcePolicy(-7001L))
        assertFalse(reopened.enabled)
        assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()

    @Test fun openingSettingsWithoutAnAccountIsPrivateAndCannotCreateASessionOrRoute() {
        assertNull(TelegramSession.current)
        val controller = Robolectric.buildActivity(RelaySettingsActivity::class.java).create().start().resume()
        try {
            val activity = controller.get()
            val all = views(activity.window.decorView)
            assertTrue(all.filterIsInstance<TextView>().any { it.text.toString().contains("salida real OFF") })
            assertFalse(all.filterIsInstance<Button>().any { it.text.toString().contains("Elegir mi canal") })
            assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertNull(TelegramSession.current)
            assertEquals(0L, preferences.destinationChatId())
        } finally { controller.pause().stop().destroy() }
    }
}
