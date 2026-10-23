package mx.charly.kart

import org.junit.Assert.*
import org.junit.Test

/** JVM tests for the pure parts of the Android module: layout on several screens, control maths and frame statistics. */
class KartPureTest {
    // POCO F7-class 20:9, common 16:9, tablet-like 16:10 and 4:3 (pixels, landscape).
    private val screens=listOf(2712 to 1220,2400 to 1080,1920 to 1080,1280 to 800,2048 to 1536)

    @Test fun raceControlsAreLargeInsideScreenAndDoNotOverlap(){
        for((px,py) in screens){val (w,h)=HudLayout.virtualSize(px,py);val l=HudLayout.race(w,h);val zone=HudLayout.trackZone(w,h)
            l.forEach{(id,b)->assertTrue("$id small on ${px}x$py",b.w>=HudLayout.MIN_BUTTON&&b.h>=HudLayout.MIN_BUTTON*.8f)
                assertTrue("$id out on ${px}x$py",b.x>=0&&b.y>=0&&b.x+b.w<=w&&b.y+b.h<=h)
                assertFalse("$id covers track on ${px}x$py",b.overlaps(zone))}
            val v=l.values.toList();for(i in v.indices)for(j in i+1 until v.size)assertFalse("overlap ${px}x$py",v[i].overlaps(v[j]))
            assertTrue(zone.w>=400f&&zone.h>=250f)}
    }
    @Test fun menuAndPauseButtonsFitEveryScreen(){
        for((px,py) in screens){val (w,h)=HudLayout.virtualSize(px,py)
            for(map in listOf(HudLayout.menu(w,h),HudLayout.pause(w,h))){val v=map.values.toList()
                v.forEach{assertTrue(it.x>=0&&it.y>=0&&it.x+it.w<=w&&it.y+it.h<=h)}
                for(i in v.indices)for(j in i+1 until v.size)assertFalse("menu overlap ${px}x$py",v[i].overlaps(v[j]))}}
    }
    @Test fun virtualSizeKeepsAspect(){val (w,h)=HudLayout.virtualSize(2400,1080);assertEquals(600f,h,0f);assertEquals(1333.3f,w,.1f)
        val (w2,h2)=HudLayout.virtualSize(1024,768);assertEquals(1000f,w2,0f);assertEquals(750f,h2,.1f)}
    @Test fun deadZoneAndSensitivity(){
        val c=ControlSettings(sensitivity=1f,deadZone=.1f)
        assertEquals(0f,c.tiltSteer(.3f,0f),0f);assertTrue(c.tiltSteer(2f,0f)>0f);assertTrue(c.tiltSteer(-2f,0f)<0f)
        assertEquals(1f,c.tiltSteer(9f,0f),0f);assertEquals(0f,c.tiltSteer(Float.NaN,0f),0f)
        assertTrue(ControlSettings(sensitivity=2f,deadZone=.1f).tiltSteer(1.5f,0f)>c.tiltSteer(1.5f,0f))
        assertEquals(0f,c.tiltSteer(3f,3f),0f)
    }
    @Test fun settingsRoundTripAndClamp(){
        val c=ControlSettings(1.3f,.12f,true);assertEquals(c,ControlSettings.decode(c.encode()))
        assertEquals(ControlSettings(),ControlSettings.decode("basura"));assertEquals(ControlSettings(),ControlSettings.decode(null))
        var s=ControlSettings();repeat(30){s=s.withSensitivity(.1f)};assertEquals(2f,s.sensitivity,0f)
        repeat(40){s=s.withDeadZone(-.02f)};assertEquals(0f,s.deadZone,0f)
        assertEquals(1f,ControlSettings(Float.NaN,.1f).normalized().sensitivity,0f)
    }
    @Test fun buttonSteeringRampsAndReturns(){
        val c=ControlSettings();var s=0f;s=c.ramp(s,1f,1f/60);assertTrue(s>0f&&s<.2f)
        repeat(60){s=c.ramp(s,1f,1f/60)};assertEquals(1f,s,0f);repeat(30){s=c.ramp(s,0f,1f/60)};assertEquals(0f,s,0f)
        assertEquals(.5f,c.ramp(.5f,1f,Float.NaN),0f)
    }
    @Test fun perfMeterReportsMeasuredNumbers(){
        val p=PerfMeter(100);assertEquals(0f,p.averageFps(),0f)
        repeat(90){p.add(1f/60)};repeat(10){p.add(1f/20)};assertEquals(100,p.samples)
        assertTrue(p.averageFps() in 45f..51f);assertEquals(50f,p.worstMs(),.1f);assertEquals(10,p.hitches());assertEquals(50f,p.percentileMs(.95f),.1f)
        p.add(Float.NaN);p.add(-1f);p.add(5f);assertEquals(100L,p.total)
        repeat(100){p.add(1f/30)};assertEquals(30f,p.averageFps(),.1f);assertTrue(p.summary("x").startsWith("SANDRIFT_PERF x frames=100"))
    }
}
