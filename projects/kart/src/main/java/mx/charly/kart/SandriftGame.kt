package mx.charly.kart

import com.badlogic.gdx.*
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.*
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import mx.charly.logic.Cup
import mx.charly.logic.Race
import kotlin.math.*

/** Sandrift Karts v0.2: original procedural 3D arcade racer. Rules live in :logic Race; this class only renders and reads input. */
class SandriftGame(private val autostart:Boolean=false):ApplicationAdapter(){
 private lateinit var batch:ModelBatch;private lateinit var sprites:SpriteBatch;private lateinit var shapes:ShapeRenderer;private lateinit var font:BitmapFont
 private lateinit var camera:PerspectiveCamera;private lateinit var overlay:OrthographicCamera;private lateinit var viewport:ExtendViewport;private lateinit var env:Environment
 private lateinit var prefs:Preferences
 private var world:World?=null
 private var race=Race();private var track=0;private var vehicle=0;private var skin=0;private var mode=0;private var difficulty=1
 private var controls=ControlSettings();private var cup:Cup?=null;private var recorded=false
 private var inMenu=true;private var paused=false;private var menuTime=0f;private var time=0f;private var steer=0f;private var tiltZero=0f;private var best=Float.MAX_VALUE
 private val perf=PerfMeter();private var perfClock=0f
 private val touches=BooleanArray(10);private var buttons:Map<String,Box> =emptyMap()
 private val labels=mutableListOf<Triple<String,Box,Color>>();private val texts=mutableListOf<Triple<String,Vector2,Pair<Float,Color>>>()
 private val itemNames=mapOf("NITRO" to "TURBO","SHIELD" to "ESCUDO","PULSE" to "PULSO","MINE" to "MINA")
 private val rivalNames=listOf("TU","MAREA","CORAL","DUNA","BRISA")
 private val gold=Color.valueOf("FFD06A");private val teal=Color.valueOf("138D91");private val navy=Color.valueOf("193B50")
 private val tierColors=listOf(Color.WHITE,Color.valueOf("4FC3F7"),Color.valueOf("FF9F43"),Color.valueOf("C77DFF"))
 override fun create(){
  batch=ModelBatch();sprites=SpriteBatch();shapes=ShapeRenderer();font=BitmapFont();font.setUseIntegerPositions(false)
  camera=PerspectiveCamera(64f,Gdx.graphics.width.toFloat(),Gdx.graphics.height.toFloat()).apply{near=.3f;far=800f}
  overlay=OrthographicCamera();viewport=ExtendViewport(HudLayout.MIN_W,HudLayout.MIN_H,overlay);viewport.update(Gdx.graphics.width,Gdx.graphics.height,true)
  env=Environment().apply{set(ColorAttribute(ColorAttribute.AmbientLight,.62f,.66f,.74f,1f));add(DirectionalLight().set(.98f,.92f,.82f,-.5f,-1f,-.35f))}
  prefs=Gdx.app.getPreferences("sandrift_settings");controls=ControlSettings.decode(prefs.getString("controls",null))
  Gdx.input.setCatchKey(Input.Keys.BACK,true)
  rebuild();if(autostart)start(false)
 }
 private fun rebuild(){world?.dispose();world=World(race,track,vehicle,skin)}
 private fun newRace()=Race(track,vehicle,difficulty,seed=System.nanoTime() and 0xFFFF,rivals=if(mode==2)0 else 4)
 private fun start(isCup:Boolean){if(isCup){cup=Cup();track=0}else cup=null
  race=newRace();paused=false;recorded=false;time=0f;inMenu=false;rebuild();tiltZero=Gdx.input.accelerometerY;loadBest()}
 private fun loadBest(){best=Gdx.app.getPreferences("sandrift_records").getFloat("track_${track}_vehicle_$vehicle",Float.MAX_VALUE)}
 private fun restart(){race=newRace();paused=false;recorded=false;time=0f;rebuild()}
 private fun saveControls(){prefs.putString("controls",controls.encode());prefs.flush()}
 private fun point(p:Int):Vector2=viewport.unproject(Vector2(Gdx.input.getX(p).toFloat(),Gdx.input.getY(p).toFloat()))
 private fun held(id:String):Boolean{val r=buttons[id]?:return false;for(i in touches.indices)if(Gdx.input.isTouched(i)){val p=point(i);if(r.contains(p.x,p.y))return true};return false}
 private fun input(dt:Float){
  if(Gdx.input.isKeyJustPressed(Input.Keys.BACK)||Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){if(!inMenu&&!race.finished)setPaused(!paused)}
  for(i in touches.indices){val down=Gdx.input.isTouched(i);if(down&&!touches[i]){val p=point(i);buttons.entries.firstOrNull{it.value.contains(p.x,p.y)}?.let{clicked(it.key)}};touches[i]=down}
  val l=held("left")||Gdx.input.isKeyPressed(Input.Keys.LEFT);val r=held("right")||Gdx.input.isKeyPressed(Input.Keys.RIGHT)
  steer=if(l||r||!controls.tilt)controls.ramp(steer,(if(r)1f else 0f)-(if(l)1f else 0f),dt) else controls.tiltSteer(Gdx.input.accelerometerY,tiltZero)
  if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE))race.useItem()
 }
 private fun setPaused(p:Boolean){paused=p;race.paused=p}
 private fun clicked(id:String){when(id){
  "race"->start(mode==1)
  "track"->{track=(track+1)%3;race=Race(track,vehicle,difficulty);rebuild()}
  "vehicle"->vehicle=(vehicle+1)%3
  "skin"->{skin=(skin+1)%World.COLORS.size;rebuild()}
  "mode"->mode=(mode+1)%3
  "difficulty"->difficulty=(difficulty+1)%3
  "tilt"->{controls=controls.copy(tilt=!controls.tilt);tiltZero=Gdx.input.accelerometerY;saveControls()}
  "sens-"->{controls=controls.withSensitivity(-.1f);saveControls()};"sens+"->{controls=controls.withSensitivity(.1f);saveControls()}
  "dz-"->{controls=controls.withDeadZone(-.02f);saveControls()};"dz+"->{controls=controls.withDeadZone(.02f);saveControls()}
  "item"->race.useItem()
  "pause"->setPaused(true);"resume"->setPaused(false)
  "restart"->restart()
  "menu"->{inMenu=true;setPaused(false);cup=null}
  "next"->{track=(track+1)%3;restart();loadBest()}
 }}
 override fun render(){
  val raw=Gdx.graphics.deltaTime;val dt=raw.coerceIn(0f,.05f);input(dt);menuTime+=dt
  if(!inMenu&&!paused&&!race.finished){race.step(dt,steer,held("drift")||Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT),held("brake")||Gdx.input.isKeyPressed(Input.Keys.DOWN),
   (held("gas")||Gdx.input.isKeyPressed(Input.Keys.UP)||autostart));time+=dt}
  if(!inMenu&&!paused){perf.add(raw);perfClock+=raw;if(perfClock>=5f){perfClock=0f;Gdx.app.log("SandriftPerf",perf.summary("track=$track ${Gdx.graphics.width}x${Gdx.graphics.height}"))}}
  if(race.finished&&!recorded){recorded=true;cup?.record(race.standings())
   val p=Gdx.app.getPreferences("sandrift_records");val key="track_${track}_vehicle_$vehicle";if(race.elapsed<p.getFloat(key,Float.MAX_VALUE)){p.putFloat(key,race.elapsed);p.flush()}}
  val w=world!!;val c=World.SKY[track];Gdx.gl.glViewport(0,0,Gdx.graphics.width,Gdx.graphics.height);Gdx.gl.glClearColor(c.r,c.g,c.b,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
  val me=race.drivers[0]
  if(inMenu){camera.fieldOfView=60f;camera.position.set(cos(menuTime*.12f)*160f,85f,sin(menuTime*.12f)*160f);camera.up.set(Vector3.Y);camera.lookAt(0f,0f,0f)}else{
   val p=w.pos(me.distance,me.lane);val f=w.pos(me.distance+4,me.lane).sub(p).nor();val wanted=p.cpy().mulAdd(f,-11f).add(0f,5.6f,0f)
   if(time<.1f)camera.position.set(wanted) else camera.position.lerp(wanted,(dt*7).coerceAtMost(1f))
   camera.fieldOfView=MathUtils.lerp(camera.fieldOfView,if(me.boost>0)74f else 64f,(dt*4).coerceAtMost(1f))
   camera.up.set(Vector3.Y);camera.lookAt(p.cpy().mulAdd(f,9f).add(0f,1.5f,0f))}
  camera.update();batch.begin(camera);batch.render(w.scenery,env)
  race.boxes.forEachIndexed{i,b->if(b.active){val m=w.boxes[i];m.transform.setToTranslation(w.pos(b.distance,b.lane)).translate(0f,1.4f+sin(menuTime*2+i)*.25f,0f).rotate(Vector3.Y,menuTime*70+i*20).rotate(Vector3.X,25f);batch.render(m,env)}}
  w.obstacles.forEach{batch.render(it,env)}
  race.mines.take(w.mines.size).forEachIndexed{i,m->w.mines[i].transform.setToTranslation(w.pos(m.distance,m.lane)).translate(0f,.1f,0f);batch.render(w.mines[i],env)}
  race.drivers.forEachIndexed{i,d->drawKart(w,i,d.distance,d.lane,d.latVel,d.stun>0)}
  if(!inMenu)effects(w)
  batch.end();drawUi()
 }
 private fun drawKart(w:World,i:Int,distance:Float,lane:Float,lat:Float,stunned:Boolean){
  val p=w.pos(distance,lane);val slide=(lat*2.2f).coerceIn(-22f,22f)+(if(stunned)menuTime*720f%360f else 0f)
  val base=Matrix4().setToTranslation(p).rotate(Vector3.Y,w.yaw(distance,lane)+slide).translate(0f,.75f+sin(time*12+i)*.04f,0f)
  w.shadows[i].transform.set(base).translate(0f,-.72f,0f);batch.render(w.shadows[i],env)
  val k=w.cars[i];k[0].transform.set(base);k[1].transform.set(base).translate(0f,.5f,-.3f);k[2].transform.set(base).translate(0f,1.05f,-.3f);k[3].transform.set(base).translate(0f,1.3f,-.35f)
  k[4].transform.set(base).translate(0f,1.15f,.04f);k[5].transform.set(base).translate(.45f,-.1f,-1.7f);k[6].transform.set(base).translate(0f,-.2f,1.6f);k[7].transform.set(base).translate(0f,.75f,-1.45f)
  for(n in 0..3)k[8+n].transform.set(base).translate(if(n%2==0)-1.05f else 1.05f,-.35f,if(n<2)1f else -1f).rotate(Vector3.Z,90f).rotate(Vector3.Y,distance*45)
  k.forEach{batch.render(it,env)}
 }
 private fun effects(w:World){val me=race.drivers[0];val base=Matrix4().setToTranslation(w.pos(me.distance,me.lane)).rotate(Vector3.Y,w.yaw(me.distance,me.lane))
  if(me.boost>0){w.flame.transform.set(base).translate(0f,.7f,-2.3f).rotate(Vector3.X,-90f).scale(1f,1f+sin(menuTime*40)*.25f,1f);batch.render(w.flame,env)}
  val lvl=race.driftLevel;if(race.driftCharge>.05f){val col=tierColors[lvl]
   w.sparks.forEachIndexed{n,s->(s.materials[0].get(ColorAttribute.Emissive) as ColorAttribute).color.set(col);(s.materials[0].get(ColorAttribute.Diffuse) as ColorAttribute).color.set(col)
    val side=if(n%2==0)-1f else 1f;val j=((menuTime*37+n*13)%1f);s.transform.set(base).translate(side*1.1f,.15f+j*.5f,-1.5f-j*1.2f);batch.render(s,env)}}
 }
 private fun button(id:String,b:Box,label:String,color:Color=navy){labels+=Triple(label,b,color)}
 private fun text(s:String,x:Float,y:Float,scale:Float=1.4f,color:Color=Color.WHITE){texts+=Triple(s,Vector2(x,y),Pair(scale,color))}
 /** Batched overlay: one shape pass and one text pass per frame instead of begin/end per widget. */
 private fun flush(panels:List<Pair<Box,Color>>,extra:(ShapeRenderer)->Unit={}){
  Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA)
  shapes.begin(ShapeRenderer.ShapeType.Filled);panels.forEach{(b,c)->shapes.color=c;shapes.rect(b.x,b.y,b.w,b.h)}
  labels.forEach{(_,b,c)->shapes.setColor(c.r,c.g,c.b,.86f);shapes.rect(b.x,b.y,b.w,b.h);shapes.setColor(1f,1f,1f,.18f);shapes.rect(b.x,b.y+b.h-5f,b.w,5f)};extra(shapes);shapes.end()
  Gdx.gl.glDisable(GL20.GL_BLEND)
  sprites.begin();labels.forEach{(s,b,_)->font.color=Color.WHITE;font.data.setScale(1.45f);font.draw(sprites,s,b.x,b.cy+9f,b.w,Align.center,false)}
  texts.forEach{(s,p,st)->font.color=st.second;font.data.setScale(st.first);font.draw(sprites,s,p.x,p.y)};sprites.end();labels.clear();texts.clear()
 }
 private fun drawUi(){viewport.apply();overlay.update();sprites.projectionMatrix=overlay.combined;shapes.projectionMatrix=overlay.combined
  val W=viewport.worldWidth;val H=viewport.worldHeight;val panel=Color(.035f,.08f,.14f,.82f)
  if(inMenu){val m=HudLayout.menu(W,H);buttons=m
   text("SANDRIFT KARTS",60f,H-40f,3.2f,gold);text("v0.2 beta - kart arcade original, sin compras ni anuncios",62f,H-100f,1.05f)
   button("track",m["track"]!!,"Circuito: ${World.NAMES[track]}");button("vehicle",m["vehicle"]!!,"Vehiculo: ${Race.VEHICLES[vehicle].name}")
   button("skin",m["skin"]!!,"Color: ${skin+1}");button("mode",m["mode"]!!,"Modo: ${listOf("CARRERA","COPA 3 CIRCUITOS","CONTRARRELOJ")[mode]}")
   button("difficulty",m["difficulty"]!!,"Rivales: ${listOf("SUAVE","NORMAL","RAPIDO")[difficulty]}");button("tilt",m["tilt"]!!,if(controls.tilt)"INCLINACION" else "BOTONES")
   button("race",m["race"]!!,"INICIAR",teal)
   listOf("sens-" to "-","sens+" to "+","dz-" to "-","dz+" to "+").forEach{(id,s)->button(id,m[id]!!,s)}
   text("Sensibilidad ${"%.1f".format(controls.sensitivity)}",W-370f,m["sens-"]!!.y+100f,1.2f);text("Zona muerta ${(controls.deadZone*100).toInt()}%",W-370f,m["dz-"]!!.y+90f,1.2f)
   text("Volante a la izquierda, GAS a la derecha.",W-390f,H-470f,.95f);text("Derrapa en curva y suelta: azul, naranja, violeta.",W-390f,H-495f,.95f)
   flush(listOf(Box(35f,20f,570f,H-40f) to panel,Box(W-395f,H-445f,370f,355f) to panel));return}
  val L=HudLayout.race(W,H);buttons=L;val me=race.drivers[0]
  text(if(mode==2)"CONTRARRELOJ" else "POSICION ${race.rank}/${race.drivers.size}",36f,H-30f,1.7f,gold)
  text("VUELTA ${race.lap}/${Race.LAPS}   ${"%.1f".format(race.elapsed)} s",36f,H-66f,1.3f)
  if(best<Float.MAX_VALUE)text("Record ${"%.2f".format(best)} s",36f,H-92f,1f)
  text("${(me.speed*3.6f).toInt()} km/h",W/2-60f,H-30f,1.8f);text("${Gdx.graphics.framesPerSecond} FPS medidos",W/2-60f,H-64f,1f)
  button("left",L["left"]!!,"<");button("right",L["right"]!!,">");button("gas",L["gas"]!!,"GAS",teal);button("brake",L["brake"]!!,"FRENO",Color.valueOf("8A3B3B"))
  button("drift",L["drift"]!!,"DERRAPE",tierColors[race.driftLevel].cpy().lerp(navy,.45f));button("item",L["item"]!!,itemNames[race.item]?:"SIN OBJETO",if(race.item.isEmpty())Color.valueOf("344457") else Color.valueOf("B57D31"))
  button("pause",L["pause"]!!,"PAUSA")
  if(race.countdown>0)text(ceil(race.countdown).toInt().toString(),W/2-25f,H/2+90f,6f,gold) else if(race.elapsed<1f)text("YA!",W/2-60f,H/2+90f,5f,gold)
  if(me.boost>0)text("TURBO",W/2-70f,H-110f,2.2f,Color.valueOf("F6D56B"))
  if(abs(me.lane)>Race.ROAD)text("FUERA DE PISTA",W/2-110f,H/2+20f,1.4f,Color.valueOf("F4BF86"))
  val dl=L["drift"]!!;val charge=race.driftCharge/2.1f
  val panels=mutableListOf(Box(20f,H-115f,330f,105f) to panel,Box(W-200f,H-310f,180f,180f) to panel)
  if(paused||race.finished){buttons=overlayButtons(W,H);panels+=Box(W/2-260f,H/2-230f,520f,470f) to Color(.02f,.05f,.1f,.92f)}
  flush(panels){s->s.color=tierColors[race.driftLevel];s.rect(dl.x,dl.y+dl.h+6f,dl.w*charge.coerceAtMost(1f),10f);minimap(s,W-110f,H-220f)}
 }
 private fun minimap(s:ShapeRenderer,cx:Float,cy:Float){s.setColor(1f,1f,1f,.55f)
  for(i in 0 until 60){val a=race.position(race.length*i/60);val b=race.position(race.length*(i+1)/60);s.rectLine(cx+a[0]*.55f,cy+a[2]*.55f,cx+b[0]*.55f,cy+b[2]*.55f,3f)}
  race.drivers.forEachIndexed{i,d->val p=race.position(d.distance,d.lane);s.color=if(i==0)Color.WHITE else World.COLORS[i%5];s.circle(cx+p[0]*.55f,cy+p[2]*.55f,if(i==0)7f else 5f)}
 }
 private fun overlayButtons(W:Float,H:Float):Map<String,Box>{val o=HudLayout.pause(W,H);val x=W/2-230f
  if(race.finished){text("META - POSICION ${race.rank}",x,H/2+215f,2.2f,gold);text("Tiempo ${"%.2f".format(race.elapsed)} s",x,H/2+170f,1.3f)
   val c=cup;val order=if(c!=null)c.standings() else race.standings()
   order.take(5).forEachIndexed{n,d->text("${n+1}. ${rivalNames[d]}${if(c!=null)"  ${c.points[d]} pts" else ""}",x+(n%2)*230f,H/2+135f-(n/2)*26f,1.1f)}
   val nextOk=c!=null&&!c.done
   button(if(nextOk)"next" else "restart",o["resume"]!!,if(nextOk)"SIGUIENTE CIRCUITO" else if(c!=null)"COPA TERMINADA - REPETIR" else "REPETIR CARRERA",teal)
   button("menu",o["menu"]!!,"VOLVER AL MENU");return mapOf((if(nextOk)"next" else "restart") to o["resume"]!!,"menu" to o["menu"]!!)}
  text("PAUSA",x,H/2+215f,2.4f,gold);button("resume",o["resume"]!!,"CONTINUAR",teal);button("restart",o["restart"]!!,"REINICIAR");button("menu",o["menu"]!!,"VOLVER AL MENU");return o
 }
 override fun resize(width:Int,height:Int){camera.viewportWidth=width.toFloat();camera.viewportHeight=height.toFloat();camera.update();viewport.update(width,height,true)}
 override fun pause(){if(!inMenu&&!race.finished)setPaused(true);touches.fill(false)}
 override fun dispose(){world?.dispose();batch.dispose();sprites.dispose();shapes.dispose();font.dispose()}
}
