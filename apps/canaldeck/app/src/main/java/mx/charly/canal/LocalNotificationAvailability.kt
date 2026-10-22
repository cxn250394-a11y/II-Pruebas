package mx.charly.canal

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** App permission and the individual chat channel are independent Android notification gates. */
object LocalNotificationAvailability {
    fun appAllowed(context: Context, manager: NotificationManager): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    fun allowed(context: Context, manager: NotificationManager, channelId: String): Boolean =
        appAllowed(context, manager) && manager.getNotificationChannel(channelId)?.importance?.let {
            it != NotificationManager.IMPORTANCE_NONE
        } == true
}
