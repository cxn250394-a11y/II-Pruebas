package mx.charly.canal

/** Fake stores IDs only and mirrors the durable ledger's atomic/terminal rules. */
class FakeAsyncRelayLedger : AsyncRelayLedger {
    private val rows = linkedMapOf<RelayKey, AsyncRelaySnapshot>()

    @Synchronized override fun create(snapshot: AsyncRelaySnapshot): Boolean {
        val s = snapshot.detached()
        if (!AsyncRelayValidation.valid(s) || s.version != 0L || s.phase != AsyncRelayPhase.HEADER_WAITING ||
            s.requests.size != 1 || s.requests.single().responseBound || s.requests.single().earlyUpdates.isNotEmpty()) return false
        if (s.key in rows || rows.values.any { existing ->
                existing.key.accountId == s.key.accountId && existing.key.sourceChatId == s.key.sourceChatId &&
                    existing.intent.sourceMessageIds.any { it in s.intent.sourceMessageIds }
            }) return false
        val conflicts = rows.values.filter { other -> other.intent.scope == s.intent.scope &&
            other.requests.any { r -> s.requests.any { it.requestId == r.requestId } } }
        rows[s.key] = s
        if (conflicts.isNotEmpty()) {
            (conflicts + s).filterNot { it.terminal }.forEach {
                rows[it.key] = it.copy(phase = AsyncRelayPhase.UNKNOWN, version = it.version + 1, reason = "CORRELATION_COLLISION").detached()
            }
            return false
        }
        return true
    }

    @Synchronized override fun read(key: RelayKey): AsyncRelaySnapshot? = rows[key]?.detached()

    @Synchronized override fun compareAndSet(expectedVersion: Long, next: AsyncRelaySnapshot): Boolean {
        val old = rows[next.key] ?: return false
        val s = next.detached()
        if (old.version != expectedVersion || !AsyncRelayValidation.validTransition(old, s)) return false
        val tempIds = s.requests.flatMap { it.temporaryMessageIds }.toSet()
        val finalIds = s.requests.flatMap { it.finalMessageIds.values }.toSet()
        val conflicts = rows.values.filter { other -> other.key != s.key && other.intent.scope == s.intent.scope &&
            (other.requests.any { r -> r.temporaryMessageIds.any { it in tempIds } || r.finalMessageIds.values.any { it in finalIds } } ||
                other.requests.any { r -> s.requests.any { it.requestId == r.requestId } }) }
        if (conflicts.isNotEmpty()) {
            (conflicts + old).filterNot { it.terminal }.forEach {
                rows[it.key] = it.copy(phase = AsyncRelayPhase.UNKNOWN, version = it.version + 1, reason = "CORRELATION_COLLISION").detached()
            }
            return false
        }
        rows[s.key] = s
        return true
    }

    @Synchronized override fun recoverPending(accountId: Long, sessionId: String?): Int {
        val pending = rows.values.filter { !it.terminal && it.intent.scope.accountId == accountId &&
            (sessionId == null || it.intent.scope.sessionId == sessionId) }
        pending.forEach { rows[it.key] = it.copy(phase = AsyncRelayPhase.UNKNOWN,
            version = it.version + 1, reason = "PROCESS_RECOVERY").detached() }
        return pending.size
    }
}

data class FakeAsyncRelayOutput(val stage: AsyncRelayStage, val requestId: String, val scope: AsyncRelayScope,
    val sourceChatId: Long, val sourceMessageIds: List<Long>)

/** No client, network, downloads, media bytes, real account, or real output. */
class FakeAsyncRelayTransport(initial: AsyncRelayFreshState) : AsyncRelayFakeTransport {
    override val synthetic: Boolean = true
    val sentRealMessages: Int get() = 0
    var freshState: AsyncRelayFreshState? = initial
    val outputs = mutableListOf<FakeAsyncRelayOutput>()
    val requests: List<FakeAsyncRelayOutput> get() = outputs.toList()
    var nextHeaderResponse: AsyncRelayResponse? = null
    var nextMediaResponse: AsyncRelayResponse? = null
    var headerResponseFactory: ((AsyncRelayRequest, AsyncRelayScope) -> AsyncRelayResponse)? = null
    var mediaResponseFactory: ((AsyncRelayRequest, AsyncRelayScope) -> AsyncRelayResponse)? = null
    var beforeHeaderResponse: (AsyncRelayRequest) -> Unit = {}
    var beforeMediaResponse: (AsyncRelayRequest) -> Unit = {}
    private var nextTemporaryId = 100_000L

    override fun refresh(): AsyncRelayFreshState? = freshState

    override fun sendHeader(request: AsyncRelayRequest, scope: AsyncRelayScope, header: String): AsyncRelayResponse {
        require(header.isNotBlank())
        outputs += FakeAsyncRelayOutput(AsyncRelayStage.HEADER, request.requestId, scope, 0, emptyList())
        val result = headerResponseFactory?.invoke(request, scope) ?: nextHeaderResponse ?: response(request, scope)
        beforeHeaderResponse(request)
        return result
    }

    override fun forwardOriginals(request: AsyncRelayRequest, scope: AsyncRelayScope, sourceChatId: Long): AsyncRelayResponse {
        outputs += FakeAsyncRelayOutput(AsyncRelayStage.MEDIA, request.requestId, scope, sourceChatId, request.expectedSourceMessageIds.toList())
        val result = mediaResponseFactory?.invoke(request, scope) ?: nextMediaResponse ?: response(request, scope)
        beforeMediaResponse(request)
        return result
    }

    private fun response(request: AsyncRelayRequest, scope: AsyncRelayScope): AsyncRelayResponse = AsyncRelayResponse(scope, request.requestId,
        List(request.expectedCount) { AsyncRelayQueuedMessage(nextTemporaryId++, scope.destinationChatId) })
}
