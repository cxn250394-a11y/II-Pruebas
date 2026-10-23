package mx.charly.logic

import kotlin.math.*

/** One kart. Units: metres along the track centre line (distance), metres from the centre (lane), m/s (speed). */
data class Driver(var distance:Float=0f,var lane:Float=0f,var speed:Float=0f,var boost:Float=0f,var shield:Float=0f,var stun:Float=0f,
                  var latVel:Float=0f,var item:String="",var finishTime:Float=-1f,var hitCooldown:Float=0f,var bumps:Int=0,var itemDelay:Float=0f)
class Obstacle(val distance:Float,val lane:Float,val radius:Float)
class ItemBox(val distance:Float,val lane:Float){var respawn=0f;val active get()=respawn<=0f}
class Mine(val distance:Float,val lane:Float,val owner:Int){var life=Race.MINE_LIFE}
/** Small deterministic generator so seeded races replay exactly on every JVM/Android. */
class Rng(seed:Long){private var s=(seed*-7046029254386353131L) xor 0x5DEECE66DL;init{if(s==0L)s=1L;repeat(8){next()}};fun next():Float{s=s xor (s shl 13);s=s xor (s ushr 7);s=s xor (s shl 17);return ((s ushr 40)and 0xFFFFFF).toFloat()/16777216f}}
/** Vehicle trade-offs: top speed, acceleration and grip. Original stats, not taken from any commercial game. */
class Vehicle(val name:String,val top:Float,val accel:Float,val grip:Float)

/** Deterministic arcade simulation, independent from rendering (libGDX lives only in :kart). */
class Race(val circuit:Int=0,val vehicle:Int=0,val difficulty:Int=1,val seed:Long=7L,val rivals:Int=4){
    companion object{
        const val LAPS=3;const val ROAD=6f;const val WALL=7.5f;const val MINE_LIFE=12f;const val BOX_RESPAWN=4f
        val VEHICLES=listOf(Vehicle("BUGGY",41f,1.25f,5.5f),Vehicle("KART",40f,1.6f,6.5f),Vehicle("RALLY",44f,1.05f,4.4f))
        val DRIFT_LEVELS=floatArrayOf(.6f,1.3f,2.1f);val DRIFT_BOOST=floatArrayOf(0f,.7f,1.2f,1.8f)
        val ITEMS=listOf("NITRO","SHIELD","PULSE","MINE")
        fun maxSpeed(top:Float)=top*1.5f
    }
    val length=1000f+circuit*150f
    val stats=VEHICLES[vehicle.coerceIn(0,2)]
    val drivers=MutableList(1+rivals.coerceIn(0,4)){Driver(distance=-it*4f,lane=(it%3-1)*2.2f)}
    val obstacles:List<Obstacle> = List(6+circuit*2){val d=length*(.12f+it*.8f/(6+circuit*2));Obstacle(d,if(it%2==0)3.6f else -3.6f,1.2f)}
    val boxes:List<ItemBox> = listOf(.3f,.62f,.86f).flatMap{f->listOf(-3f,0f,3f).map{ItemBox(length*f,it)}}
    val mines=mutableListOf<Mine>()
    private val rng=Rng(seed*31+circuit*7+vehicle*3+difficulty)
    var countdown=3f;private set
    var elapsed=0f;private set
    var finished=false;private set
    var paused=false
    var driftCharge=0f;private set
    var lastBoostTier=0;private set
    var wallHits=0;private set
    private var driftWas=false
    private var finishRank=0
    val item:String get()=drivers[0].item
    val driftLevel:Int get()=DRIFT_LEVELS.count{driftCharge>=it}
    val rank:Int get()=if(finishRank>0)finishRank else 1+(1 until drivers.size).count{ahead(it,0)}
    val lap:Int get()=(max(0f,drivers[0].distance)/length).toInt().coerceAtMost(LAPS-1)+1
    private fun ahead(a:Int,b:Int):Boolean{val x=drivers[a];val y=drivers[b]
        if(x.finishTime>=0&&y.finishTime>=0)return x.finishTime<y.finishTime
        if(x.finishTime>=0)return true;if(y.finishTime>=0)return false;return x.distance>y.distance}
    /** Final classification: driver indexes, winner first. */
    fun standings():List<Int> = drivers.indices.sortedWith{a,b->if(a==b)0 else if(ahead(a,b))-1 else 1}

