package mx.charly.canal

import android.app.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
    private var relay: RelayCoordinator? = null
    private var localAccount = 0L
    private var localConnected = false
    private var localEpoch = Any()
    private val notificationChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Invalidate callbacks even if a setting is switched OFF and back ON before their RPC completes.
            localEpoch = Any(); updateLocalSession()
        }
    }
    private var notificationReceiverRegistered = false
    private val observer: () -> Unit = { relay?.onSessionChanged(); updateLocalSession(); updateConnection(); if (TelegramSession.current == null) stopSelf() }
    override fun onCreate() {
        super.onCreate(); current = this; lastError = null
        manager.createNotificationChannel(NotificationChannel("connection", "Conexión de CanalDeck", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, connectionNotification("Iniciando motor de Telegram"))
        val changes = IntentFilter(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED).apply {
            addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(notificationChanges, changes, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(notificationChanges, changes)
        notificationReceiverRegistered = true
        try {
            session = TelegramSession.start(this)
            relay = session?.let { RelayCoordinator(this, it) }
            session?.onMessage = { relay?.onLiveMessage(it); notice(it) }; session?.onRead = { clear(it) }
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
    private fun notificationsAllowed(chatId: Long): Boolean {
        if (!LocalNotificationAvailability.appAllowed(this, manager)) return false
        val id = channel(chatId)
        manager.createNotificationChannel(NotificationChannel(id,
            session?.chats?.get(chatId)?.title?.take(80) ?: "Conversación $chatId", NotificationManager.IMPORTANCE_HIGH))
        return LocalNotificationAvailability.allowed(this, manager, id)
    }
    private fun quiet(org: Organization): Boolean {
        val calendar = Calendar.getInstance()
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return org.quietEnabled && DeckRules.quiet(minute, org.quietStart(), org.quietEnd())
    }
    private fun updateLocalSession() {
        val s = session
        val connected = s != null && s === TelegramSession.current && s.ready && !s.closing && s.accountId > 0L && s.network == "En línea"
        if (!connected) {
            if (localConnected) localEpoch = Any()
            localConnected = false; return
        }
        val live = requireNotNull(s)
        if (!localConnected || localAccount != live.accountId) {
            localEpoch = Any()
            if (localAccount != live.accountId) Organization(this, live.accountId).recoverPickReservations()
            localAccount = live.accountId; localConnected = true
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { if(intent?.action=="stop") stopSelf(); return START_NOT_STICKY }
    private fun notice(m: Message) {
        val s = session ?: return
        if (s !== TelegramSession.current || !s.ready || s.closing || s.accountId == 0L || m.sendingState != null || m.isOutgoing) return
        updateLocalSession()
        val org = Organization(this, s.accountId)
        val quiet = quiet(org)
        val raw = TelegramSession.preview(m.content)
        val chat = s.chats[m.chatId]
        val mode = org.pickMode(m.chatId)
        if (mode != PickWatchMode.OFF) {
            if (quiet || !notificationsAllowed(m.chatId) ||
                (chat != null && m.id <= s.localInboxReadId(m.chatId))) return
            // A delayed sync after reconnect must not announce an old pick as new.
            val secondsOld = System.currentTimeMillis() / 1000L - m.date.toLong()
            if (secondsOld !in 0L..(4L * 3600L)) return
            val plain = when (val c = m.content) {
                is dev.g000sha256.tdl.dto.MessageText -> c.text.text
                is dev.g000sha256.tdl.dto.MessagePhoto -> c.caption.text
                else -> ""
            }
            val routing = RelayPreferences(this, s.accountId)
            val policy = routing.sourcePolicy(m.chatId)
            val candidate = PickBridgeRules.candidate(
                mode, chat?.title.orEmpty(), plain, m.date.toLong(),
                m.content is dev.g000sha256.tdl.dto.MessagePhoto, m.isOutgoing,
                m.editDate > 0, policy
            ) ?: return
            val expectedAccount = s.accountId
            val envelope = LocalPickEnvelope(m.chatId, m.id, m.date.toLong(), m.mediaAlbumId, mode, policy, candidate)
            fun guard(): LocalPickGuard {
                val current = s.recent[m.chatId to m.id]
                val unchanged = current === m && (m.chatId to m.id) !in s.removed && current.id == m.id &&
                    current.chatId == m.chatId && current.date == m.date && current.mediaAlbumId == m.mediaAlbumId &&
                    current.editDate == 0 && !current.isOutgoing && current.sendingState == null &&
                    TelegramSession.preview(current.content) == raw
                val liveOrg = Organization(this, expectedAccount)
                val liveRouting = RelayPreferences(this, expectedAccount)
                val liveChat = s.chats[m.chatId]
                return LocalPickGuard(
                    s.accountId, localEpoch, s === session && s === TelegramSession.current && this@ConnectionService === ConnectionService.current &&
                        s.ready && !s.closing && s.accountId == expectedAccount && localConnected,
                    liveOrg.pickMode(m.chatId), liveRouting.sourcePolicy(m.chatId), liveOrg.noticeRevision(), liveRouting.revision(),
                    liveOrg.pickEpoch(m.chatId), notificationsAllowed(m.chatId), quiet(liveOrg),
                    liveChat != null && m.id > s.localInboxReadId(m.chatId), unchanged)
            }
            LocalPickDispatcher(org).submit(envelope, ::guard, { callback -> s.messageLink(m, callback) }) { text, title, reserve ->
                publishNotice(m, text, title, { guard().let { it.validSession && it.notificationsAllowed && !it.quiet && it.unread && it.unchangedMessage } }, reserve)
            }
            return
        }
        if (!DeckRules.shouldNotify(org.mode(m.chatId), raw, org.words(m.chatId),
            m.isOutgoing, s.visibleChat == m.chatId, quiet)) return
        publishNotice(m, raw, null)
    }

    private fun publishNotice(m: Message, raw: String, pickTitle: String?, extraGuard: () -> Boolean = { true },
                              pickReserve: (() -> Boolean)? = null): LocalPickPublishResult {
        val s = session ?: return LocalPickPublishResult.BLOCKED
        if (!s.ready || s.closing || s.accountId == 0L || m.sendingState != null) return LocalPickPublishResult.BLOCKED
        val org = Organization(this, s.accountId)
        val chat = s.chats[m.chatId]
        fun allowed() = s === session && s === TelegramSession.current && s.ready && !s.closing &&
            notificationsAllowed(m.chatId) && !quiet(org) && extraGuard() &&
            (s.chats[m.chatId]?.let { m.id > s.localInboxReadId(m.chatId) } == true)
        if (!allowed()) return LocalPickPublishResult.BLOCKED
        val channelId = channel(m.chatId)
        val title = pickTitle ?: chat?.title ?: "Nueva publicación"
        val privateContent = chat?.hasProtectedContent == true || m.selfDestructType != null
        val preview = if (pickTitle != null) raw.take(400)
                      else if (org.previews && !privateContent) raw.take(220)
                      else "Mensaje nuevo · abrir CanalDeck"
        val recent = (lines[m.chatId].orEmpty() + preview).takeLast(5).toMutableList()
        val open = Intent(this, CanalActivity::class.java)
            .setData(Uri.parse("canaldeck://chat/" + m.chatId))
            .putExtra("chat", m.chatId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = Notification.InboxStyle().setBigContentTitle(title)
        recent.asReversed().forEach { style.addLine(it) }
        val time = System.currentTimeMillis()
        val notification = Notification.Builder(this, channelId)
                .setSmallIcon(mx.charly.ui.R.drawable.ic_suite)
                .setContentTitle(title).setContentText(preview).setStyle(style)
                .setWhen(time).setShowWhen(true)
                .setSortKey((Long.MAX_VALUE - time).toString().padStart(19, '0'))
                .setGroup("canaldeck-messages")
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .setContentIntent(pending).setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).build()
        val result = LocalPickPublicationBoundary.post(::allowed, pickReserve ?: { seen.accept(m.chatId, m.id) }) {
            manager.notify(m.chatId.toString(), 2000, notification)
        }
        if (result == LocalPickPublishResult.POSTED) {
            lines[m.chatId] = recent
            while (lines.size > 30) { val id = lines.keys.first(); lines.remove(id); manager.cancel(id.toString(), 2000) }
            if (pickTitle != null) relay?.onLocalNotified(m)
            summary()
        }
        return result
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
    override fun onDestroy() { localEpoch = Any(); if (notificationReceiverRegistered) unregisterReceiver(notificationChanges); clearAll();relay?.close();relay=null;session?.observers?.remove(observer);session?.stop();session=null;current=null;super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
