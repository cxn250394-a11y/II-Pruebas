package mx.charly.canal

/** The pinned native provider has no usable completeness witness for an incoming album. */
sealed class AlbumCompletenessEvidence {
    data object ProviderUnknown : AlbumCompletenessEvidence()

    /** A deterministic test manifest, never authority for an incoming/live TDLib batch. */
    data class FakeManifest(
        val accountId: Long,
        val sourceChatId: Long,
        val albumId: Long,
        val memberIds: List<Long>
    ) : AlbumCompletenessEvidence()
}

data class AlbumReconstruction(val batch: RelayBatch, val hold: Boolean, val reason: String)

/** Canonicalize caller booleans/counts; silence, history windows and 10 observed parts are not proof. */
object AlbumCompletenessGate {
    fun reconstruct(
        observed: RelayBatch,
        evidence: AlbumCompletenessEvidence = AlbumCompletenessEvidence.ProviderUnknown
    ): AlbumReconstruction {
        val unproven = observed.copy(completeAlbum = false, expectedAlbumSize = null)
        fun hold(reason: String) = AlbumReconstruction(unproven, true, reason)
        val first = observed.messages.firstOrNull() ?: return hold("ALBUM_EMPTY")
        if (first.accountId <= 0L || first.sourceChatId == 0L || first.messageId <= 0L ||
            observed.messages.any { it.accountId != first.accountId || it.sourceChatId != first.sourceChatId || it.messageId <= 0L } ||
            observed.messages.map { it.messageId }.distinct().size != observed.messages.size)
            return hold("ALBUM_SCOPE_OR_MEMBER_INVALID")
        if (first.albumId == 0L && observed.messages.size == 1)
            return AlbumReconstruction(unproven, false, "SINGLE_MESSAGE_NO_ALBUM")
        if (first.albumId <= 0L || observed.messages.any { it.albumId != first.albumId })
            return hold("ALBUM_ID_MISMATCH")
        if (!observed.synthetic) return hold("LIVE_ALBUM_COMPLETENESS_UNPROVEN")
        val manifest = evidence as? AlbumCompletenessEvidence.FakeManifest
            ?: return hold("FAKE_ALBUM_MANIFEST_MISSING")
        val ids = observed.messages.map { it.messageId }.toSet()
        if (manifest.accountId != first.accountId || manifest.sourceChatId != first.sourceChatId ||
            manifest.albumId != first.albumId || manifest.memberIds.size !in 2..10 ||
            manifest.memberIds.any { it <= 0L } || manifest.memberIds.distinct().size != manifest.memberIds.size ||
            manifest.memberIds.toSet() != ids || observed.messages.any { it.kind != RelayContentKind.PHOTO || it.edited })
            return hold("FAKE_ALBUM_MANIFEST_MISMATCH")
        return AlbumReconstruction(observed.copy(messages = observed.messages.sortedBy { it.messageId },
            completeAlbum = true, expectedAlbumSize = manifest.memberIds.size), false, "FAKE_MANIFEST_EXACT")
    }
}

data class LiveAlbumKey(val accountId: Long, val sessionGeneration: Long, val sourceChatId: Long, val albumId: Long)
data class LiveAlbumObservation(val key: LiveAlbumKey, val observedIds: List<Long>, val reason: String) {
    val complete: Boolean get() = false
}

/** Bounded transient reconstruction of observed identities only. There is deliberately no release/timeout API. */
class LiveAlbumObservationIndex(private val maxAlbums: Int = 128, private val maxMembers: Int = 64) {
    private data class Group(val ids: MutableSet<Long> = linkedSetOf(), var changed: Boolean = false, var overflow: Boolean = false)
    private val groups = linkedMapOf<LiveAlbumKey, Group>()
    init { require(maxAlbums > 0 && maxMembers > 0) }

    @Synchronized fun observe(sessionGeneration: Long, message: RelayMessage): LiveAlbumObservation {
        require(sessionGeneration > 0L && message.accountId > 0L && message.sourceChatId != 0L && message.messageId > 0L && message.albumId > 0L)
        val key = LiveAlbumKey(message.accountId, sessionGeneration, message.sourceChatId, message.albumId)
        if (key !in groups && groups.size >= maxAlbums)
            return LiveAlbumObservation(key, emptyList(), "LIVE_ALBUM_OBSERVATION_LIMIT")
        val group = groups.getOrPut(key) { Group() }
        if (message.messageId !in group.ids && group.ids.size >= maxMembers) group.overflow = true
        else group.ids.add(message.messageId)
        if (message.edited || message.outgoing || message.kind != RelayContentKind.PHOTO) group.changed = true
        return snapshot(key, group)
    }

    @Synchronized fun invalidate(accountId: Long, sessionGeneration: Long, sourceChatId: Long, messageId: Long) {
        groups.filterKeys { it.accountId == accountId && it.sessionGeneration == sessionGeneration && it.sourceChatId == sourceChatId }
            .values.filter { messageId in it.ids }.forEach { it.changed = true }
    }

    @Synchronized fun inspect(key: LiveAlbumKey): LiveAlbumObservation? = groups[key]?.let { snapshot(key, it) }
    private fun snapshot(key: LiveAlbumKey, group: Group) = LiveAlbumObservation(key, group.ids.sorted(), when {
        group.overflow -> "LIVE_ALBUM_OBSERVATION_LIMIT"
        group.changed -> "LIVE_ALBUM_CHANGED_OR_UNSUPPORTED"
        else -> "LIVE_ALBUM_COMPLETENESS_UNPROVEN"
    })
}
