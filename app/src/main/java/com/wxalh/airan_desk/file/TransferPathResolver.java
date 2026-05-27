package com.wxalh.airan_desk.file;

import com.wxalh.airan_desk.model.TransferRecord;

public final class TransferPathResolver {
    private TransferPathResolver() {
    }

    public static String localPath(TransferRecord record, boolean controlRole) {
        if (record == null) {
            return "";
        }
        if (record.localPath != null && record.localPath.length() > 0 && !TransferHistoryStore.looksLikeTransferId(record.localPath)) {
            return record.localPath;
        }
        return inferLocalPath(record.direction, record.sourcePath, record.targetPath, controlRole);
    }

    public static String inferLocalPath(String direction, String sourcePath, String targetPath, boolean controlRole) {
        String source = sourcePath == null ? "" : sourcePath;
        String target = targetPath == null ? "" : targetPath;
        String local = "upload".equals(direction) ? (controlRole ? source : target) : (controlRole ? target : source);
        if (local.length() == 0 || TransferHistoryStore.looksLikeTransferId(local)) {
            local = "upload".equals(direction) ? source : target;
        }
        return local == null || TransferHistoryStore.looksLikeTransferId(local) ? "" : local;
    }

    public static boolean pathsEqual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return normalizePath(a).equals(normalizePath(b));
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replace('\\', '/');
    }
}
