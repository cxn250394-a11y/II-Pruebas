package mx.charly.audio
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) class AudioSmokeTest{
 @Test fun opensWithoutPlaybackOrNetwork(){val c=Robolectric.buildActivity(AudioActivity::class.java).create();try{assertNotNull(c.get().window.decorView)}finally{c.destroy()}}
 @Test @Config(sdk=[35]) fun opens35(){val c=Robolectric.buildActivity(AudioActivity::class.java).create();try{assertNotNull(c.get().window.decorView)}finally{c.destroy()}}
}
