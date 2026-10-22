package mx.charly.canal

/** Pure planner. No client, file, notification, media bytes or network operation. */
object PickRelayPlanner {
    fun plan(batch: RelayBatch, config: RelayConfig, permissions: RelayPermissions, nowUnix: Long): RelayPlan {
        val messages = batch.messages
        fun drop(reason: String) = RelayPlan(RelayAction.DROP, reason)
        fun hold(reason: String) = RelayPlan(RelayAction.HOLD, reason)
        if (messages.isEmpty()) return drop("EMPTY")
        if (config.accountId <= 0L || messages.any { it.accountId != config.accountId }) return drop("ACCOUNT_MISMATCH")
        val sourceId = messages.first().sourceChatId
        if (sourceId == 0L || messages.any { it.sourceChatId != sourceId || it.messageId <= 0L }) return drop("SOURCE_OR_MESSAGE_ID")
        if (messages.map { it.messageId }.distinct().size != messages.size) return hold("DUPLICATE_ALBUM_MEMBER")
        if (!batch.liveUpdate) return drop("HISTORY_NOT_LIVE")
        if (messages.any { it.outgoing || it.edited }) return drop("OUTGOING_OR_EDITED")
        val bindings = config.sources.filter { it.accountId == config.accountId && it.chatId == sourceId && it.enabled }
        if (bindings.size != 1) return hold("SOURCE_NOT_EXACTLY_BOUND")
        val binding = bindings.single()
        if (binding.policy in setOf(RelaySourcePolicy.MAZDITA_LEGACY, RelaySourcePolicy.DIRECT_OTHER)) return hold("SOURCE_POLICY_UNAPPROVED")
        if (config.maxAgeSeconds !in 1..14400L || nowUnix <= 0L) return hold("INVALID_CLOCK_OR_TTL")
        if (messages.any { PickBridgeRules.originalDate(it.originalUnix) == "No disponible" }) return hold("ORIGINAL_DATE_UNAVAILABLE")
        if (messages.any { it.originalUnix > nowUnix || nowUnix - it.originalUnix > config.maxAgeSeconds }) return drop("STALE_OR_FUTURE")
        if (messages.any { it.kind == RelayContentKind.UNSUPPORTED }) return hold("UNSUPPORTED_CONTENT")
        val albumId = messages.first().albumId
        if (messages.any { it.albumId != albumId }) return hold("MIXED_ALBUM_IDS")
        if (albumId != 0L) {
            if (albumId < 0L || !batch.completeAlbum || batch.expectedAlbumSize != messages.size ||
                messages.size !in 2..10 || messages.any { it.kind != RelayContentKind.PHOTO }) return hold("ALBUM_INCOMPLETE")
        } else if (messages.size != 1) return hold("UNRELATED_MESSAGES")
        if (messages.all { it.kind == RelayContentKind.TEXT && it.text.isBlank() }) return drop("EMPTY_TEXT")
        val ordered = messages.sortedBy { it.messageId }
        val firstLine: String
        val verified: Boolean
        if (binding.policy.reseller) {
            val attributed = messages.map { attribution(it.text) }
            if (attributed.any { it.invalid }) return drop("UNKNOWN_OR_AMBIGUOUS_TIPSTER")
            val names = attributed.mapNotNull { it.name }.distinct()
            if (names.size != 1) return drop("TIPSTER_NOT_EXACTLY_VERIFIED")
            firstLine = "Tipster: ${names.single()}"
            verified = true
        } else {
            val title = binding.title.map { if (it.isISOControl() || it == '\u2028' || it == '\u2029') ' ' else it }.joinToString("").trim().take(100)
            if (title.isBlank()) return hold("SOURCE_TITLE_UNAVAILABLE")
            firstLine = "Canal: $title"
            verified = false
        }
        val protected = permissions.sourceHasProtectedContent || messages.any {
            it.hasProtectedContent || permissions.messageProtected[it.messageId] == true
        }
        val mayForward = !protected && messages.all {
            it.canBeForwarded && permissions.messageForwardable[it.messageId] == true && permissions.messageProtected[it.messageId] == false
        }
        val link = ordered.firstNotNullOfOrNull { PickBridgeRules.validTelegramLink(it.permalink) }
        val header = PickReference(firstLine, messages.minOf { it.originalUnix }).render(link)
        val key = RelayKey(config.accountId, sourceId, if (albumId != 0L) "a:$albumId" else "m:${ordered.single().messageId}")
        val candidate = RelayPlan(
            if (mayForward) RelayAction.FORWARD_ALLOWED_MEDIA else RelayAction.REFERENCE_ONLY,
            if (mayForward) "ORIGINALS_ALLOWED" else "REFERENCE_ONLY_NO_COPY",
            key, header, if (mayForward) ordered.map { it.messageId } else emptyList(), protected, verified, true
        )
        fun blocked(action: RelayAction, reason: String) = candidate.copy(action = action, reason = reason, messageIds = emptyList())
        val destination = config.destinationChatId
        if (destination != null && (destination == 0L || destination == sourceId || config.sources.any { it.chatId == destination }))
            return blocked(RelayAction.DROP, "DESTINATION_IS_SOURCE")
        if (!config.userEnabled) return blocked(RelayAction.HOLD, "USER_SWITCH_OFF")
        if (destination == null) return blocked(RelayAction.HOLD, "DESTINATION_NOT_SELECTED")
        if (permissions.accountId != config.accountId || permissions.sourceChatId != sourceId || permissions.destinationChatId != destination)
            return blocked(RelayAction.HOLD, "PERMISSION_SCOPE_MISMATCH")
        if (!permissions.canReadSource || !permissions.canWriteDestination)
            return blocked(RelayAction.HOLD, "CHAT_PERMISSION_UNKNOWN_OR_DENIED")
        return candidate
    }

    private data class Attribution(val name: String?, val invalid: Boolean)
    private val prefix = Regex("""^\s*(?:tipster|pronosticador|picks?\s+de|by)\s*[:#\-]\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
    private fun attribution(text: String): Attribution {
        val allowed = PickBridgeRules.allowedTipsters.associateBy { PickBridgeRules.normalized(it) }
        val found = mutableSetOf<String>()
        var invalid = false
        text.lineSequence().forEach { line ->
            val explicit = prefix.matchEntire(line.trim())?.groupValues?.get(1)
            val name = allowed[PickBridgeRules.normalized(explicit ?: line)]
            if (explicit != null && name == null) invalid = true
            if (name != null) found.add(name)
        }
        return Attribution(found.singleOrNull(), invalid || found.size > 1)
    }
}
