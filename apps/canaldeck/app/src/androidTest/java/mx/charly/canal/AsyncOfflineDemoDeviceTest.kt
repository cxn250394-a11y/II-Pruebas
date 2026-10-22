package mx.charly.canal

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android UI + SQLite; synthetic demo only, no client, login, media or network. */
@RunWith(AndroidJUnit4::class)
class AsyncOfflineDemoDeviceTest {
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun click(activity: AsyncOfflineDemoActivity, label: String) = views(activity.window.decorView)
        .filterIsInstance<Button>().first { it.text.toString() == label }.performClick()
    private fun hasText(activity: AsyncOfflineDemoActivity, text: String) = views(activity.window.decorView)
        .filterIsInstance<TextView>().any { it.text.toString().contains(text) }

    @Test fun ownerCanEnterOfflineFlowAndRecoverUnknownWithoutAccount() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitor = Instrumentation.ActivityMonitor(AsyncOfflineDemoActivity::class.java.name, null, false)
        instrumentation.addMonitor(monitor)
        val home = instrumentation.startActivitySync(Intent(context, CanalActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CanalActivity
        var demo: AsyncOfflineDemoActivity? = null
        try {
            instrumentation.runOnMainSync {
                views(home.window.decorView).filterIsInstance<Button>()
                    .first { it.text.toString() == "Ensayo offline · sin cuenta" }.performClick()
            }
            demo = requireNotNull(monitor.waitForActivityWithTimeout(5_000)) as AsyncOfflineDemoActivity
            val activity = requireNotNull(demo)
            instrumentation.runOnMainSync {
                assertTrue(hasText(activity, "Picks ficticios · 0 envíos reales"))
                click(activity, "Nuevo ensayo ficticio")
                assertTrue(hasText(activity, "Álbum incompleto · retenido"))
                click(activity, "Completar álbum ficticio")
                click(activity, "Simular acuse rápido")
                assertTrue(hasText(activity, "Acuse rápido recibido · sigue pendiente"))
                click(activity, "Confirmar encabezado ficticio")
                click(activity, "Confirmar primera foto ficticia")
                assertTrue(hasText(activity, "1 de 2 fotos confirmadas · sigue pendiente"))
                click(activity, "Confirmar segunda foto ficticia")
                assertTrue(hasText(activity, "Escenario ficticio confirmado · 2 de 2 fotos"))
                click(activity, "Nuevo ensayo ficticio")
                click(activity, "Completar álbum ficticio")
                click(activity, "Comprobar reapertura · sin reintentar")
                assertTrue(hasText(activity, "UNKNOWN / incierto · no se reintenta"))
                assertTrue(hasText(activity, "nuevas solicitudes: 0"))
                assertNull(TelegramSession.current)
                assertFalse(BuildConfig.PICK_RELAY_REAL_ENABLED)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync { demo?.finish(); home.finish() }
        }
    }
}
