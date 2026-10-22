package mx.charly.canal

/** Shared fail-closed validation for the in-memory and SQLite ledgers. */
object AsyncRelayValidation {
    const val MAX_EARLY_UPDATES = 64
    fun valid(snapshot: AsyncRelaySnapshot): Boolean {
        val i = snapshot.intent
        if (i.key.accountId <= 0 || i.key.accountId != i.scope.accountId || i.key.sourceChatId == 0L ||
            i.scope.destinationChatId == 0L || i.scope.destinationChatId == i.key.sourceChatId ||
            !i.scope.sessionId.matches(Regex("[A-Za-z0-9_.:-]{1,128}")) || i.createdMillis < 0 ||
            i.configurationRevision < 0 || i.configurationRevision == Long.MAX_VALUE ||
            i.timeoutMillis !in 1..300_000 || snapshot.version < 0 ||
            i.sourceMessageIds.isEmpty() || i.sourceMessageIds.size > 10 ||
            i.sourceMessageIds != i.sourceMessageIds.distinct().sorted() || i.sourceMessageIds.any { it <= 0L } ||
            !snapshot.reason.matches(Regex("[A-Z0-9_]{0,96}"))) return false
        val groupId = i.key.groupId
        if (groupId.startsWith("m:")) {
            if (i.sourceMessageIds.size != 1 || groupId != "m:${i.sourceMessageIds.single()}") return false
        } else if (!groupId.matches(Regex("a:[1-9][0-9]*")) || groupId.substring(2).toLongOrNull() == null || i.sourceMessageIds.size < 2) return false
        if (snapshot.requests.size !in 1..2 || snapshot.requests.map { it.requestId }.distinct().size != snapshot.requests.size) return false
        snapshot.requests.forEachIndexed { index, r ->
            if (!r.requestId.matches(Regex("[A-Za-z0-9_.:-]{1,128}")) || r.deadlineMillis < i.createdMillis ||
                r.stage != (if (index == 0) AsyncRelayStage.HEADER else AsyncRelayStage.MEDIA) ||
                r.expectedSourceMessageIds != (if (index == 0) emptyList<Long>() else i.sourceMessageIds) ||
                r.earlyUpdates.size > MAX_EARLY_UPDATES) return false
            if (!r.responseBound && (r.temporaryMessageIds.isNotEmpty() || r.finalMessageIds.isNotEmpty())) return false
            if (r.responseBound && (r.temporaryMessageIds.size != r.expectedCount ||
                r.temporaryMessageIds.any { it == 0L } || r.temporaryMessageIds.distinct().size != r.expectedCount || r.earlyUpdates.isNotEmpty())) return false
            if (r.finalMessageIds.keys.any { it !in r.temporaryMessageIds } || r.finalMessageIds.values.any { it <= 0L } ||
                r.finalMessageIds.values.distinct().size != r.finalMessageIds.size) return false
            if (r.earlyUpdates.any { it.scope != i.scope || it.oldMessageId == 0L || it.kind == AsyncRelayUpdateKind.ACKNOWLEDGED }) return false
        }
        val header = snapshot.requests.first()
        if (snapshot.requests.size == 2 && (!i.mediaRequired || !confirmed(header))) return false
        if (snapshot.phase == AsyncRelayPhase.HEADER_WAITING && snapshot.requests.size != 1) return false
        if (snapshot.phase == AsyncRelayPhase.MEDIA_WAITING && snapshot.requests.size != 2) return false
        if (snapshot.phase == AsyncRelayPhase.COMPLETE && (!confirmed(snapshot.requests.last()) ||
                i.mediaRequired && snapshot.requests.size != 2)) return false
        val temps = snapshot.requests.flatMap { it.temporaryMessageIds }
        val finals = snapshot.requests.flatMap { it.finalMessageIds.values }
        return temps.distinct().size == temps.size && finals.distinct().size == finals.size
    }

    fun confirmed(request: AsyncRelayRequest): Boolean = request.responseBound &&
        request.finalMessageIds.size == request.expectedCount && request.finalMessageIds.keys.toSet() == request.temporaryMessageIds.toSet()

    fun validTransition(old: AsyncRelaySnapshot, next: AsyncRelaySnapshot): Boolean {
        if (!valid(next) || old.terminal || old.intent != next.intent || next.version != old.version + 1 ||
            next.requests.size !in old.requests.size..(old.requests.size + 1)) return false
        old.requests.forEachIndexed { index, prior ->
            val r = next.requests[index]
            if (r.requestId != prior.requestId || r.stage != prior.stage || r.expectedSourceMessageIds != prior.expectedSourceMessageIds ||
                r.deadlineMillis != prior.deadlineMillis || prior.responseBound && (!r.responseBound || r.temporaryMessageIds != prior.temporaryMessageIds) ||
                prior.finalMessageIds.any { r.finalMessageIds[it.key] != it.value }) return false
        }
        return next.phase == AsyncRelayPhase.UNKNOWN || when (old.phase) {
            AsyncRelayPhase.HEADER_WAITING -> next.phase in setOf(AsyncRelayPhase.HEADER_WAITING, AsyncRelayPhase.MEDIA_WAITING, AsyncRelayPhase.COMPLETE)
            AsyncRelayPhase.MEDIA_WAITING -> next.phase in setOf(AsyncRelayPhase.MEDIA_WAITING, AsyncRelayPhase.COMPLETE)
            else -> false
        }
    }
}
