package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PickBridgeRulesTest {
    private val time = LocalDateTime.of(2026, 10, 22, 12, 35)
        .atZone(ZoneId.of("America/Mexico_City")).toEpochSecond()

    @Test fun directChannelHasExactThreeLines() {
        val alert = PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", "Pick nuevo", time, true)!!
        assertEquals(
            "Canal: Fano Futbol\nHora original: 22/10/2026 12:35 CDMX\nLink: https://t.me/c/123/456",
            alert.render("https://t.me/c/123/456")
        )
    }

    @Test fun photoWithoutCaptionInDirectChannelIsEligible() {
        assertNotNull(PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", "", time, true))
    }

    @Test fun originalClockNotLocalProcessingClock() {
        val alert = PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico", "Tipster: Dr MLS", time, false)!!
        assertTrue(alert.render(null).contains("Hora original: 22/10/2026 12:35 CDMX"))
    }

    @Test fun resellerAllowsExactLabels() {
        for (name in PickBridgeRules.allowedTipsters) {
            val alert = PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico", "Tipster: $name\nOver 8.5", time, false)
            assertEquals("Tipster: $name", alert?.firstLine)
        }
    }

    @Test fun noChatTitleAsTipster() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico - Dr MLS", "Mira esta apuesta", time, false))
    }

    @Test fun casualMentionsAreNeverAttribution() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico", "Saludos a Dr MLS\nMi jugada", time, false))
    }

    @Test fun unlistedSourceIsRejected() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico", "Tipster: Otro", time, true))
    }

    @Test fun namesAreAccentAndCaseTolerantButNotSubstring() {
        assertEquals("Diego Palafox", PickBridgeRules.tipster("TIPSTER: DIEGO PALAFOX\nJugada"))
        assertEquals("Rodrigo Picks", PickBridgeRules.tipster("Pronosticador: Rodrigo Picks"))
        assertNull(PickBridgeRules.tipster("Tipster: Rodrigo Picks VIP"))
    }

    @Test fun noModeNoEvent() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.OFF, "Fano Futbol", "Pick", time, true))
    }

    @Test fun editDoesNotBecomeNewPick() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", "Pick", time, true, edited=true))
    }

    @Test fun outgoingDoesNotBecomeNewPick() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", "Pick", time, true, outgoing=true))
    }

    @Test fun emptyNonPhotoIsRejected() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", "", time, false))
    }

    @Test fun badClockIsUnknown() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", "Pick", 0, true))
        assertEquals("No disponible", PickBridgeRules.originalDate(0))
    }

    @Test fun onlyTelegramHttpsLinksAccepted() {
        val good = listOf("https://t.me/fanofutbol/100", "https://t.me/c/123/456?single", "https://telegram.me/c/123/456")
        good.forEach { assertEquals(it, PickBridgeRules.validTelegramLink(it)) }
        val bad = listOf("https://t.me.evil.test/x", "http://t.me/x",
            "https://t.me@evil.test/x", "javascript:alert(1)", "https://t.me/", "https://t.me/x\nX-Injected: y")
        bad.forEach { assertNull(PickBridgeRules.validTelegramLink(it)) }
    }

    @Test fun missingLinkIsExplicitlyUnknown() {
        val alert = PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", "Pick", time, false)!!
        assertTrue(alert.render(null).endsWith("Link: No disponible"))
    }

    @Test fun namesListHasExactlyTwelveOwners() {
        assertEquals(12, PickBridgeRules.allowedTipsters.size)
        assertEquals(12, PickBridgeRules.allowedTipsters.distinct().size)
    }
}
