package mx.charly.canal

import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Selected picks are local notifications only. Never send a Telegram message or download media. */
enum class PickWatchMode { OFF, DIRECT_CHANNEL, RESELLER_TIPSTERS }

data class PickWatchNotice(val heading: String, val originalTime: String) {
    fun render(link: String?): String = listOf(
        heading,
        "Hora original: $originalTime",
        "Link: ${PickWatchRules.checkedLink(link) ?: "No disponible"}"
    ).joinToString("\n")
}

object PickWatchRules {
    /** Owner-confirmed names (18 Oct 2026). No title-based chat identity or inferred tipster. */
    val allowedTipsters = listOf(
        "Dr MLS", "Diego Palafox", "LaraBasado", "Zorrito VIP",
        "Koala Picks", "Revolver Bets", "Renne NFL", "Rodrigo Picks",
        "Alfredo Valenzuela", "Tennis Insider", "Sharp Investment", "Bet Data"
    )
    private val zone = ZoneId.of("America/Mexico_City")
    private val time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "MX"))
    private val explicit = Regex("""^\s*(?:tipster|pronosticador|pick\s+de|by)\s*[:#\-]\s*(.*?)\s*$""", RegexOption.IGNORE_CASE)
    private val results = Regex("""\b(?:ganad[oa]s?|perdid[oa]s?|cobrad[oa]s?|liquidad[oa]s?|recap|resultados?)\b""", RegexOption.IGNORE_CASE)

    fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "").lowercase(Locale.ROOT)
        .replace(Regex("""[\u200b\u200d\ufeff\ufe0f]"""), "").trim()
        .replace(Regex("""\s+"""), " ")

    fun tipster(text: String): String? {
        val names = allowedTipsters.associateBy(::normalized)
        // Match a declared attribution or a complete line. Never a casual mention inside prose.
        for (line in text.lineSequence().take(24)) {
            val t = line.trim()
            val candidate = explicit.matchEntire(t)?.groupValues?.get(1) ?: t
            names[normalized(candidate)]?.let { return it }
        }
        return null
    }

    fun checkedLink(value: String?): String? {
        if (value.isNullOrBlank() || value.length > 2048 || value.any { it.isISOControl() }) return null
        return try {
            val u = URI(value)
            if (u.scheme == "https" && u.host in setOf("t.me", "telegram.me") && u.userInfo == null
                && u.path.isNotBlank() && u.path != "/") value else null
        } catch (_: Exception) { null }
    }

    /** Propose no notice on old/replayed, edited, results-only or ambiguous resale posts. */
    fun propose(
        mode: PickWatchMode, chatTitle: String, text: String,
        hasImage: Boolean, sourceDate: Long, now: Long,
        outgoing: Boolean = false, edited: Boolean = false
    ): PickWatchNotice? {
        if (mode == PickWatchMode.OFF || outgoing || edited || (!hasImage && text.isBlank())) return null
        if (sourceDate < 946684800L || sourceDate > now + 60 || now - sourceDate > 4 * 3600) return null
        if (chatTitle.isBlank() || chatTitle.length > 180 || chatTitle.any { it.isISOControl() }) return null
        val label = when (mode) {
            PickWatchMode.OFF -> return null
            PickWatchMode.DIRECT_CHANNEL -> "Canal: $chatTitle"
            PickWatchMode.RESELLER_TIPSTERS -> {
                if (results.containsMatchIn(normalized(text))) return null
                val tipster = tipster(text) ?: return null
                "Tipster: $tipster"
            }
        }
        val date = Instant.ofEpochSecond(sourceDate).atZone(zone).format(time) + " CDMX"
        return PickWatchNotice(label, date)
    }
}
