package mx.charly.canal

import java.time.Instant

internal object RelayTestFixtures {
    val now = Instant.parse("2026-10-22T19:00:00Z").epochSecond
    val source = RelaySourceBinding(1, -111, "Fano Futbol", RelaySourcePolicy.FANO_DIRECT, true)
    val config = RelayConfig(1, -999, true, listOf(source))
    val message = RelayMessage(1, -111, 101, now - 1, "Texto sintético", RelayContentKind.TEXT,
        canBeForwarded = true, hasProtectedContent = false, permalink = "https://t.me/example_source/101")
    fun batch(vararg messages: RelayMessage, album: Boolean = false, size: Int? = null, synthetic: Boolean = true) =
        RelayBatch(messages.toList(), album, size, true, synthetic)
    fun permissions(messages: List<RelayMessage> = listOf(message), sourceChat: Long = -111, account: Long = 1) =
        RelayPermissions(account, sourceChat, -999, true, true, false,
            messages.associate { it.messageId to true }, messages.associate { it.messageId to false })
    fun ledger(): MemoryRelayLedger = MemoryRelayLedger().apply { checkpoint(1, -111, 100, now - 60) }
}

/** The Android store is tested separately against SQLite, including reopen/crash recovery. */
internal class MemoryRelayLedger : RelayLedger {
    val baselines = mutableMapOf<Pair<Long, Long>, RelayBaseline>()
    val observed = mutableSetOf<RelayKey>()
    val deliveries = mutableMapOf<RelayKey, RelayDeliveryState>()
    val totals = mutableMapOf<Pair<RelayCounterKey, RelayMetric>, Long>()
    @Synchronized override fun checkpoint(accountId: Long, sourceChatId: Long, latestMessageId: Long, notBeforeUnix: Long) {
        val old = baselines[accountId to sourceChatId]
        baselines[accountId to sourceChatId] = RelayBaseline(maxOf(latestMessageId, old?.latestMessageId ?: 0), maxOf(notBeforeUnix, old?.notBeforeUnix ?: 0))
    }
    override fun baseline(accountId: Long, sourceChatId: Long) = baselines[accountId to sourceChatId]
    @Synchronized override fun acceptObserved(key: RelayKey, messageIds: List<Long>, originalUnix: Long): Boolean {
        val baseline = baseline(key.accountId, key.sourceChatId) ?: return false
        val keys = (listOf(key) + messageIds.map { RelayKey(key.accountId, key.sourceChatId, "m:$it") }).toSet()
        if (messageIds.isEmpty() || messageIds.any { it <= baseline.latestMessageId } || originalUnix <= baseline.notBeforeUnix || keys.any { it in observed }) return false
        observed.addAll(keys)
        return true
    }
    @Synchronized override fun claim(key: RelayKey): Boolean =
        if (key in deliveries) false else { deliveries[key] = RelayDeliveryState.PENDING; true }
    override fun state(key: RelayKey) = deliveries[key]
    @Synchronized override fun finish(key: RelayKey, state: RelayDeliveryState) {
        require(state != RelayDeliveryState.PENDING)
        if (deliveries[key] == RelayDeliveryState.PENDING) deliveries[key] = state
    }
    @Synchronized override fun recoverPending(accountId: Long): Int {
        val keys = deliveries.filter { it.key.accountId == accountId && it.value == RelayDeliveryState.PENDING }.keys
        keys.forEach { deliveries[it] = RelayDeliveryState.UNKNOWN }; return keys.size
    }
    @Synchronized override fun increment(key: RelayCounterKey, metric: RelayMetric, amount: Long) {
        require(key.accountId > 0L && key.sourceChatId != 0L && key.windowStartUnix >= 0L && amount > 0L && metric != RelayMetric.REAL_CONFIRMED)
        totals[key to metric] = (totals[key to metric] ?: 0) + amount
    }
    override fun counters(key: RelayCounterKey) = RelayMetric.entries.associateWith { totals[key to it] ?: 0L }
}
