package com.wxalh.airan_desk.rtc;

import java.util.Map;

final class RtcStatsUtils {
    private RtcStatsUtils() {
    }

    static String stringMember(Map<String, Object> members, String key) {
        Object value = members.get(key);
        return value == null ? "" : value.toString();
    }

    static long longMember(Map<String, Object> members, String key) {
        Object value = members.get(key);
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            }
            catch (Exception exception) {
                // Preserve previous tolerant stats parsing behavior.
            }
        }
        return 0L;
    }

    static double doubleMember(Map<String, Object> members, String key) {
        Object value = members.get(key);
        if (value instanceof Number) {
            return ((Number)value).doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            }
            catch (Exception exception) {
                // Preserve tolerant stats parsing behavior.
            }
        }
        return 0.0;
    }
}
