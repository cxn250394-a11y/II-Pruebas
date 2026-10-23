package mx.charly.stats

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import mx.charly.ui.*
import mx.charly.logic.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class StatsActivity:Screen(){
 private val io=Executors.newSingleThreadExecutor()
 private val prefs by lazy{getSharedPreferences("stats",MODE_PRIVATE)}
 private val cache by lazy{LocalJson(this,"matches.json")}
 private val watch by lazy{WatchPrefs(this)}
 private val changes by lazy{WatchHistory(this)}
 private var onlyFollowed=false
 private fun scope()=StatsWatch.scope(league,season)
 private var raw=JSONArray();private var matches=emptyList<Match>();private var query="";private var page=0;private var busy=false
 private var league="bl1";private var season=java.time.LocalDate.now().year
 private var fetched=0L;private var importSource="OpenLigaDB";private var renderedList:android.widget.LinearLayout?=null
 override fun onCreate(state:Bundle?){super.onCreate(state);league=prefs.getString("league","bl1")?:"bl1";season=prefs.getInt("season",season);fetched=prefs.getLong("fetched",0);importSource=prefs.getString("source","OpenLigaDB")?:"OpenLigaDB"
   try{raw=cache.read();matches=Feed.parse(raw)}catch(_:Exception){toast("La caché no se pudo leer; puedes descargar una copia nueva")};home()
 }
 override fun onDestroy(){io.shutdown();super.onDestroy()}
 private fun whenText(ms:Long):String=if(ms==0L)"Hora no disponible" else DateTimeFormatter.ofPattern("dd MMM · HH:mm",Locale("es","MX")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms))
 private fun home(){layout("StatsDeck","Partidos + forma + historial · fuentes identificadas")
  renderedList=null
  card("${league.uppercase()} · temporada $season","Fuente: $importSource\nÚltima descarga: ${if(fetched>0)whenText(fetched) else "todavía no se ha descargado"}\nDatos comunitarios; pueden estar incompletos o retrasados. No son cuotas de apuestas.")
  button(if(busy)"Descarga en curso" else "Actualizar partidos",true){refresh()}
  button("Elegir liga / temporada"){choose()}
  button("Estadísticas por equipo"){teams()}
  button("Seguidos y cambios observados"){watchScreen()}
  button(if(onlyFollowed)"Mostrar todos los partidos"else"Ver solo mis seguidos"){onlyFollowed=!onlyFollowed;home()}
  button("Exportar muestra CSV"){exportCsv()}
  button("Importar resultados CSV"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE)},11)}
  label("Córners, tarjetas y tiros: N/D en esta fuente. Nunca se sustituyen por ceros.",12f,soft)
  label("Los cambios de marcador solo se registran al pulsar Actualizar. No equivalen a goles confirmados ni avisos en segundo plano.",12f,soft)
  val search=field("Buscar equipo",query)
  search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun afterTextChanged(s:Editable?){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){query=s.toString();page=0;renderMatches()}})
  renderedList=column();body.addView(renderedList);renderMatches()
  button("Más partidos"){page++;renderMatches()}
  button("Fuente y licencia"){website("https://www.openligadb.de/lizenz")}
 }
 private fun renderMatches(){val holder=renderedList?:return;holder.removeAllViews()
  val now=System.currentTimeMillis()
  val currentScope=scope()
  val filtered=matches.filter{m->
     (!onlyFollowed || watch.isTracked(currentScope,m)) &&
       (query.isBlank()||(m.home+" "+m.away).contains(query,true))
  }.sortedBy{kotlin.math.abs(it.whenMs-now)}
  val visible=filtered.take((page+1)*40)
  holder.addView(text("${filtered.size} partidos · ${visible.size} visibles",12f,accent))
  if(filtered.isEmpty())holder.addView(text("No hay partidos para mostrar. Actualiza una liga o importa resultados.",14f,soft))
  visible.forEach{m->val state=if(m.finished)"FINAL" else if(m.whenMs>now)"PROGRAMADO" else "NO FINAL / ESTADO SIN CONFIRMAR"
    val b=android.widget.Button(this).apply{text="${m.home}   ${m.homeGoals?.toString()?:"–"} : ${m.awayGoals?.toString()?:"–"}   ${m.away}\n$state · ${whenText(m.whenMs)}";isAllCaps=false;setTextColor(android.graphics.Color.WHITE);backgroundTintList=android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(23,36,53));minHeight=dp(80);setOnClickListener{detail(m)}}
    holder.addView(b,android.widget.LinearLayout.LayoutParams(-1,-2))
  }
 }
 private fun refresh(){
  if(busy)return
  val now=System.currentTimeMillis()
  val leagueAtRequest=league;val seasonAtRequest=season;val requestedScope=scope()
  val oldScope=runCatching{
    StatsWatch.scope(prefs.getString("league","bl1")?:"bl1",prefs.getInt("season",seasonAtRequest))
  }.getOrNull()
  if(importSource=="OpenLigaDB" && requestedScope==oldScope && now-fetched<60_000){
    toast("Ya se descargó hace menos de un minuto");return
  }
  val compare=importSource=="OpenLigaDB" && oldScope==requestedScope && fetched>0L
  val previous=matches.toList()
  busy=true;toast("Consultando fuente pública · no es señal en vivo")
  io.execute {
   try{
    val downloaded=Feed.get("/getmatchdata/$leagueAtRequest/$seasonAtRequest")
    val parsed=Feed.parse(downloaded)
    val observed=if(compare)StatsWatch.diff(previous,parsed){ m->
       watch.isTracked(requestedScope,m)
    }else emptyList()
    cache.write(downloaded)
    val stamp=System.currentTimeMillis()
    val storedHistory=try{changes.append(requestedScope,"OpenLigaDB",stamp,observed);true}catch(_:Exception){false}
    prefs.edit().putString("league",leagueAtRequest).putInt("season",seasonAtRequest)
      .putLong("fetched",stamp).putString("source","OpenLigaDB").apply()
    runOnUiThread{
      if(!isDestroyed){
        raw=downloaded;matches=parsed;fetched=stamp;importSource="OpenLigaDB";busy=false;page=0
        home()
        if(observed.isNotEmpty())toast(observed.size.toString()+" cambios de seguidos observados"+
          if(storedHistory)"" else " · no se pudo guardar el historial")
      }
    }
   }catch(e:Exception){runOnUiThread{
      if(!isDestroyed){busy=false;toast("No se actualizó: "+(e.message?.take(140)?:"error")+". Se conserva la caché.");home()}
   }}
  }
 }
 private fun choose(){layout("Fuentes y cobertura","Sin claves ni API contratada")
  label("Puedes consultar la oferta actual del proveedor. La existencia de una liga no garantiza que esté completa.",13f,soft)
  val year=field("Año de inicio de temporada",season.toString(),numeric=true);val code=field("Código de liga",league)
  button("Usar esta liga",true){val y=year.text.toString().toIntOrNull();val c=code.text.toString().trim();if(y==null||y !in 2000..2100||!c.matches(Regex("[A-Za-z0-9_-]{1,40}"))){toast("Liga o año inválido");return@button};league=c;season=y;fetched=0;refresh()}
  button("Consultar catálogo de ligas de ese año"){
   val y=year.text.toString().toIntOrNull()?:return@button;if(busy)return@button;busy=true
   io.execute{try{val data=Feed.get("/getavailableleagues/$y");val options=(0 until data.length()).mapNotNull{data.optJSONObject(it)}.filter{it.optString("leagueShortcut").isNotBlank()}.take(1000)
    runOnUiThread{if(!isDestroyed){busy=false;val names=options.map{it.optString("leagueName")+" ["+it.optString("leagueShortcut")+"]"};android.app.AlertDialog.Builder(this).setTitle("Ligas disponibles · $y").setItems(names.toTypedArray()){_,i->code.setText(options[i].optString("leagueShortcut"))}.setNegativeButton("Cerrar",null).show()}}
   }catch(e:Exception){runOnUiThread{busy=false;if(!isDestroyed)toast(e.message?:"Sin catálogo")}}}
  };button("Volver"){home()}
 }
 private fun exportCsv(){
   if(matches.isEmpty()){toast("No hay partidos en esta muestra para exportar");return}
   try{
     startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
       addCategory(Intent.CATEGORY_OPENABLE)
       type="text/csv"
       putExtra(Intent.EXTRA_TITLE,"statsdeck-"+league+"-"+season+"-muestra.csv")
     },12)
   }catch(_:Exception){toast("No se pudo abrir el selector de archivos")}
 }
 private fun watchScreen(){
   layout("Mis seguidos","Marcadores observados en tus actualizaciones · sin push automático")
   button("Volver"){home()}
   val current=scope()
   val teamNames=watch.teams(current).sorted()
   val matchIds=watch.matches(current).sorted()
   card("Equipos seguidos ("+teamNames.size+")",
      teamNames.joinToString("\n").ifBlank{"Ninguno: abre Estadísticas por equipo y marca ★ Seguir."})
   card("Partidos seguidos ("+matchIds.size+")",
      matchIds.joinToString(", ").ifBlank{"Ninguno: abre un partido y marca ★ Seguir."})
   button("Ver solo mis seguidos"){onlyFollowed=true;home()}
   button("Actualizar y comprobar cambios",true){refresh()}
   label("La primera consulta no inventa cambios. No son goles confirmados ni avisos con la app cerrada.",12f,soft)
   val records=try{changes.all(current).take(60)}catch(_:Exception){
     label("El historial no está legible y se conservará sin sobrescribirse.",12f,soft);return
   }
   if(records.isEmpty())card("Todavía no hay cambios","Después de seguir equipos o partidos, actualiza la misma liga y temporada otra vez para comparar observaciones.")
   records.forEach { record->
     val e=record.change
     val before=StatsWatch.score(e.beforeHome,e.beforeAway)
     val after=StatsWatch.score(e.afterHome,e.afterAway)
     card(e.home+" vs "+e.away+" · "+e.kind.label,
        before+" → "+after+"\nObservado: "+whenText(record.observedAt)+
        "\nFuente: "+record.source+". No confirma goles, tarjetas ni córners.")
   }
 }
 private fun teams(){layout("Forma de equipos","Solo partidos finalizados con marcador disponible")
  button("Volver"){home()};val names=matches.flatMap{listOf(it.home,it.away)}.distinct().sorted()
  if(names.isEmpty()){label("Descarga una liga primero");return}
  var chosen=names.first();menu(names){chosen=names[it]};button("Analizar últimos 10",true){team(chosen)}
 }
 private fun team(name:String){layout(name,"Últimos 10 finalizados de la muestra local")
  button("Volver a equipos"){teams()}
  button(if(StatsWatch.teamKey(name) in watch.teams(scope()))"Dejar de seguir equipo" else "★ Seguir este equipo"){
    watch.toggleTeam(scope(),name);team(name)
  }
  val f=Stats.form(matches,name)
  card("${f.played} partidos analizados","Ganados ${f.won} · empatados ${f.drawn} · perdidos ${f.lost}\nGoles a favor ${f.scored} · en contra ${f.conceded}")
  if(f.played>0){card("Distribución observada","Ambos anotaron: ${f.bothScored}/${f.played}\nMás de 2.5 goles: ${f.over25}/${f.played}\nEstas frecuencias NO son probabilidades de un próximo partido.")}
  matches.filter{it.validFinal&&(it.home==name||it.away==name)}.sortedByDescending{it.whenMs}.take(10).forEach{m->card("${m.home} ${m.homeGoals}-${m.awayGoals} ${m.away}",whenText(m.whenMs)){detail(m)}}
 }
 private fun detail(m:Match){layout("${m.home} vs ${m.away}","Fuente: $importSource · ID ${m.id}")
  button("Volver"){home()}
  button(if(m.id in watch.matches(scope()))"Dejar de seguir partido"else"★ Seguir este partido"){
    watch.toggleMatch(scope(),m.id);detail(m)
  }
  card("${m.homeGoals?:"–"} : ${m.awayGoals?:"–"}","${if(m.finished)"FINAL" else "NO FINAL"} · ${whenText(m.whenMs)}\nDescargado: ${whenText(fetched)}")
  val objectRaw=(0 until raw.length()).mapNotNull{raw.optJSONObject(it)}.firstOrNull{it.optLong("matchID").toString()==m.id}
  if(objectRaw!=null){val goals=Feed.goals(objectRaw);label("Goles registrados por la fuente",18f,accent);label(if(goals.isEmpty())"Sin detalle de goles disponible" else goals.joinToString("\n"),14f,soft)}
  label("Córners N/D · tarjetas N/D · remates N/D",14f,soft)
  button("Forma de ${m.home}"){team(m.home)};button("Forma de ${m.away}"){team(m.away)}
  val h2h=matches.filter{it.validFinal&&setOf(it.home,it.away)==setOf(m.home,m.away)}
  card("Enfrentamientos en esta muestra: ${h2h.size}",h2h.joinToString("\n"){"${it.home} ${it.homeGoals}-${it.awayGoals} ${it.away}"}.ifBlank{"No hay historial suficiente"})
 }
 @Deprecated("Activity compatibility") override fun onActivityResult(request:Int,result:Int,data:Intent?){
   super.onActivityResult(request,result,data)
   if(result!=RESULT_OK)return
   if(request==12) {
     val uri=data?.data?:return
     try{
       val output=MatchExport.csv(matches).toByteArray(Charsets.UTF_8)
       contentResolver.openOutputStream(uri,"w")?.use{it.write(output)} ?: error("El archivo no está disponible")
       toast("CSV exportado. Son observaciones de la fuente, no datos verificados independientemente.")
     }catch(_:Exception){toast("No se pudo guardar el CSV. La muestra local se conserva.")}
     return
   }
   if(request!=11)return
   val uri=data?.data?:return
  try{val rows=contentResolver.openInputStream(uri)!!.use{Csv.parse(String(Streams.readLimited(it,2_000_000),Charsets.UTF_8))};val converted=JSONArray()
   rows.forEach{r->val id=r["id"]?.toLongOrNull()?:error("Falta id numérico");val h=r["home"].orEmpty();val a=r["away"].orEmpty();val hg=r["home_goals"]?.toIntOrNull();val ag=r["away_goals"]?.toIntOrNull();val date=r["date_utc"].orEmpty();require(h.isNotBlank()&&a.isNotBlank()&&hg!=null&&ag!=null&&hg>=0&&ag>=0);Instant.parse(date)
    converted.put(JSONObject().put("matchID",id).put("team1",JSONObject().put("teamName",h)).put("team2",JSONObject().put("teamName",a)).put("matchIsFinished",true).put("matchDateTimeUTC",date).put("matchResults",JSONArray().put(JSONObject().put("resultTypeID",2).put("pointsTeam1",hg).put("pointsTeam2",ag))))
   }
   confirm("Importar ${rows.size} resultados","Sustituye la muestra local. Columnas: id,home,away,home_goals,away_goals,date_utc. No valida independientemente los resultados."){
    try{cache.write(converted);raw=converted;matches=Feed.parse(raw);importSource="CSV del usuario (sin verificar)";fetched=System.currentTimeMillis();prefs.edit().putString("source",importSource).putLong("fetched",fetched).apply();home()}catch(_:Exception){toast("No se pudo guardar")}
   }
  }catch(e:Exception){toast("Importación rechazada: ${e.message?.take(160)}")}
 }
}
