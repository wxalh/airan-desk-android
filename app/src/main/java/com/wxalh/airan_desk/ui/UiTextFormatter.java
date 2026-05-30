package com.wxalh.airan_desk.ui;

import android.content.Context;
import com.wxalh.airan_desk.R;
import com.wxalh.airan_desk.file.TransferHistoryStore;
import com.wxalh.airan_desk.model.TransferRecord;
import com.wxalh.airan_desk.rtc.AudioModePolicy;
import java.util.Locale;

public final class UiTextFormatter {
    private static final int STATUS_MAX_LENGTH = 160;

    private final Context context;

    public UiTextFormatter(Context context) {
        this.context = context;
    }

    public String bitrateLabel(String value) {
        if ("low".equals(value)) {
            return this.context.getString(R.string.bitrate_low);
        }
        if ("high".equals(value)) {
            return this.context.getString(R.string.bitrate_high);
        }
        return this.context.getString(R.string.bitrate_medium);
    }

    public String resolutionLabel(int width, int height) {
        if (width > 0 && height > 0) {
            return width + "x" + height;
        }
        return this.context.getString(R.string.resolution_original);
    }

    public String audioModeLabel(String value) {
        String normalized = AudioModePolicy.normalize(value);
        if (AudioModePolicy.LISTEN.equals(normalized)) {
            return this.context.getString(R.string.audio_mode_listen);
        }
        if (AudioModePolicy.CALL.equals(normalized)) {
            return this.context.getString(R.string.audio_mode_call);
        }
        return this.context.getString(R.string.audio_mode_off);
    }

    public String networkPathLabel(String value) {
        if ("direct".equals(value)) {
            return this.context.getString(R.string.network_direct);
        }
        if ("turn_udp".equals(value)) {
            return this.context.getString(R.string.network_turn_udp);
        }
        if ("turn_tcp".equals(value)) {
            return this.context.getString(R.string.network_turn_tcp);
        }
        return this.context.getString(R.string.network_auto);
    }

    public String displayModeLabel(String value, String actualValue) {
        if (actualValue != null && actualValue.equals(value)) {
            return "1:1";
        }
        return this.context.getString(R.string.display_fit);
    }

    public String modeLabel(String mode) {
        if ("file".equals(mode)) {
            return this.context.getString(R.string.files);
        }
        if ("terminal".equals(mode)) {
            return this.context.getString(R.string.shell);
        }
        return this.context.getString(R.string.desktop);
    }

    public String transferDisplayName(TransferRecord record) {
        if (record == null) {
            return this.context.getString(R.string.transfer_file_default);
        }
        String name = record.name == null ? "" : record.name;
        if (name.length() == 0 || TransferHistoryStore.looksLikeTransferId(name)) {
            name = TransferHistoryStore.fileNameFromPath(record.targetPath);
        }
        if (name.length() == 0 || TransferHistoryStore.looksLikeTransferId(name)) {
            name = TransferHistoryStore.fileNameFromPath(record.sourcePath);
        }
        return name.length() == 0 || TransferHistoryStore.looksLikeTransferId(name) ? this.context.getString(R.string.transfer_file_default) : name;
    }

    public String shortTransferLine(TransferRecord record) {
        if (record == null) {
            return "";
        }
        return transferDirectionLabel(record.direction) + " " + transferDisplayName(record) + " " + transferStateLabel(record.done, record.success);
    }

    public String formatTransferStatus(TransferRecord record) {
        if (record == null) {
            return this.context.getString(R.string.transfer_status_idle);
        }
        return transferDirectionLabel(record.direction) + ": " + transferDisplayName(record) + " " + transferStateLabel(record.done, record.success) + " " + transferSizeLabel(record);
    }

    public String transferDirectionLabel(String direction) {
        return "upload".equals(direction) ? this.context.getString(R.string.transfer_upload) : this.context.getString(R.string.transfer_download);
    }

    public String transferStateLabel(boolean done, boolean success) {
        if (!done) {
            return this.context.getString(R.string.transfer_running);
        }
        return success ? this.context.getString(R.string.transfer_done) : this.context.getString(R.string.transfer_failed);
    }

    public String transferSizeLabel(TransferRecord record) {
        if (record == null || record.totalBytes <= 0L) {
            return "";
        }
        int percent = (int)Math.min(100L, Math.max(0L, record.transferredBytes) * 100L / record.totalBytes);
        return formatBytes(record.transferredBytes) + "/" + formatBytes(record.totalBytes) + " " + percent + "%";
    }

    public static String shortStatus(String message) {
        if (message == null) {
            return "";
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= STATUS_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, STATUS_MAX_LENGTH - 3) + "...";
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = (double)bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return String.format(Locale.US, "%.1f GB", mb / 1024.0);
    }
}
