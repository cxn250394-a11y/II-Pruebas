package mx.charly.canal

/** Singleton-only preparation. It does not classify an image as a pick or attest a live account. */
internal object SingleRelayPlanning {
    data class Evaluation(val fresh: SingleRelayFreshState, val plan: RelayPlan, val semantics: String)

    fun evaluate(observed: SingleRelayObserved, value: SingleRelayFreshState, nowUnix: Long,
                 mode: SingleRelayPortMode): Evaluation {
        val raw = value.state
        val fresh = value.copy(state = raw.copy(
            batch = raw.batch.copy(messages = raw.batch.messages.toList(), completeAlbum = false, expectedAlbumSize = null),
            config = raw.config.copy(sources = raw.config.sources.toList()),
            permissions = raw.permissions.copy(messageForwardable = raw.permissions.messageForwardable.toMap(),
                messageProtected = raw.permissions.messageProtected.toMap())))
        fun hold(reason: String, semantics: String = "TEXT_NOT_CLASSIFIED") =
            Evaluation(fresh, RelayPlan(RelayAction.HOLD, reason, observed.key), semantics)
        val state = fresh.state
        if (!fresh.connected || state.scope.accountId != observed.accountId || state.scope.sessionId != observed.sessionId ||
            state.config.accountId != observed.accountId || state.config.destinationChatId != state.scope.destinationChatId ||
            state.configurationRevision < 0 || state.configurationRevision == Long.MAX_VALUE ||
            state.batch.synthetic != (mode == SingleRelayPortMode.FAKE)) return hold("SESSION_OR_LANE_MISMATCH")
        val message = state.batch.messages.singleOrNull() ?: return hold("SINGLETON_REQUIRED")
        if (message.accountId != observed.accountId || message.sourceChatId != observed.sourceChatId ||
            message.messageId != observed.messageId || message.originalUnix != observed.originalUnix)
            return hold("OBSERVED_MESSAGE_MISMATCH")
        if (message.albumId != 0L || observed.albumId != 0L) return hold("LIVE_ALBUM_COMPLETENESS_UNPROVEN")
        val binding = state.config.sources.singleOrNull {
            it.enabled && it.accountId == observed.accountId && it.chatId == observed.sourceChatId
        } ?: return hold("SOURCE_NOT_EXACTLY_BOUND")
        if (binding.policy != RelaySourcePolicy.FANO_DIRECT && !binding.policy.reseller)
            return hold("SOURCE_POLICY_UNAPPROVED")
        val photoOnly = message.kind == RelayContentKind.PHOTO && message.text.isBlank()
        val semantics = when {
            photoOnly -> "UNKNOWN_IMAGE_SEMANTICS"
            message.kind == RelayContentKind.PHOTO -> "CAPTION_NOT_CLASSIFIED"
            else -> "TEXT_NOT_CLASSIFIED"
        }
        if (photoOnly && binding.policy.reseller) return hold("PHOTO_TIPSTER_UNVERIFIED", semantics)
        if (photoOnly && !fresh.photoOnlyConsent) return hold("PHOTO_ONLY_CONSENT_REQUIRED", semantics)
        var plan = PickRelayPlanner.plan(state.batch, state.config, state.permissions, nowUnix)
        if (plan.action !in setOf(RelayAction.REFERENCE_ONLY, RelayAction.FORWARD_ALLOWED_MEDIA))
            return Evaluation(fresh, plan, semantics)
        // A reference must actually lead back to the selected publication. The native port
        // obtains this URL from getMessageLink for exactly the selected account/chat/message.
        if (photoOnly && PickBridgeRules.validTelegramLink(message.permalink) == null)
            return hold("PHOTO_ONLY_REFERENCE_LINK_UNAVAILABLE", semantics)
        if (message.kind == RelayContentKind.TEXT || plan.action == RelayAction.REFERENCE_ONLY) {
            if (PickBridgeRules.validTelegramLink(message.permalink) == null)
                return hold("REFERENCE_LINK_UNAVAILABLE", semantics)
            plan = plan.copy(action = RelayAction.REFERENCE_ONLY, reason = "SINGLE_REFERENCE_ONLY", messageIds = emptyList())
        }
        if (photoOnly) plan = plan.copy(header = plan.header + "\nImagen sin texto; contenido no verificado.")
        return Evaluation(fresh, plan, semantics)
    }
}
