package com.rk.agent;

import android.webkit.ValueCallback;
import android.webkit.WebView;

public class WebViewHolder {
    public static WebView wv;

    public static String getUrl() {
        try {
            if (wv == null) return null;
            return wv.getUrl();
        } catch (Exception e) { return null; }
    }

    public static void eval(String js, ValueCallback<String> cb) {
        try {
            if (wv == null) return;
            wv.evaluateJavascript(js, cb);
        } catch (Exception ignored) {}
    }
}
