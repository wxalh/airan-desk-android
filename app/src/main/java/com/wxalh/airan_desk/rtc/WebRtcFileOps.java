package com.wxalh.airan_desk.rtc;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import com.wxalh.airan_desk.config.AppConfig;
import com.wxalh.airan_desk.file.DirectoryStats;
import com.wxalh.airan_desk.file.DownloadDirectoryProvider;
import com.wxalh.airan_desk.file.FileSortUtils;
import com.wxalh.airan_desk.file.LocalFileListResponseBuilder;
import com.wxalh.airan_desk.input.KeyboardChord;
import com.wxalh.airan_desk.input.KeyboardChordMapper;
import com.wxalh.airan_desk.file.LocalFileUtils;
import com.wxalh.airan_desk.service.MediaProjectionForegroundService;
import com.wxalh.airan_desk.input.RemoteAndroidInputHandler;
import com.wxalh.airan_desk.input.RemoteInputAccessibilityService;
import com.wxalh.airan_desk.model.SessionInfo;
import com.wxalh.airan_desk.network.SignalingClient;
import com.wxalh.airan_desk.terminal.TerminalSession;
import com.wxalh.airan_desk.terminal.TerminalInputDecoder;
import com.wxalh.airan_desk.util.DisplayMetricsProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStats;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

