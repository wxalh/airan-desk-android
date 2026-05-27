package com.wxalh.airan_desk.terminal;

import java.util.List;

final class TerminalTextGridUtils {
    private TerminalTextGridUtils() {
    }

    static String[] merge(List<String> archivedLines, String[] nativeLines) {
        if (archivedLines == null || archivedLines.isEmpty()) {
            return nativeLines == null ? new String[]{} : nativeLines;
        }
        int nativeCount = nativeLines == null ? 0 : nativeLines.length;
        String[] merged = new String[archivedLines.size() + nativeCount];
        for (int i = 0; i < archivedLines.size(); ++i) {
            merged[i] = archivedLines.get(i);
        }
        for (int i = 0; i < nativeCount; ++i) {
            merged[archivedLines.size() + i] = nativeLines[i];
        }
        return merged;
    }

    static int lineLength(String[] lines, int row) {
        if (lines == null || row < 0 || row >= lines.length || lines[row] == null) {
            return 0;
        }
        return lines[row].codePointCount(0, lines[row].length());
    }

    static String selectedText(String[] lines, int startRow, int startCol, int endRow, int endCol) {
        if (lines == null || lines.length == 0) {
            return "";
        }
        int topRow = topRow(startRow, startCol, endRow, endCol);
        int bottomRow = bottomRow(startRow, startCol, endRow, endCol);
        int topCol = topCol(startRow, startCol, endRow, endCol);
        int bottomCol = bottomCol(startRow, startCol, endRow, endCol);
        StringBuilder builder = new StringBuilder();
        for (int row = topRow; row <= bottomRow && row < lines.length; ++row) {
            String line = lines[row] == null ? "" : lines[row];
            int rowStartCol = row == topRow ? topCol : 0;
            int rowEndCol = row == bottomRow ? bottomCol : lineLength(lines, row);
            if (row == topRow && row == bottomRow && rowEndCol < rowStartCol) {
                int tmp = rowStartCol;
                rowStartCol = rowEndCol;
                rowEndCol = tmp;
            }
            int lineLength = lineLength(lines, row);
            rowStartCol = Math.max(0, Math.min(lineLength, rowStartCol));
            rowEndCol = Math.max(rowStartCol, Math.min(lineLength, rowEndCol));
            builder.append(sliceByCodePoint(line, rowStartCol, rowEndCol));
            if (row < bottomRow) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static int topRow(int startRow, int startCol, int endRow, int endCol) {
        return isForward(startRow, startCol, endRow, endCol) ? startRow : endRow;
    }

    private static int bottomRow(int startRow, int startCol, int endRow, int endCol) {
        return isForward(startRow, startCol, endRow, endCol) ? endRow : startRow;
    }

    private static int topCol(int startRow, int startCol, int endRow, int endCol) {
        return isForward(startRow, startCol, endRow, endCol) ? startCol : endCol;
    }

    private static int bottomCol(int startRow, int startCol, int endRow, int endCol) {
        return isForward(startRow, startCol, endRow, endCol) ? endCol : startCol;
    }

    private static boolean isForward(int startRow, int startCol, int endRow, int endCol) {
        return startRow < endRow || startRow == endRow && startCol <= endCol;
    }

    private static String sliceByCodePoint(String text, int startCol, int endCol) {
        if (text == null || text.length() == 0 || endCol <= startCol) {
            return "";
        }
        int length = text.codePointCount(0, text.length());
        int start = text.offsetByCodePoints(0, Math.max(0, Math.min(startCol, length)));
        int end = text.offsetByCodePoints(0, Math.max(0, Math.min(endCol, length)));
        return text.substring(start, end);
    }
}
