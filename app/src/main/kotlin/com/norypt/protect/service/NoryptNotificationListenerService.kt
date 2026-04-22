package com.norypt.protect.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * B6 — Notification Listener Service stub.
 *
 * Registers the service so the user can grant Notification Access via the
 * Settings deep-link. Future work will hook lock/package events via
 * [onNotificationPosted] and [onNotificationRemoved].
 */
class NoryptNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) { /* hook for future */ }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* hook for future */ }
}
