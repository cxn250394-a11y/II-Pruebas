package mx.charly.logic

import java.math.BigDecimal

object Numbers {
    fun number(s:String):Double?=s.trim().replace(',','.').toDoubleOrNull()?.takeIf{it.isFinite()}
    fun decimal(s:String):String {
        val value=s.trim().replace(',','.')
        require(value.matches(Regex("[-+]?[0-9]{1,6}(\\.[0-9]{1,6})?"))){"Línea decimal fuera de formato"}
        return BigDecimal(value).stripTrailingZeros().toPlainString()
    }
    fun odds(s:String):Double?=number(s)?.takeIf{it>1.0 && it<=10000.0}
    fun ev(probability:Double,odds:Double):Double { require(probability.isFinite()&&probability in 0.0..1.0&&odds.isFinite()&&odds>1);return probability*odds-1 }
    fun noVig(odds:List<Double>):List<Double>{require(odds.size>=2&&odds.all{it.isFinite()&&it>1});val sum=odds.sumOf{1/it};return odds.map{1/it/sum}}
    fun profit(stake:Double,odds:Double,result:String):Double?{
        require(stake.isFinite()&&stake>0&&odds.isFinite()&&odds>1)
        return when(result){"WIN"->stake*(odds-1);"LOSS"->-stake;"VOID"->0.0;"PENDING"->null;else->throw IllegalArgumentException("Resultado inválido")}
    }
}

/** Restricted RFC4180 reader; parse the complete import before replacing user state. */
object Csv {
    fun parse(input:String):List<Map<String,String>> {
        require(input.length<=2_000_000){"El CSV excede 2 MB"}
        val text=input.removePrefix("\uFEFF");val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val cell=StringBuilder()
        var quoted=false;var justClosed=false;var i=0
        fun endCell(){row+=cell.toString();cell.setLength(0);justClosed=false}
        fun endRow(){endCell();if(row.any{it.isNotBlank()})rows+=row.toList();row.clear();require(rows.size<=10001){"Máximo 10,000 filas"}}
        while(i<text.length){val c=text[i]
            if(quoted){if(c=='"'){if(i+1<text.length&&text[i+1]=='"'){cell.append('"');i++}else{quoted=false;justClosed=true}}else cell.append(c)}
            else when(c){'"'->{require(cell.isEmpty()&&!justClosed){"Comillas inválidas"};quoted=true};','->endCell();'\n'->endRow();'\r'->{if(i+1<text.length&&text[i+1]=='\n')i++;endRow()};else->{require(!justClosed||c==' '){"Contenido tras comillas"};if(!justClosed)cell.append(c)}}
            i++
        }
        require(!quoted){"Campo entre comillas incompleto"};if(cell.isNotEmpty()||row.isNotEmpty()||justClosed)endRow()
        require(rows.isNotEmpty()){ "CSV vacío" };val headers=rows.first().map{it.trim().lowercase()}
        require(headers.all{it.isNotEmpty()}&&headers.distinct().size==headers.size){"Encabezados vacíos o duplicados"}
        return rows.drop(1).mapIndexed{n,r->require(r.size==headers.size){"Fila ${n+2}: columnas incorrectas"};headers.zip(r).toMap()}
    }
}

data class Quote(val event:String,val market:String,val period:String,val selection:String,val line:String,val rules:String,val book:String,val price:Double,val seen:Long,val source:String){
    val key:List<String> get()=listOf(event.trim(),market.trim(),period.trim(),selection.trim(),line.trim(),rules.trim())
    fun fresh(now:Long,ttl:Long=120_000):Boolean=seen>0&&seen<=now&&now-seen<=ttl
}
object Quotes {
    fun parse(csv:String):List<Quote> = Csv.parse(csv).map { r ->
        val required=listOf("event","market","period","selection","line","rules","book","odds","seen_ms","source")
        require(r.keys.containsAll(required)){"Faltan columnas: ${required.joinToString()}"}
        fun s(k:String)=r.getValue(k).trim().also{require(it.length<=300){"Campo demasiado largo"}}
        val rawLine=s("line");val line=if(rawLine=="NA")rawLine else try{Numbers.decimal(rawLine)}catch(_:Exception){throw IllegalArgumentException("Línea inválida: usa número o NA")}
        val q=Quote(s("event"),s("market"),s("period"),s("selection"),line,s("rules"),s("book"),Numbers.odds(s("odds"))?:error("Cuota inválida"),s("seen_ms").toLongOrNull()?:error("seen_ms debe ser epoch en milisegundos"),s("source"))
        require(q.key.all{it.isNotBlank()}&&q.book.isNotBlank()&&q.source.isNotBlank()&&q.seen>0){"Identidad y procedencia obligatorias"};q
    }
    /** Same market/selection/line/period/settlement only. Never joins on fuzzy team names. */
    fun best(quotes:List<Quote>,now:Long):List<Quote> = quotes.filter{it.fresh(now)}
        .groupBy{it.key+it.book}.map{(_,q)->q.maxBy{it.seen}}
        .groupBy{it.key}.map{(_,q)->q.maxBy{it.price}}.sortedByDescending{it.price}
}

data class Match(val id:String,val home:String,val away:String,val homeGoals:Int?,val awayGoals:Int?,val finished:Boolean,val whenMs:Long=0){
    val validFinal get()=finished&&homeGoals!=null&&awayGoals!=null&&homeGoals>=0&&awayGoals>=0
}
data class Form(val played:Int,val won:Int,val drawn:Int,val lost:Int,val scored:Int,val conceded:Int,val bothScored:Int,val over25:Int)
object Stats {
    fun form(matches:List<Match>,team:String,last:Int=10):Form {
        val sample=matches.filter{it.validFinal&&(it.home==team||it.away==team)}.distinctBy{it.id}.sortedByDescending{it.whenMs}.take(last.coerceIn(1,100))
        var w=0;var d=0;var l=0;var gf=0;var ga=0;var b=0;var over=0
        sample.forEach{val h=it.homeGoals!!;val a=it.awayGoals!!;val own=if(it.home==team)h else a;val other=if(it.home==team)a else h
            when{own>other->w++;own==other->d++;else->l++};gf+=own;ga+=other;if(h>0&&a>0)b++;if(h+a>2)over++}
        return Form(sample.size,w,d,l,gf,ga,b,over)
    }
}
