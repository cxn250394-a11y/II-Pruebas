package mx.charly.canal
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class OrganizationTest {
 private val c:Context get()=RuntimeEnvironment.getApplication()
 @Test fun stableFolderIdAfterRename(){val o=Organization(c,101);val id=o.createFolder("Lectura")!!;o.assign(18,id);assertTrue(o.renameFolder(id,"Trabajo"));assertEquals(id,o.folder(18));assertEquals("Trabajo",o.folders().single().second)}
 @Test fun duplicateNormalizedNameRejected(){val o=Organization(c,102);assertNotNull(o.createFolder("Fútbol"));assertNull(o.createFolder("futbol"))}
 @Test fun deletionDoesNotDeleteChatFavorite(){val o=Organization(c,103);val id=o.createFolder("Uno")!!;o.assign(1,id);o.toggleFavorite(1);o.deleteFolder(id);assertEquals("",o.folder(1));assertTrue(o.favorite(1))}
 @Test fun accountNamespacesSeparate(){val a=Organization(c,104);val b=Organization(c,105);a.toggleFavorite(1);assertTrue(a.favorite(1));assertFalse(b.favorite(1));a.mode(1,NoticeMode.ALL);assertEquals(NoticeMode.OFF,b.mode(1))}
 @Test fun noticesAndPreviewsDefaultOff(){val o=Organization(c,106);assertEquals(NoticeMode.OFF,o.mode(1));assertFalse(o.previews)}
 @Test fun modesPersist(){val o=Organization(c,107);o.mode(1,NoticeMode.KEYWORDS);o.words(1,"gol");assertEquals(NoticeMode.KEYWORDS,Organization(c,107).mode(1));assertEquals("gol",Organization(c,107).words(1))}
 @Test fun invalidFolderIgnored(){val o=Organization(c,108);o.assign(1,"not-present");assertEquals("",o.folder(1))}
 @Test fun invalidQuietDoesNotMutate(){val o=Organization(c,109);assertFalse(o.hours("25:00","01:00"));assertEquals(23*60,o.quietStart());assertFalse(o.hours("01:00","01:00"));assertTrue(o.hours("22:30","07:00"));assertEquals(1350,o.quietStart())}
}
