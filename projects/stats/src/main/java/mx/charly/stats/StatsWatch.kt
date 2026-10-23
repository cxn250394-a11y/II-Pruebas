package mx.charly.stats

import mx.charly.logic.Match
import java.text.Normalizer
import java.util.Locale

/**
 * OpenLigaDB (or imported) changes are observations, not confirmations of a goal.
 * Never fabricate a missing score or compare unmatched match identities.
 */
enum class UpdateKind(val label: String) {
    SCORE("Marcador actualizado"),
    FIRST_SCORE("Primer marcador disponible"),
    SCORE_UNKNOWN("Marcador dejó de estar disponible"),
    SCORE_CORRECTION("Marcador corregido o reducido"),
    FINISHED("Finalización informada por fuente"),
    REOPENED("Estado final retirado por fuente")
}
data class ScoreObservation(
    val id: String,
    val home: String,
    val away: String,
    val beforeHome: Int?, val beforeAway: Int?,
    val afterHome: Int?, val afterAway: Int?,
    val kind: UpdateKind
)
object StatsWatch {
    fun scope(league: String, season: Int): String {
        require(league.matches(Regex("[A-Za-z0-9_-]{1,40}")) && season in 2000..2100)
        return league.lowercase(Locale.ROOT) + ":" + season
    }
    fun teamKey(team: String): String {
        require(team.isNotBlank() && team.length <= 120)
        return Normalizer.normalize(team.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
    }
    fun diff(old: List<Match>, fresh: List<Match>, followed: (Match) -> Boolean): List<ScoreObservation> {
        val previous=old.distinctBy { it.id }.associateBy { it.id }
        return fresh.distinctBy { it.id }.mapNotNull { now ->
            if (!followed(now)) return@mapNotNull null
            val before=previous[now.id] ?: return@mapNotNull null
            if (before.home != now.home || before.away != now.away) return@mapNotNull null
            val oldKnown=before.homeGoals != null && before.awayGoals != null
            val newKnown=now.homeGoals != null && now.awayGoals != null
            val kind=when {
                oldKnown && newKnown && (before.homeGoals != now.homeGoals || before.awayGoals != now.awayGoals) -> {
                    if(now.homeGoals!! < before.homeGoals!! || now.awayGoals!! < before.awayGoals!!) UpdateKind.SCORE_CORRECTION
                    else UpdateKind.SCORE
                }
                !oldKnown && newKnown -> UpdateKind.FIRST_SCORE
                oldKnown && !newKnown -> UpdateKind.SCORE_UNKNOWN
                !before.finished && now.finished -> UpdateKind.FINISHED
                before.finished && !now.finished -> UpdateKind.REOPENED
                else -> null
            } ?: return@mapNotNull null
            ScoreObservation(now.id,now.home,now.away,before.homeGoals,before.awayGoals,now.homeGoals,now.awayGoals,kind)
        }
    }
    fun score(home:Int?, away:Int?)=if(home==null || away==null)"N/D" else "$home–$away"
}
