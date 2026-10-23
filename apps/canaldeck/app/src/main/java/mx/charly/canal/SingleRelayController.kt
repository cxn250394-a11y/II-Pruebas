package mx.charly.canal

import java.util.UUID

/**
 * Nonblocking singleton integration using the durable ACK protocol from #9. This controller
 * does not own a client or collect a second stream. Native entry is compiled OFF before even
 * admission/refresh; only explicitly synthetic Fake ports exercise dispatch in this candidate.
 */
class SingleRelayController(
    private val ledger: AsyncRelayLedger,
    private val port: SingleRelayPort,
    private val admission: SingleRelayAdmission,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    private data class Starting(val observed: SingleRelayObserved, val token: String, val started: Long)
    private data class Context(val observed: SingleRelayObserved, val evaluation: SingleRelayPlanning.Evaluation,
        val headerChecks: MutableSet<String> = mutableSetOf(),
        val answeredHeaderChecks: MutableSet<String> = mutableSetOf(),
        val issuedRequests: MutableSet<String> = mutableSetOf())
    private val starting = linkedMapOf<RelayKey, Starting>()
    private val contexts = linkedMapOf<RelayKey, Context>()
    private val decisions = linkedMapOf<RelayKey, SingleRelayDecision>()
    private val revokedSessions = linkedSetOf<Pair<Long, String>>()
    private var closed = false

    @Synchronized fun snapshot(key: RelayKey): AsyncRelaySnapshot? = ledger.read(key)
    @Synchronized fun decision(key: RelayKey): SingleRelayDecision? = decisions[key]?.copy(snapshot = ledger.read(key))

    /** Local read-only lease used immediately before a dormant native provider invocation. */
    @Synchronized fun isRequestActive(command: SingleRelayCommand): Boolean {
        if (closed) return false
        val context = contexts[command.key] ?: return false
        val old = ledger.read(command.key) ?: return false
        if (old.terminal || old.intent.scope != command.scope || command.sourceChatId != command.key.sourceChatId ||
            old.requests.last().requestId != command.request.requestId || old.requests.last().stage != command.request.stage ||
            old.requests.last().responseBound || command.request.responseBound ||
            old.requests.last().expectedSourceMessageIds != command.request.expectedSourceMessageIds ||
            old.requests.last().deadlineMillis != command.request.deadlineMillis)
            return false
        val now = clockMillis()
        if (now < old.intent.createdMillis || now >= old.requests.last().deadlineMillis) return false
        return if (command.request.stage == AsyncRelayStage.HEADER)
            command.header == context.evaluation.plan.header
        else AsyncRelayValidation.confirmed(old.requests.first()) &&
            old.requests.first().finalMessageIds.values.singleOrNull() == command.confirmedHeaderMessageId &&
            command.expectedHeaderText == context.evaluation.plan.header
    }

    /** Recheck a committed owner revision after native awaits, including OFF -> ON ABA. */
    @Synchronized fun isRequestActive(command: SingleRelayCommand, currentConfigurationRevision: Long): Boolean =
        currentConfigurationRevision >= 0L && currentConfigurationRevision < Long.MAX_VALUE &&
            ledger.read(command.key)?.intent?.configurationRevision == currentConfigurationRevision && isRequestActive(command)

    @Synchronized fun start(observed: SingleRelayObserved): SingleRelayDecision {
        val key = observed.key
        fun hold(reason: String) = record(SingleRelayDecision(key, RelayAction.HOLD, reason))
        if (closed) return hold("CONTROLLER_STOPPED")
        if (observed.albumId != 0L) return hold("LIVE_ALBUM_COMPLETENESS_UNPROVEN")
        if (!observed.incoming || observed.edited || observed.accountId <= 0L || observed.sourceChatId == 0L ||
            observed.messageId <= 0L || observed.originalUnix <= 0L ||
            !observed.sessionId.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) return hold("INVALID_OBSERVED_PUBLICATION")
        if ((observed.accountId to observed.sessionId) in revokedSessions) return hold("SESSION_REVOKED")
        // Literal native fuse is checked before admission, reads, subscriptions or dispatch.
        if (port.mode == SingleRelayPortMode.EXISTING_CLIENT && !BuildConfig.PICK_RELAY_REAL_ENABLED)
            return hold("REAL_RELAY_COMPILED_OFF")
        ledger.read(key)?.let { old ->
            return record(SingleRelayDecision(key, RelayAction.HOLD, "DURABLE_INTENT_EXISTS", snapshot = old))
        }
        if (key in starting || key in contexts) return decisions[key] ?: hold("PREPARATION_PENDING")
        if (starting.size + contexts.size >= MAX_ACTIVE) return hold("ACTIVE_CAPACITY_HOLD")
        val now = clockMillis()
        if (now < 0 || now > Long.MAX_VALUE - TIMEOUT_MILLIS) return hold("INVALID_CLOCK")
        val token = UUID.randomUUID().toString()
        starting[key] = Starting(observed, token, now)
        record(SingleRelayDecision(key, RelayAction.HOLD, "PREPARATION_PENDING"))
        val accepted = try { admission.admit(observed) } catch (_: Exception) { false }
        if (!accepted) { starting.remove(key); return hold("LIVE_BASELINE_OR_REPLAY_HOLD") }
        if (starting[key]?.token != token || closed) return decisions[key] ?: hold("PREPARATION_CANCELLED")
        try { port.refresh(observed) { fresh -> initialSnapshot(observed, token, fresh) } }
        catch (_: Exception) { starting.remove(key); return hold("INITIAL_SNAPSHOT_FAILED") }
        return decision(key) ?: hold("PREPARATION_PENDING")
    }

    @Synchronized private fun initialSnapshot(observed: SingleRelayObserved, token: String, fresh: SingleRelayFreshState?) {
        val key = observed.key
        val pending = starting[key]?.takeIf { it.token == token } ?: return
        starting.remove(key) // A repeated callback cannot reserve or dispatch a second intent.
        if (closed || (observed.accountId to observed.sessionId) in revokedSessions) return
        val now = clockMillis()
        if (now < pending.started || now - pending.started >= TIMEOUT_MILLIS || fresh == null) {
            record(SingleRelayDecision(key, RelayAction.HOLD, "INITIAL_SNAPSHOT_UNAVAILABLE_OR_EXPIRED")); return
        }
        val evaluation = SingleRelayPlanning.evaluate(observed, fresh, now / 1000L, port.mode)
        val plan = evaluation.plan
        if (plan.action !in setOf(RelayAction.REFERENCE_ONLY, RelayAction.FORWARD_ALLOWED_MEDIA) || plan.key != key || plan.header.isNullOrBlank()) {
            record(SingleRelayDecision(key, plan.action, plan.reason, evaluation.semantics)); return
        }
        if (now < 0 || now > Long.MAX_VALUE - TIMEOUT_MILLIS) return
        val intent = AsyncRelayIntent(key, evaluation.fresh.state.scope, listOf(observed.messageId),
            plan.action == RelayAction.FORWARD_ALLOWED_MEDIA, now, TIMEOUT_MILLIS,
            evaluation.fresh.state.configurationRevision)
        val request = AsyncRelayRequest(UUID.randomUUID().toString(), AsyncRelayStage.HEADER, emptyList(), now + TIMEOUT_MILLIS)
        val initial = AsyncRelaySnapshot(intent, AsyncRelayPhase.HEADER_WAITING, listOf(request))
        if (!AsyncRelayValidation.valid(initial)) {
            record(SingleRelayDecision(key, RelayAction.HOLD, "INVALID_INTENT", evaluation.semantics)); return
        }
        val context = Context(observed, evaluation)
        contexts[key] = context
        if (!ledger.create(initial)) {
            contexts.remove(key)
            record(SingleRelayDecision(key, RelayAction.HOLD, "DURABLE_RESERVATION_REJECTED", evaluation.semantics, ledger.read(key)))
            return
        }
        if (closed || contexts[key] !== context || (observed.accountId to observed.sessionId) in revokedSessions) {
            stop(key, "SESSION_CHANGED_DURING_RESERVATION")
            return
        }
        record(SingleRelayDecision(key, plan.action, plan.reason, evaluation.semantics, initial))
        refreshBeforeDispatch(context, request)
    }

    @Synchronized private fun refreshBeforeDispatch(context: Context, request: AsyncRelayRequest) {
        val key = context.observed.key
        if (closed || contexts[key] !== context || !currentRequest(key, request.requestId)) return
        try {
            port.refresh(context.observed) { fresh ->
                synchronized(this) {
                    if (closed || contexts[key] !== context || !currentRequest(key, request.requestId)) return@synchronized
                    val now = clockMillis()
                    val current = fresh?.let { SingleRelayPlanning.evaluate(context.observed, it, now / 1000L, port.mode) }
                    val old = ledger.read(key) ?: return@synchronized
                    if (current == null || now < old.intent.createdMillis || current != context.evaluation ||
                        current.fresh.state.configurationRevision != old.intent.configurationRevision ||
                        current.fresh.state.scope != old.intent.scope) {
                        stop(key, "CURRENT_CONSENT_PERMISSION_SESSION_OR_CONTENT_CHANGED"); return@synchronized
                    }
                    if (port.mode == SingleRelayPortMode.EXISTING_CLIENT && !BuildConfig.PICK_RELAY_REAL_ENABLED) {
                        stop(key, "REAL_RELAY_COMPILED_OFF"); return@synchronized
                    }
                    if (!currentRequest(key, request.requestId)) return@synchronized
                    val headerId = if (request.stage == AsyncRelayStage.MEDIA)
                        old.requests.first().takeIf { AsyncRelayValidation.confirmed(it) }
                            ?.finalMessageIds?.values?.singleOrNull() else null
                    if (request.stage == AsyncRelayStage.MEDIA && (headerId == null || headerId <= 0L)) {
                        stop(key, "FINAL_HEADER_ID_UNAVAILABLE"); return@synchronized
                    }
                    val command = SingleRelayCommand(key, request, old.intent.scope, key.sourceChatId,
                        if (request.stage == AsyncRelayStage.HEADER) context.evaluation.plan.header else null,
                        headerId, if (request.stage == AsyncRelayStage.MEDIA) context.evaluation.plan.header else null)
                    if (request.stage == AsyncRelayStage.HEADER) dispatchOnce(context, command)
                    else if (context.headerChecks.add(request.requestId)) {
                        try {
                            port.checkHeaderExists(command) { exists ->
                                synchronized(this) headerRead@{
                                    if (closed || contexts[key] !== context || !currentRequest(key, request.requestId)) return@headerRead
                                    if (!context.answeredHeaderChecks.add(request.requestId)) return@headerRead
                                    if (!exists) { stop(key, "FINAL_HEADER_MISSING_OR_CHANGED"); return@headerRead }
                                    // A matching delete/session/source mutation during the read removed
                                    // this context. A positive stale callback cannot revive its intent.
                                    refreshAfterHeaderCheck(context, command)
                                }
                            }
                        } catch (_: Exception) { stop(key, "FINAL_HEADER_READ_FAILED") }
                    }
                }
            }
        } catch (_: Exception) { stop(key, "FRESH_SNAPSHOT_EXCEPTION") }
    }

    private fun refreshAfterHeaderCheck(context: Context, command: SingleRelayCommand) {
        val key = context.observed.key
        try {
            port.refresh(context.observed) { fresh ->
                synchronized(this) {
                    if (closed || contexts[key] !== context || !currentRequest(key, command.request.requestId)) return@synchronized
                    val old = ledger.read(key) ?: return@synchronized
                    val now = clockMillis()
                    val current = fresh?.let { SingleRelayPlanning.evaluate(context.observed, it, now / 1000L, port.mode) }
                    if (current == null || now < old.intent.createdMillis || current != context.evaluation ||
                        current.fresh.state.configurationRevision != old.intent.configurationRevision ||
                        current.fresh.state.scope != old.intent.scope) {
                        stop(key, "CURRENT_CONSENT_PERMISSION_SESSION_OR_CONTENT_CHANGED"); return@synchronized
                    }
                    dispatchOnce(context, command)
                }
            }
        } catch (_: Exception) { stop(key, "FRESH_SNAPSHOT_EXCEPTION") }
    }

    private fun dispatchOnce(context: Context, command: SingleRelayCommand) {
        val key = context.observed.key
        if (closed || contexts[key] !== context || !currentRequest(key, command.request.requestId) ||
            !context.issuedRequests.add(command.request.requestId)) return
        if (port.mode == SingleRelayPortMode.EXISTING_CLIENT && !BuildConfig.PICK_RELAY_REAL_ENABLED) {
            stop(key, "REAL_RELAY_COMPILED_OFF"); return
        }
        try { port.dispatch(command) { result -> dispatchResult(key, command.request.requestId, result) } }
        catch (_: Exception) { stop(key, "DISPATCH_EXCEPTION") }
    }

    /** Permanent destination deletions only; the bridge supplies the exact current scope. */
    @Synchronized fun onDestinationDeleted(scope: AsyncRelayScope, messageIds: Collection<Long>) {
        if (closed || messageIds.isEmpty()) return
        val deleted = messageIds.filter { it > 0L }.toSet()
        contexts.keys.toList().forEach { key ->
            val old = ledger.read(key) ?: return@forEach
            if (old.terminal || old.intent.scope != scope) return@forEach
            val header = old.requests.first()
            if (header.stage == AsyncRelayStage.HEADER && header.finalMessageIds.values.any { it in deleted })
                stop(key, "FINAL_HEADER_DELETED")
        }
    }

    private fun currentRequest(key: RelayKey, requestId: String): Boolean {
        val old = ledger.read(key) ?: return false
        if (old.terminal || old.requests.last().requestId != requestId) return false
        val now = clockMillis()
        if (now < old.intent.createdMillis || now >= old.requests.last().deadlineMillis) { stop(key, "REQUEST_TIMEOUT"); return false }
        return true
    }

    @Synchronized private fun dispatchResult(key: RelayKey, requestId: String, result: SingleRelayDispatchResult) {
        if (closed) return // The bridge may already have closed its SQLite handles.
        val old = ledger.read(key) ?: return
        if (old.terminal) return
        if (result is SingleRelayDispatchResult.Rejected) { stop(key, "REQUEST_REJECTED_OR_AMBIGUOUS"); return }
        val response = (result as SingleRelayDispatchResult.Queued).response
        val current = old.requests.last()
        val expected = old.requests.firstOrNull { it.requestId == requestId }
        if (expected == null || response.requestId != requestId || response.scope != old.intent.scope) {
            stop(key, "RESPONSE_CORRELATION_MISMATCH"); return
        }
        val entries = response.messages?.toList()
        if (entries == null || entries.size != expected.expectedCount || entries.any { it == null } ||
            entries.filterNotNull().any { it.temporaryMessageId <= 0 || it.destinationChatId != old.intent.scope.destinationChatId ||
                !it.isOutgoing || !it.hasSendingState } || entries.filterNotNull().map { it.temporaryMessageId }.distinct().size != expected.expectedCount) {
            stop(key, "PARTIAL_OR_INVALID_QUEUED_RESPONSE"); return
        }
        val ids = entries.filterNotNull().map { it.temporaryMessageId }
        if (expected.responseBound) {
            if (expected.temporaryMessageIds != ids) stop(key, "CONFLICTING_QUEUED_RESPONSE")
            return
        }
        val now = clockMillis()
        if (current.requestId != requestId || now < old.intent.createdMillis || now >= current.deadlineMillis) { stop(key, "LATE_OR_UNEXPECTED_RESPONSE"); return }
        var bound = current.copy(responseBound = true, temporaryMessageIds = ids, earlyUpdates = emptyList())
        for (early in current.earlyUpdates) if (early.oldMessageId in ids) {
            bound = applyUpdate(bound, early) ?: run { stop(key, "EARLY_FINAL_FAILED_OR_CONFLICTING"); return }
        }
        val next = old.copy(requests = old.requests.dropLast(1) + bound, version = old.version + 1)
        if (!transition(old, next)) { stop(key, "CORRELATION_COLLISION"); return }
        advance(key)
    }

    @Synchronized fun onUpdate(update: AsyncRelayUpdate) {
        if (closed) return
        contexts.keys.toList().forEach { key ->
            val old = ledger.read(key) ?: return@forEach
            if (old.terminal || old.intent.scope != update.scope) return@forEach
            val now = clockMillis()
            if (now < old.intent.createdMillis || now >= old.requests.last().deadlineMillis) { stop(key, "REQUEST_TIMEOUT"); return@forEach }
            if (update.kind == AsyncRelayUpdateKind.ACKNOWLEDGED) return@forEach
            if (update.oldMessageId <= 0) { stop(key, "INVALID_TEMPORARY_ID"); return@forEach }
            val current = old.requests.last()
            val historical = old.requests.dropLast(1).firstOrNull { update.oldMessageId in it.temporaryMessageIds }
            if (historical != null) {
                if (!validSuccess(update) || historical.finalMessageIds[update.oldMessageId] != update.finalMessageId)
                    stop(key, "CONFLICTING_HISTORICAL_UPDATE")
                return@forEach
            }
            val changed = if (!current.responseBound) {
                if (update in current.earlyUpdates) return@forEach
                if (current.earlyUpdates.size >= AsyncRelayValidation.MAX_EARLY_UPDATES) {
                    stop(key, "EARLY_UPDATE_OVERFLOW"); return@forEach
                }
                current.copy(earlyUpdates = current.earlyUpdates + update)
            } else {
                if (update.oldMessageId !in current.temporaryMessageIds) return@forEach
                applyUpdate(current, update) ?: run { stop(key, "FAILED_OR_CONFLICTING_FINAL_UPDATE"); return@forEach }
            }
            if (changed == current) return@forEach
            val next = old.copy(requests = old.requests.dropLast(1) + changed, version = old.version + 1)
            if (!transition(old, next)) { stop(key, "CORRELATION_COLLISION"); return@forEach }
            advance(key)
        }
    }

    private fun validSuccess(update: AsyncRelayUpdate) = update.kind == AsyncRelayUpdateKind.SUCCEEDED &&
        update.finalMessageId > 0 && update.finalIsOutgoing && !update.finalHasSendingState

    private fun applyUpdate(request: AsyncRelayRequest, update: AsyncRelayUpdate): AsyncRelayRequest? {
        if (!validSuccess(update)) return null
        val previous = request.finalMessageIds[update.oldMessageId]
        if (previous != null) return if (previous == update.finalMessageId) request else null
        if (update.finalMessageId in request.finalMessageIds.values) return null
        return request.copy(finalMessageIds = request.finalMessageIds + (update.oldMessageId to update.finalMessageId))
    }

    private fun transition(old: AsyncRelaySnapshot, next: AsyncRelaySnapshot): Boolean =
        AsyncRelayValidation.validTransition(old, next) && ledger.compareAndSet(old.version, next)

    private fun advance(key: RelayKey) {
        val old = ledger.read(key) ?: return
        if (old.terminal || !AsyncRelayValidation.confirmed(old.requests.last())) return
        if (clockMillis() >= old.requests.last().deadlineMillis) { stop(key, "REQUEST_TIMEOUT"); return }
        if (old.phase == AsyncRelayPhase.MEDIA_WAITING || !old.intent.mediaRequired) {
            val next = old.copy(phase = AsyncRelayPhase.COMPLETE, version = old.version + 1)
            if (!transition(old, next)) { stop(key, "FINAL_STATE_CONFLICT"); return }
            contexts.remove(key)
            decisions[key]?.let { record(it.copy(reason = "FINAL_ACKS_COMPLETE", snapshot = next)) }
            return
        }
        val context = contexts[key] ?: run { stop(key, "TRANSIENT_CONTEXT_MISSING"); return }
        val now = clockMillis()
        if (now < old.intent.createdMillis || now > Long.MAX_VALUE - TIMEOUT_MILLIS) { stop(key, "INVALID_CLOCK"); return }
        val request = AsyncRelayRequest(UUID.randomUUID().toString(), AsyncRelayStage.MEDIA,
            old.intent.sourceMessageIds.toList(), now + TIMEOUT_MILLIS)
        val next = old.copy(phase = AsyncRelayPhase.MEDIA_WAITING, requests = old.requests + request, version = old.version + 1)
        if (!transition(old, next)) { stop(key, "MEDIA_RESERVATION_CONFLICT"); return }
        refreshBeforeDispatch(context, request)
    }

    @Synchronized fun stop(key: RelayKey, reason: String = "STOPPED"): AsyncRelaySnapshot? {
        starting.remove(key)
        contexts.remove(key)
        val safe = reason.filter { it in 'A'..'Z' || it in '0'..'9' || it == '_' }.take(80).ifBlank { "STOPPED" }
        repeat(64) {
            val old = ledger.read(key) ?: run {
                record(SingleRelayDecision(key, RelayAction.HOLD, safe))
                return null
            }
            if (old.terminal) return old
            val next = old.copy(phase = AsyncRelayPhase.UNKNOWN, version = old.version + 1, reason = safe)
            if (transition(old, next)) {
                decisions[key]?.let { record(it.copy(reason = safe, snapshot = next)) }
                return next
            }
        }
        closed = true // Storage ambiguity must never permit another output invocation.
        return ledger.read(key)
    }

    /** Disconnect, auth/session change and explicit STOP all revoke transient callbacks. */
    @Synchronized fun invalidate(accountId: Long, sessionId: String? = null, reason: String = "SESSION_INVALIDATED") {
        if (accountId <= 0L) return
        val affected = (starting.values.map { it.observed } + contexts.values.map { it.observed })
            .filter { it.accountId == accountId && (sessionId == null || it.sessionId == sessionId) }
        if (sessionId != null) revokedSessions += accountId to sessionId
        affected.forEach { observed ->
            revokedSessions += accountId to observed.sessionId
            stop(observed.key, reason)
            if (ledger.read(observed.key) == null) record(SingleRelayDecision(observed.key, RelayAction.HOLD, reason))
        }
        ledger.recoverPending(accountId, sessionId)
        if (revokedSessions.size > MAX_REVOKED) closed = true
    }

    @Synchronized fun expire() {
        if (closed) return
        val now = clockMillis()
        starting.values.toList().filter { now < it.started || now - it.started >= TIMEOUT_MILLIS }.forEach {
            starting.remove(it.observed.key)
            record(SingleRelayDecision(it.observed.key, RelayAction.HOLD, "INITIAL_SNAPSHOT_EXPIRED"))
        }
        contexts.keys.toList().forEach { key ->
            val old = ledger.read(key) ?: return@forEach
            if (!old.terminal && (now < old.intent.createdMillis || now >= old.requests.last().deadlineMillis)) stop(key, "REQUEST_TIMEOUT")
        }
    }

    @Synchronized fun close() {
        closed = true
        val accounts = (starting.values.map { it.observed.accountId } + contexts.values.map { it.observed.accountId }).distinct()
        accounts.forEach { invalidate(it, reason = "CONTROLLER_CLOSED") }
    }

    private fun record(decision: SingleRelayDecision): SingleRelayDecision {
        decisions[decision.key] = decision
        while (decisions.size > MAX_DECISIONS) decisions.remove(decisions.keys.first())
        return decision
    }

    companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val MAX_ACTIVE = 64
        const val MAX_REVOKED = 256
        const val MAX_DECISIONS = 256
    }
}
