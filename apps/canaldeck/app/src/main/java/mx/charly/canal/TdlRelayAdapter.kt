package mx.charly.canal

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CancellationException

/**
 * Read-only snapshots and dormant official operations on CanalDeck's existing client.
 * No second client, downloads, OCR, copied media, retry worker or stored message body.
 * Production code only calls snapshot; the delivery method is compiled closed in v0.4.
 */
internal class TdlRelayAdapter(
    private val client: TdlClient,
    private val currentAccount: () -> Long,
    private val isReady: () -> Boolean,
    private val persistedConfigStillMatches: (RelayConfig) -> Boolean
) {
    private fun sameSession(account: Long) = account > 0L && isReady() && currentAccount() == account

    private fun member(status: ChatMemberStatus): Boolean = when (status) {
        is ChatMemberStatusCreator -> status.isMember
        is ChatMemberStatusAdministrator, is ChatMemberStatusMember -> true
        is ChatMemberStatusRestricted -> status.isMember
        else -> false
    }

    private suspend fun sourceReadable(chat: Chat): Boolean = when (val type = chat.type) {
        is ChatTypeSupergroup -> when (val group = client.getSupergroup(type.supergroupId)) {
            is TdlResult.Success -> group.result.id == type.supergroupId && member(group.result.status)
            else -> false
        }
        is ChatTypeBasicGroup -> when (val group = client.getBasicGroup(type.basicGroupId)) {
            is TdlResult.Success -> group.result.id == type.basicGroupId && group.result.isActive && member(group.result.status)
            else -> false
        }
        else -> false
    }

    /** Only a channel's actual creator/posting administrator may be the Pick$ destination. */
    private suspend fun destinationWritable(chat: Chat): Boolean {
        val type = chat.type as? ChatTypeSupergroup ?: return false
        if (!type.isChannel) return false
        val result = client.getSupergroup(type.supergroupId)
        if (result !is TdlResult.Success || result.result.id != type.supergroupId || !result.result.isChannel ||
            result.result.paidMessageStarCount != 0L) return false
        return when (val status = result.result.status) {
            is ChatMemberStatusCreator -> status.isMember
            is ChatMemberStatusAdministrator -> status.rights.canPostMessages
            else -> false
        }
    }

    suspend fun snapshot(
        messages: List<Message>, albumComplete: Boolean, config: RelayConfig
    ): Pair<RelayBatch, RelayPermissions>? {
        if (messages.isEmpty() || messages.size > 10 || !sameSession(config.accountId)) return null
        val source = messages.first().chatId
        if (source == 0L || messages.any { it.chatId != source || it.id <= 0L } ||
            messages.map { it.id }.distinct().size != messages.size ||
            config.sources.count { it.accountId == config.accountId && it.chatId == source && it.enabled } != 1)
            return null
        return snapshotIds(source, messages.map { it.id }, albumComplete, config)
    }

    private suspend fun snapshotIds(
        sourceId: Long, messageIds: List<Long>, albumComplete: Boolean, config: RelayConfig
    ): Pair<RelayBatch, RelayPermissions>? {
        if (!sameSession(config.accountId)) return null
        val sourceResult = client.getChat(sourceId)
        if (sourceResult !is TdlResult.Success || sourceResult.result.id != sourceId) return null
        val source = sourceResult.result
        val sourceAllowed = sourceReadable(source)
        val destination = config.destinationChatId ?: 0L
        var destinationAllowed = false
        if (destination != 0L && destination != sourceId) {
            val chat = client.getChat(destination)
            if (chat is TdlResult.Success && chat.result.id == destination)
                destinationAllowed = destinationWritable(chat.result)
        }
        val relay = mutableListOf<RelayMessage>()
        val forwardable = linkedMapOf<Long, Boolean>()
        val protected = linkedMapOf<Long, Boolean>()
        for (id in messageIds) {
            val response = client.getMessage(chatId = sourceId, messageId = id)
            if (response !is TdlResult.Success) return null
            val message = response.result
            if (message.chatId != sourceId || message.id != id || !sameSession(config.accountId)) return null
            val propertiesResult = client.getMessageProperties(chatId = sourceId, messageId = id)
            val properties = if (propertiesResult is TdlResult.Success) propertiesResult.result else null
            val knownProtected = source.hasProtectedContent || message.selfDestructType != null ||
                properties?.hasProtectedContentByCurrentUser == true ||
                properties?.hasProtectedContentByOtherUser == true
            // Missing properties deny media but must not invent a measured protected post.
            val allowed = properties?.canBeForwarded == true && !knownProtected
            val text: String
            val kind: RelayContentKind
            when (val content = message.content) {
                is MessageText -> { text = content.text.text; kind = RelayContentKind.TEXT }
                is MessagePhoto -> { text = content.caption.text; kind = RelayContentKind.PHOTO }
                else -> { text = ""; kind = RelayContentKind.UNSUPPORTED }
            }
            var link: String? = null
            if (properties?.canGetLink == true) {
                val result = client.getMessageLink(chatId = sourceId, messageId = id,
                    mediaTimestamp = 0, checklistTaskId = 0, pollOptionId = "",
                    forAlbum = false, inMessageThread = false)
                if (result is TdlResult.Success) link = PickBridgeRules.validTelegramLink(result.result.link)
            }
            // Original means the selected source post's TDLib date, never the processing clock.
            relay += RelayMessage(config.accountId, sourceId, id, message.date.toLong(), text, kind,
                message.mediaAlbumId, message.isOutgoing || message.sendingState != null ||
                    message.schedulingState != null, message.editDate > 0, allowed, knownProtected, link)
            forwardable[id] = allowed
            if (properties != null || knownProtected) protected[id] = knownProtected
        }
        if (!sameSession(config.accountId)) return null
        // TDLib 14.0.0 has no authoritative album member count. A caller's timeout or a
        // partial list must never turn a live album into a supposedly complete one.
        val single = relay.size == 1 && relay.single().albumId == 0L
        // This adapter is called only from the UpdateNewMessage coordinator path.
        // Message.isFromOffline denotes sender-side scheduled/away/greeting actions,
        // not receiver history. Anti-replay is the durable baseline, not that flag.
        val batch = RelayBatch(relay, completeAlbum = albumComplete && single,
            expectedAlbumSize = null, liveUpdate = true, synthetic = false)
        return batch to RelayPermissions(config.accountId, sourceId, destination,
            sourceAllowed, destinationAllowed, source.hasProtectedContent, forwardable, protected)
    }

    private fun requireCurrentConsent(config: RelayConfig) {
        RelayRealGate.requireOpen(config.userEnabled) // First gate, before any provider I/O.
        check(sameSession(config.accountId) && persistedConfigStillMatches(config)) { "RELAY_CONSENT_CHANGED" }
    }

    private fun options() = MessageSendOptions(suggestedPostInfo = null,
        disableNotification = true, fromBackground = true, protectContent = false,
        allowPaidBroadcast = false, paidMessageStarCount = 0, updateOrderOfInstalledStickerSets = false,
        schedulingState = null, effectId = 0, sendingId = 0, onlyPreview = false)

    /**
     * Dormant activation seam, NOT called by the app or Fake transport in this release.
     * A new reviewed activation must wire asynchronous ACKs; a queued send is UNKNOWN,
     * and cannot automatically advance to forwarding or be retried.
     */
    suspend fun deliverAfterSeparateActivation(
        batch: RelayBatch, config: RelayConfig, ledger: RelayLedger
    ): RelayAck {
        requireCurrentConsent(config)
        if (batch.synthetic || !batch.liveUpdate || batch.messages.isEmpty()) return RelayAck.REJECTED
        val first = batch.messages.first()
        val ids = batch.messages.map { it.messageId }
        val initial = snapshotIds(first.sourceChatId, ids, batch.completeAlbum, config) ?: return RelayAck.REJECTED
        val plan = PickRelayPlanner.plan(initial.first, config, initial.second, System.currentTimeMillis() / 1000L)
        if (plan.action !in setOf(RelayAction.REFERENCE_ONLY, RelayAction.FORWARD_ALLOWED_MEDIA)) return RelayAck.REJECTED
        val key = plan.key ?: return RelayAck.REJECTED
        val destination = config.destinationChatId ?: return RelayAck.REJECTED
        if (!ledger.acceptObserved(key, ids, initial.first.messages.minOf { it.originalUnix }) || !ledger.claim(key))
            return RelayAck.REJECTED
        try {
            requireCurrentConsent(config)
            val refreshed = snapshotIds(first.sourceChatId, ids, batch.completeAlbum, config)
                ?: return unknown(ledger, key)
            val beforeHeader = PickRelayPlanner.plan(refreshed.first, config, refreshed.second, System.currentTimeMillis() / 1000L)
            if (beforeHeader != plan) return unknown(ledger, key)
            val header = plan.header ?: return unknown(ledger, key)
            val sent = client.sendMessage(chatId = destination, topicId = null, replyTo = null,
                options = options(), inputMessageContent = InputMessageText(
                    text = FormattedText(text = header, entities = emptyArray()),
                    // References must not implicitly copy a protected post through a URL preview.
                    linkPreviewOptions = LinkPreviewOptions(isDisabled = true, url = "",
                        forceSmallMedia = false, forceLargeMedia = false, showAboveText = false), clearDraft = false))
            if (sent !is TdlResult.Success || sent.result.chatId != destination || sent.result.sendingState != null)
                return unknown(ledger, key)
            if (plan.action == RelayAction.FORWARD_ALLOWED_MEDIA) {
                requireCurrentConsent(config)
                val latest = snapshotIds(first.sourceChatId, ids, batch.completeAlbum, config)
                    ?: return unknown(ledger, key)
                val beforeMedia = PickRelayPlanner.plan(latest.first, config, latest.second, System.currentTimeMillis() / 1000L)
                if (beforeMedia != plan) return unknown(ledger, key)
                val forwarded = client.forwardMessages(chatId = destination, topicId = null,
                    fromChatId = first.sourceChatId, messageIds = plan.messageIds.toLongArray(),
                    options = options(), sendCopy = false, removeCaption = false)
                if (forwarded !is TdlResult.Success || forwarded.result.messages.size != plan.messageIds.size ||
                    forwarded.result.messages.any { it == null || it.chatId != destination || it.sendingState != null })
                    return unknown(ledger, key)
            }
            ledger.finish(key, RelayDeliveryState.CONFIRMED)
            return RelayAck.CONFIRMED
        } catch (cancelled: CancellationException) {
            ledger.finish(key, RelayDeliveryState.UNKNOWN)
            throw cancelled
        } catch (_: Exception) {
            return unknown(ledger, key)
        }
    }

    private fun unknown(ledger: RelayLedger, key: RelayKey): RelayAck {
        ledger.finish(key, RelayDeliveryState.UNKNOWN)
        return RelayAck.UNKNOWN
    }
}
