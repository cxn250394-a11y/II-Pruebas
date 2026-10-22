package mx.charly.canal

/**
 * Pure live ingress fence. Only a separately loaded chat arms an immutable epoch baseline;
 * incoming callbacks never move it. SQLite's atomic acceptObserved reserves the publication.
 */
internal class ExistingClientRelayAdmission(private val ledger: RelayLedger) : SingleRelayAdmission {
    private data class Armed(val accountId: Long, val sessionId: String)
    private val armed = mutableMapOf<Long, Armed>()

    @Synchronized fun reset() { armed.clear() }

    @Synchronized fun armLoaded(
        accountId: Long, sessionId: String, sourceId: Long, latestLoadedMessageId: Long,
        notBeforeUnix: Long
    ) {
        require(accountId > 0L && sessionId.isNotBlank() && sourceId != 0L && latestLoadedMessageId >= 0L && notBeforeUnix > 0L)
        val target = Armed(accountId, sessionId)
        if (armed[sourceId] == target) return
        ledger.checkpoint(accountId, sourceId, latestLoadedMessageId, notBeforeUnix)
        armed[sourceId] = target
    }

    @Synchronized override fun admit(observed: SingleRelayObserved): Boolean {
        if (observed.albumId != 0L || !observed.incoming || observed.edited || observed.messageId <= 0L ||
            armed[observed.sourceChatId] != Armed(observed.accountId, observed.sessionId)) return false
        return ledger.acceptObserved(observed.key, listOf(observed.messageId), observed.originalUnix)
    }
}
