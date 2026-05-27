package com.wxalh.airan_desk.status;

import android.content.Context;
import com.wxalh.airan_desk.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RuntimeStatusLog {
    private static final int MAX_ENTRIES = 500;

    private final Context context;
    private final List<String> logs = new ArrayList<String>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public RuntimeStatusLog(Context context) {
        this.context = context;
    }

    public synchronized void add(String message) {
        String raw = StatusLocalizer.normalize(message);
        if (raw.length() == 0) {
            return;
        }
        String localized = StatusLocalizer.localize(this.context, raw);
        StringBuilder entry = new StringBuilder();
        entry.append('[').append(this.timeFormat.format(new Date())).append("] ");
        entry.append(localized);
        if (!raw.equals(localized)) {
            entry.append('\n').append("    ").append(raw);
        }
        this.logs.add(entry.toString());
        while (this.logs.size() > MAX_ENTRIES) {
            this.logs.remove(0);
        }
    }

    public synchronized void clear() {
        this.logs.clear();
    }

    public synchronized String buildText() {
        if (this.logs.isEmpty()) {
            return this.context.getString(R.string.no_runtime_logs);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = this.logs.size() - 1; i >= 0; --i) {
            if (i < this.logs.size() - 1) {
                builder.append("\n\n");
            }
            builder.append(this.logs.get(i));
        }
        return builder.toString();
    }
}
