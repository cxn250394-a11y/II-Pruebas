package mx.charly.logic

import kotlin.math.*

data class Driver(var distance:Float=0f,var lane:Float=0f,var speed:Float=0f,var boost:Float=0f,var shield:Float=0f,var stun:Float=0f)
/** Deterministic arcade simulation. Track progress and lateral steering are independent. */
class Race(val circuit:Int=0,val vehicle:Int=0,val difficulty:Int=1){
    val length=1000f+circuit*150f
    val drivers=MutableList(5){Driver(distance=-it*4f,lane=(it%3-1)*2.2f)}
    var countdown=3f;private set
    var elapsed=0f;private set
    var finished=false;private set
    var item="";private set
    var driftCharge=0f;private set
    private var driftWas=false
    private var pickup=-1
    val rank:Int get()=1+drivers.drop(1).count{it.distance>drivers[0].distance}
    val lap:Int get()=(max(0f,drivers[0].distance)/length).toInt().coerceAtMost(2)+1
    fun useItem(){if(finished||countdown>0)return;val me=drivers[0];when(item){"NITRO"->me.boost=3.2f;"SHIELD"->me.shield=6f;"PULSE"->drivers.drop(1).filter{abs(it.distance-me.distance)<80&&it.shield<=0}.forEach{it.stun=2f}};item=""}
    fun step(inputDt:Float,steer:Float,drift:Boolean,brake:Boolean){
        if(!inputDt.isFinite()||inputDt<=0||finished)return
        val dt=inputDt.coerceAtMost(0.05f)
        if(countdown>0){countdown=(countdown-dt).coerceAtLeast(0f);return}
        elapsed+=dt
        val turn=if(steer.isFinite())steer.coerceIn(-1f,1f) else 0f
        val me=drivers[0]
        me.lane=(me.lane+turn*dt*(if(drift)8.0f else 5.0f)).coerceIn(-8f,8f)
        if(drift&&abs(turn)>0.1f&&me.speed>12)driftCharge=(driftCharge+dt).coerceAtMost(2f)
        if(driftWas&&!drift){if(driftCharge>0.6f)me.boost=max(me.boost,driftCharge);driftCharge=0f};driftWas=drift
        drivers.forEachIndexed{i,d->
            d.boost=max(0f,d.boost-dt);d.shield=max(0f,d.shield-dt);d.stun=max(0f,d.stun-dt)
            val top=if(i==0)40f+vehicle*2f else 37f+difficulty*2+i*.45f
            var target=if(d.boost>0)top*1.5f else top
            if(abs(d.lane)>5.7f)target*=.45f
            if(i==0&&brake)target*=.3f
            if(d.stun>0&&d.shield<=0)target*=.35f
            d.speed+=(target-d.speed)*min(1f,dt*(if(target<d.speed)5 else 1.1f))
            d.distance+=max(0f,d.speed)*dt
            if(i>0)d.lane=sin(d.distance*.015f+i)*3.8f
        }
        val marker=floor(me.distance/180).toInt()
        if(marker>pickup){pickup=marker;if(abs(me.lane)<3.7f&&item.isEmpty())item=listOf("NITRO","SHIELD","PULSE")[(marker+circuit)%3]}
        if(me.distance>=length*3)finished=true
    }
    fun position(distance:Float,lane:Float=0f):FloatArray {
        val a=distance/length*(PI*2).toFloat();val radius=110f+sin(a*(2+circuit))*(12f+circuit*5)
        return floatArrayOf(cos(a)*(radius+lane),sin(a*3+circuit)*1.5f,sin(a)*(radius*.78f+lane))
    }
}
