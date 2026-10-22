package mx.charly.canal

import org.junit.Assert.*
import org.junit.Test

class PickRelayPlannerTest {
    private val f = RelayTestFixtures
    private fun plan(m: RelayMessage = f.message, cfg: RelayConfig = f.config, permissions: RelayPermissions = f.permissions(listOf(m))) =
        PickRelayPlanner.plan(f.batch(m), cfg, permissions, f.now)

    @Test fun defaultsKeepOutputOff() {
        assertEquals(RelayAction.HOLD, plan(cfg = RelayConfig(1)).action)
        assertFalse(RelayConfig(1).userEnabled)
        assertFalse(RelaySourceBinding(1, -111, "Fano Futbol", RelaySourcePolicy.FANO_DIRECT).enabled)
    }
    @Test fun fanoTextUsesThreeOriginalMetadataLines() {
        val p = plan()
        assertEquals(RelayAction.FORWARD_ALLOWED_MEDIA, p.action)
        assertEquals("Canal: Fano Futbol\nHora original: 22/10/2026 12:59 CDMX\nLink: https://t.me/example_source/101", p.header)
        assertFalse(p.header!!.contains("Texto sintético"))
        assertEquals(listOf(101L), p.messageIds)
    }
    @Test fun fanoPhotoWithoutCaptionAllowedWhenEveryPermissionKnown() {
        assertEquals(RelayAction.FORWARD_ALLOWED_MEDIA, plan(f.message.copy(text = "", kind = RelayContentKind.PHOTO)).action)
    }
    @Test fun twelveExactAllowlistedNamesWorkInAllFourUnicoPolicies() {
        val policies = RelaySourcePolicy.entries.filter { it.reseller }
        assertEquals(4, policies.size)
        assertEquals(12, PickBridgeRules.allowedTipsters.size)
        for (policy in policies) for (name in PickBridgeRules.allowedTipsters) {
            val p = plan(f.message.copy(text = "Tipster: $name\nJugada sintética"),
                f.config.copy(sources = listOf(f.source.copy(policy = policy))))
            assertEquals(RelayAction.FORWARD_ALLOWED_MEDIA, p.action)
            assertEquals("Tipster: $name", p.header!!.lineSequence().first())
            assertTrue(p.tipsterVerified)
        }
    }
    @Test fun fourteenPlusUnlistedOrMisleadingAuthorsNeverPass() {
        val bad = listOf("Otro", "Dr MLS VIP", "DrMLS", "Diego Palafox VIP", "Lara Basado", "Zorrito",
            "Koala", "Revolver", "Renne", "Rodrigo", "Alfredo", "Tennis", "Sharp", "BetData", "UnicoRey", "Mazdita")
        assertTrue(bad.size >= 14)
        for (name in bad) assertEquals(RelayAction.DROP,
            plan(f.message.copy(text = "Tipster: $name"), f.config.copy(sources = listOf(f.source.copy(policy = RelaySourcePolicy.UNICOREY_MEX)))).action)
    }
    @Test fun resellerDoesNotInferAttributionFromTitleOrCasualMention() {
        val c = f.config.copy(sources = listOf(f.source.copy(title = "Dr MLS", policy = RelaySourcePolicy.UNICOREY_MEX)))
        assertEquals(RelayAction.DROP, plan(f.message.copy(text = "Saludos a Dr MLS"), c).action)
        assertEquals(RelayAction.DROP, plan(f.message.copy(text = "", kind = RelayContentKind.PHOTO), c).action)
    }
    @Test fun contradictoryAndUnlistedAttributionsAreRejected() {
        val c = f.config.copy(sources = listOf(f.source.copy(policy = RelaySourcePolicy.UNICOREY_MEX)))
        assertEquals(RelayAction.DROP, plan(f.message.copy(text = "Tipster: Dr MLS\nTipster: Bet Data"), c).action)
        assertEquals(RelayAction.DROP, plan(f.message.copy(text = "Tipster: Dr MLS\nTipster: No autorizado"), c).action)
    }
    @Test fun mazditaNeverInheritsUnicoAllowlist() {
        val c = f.config.copy(sources = listOf(f.source.copy(policy = RelaySourcePolicy.MAZDITA_LEGACY)))
        assertEquals("SOURCE_POLICY_UNAPPROVED", plan(f.message.copy(text = "Tipster: Dr MLS"), c).reason)
    }
    @Test fun unknownDirectPolicyAlsoHolds() {
        assertEquals(RelayAction.HOLD, plan(cfg = f.config.copy(sources = listOf(f.source.copy(policy = RelaySourcePolicy.DIRECT_OTHER)))).action)
    }
    @Test fun duplicateTitlesDoNotBindAnotherChat() {
        assertEquals(RelayAction.HOLD, plan(f.message.copy(sourceChatId = -222), permissions = f.permissions(sourceChat = -222)).action)
    }
    @Test fun unknownSourceAndDuplicateBindingsHold() {
        assertEquals(RelayAction.HOLD, plan(cfg = f.config.copy(sources = emptyList())).action)
        assertEquals(RelayAction.HOLD, plan(cfg = f.config.copy(sources = listOf(f.source, f.source))).action)
    }
    @Test fun sourceMustBeEnabledForTheExactAccount() {
        assertEquals(RelayAction.HOLD, plan(cfg = f.config.copy(sources = listOf(f.source.copy(enabled = false)))).action)
        assertEquals(RelayAction.HOLD, plan(cfg = f.config.copy(sources = listOf(f.source.copy(accountId = 2)))).action)
    }
    @Test fun crossAccountOutgoingAndEditedMessagesDrop() {
        assertEquals(RelayAction.DROP, plan(f.message.copy(accountId = 2)).action)
        assertEquals(RelayAction.DROP, plan(f.message.copy(outgoing = true)).action)
        assertEquals(RelayAction.DROP, plan(f.message.copy(edited = true)).action)
    }
    @Test fun destinationNeverMayBeAnySelectedSource() {
        assertEquals(RelayAction.DROP, plan(cfg = f.config.copy(destinationChatId = -111)).action)
        assertEquals(RelayAction.DROP, plan(cfg = f.config.copy(sources = listOf(f.source, f.source.copy(chatId = -999, enabled = false)))).action)
    }
    @Test fun destinationMustBeChosen() {
        assertEquals(RelayAction.HOLD, plan(cfg = f.config.copy(destinationChatId = null)).action)
    }
    @Test fun historyCannotBecomeLiveDelivery() {
        val p = PickRelayPlanner.plan(f.batch(f.message).copy(liveUpdate = false), f.config, f.permissions(), f.now)
        assertEquals("HISTORY_NOT_LIVE", p.reason)
    }
    @Test fun missingDateHoldsAndStaleFutureDatesDrop() {
        assertEquals(RelayAction.HOLD, plan(f.message.copy(originalUnix = 0)).action)
        assertEquals(RelayAction.DROP, plan(f.message.copy(originalUnix = f.now - 14401)).action)
        assertEquals(RelayAction.DROP, plan(f.message.copy(originalUnix = f.now + 1)).action)
    }
    @Test fun noMissingOrWrongChatPermissions() {
        assertEquals(RelayAction.HOLD, plan(permissions = f.permissions().copy(canReadSource = false)).action)
        assertEquals(RelayAction.HOLD, plan(permissions = f.permissions().copy(canWriteDestination = false)).action)
        assertEquals(RelayAction.HOLD, plan(permissions = f.permissions().copy(accountId = 2)).action)
        assertEquals(RelayAction.HOLD, plan(permissions = f.permissions().copy(destinationChatId = -777)).action)
    }
    @Test fun everyProtectedOrUnforwardableMessageIsReferenceOnly() {
        assertEquals(RelayAction.REFERENCE_ONLY, plan(f.message.copy(hasProtectedContent = true)).action)
        assertEquals(RelayAction.REFERENCE_ONLY, plan(f.message.copy(canBeForwarded = false)).action)
        assertEquals(RelayAction.REFERENCE_ONLY, plan(permissions = f.permissions().copy(sourceHasProtectedContent = true)).action)
        assertEquals(RelayAction.REFERENCE_ONLY, plan(permissions = f.permissions().copy(messageProtected = emptyMap())).action)
        assertEquals(RelayAction.REFERENCE_ONLY, plan(permissions = f.permissions().copy(messageForwardable = emptyMap())).action)
        assertTrue(plan(f.message.copy(hasProtectedContent = true)).messageIds.isEmpty())
    }
    @Test fun unavailableLinkIsNotInvented() {
        val p = plan(f.message.copy(permalink = null, hasProtectedContent = true))
        assertTrue(p.header!!.endsWith("Link: No disponible"))
        assertEquals(RelayAction.REFERENCE_ONLY, p.action)
    }
    @Test fun invitationsAndInvalidLinksNeverEscapeIntoHeader() {
        val bad = listOf("https://t.me/+Invite123", "https://t.me/joinchat/123", "https://t.me/example_source", "https://evil.test/x", "https://t.me/share/url?url=evil", "https://t.me/c/111/101#x")
        for (link in bad) assertTrue(plan(f.message.copy(permalink = link)).header!!.endsWith("Link: No disponible"))
    }
    @Test fun completePhotoAlbumHasOneHeaderAndSortedOriginalIds() {
        val a = f.message.copy(kind = RelayContentKind.PHOTO, albumId = 17)
        val b = a.copy(messageId = 102)
        val p = PickRelayPlanner.plan(f.batch(b, a, album = true, size = 2), f.config, f.permissions(listOf(a, b)), f.now)
        assertEquals(RelayAction.FORWARD_ALLOWED_MEDIA, p.action)
        assertEquals("a:17", p.key!!.groupId)
        assertEquals(listOf(101L, 102L), p.messageIds)
        assertEquals(3, p.header!!.lines().size)
    }
    @Test fun partialAlbumUnknownCardinalityMixedOrDuplicateMembersHold() {
        val a = f.message.copy(kind = RelayContentKind.PHOTO, albumId = 17)
        val b = a.copy(messageId = 102)
        val cases = listOf(f.batch(a), f.batch(a, b, size = 2), f.batch(a, b, album = true),
            f.batch(a, b, album = true, size = 3), f.batch(a, a, album = true, size = 2),
            f.batch(a, b.copy(albumId = 18), album = true, size = 2))
        for (batch in cases) assertEquals(RelayAction.HOLD, PickRelayPlanner.plan(batch, f.config, f.permissions(listOf(a,b)), f.now).action)
    }
    @Test fun aSingleProtectedMemberDowngradesEntireAlbum() {
        val a = f.message.copy(kind = RelayContentKind.PHOTO, albumId = 17)
        val b = a.copy(messageId = 102, hasProtectedContent = true)
        val p = PickRelayPlanner.plan(f.batch(a, b, album = true, size = 2), f.config, f.permissions(listOf(a,b)), f.now)
        assertEquals(RelayAction.REFERENCE_ONLY, p.action)
        assertTrue(p.messageIds.isEmpty())
    }
    @Test fun directTitleCannotInjectExtraHeaderLines() {
        val c = f.config.copy(sources = listOf(f.source.copy(title = "Fano\nX\u2028Y\u2029Z")))
        val p = plan(cfg = c)
        assertEquals(3, p.header!!.lines().size)
        assertFalse(p.header!!.contains('\u2028'))
        assertFalse(p.header!!.contains('\u2029'))
    }
    @Test fun contradictoryAuthorAfterThirtyLinesStillRejects() {
        val c = f.config.copy(sources = listOf(f.source.copy(policy = RelaySourcePolicy.UNICOREY_MEX)))
        val text = "Tipster: Dr MLS\n" + "\n".repeat(30) + "Tipster: No autorizado"
        assertEquals(RelayAction.DROP, plan(f.message.copy(text = text), c).action)
    }
    @Test fun emptyAndUnsupportedPayloadsAreNotSent() {
        assertEquals(RelayAction.DROP, plan(f.message.copy(text = "")).action)
        assertEquals(RelayAction.HOLD, plan(f.message.copy(kind = RelayContentKind.UNSUPPORTED)).action)
        assertEquals(RelayAction.DROP, PickRelayPlanner.plan(f.batch(), f.config, f.permissions(), f.now).action)
    }
}
