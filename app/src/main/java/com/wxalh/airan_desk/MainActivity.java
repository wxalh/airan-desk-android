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
import android.view.WindowManager;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
public class MainActivity
extends AiranDeskCallbacksActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        this.config = new AppConfig((Context)this);
        this.applyLanguage(this.config.language());
        this.runtimeStatusLog = new RuntimeStatusLog((Context)this);
        this.uiTextFormatter = new UiTextFormatter((Context)this);
        this.uiFactory = new UiComponentFactory((Context)this);
        this.crashLogStore = new CrashLogStore((Context)this);
        this.crashLogStore.install();
        this.projectionManager = (MediaProjectionManager)this.getSystemService("media_projection");
        this.transferHistoryStore = new TransferHistoryStore((Context)this, this.getString(R.string.transfer_file_default), new TransferHistoryStore.LocalPathResolver(){

            @Override
            public String inferLocalPath(String direction, String sourcePath, String targetPath) {
                return TransferPathResolver.inferLocalPath(direction, sourcePath, targetPath, MainActivity.this.isTransferControlRole());
            }
        });
        this.loadTransferHistory();
        WebRtcClient.init((Context)this);
        WebRtcClient.setUiEvents(this);
        WebRtcClient.setUiVisible(true);
        SignalingClient.setListener(this);
        this.buildUi();
        this.requestNotificationPermissionIfNeeded();
        this.startKeepAliveService();
        this.autoConnectWebSocket();
        this.showHome();
        this.registerSystemBackHandler();
        this.showLastCrashIfAny();
    }
    protected void onResume() {
        super.onResume();
        WebRtcClient.setUiVisible(true);
        this.recoverStaleScreenCapturePermissionRequest();
        if (!SignalingClient.isConnected() && !SignalingClient.isConnecting()) {
            this.autoConnectWebSocket();
        }
        if ("files".equals(this.currentTab)) {
            this.requestStorageAccessIfNeeded();
        }
        if ("desktop".equals(this.currentTab)) {
            this.scheduleDesktopVideoLayout(false);
            WebRtcClient.refreshActiveVideo();
        }
        this.updateRemoteKeepScreenOnFlag();
    }
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }
    protected void onPause() {
        this.syncNetworkConfigFromEditors();
        WebRtcClient.setUiVisible(false);
        super.onPause();
    }
    protected void onDestroy() {
        this.unregisterSystemBackHandler();
        this.clearRemoteKeepScreenOnFlag();
        this.detachRenderer();
        super.onDestroy();
    }
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if ("files".equals(this.currentTab)) {
            this.showFiles();
        } else if ("terminal".equals(this.currentTab)) {
            this.fitTerminalWebView();
        }
    }
    protected void buildUi() {
        LinearLayout root = new LinearLayout((Context)this);
        root.setOrientation(1);
        root.setBackgroundColor(C_BG);
        this.applyWindowInsets(root);
        root.addView(this.createTopBar(), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(56)));
        this.contentHost = new FrameLayout((Context)this);
        root.addView((View)this.contentHost, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.setContentView((View)root);
        ViewCompat.requestApplyInsets((View)root);
    }

    private void applyWindowInsets(final LinearLayout root) {
        ViewCompat.setOnApplyWindowInsetsListener((View)root, new androidx.core.view.OnApplyWindowInsetsListener(){

            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                int top = Math.max(systemBars.top, displayCutout.top);
                int bottom = Math.max(systemBars.bottom, ime.bottom);
                v.setPadding(0, top, 0, bottom);
                return insets;
            }
        });
    }

}
