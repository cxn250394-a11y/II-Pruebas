package mx.charly.canal

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Build
import dev.g000sha256.tdl.dto.Message

/** Visible connection, optional per-chat notices. No boot receiver or hidden collection. */
class ConnectionService:Service(){
 private val connectedAt=System.currentTimeMillis()/1000
 private val seen=LinkedHashSet<String>()
 private val manager by lazy{getSystemService(NotificationManager::class.java)}
 override fun onCreate(){super.onCreate();manager.createNotificationChannel(NotificationChannel("connection","Conexión de Telegram",NotificationManager.IMPORTANCE_LOW))
  val open=PendingIntent.getActivity(this,0,Intent(this,CanalActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val stop=PendingIntent.getService(this,1,Intent(this,ConnectionService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
  startForeground(1,Notification.Builder(this,"connection").setSmallIcon(mx.charly.ui.R.drawable.ic_suite).setContentTitle("CanalDeck conectado").setContentText("Cliente de Telegram independiente · conexión visible").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Desconectar",stop).build()).build())
  try{TelegramSession.start(this).onMessage={notice(it)}}catch(_:Throwable){stopSelf()}
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{if(intent?.action=="stop")stopSelf();return START_NOT_STICKY}
 private fun notice(message:Message){
  if(message.isOutgoing||message.date.toLong()<connectedAt-3)return
  val key="${message.chatId}:${message.id}";if(!seen.add(key))return;while(seen.size>500)seen.remove(seen.first())
  val prefs=getSharedPreferences("organization",MODE_PRIVATE);if(message.chatId.toString() !in (prefs.getStringSet("notify",emptySet())?:emptySet()))return
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
  val channel="chat_${message.chatId}";manager.createNotificationChannel(NotificationChannel(channel,"Canal ${message.chatId}",NotificationManager.IMPORTANCE_HIGH))
  val chat=TelegramSession.current?.chats?.get(message.chatId)
  val open=PendingIntent.getActivity(this,message.chatId.hashCode(),Intent(this,CanalActivity::class.java).putExtra("chat",message.chatId),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  manager.notify(message.chatId.toString(),2000,Notification.Builder(this,channel).setSmallIcon(mx.charly.ui.R.drawable.ic_suite).setContentTitle(chat?.title?:"Nuevo mensaje").setContentText("Publicación nueva · abrir CanalDeck").setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
 }
 override fun onDestroy(){TelegramSession.current?.stop();super.onDestroy()}
 override fun onBind(intent:Intent?):IBinder?=null
}
