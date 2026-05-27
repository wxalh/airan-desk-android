package com.wxalh.airan_desk.file;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import java.io.File;

public final class DownloadDirectoryProvider {
    private DownloadDirectoryProvider() {
    }

    public static File defaultDownloadDir(Context context) {
        File scopedDir;
        if (Build.VERSION.SDK_INT >= 29 && (scopedDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) != null && (scopedDir.exists() || scopedDir.mkdirs()) && scopedDir.canWrite()) {
            return scopedDir;
        }
        File publicDir = Environment.getExternalStoragePublicDirectory((String)Environment.DIRECTORY_DOWNLOADS);
        if (publicDir != null && (publicDir.exists() || publicDir.mkdirs()) && publicDir.canWrite()) {
            return publicDir;
        }
        File externalDir = context.getExternalFilesDir(null);
        File privateDir = externalDir == null ? new File(context.getFilesDir(), "Downloads") : new File(externalDir, "Downloads");
        if (!privateDir.exists()) {
            privateDir.mkdirs();
        }
        return privateDir;
    }
}
