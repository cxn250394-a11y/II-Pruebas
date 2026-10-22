package mx.charly.canal

/** Local account generation, destination and session identity; never a credential. */
data class AsyncRelayScope(val accountId: Long, val sessionId: String, val destinationChatId: Long)

enum class AsyncRelayStage { HEADER, MEDIA }
enum class AsyncRelayPhase { HEADER_WAITING, MEDIA_WAITING, COMPLETE, UNKNOWN }
enum class AsyncRelayUpdateKind { ACKNOWLEDGED, SUCCEEDED, FAILED }

/** Durable IDs only. The source list is the exact original publication, not album proof. */
data class AsyncRelayIntent(
    val key: RelayKey,
    val scope: AsyncRelayScope,
    val sourceMessageIds: List<Long>,
    val mediaRequired: Boolean,
    val createdMillis: Long,
    val timeoutMillis: Long = 30_000,
    /** Monotonic owner consent/config revision, including OFF -> ON transitions. */
    val configurationRevision: Long = 0
)

/** oldMessageId is the temporary ID IN THE DESTINATION, never a source ID. */
data class AsyncRelayUpdate(
    val scope: AsyncRelayScope,
    val kind: AsyncRelayUpdateKind,
    val oldMessageId: Long,
    val finalMessageId: Long = 0,
    val finalIsOutgoing: Boolean = false,
    val finalHasSendingState: Boolean = true
)

data class AsyncRelayQueuedMessage(
    val temporaryMessageId: Long,
    val destinationChatId: Long,
    val isOutgoing: Boolean = true,
    val hasSendingState: Boolean = true
)

data class AsyncRelayResponse(
    val scope: AsyncRelayScope,
    val requestId: String,
    /** Forward result order MUST match expectedSourceMessageIds; null is a partial failure. */
    val messages: List<AsyncRelayQueuedMessage?>?
)

/** Immutable request metadata is persisted BEFORE invoking any output function. */
data class AsyncRelayRequest(
    val requestId: String,
    val stage: AsyncRelayStage,
    val expectedSourceMessageIds: List<Long>,
    val deadlineMillis: Long,
    val responseBound: Boolean = false,
    val temporaryMessageIds: List<Long> = emptyList(),
    val finalMessageIds: Map<Long, Long> = emptyMap(),
    val earlyUpdates: List<AsyncRelayUpdate> = emptyList()
) {
    val expectedCount: Int get() = if (stage == AsyncRelayStage.HEADER) 1 else expectedSourceMessageIds.size
}

data class AsyncRelaySnapshot(
    val intent: AsyncRelayIntent,
    val phase: AsyncRelayPhase,
    /** Header history is retained when media begins, for exact correlation and unique IDs. */
    val requests: List<AsyncRelayRequest>,
    val version: Long = 0,
    val reason: String = ""
) {
    val key: RelayKey get() = intent.key
    val terminal: Boolean get() = phase == AsyncRelayPhase.COMPLETE || phase == AsyncRelayPhase.UNKNOWN
}

fun AsyncRelaySnapshot.detached(): AsyncRelaySnapshot = copy(
    intent = intent.copy(sourceMessageIds = intent.sourceMessageIds.toList()),
    requests = requests.map { it.copy(
        expectedSourceMessageIds = it.expectedSourceMessageIds.toList(),
        temporaryMessageIds = it.temporaryMessageIds.toList(),
        finalMessageIds = it.finalMessageIds.toMap(), earlyUpdates = it.earlyUpdates.toList()
    ) }
)

/**
 * Atomic durable metadata store. Implementations must defensively copy inputs and outputs.
 * create reserves key AND each account/source/member-ID alias (also across destinations/sessions).
 * compareAndSet is versioned, preserves immutable intent/request identities, and permits only
 * WAITING -> WAITING/COMPLETE/UNKNOWN. UNKNOWN and COMPLETE are sticky. Temporary IDs and final
 * IDs are unique within scope across requests; collision must fail closed, never reassign an ID.
 * No caption, header, permalink, message body or media bytes may be persisted here.
 */
interface AsyncRelayLedger {
    fun create(snapshot: AsyncRelaySnapshot): Boolean
    fun read(key: RelayKey): AsyncRelaySnapshot?
    fun compareAndSet(expectedVersion: Long, next: AsyncRelaySnapshot): Boolean
    /** Explicit process recovery: unresolved requests become UNKNOWN and are never dispatched. */
    fun recoverPending(accountId: Long, sessionId: String? = null): Int
}

data class AsyncRelayFreshState(
    val scope: AsyncRelayScope,
    val batch: RelayBatch,
    val config: RelayConfig,
    val permissions: RelayPermissions,
    val configurationRevision: Long = 0
)

/** Fake-only lane. No TDLib client implements this interface. */
interface AsyncRelayFakeTransport {
    val synthetic: Boolean
    /** Re-read config, session, batch and permissions; engine supplies its CURRENT clock. */
    fun refresh(): AsyncRelayFreshState?
    fun sendHeader(request: AsyncRelayRequest, scope: AsyncRelayScope, header: String): AsyncRelayResponse
    fun forwardOriginals(request: AsyncRelayRequest, scope: AsyncRelayScope, sourceChatId: Long): AsyncRelayResponse
}
