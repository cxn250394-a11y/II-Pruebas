package mx.charly.audio

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.Build
import mx.charly.logic.Wave
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Experimental PCM-only route. Never silently falls back to the speaker or standard mixer. */
class UsbWave(private val context:Context,private val status:(String)->Unit){
 private val executor=Executors.newSingleThreadExecutor();private val generation=AtomicInteger()
 @Volatile private var track:AudioTrack?=null
 fun stop(){generation.incrementAndGet();try{track?.pause();track?.flush()}catch(_:Exception){}}
 fun close(){stop();executor.shutdown()}
 fun play(uri:Uri){stop();val token=generation.get()
  executor.execute{
   if(Build.VERSION.SDK_INT<34){status("Se necesita Android 14 o posterior para solicitar este modo USB.");return@execute}
   val am=context.getSystemService(AudioManager::class.java)
   val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
   var usb:AudioDeviceInfo?=null;var preferred=false;var audio:AudioTrack?=null
   try{context.contentResolver.openInputStream(uri)!!.use{input->
    val f=Wave.header(input)
    val encoding=when(f.bits){16->AudioFormat.ENCODING_PCM_16BIT;24->AudioFormat.ENCODING_PCM_24BIT_PACKED;32->AudioFormat.ENCODING_PCM_32BIT;else->error("PCM no compatible")}
    val channel=if(f.channels==1)AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
    usb=am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull{it.type==AudioDeviceInfo.TYPE_USB_DEVICE||it.type==AudioDeviceInfo.TYPE_USB_HEADSET}?:error("No se detectó salida USB; se cancela sin usar el altavoz")
    val device=usb!!
    val supported=am.getSupportedMixerAttributes(device).firstOrNull{it.mixerBehavior==AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT&&it.format.sampleRate==f.rate&&it.format.encoding==encoding&&it.format.channelMask==channel}?:error("Android no anuncia BIT_PERFECT para ${f.rate} Hz / ${f.bits} bits / ${f.channels} canales en este USB")
    require(generation.get()==token){"Cancelado"}
    preferred=am.setPreferredMixerAttributes(attributes,device,supported)
    require(preferred){"Android rechazó la preferencia bit-perfect"}
    val format=AudioFormat.Builder().setEncoding(encoding).setSampleRate(f.rate).setChannelMask(channel).build()
    val min=AudioTrack.getMinBufferSize(f.rate,channel,encoding);require(min>0){"Formato no reproducible por AudioTrack"}
    audio=AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(min,f.frameBytes*4096)).build()
    val player=audio!!;require(player.state==AudioTrack.STATE_INITIALIZED);require(player.setPreferredDevice(device)){"No se pudo fijar la salida USB"};track=player
    player.play()
    status("Solicitud BIT_PERFECT aceptada por Android · WAV ${f.rate} Hz / ${f.bits} bits. Sin DSP ni ajuste de volumen en la app. Pendiente medición física.")
    val bytes=ByteArray(f.frameBytes*2048);var left=f.dataBytes
    while(left>0&&generation.get()==token){
     require(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any{it.id==device.id}){"USB desconectado: reproducción detenida"}
     val route=player.routedDevice;if(route!=null)require(route.id==device.id){"La ruta cambió; no se permite fallback"}
     val desired=minOf(bytes.size.toLong(),left).toInt();var got=0
     while(got<desired){val n=input.read(bytes,got,desired-got);require(n>0){"Datos WAV truncados"};got+=n}
     var written=0;while(written<got&&generation.get()==token){val n=player.write(bytes,written,got-written,AudioTrack.WRITE_BLOCKING);require(n>0){"Fallo de escritura de audio"};written+=n};left-=got
    }
    if(generation.get()==token)status("WAV terminado. Solicitud de ruta bit-perfect utilizada; no certificada por medición externa.")
   }}catch(e:Exception){if(generation.get()==token)status(e.message?:"No se pudo abrir la ruta USB")}
   finally{try{audio?.stop()}catch(_:Exception){};audio?.release();if(track===audio)track=null;if(preferred&&usb!=null)try{am.clearPreferredMixerAttributes(attributes,usb!!)}catch(_:Exception){}}
  }
 }
 fun diagnostics():String {
  val am=context.getSystemService(AudioManager::class.java);val devices=am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
  return devices.joinToString("\n\n"){d->"${d.productName} · tipo ${d.type}\nFrecuencias anunciadas: ${d.sampleRates.joinToString()}"+(if(Build.VERSION.SDK_INT>=34&&(d.type==AudioDeviceInfo.TYPE_USB_DEVICE||d.type==AudioDeviceInfo.TYPE_USB_HEADSET)){
   val configs=am.getSupportedMixerAttributes(d).filter{it.mixerBehavior==AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT};"\nConfiguraciones BIT_PERFECT anunciadas: ${configs.size}\n"+configs.take(12).joinToString("\n"){"${it.format.sampleRate} Hz · encoding ${it.format.encoding} · canales ${it.format.channelCount}"}
  }else "\nEsta pantalla no verifica la señal de salida.")}.ifBlank{"Sin dispositivos de salida detectados"}
 }
}
