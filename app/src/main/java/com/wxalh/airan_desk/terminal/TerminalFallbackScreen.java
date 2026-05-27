package com.wxalh.airan_desk.terminal;

public final class TerminalFallbackScreen {
    public static final class Result {
        public final boolean visibleOutput;
        public final String renderedText;

        private Result(boolean visibleOutput, String renderedText) {
            this.visibleOutput = visibleOutput;
            this.renderedText = renderedText;
        }
    }

    private char[][] screen;
    private int rows = 40;
    private int cols = 80;
    private int cursorRow = 0;
    private int cursorCol = 0;
    private int savedRow = 0;
    private int savedCol = 0;
    private String renderedText = "";

    public TerminalFallbackScreen() {
        reset();
    }

    public void reset() {
        this.rows = 40;
        this.cols = 80;
        this.cursorRow = 0;
        this.cursorCol = 0;
        this.savedRow = 0;
        this.savedCol = 0;
        this.screen = new char[this.rows][this.cols];
        clear();
        this.renderedText = "";
    }

    public Result apply(String text) {
        if (text == null || text.length() == 0) {
            return new Result(false, null);
        }
        ensure();
        boolean changed = false;
        boolean visibleOutput = false;
        for (int i = 0; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if (ch == '\u001b') {
                int next = consumeEscape(text, i);
                if (next <= i) {
                    continue;
                }
                i = next;
                changed = true;
                continue;
            }
            if (ch == '\r') {
                this.cursorCol = 0;
                continue;
            }
            if (ch == '\n') {
                newLine();
                changed = true;
                continue;
            }
            if (ch == '\b') {
                this.cursorCol = Math.max(0, this.cursorCol - 1);
                continue;
            }
            if (ch == '\t') {
                int spaces = 8 - this.cursorCol % 8;
                for (int s = 0; s < spaces; ++s) {
                    putChar(' ');
                }
                changed = true;
                continue;
            }
            if (ch < ' ' || ch == '\u007f') {
                continue;
            }
            putChar(ch);
            changed = true;
            if (ch != ' ') {
                visibleOutput = true;
            }
        }
        if (changed && (visibleOutput || hasText())) {
            this.renderedText = render();
            return new Result(visibleOutput, this.renderedText);
        }
        return new Result(visibleOutput, null);
    }

    public String renderedText() {
        return this.renderedText;
    }

    private int consumeEscape(String text, int escIndex) {
        int i = escIndex + 1;
        int len = text.length();
        if (i >= len) {
            return escIndex;
        }
        char introducer = text.charAt(i);
        if (introducer == '[') {
            int start = i + 1;
            for (int end = start; end < len; ++end) {
                char c = text.charAt(end);
                if (c < '@' || c > '~') {
                    continue;
                }
                handleCsi(text.substring(start, end), c);
                return end;
            }
            return len - 1;
        }
        if (introducer == ']') {
            for (int end = i + 1; end < len; ++end) {
                char c = text.charAt(end);
                if (c == '\u0007') {
                    return end;
                }
                if (c == '\u001b' && end + 1 < len && text.charAt(end + 1) == '\\') {
                    return end + 1;
                }
            }
            return len - 1;
        }
        if (introducer == '7') {
            this.savedRow = this.cursorRow;
            this.savedCol = this.cursorCol;
            return i;
        }
        if (introducer == '8') {
            this.cursorRow = clampRow(this.savedRow);
            this.cursorCol = clampCol(this.savedCol);
            return i;
        }
        return i;
    }

    private void handleCsi(String params, char command) {
        String clean = params == null ? "" : params.replace("?", "").replace(">", "");
        int[] values = parseCsiValues(clean);
        if (command == 'm' || command == 'h' || command == 'l') {
            return;
        }
        if (command == 'H' || command == 'f') {
            int row = values.length > 0 && values[0] > 0 ? values[0] - 1 : 0;
            int col = values.length > 1 && values[1] > 0 ? values[1] - 1 : 0;
            this.cursorRow = clampRow(row);
            this.cursorCol = clampCol(col);
            return;
        }
        if (command == 'A') {
            this.cursorRow = clampRow(this.cursorRow - csiValue(values, 0, 1));
            return;
        }
        if (command == 'B') {
            this.cursorRow = clampRow(this.cursorRow + csiValue(values, 0, 1));
            return;
        }
        if (command == 'C') {
            this.cursorCol = clampCol(this.cursorCol + csiValue(values, 0, 1));
            return;
        }
        if (command == 'D') {
            this.cursorCol = clampCol(this.cursorCol - csiValue(values, 0, 1));
            return;
        }
        if (command == 'G') {
            this.cursorCol = clampCol(csiValue(values, 0, 1) - 1);
            return;
        }
        if (command == 'J') {
            int mode = csiValue(values, 0, 0);
            if (mode == 2 || mode == 3) {
                clear();
                this.cursorRow = 0;
                this.cursorCol = 0;
            } else if (mode == 0) {
                eraseToEnd();
            }
            return;
        }
        if (command == 'K') {
            eraseLine(csiValue(values, 0, 0));
            return;
        }
        if (command == 's') {
            this.savedRow = this.cursorRow;
            this.savedCol = this.cursorCol;
            return;
        }
        if (command == 'u') {
            this.cursorRow = clampRow(this.savedRow);
            this.cursorCol = clampCol(this.savedCol);
        }
    }

