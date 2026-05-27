package com.wxalh.airan_desk.file;

import android.content.Context;
import android.content.SharedPreferences;
import com.wxalh.airan_desk.model.TransferRecord;
import java.util.List;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public final class TransferHistoryStore {
    public interface LocalPathResolver {
        String inferLocalPath(String direction, String sourcePath, String targetPath);
    }

    private static final String PREFS = "airan_transfer_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_HISTORY = 100;
    private static final Pattern TRANSFER_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final Context context;
    private final String defaultFileName;
    private final LocalPathResolver localPathResolver;

    public TransferHistoryStore(Context context, String defaultFileName, LocalPathResolver localPathResolver) {
        this.context = context.getApplicationContext();
        this.defaultFileName = defaultFileName == null || defaultFileName.length() == 0 ? "file" : defaultFileName;
        this.localPathResolver = localPathResolver;
    }

    public void loadInto(List<TransferRecord> history) {
        history.clear();
        try {
            SharedPreferences prefs = this.context.getSharedPreferences(PREFS, 0);
            String raw = prefs.getString(KEY_ITEMS, "");
            if (raw == null || raw.length() == 0) {
                return;
            }
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length() && history.size() < MAX_HISTORY; ++i) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                TransferRecord record = fromJson(object);
                normalizeLoadedRecord(record);
                history.add(record);
            }
        } catch (Exception ignored) {
            history.clear();
        }
    }

    public void save(List<TransferRecord> history) {
        try {
            JSONArray array = new JSONArray();
            for (int i = 0; i < history.size(); ++i) {
                array.put(toJson(history.get(i)));
            }
            this.context.getSharedPreferences(PREFS, 0).edit().putString(KEY_ITEMS, array.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public TransferRecord upsert(List<TransferRecord> history, String direction, String transferId, String sourcePath, String targetPath, String fileName, long transferredBytes, long totalBytes, boolean done, boolean success) {
        String safeDirection = direction == null || direction.length() == 0 ? "download" : direction;
        String safeTransferId = transferId == null ? "" : transferId;
        String safeSourcePath = sourcePath == null ? "" : sourcePath;
        String safeTargetPath = targetPath == null ? "" : targetPath;
        String safeName = fileName == null ? "" : fileName;
        if (safeName.length() == 0 || looksLikeTransferId(safeName)) {
            safeName = fileNameFromPath(safeTargetPath);
        }
        if (safeName.length() == 0 || looksLikeTransferId(safeName)) {
            safeName = fileNameFromPath(safeSourcePath);
        }
        if (safeName.length() == 0 || looksLikeTransferId(safeName)) {
            safeName = this.defaultFileName;
        }
        long total = Math.max(0L, totalBytes);
        long transferred = Math.max(0L, transferredBytes);
        long now = System.currentTimeMillis();
        TransferRecord record = find(history, safeDirection, safeTransferId, safeSourcePath, safeTargetPath, safeName, total, now);
        if (record == null) {
            record = new TransferRecord();
            record.startedAt = now;
        } else {
            history.remove(record);
        }
        if (safeTransferId.length() > 0) {
            record.transferId = safeTransferId;
        }
        record.direction = safeDirection;
        if (looksLikeTransferId(safeName) && record.name != null && record.name.length() > 0 && !looksLikeTransferId(record.name)) {
            safeName = record.name;
        }
        record.name = safeName;
        if (safeSourcePath.length() > 0 && !looksLikeTransferId(safeSourcePath)) {
            record.sourcePath = safeSourcePath;
        }
        if (safeTargetPath.length() > 0 && !looksLikeTransferId(safeTargetPath)) {
            record.targetPath = safeTargetPath;
        }
        String localPath = inferLocalPath(safeDirection, record.sourcePath == null ? safeSourcePath : record.sourcePath, record.targetPath == null ? safeTargetPath : record.targetPath);
        if (localPath.length() > 0) {
            record.localPath = localPath;
        }
        record.transferredBytes = transferred;
        record.totalBytes = total;
        record.done = done;
        record.success = success;
        record.updatedAt = now;
        history.add(0, record);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        save(history);
        return record;
    }

    public static boolean looksLikeTransferId(String value) {
        if (value == null) {
            return false;
        }
        return TRANSFER_ID_PATTERN.matcher(value.trim()).matches();
    }

    public static String fileNameFromPath(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf(47);
        return index >= 0 && index + 1 < normalized.length() ? normalized.substring(index + 1) : normalized;
    }

    private TransferRecord find(List<TransferRecord> history, String direction, String transferId, String sourcePath, String targetPath, String name, long totalBytes, long now) {
        TransferRecord fallback = null;
        for (int i = 0; i < history.size(); ++i) {
            TransferRecord record = history.get(i);
            if (record.done || !direction.equals(record.direction)) {
                continue;
            }
            if (transferId != null && transferId.length() > 0 && transferId.equals(record.transferId)) {
                return record;
            }
            if (targetPath != null && targetPath.length() > 0 && targetPath.equals(record.targetPath)) {
                return record;
            }
            if (sourcePath != null && sourcePath.length() > 0 && sourcePath.equals(record.sourcePath)) {
                return record;
            }
            if (name.equals(record.name)) {
                return record;
            }
            if (totalBytes > 0L && record.totalBytes == totalBytes) {
                return record;
            }
            if (fallback == null) {
                fallback = record;
            }
        }
        if (totalBytes > 0L) {
            for (int i = 0; i < history.size(); ++i) {
                TransferRecord record = history.get(i);
                if (direction.equals(record.direction) && record.totalBytes == totalBytes && now - record.updatedAt < 10000L) {
                    return record;
                }
            }
        }
        return fallback;
    }

    private void normalizeLoadedRecord(TransferRecord record) {
        if (record.transferId.length() == 0 && looksLikeTransferId(record.name)) {
            record.transferId = record.name;
            record.name = this.defaultFileName;
        }
        if (record.localPath.length() == 0 || looksLikeTransferId(record.localPath)) {
            record.localPath = inferLocalPath(record.direction, record.sourcePath, record.targetPath);
        }
    }

    private String inferLocalPath(String direction, String sourcePath, String targetPath) {
        if (this.localPathResolver == null) {
            return "";
        }
        String value = this.localPathResolver.inferLocalPath(direction, sourcePath, targetPath);
        return value == null ? "" : value;
    }

    private TransferRecord fromJson(JSONObject object) {
        TransferRecord record = new TransferRecord();
        record.transferId = object.optString("transferId", "");
        record.direction = object.optString("direction", "");
        record.name = object.optString("name", this.defaultFileName);
        record.sourcePath = object.optString("sourcePath", "");
        record.targetPath = object.optString("targetPath", "");
        record.localPath = object.optString("localPath", "");
        record.transferredBytes = object.optLong("transferredBytes", 0L);
        record.totalBytes = object.optLong("totalBytes", 0L);
        record.done = object.optBoolean("done", false);
        record.success = object.optBoolean("success", true);
        record.startedAt = object.optLong("startedAt", object.optLong("updatedAt", System.currentTimeMillis()));
        record.updatedAt = object.optLong("updatedAt", record.startedAt);
        return record;
    }

    private static JSONObject toJson(TransferRecord record) throws Exception {
        JSONObject object = new JSONObject();
        object.put("transferId", record.transferId);
        object.put("direction", record.direction);
        object.put("name", record.name);
        object.put("sourcePath", record.sourcePath);
        object.put("targetPath", record.targetPath);
        object.put("localPath", record.localPath);
        object.put("transferredBytes", record.transferredBytes);
        object.put("totalBytes", record.totalBytes);
        object.put("done", record.done);
        object.put("success", record.success);
        object.put("startedAt", record.startedAt);
        object.put("updatedAt", record.updatedAt);
        return object;
    }
}
