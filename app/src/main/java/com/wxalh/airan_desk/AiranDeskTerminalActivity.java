package com.wxalh.airan_desk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.wxalh.airan_desk.file.LocalFileOpener;
import com.wxalh.airan_desk.Md5Util;
import com.wxalh.airan_desk.config.AppConfig;
import com.wxalh.airan_desk.file.PickedFileCache;
import com.wxalh.airan_desk.file.RemoteFileEntryUtils;
import com.wxalh.airan_desk.file.TransferPathResolver;
import com.wxalh.airan_desk.file.TransferHistoryStore;
import com.wxalh.airan_desk.input.KeyCodeMapper;
import com.wxalh.airan_desk.input.RemoteInputAccessibilityService;
import com.wxalh.airan_desk.model.RemoteSession;
import com.wxalh.airan_desk.model.SessionInfo;
import com.wxalh.airan_desk.model.TransferRecord;
import com.wxalh.airan_desk.network.SignalingClient;
import com.wxalh.airan_desk.rtc.TextureVideoRenderer;
import com.wxalh.airan_desk.rtc.WebRtcClient;
import com.wxalh.airan_desk.service.KeepAliveForegroundService;
import com.wxalh.airan_desk.status.RuntimeStatusLog;
import com.wxalh.airan_desk.status.StatusLocalizer;
import com.wxalh.airan_desk.status.TransferProgressFormatter;
import com.wxalh.airan_desk.terminal.TerminalFallbackScreen;
import com.wxalh.airan_desk.terminal.NativeTerminalView;
import com.wxalh.airan_desk.terminal.TerminalOutputDecoder;
import com.wxalh.airan_desk.terminal.TerminalPathTrackingScript;
import com.wxalh.airan_desk.terminal.TerminalTextUtils;
import com.wxalh.airan_desk.terminal.TerminalWebScripts;
import com.wxalh.airan_desk.ui.AppTheme;
import com.wxalh.airan_desk.ui.DesktopCommandMenus;
import com.wxalh.airan_desk.ui.DesktopCursorIconView;
import com.wxalh.airan_desk.ui.DesktopSettingsMenus;
import com.wxalh.airan_desk.ui.DesktopViewportMath;
import com.wxalh.airan_desk.ui.FileBrowserMenus;
import com.wxalh.airan_desk.ui.RemoteControlSectionView;
import com.wxalh.airan_desk.ui.SettingsScreenView;
import com.wxalh.airan_desk.ui.TerminalActionMenu;
import com.wxalh.airan_desk.ui.TransferHistoryScreenView;
import com.wxalh.airan_desk.ui.UiComponentFactory;
import com.wxalh.airan_desk.ui.UiTextFormatter;
import com.wxalh.airan_desk.util.AiranPathUtils;
import com.wxalh.airan_desk.util.AudioPermissionHelper;
import com.wxalh.airan_desk.util.ClipboardUtils;
import com.wxalh.airan_desk.util.CrashLogStore;
import com.wxalh.airan_desk.util.RemoteCredentialParser;
import com.wxalh.airan_desk.util.SettingsNavigator;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

