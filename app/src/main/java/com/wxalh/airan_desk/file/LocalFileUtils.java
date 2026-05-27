package com.wxalh.airan_desk.file;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LocalFileUtils {
    private LocalFileUtils() {
    }

    public static String newTransferId() {
        return UUID.randomUUID().toString();
    }

    public static String normalizeTransferId(String transferId) {
        return transferId == null || transferId.length() == 0 ? newTransferId() : transferId;
    }

    public static String safeRemoteName(String remotePath) {
        if (remotePath == null || remotePath.trim().length() == 0) {
            return "downloaded_item";
        }
        String cleaned = remotePath.replace('\\', '/');
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        int slash = cleaned.lastIndexOf('/');
        String name = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return name.length() == 0 || ".".equals(name) || "..".equals(name) ? "downloaded_item" : name;
    }

    public static String safeString(String value) {
        return value == null ? "" : value;
    }

    public static boolean looksLikeTransferId(String value) {
        return TransferHistoryStore.looksLikeTransferId(value);
    }

    public static File resolveLocalPath(Context context, String path) {
        if (path == null || path.length() == 0 || "home".equals(path)) {
            return getHomeDir(context);
        }
        File requested = new File(path);
        if (!requested.isAbsolute()) {
            requested = new File(getHomeDir(context), path);
        }
        return enforceSandbox(context, requested);
    }

    public static File resolveSandboxed(Context context, File requested) {
        return enforceSandbox(context, requested);
    }

    private static File enforceSandbox(Context context, File requested) {
        File canonical = canonical(requested);
        for (File root : sandboxRoots(context)) {
            if (root != null && isWithin(canonical, canonical(root))) {
                return canonical;
            }
        }
        throw new SecurityException("path escapes sandbox: " + requested.getPath());
    }

    private static List<File> sandboxRoots(Context context) {
        List<File> roots = new ArrayList<File>();
        if (context != null) {
            File files = context.getFilesDir();
            if (files != null) {
                roots.add(files);
            }
            File cache = context.getCacheDir();
            if (cache != null) {
                roots.add(cache);
            }
            File external = context.getExternalFilesDir(null);
            if (external != null) {
                roots.add(external);
            }
            File downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null) {
                roots.add(downloads);
            }
            try {
                File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (publicDir != null) {
                    roots.add(publicDir);
                }
            } catch (Exception ignored) {
            }
            if (canAccessSharedStorageRoot()) {
                File externalRoot = Environment.getExternalStorageDirectory();
                if (externalRoot != null) {
                    roots.add(externalRoot);
                }
            }
        }
        return roots;
    }

    public static boolean canAccessSharedStorageRoot() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (Exception ignored) {
            return file.getAbsoluteFile();
        }
    }

    private static boolean isWithin(File child, File parent) {
        if (child == null || parent == null) {
            return false;
        }
        String childPath = child.getAbsolutePath();
        String parentPath = parent.getAbsolutePath();
        if (childPath.equals(parentPath)) {
            return true;
        }
        String prefix = parentPath.endsWith(File.separator) ? parentPath : parentPath + File.separator;
        return childPath.startsWith(prefix);
    }

    public static File getHomeDir(Context context) {
        File external = context.getExternalFilesDir(null);
        return external == null ? context.getFilesDir() : external;
    }

    public static void copyFile(File source, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(target);
            byte[] buf = new byte[65536];
            int read;
            while ((read = input.read(buf)) >= 0) {
                output.write(buf, 0, read);
            }
        } finally {
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
        }
    }

    public static String suffix(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot + 1 < name.length() ? name.substring(dot + 1).toLowerCase(Locale.US) : "";
    }

    public static String fileNameFromPath(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : path;
    }
}
