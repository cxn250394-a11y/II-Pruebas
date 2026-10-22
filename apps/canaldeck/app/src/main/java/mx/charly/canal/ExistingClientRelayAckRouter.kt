package mx.charly.canal

import dev.g000sha256.tdl.dto.Update

/**
 * Lifetime-local ACK identity router. TDLib updates do not carry a caller's generation.
 * Never retag a known temporary ID after reconnect. Unknown early updates remain unscoped
 * until the exact response binds them; overflow or reuse closes the router for this client.
 */
internal class ExistingClientRelayAckRouter(
    private val maxBindings: Int = 1024,
    private val maxEarly: Int = 64
) {
    data class Routed(val updates: List<AsyncRelayUpdate> = emptyList(), val uncertain: Boolean = false)
    private data class Id(val accountId: Long, val destination: Long, val temporary: Long)
    private data class Binding(val scope: AsyncRelayScope, val requestId: String)
    private data class Early(val update: AsyncRelayUpdate, val candidateRequests: Set<String>)
    private val bindings = linkedMapOf<Id, Binding>()
    private val pending = linkedMapOf<String, AsyncRelayScope>()
    private val early = mutableListOf<Early>() // No generation is guessed from arrival time.
    private var lifetimeAccount = 0L
    var stopped: Boolean = false; private set

    /** SDK ACK DTOs carry no account identity. One client lifetime may attest only one account. */
    @Synchronized fun sealAccount(accountId: Long): Boolean {
        if (stopped || accountId <= 0L) return false
        if (lifetimeAccount == 0L) lifetimeAccount = accountId
        else if (lifetimeAccount != accountId) { poison(); return false }
        return true
    }

    @Synchronized fun begin(command: SingleRelayCommand): Boolean {
        if (!sealAccount(command.scope.accountId) || command.request.requestId in pending ||
            bindings.values.any { it.requestId == command.request.requestId } || pending.size >= 32) return false
        pending[command.request.requestId] = command.scope
        return true
    }

    @Synchronized fun bind(response: AsyncRelayResponse): Routed {
        if (stopped) return Routed(uncertain = true)
        val scope = pending[response.requestId]
        val message = response.messages?.singleOrNull()
        if (scope == null || scope != response.scope || message == null || message.temporaryMessageId <= 0L ||
            message.destinationChatId != scope.destinationChatId || !message.isOutgoing || !message.hasSendingState)
            return poison()
        val id = Id(scope.accountId, scope.destinationChatId, message.temporaryMessageId)
        val binding = Binding(scope, response.requestId)
        if (bindings[id]?.let { it != binding } == true || id !in bindings && bindings.size >= maxBindings)
            return poison()
        bindings[id] = binding
        pending.remove(response.requestId)
        val matching = early.filter { response.requestId in it.candidateRequests &&
            it.update.scope.accountId == id.accountId && it.update.scope.destinationChatId == id.destination &&
            it.update.oldMessageId == id.temporary }
        val remaining = early.filter { it !in matching }.mapNotNull { item ->
            val candidates = item.candidateRequests - response.requestId
            if (candidates.isEmpty()) null else item.copy(candidateRequests = candidates)
        }
        early.clear(); early.addAll(remaining)
        // Exact response binding supplies the scope; callback arrival order never does.
        return Routed(matching.map { it.update.copy(scope = scope) })
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
                val candidates = pending.filterValues { it.accountId == accountId && it.destinationChatId == id.destination }.keys.toSet()
                if (candidates.isEmpty()) continue
                val item = Early(raw, candidates)
                if (early.none { it.update == raw }) {
                    if (early.size >= maxEarly) return poison()
                    early += item
                }
            }
        }
        return Routed(routed)
    }

    /** Unknown I/O before binding cannot safely be followed by another native request. */
    @Synchronized fun uncertain(requestId: String): Routed {
        if (requestId in pending) return poison()
        return Routed()
    }

    @Synchronized fun disconnected(): Routed = if (pending.isNotEmpty()) poison() else Routed()

    private fun poison(): Routed { stopped = true; return Routed(uncertain = true) }
}
