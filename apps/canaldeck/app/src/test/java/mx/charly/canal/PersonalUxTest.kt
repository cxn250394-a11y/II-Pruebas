package mx.charly.canal

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class MessageLinksTest {
    @Test fun publicChannel() { assertEquals(PublicTelegramLink("canal_01",null),MessageLinks.parsePublic("https://t.me/canal_01")) }
    @Test fun publication() { assertEquals(PublicTelegramLink("canal_01",123),MessageLinks.parsePublic("https://t.me/canal_01/123")) }
    @Test fun browserPublicVariant() { assertEquals(PublicTelegramLink("canal_01",17),MessageLinks.parsePublic("https://t.me/s/canal_01/17")) }
    @Test fun privateProtectedLinkRemainsExternal() { assertNull(MessageLinks.parsePublic("https://t.me/c/12345/678")) }
    @Test fun inviteIsNotAssumed() { assertNull(MessageLinks.parsePublic("https://t.me/+SecretHash")) }
    @Test fun lookalikeHostRejected() { assertNull(MessageLinks.parsePublic("https://t.me.attacker.com/canal_01/123")) }
    @Test fun fakeUserInfoRejected() { assertNull(MessageLinks.parsePublic("https://t.me@attacker.com/canal_01")) }
    @Test fun explicitPortRejected() { assertNull(MessageLinks.parsePublic("https://t.me:443/canal_01")) }
    @Test fun maliciousSchemeRejected() { assertNull(MessageLinks.parsePublic("javascript:alert(1)")) }
    @Test fun invalidIdRejected() { assertNull(MessageLinks.parsePublic("https://t.me/canal_01/0")) }
}
class LocalBetTextRulesTest {
    @Test fun cornersFromSpanish() { assertTrue("Córners" in LocalBetText.labels("Mas de 9 corners")) }
    @Test fun flagsNotVerifyWager() { assertTrue("Cuota o momio (no verificado)" in LocalBetText.labels("Cuota 1.82")) }
    @Test fun emptyTextHasNoMarkets() { assertTrue(LocalBetText.labels("").isEmpty()) }
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class OrganizationBackupTest {
    private val c:Context get()=RuntimeEnvironment.getApplication()
    @Test fun foldersCanBeMoved() {
        val o=Organization(c,-9100011)
        val a=o.createFolder("Primera")!!;val b=o.createFolder("Segunda")!!
        assertEquals(listOf(a,b),o.folders().map{it.first})
        assertTrue(o.moveFolder(b,-1));assertEquals(listOf(b,a),o.folders().map{it.first})
        assertFalse(o.moveFolder(b,-1))
        assertEquals(listOf(b,a),Organization(c,-9100011).folders().map{it.first})
    }
    @Test fun encryptedBackupRoundTripsOnlyLocalPreferences() {
        val id=-9100012L;val a=Organization(c,id)
        val folder=a.createFolder("Canales")!!
        a.assign(88,folder);a.toggleFavorite(88);a.toggleImportant(88);a.previews=true;a.screenshots=true
        a.words(88,"gol,tarjeta");a.mode(88,NoticeMode.KEYWORDS)
        val pass="Una clave larga y privada 123".toCharArray()
        val encrypted=OrganizationBackup.encrypt(a.exportOrganization().toByteArray(),pass)
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("Canales"))
        val plain=OrganizationBackup.decrypt(encrypted,pass).toString(Charsets.UTF_8)
        val b=Organization(c,id)
        assertTrue(b.importOrganization(plain))
        assertTrue(b.favorite(88));assertTrue(b.important(88));assertEquals(folder,b.folder(88))
        assertEquals("gol,tarjeta",b.words(88));assertEquals(NoticeMode.KEYWORDS,b.mode(88))
        assertTrue(b.previews);assertTrue(b.screenshots)
    }
    @Test fun wrongPasswordFails() {
        val cipher=OrganizationBackup.encrypt("{}".toByteArray(),"correct-password-000".toCharArray())
        try {OrganizationBackup.decrypt(cipher,"incorrect-password-000".toCharArray());fail("Wrong key accepted")}
        catch(_:Exception){}
    }
    @Test fun otherAccountCannotImportOrMutate() {
        val x=Organization(c,-9100013);val y=Organization(c,-9100014)
        x.toggleFavorite(99)
        assertFalse(y.importOrganization(x.exportOrganization()))
        assertFalse(y.favorite(99))
    }
    @Test fun malformedBackupDoesNotDestroyExistingPrefs() {
        val o=Organization(c,-9100015);o.toggleFavorite(5)
        assertFalse(o.importOrganization("""{"schema":1,"accountId":"-9100015","settings":{"favorites":123}}"""))
        assertTrue(o.favorite(5))
    }
    @Test fun tamperedCipherFails() {
        val c=OrganizationBackup.encrypt("{}".toByteArray(),"strong-password-000".toCharArray())
        c[c.lastIndex]=(c.last().toInt() xor 1).toByte()
        try {OrganizationBackup.decrypt(c,"strong-password-000".toCharArray());fail("Tampered accepted")}
        catch(_:Exception){}
    }
}
