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
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
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
abstract class AiranDeskDesktopInteractionActivity
extends AiranDeskTerminalActivity {
    protected EditText createDesktopKeyboardInput() {
        final EditText input = new EditText((Context)this){

            @Override
            public InputConnection onCreateInputConnection(final EditorInfo outAttrs) {
                outAttrs.inputType = this.getInputType();
                outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE;
                return new BaseInputConnection(this, true){

                    @Override
                    public Editable getEditable() {
                        return AiranDeskDesktopInteractionActivity.this.desktopKeyboardEditable;
                    }

                    @Override
                    public boolean commitText(CharSequence text, int newCursorPosition) {
                        String value = text == null ? "" : text.toString();
                        if (value.length() > 0 && (!AiranDeskDesktopInteractionActivity.this.desktopComposingTextSent || !value.equals(AiranDeskDesktopInteractionActivity.this.desktopComposingText))) {
                            AiranDeskDesktopInteractionActivity.this.sendDesktopCommittedText(value);
                        }
                        AiranDeskDesktopInteractionActivity.this.clearDesktopComposingState();
                        return true;
                    }

                    @Override
                    public boolean setComposingText(CharSequence text, int newCursorPosition) {
                        String value = text == null ? "" : text.toString();
                        if (value.length() == 0) {
                            AiranDeskDesktopInteractionActivity.this.clearDesktopComposingState();
                            return true;
                        }
                        if (AiranDeskDesktopInteractionActivity.this.isDirectAsciiComposition(value)) {
                            if (AiranDeskDesktopInteractionActivity.this.desktopComposingTextSent && value.startsWith(AiranDeskDesktopInteractionActivity.this.desktopComposingText)) {
                                AiranDeskDesktopInteractionActivity.this.sendDesktopCommittedText(value.substring(AiranDeskDesktopInteractionActivity.this.desktopComposingText.length()));
                            } else if (!AiranDeskDesktopInteractionActivity.this.desktopComposingTextSent) {
                                AiranDeskDesktopInteractionActivity.this.sendDesktopCommittedText(value);
                            }
                            AiranDeskDesktopInteractionActivity.this.desktopComposingText = value;
                            AiranDeskDesktopInteractionActivity.this.desktopComposingTextSent = true;
                            AiranDeskDesktopInteractionActivity.this.desktopKeyboardEditable.clear();
                            return true;
                        }
                        AiranDeskDesktopInteractionActivity.this.desktopComposingText = value;
                        AiranDeskDesktopInteractionActivity.this.desktopComposingTextSent = false;
                        AiranDeskDesktopInteractionActivity.this.desktopKeyboardEditable.clear();
                        return true;
                    }

                    @Override
                    public boolean finishComposingText() {
                        AiranDeskDesktopInteractionActivity.this.clearDesktopComposingState();
                        return true;
                    }

                    @Override
                    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                        AiranDeskDesktopInteractionActivity.this.clearDesktopComposingState();
                        if (beforeLength > 0) {
                            AiranDeskDesktopInteractionActivity.this.sendDesktopKeyTap(8);
                            return true;
                        }
                        if (afterLength > 0) {
                            AiranDeskDesktopInteractionActivity.this.sendDesktopKeyTap(46);
                            return true;
                        }
                        return super.deleteSurroundingText(beforeLength, afterLength);
                    }

                    @Override
                    public boolean sendKeyEvent(KeyEvent event) {
                        if (event != null && event.getAction() == 0) {
                            AiranDeskDesktopInteractionActivity.this.clearDesktopComposingState();
                        }
                        return AiranDeskDesktopInteractionActivity.this.handleDesktopKeyboardKeyEvent(event) || super.sendKeyEvent(event);
                    }
                };
            }
        };
        input.setSingleLine(false);
        input.setTextColor(0);
        input.setHintTextColor(0);
        input.setCursorVisible(false);
        input.setBackgroundColor(0);
        input.setPadding(0, 0, 0, 0);
        input.setInputType(131073);
        input.setOnKeyListener(new View.OnKeyListener(){

            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != 0) {
                    return false;
                }
                return AiranDeskDesktopInteractionActivity.this.handleDesktopKeyboardKeyEvent(event);
            }
        });
        return input;
    }
    protected void sendDesktopCommittedText(String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        WebRtcClient.sendKeyboardText(text);
        this.onStatus("desktop keyboard text committed: " + text.length());
    }
    protected void clearDesktopComposingState() {
        this.desktopComposingText = "";
        this.desktopComposingTextSent = false;
        this.desktopKeyboardEditable.clear();
    }
    protected boolean isDirectAsciiComposition(String value) {
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
    protected boolean handleDesktopKeyboardKeyEvent(KeyEvent event) {
        if (event == null || event.getAction() != 0) {
            return false;
        }
        int winKey = KeyCodeMapper.androidKeyCodeToWinKey(event.getKeyCode());
        if (winKey <= 0) {
            return false;
        }
        this.sendDesktopKeyTap(winKey);
        return true;
    }
    protected void focusDesktopKeyboard() {
        if (this.desktopKeyboardInput == null) {
            return;
        }
        this.desktopKeyboardInput.requestFocus();
        InputMethodManager imm = (InputMethodManager)this.getSystemService("input_method");
        if (imm != null) {
            imm.showSoftInput((View)this.desktopKeyboardInput, 1);
        }
    }
    protected void adjustDesktopForKeyboard() {
        if (this.controlWindowCard == null || !"desktop".equals(this.currentTab)) {
            return;
        }
        Rect visible = new Rect();
        this.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int[] location = new int[2];
        this.controlWindowCard.getLocationOnScreen(location);
        int bottom = location[1] + this.controlWindowCard.getHeight();
        int nextInset = Math.max(0, bottom - visible.bottom);
        if (Math.abs(nextInset - this.desktopKeyboardInset) < this.dp(2)) {
            return;
        }
        this.desktopKeyboardInset = nextInset;
        this.scheduleDesktopVideoLayout(false);
    }
    protected void sendDesktopKeyTap(int winKey) {
        WebRtcClient.sendKeyboardEvent(winKey, true);
        WebRtcClient.sendKeyboardEvent(winKey, false);
    }
    protected void sendDesktopShortcut(int ... winKeys) {
        if (winKeys == null || winKeys.length == 0) {
            return;
        }
        for (int winKey : winKeys) {
            WebRtcClient.sendKeyboardEvent(winKey, true);
        }
        for (int i = winKeys.length - 1; i >= 0; --i) {
            WebRtcClient.sendKeyboardEvent(winKeys[i], false);
        }
    }
    protected void toggleDesktopModifier(int winKey) {
        if (winKey == 17) {
            boolean next;
            this.desktopCtrlHeld = next = !this.desktopCtrlHeld;
            WebRtcClient.sendKeyboardEvent(17, next);
        } else if (winKey == 16) {
            boolean next;
            this.desktopShiftHeld = next = !this.desktopShiftHeld;
            WebRtcClient.sendKeyboardEvent(16, next);
        } else if (winKey == 18) {
            boolean next;
            this.desktopAltHeld = next = !this.desktopAltHeld;
            WebRtcClient.sendKeyboardEvent(18, next);
        } else if (winKey == 91) {
            boolean next;
            this.desktopWinHeld = next = !this.desktopWinHeld;
            WebRtcClient.sendKeyboardEvent(91, next);
        }
        this.updateDesktopModifierButtons();
    }
    protected void releaseDesktopModifierKeys() {
        if (this.desktopCtrlHeld) {
            WebRtcClient.sendKeyboardEvent(17, false);
        }
        if (this.desktopShiftHeld) {
            WebRtcClient.sendKeyboardEvent(16, false);
        }
        if (this.desktopAltHeld) {
            WebRtcClient.sendKeyboardEvent(18, false);
        }
        if (this.desktopWinHeld) {
            WebRtcClient.sendKeyboardEvent(91, false);
        }
        this.desktopCtrlHeld = false;
        this.desktopShiftHeld = false;
        this.desktopAltHeld = false;
        this.desktopWinHeld = false;
        this.updateDesktopModifierButtons();
    }
    protected void updateDesktopModifierButtons() {
        this.styleModifierChip(this.desktopCtrlButton, this.desktopCtrlHeld);
        this.styleModifierChip(this.desktopShiftButton, this.desktopShiftHeld);
        this.styleModifierChip(this.desktopAltButton, this.desktopAltHeld);
        this.styleModifierChip(this.desktopWinButton, this.desktopWinHeld);
    }
    protected void styleModifierChip(Button button, boolean active) {
        if (button == null) {
            return;
        }
        button.setTextColor(active ? C_ON_PRIMARY : C_TEXT);
        button.setBackground((Drawable)this.uiFactory.border(active ? C_PRIMARY : C_CONTAINER, active ? C_PRIMARY : C_OUTLINE, this.dp(8), 1));
    }
    protected void sendDesktopMouseClick(int button) {
        float x = this.desktopHasLastPointer ? this.desktopLastPointerX : 0.5f;
        float y = this.desktopHasLastPointer ? this.desktopLastPointerY : 0.5f;
        this.sendDesktopMouseClickAt(x, y, button);
    }
    protected void sendDesktopMouseClickAt(float x, float y, int button) {
        WebRtcClient.sendPointer(x, y, true, false, button);
        WebRtcClient.sendPointer(x, y, false, true, button);
    }
    protected int desktopVisibleVideoWidth() {
        return WebRtcClient.remoteVisibleWidth();
    }
    protected int desktopVisibleVideoHeight() {
        return WebRtcClient.remoteVisibleHeight();
    }
    protected void applyRemoteVisibleRectToRenderer() {
        if (this.renderer == null) {
            return;
        }
        this.renderer.setVisibleRect(WebRtcClient.remoteVisibleWidth(), WebRtcClient.remoteVisibleHeight(),
                WebRtcClient.remotePadLeft(), WebRtcClient.remotePadTop(),
                WebRtcClient.remotePadRight(), WebRtcClient.remotePadBottom());
    }
    protected void configureDesktopVideoLayout(boolean resetPan) {
        int baseH;
        int baseW;
        if (this.controlWindowCard == null || this.renderer == null) {
            return;
        }
        this.applyRemoteVisibleRectToRenderer();
        int viewportW = this.controlWindowCard.getWidth();
        int viewportH = this.controlWindowCard.getHeight();
        if (viewportW <= 0 || viewportH <= 0) {
            return;
        }
        viewportH = this.desktopEffectiveViewportHeight(viewportH);
        int visibleW = this.desktopVisibleVideoWidth();
        int visibleH = this.desktopVisibleVideoHeight();
        if (DISPLAY_MODE_ACTUAL.equals(this.desktopDisplayMode) && visibleW > 0 && visibleH > 0) {
            baseW = visibleW;
            baseH = visibleH;
        } else {
            float aspect = 1.7777778f;
            if (visibleW > 0 && visibleH > 0) {
                aspect = (float)visibleW / Math.max(1.0f, (float)visibleH);
            }
            baseH = viewportH;
            baseW = Math.round((float)baseH * aspect);
        }
        boolean sizeChanged = baseW != this.desktopBaseVideoWidth || baseH != this.desktopBaseVideoHeight;
        this.desktopBaseVideoWidth = baseW;
        this.desktopBaseVideoHeight = baseH;
        ViewGroup.LayoutParams lp = this.renderer.getLayoutParams();
        if (lp == null || lp.width != baseW || lp.height != baseH) {
            this.renderer.setLayoutParams((ViewGroup.LayoutParams)new FrameLayout.LayoutParams(baseW, baseH));
        }
        this.renderer.setPivotX(0.0f);
        this.renderer.setPivotY(0.0f);
        if (resetPan || sizeChanged) {
            this.desktopZoom = Math.max(1.0f, this.desktopZoom);
            this.desktopPanX = ((float)viewportW - (float)baseW * this.desktopZoom) / 2.0f;
            this.desktopPanY = ((float)viewportH - (float)baseH * this.desktopZoom) / 2.0f;
        }
        this.applyDesktopVideoTransform();
    }
    protected int desktopEffectiveViewportHeight(int height) {
        int inset = this.desktopKeyboardInset;
        if (this.desktopActionDrawer != null && this.desktopActionDrawer.getVisibility() == 0) {
            inset = Math.max(inset, this.desktopActionDrawer.getHeight() + this.dp(16));
        }
        return DesktopViewportMath.effectiveViewportHeight(height, inset, this.dp(180));
    }
    protected void scheduleDesktopVideoLayout(boolean resetPan) {
        if (this.controlWindowCard == null) {
            return;
        }
        boolean bl = this.desktopLayoutResetPending = this.desktopLayoutResetPending || resetPan;
        if (this.desktopLayoutUpdateScheduled) {
            return;
        }
        this.desktopLayoutUpdateScheduled = true;
        this.controlWindowCard.post(new Runnable(){

            @Override
            public void run() {
                AiranDeskDesktopInteractionActivity.this.desktopLayoutUpdateScheduled = false;
                boolean shouldReset = AiranDeskDesktopInteractionActivity.this.desktopLayoutResetPending;
                AiranDeskDesktopInteractionActivity.this.desktopLayoutResetPending = false;
                AiranDeskDesktopInteractionActivity.this.configureDesktopVideoLayout(shouldReset);
            }
        });
    }
    protected boolean handleDesktopTouch(View view, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == 5 && event.getPointerCount() >= 2) {
            this.cancelDesktopLongPress(view);
            this.desktopTouchpadBlockedByPinch = true;
            if (this.desktopPinchZoomEnabled) {
                this.startDesktopPinch(event);
            } else {
                this.startDesktopRemoteTwoFingerGesture(event);
            }
            return true;
        }
        if (this.desktopTouchpadBlockedByPinch) {
            if (action == 2 && event.getPointerCount() >= 2) {
                if (this.desktopPinchZoomEnabled) {
                    this.updateDesktopPinch(event);
                } else {
                    this.updateDesktopRemoteTwoFingerGesture(event);
                }
                return true;
            }
            if (action == 1 || action == 3) {
                this.desktopTouchpadBlockedByPinch = false;
                this.desktopGestureMode = 0;
            }
            this.cancelDesktopLongPress(view);
            return true;
        }
        return this.handleDesktopDirectTouch(view, event);
    }
    protected boolean handleDesktopDirectTouch(View view, MotionEvent event) {
        if (event == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == 0) {
            this.cancelDesktopLongPress(view);
            this.desktopViewportDragging = false;
            this.desktopMouseLongClickTriggered = false;
            this.desktopGestureMode = 1;
            this.desktopTouchDownViewportX = event.getX();
            this.desktopTouchDownViewportY = event.getY();
            this.desktopMouseDragDownX = event.getRawX();
            this.desktopMouseDragDownY = event.getRawY();
            this.desktopViewportDragLastRawX = event.getRawX();
            this.desktopViewportDragLastRawY = event.getRawY();
            this.pendingDesktopLongPress = new Runnable(){

                @Override
                public void run() {
                    if (AiranDeskDesktopInteractionActivity.this.desktopViewportDragging) {
                        return;
                    }
                    AiranDeskDesktopInteractionActivity.this.desktopMouseLongClickTriggered = true;
                    AiranDeskDesktopInteractionActivity.this.desktopGestureMode = 4;
                    float[] p = AiranDeskDesktopInteractionActivity.this.desktopNormalizedPoint(AiranDeskDesktopInteractionActivity.this.desktopTouchDownViewportX, AiranDeskDesktopInteractionActivity.this.desktopTouchDownViewportY);
                    AiranDeskDesktopInteractionActivity.this.sendDesktopMouseClickAt(p[0], p[1], 2);
                }
            };
            view.postDelayed(this.pendingDesktopLongPress, (long)ViewConfiguration.getLongPressTimeout());
            return true;
        }
        if (action == 2) {
            if (this.moveDesktopViewportByTouch(event)) {
                this.cancelDesktopLongPress(view);
            }
            return true;
        }
        if (action == 1 || action == 3) {
            this.cancelDesktopLongPress(view);
            if (action == 1) {
                if (this.desktopViewportDragging) {
                    this.moveDesktopViewportByTouch(event);
                } else if (!this.desktopMouseLongClickTriggered) {
                    float[] p = this.desktopNormalizedPoint(event.getX(), event.getY());
                    this.sendDesktopMouseClickAt(p[0], p[1], 1);
                }
            }
            this.desktopViewportDragging = false;
            this.desktopMouseLongClickTriggered = false;
            this.desktopGestureMode = 0;
            return true;
        }
        return true;
    }
    protected boolean handleDesktopMouseModeTouch(View view, MotionEvent event) {
        if (event == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == 0) {
            this.cancelDesktopLongPress(view);
            this.desktopMouseDragging = false;
            this.desktopViewportDragging = false;
            this.desktopMouseLongClickTriggered = false;
            this.desktopGestureMode = 1;
            this.desktopMouseDragDownX = event.getRawX();
            this.desktopMouseDragDownY = event.getRawY();
            this.desktopViewportDragLastRawX = event.getRawX();
            this.desktopViewportDragLastRawY = event.getRawY();
            this.desktopMouseOverlayStartX = this.desktopMouseOverlayView == null ? 0.0f : this.desktopMouseOverlayView.getX();
            this.desktopMouseOverlayStartY = this.desktopMouseOverlayView == null ? 0.0f : this.desktopMouseOverlayView.getY();
            this.pendingDesktopLongPress = new Runnable(){

                @Override
                public void run() {
                    if (AiranDeskDesktopInteractionActivity.this.desktopMouseDragging) {
                        return;
                    }
                    AiranDeskDesktopInteractionActivity.this.desktopMouseLongClickTriggered = true;
                    AiranDeskDesktopInteractionActivity.this.desktopGestureMode = 4;
                    AiranDeskDesktopInteractionActivity.this.sendDesktopMouseClick(2);
                }
            };
            view.postDelayed(this.pendingDesktopLongPress, (long)ViewConfiguration.getLongPressTimeout());
            return true;
        }
        if (action == 2) {
            float dx = event.getRawX() - this.desktopMouseDragDownX;
            float dy = event.getRawY() - this.desktopMouseDragDownY;
            int slop = ViewConfiguration.get((Context)this).getScaledTouchSlop();
            if (this.desktopMouseExpanded && !this.desktopMouseDragging && dx * dx + dy * dy > (float)(slop * slop)) {
                this.desktopMouseDragging = true;
                this.cancelDesktopLongPress(view);
            }
            if (this.desktopMouseDragging) {
                this.moveDesktopMouseOverlayTo(this.desktopMouseOverlayStartX + dx, this.desktopMouseOverlayStartY + dy);
                this.syncDesktopPointerFromMouseOverlay();
                return true;
            }
            if (this.moveDesktopViewportByTouch(event)) {
                this.cancelDesktopLongPress(view);
                return true;
            }
            return true;
        }
        if (action == 1 || action == 3) {
            this.cancelDesktopLongPress(view);
            if (action == 1) {
                if (this.desktopMouseDragging) {
                    this.moveDesktopMouseOverlayTo(this.desktopMouseOverlayStartX + (event.getRawX() - this.desktopMouseDragDownX), this.desktopMouseOverlayStartY + (event.getRawY() - this.desktopMouseDragDownY));
                    this.syncDesktopPointerFromMouseOverlay();
                } else if (this.desktopViewportDragging) {
                    this.moveDesktopViewportByTouch(event);
                }
                if (!(this.desktopViewportDragging || this.desktopMouseDragging || this.desktopMouseLongClickTriggered)) {
                    boolean doubleTap;
                    float[] p = this.desktopNormalizedPoint(event.getX(), event.getY());
                    this.sendDesktopMouseClickAt(p[0], p[1], 1);
                    long now = System.currentTimeMillis();
                    float tapDx = event.getX() - this.desktopLastTapX;
                    float tapDy = event.getY() - this.desktopLastTapY;
                    int slop = ViewConfiguration.get((Context)this).getScaledDoubleTapSlop();
                    boolean bl = doubleTap = now - this.desktopLastTapMs <= (long)ViewConfiguration.getDoubleTapTimeout() && tapDx * tapDx + tapDy * tapDy <= (float)(slop * slop);
                    if (doubleTap) {
                        this.desktopLastTapMs = 0L;
                    } else {
                        this.desktopLastTapMs = now;
                        this.desktopLastTapX = event.getX();
                        this.desktopLastTapY = event.getY();
                    }
                }
            }
            this.desktopGestureMode = 0;
            this.desktopMouseDragging = false;
            this.desktopViewportDragging = false;
            this.desktopMouseLongClickTriggered = false;
        }
        return true;
    }
    protected boolean moveDesktopViewportByTouch(MotionEvent event) {
        boolean canPanY;
        if (event == null || event.getPointerCount() == 0) {
            return false;
        }
        float scaledW = (float)this.desktopBaseVideoWidth * this.desktopZoom;
        float scaledH = (float)this.desktopBaseVideoHeight * this.desktopZoom;
        int viewportW = this.controlWindowCard == null ? 0 : this.controlWindowCard.getWidth();
        int viewportH = this.controlWindowCard == null ? 0 : this.desktopEffectiveViewportHeight(this.controlWindowCard.getHeight());
        boolean canPanX = scaledW + this.desktopMouseViewportPaddingLeft() + this.desktopMouseViewportPaddingRight() > (float)(viewportW + 1);
        boolean bl = canPanY = scaledH + this.desktopMouseViewportPaddingTop() + this.desktopMouseViewportPaddingBottom() > (float)(viewportH + 1);
        if (!canPanX && !canPanY) {
            return false;
        }
        float totalDx = event.getRawX() - this.desktopMouseDragDownX;
        float totalDy = event.getRawY() - this.desktopMouseDragDownY;
        int slop = ViewConfiguration.get((Context)this).getScaledTouchSlop();
        if (!this.desktopViewportDragging && !this.desktopMouseDragging && totalDx * totalDx + totalDy * totalDy > (float)(slop * slop) && Math.abs(totalDx) > Math.abs(totalDy) * 1.15f && canPanX) {
            this.desktopViewportDragging = true;
            this.desktopGestureMode = 2;
        }
        if (!this.desktopViewportDragging) {
            return false;
        }
        this.desktopPanX += event.getRawX() - this.desktopViewportDragLastRawX;
        if (canPanY) {
            this.desktopPanY += event.getRawY() - this.desktopViewportDragLastRawY;
        }
        this.desktopViewportDragLastRawX = event.getRawX();
        this.desktopViewportDragLastRawY = event.getRawY();
        this.applyDesktopVideoTransform();
        return true;
    }
    protected void moveDesktopMouseByTouchDelta(MotionEvent event, boolean forceSend) {
        if (event == null || event.getPointerCount() == 0) {
            return;
        }
        float dx = event.getRawX() - this.desktopMouseDragDownX;
        float dy = event.getRawY() - this.desktopMouseDragDownY;
        int slop = ViewConfiguration.get((Context)this).getScaledTouchSlop();
        if (!this.desktopMouseDragging && dx * dx + dy * dy > (float)(slop * slop)) {
            this.desktopMouseDragging = true;
            this.desktopGestureMode = 2;
        }
        if (!this.desktopMouseDragging && !forceSend) {
            return;
        }
        float contentW = Math.max(1.0f, (float)this.desktopBaseVideoWidth * Math.max(0.001f, this.desktopZoom));
        float contentH = Math.max(1.0f, (float)this.desktopBaseVideoHeight * Math.max(0.001f, this.desktopZoom));
        this.setDesktopPointer(this.desktopMouseDragStartPointerX + dx / contentW, this.desktopMouseDragStartPointerY + dy / contentH);
        long now = System.currentTimeMillis();
        if (forceSend || now - this.desktopLastMouseMoveSentMs >= 16L) {
            this.desktopLastMouseMoveSentMs = now;
            WebRtcClient.sendPointer(this.desktopLastPointerX, this.desktopLastPointerY, false, false, 1);
        }
    }
    protected void startDesktopPinch(MotionEvent event) {
        this.desktopGestureMode = 3;
        this.desktopPinchStartDistance = DesktopViewportMath.pointerDistance(event);
        this.desktopPinchStartZoom = this.desktopZoom;
        float focusX = (event.getX(0) + event.getX(1)) / 2.0f;
        float focusY = (event.getY(0) + event.getY(1)) / 2.0f;
        this.desktopPinchFocusContentX = (focusX - this.desktopPanX) / Math.max(0.001f, this.desktopZoom);
        this.desktopPinchFocusContentY = (focusY - this.desktopPanY) / Math.max(0.001f, this.desktopZoom);
        this.desktopPinchLastFocusY = focusY;
        this.desktopWheelRemainderY = 0.0f;
    }
    protected void startDesktopRemoteTwoFingerGesture(MotionEvent event) {
        this.desktopGestureMode = 5;
        float focusX = (event.getX(0) + event.getX(1)) / 2.0f;
        float focusY = (event.getY(0) + event.getY(1)) / 2.0f;
        this.desktopPinchLastFocusY = focusY;
        this.desktopWheelRemainderY = 0.0f;
        this.rememberDesktopPointer(this.desktopNormalizedPoint(focusX, focusY));
    }
    protected void updateDesktopRemoteTwoFingerGesture(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return;
        }
        float focusX = (event.getX(0) + event.getX(1)) / 2.0f;
        float focusY = (event.getY(0) + event.getY(1)) / 2.0f;
        this.rememberDesktopPointer(this.desktopNormalizedPoint(focusX, focusY));
        this.sendDesktopMouseWheelFromDrag(focusY - this.desktopPinchLastFocusY);
        this.desktopPinchLastFocusY = focusY;
    }
    protected void updateDesktopPinch(MotionEvent event) {
        if (event.getPointerCount() < 2 || this.desktopPinchStartDistance <= 0.0f) {
            return;
        }
        float focusX = (event.getX(0) + event.getX(1)) / 2.0f;
        float focusY = (event.getY(0) + event.getY(1)) / 2.0f;
        float scale = DesktopViewportMath.pointerDistance(event) / this.desktopPinchStartDistance;
        float focusDy = focusY - this.desktopPinchLastFocusY;
        this.desktopPinchLastFocusY = focusY;
        if (this.desktopPinchStartZoom <= 1.01f && Math.abs(scale - 1.0f) < 0.04f && Math.abs(focusDy) > 0.1f) {
            this.desktopWheelRemainderY += focusDy;
            float step = Math.max(18.0f, (float)this.dp(24));
            if (Math.abs(this.desktopWheelRemainderY) >= step) {
                int notches = (int)(this.desktopWheelRemainderY / step);
                this.desktopWheelRemainderY -= (float)notches * step;
                WebRtcClient.sendMouseWheel(this.desktopLastPointerX, this.desktopLastPointerY, notches * 120);
            }
            return;
        }
        this.desktopZoom = DesktopViewportMath.clamp(this.desktopPinchStartZoom * scale, 1.0f, 4.0f);
        this.desktopPanX = focusX - this.desktopPinchFocusContentX * this.desktopZoom;
        this.desktopPanY = focusY - this.desktopPinchFocusContentY * this.desktopZoom;
        this.applyDesktopVideoTransform();
    }
    protected void sendDesktopMouseWheelFromDrag(float dy) {
        if (Math.abs(dy) <= 0.0f) {
            return;
        }
        this.desktopWheelRemainderY += dy;
        float step = Math.max(18.0f, (float)this.dp(24));
        if (Math.abs(this.desktopWheelRemainderY) < step) {
            return;
        }
        int notches = (int)(this.desktopWheelRemainderY / step);
        this.desktopWheelRemainderY -= (float)notches * step;
        float x = this.desktopHasLastPointer ? this.desktopLastPointerX : 0.5f;
        float y = this.desktopHasLastPointer ? this.desktopLastPointerY : 0.5f;
        WebRtcClient.sendMouseWheel(x, y, notches * 120);
    }
    protected void sendDesktopMouseMoveWithButton(int button, boolean forceSend) {
        long now = System.currentTimeMillis();
        if (!forceSend && now - this.desktopLastMouseMoveSentMs < 16L) {
            return;
        }
        this.desktopLastMouseMoveSentMs = now;
        WebRtcClient.sendPointer(this.desktopLastPointerX, this.desktopLastPointerY, false, false, button);
    }
    protected float[] desktopNormalizedPoint(float viewportX, float viewportY) {
        return DesktopViewportMath.normalizedPoint(viewportX, viewportY, this.desktopPanX, this.desktopPanY, this.desktopZoom, this.desktopBaseVideoWidth, this.desktopBaseVideoHeight);
    }
    protected void rememberDesktopPointer(float[] point) {
        if (point == null || point.length < 2) {
            return;
        }
        this.setDesktopPointer(point[0], point[1]);
    }
    protected void setDesktopPointer(float x, float y) {
        this.desktopLastPointerX = DesktopViewportMath.clamp(x, 0.0f, 1.0f);
        this.desktopLastPointerY = DesktopViewportMath.clamp(y, 0.0f, 1.0f);
        this.desktopHasLastPointer = true;
        this.updateDesktopPointerView();
    }
    protected void updateDesktopPointerView() {
        if (this.desktopPointerView == null || this.controlWindowCard == null) {
            return;
        }
        this.desktopPointerView.setVisibility(8);
    }
    protected void syncDesktopPointerFromMouseOverlay() {
        if (this.desktopMouseOverlayView == null || this.controlWindowCard == null) {
            return;
        }
        float anchorX = this.desktopMouseOverlayView.getX() + this.desktopMousePointerAnchorX();
        float anchorY = this.desktopMouseOverlayView.getY() + this.desktopMousePointerAnchorY();
        float[] point = this.desktopNormalizedPoint(anchorX, anchorY);
        this.setDesktopPointer(point[0], point[1]);
    }
    protected void moveDesktopMouseOverlayTo(float viewX, float viewY) {
        if (this.desktopMouseOverlayView == null || this.controlWindowCard == null) {
            return;
        }
        float anchorX = this.desktopMousePointerAnchorX();
        float anchorY = this.desktopMousePointerAnchorY();
        float minX = -anchorX;
        float maxX = Math.max(minX, (float)this.controlWindowCard.getWidth() - anchorX);
        float minY = -anchorY;
        float maxY = Math.max(minY, (float)this.desktopEffectiveViewportHeight(this.controlWindowCard.getHeight()) - anchorY);
        if (this.desktopMouseExpanded) {
            minX = 0.0f;
            maxX = Math.max(minX, (float)this.controlWindowCard.getWidth() - this.desktopMouseOverlayWidth());
            minY = 0.0f;
            maxY = Math.max(minY, (float)this.desktopEffectiveViewportHeight(this.controlWindowCard.getHeight()) - this.desktopMouseOverlayHeight());
        }
        this.desktopMouseOverlayView.setX(DesktopViewportMath.clamp(viewX, minX, maxX));
        this.desktopMouseOverlayView.setY(DesktopViewportMath.clamp(viewY, minY, maxY));
        this.panDesktopViewportForExpandedMouseOverlay();
        this.syncDesktopPointerFromMouseOverlay();
        this.autoPanDesktopViewportForEdgePointer();
    }
    protected void resetDesktopMouseOverlayPosition() {
        if (this.desktopMouseOverlayView == null || this.controlWindowCard == null) {
            return;
        }
        float anchorX = this.desktopMousePointerAnchorX();
        float anchorY = this.desktopMousePointerAnchorY();
        float x = (float)this.controlWindowCard.getWidth() - anchorX - (float)this.dp(22);
        float y = (float)this.desktopEffectiveViewportHeight(this.controlWindowCard.getHeight()) * 0.5f - anchorY;
        this.moveDesktopMouseOverlayTo(x, y);
    }
    protected float desktopMousePointerAnchorX() {
        return this.desktopMouseExpanded ? 0.0f : this.dp(67);
    }
    protected float desktopMousePointerAnchorY() {
        return this.desktopMouseExpanded ? 0.0f : this.dp(12);
    }
    protected float desktopMouseOverlayWidth() {
        if (this.desktopMouseOverlayView != null && this.desktopMouseOverlayView.getWidth() > 0) {
            return this.desktopMouseOverlayView.getWidth();
        }
        return this.dp(132);
    }
    protected float desktopMouseOverlayHeight() {
        if (this.desktopMouseOverlayView != null && this.desktopMouseOverlayView.getHeight() > 0) {
            return this.desktopMouseOverlayView.getHeight();
        }
        return this.dp(136);
    }
    protected float desktopMouseViewportPaddingLeft() {
        return this.desktopMouseExpanded ? this.desktopMouseOverlayWidth() : 0.0f;
    }
    protected float desktopMouseViewportPaddingRight() {
        return this.desktopMouseExpanded ? this.desktopMouseOverlayWidth() : 0.0f;
    }
    protected float desktopMouseViewportPaddingTop() {
        return this.desktopMouseExpanded ? this.desktopMouseOverlayHeight() : 0.0f;
    }
    protected float desktopMouseViewportPaddingBottom() {
        return this.desktopMouseExpanded ? this.desktopMouseOverlayHeight() : 0.0f;
    }
    protected void panDesktopViewportForExpandedMouseOverlay() {
        if (!this.desktopMouseExpanded || this.desktopMouseOverlayView == null || this.controlWindowCard == null) {
            return;
        }
        int viewportW = this.controlWindowCard.getWidth();
        int viewportH = this.desktopEffectiveViewportHeight(this.controlWindowCard.getHeight());
        if (viewportW <= 0 || viewportH <= 0) {
            return;
        }
        float scaledW = (float)this.desktopBaseVideoWidth * this.desktopZoom;
        float scaledH = (float)this.desktopBaseVideoHeight * this.desktopZoom;
        float minPanX = (float)viewportW - scaledW - this.desktopMouseViewportPaddingRight();
        float maxPanX = this.desktopMouseViewportPaddingLeft();
        float minPanY = (float)viewportH - scaledH - this.desktopMouseViewportPaddingBottom();
        float maxPanY = this.desktopMouseViewportPaddingTop();
        float maxOverlayX = Math.max(1.0f, (float)viewportW - this.desktopMouseOverlayWidth());
        float maxOverlayY = Math.max(1.0f, (float)viewportH - this.desktopMouseOverlayHeight());
        float tX = DesktopViewportMath.clamp(this.desktopMouseOverlayView.getX() / maxOverlayX, 0.0f, 1.0f);
        float tY = DesktopViewportMath.clamp(this.desktopMouseOverlayView.getY() / maxOverlayY, 0.0f, 1.0f);
        this.desktopPanX = maxPanX + tX * (minPanX - maxPanX);
        this.desktopPanY = maxPanY + tY * (minPanY - maxPanY);
        this.applyDesktopVideoTransform();
    }
    protected void autoPanDesktopViewportForEdgePointer() {
        if (!this.desktopMouseDragging || this.controlWindowCard == null || this.renderer == null) {
            return;
        }
        int viewportW = this.controlWindowCard.getWidth();
        int viewportH = this.desktopEffectiveViewportHeight(this.controlWindowCard.getHeight());
        if (viewportW <= 0 || viewportH <= 0) {
            return;
        }
        float scaledW = (float)this.desktopBaseVideoWidth * this.desktopZoom;
        float scaledH = (float)this.desktopBaseVideoHeight * this.desktopZoom;
        boolean canPanX = scaledW + this.desktopMouseViewportPaddingLeft() + this.desktopMouseViewportPaddingRight() > (float)(viewportW + 1);
        boolean canPanY = scaledH + this.desktopMouseViewportPaddingTop() + this.desktopMouseViewportPaddingBottom() > (float)(viewportH + 1);
        if (!canPanX && !canPanY) {
            return;
        }
        float threshold = 0.02f;
        float step = Math.max(6.0f, this.dp(12));
        boolean changed = false;
        if (canPanX && this.desktopLastPointerX <= threshold) {
            this.desktopPanX += step;
            changed = true;
        } else if (canPanX && this.desktopLastPointerX >= 1.0f - threshold) {
            this.desktopPanX -= step;
            changed = true;
        }
        if (canPanY && this.desktopLastPointerY <= threshold) {
            this.desktopPanY += step;
            changed = true;
        } else if (canPanY && this.desktopLastPointerY >= 1.0f - threshold) {
            this.desktopPanY -= step;
            changed = true;
        }
        if (changed) {
            this.applyDesktopVideoTransform();
        }
    }
    protected void applyDesktopVideoTransform() {
        if (this.renderer == null || this.controlWindowCard == null) {
            return;
        }
        int viewportW = this.controlWindowCard.getWidth();
        int viewportH = this.desktopEffectiveViewportHeight(this.controlWindowCard.getHeight());
        float scaledW = (float)this.desktopBaseVideoWidth * this.desktopZoom;
        float scaledH = (float)this.desktopBaseVideoHeight * this.desktopZoom;
        float padLeft = this.desktopMouseViewportPaddingLeft();
        float padRight = this.desktopMouseViewportPaddingRight();
        float padTop = this.desktopMouseViewportPaddingTop();
        float padBottom = this.desktopMouseViewportPaddingBottom();
        float paddedW = scaledW + padLeft + padRight;
        float paddedH = scaledH + padTop + padBottom;
        if (paddedW <= (float)viewportW) {
            this.desktopPanX = ((float)viewportW - scaledW + padLeft - padRight) / 2.0f;
        } else {
            this.desktopPanX = DesktopViewportMath.clamp(this.desktopPanX, (float)viewportW - scaledW - padRight, padLeft);
        }
        if (paddedH <= (float)viewportH) {
            this.desktopPanY = ((float)viewportH - scaledH + padTop - padBottom) / 2.0f;
        } else {
            this.desktopPanY = DesktopViewportMath.clamp(this.desktopPanY, (float)viewportH - scaledH - padBottom, padTop);
        }
        this.renderer.setScaleX(this.desktopZoom);
        this.renderer.setScaleY(this.desktopZoom);
        this.renderer.setTranslationX(this.desktopPanX);
        this.renderer.setTranslationY(this.desktopPanY);
        this.updateDesktopPointerView();
    }
    protected void cancelDesktopLongPress(View view) {
        if (this.pendingDesktopLongPress != null && view != null) {
            view.removeCallbacks(this.pendingDesktopLongPress);
        }
        this.pendingDesktopLongPress = null;
    }

}
