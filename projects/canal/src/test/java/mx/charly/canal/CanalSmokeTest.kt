package mx.charly.canal
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) class CanalSmokeTest{
 @Test fun opensWithoutCreatingTelegramSession(){val c=Robolectric.buildActivity(CanalActivity::class.java).create();try{assertNotNull(c.get().window.decorView);assertNull(TelegramSession.current)}finally{c.destroy()}}
 @Test fun validatesDeveloperIdentity(){assertTrue(Vault.validApi("123","a".repeat(32)));assertFalse(Vault.validApi("0","a".repeat(32)));assertFalse(Vault.validApi("123","password"))}
}
