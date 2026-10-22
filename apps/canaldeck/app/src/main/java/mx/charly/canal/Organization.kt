package mx.charly.canal

import android.content.Context
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Account-scoped preferences; titles never serve as identities. No chat content is stored here. */
class Organization(context: Context, val accountId: Long) {
    companion object {
        private class NoticeState { val lock = Any(); @Volatile var healthy = true }
        private val noticeStates = ConcurrentHashMap<Long, NoticeState>()
    }
    private val application = context.applicationContext
    private val p = context.applicationContext.getSharedPreferences("canaldeck-org-$accountId", Context.MODE_PRIVATE)
    private val noticeState = noticeStates.getOrPut(accountId) { NoticeState() }
    private fun commitNotice(edit: android.content.SharedPreferences.Editor): Boolean {
        if (!noticeState.healthy) return false
        val revision = p.getLong("notice-revision", 0L)
        if (revision < 0L || revision >= Long.MAX_VALUE - 1L) { noticeState.healthy = false; return false }
        return edit.putLong("notice-revision", revision + 1L).commit().also { if (!it) noticeState.healthy = false }
    }
    fun noticeRevision(): Long = synchronized(noticeState.lock) {
        if (noticeState.healthy) p.getLong("notice-revision", 0L).takeIf { it >= 0L } ?: Long.MAX_VALUE else Long.MAX_VALUE
    }
    fun pickEpoch(chat: Long): Long = synchronized(noticeState.lock) {
        if (noticeState.healthy) p.getLong("pick-epoch-$chat", 0L).takeIf { it >= 0L } ?: Long.MAX_VALUE else Long.MAX_VALUE
    }
    private fun set(key: String) = p.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    fun favorite(id: Long) = id.toString() in set("favorites")
    fun toggleFavorite(id: Long) { val s = set("favorites").toMutableSet(); if (!s.add(id.toString())) s.remove(id.toString()); p.edit().putStringSet("favorites", s).apply() }
    fun folders(): List<Pair<String, String>> = set("folders").map { it to p.getString("name-$it", "Carpeta").orEmpty() }.sortedBy { DeckRules.normalized(it.second) }
    fun createFolder(name: String): String? {
        val n = name.trim()
        if (!DeckRules.validFolder(n) || folders().any { DeckRules.normalized(it.second) == DeckRules.normalized(n) }) return null
        val id = UUID.randomUUID().toString()
        p.edit().putString("name-$id", n).putStringSet("folders", set("folders") + id).apply(); return id
    }
    fun renameFolder(id: String, name: String): Boolean {
        val n = name.trim()
        if (id !in set("folders") || !DeckRules.validFolder(n) || folders().any { it.first != id && DeckRules.normalized(it.second) == DeckRules.normalized(n) }) return false
        p.edit().putString("name-$id", n).apply(); return true
    }
    fun deleteFolder(id: String) {
        val e = p.edit().putStringSet("folders", set("folders") - id).remove("name-$id")
        p.all.filter { it.key.startsWith("chat-folder-") && it.value == id }.keys.forEach { e.remove(it) }; e.apply()
    }
    fun folder(chat: Long) = p.getString("chat-folder-$chat", "").orEmpty().takeIf { it in set("folders") }.orEmpty()
    fun assign(chat: Long, folder: String) { if (folder.isBlank() || folder in set("folders")) p.edit().putString("chat-folder-$chat", folder).apply() }
    fun mode(chat: Long): NoticeMode = NoticeMode.entries.getOrElse(p.getInt("notice-$chat", 0)) { NoticeMode.OFF }
    fun mode(chat: Long, mode: NoticeMode) { synchronized(noticeState.lock) { commitNotice(p.edit().putInt("notice-$chat", mode.ordinal)) } }
    /** Owner-enabled per account and exact chat ID; never enable by a matching title. */
    fun pickMode(chat: Long): PickWatchMode = PickWatchMode.entries
        .getOrElse(p.getInt("pick-mode-$chat", 0)) { PickWatchMode.OFF }
    fun pickMode(chat: Long, mode: PickWatchMode, latestMessageId: Long = 0L): Boolean {
        return synchronized(noticeState.lock) {
            if (accountId <= 0L || chat == 0L || latestMessageId < 0L || pickEpoch(chat) >= Long.MAX_VALUE - 1L) return@synchronized false
            // This is the activation boundary, NOT the last completed asynchronous callback.
            val baseline = maxOf(pickBaseline(chat), latestMessageId)
            commitNotice(p.edit().putInt("pick-mode-$chat", mode.ordinal)
                .putLong("pick-enabled-at-$chat", System.currentTimeMillis() / 1000L)
                .putLong("pick-activation-baseline-$chat", baseline)
                .putLong("pick-epoch-$chat", pickEpoch(chat) + 1L))
        }
    }
    fun pickBaseline(chat: Long): Long = synchronized(noticeState.lock) {
        // A v0.3 last-ID is a one-time migration boundary, never a mutable delivery watermark.
        p.getLong("pick-activation-baseline-$chat", p.getLong("pick-last-id-$chat", 0L))
    }
    private fun eligible(chat: Long, messageId: Long, date: Long, albumId: Long): Boolean =
        noticeState.healthy && accountId > 0L && chat != 0L && pickMode(chat) != PickWatchMode.OFF && messageId > 0L && albumId >= 0L &&
            date >= p.getLong("pick-enabled-at-$chat", Long.MAX_VALUE) && messageId > pickBaseline(chat)
    private fun migrateLegacyPick(chat: Long): Boolean {
        if (p.getBoolean("pick-ledger-migrated-$chat", false)) return true
        val lastId = p.getLong("pick-last-id-$chat", 0L)
        val lastAlbum = p.getLong("pick-last-album-$chat", 0L)
        if (lastAlbum != 0L) {
            if (lastId <= 0L || lastAlbum < 0L) { noticeState.healthy = false; return false }
            LocalPickDeliveryStore(application).use {
                it.claim(accountId, chat, lastId, lastAlbum)
                it.finish(accountId, chat, lastId, lastAlbum, LocalPickDeliveryState.UNKNOWN)
            }
        }
        return p.edit().putBoolean("pick-ledger-migrated-$chat", true).commit().also { if (!it) noticeState.healthy = false }
    }
    fun canAcceptPick(chat: Long, messageId: Long, date: Long, albumId: Long): Boolean = synchronized(noticeState.lock) {
        if (!eligible(chat, messageId, date, albumId)) return@synchronized false
        try { migrateLegacyPick(chat) && LocalPickDeliveryStore(application).use { !it.known(accountId, chat, messageId, albumId) } }
        catch (_: Exception) { noticeState.healthy = false; false }
    }
    /** Only the final publication boundary calls this: blocked notifications never reserve a pick. */
    fun acceptPick(chat: Long, messageId: Long, date: Long, albumId: Long,
                   expectedRevision: Long? = null, expectedEpoch: Long? = null): Boolean = synchronized(noticeState.lock) {
        if (!eligible(chat, messageId, date, albumId) ||
            (expectedRevision != null && expectedRevision != noticeRevision()) ||
            (expectedEpoch != null && expectedEpoch != pickEpoch(chat))) return@synchronized false
        try { migrateLegacyPick(chat) && LocalPickDeliveryStore(application).use { it.claim(accountId, chat, messageId, albumId) } }
        catch (_: Exception) { noticeState.healthy = false; false }
    }
    fun finishPick(chat: Long, messageId: Long, albumId: Long, state: LocalPickDeliveryState) = synchronized(noticeState.lock) {
        try { LocalPickDeliveryStore(application).use { it.finish(accountId, chat, messageId, albumId, state) } }
        catch (_: Exception) { noticeState.healthy = false }
    }
    fun recoverPickReservations() = synchronized(noticeState.lock) {
        if (accountId > 0L) try { LocalPickDeliveryStore(application).use { it.recoverPending(accountId) } }
        catch (_: Exception) { noticeState.healthy = false }
    }
    fun words(chat: Long) = p.getString("words-$chat", "").orEmpty()
    fun words(chat: Long, value: String) { synchronized(noticeState.lock) { commitNotice(p.edit().putString("words-$chat", value.take(2400))) } }
    var previews: Boolean
        get() = p.getBoolean("previews", false)
        set(v) { synchronized(noticeState.lock) { commitNotice(p.edit().putBoolean("previews", v)) } }
    var quietEnabled: Boolean
        get() = p.getBoolean("quiet-enabled", false)
        set(v) { synchronized(noticeState.lock) { commitNotice(p.edit().putBoolean("quiet-enabled", v)) } }
    fun quietStart() = p.getInt("quiet-start", 23 * 60)
    fun quietEnd() = p.getInt("quiet-end", 8 * 60)
    fun hours(start: String, end: String): Boolean {
        val a = DeckRules.clock(start) ?: return false; val b = DeckRules.clock(end) ?: return false
        if (a == b) return false
        return synchronized(noticeState.lock) { commitNotice(p.edit().putInt("quiet-start", a).putInt("quiet-end", b)) }
    }
}
