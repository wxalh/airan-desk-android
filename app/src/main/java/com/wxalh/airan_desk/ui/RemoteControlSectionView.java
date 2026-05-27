package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import com.wxalh.airan_desk.R;
import com.wxalh.airan_desk.model.RemoteCredentials;
import com.wxalh.airan_desk.util.RemoteCredentialParser;
import java.util.Locale;

public final class RemoteControlSectionView {
    public interface Listener {
        void onModeChanged(String mode);

        void onConnectRequested();

        boolean isLocalCredentialId(String id);

        void onCredentialsFilledFromPaste();
    }

    private final Context context;
    private final UiComponentFactory uiFactory;
    private final Listener listener;
    private Button desktopModeButton;
    private Button filesModeButton;
    private Button terminalModeButton;
    private EditText remoteIdEdit;
    private EditText remotePwdEdit;
    private String selectedMode;
    private boolean applyingRemoteCredentialsFromPaste = false;
    private boolean sanitizingRemoteCredentialInput = false;

    public RemoteControlSectionView(Context context, UiComponentFactory uiFactory, Listener listener) {
        this.context = context;
        this.uiFactory = uiFactory;
        this.listener = listener;
    }

    public View build(String selectedMode, String remoteIdDraft, String remotePasswordDraft) {
        this.selectedMode = selectedMode == null || selectedMode.length() == 0 ? "desktop" : selectedMode;
        LinearLayout section = new LinearLayout(this.context);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(this.sectionTitle(this.context.getString(R.string.connect_session), "", 8, 6));

        LinearLayout modeRow = new LinearLayout(this.context);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        this.desktopModeButton = this.modeButton(this.context.getString(R.string.desktop), "desktop");
        this.filesModeButton = this.modeButton(this.context.getString(R.string.files), "file");
        this.terminalModeButton = this.modeButton(this.context.getString(R.string.shell), "terminal");
        modeRow.addView(this.desktopModeButton, this.uiFactory.weight());
        modeRow.addView(this.filesModeButton, this.uiFactory.weightWithLeftMargin());
        modeRow.addView(this.terminalModeButton, this.uiFactory.weightWithLeftMargin());
        section.addView(modeRow);
        this.updateModeButtons();

        this.remoteIdEdit = this.edit(remoteIdDraft, this.context.getString(R.string.remote_id));
        this.remotePwdEdit = this.edit(remotePasswordDraft, this.context.getString(R.string.remote_code));
        this.remotePwdEdit.setInputType(1);
        this.installRemoteCredentialSpaceCleaner(this.remoteIdEdit);
        this.installRemoteCredentialSpaceCleaner(this.remotePwdEdit);
        this.installRemoteCredentialPasteParser(this.remoteIdEdit);
        this.installRemoteCredentialPasteParser(this.remotePwdEdit);
        section.addView(this.labeledInput(this.context.getString(R.string.remote_id), this.remoteIdEdit));
        section.addView(this.labeledInput(this.context.getString(R.string.remote_code), this.remotePwdEdit));

        LinearLayout actions = new LinearLayout(this.context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button connect = this.uiFactory.primaryButton(this.context.getString(R.string.connect_session));
        connect.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                if (RemoteControlSectionView.this.listener != null) {
                    RemoteControlSectionView.this.listener.onConnectRequested();
                }
            }
        });
        actions.addView(connect, this.uiFactory.weight());
        section.addView(actions);
        return section;
    }

    public String selectedMode() {
        return this.selectedMode == null || this.selectedMode.length() == 0 ? "desktop" : this.selectedMode;
    }

    public void setSelectedMode(String mode) {
        this.selectedMode = mode == null || mode.length() == 0 ? "desktop" : mode;
        this.updateModeButtons();
    }

    public String remoteId() {
        return this.remoteIdEdit == null ? "" : this.sanitizeRemoteCredentialInput(this.remoteIdEdit.getText().toString());
    }

    public String password() {
        return this.remotePwdEdit == null ? "" : this.sanitizeRemoteCredentialInput(this.remotePwdEdit.getText().toString());
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
                        Rect rect = new Rect(0, 0, v.getWidth(), v.getHeight() + RemoteControlSectionView.this.dp(120));
                        v.requestRectangleOnScreen(rect, false);
                    }
                }, 180L);
            }
        });
        return edit;
    }

    private Button modeButton(String text, final String mode) {
        Button button = this.uiFactory.ghostButton(text);
        button.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                RemoteControlSectionView.this.selectedMode = mode;
                RemoteControlSectionView.this.updateModeButtons();
                if (RemoteControlSectionView.this.listener != null) {
                    RemoteControlSectionView.this.listener.onModeChanged(mode);
                }
            }
        });
        return button;
    }

    private void updateModeButtons() {
        this.styleModeButton(this.desktopModeButton, "desktop".equals(this.selectedMode()));
        this.styleModeButton(this.filesModeButton, "file".equals(this.selectedMode()));
        this.styleModeButton(this.terminalModeButton, "terminal".equals(this.selectedMode()));
    }

    private void styleModeButton(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setTextColor(selected ? AppTheme.C_PRIMARY : AppTheme.C_TEXT_MUTED);
        button.setBackground((Drawable)this.uiFactory.border(selected ? Color.argb(28, 131, 211, 227) : AppTheme.C_CONTAINER, selected ? AppTheme.C_PRIMARY : AppTheme.C_OUTLINE, this.dp(8), 1));
    }

    private void installRemoteCredentialSpaceCleaner(final EditText edit) {
        if (edit == null) {
            return;
        }
        edit.addTextChangedListener(new TextWatcher(){

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                if (RemoteControlSectionView.this.sanitizingRemoteCredentialInput || RemoteControlSectionView.this.applyingRemoteCredentialsFromPaste || s == null) {
                    return;
                }
                String value = s.toString();
                String cleaned = RemoteControlSectionView.this.sanitizeRemoteCredentialInput(value);
                if (cleaned.equals(value)) {
                    return;
                }
                int selection = Math.min(value.length(), Math.max(0, edit.getSelectionStart()));
                int cleanedSelection = RemoteControlSectionView.this.sanitizeRemoteCredentialInput(value.substring(0, selection)).length();
                RemoteControlSectionView.this.sanitizingRemoteCredentialInput = true;
                try {
                    edit.setText((CharSequence)cleaned);
                    edit.setSelection(Math.min(cleanedSelection, edit.getText().length()));
                }
                finally {
                    RemoteControlSectionView.this.sanitizingRemoteCredentialInput = false;
                }
            }
        });
    }

    private String sanitizeRemoteCredentialInput(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private void installRemoteCredentialPasteParser(EditText edit) {
        if (edit == null) {
            return;
        }
        edit.addTextChangedListener(new TextWatcher(){

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                if (RemoteControlSectionView.this.applyingRemoteCredentialsFromPaste || RemoteControlSectionView.this.sanitizingRemoteCredentialInput) {
                    return;
                }
                RemoteControlSectionView.this.maybeFillRemoteCredentialsFromPastedText(s == null ? "" : s.toString());
            }
        });
    }

    private void maybeFillRemoteCredentialsFromPastedText(String text) {
        if (this.remoteIdEdit == null || this.remotePwdEdit == null) {
            return;
        }
        RemoteCredentials credentials = RemoteCredentialParser.parse(text);
        if (credentials == null || this.isLocalCredentialId(credentials.remoteId)) {
            return;
        }
        this.applyingRemoteCredentialsFromPaste = true;
        try {
            this.remoteIdEdit.setText((CharSequence)credentials.remoteId);
            this.remotePwdEdit.setText((CharSequence)credentials.password);
            this.remoteIdEdit.setSelection(this.remoteIdEdit.getText().length());
            this.remotePwdEdit.setSelection(this.remotePwdEdit.getText().length());
            if (this.listener != null) {
                this.listener.onCredentialsFilledFromPaste();
            }
        }
        finally {
            this.applyingRemoteCredentialsFromPaste = false;
        }
    }

    private boolean isLocalCredentialId(String id) {
        return this.listener != null && this.listener.isLocalCredentialId(id);
    }

    private int dp(int value) {
        return (int)((float)value * this.context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