    fun useItem()=use(0)
    private fun use(i:Int){if(finished||countdown>0||paused)return;val me=drivers[i]
        when(me.item){
            "NITRO"->me.boost=max(me.boost,2.4f)
            "SHIELD"->me.shield=6f
            "PULSE"->drivers.indices.filter{it!=i}.map{drivers[it]}.filter{it.distance>me.distance-5&&it.distance-me.distance<90&&it.finishTime<0}
                .minByOrNull{it.distance-me.distance}?.let{if(it.shield>0)it.shield=0f else it.stun=1.6f}
            "MINE"->mines+=Mine(me.distance-3f,me.lane,i)
        }
        me.item=""}
    private fun grant(i:Int){val d=drivers[i];if(d.item.isNotEmpty())return
        val pos=1+(drivers.indices).count{it!=i&&ahead(it,i)};val r=rng.next()
        // Leaders mostly get defence; trailing karts get more speed and attack. Never two items at once.
        d.item=when{pos==1->if(r<.45f)"SHIELD" else if(r<.75f)"MINE" else if(r<.9f)"PULSE" else "NITRO"
            pos>=drivers.size-1->if(r<.45f)"NITRO" else if(r<.8f)"PULSE" else if(r<.9f)"SHIELD" else "MINE"
            else->ITEMS[(r*4).toInt().coerceIn(0,3)]}
        d.itemDelay=.8f+rng.next()*2.5f}

