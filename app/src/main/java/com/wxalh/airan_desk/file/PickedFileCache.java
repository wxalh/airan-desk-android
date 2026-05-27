package com.wxalh.airan_desk.file;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import com.wxalh.airan_desk.util.AiranPathUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class PickedFileCache {
    private static final int BUFFER_SIZE = 65536;

    private PickedFileCache() {
    }

    public static File copyPickedFile(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                return null;
            }
            File file = cacheUploadFile(context, displayNameForUri(context, uri));
            output = new FileOutputStream(file);
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buf)) >= 0) {
                output.write(buf, 0, read);
            }
            return file;
        } catch (Exception e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    public static File copyPickedDirectory(Context context, Uri treeUri) {
        if (treeUri == null) {
            return null;
        }
        try {
            try {
                context.getContentResolver().takePersistableUriPermission(treeUri, 1);
            } catch (Exception ignored) {
            }
            DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
            if (root == null || !root.isDirectory()) {
                return null;
            }
            String folderName = root.getName();
            if (folderName == null || folderName.length() == 0) {
                folderName = "airan_upload_dir";
            }
            File targetRoot = new File(context.getCacheDir(), folderName);
            deleteRecursively(targetRoot);
            if (!targetRoot.mkdirs() && !targetRoot.exists()) {
                return null;
            }
            return copyDocumentDirectory(context, root, targetRoot) ? targetRoot : null;
        } catch (Exception e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private static String displayNameForUri(Context context, Uri uri) {
        String name = "";
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null);
            int index;
            if (cursor != null && cursor.moveToFirst() && (index = cursor.getColumnIndex("_display_name")) >= 0) {
                name = cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (name == null || name.length() == 0) {
            try {
                DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
                if (documentFile != null) {
                    name = documentFile.getName();
                }
            } catch (Exception ignored) {
            }
        }
        if (name == null || name.length() == 0) {
            name = "airan_upload";
        }
        return AiranPathUtils.safeFileName(name);
    }

    private static File cacheUploadFile(Context context, String displayName) {
        File dir = new File(new File(context.getCacheDir(), "uploads"), String.valueOf(System.currentTimeMillis()));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, AiranPathUtils.safeFileName(displayName));
    }

    private static boolean copyDocumentDirectory(Context context, DocumentFile source, File targetDir) throws Exception {
        if (source == null || !source.isDirectory()) {
            return false;
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return false;
        }
        DocumentFile[] children = source.listFiles();
        for (DocumentFile child : children) {
            String name = child.getName();
            if (name == null || name.length() == 0) {
                continue;
            }
            String safeName = AiranPathUtils.safeFileName(name);
            File target = new File(targetDir, safeName);
            try {
                if (!target.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator)) {
                    continue;
                }
            } catch (Exception ignored) {
                continue;
            }
            if (child.isDirectory()) {
                if (!copyDocumentDirectory(context, child, target)) {
                    return false;
                }
                continue;
            }
            if (child.isFile()) {
                copyDocumentFile(context, child, target);
            }
        }
        return true;
    }

    private static void copyDocumentFile(Context context, DocumentFile source, File target) throws Exception {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            input = context.getContentResolver().openInputStream(source.getUri());
            if (input == null) {
                throw new IllegalStateException("open input failed");
            }
            output = new FileOutputStream(target);
            byte[] buf = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buf)) >= 0) {
                output.write(buf, 0, read);
            }
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children;
        if (file.isDirectory() && (children = file.listFiles()) != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (Exception ignored) {
        }
    }
}
