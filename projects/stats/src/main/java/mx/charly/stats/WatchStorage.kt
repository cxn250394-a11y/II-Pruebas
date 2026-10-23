package mx.charly.stats

import android.content.Context
import mx.charly.logic.Match
import mx.charly.ui.LocalJson
import org.json.JSONArray
import org.json.JSONObject

/** Entirely local preferences. Match IDs are scoped to league and season. */
class WatchPrefs(context: Context) {
    private val store=context.applicationContext.getSharedPreferences("stats_watch", Context.MODE_PRIVATE)
    private fun stored(key: String): Set<String> = store.getStringSet(key,emptySet())?.toSet() ?: emptySet()
    fun matches(scope: String): Set<String> = stored("m:"+scope)
    fun teams(scope: String): Set<String> = stored("t:"+scope)
    fun isTracked(scope: String, match: Match): Boolean =
        match.id in matches(scope) || StatsWatch.teamKey(match.home) in teams(scope) ||
            StatsWatch.teamKey(match.away) in teams(scope)
    fun toggleMatch(scope:String, id:String):Boolean {
        require(id.isNotBlank() && id.length <= 80)
        val current=matches(scope).toMutableSet()
        val added=if(current.add(id))true else {current.remove(id);false}
        store.edit().putStringSet("m:"+scope,current).apply()
        return added
    }
    fun toggleTeam(scope:String, name:String):Boolean {
        val key=StatsWatch.teamKey(name)
        val current=teams(scope).toMutableSet()
        val added=if(current.add(key))true else {current.remove(key);false}
        store.edit().putStringSet("t:"+scope,current).apply()
        return added
    }
}

data class WatchRecord(val scope:String,val observedAt:Long,val source:String,val change:ScoreObservation)

/** Atomic file append; never overwrite a corrupt history silently. */
class WatchHistory(context:Context) {
    private val file=LocalJson(context.applicationContext,"stats-watch-changes.json")
    companion object {
        private const val MAX_ROWS=300
        private const val DAYS_30=30L*24*60*60*1000
    }
    private fun parse(raw:JSONArray):List<WatchRecord> = (0 until raw.length()).mapNotNull { i ->
        val o=raw.optJSONObject(i) ?: return@mapNotNull null
        try {
            val kind=UpdateKind.valueOf(o.getString("kind"))
            fun nullable(key:String):Int?=if(!o.has(key)||o.isNull(key))null else o.getInt(key).takeIf { it>=0 }
            WatchRecord(o.getString("scope"),o.getLong("at"),o.getString("source"),
                ScoreObservation(o.getString("id"),o.getString("home"),o.getString("away"),
                    nullable("oldH"),nullable("oldA"),nullable("newH"),nullable("newA"),kind))
        }catch(_:Exception){null}
    }
    @Synchronized fun all(scope:String):List<WatchRecord> =
        parse(file.read()).filter{it.scope==scope}.sortedByDescending{it.observedAt}
    @Synchronized fun append(scope:String,source:String,seenAt:Long,changes:List<ScoreObservation>) {
        if(changes.isEmpty())return
        require(source.isNotBlank() && source.length<=80 && seenAt>0 && changes.size<=1000)
        val current=parse(file.read()).filter { it.observedAt <= seenAt && seenAt-it.observedAt<=DAYS_30 }
        val combined=(current+changes.map{WatchRecord(scope,seenAt,source,it)}).takeLast(MAX_ROWS)
        val result=JSONArray()
        combined.forEach { r ->
            val c=r.change
            result.put(JSONObject()
                .put("scope",r.scope).put("at",r.observedAt).put("source",r.source)
                .put("id",c.id).put("home",c.home).put("away",c.away).put("kind",c.kind.name)
                .put("oldH",c.beforeHome ?: JSONObject.NULL).put("oldA",c.beforeAway ?: JSONObject.NULL)
                .put("newH",c.afterHome ?: JSONObject.NULL).put("newA",c.afterAway ?: JSONObject.NULL))
        }
        file.write(result)
    }
}

/** Export reproducible records only, never invent missing scores, cards, corners or shots. */
object MatchExport {
    fun csv(matches:List<Match>):String {
        require(matches.size<=10_000)
        fun quote(s:String):String = "\"" + s.replace("\"","\"\"") + "\""
        val head="id,home,away,home_goals,away_goals,finished,date_utc"
        val body=matches.joinToString("\r\n"){ m ->
            val whenText=if(m.whenMs>0)java.time.Instant.ofEpochMilli(m.whenMs).toString() else ""
            listOf(m.id,m.home,m.away,m.homeGoals?.toString().orEmpty(),m.awayGoals?.toString().orEmpty(),
                m.finished.toString(),whenText).joinToString(","){quote(it)}
        }
        return if(matches.isEmpty())"$head\r\n" else "$head\r\n$body\r\n"
    }
}
