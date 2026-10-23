package mx.charly.logic

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Sandrift v0.2 race-engine tests. Each test checks a behaviour that a broken implementation would fail. */
class RaceTest {
    private fun go(r:Race,s:Int,steer:Float=0f,drift:Boolean=false,brake:Boolean=false,throttle:Boolean=true){repeat(s){r.step(1f/60,steer,drift,brake,throttle)}}
    private fun started()=Race(rivals=0).also{go(it,600)}

    @Test fun speedAndPositionStayFiniteUnderHostileInput(){
        val r=Race(2,2,2,seed=99)
        val inputs=floatArrayOf(Float.NaN,Float.POSITIVE_INFINITY,-1e9f,1f,-1f,0f)
        repeat(20000){n->r.step(if(n%97==0)Float.NaN else if(n%89==0)1e6f else 1f/60,inputs[n%inputs.size],n%3!=0,n%50==0)
            if(n%120==0)r.useItem()
            r.drivers.forEach{d->assertTrue(d.distance.isFinite()&&d.speed.isFinite()&&d.lane.isFinite()&&d.latVel.isFinite())
                assertTrue(abs(d.lane)<=Race.WALL);assertTrue(d.speed<=Race.maxSpeed(50f))}}
    }
    @Test fun throttleAcceleratesAndCoastingSlows(){
        val r=started();val v=r.drivers[0].speed;assertTrue(v>20f)
        go(r,120,throttle=false);assertTrue(r.drivers[0].speed<v-5f)
        val b=started();go(b,60,brake=true);assertTrue(b.drivers[0].speed<r.drivers[0].speed)
    }
    @Test fun speedNeverExceedsVehicleCapEvenWithStackedBoosts(){
        for(v in 0..2){val r=Race(vehicle=v,rivals=0);go(r,200)
            repeat(400){r.drivers[0].boost=5f;r.step(1f/60,0f,false,false)}
            assertTrue(r.drivers[0].speed<=Race.maxSpeed(Race.VEHICLES[v].top)+1e-3f)
            r.drivers[0].speed=500f;r.step(1f/60,0f,false,false);assertTrue(r.drivers[0].speed<=Race.maxSpeed(Race.VEHICLES[v].top)+1e-3f)}
    }
    @Test fun pausedRaceDoesNotAdvance(){
        val r=started();r.paused=true;val d=r.drivers[0].distance;val t=r.elapsed
        go(r,300,steer=1f);r.useItem();assertEquals(d,r.drivers[0].distance,0f);assertEquals(t,r.elapsed,0f)
        r.paused=false;go(r,10);assertTrue(r.drivers[0].distance>d)
    }
    @Test fun wallStopsKartAndCostsSpeedWithoutExplosion(){
        val r=started();val before=r.drivers[0].speed
        var maxJump=0f;var last=r.drivers[0].lane
        repeat(240){r.step(1f/60,1f,false,false);maxJump=maxOf(maxJump,abs(r.drivers[0].lane-last));last=r.drivers[0].lane}
        assertEquals(Race.WALL,r.drivers[0].lane,1e-3f);assertTrue(r.wallHits>=1);assertTrue(maxJump<0.5f)
        assertTrue(r.drivers[0].speed<before)
    }
    @Test fun obstacleSlowsButDoesNotTeleport(){
        val r=Race(rivals=0);val o=r.obstacles[0];r.drivers[0].lane=o.lane;go(r,180)
        val d=r.drivers[0];d.distance=o.distance-5f;d.lane=o.lane;d.latVel=0f;d.speed=40f;d.boost=0f
        var jump=0f;var x=d.distance;repeat(30){r.step(1f/60,0f,false,false);jump=maxOf(jump,r.drivers[0].distance-x);x=r.drivers[0].distance}
        assertTrue(d.bumps>=1);assertTrue(d.speed<40f);assertTrue(jump<1.5f)
    }
    @Test fun shieldIgnoresObstacleSlowdown(){
        val r=Race(rivals=0);go(r,180);val o=r.obstacles[0];val d=r.drivers[0]
        d.distance=o.distance-3f;d.lane=o.lane;d.speed=40f;d.shield=5f;go(r,15)
        assertTrue(d.bumps>=1);assertTrue(d.speed>35f)
    }
    @Test fun driftChargeGivesTieredMiniTurbo(){
        val short=started();go(short,20,steer=1f,drift=true);short.drivers[0].lane=0f;go(short,1);assertEquals(0f,short.drivers[0].boost,0f)
        val mid=started();repeat(50){mid.drivers[0].lane=0f;mid.step(1f/60,1f,true,false)};go(mid,1);assertEquals(1,mid.lastBoostTier);assertTrue(mid.drivers[0].boost>.6f)
        val long=started();repeat(140){long.drivers[0].lane=0f;long.step(1f/60,-1f,true,false)};go(long,1);assertEquals(3,long.lastBoostTier)
        assertTrue(long.drivers[0].boost>mid.drivers[0].boost)
        assertEquals(0f,long.driftCharge,0f)
    }
    @Test fun driftDoesNotChargeWhenSlowOrStraight(){
        val r=Race(rivals=0);go(r,180+5,steer=1f,drift=true);assertEquals(0f,r.driftCharge,.1f)
        val s=started();go(s,120,steer=0f,drift=true);assertEquals(0f,s.driftCharge,0f)
    }
    @Test fun itemBoxGrantsOneItemAndRespawns(){
        val r=started();val box=r.boxes[1];val d=r.drivers[0]
        d.distance=box.distance-1f;d.lane=box.lane;d.latVel=0f;r.step(1f/60,0f,false,false)
        assertTrue(r.item in Race.ITEMS);assertFalse(box.active)
        val held=r.item;val other=r.boxes[4];d.distance=other.distance-1f;d.lane=other.lane;d.latVel=0f;r.step(1f/60,0f,false,false)
        assertEquals(held,r.item)
        go(r,(Race.BOX_RESPAWN*60).toInt()+5);assertTrue(box.active)
    }
    @Test fun usingItemConsumesItExactlyOnce(){
        val r=started();r.drivers[0].item="NITRO";r.useItem();assertEquals("",r.item);val b=r.drivers[0].boost;assertTrue(b>2f)
        go(r,30);r.useItem();assertTrue(r.drivers[0].boost<b)
    }
    @Test fun shieldExpires(){val r=started();r.drivers[0].item="SHIELD";r.useItem();assertTrue(r.drivers[0].shield>5f);go(r,6*60+5);assertEquals(0f,r.drivers[0].shield,0f)}
    @Test fun pulseStunsNearestRivalAheadAndShieldBlocks(){
        val r=Race();go(r,200);val me=r.drivers[0];val a=r.drivers[1];val b=r.drivers[2]
        a.distance=me.distance+20f;b.distance=me.distance+50f;a.item="";b.item=""
        me.item="PULSE";r.useItem();assertTrue(a.stun>0);assertEquals(0f,b.stun,0f)
        a.stun=0f;a.shield=3f;me.item="PULSE";r.useItem();assertEquals(0f,a.stun,0f);assertEquals(0f,a.shield,0f)
    }
    @Test fun mineHitsFollowerAndExpires(){
        val r=Race(rivals=1);go(r,200);val me=r.drivers[0];val f=r.drivers[1];f.item=""
        me.item="MINE";r.useItem();assertEquals(1,r.mines.size);val m=r.mines[0]
        f.distance=m.distance-1f;f.lane=m.lane;f.latVel=0f;f.shield=0f;r.step(1f/60,0f,false,false)
        assertTrue(f.stun>0);assertTrue(r.mines.isEmpty())
        me.item="MINE";r.useItem();go(r,(Race.MINE_LIFE*60).toInt()+5);assertTrue(r.mines.none{it.owner==0&&it.distance==me.distance})
    }
    @Test fun lapsCountUpToThreeAndFinish(){
        val r=Race(rivals=0);val seen=mutableSetOf<Int>()
        repeat(30000){if(!r.finished){r.step(1f/60,0f,false,false);seen+=r.lap}}
        assertEquals(setOf(1,2,3),seen);assertTrue(r.finished);assertTrue(r.drivers[0].distance>=r.length*3)
        assertEquals(1,r.rank)
    }
    @Test fun seededRacesAreDeterministicAndSeedMatters(){
        fun run(seed:Long):List<Float>{val r=Race(1,1,2,seed);repeat(9000){n->r.step(1f/60,if(n%400<200)0.3f else -0.3f,n%500<120,false);if(n%300==0)r.useItem()};return r.drivers.flatMap{listOf(it.distance,it.lane)}}
        assertEquals(run(42),run(42))
        val items=(1L..20L).map{s->val r=Race(seed=s);go(r,200);val b=r.boxes[0];r.drivers[0].distance=b.distance-1f;r.drivers[0].lane=b.lane;r.step(1f/60,0f,false,false);r.item}.toSet()
        assertTrue(items.size>=2)
    }
    @Test fun aiFinishesAtAPlausiblePace(){
        for(diff in 0..2){val r=Race(difficulty=diff);repeat(36000){if(!r.finished)r.step(1f/60,0f,false,false)}
            val best=r.drivers.drop(1).maxOf{it.distance}/r.elapsed
            assertTrue("AI avg speed $best",best<Race.maxSpeed(Race.VEHICLES[2].top));assertTrue(best>20f)}
    }
    @Test fun standingsAndRankAgree(){val r=Race();go(r,3000);assertEquals(r.rank,r.standings().indexOf(0)+1);assertEquals(5,r.standings().toSet().size)}
    @Test fun cupScoring(){
        val c=Cup();c.record(listOf(0,1,2,3,4));c.record(listOf(1,0,2,3,4));assertFalse(c.done);c.record(listOf(1,0,4,3,2))
        assertTrue(c.done);assertArrayEquals(intArrayOf(24,27,11,9,7),c.points);assertEquals(listOf(1,0,2,3,4),c.standings())
        val tie=Cup(2);tie.record(listOf(0,1));tie.record(listOf(1,0));assertArrayEquals(intArrayOf(17,17),tie.points);assertEquals(listOf(1,0),tie.standings())
    }
    @Test(expected=IllegalArgumentException::class) fun cupRejectsBadOrder(){Cup().record(listOf(0,0,1,2,3))}
    @Test fun timeTrialHasOnlyThePlayer(){val r=Race(rivals=0);assertEquals(1,r.drivers.size);assertEquals(1,r.rank)}
    @Test fun dunesCircuitHasRealElevation(){val r=Race(1);val ys=(0..200).map{r.position(r.length*it/200)[1]};assertTrue(ys.max()-ys.min()>8f)}
}
