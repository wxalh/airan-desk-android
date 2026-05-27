package com.wxalh.airan_desk.terminal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"deprecation", "unchecked"})
public final class NativeTerminalView
extends View {
    private static final int BG;
    private static final int FG;
    private static final int CURSOR = -1;
    private static final int SELECTION;
    private static final int MIN_ROWS = 8;
    private static final int MIN_COLS = 80;
    private static final float MIN_ZOOM = 0.45f;
    private static final float MAX_ZOOM = 10.0f;
    private static final int RESIZE_HISTORY_LIMIT = 300;
    private static final int MAX_ARCHIVE_ROWS = 8000;
    private final Paint textPaint = new Paint(129);
    private final Paint fillPaint = new Paint();
    private final ScaleGestureDetector scaleDetector;
    private final Editable inputEditable = new SpannableStringBuilder();
    private Listener listener;
    private long nativePtr;
    private final List<String> archivedLines = new ArrayList<String>();
    private String[] lines = new String[0];
    private String[] nativeLinesSnapshot = new String[0];
    private int rows = 24;
    private int cols = 80;
    private int cursorRow = 0;
    private int cursorCol = 0;
    private boolean cursorVisible = true;
    private float baseTextSize;
    private float zoom = 1.0f;
    private float cellWidth = 8.0f;
    private float cellHeight = 18.0f;
    private float ascent = 14.0f;
    private float scrollX = 0.0f;
    private float scrollY = 0.0f;
    private float lastTouchX;
    private float lastTouchY;
    private boolean localEchoEnabled = false;
    private boolean scaling = false;
    private boolean longPressTriggered = false;
    private String composingText = "";
    private boolean composingTextSent = false;
    private boolean selectionMode = false;
    private boolean selecting = false;
    private boolean selectionActive = false;
    private int selectionStartRow = 0;
    private int selectionStartCol = 0;
    private int selectionEndRow = 0;
    private int selectionEndCol = 0;
    private final Runnable zoomResizeCommitRunnable = new Runnable(){

        @Override
        public void run() {
            NativeTerminalView.this.commitZoomResize();
        }
    };
    private float downX;
    private float downY;
    private final int touchSlop;
    private final Runnable longPressRunnable = new Runnable(){

        @Override
        public void run() {
            NativeTerminalView.this.longPressTriggered = true;
            NativeTerminalView.this.performLongClick();
        }
    };

    public NativeTerminalView(Context context) {
        super(context);
        this.setFocusable(true);
        this.setFocusableInTouchMode(true);
        this.setLongClickable(true);
        this.setBackgroundColor(BG);
        this.touchSlop = ViewConfiguration.get((Context)context).getScaledTouchSlop();
        this.baseTextSize = this.sp(14.0f);
        this.textPaint.setColor(FG);
        this.textPaint.setTypeface(Typeface.MONOSPACE);
        this.textPaint.setTextSize(this.baseTextSize);
        this.fillPaint.setStyle(Paint.Style.FILL);
        this.recalcMetrics();
        this.nativePtr = this.nativeCreate(this.rows, this.cols);
        this.refreshSnapshot();
        this.scaleDetector = new ScaleGestureDetector(context, (ScaleGestureDetector.OnScaleGestureListener)new ScaleGestureDetector.SimpleOnScaleGestureListener(){

            public boolean onScaleBegin(ScaleGestureDetector detector) {
                NativeTerminalView.this.scaling = true;
                return true;
            }

            public boolean onScale(ScaleGestureDetector detector) {
                NativeTerminalView.this.applyZoom(NativeTerminalView.this.zoom * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }

            public void onScaleEnd(ScaleGestureDetector detector) {
                NativeTerminalView.this.removeCallbacks(NativeTerminalView.this.zoomResizeCommitRunnable);
                NativeTerminalView.this.postDelayed(NativeTerminalView.this.zoomResizeCommitRunnable, 450L);
                NativeTerminalView.this.postDelayed(new Runnable(){

                    @Override
                    public void run() {
                        NativeTerminalView.this.scaling = false;
                    }
                }, 80L);
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setLocalEchoEnabled(boolean enabled) {
        this.localEchoEnabled = enabled;
    }

    public void write(byte[] data) {
        if (this.nativePtr == 0L || data == null || data.length == 0) {
            return;
        }
        this.nativeWrite(this.nativePtr, data);
        this.refreshSnapshot();
        this.keepCursorVisible();
        this.invalidate();
    }

    public void focusInput() {
        this.requestFocusFromTouch();
        this.requestFocus();
        final InputMethodManager imm = (InputMethodManager)this.getContext().getSystemService("input_method");
        if (imm != null) {
            this.post(new Runnable(){

                @Override
                public void run() {
                    imm.showSoftInput((View)NativeTerminalView.this, 1);
                    if (!imm.isActive((View)NativeTerminalView.this)) {
                        imm.toggleSoftInput(2, 0);
                    }
                }
            });
        }
        this.keepCursorVisible();
    }

    public int currentRows() {
        return this.rows;
    }

    public int currentCols() {
        return this.cols;
    }

    public void scrollCursorIntoView() {
        this.keepCursorVisible();
        this.invalidate();
    }

    public String visibleText() {
        StringBuilder builder = new StringBuilder();
        if (this.lines != null) {
            for (String line : this.lines) {
                if (line != null && line.length() > 0) {
                    builder.append(line);
                }
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    public void setSelectionMode(boolean enabled) {
        this.selectionMode = enabled;
        this.selecting = false;
        if (!enabled) {
            this.clearSelection();
        }
    }

    public boolean isSelectionMode() {
        return this.selectionMode;
    }

    public boolean hasSelection() {
        return this.selectionActive && this.normalizedSelectionText().trim().length() > 0;
    }

    public void selectAll() {
        this.selectionMode = true;
        this.selectionActive = true;
        this.selecting = false;
        this.selectionStartRow = 0;
        this.selectionStartCol = 0;
        this.selectionEndRow = Math.max(0, this.lines == null ? 0 : this.lines.length - 1);
        this.selectionEndCol = this.lineLength(this.selectionEndRow);
        this.invalidate();
    }

    public void clearSelection() {
        this.selectionActive = false;
        this.selecting = false;
        this.invalidate();
    }

    public String selectedText() {
        return this.normalizedSelectionText();
    }

    public void pasteText(String text) {
        this.sendInput(text);
    }

    protected void onDetachedFromWindow() {
        this.removeCallbacks(this.longPressRunnable);
        this.removeCallbacks(this.zoomResizeCommitRunnable);
        this.listener = null;
        if (this.nativePtr != 0L) {
            this.nativeDestroy(this.nativePtr);
            this.nativePtr = 0L;
        }
        super.onDetachedFromWindow();
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw > 0 && w == oldw) {
            this.scrollY = this.clamp(this.scrollY, 0.0f, this.maxScrollY());
            this.keepCursorVisible();
            this.invalidate();
            return;
        }
        this.updateGridFromSize();
    }

    protected void onDraw(Canvas canvas) {
        canvas.drawColor(BG);
        canvas.save();
        canvas.translate(-this.scrollX, -this.scrollY);
        this.fillPaint.setColor(BG);
        int startRow = Math.max(0, (int)(this.scrollY / this.cellHeight) - 1);
        int endRow = Math.min(this.lines.length - 1, (int)((this.scrollY + (float)this.getHeight()) / this.cellHeight) + 1);
        this.drawSelection(canvas, startRow, endRow);
        for (int row = startRow; row <= endRow && row < this.lines.length; ++row) {
            String line = this.lines[row] == null ? "" : this.lines[row];
            canvas.drawText(line, 0.0f, (float)row * this.cellHeight + this.ascent, this.textPaint);
        }
        this.fillPaint.setColor(-1);
        if (this.cursorVisible) {
            int drawCursorCol = this.visualCursorCol();
            float cursorLeft = (float)drawCursorCol * this.cellWidth;
            float cursorTop = (float)this.cursorRow * this.cellHeight;
            canvas.drawRect(cursorLeft, cursorTop, cursorLeft + Math.max(2.0f, this.zoom * 2.0f), cursorTop + this.cellHeight, this.fillPaint);
        }
        canvas.restore();
    }

    public boolean onTouchEvent(MotionEvent event) {
        this.scaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (event.getPointerCount() >= 2 || this.scaling) {
            this.removeCallbacks(this.longPressRunnable);
            return true;
        }
        if (action == 0) {
            this.lastTouchX = event.getX();
            this.lastTouchY = event.getY();
            this.downX = this.lastTouchX;
            this.downY = this.lastTouchY;
            this.longPressTriggered = false;
            this.removeCallbacks(this.longPressRunnable);
            this.postDelayed(this.longPressRunnable, ViewConfiguration.getLongPressTimeout());
            this.requestFocus();
            if (this.selectionMode) {
                this.selecting = false;
            }
            return true;
        }
        if (action == 2) {
            float dx = event.getX() - this.lastTouchX;
            float dy = event.getY() - this.lastTouchY;
            if (Math.abs(event.getX() - this.downX) > (float)this.touchSlop || Math.abs(event.getY() - this.downY) > (float)this.touchSlop) {
                this.removeCallbacks(this.longPressRunnable);
                if (this.selectionMode) {
                    if (!this.selecting) {
                        this.setSelectionAnchor(this.downX, this.downY);
                        this.selecting = true;
                    }
                    this.updateSelectionEdge(event.getX(), event.getY());
                    this.invalidate();
                    return true;
                }
            }
            this.scrollX = this.clamp(this.scrollX - dx, 0.0f, this.maxScrollX());
            this.scrollY = this.clamp(this.scrollY - dy, 0.0f, this.maxScrollY());
            this.lastTouchX = event.getX();
            this.lastTouchY = event.getY();
            this.invalidate();
            return true;
        }
        if (action == 1) {
            this.removeCallbacks(this.longPressRunnable);
            if (this.selectionMode && this.selecting) {
                this.updateSelectionEdge(event.getX(), event.getY());
                this.selecting = false;
                this.invalidate();
                return true;
            }
            if (!this.longPressTriggered && event.getEventTime() - event.getDownTime() < 180L) {
                this.focusInput();
            }
            return true;
        }
        if (action == 3) {
            this.removeCallbacks(this.longPressRunnable);
            return true;
        }
        return true;
    }

    public boolean performLongClick() {
        super.performLongClick();
        this.selecting = false;
        if (this.listener != null) {
            this.listener.onLongPress(this);
        }
        return true;
    }

    public boolean onCheckIsTextEditor() {
        return true;
    }

    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = 655505;
        outAttrs.imeOptions = 0x12000001;
        return new BaseInputConnection(this, true){

            public Editable getEditable() {
                return NativeTerminalView.this.inputEditable;
            }

            public boolean commitText(CharSequence text, int newCursorPosition) {
                String value;
                String string = value = text == null ? "" : text.toString();
                if (!NativeTerminalView.this.composingTextSent || !value.equals(NativeTerminalView.this.composingText)) {
                    NativeTerminalView.this.sendInput(value);
                }
                NativeTerminalView.this.clearComposingState();
                return true;
            }

            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                String value;
                String string = value = text == null ? "" : text.toString();
                if (value.length() == 0) {
                    NativeTerminalView.this.clearComposingState();
                    return true;
                }
                if (NativeTerminalView.this.isDirectAsciiComposition(value)) {
                    if (NativeTerminalView.this.composingTextSent && value.startsWith(NativeTerminalView.this.composingText)) {
                        NativeTerminalView.this.sendInput(value.substring(NativeTerminalView.this.composingText.length()));
                    } else if (!NativeTerminalView.this.composingTextSent) {
                        NativeTerminalView.this.sendInput(value);
                    }
                    NativeTerminalView.this.composingText = value;
                    NativeTerminalView.this.composingTextSent = true;
                    NativeTerminalView.this.inputEditable.clear();
                    return true;
                }
                NativeTerminalView.this.composingText = value;
                NativeTerminalView.this.composingTextSent = false;
                NativeTerminalView.this.inputEditable.clear();
                return true;
            }

            public boolean finishComposingText() {
                NativeTerminalView.this.clearComposingState();
                return true;
            }

            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                NativeTerminalView.this.clearComposingState();
                NativeTerminalView.this.sendInput("\u007f");
                return true;
            }

            public boolean sendKeyEvent(KeyEvent event) {
                if (event.getAction() != 0) {
                    return true;
                }
                return NativeTerminalView.this.handleKeyDown(event.getKeyCode(), event) || super.sendKeyEvent(event);
            }
        };
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return this.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    private void sendInput(String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        if (this.localEchoEnabled) {
            this.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (this.listener != null) {
            this.listener.onInput(text);
        }
    }

    private boolean handleKeyDown(int keyCode, KeyEvent event) {
        int unicodeChar;
        this.clearComposingState();
        if (keyCode == 66) {
            this.sendInput("\r");
            return true;
        }
        if (keyCode == 67) {
            this.sendInput("\u007f");
            return true;
        }
        int n = unicodeChar = event == null ? 0 : event.getUnicodeChar();
        if (unicodeChar > 0) {
            this.sendInput(new String(Character.toChars(unicodeChar)));
            return true;
        }
        return false;
    }

    private void clearComposingState() {
        this.composingText = "";
        this.composingTextSent = false;
        this.inputEditable.clear();
    }

    private boolean isDirectAsciiComposition(String value) {
        if (value == null || value.length() == 0 || value.length() > 64) {
            return false;
        }
        for (int i = 0; i < value.length(); ++i) {
            char ch = value.charAt(i);
            if (ch >= ' ' && ch < '\u007f') continue;
            return false;
        }
        return true;
    }

    private void updateGridFromSize() {
        this.recalcMetrics();
        this.resizeGridIfNeeded();
    }

    private void resizeGridIfNeeded() {
        int nextCols = Math.max(80, (int)Math.floor((float)Math.max(1, this.getWidth()) / this.cellWidth));
        int nextRows = Math.max(8, (int)Math.floor((float)Math.max(1, this.getHeight()) / this.cellHeight));
        if (nextCols == this.cols && nextRows == this.rows) {
            return;
        }
        if (this.lines != null && this.lines.length > this.rows + 300) {
            this.invalidate();
            return;
        }
        this.cols = nextCols;
        this.rows = nextRows;
        if (this.nativePtr != 0L) {
            this.nativeResize(this.nativePtr, this.rows, this.cols);
        }
        this.refreshSnapshot();
        if (this.listener != null) {
            this.listener.onResize(this.rows, this.cols);
        }
        this.invalidate();
    }

    private void commitZoomResize() {
        int nextCols = Math.max(80, (int)Math.floor((float)Math.max(1, this.getWidth()) / this.cellWidth));
        int nextRows = Math.max(8, (int)Math.floor((float)Math.max(1, this.getHeight()) / this.cellHeight));
        if (nextCols == this.cols && nextRows == this.rows) {
            return;
        }
        this.appendNativeSnapshotToArchive();
        this.cols = nextCols;
        this.rows = nextRows;
        if (this.nativePtr != 0L) {
            this.nativeReset(this.nativePtr, this.rows, this.cols);
        }
        this.clearSelection();
        this.refreshSnapshot();
        if (this.listener != null) {
            this.listener.onResize(this.rows, this.cols);
        }
        this.scrollY = this.clamp(this.scrollY, 0.0f, this.maxScrollY());
        this.scrollX = this.clamp(this.scrollX, 0.0f, this.maxScrollX());
        this.invalidate();
    }

    private void applyZoom(float requestedZoom, float focusX, float focusY) {
        float next = this.clamp(requestedZoom, 0.45f, 10.0f);
        if (Math.abs(next - this.zoom) <= 0.001f) {
            return;
        }
        float oldCellWidth = this.cellWidth;
        float oldCellHeight = this.cellHeight;
        float focusCol = oldCellWidth <= 0.0f ? 0.0f : (this.scrollX + focusX) / oldCellWidth;
        float focusRow = oldCellHeight <= 0.0f ? 0.0f : (this.scrollY + focusY) / oldCellHeight;
        this.zoom = next;
        this.recalcMetrics();
        this.scrollX = this.clamp(focusCol * this.cellWidth - focusX, 0.0f, this.maxScrollX());
        this.scrollY = this.clamp(focusRow * this.cellHeight - focusY, 0.0f, this.maxScrollY());
        this.invalidate();
    }

    private void refreshSnapshot() {
        if (this.nativePtr == 0L) {
            return;
        }
        this.nativeLinesSnapshot = this.nativeLines(this.nativePtr);
        this.lines = this.mergeArchivedAndNativeLines();
        this.cursorRow = this.archivedLines.size() + this.nativeCursorRow(this.nativePtr);
        this.cursorCol = this.nativeCursorCol(this.nativePtr);
        this.cursorVisible = this.nativeCursorVisible(this.nativePtr);
    }

    private String[] mergeArchivedAndNativeLines() {
        return TerminalTextGridUtils.merge(this.archivedLines, this.nativeLinesSnapshot);
    }

    private void appendNativeSnapshotToArchive() {
        String line;
        int last;
        if (this.nativeLinesSnapshot == null || this.nativeLinesSnapshot.length == 0) {
            return;
        }
        for (last = this.nativeLinesSnapshot.length - 1; last >= 0 && ((line = this.nativeLinesSnapshot[last]) == null || line.trim().length() <= 0); --last) {
        }
        if (last < 0) {
            return;
        }
        for (int i = 0; i <= last; ++i) {
            this.archivedLines.add(this.nativeLinesSnapshot[i] == null ? "" : this.nativeLinesSnapshot[i]);
        }
        while (this.archivedLines.size() > 8000) {
            this.archivedLines.remove(0);
        }
    }

    private void keepCursorVisible() {
        int drawCursorCol;
        float cursorRight;
        float cursorBottom = (float)(this.cursorRow + 1) * this.cellHeight;
        if (cursorBottom > this.scrollY + (float)this.getHeight()) {
            this.scrollY = this.clamp(cursorBottom - (float)this.getHeight() + this.cellHeight, 0.0f, this.maxScrollY());
        }
        if ((float)this.cursorRow * this.cellHeight < this.scrollY) {
            this.scrollY = this.clamp((float)this.cursorRow * this.cellHeight, 0.0f, this.maxScrollY());
        }
        if ((cursorRight = (float)((drawCursorCol = this.visualCursorCol()) + 2) * this.cellWidth) > this.scrollX + (float)this.getWidth()) {
            this.scrollX = this.clamp(cursorRight - (float)this.getWidth() + this.cellWidth, 0.0f, this.maxScrollX());
        }
        Rect cursorRect = new Rect(Math.max(0, (int)((float)drawCursorCol * this.cellWidth - this.scrollX)), Math.max(0, (int)((float)this.cursorRow * this.cellHeight - this.scrollY)), Math.min(this.getWidth(), (int)((float)(drawCursorCol + 2) * this.cellWidth - this.scrollX)), Math.min(this.getHeight(), (int)((float)(this.cursorRow + 1) * this.cellHeight - this.scrollY)));
        this.requestRectangleOnScreen(cursorRect, false);
    }

    private int visualCursorCol() {
        if (this.lines == null || this.cursorRow < 0 || this.cursorRow >= this.lines.length) {
            return Math.max(0, this.cursorCol);
        }
        String line = this.lines[this.cursorRow] == null ? "" : this.lines[this.cursorRow];
        int visibleCols = line.codePointCount(0, line.length());
        if (this.cursorCol > visibleCols + 2) {
            return visibleCols + 2;
        }
        return Math.max(0, this.cursorCol);
    }

    private void drawSelection(Canvas canvas, int startRow, int endRow) {
        int lastRow;
        if (!this.selectionActive || this.lines == null || this.lines.length == 0) {
            return;
        }
        int topRow = this.selectionTopRow();
        int bottomRow = this.selectionBottomRow();
        int firstRow = Math.max(startRow, topRow);
        if (firstRow > (lastRow = Math.min(endRow, bottomRow))) {
            return;
        }
        this.fillPaint.setColor(SELECTION);
        for (int row = firstRow; row <= lastRow; ++row) {
            int endCol;
            int startCol = row == topRow ? this.selectionTopCol() : 0;
            int n = endCol = row == bottomRow ? this.selectionBottomCol() : this.lineLength(row);
            if (endCol < startCol) {
                int tmp = startCol;
                startCol = endCol;
                endCol = tmp;
            }
            if (endCol == startCol) {
                endCol = startCol + 1;
            }
            float left = (float)startCol * this.cellWidth;
            float right = Math.max(left + this.cellWidth, (float)endCol * this.cellWidth);
            float top = (float)row * this.cellHeight;
            canvas.drawRect(left, top, right, top + this.cellHeight, this.fillPaint);
        }
    }

    private void setSelectionAnchor(float x, float y) {
        int[] cell = this.touchToCell(x, y);
        this.selectionStartRow = cell[0];
        this.selectionStartCol = cell[1];
        this.selectionEndRow = this.selectionStartRow;
        this.selectionEndCol = this.selectionStartCol;
        this.selectionActive = true;
    }

    private void updateSelectionEdge(float x, float y) {
        int[] cell = this.touchToCell(x, y);
        this.selectionEndRow = cell[0];
        this.selectionEndCol = cell[1];
        this.selectionActive = true;
    }

    private int[] touchToCell(float x, float y) {
        int maxRow = Math.max(0, this.lines == null ? 0 : this.lines.length - 1);
        int row = Math.max(0, Math.min(maxRow, (int)Math.floor((this.scrollY + y) / this.cellHeight)));
        int maxCol = Math.max(0, this.lineLength(row));
        int col = Math.max(0, Math.min(maxCol, (int)Math.floor((this.scrollX + x) / this.cellWidth)));
        return new int[]{row, col};
    }

    private String normalizedSelectionText() {
        if (!this.selectionActive || this.lines == null || this.lines.length == 0) {
            return "";
        }
        return TerminalTextGridUtils.selectedText(this.lines, this.selectionStartRow, this.selectionStartCol, this.selectionEndRow, this.selectionEndCol);
    }

    private int selectionTopRow() {
        return this.selectionStartRow < this.selectionEndRow || this.selectionStartRow == this.selectionEndRow && this.selectionStartCol <= this.selectionEndCol ? this.selectionStartRow : this.selectionEndRow;
    }

    private int selectionBottomRow() {
        return this.selectionStartRow < this.selectionEndRow || this.selectionStartRow == this.selectionEndRow && this.selectionStartCol <= this.selectionEndCol ? this.selectionEndRow : this.selectionStartRow;
    }

    private int selectionTopCol() {
        return this.selectionStartRow < this.selectionEndRow || this.selectionStartRow == this.selectionEndRow && this.selectionStartCol <= this.selectionEndCol ? this.selectionStartCol : this.selectionEndCol;
    }

    private int selectionBottomCol() {
        return this.selectionStartRow < this.selectionEndRow || this.selectionStartRow == this.selectionEndRow && this.selectionStartCol <= this.selectionEndCol ? this.selectionEndCol : this.selectionStartCol;
    }

    private int lineLength(int row) {
        return TerminalTextGridUtils.lineLength(this.lines, row);
    }

    private void recalcMetrics() {
        this.textPaint.setTextSize(this.baseTextSize * this.zoom);
        Paint.FontMetrics fm = this.textPaint.getFontMetrics();
        this.cellWidth = Math.max(1.0f, this.textPaint.measureText("M"));
        this.cellHeight = Math.max(1.0f, fm.descent - fm.ascent + this.dp(2.0f));
        this.ascent = -fm.ascent;
    }

    private float maxScrollX() {
        return Math.max(0.0f, (float)this.cols * this.cellWidth - (float)this.getWidth());
    }

    private float maxScrollY() {
        int lineCount = this.lines == null || this.lines.length == 0 ? this.rows : this.lines.length;
        return Math.max(0.0f, (float)lineCount * this.cellHeight - (float)this.getHeight());
    }

    private float dp(float value) {
        return value * this.getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * this.getResources().getDisplayMetrics().scaledDensity;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private native long nativeCreate(int var1, int var2);

    private native void nativeDestroy(long var1);

    private native void nativeReset(long var1, int var3, int var4);

    private native void nativeResize(long var1, int var3, int var4);

    private native void nativeWrite(long var1, byte[] var3);

    private native String[] nativeLines(long var1);

    private native int nativeCursorRow(long var1);

    private native int nativeCursorCol(long var1);

    private native boolean nativeCursorVisible(long var1);

    static {
        System.loadLibrary("airan_terminal");
        BG = Color.rgb((int)12, (int)12, (int)12);
        FG = Color.rgb((int)204, (int)204, (int)204);
        SELECTION = Color.rgb((int)42, (int)106, (int)140);
    }

    public static interface Listener {
        public void onInput(String var1);

        public void onResize(int var1, int var2);

        public void onLongPress(View var1);
    }
}
