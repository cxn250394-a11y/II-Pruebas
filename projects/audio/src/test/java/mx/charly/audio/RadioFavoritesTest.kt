package mx.charly.audio

import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RadioFavoritesTest {
 private val context: Context get() = RuntimeEnvironment.getApplication()
 @Before fun clear() {
  for(suffix in listOf(""," .bak",".bak",".new")) File(context.filesDir,"radio-favorites.json"+suffix.trim()).delete()
 }
 @Test fun onlyHttps() {
  assertTrue(RadioFavorites.validStream("https://radio.example.com/live.mp3"))
  assertFalse(RadioFavorites.validStream("http://radio.example.com/live"))
  assertFalse(RadioFavorites.validStream("javascript:alert(1)"))
  assertFalse(RadioFavorites.validStream("https://user:password@radio.example.com/live"))
  assertFalse(RadioFavorites.validStream("https://radio.example.com/live#anchor"))
 }
 @Test fun saveAndReload() {
  val f=RadioFavorites(context)
  assertTrue(f.add(FavoriteStation("Rock Mexico","https://radio.example.com/live","MP3",192)))
  assertEquals(1,RadioFavorites(context).all().size)
  assertEquals("Rock Mexico",RadioFavorites(context).all().single().name)
  assertEquals(192,RadioFavorites(context).all().single().bitrate)
 }
 @Test fun duplicateAndRemoval() {
  val f=RadioFavorites(context)
  val station=FavoriteStation("Test","https://radio.example.com/test")
  assertTrue(f.add(station));assertFalse(f.add(station))
  assertFalse(f.remove("https://radio.example.com/other"))
  assertTrue(f.remove(station.url));assertTrue(f.all().isEmpty())
 }
 @Test fun malformedDataMustNotBeOverwritten() {
  val saved=File(context.filesDir,"radio-favorites.json");saved.writeText("unparseable")
  val f=RadioFavorites(context)
  try { f.add(FavoriteStation("Station","https://radio.example.com/live")); fail("Invalid library overwritten") }
  catch (_: Exception) { assertEquals("unparseable",saved.readText()) }
 }
 @Test fun rejectEmptyStation() {
  try{RadioFavorites(context).add(FavoriteStation("  ","https://radio.example.com/live"));fail("Accepted blank name")}
  catch(_:IllegalArgumentException){}
 }
}
class SleepTimerRulesTest {
 @Test fun onlyKnownTimerValues(){assertTrue(SleepTimerRules.validMinutes(15));assertTrue(SleepTimerRules.validMinutes(120));assertFalse(SleepTimerRules.validMinutes(17))}
 @Test fun disablingHasNoDeadline(){assertNull(SleepTimerRules.deadline(5000,0))}
 @Test fun stableMonotonicClockDeadline(){assertEquals(905000L,SleepTimerRules.deadline(5000L,15))}
 @Test fun invalidClockRejected(){assertNull(SleepTimerRules.deadline(-1,30))}
}
