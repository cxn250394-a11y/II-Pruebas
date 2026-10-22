package mx.charly.canal

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
        val result = PickAttribution.parse(text)
        return result.name.takeUnless { result.invalid }
    }

    fun originalDate(unix: Long): String {
        if (unix < 946684800L || unix > 4102444800L) return "No disponible"
        return try {
            formatter.format(Instant.ofEpochSecond(unix).atZone(mexico)) + " CDMX"
        } catch (_: Exception) { "No disponible" }
    }

    /** Grammar only: this does not attest TDLib provenance, identity, access or media permission. */
    fun validTelegramLink(link: String?): String? = TelegramMessageLinks.validated(link)

    fun safeSourceTitle(title: String): String = title.map {
        if (it.isISOControl() || it == '\u2028' || it == '\u2029') ' ' else it
    }.joinToString("").trim().take(100)

    /**
     * Matching a chat requires a real account-scoped selection via Organization.
     * Returns NULL for unknown authors in reseller groups and for non-pick media.
     */
    fun candidate(
        mode: PickWatchMode, sourceTitle: String, content: String, originalUnix: Long,
        hasPhoto: Boolean, outgoing: Boolean = false, edited: Boolean = false,
        sourcePolicy: RelaySourcePolicy? = null
    ): PickReference? {
        if (mode == PickWatchMode.OFF || outgoing || edited || originalDate(originalUnix) == "No disponible")
            return null
        if (!hasPhoto && content.isBlank()) return null
        if (PickContentRules.isClearlyRecapOnly(content)) return null
        val label = when (mode) {
            PickWatchMode.OFF -> return null
            PickWatchMode.DIRECT -> {
                // Legacy direct alerts remain an explicit local opt-in, not a relay authorization.
                if (sourcePolicy != null && sourcePolicy != RelaySourcePolicy.FANO_DIRECT) return null
                val title = safeSourceTitle(sourceTitle)
                if (title.isBlank()) return null
                "Canal: $title"
            }
            PickWatchMode.RESELLER -> {
                // Families are owner-bound by account/chat ID; never infer UnicoRey from a title.
                if (sourcePolicy?.reseller != true) return null
                "Tipster: " + (tipster(content) ?: return null)
            }
        }
        return PickReference(label, originalUnix)
    }
}
