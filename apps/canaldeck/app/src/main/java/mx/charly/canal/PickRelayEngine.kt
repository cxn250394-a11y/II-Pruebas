package mx.charly.canal

/** Preparation is safe for live app snapshots; delivery exists only for explicitly synthetic fixtures. */
class PickRelayEngine(private val ledger: RelayLedger) {
    fun prepare(batch: RelayBatch, config: RelayConfig, permissions: RelayPermissions, nowUnix: Long): RelayOutcome {
        val first = batch.messages.firstOrNull()
        val counter = first?.takeIf { config.accountId > 0L && nowUnix > 0L && it.accountId == config.accountId && it.sourceChatId != 0L }
            ?.let { RelayCounterKey(config.accountId, it.sourceChatId, nowUnix / 3600L * 3600L) }
        fun count(metric: RelayMetric, amount: Long = 1) { if (counter != null && amount > 0L) ledger.increment(counter, metric, amount) }
        count(RelayMetric.SEEN, batch.messages.size.toLong())
        val plan = PickRelayPlanner.plan(batch, config, permissions, nowUnix)
        if (plan.protected) count(RelayMetric.PROTECTED)
        if (plan.candidate) count(RelayMetric.CANDIDATES)
        if (plan.tipsterVerified) count(RelayMetric.VERIFIED_TIPSTER)
        if (plan.action == RelayAction.DROP) count(RelayMetric.DISCARDED)
        if (!plan.candidate || plan.action == RelayAction.DROP) return RelayOutcome(plan)
        val key = requireNotNull(plan.key)
        if (!ledger.acceptObserved(key, batch.messages.map { it.messageId }, batch.messages.minOf { it.originalUnix })) {
            count(RelayMetric.DISCARDED)
            return RelayOutcome(plan.copy(action = RelayAction.DROP, reason = "DUPLICATE_STALE_OR_UNARMED", header = null, messageIds = emptyList()))
        }
        count(RelayMetric.UNIQUE)
        if (plan.action !in setOf(RelayAction.REFERENCE_ONLY, RelayAction.FORWARD_ALLOWED_MEDIA)) return RelayOutcome(plan)
        count(RelayMetric.PREPARED)
        return RelayOutcome(plan)
    }

    /** This path cannot accept a live batch or real transport, even with the user switch enabled. */
    fun deliverSynthetic(batch: RelayBatch, config: RelayConfig, transport: RelayTransport, nowUnix: Long): RelayOutcome {
        if (!batch.synthetic || !transport.synthetic) return RelayOutcome(RelayPlan(RelayAction.HOLD, "SYNTHETIC_ONLY"))
        val initial = try { transport.currentPermissions(batch, config) }
            catch (_: Exception) { return RelayOutcome(RelayPlan(RelayAction.HOLD, "PERMISSION_LOOKUP_UNAVAILABLE")) }
        val outcome = prepare(batch, config, initial, nowUnix)
        val plan = outcome.plan
        if (plan.action !in setOf(RelayAction.REFERENCE_ONLY, RelayAction.FORWARD_ALLOWED_MEDIA)) return outcome
        val key = requireNotNull(plan.key)
        if (!ledger.claim(key)) return RelayOutcome(plan.copy(action = RelayAction.HOLD, reason = "DELIVERY_ALREADY_TRACKED"), ledger.state(key))
        fun unknown(reason: String): RelayOutcome {
            ledger.finish(key, RelayDeliveryState.UNKNOWN)
            return RelayOutcome(plan.copy(action = RelayAction.HOLD, reason = reason), RelayDeliveryState.UNKNOWN)
        }
        try {
            val refreshed = transport.refreshBatch(batch)
            if (!stableBatch(batch, refreshed)) return unknown("STOP_MESSAGE_CHANGED_BEFORE_HEADER")
            val fresh = PickRelayPlanner.plan(refreshed, config, transport.currentPermissions(refreshed, config), nowUnix)
            if (fresh.action !in setOf(RelayAction.REFERENCE_ONLY, RelayAction.FORWARD_ALLOWED_MEDIA) || fresh.key != key || fresh.header != plan.header)
                return unknown("STOP_PERMISSION_CHANGED_BEFORE_HEADER")
            val destination = requireNotNull(config.destinationChatId)
            if (transport.sendHeader(destination, requireNotNull(fresh.header)) != RelayAck.CONFIRMED)
                return unknown("STOP_HEADER_ACK_UNKNOWN_OR_REJECTED")
            if (fresh.action == RelayAction.FORWARD_ALLOWED_MEDIA) {
                val latest = transport.refreshBatch(batch)
                if (!stableBatch(batch, latest)) return unknown("STOP_MESSAGE_CHANGED_BEFORE_FORWARD")
                val final = PickRelayPlanner.plan(latest, config, transport.currentPermissions(latest, config), nowUnix)
                if (final.action != RelayAction.FORWARD_ALLOWED_MEDIA || final.key != key || final.messageIds != fresh.messageIds)
                    return unknown("STOP_PERMISSION_CHANGED_BEFORE_FORWARD")
                if (transport.forwardOriginals(destination, key.sourceChatId, final.messageIds) != RelayAck.CONFIRMED)
                    return unknown("STOP_FORWARD_ACK_UNKNOWN_OR_REJECTED")
            }
            ledger.finish(key, RelayDeliveryState.CONFIRMED)
            // Never increment REAL_CONFIRMED: a Fake ACK is not a Telegram delivery.
            return RelayOutcome(fresh, RelayDeliveryState.CONFIRMED)
        } catch (_: Exception) {
            return unknown("STOP_DELIVERY_EXCEPTION_UNKNOWN")
        }
    }

    private fun stableBatch(expected: RelayBatch, actual: RelayBatch): Boolean =
        expected.copy(messages = expected.messages.sortedBy { it.messageId }) ==
            actual.copy(messages = actual.messages.sortedBy { it.messageId })
}
