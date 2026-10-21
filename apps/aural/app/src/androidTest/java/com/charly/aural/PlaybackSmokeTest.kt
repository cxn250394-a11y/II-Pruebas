package com.charly.aural

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Executes actual UI/controller/service/decoder on Android. No accounts, network or user files. */
@RunWith(AndroidJUnit4::class)
class PlaybackSmokeTest {
    @Test fun originalAudioPlaysPausesAndSurvivesBackground() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val device=UiDevice.getInstance(instrumentation)
        val launch=requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        assertTrue("Welcome screen",device.wait(Until.hasObject(By.text("AURAL")),15000))
        assertTrue("Controller connected",device.wait(Until.hasObject(By.textContains("Nada en cola")),15000))
        device.findObject(By.textStartsWith("Probar sonido")).click()
        assertTrue("Decoder playing",device.wait(Until.hasObject(By.textStartsWith("Reproduciendo")),15000))
        Thread.sleep(1300)
        assertNotNull("Transport has duration",device.findObject(By.textContains(" / 0:08")))
        device.findObject(By.text("Pausar")).click()
        assertTrue("Pause acknowledged",device.wait(Until.hasObject(By.text("En pausa")),5000))
        val paused=device.findObject(By.textContains(" / 0:08")).text
        Thread.sleep(1100)
        assertEquals("Clock stays paused",paused,device.findObject(By.textContains(" / 0:08")).text)
        device.findObject(By.text("Reproducir")).click()
        assertTrue(device.wait(Until.hasObject(By.textStartsWith("Reproduciendo")),5000))
        device.pressHome()
        Thread.sleep(800)
        context.startActivity(Intent(launch).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        assertTrue("Playback survives controller disconnect",device.wait(Until.hasObject(By.textStartsWith("Reproduciendo")),5000))
        val screenshot=File(context.getExternalFilesDir(null),"aural-playback.png")
        assertTrue("Actual Android screenshot",device.takeScreenshot(screenshot))
        device.findObject(By.text("Detener")).click()
        assertTrue("Stop clears queue",device.wait(Until.hasObject(By.textContains("Nada en cola")),5000))
    }
}
