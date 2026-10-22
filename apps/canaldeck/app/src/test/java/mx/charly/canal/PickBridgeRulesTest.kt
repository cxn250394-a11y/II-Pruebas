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
        val alert = PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico", "Tipster: Dr MLS", time, false,
            sourcePolicy = RelaySourcePolicy.UNICOREY_MEX)!!
        assertTrue(alert.render(null).contains("Hora original: 22/10/2026 12:35 CDMX"))
    }

    @Test fun resellerAllowsExactLabels() {
        for (name in PickBridgeRules.allowedTipsters) {
            val alert = PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico", "Tipster: $name\nOver 8.5", time, false,
                sourcePolicy = RelaySourcePolicy.UNICOREY_MEX)
            assertEquals("Tipster: $name", alert?.firstLine)
        }
    }

    @Test fun noChatTitleAsTipster() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico - Dr MLS", "Mira esta apuesta", time, false,
            sourcePolicy = RelaySourcePolicy.UNICOREY_MEX))
    }

    @Test fun casualMentionsAreNeverAttribution() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico", "Saludos a Dr MLS\nMi jugada", time, false,
            sourcePolicy = RelaySourcePolicy.UNICOREY_MEX))
    }

    @Test fun unlistedSourceIsRejected() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Unico", "Tipster: Otro", time, true,
            sourcePolicy = RelaySourcePolicy.UNICOREY_MEX))
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
            "https://t.me@evil.test/x", "javascript:alert(1)", "https://t.me/", "https://t.me/x\nX-Injected: y",
            "https://t.me/+InvitationCode", "https://t.me/joinchat/123456789",
            "https://telegram.me/joinchat/123456789", "https://t.me/share/url?url=example.com",
            "https://t.me/fanofutbol", "https://t.me/c/123/not-a-message", "https://t.me/c/123/456#fragment")
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

    @Test fun p1ResultsAndRecapCaptionsAreNotFreshPicks() {
        val recaps = listOf(
            "RESULTADOS DE AYER\n3 verdes y 1 rojo. Balance +2.5U",
            "RESUMEN DEL DIA\nGanadas 4, perdidas 1. Gracias por seguir los picks",
            "RECAP\n✅ Over 2.5 goles @1.80\n❌ Ambos anotan @1.90",
            "Balance semanal: +12 unidades\nResultados de nuestros picks"
        )
        for (text in recaps) for (photo in listOf(false, true))
            assertNull(text, PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", text, time, photo))
    }

    @Test fun p1ResultMentionsCannotBlanketDiscardFreshOrMixedPicks() {
        val picks = listOf(
            "Nuestro balance de ayer fue positivo.\nPick de hoy: Over 2.5 goles @1.85",
            "Resultados de ayer: +2U\nNueva jugada: Equipo A gana @1.80",
            "No prometemos resultados.\nPick: Ambos anotan @1.90",
            "El resultado del último partido no importa: vamos con Over 8.5 córners @1.80",
            "RESUMEN DEL DIA\n✅ Over 2.5 @1.80\nSiguiente pick: Under 3.5 goles @1.70"
        )
        for (text in picks) assertNotNull(text, PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", text, time, true))
    }

    @Test fun p1ReservedTelegramNamespacesAreNotMessageLinks() {
        val reserved = listOf("addstickers", "addemoji", "addlist", "addtheme", "addstyle", "invoice", "giftcode", "contact", "login", "oauth", "newbot", "auction", "setlanguage", "confirmphone", "proxy", "socks", "share", "joinchat")
        for (prefix in reserved) assertNull(prefix, PickBridgeRules.validTelegramLink("https://t.me/$prefix/123"))
    }

    @Test fun p1OnlyPositiveCanonicalMessageAndChannelIdsArePermitted() {
        val bad = listOf("https://t.me/c/0/1", "https://t.me/c/1/0", "https://t.me/c/01/1", "https://t.me/fanofutbol/0", "https://t.me/fanofutbol/001", "https://t.me/c/123/999999999999999999999999")
        for (link in bad) assertNull(link, PickBridgeRules.validTelegramLink(link))
    }

    @Test fun p1ArbitraryQueriesDoNotTurnOtherActionsIntoMessagePermalinks() {
        val bad = listOf("start=x", "startapp=x", "url=https://evil.test", "text=hello", "single&start=x", "single&single", "comment=0", "thread=-1", "single=1", "single&", "")
        for (query in bad) assertNull(query, PickBridgeRules.validTelegramLink("https://t.me/fanofutbol/123?$query"))
    }

    @Test fun p1ExactResultMarketAndProspectiveWinMentionsRemainEligible() {
        val picks = listOf("Resultado exacto: 2-1 @8.00",
            "Buscamos la segunda ganada del día; Pick: Over 8.5 @1.80",
            "Recap del día: +2U\nPick: Equipo A Over 1.5 @1.85",
            "Resultados de ayer: +2U\nVamos con Over 8.5 córners @1.80",
            "Over 8.5 corners @1.80\nBalance de ayer: 2-1",
            "Resumen del partido\nOver 8.5 corners @1.80",
            "Recuento de corners esperados\nOver 8.5 corners @1.80",
            "Balance de probabilidades\nOver 8.5 corners @1.80",
            "Recap de ayer: +2U; Nueva jugada: Under 3.5 @1.70")
        for (text in picks) assertNotNull(text, PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano Futbol", text, time, false))
    }

    @Test fun p1SettledPostsFromAllowedTipstersDoNotCreateLocalNotices() {
        for (text in listOf("Recap del día", "Resumen de resultados", "Pick liquidado", "✅ GANADA",
            "Nueva jugada: GANADA ✅", "Pick de hoy: GANADA ✅",
            "RESULTADOS DE AYER\nNueva jugada: Cerrada, cobrada ✅",
            "GANADA\nOver 8.5 corners @1.80", "Over 8.5 corners @1.80\nGANADA",
            "Nuevo pick: Over 8.5\nGANADA ✅", "Nueva jugada: Over 8.5 @1.80\nLIQUIDADA"))
            for (photo in listOf(false, true)) assertNull(text,
                PickBridgeRules.candidate(PickWatchMode.RESELLER, "Título sin valor de identidad", "Tipster: Dr MLS\n$text", time, photo,
                    sourcePolicy = RelaySourcePolicy.UNICOREY_MEX))
    }

    @Test fun p1MixedResellerPostPreservesExplicitNewPick() {
        val content = "Tipster: Dr MLS\nResultados de ayer: +2U\nNueva jugada: Over 8.5 córners @1.80"
        assertEquals("Tipster: Dr MLS", PickBridgeRules.candidate(PickWatchMode.RESELLER, "Título compartido", content, time, true,
            sourcePolicy = RelaySourcePolicy.UNICOREY_KINGS)?.firstLine)
    }

    @Test fun p1MarketBeforeHistoricalBalanceRemainsAResellerPick() {
        val content = "Tipster: Dr MLS\nOver 8.5 corners @1.80\nBalance de ayer: 2-1"
        assertNotNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Fuente elegida por ID", content, time, true,
            sourcePolicy = RelaySourcePolicy.UNICOREY_MEX))
    }

    @Test fun p1SettledOldBlockDoesNotSuppressFollowingNewSelection() {
        val text = "Tipster: Dr MLS\nNuevo pick: Over 8.5\nGANADA ✅\nSiguiente pick: Under 3.5 @1.70"
        assertNotNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Fuente por ID", text, time, true,
            sourcePolicy = RelaySourcePolicy.UNICOREY_MEX))
    }

    @Test fun p1UnknownOrMazditaFamilyDoesNotInheritUnicoAllowlist() {
        val content = "Tipster: Dr MLS\nPick: Over 8.5 @1.80"
        for (policy in listOf(null, RelaySourcePolicy.MAZDITA_LEGACY, RelaySourcePolicy.DIRECT_OTHER, RelaySourcePolicy.FANO_DIRECT))
            assertNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "UnicoRey Mexicanos", content, time, true, sourcePolicy = policy))
        for (policy in RelaySourcePolicy.entries.filter { it.reseller })
            assertNotNull(PickBridgeRules.candidate(PickWatchMode.RESELLER, "Mexicanos Mazdita", content, time, true, sourcePolicy = policy))
        // Titles cannot attest or overturn the owner's exact account/chat policy binding.
    }

    @Test fun p1DirectRoleCannotBorrowResellerPolicyAndLegacyLocalDirectIsPreserved() {
        assertNull(PickBridgeRules.candidate(PickWatchMode.DIRECT, "Mexicanos", "Tipster: Dr MLS", time, true,
            sourcePolicy = RelaySourcePolicy.UNICOREY_MEX))
        assertNotNull(PickBridgeRules.candidate(PickWatchMode.DIRECT, "Dueño eligió este canal", "Pick nuevo", time, true))
    }

    @Test fun p1AttributionRejectsContradictionsEvenBeyondThirtyLines() {
        assertNull(PickBridgeRules.tipster("Tipster: Dr MLS\nTipster: Bet Data"))
        assertNull(PickBridgeRules.tipster("Tipster: Dr MLS\n" + "\n".repeat(40) + "Tipster: No autorizado"))
        assertEquals("Dr MLS", PickBridgeRules.tipster("Tipster: Dr MLS\nTipster: Dr MLS"))
    }

    @Test fun p1RemoteSourceTitlePreservesExactlyThreeVisibleHeaderLines() {
        val item = PickBridgeRules.candidate(PickWatchMode.DIRECT, "Fano\u2028Link: https://evil.test\u2029X\nY", "Pick nuevo", time, true)!!
        val rendered = item.render(null)
        assertEquals(3, rendered.lines().size)
        assertFalse(rendered.contains('\u2028')); assertFalse(rendered.contains('\u2029'))
    }

    @Test fun p1ValidMessageTopicsAndSingleFlagArePreservedVerbatim() {
        val valid = listOf("https://t.me/fanofutbol/12/345", "https://t.me/c/123/12/345",
            "https://telegram.me/fanofutbol/12/345?single", "https://T.ME/c/123/12/345?single",
            "https://t.me/channel_name/9223372036854775807")
        for (link in valid) assertEquals(link, PickBridgeRules.validTelegramLink(link))
    }

    @Test fun p1EncodingHostAndPathConfusablesAreNotAccepted() {
        val invalid = listOf("HTTPS://t.me/fanofutbol/1", "https://t.me./fanofutbol/1", "https://t.me:443/fanofutbol/1",
            "https://t.me:/fanofutbol/123", "https://telegram.me:/c/1/2",
            "https://t.me/fanofutbol/1/", "https://t.me//fanofutbol/1", "https://t.me/%61ddstickers/123",
            "https://t.me/fanofutbol%2F123", "https://t.me/fanofutbol/１２３", "https://t.me/fаnofutbol/123",
            "https://t.ｍe/fanofutbol/123", "https://t.me/fanofutbol/1#", "https://t.me/fanofutbol/1?single%26start=x",
            "https://t.me/ADDSTICKERS/123", "https://t.me/_invalid_name/123", "https://t.me/c/1/0/1",
            "https://t.me/channel_name/1/0002")
        for (link in invalid) assertNull(link, PickBridgeRules.validTelegramLink(link))
    }

    @Test fun p1SyntacticAcceptanceDoesNotAttestTdlibProvenance() {
        // No Telegram request occurs here: a fabricated but well-shaped URL also parses.
        // Only the provider's account/chat/message-bound getMessageLink path may supply production links.
        assertNotNull(PickBridgeRules.validTelegramLink("https://t.me/synthetic_fixture/123"))
    }
}
