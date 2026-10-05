package com.rk.agent;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Context ctx;
    private final Thread.UncaughtExceptionHandler prev;

    public CrashHandler(Context c, Thread.UncaughtExceptionHandler prev) {
        this.ctx = c.getApplicationContext();
        this.prev = prev;
    }

    private static File logFile(Context ctx) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs();
                return new File(dir, "agent_crash.txt");
            }
        } catch (Exception ignored) {}
        try {
            return new File(ctx.getFilesDir(), "agent_crash.txt");
        } catch (Exception e) {
            return new File(Environment.getDataDirectory(), "agent_crash.txt");
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            File out = logFile(ctx);
            FileWriter fw = new FileWriter(out, true);
            PrintWriter pw = new PrintWriter(fw);
            pw.println("=== CRASH "
                + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date()) + " ===");
            pw.println("Thread: " + t.getName());
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL
                + " API" + Build.VERSION.SDK_INT);
            e.printStackTrace(pw);
            pw.println();
            pw.flush();
            pw.close();
        } catch (Throwable ignored) {}

        try {
            if (prev != null) prev.uncaughtException(t, e);
        } catch (Throwable ignored) {}
    }

    public static String read(Context c) {
        try {
            File out = logFile(c);
            if (!out.exists()) return "no crash log";
            BufferedReader r = new BufferedReader(new FileReader(out));
            StringBuilder sb = new StringBuilder();
            String l; int lines = 0;
            while ((l = r.readLine()) != null) {
                sb.append(l).append("\n");
                if (++lines > 200) { sb.append("...(truncated)\n"); break; }
            }
            r.close();
            return sb.toString();
        } catch (Exception e) { return "err: " + e; }
    }

    public static void clear(Context c) {
        try {
            File out = logFile(c);
            if (out.exists()) out.delete();
        } catch (Exception ignored) {}
    }
}
