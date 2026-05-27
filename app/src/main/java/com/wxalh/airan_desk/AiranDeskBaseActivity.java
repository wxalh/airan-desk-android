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
abstract class AiranDeskBaseActivity
extends Activity
implements SignalingClient.Listener,
WebRtcClient.UiEvents {
    protected static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;
    protected static final int REQUEST_CODE_PICK_FILE = 1002;
    protected static final int REQUEST_CODE_PICK_DIRECTORY = 1003;
    protected static final int REQUEST_CODE_STORAGE_PERMISSION = 1004;
    protected static final int REQUEST_CODE_NOTIFICATION_PERMISSION = 1005;
    protected static final int REQUEST_CODE_RECORD_AUDIO_PERMISSION = 1006;
    protected static final String DEFAULT_REMOTE_ID = "";
    protected static final String DEFAULT_REMOTE_PASSWORD = "";
    protected static final float REMOTE_VIDEO_ASPECT = 1.7777778f;
    protected static final float DESKTOP_MIN_ZOOM = 1.0f;
    protected static final float DESKTOP_MAX_ZOOM = 4.0f;
    protected static final String DISPLAY_MODE_FIT = "fit";
    protected static final String DISPLAY_MODE_ACTUAL = "actual";
    protected static final int VK_BACK = 8;
    protected static final int VK_TAB = 9;
    protected static final int VK_RETURN = 13;
    protected static final int VK_SHIFT = 16;
    protected static final int VK_CONTROL = 17;
    protected static final int VK_MENU = 18;
    protected static final int VK_ESCAPE = 27;
    protected static final int VK_SPACE = 32;
    protected static final int VK_PRIOR = 33;
    protected static final int VK_NEXT = 34;
    protected static final int VK_END = 35;
    protected static final int VK_HOME = 36;
    protected static final int VK_LEFT = 37;
    protected static final int VK_UP = 38;
    protected static final int VK_RIGHT = 39;
    protected static final int VK_DOWN = 40;
    protected static final int VK_DELETE = 46;
    protected static final int VK_LWIN = 91;
    protected static final int VK_F5 = 116;
    protected static final int DESKTOP_GESTURE_NONE = 0;
    protected static final int DESKTOP_GESTURE_TAP = 1;
    protected static final int DESKTOP_GESTURE_PAN = 2;
    protected static final int DESKTOP_GESTURE_PINCH = 3;
    protected static final int DESKTOP_GESTURE_RIGHT_CLICK = 4;
    protected static final int TERMINAL_MIN_COLS = 80;
    protected static final int C_BG = AppTheme.C_BG;
    protected static final int C_LOWEST = AppTheme.C_LOWEST;
    protected static final int C_CONTAINER = AppTheme.C_CONTAINER;
    protected static final int C_CONTAINER_HIGH = AppTheme.C_CONTAINER_HIGH;
    protected static final int C_VARIANT = AppTheme.C_VARIANT;
    protected static final int C_TEXT = AppTheme.C_TEXT;
    protected static final int C_TEXT_MUTED = AppTheme.C_TEXT_MUTED;
    protected static final int C_OUTLINE = AppTheme.C_OUTLINE;
    protected static final int C_PRIMARY = AppTheme.C_PRIMARY;
    protected static final int C_ON_PRIMARY = AppTheme.C_ON_PRIMARY;
    protected static final int C_SECONDARY = AppTheme.C_SECONDARY;
    protected static final int C_SECONDARY_CONTAINER = AppTheme.C_SECONDARY_CONTAINER;
    protected AppConfig config;
    protected MediaProjectionManager projectionManager;
    protected TextureVideoRenderer renderer;
    protected FrameLayout contentHost;
    protected Button topBackButton;
    protected Button topSettingsButton;
    protected TextView appTitleText;
    protected TextView statusText;
    protected TextView runtimeLogText;
    protected WebView terminalWebView;
    protected NativeTerminalView nativeTerminalView;
    protected FrameLayout terminalHostView;
    protected ScaleGestureDetector terminalScaleDetector;
    protected View terminalFallbackView;
    protected TextView terminalFallbackText;
    protected ScrollView terminalFallbackScroll;
    protected EditText terminalFallbackInput;
    protected SettingsScreenView settingsScreenView;
    protected ListView fileList;
    protected ArrayAdapter<String> fileAdapter;
    protected TextView remotePathText;
    protected TextView fileMountsText;
    protected TextView fileTransferText;
    protected ProgressBar fileTransferProgress;
    protected TransferHistoryScreenView transferHistoryScreenView;
    protected FrameLayout controlWindowCard;
    protected RemoteControlSectionView remoteControlSectionView;
    protected AlertDialog connectDialog;
    protected TextView connectDialogMessage;
    protected TextView sessionCountText;
    protected LinearLayout sessionListHost;
    protected TransferHistoryStore transferHistoryStore;
    protected RuntimeStatusLog runtimeStatusLog;
    protected UiTextFormatter uiTextFormatter;
    protected UiComponentFactory uiFactory;
    protected CrashLogStore crashLogStore;
    protected boolean pendingConnectNavigation = false;
    protected String pendingConnectMode = "desktop";
    protected String pendingSessionRemoteId = "";
    protected boolean connectionFailureDialogShowing = false;
    protected final List<RemoteSession> activeSessions = new ArrayList<RemoteSession>();
    protected final List<JSONObject> remoteFileObjects = new ArrayList<JSONObject>();
    protected final List<String> remoteMountedRoots = new ArrayList<String>();
    protected final List<TransferRecord> transferHistory = new ArrayList<TransferRecord>();
    protected final SimpleDateFormat transferHistoryTimeFormat = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
    protected int selectedRemoteIndex = -1;
    protected int lastFileClickIndex = -1;
    protected long lastFileClickTimeMs = 0L;
    protected String currentRemotePath = "";
    protected String terminalOs = "";
    protected String terminalShell = "";
    protected String terminalMode = "";
    protected boolean terminalPathTracking = false;
    protected boolean terminalPathTrackingInjected = false;
    protected boolean terminalWebReady = false;
    protected boolean terminalWebRenderedOutput = false;
    protected boolean terminalSelectionMode = false;
    protected boolean terminalHasSelection = false;
    protected String nativeTerminalSelection = "";
    protected boolean terminalRemoteOutputSeen = false;
    protected boolean terminalWakeScheduled = false;
    protected boolean terminalWaitingHintShown = false;
    protected boolean terminalLayoutRetryScheduled = false;
    protected boolean terminalFallbackHintVisible = false;
    protected boolean terminalFallbackDiagnosticShown = false;
    protected int terminalInvisibleByteCount = 0;
    protected boolean pendingTerminalVisibleOutput = false;
    protected final ByteArrayOutputStream pendingTerminalOutput = new ByteArrayOutputStream();
    protected final TerminalOutputDecoder terminalOutputDecoder = new TerminalOutputDecoder();
    protected final StringBuilder terminalFallbackTranscript = new StringBuilder();
    protected final TerminalFallbackScreen terminalFallbackScreen = new TerminalFallbackScreen();
    protected int pendingTerminalRows = 24;
    protected int pendingTerminalCols = 80;
    protected int lastTerminalResizeRows = -1;
    protected int lastTerminalResizeCols = -1;
    protected int lastTerminalKeyboardAvailableHeight = -1;
    protected float terminalZoomScale = 1.0f;
    protected boolean terminalScalingGesture = false;
    protected boolean screenCapturePermissionUiRequestInFlight = false;
    protected long screenCapturePermissionUiRequestStartedMs = 0L;
    protected boolean accessibilityPromptShowing = false;
    protected long lastAccessibilityPromptMs = 0L;
    protected boolean storageAccessPrompted = false;
    protected boolean remoteKeepScreenOn = false;
    protected EditText desktopKeyboardInput;
    protected boolean desktopKeyboardClearing = false;
    protected boolean desktopPinchZoomEnabled = false;
    protected boolean desktopMouseExpanded = false;
    protected FrameLayout desktopMousePanel;
    protected View desktopMouseToggleButton;
    protected View desktopMouseFloatingPointer;
    protected View desktopMouseOverlayView;
    protected TextView desktopPointerView;
    protected float desktopTouchDownViewportX;
    protected float desktopTouchDownViewportY;
    protected int desktopKeyboardInset = 0;
    protected boolean desktopViewportDragging = false;
    protected float desktopViewportDragLastRawX;
    protected float desktopViewportDragLastRawY;
    protected float desktopMouseDragDownX;
    protected float desktopMouseDragDownY;
    protected float desktopMouseOverlayStartX;
    protected float desktopMouseOverlayStartY;
    protected float desktopMouseDragStartPointerX;
    protected float desktopMouseDragStartPointerY;
    protected boolean desktopMouseDragging = false;
    protected int desktopMouseButtonDragButton = 0;
    protected long desktopLastMouseMoveSentMs = 0L;
    protected boolean desktopMouseLongClickTriggered = false;
    protected LinearLayout desktopActionDrawer;
    protected Button desktopDrawerToggleButton;
    protected boolean desktopDrawerExpanded = false;
    protected Button desktopCtrlButton;
    protected Button desktopShiftButton;
    protected Button desktopAltButton;
    protected Button desktopWinButton;
    protected boolean desktopCtrlHeld = false;
    protected boolean desktopShiftHeld = false;
    protected boolean desktopAltHeld = false;
    protected boolean desktopWinHeld = false;
    protected Runnable pendingDesktopLongPress;
    protected long desktopLastTapMs = 0L;
    protected float desktopLastTapX;
    protected float desktopLastTapY;
    protected float desktopPanX;
    protected float desktopPanY;
    protected float desktopZoom = 1.0f;
    protected float desktopPinchStartDistance;
    protected float desktopPinchStartZoom;
    protected float desktopPinchFocusContentX;
    protected float desktopPinchFocusContentY;
    protected float desktopPinchLastFocusY;
    protected float desktopWheelRemainderY;
    protected boolean desktopMouseWheelDragging = false;
    protected float desktopMouseWheelLastRawY;
    protected boolean desktopTouchpadBlockedByPinch = false;
    protected int desktopBaseVideoWidth;
    protected int desktopBaseVideoHeight;
    protected int desktopGestureMode = 0;
    protected boolean desktopLayoutUpdateScheduled = false;
    protected boolean desktopLayoutResetPending = false;
    protected boolean desktopHasLastPointer = false;
    protected float desktopLastPointerX = 0.5f;
    protected float desktopLastPointerY = 0.5f;
    protected String selectedMode = "desktop";
    protected String currentTab = "home";
    protected String settingsReturnTab = "home";
    protected String logsReturnTab = "home";
    protected String remoteIdDraft = "";
    protected String remotePasswordDraft = "";
    protected String desktopDisplayMode = "fit";
    protected Button desktopBitrateButton;
    protected Button desktopResolutionButton;
    protected Button desktopCaptureButton;
    protected Button desktopNetworkButton;
    protected Button desktopDisplayButton;
    protected Button desktopAudioButton;
    protected Button desktopZoomToggleButton;
    protected final Editable desktopKeyboardEditable = new SpannableStringBuilder();
    protected String desktopComposingText = "";
    protected boolean desktopComposingTextSent = false;
    protected String pendingAudioMode = "";

    protected abstract void releaseDesktopModifierKeys();

    protected abstract void cancelDesktopLongPress(View view);

    protected abstract void updateAppTitleText();

    protected abstract void applyDesktopAudioMode(String mode);

    protected void requestStorageAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                if (this.storageAccessPrompted) {
                    return;
                }
                this.storageAccessPrompted = true;
                try {
                    Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                    intent.setData(Uri.parse((String)("package:" + this.getPackageName())));
                    this.startActivity(intent);
                }
                catch (Exception e) {
                    Intent intent = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
                    this.startActivity(intent);
                }
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            boolean writeGranted;
            boolean readGranted = this.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == 0;
            boolean bl = writeGranted = this.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == 0;
            if (!readGranted || !writeGranted) {
                if (this.storageAccessPrompted) {
                    return;
                }
                this.storageAccessPrompted = true;
                this.requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"}, 1004);
            }
        }
    }
    protected void startKeepAliveService() {
        try {
            KeepAliveForegroundService.start((Context)this);
        }
        catch (Exception e) {
            this.showUserStatus("keep alive start failed: " + e.getMessage());
        }
    }
    protected void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (this.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
            return;
        }
        this.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1005);
    }
    protected void openBatteryOptimizationSettings() {
        SettingsNavigator.openBatteryOptimizationSettings((Context)this);
    }
    protected void openAutoStartSettings() {
        SettingsNavigator.AutoStartResult result = SettingsNavigator.openAutoStartSettings((Context)this);
        if (result == SettingsNavigator.AutoStartResult.OPENED) {
            this.showUserStatus(this.getString(R.string.enable_autostart_hint));
            return;
        }
        this.showUserStatus(this.getString(R.string.autostart_settings_not_found));
    }
    protected void openAppDetailsSettings() {
        SettingsNavigator.openAppDetailsSettings((Context)this);
    }
    protected void requestScreenCapturePermission(boolean automatic) {
        if (WebRtcClient.hasScreenCapturePermission()) {
            this.showPermissionReadyDialog(this.getString(R.string.screen_permission), this.getString(R.string.screen_permission_already_granted_message));
            return;
        }
        if (this.screenCapturePermissionUiRequestInFlight) {
            this.showUserStatus("screen permission request already open");
            return;
        }
        try {
            this.screenCapturePermissionUiRequestInFlight = true;
            this.screenCapturePermissionUiRequestStartedMs = System.currentTimeMillis();
            this.startActivityForResult(this.createFullDisplayScreenCaptureIntent(), 1001);
        }
        catch (Exception e) {
            this.screenCapturePermissionUiRequestInFlight = false;
            this.screenCapturePermissionUiRequestStartedMs = 0L;
            WebRtcClient.onScreenCapturePermissionDenied();
            this.showUserStatus("screen permission request failed: " + e.getMessage());
        }
    }
    protected void recoverStaleScreenCapturePermissionRequest() {
        if (!this.screenCapturePermissionUiRequestInFlight || WebRtcClient.hasScreenCapturePermission()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.screenCapturePermissionUiRequestStartedMs;
        if (elapsed < 30000L) {
            return;
        }
        this.screenCapturePermissionUiRequestInFlight = false;
        this.screenCapturePermissionUiRequestStartedMs = 0L;
        this.onStatus("Stale screen capture permission UI request cleared");
    }
    protected Intent createFullDisplayScreenCaptureIntent() {
        if (Build.VERSION.SDK_INT >= 34) {
            return this.projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        }
        return this.projectionManager.createScreenCaptureIntent();
    }
    protected void setScrollableContent(View child) {
        this.contentHost.removeAllViews();
        ScrollView scroll = new ScrollView((Context)this);
        scroll.setFillViewport(false);
        scroll.addView(child);
        this.contentHost.addView((View)scroll, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
    }
    protected void setDirectContent(View child) {
        this.contentHost.removeAllViews();
        this.contentHost.addView(child, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
    }
    protected void copyTextToClipboard(String text, String label) {
        if (text == null || text.length() == 0) {
            this.showUserStatus(this.getString(R.string.status_nothing_selected));
            return;
        }
        if (ClipboardUtils.copyText((Context)this, label, text)) {
            this.showUserStatus(this.getString(R.string.status_copied));
        }
    }
    protected void detachRenderer() {
        this.releaseDesktopModifierKeys();
        if (this.controlWindowCard != null) {
            this.cancelDesktopLongPress((View)this.controlWindowCard);
        }
        if (this.renderer != null) {
            this.cancelDesktopLongPress((View)this.renderer);
            WebRtcClient.setRenderer(null);
            try {
                this.renderer.release();
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.renderer = null;
        }
        this.desktopLayoutUpdateScheduled = false;
        this.desktopLayoutResetPending = false;
        this.desktopKeyboardInput = null;
        this.desktopMousePanel = null;
        this.desktopMouseToggleButton = null;
        this.desktopMouseFloatingPointer = null;
        this.desktopMouseOverlayView = null;
        this.desktopPointerView = null;
        this.terminalHostView = null;
        this.terminalScaleDetector = null;
        this.terminalZoomScale = 1.0f;
        this.terminalScalingGesture = false;
        this.lastTerminalKeyboardAvailableHeight = -1;
        this.desktopMouseExpanded = false;
        this.desktopMouseDragging = false;
        this.desktopViewportDragging = false;
        this.desktopKeyboardInset = 0;
        this.desktopActionDrawer = null;
        this.desktopDrawerToggleButton = null;
        this.desktopDrawerExpanded = false;
        this.desktopCtrlButton = null;
        this.desktopShiftButton = null;
        this.desktopAltButton = null;
        this.desktopWinButton = null;
        this.desktopAudioButton = null;
        if (this.terminalWebView != null) {
            try {
                this.terminalWebView.destroy();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        this.terminalWebView = null;
        this.nativeTerminalView = null;
        this.nativeTerminalSelection = "";
        this.terminalFallbackView = null;
        this.terminalFallbackText = null;
        this.terminalFallbackScroll = null;
        this.terminalFallbackInput = null;
        this.terminalWebReady = false;
        this.terminalLayoutRetryScheduled = false;
        this.terminalFallbackHintVisible = false;
        this.terminalFallbackDiagnosticShown = false;
        this.terminalInvisibleByteCount = 0;
        this.pendingTerminalVisibleOutput = false;
        this.pendingTerminalOutput.reset();
        this.controlWindowCard = null;
    }
    protected LinearLayout page() {
        LinearLayout page = new LinearLayout((Context)this);
        page.setOrientation(1);
        page.setPadding(this.dp(20), this.dp(20), this.dp(20), this.dp(24));
        page.setBackgroundColor(C_BG);
        return page;
    }
    protected LinearLayout card() {
        LinearLayout card = new LinearLayout((Context)this);
        card.setOrientation(1);
        card.setPadding(this.dp(16), this.dp(16), this.dp(16), this.dp(16));
        card.setBackground((Drawable)this.uiFactory.border(C_CONTAINER, C_OUTLINE, this.dp(8), 1));
        LinearLayout.LayoutParams lp = this.uiFactory.fullWidth();
        lp.bottomMargin = this.dp(12);
        card.setLayoutParams((ViewGroup.LayoutParams)lp);
        return card;
    }
    protected View sectionTitle(String title, String meta) {
        return this.sectionTitle(title, meta, 16, 10);
    }
    protected View sectionTitle(String title, String meta, int topDp, int bottomDp) {
        LinearLayout row = new LinearLayout((Context)this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(0, this.dp(topDp), 0, this.dp(bottomDp));
        row.addView((View)this.uiFactory.text(title, 20, C_TEXT, true, false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (meta != null && meta.length() > 0) {
            row.addView((View)this.uiFactory.text(meta, 11, C_TEXT_MUTED, false, true));
        }
        return row;
    }
    protected View labeledInput(String label, EditText input) {
        LinearLayout box = new LinearLayout((Context)this);
        box.setOrientation(1);
        box.setPadding(0, this.dp(4), 0, this.dp(4));
        box.addView((View)this.uiFactory.text(label, 12, C_TEXT_MUTED, false, true), (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        box.addView((View)input, (ViewGroup.LayoutParams)this.uiFactory.fullWidthInput());
        return box;
    }
    protected EditText edit(String text, String hint) {
        EditText edit = new EditText((Context)this);
        edit.setText((CharSequence)text);
        edit.setHint((CharSequence)hint);
        edit.setSingleLine(true);
        edit.setTextColor(C_TEXT);
        edit.setHintTextColor(Color.argb((int)90, (int)190, (int)200, (int)203));
        edit.setTextSize(14.0f);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setPadding(this.dp(12), 0, this.dp(12), 0);
        edit.setBackground((Drawable)this.uiFactory.border(C_CONTAINER, C_OUTLINE, this.dp(8), 1));
        edit.setOnFocusChangeListener(new View.OnFocusChangeListener(){

            public void onFocusChange(final View v, boolean hasFocus) {
                if (!hasFocus) {
                    return;
                }
                v.postDelayed(new Runnable(){

                    @Override
                    public void run() {
                        Rect rect = new Rect(0, 0, v.getWidth(), v.getHeight() + AiranDeskBaseActivity.this.dp(120));
                        v.requestRectangleOnScreen(rect, false);
                    }
                }, 180L);
            }
        });
        return edit;
    }
    protected void updateNav() {
        this.updateTopBar();
    }
    protected void updateTopBar() {
        if (this.topBackButton != null) {
            this.topBackButton.setVisibility("home".equals(this.currentTab) ? 8 : 0);
        }
        if (this.topSettingsButton != null) {
            this.topSettingsButton.setVisibility("settings".equals(this.currentTab) ? 8 : 0);
        }
        if (this.appTitleText != null) {
            this.updateAppTitleText();
        }
    }
    protected int androidDrawableId(String name, int fallback) {
        int id = this.getResources().getIdentifier(name, "drawable", "android");
        return id == 0 ? fallback : id;
    }
    protected Drawable rotatedAndroidDrawable(int iconRes, float degrees) {
        Drawable source = this.getResources().getDrawable(iconRes);
        int width = Math.max(1, source.getIntrinsicWidth());
        int height = Math.max(1, source.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap((int)width, (int)height, (Bitmap.Config)Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        source.setBounds(0, 0, width, height);
        source.draw(canvas);
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap((Bitmap)bitmap, (int)0, (int)0, (int)width, (int)height, (Matrix)matrix, (boolean)true);
        return new BitmapDrawable(this.getResources(), rotated);
    }
    protected void tintCompoundDrawables(TextView view, int color) {
        Drawable[] drawables;
        for (Drawable drawable2 : drawables = view.getCompoundDrawables()) {
            if (drawable2 == null) continue;
            drawable2.mutate().setTint(color);
        }
    }
    protected void showLastCrashIfAny() {
        try {
            File file = this.crashLogStore == null ? null : this.crashLogStore.lastCrashFile();
            if (file != null && this.crashLogStore.hasLastCrash()) {
                this.showUserStatus(this.getString(R.string.last_crash, new Object[]{file.getAbsolutePath()}));
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
    protected void syncNetworkConfigFromEditors() {
        String value;
        if (this.settingsScreenView != null) {
            this.config.setWsUrl(this.settingsScreenView.wsUrl());
        }
        if (this.settingsScreenView != null) {
            this.config.setIceUri(this.settingsScreenView.iceUri());
        }
        if (this.settingsScreenView != null) {
            this.config.setIceUser(this.settingsScreenView.iceUser());
        }
        if (this.settingsScreenView != null) {
            this.config.setIcePassword(this.settingsScreenView.icePassword());
        }
        if (this.remoteControlSectionView != null && (value = this.remoteControlSectionView.remoteId()).length() > 0) {
            this.remoteIdDraft = value;
        }
        if (this.remoteControlSectionView != null && (value = this.remoteControlSectionView.password()).length() > 0) {
            this.remotePasswordDraft = value;
        }
    }
    protected void connectWsFromConfig(boolean quiet) {
        this.syncNetworkConfigFromEditors();
        String wsUrl = this.config.wsUrl().trim();
        if (wsUrl.length() == 0) {
            if (!quiet) {
                this.showUserStatus(this.getString(R.string.ws_required));
            }
            return;
        }
        String finalUrl = this.config.buildWsUrl((Context)this);
        if (!quiet) {
            this.showUserStatus(this.getString(R.string.connecting_websocket, new Object[]{finalUrl}));
        } else {
            this.onStatus(this.getString(R.string.connecting_websocket, new Object[]{finalUrl}));
        }
        SignalingClient.connect(finalUrl);
    }
    protected void autoConnectWebSocket() {
        this.connectWsFromConfig(true);
    }
    protected void updateControlWindowVisibility() {
        if (this.controlWindowCard != null) {
            this.controlWindowCard.setVisibility(0);
        }
    }
    protected void showUserStatus(String message) {
        this.onStatus(message);
    }
    protected void recordRuntimeStatus(String message) {
        if (this.runtimeStatusLog != null) {
            this.runtimeStatusLog.add(message);
        }
        this.refreshRuntimeLogText();
    }
    protected void refreshRuntimeLogText() {
        if (this.runtimeLogText != null) {
            this.runtimeLogText.setText((CharSequence)this.buildRuntimeLogText());
        }
    }
    protected String buildRuntimeLogText() {
        return this.runtimeStatusLog == null ? this.getString(R.string.no_runtime_logs) : this.runtimeStatusLog.buildText();
    }
    protected void showPermissionReadyDialog(String title, String message) {
        this.showUserStatus(message);
        new AlertDialog.Builder((Context)this)
                .setTitle((CharSequence)title)
                .setMessage((CharSequence)message)
                .setPositiveButton(17039370, null)
                .show();
    }
    protected void showRuntimeDiagnosticsDialog() {
        final String diagnostics = this.buildRuntimeDiagnosticsText();
        TextView view = this.uiFactory.text(diagnostics, 12, C_TEXT, false, true);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        view.setPadding(this.dp(12), this.dp(12), this.dp(12), this.dp(12));
        ScrollView scroll = new ScrollView((Context)this);
        scroll.addView((View)view, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder((Context)this)
                .setTitle((CharSequence)this.getString(R.string.runtime_diagnostics))
                .setView((View)scroll)
                .setPositiveButton(17039370, null)
                .setNegativeButton((CharSequence)this.getString(R.string.copy_all), new DialogInterface.OnClickListener(){

                    public void onClick(DialogInterface dialog, int which) {
                        AiranDeskBaseActivity.this.copyTextToClipboard(diagnostics, AiranDeskBaseActivity.this.getString(R.string.runtime_diagnostics));
                    }
                })
                .show();
    }
    protected String buildRuntimeDiagnosticsText() {
        StringBuilder builder = new StringBuilder();
        builder.append("App: ").append(this.getString(R.string.app_name)).append(" v").append(BuildConfig.VERSION_NAME).append('\n');
        builder.append("SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        builder.append("WebSocket: connected=").append(SignalingClient.isConnected())
                .append(" connecting=").append(SignalingClient.isConnecting()).append('\n');
        builder.append("Screen permission: ").append(WebRtcClient.hasScreenCapturePermission()).append('\n');
        builder.append("Accessibility: ").append(RemoteInputAccessibilityService.isReady()).append('\n');
        builder.append("Record audio: ").append(AudioPermissionHelper.hasRecordAudio((Context)this)).append('\n');
        builder.append("Keep screen on: ").append(this.remoteKeepScreenOn).append('\n');
        builder.append("Current tab: ").append(this.currentTab).append('\n');
        builder.append("Active UI sessions: ").append(this.activeSessions.size()).append("\n\n");
        builder.append(WebRtcClient.runtimeDiagnostics());
        builder.append("\nRecent runtime log:\n");
        builder.append(this.buildRuntimeLogText());
        return builder.toString();
    }
    protected void applyLanguage(String tag) {
        Locale locale = "en".equals(tag) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
        Locale.setDefault(locale);
        Configuration configuration = this.getResources().getConfiguration();
        configuration.setLocale(locale);
        this.getResources().updateConfiguration(configuration, this.getResources().getDisplayMetrics());
    }
    protected void toggleLanguage() {
        this.config.setLanguage("en".equals(this.config.language()) ? "zh" : "en");
        this.recreate();
    }
    protected boolean isLocalCredentialId(String id) {
        String localId = this.config == null ? "" : this.config.localId();
        return RemoteCredentialParser.normalize(id).equalsIgnoreCase(RemoteCredentialParser.normalize(localId));
    }
    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected int dp(int value) {
        return (int)((float)value * this.getResources().getDisplayMetrics().density + 0.5f);
    }
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001 && resultCode == -1 && data != null) {
            this.screenCapturePermissionUiRequestInFlight = false;
            this.screenCapturePermissionUiRequestStartedMs = 0L;
            WebRtcClient.setScreenCapturePermission(resultCode, data);
            Toast.makeText((Context)this, (CharSequence)this.getString(R.string.screen_permission_granted), (int)0).show();
            return;
        }
        if (requestCode == 1001) {
            this.screenCapturePermissionUiRequestInFlight = false;
            this.screenCapturePermissionUiRequestStartedMs = 0L;
            WebRtcClient.onScreenCapturePermissionDenied();
            this.showUserStatus("screen permission denied");
            return;
        }
        if (requestCode == 1002 && resultCode == -1 && data != null) {
            File file = PickedFileCache.copyPickedFile((Context)this, data.getData());
            if (file != null) {
                WebRtcClient.uploadLocalFile(file, this.currentRemotePath);
            }
            return;
        }
        if (requestCode == 1003 && resultCode == -1 && data != null) {
            File file = PickedFileCache.copyPickedDirectory((Context)this, data.getData());
            if (file != null) {
                WebRtcClient.uploadLocalFile(file, this.currentRemotePath);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_RECORD_AUDIO_PERMISSION) {
            boolean granted = grantResults != null && grantResults.length > 0 && grantResults[0] == 0;
            if (!granted) {
                this.pendingAudioMode = "";
                this.showUserStatus(this.getString(R.string.audio_permission_denied));
                return;
            }
            this.showUserStatus(this.getString(R.string.audio_permission_ready));
            if (this.pendingAudioMode.length() > 0) {
                String mode = this.pendingAudioMode;
                this.pendingAudioMode = "";
                this.applyDesktopAudioMode(mode);
            }
        }
    }
    protected void updateRemoteKeepScreenOnFlag() {
        boolean shouldKeepOn = WebRtcClient.hasConnectedControlledDesktopSession();
        if (shouldKeepOn == this.remoteKeepScreenOn) {
            return;
        }
        this.remoteKeepScreenOn = shouldKeepOn;
        if (shouldKeepOn) {
            this.getWindow().addFlags(128);
        } else {
            this.getWindow().clearFlags(128);
        }
    }
    protected void clearRemoteKeepScreenOnFlag() {
        if (!this.remoteKeepScreenOn) {
            return;
        }
        this.remoteKeepScreenOn = false;
        this.getWindow().clearFlags(128);
    }
    protected TransferRecord upsertTransferRecord(String direction, String transferId, String sourcePath, String targetPath, String fileName, long transferredBytes, long totalBytes, boolean done, boolean success) {
        return this.transferHistoryStore.upsert(this.transferHistory, direction, transferId, sourcePath, targetPath, fileName, transferredBytes, totalBytes, done, success);
    }
    protected void updateFileTransferStatusView() {
        if (this.fileTransferText == null || this.fileTransferProgress == null) {
            return;
        }
        TransferRecord latest = this.latestTransferRecord();
        if (latest == null) {
            this.fileTransferText.setText((CharSequence)this.getString(R.string.transfer_status_idle));
            this.fileTransferProgress.setProgress(0);
            return;
        }
        this.fileTransferText.setText((CharSequence)this.uiTextFormatter.formatTransferStatus(latest));
        this.fileTransferProgress.setProgress(TransferProgressFormatter.progressPermille(latest));
    }
    protected TransferRecord latestTransferRecord() {
        return this.transferHistory.isEmpty() ? null : this.transferHistory.get(0);
    }
    protected void loadTransferHistory() {
        this.transferHistoryStore.loadInto(this.transferHistory);
    }
    protected void saveTransferHistory() {
        this.transferHistoryStore.save(this.transferHistory);
    }

}
