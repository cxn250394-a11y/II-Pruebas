package mx.charly.canal

import java.util.UUID

/** Durable metadata only: no header, caption, image, credential or provider client. */
internal data class SingleRelayRecoveryToken(
    val previousScope: AsyncRelayScope,
    val configurationRevision: Long,
    val nonce: String,
    val clientLifetime: String
)

/** A separate, explicit action for fresh work; never permission to replay an old intent. */
internal data class SingleRelayRecoveryConsent(
    val token: SingleRelayRecoveryToken,
    val resumedScope: AsyncRelayScope,
    val configurationRevision: Long
)

/** Implementations must make compareAndSet durable and atomic, including removal. */
internal interface SingleRelayRecoveryStore {
    fun read(accountId: Long): SingleRelayRecoveryToken?
    fun compareAndSet(accountId: Long, expected: SingleRelayRecoveryToken?, next: SingleRelayRecoveryToken?): Boolean
}

/**
 * A connection becoming ready only updates an observation. It never clears a durable pause.
 * The existing router must settle all unknown temporary IDs before an explicit consent can
 * admit a fresh generation. Old pending intents remain UNKNOWN in their separate ledger.
 * A pause from a different client/process lifetime cannot be cleared by this gate: RAM ACK
 * identity tombstones from that lifetime are unavailable, so recovery requires review.
 */
internal class SingleRelayRecoveryGate(
    private val store: SingleRelayRecoveryStore,
    private val clientLifetime: String = UUID.randomUUID().toString()
) {
    private data class Transport(val scope: AsyncRelayScope, val revision: Long, val enabled: Boolean)
    private var observed: Transport? = null
    private var failed = !validOpaqueId(clientLifetime)

    @Synchronized fun token(accountId: Long): SingleRelayRecoveryToken? = read(accountId)

    @Synchronized fun isPaused(accountId: Long): Boolean {
        val current = read(accountId)
        return failed || current != null
    }

    /** Persist before invalidating transient work; a storage failure blocks this gate. */
    @Synchronized fun pause(scope: AsyncRelayScope, configurationRevision: Long): SingleRelayRecoveryToken? {
        observed = null
        if (!validScope(scope) || !validRevision(configurationRevision)) { failed = true; return null }
        val previous = read(scope.accountId)
        if (failed) return null
        // Never rewrite a previous lifetime's unresolved identity as if it were this client.
        if (previous != null && previous.clientLifetime != clientLifetime) return previous
        val next = SingleRelayRecoveryToken(scope, configurationRevision, UUID.randomUUID().toString(), clientLifetime)
        return try {
            if (!store.compareAndSet(scope.accountId, previous, next)) { failed = true; null }
            else next
        } catch (_: Exception) { failed = true; null }
    }

    /** No output, preference mutation, router resume or retry is allowed from this method. */
    @Synchronized fun observeTransport(current: ExistingClientRelayContext?) {
        observed = current?.let(::transport)
    }

    /**
     * Callers must separately enforce the compiled native fuse. This pure gate opens no port.
     * Resume the settled router first, then CAS-clear the durable pause. If persistence fails,
     * this gate remains blocked even though the router may have accepted its local resume.
     */
    @Synchronized fun resume(
        consent: SingleRelayRecoveryConsent,
        current: ExistingClientRelayContext?,
        routerCanResume: Boolean,
        resumeRouter: () -> Boolean
    ): Boolean {
        if (failed || !routerCanResume) return false
        val token = read(consent.token.previousScope.accountId) ?: return false
        if (failed || token != consent.token || token.clientLifetime != clientLifetime) return false
        val fresh = current?.let(::transport) ?: return false
        if (fresh != observed || !fresh.enabled || fresh.scope != consent.resumedScope ||
            fresh.scope.accountId != token.previousScope.accountId ||
            fresh.scope.sessionId == token.previousScope.sessionId ||
            fresh.revision != consent.configurationRevision || fresh.revision < token.configurationRevision) return false
        return try {
            if (!resumeRouter()) return false
            if (!store.compareAndSet(token.previousScope.accountId, token, null)) { failed = true; false }
            else if (read(token.previousScope.accountId) != null || failed) { failed = true; false }
            else { observed = null; true }
        } catch (_: Exception) { failed = true; false }
    }

    private fun read(accountId: Long): SingleRelayRecoveryToken? {
        if (failed) return null
        if (accountId <= 0L) { failed = true; return null }
        return try {
            store.read(accountId)?.also {
                if (it.previousScope.accountId != accountId || !validScope(it.previousScope) ||
                    !validRevision(it.configurationRevision) || !validOpaqueId(it.nonce) ||
                    !validOpaqueId(it.clientLifetime)) { failed = true; return null }
            }
        } catch (_: Exception) { failed = true; null }
    }

    private fun transport(context: ExistingClientRelayContext): Transport? {
        if (!context.connected || !validScope(context.scope) || !validRevision(context.configurationRevision) ||
            context.scope.accountId != context.config.accountId ||
            context.scope.destinationChatId != context.config.destinationChatId) return null
        return Transport(context.scope, context.configurationRevision, context.config.userEnabled)
    }

    companion object {
        private fun validOpaqueId(value: String) = value.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))
        private fun validRevision(value: Long) = value >= 0L && value < Long.MAX_VALUE
        private fun validScope(scope: AsyncRelayScope) = scope.accountId > 0L &&
            scope.destinationChatId != 0L && validOpaqueId(scope.sessionId)
    }
}
