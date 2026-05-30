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
abstract class AiranDeskDesktopActivity
extends AiranDeskDesktopInteractionActivity {
    protected void showDesktop() {
        this.syncNetworkConfigFromEditors();
        this.currentTab = "desktop";
        this.updateNav();
        this.detachRenderer();
        LinearLayout content = this.page();
        content.setPadding(0, 0, 0, 0);
        this.desktopKeyboardInput = this.createDesktopKeyboardInput();
        content.addView((View)this.desktopKeyboardInput, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(1), this.dp(1)));
        this.renderer = new TextureVideoRenderer((Context)this);
        this.renderer.init(WebRtcClient.eglContext());
        this.renderer.setMirror(false);
        this.applyRemoteVisibleRectToRenderer();
        this.renderer.setFrameSizeListener(new TextureVideoRenderer.FrameSizeListener(){

            @Override
            public void onFrameSizeChanged(int width, int height) {
                AiranDeskDesktopActivity.this.applyRemoteVisibleRectToRenderer();
                AiranDeskDesktopActivity.this.scheduleDesktopVideoLayout(true);
            }
        });
        WebRtcClient.setRenderer(this.renderer);
        this.controlWindowCard = new FrameLayout((Context)this);
        this.controlWindowCard.setBackgroundColor(C_LOWEST);
        this.controlWindowCard.setClipChildren(true);
        this.controlWindowCard.setOnTouchListener(new View.OnTouchListener(){

            public boolean onTouch(View v, MotionEvent event) {
                return AiranDeskDesktopActivity.this.handleDesktopTouch(v, event);
            }
        });
        this.controlWindowCard.addView((View)this.renderer, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        this.desktopPointerView = this.createDesktopPointerView();
        this.controlWindowCard.addView((View)this.desktopPointerView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.dp(34), this.dp(34)));
        FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams(-1, -2, 80);
        drawerLp.leftMargin = this.dp(8);
        drawerLp.rightMargin = this.dp(8);
        drawerLp.bottomMargin = this.dp(8);
        this.controlWindowCard.addView(this.desktopActionDrawer(), (ViewGroup.LayoutParams)drawerLp);
        FrameLayout.LayoutParams mouseLp = new FrameLayout.LayoutParams(this.dp(132), this.dp(136), 51);
        this.controlWindowCard.addView(this.desktopMouseOverlay(), (ViewGroup.LayoutParams)mouseLp);
        this.controlWindowCard.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){

            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                AiranDeskDesktopActivity.this.scheduleDesktopVideoLayout(false);
            }
        });
        this.controlWindowCard.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){

            public void onGlobalLayout() {
                AiranDeskDesktopActivity.this.adjustDesktopForKeyboard();
            }
        });
        content.addView((View)this.controlWindowCard, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.setDirectContent((View)content);
        this.desktopZoom = 1.0f;
        this.desktopPanX = 0.0f;
        this.desktopPanY = 0.0f;
        this.desktopBaseVideoWidth = 0;
        this.desktopBaseVideoHeight = 0;
        this.desktopGestureMode = 0;
        this.desktopPinchZoomEnabled = false;
        this.desktopTouchpadBlockedByPinch = false;
        this.desktopLastTapMs = 0L;
        this.desktopHasLastPointer = true;
        this.desktopLastPointerX = 0.5f;
        this.desktopLastPointerY = 0.5f;
        this.updateDesktopPointerView();
        this.desktopMouseExpanded = false;
        this.desktopDrawerExpanded = false;
        this.controlWindowCard.post(new Runnable(){

            @Override
            public void run() {
                AiranDeskDesktopActivity.this.scheduleDesktopVideoLayout(true);
                AiranDeskDesktopActivity.this.resetDesktopMouseOverlayPosition();
            }
        });
        this.updateControlWindowVisibility();
    }
    protected View desktopActionDrawer() {
        this.desktopActionDrawer = new LinearLayout((Context)this);
        this.desktopActionDrawer.setOrientation(1);
        this.desktopActionDrawer.setPadding(this.dp(6), this.dp(4), this.dp(6), this.dp(6));
        this.desktopActionDrawer.setBackground((Drawable)this.uiFactory.border(Color.argb((int)224, (int)0, (int)0, (int)0), Color.argb((int)80, (int)255, (int)255, (int)255), this.dp(8), 1));
        this.updateDesktopActionDrawer();
        return this.desktopActionDrawer;
    }
    protected View desktopDrawerButton() {
        this.desktopDrawerToggleButton = this.uiFactory.floatingIconButton(this.androidDrawableId("ic_menu_more", 17301570));
        this.desktopDrawerToggleButton.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.toggleDesktopActionDrawer();
            }
        });
        this.desktopDrawerToggleButton.setContentDescription((CharSequence)this.getString(R.string.desktop_remote_control_menu));
        this.updateDesktopActionDrawer();
        return this.desktopDrawerToggleButton;
    }
    protected void toggleDesktopActionDrawer() {
        this.desktopDrawerExpanded = !this.desktopDrawerExpanded;
        this.updateDesktopActionDrawer();
    }
    protected void updateDesktopActionDrawer() {
        if (this.desktopActionDrawer != null) {
            this.desktopActionDrawer.setVisibility(0);
            this.desktopActionDrawer.removeAllViews();
            this.desktopCtrlButton = null;
            this.desktopShiftButton = null;
            this.desktopAltButton = null;
            this.desktopWinButton = null;
            if (this.desktopDrawerExpanded) {
                this.desktopActionDrawer.addView(this.desktopExpandedActionHeader(), (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
                this.desktopActionDrawer.addView(this.desktopQuickActionToolbarClean(), (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
                this.desktopActionDrawer.addView(this.desktopControlToolbar(), (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
                this.updateDesktopModifierButtons();
            } else {
                this.desktopActionDrawer.addView(this.desktopCollapsedActionBar(), (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
            }
            this.desktopActionDrawer.post(new Runnable(){

                @Override
                public void run() {
                    AiranDeskDesktopActivity.this.scheduleDesktopVideoLayout(false);
                    AiranDeskDesktopActivity.this.updateDesktopPointerView();
                    if (AiranDeskDesktopActivity.this.desktopMouseOverlayView != null) {
                        AiranDeskDesktopActivity.this.moveDesktopMouseOverlayTo(AiranDeskDesktopActivity.this.desktopMouseOverlayView.getX(), AiranDeskDesktopActivity.this.desktopMouseOverlayView.getY());
                    }
                }
            });
        }
        if (this.desktopDrawerToggleButton != null) {
            this.desktopDrawerToggleButton.setAlpha(this.desktopDrawerExpanded ? 1.0f : 0.78f);
        }
    }
    protected View desktopExpandedActionHeader() {
        LinearLayout row = this.toolbarRow();
        this.desktopAudioButton = this.toolbarChip("", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.showDesktopAudioMenu(v);
            }
        });
        row.addView((View)this.desktopAudioButton, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(36), 1.0f));
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(this.dp(42), this.dp(36));
        arrowLp.leftMargin = this.dp(4);
        row.addView((View)this.toolbarChip("\u2193", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.toggleDesktopActionDrawer();
            }
        }), (ViewGroup.LayoutParams)arrowLp);
        return row;
    }
    protected View desktopCollapsedActionBar() {
        LinearLayout row = this.toolbarRow();
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_shortcuts), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.showDesktopShortcutMenu(v);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(false));
        this.desktopAudioButton = this.toolbarChip("", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.showDesktopAudioMenu(v);
            }
        });
        row.addView((View)this.desktopAudioButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        this.desktopZoomToggleButton = this.toolbarChip(this.getString(R.string.desktop_zoom), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.toggleDesktopPinchZoomMode();
            }
        });
        row.addView((View)this.desktopZoomToggleButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_input), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.focusDesktopKeyboard();
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_switch_screen), new View.OnClickListener(){

            public void onClick(View v) {
                if (WebRtcClient.sendSwitchScreen()) {
                    AiranDeskDesktopActivity.this.showUserStatus("switch screen");
                }
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip("\u2191", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.toggleDesktopActionDrawer();
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        this.updateDesktopToolbarLabels();
        return row;
    }
    protected void toggleDesktopPinchZoomMode() {
        this.desktopPinchZoomEnabled = !this.desktopPinchZoomEnabled;
        this.updateDesktopZoomToggleButton();
    }
    protected void updateDesktopZoomToggleButton() {
        if (this.desktopZoomToggleButton == null) {
            return;
        }
        this.desktopZoomToggleButton.setTextColor(this.desktopPinchZoomEnabled ? C_ON_PRIMARY : C_TEXT);
        this.desktopZoomToggleButton.setBackground((Drawable)this.uiFactory.border(this.desktopPinchZoomEnabled ? C_PRIMARY : C_CONTAINER, this.desktopPinchZoomEnabled ? C_PRIMARY : C_OUTLINE, this.dp(6), 1));
    }
    protected View desktopControlToolbar() {
        LinearLayout root = this.toolbarRows();
        LinearLayout row = this.toolbarRow();
        this.desktopResolutionButton = this.toolbarChip("", new View.OnClickListener(){

            public void onClick(View v) {
                DesktopSettingsMenus.showResolutionMenu((Context)AiranDeskDesktopActivity.this, v, WebRtcClient.streamWidth(), WebRtcClient.streamHeight(), new DesktopSettingsMenus.ResolutionSelection(){

                    @Override
                    public void onSelected(int width, int height) {
                        WebRtcClient.setResolution(width, height);
                        AiranDeskDesktopActivity.this.updateDesktopToolbarLabels();
                        AiranDeskDesktopActivity.this.showUserStatus("resolution: " + AiranDeskDesktopActivity.this.uiTextFormatter.resolutionLabel(WebRtcClient.streamWidth(), WebRtcClient.streamHeight()));
                    }
                });
            }
        });
        row.addView((View)this.desktopResolutionButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        this.desktopDisplayButton = this.toolbarChip("", new View.OnClickListener(){

            public void onClick(View v) {
                DesktopSettingsMenus.showValueMenu((Context)AiranDeskDesktopActivity.this, v, new String[]{"1:1", AiranDeskDesktopActivity.this.getString(R.string.display_fit_window)}, new String[]{AiranDeskDesktopActivity.DISPLAY_MODE_ACTUAL, AiranDeskDesktopActivity.DISPLAY_MODE_FIT}, AiranDeskDesktopActivity.this.desktopDisplayMode, new DesktopSettingsMenus.ValueSelection(){

                    @Override
                    public void onSelected(String value) {
                        AiranDeskDesktopActivity.this.desktopDisplayMode = value;
                        AiranDeskDesktopActivity.this.updateDesktopToolbarLabels();
                        AiranDeskDesktopActivity.this.scheduleDesktopVideoLayout(true);
                        AiranDeskDesktopActivity.this.showUserStatus("display mode: " + value);
                    }
                });
            }
        });
        row.addView((View)this.desktopDisplayButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        root.addView((View)row, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        row = this.toolbarRow();
        this.desktopNetworkButton = this.toolbarChip("", new View.OnClickListener(){

            public void onClick(View v) {
                DesktopSettingsMenus.showValueMenu((Context)AiranDeskDesktopActivity.this, v, new String[]{AiranDeskDesktopActivity.this.getString(R.string.network_auto), AiranDeskDesktopActivity.this.getString(R.string.network_direct), AiranDeskDesktopActivity.this.getString(R.string.network_turn_udp), AiranDeskDesktopActivity.this.getString(R.string.network_turn_tcp)}, new String[]{"auto", "direct", "turn_udp", "turn_tcp"}, WebRtcClient.networkPath(), new DesktopSettingsMenus.ValueSelection(){

                    @Override
                    public void onSelected(String value) {
                        WebRtcClient.setNetworkPath(value);
                        AiranDeskDesktopActivity.this.updateDesktopToolbarLabels();
                        AiranDeskDesktopActivity.this.showUserStatus("network path: " + value);
                        if (WebRtcClient.isControlRole()) {
                            WebRtcClient.restartActiveControlSession();
                        }
                    }
                });
            }
        });
        row.addView((View)this.desktopNetworkButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        root.addView((View)row, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        this.updateDesktopToolbarLabels();
        return root;
    }
    protected Button modifierChip(String label, final int winKey) {
        Button button = this.toolbarChip(label, new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.toggleDesktopModifier(winKey);
            }
        });
        this.styleModifierChip(button, false);
        return button;
    }
    protected View desktopQuickActionToolbarClean() {
        LinearLayout root = this.toolbarRows();
        LinearLayout row = this.toolbarRow();
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_input), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.focusDesktopKeyboard();
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(false));
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_mouse), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.toggleDesktopMousePanel();
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_switch_screen), new View.OnClickListener(){

            public void onClick(View v) {
                if (WebRtcClient.sendSwitchScreen()) {
                    AiranDeskDesktopActivity.this.showUserStatus("switch screen");
                }
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip(this.getString(R.string.files), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.showFiles();
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        root.addView((View)row, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        row = this.toolbarRow();
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_shortcuts), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.showDesktopShortcutMenu(v);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(false));
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_function_keys), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.showDesktopFunctionKeyMenu(v);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_remote_operations), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.showRemoteOperationMenu(v);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip(this.getString(R.string.desktop_release), new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.releaseDesktopModifierKeys();
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        root.addView((View)row, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        row = this.toolbarRow();
        this.desktopCtrlButton = this.modifierChip("Ctrl", 17);
        row.addView((View)this.desktopCtrlButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(false));
        this.desktopShiftButton = this.modifierChip("Shift", 16);
        row.addView((View)this.desktopShiftButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        this.desktopAltButton = this.modifierChip("Alt", 18);
        row.addView((View)this.desktopAltButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        this.desktopWinButton = this.modifierChip("Win", 91);
        row.addView((View)this.desktopWinButton, (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip("Esc", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(27);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(false));
        root.addView((View)row, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        row = this.toolbarRow();
        row.addView((View)this.toolbarChip("Tab", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(9);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(false));
        row.addView((View)this.toolbarChip("Enter", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(13);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip("Del", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(46);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip("<", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(37);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip("^", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(38);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip("v", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(40);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        row.addView((View)this.toolbarChip(">", new View.OnClickListener(){

            public void onClick(View v) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(39);
            }
        }), (ViewGroup.LayoutParams)this.toolbarChipLayout(true));
        root.addView((View)row, (ViewGroup.LayoutParams)this.uiFactory.fullWidth());
        return root;
    }
    protected void showDesktopShortcutMenu(View anchor) {
        DesktopCommandMenus.showShortcutMenu((Context)this, anchor, this.desktopCommandListener());
    }
    protected void showDesktopFunctionKeyMenu(View anchor) {
        DesktopCommandMenus.showFunctionKeyMenu((Context)this, anchor, this.desktopCommandListener());
    }
    protected void showRemoteOperationMenu(View anchor) {
        DesktopCommandMenus.showRemoteOperationMenu((Context)this, anchor, this.desktopCommandListener());
    }
    protected void showDesktopAudioMenu(View anchor) {
        DesktopSettingsMenus.showAudioModeMenu((Context)this, anchor, WebRtcClient.audioMode(), new DesktopSettingsMenus.AudioModeSelection(){

            @Override
            public void onSelected(String mode) {
                AiranDeskDesktopActivity.this.applyDesktopAudioMode(mode);
            }
        });
    }
    protected DesktopCommandMenus.Listener desktopCommandListener() {
        return new DesktopCommandMenus.Listener(){

            @Override
            public void onKeyTap(int winKey) {
                AiranDeskDesktopActivity.this.sendDesktopKeyTap(winKey);
            }

            @Override
            public void onShortcut(int[] winKeys) {
                AiranDeskDesktopActivity.this.sendDesktopShortcut(winKeys);
            }

            @Override
            public void onRemoteOperation(String action) {
                WebRtcClient.sendRemoteOperation(action);
            }
        };
    }
    protected TextView createDesktopPointerView() {
        TextView pointer = this.uiFactory.text("\u2196", 30, -1, false, false);
        pointer.setGravity(51);
        pointer.setShadowLayer(3.0f, 1.0f, 1.0f, -16777216);
        pointer.setClickable(false);
        pointer.setFocusable(false);
        return pointer;
    }
    protected View desktopMouseOverlay() {
        FrameLayout host = new FrameLayout((Context)this);
        this.desktopMouseOverlayView = host;
        host.setPadding(0, 0, 0, 0);
        this.desktopMousePanel = new FrameLayout((Context)this);
        this.desktopMousePanel.setVisibility(8);
        this.desktopMouseFloatingPointer = new DesktopCursorIconView((Context)this, false);
        FrameLayout.LayoutParams pointerLp = new FrameLayout.LayoutParams(this.dp(24), this.dp(24));
        pointerLp.leftMargin = 0;
        pointerLp.topMargin = 0;
        this.desktopMousePanel.addView(this.desktopMouseFloatingPointer, (ViewGroup.LayoutParams)pointerLp);
        Button left = this.mouseActionButton("L", 1);
        left.setContentDescription((CharSequence)this.getString(R.string.mouse_left_button));
        Button middle = this.mouseActionButton("M", 3);
        middle.setContentDescription((CharSequence)this.getString(R.string.mouse_middle_button));
        Button right = this.mouseActionButton("R", 2);
        right.setContentDescription((CharSequence)this.getString(R.string.mouse_right_button));
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(this.dp(38), this.dp(38));
        leftLp.leftMargin = this.dp(8);
        leftLp.topMargin = this.dp(54);
        this.desktopMousePanel.addView((View)left, (ViewGroup.LayoutParams)leftLp);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(this.dp(38), this.dp(38));
        rightLp.leftMargin = this.dp(86);
        rightLp.topMargin = this.dp(54);
        this.desktopMousePanel.addView((View)right, (ViewGroup.LayoutParams)rightLp);
        FrameLayout.LayoutParams middleLp = new FrameLayout.LayoutParams(this.dp(38), this.dp(38));
        middleLp.leftMargin = this.dp(47);
        middleLp.topMargin = this.dp(98);
        this.desktopMousePanel.addView((View)middle, (ViewGroup.LayoutParams)middleLp);
        host.addView((View)this.desktopMousePanel, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.dp(132), this.dp(136)));
        this.desktopMouseToggleButton = new DesktopCursorIconView((Context)this, true);
        this.desktopMouseToggleButton.setRotation(45.0f);
        this.desktopMouseToggleButton.setPadding(0, 0, 0, 0);
        this.desktopMouseToggleButton.setContentDescription((CharSequence)this.getString(R.string.desktop_mouse));
        this.desktopMouseToggleButton.setOnTouchListener(new View.OnTouchListener(){

            public boolean onTouch(View v, MotionEvent event) {
                return AiranDeskDesktopActivity.this.handleDesktopMouseControllerTouch(event);
            }
        });
        FrameLayout.LayoutParams toggleLp = new FrameLayout.LayoutParams(this.dp(48), this.dp(48));
        toggleLp.leftMargin = this.dp(42);
        toggleLp.topMargin = this.dp(49);
        host.addView(this.desktopMouseToggleButton, (ViewGroup.LayoutParams)toggleLp);
        this.updateDesktopMousePanel();
        return host;
    }
    protected Button mouseActionButton(String label, final int button) {
        Button key = this.uiFactory.baseButton(label);
        key.setTextColor(-1);
        key.setTextSize(12.0f);
        key.setPadding(0, 0, 0, 0);
        key.setBackground((Drawable)this.uiFactory.oval(Color.argb((int)204, (int)190, (int)190, (int)190), Color.argb((int)180, (int)0, (int)0, (int)0), 1));
        key.setOnTouchListener(new View.OnTouchListener(){

            public boolean onTouch(View v, MotionEvent event) {
                if (event != null) {
                    int action = event.getActionMasked();
                    if (action == 0) {
                        v.setPressed(true);
                    } else if (action == 1 || action == 3) {
                        v.setPressed(false);
                    }
                }
                if (button == 3) {
                    return AiranDeskDesktopActivity.this.handleDesktopMouseMiddleButtonTouch(event);
                }
                return AiranDeskDesktopActivity.this.handleDesktopMouseButtonDragTouch(event, button);
            }
        });
        return key;
    }
    protected boolean handleDesktopMouseButtonDragTouch(MotionEvent event, int button) {
        if (event == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == 0) {
            this.desktopMouseButtonDragButton = button;
            this.desktopMouseDragging = false;
            this.desktopMouseDragDownX = event.getRawX();
            this.desktopMouseDragDownY = event.getRawY();
            this.desktopMouseOverlayStartX = this.desktopMouseOverlayView == null ? 0.0f : this.desktopMouseOverlayView.getX();
            this.desktopMouseOverlayStartY = this.desktopMouseOverlayView == null ? 0.0f : this.desktopMouseOverlayView.getY();
            this.syncDesktopPointerFromMouseOverlay();
            WebRtcClient.sendPointer(this.desktopLastPointerX, this.desktopLastPointerY, true, false, button);
            return true;
        }
        if (action == 2) {
            if (this.desktopMouseButtonDragButton != button) {
                return true;
            }
            float dx = event.getRawX() - this.desktopMouseDragDownX;
            float dy = event.getRawY() - this.desktopMouseDragDownY;
            int slop = ViewConfiguration.get((Context)this).getScaledTouchSlop();
            if (!this.desktopMouseDragging && dx * dx + dy * dy > (float)(slop * slop)) {
                this.desktopMouseDragging = true;
            }
            if (this.desktopMouseDragging) {
                this.moveDesktopMouseOverlayTo(this.desktopMouseOverlayStartX + dx, this.desktopMouseOverlayStartY + dy);
                this.sendDesktopMouseMoveWithButton(button, false);
            }
            return true;
        }
        if (action == 1 || action == 3) {
            if (this.desktopMouseButtonDragButton == button) {
                if (action == 1 && this.desktopMouseDragging) {
                    this.moveDesktopMouseOverlayTo(this.desktopMouseOverlayStartX + (event.getRawX() - this.desktopMouseDragDownX), this.desktopMouseOverlayStartY + (event.getRawY() - this.desktopMouseDragDownY));
                    this.sendDesktopMouseMoveWithButton(button, true);
                } else {
                    this.syncDesktopPointerFromMouseOverlay();
                }
                WebRtcClient.sendPointer(this.desktopLastPointerX, this.desktopLastPointerY, false, true, button);
            }
            this.desktopMouseButtonDragButton = 0;
            this.desktopMouseDragging = false;
            return true;
        }
        return true;
    }
    protected boolean handleDesktopMouseMiddleButtonTouch(MotionEvent event) {
        if (event == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == 0) {
            this.desktopMouseWheelDragging = false;
            this.desktopMouseDragDownX = event.getRawX();
            this.desktopMouseDragDownY = event.getRawY();
            this.desktopMouseWheelLastRawY = event.getRawY();
            this.desktopWheelRemainderY = 0.0f;
            return true;
        }
        if (action == 2) {
            float totalDx = event.getRawX() - this.desktopMouseDragDownX;
            float totalDy = event.getRawY() - this.desktopMouseDragDownY;
            int slop = ViewConfiguration.get((Context)this).getScaledTouchSlop();
            if (!this.desktopMouseWheelDragging && totalDx * totalDx + totalDy * totalDy > (float)(slop * slop)) {
                this.desktopMouseWheelDragging = true;
                this.syncDesktopPointerFromMouseOverlay();
            }
            if (this.desktopMouseWheelDragging) {
                this.sendDesktopMouseWheelFromDrag(event.getRawY() - this.desktopMouseWheelLastRawY);
                this.desktopMouseWheelLastRawY = event.getRawY();
            }
            return true;
        }
        if (action == 1) {
            if (!this.desktopMouseWheelDragging) {
                this.syncDesktopPointerFromMouseOverlay();
                this.sendDesktopMouseClick(3);
            }
            this.desktopMouseWheelDragging = false;
            this.desktopWheelRemainderY = 0.0f;
            return true;
        }
        if (action == 3) {
            this.desktopMouseWheelDragging = false;
            this.desktopWheelRemainderY = 0.0f;
            return true;
        }
        return true;
    }
    protected void toggleDesktopMousePanel() {
        this.desktopMouseExpanded = !this.desktopMouseExpanded;
        this.updateDesktopMousePanel();
    }
    protected boolean handleDesktopMouseControllerTouch(MotionEvent event) {
        if (event == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == 0) {
            this.desktopMouseDragging = false;
            this.desktopMouseDragDownX = event.getRawX();
            this.desktopMouseDragDownY = event.getRawY();
            this.desktopMouseOverlayStartX = this.desktopMouseOverlayView == null ? 0.0f : this.desktopMouseOverlayView.getX();
            this.desktopMouseOverlayStartY = this.desktopMouseOverlayView == null ? 0.0f : this.desktopMouseOverlayView.getY();
            return true;
        }
        if (action == 2) {
            float dx = event.getRawX() - this.desktopMouseDragDownX;
            float dy = event.getRawY() - this.desktopMouseDragDownY;
            int slop = ViewConfiguration.get((Context)this).getScaledTouchSlop();
            if (!this.desktopMouseDragging && dx * dx + dy * dy > (float)(slop * slop)) {
                this.desktopMouseDragging = true;
            }
            if (this.desktopMouseDragging) {
                this.moveDesktopMouseOverlayTo(this.desktopMouseOverlayStartX + dx, this.desktopMouseOverlayStartY + dy);
                this.syncDesktopPointerFromMouseOverlay();
            }
            return true;
        }
        if (action == 1) {
            if (!this.desktopMouseDragging) {
                this.toggleDesktopMousePanel();
            }
            this.desktopMouseDragging = false;
            return true;
        }
        if (action == 3) {
            this.desktopMouseDragging = false;
            return true;
        }
        return true;
    }
    protected void updateDesktopMousePanel() {
        if (this.desktopMousePanel != null) {
            this.desktopMousePanel.setVisibility(this.desktopMouseExpanded ? 0 : 8);
        }
        if (this.desktopMouseToggleButton != null) {
            this.desktopMouseToggleButton.setAlpha(this.desktopMouseExpanded ? 1.0f : 0.82f);
            this.desktopMouseToggleButton.setRotation(this.desktopMouseExpanded ? 0.0f : 45.0f);
            if (this.desktopMouseToggleButton instanceof DesktopCursorIconView) {
                ((DesktopCursorIconView)this.desktopMouseToggleButton).setExpandedStyle(this.desktopMouseExpanded);
            }
        }
        if (this.desktopMouseExpanded) {
            if (this.desktopMouseOverlayView != null) {
                this.moveDesktopMouseOverlayTo(this.desktopMouseOverlayView.getX(), this.desktopMouseOverlayView.getY());
            }
            this.syncDesktopPointerFromMouseOverlay();
        } else {
            if (this.desktopMouseOverlayView != null) {
                this.moveDesktopMouseOverlayTo(this.desktopMouseOverlayView.getX(), this.desktopMouseOverlayView.getY());
            }
            this.updateDesktopPointerView();
        }
    }
    protected void updateDesktopToolbarLabels() {
        if (this.desktopResolutionButton != null) {
            this.desktopResolutionButton.setText((CharSequence)(this.getString(R.string.desktop_resolution) + "\n" + this.uiTextFormatter.resolutionLabel(WebRtcClient.streamWidth(), WebRtcClient.streamHeight())));
        }
        if (this.desktopNetworkButton != null) {
            this.desktopNetworkButton.setText((CharSequence)(this.getString(R.string.desktop_network) + "\n" + this.uiTextFormatter.networkPathLabel(WebRtcClient.networkPath())));
        }
        if (this.desktopDisplayButton != null) {
            this.desktopDisplayButton.setText((CharSequence)(this.getString(R.string.desktop_display) + "\n" + this.uiTextFormatter.displayModeLabel(this.desktopDisplayMode, DISPLAY_MODE_ACTUAL)));
        }
        if (this.desktopAudioButton != null) {
            this.desktopAudioButton.setText((CharSequence)this.getString(R.string.desktop_audio));
            this.desktopAudioButton.setContentDescription((CharSequence)this.getString(R.string.desktop_audio));
        }
        this.updateDesktopZoomToggleButton();
    }
    protected void applyDesktopAudioMode(String mode) {
        String normalized = com.wxalh.airan_desk.rtc.AudioModePolicy.normalize(mode);
        if (com.wxalh.airan_desk.rtc.AudioModePolicy.CALL.equals(normalized) && !AudioPermissionHelper.hasRecordAudio((Context)this)) {
            this.pendingAudioMode = normalized;
            AudioPermissionHelper.requestRecordAudio(this, REQUEST_CODE_RECORD_AUDIO_PERMISSION);
            return;
        }
        WebRtcClient.setAudioMode(normalized);
        this.updateDesktopToolbarLabels();
        this.showUserStatus("audio mode: " + normalized);
    }
    protected Button toolbarChip(String text, View.OnClickListener listener) {
        Button button = this.uiFactory.baseButton(text);
        button.setTextColor(C_TEXT);
        button.setTextSize(9.0f);
        button.setMinHeight(this.dp(28));
        button.setPadding(this.dp(4), 0, this.dp(4), 0);
        button.setGravity(17);
        button.setBackground((Drawable)this.uiFactory.border(C_CONTAINER, C_OUTLINE, this.dp(6), 1));
        button.setOnClickListener(listener);
        return button;
    }
    protected LinearLayout toolbarRows() {
        LinearLayout root = new LinearLayout((Context)this);
        root.setOrientation(1);
        root.setPadding(0, 0, 0, this.dp(1));
        return root;
    }
    protected LinearLayout toolbarRow() {
        LinearLayout row = new LinearLayout((Context)this);
        row.setOrientation(0);
        row.setPadding(0, 0, 0, this.dp(3));
        return row;
    }
    protected LinearLayout.LayoutParams toolbarChipLayout(boolean leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, this.dp(32), 1.0f);
        if (leftMargin) {
            lp.leftMargin = this.dp(4);
        }
        return lp;
    }
    protected boolean isLandscape() {
        return this.getResources().getConfiguration().orientation == 2;
    }

}
