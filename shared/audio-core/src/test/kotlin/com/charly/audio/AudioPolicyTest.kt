package com.charly.audio

import org.junit.Assert.*
import org.junit.Test

class AudioPolicyTest {
    @Test fun httpsAccepted() = assertTrue(AudioPolicy.validRadio("https://radio.example/live.mp3"))
    @Test fun hlsAccepted() = assertTrue(AudioPolicy.validRadio("https://radio.example/live.m3u8"))
    @Test fun httpRejected() = assertFalse(AudioPolicy.validRadio("http://radio.example/live"))
    @Test fun credentialsRejected() = assertFalse(AudioPolicy.validRadio("https://user:password@radio.example/live"))
    @Test fun relativeRejected() = assertFalse(AudioPolicy.validRadio("/live"))
    @Test fun javascriptRejected() = assertFalse(AudioPolicy.validRadio("javascript:alert(1)"))
    @Test fun fileRejected() = assertFalse(AudioPolicy.validRadio("file:///sdcard/music.mp3"))
    @Test fun fragmentRejected() = assertFalse(AudioPolicy.validRadio("https://radio.example/live#secret"))
    @Test fun whitespaceRejected() = assertFalse(AudioPolicy.validRadio("https://radio.example/live stream"))
    @Test fun controlRejected() = assertFalse(AudioPolicy.validRadio("https://radio.example/live\r\nHeader:x"))
    @Test fun invalidPortRejected() = assertFalse(AudioPolicy.validRadio("https://radio.example:70000/live"))
    @Test fun validPortAccepted() = assertTrue(AudioPolicy.validRadio("https://radio.example:8443/live"))
    @Test fun lengthBounded() = assertFalse(AudioPolicy.validRadio("https://radio.example/"+"a".repeat(2048)))
    @Test fun blankRejected() = assertFalse(AudioPolicy.validRadio(""))
    @Test fun queriesAllowedWithoutLeakingInId() { val e=AudioEntry("https://radio.example/live?token=sample","Radio",EntryKind.RADIO); assertTrue(AudioPolicy.validEntry(e)); assertFalse(e.id.contains("sample")); assertEquals(64,e.id.length) }
    @Test fun localRequiresContentUri() { assertTrue(AudioPolicy.validEntry(AudioEntry("content://media/audio/7","Song",EntryKind.LOCAL))); assertFalse(AudioPolicy.validEntry(AudioEntry("https://radio.example/a","Song",EntryKind.LOCAL))) }
    @Test fun localRequiresAuthority() = assertFalse(AudioPolicy.validEntry(AudioEntry("content:///audio/7","Song",EntryKind.LOCAL)))
    @Test fun onlyOwnDemo() { assertTrue(AudioPolicy.validEntry(AudioPolicy.demo)); assertFalse(AudioPolicy.validEntry(AudioEntry("asset:///arbitrary","Song",EntryKind.DEMO))) }
    @Test fun nameCannotBeBlank() = assertFalse(AudioPolicy.validEntry(AudioEntry("https://radio.example/live"," ",EntryKind.RADIO)))
    @Test fun namesAreClippedAndCleaned() { assertEquals(160,AudioPolicy.title("x".repeat(500)).length); assertFalse(AudioPolicy.title("A\nB").contains('\n')); assertTrue(AudioPolicy.title(" ").isNotBlank()) }
    @Test fun accentInsensitiveSearch() = assertTrue(AudioPolicy.matches(AudioEntry("content://a/1","Canción bonita",EntryKind.LOCAL),"cancion"))
    @Test fun blankSearchMatches() = assertTrue(AudioPolicy.matches(AudioPolicy.demo," "))
    @Test fun unknownSearchNotInvented() = assertFalse(AudioPolicy.matches(AudioPolicy.demo,"impossible title"))
    @Test fun deterministicIds() = assertEquals(AudioPolicy.demo.id,AudioPolicy.demo.copy(title="Renamed").id)
    @Test fun favoritesDoNotChangeId() = assertEquals(AudioPolicy.demo.id,AudioPolicy.demo.copy(favorite=true).id)
    @Test fun uriCaseNotFolded() = assertNotEquals(AudioEntry("https://radio.example/A","A",EntryKind.RADIO).id,AudioEntry("https://radio.example/a","A",EntryKind.RADIO).id)
    @Test fun clockNegativeClamped() = assertEquals("0:00",AudioPolicy.clock(-1))
    @Test fun clockMinutes() = assertEquals("2:05",AudioPolicy.clock(125000))
    @Test fun highResolutionDoesNotImplyBitPerfect() { assertTrue(AudioPolicy.sourceFormat(192000,2,"audio/flac").startsWith("Fuente:")); assertFalse(AudioPolicy.sourceFormat(192000,2,"audio/flac").contains("bit-perfect",true)); assertEquals("Bit-perfect: NO VERIFICADO",AudioPolicy.BIT_PERFECT_STATUS) }
    @Test fun unknownSourceNotZero() = assertTrue(AudioPolicy.sourceFormat(-1,-1,null).contains("desconocida"))
}
