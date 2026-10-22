package mx.charly.canal

import java.net.URI
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure, fail-closed policy for PRIVATE, LOCAL alerts in CanalDeck.
 *
 * Does not read protected media, perform network requests, send to Pick$,
 * forward Telegram posts, or change the existing TDLib session.
 */
enum class PickSourceMode { OFF, CANAL, TIPSTER }

data class PickAlertCandidate(
    val heading: String,
    val originalUnixSeconds: Long,
    val reason: String = "EXTERNAL_UNVERIFIED"
)

object PickAlertPolicy {
    /** Charly-approved list; scoped only to reseller channels he assigns manually. */
    val unicoreyTipsters = listOf(
        "Dr MLS", "Diego Palafox", "LaraBasado", "Zorrito VIP",
        "Koala Picks", "Revolver Bets", "Renne NFL", "Rodrigo Picks",
        "Alfredo Valenzuela", "Tennis Insider", "Sharp Investment", "Bet Data"
    )
    private val known = unicoreyTipsters.associateBy { normalized(it) }
    private val attribution = Regex(
        """^\s*(?:tipster|pronosticador|picks?\s+de|by)\s*[:#\-\u2013]\s*(.*?)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val results = Regex(
        """\b(?:ganad[oa]s?|perdid[oa]s?|recap|resultados?|liquidad[oa]s?|cobrad[oa]s?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val cLink = Regex("""^/c/[0-9]+/[0-9]+$""")
    private val publicLink = Regex("""^/[A-Za-z0-9_]{5,32}/[0-9]+$""")

    fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace("\u200b", "")
        .replace("\u200d", "").replace("\ufeff", "").trim()
        .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /** Only a complete attribution line or exact source-name line is trusted. */
    fun tipsterFrom(text: String): String? {
        for (line in text.lineSequence().take(24)) {
            val raw = line.trim()
            val declared = attribution.matchEntire(raw)
            val name = if (declared != null) declared.groupValues[1] else raw
            val approved = known[normalized(name)]
            if (approved != null) return approved
        }
        return null
    }

    /**
     * The timestamp is the Telegram DATE of the actual message in the watched
     * source, NOT the betting event, screenshot time or upstream author's post.
     */
    fun candidate(
        mode: PickSourceMode,
        sourceName: String,
        messageText: String,
        hasPhoto: Boolean,
        sourceDate: Long,
        outgoing: Boolean = false,
        changed: Boolean = false
    ): PickAlertCandidate? {
        if (mode == PickSourceMode.OFF || outgoing || changed ||
            sourceDate !in 946684800L..4102444800L) return null
        if (!hasPhoto && messageText.isBlank()) return null
        if (results.containsMatchIn(normalized(messageText))) return null
        return when (mode) {
            PickSourceMode.CANAL -> {
                val name = sourceName.trim()
                if (name.isEmpty() || name.length > 80 || name.any { it.isISOControl() }) null
                else PickAlertCandidate("Canal: $name", sourceDate)
            }
            PickSourceMode.TIPSTER -> tipsterFrom(messageText)
                ?.let { PickAlertCandidate("Tipster: $it", sourceDate) }
            PickSourceMode.OFF -> null
        }
    }

    /** Only a link provided by TDLib and representing an actual message. */
    fun canonicalMessageLink(value: String?): String? {
        if (value.isNullOrBlank() || value.length > 1024 || value.any { it.isISOControl() }) return null
        return try {
            val uri = URI(value)
            if (uri.scheme != "https" || uri.host !in setOf("t.me", "telegram.me") ||
                uri.userInfo != null || uri.port != -1 || uri.fragment != null ||
                uri.rawPath == null || !(cLink.matches(uri.rawPath) || publicLink.matches(uri.rawPath)))
                null else value
        } catch (_: Exception) { null }
    }

    fun render(candidate: PickAlertCandidate, link: String?): String {
        val clock = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX")).apply {
            timeZone = TimeZone.getTimeZone("America/Mexico_City")
        }.format(Date(candidate.originalUnixSeconds * 1000L))
        return candidate.heading + "\nHora original: " + clock + " CDMX\nLink: " +
            (canonicalMessageLink(link) ?: "No disponible")
    }
}
