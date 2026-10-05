package com.rk.agent;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            startAgent(context);
            scheduleRepeatingAlarm(context);
        } catch (Exception ignored) {}
    }

    static void startAgent(Context context) {
        try {
            boolean consented = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                .getBoolean("consent_given", false);
            boolean stopped = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                .getBoolean("user_stopped", false);
            if (!consented || stopped) return;

            Intent svc = new Intent(context, AgentService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception ignored) {}
    }

    static void scheduleRepeatingAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            Intent i = new Intent(context, AlarmReceiver.class);
            i.setAction("com.rk.agent.WAKE");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(context, 1, i, flags);

            long triggerAt = SystemClock.elapsedRealtime() + 60 * 1000L;

            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                    } else {
                        am.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                    }
                } else if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                }
            } catch (Exception e) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            }
        } catch (Exception ignored) {}
    }
}
