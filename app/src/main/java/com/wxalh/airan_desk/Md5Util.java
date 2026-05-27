package com.wxalh.airan_desk;

import java.security.MessageDigest;
import java.util.Locale;

public final class Md5Util {
    private Md5Util() {
    }

    public static String md5Upper(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format(Locale.US, "%02X", b & 0xFF));
            }
            return builder.toString();
        }
        catch (Exception e) {
            return "";
        }
    }
}
