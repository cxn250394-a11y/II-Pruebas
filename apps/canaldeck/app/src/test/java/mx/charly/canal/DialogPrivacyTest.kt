package mx.charly.canal
import android.app.AlertDialog
import android.view.WindowManager
import mx.charly.ui.Screen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class PrivacyFixtureActivity : Screen() {
 fun createProtected() = dialog().setMessage("Synthetic message").create()
 fun showProtected() = dialog().setMessage("Synthetic message").show()
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class DialogPrivacyTest {
 private fun protected(d:AlertDialog)=d.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
 @Test fun secureBeforeDisplay(){val c=Robolectric.buildActivity(PrivacyFixtureActivity::class.java).create();try{val d=c.get().createProtected();assertTrue(protected(d));assertFalse(d.isShowing)}finally{c.destroy()}}
 @Test fun secureWhenShown(){val c=Robolectric.buildActivity(PrivacyFixtureActivity::class.java).create();try{val d=c.get().showProtected();assertTrue(protected(d));assertTrue(d.isShowing);d.dismiss()}finally{c.destroy()}}
}
