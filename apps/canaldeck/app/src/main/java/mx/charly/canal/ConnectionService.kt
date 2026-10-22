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
        fun channel(chat: Long) = "canaldeck-$chat"
    }
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    private val seen = NoticeBook()
    private val lines = linkedMapOf<Long, MutableList<String>>()
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
            .setContentTitle("CanalDeck · conexión activa").setContentText(status).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"Desconectar",stop).build()).build()
    }
    private fun updateConnection() { val s = session ?: return; manager.notify(1,connectionNotification(if(s.ready) s.network else s.status)) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if(intent?.action=="stop") stopSelf(); return START_NOT_STICKY }
    /**
     * Private phone notification only. No Bot API, TDLib forwarding, media transfer,
     * RS1000 calls or changes to the current Pick$ channel.
     */
    private fun pickNotice(s: TelegramSession, m: Message, org: Organization): Boolean {
        val mode = org.pickMode(m.chatId)
        if (mode == PickSourceMode.OFF || m.isOutgoing || m.sendingState != null ||
            m.selfDestructType != null) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false
        if (!manager.areNotificationsEnabled()) return false
        val chat = s.chats[m.chatId] ?: return false
        val text = when (val c = m.content) {
            is dev.g000sha256.tdl.dto.MessageText -> c.text.text
            is dev.g000sha256.tdl.dto.MessagePhoto -> c.caption.text
            is dev.g000sha256.tdl.dto.MessageDocument -> c.caption.text
            else -> ""
        }
        val hasPhoto = m.content is dev.g000sha256.tdl.dto.MessagePhoto ||
            (m.content is dev.g000sha256.tdl.dto.MessageDocument &&
                (m.content as dev.g000sha256.tdl.dto.MessageDocument).document.mimeType in
                setOf("image/jpeg", "image/png", "image/webp"))
        val timestamp = m.date.toLong()
        val age = System.currentTimeMillis() / 1000L - timestamp
        if (age !in 0L..(4L * 3600L)) return false
        val candidate = PickAlertPolicy.candidate(
            mode, chat.title, text, hasPhoto, timestamp, outgoing = m.isOutgoing,
            changed = m.editDate > 0
        ) ?: return false
        // Albums consist of multiple messages. This strictly consumes only the first
        // qualifying message and does not combine captions across unrelated posts.
        if (!org.acceptPick(m.chatId, m.id, timestamp, m.mediaAlbumId)) return false
        val protected = chat.hasProtectedContent || m.selfDestructType != null
        s.pickMessageLink(m.chatId, m.id, m.mediaAlbumId != 0L) { link ->
            if (session !== s || !s.ready) return@pickMessageLink
            val rendered = PickAlertPolicy.render(candidate, link)
            val id = "canaldeck-picks"
            manager.createNotificationChannel(NotificationChannel(
                id, "Picks seleccionados · CanalDeck", NotificationManager.IMPORTANCE_DEFAULT
            ))
            val open = Intent(this, CanalActivity::class.java)
                .setData(Uri.parse("canaldeck://chat/" + m.chatId))
                .putExtra("chat", m.chatId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // For protected sources the notice contains NO text/caption/image from
            // Telegram. The same is true for unprotected sources: only metadata.
            val body = if (protected) rendered else rendered
            manager.notify("picks-" + m.chatId, 3000,
                Notification.Builder(this, id)
                    .setSmallIcon(mx.charly.ui.R.drawable.ic_suite)
                    .setContentTitle("Pick externo · sin verificar")
                    .setContentText(candidate.heading)
                    .setStyle(Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .build())
        }
        return true
    }

    private fun notice(m: Message) {
        val s = session ?: return
        if (!s.ready || s.accountId == 0L || m.sendingState != null) return
        val org = Organization(this,s.accountId)
        if (pickNotice(s, m, org)) return
        val cal = Calendar.getInstance(); val now = cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE)
        val raw = TelegramSession.preview(m.content)
        val quiet = org.quietEnabled && DeckRules.quiet(now,org.quietStart(),org.quietEnd())
        if (!DeckRules.shouldNotify(org.mode(m.chatId),raw,org.words(m.chatId),m.isOutgoing,s.visibleChat==m.chatId,quiet)) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!manager.areNotificationsEnabled() || !seen.accept(m.chatId,m.id)) return
        val chat = s.chats[m.chatId]
        if (chat != null && m.id <= chat.lastReadInboxMessageId) return
        val channelId = channel(m.chatId)
        manager.createNotificationChannel(NotificationChannel(channelId,chat?.title?.take(80) ?: "Conversación ${m.chatId}",NotificationManager.IMPORTANCE_HIGH))
        val title = chat?.title ?: "Nueva publicación"
        val privateContent = chat?.hasProtectedContent == true || m.selfDestructType != null
        val preview = if(org.previews && !privateContent) raw.take(220) else "Mensaje nuevo · abrir CanalDeck"
        val recent = lines.getOrPut(m.chatId){mutableListOf()};recent.add(preview);while(recent.size>5)recent.removeAt(0)
        while(lines.size>30){val id=lines.keys.first();lines.remove(id);manager.cancel(id.toString(),2000)}
        val open = Intent(this,CanalActivity::class.java).setData(Uri.parse("canaldeck://chat/${m.chatId}"))
            .putExtra("chat",m.chatId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = Notification.InboxStyle().setBigContentTitle(title)
        recent.asReversed().forEach{style.addLine(it)}
        val time=System.currentTimeMillis()
        manager.notify(m.chatId.toString(),2000,Notification.Builder(this,channelId).setSmallIcon(mx.charly.ui.R.drawable.ic_suite)
            .setContentTitle(title).setContentText(preview).setStyle(style).setWhen(time).setShowWhen(true)
            .setSortKey((Long.MAX_VALUE-time).toString().padStart(19,'0')).setGroup("canaldeck-messages")
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN).setContentIntent(pending).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build())
        summary()
    }
    private fun summary() {
        if(lines.isEmpty()){manager.cancel(2);return}
        manager.notify(2,Notification.Builder(this,"connection").setSmallIcon(mx.charly.ui.R.drawable.ic_suite)
            .setContentTitle("CanalDeck").setContentText("Avisos de ${lines.size} conversaciones")
            .setGroup("canaldeck-messages").setGroupSummary(true).setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).build())
    }
    fun clear(chat: Long) { lines.remove(chat);manager.cancel(chat.toString(),2000);summary() }
    fun clearAll() { lines.keys.toList().forEach{manager.cancel(it.toString(),2000)};lines.clear();manager.cancel(2) }
    override fun onDestroy() { clearAll();session?.observers?.remove(observer);session?.stop();session=null;current=null;super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
