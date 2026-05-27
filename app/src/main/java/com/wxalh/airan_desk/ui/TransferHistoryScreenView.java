package com.wxalh.airan_desk.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.wxalh.airan_desk.R;
import com.wxalh.airan_desk.file.TransferHistoryStore;
import com.wxalh.airan_desk.file.TransferPathResolver;
import com.wxalh.airan_desk.model.TransferRecord;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public final class TransferHistoryScreenView {
    public interface Listener {
        void onShowTransferHistory();

        void onClearHistory();

        void onOpenLocalPath(String path);

        boolean isTransferControlRole();
    }

    private final Context context;
    private final UiComponentFactory uiFactory;
    private final UiTextFormatter textFormatter;
    private final SimpleDateFormat timeFormat;
    private final Listener listener;
    private TextView summaryText;
    private LinearLayout listHost;

    public TransferHistoryScreenView(Context context, UiComponentFactory uiFactory, UiTextFormatter textFormatter, SimpleDateFormat timeFormat, Listener listener) {
        this.context = context;
        this.uiFactory = uiFactory;
        this.textFormatter = textFormatter;
        this.timeFormat = timeFormat;
        this.listener = listener;
    }

    public View buildSummary(List<TransferRecord> history) {
        LinearLayout row = new LinearLayout(this.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(0, this.dp(8), 0, this.dp(6));
        row.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                if (TransferHistoryScreenView.this.listener != null) {
                    TransferHistoryScreenView.this.listener.onShowTransferHistory();
                }
            }
        });
        TextView title = this.uiFactory.text(this.context.getString(R.string.transfer_history), 20, AppTheme.C_TEXT, true, false);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1.0f));
        this.summaryText = this.uiFactory.text("", 13, AppTheme.C_PRIMARY, true, true);
        this.summaryText.setGravity(16);
        row.addView(this.summaryText, new LinearLayout.LayoutParams(-2, this.dp(36)));
        TextView arrow = this.uiFactory.text(">", 20, AppTheme.C_TEXT_MUTED, true, true);
        arrow.setGravity(17);
        row.addView(arrow, new LinearLayout.LayoutParams(this.dp(28), this.dp(36)));
        this.updateSummary(history);
        return row;
    }

    public View buildHistoryPage(List<TransferRecord> history) {
        LinearLayout content = this.page();
        content.addView(this.sectionTitle(this.context.getString(R.string.transfer_history), this.context.getString(R.string.transfer_history_count, history.size())));
        Button clear = this.uiFactory.outlineButton(this.context.getString(R.string.clear_history));
        clear.setOnClickListener(new View.OnClickListener(){

            public void onClick(View v) {
                if (TransferHistoryScreenView.this.listener != null) {
                    TransferHistoryScreenView.this.listener.onClearHistory();
                }
            }
        });
        content.addView(clear, this.uiFactory.fullWidthButton());
        this.listHost = new LinearLayout(this.context);
        this.listHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = this.uiFactory.fullWidth();
        listLp.topMargin = this.dp(8);
        content.addView(this.listHost, listLp);
        this.renderList(history);
        return content;
    }

    public void updateSummary(List<TransferRecord> history) {
        if (this.summaryText == null) {
            return;
        }
        this.summaryText.setText((CharSequence)this.context.getString(R.string.transfer_history_count, history == null ? 0 : history.size()));
    }

    public void renderList(List<TransferRecord> history) {
        if (this.listHost == null) {
            return;
        }
        this.listHost.removeAllViews();
        if (history == null || history.isEmpty()) {
            LinearLayout empty = this.card();
            empty.addView(this.uiFactory.text(this.context.getString(R.string.transfer_history_empty), 14, AppTheme.C_TEXT_MUTED, false, false), this.uiFactory.fullWidth());
            this.listHost.addView(empty);
            return;
        }
        for (int i = 0; i < history.size(); ++i) {
            this.listHost.addView(this.transferHistoryRow(history.get(i)));
        }
    }

    private View transferHistoryRow(TransferRecord record) {
        LinearLayout row = this.card();
        TextView title = this.uiFactory.text(this.textFormatter.transferDirectionLabel(record.direction) + ": " + this.textFormatter.transferDisplayName(record), 14, AppTheme.C_TEXT, true, true);
        title.setSingleLine(false);
        title.setMaxLines(3);
        row.addView(title, this.uiFactory.fullWidth());
        String time = this.timeFormat.format(new Date(record.updatedAt));
        TextView detail = this.uiFactory.text(time + "  " + this.textFormatter.transferStateLabel(record.done, record.success) + "  " + this.textFormatter.transferSizeLabel(record), 12, AppTheme.C_TEXT_MUTED, false, true);
        detail.setSingleLine(true);
        row.addView(detail, this.uiFactory.fullWidth());
        String source = record.sourcePath == null ? "" : record.sourcePath;
        String target = record.targetPath == null ? "" : record.targetPath;
        String localPath = this.transferLocalPath(record);
        if (source.length() > 0 && !TransferHistoryStore.looksLikeTransferId(source)) {
            row.addView(this.pathView(R.string.transfer_source_path, source, TransferPathResolver.pathsEqual(source, localPath)), this.uiFactory.fullWidth());
        }
        if (target.length() > 0 && !TransferHistoryStore.looksLikeTransferId(target)) {
            int label = "upload".equals(record.direction) ? R.string.transfer_upload_path : R.string.transfer_receive_path;
            row.addView(this.pathView(label, target, TransferPathResolver.pathsEqual(target, localPath)), this.uiFactory.fullWidth());
        }
        return row;
    }

    private TextView pathView(int label, final String path, boolean local) {
        TextView view = this.uiFactory.text(this.context.getString(label, path), 12, local ? AppTheme.C_SECONDARY : AppTheme.C_TEXT_MUTED, local, true);
        view.setSingleLine(false);
        view.setMaxLines(3);
        if (local) {
            view.setPadding(0, this.dp(4), 0, this.dp(4));
            view.setBackground((Drawable)this.uiFactory.border(Color.argb(24, 255, 177, 192), 0, this.dp(4), 0));
            view.setOnClickListener(new View.OnClickListener(){

                public void onClick(View v) {
                    if (TransferHistoryScreenView.this.listener != null) {
                        TransferHistoryScreenView.this.listener.onOpenLocalPath(path);
                    }
                }
            });
        }
        return view;
    }

    private String transferLocalPath(TransferRecord record) {
        return TransferPathResolver.localPath(record, this.listener != null && this.listener.isTransferControlRole());
    }

    private LinearLayout page() {
        LinearLayout content = new LinearLayout(this.context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(this.dp(14), this.dp(14), this.dp(14), this.dp(24));
        return content;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this.context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(this.dp(14), this.dp(12), this.dp(14), this.dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, this.dp(6), 0, this.dp(6));
        card.setLayoutParams(lp);
        card.setBackground((Drawable)this.uiFactory.border(AppTheme.C_CONTAINER, AppTheme.C_OUTLINE, this.dp(8), 1));
        return card;
    }

    private View sectionTitle(String title, String meta) {
        LinearLayout row = new LinearLayout(this.context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(16);
        row.setPadding(0, this.dp(16), 0, this.dp(10));
        row.addView(this.uiFactory.text(title, 20, AppTheme.C_TEXT, true, false), new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (meta != null && meta.length() > 0) {
            row.addView(this.uiFactory.text(meta, 11, AppTheme.C_TEXT_MUTED, false, true));
        }
        return row;
    }

    private int dp(int value) {
        return (int)((float)value * this.context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
