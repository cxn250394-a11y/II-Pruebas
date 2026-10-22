package mx.charly.canal

import java.net.URI
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Owner-selected local alerts and manual share payloads. NO Telegram send/forward. */
enum class PickWatchMode { OFF, DIRECT, RESELLER }

data class PickReference(val firstLine: String, val originalUnix: Long) {
    fun render(sourceLink: String?): String {
        val whenOriginal = PickBridgeRules.originalDate(originalUnix)
        val url = PickBridgeRules.validTelegramLink(sourceLink) ?: "No disponible"
        return "$firstLine\nHora original: $whenOriginal\nLink: $url"
    }
}

object PickBridgeRules {
    /** This is the user's explicitly approved 12-name allowlist; not a list of chat IDs. */
    val allowedTipsters = listOf(
        "Dr MLS", "Diego Palafox", "LaraBasado", "Zorrito VIP", "Koala Picks",
        "Revolver Bets", "Renne NFL", "Rodrigo Picks", "Alfredo Valenzuela",
        "Tennis Insider", "Sharp Investment", "Bet Data"
    )
    private val names = allowedTipsters.associateBy { normalized(it) }
    private val attribution = Regex(
        """^\s*(?:tipster|pronosticador|picks?\s+de|by)\s*[:#\-]\s*(.+?)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es", "MX"))
    private val mexico = ZoneId.of("America/Mexico_City")

    fun normalized(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[\\uFE0F\\u200B\\u200D\\uFEFF]"), "")
        .trim().replace(Regex("\\s+"), " ")

    /**
     * Attribution must occupy its own line. Do not infer author from mentions like
     * "apoyen a Dr MLS", untrusted previews, unrelated images or reseller title.
     */
    fun tipster(text: String): String? {
        text.lineSequence().take(30).forEach { line ->
            val input = line.trim()
            val candidate = attribution.matchEntire(input)?.groupValues?.get(1) ?: input
            names[normalized(candidate)]?.let { return it }
        }
        return null
    }

    fun originalDate(unix: Long): String {
        if (unix < 946684800L || unix > 4102444800L) return "No disponible"
        return try {
            formatter.format(Instant.ofEpochSecond(unix).atZone(mexico)) + " CDMX"
        } catch (_: Exception) { "No disponible" }
    }

    /** Accept only links actually returned by TDLib. Never synthesize a t.me/c URL. */
    fun validTelegramLink(link: String?): String? {
        if (link.isNullOrBlank() || link.any { it.isISOControl() }) return null
        return try {
            val uri = URI(link)
            if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1 ||
                uri.host?.lowercase(Locale.ROOT) !in setOf("t.me", "telegram.me") ||
                uri.rawPath.isNullOrBlank() || uri.rawPath == "/"
            ) null else link
        } catch (_: Exception) { null }
    }

    /**
     * Matching a chat requires a real account-scoped selection via Organization.
     * Returns NULL for unknown authors in reseller groups and for non-pick media.
     */
    fun candidate(
        mode: PickWatchMode, sourceTitle: String, content: String, originalUnix: Long,
        hasPhoto: Boolean, outgoing: Boolean = false, edited: Boolean = false
    ): PickReference? {
        if (mode == PickWatchMode.OFF || outgoing || edited || originalDate(originalUnix) == "No disponible")
            return null
        if (!hasPhoto && content.isBlank()) return null
        val label = when (mode) {
            PickWatchMode.OFF -> return null
            PickWatchMode.DIRECT -> {
                if (sourceTitle.isBlank()) return null
                "Canal: " + sourceTitle.take(100).replace(Regex("[\\r\\n]"), " ").trim()
            }
            PickWatchMode.RESELLER -> "Tipster: " + (tipster(content) ?: return null)
        }
        return PickReference(label, originalUnix)
    }
}
