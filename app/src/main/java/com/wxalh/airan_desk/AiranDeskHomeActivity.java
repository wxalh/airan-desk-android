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
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
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
abstract class AiranDeskHomeActivity
extends AiranDeskDesktopActivity {
    protected OnBackInvokedCallback systemBackCallback;

    protected View createTopBar() {
        LinearLayout bar = new LinearLayout((Context)this);
        bar.setOrientation(0);
        bar.setGravity(16);
        bar.setPadding(this.dp(8), 0, this.dp(12), 0);
        bar.setBackground((Drawable)this.uiFactory.border(C_BG, C_OUTLINE, 0, 0));
        this.topBackButton = this.uiFactory.iconButton(this.androidDrawableId("ic_menu_back", 17301541));
        this.topBackButton.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.navigateUp();
            }
        });
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(this.dp(40), this.dp(40));
        bar.addView((View)this.topBackButton, (ViewGroup.LayoutParams)backLp);
        LinearLayout titleBox = new LinearLayout((Context)this);
        titleBox.setOrientation(1);
        titleBox.setGravity(16);
        titleBox.setPadding(this.dp(8), 0, this.dp(8), 0);
        this.appTitleText = this.uiFactory.text(this.getString(R.string.app_name), 15, C_TEXT, true, true);
        this.appTitleText.setSingleLine(true);
        this.statusText = this.uiFactory.text(this.getString(R.string.status_ready), 11, C_PRIMARY, false, true);
        this.statusText.setSingleLine(true);
        this.updateAppTitleText();
        titleBox.addView((View)this.appTitleText, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(22)));
        titleBox.addView((View)this.statusText, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.dp(18)));
        titleBox.setClickable(true);
        titleBox.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.showRuntimeLogs();
            }
        });
        bar.addView((View)titleBox, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(44), 1.0f));
        this.topSettingsButton = this.uiFactory.iconButton(this.androidDrawableId("ic_menu_manage", 17301570));
        this.topSettingsButton.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.showSettings();
            }
        });
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(this.dp(40), this.dp(40));
        settingsLp.leftMargin = this.dp(8);
        bar.addView((View)this.topSettingsButton, (ViewGroup.LayoutParams)settingsLp);
        this.updateTopBar();
        return bar;
    }
    protected void updateAppTitleText() {
        if (this.appTitleText == null) {
            return;
        }
        this.appTitleText.setText((CharSequence)(this.getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME));
    }
    protected void showHome() {
        this.syncNetworkConfigFromEditors();
        this.detachRenderer();
        this.currentTab = "home";
        this.updateNav();
        LinearLayout content = this.page();
        content.setPadding(this.dp(16), this.dp(12), this.dp(16), this.dp(16));
        content.addView(this.identitySection());
        content.addView(this.sessionSummarySection());
        content.addView(this.transferHistorySummarySection());
        content.addView(this.remoteControlSection());
        this.setScrollableContent((View)content);
    }
    protected void showSettings() {
        this.syncNetworkConfigFromEditors();
        this.detachRenderer();
        if (!"settings".equals(this.currentTab)) {
            this.settingsReturnTab = this.currentTab;
        }
        this.currentTab = "settings";
        this.updateNav();
        this.settingsScreenView = new SettingsScreenView((Context)this, this.uiFactory, new SettingsScreenView.Listener(){

            @Override
            public void onScreenPermissionClicked() {
                if (WebRtcClient.hasScreenCapturePermission()) {
                    AiranDeskHomeActivity.this.showPermissionReadyDialog(AiranDeskHomeActivity.this.getString(R.string.screen_permission), AiranDeskHomeActivity.this.getString(R.string.screen_permission_already_granted_message));
                    return;
                }
                AiranDeskHomeActivity.this.requestScreenCapturePermission(false);
            }

            @Override
            public void onInputServiceClicked() {
                if (RemoteInputAccessibilityService.isReady()) {
                    AiranDeskHomeActivity.this.showPermissionReadyDialog(AiranDeskHomeActivity.this.getString(R.string.input_service), AiranDeskHomeActivity.this.getString(R.string.input_service_already_enabled_message));
                    return;
                }
                AiranDeskHomeActivity.this.startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
            }

            @Override
            public void onAudioPermissionClicked() {
                if (AudioPermissionHelper.hasRecordAudio((Context)AiranDeskHomeActivity.this)) {
                    AiranDeskHomeActivity.this.showPermissionReadyDialog(AiranDeskHomeActivity.this.getString(R.string.audio_permission), AiranDeskHomeActivity.this.getString(R.string.audio_permission_already_granted_message));
                    return;
                }
                AudioPermissionHelper.requestRecordAudio(AiranDeskHomeActivity.this, REQUEST_CODE_RECORD_AUDIO_PERMISSION);
            }

            @Override
            public void onNewPasswordClicked() {
                AiranDeskHomeActivity.this.config.rotatePassword();
                AiranDeskHomeActivity.this.showUserStatus(AiranDeskHomeActivity.this.getString(R.string.password_regenerated));
                AiranDeskHomeActivity.this.showHome();
            }

            @Override
            public void onBatteryOptimizationClicked() {
                AiranDeskHomeActivity.this.openBatteryOptimizationSettings();
            }

            @Override
            public void onAppBatterySettingsClicked() {
                AiranDeskHomeActivity.this.openAppDetailsSettings();
            }

            @Override
            public void onLanguageClicked() {
                AiranDeskHomeActivity.this.toggleLanguage();
            }

            @Override
            public void onDiagnosticsClicked() {
                AiranDeskHomeActivity.this.showRuntimeDiagnosticsDialog();
            }
        });
        this.setScrollableContent(this.settingsScreenView.build(this.config));
    }
    protected void showRuntimeLogs() {
        this.syncNetworkConfigFromEditors();
        this.detachRenderer();
        if (!"logs".equals(this.currentTab)) {
            this.logsReturnTab = this.currentTab;
        }
        this.currentTab = "logs";
        this.updateNav();
        LinearLayout content = this.page();
        content.addView(this.sectionTitle(this.getString(R.string.runtime_logs), this.getString(R.string.status_history)));
        LinearLayout actions = new LinearLayout((Context)this);
        actions.setOrientation(0);
        Button copy = this.uiFactory.secondaryButton(this.getString(R.string.copy_all));
        copy.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.copyTextToClipboard(AiranDeskHomeActivity.this.buildRuntimeLogText(), AiranDeskHomeActivity.this.getString(R.string.runtime_logs));
            }
        });
        Button clear = this.uiFactory.outlineButton(this.getString(R.string.clear_logs));
        clear.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                if (AiranDeskHomeActivity.this.runtimeStatusLog != null) {
                    AiranDeskHomeActivity.this.runtimeStatusLog.clear();
                }
                AiranDeskHomeActivity.this.refreshRuntimeLogText();
                AiranDeskHomeActivity.this.showUserStatus(AiranDeskHomeActivity.this.getString(R.string.status_log_cleared));
            }
        });
        actions.addView((View)copy, (ViewGroup.LayoutParams)this.uiFactory.weight());
        actions.addView((View)clear, (ViewGroup.LayoutParams)this.uiFactory.weightWithLeftMargin());
        content.addView((View)actions, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        this.runtimeLogText = this.uiFactory.text(this.buildRuntimeLogText(), 12, C_TEXT, false, true);
        this.runtimeLogText.setTypeface(Typeface.MONOSPACE);
        this.runtimeLogText.setTextIsSelectable(true);
        this.runtimeLogText.setPadding(this.dp(12), this.dp(12), this.dp(12), this.dp(12));
        this.runtimeLogText.setBackground((Drawable)this.uiFactory.border(C_LOWEST, C_OUTLINE, this.dp(8), 1));
        ScrollView scroll = new ScrollView((Context)this);
        scroll.setFillViewport(true);
        scroll.addView((View)this.runtimeLogText, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        scrollLp.topMargin = this.dp(10);
        content.addView((View)scroll, (ViewGroup.LayoutParams)scrollLp);
        this.setDirectContent((View)content);
        this.refreshRuntimeLogText();
    }
    protected View heroCard() {
        LinearLayout card = this.card();
        card.setGravity(17);
        card.setMinimumHeight(this.dp(132));
        TextView label = this.uiFactory.text(this.getString(R.string.system_status), 12, C_PRIMARY, false, true);
        TextView value = this.uiFactory.text(this.getString(R.string.status_encrypted), 32, C_PRIMARY, true, false);
        label.setGravity(17);
        value.setGravity(17);
        card.addView((View)label, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        card.addView((View)value, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        return card;
    }
    protected View identitySection() {
        LinearLayout section = new LinearLayout((Context)this);
        section.setOrientation(1);
        section.addView(this.sectionTitle(this.getString(R.string.local_device), this.getString(R.string.this_device), 8, 6));
        section.addView(this.identityCard(this.getString(R.string.local_id), this.config.localId(), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.copyLocalCredentialsToClipboard();
            }
        }, this.androidDrawableId("ic_menu_share", 17301586), this.getString(R.string.share_local_credentials)));
        section.addView(this.identityCard(this.getString(R.string.verification_code), this.config.localPassword(), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.config.rotatePassword();
                AiranDeskHomeActivity.this.showUserStatus(AiranDeskHomeActivity.this.getString(R.string.password_regenerated));
                AiranDeskHomeActivity.this.showHome();
            }
        }, this.androidDrawableId("ic_popup_sync", 17301599), this.getString(R.string.new_password)));
        return section;
    }
    protected void copyLocalCredentialsToClipboard() {
        String text = this.getString(R.string.local_credentials_share_text, new Object[]{this.config.localId(), this.config.localPassword()});
        if (ClipboardUtils.copyText((Context)this, "Airan Desk", text)) {
            this.showUserStatus(this.getString(R.string.local_credentials_copied));
            new AlertDialog.Builder((Context)this)
                    .setTitle((CharSequence)this.getString(R.string.share_local_credentials))
                    .setMessage((CharSequence)this.getString(R.string.local_credentials_copied))
                    .setPositiveButton(17039370, null)
                    .show();
        }
    }
    protected View sessionSummarySection() {
        this.syncActiveSessionFromWebRtc();
        LinearLayout row = new LinearLayout((Context)this);
        row.setOrientation(0);
        row.setGravity(16);
        row.setPadding(0, this.dp(8), 0, this.dp(6));
        row.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.showSessions();
            }
        });
        TextView title = this.uiFactory.text(this.getString(R.string.session_management), 20, C_TEXT, true, false);
        row.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        this.sessionCountText = this.uiFactory.text("", 13, C_PRIMARY, true, true);
        this.sessionCountText.setGravity(16);
        row.addView((View)this.sessionCountText, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, this.dp(36)));
        TextView arrow = this.uiFactory.text(">", 20, C_TEXT_MUTED, true, true);
        arrow.setGravity(17);
        row.addView((View)arrow, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(28), this.dp(36)));
        this.updateSessionSummary();
        return row;
    }
    protected void showSessions() {
        this.syncNetworkConfigFromEditors();
        this.detachRenderer();
        this.currentTab = "sessions";
        this.updateNav();
        this.syncActiveSessionFromWebRtc();
        LinearLayout content = this.page();
        content.addView(this.sectionTitle(this.getString(R.string.session_management), this.getString(R.string.current_connections, new Object[]{this.activeSessions.size()})));
        this.sessionListHost = new LinearLayout((Context)this);
        this.sessionListHost.setOrientation(1);
        content.addView((View)this.sessionListHost, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        this.renderSessionList();
        this.setScrollableContent((View)content);
    }
    protected void renderSessionList() {
        if (this.sessionListHost == null) {
            return;
        }
        this.syncActiveSessionFromWebRtc();
        this.sessionListHost.removeAllViews();
        if (this.activeSessions.isEmpty()) {
            LinearLayout empty = this.card();
            TextView text = this.uiFactory.text(this.getString(R.string.no_active_sessions), 14, C_TEXT_MUTED, false, false);
            empty.addView((View)text, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
            this.sessionListHost.addView((View)empty);
            return;
        }
        for (int i = 0; i < this.activeSessions.size(); ++i) {
            this.sessionListHost.addView(this.sessionRow(this.activeSessions.get(i)));
        }
    }
    protected View sessionRow(final RemoteSession session) {
        LinearLayout row = this.card();
        row.setOrientation(0);
        row.setGravity(16);
        row.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.openSession(session);
            }
        });
        LinearLayout info = new LinearLayout((Context)this);
        info.setOrientation(1);
        TextView remote = this.uiFactory.text(session.remoteId, 14, C_TEXT, true, true);
        remote.setSingleLine(true);
        TextView detail = this.uiFactory.text(this.getString(R.string.session_mode, new Object[]{this.uiTextFormatter.modeLabel(session.mode)}), 11, C_TEXT_MUTED, false, true);
        detail.setSingleLine(true);
        info.addView((View)remote, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        info.addView((View)detail, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        row.addView((View)info, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button close = this.uiFactory.ghostButton("X");
        close.setTextColor(C_TEXT_MUTED);
        close.setContentDescription((CharSequence)this.getString(R.string.close_session));
        close.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskHomeActivity.this.closeSession(session);
            }
        });
        row.addView((View)close, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(44), this.dp(40)));
        return row;
    }
    protected void openSession(RemoteSession session) {
        if (session == null || !WebRtcClient.selectSession(session.remoteId)) {
            this.syncActiveSessionFromWebRtc();
            this.showSessions();
            return;
        }
        this.selectedMode = session.mode;
        this.pendingConnectMode = session.mode;
        this.remoteIdDraft = session.remoteId;
        this.navigateToSelectedMode();
    }
    protected void closeSession(RemoteSession session) {
        if (session != null) {
            WebRtcClient.stopSession(session.remoteId);
        }
        this.syncActiveSessionFromWebRtc();
        this.showUserStatus(this.getString(R.string.rtc_stop_requested));
        if ("sessions".equals(this.currentTab)) {
            this.renderSessionList();
        } else {
            this.updateSessionSummary();
        }
    }
    protected void syncActiveSessionFromWebRtc() {
        this.activeSessions.clear();
        List<SessionInfo> sessions = WebRtcClient.connectedControlSessions();
        for (int i = 0; i < sessions.size(); ++i) {
            SessionInfo info = sessions.get(i);
            if (info == null || !info.connected || !info.controlRole || info.remoteId.length() <= 0) continue;
            this.activeSessions.add(new RemoteSession(info.remoteId, info.mode));
        }
    }
    protected void updateSessionSummary() {
        this.syncActiveSessionFromWebRtc();
        if (this.sessionCountText != null) {
            this.sessionCountText.setText((CharSequence)this.getString(R.string.current_connections, new Object[]{this.activeSessions.size()}));
        }
    }
    protected View transferHistorySummarySection() {
        this.transferHistoryScreenView = this.createTransferHistoryScreenView();
        return this.transferHistoryScreenView.buildSummary(this.transferHistory);
    }
    protected TransferHistoryScreenView createTransferHistoryScreenView() {
        return new TransferHistoryScreenView((Context)this, this.uiFactory, this.uiTextFormatter, this.transferHistoryTimeFormat, new TransferHistoryScreenView.Listener(){

            @Override
            public void onShowTransferHistory() {
                AiranDeskHomeActivity.this.showTransferHistory();
            }

            @Override
            public void onClearHistory() {
                AiranDeskHomeActivity.this.transferHistory.clear();
                AiranDeskHomeActivity.this.saveTransferHistory();
                AiranDeskHomeActivity.this.updateTransferHistorySummary();
                AiranDeskHomeActivity.this.updateFileTransferStatusView();
                AiranDeskHomeActivity.this.showUserStatus(AiranDeskHomeActivity.this.getString(R.string.status_transfer_history_cleared));
                AiranDeskHomeActivity.this.showTransferHistory();
            }

            @Override
            public void onOpenLocalPath(String path) {
                AiranDeskHomeActivity.this.openTransferPathInFileManager(path);
            }

            @Override
            public boolean isTransferControlRole() {
                return AiranDeskHomeActivity.this.isTransferControlRole();
            }
        });
    }
    protected void updateTransferHistorySummary() {
        if (this.transferHistoryScreenView == null) {
            return;
        }
        this.transferHistoryScreenView.updateSummary(this.transferHistory);
    }
    protected void showTransferHistory() {
        this.syncNetworkConfigFromEditors();
        this.detachRenderer();
        this.currentTab = "transfers";
        this.updateNav();
        this.transferHistoryScreenView = this.createTransferHistoryScreenView();
        this.setScrollableContent(this.transferHistoryScreenView.buildHistoryPage(this.transferHistory));
    }
    protected void renderTransferHistoryList() {
        if (this.transferHistoryScreenView == null) {
            return;
        }
        this.transferHistoryScreenView.renderList(this.transferHistory);
    }
    protected boolean isTransferControlRole() {
        return !WebRtcClient.isPeerConnected() || WebRtcClient.isControlRole();
    }
    protected void openTransferPathInFileManager(String path) {
        if (path == null || path.length() == 0 || TransferHistoryStore.looksLikeTransferId(path)) {
            this.showUserStatus(this.getString(R.string.status_nothing_selected));
            return;
        }
        LocalFileOpener.Result result = LocalFileOpener.openTransferPath((Context)this, path);
        if (result == LocalFileOpener.Result.OPENED) {
            return;
        }
        File fallback = LocalFileOpener.copyFallbackPath(path);
        if (result == LocalFileOpener.Result.MISSING || fallback == null) {
            this.copyTextToClipboard(path, "Transfer path");
            this.showUserStatus(this.getString(R.string.transfer_path_missing_copied));
            return;
        }
        this.copyTextToClipboard(fallback.getAbsolutePath(), "Transfer directory");
        this.showUserStatus(this.getString(R.string.file_manager_open_failed_copied_directory));
    }
    protected View remoteControlSection() {
        this.remoteControlSectionView = new RemoteControlSectionView((Context)this, this.uiFactory, new RemoteControlSectionView.Listener(){

            @Override
            public void onModeChanged(String mode) {
                AiranDeskHomeActivity.this.selectedMode = mode;
            }

            @Override
            public void onConnectRequested() {
                AiranDeskHomeActivity.this.connectRemote();
            }

            @Override
            public boolean isLocalCredentialId(String id) {
                return AiranDeskHomeActivity.this.isLocalCredentialId(id);
            }

            @Override
            public void onCredentialsFilledFromPaste() {
                AiranDeskHomeActivity.this.remoteIdDraft = AiranDeskHomeActivity.this.remoteControlSectionView.remoteId();
                AiranDeskHomeActivity.this.remotePasswordDraft = AiranDeskHomeActivity.this.remoteControlSectionView.password();
                AiranDeskHomeActivity.this.onStatus("Remote credentials filled from pasted text");
            }
        });
        return this.remoteControlSectionView.build(this.selectedMode, this.remoteIdDraft, this.remotePasswordDraft);
    }
    protected View identityCard(String label, String value, View.OnClickListener action) {
        return this.identityCard(label, value, action, this.androidDrawableId("ic_popup_sync", 17301599), "");
    }
    protected View identityCard(String label, String value, View.OnClickListener action, int actionIcon, String actionDescription) {
        LinearLayout card = this.card();
        LinearLayout row = new LinearLayout((Context)this);
        row.setOrientation(0);
        TextView labelView = this.uiFactory.text(label, 12, C_TEXT_MUTED, false, true);
        row.addView((View)labelView, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (action != null) {
            Button actionButton = this.uiFactory.iconButton(actionIcon);
            actionButton.setContentDescription((CharSequence)(actionDescription == null ? "" : actionDescription));
            actionButton.setOnClickListener(action);
            row.addView((View)actionButton, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(44), this.dp(36)));
        }
        card.addView((View)row, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        TextView valueView = this.uiFactory.text(value, 14, C_PRIMARY, false, true);
        valueView.setSingleLine(true);
        valueView.setIncludeFontPadding(false);
        valueView.setPadding(0, this.dp(6), 0, 0);
        card.addView((View)valueView, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        this.uiFactory.fitSingleLineText(valueView, value, 14.0f, 8.0f);
        return card;
    }
    protected void connectRemote() {
        this.syncNetworkConfigFromEditors();
        String targetId = this.remoteControlSectionView == null ? "" : this.remoteControlSectionView.remoteId();
        String password = this.remoteControlSectionView == null ? "" : this.remoteControlSectionView.password();
        if (targetId.length() == 0 || password.length() == 0) {
            this.showUserStatus(this.getString(R.string.remote_required));
            return;
        }
        this.remoteIdDraft = targetId;
        this.remotePasswordDraft = password;
        if (this.remoteControlSectionView != null) {
            this.selectedMode = this.remoteControlSectionView.selectedMode();
        }
        if ("file".equals(this.selectedMode)) {
            this.requestStorageAccessIfNeeded();
        }
        if (!SignalingClient.isConnected() && !SignalingClient.isConnecting()) {
            this.connectWsFromConfig(false);
        }
        this.pendingConnectMode = this.selectedMode;
        this.pendingSessionRemoteId = targetId;
        this.pendingConnectNavigation = true;
        WebRtcClient.resetAudioModeForNewConnection();
        this.updateDesktopToolbarLabels();
        this.showConnectProgress(this.getString(R.string.waiting_remote_connection));
        boolean queuedOrSent = WebRtcClient.startControl(targetId, Md5Util.md5Upper(password), this.selectedMode);
        this.syncActiveSessionFromWebRtc();
        this.showUserStatus(queuedOrSent ? this.getString(R.string.remote_requested) : this.getString(R.string.remote_not_sent));
        if (queuedOrSent) {
            this.updateConnectProgress(this.getString(R.string.remote_requested));
        } else {
            this.pendingConnectNavigation = false;
            WebRtcClient.stopSession(targetId);
            this.syncActiveSessionFromWebRtc();
            this.dismissConnectProgress();
            this.showConnectionFailedDialog(this.connectionFailureMessage("CONNECT was not sent"));
        }
    }
    protected void navigateToSelectedMode() {
        String mode;
        String string2 = mode = this.pendingConnectMode == null ? this.selectedMode : this.pendingConnectMode;
        if ("file".equals(mode)) {
            this.showFiles();
        } else if ("terminal".equals(mode)) {
            this.showTerminal();
        } else {
            this.showDesktop();
        }
    }
    protected void showConnectProgress(String message) {
        if (this.connectDialog != null && this.connectDialog.isShowing()) {
            this.updateConnectProgress(message);
            return;
        }
        LinearLayout box = new LinearLayout((Context)this);
        box.setOrientation(1);
        box.setPadding(this.dp(24), this.dp(12), this.dp(24), this.dp(4));
        this.connectDialogMessage = this.uiFactory.text(message, 14, C_TEXT, false, false);
        this.connectDialogMessage.setGravity(16);
        box.addView((View)this.connectDialogMessage, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.connectDialog = new AlertDialog.Builder((Context)this).setTitle((CharSequence)this.getString(R.string.connecting_remote)).setView((View)box).setNegativeButton((CharSequence)this.getString(R.string.cancel), new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialog, int which) {
                AiranDeskHomeActivity.this.pendingConnectNavigation = false;
                WebRtcClient.stop();
            }
        }).create();
        this.connectDialog.setOnCancelListener(new DialogInterface.OnCancelListener(){

            public void onCancel(DialogInterface dialog) {
                AiranDeskHomeActivity.this.pendingConnectNavigation = false;
                WebRtcClient.stop();
            }
        });
        this.connectDialog.show();
    }
    protected void updateConnectProgress(String message) {
        if (this.connectDialogMessage != null) {
            this.connectDialogMessage.setText((CharSequence)UiTextFormatter.shortStatus(message));
        }
    }
    protected void dismissConnectProgress() {
        if (this.connectDialog != null && this.connectDialog.isShowing()) {
            this.connectDialog.dismiss();
        }
        this.connectDialog = null;
        this.connectDialogMessage = null;
    }
    protected String connectionFailureMessage(String rawMessage) {
        String reason = StatusLocalizer.connectionFailureReason((Context)this, rawMessage);
        return this.getString(R.string.connection_failed_with_reason, new Object[]{reason});
    }
    protected boolean shouldShowConnectionFailureDialog() {
        return this.pendingConnectNavigation || WebRtcClient.isControlRole() || this.isControlSessionPage();
    }
    protected void handleConnectionFailure(String rawMessage, boolean returnHome) {
        this.pendingConnectNavigation = false;
        this.dismissConnectProgress();
        this.syncActiveSessionFromWebRtc();
        String message = this.connectionFailureMessage(rawMessage);
        this.showUserStatus(message);
        if (returnHome && !"home".equals(this.currentTab)) {
            this.showHome();
        }
        this.showConnectionFailedDialog(message);
    }
    protected void showConnectionFailedDialog(String message) {
        if (this.isFinishing() || this.connectionFailureDialogShowing) {
            return;
        }
        this.connectionFailureDialogShowing = true;
        new AlertDialog.Builder((Context)this).setTitle((CharSequence)this.getString(R.string.connection_failed)).setMessage((CharSequence)message).setPositiveButton((CharSequence)this.getString(android.R.string.ok), new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialog, int which) {
                AiranDeskHomeActivity.this.connectionFailureDialogShowing = false;
            }
        }).setOnCancelListener(new DialogInterface.OnCancelListener(){

            public void onCancel(DialogInterface dialog) {
                AiranDeskHomeActivity.this.connectionFailureDialogShowing = false;
            }
        }).show();
    }
    protected void navigateUp() {
        if ("home".equals(this.currentTab)) {
            this.moveTaskToBack(true);
            return;
        }
        if ("settings".equals(this.currentTab)) {
            this.navigateToTab(this.settingsReturnTab);
            return;
        }
        if ("logs".equals(this.currentTab)) {
            this.navigateToTab(this.logsReturnTab);
            return;
        }
        if (("files".equals(this.currentTab) || "terminal".equals(this.currentTab)) && WebRtcClient.isPeerConnected()) {
            this.confirmDisconnect(new Runnable(){

                @Override
                public void run() {
                    AiranDeskHomeActivity.this.showHome();
                }
            });
            return;
        }
        if (this.isControlSessionPage()) {
            this.promptLeaveControlSession();
            return;
        }
        this.showHome();
    }
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            this.navigateUp();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    public void onBackPressed() {
        this.navigateUp();
    }
    protected void registerSystemBackHandler() {
        if (Build.VERSION.SDK_INT < 33 || this.systemBackCallback != null) {
            return;
        }
        this.systemBackCallback = new OnBackInvokedCallback(){

            @Override
            public void onBackInvoked() {
                AiranDeskHomeActivity.this.navigateUp();
            }
        };
        this.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this.systemBackCallback);
    }
    protected void unregisterSystemBackHandler() {
        if (Build.VERSION.SDK_INT < 33 || this.systemBackCallback == null) {
            return;
        }
        this.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(this.systemBackCallback);
        this.systemBackCallback = null;
    }
    protected void navigateToTab(String tab) {
        if ("desktop".equals(tab)) {
            this.showDesktop();
        } else if ("files".equals(tab)) {
            this.showFiles();
        } else if ("terminal".equals(tab)) {
            this.showTerminal();
        } else if ("sessions".equals(tab)) {
            this.showSessions();
        } else if ("logs".equals(tab)) {
            this.showRuntimeLogs();
        } else if ("transfers".equals(tab)) {
            this.showTransferHistory();
        } else {
            this.showHome();
        }
    }
    protected boolean isControlSessionPage() {
        return "desktop".equals(this.currentTab) || "files".equals(this.currentTab) || "terminal".equals(this.currentTab);
    }
    protected void promptLeaveControlSession() {
        if (!WebRtcClient.isControlRole() || !WebRtcClient.isPeerConnected()) {
            this.showHome();
            return;
        }
        new AlertDialog.Builder((Context)this).setTitle((CharSequence)this.getString(R.string.leave_control_title)).setMessage((CharSequence)this.getString(R.string.leave_control_message)).setPositiveButton((CharSequence)this.getString(R.string.disconnect_ws), new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialog, int which) {
                String remoteId = WebRtcClient.currentRemoteId();
                if (remoteId.length() > 0) {
                    WebRtcClient.stopSession(remoteId);
                } else {
                    WebRtcClient.stop();
                }
                AiranDeskHomeActivity.this.syncActiveSessionFromWebRtc();
                AiranDeskHomeActivity.this.showUserStatus(AiranDeskHomeActivity.this.getString(R.string.rtc_stop_requested));
                AiranDeskHomeActivity.this.showHome();
            }
        }).setNegativeButton((CharSequence)this.getString(R.string.keep_in_sessions), new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialog, int which) {
                AiranDeskHomeActivity.this.syncActiveSessionFromWebRtc();
                AiranDeskHomeActivity.this.showSessions();
            }
        }).setNeutralButton((CharSequence)this.getString(R.string.cancel), null).show();
    }
    protected void confirmDisconnect(final Runnable afterDisconnect) {
        new AlertDialog.Builder((Context)this).setTitle((CharSequence)this.getString(R.string.disconnect_remote_title)).setMessage((CharSequence)this.getString(R.string.disconnect_remote_message)).setPositiveButton((CharSequence)this.getString(R.string.disconnect_ws), new DialogInterface.OnClickListener(){

            public void onClick(DialogInterface dialog, int which) {
                WebRtcClient.stop();
                AiranDeskHomeActivity.this.syncActiveSessionFromWebRtc();
                AiranDeskHomeActivity.this.showUserStatus(AiranDeskHomeActivity.this.getString(R.string.rtc_stop_requested));
                if (afterDisconnect != null) {
                    afterDisconnect.run();
                }
            }
        }).setNegativeButton((CharSequence)this.getString(R.string.cancel), null).show();
    }

}
