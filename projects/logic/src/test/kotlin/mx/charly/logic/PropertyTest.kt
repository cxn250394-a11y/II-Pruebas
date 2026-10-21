package mx.charly.logic

import kotlin.random.Random
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

/** Deterministic property samples; not device, fidelity or frame-rate tests. */
class PropertyTest {
    @Test fun probabilityAndEvProperties() {
        val random=Random(240394)
        repeat(2500) {
            val prices=List(random.nextInt(2,10)){random.nextDouble(1.01,200.0)}
            val p=Numbers.noVig(prices)
            assertEquals(1.0,p.sum(),1e-10)
            assertTrue(p.all{it in 0.0..1.0})
            val probability=random.nextDouble();val odds=random.nextDouble(1.01,100.0)
            assertEquals(probability*odds-1.0,Numbers.ev(probability,odds),1e-12)
        }
    }
    @Test fun quotedCsvRoundTrips() {
        val samples=listOf("uno","con,coma","doble\"comilla","dos\nlineas","córner",""," normal ")
        fun quote(s:String)="\""+s.replace("\"","\"\"")+"\""
        for(a in samples)for(b in samples){
            val rows=Csv.parse("a,b\n${quote(a)},${quote(b)}")
            if(a.isBlank()&&b.isBlank())assertTrue(rows.isEmpty())
            else assertEquals(mapOf("a" to a,"b" to b),rows.single())
        }
    }
    @Test fun allRaceConfigurationsFinishWithoutInvalidState() {
        for(circuit in 0..2)for(vehicle in 0..2)for(difficulty in 0..2){
            val race=Race(circuit,vehicle,difficulty)
            repeat(18000){n->
                race.step(1f/60,0f,false,false)
                assertTrue(race.drivers.all{it.distance.isFinite()&&it.speed.isFinite()&&abs(it.lane)<=8f})
                if(n%240==0)race.useItem()
            }
            assertTrue(race.finished);assertTrue(race.rank in 1..5);assertEquals(3,race.lap)
        }
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnboundedDecimalExponent(){Numbers.decimal("1e2147483647")}
    @Test fun canonicalDecimalBoundaries(){assertEquals("999999.999999",Numbers.decimal("999999.999999"));assertEquals("-2.5",Numbers.decimal("-2,500000"));assertEquals("0",Numbers.decimal("-0.000000"))}
}
