package mx.charly.odds
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) class OddsSmokeTest{
 @Test fun opensWithoutNetwork(){val c=Robolectric.buildActivity(OddsActivity::class.java).create();try{assertNotNull(c.get().window.decorView)}finally{c.destroy()}}
 @Test @Config(sdk=[35]) fun opens35(){val c=Robolectric.buildActivity(OddsActivity::class.java).create();try{assertNotNull(c.get().window.decorView)}finally{c.destroy()}}
}
