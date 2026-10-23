package mx.charly.kart

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/** Saved control preferences. Pure Kotlin so dead zone and sensitivity maths is unit-tested. */
data class ControlSettings(val sensitivity:Float=1f,val deadZone:Float=.08f,val tilt:Boolean=false){
    companion object{
        const val TILT_RANGE=4.5f
        fun decode(s:String?):ControlSettings{val p=s?.split(';')?:return ControlSettings()
            return runCatching{ControlSettings(p[0].toFloat(),p[1].toFloat(),p[2].toBoolean()).normalized()}.getOrDefault(ControlSettings())}
    }
    fun normalized()=ControlSettings(if(sensitivity.isFinite())sensitivity.coerceIn(.5f,2f) else 1f,if(deadZone.isFinite())deadZone.coerceIn(0f,.4f) else .08f,tilt)
    fun encode()="$sensitivity;$deadZone;$tilt"
    fun withSensitivity(delta:Float)=copy(sensitivity=Math.round((sensitivity+delta)*10)/10f).normalized()
    fun withDeadZone(delta:Float)=copy(deadZone=Math.round((deadZone+delta)*100)/100f).normalized()
    /** Accelerometer reading (m/s^2, landscape Y) relative to the calibrated zero, mapped to steering -1..1. */
    fun tiltSteer(raw:Float,zero:Float):Float{if(!raw.isFinite()||!zero.isFinite())return 0f
        val x=((raw-zero)/TILT_RANGE).coerceIn(-1f,1f);val m=abs(x);if(m<=deadZone)return 0f
        return sign(x)*min(1f,(m-deadZone)/(1f-deadZone)*sensitivity)}
    /** Buttons are digital; ramp the wheel so taps steer gently and holds reach full lock. */
    fun ramp(current:Float,target:Float,dt:Float):Float{if(!dt.isFinite()||dt<=0f)return current
        val rate=(if(target==0f)8f else 4.5f)*sensitivity;val d=target-current
        return if(abs(d)<=rate*dt)target else current+sign(d)*rate*dt}
}
