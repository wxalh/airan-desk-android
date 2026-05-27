package com.wxalh.airan_desk.util;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class CrashLogStore {
    private static final String FILE_NAME = "last_crash.txt";

    private final Context context;

    public CrashLogStore(Context context) {
        this.context = context;
    }

    public void install() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                write(thread, throwable);
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                }
            }
        });
    }

    public File lastCrashFile() {
        File dir = this.context.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    public boolean hasLastCrash() {
        File file = lastCrashFile();
        return file != null && file.exists() && file.length() > 0L;
    }

    private void write(Thread thread, Throwable throwable) {
        FileOutputStream output = null;
        try {
            File file = lastCrashFile();
            if (file == null) {
                return;
            }
            output = new FileOutputStream(file, false);
            StringWriter writer = new StringWriter();
            throwable.printStackTrace(new PrintWriter(writer));
            String threadName = thread == null ? "" : thread.getName();
            output.write(("Thread: " + threadName + "\n" + writer.toString()).getBytes("UTF-8"));
        } catch (Exception ignored) {
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
