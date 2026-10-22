package mx.charly.canal

import android.content.Context
import dev.g000sha256.tdl.dto.Message

/**
 * Connects live updates from the ONE existing CanalDeck session to preparation and counters.
 * It has no transport and never calls deliverSynthetic or any Telegram output operation.
 */
class RelayCoordinator(context: Context, private val session: TelegramSession) : AutoCloseable {
    private val application = context.applicationContext
    private val database = RelayDatabase(application)
    private val engine = PickRelayEngine(database)
    private var account = 0L
    private var connected = false
    private var connectionStartedAt = 0L
    private val armed = mutableSetOf<Long>()
    private var closed = false
    private var stopped = false

    fun onSessionChanged() {
        if (closed || stopped) return
        try {
            val valid = TelegramSession.current === session && session.ready && !session.closing && session.accountId > 0L
            if (!valid || session.network != "En línea") {
                connected = false
                armed.clear()
                return
            }
            if (account != session.accountId) {
                account = session.accountId
                database.recoverPending(account)
                connected = false
                armed.clear()
            }
            if (!connected) {
                connectionStartedAt = now()
                connected = true
                armed.clear()
            }
            val preferences = RelayPreferences(application, account)
            preferences.sources().keys.forEach { chat ->
                if (preferences.sourcePolicy(chat) !in RelaySettingsSelection.allowedPolicies) return@forEach
                val loaded = session.chats[chat] ?: return@forEach
                val baseline = if (chat !in armed) maxOf(preferences.sourceBaseline(chat), loaded.lastMessage?.id ?: 0L)
                    else preferences.sourceBaseline(chat)
                val notBefore = maxOf(preferences.enabledAt(chat), connectionStartedAt)
                database.checkpoint(account, chat, baseline, notBefore)
                armed.add(chat)
            }
        } catch (_: Exception) { failClosed() }
    }

    private fun config(): RelayConfig {
        val preferences = RelayPreferences(application, account)
        return RelayConfig(
            accountId = account,
            destinationChatId = preferences.destinationChatId().takeIf { it != 0L },
            userEnabled = BuildConfig.PICK_RELAY_REAL_ENABLED && preferences.enabled,
            sources = preferences.sources().keys.mapNotNull { chat ->
                val policy = preferences.sourcePolicy(chat)?.takeIf { it in RelaySettingsSelection.allowedPolicies }
                    ?: return@mapNotNull null
                val loaded = session.chats[chat] ?: return@mapNotNull null
                RelaySourceBinding(account, chat, loaded.title, policy, enabled = true)
            }
        )
    }

    fun onLiveMessage(message: Message) {
        if (closed || stopped) return
        onSessionChanged()
        if (!connected || account != session.accountId || message.chatId == 0L) return
        try {
            val current = config()
            if (current.sources.none { it.chatId == message.chatId } || message.chatId !in armed) {
                // Coverage is per observed chat, including unconfigured Mazdita, without granting it a route.
                val key = RelayCounterKey(account, message.chatId, now() / 3600L * 3600L)
                database.increment(key, RelayMetric.SEEN)
                database.increment(key, RelayMetric.DISCARDED)
                return
            }
            // A single incoming album fragment is NEVER proof of the complete album.
            session.relaySnapshot(listOf(message), message.mediaAlbumId == 0L, current) { batch, permissions ->
                if (closed || stopped || TelegramSession.current !== session || session.closing ||
                    !session.ready || session.accountId != account) return@relaySnapshot
                try {
                    if (config() != current) return@relaySnapshot
                    if (batch == null || permissions == null) {
                        database.increment(RelayCounterKey(account, message.chatId, now() / 3600L * 3600L), RelayMetric.SEEN)
                        return@relaySnapshot
                    }
                    engine.prepare(batch, current, permissions, now())
                } catch (_: Exception) { failClosed() }
            }
        } catch (_: Exception) { failClosed() }
    }

    /** Called only after Android accepted a v0.3 pick notification, not merely a candidate. */
    fun onLocalNotified(message: Message) {
        if (closed || stopped || account <= 0L || session.accountId != account) return
        try {
            database.increment(RelayCounterKey(account, message.chatId, now() / 3600L * 3600L), RelayMetric.LOCAL_NOTIFIED)
        } catch (_: Exception) { failClosed() }
    }

    private fun failClosed() {
        stopped = true
        if (account > 0L) try { RelayPreferences(application, account).stop() } catch (_: Exception) { /* preserve data */ }
    }
    private fun now() = System.currentTimeMillis() / 1000L
    override fun close() { closed = true; database.close() }
}
