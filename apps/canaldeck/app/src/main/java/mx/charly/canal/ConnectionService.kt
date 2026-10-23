package mx.charly.canal

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import dev.g000sha256.tdl.dto.Message
import java.util.Calendar

/** Visible, opt-in connection. No FCM credentials, hidden receiver or restart after explicit stop. */
class ConnectionService : Service() {
    companion object {
        @Volatile var current: ConnectionService? = null; private set
        @Volatile var lastError: String? = null; private set
        fun channel(chat: Long, important: Boolean = false) = if (important) "canaldeck-important-$chat" else "canaldeck-$chat"
    }
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    private val seen = NoticeBook()
    private val lines = linkedMapOf<Long, MutableList<String>>()
    private val importantChats = linkedSetOf<Long>()
    private var session: TelegramSession? = null
    private val observer: () -> Unit = { updateConnection(); if (TelegramSession.current == null) stopSelf() }
    override fun onCreate() {
        super.onCreate(); current = this; lastError = null
        manager.createNotificationChannel(NotificationChannel("connection", "Conexión de CanalDeck", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, connectionNotification("Iniciando motor de Telegram"))
        try {
            session = TelegramSession.start(this)
            session?.onMessage = { notice(it) }; session?.onRead = { clear(it) }
            session?.observers?.add(observer); updateConnection()
        } catch (_: Throwable) { lastError = "No se pudo cargar el motor de Telegram. No se inició sesión."; stopSelf() }
    }
    private fun connectionNotification(status: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, CanalActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, ConnectionService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,"connection").setSmallIcon(mx.charly.ui.R.drawable.ic_suite)
            .setContentTitle("CanalDeck · conexión activa (no es un mensaje)").setContentText("Servicio funcionando · las publicaciones llegan en avisos separados si están activados").setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"Desconectar",stop).build()).build()
    }
    private fun updateConnection() { val s = session ?: return; manager.notify(1,connectionNotification(if(s.ready) "Conectado" else s.status)) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if(intent?.action=="stop") stopSelf(); return START_NOT_STICKY }
    private fun notice(m: Message) {
        val s = session ?: return
        if (!s.ready || s.accountId == 0L || m.sendingState != null) return
        val org = Organization(this,s.accountId)
        val cal = Calendar.getInstance(); val now = cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE)
        val raw = TelegramSession.preview(m.content)
        val quiet = org.quietEnabled && DeckRules.quiet(now,org.quietStart(),org.quietEnd())
        if (!DeckRules.shouldNotify(org.mode(m.chatId),raw,org.words(m.chatId),m.isOutgoing,s.visibleChat==m.chatId,quiet)) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!manager.areNotificationsEnabled() || !seen.accept(m.chatId,m.id)) return
        val chat = s.chats[m.chatId]
        if (chat != null && m.id <= chat.lastReadInboxMessageId) return
        val important = org.important(m.chatId)
        val channelId = channel(m.chatId,important)
        manager.createNotificationChannel(NotificationChannel(channelId,
            (if (important) "Prioritario · " else "")+(chat?.title?.take(70) ?: "Conversación ${m.chatId}"),
            if (important) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT))
        val title = chat?.title ?: "Nueva publicación"
        val privateContent = chat?.hasProtectedContent == true || m.selfDestructType != null
        val preview = if(org.previews && !privateContent) raw.take(220) else "Mensaje nuevo · abrir CanalDeck"
        val recent = lines.getOrPut(m.chatId){mutableListOf()};recent.add(preview);while(recent.size>5)recent.removeAt(0)
        if(important)importantChats.add(m.chatId)else importantChats.remove(m.chatId)
        while(lines.size>30){val id=lines.keys.first();lines.remove(id);importantChats.remove(id);manager.cancel(id.toString(),2000)}
        val open = Intent(this,CanalActivity::class.java).setData(Uri.parse("canaldeck://chat/${m.chatId}"))
            .putExtra("chat",m.chatId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = Notification.InboxStyle().setBigContentTitle(title)
        recent.asReversed().forEach{style.addLine(it)}
        val time=System.currentTimeMillis()
        manager.notify(m.chatId.toString(),2000,Notification.Builder(this,channelId).setSmallIcon(mx.charly.ui.R.drawable.ic_suite)
            .setContentTitle(title).setContentText(preview).setStyle(style).setWhen(time).setShowWhen(true)
            .setSortKey((Long.MAX_VALUE-time).toString().padStart(19,'0')).setGroup(if(important)"canaldeck-important"else"canaldeck-messages")
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN).setContentIntent(pending).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build())
        org.recordLocalNoticeAge(m.date.toLong(),time)
        summary()
    }
    private fun summary() {
        val normalCount=lines.keys.count { it !in importantChats }
        val importantCount=lines.keys.count { it in importantChats }
        fun publish(summaryId: Int, count: Int, group: String, title: String) {
            if(count==0){manager.cancel(summaryId);return}
            manager.notify(summaryId,Notification.Builder(this,"connection").setSmallIcon(mx.charly.ui.R.drawable.ic_suite)
                .setContentTitle(title).setContentText("Avisos de $count conversaciones")
                .setGroup(group).setGroupSummary(true).setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
        }
        publish(2,normalCount,"canaldeck-messages","CanalDeck")
        publish(3,importantCount,"canaldeck-important","CanalDeck · prioritarios")
    }
    fun clear(chat: Long) { lines.remove(chat);importantChats.remove(chat);manager.cancel(chat.toString(),2000);summary() }
    fun clearAll() { lines.keys.toList().forEach{manager.cancel(it.toString(),2000)};lines.clear();importantChats.clear();manager.cancel(2);manager.cancel(3) }
    override fun onDestroy() { clearAll();session?.observers?.remove(observer);session?.stop();session=null;current=null;super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
