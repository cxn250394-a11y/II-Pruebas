package mx.charly.canal

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/** Private, account-bound routing choices. No message body, caption, image or credential. */
class RelayPreferences(context: Context, val accountId: Long) {
    companion object {
        private class AccountState {
            val lock = Any()
            @Volatile var healthy = true
        }
        private val states = ConcurrentHashMap<Long, AccountState>()
        fun fileName(accountId: Long) = "canaldeck-relay-$accountId"
    }

    init { require(accountId > 0L) { "A connected account is required" } }
    private val p = context.applicationContext.getSharedPreferences(fileName(accountId), Context.MODE_PRIVATE)
    private val state = states.getOrPut(accountId) { AccountState() }
    private val lock get() = state.lock
    private var healthy: Boolean
        get() = state.healthy
        set(value) { state.healthy = value }

    private fun commit(edit: SharedPreferences.Editor): Boolean {
        if (!healthy) return false
        val revision = p.getLong("revision", 0L)
        if (revision < 0L || revision >= Long.MAX_VALUE - 1L) {
            healthy = false
            return false
        }
        // Persist the generation with the setting. A change away and back must
        // invalidate work queued under the earlier consent, even within one second.
        val saved = edit.putLong("revision", revision + 1L).commit()
        if (!saved) healthy = false
        return saved
    }

    fun revision(): Long = synchronized(lock) {
        val revision = p.getLong("revision", 0L)
        if (healthy && revision >= 0L) revision else Long.MAX_VALUE
    }

    fun destinationChatId(): Long = synchronized(lock) { if (healthy) p.getLong("destination", 0L) else 0L }

    /** Called only with a chat chosen from this account's loaded TDLib chat list. */
    fun selectDestination(chatId: Long): Boolean = synchronized(lock) {
        if (!healthy || chatId == 0L || sourceRole(chatId) != PickWatchMode.OFF) return@synchronized false
        // Changing a route always turns the user's output switch OFF.
        val edit = p.edit().putLong("destination", chatId).putBoolean("enabled", false)
        clearSinglePhotoConsents(edit)
        commit(edit)
    }

    fun sources(): Map<Long, PickWatchMode> = synchronized(lock) {
        if (!healthy) return@synchronized emptyMap()
        p.getStringSet("sources", emptySet()).orEmpty().mapNotNull { raw ->
            val id = raw.toLongOrNull() ?: return@mapNotNull null
            val role = sourceRole(id)
            if (id == 0L || role == PickWatchMode.OFF) null else id to role
        }.toMap()
    }

    fun sourceRole(chatId: Long): PickWatchMode = synchronized(lock) {
        if (!healthy || chatId == 0L) return@synchronized PickWatchMode.OFF
        val policy = storedPolicy(chatId) ?: return@synchronized PickWatchMode.OFF
        val role = PickWatchMode.entries.getOrElse(p.getInt("source-role-$chatId", 0)) { PickWatchMode.OFF }
        if (role != PickWatchMode.OFF && role != policyRole(policy)) PickWatchMode.OFF else role
    }

    private fun allowedPolicy(policy: RelaySourcePolicy) = policy == RelaySourcePolicy.FANO_DIRECT || policy.reseller
    private fun policyRole(policy: RelaySourcePolicy) = if (policy.reseller) PickWatchMode.RESELLER else PickWatchMode.DIRECT
    private fun storedPolicy(chatId: Long): RelaySourcePolicy? =
        RelaySourcePolicy.entries.firstOrNull { it.name == p.getString("source-policy-$chatId", null) && allowedPolicy(it) }

    fun sourcePolicy(chatId: Long): RelaySourcePolicy? = synchronized(lock) {
        if (sourceRole(chatId) == PickWatchMode.OFF) null else storedPolicy(chatId)
    }

    /** The owner chooses a specific registry family, never a generic title-matched reseller. */
    fun selectSourcePolicy(
        chatId: Long,
        policy: RelaySourcePolicy,
        latestMessageId: Long,
        nowUnix: Long = System.currentTimeMillis() / 1000L
    ): Boolean = synchronized(lock) {
        if (!allowedPolicy(policy)) return@synchronized false
        storeSource(chatId, policyRole(policy), policy, latestMessageId, nowUnix)
    }

