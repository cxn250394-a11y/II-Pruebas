package mx.charly.kart

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.*
import mx.charly.logic.Race
import kotlin.math.*

/** Procedural, original 3D scenery built from primitives. No external meshes, textures or third-party game assets. */
class World(val race:Race,val track:Int,val vehicle:Int,val skin:Int){
    companion object{
        val COLORS=listOf("FFCD5C","61E2D0","FC7193","9C94FF","F28E54").map{Color.valueOf(it)}
        val SKY=listOf("7DCADF","F1B485","233355").map{Color.valueOf(it)}
        val NAMES=listOf("COSTA TURQUESA","DUNAS DEL SOL","ISLA NOCTURNA")
        const val SEGMENTS=200
    }
    private val models=mutableListOf<Model>();private val b=ModelBuilder()
    private val attrs=(VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
    val scenery=ModelCache()
    val cars=mutableListOf<List<ModelInstance>>()
    val shadows=mutableListOf<ModelInstance>()
    val boxes=mutableListOf<ModelInstance>()
    val obstacles=mutableListOf<ModelInstance>()
    val mines=mutableListOf<ModelInstance>()
    lateinit var flame:ModelInstance
    val sparks=mutableListOf<ModelInstance>()
    private fun mat(c:Color,emissive:Boolean=false)=if(emissive)Material(ColorAttribute.createDiffuse(c),ColorAttribute.createEmissive(c),IntAttribute.createCullFace(GL20.GL_NONE))
        else Material(ColorAttribute.createDiffuse(c),IntAttribute.createCullFace(GL20.GL_NONE))
    private fun keep(m:Model)=m.also{models+=it}
    private fun box(w:Float,h:Float,d:Float,c:Color,e:Boolean=false)=keep(b.createBox(w,h,d,mat(c,e),attrs))
    private fun sphere(w:Float,h:Float,d:Float,c:Color,e:Boolean=false)=keep(b.createSphere(w,h,d,10,8,mat(c,e),attrs))
    private fun cyl(w:Float,h:Float,d:Float,c:Color)=keep(b.createCylinder(w,h,d,10,mat(c),attrs))
    fun pos(distance:Float,lane:Float=0f):Vector3{val p=race.position(distance,lane);return Vector3(p[0],p[1]+1.2f,p[2])}
    fun yaw(distance:Float,lane:Float=0f):Float{val t=pos(distance+1f,lane).sub(pos(distance,lane));return atan2(t.x,t.z)*MathUtils.radiansToDegrees}
    private fun strip(name:String,c:Color,from:Float,to:Float,lift:Float=0f,every:Int=1,offset:Int=0):Model{
        b.begin();val part=b.part(name,GL20.GL_TRIANGLES,attrs,mat(c))
        for(i in 0 until SEGMENTS){if(every>1&&(i+offset)%every!=0)continue;val a=race.length*i/SEGMENTS;val e=race.length*(i+1)/SEGMENTS
            part.rect(pos(a,from).add(0f,lift,0f),pos(e,from).add(0f,lift,0f),pos(e,to).add(0f,lift,0f),pos(a,to).add(0f,lift,0f),Vector3.Y)}
        return keep(b.end())}
    init{
        val night=track==2;val dunes=track==1;val list=mutableListOf<ModelInstance>()
        list+=ModelInstance(box(1400f,1f,1400f,Color.valueOf(if(night)"10223F" else "19A7BA"))).apply{transform.setToTranslation(0f,-6f,0f)}
        val sand=Color.valueOf(if(dunes)"D6985E" else "E2CC95")
        list+=ModelInstance(cyl(180f,4f,150f,sand)).apply{transform.setToTranslation(0f,-5f,0f)}
        list+=ModelInstance(strip("road",Color.valueOf(if(dunes)"8E6A4A" else if(night)"2C3646" else "3C5963"),-Race.ROAD,Race.ROAD))
        // Alternating kerbs mark the drivable edge; the wall line sits just outside them.
        val kerbA=Color.valueOf("E8473B");val kerbB=Color.valueOf("F4F1E6")
        for(side in listOf(-1f,1f)){val r0=side*Race.ROAD;val r1=side*Race.WALL
            list+=ModelInstance(strip("kerbA",kerbA,r0,r1,.04f,2,0));list+=ModelInstance(strip("kerbB",kerbB,r0,r1,.04f,2,1))}
        b.begin();val banks=b.part("banks",GL20.GL_TRIANGLES,attrs,mat(sand))
        for(i in 0 until SEGMENTS){val a=race.length*i/SEGMENTS;val e=race.length*(i+1)/SEGMENTS
            for(s in listOf(-1f,1f)){val w=s*Race.WALL;val far=s*24f
                banks.rect(pos(a,w),pos(e,w),pos(e,far).apply{y=min(y-2f,-3.5f)},pos(a,far).apply{y=min(y-2f,-3.5f)},Vector3.Y)}}
        list+=ModelInstance(keep(b.end()))
        val dash=box(.35f,.06f,2.4f,Color.valueOf("F2EAD0"))
        for(i in 0 until 120)if(i%2==0){val a=race.length*i/120;list+=ModelInstance(dash).apply{transform.setToTranslation(pos(a)).translate(0f,.05f,0f).rotate(Vector3.Y,yaw(a))}}
        val checkA=box(1.5f,.07f,1.5f,Color.WHITE);val checkB=box(1.5f,.07f,1.5f,Color.valueOf("1A1A1A"))
        for(row in 0..1)for(col in 0 until 8){val m=Matrix4().setToTranslation(pos(0f)).rotate(Vector3.Y,yaw(0f)).translate(-5.25f+col*1.5f,.06f,row*1.5f)
            list+=ModelInstance(if((row+col)%2==0)checkA else checkB).apply{transform.set(m)}}
        val trunk=cyl(.7f,7.5f,.7f,Color.valueOf("866148"));val leaf=box(1.2f,.25f,6f,Color.valueOf(if(dunes)"6E8750" else "3F9A62"))
        val lamp=sphere(1.1f,1.1f,1.1f,Color.valueOf("FFE7A0"),true);val pole=box(.25f,6f,.25f,Color.valueOf("5A6170"))
        for(i in 0 until 52){val d=race.length*i/52;val p=pos(d,if(i%2==0)16f else -16f);p.y=min(p.y-1.5f,-1f)
            if(night&&i%2==0){list+=ModelInstance(pole).apply{transform.setToTranslation(pos(d,9f)).translate(0f,2f,0f)};list+=ModelInstance(lamp).apply{transform.setToTranslation(pos(d,9f)).translate(0f,5.2f,0f)}}
            list+=ModelInstance(trunk).apply{transform.setToTranslation(p).translate(0f,3.7f,0f).rotate(Vector3.Z,(i%3-1)*7f)}
            repeat(5){k->list+=ModelInstance(leaf).apply{transform.setToTranslation(p).translate(0f,7.2f,0f).rotate(Vector3.Y,k*72f+i*13).rotate(Vector3.X,18f).translate(0f,0f,2.2f)}}}
        val rock=keep(b.createCone(24f,35f,25f,7,mat(Color.valueOf(if(night)"474766" else if(dunes)"C98B55" else "849D86")),attrs))
        repeat(14){i->val a=i*PI*2/14;list+=ModelInstance(rock).apply{transform.setToTranslation((cos(a)*245).toFloat(),5f+(i%3)*3,(sin(a)*230).toFloat()).scale(1f+(i%2)*.8f,1f+(i%3)*.4f,1f)}}
        val post=box(.7f,8f,.7f,Color.WHITE);val arch=box(16f,1.4f,1.2f,Color.valueOf("F9CB59"));val banner=box(12f,1.6f,.3f,Color.valueOf("138D91"))
        val t=Matrix4().setToTranslation(pos(0f)).rotate(Vector3.Y,yaw(0f))
        list+=ModelInstance(post).apply{transform.set(t).translate(-7.6f,4f,0f)};list+=ModelInstance(post).apply{transform.set(t).translate(7.6f,4f,0f)}
        list+=ModelInstance(arch).apply{transform.set(t).translate(0f,8f,0f)};list+=ModelInstance(banner).apply{transform.set(t).translate(0f,6.6f,0f)}
        scenery.begin();list.forEach{scenery.add(it)};scenery.end()
        val wheel=cyl(.72f,.45f,.72f,Color.valueOf("18242B"));val head=sphere(.62f,.62f,.62f,Color.valueOf("DDA77F"))
        val visor=box(.52f,.2f,.12f,Color.valueOf("27343B"));val pipe=box(.3f,.3f,.5f,Color.DARK_GRAY);val seat=box(1.25f,.5f,1.1f,Color.valueOf("223644"))
        val bumper=box(2.1f,.3f,.35f,Color.valueOf("2E3238"));val shadow=keep(b.createCylinder(2.6f,.02f,3.8f,12,Material(ColorAttribute.createDiffuse(0f,0f,0f,1f),BlendingAttribute(.35f)),attrs))
        for(i in race.drivers.indices){val c=COLORS[if(i==0)skin else i%COLORS.size];val v=if(i==0)vehicle else i%3
            val body=box(if(v==2)2.3f else 1.9f,if(v==0).75f else .6f,if(v==1)2.7f else 3.1f,c);val helmet=sphere(.78f,.68f,.78f,c)
            val wing=box(2.1f,.12f,.6f,c.cpy().lerp(Color.WHITE,.35f))
            cars+=listOf(ModelInstance(body),ModelInstance(seat),ModelInstance(head),ModelInstance(helmet),ModelInstance(visor),ModelInstance(pipe),ModelInstance(bumper),ModelInstance(wing),
                ModelInstance(wheel),ModelInstance(wheel),ModelInstance(wheel),ModelInstance(wheel))
            shadows+=ModelInstance(shadow)}
        val crate=box(1.5f,1.5f,1.5f,Color.valueOf("F0D078"),true);race.boxes.forEach{boxes+=ModelInstance(crate)}
        val barrel=cyl(1.9f,1.8f,1.9f,Color.valueOf(if(night)"8C5BD6" else "E07A3F"));race.obstacles.forEach{o->obstacles+=ModelInstance(barrel).apply{transform.setToTranslation(pos(o.distance,o.lane)).translate(0f,.9f,0f)}}
        val mine=sphere(1.1f,.6f,1.1f,Color.valueOf("C2413B"),true);repeat(8){mines+=ModelInstance(mine)}
        flame=ModelInstance(keep(b.createCone(.55f,1.6f,.55f,8,Material(ColorAttribute.createDiffuse(Color.valueOf("FFB347")),ColorAttribute.createEmissive(Color.valueOf("FF7A1A"))),attrs)))
        val spark=box(.22f,.22f,.22f,Color.WHITE,true);repeat(6){sparks+=ModelInstance(spark)}
    }
    fun dispose(){scenery.dispose();models.forEach{it.dispose()};models.clear()}
}
