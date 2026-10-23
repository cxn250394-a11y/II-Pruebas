package mx.charly.kart

/** Rolling frame-time statistics from real frame deltas. It reports measurements; it never assumes a frame rate. */
class PerfMeter(private val capacity:Int=600){
    private val frames=FloatArray(capacity);private var count=0;private var next=0
    var total=0L;private set
    fun add(seconds:Float){if(!seconds.isFinite()||seconds<=0f||seconds>1f)return;frames[next]=seconds;next=(next+1)%capacity;if(count<capacity)count++;total++}
    val samples get()=count
    private fun window()=frames.copyOf(count)
    fun averageFps():Float=if(count==0)0f else count/window().sum()
    fun percentileMs(p:Float):Float{if(count==0)return 0f;val s=window().sorted();return s[((s.size-1)*p).toInt().coerceIn(0,s.size-1)]*1000f}
    fun worstMs():Float=if(count==0)0f else window().max()*1000f
    /** Frames slower than 1.5 frame budgets at 60 Hz (25 ms): visible hitches. */
    fun hitches():Int=window().count{it>.025f}
    fun summary(label:String)="SANDRIFT_PERF $label frames=$count avgFps=${"%.1f".format(averageFps())} p95ms=${"%.1f".format(percentileMs(.95f))} worstMs=${"%.1f".format(worstMs())} hitches=${hitches()}"
}
