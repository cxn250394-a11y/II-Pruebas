package mx.charly.kart

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.*
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.*
import com.badlogic.gdx.utils.viewport.StretchViewport
import mx.charly.logic.Race
import kotlin.math.*

/** Original procedural 3D arcade racer. No Vector Unit / Nintendo assets or code. */
class SandriftGame:ApplicationAdapter(){
 private lateinit var batch:ModelBatch;private lateinit var sprites:SpriteBatch;private lateinit var shapes:ShapeRenderer;private lateinit var font:BitmapFont
 private lateinit var camera:PerspectiveCamera;private lateinit var overlay:OrthographicCamera;private lateinit var viewport:StretchViewport;private lateinit var environment:Environment
 private var scenery:ModelCache?=null
 private val models=mutableListOf<Model>();private val carModels=mutableListOf<Model>();private val cars=mutableListOf<List<ModelInstance>>()
 private val boxes=mutableListOf<ModelInstance>();private lateinit var pickupModel:Model
 private var race=Race();private var track=0;private var vehicle=0;private var skin=0;private var mode=0;private var difficulty=1;private var tilt=false
 private var inMenu=true;private var paused=false;private var awarded=false;private var cupPoints=0;private var circuitInCup=0
 private var menuTime=0f;private var time=0f;private var steer=0f;private var tiltZero=0f
 private val touches=BooleanArray(10)
 private val buttons=linkedMapOf<String,Rectangle>()
 private val colors=listOf(Color.valueOf("FFCD5C"),Color.valueOf("61E2D0"),Color.valueOf("FC7193"),Color.valueOf("9C94FF"),Color.valueOf("F28E54"))
 private val sky=listOf(Color.valueOf("7DCADF"),Color.valueOf("F1B485"),Color.valueOf("233355"))
 private val names=listOf("COSTA TURQUESA","DUNAS DEL SOL","ISLA NOCTURNA")
 private val attrs=(VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
 private val builder=ModelBuilder()
 private fun mat(c:Color)=Material(ColorAttribute.createDiffuse(c),IntAttribute.createCullFace(GL20.GL_NONE))
 private fun keep(m:Model):Model=models.add(m).let{m}
 private fun box(w:Float,h:Float,d:Float,c:Color)=keep(builder.createBox(w,h,d,mat(c),attrs))
 private fun sphere(w:Float,h:Float,d:Float,c:Color)=keep(builder.createSphere(w,h,d,8,6,mat(c),attrs))
 private fun cylinder(w:Float,h:Float,d:Float,c:Color)=keep(builder.createCylinder(w,h,d,8,mat(c),attrs))
 override fun create(){
  batch=ModelBatch();sprites=SpriteBatch();shapes=ShapeRenderer();font=BitmapFont();font.data.setScale(1.35f)
  camera=PerspectiveCamera(64f,Gdx.graphics.width.toFloat(),Gdx.graphics.height.toFloat());camera.near=.2f;camera.far=750f
  overlay=OrthographicCamera();viewport=StretchViewport(1000f,600f,overlay);viewport.update(Gdx.graphics.width,Gdx.graphics.height,true)
  environment=Environment().apply{set(ColorAttribute(ColorAttribute.AmbientLight,.68f,.72f,.78f,1f));add(DirectionalLight().set(.95f,.9f,.8f,-.5f,-1f,-.4f))}
  Gdx.input.setCatchKey(Input.Keys.BACK,true)
  buildWorld()
 }
 private fun pos(distance:Float,lane:Float=0f):Vector3{val p=race.position(distance,lane);return Vector3(p[0],p[1]+1.2f,p[2])}
 private fun buildWorld(){
  scenery?.dispose();models.forEach{it.dispose()};models.clear();cars.clear();boxes.clear();carModels.clear()
  val static=mutableListOf<ModelInstance>()
  val water=box(1200f,1f,1200f,if(track==2)Color.valueOf("10223F") else Color.valueOf("19A7BA"));static+=ModelInstance(water).apply{transform.setToTranslation(0f,-5f,0f)}
  val sand=if(track==1)Color.valueOf("D6985E") else Color.valueOf("D9C28B")
  val inner=cylinder(175f,3f,145f,sand);static+=ModelInstance(inner).apply{transform.setToTranslation(0f,-4.5f,0f)}
  builder.begin();val road=builder.part("asphalt",GL20.GL_TRIANGLES,attrs,mat(if(track==1)Color.valueOf("97704E") else Color.valueOf("3C5963")))
  val n=180
  for(i in 0 until n){val a=race.length*i/n;val b=race.length*(i+1)/n;road.rect(pos(a,-6f),pos(b,-6f),pos(b,6f),pos(a,6f),Vector3.Y)}
  val roadModel=keep(builder.end());static+=ModelInstance(roadModel)
  builder.begin();val edges=builder.part("banks",GL20.GL_TRIANGLES,attrs,mat(sand))
  for(i in 0 until n){val a=race.length*i/n;val b=race.length*(i+1)/n
   edges.rect(pos(a,-6f).add(0f,-.03f,0f),pos(b,-6f).add(0f,-.03f,0f),pos(b,-22f).apply{y=-3.5f},pos(a,-22f).apply{y=-3.5f},Vector3.Y)
   edges.rect(pos(a,6f).add(0f,-.03f,0f),pos(b,6f).add(0f,-.03f,0f),pos(b,23f).apply{y=-3.5f},pos(a,23f).apply{y=-3.5f},Vector3.Y)
  };static+=ModelInstance(keep(builder.end()))
  val marker=box(.3f,.07f,2.0f,Color.valueOf("E8E3CF"))
  for(i in 0 until 100){val a=race.length*i/100;val p=pos(a);val forward=pos(a+1).sub(p);val yaw=atan2(forward.x,forward.z)*MathUtils.radiansToDegrees;static+=ModelInstance(marker).apply{transform.setToTranslation(p).translate(0f,.05f,0f).rotate(Vector3.Y,yaw)}}
  val trunk=cylinder(.65f,7f,.65f,Color.valueOf("866148"));val leaf=box(1.1f,.24f,5.7f,if(track==1)Color.valueOf("6E8750") else Color.valueOf("438E62"))
  for(i in 0 until 46){val p=pos(race.length*i/46,if(i%2==0)17f else -17f);p.y=-1.5f
   static+=ModelInstance(trunk).apply{transform.setToTranslation(p).translate(0f,3.5f,0f).rotate(Vector3.Z,(i%3-1)*7f)}
   repeat(5){k->static+=ModelInstance(leaf).apply{transform.setToTranslation(p).translate(0f,6.7f,0f).rotate(Vector3.Y,k*72f+i*13).rotate(Vector3.X,18f).translate(0f,0f,2f)}}
  }
  val rock=keep(builder.createCone(24f,35f,25f,6,mat(if(track==2)Color.valueOf("474766") else Color.valueOf("849D86")),attrs))
  repeat(12){i->val a=i*PI*2/12;static+=ModelInstance(rock).apply{transform.setToTranslation((cos(a)*230).toFloat(),5f+(i%3)*3,(sin(a)*220).toFloat()).scale(1f+(i%2)*.8f,1f+(i%3)*.35f,1f)}}
  val post=box(.6f,7f,.6f,Color.WHITE);val arch=box(14f,1f,1f,Color.valueOf("F9CB59"));val start=pos(0f);val direction=pos(1f).sub(start);val yaw=atan2(direction.x,direction.z)*MathUtils.radiansToDegrees;val transform=Matrix4().setToTranslation(start).rotate(Vector3.Y,yaw)
  static+=ModelInstance(post).apply{this.transform.set(transform).translate(-6.7f,3.5f,0f)};static+=ModelInstance(post).apply{this.transform.set(transform).translate(6.7f,3.5f,0f)};static+=ModelInstance(arch).apply{this.transform.set(transform).translate(0f,7f,0f)}
  scenery=ModelCache().apply{begin();static.forEach{add(it)};end()}
  val wheel=cylinder(.68f,.42f,.68f,Color.valueOf("18242B"));val head=sphere(.65f,.65f,.65f,Color.valueOf("DDA77F"));val visor=box(.5f,.18f,.12f,Color.valueOf("27343B"));val exhaust=box(.3f,.3f,.4f,Color.DARK_GRAY)
  for(i in 0 until 5){val c=colors[if(i==0)skin else i];val body=box(if(vehicle==2&&i==0)2.3f else 1.9f,.65f,3f,c);val seat=box(1.25f,.5f,1.1f,Color.valueOf("223644"));val helmet=sphere(.75f,.65f,.75f,c)
   cars+=listOf(ModelInstance(body),ModelInstance(seat),ModelInstance(head),ModelInstance(helmet),ModelInstance(visor),ModelInstance(exhaust),ModelInstance(wheel),ModelInstance(wheel),ModelInstance(wheel),ModelInstance(wheel))
  }
  pickupModel=box(1.4f,1.4f,1.4f,Color.valueOf("F0D078"));repeat(6){boxes+=ModelInstance(pickupModel)}
 }
 private fun start(cup:Boolean=false){if(cup){circuitInCup=0;cupPoints=0;track=0};race=Race(track,vehicle,difficulty);paused=false;awarded=false;time=0f;inMenu=false;buildWorld();tiltZero=Gdx.input.accelerometerY}
 private fun restart(){race=Race(track,vehicle,difficulty);paused=false;awarded=false;time=0f}
 private fun point(pointer:Int):Vector2=viewport.unproject(Vector2(Gdx.input.getX(pointer).toFloat(),Gdx.input.getY(pointer).toFloat()))
 private fun isHeld(id:String):Boolean{val r=buttons[id]?:return false;for(i in touches.indices)if(Gdx.input.isTouched(i)&&r.contains(point(i)))return true;return false}
 private fun input(){
  if(Gdx.input.isKeyJustPressed(Input.Keys.BACK)||Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){if(!inMenu)paused=!paused}
  steer=if(tilt)((Gdx.input.accelerometerY-tiltZero)/4f).coerceIn(-1f,1f) else 0f
  if(isHeld("left")||Gdx.input.isKeyPressed(Input.Keys.LEFT))steer=-1f
  if(isHeld("right")||Gdx.input.isKeyPressed(Input.Keys.RIGHT))steer=1f
  for(i in touches.indices){val down=Gdx.input.isTouched(i);if(down&&!touches[i]){val p=point(i);buttons.entries.firstOrNull{it.value.contains(p)}?.let{clicked(it.key)}};touches[i]=down}
  if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE))race.useItem()
 }
 private fun clicked(id:String){when(id){
  "race"->start(mode==1)
  "track"->{track=(track+1)%3;race=Race(track,vehicle,difficulty);buildWorld()}
  "vehicle"->vehicle=(vehicle+1)%3
  "skin"->{skin=(skin+1)%colors.size;buildWorld()}
  "mode"->mode=(mode+1)%3
  "difficulty"->difficulty=(difficulty+1)%3
  "tilt"->{tilt=!tilt;tiltZero=Gdx.input.accelerometerY}
  "item"->race.useItem()
  "pause"->paused=!paused
  "resume"->paused=false
  "restart"->restart()
  "menu"->{inMenu=true;paused=false}
  "next"->{circuitInCup++;track=circuitInCup;race=Race(track,vehicle,difficulty);awarded=false;paused=false;time=0f;buildWorld()}
 }}
 override fun render(){val dt=Gdx.graphics.deltaTime.coerceIn(0f,.05f);input();menuTime+=dt
  if(!inMenu&&!paused&&!race.finished){race.step(dt,steer,isHeld("drift")||Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT),isHeld("brake")||Gdx.input.isKeyPressed(Input.Keys.DOWN));time+=dt}
  if(race.finished&&!awarded){awarded=true;val points=listOf(10,7,5,3,1)[race.rank-1];if(mode==1)cupPoints+=points
   val pref=Gdx.app.getPreferences("sandrift_records");val key="track_${track}_vehicle_$vehicle";val old=pref.getFloat(key,Float.MAX_VALUE);if(race.elapsed<old){pref.putFloat(key,race.elapsed);pref.flush()}}
  val c=sky[track];Gdx.gl.glViewport(0,0,Gdx.graphics.width,Gdx.graphics.height);Gdx.gl.glClearColor(c.r,c.g,c.b,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
  val me=race.drivers[0]
  if(inMenu){camera.position.set(cos(menuTime*.12f)*155f,82f,sin(menuTime*.12f)*155f);camera.lookAt(0f,0f,0f)}else{
   val p=pos(me.distance,me.lane);val forward=pos(me.distance+4,me.lane).sub(p).nor();val wanted=p.cpy().mulAdd(forward,-10f).add(0f,5f,0f)
   if(time<.1f)camera.position.set(wanted)else camera.position.lerp(wanted,(dt*7).coerceAtMost(1f));camera.up.set(Vector3.Y);camera.lookAt(p.cpy().mulAdd(forward,9f).add(0f,1.5f,0f))
  };camera.update();batch.begin(camera);scenery?.let{batch.render(it,environment)}
  val count=if(mode==2&&!inMenu)1 else 5
  repeat(count){i->val driver=race.drivers[i];val p=pos(driver.distance,driver.lane);val tangent=pos(driver.distance+2,driver.lane).sub(p);val yaw=atan2(tangent.x,tangent.z)*MathUtils.radiansToDegrees+(if(i==0&&isHeld("drift"))steer*18 else 0f)
   val base=Matrix4().setToTranslation(p).rotate(Vector3.Y,yaw).translate(0f,.75f+sin(time*12+i)*.045f,0f)
   val parts=cars[i];parts[0].transform.set(base);parts[1].transform.set(base).translate(0f,.55f,-.3f);parts[2].transform.set(base).translate(0f,1.1f,-.3f);parts[3].transform.set(base).translate(0f,1.32f,-.35f);parts[4].transform.set(base).translate(0f,1.15f,.02f);parts[5].transform.set(base).translate(.4f,-.15f,-1.7f)
   for(w in 0..3){parts[6+w].transform.set(base).translate(if(w%2==0)-1.05f else 1.05f,-.35f,if(w<2)1f else -1f).rotate(Vector3.Z,90f).rotate(Vector3.Y,driver.distance*45)}
   parts.forEach{batch.render(it,environment)}
  }
  boxes.forEachIndexed{i,b->b.transform.setToTranslation(pos(i*180f,0f)).translate(0f,2f+sin(menuTime*2+i)*.25f,0f).rotate(Vector3.Y,menuTime*55);batch.render(b,environment)}
  batch.end();drawUi()
 }
 private fun rect(id:String,x:Float,y:Float,w:Float,h:Float,label:String,color:Color=Color.valueOf("193B50")){
  buttons[id]=Rectangle(x,y,w,h);shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.color=color;shapes.rect(x,y,w,h);shapes.end();sprites.begin();font.color=Color.WHITE;font.data.setScale(1.3f);font.draw(sprites,label,x+12,y+h/2+8);sprites.end()
 }
 private fun words(s:String,x:Float,y:Float,scale:Float=1.4f,color:Color=Color.WHITE){sprites.begin();font.color=color;font.data.setScale(scale);font.draw(sprites,s,x,y);sprites.end()}
 private fun plate(x:Float,y:Float,w:Float,h:Float){Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.setColor(.035f,.08f,.14f,.88f);shapes.rect(x,y,w,h);shapes.end();Gdx.gl.glDisable(GL20.GL_BLEND)}
 private fun drawUi(){viewport.apply();overlay.update();sprites.projectionMatrix=overlay.combined;shapes.projectionMatrix=overlay.combined;buttons.clear()
  if(inMenu){plate(35f,35f,590f,530f);words("SANDRIFT",60f,520f,3.4f,Color.valueOf("FFD06A"));words("KARTS  /  3D ARCADE",63f,468f,1.8f)
   words("Prototipo original - 3 circuitos, 3 vehiculos, 5 pilotos",63f,430f,1.05f)
   rect("track",60f,350f,530f,48f,"Circuito: ${names[track]}")
   rect("vehicle",60f,290f,255f,48f,"Vehiculo: ${listOf("BUGGY","KART","RALLY")[vehicle]}")
   rect("skin",330f,290f,260f,48f,"Piloto / color: ${skin+1}")
   rect("mode",60f,230f,530f,48f,"Modo: ${listOf("CARRERA","COPA 3 CIRCUITOS","CONTRARRELOJ")[mode]}")
   rect("difficulty",60f,170f,255f,48f,"Rivales: ${listOf("SUAVE","NORMAL","RAPIDO")[difficulty]}")
   rect("tilt",330f,170f,260f,48f,if(tilt)"Control: INCLINACION" else "Control: BOTONES")
   rect("race",60f,85f,530f,62f,"INICIAR",Color.valueOf("138D91"))
   words("Aceleracion automatica. Derrapa y suelta para mini-turbo.",650f,140f,.9f)
   words("Objetos: NITRO, SHIELD, PULSE. Sin compras ni anuncios.",650f,115f,.9f)
   return
  }
  plate(20f,495f,340f,85f);words(if(mode==2)"CONTRARRELOJ" else "POSICION ${race.rank} / 5",36f,565f,1.65f)
  words("VUELTA ${race.lap}/3   ${"%.1f".format(race.elapsed)} s",36f,531f,1.3f)
  words("${(race.drivers[0].speed*3.6).toInt()} km/h",420f,565f,1.7f)
  words("${Gdx.graphics.framesPerSecond} FPS medidos",420f,535f,1f)
  rect("pause",835f,525f,140f,48f,"PAUSA")
  if(race.countdown>0)words(ceil(race.countdown).toInt().toString(),470f,350f,6f,Color.valueOf("FFD06A"))
  rect("left",25f,30f,120f,90f,"< IZQ");rect("right",160f,30f,120f,90f,"DER >")
  rect("brake",325f,30f,135f,68f,"FRENO");rect("drift",490f,30f,180f,68f,"DERRAPE")
  rect("item",725f,30f,250f,90f,race.item.ifEmpty{"SIN OBJETO"},if(race.item.isEmpty())Color.valueOf("344457")else Color.valueOf("B57D31"))
  if(race.driftCharge>.1f){shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.color=Color.valueOf("65E8DF");shapes.rect(490f,108f,180f*race.driftCharge/2,10f);shapes.end()}
  if(race.drivers[0].boost>0)words("TURBO",440f,450f,2.5f,Color.valueOf("F6D56B"))
  if(abs(race.drivers[0].lane)>5.7f)words("FUERA DE PISTA - REGRESA AL CENTRO",290f,400f,1.3f,Color.valueOf("F4BF86"))
  if(paused||race.finished){buttons.clear();plate(260f,140f,480f,340f)
   words(if(race.finished)"META - POSICION ${race.rank}" else "PAUSA",300f,440f,2.4f,Color.valueOf("FFD06A"))
   if(race.finished){words("Tiempo: ${"%.2f".format(race.elapsed)} s",300f,390f,1.5f);if(mode==1)words("Copa: $cupPoints puntos",300f,360f,1.4f)
    if(mode==1&&circuitInCup<2)rect("next",300f,275f,400f,58f,"SIGUIENTE CIRCUITO",Color.valueOf("138D91"))else rect("restart",300f,275f,400f,58f,"REPETIR CARRERA")
   }else rect("resume",300f,285f,400f,65f,"CONTINUAR",Color.valueOf("138D91"))
   rect("menu",300f,190f,400f,58f,"VOLVER AL MENU")
  }
 }
 override fun resize(width:Int,height:Int){camera.viewportWidth=width.toFloat();camera.viewportHeight=height.toFloat();camera.update();viewport.update(width,height,true)}
 override fun pause(){if(!inMenu)paused=true}
 override fun dispose(){scenery?.dispose();models.forEach{it.dispose()};batch.dispose();sprites.dispose();shapes.dispose();font.dispose()}
}
