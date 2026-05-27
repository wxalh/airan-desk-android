package com.wxalh.airan_desk.file;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import org.json.JSONArray;

public final class MountedRootCollector {
    private MountedRootCollector() {
    }

    public static JSONArray collect(Context context) throws Exception {
        JSONArray roots = new JSONArray();
        addUniquePath(roots, LocalFileUtils.getHomeDir(context));
        addUniquePath(roots, context.getFilesDir());
        addUniquePath(roots, DownloadDirectoryProvider.defaultDownloadDir(context));
        if (LocalFileUtils.canAccessSharedStorageRoot()) {
            addUniquePath(roots, Environment.getExternalStorageDirectory());
        }
        File[] externalDirs = context.getExternalFilesDirs(null);
        if (externalDirs != null) {
            for (File dir : externalDirs) {
                addUniquePath(roots, dir);
            }
        }
        return roots;
    }

    private static void addUniquePath(JSONArray array, File file) throws Exception {
        if (file == null) {
            return;
        }
        String path = file.getAbsolutePath();
        if (path == null || path.length() == 0) {
            return;
        }
        for (int i = 0; i < array.length(); ++i) {
            if (!path.equals(array.optString(i))) continue;
            return;
        }
        array.put((Object)path);
    }
}
