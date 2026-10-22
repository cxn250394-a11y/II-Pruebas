package mx.charly.canal

/** The single session's current identity and committed configuration, never a credential. */
data class ExistingClientRelayContext(
    val scope: AsyncRelayScope,
    val config: RelayConfig,
    val configurationRevision: Long,
    val connected: Boolean,
    val photoOnlyConsent: Boolean = false
)

/** Injectable operations on a supplied client; tests provide a recording Fake, never TDLib. */
internal interface ExistingClientRelayCalls {
    suspend fun snapshot(observed: SingleRelayObserved, config: RelayConfig): Pair<RelayBatch, RelayPermissions>?
    suspend fun queue(command: SingleRelayCommand): AsyncRelayResponse?
}

/**
 * Callback adapter for the controller, using the existing session's coroutine scope.
 * The literal compiled fuse is checked before launching, before every provider operation,
 * and after its response. No constructor creates a client, session, collector or retry job.
 */
internal class ExistingClientRelayPort(
    private val calls: ExistingClientRelayCalls,
    private val context: (Long) -> ExistingClientRelayContext?,
    private val launch: (suspend () -> Unit) -> Unit,
    private val beforeQueue: (SingleRelayCommand) -> Boolean = { true },
    private val afterQueued: (AsyncRelayResponse) -> Boolean = { true },
    private val queueUncertain: (SingleRelayCommand) -> Unit = {}
) : SingleRelayPort {
    override val mode = SingleRelayPortMode.EXISTING_CLIENT

    private fun authorized(source: Long, expectedScope: AsyncRelayScope? = null): ExistingClientRelayContext? {
        // This must precede context/prefs access as well as any SDK call.
        if (!BuildConfig.PICK_RELAY_REAL_ENABLED) return null
        val current = context(source) ?: return null
        if (!RelayRealGate.isOpen(current.config.userEnabled) || !current.connected ||
            current.scope.accountId <= 0L || current.scope.sessionId.isBlank() ||
            current.scope.accountId != current.config.accountId ||
            current.scope.destinationChatId != current.config.destinationChatId ||
            current.scope.destinationChatId == source || current.configurationRevision < 0L ||
            current.config.sources.count { it.enabled && it.accountId == current.scope.accountId &&
                it.chatId == source && it.policy in RelaySettingsSelection.allowedPolicies } != 1 ||
            expectedScope != null && current.scope != expectedScope) return null
        return current
    }

    override fun refresh(observed: SingleRelayObserved, callback: (SingleRelayFreshState?) -> Unit) {
        if (observed.albumId != 0L || !observed.incoming || observed.edited || observed.messageId <= 0L) {
            callback(null); return
        }
        val initial = authorized(observed.sourceChatId)?.takeIf {
            it.scope.accountId == observed.accountId && it.scope.sessionId == observed.sessionId
        } ?: run { callback(null); return }
        launch {
            val fresh = try {
                if (authorized(observed.sourceChatId, initial.scope) != initial) null
                else {
                    val result = calls.snapshot(observed, initial.config)
                    if (result == null || authorized(observed.sourceChatId, initial.scope) != initial) null
                    else SingleRelayFreshState(AsyncRelayFreshState(initial.scope, result.first,
                        initial.config, result.second, initial.configurationRevision),
                        initial.connected, initial.photoOnlyConsent)
                }
            } catch (_: Exception) { null }
            // A consumer exception must never cause a second callback or provider request.
            callback(fresh)
        }
    }

    override fun dispatch(command: SingleRelayCommand, callback: (SingleRelayDispatchResult) -> Unit) {
        val initial = authorized(command.sourceChatId, command.scope)
        if (initial == null || command.key.accountId != command.scope.accountId ||
            command.key.sourceChatId != command.sourceChatId ||
            !command.key.groupId.matches(Regex("m:[1-9][0-9]{0,18}")) || command.key.groupId.substring(2).toLongOrNull() == null ||
            command.request.expectedCount != 1 || command.request.responseBound ||
            command.request.stage == AsyncRelayStage.HEADER && command.header.isNullOrBlank() ||
            command.request.stage == AsyncRelayStage.MEDIA && (command.header != null ||
                command.request.expectedSourceMessageIds != listOf(command.key.groupId.substring(2).toLongOrNull()))) {
            callback(SingleRelayDispatchResult.Rejected); return
        }
        launch {
            val result = try {
                if (authorized(command.sourceChatId, command.scope) != initial) {
                    SingleRelayDispatchResult.Rejected
                } else if (!beforeQueue(command)) SingleRelayDispatchResult.Rejected
                else {
                    val response = calls.queue(command)
                    if (response == null || !afterQueued(response) || authorized(command.sourceChatId, command.scope) != initial) {
                        queueUncertain(command)
                        SingleRelayDispatchResult.Rejected
                    } else SingleRelayDispatchResult.Queued(response)
                }
            } catch (_: Exception) { queueUncertain(command); SingleRelayDispatchResult.Rejected }
            callback(result)
        }
    }
}
