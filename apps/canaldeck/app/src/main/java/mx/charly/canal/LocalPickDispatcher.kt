package mx.charly.canal

data class LocalPickEnvelope(
    val chatId: Long, val messageId: Long, val originalUnix: Long, val albumId: Long,
    val mode: PickWatchMode, val sourcePolicy: RelaySourcePolicy?, val reference: PickReference
)

/** A transient consent/session snapshot. It contains neither message contents nor credentials. */
data class LocalPickGuard(
    val accountId: Long,
    val sessionEpoch: Any,
    val validSession: Boolean,
    val mode: PickWatchMode,
    val sourcePolicy: RelaySourcePolicy?,
    val organizationRevision: Long,
    val sourceRevision: Long,
    val sourceEpoch: Long,
    val notificationsAllowed: Boolean,
    val quiet: Boolean,
    val unread: Boolean,
    val unchangedMessage: Boolean
)

enum class LocalPickPublishResult { BLOCKED, DUPLICATE, POSTED, UNKNOWN }

/** Final checks must happen before either durable pick dedup or the generic NoticeBook is advanced. */
object LocalPickPublicationBoundary {
    fun post(allowed: () -> Boolean, reserve: () -> Boolean, emit: () -> Unit): LocalPickPublishResult {
        if (!allowed()) return LocalPickPublishResult.BLOCKED
        if (!reserve()) return LocalPickPublishResult.DUPLICATE
        return try { emit(); LocalPickPublishResult.POSTED }
        catch (_: Exception) { LocalPickPublishResult.UNKNOWN }
    }
}

/** No durable backlog: only current live candidates may wait transiently for their own link callback. */
class LocalPickDispatcher(private val organization: Organization) {
    private fun allowed(guard: LocalPickGuard, envelope: LocalPickEnvelope): Boolean =
        guard.accountId == organization.accountId && guard.validSession && guard.mode == envelope.mode &&
            guard.mode != PickWatchMode.OFF && guard.sourcePolicy == envelope.sourcePolicy &&
            guard.organizationRevision >= 0L && guard.organizationRevision != Long.MAX_VALUE &&
            guard.sourceRevision >= 0L && guard.sourceRevision != Long.MAX_VALUE &&
            guard.sourceEpoch >= 0L && guard.sourceEpoch != Long.MAX_VALUE &&
            guard.notificationsAllowed && !guard.quiet && guard.unread && guard.unchangedMessage

    fun submit(
        envelope: LocalPickEnvelope,
        currentGuard: () -> LocalPickGuard,
        requestLink: ((String?) -> Unit) -> Unit,
        publish: (String, String, () -> Boolean) -> LocalPickPublishResult
    ): Boolean {
        val expected = currentGuard()
        if (!allowed(expected, envelope) || !organization.canAcceptPick(
                envelope.chatId, envelope.messageId, envelope.originalUnix, envelope.albumId)) return false
        fun stillCurrent(): Boolean {
            val current = currentGuard()
            return allowed(current, envelope) && current.sessionEpoch === expected.sessionEpoch &&
                current.accountId == expected.accountId && current.organizationRevision == expected.organizationRevision &&
                current.sourceRevision == expected.sourceRevision && current.sourceEpoch == expected.sourceEpoch
        }
        requestLink { link ->
            if (stillCurrent()) {
                var reserved = false
                val result = publish(envelope.reference.render(link), envelope.reference.firstLine) {
                    if (!stillCurrent()) false else organization.acceptPick(
                        envelope.chatId, envelope.messageId, envelope.originalUnix, envelope.albumId,
                        expectedRevision = expected.organizationRevision, expectedEpoch = expected.sourceEpoch
                    ).also { reserved = it }
                }
                if (reserved) organization.finishPick(envelope.chatId, envelope.messageId, envelope.albumId,
                    if (result == LocalPickPublishResult.POSTED) LocalPickDeliveryState.NOTIFIED else LocalPickDeliveryState.UNKNOWN)
            }
        }
        return true
    }
}
