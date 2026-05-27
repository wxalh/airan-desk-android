package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.wxalh.airan_desk.R;
import com.wxalh.airan_desk.config.AppConfig;

public final class SettingsScreenView {
    public interface Listener {
        void onScreenPermissionClicked();

        void onInputServiceClicked();

        void onAudioPermissionClicked();

        void onNewPasswordClicked();

        void onBatteryOptimizationClicked();

        void onAppBatterySettingsClicked();

        void onLanguageClicked();

        void onDiagnosticsClicked();
    }

    private final Context context;
    private final UiComponentFactory uiFactory;
    private final Listener listener;
    private EditText wsUrlEdit;
    private EditText iceEdit;
    private EditText iceUserEdit;
    private EditText icePasswordEdit;

    public SettingsScreenView(Context context, UiComponentFactory uiFactory, Listener listener) {
        this.context = context;
        this.uiFactory = uiFactory;
        this.listener = listener;
    }

    public View build(AppConfig config) {
        LinearLayout content = this.page();
        content.addView(this.sectionTitle(this.context.getString(R.string.network), this.context.getString(R.string.status_online)));
        this.wsUrlEdit = this.edit(config.wsUrl(), this.context.getString(R.string.ws_url));
        this.iceEdit = this.edit(config.iceUri(), this.context.getString(R.string.ice_uri));
        this.iceUserEdit = this.edit(config.iceUser(), this.context.getString(R.string.ice_user));
        this.icePasswordEdit = this.edit(config.icePassword(), this.context.getString(R.string.ice_password));
        this.iceUserEdit.setInputType(145);
        this.icePasswordEdit.setInputType(145);
        content.addView(this.labeledInput(this.context.getString(R.string.ws_url), this.wsUrlEdit));
        content.addView(this.labeledInput(this.context.getString(R.string.ice_uri), this.iceEdit));
        content.addView(this.labeledInput(this.context.getString(R.string.ice_user), this.iceUserEdit));
        content.addView(this.labeledInput(this.context.getString(R.string.ice_password), this.icePasswordEdit));

        content.addView(this.sectionTitle(this.context.getString(R.string.permissions), this.context.getString(R.string.this_device)));
        LinearLayout permRow = new LinearLayout(this.context);
        permRow.setOrientation(LinearLayout.VERTICAL);
        permRow.addView(this.outlineButton(R.string.screen_permission, new Runnable(){

            @Override
            public void run() {
                SettingsScreenView.this.listener.onScreenPermissionClicked();
            }
        }), this.uiFactory.fullWidthButton());
        permRow.addView(this.outlineButton(R.string.input_service, new Runnable(){

            @Override
            public void run() {
                SettingsScreenView.this.listener.onInputServiceClicked();
            }
        }), this.uiFactory.fullWidthButton());
        permRow.addView(this.outlineButton(R.string.audio_permission, new Runnable(){

            @Override
            public void run() {
                SettingsScreenView.this.listener.onAudioPermissionClicked();
            }
        }), this.uiFactory.fullWidthButton());
        permRow.addView(this.outlineButton(R.string.new_password, new Runnable(){

            @Override
            public void run() {
                SettingsScreenView.this.listener.onNewPasswordClicked();
            }
        }), this.uiFactory.fullWidthButton());
        content.addView(permRow);

        content.addView(this.sectionTitle(this.context.getString(R.string.keep_alive), this.context.getString(R.string.lock_screen)));
        TextView keepAliveHelp = this.uiFactory.text(this.context.getString(R.string.keep_alive_guide), 13, AppTheme.C_TEXT_MUTED, false, true);
        keepAliveHelp.setPadding(0, 0, 0, this.dp(8));
        content.addView(keepAliveHelp, this.uiFactory.fullWidth());
        LinearLayout keepAliveRow = new LinearLayout(this.context);
        keepAliveRow.setOrientation(LinearLayout.VERTICAL);
        keepAliveRow.addView(this.outlineButton(R.string.ignore_battery_optimization, new Runnable(){

            @Override
            public void run() {
                SettingsScreenView.this.listener.onBatteryOptimizationClicked();
            }
        }), this.uiFactory.fullWidthButton());
        keepAliveRow.addView(this.outlineButton(R.string.open_app_battery_settings, new Runnable(){

            @Override
            public void run() {
                SettingsScreenView.this.listener.onAppBatterySettingsClicked();
            }
        }), this.uiFactory.fullWidthButton());
        content.addView(keepAliveRow);

        Button language = this.uiFactory.outlineButton(this.context.getString(R.string.language) + " / " + this.context.getString(R.string.switch_language));
        language.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                SettingsScreenView.this.listener.onLanguageClicked();
            }
        });
        content.addView(language, this.uiFactory.fullWidthButton());

        Button diagnostics = this.uiFactory.secondaryButton(this.context.getString(R.string.runtime_diagnostics));
        diagnostics.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                SettingsScreenView.this.listener.onDiagnosticsClicked();
            }
        });
        content.addView(diagnostics, this.uiFactory.fullWidthButton());
        return content;
    }

    public String wsUrl() {
        return this.wsUrlEdit == null ? "" : this.wsUrlEdit.getText().toString().trim();
    }

    public String iceUri() {
        return this.iceEdit == null ? "" : this.iceEdit.getText().toString().trim();
    }

    public String iceUser() {
        return this.iceUserEdit == null ? "" : this.iceUserEdit.getText().toString().trim();
    }

    public String icePassword() {
        return this.icePasswordEdit == null ? "" : this.icePasswordEdit.getText().toString();
    }

    private Button outlineButton(int stringRes, final Runnable action) {
        Button button = this.uiFactory.outlineButton(this.context.getString(stringRes));
        button.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                if (action != null) {
                    action.run();
                }
            }
        });
        return button;
    }

    private LinearLayout page() {
        LinearLayout content = new LinearLayout(this.context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(this.dp(14), this.dp(14), this.dp(14), this.dp(24));
        return content;
    }

    private View sectionTitle(String title, String meta) {
        return this.sectionTitle(title, meta, 16, 10);
    }

    private View sectionTitle(String title, String meta, int topDp, int bottomDp) {
        LinearLayout row = new LinearLayout(this.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(0, this.dp(topDp), 0, this.dp(bottomDp));
        row.addView(this.uiFactory.text(title, 20, AppTheme.C_TEXT, true, false), new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (meta != null && meta.length() > 0) {
            row.addView(this.uiFactory.text(meta, 11, AppTheme.C_TEXT_MUTED, false, true));
        }
        return row;
    }

    private View labeledInput(String label, EditText input) {
        LinearLayout box = new LinearLayout(this.context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, this.dp(4), 0, this.dp(4));
        box.addView(this.uiFactory.text(label, 12, AppTheme.C_TEXT_MUTED, false, true), this.uiFactory.fullWidth());
        box.addView(input, this.uiFactory.fullWidthInput());
        return box;
    }

    private EditText edit(String text, String hint) {
        EditText edit = new EditText(this.context);
        edit.setText((CharSequence)(text == null ? "" : text));
        edit.setHint((CharSequence)hint);
        edit.setSingleLine(true);
        edit.setTextColor(AppTheme.C_TEXT);
        edit.setHintTextColor(Color.argb(90, 190, 200, 203));
        edit.setTextSize(14.0f);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setPadding(this.dp(12), 0, this.dp(12), 0);
        edit.setBackground((Drawable)this.uiFactory.border(AppTheme.C_CONTAINER, AppTheme.C_OUTLINE, this.dp(8), 1));
        edit.setOnFocusChangeListener(new View.OnFocusChangeListener(){

            public void onFocusChange(final View v, boolean hasFocus) {
                if (!hasFocus) {
                    return;
                }
                v.postDelayed(new Runnable(){

                    @Override
                    public void run() {
                        Rect rect = new Rect(0, 0, v.getWidth(), v.getHeight() + SettingsScreenView.this.dp(120));
                        v.requestRectangleOnScreen(rect, false);
                    }
                }, 180L);
            }
        });
        return edit;
    }

    private int dp(int value) {
        return (int)((float)value * this.context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
