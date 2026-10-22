package mx.charly.canal

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*

/**
 * Read-only snapshots on CanalDeck's existing client.
 * No second client, downloads, OCR, copied media, retry worker or stored message body.
 * Output operations live only in the guarded asynchronous singleton port.
 */
internal class TdlRelayAdapter(
    private val client: TdlClient,
    private val currentAccount: () -> Long,
    private val isReady: () -> Boolean
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

    /** Singleton integration snapshot. Observed metadata is not a provider completeness proof. */
    internal suspend fun snapshotSingle(
        observed: SingleRelayObserved, config: RelayConfig
    ): Pair<RelayBatch, RelayPermissions>? {
        RelayRealGate.requireOpen(config.userEnabled)
        if (observed.albumId != 0L || !observed.incoming || observed.edited ||
            observed.accountId != config.accountId || observed.messageId <= 0L || observed.sourceChatId == 0L)
            return null
        return snapshotIds(observed.sourceChatId, listOf(observed.messageId), false, config)
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
        // The pinned incoming-message API has no account/chat/album/member-set
        // completeness witness. A PUSH total_count is not such a witness either.
        // Caller booleans, history windows and silence never authorize a live album.
        val single = relay.size == 1 && relay.single().albumId == 0L
        // This adapter is called only from the UpdateNewMessage coordinator path.
        // Message.isFromOffline denotes sender-side scheduled/away/greeting actions,
        // not receiver history. Anti-replay is the durable baseline, not that flag.
        val observed = RelayBatch(relay, completeAlbum = albumComplete && single,
            expectedAlbumSize = null, liveUpdate = true, synthetic = false)
        val batch = AlbumCompletenessGate.reconstruct(observed).batch
        return batch to RelayPermissions(config.accountId, sourceId, destination,
            sourceAllowed, destinationAllowed, source.hasProtectedContent, forwardable, protected)
    }

}
