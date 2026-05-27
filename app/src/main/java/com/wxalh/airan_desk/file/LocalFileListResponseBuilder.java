package com.wxalh.airan_desk.file;

import android.content.Context;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LocalFileListResponseBuilder {
    private LocalFileListResponseBuilder() {
    }

    public static JSONObject build(Context context, String requestedPath) throws Exception {
        File dir = LocalFileUtils.resolveLocalPath(context, requestedPath);
        File[] files = dir.listFiles();
        JSONArray array = new JSONArray();
        if (files != null) {
            FileSortUtils.sortDirectoriesFirst(files);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            for (File file : files) {
                JSONObject item = new JSONObject();
                item.put("name", (Object)file.getName());
                item.put("is_dir", file.isDirectory());
                item.put("file_size", file.length());
                item.put("file_suffix", (Object)LocalFileUtils.suffix(file.getName()));
                item.put("file_executable", file.canExecute());
                item.put("file_last_mod_time", (Object)format.format(new Date(file.lastModified())));
                array.put((Object)item);
            }
        }
        JSONObject response = new JSONObject();
        response.put("role", (Object)"cli");
        response.put("msgType", (Object)"file_list");
        response.put("path", (Object)dir.getAbsolutePath());
        response.put("folderFiles", (Object)array);
        response.put("mounted", (Object)MountedRootCollector.collect(context));
        return response;
    }
}
