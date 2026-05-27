package com.wxalh.airan_desk.file;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import androidx.core.content.FileProvider;
import java.io.File;

public final class LocalFileOpener {
    public enum Result {
        OPENED,
        MISSING,
        OPEN_FAILED
    }

    private LocalFileOpener() {
    }

    public static Result openTransferPath(Context context, String path) {
        if (path == null || path.length() == 0 || TransferHistoryStore.looksLikeTransferId(path)) {
            return Result.MISSING;
        }
        File target = new File(path);
        File directory = target.isDirectory() ? target : target.getParentFile();
        if (directory == null || !directory.exists()) {
            return Result.MISSING;
        }
        if (startDirectoryIntent(context, directory)) {
            return Result.OPENED;
        }
        if (target.exists() && target.isFile() && startFileManagerIntent(context, target, "*/*")) {
            return Result.OPENED;
        }
        return Result.OPEN_FAILED;
    }

    public static File copyFallbackPath(String path) {
        if (path == null || path.length() == 0 || TransferHistoryStore.looksLikeTransferId(path)) {
            return null;
        }
        File target = new File(path);
        File directory = target.isDirectory() ? target : target.getParentFile();
        if (directory == null || !directory.exists()) {
            return target;
        }
        return target.exists() && target.isDirectory() ? target : directory;
    }

    private static boolean startFileManagerIntent(Context context, File file, String type) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            return startViewIntent(context, uri, type);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean startDirectoryIntent(Context context, File directory) {
        Uri documentUri = externalStorageDocumentUri(directory);
        return documentUri != null && startViewIntent(context, documentUri, "vnd.android.document/directory");
    }

    private static boolean startViewIntent(Context context, Uri uri, String type) {
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setDataAndType(uri, type);
            intent.addCategory("android.intent.category.DEFAULT");
            intent.addFlags(1);
            intent.addFlags(0x10000000);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Uri externalStorageDocumentUri(File directory) {
        try {
            File root = Environment.getExternalStorageDirectory().getCanonicalFile();
            File dir = directory.getCanonicalFile();
            String rootPath = root.getAbsolutePath();
            String dirPath = dir.getAbsolutePath();
            if (!dirPath.equals(rootPath) && !dirPath.startsWith(rootPath + File.separator)) {
                return null;
            }
            String relative = dirPath.equals(rootPath) ? "" : dirPath.substring(rootPath.length() + 1);
            relative = relative.replace(File.separatorChar, '/');
            String documentId = relative.length() == 0 ? "primary:" : "primary:" + relative;
            return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId);
        } catch (Exception e) {
            return null;
        }
    }
}
