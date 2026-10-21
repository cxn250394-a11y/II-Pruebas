package mx.charly.stats

import org.json.JSONArray
import org.json.JSONObject
import mx.charly.logic.Match
import mx.charly.ui.Streams
import java.net.URL
import java.time.Instant

/** No credentials or scraping. Only the documented OpenLigaDB public API. */
object Feed {
 const val ORIGIN="https://api.openligadb.de"
 fun get(path:String):JSONArray {
    require(path.matches(Regex("/[A-Za-z0-9_/-]+")))
    val connection=URL(ORIGIN+path).openConnection() as javax.net.ssl.HttpsURLConnection
    connection.connectTimeout=15000;connection.readTimeout=15000
    connection.setRequestProperty("User-Agent","StatsDeck/0.1 personal research")
    connection.instanceFollowRedirects=false
    try{val status=connection.responseCode;require(status==200){"Proveedor respondió HTTP $status"};return connection.inputStream.use{JSONArray(String(Streams.readLimited(it,4_000_000),Charsets.UTF_8))}}
    finally{connection.disconnect()}
 }
 fun parse(raw:JSONArray):List<Match>=(0 until raw.length()).mapNotNull{i->
    val o=raw.optJSONObject(i)?:return@mapNotNull null
    val id=o.optLong("matchID",0);val h=o.optJSONObject("team1")?.optString("teamName").orEmpty();val a=o.optJSONObject("team2")?.optString("teamName").orEmpty()
    if(id<=0||h.isBlank()||a.isBlank())return@mapNotNull null
    val r=o.optJSONArray("matchResults")?:JSONArray()
    val score=(0 until r.length()).mapNotNull{r.optJSONObject(it)}.firstOrNull{it.optInt("resultTypeID")==2}
    fun goal(key:String):Int?=score?.let{if(it.has(key)&&!it.isNull(key))it.optInt(key,-1).takeIf{g->g>=0} else null}
    val date=try{Instant.parse(o.optString("matchDateTimeUTC")).toEpochMilli()}catch(_:Exception){0L}
    Match(id.toString(),h,a,goal("pointsTeam1"),goal("pointsTeam2"),o.optBoolean("matchIsFinished"),date)
 }.distinctBy{it.id}
 fun goals(raw:JSONObject):List<String>{val g=raw.optJSONArray("goals")?:return emptyList();return (0 until g.length()).mapNotNull{g.optJSONObject(it)}.sortedBy{it.optInt("matchMinute")}.map{g->
    "${g.optInt("matchMinute")}' · ${g.optInt("scoreTeam1")}-${g.optInt("scoreTeam2")} · ${g.optString("goalGetterName").ifBlank{"Autor no disponible"}}"+(if(g.optBoolean("isOwnGoal"))" · autogol" else "")
 }}
}