    /** A current high-water mark is mandatory; previous checkpoints are never rewound. */
    fun selectSource(
        chatId: Long,
        role: PickWatchMode,
        latestMessageId: Long,
        nowUnix: Long = System.currentTimeMillis() / 1000L
    ): Boolean = synchronized(lock) {
        val policy = storedPolicy(chatId)
        if (role != PickWatchMode.OFF && (policy == null || role != policyRole(policy))) return@synchronized false
        storeSource(chatId, role, policy, latestMessageId, nowUnix)
    }

    private fun storeSource(chatId: Long, role: PickWatchMode, policy: RelaySourcePolicy?, latestMessageId: Long, nowUnix: Long): Boolean {
        if (!healthy || chatId == 0L || latestMessageId < 0L || nowUnix <= 0L ||
            (role != PickWatchMode.OFF && chatId == destinationChatId())) return false
        val ids = p.getStringSet("sources", emptySet()).orEmpty().toMutableSet()
        val edit = p.edit().putInt("source-role-$chatId", role.ordinal).putBoolean("enabled", false)
            .putBoolean("single-photo-only-$chatId", false)
        if (role == PickWatchMode.OFF) {
            ids.remove(chatId.toString())
            // Deliberately keep the source's baseline and date when disabling it.
        } else {
            require(policy != null && allowedPolicy(policy))
            ids.add(chatId.toString())
            val previousId = p.getLong("source-baseline-$chatId", 0L)
            val previousDate = p.getLong("source-enabled-at-$chatId", 0L)
            edit.putLong("source-baseline-$chatId", maxOf(previousId, latestMessageId))
                .putLong("source-enabled-at-$chatId", maxOf(previousDate, nowUnix))
                .putString("source-policy-$chatId", policy.name)
        }
        return commit(edit.putStringSet("sources", ids))
    }

    fun sourceBaseline(chatId: Long): Long = synchronized(lock) {
        if (!healthy) Long.MAX_VALUE else p.getLong("source-baseline-$chatId", Long.MAX_VALUE)
    }
    fun enabledAt(chatId: Long): Long = synchronized(lock) {
        if (!healthy) Long.MAX_VALUE else p.getLong("source-enabled-at-$chatId", Long.MAX_VALUE)
    }

    /** User intent only. The compiled safety gate is independently false in this release. */
    var enabled: Boolean
        get() = synchronized(lock) { healthy && p.getBoolean("enabled", false) && destinationChatId() != 0L && sources().isNotEmpty() }
        set(value) { changeEnabled(value) }

    fun changeEnabled(value: Boolean): Boolean = synchronized(lock) {
        if (!healthy || (value && (destinationChatId() == 0L || sources().isEmpty()))) return@synchronized false
        val edit = p.edit().putBoolean("enabled", value)
        if (!value) clearSinglePhotoConsents(edit)
        commit(edit)
    }

    /** An image without text has UNKNOWN semantics; this choice never verifies a tipster. */
    fun singlePhotoOnlyConsent(chatId: Long): Boolean = synchronized(lock) {
        healthy && sourcePolicy(chatId) == RelaySourcePolicy.FANO_DIRECT &&
            p.getBoolean("single-photo-only-$chatId", false)
    }

    /** Future owner control only. No UI or remote setting enables this in the offline build. */
    fun setSinglePhotoOnlyConsent(chatId: Long, allow: Boolean): Boolean = synchronized(lock) {
        if (!healthy || sourcePolicy(chatId) != RelaySourcePolicy.FANO_DIRECT ||
            destinationChatId() == 0L) return@synchronized false
        commit(p.edit().putBoolean("single-photo-only-$chatId", allow).putBoolean("enabled", false))
    }

    private fun clearSinglePhotoConsents(edit: SharedPreferences.Editor) {
        p.getStringSet("sources", emptySet()).orEmpty().forEach { raw ->
            raw.toLongOrNull()?.let { edit.putBoolean("single-photo-only-$it", false) }
        }
    }

    /** Rollback closes output and photo-only consent; identities and checkpoints survive. */
    fun stop(): Boolean = synchronized(lock) {
        val edit = p.edit().putBoolean("enabled", false)
        clearSinglePhotoConsents(edit)
        commit(edit)
    }
}
