package mx.charly.canal

import java.text.Normalizer
import java.util.Locale

/** Independent UI models contain no credentials or Android objects. */
data class ChatRow(val id: Long, val title: String, val preview: String, val date: Long,
    val unread: Int, val kind: String, val favorite: Boolean = false, val folder: String = "")
enum class NoticeMode { OFF, ALL, KEYWORDS }
object DeckRules {
    fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    fun validFolder(name: String) = name.trim().length in 1..40 && name.none { it.isISOControl() }
    fun validMessage(text: String) = text.isNotBlank() && text.length <= 4096
    fun clock(value: String): Int? {
        val m = Regex("^(\\d{2}):(\\d{2})$").matchEntire(value) ?: return null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }
    fun quiet(now: Int, start: Int, end: Int): Boolean {
        if (now !in 0..1439 || start !in 0..1439 || end !in 0..1439 || start == end) return false
        return if (start < end) now >= start && now < end else now >= start || now < end
    }
    fun keyword(text: String, words: String): Boolean {
        val t = normalized(text)
        return words.split(',', '\n').map { normalized(it.trim()) }.filter { it.isNotBlank() }.take(60)
            .any { Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])").containsMatchIn(t) }
    }
    fun shouldNotify(mode: NoticeMode, text: String, words: String, outgoing: Boolean,
                     chatVisible: Boolean, quiet: Boolean): Boolean = !outgoing && !chatVisible && !quiet &&
        when (mode) { NoticeMode.OFF -> false; NoticeMode.ALL -> true; NoticeMode.KEYWORDS -> keyword(text, words) }
    fun filter(rows: List<ChatRow>, query: String = "", kind: String = "all", folder: String = "*", sort: Int = 0): List<ChatRow> {
        val q = normalized(query.trim())
        val filtered = rows.filter {
            (folder == "*" || it.folder == folder) &&
                (when (kind) { "all" -> true; "unread" -> it.unread > 0; "favorite" -> it.favorite; else -> it.kind == kind }) &&
                (q.isBlank() || normalized(it.title + " " + it.preview).contains(q))
        }
        return when (sort) {
            1 -> filtered.sortedWith(compareBy<ChatRow> { normalized(it.title) }.thenBy { it.id })
            2 -> filtered.sortedWith(compareByDescending<ChatRow> { it.unread }.thenByDescending { it.date }.thenBy { it.id })
            else -> filtered.sortedWith(compareByDescending<ChatRow> { it.favorite }.thenByDescending { it.date }.thenBy { it.id })
        }
    }
}

/** Deduplication includes the full chat ID; never collapse equal text from different messages. */
class NoticeBook(private val capacity: Int = 512) {
    private val seen = linkedSetOf<Pair<Long, Long>>()
    init { require(capacity > 0) }
    fun accept(chat: Long, message: Long): Boolean {
        if (!seen.add(chat to message)) return false
        while (seen.size > capacity) seen.remove(seen.first())
        return true
    }
    fun size() = seen.size
}
