package mx.charly.canal

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual Android and native TDLib, without app identity, account credentials or production login. */
@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun vaultRoundTripAndDatabaseKey() {
        val v = Vault(context)
        v.put("instrumentation-synthetic", "synthetic-not-a-secret")
        assertEquals("synthetic-not-a-secret", v.get("instrumentation-synthetic"))
        assertEquals(32, v.databaseKey().size)
        assertTrue(v.databaseKey().contentEquals(v.databaseKey()))
    }
    @Test fun nativeTdlibLoadsAndWaitsForOwnIdentity(): Unit = runBlocking {
        withTimeout(15_000) {
            val client = TdlClient.create()
            try {
                client.setLogVerbosityLevel(0)
                val result = client.getAuthorizationState()
                assertTrue("Native runtime must return a response", result is TdlResult.Success)
                assertTrue((result as TdlResult.Success).result is AuthorizationStateWaitTdlibParameters)
            } finally { client.close() }
        }
        Unit
    }
    @Test fun accountScopedPreferencesOnDevice() {
        val a = Organization(context, -101)
        val b = Organization(context, -102)
        val id = a.createFolder("Device test ${System.nanoTime()}")!!
        a.assign(999, id)
        assertEquals(id, a.folder(999))
        assertEquals("", b.folder(999))
        assertTrue(a.renameFolder(id, "Renamed ${System.nanoTime()}"))
        assertEquals(id, a.folder(999))
        a.deleteFolder(id)
        assertEquals("", a.folder(999))
    }
    private fun views(v:View):List<View> = listOf(v) + if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    @Test fun nativeDemoNavigationAndSyntheticScreenshot() {
        val i=InstrumentationRegistry.getInstrumentation()
        val a=i.startActivitySync(Intent(context,CanalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CanalActivity
        try {
            i.runOnMainSync {
                views(a.window.decorView).filterIsInstance<Button>().first{it.text.toString().contains("Ver diseño")}.performClick()
                assertEquals(5,views(a.window.decorView).filterIsInstance<ListView>().single().adapter.count)
                views(a.window.decorView).filterIsInstance<Button>().first{it.text.toString()=="Canales"}.performClick()
                assertEquals(3,views(a.window.decorView).filterIsInstance<ListView>().single().adapter.count)
                assertNull(TelegramSession.current)
            }
            i.waitForIdleSync();SystemClock.sleep(250)
            val picture=requireNotNull(i.uiAutomation.takeScreenshot())
            File(context.filesDir,"canaldeck-demo.png").outputStream().use{picture.compress(Bitmap.CompressFormat.PNG,100,it)}
            picture.recycle()
        } finally { i.runOnMainSync { a.finish() } }
    }
}
