package com.charly.aural

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import com.charly.audio.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class AuralAndroidTest {
    private val context:Context get()=RuntimeEnvironment.getApplication()
    @Before fun clean() { context.getSharedPreferences("aural_library",Context.MODE_PRIVATE).edit().clear().commit() }
    private fun local(n:Int=1)=AudioEntry("content://media/audio/$n","Song $n",EntryKind.LOCAL)
    private fun radio(n:Int=1)=AudioEntry("https://radio.example/live$n","Radio $n",EntryKind.RADIO)
    @Test fun defaultLibraryEmpty() = assertTrue(LibraryStore(context).entries().isEmpty())
    @Test fun noNetworkConsentInitially() = assertFalse(LibraryStore(context).networkAllowed())
    @Test fun networkConsentRevocable() { val s=LibraryStore(context); s.allowNetwork(true); assertTrue(s.networkAllowed()); s.allowNetwork(false); assertFalse(s.networkAllowed()) }
    @Test fun addingRadioDoesNotAuthorizeNetwork() { val s=LibraryStore(context); assertTrue(s.add(radio())); assertFalse(s.networkAllowed()) }
    @Test fun localPersists() { assertTrue(LibraryStore(context).add(local())); assertEquals(local(),LibraryStore(context).entries().single()) }
    @Test fun duplicateDoesNotOverwriteFavorite() { val s=LibraryStore(context); s.add(local()); s.favorite(local().id); assertFalse(s.add(local())); assertTrue(s.entries().single().favorite) }
    @Test fun invalidStreamNotStored() = assertFalse(LibraryStore(context).add(AudioEntry("http://insecure.example/live","Bad",EntryKind.RADIO)))
    @Test fun demoNotPersisted() { assertFalse(LibraryStore(context).add(AudioPolicy.demo)); assertTrue(LibraryStore(context).entries().isEmpty()) }
    @Test fun removeDoesNotRemoveAnother() { val s=LibraryStore(context); s.add(local()); s.add(local(2)); s.remove(local().id); assertEquals(local(2),s.entries().single()) }
    @Test fun favoriteTogglePersists() { val s=LibraryStore(context); s.add(radio()); s.favorite(radio().id); assertTrue(LibraryStore(context).entries().single().favorite); s.favorite(radio().id); assertFalse(s.entries().single().favorite) }
    @Test fun radioCapacityEnforced() { val s=LibraryStore(context); for(i in 0 until AudioPolicy.MAX_RADIO) assertTrue(s.add(radio(i))); assertFalse(s.add(radio(101))); assertTrue(s.add(local())) }
    @Test fun corruptJsonDoesNotCrash() { context.getSharedPreferences("aural_library",Context.MODE_PRIVATE).edit().putString("entries","bad json").commit(); assertTrue(LibraryStore(context).entries().isEmpty()) }
    @Test fun malformedPersistedItemIgnored() { context.getSharedPreferences("aural_library",Context.MODE_PRIVATE).edit().putString("entries","[{\"uri\":\"javascript:run()\",\"title\":\"Bad\",\"kind\":\"RADIO\"}]").commit(); assertTrue(LibraryStore(context).entries().isEmpty()) }
    @Test fun metadataDoesNotClaimBitPerfect() { val m=playable(local()); assertEquals(local().id,m.mediaId); assertEquals("Song 1",m.mediaMetadata.title); assertFalse(m.mediaMetadata.toString().contains("bit-perfect")) }
    @Test fun noInvasivePermissions() { val p=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet(); assertTrue("android.permission.INTERNET" in p); listOf("READ_EXTERNAL_STORAGE","READ_MEDIA_AUDIO","READ_CONTACTS","RECORD_AUDIO","QUERY_ALL_PACKAGES").forEach { assertFalse("android.permission.$it" in p) } }
    private fun texts(v:View):List<String> = (if(v is TextView) listOf(v.text.toString()) else emptyList()) + if(v is ViewGroup) (0 until v.childCount).flatMap { texts(v.getChildAt(it)) } else emptyList()
    @Test fun firstScreenOffersRealFilesNotFakeLibrary() { val c=Robolectric.buildActivity(MainActivity::class.java).create(); try { val t=texts(c.get().window.decorView); assertTrue(t.any { it=="Añadir archivos" }); assertTrue(t.any { it.startsWith("Lista vacía") }); assertFalse(LibraryStore(context).networkAllowed()) } finally { c.destroy() } }
    @Test @Config(sdk=[35]) fun api35FirstScreen() { val c=Robolectric.buildActivity(MainActivity::class.java).create(); try { assertTrue(texts(c.get().window.decorView).contains("AURAL")) } finally { c.destroy() } }
    @Test fun diagnosticDoesNotCertifyRoute() = assertTrue(AudioDiagnostics.report(context).contains("NO VERIFICADO"))
}
