package mx.charly.odds

import android.content.Intent
import android.os.Bundle
import mx.charly.ui.*
import mx.charly.logic.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Offline analyst: no sportsbook logins, bets, fabricated live prices or SB integration. */
class OddsActivity:Screen(){
    private val prefs by lazy{getSharedPreferences("odds",MODE_PRIVATE)}
    private val ledger by lazy{LocalJson(this,"ledger.json")}
    private var quotes=emptyList<Quote>()
    private var bets=JSONArray()
    private var healthy=true
    private var demo=false
    private fun f(n:Double)=String.format(Locale.US,"%.2f",n)
    private fun tm(n:Long)=SimpleDateFormat("dd/MM HH:mm:ss",Locale("es","MX")).format(Date(n))
    override fun onCreate(state:Bundle?){super.onCreate(state)
        try{bets=ledger.read();val csv=prefs.getString("csv","").orEmpty();if(csv.isNotBlank())quotes=Quotes.parse(csv)}catch(_:Exception){healthy=false;toast("No se pudo leer el archivo. No se sobrescribirán tus datos.")};home()
    }
    private fun home(){layout("OddsDeck","Comparación + registro + cálculo · independiente")
        card("Datos observados, no promesas de ganancia","No está conectado a casas ni a SB. Trabaja con precios que introduzcas o importes. Ninguna cuota se presenta como verificada en vivo.")
        button("Comparar cuotas",true){comparison()};button("Mi registro de apuestas"){portfolio()};button("Calculadora EV / cuotas justas"){calculator()}
        button("Agregar una cuota observada"){manual()};button("Importar archivo CSV"){pick()};button("Ver ejemplo de CSV"){demo=true;quotes=Quotes.parse(example());comparison()}
        if(demo)button("Salir del ejemplo"){demo=false;quotes=try{prefs.getString("csv","").orEmpty().let{if(it.isBlank())emptyList() else Quotes.parse(it)}}catch(_:Exception){emptyList()};home()}
        label("Los precios solo son comparables si evento, mercado, selección, línea, periodo y reglas de liquidación coinciden. Caducidad operativa: 120 segundos; no garantiza que una casa conserve el precio.",12f,soft)
    }
    private fun example():String{val now=System.currentTimeMillis();return "event,market,period,selection,line,rules,book,odds,seen_ms,source\nDEMO-1,total_goles,FT,over,2.5,90min,Casa A,1.85,$now,DEMO FICTICIO\nDEMO-1,total_goles,FT,over,2.5,90min,Casa B,1.95,$now,DEMO FICTICIO"}
    private fun comparison(){layout("Comparador",if(demo)"DEMO FICTICIO · no son precios reales" else "Cuotas importadas / anotadas por ti")
        button("Volver"){home()};button("Revisar vigencia ahora"){comparison()};button("Agregar precio"){manual()}
        val now=System.currentTimeMillis();val best=Quotes.best(quotes,now)
        label("${quotes.size} observaciones · ${best.size} mejores precios vigentes por selección",13f,accent)
        if(quotes.isEmpty())card("Todavía no hay precios","Agrega una observación o importa un CSV. No se generan precios ficticios automáticamente.")
        best.take(300).forEach{q->card("${q.book} · ${f(q.price)}","${q.event}\n${q.market} · ${q.selection} ${q.line} · ${q.period}\nReglas: ${q.rules}\nObservado: ${tm(q.seen)}\nFuente: ${q.source}")}
        val stale=quotes.count{!it.fresh(now)};if(stale>0)card("$stale observaciones fuera de vigencia","Se conservan en el archivo, pero no se usan para elegir el mejor precio. Importa una medición nueva; no se rejuvenece una cuota vieja.")
        if(best.size>300)label("Mostrando las primeras 300 selecciones; se evaluaron todas.",12f,soft)
        button("Formato de importación"){info("CSV requerido","event,market,period,selection,line,rules,book,odds,seen_ms,source\n\nseen_ms = instante de observación en milisegundos UTC. line = número o NA. No se deducen cuotas ni probabilidades de notificaciones.")}
    }
    private fun manual(){demo=false;layout("Anotar cuota","Solo registra tu observación; no envía apuestas")
        val event=field("ID exacto del evento");val market=field("Mercado","total_goles");val period=field("Periodo","FT");val selection=field("Selección","over");val line=field("Línea / NA","2.5");val rules=field("Reglas de liquidación","90min");val book=field("Casa");val price=field("Cuota decimal","",numeric=true);val source=field("Dónde la observaste","manual")
        button("Guardar con la hora actual",true){
            if(!healthy){toast("Archivo local bloqueado por error de lectura");return@button}
            fun esc(s:String)="\""+s.replace("\"","\"\"")+"\""
            val row=listOf(event.text,market.text,period.text,selection.text,line.text,rules.text,book.text,price.text,System.currentTimeMillis().toString(),source.text).joinToString(","){esc(it.toString())}
            val head="event,market,period,selection,line,rules,book,odds,seen_ms,source"
            val old=prefs.getString("csv","").orEmpty();val csv=(if(old.isBlank())head else old.trimEnd())+"\n"+row
            try{val parsed=Quotes.parse(csv);prefs.edit().putString("csv",csv).apply();quotes=parsed;comparison()}catch(e:Exception){toast(e.message?:"Entrada inválida")}
        };button("Cancelar"){home()}
    }
    private fun pick(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE)},10)}
    @Deprecated("Activity compatibility") override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data)
        if(request!=10||result!=RESULT_OK)return;val uri=data?.data?:return
        try{val csv=contentResolver.openInputStream(uri)!!.use{val b=it.readNBytes(2_000_001);require(b.size<=2_000_000){"Archivo demasiado grande"};String(b,Charsets.UTF_8)};val parsed=Quotes.parse(csv)
            confirm("Importar ${parsed.size} observaciones","Reemplazará únicamente el archivo de cuotas, no tu registro de apuestas."){
                if(!healthy){toast("No se puede escribir: error previo de lectura");return@confirm};prefs.edit().putString("csv",csv).apply();quotes=parsed;demo=false;comparison()
            }
        }catch(e:Exception){toast(e.message?:"CSV inválido; no se modificó nada")}
    }
    private fun portfolio(){layout("Mi registro","Seguimiento manual · no compra ni envía apuestas")
        button("Volver"){home()};button("Registrar apuesta",true){newBet()}
        var profit=0.0;var settled=0.0;var pending=0.0
        for(i in 0 until bets.length()){val b=bets.getJSONObject(i);val p=Numbers.profit(b.getDouble("stake"),b.getDouble("odds"),b.getString("result"));if(p==null)pending+=b.getDouble("stake") else{profit+=p;settled+=b.getDouble("stake")}}
        card("Resultado neto: ${f(profit)} MXN","ROI liquidado: ${if(settled>0)f(profit/settled*100)+"%" else "sin muestra"}\nImporte pendiente: ${f(pending)} MXN\nNo es un pronóstico de resultados futuros.")
        for(i in bets.length()-1 downTo maxOf(0,bets.length()-300)){val b=bets.getJSONObject(i);val close=b.optDouble("close",Double.NaN)
            card("${b.getString("selection")} · ${b.getString("result")}","${b.getString("book")} · ${f(b.getDouble("odds"))} · ${f(b.getDouble("stake"))} MXN\n${tm(b.getLong("time"))}"+(if(close.isFinite())"\nCambio de precio vs cierre: ${f((b.getDouble("odds")/close-1)*100)}% (sin quitar margen)" else "")){
                val choices=arrayOf("Ganada","Perdida","Nula","Pendiente","Anotar cuota de cierre")
                android.app.AlertDialog.Builder(this).setTitle("Actualizar registro local").setItems(choices){_,n->if(n<4){b.put("result",listOf("WIN","LOSS","VOID","PENDING")[n]);saveBets();portfolio()}else prompt("Cuota decimal de cierre") {v->val d=Numbers.odds(v);if(d==null)toast("Cuota inválida") else{b.put("close",d);saveBets();portfolio()}}}.show()
            }
        }
    }
    private fun newBet(){layout("Registrar apuesta","Anota una apuesta que decidiste por tu cuenta")
        val selection=field("Evento y selección");val book=field("Casa");val odds=field("Cuota decimal",numeric=true);val stake=field("Importe MXN",numeric=true)
        button("Guardar registro",true){val o=Numbers.odds(odds.text.toString());val s=Numbers.number(stake.text.toString());if(o==null||s==null||s<=0||selection.text.isBlank()||book.text.isBlank()){toast("Completa los campos con valores válidos");return@button}
            if(!healthy||bets.length()>=10000){toast("Archivo bloqueado o límite de 10,000 registros");return@button}
            bets.put(JSONObject().put("selection",selection.text.toString().take(300)).put("book",book.text.toString().take(100)).put("odds",o).put("stake",s).put("result","PENDING").put("time",System.currentTimeMillis()));saveBets();portfolio()
        };button("Cancelar"){portfolio()}
    }
    private fun saveBets(){try{if(healthy)ledger.write(bets)}catch(_:Exception){healthy=false;toast("No se pudo guardar; no des por registrado el cambio")}}
    private fun calculator(){layout("Herramientas","Cálculos transparentes, no probabilidades inventadas")
        button("Volver"){home()}
        label("EV a partir de una probabilidad que tú introduces",18f,accent)
        val prob=field("Probabilidad estimada, %",numeric=true);val price=field("Cuota decimal",numeric=true);val out=label("La probabilidad es un supuesto, no un hecho.",13f,soft)
        button("Calcular EV") {val p=Numbers.number(prob.text.toString());val o=Numbers.odds(price.text.toString());if(p==null||p !in 0.0..100.0||o==null)out.text="Valores inválidos" else out.text="EV por unidad = ${f(Numbers.ev(p/100,o)*100)}%\nEquilibrio: ${f(100/o)}%\nSolo es válido si la probabilidad estimada es correcta."}
        label("Quitar margen proporcional",18f,accent);val outcomes=field("Cuotas de TODOS los resultados, separadas con ;","1.90;1.90");val noVig=label("Solo resultados mutuamente excluyentes y exhaustivos.",13f,soft)
        button("Calcular probabilidades sin margen") {try{val os=outcomes.text.toString().split(';').map{Numbers.odds(it)?:error("Cuota inválida")};noVig.text=Numbers.noVig(os).mapIndexed{i,p->"Resultado ${i+1}: ${f(p*100)}% · cuota ${f(1/p)}"}.joinToString("\n")+"\nEstimación proporcional, no probabilidad real verificada."}catch(_:Exception){noVig.text="Introduce al menos dos cuotas válidas"}}
    }
}
