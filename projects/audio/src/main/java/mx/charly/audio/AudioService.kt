package mx.charly.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@androidx.annotation.OptIn(UnstableApi::class)
class AudioService:MediaSessionService(){
 companion object {
   const val ACTION_SLEEP_TIMER = "mx.charly.audio.action.SET_SLEEP_TIMER"
   const val EXTRA_MINUTES = "minutes"
   @Volatile var sourceInfo = "Formato fuente aún no disponible"
   @Volatile private var sleepEndElapsed = 0L
   fun remainingSleepMillis(): Long = if (sleepEndElapsed <= 0) 0 else (sleepEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
   fun sleepActive(): Boolean = remainingSleepMillis() > 0
 }
 private val timerHandler = Handler(Looper.getMainLooper())
 private val endPlayback = Runnable {
   session?.player?.pause()
   sleepEndElapsed = 0L
 }
 private var session:MediaSession?=null
 override fun onCreate(){super.onCreate()
  val p=ExoPlayer.Builder(this).build()
  p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
  p.setHandleAudioBecomingNoisy(true);p.setWakeMode(C.WAKE_MODE_LOCAL)
  p.addListener(object:Player.Listener{override fun onTracksChanged(tracks:Tracks){val f=p.audioFormat;sourceInfo=if(f==null)"Formato fuente aún no disponible" else "Fuente: ${f.sampleMimeType} · ${f.sampleRate} Hz · ${f.channelCount} canales\nSalida Android: no medida; no se afirma bit-perfect"}})
  val activity=PendingIntent.getActivity(this,0,Intent(this,AudioActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  session=MediaSession.Builder(this,p).setSessionActivity(activity).build()
 }
 override fun onStartCommand(intent:Intent?, flags:Int, startId:Int):Int {
   if (intent?.action == ACTION_SLEEP_TIMER) {
     val minutes=intent.getIntExtra(EXTRA_MINUTES, -1)
     if (SleepTimerRules.validMinutes(minutes)) {
       timerHandler.removeCallbacks(endPlayback)
       sleepEndElapsed=0L
       if (minutes>0) {
         sleepEndElapsed=SleepTimerRules.deadline(SystemClock.elapsedRealtime(),minutes) ?: 0
         timerHandler.postDelayed(endPlayback,minutes*60_000L)
       }
     }
     return START_NOT_STICKY
   }
   return super.onStartCommand(intent,flags,startId)
 }
 override fun onGetSession(controllerInfo:MediaSession.ControllerInfo):MediaSession? = if(controllerInfo.packageName==packageName||controllerInfo.isTrusted)session else null
 override fun onDestroy(){timerHandler.removeCallbacks(endPlayback);sleepEndElapsed=0L;session?.player?.release();session?.release();session=null;sourceInfo="Sin reproducción";super.onDestroy()}
}
