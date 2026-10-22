package mx.charly.canal

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline asynchronous rehearsal. A queued response is never delivery evidence. Only exact,
 * final destination updates advance a durable request, and UNKNOWN never resumes or retries.
 * No application TDLib transport implements AsyncRelayFakeTransport.
 */
class AsyncRelayEngine(
    private val ledger: AsyncRelayLedger,
    private val transport: AsyncRelayFakeTransport,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    private data class Context(val fresh: AsyncRelayFreshState, val evidence: AlbumCompletenessEvidence)
    private val contexts = ConcurrentHashMap<RelayKey, Context>()

    fun snapshot(key: RelayKey): AsyncRelaySnapshot? = ledger.read(key)

    fun start(intent: AsyncRelayIntent, evidence: AlbumCompletenessEvidence = AlbumCompletenessEvidence.ProviderUnknown): AsyncRelaySnapshot? =
        startFrozen(intent.copy(sourceMessageIds = intent.sourceMessageIds.toList()), evidence)

    private fun startFrozen(intent: AsyncRelayIntent, evidence: AlbumCompletenessEvidence): AsyncRelaySnapshot? {
        ledger.read(intent.key)?.let { return it } // Never dispatch by replaying an existing intent.
        if (!transport.synthetic) return null
        val proof = when (evidence) {
            is AlbumCompletenessEvidence.FakeManifest -> evidence.copy(memberIds = evidence.memberIds.toList())
            AlbumCompletenessEvidence.ProviderUnknown -> evidence
        }
        val fresh = fresh(proof) ?: return null
        val plan = PickRelayPlanner.plan(fresh.batch, fresh.config, fresh.permissions, clockMillis() / 1000)
        val ids = fresh.batch.messages.map { it.messageId }.sorted()
        if (fresh.scope != intent.scope || fresh.configurationRevision != intent.configurationRevision ||
            ids != intent.sourceMessageIds || plan.key != intent.key || plan.header == null ||
            plan.action !in setOf(RelayAction.REFERENCE_ONLY, RelayAction.FORWARD_ALLOWED_MEDIA) ||
            intent.mediaRequired != (plan.action == RelayAction.FORWARD_ALLOWED_MEDIA)) return null
        val now = clockMillis()
        if (intent.createdMillis !in 0..now || now - intent.createdMillis > intent.timeoutMillis ||
            now < 0 || now > Long.MAX_VALUE - intent.timeoutMillis) return null
        val request = AsyncRelayRequest(UUID.randomUUID().toString(), AsyncRelayStage.HEADER, emptyList(), now + intent.timeoutMillis)
        val initial = AsyncRelaySnapshot(intent.copy(sourceMessageIds = intent.sourceMessageIds.toList()),
            AsyncRelayPhase.HEADER_WAITING, listOf(request))
        if (!AsyncRelayValidation.valid(initial)) return null
        contexts[intent.key] = Context(fresh, proof)
        if (!ledger.create(initial)) {
            contexts.remove(intent.key)
            return ledger.read(intent.key)
        }
        if (!authorize(contexts[intent.key], intent, plan.action) ||
            ledger.read(intent.key)?.let { it.terminal || expired(it) || it.requests.last().requestId != request.requestId } != false) {
            return stop(intent.key, "CURRENT_STATE_CHANGED_BEFORE_HEADER")
        }
        // Intent is already durable. An exception or crash from here must never cause resubmission.
        try {
            val response = transport.sendHeader(request, intent.scope, plan.header)
            bindResponse(intent.key, request.requestId, response)
        } catch (_: Exception) { stop(intent.key, "HEADER_REQUEST_EXCEPTION") }
        return ledger.read(intent.key)
    }

    fun onUpdate(key: RelayKey, update: AsyncRelayUpdate): AsyncRelaySnapshot? {
        while (true) {
            val old = ledger.read(key) ?: return null
            if (old.terminal || old.intent.scope != update.scope) return old
            if (expired(old)) return stop(key, "REQUEST_TIMEOUT")
            if (update.kind == AsyncRelayUpdateKind.ACKNOWLEDGED) return old
            if (update.oldMessageId == 0L) return stop(key, "INVALID_UPDATE_ID")
            val current = old.requests.last()
            val historical = old.requests.dropLast(1).firstOrNull { update.oldMessageId in it.temporaryMessageIds }
            if (historical != null) {
                if (update.kind == AsyncRelayUpdateKind.SUCCEEDED && validSuccess(update) &&
                    historical.finalMessageIds[update.oldMessageId] == update.finalMessageId) return old
                return stop(key, "CONFLICTING_HISTORICAL_UPDATE")
            }
            val changed = if (!current.responseBound) {
                if (update in current.earlyUpdates) return old
                if (current.earlyUpdates.size >= AsyncRelayValidation.MAX_EARLY_UPDATES) return stop(key, "EARLY_UPDATE_OVERFLOW")
                current.copy(earlyUpdates = current.earlyUpdates + update)
            } else {
                if (update.oldMessageId !in current.temporaryMessageIds) return old
                applyUpdate(current, update) ?: return stop(key, "FAILED_OR_CONFLICTING_FINAL_UPDATE")
            }
            if (changed == current) return old
            val next = old.copy(requests = old.requests.dropLast(1) + changed, version = old.version + 1)
            if (!AsyncRelayValidation.validTransition(old, next)) return stop(key, "CORRELATION_COLLISION")
            if (ledger.compareAndSet(old.version, next)) {
                advance(key)
                return ledger.read(key)
            }
        }
    }

    /** Caller invokes on its local timer; expiration never retransmits a request. */
    fun expire(key: RelayKey): AsyncRelaySnapshot? {
        val old = ledger.read(key) ?: return null
        return if (!old.terminal && expired(old)) stop(key, "REQUEST_TIMEOUT") else old
    }

    /** Explicit close, auth/session change, cancellation or unknown provider outcome. */
    fun stop(key: RelayKey, reason: String = "STOPPED"): AsyncRelaySnapshot? {
        while (true) {
            val old = ledger.read(key) ?: return null
            if (old.terminal) return old
            val safeReason = reason.filter { it in 'A'..'Z' || it in '0'..'9' || it == '_' }.take(80).ifBlank { "STOPPED" }
            if (ledger.compareAndSet(old.version, old.copy(phase = AsyncRelayPhase.UNKNOWN,
                    version = old.version + 1, reason = safeReason))) {
                contexts.remove(key)
                return ledger.read(key)
            }
        }
    }

    private fun fresh(evidence: AlbumCompletenessEvidence): AsyncRelayFreshState? {
        val raw = try { transport.refresh() } catch (_: Exception) { null } ?: return null
        if (!transport.synthetic || !raw.batch.synthetic) return null
        val copied = raw.copy(
            batch = raw.batch.copy(messages = raw.batch.messages.toList()),
            config = raw.config.copy(sources = raw.config.sources.toList()),
            permissions = raw.permissions.copy(messageForwardable = raw.permissions.messageForwardable.toMap(),
                messageProtected = raw.permissions.messageProtected.toMap())
        )
        val canonical = AlbumCompletenessGate.reconstruct(copied.batch, evidence)
        if (canonical.hold) return null
        return copied.copy(batch = canonical.batch)
    }

    private fun expired(snapshot: AsyncRelaySnapshot): Boolean = clockMillis() >= snapshot.requests.last().deadlineMillis

    private fun bindResponse(key: RelayKey, requestId: String, response: AsyncRelayResponse) {
        while (true) {
            val old = ledger.read(key) ?: return
            if (old.terminal) return
            if (expired(old)) { stop(key, "REQUEST_TIMEOUT"); return }
            val current = old.requests.last()
            if (current.requestId != requestId || response.requestId != requestId || response.scope != old.intent.scope) {
                stop(key, "RESPONSE_CORRELATION_MISMATCH"); return
            }
            val entries = response.messages?.toList()
            if (entries == null || entries.size != current.expectedCount || entries.any { it == null } ||
                entries.filterNotNull().any { it.temporaryMessageId == 0L || it.destinationChatId != old.intent.scope.destinationChatId ||
                    !it.isOutgoing || !it.hasSendingState } ||
                entries.filterNotNull().map { it.temporaryMessageId }.distinct().size != current.expectedCount) {
                stop(key, "PARTIAL_OR_INVALID_QUEUED_RESPONSE"); return
            }
            val ids = entries.filterNotNull().map { it.temporaryMessageId }
            if (current.responseBound) {
                if (current.temporaryMessageIds != ids) stop(key, "CONFLICTING_QUEUED_RESPONSE")
                return
            }
            var bound = current.copy(responseBound = true, temporaryMessageIds = ids, earlyUpdates = emptyList())
            for (early in current.earlyUpdates) if (early.oldMessageId in ids) {
                bound = applyUpdate(bound, early) ?: run { stop(key, "EARLY_FINAL_FAILED_OR_CONFLICTING"); return }
            }
            val next = old.copy(requests = old.requests.dropLast(1) + bound, version = old.version + 1)
            if (!AsyncRelayValidation.validTransition(old, next)) { stop(key, "CORRELATION_COLLISION"); return }
            if (ledger.compareAndSet(old.version, next)) { advance(key); return }
        }
    }

    private fun validSuccess(u: AsyncRelayUpdate): Boolean = u.kind == AsyncRelayUpdateKind.SUCCEEDED &&
        u.finalMessageId > 0 && u.finalIsOutgoing && !u.finalHasSendingState

    private fun applyUpdate(r: AsyncRelayRequest, u: AsyncRelayUpdate): AsyncRelayRequest? {
        if (!validSuccess(u)) return null
        val previous = r.finalMessageIds[u.oldMessageId]
        if (previous != null) return if (previous == u.finalMessageId) r else null
        if (u.finalMessageId in r.finalMessageIds.values) return null
        return r.copy(finalMessageIds = r.finalMessageIds + (u.oldMessageId to u.finalMessageId))
    }

    private fun authorize(context: Context?, intent: AsyncRelayIntent, action: RelayAction): Boolean {
        context ?: return false
        val current = fresh(context.evidence) ?: return false
        val now = clockMillis()
        val plan = PickRelayPlanner.plan(current.batch, current.config, current.permissions, now / 1000)
        return now >= intent.createdMillis && current == context.fresh && current.scope == intent.scope &&
            current.configurationRevision == intent.configurationRevision && plan.key == intent.key && plan.action == action
    }

    private fun advance(key: RelayKey) {
        while (true) {
            val old = ledger.read(key) ?: return
            if (old.terminal || !AsyncRelayValidation.confirmed(old.requests.last())) return
            if (expired(old)) { stop(key, "REQUEST_TIMEOUT"); return }
            if (old.phase == AsyncRelayPhase.MEDIA_WAITING || !old.intent.mediaRequired) {
                if (ledger.compareAndSet(old.version, old.copy(phase = AsyncRelayPhase.COMPLETE, version = old.version + 1))) {
                    contexts.remove(key); return
                }
                continue
            }
            val context = contexts[key] ?: run { stop(key, "TRANSIENT_CONTEXT_MISSING"); return }
            val current = fresh(context.evidence) ?: run { stop(key, "FRESH_STATE_UNAVAILABLE"); return }
            val now = clockMillis()
            val plan = PickRelayPlanner.plan(current.batch, current.config, current.permissions, now / 1000)
            if (current != context.fresh || current.scope != old.intent.scope ||
                current.configurationRevision != old.intent.configurationRevision || plan.key != key ||
                plan.action != RelayAction.FORWARD_ALLOWED_MEDIA || plan.messageIds != old.intent.sourceMessageIds ||
                now < 0 || now > Long.MAX_VALUE - old.intent.timeoutMillis) {
                stop(key, "CURRENT_CONSENT_SESSION_CONTENT_OR_PERMISSION_CHANGED"); return
            }
            val media = AsyncRelayRequest(UUID.randomUUID().toString(), AsyncRelayStage.MEDIA,
                old.intent.sourceMessageIds.toList(), now + old.intent.timeoutMillis)
            val next = old.copy(phase = AsyncRelayPhase.MEDIA_WAITING, requests = old.requests + media, version = old.version + 1)
            if (!ledger.compareAndSet(old.version, next)) continue
            if (!authorize(context, old.intent, RelayAction.FORWARD_ALLOWED_MEDIA) ||
                ledger.read(key)?.let { it.terminal || expired(it) || it.requests.last().requestId != media.requestId } != false) {
                stop(key, "CURRENT_STATE_CHANGED_BEFORE_MEDIA"); return
            }
            try {
                val response = transport.forwardOriginals(media, old.intent.scope, key.sourceChatId)
                bindResponse(key, media.requestId, response)
            } catch (_: Exception) { stop(key, "MEDIA_REQUEST_EXCEPTION") }
            return
        }
    }
}
