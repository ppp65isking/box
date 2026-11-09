package com.ppp65isking.box.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ppp65isking.box.data.NotificationRepository
import com.ppp65isking.box.model.BoxNotification

/**
 * Collects other apps' notifications into Box's unified inbox.
 * Read-only: it never replies, dismisses, or acts on anything.
 *
 * The user grants this once in Settings → Notifications → Notification
 * access (Box shows a card that deep-links there). Until then the inbox
 * is empty and says so honestly.
 */
class BoxNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Seed with what's currently showing, so a restart isn't empty.
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        NotificationRepository.setAll(active.mapNotNull { toBoxNotification(it) })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        toBoxNotification(sbn)?.let { NotificationRepository.upsert(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        NotificationRepository.remove(notificationId(sbn))
    }

    private fun toBoxNotification(sbn: StatusBarNotification): BoxNotification? {
        // Never show Box's own notifications in its own inbox.
        if (sbn.packageName == packageName) return null
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        // Skip silent/empty shells (progress bars, foreground-service
        // placeholders with no text at all).
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null
        return BoxNotification(
            id = notificationId(sbn),
            packageName = sbn.packageName,
            appName = appLabel(sbn.packageName),
            title = title,
            text = text,
            postTime = sbn.postTime,
            // The notification's own tap intent — firing it opens the
            // exact screen in the source app (a deal screen, an order
            // tracker) instead of just launching the app.
            contentIntent = notification.contentIntent,
        )
    }

    private fun notificationId(sbn: StatusBarNotification): String =
        "${sbn.packageName}|${sbn.key}"

    private fun appLabel(packageName: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { packageName }
    }
}
