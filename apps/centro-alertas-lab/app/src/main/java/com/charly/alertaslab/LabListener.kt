package com.charly.alertaslab

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.charly.alertcenter.LabEngine
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Public notification text only. No history import, login, scraping, replies, cancellation or re-alerting. */
class LabListener : NotificationListenerService() {
    companion object {
        const val CHANGED = "com.charly.alertaslab.CHANGED"
        @Volatile var connected: Boolean = false
            private set
    }
    private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(256))
    override fun onCreate() {
        super.onCreate()
        worker.setRejectedExecutionHandler { _, _ -> LabPrefs(applicationContext).recordDropped() }
    }
    override fun onListenerConnected() { connected = true; changed() }
    override fun onListenerDisconnected() { connected = false; changed() }
    private fun changed() { sendBroadcast(Intent(CHANGED).setPackage(packageName)) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName ||
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val source = sbn.packageName
        if (!LabPrefs(this).permits(source)) return
        val profile = sbn.user.hashCode()
        val key = sbn.key
        val posted = sbn.postTime
        val received = System.currentTimeMillis()
        val parts = try { NotificationParts.extract(this, sbn.notification) } catch (_: Exception) { return }
        worker.execute {
            try {
                synchronized(LabStore.writeLock) {
                    if (!LabPrefs(applicationContext).permits(source)) return@synchronized
                    var inserted = false
                    LabStore(applicationContext).use { db ->
                        for (p in parts) inserted = db.insert(source, profile, "$key|${p.style}", p.title, p.body,
                            posted, received, p.timestamp, p.occurrence) || inserted
                    }
                    if (inserted) changed()
                }
            } catch (_: Exception) {
                // Do not log private text, SQL arguments or exception payloads.
                Log.w("AlertasLab", "Capture failed; notification content omitted")
            }
        }
    }
    override fun onDestroy() { connected = false; worker.shutdown(); super.onDestroy() }
}

data class NotificationPart(val title: String, val body: String, val timestamp: Long = 0,
                            val occurrence: Int = 0, val style: String = "plain")

object NotificationParts {
    fun extract(context: Context, n: Notification): List<NotificationPart> {
        val e = n.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val style = try { Notification.Builder.recoverBuilder(context, n).style as? Notification.MessagingStyle }
            catch (_: Exception) { null }
        val messages = style?.messages.orEmpty().takeLast(50)
        if (messages.isNotEmpty()) {
            val conversation = style?.conversationTitle?.toString()?.takeIf { it.isNotBlank() } ?: title
            val seen = mutableMapOf<String, Int>()
            return messages.mapNotNull { message ->
                val text = message.text?.toString().orEmpty()
                if (text.isBlank()) return@mapNotNull null
                val sender = message.senderPerson?.name?.toString().orEmpty()
                val body = if (sender.isBlank()) text else "$sender: $text"
                val key = LabEngine.digest(body, message.timestamp.toString())
                val occurrence = seen.getOrDefault(key, 0)
                seen[key] = occurrence + 1
                NotificationPart(conversation, body, message.timestamp, occurrence, "messaging")
            }
        }
        val body = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n") { it.toString() }?.takeIf { it.isNotBlank() }
            ?: e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return if (title.isBlank() && body.isBlank()) emptyList() else listOf(NotificationPart(title, body))
    }
}
