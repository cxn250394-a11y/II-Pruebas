package mx.charly.canal

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class CanalSmokeTest {
    private fun views(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun click(a:CanalActivity,part:String){views(a.window.decorView).filterIsInstance<Button>().first{it.text.toString().contains(part)}.performClick()}
    @Test fun nativeWelcomeHasClearRealAndDemoChoices(){
        val c=Robolectric.buildActivity(CanalActivity::class.java).create()
        try{val a=c.get();val all=views(a.window.decorView).filterIsInstance<TextView>().map{it.text.toString()};assertTrue(all.any{it.contains("conectar mi cuenta")});assertTrue(all.any{it.contains("sin iniciar sesión")});assertNull(TelegramSession.current)}finally{c.destroy()}
    }
    @Test fun demoDoesNotStartSessionAndFiltersRealListWidget(){
        val c=Robolectric.buildActivity(CanalActivity::class.java).create()
        try{val a=c.get();click(a,"Ver diseño");assertEquals(5,views(a.window.decorView).filterIsInstance<ListView>().single().adapter.count);click(a,"Canales");assertEquals(3,views(a.window.decorView).filterIsInstance<ListView>().single().adapter.count);assertNull(TelegramSession.current)}finally{c.destroy()}
    }
    @Test fun demoSearchUsesAllRows(){
        val c=Robolectric.buildActivity(CanalActivity::class.java).create()
        try{val a=c.get();click(a,"Ver diseño");views(a.window.decorView).filterIsInstance<EditText>().single().setText("musica");assertEquals(1,views(a.window.decorView).filterIsInstance<ListView>().single().adapter.count)}finally{c.destroy()}
    }
    @Test fun credentialsScreenIsProtectedAndDoesNotStartSession(){
        val c=Robolectric.buildActivity(CanalActivity::class.java).create()
        try{val a=c.get();click(a,"Identidad de aplicación");assertTrue(a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0);assertEquals(2,views(a.window.decorView).filterIsInstance<EditText>().size);assertTrue(views(a.window.decorView).filterIsInstance<EditText>().all{!it.isSaveEnabled});assertNull(TelegramSession.current)}finally{c.destroy()}
    }
    @Test fun rejectsMissingIdentity(){assertFalse(Vault.validApi("",""));assertFalse(Vault.validApi("0","a".repeat(32)));assertFalse(Vault.validApi("42","z".repeat(32)));assertTrue(Vault.validApi("42","a".repeat(32)))}
    @Test @Config(sdk=[35]) fun android35Welcome(){val c=Robolectric.buildActivity(CanalActivity::class.java).create();try{assertNotNull(c.get().findViewById<View>(android.R.id.content))}finally{c.destroy()}}
}
