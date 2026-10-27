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
 fun showInitiallyAllowed(): AlertDialog { allowDialogScreenshots = true; return dialog().setMessage("Synthetic message").show() }
 fun lockVisibleDialogs() { allowDialogScreenshots = false; protectOpenDialogs() }
}
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class DialogPrivacyTest {
 private fun protected(d:AlertDialog)=d.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
 @Test fun secureBeforeDisplay(){val c=Robolectric.buildActivity(PrivacyFixtureActivity::class.java).create();try{val d=c.get().createProtected();assertTrue(protected(d));assertFalse(d.isShowing)}finally{c.destroy()}}
 @Test fun secureWhenShown(){val c=Robolectric.buildActivity(PrivacyFixtureActivity::class.java).create();try{val d=c.get().showProtected();assertTrue(protected(d));assertTrue(d.isShowing);d.dismiss()}finally{c.destroy()}}
 @Test fun existingDialogLocksOnPrivacyChange(){
  val c=Robolectric.buildActivity(PrivacyFixtureActivity::class.java).create()
  try { val a=c.get(); val d=a.showInitiallyAllowed(); assertFalse(protected(d)); a.lockVisibleDialogs(); assertTrue(protected(d)); assertTrue(d.isShowing); d.dismiss() } finally { c.destroy() }
 }
 @Test fun unknownHistoryAndBackgroundAreProtected() {
  val controller=Robolectric.buildActivity(CanalActivity::class.java).create().start().resume()
  try {
   val a=controller.get()
   CanalActivity::class.java.getDeclaredField("screen").apply { isAccessible=true }.set(a,"history")
   CanalActivity::class.java.getDeclaredMethod("secure").apply { isAccessible=true }.invoke(a)
   assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
   controller.pause()
   assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
  } finally { controller.stop().destroy() }
 }
 @Test fun backgroundSecureCannotBeClearedByDialogDismissRecheck() {
  val controller=Robolectric.buildActivity(CanalActivity::class.java).create().start().resume()
  try {
   val a=controller.get()
   assertEquals(0, a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
   controller.pause()
   CanalActivity::class.java.getDeclaredMethod("secure").apply { isAccessible=true }.invoke(a)
   assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
  } finally { controller.stop().destroy() }
 }
 @Test fun accountTransitionCannotUnprotectDuringDialogDismiss() {
  val controller=Robolectric.buildActivity(CanalActivity::class.java).create().start().resume()
  try {
   val a=controller.get()
   val builder=Screen::class.java.getDeclaredMethod("dialog").apply { isAccessible=true }.invoke(a) as AlertDialog.Builder
   val d=builder.setMessage("Contenido sintético").create()
   d.setOnDismissListener { CanalActivity::class.java.getDeclaredMethod("secure").apply { isAccessible=true }.invoke(a) }
   d.show()
   CanalActivity::class.java.getDeclaredField("transitioningAccount").apply { isAccessible=true }.setBoolean(a,true)
   a.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
   d.dismiss()
   assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
  } finally { controller.pause().stop().destroy() }
 }
 @Test fun lossOfIdentityImmediatelyClosesListDialogsWithoutDebounce() {
  val controller=Robolectric.buildActivity(CanalActivity::class.java).create().start().resume()
  try {
   val a=controller.get()
   CanalActivity::class.java.getDeclaredField("screen").apply { isAccessible=true }.set(a,"list")
   CanalActivity::class.java.getDeclaredField("lastAccount").apply { isAccessible=true }.setLong(a,42L)
   val builder=Screen::class.java.getDeclaredMethod("dialog").apply { isAccessible=true }.invoke(a) as AlertDialog.Builder
   val d=builder.setMessage("Configuración sintética").show()
   @Suppress("UNCHECKED_CAST")
   val changed=CanalActivity::class.java.getDeclaredField("changed").apply { isAccessible=true }.get(a) as () -> Unit
   changed()
   assertFalse(d.isShowing)
   assertEquals("home",CanalActivity::class.java.getDeclaredField("screen").apply { isAccessible=true }.get(a))
   assertEquals(0L,CanalActivity::class.java.getDeclaredField("lastAccount").apply { isAccessible=true }.getLong(a))
  } finally { controller.pause().stop().destroy() }
 }
 @Test fun staleAccountActionDoesNotRun() {
  val controller=Robolectric.buildActivity(CanalActivity::class.java).create().start().resume()
  try {
   val a=controller.get();var ran=false
   val action: () -> Unit = { ran=true }
   val method=CanalActivity::class.java.getDeclaredMethod("withAccount", TelegramSession::class.java, Long::class.javaPrimitiveType, kotlin.jvm.functions.Function0::class.java)
   method.isAccessible=true; method.invoke(a,null,42L,action)
   assertFalse(ran)
  } finally { controller.pause().stop().destroy() }
 }
 @Test fun pausedViewLosingSessionResetsOnResume() {
  val controller=Robolectric.buildActivity(CanalActivity::class.java).create().start().resume()
  try {
   val a=controller.get()
   CanalActivity::class.java.getDeclaredField("screen").apply { isAccessible=true }.set(a,"list")
   CanalActivity::class.java.getDeclaredField("lastAccount").apply { isAccessible=true }.setLong(a,42L)
   controller.pause().stop().start().resume()
   assertEquals("home",CanalActivity::class.java.getDeclaredField("screen").apply { isAccessible=true }.get(a))
   assertEquals(0L,CanalActivity::class.java.getDeclaredField("lastAccount").apply { isAccessible=true }.getLong(a))
  } finally { controller.pause().stop().destroy() }
 }
 @Test fun newIntentReconcilesIdentityBeforeSavingOldDraft() {
  val controller=Robolectric.buildActivity(CanalActivity::class.java).create().start().resume()
  try {
   val a=controller.get()
   CanalActivity::class.java.getDeclaredField("screen").apply { isAccessible=true }.set(a,"history")
   CanalActivity::class.java.getDeclaredField("lastAccount").apply { isAccessible=true }.setLong(a,42L)
   CanalActivity::class.java.getDeclaredField("activeChat").apply { isAccessible=true }.setLong(a,9L)
   val editor=android.widget.EditText(a).apply { setText("borrador sintético anterior") }
   CanalActivity::class.java.getDeclaredField("composer").apply { isAccessible=true }.set(a,editor)
   val intent=android.content.Intent(a,CanalActivity::class.java).putExtra("chat",9L)
   CanalActivity::class.java.getDeclaredMethod("onNewIntent",android.content.Intent::class.java).apply { isAccessible=true }.invoke(a,intent)
   val drafts=CanalActivity::class.java.getDeclaredField("drafts").apply { isAccessible=true }.get(a) as Map<*, *>
   assertTrue(drafts.isEmpty())
   assertEquals("home",CanalActivity::class.java.getDeclaredField("screen").apply { isAccessible=true }.get(a))
  } finally { controller.pause().stop().destroy() }
 }
}
