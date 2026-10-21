package com.charly.alertcenter

import java.security.MessageDigest

/** This is notification evidence, never a verified match event or Telegram account identity. */
data class LabAlert(
    val id: Long = 0,
    val source: String,
    val profile: Int,
    val title: String,
    val body: String,
    val postedAt: Long,
    val receivedAt: Long,
    val messageAt: Long = 0,
    val read: Boolean = false,
    val favorite: Boolean = false
) {
    val groupKey: String get() = LabEngine.digest(source, profile.toString(), title.trim())
}

data class ChannelGroup(val key: String, val alerts: List<LabAlert>) {
    val latest: LabAlert get() = alerts.first()
    val unread: Int get() = alerts.count { !it.read }
}

enum class SportMention(val label: String) {
    GOAL("Mención de gol"), DISALLOWED("Gol anulado / corrección"),
    CORNER("Mención de córner"), RED("Mención de tarjeta roja"),
    YELLOW("Mención de tarjeta amarilla"), PENALTY("Mención de penal"),
    VAR("Revisión VAR"), MULTIPLE("Varias menciones; revisar texto")
}

object LabEngine {
    /** Length-prefixed inputs avoid separator collisions. No raw secrets in persisted identifiers. */
    fun digest(vararg parts: String): String {
        val sha = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            sha.update("${bytes.size}:".toByteArray(Charsets.UTF_8))
            sha.update(bytes)
        }
        return sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** Keep repeated real messages with a different timestamp or occurrence; never merge clients. */
    fun eventKey(source: String, profile: Int, key: String, title: String, body: String,
                 timestamp: Long, occurrence: Int = 0): String =
        digest(source, profile.toString(), key, title, body, timestamp.toString(), occurrence.toString())

    fun sorted(alerts: List<LabAlert>): List<LabAlert> = alerts.sortedWith(
        compareByDescending<LabAlert> { it.receivedAt }.thenByDescending { it.id }
    )

    fun groups(alerts: List<LabAlert>): List<ChannelGroup> = sorted(alerts)
        .groupBy { it.groupKey }.map { ChannelGroup(it.key, it.value) }

    fun matches(alert: LabAlert, query: String): Boolean =
        AlertRules.matchesQuery(alert.title, alert.body, query) ||
            AlertRules.normalized(alert.source).contains(AlertRules.normalized(query))

    fun validFolder(name: String): Boolean = name.trim().length in 1..36 &&
        name.none { it.isISOControl() }

    private fun word(text: String, pattern: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])(?:$pattern)(?![\\p{L}\\p{N}])").containsMatchIn(text)

    /** Conservative hints from source text only. No counters, scores, or inferred match identity. */
    fun sportMention(title: String, body: String, category: AlertCategory): SportMention? {
        if (category != AlertCategory.SPORTS) return null
        val t = AlertRules.normalized("$title $body")
        if (t.contains('?') || word(t, "cuota[s]?|apuesta[s]?|pick[s]?|pronostico[s]?|bono[s]?|bonus|boost|cashback|prediccion|over|under|bet[s]?|odds|gratis|gana dinero")) return null
        if (word(t, "gol anulado|gol cancelado|goal disallowed|disallowed goal|goal ruled out|no goal")) return SportMention.DISALLOWED
        if (word(t, "sin gol|sin goles|no hubo|no hay|no fue|no se concedio|no corner|no penalty|marcara|marcaran|habra|habria")) return null
        if (word(t, "var|videoarbitraje")) return SportMention.VAR
        val mentions = mutableListOf<SportMention>()
        if (word(t, "go+l|goal|golazo")) mentions += SportMention.GOAL
        if (word(t, "corner|saque de esquina")) mentions += SportMention.CORNER
        if (word(t, "tarjeta roja|red card|expulsion|expulsado")) mentions += SportMention.RED
        if (word(t, "tarjeta amarilla|yellow card|amonestacion|amonestado")) mentions += SportMention.YELLOW
        if (word(t, "penal|penalti|penalty")) mentions += SportMention.PENALTY
        return when (mentions.size) { 0 -> null; 1 -> mentions.first(); else -> SportMention.MULTIPLE }
    }

    /** In-memory examples: negative IDs cannot mutate persisted records. */
    fun demo(now: Long): List<LabAlert> = listOf(
        LabAlert(-1, "org.telegram.messenger", 0, "Canal de ejemplo", "Una publicación nueva para organizar.", now - 1_000, now - 1_000),
        LabAlert(-2, "com.sofascore.results", 0, "Equipo Azul vs Equipo Verde", "Gol · minuto 32", now - 20_000, now - 20_000),
        LabAlert(-3, "org.telegram.messenger", 0, "Canal de ejemplo", "La publicación anterior, en el mismo grupo.", now - 40_000, now - 40_000, read = true),
        LabAlert(-4, "eu.livesport.FlashScore_com", 0, "Equipo Sol vs Equipo Luna", "Tarjeta roja · minuto 61", now - 60_000, now - 60_000),
        LabAlert(-5, "app.nicegram", 0, "Canal de ejemplo", "Otro cliente: no se fusiona automáticamente con Telegram.", now - 80_000, now - 80_000),
        LabAlert(-6, "mex.novibet.sports.betting", 0, "Equipo Azul vs Equipo Verde", "Córner · minuto 33", now - 100_000, now - 100_000),
        LabAlert(-7, "com.sofascore.results", 0, "Equipo Azul vs Equipo Verde", "Gol anulado por VAR", now - 120_000, now - 120_000)
    )
}
