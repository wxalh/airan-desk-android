package com.wxalh.airan_desk.rtc;

import java.util.Locale;

final class StreamConfigPolicy {
    private StreamConfigPolicy() {
    }

    static String normalizeOneOf(String value, String fallback, String ... allowed) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        for (String item : allowed) {
            if (!item.equals(normalized)) continue;
            return normalized;
        }
        return fallback;
    }

    static String summary(int streamWidth, int streamHeight, int streamFps, String networkPath) {
        String resolution = streamWidth > 0 && streamHeight > 0 ? streamWidth + "x" + streamHeight : "original";
        return "auto " + resolution + "@" + streamFps + " network=" + networkPath;
    }
}
