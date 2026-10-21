package mx.charly.audio

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import mx.charly.ui.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class AudioActivity:Screen(){
 private var controller:MediaController?=null
 private var future:com.google.common.util.concurrent.ListenableFuture<MediaController>?=null
 private val saved by lazy{LocalJson(this,"library.json")};private var library=JSONArray();private var healthy=true
 private val handler=Handler(Looper.getMainLooper());private val io=Executors.newSingleThreadExecutor()
 private lateinit var usb:UsbWave;private var statusView:TextView?=null;private var seek:SeekBar?=null;private var usbStatus="WAV USB experimental: no iniciado";private var screen="home"
 private val ticker=object:Runnable{override fun run(){val p=controller;statusView?.text=(p?.mediaMetadata?.title?.toString()?:"Sin pista")+"\n"+(if(p?.isPlaying==true)"Reproduciendo" else "En pausa")+" · ${minutes(p?.currentPosition?:0)}\n"+AudioService.sourceInfo
  val duration=p?.duration?:0;if(duration>0&&duration!=C.TIME_UNSET){seek?.max=(duration/1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt();seek?.progress=((p?.currentPosition?:0)/1000).toInt()};handler.postDelayed(this,700)}}
 override fun onCreate(state:Bundle?){super.onCreate(state);try{library=saved.read()}catch(_:Exception){healthy=false;toast("Biblioteca no legible; no será sobrescrita")};usb=UsbWave(applicationContext){s->runOnUiThread{if(!isDestroyed){usbStatus=s;if(screen=="usb")usbScreen()}}};home()}
 override fun onStart(){super.onStart();val f=MediaController.Builder(this,SessionToken(this,ComponentName(this,AudioService::class.java))).buildAsync();future=f
  f.addListener({try{if(!isDestroyed){controller=f.get();controller?.addListener(object:Player.Listener{override fun onPlayerError(error:PlaybackException){toast("No se pudo reproducir: ${error.errorCodeName}")}})}}catch(_:Exception){toast("No se pudo conectar el reproductor")}},mainExecutor);handler.post(ticker)
 }
 override fun onStop(){handler.removeCallbacks(ticker);usb.stop();future?.let{MediaController.releaseFuture(it)};future=null;controller=null;super.onStop()}
 override fun onDestroy(){usb.close();io.shutdown();super.onDestroy()}
 private fun minutes(ms:Long):String{val sec=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(sec/60,sec%60)}
 private fun base(title:String,subtitle:String){statusView=null;seek=null;layout(title,subtitle)}
 private fun home(){screen="home";base("AudioDeck","Música local + radio · sin anuncios propios")
  statusView=label("Conectando reproductor",15f,accent)
  seek=SeekBar(this).apply{setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,v:Int,user:Boolean){if(user)controller?.seekTo(v*1000L)};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){}})}.also{body.addView(it)}
  button("Reproducir / pausar",true){usb.stop();controller?.let{if(it.isPlaying)it.pause() else it.play()}}
  button("Anterior"){usb.stop();controller?.seekToPreviousMediaItem()};button("Siguiente"){usb.stop();controller?.seekToNextMediaItem()}
  button("Aleatorio: cambiar"){controller?.let{it.shuffleModeEnabled=!it.shuffleModeEnabled;toast(if(it.shuffleModeEnabled)"Aleatorio activado" else "Orden normal")}}
  button("Repetir: cambiar"){controller?.let{it.repeatMode=(it.repeatMode+1)%3;toast(listOf("Sin repetición","Repetir pista","Repetir cola")[it.repeatMode])}}
  button("Biblioteca local (${library.length()})"){libraryScreen()};button("Radio por Internet"){radio()};button("USB / bit-perfect experimental"){usbScreen()};button("TIDAL y Qobuz: estado de integración"){providers()}
  label("Reproducción normal usa Media3 / Android. No confundir calidad del archivo con señal de salida bit-perfect.",12f,soft)
 }
 private fun libraryScreen(){screen="library";base("Tu biblioteca","Archivos elegidos por ti; sin permiso de almacenamiento global")
  button("Volver al reproductor"){home()};button("Agregar archivos de audio",true){pick(20)}
  if(library.length()==0)card("Tu música local","Selecciona FLAC, MP3, WAV, AAC u otros formatos admitidos por tu dispositivo. Las selecciones quedan guardadas y forman una cola.")
  for(i in 0 until minOf(library.length(),1000)){val o=library.getJSONObject(i);card(o.getString("title"),"Toca para reproducir desde esta pista"){playLibrary(i)}}
  button("Vaciar lista local"){confirm("Vaciar biblioteca","Solo quita la lista; no borra tus archivos originales."){if(healthy){library=JSONArray();saved.write(library);libraryScreen()}}}
 }
 private fun pick(code:Int){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="audio/*";addCategory(Intent.CATEGORY_OPENABLE);if(code==20)putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)},code)}
 private fun item(o:JSONObject)=MediaItem.Builder().setMediaId(o.getString("uri")).setUri(o.getString("uri")).setMediaMetadata(MediaMetadata.Builder().setTitle(o.getString("title")).build()).build()
 private fun playLibrary(index:Int){val p=controller?:run{toast("Espera a que conecte el servicio de audio");return};usb.stop();p.setMediaItems((0 until library.length()).map{item(library.getJSONObject(it))},index,0);p.prepare();p.play();home()}
 private fun playUrl(name:String,url:String){val uri=Uri.parse(url);if(uri.scheme!="https"||uri.host.isNullOrBlank()||uri.userInfo!=null){toast("Solo streams HTTPS sin credenciales en URL");return};usb.stop();val p=controller?:return;p.setMediaItem(MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(name).build()).build());p.prepare();p.play();home()}
 @Deprecated("Activity compatibility") override fun onActivityResult(request:Int,result:Int,data:Intent?){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return
  if(request==21){val uri=data.data?:return;confirm("Prueba USB bit-perfect","Solo WAV PCM16/24/32. Baja el volumen en tu DAC antes de probar: esta ruta no atenúa digitalmente. Se detiene al salir de la app y no cambia al altavoz si falla."){controller?.pause();usbScreen();usb.play(uri)};return}
  if(request!=20||!healthy)return
  val uris=if(data.clipData!=null)(0 until data.clipData!!.itemCount).map{data.clipData!!.getItemAt(it).uri}else listOfNotNull(data.data)
  try{val next=JSONArray(library.toString());val known=(0 until next.length()).map{next.getJSONObject(it).getString("uri")}.toMutableSet()
   for(uri in uris.take(2000)){if(next.length()>=2000)break;if(!known.add(uri.toString()))continue
    val flags=data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
    contentResolver.takePersistableUriPermission(uri,flags)
    val title=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else "Audio"}?:"Audio"
    next.put(JSONObject().put("uri",uri.toString()).put("title",title))
   };saved.write(next);library=next;libraryScreen()
  }catch(_:Exception){toast("No se pudo conservar acceso a todos los archivos; biblioteca anterior intacta")}
 }
 private fun radio(){screen="radio";base("Radio por Internet","Directorio Radio Browser · solo streams HTTPS")
  button("Volver"){home()};val name=field("Buscar estación","México");val results=column();body.addView(results)
  button("Buscar",true){val q=name.text.toString();io.execute{try{val data=RadioDirectory.search(q);runOnUiThread{if(!isDestroyed&&screen=="radio"){
   results.removeAllViews();var count=0
   for(i in 0 until data.length()){val o=data.getJSONObject(i);val url=o.optString("url_resolved");if(!url.startsWith("https://"))continue;count++
    results.addView(android.widget.Button(this).apply{text=o.optString("name")+" · "+o.optString("codec")+" "+o.optInt("bitrate")+" kbps";isAllCaps=false;setOnClickListener{playUrl(o.optString("name"),url)}})
   };if(count==0)results.addView(text("Sin streams HTTPS en esta búsqueda",14f,soft))
  }}}catch(e:Exception){runOnUiThread{if(!isDestroyed)toast(e.message?:"No respondió el directorio")}}}}
  button("Agregar URL HTTPS manual"){prompt("URL del stream autorizado"){url->playUrl("Radio manual",url.trim())}}
  label("La disponibilidad y los metadatos dependen de cada emisora. No se descargan ni redistribuyen sus emisiones.",12f,soft)
 }
 private fun usbScreen(){screen="usb";base("Salida USB","Prueba explícita; nunca simula una certificación")
  button("Volver"){usb.stop();home()};card("Estado",usbStatus)
  button("Inspeccionar capacidades del dispositivo",true){try{info("Dispositivos y formatos",usb.diagnostics())}catch(_:Exception){toast("No se pudieron consultar capacidades")}}
  button("Seleccionar WAV para prueba bit-perfect"){pick(21)};button("Detener prueba"){usb.stop();usbStatus="Prueba detenida";usbScreen()}
  label("Solo usa configuraciones BIT_PERFECT que Android anuncie para el USB y el formato exacto del WAV. FLAC/radio siguen por el reproductor normal. No incluye el controlador USB propietario de UAPP; hace falta medición real antes de afirmar equivalencia.",13f,soft)
 }
 private fun providers(){screen="providers";base("Servicios de música","No hay reproducción TIDAL/Qobuz integrada todavía")
  button("Volver"){home()}
  card("TIDAL · integración pendiente","Hace falta registrar nuestra aplicación, disponer del acceso permitido por TIDAL y utilizar su SDK de reproducción. Una cuenta de usuario no sustituye las credenciales de desarrollador. No se usan tokens extraídos de otra app.")
  card("Qobuz · integración pendiente","Qobuz debe emitir identificadores de aplicación válidos. Después se conecta tu cuenta y su suscripción. Esta APK no extrae secretos de UAPP ni desbloquea catálogos.")
  button("Portal oficial de TIDAL"){website("https://developer.tidal.com/")};button("Ecosistema oficial de Qobuz"){website("https://www.qobuz.com/gb-en/discover/apps-partners")}
 }
}
