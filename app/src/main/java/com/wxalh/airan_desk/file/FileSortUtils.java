package com.wxalh.airan_desk.file;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public final class FileSortUtils {
    private static final Comparator<File> DIRECTORIES_FIRST_BY_NAME = new Comparator<File>(){

        @Override
        public int compare(File left, File right) {
            if (left.isDirectory() != right.isDirectory()) {
                return left.isDirectory() ? -1 : 1;
            }
            return left.getName().compareToIgnoreCase(right.getName());
        }
    };

    private FileSortUtils() {
    }

    public static void sortDirectoriesFirst(File[] files) {
        if (files != null) {
            Arrays.sort(files, DIRECTORIES_FIRST_BY_NAME);
        }
    }
}
