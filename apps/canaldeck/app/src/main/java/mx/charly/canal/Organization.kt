package mx.charly.canal

import android.content.Context
import java.util.UUID

/** Account-scoped preferences; titles never serve as identities. No chat content is stored here. */
class Organization(context: Context, accountId: Long) {
    private val p = context.applicationContext.getSharedPreferences("canaldeck-org-$accountId", Context.MODE_PRIVATE)
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
    fun mode(chat: Long, mode: NoticeMode) { p.edit().putInt("notice-$chat", mode.ordinal).apply() }
    fun words(chat: Long) = p.getString("words-$chat", "").orEmpty()
    fun words(chat: Long, value: String) { p.edit().putString("words-$chat", value.take(2400)).apply() }

    // Device-local pick notices are distinct from Telegram sending / Pick$ channel forwarding.
    // A chat must be enabled explicitly from its actual chat ID in the UI.
    fun pickMode(chat: Long): PickSourceMode = PickSourceMode.entries.getOrElse(
        p.getInt("pick-mode-$chat", 0)
    ) { PickSourceMode.OFF }

    fun pickMode(chat: Long, mode: PickSourceMode, currentLastId: Long): Boolean {
        if (chat == 0L) return false
        val baseline = currentLastId.coerceAtLeast(0L)
        // Baseline and mode are committed together: do not replay existing history.
        return p.edit().putLong("pick-id-$chat", baseline)
            .putLong("pick-enabled-at-$chat", System.currentTimeMillis() / 1000L)
            .putLong("pick-album-$chat", 0L)
            .putInt("pick-mode-$chat", mode.ordinal).commit()
    }

    fun acceptPick(chat: Long, messageId: Long, sourceDate: Long, albumId: Long): Boolean {
        if (pickMode(chat) == PickSourceMode.OFF || messageId <= 0L ||
            sourceDate < p.getLong("pick-enabled-at-$chat", Long.MAX_VALUE) ||
            messageId <= p.getLong("pick-id-$chat", Long.MAX_VALUE)) return false
        if (albumId != 0L && albumId == p.getLong("pick-album-$chat", 0L)) return false
        // Write dedup checkpoint before displaying; uncertain delivery means no blind replay.
        return p.edit().putLong("pick-id-$chat", messageId)
            .putLong("pick-album-$chat", albumId).commit()
    }
    var previews: Boolean
        get() = p.getBoolean("previews", false)
        set(v) { p.edit().putBoolean("previews", v).apply() }
    var quietEnabled: Boolean
        get() = p.getBoolean("quiet-enabled", false)
        set(v) { p.edit().putBoolean("quiet-enabled", v).apply() }
    fun quietStart() = p.getInt("quiet-start", 23 * 60)
    fun quietEnd() = p.getInt("quiet-end", 8 * 60)
    fun hours(start: String, end: String): Boolean {
        val a = DeckRules.clock(start) ?: return false; val b = DeckRules.clock(end) ?: return false
        if (a == b) return false
        p.edit().putInt("quiet-start", a).putInt("quiet-end", b).apply(); return true
    }
}
