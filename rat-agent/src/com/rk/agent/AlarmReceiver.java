package com.rk.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            boolean consented = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                .getBoolean("consent_given", false);
            boolean userStopped = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                .getBoolean("user_stopped", false);

            if (!consented || userStopped) return;

            BootReceiver.startAgent(context);
            BootReceiver.scheduleRepeatingAlarm(context);
        } catch (Exception ignored) {}
    }
}
