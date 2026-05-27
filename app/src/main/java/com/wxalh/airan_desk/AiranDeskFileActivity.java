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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

@SuppressWarnings({"deprecation", "unchecked"})
abstract class AiranDeskFileActivity
extends AiranDeskBaseActivity {
    private static final int FILE_NAME_COL = 20;
    private static final int FILE_SIZE_COL = 12;
    private static final int FILE_DATE_COL = 19;
    private float fileTableZoom = 1.0f;
    private ScaleGestureDetector fileTableScaleDetector;
    private HorizontalScrollView fileTableScroll;
    private LinearLayout fileTableContainer;
    private TextView fileHeaderText;

    protected abstract boolean isLandscape();

    protected abstract void showDesktop();

    protected abstract void showTransferHistory();

    protected void showFiles() {
        this.syncNetworkConfigFromEditors();
        this.requestStorageAccessIfNeeded();
        this.detachRenderer();
        this.currentTab = "files";
        this.updateNav();
        LinearLayout content = this.page();
        content.addView(this.sectionTitle(this.getString(R.string.file_manager), this.getString(R.string.remote_device)));
        this.addDesktopPreviewIfAvailable(content);
        content.addView(this.pathChip());
        this.fileMountsText = this.uiFactory.text("", 11, C_TEXT_MUTED, false, true);
        this.fileMountsText.setPadding(0, 0, 0, this.dp(6));
        content.addView((View)this.fileMountsText, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        content.addView(this.fileActions());
        content.addView(this.fileTransferStatus(), (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        this.fileAdapter = new ArrayAdapter<String>((Context)this, 17367043, new ArrayList()){

            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView)super.getView(position, convertView, parent);
                view.setTextColor(C_TEXT);
                view.setTextSize(14.0f * AiranDeskFileActivity.this.fileTableZoom);
                view.setTypeface(Typeface.MONOSPACE);
                view.setMinHeight(AiranDeskFileActivity.this.dp(48));
                view.setGravity(16);
                view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                view.setPadding(AiranDeskFileActivity.this.dp(12), view.getPaddingTop(), AiranDeskFileActivity.this.dp(12), view.getPaddingBottom());
                boolean selected = position == AiranDeskFileActivity.this.selectedRemoteIndex;
                view.setTextColor(selected ? C_ON_PRIMARY : C_TEXT);
                view.setBackgroundColor(selected ? C_PRIMARY : C_LOWEST);
                return view;
            }
        };
        this.fileList = new ListView((Context)this);
        this.fileList.setDivider(null);
        this.fileList.setCacheColorHint(0);
        this.fileList.setBackgroundColor(C_LOWEST);
        this.fileList.setChoiceMode(1);
        this.fileList.setAdapter(this.fileAdapter);
        this.fileList.setOnTouchListener(new View.OnTouchListener(){

            public boolean onTouch(View v, MotionEvent event) {
                AiranDeskFileActivity.this.handleFileTableScale(event);
                return false;
            }
        });
        this.fileList.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= AiranDeskFileActivity.this.remoteFileObjects.size()) {
                    return;
                }
                long now = System.currentTimeMillis();
                boolean doubleClick = position == AiranDeskFileActivity.this.lastFileClickIndex && now - AiranDeskFileActivity.this.lastFileClickTimeMs < 450L;
                AiranDeskFileActivity.this.lastFileClickIndex = position;
                AiranDeskFileActivity.this.lastFileClickTimeMs = now;
                AiranDeskFileActivity.this.selectedRemoteIndex = position;
                AiranDeskFileActivity.this.fileList.setItemChecked(position, true);
                AiranDeskFileActivity.this.fileAdapter.notifyDataSetChanged();
                JSONObject object = (JSONObject)AiranDeskFileActivity.this.remoteFileObjects.get(position);
                if (!doubleClick) {
                    AiranDeskFileActivity.this.maybeShowSelectedFileDetails(object);
                }
                if (doubleClick && object.optBoolean("is_dir")) {
                    AiranDeskFileActivity.this.openRemoteDirectory(object);
                }
            }
        });
        this.fileList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener(){

            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= AiranDeskFileActivity.this.remoteFileObjects.size()) {
                    return true;
                }
                AiranDeskFileActivity.this.selectedRemoteIndex = position;
                AiranDeskFileActivity.this.fileList.setItemChecked(position, true);
                AiranDeskFileActivity.this.fileAdapter.notifyDataSetChanged();
                JSONObject object = (JSONObject)AiranDeskFileActivity.this.remoteFileObjects.get(position);
                if (object.optBoolean("is_dir")) {
                    AiranDeskFileActivity.this.openRemoteDirectory(object);
                }
                return true;
            }
        });
        content.addView(this.fileTable(), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.restoreFileRows();
        this.requestCurrentRemoteFileList();
        this.setDirectContent((View)content);
    }
    protected View fileTable() {
        this.ensureFileTableScaleDetector();
        this.fileTableScroll = new HorizontalScrollView((Context)this);
        this.fileTableScroll.setHorizontalScrollBarEnabled(true);
        this.fileTableScroll.setFillViewport(true);
        this.fileTableScroll.setOnTouchListener(new View.OnTouchListener(){

            public boolean onTouch(View v, MotionEvent event) {
                AiranDeskFileActivity.this.handleFileTableScale(event);
                return false;
            }
        });
        this.fileTableContainer = new LinearLayout((Context)this);
        this.fileTableContainer.setOrientation(1);
        this.fileTableContainer.addView(this.fileHeader(), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        this.fileTableContainer.addView((View)this.fileList, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -1));
        this.fileTableScroll.addView((View)this.fileTableContainer, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-2, -1));
        this.refreshFileTableMetrics();
        return this.fileTableScroll;
    }
    protected void addDesktopPreviewIfAvailable(LinearLayout content) {
        if (!(content != null && WebRtcClient.isControlRole() && WebRtcClient.isPeerConnected() && "desktop".equals(WebRtcClient.currentMode()))) {
            return;
        }
        FrameLayout preview = new FrameLayout((Context)this);
        preview.setBackgroundColor(C_LOWEST);
        preview.setClipChildren(true);
        preview.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.showDesktop();
            }
        });
        this.renderer = new TextureVideoRenderer((Context)this);
        this.renderer.init(WebRtcClient.eglContext());
        this.renderer.setMirror(false);
        WebRtcClient.setRenderer(this.renderer);
        preview.addView((View)this.renderer, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1, 17));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, this.isLandscape() ? this.dp(140) : this.dp(168));
        lp.bottomMargin = this.dp(10);
        content.addView((View)preview, (ViewGroup.LayoutParams)lp);
    }
    protected View fileTransferStatus() {
        LinearLayout box = new LinearLayout((Context)this);
        box.setOrientation(1);
        box.setPadding(this.dp(12), this.dp(10), this.dp(12), this.dp(10));
        box.setBackground((Drawable)this.uiFactory.border(C_CONTAINER, C_OUTLINE, this.dp(8), 1));
        box.setClickable(true);
        box.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.showTransferHistory();
            }
        });
        this.fileTransferText = this.uiFactory.text(this.getString(R.string.transfer_status_idle), 12, C_TEXT_MUTED, false, true);
        this.fileTransferText.setSingleLine(false);
        this.fileTransferText.setMaxLines(2);
        box.addView((View)this.fileTransferText, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        this.fileTransferProgress = new ProgressBar((Context)this, null, 16842872);
        this.fileTransferProgress.setMax(1000);
        this.fileTransferProgress.setProgress(0);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, this.dp(6));
        progressLp.topMargin = this.dp(8);
        box.addView((View)this.fileTransferProgress, (ViewGroup.LayoutParams)progressLp);
        LinearLayout.LayoutParams lp = this.uiFactory.fullWidth();
        lp.topMargin = this.dp(10);
        lp.bottomMargin = this.dp(8);
        box.setLayoutParams((ViewGroup.LayoutParams)lp);
        this.updateFileTransferStatusView();
        return box;
    }
    protected View fileActions() {
        LinearLayout row = new LinearLayout((Context)this);
        row.setOrientation(0);
        row.setGravity(16);
        Button run = this.uiFactory.fileActionButton(this.getString(R.string.file_action_run), this.androidDrawableId("ic_media_play", 17301540));
        run.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.runSelectedRemoteItem();
            }
        });
        row.addView((View)run, (ViewGroup.LayoutParams)this.fileActionLayout(false));
        Button download = this.uiFactory.fileActionButton(this.getString(R.string.file_action_download), this.androidDrawableId("stat_sys_download_done", 17301634));
        download.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.downloadSelectedRemoteItem();
            }
        });
        row.addView((View)download, (ViewGroup.LayoutParams)this.fileActionLayout(true));
        Button uploadFile = this.uiFactory.fileActionButton(this.getString(R.string.file_action_upload), this.androidDrawableId("ic_doc_generic", 17301582));
        uploadFile.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.pickUploadFile();
            }
        });
        row.addView((View)uploadFile, (ViewGroup.LayoutParams)this.fileActionLayout(true));
        Button uploadFolder = this.uiFactory.fileActionButton(this.getString(R.string.file_action_upload), this.androidDrawableId("ic_doc_folder", 17301556));
        uploadFolder.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.pickUploadDirectory();
            }
        });
        row.addView((View)uploadFolder, (ViewGroup.LayoutParams)this.fileActionLayout(true));
        Button refresh = this.uiFactory.fileActionButton(this.getString(R.string.file_action_refresh), 17301599);
        refresh.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.requestCurrentRemoteFileList();
            }
        });
        row.addView((View)refresh, (ViewGroup.LayoutParams)this.fileActionLayout(true));
        return row;
    }
    protected LinearLayout.LayoutParams fileActionLayout(boolean leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, this.dp(42), 1.0f);
        if (leftMargin) {
            lp.leftMargin = this.dp(5);
        }
        return lp;
    }
    protected void showFileActionMenu(final View anchor) {
        FileBrowserMenus.showFileActionMenu((Context)this, anchor, new FileBrowserMenus.FileActionListener(){

            @Override
            public void onUploadFile() {
                AiranDeskFileActivity.this.pickUploadFile();
            }

            @Override
            public void onUploadDirectory() {
                AiranDeskFileActivity.this.pickUploadDirectory();
            }

            @Override
            public void onRunSelected() {
                AiranDeskFileActivity.this.runSelectedRemoteItem();
            }

            @Override
            public void onShowMountRoots(View menuAnchor) {
                AiranDeskFileActivity.this.showMountRootPicker(menuAnchor);
            }
        });
    }
    protected void pickUploadFile() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        this.startActivityForResult(intent, 1002);
    }
    protected void pickUploadDirectory() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
        intent.addFlags(1);
        intent.addFlags(64);
        this.startActivityForResult(intent, 1003);
    }
    protected View pathChip() {
        TextView path;
        String remotePathLabel = this.getString(R.string.remote_path_label);
        this.remotePathText = path = this.uiFactory.text(this.currentRemotePath.length() == 0 ? remotePathLabel + ": /" : remotePathLabel + ": " + this.currentRemotePath, 12, C_PRIMARY, false, true);
        path.setPadding(this.dp(12), this.dp(10), this.dp(12), this.dp(10));
        path.setSingleLine(true);
        path.setBackground((Drawable)this.uiFactory.border(C_CONTAINER, C_OUTLINE, this.dp(8), 1));
        path.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.showMountRootPicker(v);
            }
        });
        path.setOnLongClickListener(new View.OnLongClickListener(){

            public boolean onLongClick(View v) {
                String value = AiranDeskFileActivity.this.currentRemotePath == null || AiranDeskFileActivity.this.currentRemotePath.length() == 0 ? "/" : AiranDeskFileActivity.this.currentRemotePath;
                AiranDeskFileActivity.this.copyTextToClipboard(value, "Remote path");
                AiranDeskFileActivity.this.showUserStatus(AiranDeskFileActivity.this.getString(R.string.remote_path_copied));
                return true;
            }
        });
        HorizontalScrollView scroll = new HorizontalScrollView((Context)this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setBackground((Drawable)this.uiFactory.border(C_CONTAINER, C_OUTLINE, this.dp(8), 1));
        scroll.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskFileActivity.this.showMountRootPicker(v);
            }
        });
        scroll.setOnLongClickListener(new View.OnLongClickListener(){

            public boolean onLongClick(View v) {
                String value = AiranDeskFileActivity.this.currentRemotePath == null || AiranDeskFileActivity.this.currentRemotePath.length() == 0 ? "/" : AiranDeskFileActivity.this.currentRemotePath;
                AiranDeskFileActivity.this.copyTextToClipboard(value, "Remote path");
                AiranDeskFileActivity.this.showUserStatus(AiranDeskFileActivity.this.getString(R.string.remote_path_copied));
                return true;
            }
        });
        path.setBackgroundColor(0);
        scroll.addView((View)path, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-2, -1));
        LinearLayout.LayoutParams lp = this.uiFactory.fullWidth();
        lp.bottomMargin = this.dp(12);
        scroll.setLayoutParams((ViewGroup.LayoutParams)lp);
        return scroll;
    }
    protected View fileHeader() {
        LinearLayout row = new LinearLayout((Context)this);
        row.setOrientation(0);
        row.setPadding(0, this.dp(10), 0, this.dp(4));
        this.fileHeaderText = this.uiFactory.text(this.fileHeaderRowText(), 10, C_TEXT_MUTED, false, true);
        this.fileHeaderText.setTypeface(Typeface.MONOSPACE);
        this.fileHeaderText.setTextSize(10.0f * this.fileTableZoom);
        row.addView((View)this.fileHeaderText, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        return row;
    }
    protected void restoreFileRows() {
        if (this.fileAdapter == null) {
            return;
        }
        this.fileAdapter.clear();
        for (int i = 0; i < this.remoteFileObjects.size(); ++i) {
            JSONObject object = this.remoteFileObjects.get(i);
            if (RemoteFileEntryUtils.isParentEntry(object)) {
                this.fileAdapter.add("..");
                continue;
            }
            this.fileAdapter.add(this.formatRemoteFileRow(object));
        }
        this.fileAdapter.notifyDataSetChanged();
    }
    protected String formatRemoteFileRow(JSONObject object) {
        if (object == null) {
            return "";
        }
        String name = this.truncateForColumn(object.optString("name", ""), 20);
        String size = object.optBoolean("is_dir") ? "<DIR>" : UiTextFormatter.formatBytes(Math.max(0L, object.optLong("file_size", 0L)));
        String date = this.formatRemoteFileDate(object);
        return String.format(Locale.US, "%-" + FILE_NAME_COL + "s  %-" + FILE_SIZE_COL + "s  %-" + FILE_DATE_COL + "s", name, size, date);
    }
    protected String formatRemoteFileDate(JSONObject object) {
        if (object == null) {
            return "-";
        }
        String raw = object.optString("file_last_mod_time", "");
        if (raw == null || raw.length() == 0) {
            raw = object.optString("last_modified", "");
        }
        if (raw == null || raw.length() == 0) {
            raw = object.optString("mtime", "");
        }
        raw = raw == null ? "" : raw.trim();
        if (raw.length() == 0) {
            return "-";
        }
        Date parsed = this.parseRemoteFileDate(raw);
        if (parsed != null) {
            return this.remoteDateDisplayFormat().format(parsed);
        }
        if (raw.length() >= 19 && raw.charAt(10) == 'T') {
            return raw.substring(0, 19).replace('T', ' ');
        }
        if (raw.length() >= 19) {
            return raw.substring(0, 19);
        }
        return raw;
    }
    protected Date parseRemoteFileDate(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0) {
            return null;
        }
        String[] patterns = new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
            parser.setLenient(false);
            if (pattern.indexOf('X') < 0) {
                parser.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
            try {
                return parser.parse(value);
            }
            catch (ParseException parseException) {
            }
        }
        return null;
    }
    protected SimpleDateFormat remoteDateDisplayFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        format.setTimeZone(TimeZone.getDefault());
        return format;
    }
    protected String truncateForColumn(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        if (maxChars <= 3) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, maxChars - 3) + "...";
    }
    protected void maybeShowSelectedFileDetails(JSONObject object) {
        if (object == null || RemoteFileEntryUtils.isParentEntry(object)) {
            return;
        }
        String name = object.optString("name", "");
        if (name.length() <= 20) {
            return;
        }
        String size = object.optBoolean("is_dir") ? "<DIR>" : UiTextFormatter.formatBytes(Math.max(0L, object.optLong("file_size", 0L)));
        String date = this.formatRemoteFileDate(object);
        StringBuilder message = new StringBuilder();
        message.append(this.getString(R.string.name)).append("\n").append(name).append("\n\n");
        message.append(this.getString(R.string.size)).append("\n").append(size).append("\n\n");
        message.append(this.getString(R.string.date)).append("\n").append(date);
        new AlertDialog.Builder((Context)this).setTitle((CharSequence)this.getString(R.string.file_manager)).setMessage((CharSequence)message.toString()).setPositiveButton((CharSequence)this.getString(android.R.string.ok), null).show();
    }
    protected void ensureFileTableScaleDetector() {
        if (this.fileTableScaleDetector != null) {
            return;
        }
        this.fileTableScaleDetector = new ScaleGestureDetector((Context)this, (ScaleGestureDetector.OnScaleGestureListener)new ScaleGestureDetector.SimpleOnScaleGestureListener(){

            public boolean onScale(ScaleGestureDetector detector) {
                float next = DesktopViewportMath.clamp(AiranDeskFileActivity.this.fileTableZoom * detector.getScaleFactor(), 0.85f, 1.8f);
                if (Math.abs(next - AiranDeskFileActivity.this.fileTableZoom) < 0.01f) {
                    return true;
                }
                AiranDeskFileActivity.this.fileTableZoom = next;
                AiranDeskFileActivity.this.refreshFileTableMetrics();
                AiranDeskFileActivity.this.restoreFileRows();
                return true;
            }
        });
    }
    protected void handleFileTableScale(MotionEvent event) {
        if (this.fileTableScaleDetector != null && event != null && event.getPointerCount() >= 2) {
            this.fileTableScaleDetector.onTouchEvent(event);
        }
    }
    protected void refreshFileTableMetrics() {
        int width = this.fileTableContentWidth();
        if (this.fileHeaderText != null) {
            this.fileHeaderText.setText((CharSequence)this.fileHeaderRowText());
            this.fileHeaderText.setTextSize(10.0f * this.fileTableZoom);
        }
        if (this.fileTableContainer != null) {
            ViewGroup.LayoutParams lp = this.fileTableContainer.getLayoutParams();
            if (lp != null) {
                lp.width = width;
                this.fileTableContainer.setLayoutParams(lp);
            }
        }
    }
    protected int fileTableContentWidth() {
        TextView probe = this.uiFactory.text("", 14, C_TEXT, false, true);
        probe.setTypeface(Typeface.MONOSPACE);
        probe.setTextSize(14.0f * this.fileTableZoom);
        int chars = FILE_NAME_COL + FILE_SIZE_COL + FILE_DATE_COL + 4;
        float textWidth = probe.getPaint().measureText(this.paddingChars(chars));
        int iconSpace = this.dp(26);
        int horizontalPadding = this.dp(28);
        return Math.max(this.dp(260), (int)Math.ceil(textWidth) + iconSpace + horizontalPadding);
    }
    protected String fileHeaderRowText() {
        return String.format(Locale.US, "%-" + FILE_NAME_COL + "s  %-" + FILE_SIZE_COL + "s  %-" + FILE_DATE_COL + "s", this.getString(R.string.name).toUpperCase(Locale.US), this.getString(R.string.size).toUpperCase(Locale.US), this.getString(R.string.date).toUpperCase(Locale.US));
    }
    protected String paddingChars(int count) {
        StringBuilder builder = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; ++i) {
            builder.append('M');
        }
        return builder.toString();
    }
    protected int fileRowIcon(int position) {
        if (position >= 0 && position < this.remoteFileObjects.size()) {
            JSONObject object = this.remoteFileObjects.get(position);
            return object != null && object.optBoolean("is_dir") ? this.androidDrawableId("ic_doc_folder", 17301556) : this.androidDrawableId("ic_doc_generic", 17301582);
        }
        return this.androidDrawableId("ic_doc_generic", 17301582);
    }
    protected void requestCurrentRemoteFileList() {
        WebRtcClient.requestRemoteFileList(this.currentRemotePath.length() == 0 ? "home" : this.currentRemotePath);
    }
    protected void showMountRootPicker(View anchor) {
        FileBrowserMenus.showMountRootMenu((Context)this, anchor, this.remoteMountedRoots, new FileBrowserMenus.MountRootListener(){

            @Override
            public void onSelected(String root) {
                AiranDeskFileActivity.this.currentRemotePath = root;
                AiranDeskFileActivity.this.requestCurrentRemoteFileList();
            }
        });
    }
    protected JSONObject selectedRemoteObject() {
        if (this.selectedRemoteIndex < 0 || this.selectedRemoteIndex >= this.remoteFileObjects.size()) {
            return null;
        }
        return this.remoteFileObjects.get(this.selectedRemoteIndex);
    }
    protected String selectedRemotePath() {
        JSONObject object = this.selectedRemoteObject();
        if (object == null) {
            return "";
        }
        if (RemoteFileEntryUtils.isParentEntry(object)) {
            return "";
        }
        return AiranPathUtils.joinPath(this.currentRemotePath, object.optString("name"));
    }
    protected void openRemoteDirectory(JSONObject object) {
        if (object == null || !object.optBoolean("is_dir")) {
            return;
        }
        this.currentRemotePath = RemoteFileEntryUtils.isParentEntry(object) ? RemoteFileEntryUtils.parentPath(this.currentRemotePath) : AiranPathUtils.joinPath(this.currentRemotePath, object.optString("name"));
        this.requestCurrentRemoteFileList();
    }
    protected void downloadSelectedRemoteItem() {
        JSONObject object = this.selectedRemoteObject();
        if (object == null) {
            Toast.makeText((Context)this, (CharSequence)this.getString(R.string.file_select_file_or_folder), (int)0).show();
            return;
        }
        if (RemoteFileEntryUtils.isParentEntry(object)) {
            Toast.makeText((Context)this, (CharSequence)this.getString(R.string.file_select_file_or_folder), (int)0).show();
            return;
        }
        WebRtcClient.downloadRemoteFile(this.selectedRemotePath());
    }
    protected void runSelectedRemoteItem() {
        JSONObject object = this.selectedRemoteObject();
        if (object == null) {
            Toast.makeText((Context)this, (CharSequence)this.getString(R.string.file_select_executable), (int)0).show();
            return;
        }
        if (RemoteFileEntryUtils.isParentEntry(object)) {
            Toast.makeText((Context)this, (CharSequence)this.getString(R.string.file_select_executable), (int)0).show();
            return;
        }
        if (object.optBoolean("is_dir")) {
            Toast.makeText((Context)this, (CharSequence)this.getString(R.string.file_directory_cannot_run), (int)0).show();
            return;
        }
        if (!RemoteFileEntryUtils.isExecutable(object)) {
            Toast.makeText((Context)this, (CharSequence)this.getString(R.string.file_select_executable), (int)0).show();
            return;
        }
        WebRtcClient.runRemoteFile(this.selectedRemotePath());
    }

}
