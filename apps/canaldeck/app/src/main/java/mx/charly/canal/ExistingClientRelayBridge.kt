package mx.charly.canal

import android.content.Context
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.*
import java.io.Closeable
import java.util.UUID

/**
 * Prepared integration on TelegramSession's single client and update stream. The installed
 * candidate cannot log in; native admission and provider calls are independently compiled OFF.
 */
internal class ExistingClientRelayBridge(
    context: Context,
    client: TdlClient,
    adapter: TdlRelayAdapter,
    private val currentAccount: () -> Long,
    private val usableSession: () -> Boolean,
    private val loadedChats: () -> Map<Long, Chat>,
    launch: (suspend () -> Unit) -> Unit,
    private val clockMillis: () -> Long = System::currentTimeMillis
) : Closeable {
    private val application = context.applicationContext
    // Separate metadata stores avoid competing with the legacy preparation-only coordinator.
    private val observations = RelayDatabase(application, "canaldeck-single-observations.db")
    private val acknowledgements = AsyncRelayDatabase(application, "canaldeck-single-ack.db")
    private val admission = ExistingClientRelayAdmission(observations)
    private val router = ExistingClientRelayAckRouter()
    private var account = 0L
    private var sessionId = UUID.randomUUID().toString()
    private var connected = false
    private var connectionStartedUnix = 0L
    private var revision = -1L
    private var closed = false
    private var routerFailureHandled = false
    private val calls = TdlSingleRelayCalls(client, adapter) { scope ->
        currentContext(0L)?.let { it.scope == scope && it.connected && it.config.userEnabled } == true
    }
    private val port = ExistingClientRelayPort(calls, ::currentContext, launch,
        beforeQueue = router::begin,
        afterQueued = { response ->
            val routed = router.bind(response)
            if (routed.uncertain) stopRouter()
            else routed.updates.forEach { controller.onUpdate(it) }
            !routed.uncertain
        },
        queueUncertain = { command -> if (router.uncertain(command.request.requestId).uncertain) stopRouter() })
    private val controller: SingleRelayController by lazy { SingleRelayController(acknowledgements, port, admission, clockMillis) }

    private fun prefs() = RelayPreferences(application, account)

    private fun config(preferences: RelayPreferences): RelayConfig = RelayConfig(account,
        preferences.destinationChatId().takeIf { it != 0L }, preferences.enabled,
        preferences.sources().keys.mapNotNull { source ->
            val policy = preferences.sourcePolicy(source)?.takeIf { it in RelaySettingsSelection.allowedPolicies }
                ?: return@mapNotNull null
            val chat = loadedChats()[source] ?: return@mapNotNull null
            RelaySourceBinding(account, source, chat.title, policy, true)
        })

    private fun currentContext(source: Long): ExistingClientRelayContext? {
        if (closed || router.stopped || !connected || !usableSession() || currentAccount() != account || account <= 0L) return null
        val preferences = prefs()
        val config = config(preferences)
        val destination = config.destinationChatId ?: return null
        return ExistingClientRelayContext(AsyncRelayScope(account, sessionId, destination), config,
            preferences.revision(), true, source != 0L && preferences.singlePhotoOnlyConsent(source))
    }

    /** Called after actual session state/loaded-chat changes, never from an incoming ID. */
    fun onSessionChanged() {
        if (closed || router.stopped) return
        val nextAccount = currentAccount()
        // ACK DTOs have no sender account field. Never reuse one client/router for A -> B,
        // even if the destination or temporary ID happens to match, or A later returns.
        if (account > 0L && nextAccount > 0L && nextAccount != account) {
            router.sealAccount(nextAccount)
            stopRouter()
            return
        }
        if (!usableSession() || nextAccount <= 0L) { disconnect("CONNECTION_OR_AUTH_CHANGED"); return }
        if (!router.sealAccount(nextAccount)) { stopRouter(); return }
        val nextRevision = RelayPreferences(application, nextAccount).revision()
        if (!connected || account != nextAccount || revision != nextRevision) {
            disconnect("CONNECTION_ACCOUNT_OR_CONFIG_CHANGED")
            if (closed || router.stopped) return
            account = nextAccount; sessionId = UUID.randomUUID().toString()
            connectionStartedUnix = clockMillis() / 1000L
            if (connectionStartedUnix <= 0L || nextRevision < 0L || nextRevision == Long.MAX_VALUE) return
            acknowledgements.recoverPending(account)
            connected = true; revision = nextRevision
        }
        val preferences = prefs()
        preferences.sources().keys.forEach { source ->
            if (preferences.sourcePolicy(source) !in RelaySettingsSelection.allowedPolicies) return@forEach
            val loaded = loadedChats()[source] ?: return@forEach
            admission.armLoaded(account, sessionId, source,
                maxOf(preferences.sourceBaseline(source), loaded.lastMessage?.id ?: 0L),
                maxOf(preferences.enabledAt(source), connectionStartedUnix))
        }
    }

    /** Mutation invalidation precedes asynchronous SDK rereads in the existing collector. */
    fun beforeUpdate(update: Update) {
        if (closed) return
        when (update) {
            is UpdateConnectionState -> if (update.state !is ConnectionStateReady) disconnect("CONNECTION_CHANGED")
            is UpdateAuthorizationState -> if (update.authorizationState !is AuthorizationStateReady) disconnect("AUTH_CHANGED")
            is UpdateMessageContent -> invalidateSource(update.chatId, update.messageId)
            is UpdateMessageEdited -> invalidateSource(update.chatId, update.messageId)
            is UpdateDeleteMessages -> if (update.isPermanent) update.messageIds.forEach { invalidateSource(update.chatId, it) }
            else -> Unit
        }
    }

    /** Consumes exactly the update already emitted by TelegramSession; no second collection. */
    fun afterUpdate(update: Update) {
        if (closed) return
        if (update !is UpdateNewMessage) onSessionChanged()
        if (account > 0L) {
            val routed = router.observe(account, update)
            if (routed.uncertain) stopRouter() else routed.updates.forEach { controller.onUpdate(it) }
        }
        if (update is UpdateNewMessage) onLiveMessage(update.message)
    }

    private fun onLiveMessage(message: Message) {
        // Before admission, snapshot, config reads or SDK calls. Fake is a separate entry point.
        if (!BuildConfig.PICK_RELAY_REAL_ENABLED || closed || !connected || router.stopped ||
            message.mediaAlbumId != 0L || message.isOutgoing || message.sendingState != null ||
            message.schedulingState != null || message.editDate != 0) return
        if (prefs().revision() != revision) { disconnect("CONFIG_CHANGED_BEFORE_INGRESS"); return }
        controller.start(SingleRelayObserved(account, sessionId, message.chatId, message.id,
            message.date.toLong(), message.mediaAlbumId, incoming = true, edited = false))
    }

    private fun invalidateSource(chatId: Long, messageId: Long) {
        if (account > 0L && messageId > 0L) controller.stop(RelayKey(account, chatId, "m:$messageId"), "SOURCE_MUTATED")
    }

    private fun disconnect(reason: String) {
        if (account > 0L && connected) controller.invalidate(account, sessionId, reason)
        connected = false; admission.reset()
        if (router.disconnected().uncertain) stopRouter()
    }

    private fun stopRouter() {
        if (routerFailureHandled) return
        routerFailureHandled = true
        if (account > 0L) {
            controller.invalidate(account, reason = "ACK_IDENTITY_UNCERTAIN")
            // Preserve all metadata; the owner must explicitly reauthorize another candidate.
            prefs().stop()
        }
        connected = false; admission.reset()
    }

    /** Local timer only; expiration does not query TDLib or create an output. */
    fun expire() { if (!closed) controller.expire() }

    override fun close() {
        if (closed) return
        disconnect("SESSION_CLOSED"); controller.close(); closed = true
        observations.close(); acknowledgements.close()
    }
}