    fun step(inputDt:Float,steer:Float,drift:Boolean,brake:Boolean,throttle:Boolean=true){
        if(!inputDt.isFinite()||inputDt<=0||finished||paused)return
        val dt=inputDt.coerceAtMost(0.05f)
        if(countdown>0){countdown=(countdown-dt).coerceAtLeast(0f);return}
        elapsed+=dt
        val turn=if(steer.isFinite())steer.coerceIn(-1f,1f) else 0f
        val me=drivers[0]
        // Graduated drift: charge only while steering hard at speed; release grants a tiered mini-turbo.
        val drifting=drift&&abs(turn)>.3f&&me.speed>14f&&me.stun<=0
        if(drifting)driftCharge=(driftCharge+dt).coerceAtMost(2.5f)
        if(driftWas&&!drifting){val lvl=driftLevel;if(lvl>0){me.boost=max(me.boost,DRIFT_BOOST[lvl]);lastBoostTier=lvl};driftCharge=0f}
        driftWas=drifting
        drivers.forEachIndexed{i,d->
            d.boost=max(0f,d.boost-dt);d.shield=max(0f,d.shield-dt);d.stun=max(0f,d.stun-dt);d.hitCooldown=max(0f,d.hitCooldown-dt)
            if(d.finishTime>=0){d.speed=max(0f,d.speed-20*dt);d.distance+=d.speed*dt;return@forEachIndexed}
            val top=if(i==0)stats.top else 36.5f+difficulty*2.2f+i*.4f+rubber(i)
            var target=if(d.boost>0)top*1.45f else top
            val offroad=abs(d.lane)>ROAD
            if(offroad)target*=.55f
            if(d.stun>0)target*=.3f
            val accel=if(i==0)stats.accel else 1.2f
            val pedal=if(i==0)!brake&&throttle else true
            if(i==0&&brake)d.speed-=28f*dt
            else if(!pedal)d.speed-=5f*dt
            else d.speed+=(target-d.speed)*min(1f,dt*(if(target<d.speed)3.5f else if(d.boost>0)4f else accel))
            d.speed=d.speed.coerceIn(0f,maxSpeed(top))
            // Lateral motion: steering authority grows with speed; drifting widens the line.
            val wantLat=if(i==0)turn*(if(drifting)9f else 7f)*(.35f+.65f*min(1f,d.speed/top)) else aiLat(i,d)
            val grip=(if(i==0)stats.grip else 5f)*(if(offroad).6f else 1f)*(if(i==0&&drifting).7f else 1f)
            d.latVel+=(wantLat-d.latVel)*min(1f,grip*dt)
            d.lane+=d.latVel*dt
            if(abs(d.lane)>WALL){d.lane=sign(d.lane)*WALL;d.latVel=-d.latVel*.35f;if(d.hitCooldown<=0){d.speed*=.8f;d.hitCooldown=.4f;d.bumps++;if(i==0)wallHits++}}
            d.distance+=d.speed*dt
            if(i>0&&d.item.isNotEmpty()){d.itemDelay-=dt;if(d.itemDelay<=0)use(i)}
            if(d.distance>=length*LAPS){d.finishTime=elapsed;if(i==0)finished=true}
        }
        collide(dt)
        if(finished)finishRank=1+(1 until drivers.size).count{drivers[it].finishTime>=0&&drivers[it].finishTime<drivers[0].finishTime}
    }
    private fun rubber(i:Int):Float{val gap=drivers[i].distance-drivers[0].distance
        return if(gap>120f)-1.5f else if(gap<-150f)1.5f else 0f}
    private fun lapPos(x:Float)=((x%length)+length)%length
    private fun aiLat(i:Int,d:Driver):Float{
        var line=sin(d.distance*.012f+i*1.7f)*3.2f
        val here=lapPos(d.distance)
        obstacles.firstOrNull{val g=it.distance-here;g in 0f..28f&&abs(it.lane-line)<2.4f}?.let{line=if(it.lane>0)it.lane-4.2f else it.lane+4.2f}
        return ((line-d.lane)*1.4f).coerceIn(-6f,6f)}
    private fun collide(dt:Float){
        drivers.forEachIndexed{i,d->if(d.finishTime>=0)return@forEachIndexed
            val here=lapPos(d.distance)
            for(o in obstacles)if(abs(o.distance-here)<1.6f&&abs(o.lane-d.lane)<o.radius+.9f&&d.hitCooldown<=0){
                d.hitCooldown=.6f;d.bumps++
                if(d.shield<=0)d.speed=max(6f,d.speed*.55f)
                d.latVel=(if(d.lane>=o.lane)1f else -1f)*6f}
            for(b in boxes)if(b.active&&abs(b.distance-here)<1.8f&&abs(b.lane-d.lane)<1.5f){b.respawn=BOX_RESPAWN;grant(i)}
            val it=mines.iterator()
            while(it.hasNext()){val m=it.next();if(abs(m.distance-d.distance)<1.4f&&abs(m.lane-d.lane)<1.3f&&!(m.owner==i&&m.life>MINE_LIFE-1.5f)){
                if(d.shield>0)d.shield=0f else{d.stun=max(d.stun,1.2f);d.speed*=.6f};it.remove()}}
        }
        for(b in boxes)b.respawn=max(0f,b.respawn-dt)
        mines.forEach{it.life-=dt};mines.removeAll{it.life<=0}
        for(a in drivers.indices)for(b in a+1 until drivers.size){val x=drivers[a];val y=drivers[b]
            if(x.finishTime>=0||y.finishTime>=0)continue
            if(abs(x.distance-y.distance)<2.2f&&abs(x.lane-y.lane)<1.6f){val push=if(x.lane>=y.lane)1f else -1f
                x.latVel=push*3f;y.latVel=-push*3f;x.lane=(x.lane+push*.05f).coerceIn(-WALL,WALL);y.lane=(y.lane-push*.05f).coerceIn(-WALL,WALL)}}
    }
    fun position(distance:Float,lane:Float=0f):FloatArray {
        val a=distance/length*(PI*2).toFloat();val radius=110f+sin(a*(2+circuit))*(12f+circuit*5)
        val hills=if(circuit==1)sin(a*4)*5f+sin(a*2)*2f else sin(a*3+circuit)*1.5f
        return floatArrayOf(cos(a)*(radius+lane),hills,sin(a)*(radius*.78f+lane))
    }
}

/** Cup: three circuits, 10-7-5-3-1 points per race, ties broken by best last-race finish. */
class Cup(val drivers:Int=5){
    companion object{val POINTS=intArrayOf(10,7,5,3,1)}
    val points=IntArray(drivers);var races=0;private set
    private var last=List(drivers){it}
    fun record(order:List<Int>){require(order.sorted()==List(drivers){it});order.forEachIndexed{p,d->points[d]+=POINTS.getOrElse(p){0}};last=order;races++}
    val done get()=races>=3
    fun standings():List<Int> = (0 until drivers).sortedWith(compareByDescending<Int>{points[it]}.thenBy{last.indexOf(it)})
}
