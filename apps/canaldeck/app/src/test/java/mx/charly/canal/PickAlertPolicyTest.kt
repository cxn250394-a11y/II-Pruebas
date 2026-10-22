package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class PickAlertPolicyTest {
    private val epoch = 1792694100L

    @Test fun approvedTwelveNamesNotInvented() {
        assertEquals(12, PickAlertPolicy.unicoreyTipsters.size)
        assertEquals("Dr MLS", PickAlertPolicy.tipsterFrom("Tipster: Dr MLS\nOver 8.5"))
        assertEquals("Diego Palafox", PickAlertPolicy.tipsterFrom("PRONOSTICADOR: DIEGO PALAFOX"))
        assertEquals("Renne NFL", PickAlertPolicy.tipsterFrom("by: renne nfl"))
        assertNull(PickAlertPolicy.tipsterFrom("Tipster: Fano Futbol"))
        assertNull(PickAlertPolicy.tipsterFrom("Saludos a Dr MLS."))
    }

    @Test fun resellerIsTipsterWithExactThreeLines() {
        val candidate = PickAlertPolicy.candidate(
            PickSourceMode.TIPSTER, "🇲🇽 Mexicanos - OCTUBRE",
            "Tipster: Dr MLS\nCórners 8.5", true, epoch
        )
        assertNotNull(candidate)
        assertEquals(
            "Tipster: Dr MLS\nHora original: 22/10/2026 12:35 CDMX\nLink: https://t.me/c/12345/77",
            PickAlertPolicy.render(candidate!!,"https://t.me/c/12345/77")
        )
    }

    @Test fun fanoDirectImageAndCaption() {
        val c = PickAlertPolicy.candidate(
            PickSourceMode.CANAL, "Fano Futbol", "SOT Over 6.5", true, epoch
        )
        assertNotNull(c)
        assertTrue(PickAlertPolicy.render(c!!, "https://t.me/FanoCanal/42").startsWith("Canal: Fano Futbol\n"))
    }

    @Test fun directCaptionlessPhotoAllowed() {
        assertNotNull(PickAlertPolicy.candidate(
            PickSourceMode.CANAL, "Fano Futbol", "", true, epoch
        ))
    }

    @Test fun resellerWithoutExplicitTipsterIsHeld() {
        assertNull(PickAlertPolicy.candidate(PickSourceMode.TIPSTER,
            "Los Reyes", "Nueva selección de Dr MLS esta noche", true, epoch))
        assertNull(PickAlertPolicy.candidate(PickSourceMode.TIPSTER,
            "Los Reyes", "", true, epoch))
    }

    @Test fun resultPostsSkipped() {
        assertNull(PickAlertPolicy.candidate(PickSourceMode.TIPSTER,
            "Mexicanos", "Tipster: Dr MLS\nRESULTADO GANADO", true, epoch))
    }

    @Test fun offOutgoingEditedSkipped() {
        assertNull(PickAlertPolicy.candidate(PickSourceMode.OFF,
            "Fano Futbol", "Pick 8", true, epoch))
        assertNull(PickAlertPolicy.candidate(PickSourceMode.CANAL,
            "Fano Futbol", "Pick 8", true, epoch, outgoing=true))
        assertNull(PickAlertPolicy.candidate(PickSourceMode.CANAL,
            "Fano Futbol", "Pick 8", true, epoch, changed=true))
    }

    @Test fun noInventedLinkWhenMissing() {
        val c=PickAlertCandidate("Canal: Fano Futbol", epoch)
        assertEquals("Canal: Fano Futbol\nHora original: 22/10/2026 12:35 CDMX\nLink: No disponible",
            PickAlertPolicy.render(c,null))
    }

    @Test fun rejectUnsafeUrlsOrInvites() {
        listOf(
            "http://t.me/FanoCanal/42", "https://t.me@evil.invalid/FanoCanal/42",
            "https://evil.invalid/c/12345/77", "https://t.me/+secretInvite",
            "https://t.me/FanoCanal", "javascript:alert(1)",
            "https://t.me/c/abc/77", "https://t.me/c/12345/77#fragment"
        ).forEach { assertNull(PickAlertPolicy.canonicalMessageLink(it)) }
    }

    @Test fun normalizeCasesAndAccents() {
        assertEquals("LaraBasado", PickAlertPolicy.tipsterFrom("Tipster: larabasado"))
        assertEquals("Tennis Insider", PickAlertPolicy.tipsterFrom("TENNIS INSIDER"))
        assertNull(PickAlertPolicy.tipsterFrom("BET DATA GANÓ"))
    }

    @Test fun staleDateRejected() {
        assertNull(PickAlertPolicy.candidate(PickSourceMode.CANAL,"Fano Futbol","ok",
            true,0))
    }

    @Test fun noUnsanitizedChannelName() {
        assertNull(PickAlertPolicy.candidate(PickSourceMode.CANAL,"Fano\nFutbol",
            "Pick",true,epoch))
    }
}
