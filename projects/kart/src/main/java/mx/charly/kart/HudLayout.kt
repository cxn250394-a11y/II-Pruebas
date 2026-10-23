package mx.charly.kart

/** Pure layout (no libGDX), so it can be unit-tested on several aspect ratios. Units: virtual pixels, origin bottom-left. */
data class Box(val x:Float,val y:Float,val w:Float,val h:Float){
    fun contains(px:Float,py:Float)=px>=x&&px<=x+w&&py>=y&&py<=y+h
    fun overlaps(o:Box)=x<o.x+o.w&&o.x<x+w&&y<o.y+o.h&&o.y<y+h
    val cx get()=x+w/2;val cy get()=y+h/2
}
object HudLayout{
    const val MIN_W=1000f;const val MIN_H=600f;const val MIN_BUTTON=90f;const val M=24f
    /** Virtual size produced by an ExtendViewport(1000x600) on a screen of the given pixels. */
    fun virtualSize(px:Int,py:Int):Pair<Float,Float>{val a=px.toFloat()/py.coerceAtLeast(1)
        return if(a>=MIN_W/MIN_H)Pair(MIN_H*a,MIN_H) else Pair(MIN_W,MIN_W/a)}
    /** Centre of the screen where the road and the kart are drawn; race controls must stay out of it. */
    fun trackZone(w:Float,h:Float)=Box(w*.25f,200f,w*.5f,h-310f)
    fun race(w:Float,h:Float):Map<String,Box> = linkedMapOf(
        "left" to Box(M,M,140f,150f),
        "right" to Box(M+156f,M,140f,150f),
        "item" to Box(M,190f,210f,90f),
        "gas" to Box(w-M-160f,M,160f,150f),
        "brake" to Box(w-M-160f,190f,160f,90f),
        "drift" to Box(w-M-160f-176f,M,160f,110f),
        "pause" to Box(w-M-150f,h-M-80f,150f,80f))
    fun pause(w:Float,h:Float):Map<String,Box>{val x=w/2-220f;val y=h/2-150f
        return linkedMapOf("resume" to Box(x,y+150f,440f,90f),"restart" to Box(x,y+50f,440f,90f),"menu" to Box(x,y-50f,440f,90f))}
    fun menu(w:Float,h:Float):Map<String,Box>{val x=60f;val top=h-200f
        return linkedMapOf(
            "track" to Box(x,top,530f,64f),"vehicle" to Box(x,top-76f,260f,64f),"skin" to Box(x+270f,top-76f,260f,64f),
            "mode" to Box(x,top-152f,530f,64f),"difficulty" to Box(x,top-228f,260f,64f),"tilt" to Box(x+270f,top-228f,260f,64f),
            "race" to Box(x,M+16f,530f,96f),
            "sens-" to Box(w-370f,top-76f,150f,64f),"sens+" to Box(w-200f,top-76f,150f,64f),
            "dz-" to Box(w-370f,top-228f,150f,64f),"dz+" to Box(w-200f,top-228f,150f,64f))}
}
