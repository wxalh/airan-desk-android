package com.wxalh.airan_desk.terminal;

import java.util.regex.Pattern;

public final class TerminalTextUtils {
    private static final Pattern ANSI_OSC_PATTERN = Pattern.compile("\u001b\\][^\u0007]*(?:\u0007|\u001b\\\\)");
    private static final Pattern ANSI_CSI_PATTERN = Pattern.compile("\u001b\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern ANSI_SIMPLE_PATTERN = Pattern.compile("\u001b[@-Z\\\\-_]");

    private TerminalTextUtils() {
    }

    public static String preview(String text) {
        if (text == null) {
            return "0 chars";
        }
        String preview = text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        if (preview.length() > 80) {
            preview = preview.substring(0, 80) + "...";
        }
        return text.length() + " chars \"" + preview + "\"";
    }

    public static String normalizeFallbackText(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "");
        normalized = ANSI_OSC_PATTERN.matcher(normalized).replaceAll("");
        normalized = ANSI_CSI_PATTERN.matcher(normalized).replaceAll("");
        normalized = ANSI_SIMPLE_PATTERN.matcher(normalized).replaceAll("");
        if (normalized.trim().length() == 0) {
            return "";
        }
        return normalized.replaceAll("\n{4,}", "\n\n");
    }
}
