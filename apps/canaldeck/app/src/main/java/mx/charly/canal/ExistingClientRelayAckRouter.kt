package mx.charly.canal

import dev.g000sha256.tdl.dto.Update

/**
 * Lifetime-local ACK identity router. TDLib updates do not carry a caller's generation.
 * Never retag a known temporary ID after reconnect. Unknown early updates remain unscoped
 * until the exact response binds them. A bounded buffer belongs to each pending request:
 * loss makes that request uncertain, while lifetime identity collisions still stop the client.
 */
internal class ExistingClientRelayAckRouter(
    private val maxBindings: Int = 1024,
    private val maxEarly: Int = 64
) {
    /** Uncertainty is local/recoverable unless stopped is also true; neither authorizes retry. */
    data class Routed(val updates: List<AsyncRelayUpdate> = emptyList(), val uncertain: Boolean = false)
    private data class Id(val accountId: Long, val destination: Long, val temporary: Long)
    private data class Binding(val scope: AsyncRelayScope, val requestId: String)
    private class Pending(val scope: AsyncRelayScope) {
        val early = mutableListOf<AsyncRelayUpdate>()
        var earlyLost = false
        var quarantined = false
    }
    private val bindings = linkedMapOf<Id, Binding>()
    private val pending = linkedMapOf<String, Pending>()
    private var lifetimeAccount = 0L
    var stopped: Boolean = false; private set
    var paused: Boolean = false; private set
    val hasQuarantinedRequests: Boolean @Synchronized get() = pending.values.any { it.quarantined }
    val canResume: Boolean @Synchronized get() = !stopped && !hasQuarantinedRequests

    /** SDK ACK DTOs carry no account identity. One client lifetime may attest only one account. */
    @Synchronized fun sealAccount(accountId: Long): Boolean {
        if (stopped || accountId <= 0L) return false
        if (lifetimeAccount == 0L) lifetimeAccount = accountId
        else if (lifetimeAccount != accountId) { poison(); return false }
        return true
    }

    @Synchronized fun begin(command: SingleRelayCommand): Boolean {
        if (!sealAccount(command.scope.accountId) || paused || command.request.requestId in pending ||
            bindings.values.any { it.requestId == command.request.requestId } || pending.size >= 32) return false
        pending[command.request.requestId] = Pending(command.scope)
        return true
    }

    @Synchronized fun bind(response: AsyncRelayResponse): Routed {
        if (stopped) return Routed(uncertain = true)
        val request = pending[response.requestId] ?: return poison()
        val scope = request.scope
        val message = response.messages?.singleOrNull()
        if (scope != response.scope || message == null || message.temporaryMessageId <= 0L ||
            message.destinationChatId != scope.destinationChatId || !message.isOutgoing || !message.hasSendingState)
            return poison()
        val id = Id(scope.accountId, scope.destinationChatId, message.temporaryMessageId)
        val binding = Binding(scope, response.requestId)
        if (bindings[id]?.let { it != binding } == true || id !in bindings && bindings.size >= maxBindings)
            return poison()
        bindings[id] = binding
        pending.remove(response.requestId)
        // A delayed queued response may discharge quarantine by sealing the old exact ID.
        // It may never confirm a terminal UNKNOWN or start the next stage after disconnect.
        if (request.quarantined || request.earlyLost) return Routed(uncertain = true)
        val matching = request.early.filter { it.oldMessageId == id.temporary }
        // Exact response binding supplies the scope; callback arrival order never does.
        return Routed(matching.map { it.copy(scope = scope) })
    }

    @Synchronized fun observe(accountId: Long, update: Update): Routed {
        if (stopped) return Routed(uncertain = true)
        if (!sealAccount(accountId)) return Routed(uncertain = stopped)
        val routed = mutableListOf<AsyncRelayUpdate>()
        for (raw in TdlAsyncAckMapper.map(accountId, "UNBOUND", update)) {
            if (raw.kind == AsyncRelayUpdateKind.ACKNOWLEDGED) continue
            val id = Id(accountId, raw.scope.destinationChatId, raw.oldMessageId)
            val bound = bindings[id]
            if (bound != null) routed += raw.copy(scope = bound.scope)
            else {
                // Requests that did not exist at arrival can never inherit this update.
                // At most 32 * maxEarly DTOs are retained; no foreign destination consumes
                // another destination's budget and duplicates consume no additional slot.
                for (request in pending.values) {
                    if (request.quarantined || request.earlyLost || request.scope.accountId != accountId ||
                        request.scope.destinationChatId != id.destination || raw in request.early) continue
                    if (request.early.size >= maxEarly) {
                        request.earlyLost = true
                        request.early.clear()
                    } else request.early += raw
                }
            }
        }
        return Routed(routed)
    }

    /** Unknown I/O is quarantined, never retried or absolved by a timeout/reconnect. */
    @Synchronized fun uncertain(requestId: String): Routed {
        if (requestId in pending) {
            paused = true
            quarantinePending()
            return Routed(uncertain = true)
        }
        return Routed()
    }

    /**
     * Only the adapter's proven preflight-before-SDK result may cancel an uninvoked call.
     * A disconnect, timeout, missing response or send error is never that proof. Cancelling
     * releases only this pending slot; lifetime bindings and the explicit pause stay intact.
     */
    @Synchronized fun cancelBeforeInvocation(requestId: String): Boolean {
        if (stopped) return false
        val request = pending.remove(requestId) ?: return false
        request.early.clear()
        return true
    }

    /** A disconnect pauses new dispatch but preserves every lifetime binding and unknown call. */
    @Synchronized fun disconnected(): Routed {
        if (stopped) return Routed(uncertain = true)
        paused = true
        val uncertain = pending.isNotEmpty()
        quarantinePending()
        return Routed(uncertain = uncertain)
    }

    /** Caller supplies the separate explicit owner-consent gate. Never enables any output. */
    @Synchronized fun resume(accountId: Long): Boolean {
        if (!sealAccount(accountId) || !canResume) return false
        paused = false
        return true
    }

    private fun quarantinePending() {
        pending.values.forEach { request ->
            request.quarantined = true
            request.early.clear()
        }
    }

    private fun poison(): Routed { stopped = true; return Routed(uncertain = true) }
}
