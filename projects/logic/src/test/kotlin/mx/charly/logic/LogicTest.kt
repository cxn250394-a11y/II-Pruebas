package mx.charly.logic

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LogicTest {
    @Test fun csvQuotedComma(){assertEquals("a,b",Csv.parse("x,y\n\"a,b\",c").single()["x"])}
    @Test fun csvEmbeddedQuote(){assertEquals("a\"b",Csv.parse("x\n\"a\"\"b\"").single()["x"])}
    @Test fun csvEmbeddedNewline(){assertEquals("a\nb",Csv.parse("x\n\"a\nb\"").single()["x"])}
    @Test fun csvBomCrlf(){assertEquals("v",Csv.parse("\uFEFFx\r\nv\r\n").single()["x"])}
    @Test(expected=IllegalArgumentException::class) fun csvRejectsTruncated(){Csv.parse("a\n\"broken")}
    @Test(expected=IllegalArgumentException::class) fun csvRejectsWrongColumns(){Csv.parse("a,b\nx")}
    @Test(expected=IllegalArgumentException::class) fun csvRejectsDuplicateHeaders(){Csv.parse("a,a\nx,y")}
    @Test fun oddsComma(){assertEquals(1.85,Numbers.odds("1,85")!!,0.0)}
    @Test fun oddsFinite(){assertNull(Numbers.odds("NaN"));assertNull(Numbers.odds("Infinity"));assertNull(Numbers.odds("1"))}
    @Test fun canonicalLines(){assertEquals("2.5",Numbers.decimal("2.50"))}
    @Test fun evKnown(){assertEquals(.08,Numbers.ev(.6,1.8),.000001)}
    @Test fun noVigSumsOne(){val p=Numbers.noVig(listOf(1.9,1.9));assertEquals(.5,p[0],.000001);assertEquals(1.0,p.sum(),.000001)}
    @Test(expected=IllegalArgumentException::class) fun invalidProbability(){Numbers.ev(1.1,2.0)}
    @Test fun profitStates(){assertEquals(80.0,Numbers.profit(100.0,1.8,"WIN")!!,.0001);assertEquals(-100.0,Numbers.profit(100.0,1.8,"LOSS")!!,0.0);assertEquals(0.0,Numbers.profit(100.0,1.8,"VOID")!!,0.0);assertNull(Numbers.profit(100.0,1.8,"PENDING"))}
    private fun q(book:String="A",price:Double=1.9,seen:Long=1000,line:String="2.5",rules:String="90min")=Quote("event1","total","FT","over",line,rules,book,price,seen,"manual")
    @Test fun sameMarketBest(){assertEquals("B",Quotes.best(listOf(q(),q("B",2.0)),1000).single().book)}
    @Test fun newestBookQuoteNotOldBest(){assertEquals(1.8,Quotes.best(listOf(q(price=2.2,seen=900),q(price=1.8)),1000).single().price,0.0)}
    @Test fun differentLinesNotMerged(){assertEquals(2,Quotes.best(listOf(q(),q(line="3.5")),1000).size)}
    @Test fun settlementNotMerged(){assertEquals(2,Quotes.best(listOf(q(),q(rules="incl-ET")),1000).size)}
    @Test fun staleExcluded(){assertTrue(Quotes.best(listOf(q()),121001).isEmpty())}
    @Test fun ttlInclusive(){assertEquals(1,Quotes.best(listOf(q()),121000).size)}
    @Test fun futureExcluded(){assertTrue(Quotes.best(listOf(q(seen=1001)),1000).isEmpty())}
    @Test fun noInferenceForMissingData(){assertEquals(0,Stats.form(listOf(Match("1","a","b",null,null,true)),"a").played)}
    @Test fun liveExcluded(){assertEquals(0,Stats.form(listOf(Match("1","a","b",2,1,false)),"a").played)}
    @Test fun formBothSides(){val f=Stats.form(listOf(Match("1","a","b",2,1,true),Match("2","b","a",3,0,true)),"a");assertEquals(2,f.played);assertEquals(1,f.won);assertEquals(1,f.lost);assertEquals(2,f.scored);assertEquals(4,f.conceded)}
    @Test fun duplicatesNotDoubleCounted(){val m=Match("1","a","b",2,1,true);assertEquals(1,Stats.form(listOf(m,m),"a").played)}
    @Test fun raceDoesNotMoveBeforeStart(){val r=Race();r.step(.02f,0f,false,false);assertEquals(0f,r.drivers[0].distance,0f)}
    @Test fun raceBoundsAndFinite(){val r=Race();repeat(5000){r.step(.02f,Float.NaN,false,false)};assertTrue(r.drivers.all{it.distance.isFinite()});assertTrue(abs(r.drivers[0].lane)<=8)}
    @Test fun raceBrakes(){val a=Race();val b=Race();repeat(1000){a.step(.02f,0f,false,false);b.step(.02f,0f,false,true)};assertTrue(a.drivers[0].distance>b.drivers[0].distance)}
    @Test fun raceNoTeleportAfterPause(){val r=Race();repeat(200){r.step(.02f,0f,false,false)};val before=r.drivers[0].distance;r.step(100f,0f,false,false);assertTrue(r.drivers[0].distance-before<5f)}
    @Test fun raceEventuallyFinishes(){val r=Race();repeat(15000){r.step(.02f,0f,false,false)};assertTrue(r.finished);val d=r.drivers[0].distance;r.step(1f,0f,false,false);assertEquals(d,r.drivers[0].distance,0f)}
    @Test fun raceReset(){assertEquals("",Race().item);assertFalse(Race().finished);assertEquals(1,Race().lap)}
    @Test fun trackCloses(){val r=Race();val a=r.position(0f);val b=r.position(r.length);a.indices.forEach{assertEquals(a[it],b[it],.001f)}}
    private fun wav(bits:Int=16,format:Int=1):ByteArray{val b=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".toByteArray());b.putInt(36);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(format.toShort());b.putShort(2);b.putInt(48000);b.putInt(48000*2*bits/8);b.putShort((2*bits/8).toShort());b.putShort(bits.toShort());b.put("data".toByteArray());b.putInt(0);return b.array()}
    @Test fun wav16(){assertEquals(48000,Wave.header(ByteArrayInputStream(wav())).rate)}
    @Test fun wav24(){assertEquals(6,Wave.header(ByteArrayInputStream(wav(24))).frameBytes)}
    @Test(expected=IllegalArgumentException::class) fun wavFloatRejected(){Wave.header(ByteArrayInputStream(wav(32,3)))}
    @Test(expected=IllegalArgumentException::class) fun wavTruncatedRejected(){Wave.header(ByteArrayInputStream(byteArrayOf(1,2,3)))}
    private fun abs(x:Float)=kotlin.math.abs(x)
}
