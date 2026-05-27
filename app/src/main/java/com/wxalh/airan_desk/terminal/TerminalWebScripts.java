package com.wxalh.airan_desk.terminal;

import android.util.Base64;

public final class TerminalWebScripts {
    private TerminalWebScripts() {
    }

    public static String pasteText(String text) throws Exception {
        String b64 = Base64.encodeToString((byte[])(text == null ? "" : text).getBytes("UTF-8"), (int)2);
        return "window.airanPasteB64 && window.airanPasteB64('" + b64 + "');";
    }

    public static String writeBytes(byte[] data) {
        String b64 = Base64.encodeToString((byte[])data, (int)2);
        return "window.airanWriteB64 ? (window.airanWriteB64('" + b64 + "'), true) : false;";
    }

    public static String layout() {
        return "window.airanLayout ? window.airanLayout() : (window.airanFit && window.airanFit());";
    }
}
