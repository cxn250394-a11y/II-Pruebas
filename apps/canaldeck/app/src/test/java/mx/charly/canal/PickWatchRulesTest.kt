package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PickWatchRulesTest {
    private val now = LocalDateTime.of(2026, 10, 22, 12, 40)
        .atZone(ZoneId.of("America/Mexico_City")).toEpochSecond()
    private val date = now - 300
    private fun notice(mode: PickWatchMode, text: String = "Tipster: Dr MLS",
                       image: Boolean = true, outgoing: Boolean = false,
                       edited: Boolean = false, source: Long = date) =
        PickWatchRules.propose(mode, "Fano Futbol", text, image, source, now, outgoing, edited)

    @Test fun twelveApprovedTipsters() {
        assertEquals(12, PickWatchRules.allowedTipsters.size)
        assertTrue(PickWatchRules.allowedTipsters.contains("Renne NFL"))
        assertTrue(PickWatchRules.allowedTipsters.contains("Tennis Insider"))
        assertFalse(PickWatchRules.allowedTipsters.contains("Fano Futbol"))
    }

    @Test fun directPhotoAndCaption() {
        val n = notice(PickWatchMode.DIRECT_CHANNEL, "Jugada Fano + imagen")!!
        assertEquals("Canal: Fano Futbol", n.heading)
        assertEquals("22/10/2026 12:35 CDMX", n.originalTime)
    }

    @Test fun directImageWithoutText() {
        assertNotNull(notice(PickWatchMode.DIRECT_CHANNEL, text = ""))
    }

    @Test fun directTextOnly() {
        assertNotNull(notice(PickWatchMode.DIRECT_CHANNEL, text = "Corners 8.5", image = false))
    }

    @Test fun resellerAttribution() {
        val n = notice(PickWatchMode.RESELLER_TIPSTERS, "Tipster: Dr MLS\nCuota 1.8")!!
        assertEquals("Tipster: Dr MLS", n.heading)
    }

    @Test fun resellerExactStandaloneLine() {
        assertEquals("Renne NFL", PickWatchRules.tipster("Renne NFL\nPases 220"))
    }

    @Test fun resellerNormalizedAccentsCase() {
        assertEquals("Diego Palafox", PickWatchRules.tipster("Tipster: DIEGO PALAFOX\nCorners"))
    }

    @Test fun resellerNonAuthorizedRejected() {
        assertNull(notice(PickWatchMode.RESELLER_TIPSTERS, "Tipster: No Autorizado"))
    }

    @Test fun resellerMentionIsNotProvenance() {
        assertNull(notice(PickWatchMode.RESELLER_TIPSTERS, "Saludos a Dr MLS por este boleto"))
    }

    @Test fun resellerImageOnlyNoGuessing() {
        assertNull(notice(PickWatchMode.RESELLER_TIPSTERS, "", image = true))
    }

    @Test fun skipRecaps() {
        assertNull(notice(PickWatchMode.RESELLER_TIPSTERS, "Tipster: Dr MLS\nGanado"))
        assertNull(notice(PickWatchMode.RESELLER_TIPSTERS, "Tipster: Dr MLS\nResultados"))
    }

    @Test fun noWatchByDefault() {
        assertNull(notice(PickWatchMode.OFF))
    }

    @Test fun notOwnOrEdited() {
        assertNull(notice(PickWatchMode.DIRECT_CHANNEL, outgoing = true))
        assertNull(notice(PickWatchMode.DIRECT_CHANNEL, edited = true))
    }

    @Test fun staleOrFuture() {
        assertNull(notice(PickWatchMode.DIRECT_CHANNEL, source = now - 4 * 3600 - 1))
        assertNull(notice(PickWatchMode.DIRECT_CHANNEL, source = now + 61))
    }

    @Test fun reasonableSkew() {
        assertNotNull(notice(PickWatchMode.DIRECT_CHANNEL, source = now + 20))
    }

    @Test fun blankUnknownSources() {
        assertNull(PickWatchRules.propose(PickWatchMode.DIRECT_CHANNEL, "", "texto", true, date, now))
        assertNull(PickWatchRules.propose(PickWatchMode.DIRECT_CHANNEL, "Cualquiera\nOtro", "texto", true, date, now))
    }

    @Test fun threeLinesLink() {
        val n = notice(PickWatchMode.RESELLER_TIPSTERS)!!
        assertEquals("Tipster: Dr MLS\nHora original: 22/10/2026 12:35 CDMX\nLink: https://t.me/c/123/4",
            n.render("https://t.me/c/123/4"))
        assertEquals(3, n.render(null).lines().size)
        assertTrue(n.render(null).endsWith("Link: No disponible"))
    }

    @Test fun rejectForgedLinks() {
        listOf("http://t.me/a", "https://fake-t.me/c/1",
            "https://t.me@bad.example/hi", "javascript:alert(1)", "https://t.me/",
            "https://t.me/\nBad").forEach { assertNull(PickWatchRules.checkedLink(it)) }
    }

    @Test fun acceptsOnlyTelegramUrl() {
        assertEquals("https://t.me/c/123/9", PickWatchRules.checkedLink("https://t.me/c/123/9"))
        assertEquals("https://telegram.me/x/4", PickWatchRules.checkedLink("https://telegram.me/x/4"))
    }

    @Test fun messageIdNotInferredFromText() {
        val n = notice(PickWatchMode.DIRECT_CHANNEL, "Mensaje t.me falso")!!
        assertTrue(n.render(null).contains("Link: No disponible"))
    }
}