@SuppressWarnings({"deprecation", "unchecked"})
abstract class WebRtcFileOps
extends WebRtcCore {
    public static boolean requestRemoteFileList(String path) {
        PeerSession session = WebRtcClient.requireActiveSession("file list");
        if (session == null) {
            return false;
        }
        return WebRtcClient.requestRemoteFileList(session, path);
    }
    protected static boolean requestRemoteFileList(PeerSession session, String path) {
        try {
            String targetPath;
            String string = targetPath = path == null || path.length() == 0 ? "home" : path;
            if (WebRtcClient.fileControlChannel(session) == null) {
                session.pendingRemoteFileListPath = targetPath;
                WebRtcClient.status("file list request queued");
                return true;
            }
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"file_list");
            object.put("path", (Object)targetPath);
            boolean sent = WebRtcClient.sendFileText(session, object);
            if (sent && targetPath.equals(session.pendingRemoteFileListPath)) {
                session.pendingRemoteFileListPath = "";
            }
            return sent;
        }
        catch (Exception e) {
            WebRtcClient.status("file list failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean downloadRemoteFile(String remotePath) {
        PeerSession session = WebRtcClient.requireActiveSession("download");
        if (session == null) {
            return false;
        }
        try {
            File dir = DownloadDirectoryProvider.defaultDownloadDir(appContext);
            File target = new File(dir, LocalFileUtils.safeRemoteName(remotePath));
            String transferId = LocalFileUtils.newTransferId();
            session.filePacketCodec.clearTransferCancel(transferId);
            session.transferDisplayPaths.put(transferId, target.getAbsolutePath());
            session.transferSourcePaths.put(transferId, remotePath);
            session.transferTargetPaths.put(transferId, target.getAbsolutePath());
            session.transferFileNames.put(transferId, LocalFileUtils.safeRemoteName(remotePath));
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"file_download");
            object.put("path_ctl", (Object)target.getAbsolutePath());
            object.put("path_cli", (Object)remotePath);
            object.put("transferId", (Object)transferId);
            WebRtcClient.status("download request sent: " + transferId + " " + remotePath + " -> " + target.getAbsolutePath());
            boolean sent = WebRtcClient.sendFileText(session, object);
            UiEvents events = uiEvents;
            if (sent && events != null && session == activeSession) {
                events.onFileTransferProgress("download", transferId, remotePath, target.getAbsolutePath(), LocalFileUtils.safeRemoteName(remotePath), 0L, 0L, false, true);
            }
            return sent;
        }
        catch (Exception e) {
            WebRtcClient.status("download request failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean runRemoteFile(String remotePath) {
        PeerSession session = WebRtcClient.requireActiveSession("run file");
        if (session == null) {
            return false;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"run_file");
            object.put("path_cli", (Object)remotePath);
            return WebRtcClient.sendFileText(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("run file request failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean uploadLocalFile(File localFile, String remoteDir) {
        final PeerSession session = WebRtcClient.requireActiveSession("upload");
        if (session == null || session.filePacketCodec == null || localFile == null || !localFile.exists()) {
            return false;
        }
        try {
            final String targetDir = remoteDir == null || remoteDir.length() == 0 ? "." : remoteDir;
            final File source = localFile;
            final String transferId = LocalFileUtils.newTransferId();
            final String targetPath = new File(targetDir, source.getName()).getPath();
            session.filePacketCodec.clearTransferCancel(transferId);
            session.transferDisplayPaths.put(transferId, targetPath);
            session.transferSourcePaths.put(transferId, source.getAbsolutePath());
            session.transferTargetPaths.put(transferId, targetPath);
            session.transferFileNames.put(transferId, source.getName());
            WebRtcClient.status("uploading: " + source.getName());
            WebRtcClient.runFileTransfer(session, "upload", new Runnable(){

                @Override
                public void run() {
                    try {
                        if (source.isDirectory()) {
                            WebRtcClient.sendUploadDirectory(session, source, new File(targetDir, source.getName()), transferId);
                            return;
                        }
                        JSONObject header = FileTransferHeaders.fileHeader("file_upload", source.getAbsolutePath(), targetPath, transferId, source.length(), 1, 0L, 1L, source.length(), true);
                        UiEvents events = uiEvents;
                        if (events != null && session == activeSession) {
                            events.onFileTransferProgress("upload", transferId, source.getAbsolutePath(), targetPath, source.getName(), 0L, source.length(), false, true);
                        }
                        WebRtcClient.sendTransferProgress(session, transferId, 0L, source.length(), 0, 1, true, source.getAbsolutePath(), targetPath);
                        session.filePacketCodec.sendPacket(WebRtcClient.fileTransferChannel(session), header, source);
                    }
                    catch (Exception e) {
                        WebRtcClient.status("upload failed: " + e.getMessage());
                    }
                }
            });
            return true;
        }
        catch (Exception e) {
            WebRtcClient.status("upload failed: " + e.getMessage());
            return false;
        }
    }
    protected static boolean sendFileText(PeerSession session, JSONObject object) {
        byte[] bytes;
        if (session == null || session.stopped) {
            return false;
        }
        try {
            bytes = object.toString().getBytes("UTF-8");
        }
        catch (Exception e) {
            bytes = object.toString().getBytes();
        }
        if (bytes.length > 49152) {
            DataChannel channel = WebRtcClient.fileTransferChannel(session);
            if (channel == null || channel.state() != DataChannel.State.OPEN) {
                WebRtcClient.status("channel is not open");
                return false;
            }
            return session.filePacketCodec.sendPacket(channel, object, null);
        }
        return WebRtcClient.sendDataChannelText(WebRtcClient.fileControlChannel(session), object, bytes);
    }
    protected static DataChannel fileControlChannel(PeerSession session) {
        if (session == null) {
            return null;
        }
        if (DataChannelUtils.isOpen(session.fileTextChannel)) {
            return session.fileTextChannel;
        }
        if (DataChannelUtils.isOpen(session.fileChannel)) {
            return session.fileChannel;
        }
        return null;
    }
    protected static DataChannel fileTransferChannel(PeerSession session) {
        if (session == null) {
            return null;
        }
        if (DataChannelUtils.isOpen(session.fileChannel)) {
            return session.fileChannel;
        }
        if (DataChannelUtils.isOpen(session.fileTextChannel)) {
            return session.fileTextChannel;
        }
        return null;
    }
    protected static void handleFileTextMessage(PeerSession session, JSONObject object) throws Exception {
        String msgType = object.optString("msgType");
        if ("file_list".equals(msgType)) {
            if (object.has("folderFiles")) {
                UiEvents events = uiEvents;
                if (events != null && session == activeSession) {
                    events.onRemoteFiles(object.optJSONArray("folderFiles"), object.optString("path"), object.optJSONArray("mounted"));
                }
            } else {
                WebRtcClient.sendLocalFileList(session, object.optString("path"));
            }
        } else if ("file_download".equals(msgType)) {
            if (object.has("error")) {
                String path = object.optString("path_ctl", object.optString("path", object.optString("path_cli")));
                String error = object.optString("error");
                WebRtcClient.status("download failed remotely: " + path + " " + error);
                WebRtcClient.notifyTransferResult(session, "download", path, 0L, false);
            } else if (object.optBoolean("directoryEnd", false)) {
                String path = object.optString("path_ctl", object.optString("path", object.optString("path_cli")));
                boolean ok = object.optBoolean("status", true);
                WebRtcClient.status("download directory status: " + ok + (path.length() == 0 ? "" : " " + path));
                WebRtcClient.notifyTransferResult(session, "download", path, object.optLong("transferTotalBytes", 0L), ok);
            } else {
                WebRtcClient.sendRequestedFile(session, object);
            }
        } else if ("file_upload".equals(msgType) || "upload_file_res".equals(msgType)) {
            boolean ok = object.has("error") ? false : object.optBoolean("status", true);
            String path = object.optString("path_cli");
            String message = object.optString("message", object.optString("error"));
            WebRtcClient.status("file upload status: " + ok + (path.length() == 0 ? "" : " " + path) + (message.length() == 0 ? "" : " " + message));
            String transferId = object.optString("transferId");
            UiEvents events = uiEvents;
            if (events != null && session == activeSession) {
                WebRtcClient.notifyTransferProgressUi(session, "upload", transferId, object.optString("path_ctl"), path, "", object.optLong("file_size", 0L), object.optLong("file_size", 0L), true, ok);
                events.onFileUploadFinished(ok, path);
            }
        } else if ("file_transfer_progress".equals(msgType)) {
            String transferId = object.optString("transferId");
            long transferred = object.optLong("transferBytes", 0L);
            long total = object.optLong("transferTotalBytes", 0L);
            int files = object.optInt("transferFileCount", 0);
            int totalFiles = object.optInt("transferTotalFiles", 0);
            WebRtcClient.status("file transfer progress " + transferId + ": " + transferred + "/" + total + " bytes, files " + files + "/" + totalFiles);
            UiEvents events = uiEvents;
            if (events != null && session == activeSession) {
                String direction = "ctl".equals(session.role) ? "download" : "upload";
                WebRtcClient.notifyTransferProgressUi(session, direction, transferId, object.optString("path_ctl"), object.optString("path_cli"), "", transferred, total, total > 0L && transferred >= total, true);
            }
        } else if ("file_transfer_cancel".equals(msgType)) {
            String transferId = object.optString("transferId");
            if (session.filePacketCodec != null) {
                session.filePacketCodec.cancelTransfer(transferId);
                session.filePacketCodec.cancelAllReassemblies();
            }
            WebRtcClient.status("file transfer cancelled: " + transferId);
        } else if ("terminal_start".equals(msgType)) {
            WebRtcClient.startRemoteTerminal(session, object.optInt("rows", 24), object.optInt("cols", 80));
        } else if ("terminal_input".equals(msgType)) {
            byte[] input = TerminalInputDecoder.decode(object);
            WebRtcClient.status("terminal input received: remote=" + session.remoteId + " role=" + session.role + " terminalSession=" + (session.terminalSession != null) + " bytes=" + input.length + " text=" + DataChannelUtils.bytePreview(input));
            if (session.terminalSession != null) {
                session.terminalSession.input(input);
            } else {
                WebRtcClient.status("terminal input ignored: terminal session is null");
            }
        } else if ("terminal_resize".equals(msgType)) {
            if (session.terminalSession != null) {
                session.terminalSession.resize(object.optInt("rows"), object.optInt("cols"));
            }
        } else if ("terminal_stop".equals(msgType)) {
            if (session.terminalSession != null) {
                session.terminalSession.stop();
            }
        } else if ("terminal_output".equals(msgType)) {
            UiEvents events = uiEvents;
            if (events != null && session == activeSession) {
                byte[] decoded = Base64.decode((String)object.optString("data"), (int)0);
                if (!session.terminalOutputSeen) {
                    session.terminalOutputSeen = true;
                    WebRtcClient.status("terminal output received: " + decoded.length + " bytes");
                }
                WebRtcClient.status("terminal output data: bytes=" + decoded.length + " text=" + DataChannelUtils.bytePreview(decoded));
                events.onTerminalBytes(decoded);
            }
        } else if ("terminal_info".equals(msgType)) {
            String os = object.optString("os");
            String shell = object.optString("shell");
            String terminalMode = object.optString("terminalMode");
            boolean pathTracking = object.optBoolean("pathTracking", false);
            UiEvents events = uiEvents;
            if (events != null && session == activeSession) {
                events.onTerminalInfo(os, shell, terminalMode, pathTracking);
            }
            WebRtcClient.status("terminal: " + os + " " + shell + " " + terminalMode);
        } else if ("terminal_error".equals(msgType)) {
            WebRtcClient.status("terminal error: " + object.optString("error"));
        } else if ("terminal_closed".equals(msgType)) {
            WebRtcClient.status("terminal closed: " + object.optString("status"));
            session.terminalStartRequested = false;
            session.terminalStartRequestedMs = 0L;
        } else if ("run_file".equals(msgType)) {
            WebRtcClient.status("run file status: " + object.optBoolean("status", false) + " " + object.optString("path_cli"));
        }
    }
    static void notifyFileSendProgress(PeerSession session, JSONObject header, long sentBytes, long totalBytes) {
        if (header == null) {
            return;
        }
        String msgType = header.optString("msgType");
        if (!"file_upload".equals(msgType) && !"file_download".equals(msgType)) {
            return;
        }
        if (header.optBoolean("isDirectory", false)) {
            return;
        }
        String direction = "file_upload".equals(msgType) ? "upload" : "download";
        long fileTotal = totalBytes > 0L ? totalBytes : header.optLong("file_size", 0L);
        long fileTransferred = Math.min(Math.max(0L, sentBytes), Math.max(0L, fileTotal));
        long baseBytes = header.optLong(FileTransferHeaders.TRANSFER_BASE_BYTES, 0L);
        long transferTotal = header.optLong("transferTotalBytes", fileTotal);
        int totalFiles = header.optInt("transferTotalFiles", fileTotal > 0L ? 1 : 0);
        int fileIndex = header.optInt(FileTransferHeaders.TRANSFER_FILE_INDEX, totalFiles == 1 ? 1 : 0);
        int transferredFiles = fileTotal == 0L || fileTransferred >= fileTotal ? fileIndex : Math.max(0, fileIndex - 1);
        long transferred = Math.min(baseBytes + fileTransferred, Math.max(0L, transferTotal));
        boolean done = transferTotal == 0L || transferred >= transferTotal;
        String transferId = header.optString("transferId");
        WebRtcClient.sendTransferProgress(session, transferId, transferred, transferTotal, transferredFiles, totalFiles, done, header.optString("path_ctl"), header.optString("path_cli"));
        WebRtcClient.notifyTransferProgressUi(session, direction, transferId, header.optString("path_ctl"), header.optString("path_cli"), header.optString("name"), transferred, transferTotal, done, true);
    }
    static void notifyFileReceiveProgress(PeerSession session, JSONObject header, long receivedBytes, long totalBytes, boolean done) {
        if (session == null || header == null) {
            return;
        }
        UiEvents events = uiEvents;
        if (events == null || session != activeSession) {
            return;
        }
        String msgType = header.optString("msgType");
        String direction = "file_upload".equals(msgType) ? "upload" : "download";
        WebRtcClient.notifyTransferProgressUi(session, direction, header.optString("transferId"), header.optString("path_ctl"), header.optString("path_cli"), header.optString("name"), receivedBytes, totalBytes, done, true);
    }
    protected static void sendTransferProgress(PeerSession session, String transferId, long transferredBytes, long totalBytes, int transferredFiles, int totalFiles, boolean force) {
        WebRtcClient.sendTransferProgress(session, transferId, transferredBytes, totalBytes, transferredFiles, totalFiles, force, "", "");
    }
    protected static void sendTransferProgress(PeerSession session, String transferId, long transferredBytes, long totalBytes, int transferredFiles, int totalFiles, boolean force, String pathCtl, String pathCli) {
        boolean done;
        if (session == null || transferId == null || transferId.length() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = session.lastTransferProgressMs.get(transferId);
        boolean bl = done = totalBytes == 0L || transferredBytes >= totalBytes;
        if (!force && !done && last != null && now - last < 120L) {
            return;
        }
        session.lastTransferProgressMs.put(transferId, now);
        try {
            JSONObject progress = new JSONObject();
            progress.put("msgType", (Object)"file_transfer_progress");
            progress.put("transferId", (Object)transferId);
            progress.put("transferBytes", Math.max(0L, transferredBytes));
            progress.put("transferTotalBytes", Math.max(0L, totalBytes));
            progress.put("transferFileCount", Math.max(0, transferredFiles));
            progress.put("transferTotalFiles", Math.max(0, totalFiles));
            if (pathCtl != null && pathCtl.length() > 0) {
                progress.put("path_ctl", (Object)pathCtl);
            }
            if (pathCli != null && pathCli.length() > 0) {
                progress.put("path_cli", (Object)pathCli);
            }
            WebRtcClient.sendFileText(session, progress);
        }
        catch (Exception e) {
            WebRtcClient.status("file transfer progress failed: " + e.getMessage());
        }
    }
    protected static void notifyTransferResult(PeerSession session, String direction, String path, long totalBytes, boolean success) {
        if ("upload".equals(direction)) {
            WebRtcClient.notifyTransferProgressUi(session, direction, "", "", path, "", Math.max(0L, totalBytes), Math.max(0L, totalBytes), true, success);
        } else {
            WebRtcClient.notifyTransferProgressUi(session, direction, "", path, "", "", Math.max(0L, totalBytes), Math.max(0L, totalBytes), true, success);
        }
    }
    protected static void notifyTransferProgressUi(PeerSession session, String direction, String transferId, String pathCtl, String pathCli, String fileName, long transferredBytes, long totalBytes, boolean done, boolean success) {
        String name;
        String stored;
        String targetPath;
        UiEvents events = uiEvents;
        if (events == null || session != activeSession) {
            return;
        }
        String id = transferId == null ? "" : transferId;
        String sourcePath = "upload".equals(direction) ? LocalFileUtils.safeString(pathCtl) : LocalFileUtils.safeString(pathCli);
        String string = targetPath = "upload".equals(direction) ? LocalFileUtils.safeString(pathCli) : LocalFileUtils.safeString(pathCtl);
        if (sourcePath.length() == 0 && id.length() > 0) {
            stored = session.transferSourcePaths.get(id);
            String string2 = sourcePath = stored == null ? "" : stored;
        }
        if (targetPath.length() == 0 && id.length() > 0) {
            stored = session.transferTargetPaths.get(id);
            String string3 = targetPath = stored == null ? "" : stored;
        }
        if ((targetPath.length() == 0 || LocalFileUtils.looksLikeTransferId(targetPath)) && id.length() > 0) {
            stored = session.transferDisplayPaths.get(id);
            String string4 = targetPath = stored == null ? targetPath : stored;
        }
        if ((name = LocalFileUtils.safeString(fileName)).length() == 0 && id.length() > 0) {
            String stored2 = session.transferFileNames.get(id);
            String string5 = name = stored2 == null ? "" : stored2;
        }
        if (name.length() == 0) {
            name = LocalFileUtils.fileNameFromPath(targetPath.length() > 0 ? targetPath : sourcePath);
        }
        if (name.length() == 0) {
            name = id;
        }
        if (id.length() > 0) {
            if (sourcePath.length() > 0) {
                session.transferSourcePaths.put(id, sourcePath);
            }
            if (targetPath.length() > 0) {
                session.transferTargetPaths.put(id, targetPath);
                session.transferDisplayPaths.put(id, targetPath);
            }
            if (name.length() > 0 && !LocalFileUtils.looksLikeTransferId(name)) {
                session.transferFileNames.put(id, name);
            }
        }
        events.onFileTransferProgress(direction, id, sourcePath, targetPath, name, Math.max(0L, transferredBytes), Math.max(0L, totalBytes), done, success);
    }
    protected static void sendLocalFileList(PeerSession session, String requestedPath) {
        try {
            WebRtcClient.sendFileText(session, LocalFileListResponseBuilder.build(appContext, requestedPath));
        }
        catch (Exception e) {
            WebRtcClient.status("local file list failed: " + e.getMessage());
        }
    }
    protected static void sendRequestedFile(final PeerSession session, JSONObject request) {
        try {
            final String requestedPath = request.optString("path_cli");
            final String targetPath = request.optString("path_ctl");
            final String transferId = LocalFileUtils.normalizeTransferId(request.optString("transferId"));
            session.filePacketCodec.clearTransferCancel(transferId);
            WebRtcClient.runFileTransfer(session, "download", new Runnable(){

                @Override
                public void run() {
                    try {
                        File file = LocalFileUtils.resolveLocalPath(appContext, requestedPath);
                        if (file.isDirectory()) {
                            WebRtcClient.sendDirectory(session, file, new File(targetPath), transferId);
                            return;
                        }
                        JSONObject header = FileTransferHeaders.fileHeader("file_download", targetPath, file.getAbsolutePath(), transferId, file.length(), 1, 0L, 1L, file.length(), false);
                        WebRtcClient.sendTransferProgress(session, transferId, 0L, file.length(), 0, 1, true, targetPath, file.getAbsolutePath());
                        session.filePacketCodec.sendPacket(WebRtcClient.fileTransferChannel(session), header, file);
                    }
                    catch (Exception e) {
                        WebRtcClient.status("send file failed: " + e.getMessage());
                    }
                }
            });
        }
        catch (Exception e) {
            WebRtcClient.status("send file failed: " + e.getMessage());
        }
    }
    static void handleFilePacket(PeerSession session, JSONObject header, File payloadFile) {
        try {
            String msgType = header.optString("msgType");
            String transferId = header.optString("transferId");
            if (FileControlMessageTypes.isFragmentedControlMessage(msgType)) {
                WebRtcClient.handleFileTextMessage(session, header);
            } else if ("file_download".equals(msgType)) {
                File target = LocalFileUtils.resolveSandboxed(appContext, new File(header.optString("path_ctl")));
                if (header.optBoolean("isDirectory", false)) {
                    if (header.optBoolean("directoryStart", false)) {
                        if (!target.exists() && !target.mkdirs()) {
                            WebRtcClient.status("download directory create failed: " + target.getAbsolutePath());
                        }
                        WebRtcClient.sendTransferProgress(session, transferId, 0L, header.optLong("transferTotalBytes", 0L), 0, header.optInt("transferTotalFiles", 0), true, header.optString("path_ctl"), header.optString("path_cli"));
                    } else if (header.optBoolean("directoryEnd", false)) {
                        WebRtcClient.status("downloaded directory: " + target.getAbsolutePath());
                        WebRtcClient.notifyTransferResult(session, "download", target.getAbsolutePath(), header.optLong("transferTotalBytes", 0L), header.optBoolean("status", true));
                    }
                } else {
                    LocalFileUtils.copyFile(payloadFile, target);
                    WebRtcClient.status("downloaded: " + target.getAbsolutePath());
                    WebRtcClient.notifyTransferResult(session, "download", target.getAbsolutePath(), header.optLong("file_size", target.length()), true);
                }
            } else if ("file_upload".equals(msgType)) {
                File target = LocalFileUtils.resolveLocalPath(appContext, header.optString("path_cli"));
                boolean isDirectory = header.optBoolean("isDirectory", false);
                boolean directoryStart = header.optBoolean("directoryStart", false);
                boolean directoryEnd = header.optBoolean("directoryEnd", false);
                if (isDirectory) {
                    if (directoryStart) {
                        if (!target.exists() && !target.mkdirs()) {
                            throw new IllegalStateException("failed to create directory: " + target.getAbsolutePath());
                        }
                        WebRtcClient.sendTransferProgress(session, transferId, 0L, header.optLong("transferTotalBytes", 0L), 0, header.optInt("transferTotalFiles", 0), true, header.optString("path_ctl"), header.optString("path_cli"));
                    } else if (directoryEnd) {
                        JSONObject response = new JSONObject();
                        response.put("msgType", (Object)"upload_file_res");
                        response.put("status", header.optBoolean("status", true));
                        response.put("path_cli", (Object)target.getAbsolutePath());
                        response.put("file_size", header.optLong("file_size", 0L));
                        response.put("transferId", (Object)transferId);
                        WebRtcClient.sendFileText(session, response);
                        WebRtcClient.sendLocalFileList(session, target.getParent());
                        WebRtcClient.notifyTransferResult(session, "upload", target.getAbsolutePath(), header.optLong("transferTotalBytes", 0L), header.optBoolean("status", true));
                    }
                } else {
                    LocalFileUtils.copyFile(payloadFile, target);
                    JSONObject response = new JSONObject();
                    response.put("msgType", (Object)"upload_file_res");
                    response.put("status", true);
                    response.put("path_cli", (Object)target.getAbsolutePath());
                    response.put("file_size", header.optLong("file_size", target.length()));
                    response.put("transferId", (Object)transferId);
                    WebRtcClient.sendFileText(session, response);
                    WebRtcClient.sendLocalFileList(session, target.getParent());
                    WebRtcClient.notifyTransferResult(session, "upload", target.getAbsolutePath(), header.optLong("file_size", target.length()), true);
                }
            }
        }
        catch (Exception e) {
            WebRtcClient.status("file packet save failed: " + e.getMessage());
        } finally {
            if (payloadFile != null) {
                payloadFile.delete();
            }
        }
    }
    protected static void sendDirectory(PeerSession session, File sourceDir, File targetDir, String transferId) throws Exception {
        DirectoryStats stats = DirectoryStats.collect(sourceDir);
        WebRtcClient.sendDirectoryMarker(session, "file_download", sourceDir, targetDir, transferId, true, false, true, 0, 0L, stats.totalBytes, stats.totalFiles);
        long[] result = new long[]{0L, 0L, 0L};
        WebRtcClient.sendDirectoryChildren(session, sourceDir, targetDir, transferId, result, stats.totalBytes, stats.totalFiles);
        WebRtcClient.sendDirectoryMarker(session, "file_download", sourceDir, targetDir, transferId, false, true, result[1] == 0L, (int)result[0], result[2], stats.totalBytes, stats.totalFiles);
    }
    protected static void sendDirectoryChildren(PeerSession session, File sourceDir, File targetDir, String transferId, long[] result, long totalBytes, int totalFiles) throws Exception {
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }
        FileSortUtils.sortDirectoriesFirst(children);
        for (File child : children) {
            File childTarget = new File(targetDir, child.getName());
            if (child.isDirectory()) {
                WebRtcClient.sendDirectoryMarker(session, "file_download", child, childTarget, transferId, true, false, true, (int)result[0], result[2], totalBytes, totalFiles);
                WebRtcClient.sendDirectoryChildren(session, child, childTarget, transferId, result, totalBytes, totalFiles);
                WebRtcClient.sendDirectoryMarker(session, "file_download", child, childTarget, transferId, false, true, result[1] == 0L, (int)result[0], result[2], totalBytes, totalFiles);
                continue;
            }
            if (!child.isFile()) continue;
            JSONObject header = FileTransferHeaders.fileHeader("file_download", childTarget.getAbsolutePath(), child.getAbsolutePath(), transferId, totalBytes, totalFiles, result[2], result[0] + 1L, child.length(), true);
            try {
                WebRtcClient.sendTransferProgress(session, transferId, result[2], totalBytes, (int)result[0], totalFiles, true, childTarget.getAbsolutePath(), child.getAbsolutePath());
                session.filePacketCodec.sendPacket(WebRtcClient.fileTransferChannel(session), header, child);
                result[0] = result[0] + 1L;
                result[2] = result[2] + child.length();
            }
            catch (Exception e) {
                result[1] = result[1] + 1L;
                WebRtcClient.status("send directory file failed: " + child.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }
    protected static void sendDirectoryMarker(PeerSession session, String msgType, File source, File target, String transferId, boolean start, boolean end, boolean ok, int fileCount, long transferredBytes, long totalBytes, int totalFiles) throws Exception {
        JSONObject header = FileTransferHeaders.directoryMarker(msgType, target.getAbsolutePath(), source.getAbsolutePath(), transferId, start, end, ok, fileCount, transferredBytes, totalBytes, totalFiles);
        session.filePacketCodec.sendPacket(WebRtcClient.fileTransferChannel(session), header, null);
    }
    protected static void sendUploadDirectory(PeerSession session, File sourceDir, File remoteTargetDir, String transferId) throws Exception {
        DirectoryStats stats = DirectoryStats.collect(sourceDir);
        WebRtcClient.sendUploadDirectoryMarker(session, sourceDir, remoteTargetDir, transferId, true, false, true, 0, 0L, stats.totalBytes, stats.totalFiles);
        long[] result = new long[]{0L, 0L, 0L};
        WebRtcClient.sendUploadDirectoryChildren(session, sourceDir, remoteTargetDir, transferId, result, stats.totalBytes, stats.totalFiles);
        WebRtcClient.sendUploadDirectoryMarker(session, sourceDir, remoteTargetDir, transferId, false, true, result[1] == 0L, (int)result[0], result[2], stats.totalBytes, stats.totalFiles);
    }
    protected static void sendUploadDirectoryChildren(PeerSession session, File sourceDir, File remoteTargetDir, String transferId, long[] result, long totalBytes, int totalFiles) throws Exception {
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }
        FileSortUtils.sortDirectoriesFirst(children);
        for (File child : children) {
            File childTarget = new File(remoteTargetDir, child.getName());
            if (child.isDirectory()) {
                WebRtcClient.sendUploadDirectoryMarker(session, child, childTarget, transferId, true, false, true, (int)result[0], result[2], totalBytes, totalFiles);
                WebRtcClient.sendUploadDirectoryChildren(session, child, childTarget, transferId, result, totalBytes, totalFiles);
                WebRtcClient.sendUploadDirectoryMarker(session, child, childTarget, transferId, false, true, result[1] == 0L, (int)result[0], result[2], totalBytes, totalFiles);
                continue;
            }
            if (!child.isFile()) continue;
            JSONObject header = FileTransferHeaders.fileHeader("file_upload", child.getAbsolutePath(), childTarget.getPath(), transferId, totalBytes, totalFiles, result[2], result[0] + 1L, child.length(), true);
            try {
                WebRtcClient.sendTransferProgress(session, transferId, result[2], totalBytes, (int)result[0], totalFiles, true, child.getAbsolutePath(), childTarget.getPath());
                session.filePacketCodec.sendPacket(WebRtcClient.fileTransferChannel(session), header, child);
                result[0] = result[0] + 1L;
                result[2] = result[2] + child.length();
            }
            catch (Exception e) {
                result[1] = result[1] + 1L;
                WebRtcClient.status("upload directory file failed: " + child.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }
    protected static void sendUploadDirectoryMarker(PeerSession session, File source, File target, String transferId, boolean start, boolean end, boolean ok, int fileCount, long transferredBytes, long totalBytes, int totalFiles) throws Exception {
        JSONObject header = FileTransferHeaders.directoryMarker("file_upload", source.getAbsolutePath(), target.getPath(), transferId, start, end, ok, fileCount, transferredBytes, totalBytes, totalFiles);
        session.filePacketCodec.sendPacket(WebRtcClient.fileTransferChannel(session), header, null);
    }
    protected static void runFileTransfer(PeerSession session, String name, Runnable task) {
        Thread thread = new Thread(task, "AiranFileTransfer-" + name + "-" + session.remoteId);
        thread.setDaemon(true);
        thread.start();
    }

}
