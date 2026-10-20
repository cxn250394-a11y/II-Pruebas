package com.charly.alertcenter

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

/** Receives only notifications Android delivers; does not poll Telegram or sports servers. */
class AlertListenerService : NotificationListenerService() {
    companion object {
        const val ACTION_HISTORY_UPDATED = "com.charly.alertcenter.HISTORY_UPDATED"
    }

    private val io = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val settings = AlertSettings(applicationContext)
        val source = sbn.packageName
        if (!settings.isSelected(source)) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(" · ") { it.toString() }
            ?: ""
        if (title.isBlank() && body.isBlank()) return

        // Capture immutable strings here; StatusBarNotification is not retained across threads.
        val userId = sbn.user.hashCode()
        val key = sbn.key
        val posted = sbn.postTime
        val keywords = settings.keywords()
        io.execute {
            try {
                // Recheck the opt-in in case the user changed it while this job was queued.
                if (!AlertSettings(applicationContext).isSelected(source)) return@execute
                val inserted = AlertDatabase(applicationContext).use { store ->
                    store.insert(source, userId, key, title, body, posted,
                        AlertRules.hasPriorityWord(title, body, keywords))
                }
                if (inserted) {
                    sendBroadcast(Intent(ACTION_HISTORY_UPDATED).setPackage(packageName))
                }
            } catch (error: Exception) {
                Log.e("AlertCenter", "Unable to store notification", error)
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }
}
