package com.wxalh.airan_desk.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public final class ClipboardUtils {
    private static final String DEFAULT_LABEL = "Airan Desk";

    private ClipboardUtils() {
    }

    public static boolean copyText(Context context, String label, String text) {
        if (context == null || text == null || text.length() == 0) {
            return false;
        }
        ClipboardManager manager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            return false;
        }
        String safeLabel = label == null || label.length() == 0 ? DEFAULT_LABEL : label;
        manager.setPrimaryClip(ClipData.newPlainText((CharSequence)safeLabel, (CharSequence)text));
        return true;
    }

    public static String primaryText(Context context) {
        if (context == null) {
            return "";
        }
        ClipboardManager manager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip()) {
            return "";
        }
        ClipData clip = manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return "";
        }
        CharSequence text = clip.getItemAt(0).coerceToText(context);
        return text == null ? "" : text.toString();
    }
}
