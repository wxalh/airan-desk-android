package com.wxalh.airan_desk.file;

import java.util.Locale;
import org.json.JSONObject;

public final class RemoteFileEntryUtils {
    public static final String KEY_PARENT_ENTRY = "__airan_parent_entry";

    private RemoteFileEntryUtils() {
    }

    public static boolean shouldShowParentEntry(String path) {
        if (path == null || path.length() == 0 || "home".equals(path) || "/".equals(path)) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return normalized.length() != 3 || normalized.charAt(1) != ':' || normalized.charAt(2) != '/';
    }

    public static JSONObject parentEntry() {
        JSONObject object = new JSONObject();
        try {
            object.put("name", "..");
            object.put("is_dir", true);
            object.put("file_size", 0);
            object.put(KEY_PARENT_ENTRY, true);
        } catch (Exception ignored) {
        }
        return object;
    }

    public static boolean isParentEntry(JSONObject object) {
        return object != null && object.optBoolean(KEY_PARENT_ENTRY, false);
    }

    public static boolean isExecutable(JSONObject object) {
        boolean executableName;
        if (object == null || object.optBoolean("is_dir")) {
            return false;
        }
        String name = object.optString("name", "").toLowerCase(Locale.US);
        boolean bl = executableName = name.endsWith(".exe") || name.endsWith(".bat") || name.endsWith(".cmd") || name.endsWith(".com") || name.endsWith(".msi") || name.endsWith(".sh") || name.endsWith(".run") || name.endsWith(".appimage") || name.endsWith(".desktop");
        if (object.has("file_executable")) {
            return object.optBoolean("file_executable", false) || executableName;
        }
        return true;
    }

    public static String parentPath(String path) {
        if (path == null || path.length() == 0 || "home".equals(path)) {
            return "home";
        }
        boolean usesBackslash = path.contains("\\") && !path.contains("/");
        String normalized = path.replace('\\', '/');
        boolean windowsDriveRoot = normalized.length() == 3 && normalized.charAt(1) == ':' && normalized.charAt(2) == '/';
        if (windowsDriveRoot) {
            return usesBackslash ? normalized.replace('/', '\\') : normalized;
        }
        int index = normalized.lastIndexOf('/');
        if (normalized.length() >= 3 && normalized.charAt(1) == ':' && normalized.charAt(2) == '/' && index <= 2) {
            String parent = normalized.substring(0, 3);
            return usesBackslash ? parent.replace('/', '\\') : parent;
        }
        if (index <= 0) {
            String parent = "/".equals(normalized) ? "/" : normalized;
            return usesBackslash ? parent.replace('/', '\\') : parent;
        }
        String parent = normalized.substring(0, index);
        return usesBackslash ? parent.replace('/', '\\') : parent;
    }
}