@SuppressWarnings({"deprecation", "unchecked"})
abstract class AiranDeskTerminalActivity
extends AiranDeskFileActivity {
    protected void showTerminal() {
        FrameLayout terminalHost;
        this.syncNetworkConfigFromEditors();
        this.detachRenderer();
        this.currentTab = "terminal";
        this.terminalPathTrackingInjected = false;
        this.terminalWebReady = false;
        this.terminalWebRenderedOutput = false;
        this.terminalSelectionMode = false;
        this.terminalHasSelection = false;
        this.nativeTerminalSelection = "";
        this.terminalRemoteOutputSeen = false;
        this.terminalWakeScheduled = false;
        this.terminalWaitingHintShown = false;
        this.terminalFallbackHintVisible = false;
        this.terminalFallbackDiagnosticShown = false;
        this.terminalInvisibleByteCount = 0;
        this.lastTerminalResizeRows = -1;
        this.lastTerminalResizeCols = -1;
        this.terminalZoomScale = 1.0f;
        this.terminalScalingGesture = false;
        this.pendingTerminalVisibleOutput = false;
        this.pendingTerminalOutput.reset();
        this.terminalOutputDecoder.reset();
        this.terminalFallbackTranscript.setLength(0);
        this.terminalFallbackScreen.reset();
        this.updateNav();
        this.terminalHostView = terminalHost = new FrameLayout((Context)this);
        this.lastTerminalKeyboardAvailableHeight = -1;
        terminalHost.setBackgroundColor(Color.rgb((int)12, (int)12, (int)12));
        try {
            this.nativeTerminalView = new NativeTerminalView((Context)this);
        }
        catch (Throwable throwable) {
            this.nativeTerminalView = null;
            this.onStatus("native terminal disabled: " + throwable.getClass().getSimpleName());
            this.showUserStatus(this.getString(R.string.terminal_connecting));
        }
        if (this.nativeTerminalView != null) {
            this.nativeTerminalView.setListener(new NativeTerminalView.Listener(){

            @Override
            public void onInput(String text) {
                AiranDeskTerminalActivity.this.onStatus("terminal input captured: " + TerminalTextUtils.preview(text));
                WebRtcClient.sendTerminalInput(text);
            }

            @Override
            public void onResize(int rows, int cols) {
                int nextRows = Math.max(8, rows);
                int nextCols = Math.max(80, cols);
                AiranDeskTerminalActivity.this.pendingTerminalRows = nextRows;
                AiranDeskTerminalActivity.this.pendingTerminalCols = nextCols;
                if (nextRows == AiranDeskTerminalActivity.this.lastTerminalResizeRows && nextCols == AiranDeskTerminalActivity.this.lastTerminalResizeCols) {
                    return;
                }
                AiranDeskTerminalActivity.this.lastTerminalResizeRows = nextRows;
                AiranDeskTerminalActivity.this.lastTerminalResizeCols = nextCols;
                WebRtcClient.sendTerminalResize(nextRows, nextCols);
            }

            @Override
            public void onLongPress(View anchor) {
                AiranDeskTerminalActivity.this.showTerminalMenu(anchor);
            }
            });
            this.nativeTerminalView.setLocalEchoEnabled("pipe".equals(this.terminalMode));
            terminalHost.addView((View)this.nativeTerminalView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        } else {
            this.terminalFallbackView = this.createTerminalFallbackView();
            terminalHost.addView(this.terminalFallbackView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
            this.setTerminalFallbackVisible(true);
            this.showTerminalFallbackHint();
        }
        terminalHost.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){

            public void onGlobalLayout() {
                AiranDeskTerminalActivity.this.adjustTerminalForKeyboard();
            }
        });
        this.setDirectContent((View)terminalHost);
        WebRtcClient.requestTerminalStart(this.pendingTerminalRows, this.pendingTerminalCols);
        this.maybeWakeTerminal("terminal opened");
        terminalHost.post(new Runnable(){

            @Override
            public void run() {
                AiranDeskTerminalActivity.this.focusTerminalWebView();
            }
        });
    }
    protected View createTerminalFallbackView() {
        LinearLayout terminal = new LinearLayout((Context)this);
        terminal.setOrientation(1);
        terminal.setBackgroundColor(Color.rgb((int)12, (int)12, (int)12));
        this.terminalFallbackScroll = new ScrollView((Context)this);
        this.terminalFallbackScroll.setFillViewport(true);
        this.terminalFallbackScroll.setBackgroundColor(Color.rgb((int)12, (int)12, (int)12));
        this.terminalFallbackScroll.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                if (AiranDeskTerminalActivity.this.terminalFallbackInput != null) {
                    AiranDeskTerminalActivity.this.terminalFallbackInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager)AiranDeskTerminalActivity.this.getSystemService("input_method");
                    if (imm != null) {
                        imm.showSoftInput((View)AiranDeskTerminalActivity.this.terminalFallbackInput, 1);
                    }
                    return;
                }
                AiranDeskTerminalActivity.this.focusTerminalWebView();
            }
        });
        this.terminalFallbackScroll.setOnLongClickListener(new View.OnLongClickListener(){

            public boolean onLongClick(View v) {
                AiranDeskTerminalActivity.this.showTerminalMenu(v);
                return true;
            }
        });
        this.terminalFallbackText = this.uiFactory.text("", 13, Color.rgb((int)230, (int)230, (int)230), false, true);
        this.terminalFallbackText.setTypeface(Typeface.MONOSPACE);
        this.terminalFallbackText.setGravity(0x800033);
        this.terminalFallbackText.setIncludeFontPadding(false);
        this.terminalFallbackText.setLineSpacing(0.0f, 1.0f);
        this.terminalFallbackText.setPadding(this.dp(8), this.dp(8), this.dp(8), this.dp(8));
        this.terminalFallbackText.setTextIsSelectable(false);
        this.terminalFallbackText.setFocusable(false);
        this.terminalFallbackText.setBackgroundColor(Color.rgb((int)12, (int)12, (int)12));
        this.terminalFallbackScroll.addView((View)this.terminalFallbackText, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -2));
        terminal.addView((View)this.terminalFallbackScroll, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.terminalFallbackInput = this.edit("", this.getString(R.string.terminal_hint));
        this.terminalFallbackInput.setSingleLine(true);
        this.terminalFallbackInput.setOnEditorActionListener(new TextView.OnEditorActionListener(){

            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean enter = actionId == 6 || actionId == 5 || event != null && event.getAction() == 0 && event.getKeyCode() == 66;
                if (!enter) {
                    return false;
                }
                String value = AiranDeskTerminalActivity.this.terminalFallbackInput.getText().toString();
                if (value.length() > 0) {
                    WebRtcClient.sendTerminalInput(value + "\n");
                    AiranDeskTerminalActivity.this.terminalFallbackInput.setText((CharSequence)"");
                }
                return true;
            }
        });
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, this.dp(42));
        inputLp.topMargin = this.dp(8);
        terminal.addView((View)this.terminalFallbackInput, (ViewGroup.LayoutParams)inputLp);
        return terminal;
    }
    protected void focusTerminalWebView() {
        if (this.nativeTerminalView != null) {
            this.nativeTerminalView.focusInput();
            this.nativeTerminalView.postDelayed(new Runnable(){

                @Override
                public void run() {
                    AiranDeskTerminalActivity.this.adjustTerminalForKeyboard();
                }
            }, 180L);
            return;
        }
        if (this.terminalWebView == null) {
            if (this.terminalFallbackInput != null) {
                this.terminalFallbackInput.requestFocus();
                InputMethodManager imm = (InputMethodManager)this.getSystemService("input_method");
                if (imm != null) {
                    imm.showSoftInput((View)this.terminalFallbackInput, 1);
                }
            }
            return;
        }
        this.terminalWebView.requestFocus();
        this.terminalWebView.evaluateJavascript("window.airanFocus && window.airanFocus();", null);
        InputMethodManager imm = (InputMethodManager)this.getSystemService("input_method");
        if (imm != null) {
            imm.showSoftInput((View)this.terminalWebView, 1);
        }
        this.terminalWebView.postDelayed(new Runnable(){

            @Override
            public void run() {
                AiranDeskTerminalActivity.this.adjustTerminalForKeyboard();
            }
        }, 180L);
    }
    protected void applyTerminalZoom(float focusX, float focusY) {
        if (this.terminalWebView == null || !this.terminalWebReady) {
            return;
        }
        String script = "window.airanSetZoomFromNative && window.airanSetZoomFromNative(" + this.terminalZoomScale + "," + focusX + "," + focusY + ");";
        this.terminalWebView.evaluateJavascript(script, null);
    }
    protected void adjustTerminalForKeyboard() {
        boolean keyboardActive;
        int compareHeight;
        View terminalChild = this.nativeTerminalView != null ? this.nativeTerminalView : this.terminalWebView;
        if (this.terminalHostView == null || terminalChild == null || !"terminal".equals(this.currentTab)) {
            return;
        }
        Rect visible = new Rect();
        this.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int[] location = new int[2];
        this.terminalHostView.getLocationOnScreen(location);
        int hostHeight = this.terminalHostView.getHeight();
        if (hostHeight <= 0) {
            return;
        }
        int availableHeight = visible.bottom - location[1];
        int minHeight = this.dp(120);
        int targetHeight = availableHeight > minHeight && availableHeight < hostHeight ? availableHeight : -1;
        int n = compareHeight = targetHeight == -1 ? -1 : targetHeight;
        if (compareHeight == this.lastTerminalKeyboardAvailableHeight) {
            return;
        }
        this.lastTerminalKeyboardAvailableHeight = compareHeight;
        this.applyTerminalChildHeight(terminalChild, targetHeight);
        this.applyTerminalChildHeight(this.terminalFallbackView, targetHeight);
        boolean bl = keyboardActive = targetHeight != -1;
        if (this.nativeTerminalView != null) {
            this.nativeTerminalView.postDelayed(new Runnable(){

                @Override
                public void run() {
                    if (AiranDeskTerminalActivity.this.nativeTerminalView != null && "terminal".equals(AiranDeskTerminalActivity.this.currentTab)) {
                        AiranDeskTerminalActivity.this.nativeTerminalView.scrollCursorIntoView();
                    }
                }
            }, 80L);
        }
        if (this.terminalWebView != null) {
            this.terminalWebView.postDelayed(new Runnable(){

                @Override
                public void run() {
                    if (AiranDeskTerminalActivity.this.terminalWebView != null && "terminal".equals(AiranDeskTerminalActivity.this.currentTab)) {
                        AiranDeskTerminalActivity.this.terminalWebView.evaluateJavascript("window.airanKeyboardAdjusted && window.airanKeyboardAdjusted(" + (keyboardActive ? "true" : "false") + ");", null);
                    }
                }
            }, 80L);
        }
    }
    protected void applyTerminalChildHeight(View child, int height) {
        if (child == null) {
            return;
        }
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) {
            return;
        }
        if (lp.height == height) {
            return;
        }
        lp.height = height;
        child.setLayoutParams(lp);
    }
    protected void showTerminalMenu(final View anchor) {
        if (this.nativeTerminalView != null) {
            this.terminalHasSelection = this.nativeTerminalView.hasSelection();
            this.showTerminalMenuNow(anchor, this.terminalHasSelection);
            return;
        }
        if (this.terminalWebView != null) {
            this.terminalWebView.evaluateJavascript("window.airanHasSelection ? window.airanHasSelection() : false;", (ValueCallback)new ValueCallback<String>(){

                public void onReceiveValue(String value) {
                    AiranDeskTerminalActivity.this.terminalHasSelection = "true".equals(value);
                    AiranDeskTerminalActivity.this.showTerminalMenuNow(anchor, AiranDeskTerminalActivity.this.terminalHasSelection);
                }
            });
            return;
        }
        this.showTerminalMenuNow(anchor, this.terminalHasSelection);
    }
    protected void showTerminalMenuNow(View anchor, boolean hasSelection) {
        TerminalActionMenu.show((Context)this, anchor, this.isNativeTerminalSelectionMode() || this.terminalSelectionMode, hasSelection, new TerminalActionMenu.Listener(){

            @Override
            public void onSelectAll() {
                if (AiranDeskTerminalActivity.this.nativeTerminalView != null) {
                    AiranDeskTerminalActivity.this.nativeTerminalView.selectAll();
                    AiranDeskTerminalActivity.this.nativeTerminalSelection = AiranDeskTerminalActivity.this.nativeTerminalView.selectedText();
                    AiranDeskTerminalActivity.this.terminalHasSelection = AiranDeskTerminalActivity.this.nativeTerminalView.hasSelection();
                    AiranDeskTerminalActivity.this.showUserStatus(AiranDeskTerminalActivity.this.terminalHasSelection ? AiranDeskTerminalActivity.this.getString(R.string.terminal_status_all_selected) : AiranDeskTerminalActivity.this.getString(R.string.status_nothing_selected));
                    return;
                }
                AiranDeskTerminalActivity.this.runTerminalJavascript("window.airanSelectAll && window.airanSelectAll();");
            }

            @Override
            public void onCopy() {
                if (!AiranDeskTerminalActivity.this.terminalHasSelection) {
                    AiranDeskTerminalActivity.this.showUserStatus(AiranDeskTerminalActivity.this.getString(R.string.status_nothing_selected));
                    return;
                }
                if (AiranDeskTerminalActivity.this.nativeTerminalView != null) {
                    AiranDeskTerminalActivity.this.nativeTerminalSelection = AiranDeskTerminalActivity.this.nativeTerminalView.selectedText();
                    AiranDeskTerminalActivity.this.terminalHasSelection = AiranDeskTerminalActivity.this.nativeTerminalView.hasSelection();
                    AiranDeskTerminalActivity.this.copyTextToClipboard(AiranDeskTerminalActivity.this.nativeTerminalSelection, "Terminal");
                    return;
                }
                AiranDeskTerminalActivity.this.runTerminalJavascript("window.airanCopySelection && window.airanCopySelection();");
            }

            @Override
            public void onPaste() {
                AiranDeskTerminalActivity.this.pasteClipboardToTerminal();
            }

            @Override
            public void onToggleFreeSelect() {
                if (AiranDeskTerminalActivity.this.nativeTerminalView != null) {
                    boolean enabled = !AiranDeskTerminalActivity.this.nativeTerminalView.isSelectionMode();
                    AiranDeskTerminalActivity.this.nativeTerminalView.setSelectionMode(enabled);
                    AiranDeskTerminalActivity.this.terminalHasSelection = AiranDeskTerminalActivity.this.nativeTerminalView.hasSelection();
                    AiranDeskTerminalActivity.this.nativeTerminalSelection = AiranDeskTerminalActivity.this.terminalHasSelection ? AiranDeskTerminalActivity.this.nativeTerminalView.selectedText() : "";
                    AiranDeskTerminalActivity.this.showUserStatus(enabled ? AiranDeskTerminalActivity.this.getString(R.string.terminal_status_free_select_enabled) : AiranDeskTerminalActivity.this.getString(R.string.terminal_status_free_select_disabled));
                    return;
                }
                AiranDeskTerminalActivity.this.terminalSelectionMode = !AiranDeskTerminalActivity.this.terminalSelectionMode;
                AiranDeskTerminalActivity.this.runTerminalJavascript("window.airanSetSelectionMode && window.airanSetSelectionMode(" + (AiranDeskTerminalActivity.this.terminalSelectionMode ? "true" : "false") + ");");
                AiranDeskTerminalActivity.this.showUserStatus(AiranDeskTerminalActivity.this.terminalSelectionMode ? AiranDeskTerminalActivity.this.getString(R.string.terminal_status_free_select_enabled) : AiranDeskTerminalActivity.this.getString(R.string.terminal_status_free_select_disabled));
            }
        });
    }
    protected boolean isNativeTerminalSelectionMode() {
        return this.nativeTerminalView != null && this.nativeTerminalView.isSelectionMode();
    }
    protected void runTerminalJavascript(String script) {
        if (this.terminalWebView == null || script == null || script.length() == 0) {
            return;
        }
        this.terminalWebView.evaluateJavascript(script, null);
    }
    protected void pasteClipboardToTerminal() {
        String text = ClipboardUtils.primaryText((Context)this);
        if (text.length() == 0) {
            this.showUserStatus("Clipboard is empty");
            return;
        }
        if (this.nativeTerminalView != null) {
            this.nativeTerminalView.pasteText(text);
            return;
        }
        try {
            this.runTerminalJavascript(TerminalWebScripts.pasteText(text));
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
    protected void fitTerminalWebView() {
        if (this.terminalWebView == null || !this.terminalWebReady) {
            return;
        }
        this.terminalWebView.evaluateJavascript(TerminalWebScripts.layout(), null);
    }
    protected boolean appendTerminalText(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        try {
            return this.appendTerminalBytes(text.getBytes("UTF-8"));
        }
        catch (Exception exception) {
            return false;
        }
    }
    protected boolean appendTerminalBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        data = this.terminalOutputDecoder.normalizeBytes(data);
        if (data == null || data.length == 0) {
            return false;
        }
        if (this.nativeTerminalView != null) {
            this.nativeTerminalView.write(data);
            this.nativeTerminalView.clearSelection();
            this.terminalHasSelection = false;
            this.nativeTerminalSelection = "";
            return true;
        }
        boolean visible = this.appendTerminalBytesToFallback(data);
        if (this.terminalWebView == null || !this.terminalWebReady) {
            this.pendingTerminalOutput.write(data, 0, data.length);
            this.pendingTerminalVisibleOutput = this.pendingTerminalVisibleOutput || visible;
            this.onStatus("terminal output pending: " + this.pendingTerminalOutput.size() + " bytes");
            return visible;
        }
        this.writeTerminalBytesToWebView(data, visible);
        return visible;
    }
    protected void flushPendingTerminalOutput() {
        if (this.terminalWebView == null || !this.terminalWebReady || this.pendingTerminalOutput.size() == 0) {
            return;
        }
        byte[] data = this.pendingTerminalOutput.toByteArray();
        boolean visible = this.pendingTerminalVisibleOutput;
        this.pendingTerminalOutput.reset();
        this.pendingTerminalVisibleOutput = false;
        this.onStatus("terminal output flush: " + data.length + " bytes");
        this.writeTerminalBytesToWebView(data, visible);
    }
    protected void writeTerminalBytesToWebView(byte[] data, final boolean visibleOutput) {
        try {
            if (this.terminalWebView == null) {
                return;
            }
            this.terminalWebView.evaluateJavascript(TerminalWebScripts.writeBytes(data), (ValueCallback)new ValueCallback<String>(){

                public void onReceiveValue(String value) {
                    if ("true".equals(value) && visibleOutput) {
                        AiranDeskTerminalActivity.this.terminalWebRenderedOutput = true;
                        AiranDeskTerminalActivity.this.hideTerminalFallbackIfWebReady();
                    } else if (!"true".equals(value)) {
                        AiranDeskTerminalActivity.this.showTerminalFallbackHint();
                        AiranDeskTerminalActivity.this.setTerminalFallbackVisible(true);
                    }
                }
            });
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
    protected void showTerminalFallbackHint() {
        if (this.terminalFallbackText == null || this.terminalFallbackTranscript.length() > 0) {
            return;
        }
        this.terminalFallbackHintVisible = true;
        this.terminalFallbackText.setText((CharSequence)("[" + this.getString(R.string.terminal_connecting) + "]"));
    }
    protected void clearTerminalFallbackHintIfNeeded() {
        if (!this.terminalFallbackHintVisible || this.terminalFallbackText == null) {
            return;
        }
        this.terminalFallbackHintVisible = false;
        if (this.terminalFallbackTranscript.length() == 0) {
            this.terminalFallbackText.setText((CharSequence)"");
        }
    }
    protected boolean appendTerminalBytesToFallback(byte[] data) {
        if (data == null || data.length == 0 || this.terminalFallbackText == null) {
            return false;
        }
        String text = this.terminalOutputDecoder.decodeText(data);
        TerminalFallbackScreen.Result result = this.terminalFallbackScreen.apply(text);
        if (result.renderedText != null) {
            this.terminalFallbackTranscript.setLength(0);
            this.terminalFallbackTranscript.append(result.renderedText);
            this.terminalFallbackText.setText((CharSequence)result.renderedText);
        }
        if (result.visibleOutput) {
            this.clearTerminalFallbackHintIfNeeded();
            return true;
        }
        String normalized = TerminalTextUtils.normalizeFallbackText(text);
        if (normalized.length() == 0) {
            return false;
        }
        this.clearTerminalFallbackHintIfNeeded();
        boolean shouldAutoScroll = this.terminalFallbackTranscript.length() == 0 || this.isTerminalFallbackNearBottom();
        this.terminalFallbackTranscript.append(normalized);
        int max = 120000;
        if (this.terminalFallbackTranscript.length() > max) {
            this.terminalFallbackTranscript.delete(0, this.terminalFallbackTranscript.length() - max);
        }
        this.terminalFallbackText.setText((CharSequence)this.terminalFallbackTranscript.toString());
        if (shouldAutoScroll && this.terminalFallbackScroll != null) {
            this.terminalFallbackScroll.post(new Runnable(){

                @Override
                public void run() {
                    if (AiranDeskTerminalActivity.this.terminalFallbackScroll != null) {
                        AiranDeskTerminalActivity.this.terminalFallbackScroll.fullScroll(130);
                    }
                }
            });
        }
        return true;
    }
    protected boolean isTerminalFallbackNearBottom() {
        if (this.terminalFallbackScroll == null || this.terminalFallbackScroll.getChildCount() == 0) {
            return true;
        }
        View child = this.terminalFallbackScroll.getChildAt(0);
        if (child == null) {
            return true;
        }
        int distance = child.getBottom() - (this.terminalFallbackScroll.getHeight() + this.terminalFallbackScroll.getScrollY());
        return distance <= this.dp(48);
    }
    protected void markTerminalWebReady(String source) {
        if (this.terminalWebView == null) {
            return;
        }
        if (!this.isTerminalWebViewLaidOut()) {
            if (!this.terminalLayoutRetryScheduled) {
                this.terminalLayoutRetryScheduled = true;
                this.onStatus("terminal web waiting for layout: " + source);
                this.terminalWebView.postDelayed(new Runnable(){

                    @Override
                    public void run() {
                        AiranDeskTerminalActivity.this.terminalLayoutRetryScheduled = false;
                        AiranDeskTerminalActivity.this.markTerminalWebReady("layout");
                    }
                }, 80L);
            }
            return;
        }
        boolean firstReady = !this.terminalWebReady;
        this.terminalWebReady = true;
        if (this.terminalWebView != null) {
            this.terminalWebView.setVisibility(0);
        }
        this.onStatus(firstReady ? "terminal web ready: " + source : "terminal web ready refresh: " + source);
        if (!this.terminalWaitingHintShown) {
            this.terminalWaitingHintShown = true;
            this.showTerminalFallbackHint();
        }
        this.fitTerminalWebView();
        this.updateTerminalLocalEcho();
        this.flushPendingTerminalOutput();
        this.maybeWakeTerminal("web ready");
    }
    protected void updateTerminalLocalEcho() {
        if (this.nativeTerminalView != null) {
            this.nativeTerminalView.setLocalEchoEnabled("pipe".equals(this.terminalMode));
            return;
        }
        if (this.terminalWebView == null || !this.terminalWebReady) {
            return;
        }
        boolean localEcho = "pipe".equals(this.terminalMode);
        this.runTerminalJavascript("window.airanSetLocalEcho && window.airanSetLocalEcho(" + (localEcho ? "true" : "false") + ");");
    }
    protected boolean isTerminalWebViewLaidOut() {
        return this.terminalWebView != null && this.terminalWebView.getWidth() > 0 && this.terminalWebView.getHeight() > 0;
    }
    protected void maybeWakeTerminal(String source) {
        this.terminalWakeScheduled = false;
    }
    protected void sendTerminalWakeInput() {
        if (!"terminal".equals(this.currentTab)) {
            return;
        }
        WebRtcClient.requestTerminalStart(this.pendingTerminalRows, this.pendingTerminalCols);
        WebRtcClient.sendTerminalResize(this.pendingTerminalRows, this.pendingTerminalCols);
        this.onStatus("terminal wake refresh sent");
    }
    protected void setTerminalFallbackVisible(boolean visible) {
        if (this.terminalFallbackView != null) {
            this.terminalFallbackView.setVisibility(visible ? 0 : 8);
        }
        if (this.terminalFallbackScroll != null) {
            this.terminalFallbackScroll.setVisibility(visible ? 0 : 8);
        }
    }
    protected void injectTerminalPathTracking() {
        View scheduler;
        if (!this.terminalPathTracking || this.terminalPathTrackingInjected || !"terminal".equals(this.currentTab)) {
            return;
        }
        this.terminalPathTrackingInjected = true;
        scheduler = this.nativeTerminalView != null ? this.nativeTerminalView : this.contentHost;
        if (scheduler == null) {
            this.sendTerminalPathTrackingScript();
            return;
        }
        scheduler.postDelayed(new Runnable(){

            @Override
            public void run() {
                AiranDeskTerminalActivity.this.sendTerminalPathTrackingScript();
            }
        }, 150L);
    }
    protected void sendTerminalPathTrackingScript() {
        if (!"terminal".equals(this.currentTab)) {
            return;
        }
        this.sendTerminalInputKeys(TerminalPathTrackingScript.forShell(this.terminalOs, this.terminalShell));
    }
    protected void sendTerminalInputKeys(String text) {
        this.sendTerminalInputKeys(text, 0);
    }
    protected void sendTerminalInputKeys(String text, int index) {
        if (text == null || index >= text.length() || !"terminal".equals(this.currentTab)) {
            return;
        }
        WebRtcClient.sendTerminalInput(text.substring(index));
    }
    protected void noteInvisibleTerminalBytes(int count) {
        if (!"terminal".equals(this.currentTab) || this.terminalFallbackText == null || count <= 0 || this.terminalRemoteOutputSeen) {
            return;
        }
        this.terminalInvisibleByteCount += count;
        if (this.terminalFallbackDiagnosticShown) {
            return;
        }
        if (!this.terminalFallbackHintVisible && this.terminalFallbackTranscript.length() > 0) {
            return;
        }
        this.clearTerminalFallbackHintIfNeeded();
        this.terminalFallbackDiagnosticShown = true;
        this.terminalFallbackTranscript.setLength(0);
        this.terminalFallbackTranscript.append(this.getString(R.string.terminal_waiting_for_prompt, new Object[]{this.terminalInvisibleByteCount}));
        this.terminalFallbackText.setText((CharSequence)this.terminalFallbackTranscript.toString());
    }
    protected void hideTerminalFallbackIfWebReady() {
        if (this.terminalWebReady && this.terminalWebRenderedOutput) {
            this.setTerminalFallbackVisible(false);
        }
    }

}
