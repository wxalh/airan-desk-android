package com.wxalh.airan_desk.util;

public final class AiranPathUtils {
    private AiranPathUtils() {
    }

    public static String safeFileName(String name) {
        String cleaned = name == null ? "" : name.replace('\\', '_').replace('/', '_').trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned.length() == 0 ? "airan_upload" : cleaned;
    }

    public static String joinPath(String base, String name) {
        if (base == null || base.length() == 0 || "home".equals(base)) {
            return name;
        }
        if (base.endsWith("/") || base.endsWith("\\")) {
            return base + name;
        }
        char separator = base.contains("\\") && !base.contains("/") ? '\\' : '/';
        return base + separator + name;
    }
}
