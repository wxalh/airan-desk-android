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
abstract class AiranDeskCallbacksActivity
extends AiranDeskHomeActivity {
    @Override
    public void onConnecting(String url) {
        this.onStatus(this.getString(R.string.opening_websocket, new Object[]{url}));
    }
    @Override
    public void onDebug(String message) {
        this.onStatus(message);
    }
    @Override
    public void onOpen() {
        this.showUserStatus(this.getString(R.string.websocket_connected));
    }
    @Override
    public void onClosed(String reason) {
        this.showUserStatus(this.getString(R.string.websocket_closed, new Object[]{reason}));
    }
    @Override
    public void onFailure(String error) {
        if (this.pendingConnectNavigation) {
            this.handleConnectionFailure("WebSocket error: " + error, false);
            return;
        }
        this.showUserStatus(this.getString(R.string.websocket_error, new Object[]{error}));
    }
    @Override
    public void onTextMessage(String text) {
        WebRtcClient.onSignalingMessage(text);
    }
    @Override
    public void onBinaryMessage(byte[] data) {
    }
    @Override
    public void onScreenCapturePermissionRequired() {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                AiranDeskCallbacksActivity.this.showUserStatus("screen permission required");
                AiranDeskCallbacksActivity.this.requestScreenCapturePermission(true);
            }
        });
    }
    @Override
    public void onStorageAccessRequired() {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                AiranDeskCallbacksActivity.this.requestStorageAccessIfNeeded();
            }
        });
    }
    @Override
    public void onAccessibilityPermissionRequired() {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                AiranDeskCallbacksActivity.this.promptAccessibilityPermission();
            }
        });
    }
    @Override
    public void onRemoteAudioRequest(final String mode, final WebRtcClient.AudioConsentCallback callback) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                if (AiranDeskCallbacksActivity.this.isFinishing()) {
                    callback.onResult(true);
                    return;
                }
                String label = AiranDeskCallbacksActivity.this.uiTextFormatter == null ? mode : AiranDeskCallbacksActivity.this.uiTextFormatter.audioModeLabel(mode);
                new AlertDialog.Builder((Context)AiranDeskCallbacksActivity.this)
                        .setTitle((CharSequence)AiranDeskCallbacksActivity.this.getString(R.string.remote_audio_request_title))
                        .setMessage((CharSequence)AiranDeskCallbacksActivity.this.getString(R.string.remote_audio_request_message, new Object[]{label}))
                        .setPositiveButton((CharSequence)AiranDeskCallbacksActivity.this.getString(R.string.allow), new DialogInterface.OnClickListener(){

                            public void onClick(DialogInterface dialog, int which) {
                                callback.onResult(true);
                            }
                        })
                        .setNegativeButton((CharSequence)AiranDeskCallbacksActivity.this.getString(R.string.reject), new DialogInterface.OnClickListener(){

                            public void onClick(DialogInterface dialog, int which) {
                                callback.onResult(false);
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener(){

                            public void onCancel(DialogInterface dialog) {
                                callback.onResult(false);
                            }
                        })
                        .show();
            }
        });
    }
    protected void promptAccessibilityPermission() {
        long now = System.currentTimeMillis();
        if (WebRtcClient.isControlRole() || RemoteInputAccessibilityService.isReady() || this.accessibilityPromptShowing || now - this.lastAccessibilityPromptMs < 15000L) {
            return;
        }
        this.lastAccessibilityPromptMs = now;
        this.accessibilityPromptShowing = true;
        new AlertDialog.Builder((Context)this).setTitle((CharSequence)this.getString(R.string.accessibility_control_title)).setMessage((CharSequence)this.getString(R.string.accessibility_control_message)).setPositiveButton((CharSequence)this.getString(R.string.accessibility_control_open), new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialog, int which) {
                AiranDeskCallbacksActivity.this.accessibilityPromptShowing = false;
                try {
                    Intent intent = new Intent("android.settings.ACCESSIBILITY_SETTINGS");
                    intent.addFlags(0x10000000);
                    AiranDeskCallbacksActivity.this.startActivity(intent);
                }
                catch (Exception e) {
                    AiranDeskCallbacksActivity.this.showUserStatus(AiranDeskCallbacksActivity.this.getString(R.string.accessibility_settings_open_failed));
                }
            }
        }).setNegativeButton((CharSequence)this.getString(R.string.accessibility_control_later), new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialog, int which) {
                AiranDeskCallbacksActivity.this.accessibilityPromptShowing = false;
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener(){

            public void onCancel(DialogInterface dialog) {
                AiranDeskCallbacksActivity.this.accessibilityPromptShowing = false;
            }
        }).show();
    }
    @Override
    public void onStatus(final String message) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                AiranDeskCallbacksActivity.this.updateRemoteKeepScreenOnFlag();
                AiranDeskCallbacksActivity.this.recordRuntimeStatus(message);
                if (AiranDeskCallbacksActivity.this.statusText != null) {
                    AiranDeskCallbacksActivity.this.statusText.setText((CharSequence)UiTextFormatter.shortStatus(StatusLocalizer.localize((Context)AiranDeskCallbacksActivity.this, message)));
                }
                if (message != null && message.toUpperCase(Locale.US).startsWith("REMOTE SESSION DISCONNECTED")) {
                    if (AiranDeskCallbacksActivity.this.shouldShowConnectionFailureDialog()) {
                        AiranDeskCallbacksActivity.this.handleConnectionFailure(message, true);
                    } else {
                        AiranDeskCallbacksActivity.this.syncActiveSessionFromWebRtc();
                        AiranDeskCallbacksActivity.this.updateControlWindowVisibility();
                    }
                    return;
                }
                if (message != null
                        && message.toUpperCase(Locale.US).contains("WEBRTC STOPPED")
                        && AiranDeskCallbacksActivity.this.isControlSessionPage()
                        && !WebRtcClient.isPeerConnected()) {
                    AiranDeskCallbacksActivity.this.handleConnectionFailure("Remote session disconnected: stopped", true);
                    return;
                }
                if (AiranDeskCallbacksActivity.this.pendingConnectNavigation && AiranDeskCallbacksActivity.this.connectDialogMessage != null) {
                    AiranDeskCallbacksActivity.this.updateConnectProgress(StatusLocalizer.connectionProgress((Context)AiranDeskCallbacksActivity.this, message));
                    if (StatusLocalizer.isFatalConnectionStatus(message)) {
                        AiranDeskCallbacksActivity.this.handleConnectionFailure(message, false);
                        return;
                    }
                }
                AiranDeskCallbacksActivity.this.updateControlWindowVisibility();
            }
        });
    }
    @Override
    public void onPeerConnectionChanged(final boolean connected) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                AiranDeskCallbacksActivity.this.syncActiveSessionFromWebRtc();
                AiranDeskCallbacksActivity.this.updateRemoteKeepScreenOnFlag();
                if (!connected) {
                    if ("sessions".equals(AiranDeskCallbacksActivity.this.currentTab)) {
                        AiranDeskCallbacksActivity.this.renderSessionList();
                    } else {
                        AiranDeskCallbacksActivity.this.updateSessionSummary();
                    }
                    return;
                }
                if (!AiranDeskCallbacksActivity.this.pendingConnectNavigation) {
                    if ("sessions".equals(AiranDeskCallbacksActivity.this.currentTab)) {
                        AiranDeskCallbacksActivity.this.renderSessionList();
                    } else {
                        AiranDeskCallbacksActivity.this.updateSessionSummary();
                    }
                    return;
                }
                if (AiranDeskCallbacksActivity.this.pendingSessionRemoteId.length() > 0 && !WebRtcClient.isSessionConnected(AiranDeskCallbacksActivity.this.pendingSessionRemoteId)) {
                    return;
                }
                if (AiranDeskCallbacksActivity.this.pendingSessionRemoteId.length() > 0 && !WebRtcClient.selectSession(AiranDeskCallbacksActivity.this.pendingSessionRemoteId)) {
                    return;
                }
                AiranDeskCallbacksActivity.this.pendingConnectNavigation = false;
                AiranDeskCallbacksActivity.this.updateConnectProgress(AiranDeskCallbacksActivity.this.getString(R.string.remote_connected));
                AiranDeskCallbacksActivity.this.dismissConnectProgress();
                AiranDeskCallbacksActivity.this.showUserStatus(AiranDeskCallbacksActivity.this.getString(R.string.remote_connected));
                AiranDeskCallbacksActivity.this.navigateToSelectedMode();
            }
        });
    }
    @Override
    public void onRemoteFiles(final JSONArray files, final String path, final JSONArray mounted) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                AiranDeskCallbacksActivity.this.currentRemotePath = path == null ? "" : path;
                if (AiranDeskCallbacksActivity.this.remotePathText != null) {
                    String remotePathLabel = AiranDeskCallbacksActivity.this.getString(R.string.remote_path_label);
                    AiranDeskCallbacksActivity.this.remotePathText.setText((CharSequence)(AiranDeskCallbacksActivity.this.currentRemotePath.length() == 0 ? remotePathLabel + ": /" : remotePathLabel + ": " + AiranDeskCallbacksActivity.this.currentRemotePath));
                }
                AiranDeskCallbacksActivity.this.remoteFileObjects.clear();
                AiranDeskCallbacksActivity.this.remoteMountedRoots.clear();
                if (AiranDeskCallbacksActivity.this.fileMountsText != null && mounted != null && mounted.length() > 0) {
                    StringBuilder builder = new StringBuilder(AiranDeskCallbacksActivity.this.getString(R.string.mounts_label)).append(": ");
                    for (int i = 0; i < mounted.length(); ++i) {
                        String root = mounted.optString(i);
                        if (root == null || root.length() == 0) continue;
                        if (!AiranDeskCallbacksActivity.this.remoteMountedRoots.contains(root)) {
                            AiranDeskCallbacksActivity.this.remoteMountedRoots.add(root);
                        }
                        if (i > 0) {
                            builder.append(" | ");
                        }
                        builder.append(root);
                    }
                    AiranDeskCallbacksActivity.this.fileMountsText.setText((CharSequence)builder.toString());
                } else if (AiranDeskCallbacksActivity.this.fileMountsText != null) {
                    AiranDeskCallbacksActivity.this.fileMountsText.setText((CharSequence)"");
                }
                if (RemoteFileEntryUtils.shouldShowParentEntry(AiranDeskCallbacksActivity.this.currentRemotePath)) {
                    AiranDeskCallbacksActivity.this.remoteFileObjects.add(RemoteFileEntryUtils.parentEntry());
                }
                if (files != null) {
                    for (int i = 0; i < files.length(); ++i) {
                        JSONObject object = files.optJSONObject(i);
                        if (object == null) continue;
                        AiranDeskCallbacksActivity.this.remoteFileObjects.add(object);
                    }
                }
                AiranDeskCallbacksActivity.this.selectedRemoteIndex = -1;
                AiranDeskCallbacksActivity.this.lastFileClickIndex = -1;
                AiranDeskCallbacksActivity.this.lastFileClickTimeMs = 0L;
                AiranDeskCallbacksActivity.this.restoreFileRows();
                AiranDeskCallbacksActivity.this.onStatus(AiranDeskCallbacksActivity.this.getString(R.string.remote_path, new Object[]{AiranDeskCallbacksActivity.this.currentRemotePath}));
                AiranDeskCallbacksActivity.this.updateControlWindowVisibility();
            }
        });
    }
    @Override
    public void onFileTransferProgress(final String direction, final String transferId, final String sourcePath, final String targetPath, final String fileName, final long transferredBytes, final long totalBytes, final boolean done, final boolean success) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                TransferRecord record = AiranDeskCallbacksActivity.this.upsertTransferRecord(direction, transferId, sourcePath, targetPath, fileName, transferredBytes, totalBytes, done, success);
                AiranDeskCallbacksActivity.this.updateTransferHistorySummary();
                if ("transfers".equals(AiranDeskCallbacksActivity.this.currentTab)) {
                    AiranDeskCallbacksActivity.this.renderTransferHistoryList();
                }
                AiranDeskCallbacksActivity.this.updateFileTransferStatusView();
                int progress = TransferProgressFormatter.progressPermille(transferredBytes, totalBytes, done);
                if (AiranDeskCallbacksActivity.this.fileTransferText != null) {
                    AiranDeskCallbacksActivity.this.fileTransferText.setText((CharSequence)AiranDeskCallbacksActivity.this.uiTextFormatter.formatTransferStatus(record));
                }
                if (AiranDeskCallbacksActivity.this.fileTransferProgress != null) {
                    AiranDeskCallbacksActivity.this.fileTransferProgress.setProgress(progress);
                }
            }
        });
    }
    @Override
    public void onFileUploadFinished(final boolean success, String remotePath) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                if (success) {
                    AiranDeskCallbacksActivity.this.onStatus(AiranDeskCallbacksActivity.this.getString(R.string.status_upload_complete));
                    if ("files".equals(AiranDeskCallbacksActivity.this.currentTab) && AiranDeskCallbacksActivity.this.contentHost != null) {
                        AiranDeskCallbacksActivity.this.contentHost.postDelayed(new Runnable(){

                            @Override
                            public void run() {
                                AiranDeskCallbacksActivity.this.requestCurrentRemoteFileList();
                            }
                        }, 300L);
                    }
                } else {
                    AiranDeskCallbacksActivity.this.showUserStatus(AiranDeskCallbacksActivity.this.getString(R.string.status_upload_failed));
                }
            }
        });
    }
    @Override
    public void onTerminalInfo(final String os, final String shell, final String mode, final boolean pathTracking) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                AiranDeskCallbacksActivity.this.terminalOs = os == null ? "" : os;
                AiranDeskCallbacksActivity.this.terminalShell = shell == null ? "" : shell;
                AiranDeskCallbacksActivity.this.terminalMode = mode == null ? "" : mode;
                AiranDeskCallbacksActivity.this.terminalPathTracking = pathTracking;
                AiranDeskCallbacksActivity.this.onStatus("terminal: " + AiranDeskCallbacksActivity.this.terminalOs + " " + AiranDeskCallbacksActivity.this.terminalShell + " " + AiranDeskCallbacksActivity.this.terminalMode);
                AiranDeskCallbacksActivity.this.updateTerminalLocalEcho();
                AiranDeskCallbacksActivity.this.injectTerminalPathTracking();
                AiranDeskCallbacksActivity.this.maybeWakeTerminal("terminal info");
            }
        });
    }
    @Override
    public void onTerminalText(final String text) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                if (text != null && text.length() > 0) {
                    AiranDeskCallbacksActivity.this.terminalRemoteOutputSeen = true;
                }
                if (AiranDeskCallbacksActivity.this.appendTerminalText(text)) {
                    AiranDeskCallbacksActivity.this.terminalFallbackDiagnosticShown = false;
                    AiranDeskCallbacksActivity.this.hideTerminalFallbackIfWebReady();
                } else {
                    AiranDeskCallbacksActivity.this.noteInvisibleTerminalBytes(text == null ? 0 : text.length());
                }
            }
        });
    }
    @Override
    public void onTerminalBytes(final byte[] data) {
        this.runOnUiThread(new Runnable(){

            @Override
            public void run() {
                if (data != null && data.length > 0) {
                    AiranDeskCallbacksActivity.this.terminalRemoteOutputSeen = true;
                }
                if (AiranDeskCallbacksActivity.this.appendTerminalBytes(data)) {
                    AiranDeskCallbacksActivity.this.terminalFallbackDiagnosticShown = false;
                    AiranDeskCallbacksActivity.this.hideTerminalFallbackIfWebReady();
                } else {
                    AiranDeskCallbacksActivity.this.noteInvisibleTerminalBytes(data == null ? 0 : data.length);
                }
            }
        });
    }

}
