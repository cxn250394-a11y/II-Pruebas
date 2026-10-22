package mx.charly.canal

/** Metadata captured by the one existing update stream, before any TDLib query. */
data class SingleRelayObserved(
    val accountId: Long,
    val sessionId: String,
    val sourceChatId: Long,
    val messageId: Long,
    val originalUnix: Long,
    val albumId: Long = 0,
    val incoming: Boolean = true,
    val edited: Boolean = false
) {
    val key: RelayKey get() = RelayKey(accountId, sourceChatId, "m:$messageId")
}

data class SingleRelayFreshState(
    val state: AsyncRelayFreshState,
    val connected: Boolean,
    /** Separate owner choice for Fano photos with no caption. Never infers image semantics. */
    val photoOnlyConsent: Boolean = false
)

enum class SingleRelayPortMode { FAKE, EXISTING_CLIENT }

/** Transient content only; the durable AsyncRelayRequest contains identifiers, never this header. */
data class SingleRelayCommand(
    val key: RelayKey,
    val request: AsyncRelayRequest,
    val scope: AsyncRelayScope,
    val sourceChatId: Long,
    val header: String? = null
)

sealed class SingleRelayDispatchResult {
    data class Queued(val response: AsyncRelayResponse) : SingleRelayDispatchResult()
    data object Rejected : SingleRelayDispatchResult()
}

/** Callback port: native suspend calls are never blocked by the integration controller. */
interface SingleRelayPort {
    val mode: SingleRelayPortMode
    fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit)
    fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit)
}

/** Must atomically apply the armed durable live baseline and reserve the observed publication. */
fun interface SingleRelayAdmission {
    fun admit(observed: SingleRelayObserved): Boolean
}

data class SingleRelayDecision(
    val key: RelayKey,
    val action: RelayAction,
    val reason: String,
    val semantics: String = "TEXT_NOT_CLASSIFIED",
    val snapshot: AsyncRelaySnapshot? = null
)
