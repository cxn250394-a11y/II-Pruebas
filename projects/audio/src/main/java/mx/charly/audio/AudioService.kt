package mx.charly.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@androidx.annotation.OptIn(UnstableApi::class)
class AudioService:MediaSessionService(){
 companion object { @Volatile var sourceInfo="Formato fuente aún no disponible" }
 private var session:MediaSession?=null
 override fun onCreate(){super.onCreate()
  val p=ExoPlayer.Builder(this).build()
  p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
  p.setHandleAudioBecomingNoisy(true);p.setWakeMode(C.WAKE_MODE_LOCAL)
  p.addListener(object:Player.Listener{override fun onTracksChanged(tracks:Tracks){val f=p.audioFormat;sourceInfo=if(f==null)"Formato fuente aún no disponible" else "Fuente: ${f.sampleMimeType} · ${f.sampleRate} Hz · ${f.channelCount} canales\nSalida Android: no medida; no se afirma bit-perfect"}})
  val activity=PendingIntent.getActivity(this,0,Intent(this,AudioActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  session=MediaSession.Builder(this,p).setSessionActivity(activity).build()
 }
 override fun onGetSession(controllerInfo:MediaSession.ControllerInfo):MediaSession? = if(controllerInfo.packageName==packageName||controllerInfo.isTrusted)session else null
 override fun onDestroy(){session?.player?.release();session?.release();session=null;sourceInfo="Sin reproducción";super.onDestroy()}
}