    private static int[] parseCsiValues(String params) {
        if (params == null || params.length() == 0) {
            return new int[0];
        }
        String[] parts = params.split(";");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; ++i) {
            try {
                values[i] = parts[i].length() == 0 ? 0 : Integer.parseInt(parts[i]);
            } catch (Exception ignored) {
                values[i] = 0;
            }
        }
        return values;
    }

    private static int csiValue(int[] values, int index, int fallback) {
        if (values == null || index < 0 || index >= values.length || values[index] <= 0) {
            return fallback;
        }
        return values[index];
    }

    private void eraseToEnd() {
        ensure();
        for (int r = this.cursorRow; r < this.rows; ++r) {
            int start = r == this.cursorRow ? this.cursorCol : 0;
            for (int c = start; c < this.cols; ++c) {
                this.screen[r][c] = ' ';
            }
        }
    }

    private void eraseLine(int mode) {
        ensure();
        int start = mode == 1 ? 0 : this.cursorCol;
        int end = mode == 0 ? this.cols - 1 : this.cursorCol;
        if (mode == 2) {
            start = 0;
            end = this.cols - 1;
        }
        for (int c = Math.max(0, start); c <= Math.min(this.cols - 1, end); ++c) {
            this.screen[this.cursorRow][c] = ' ';
        }
    }

    private void putChar(char ch) {
        ensure();
        this.screen[this.cursorRow][this.cursorCol] = ch;
        ++this.cursorCol;
        if (this.cursorCol >= this.cols) {
            this.cursorCol = 0;
            newLine();
        }
    }

    private void newLine() {
        ++this.cursorRow;
        if (this.cursorRow >= this.rows) {
            scroll();
            this.cursorRow = this.rows - 1;
        }
    }

    private void scroll() {
        ensure();
        for (int r = 1; r < this.rows; ++r) {
            System.arraycopy(this.screen[r], 0, this.screen[r - 1], 0, this.cols);
        }
        for (int c = 0; c < this.cols; ++c) {
            this.screen[this.rows - 1][c] = ' ';
        }
    }

    private int clampRow(int row) {
        return Math.max(0, Math.min(this.rows - 1, row));
    }

    private int clampCol(int col) {
        return Math.max(0, Math.min(this.cols - 1, col));
    }

    private String render() {
        ensure();
        StringBuilder builder = new StringBuilder();
        int lastVisibleRow = 0;
        for (int r = 0; r < this.rows; ++r) {
            if (lineEnd(r) <= 0 && r != this.cursorRow) {
                continue;
            }
            lastVisibleRow = r;
        }
        for (int r = 0; r <= lastVisibleRow; ++r) {
            int end = lineEnd(r);
            if (r == this.cursorRow) {
                end = Math.max(end, this.cursorCol + 1);
            }
            if (end > 0) {
                builder.append(this.screen[r], 0, Math.min(end, this.cols));
            }
            if (r < lastVisibleRow) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private int lineEnd(int row) {
        if (this.screen == null || row < 0 || row >= this.screen.length) {
            return 0;
        }
        for (int c = this.cols - 1; c >= 0; --c) {
            if (this.screen[row][c] != ' ') {
                return c + 1;
            }
        }
        return 0;
    }

    private boolean hasText() {
        if (this.screen == null) {
            return false;
        }
        for (int r = 0; r < this.rows; ++r) {
            if (lineEnd(r) > 0) {
                return true;
            }
        }
        return false;
    }

    private void ensure() {
        if (this.screen == null) {
            reset();
        }
    }

    private void clear() {
        if (this.screen == null) {
            return;
        }
        for (int r = 0; r < this.screen.length; ++r) {
            for (int c = 0; c < this.screen[r].length; ++c) {
                this.screen[r][c] = ' ';
            }
        }
    }
}
