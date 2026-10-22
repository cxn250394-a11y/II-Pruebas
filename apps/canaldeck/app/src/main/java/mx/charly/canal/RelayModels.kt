package mx.charly.canal

/** Explicit policy chosen by the owner for a TDLib account/chat identity, never by title. */
enum class RelaySourcePolicy {
    FANO_DIRECT, UNICOREY_MEX, UNICOREY_FOREIGN, UNICOREY_POPULAR, UNICOREY_KINGS,
    MAZDITA_LEGACY, DIRECT_OTHER;
    val reseller: Boolean get() = this in setOf(UNICOREY_MEX, UNICOREY_FOREIGN, UNICOREY_POPULAR, UNICOREY_KINGS)
}

data class RelaySourceBinding(
    val accountId: Long,
    val chatId: Long,
    val title: String,
    val policy: RelaySourcePolicy,
    val enabled: Boolean = false
)

data class RelayConfig(
    val accountId: Long,
    val destinationChatId: Long? = null,
    val userEnabled: Boolean = false,
    val sources: List<RelaySourceBinding> = emptyList(),
    val maxAgeSeconds: Long = 4 * 60 * 60
)

enum class RelayContentKind { TEXT, PHOTO, UNSUPPORTED }

/** A transient snapshot. Never persist text, caption, links or media in RelayLedger. */
data class RelayMessage(
    val accountId: Long,
    val sourceChatId: Long,
    val messageId: Long,
    val originalUnix: Long,
    val text: String = "",
    val kind: RelayContentKind = RelayContentKind.TEXT,
    val albumId: Long = 0,
    val outgoing: Boolean = false,
    val edited: Boolean = false,
    val canBeForwarded: Boolean = false,
    val hasProtectedContent: Boolean = false,
    val permalink: String? = null
)

data class RelayBatch(
    val messages: List<RelayMessage>,
    /** Album completeness must be established by the provider, not guessed from a quiet period. */
    val completeAlbum: Boolean = false,
    val expectedAlbumSize: Int? = null,
    val liveUpdate: Boolean = true,
    /** Only deterministic fixtures created by Fake TDLib may use synthetic delivery. */
    val synthetic: Boolean = false
)

data class RelayPermissions(
    val accountId: Long,
    val sourceChatId: Long,
    val destinationChatId: Long,
    val canReadSource: Boolean = false,
    val canWriteDestination: Boolean = false,
    val sourceHasProtectedContent: Boolean = false,
    /** False or missing message property fails closed to reference-only. */
    val messageForwardable: Map<Long, Boolean> = emptyMap(),
    val messageProtected: Map<Long, Boolean> = emptyMap()
)

enum class RelayAction { DROP, HOLD, REFERENCE_ONLY, FORWARD_ALLOWED_MEDIA }

data class RelayKey(val accountId: Long, val sourceChatId: Long, val groupId: String)

data class RelayPlan(
    val action: RelayAction,
    val reason: String,
    val key: RelayKey? = null,
    val header: String? = null,
    val messageIds: List<Long> = emptyList(),
    val protected: Boolean = false,
    val tipsterVerified: Boolean = false,
    val candidate: Boolean = false
)

data class RelayBaseline(val latestMessageId: Long, val notBeforeUnix: Long)
enum class RelayDeliveryState { PENDING, CONFIRMED, UNKNOWN }

enum class RelayMetric {
    SEEN, CANDIDATES, VERIFIED_TIPSTER, DISCARDED, PROTECTED, UNIQUE,
    LOCAL_NOTIFIED, PREPARED, REAL_CONFIRMED
}

data class RelayCounterKey(val accountId: Long, val sourceChatId: Long, val windowStartUnix: Long)

/** Atomic durable store; implementations retain IDs/states/counters, never message contents. */
interface RelayLedger {
    /** Monotonic live baseline: call after loading the latest ID on every activation/reconnect. */
    fun checkpoint(accountId: Long, sourceChatId: Long, latestMessageId: Long, notBeforeUnix: Long)
    fun baseline(accountId: Long, sourceChatId: Long): RelayBaseline?
    /** Atomically requires IDs above baseline and date strictly after its second, then inserts a unique observed key. False means replay/stale/unarmed. */
    fun acceptObserved(key: RelayKey, messageIds: List<Long>, originalUnix: Long): Boolean
    /** Atomic INSERT PENDING; never resets existing PENDING, CONFIRMED or UNKNOWN. */
    fun claim(key: RelayKey): Boolean
    fun state(key: RelayKey): RelayDeliveryState?
    /** Only PENDING -> CONFIRMED/UNKNOWN is permitted; UNKNOWN is terminal to automatic delivery. */
    fun finish(key: RelayKey, state: RelayDeliveryState)
    /** On process/session start, all unresolved PENDING entries become UNKNOWN, never retried. */
    fun recoverPending(accountId: Long): Int
    fun increment(key: RelayCounterKey, metric: RelayMetric, amount: Long = 1)
    fun counters(key: RelayCounterKey): Map<RelayMetric, Long>
}

enum class RelayAck { CONFIRMED, UNKNOWN, REJECTED }

/** No real TDLib client implements this interface in v0.4. */
interface RelayTransport {
    val synthetic: Boolean
    /** Re-read the original records before each request; never deliver an edited/stale snapshot. */
    fun refreshBatch(batch: RelayBatch): RelayBatch
    fun currentPermissions(batch: RelayBatch, config: RelayConfig): RelayPermissions
    fun sendHeader(destinationChatId: Long, header: String): RelayAck
    /** Official forward semantics: original IDs, complete album, never media bytes or copied captions. */
    fun forwardOriginals(destinationChatId: Long, sourceChatId: Long, messageIds: List<Long>): RelayAck
}

data class RelayOutcome(val plan: RelayPlan, val deliveryState: RelayDeliveryState? = null)
