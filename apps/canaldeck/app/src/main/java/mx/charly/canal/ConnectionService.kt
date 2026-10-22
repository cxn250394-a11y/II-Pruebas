package mx.charly.canal

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import dev.g000sha256.tdl.dto.*
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
    private val watchedAlbums = NoticeBook()
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
    private fun notice(m: Message) {
        val s = session ?: return
        if (!s.ready || s.accountId == 0L || m.sendingState != null) return
        val org = Organization(this,s.accountId)
        val cal = Calendar.getInstance(); val now = cal.get(Calendar.HOUR_OF_DAY)*60+cal.get(Calendar.MINUTE)
        val raw = TelegramSession.preview(m.content)
        val quiet = org.quietEnabled && DeckRules.quiet(now,org.quietStart(),org.quietEnd())
        val watch = org.pickWatchMode(m.chatId)
        if (watch != PickWatchMode.OFF) {
            // Owner-selected chat ID; matching a title alone never grants access.
            val selected = s.chats[m.chatId] ?: return
            if (selected.type !is ChatTypeSupergroup ||
                (watch == PickWatchMode.DIRECT_CHANNEL && !(selected.type as ChatTypeSupergroup).isChannel)) return
            if (quiet || s.visibleChat == m.chatId) return
            val body = TelegramSession.pickText(m.content)
            val hasImage = m.content is MessagePhoto || m.content is MessageDocument
            val plan = PickWatchRules.propose(
                watch, selected.title, body, hasImage, m.date.toLong(),
                System.currentTimeMillis() / 1000, m.isOutgoing, m.editDate > 0
            ) ?: return
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            if (!manager.areNotificationsEnabled() || m.id <= selected.lastReadInboxMessageId) return
            if (!seen.accept(m.chatId, m.id)) return
            if (m.mediaAlbumId != 0L && !watchedAlbums.accept(m.chatId, m.mediaAlbumId)) return
            s.linkToOriginal(m) { link ->
                if (session === s && s.ready && org.pickWatchMode(m.chatId) == watch) {
                    postPickNotice(m, plan, link)
                }
            }
            return // Curated watch never falls through to broad keyword/all-chat rules.
        }
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
    private fun postPickNotice(m: Message, plan: PickWatchNotice, link: String?) {
        val s = session ?: return
        val chat = s.chats[m.chatId] ?: return
        val channelId = channel(m.chatId)
        manager.createNotificationChannel(NotificationChannel(channelId, chat.title.take(80),
            NotificationManager.IMPORTANCE_HIGH))
        val source = plan.render(link)
        val recent = lines.getOrPut(m.chatId) { mutableListOf() }
        recent.add(source)
        while (recent.size > 5) recent.removeAt(0)
        while (lines.size > 30) {
            val oldest = lines.keys.first()
            lines.remove(oldest)
            manager.cancel(oldest.toString(), 2000)
        }
        val open = Intent(this, CanalActivity::class.java)
            .setData(Uri.parse("canaldeck://chat/${m.chatId}"))
            .putExtra("chat", m.chatId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(m.chatId.toString(), 2000,
            Notification.Builder(this, channelId)
                .setSmallIcon(mx.charly.ui.R.drawable.ic_suite)
                .setContentTitle(plan.heading)
                .setContentText("Hora original: ${plan.originalTime}")
                .setStyle(Notification.BigTextStyle().bigText(source))
                .setWhen(m.date.toLong() * 1000L)
                .setShowWhen(true)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setGroup("canaldeck-messages")
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build())
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
