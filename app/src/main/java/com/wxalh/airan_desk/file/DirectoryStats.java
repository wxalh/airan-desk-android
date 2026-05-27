package com.wxalh.airan_desk.file;

import java.io.File;

public final class DirectoryStats {
    public long totalBytes;
    public int totalFiles;

    private DirectoryStats() {
    }

    public static DirectoryStats collect(File root) {
        DirectoryStats stats = new DirectoryStats();
        collect(root, stats);
        return stats;
    }

    private static void collect(File file, DirectoryStats stats) {
        if (file == null || stats == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            stats.totalBytes += file.length();
            ++stats.totalFiles;
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collect(child, stats);
        }
    }
}
