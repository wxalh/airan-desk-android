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
abstract class WebRtcCore {
    protected static final String TAG = "WebRtcClient";
    protected static final String DEFAULT_BITRATE_PROFILE = "medium";
    protected static final String DEFAULT_NETWORK_PATH = "auto";
    protected static final String DEFAULT_CAPTURE_BACKEND = "wgc";
    protected static final String DEFAULT_AUDIO_MODE = AudioModePolicy.OFF;
    protected static final int DEFAULT_CONNECT_VIDEO_WIDTH = -1;
    protected static final int DEFAULT_CONNECT_VIDEO_HEIGHT = -1;
    protected static final int DEFAULT_STREAM_VIDEO_WIDTH = 0;
    protected static final int DEFAULT_STREAM_VIDEO_HEIGHT = 0;
    protected static final int DEFAULT_VIDEO_FPS = 25;
    protected static final long KEYFRAME_REQUEST_MIN_INTERVAL_MS = 6000L;
    protected static final long KEYFRAME_INITIAL_DELAY_MS = 1200L;
    protected static final long VIDEO_RECOVERY_MIN_INTERVAL_MS = 10000L;
    protected static final long VIDEO_ADAPT_FEEDBACK_MIN_INTERVAL_MS = 4000L;
    protected static final int SCREENCAST_MIN_BITRATE_BPS = 2500000;
    protected static final int MAX_DIRECT_DATA_CHANNEL_TEXT_BYTES = 49152;
    protected static final int SCREEN_CAPTURE_SERVICE_RETRY_MS = 200;
    protected static final int SCREEN_CAPTURE_SERVICE_MAX_ATTEMPTS = 20;
    protected static final int CAPTURE_RECT_CODED_WIDTH = 0;
    protected static final int CAPTURE_RECT_CODED_HEIGHT = 1;
    protected static final int CAPTURE_RECT_VISIBLE_WIDTH = 2;
    protected static final int CAPTURE_RECT_VISIBLE_HEIGHT = 3;
    protected static final int CAPTURE_RECT_PAD_LEFT = 4;
    protected static final int CAPTURE_RECT_PAD_TOP = 5;
    protected static final int CAPTURE_RECT_PAD_RIGHT = 6;
    protected static final int CAPTURE_RECT_PAD_BOTTOM = 7;
    protected static final Handler MAIN = new Handler(Looper.getMainLooper());
    protected static Context appContext;
    protected static AppConfig config;
    protected static UiEvents uiEvents;
    protected static PeerConnectionFactory factory;
    protected static EglBase eglBase;
    protected static VideoSink renderer;
    protected static boolean initialized;
    protected static final Map<String, PeerSession> sessions;
    protected static PeerSession activeSession;
    protected static Intent screenCaptureIntent;
    protected static int screenCaptureResultCode;
    protected static boolean screenCapturePermissionRequestInFlight;
    protected static long screenCapturePermissionRequestStartedMs;
    protected static VideoCapturer sharedScreenCapturer;
    protected static VideoSource sharedVideoSource;
    protected static VideoFramePaddingCapturerObserver sharedFramePaddingObserver;
    protected static SurfaceTextureHelper sharedSurfaceTextureHelper;
    protected static int sharedCaptureWidth;
    protected static int sharedCaptureHeight;
    protected static int sharedCaptureVisibleWidth;
    protected static int sharedCaptureVisibleHeight;
    protected static int sharedCapturePadLeft;
    protected static int sharedCapturePadTop;
    protected static int sharedCapturePadRight;
    protected static int sharedCapturePadBottom;
    protected static int sharedCaptureFps;
    protected static boolean sharedScreenCaptureStopped;
    protected static Runnable sharedCaptureReleaseRunnable;
    protected static long lastSessionEndedMs;
    protected static final long SHARED_CAPTURE_IDLE_GRACE_MS = 60000L;
    protected static PowerManager.WakeLock remoteScreenWakeLock;
    protected static String bitrateProfile;
    protected static String networkPath;
    protected static String captureBackend;
    protected static String audioMode;
    protected static int streamWidth;
    protected static int streamHeight;
    protected static int streamFps;
    protected static int remoteCodedWidth;
    protected static int remoteCodedHeight;
    protected static int remoteVisibleWidth;
    protected static int remoteVisibleHeight;
    protected static int remotePadLeft;
    protected static int remotePadTop;
    protected static int remotePadRight;
    protected static int remotePadBottom;
    protected static RemoteAndroidInputHandler remoteInputHandler;
    protected static int previousAudioMode = AudioManager.MODE_NORMAL;
    protected static boolean previousSpeakerphoneOn;
    protected static boolean audioRouteActive;

    public static void init(Context ctx) {
        if (initialized) {
            return;
        }
        appContext = ctx.getApplicationContext();
        config = new AppConfig(appContext);
        remoteInputHandler = new RemoteAndroidInputHandler(appContext, new RemoteAndroidInputHandler.Listener(){

            @Override
            public void onStatus(String message) {
                WebRtcClient.status(message);
            }
        });
        eglBase = EglBase.create();
        PeerConnectionFactory.InitializationOptions opts = PeerConnectionFactory.InitializationOptions.builder((Context)appContext).setEnableInternalTracer(false).createInitializationOptions();
        PeerConnectionFactory.initialize((PeerConnectionFactory.InitializationOptions)opts);
        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        Log.i((String)TAG, (String)"WebRTC decode priority: hardware-first via DefaultVideoDecoderFactory, GPU render via EglRenderer, hardware encode via DefaultVideoEncoderFactory");
        factory = PeerConnectionFactory.builder().setVideoEncoderFactory((VideoEncoderFactory)new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true)).setVideoDecoderFactory((VideoDecoderFactory)decoderFactory).createPeerConnectionFactory();
        initialized = true;
        WebRtcClient.status("WebRTC initialized");
    }
    public static EglBase.Context eglContext() {
        return eglBase == null ? null : eglBase.getEglBaseContext();
    }
    public static void setUiEvents(UiEvents events) {
        uiEvents = events;
    }
    public static void setRenderer(VideoSink value) {
        VideoSink previous = renderer;
        PeerSession session = activeSession;
        if (previous != null && previous != value && session != null && session.remoteVideoTrack != null) {
            try {
                session.remoteVideoTrack.removeSink(previous);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        renderer = value;
        WebRtcClient.attachRemoteVideoToRenderer(session);
    }
    public static void setScreenCapturePermission(int resultCode, Intent data) {
        screenCaptureResultCode = resultCode;
        screenCaptureIntent = data;
        screenCapturePermissionRequestInFlight = false;
        screenCapturePermissionRequestStartedMs = 0L;
        WebRtcClient.status("Screen capture permission ready");
        WebRtcClient.continuePendingControlledDesktopOffer();
    }
    public static void onScreenCapturePermissionDenied() {
        screenCapturePermissionRequestInFlight = false;
        screenCapturePermissionRequestStartedMs = 0L;
        WebRtcClient.status("Screen capture permission denied");
    }
    public static void onScreenCapturePermissionRequestSuppressed(String reason) {
        screenCapturePermissionRequestInFlight = false;
        screenCapturePermissionRequestStartedMs = 0L;
        WebRtcClient.status("Screen capture permission request suppressed" + (reason == null || reason.length() == 0 ? "" : ": " + reason));
    }
    public static boolean hasScreenCapturePermission() {
        return screenCaptureIntent != null;
    }
    public static boolean startControl(String targetId, String targetPwdMd5, String connectMode) {
        WebRtcClient.ensureInitialized();
        if (targetId == null || targetId.trim().length() == 0) {
            WebRtcClient.status("Remote ID is required");
            return false;
        }
        if (targetPwdMd5 == null || targetPwdMd5.length() == 0) {
            WebRtcClient.status("Remote password is required");
            return false;
        }
        String remote = targetId.trim();
        String mode = connectMode == null || connectMode.length() == 0 ? "desktop" : connectMode;
        PeerSession existing = sessions.get(remote);
        if (WebRtcClient.canReuseDesktopSessionForAuxMode(existing, mode)) {
            activeSession = existing;
            WebRtcClient.status("Reusing desktop WebRTC session for " + mode);
            if ("terminal".equals(mode)) {
                WebRtcClient.requestTerminalStart(existing);
            } else {
                WebRtcClient.requestRemoteFileList(existing, existing.pendingRemoteFileListPath.length() == 0 ? "home" : existing.pendingRemoteFileListPath);
            }
            return true;
        }
        WebRtcClient.resetAudioModeForNewConnection();
        WebRtcClient.stopSession(remote);
        PeerSession session = new PeerSession(appContext, remote, "ctl", targetPwdMd5, mode);
        sessions.put(remote, session);
        activeSession = session;
        WebRtcClient.createPeer(session, false);
        if (session.peer == null) {
            sessions.remove(remote);
            if (activeSession == session) {
                activeSession = WebRtcClient.firstSession();
            }
            WebRtcClient.status("PeerConnection create failed");
            return false;
        }
        return WebRtcClient.sendConnect(session, connectMode);
    }
    public static void onSignalingMessage(String msg) {
        WebRtcClient.ensureInitialized();
        try {
            JSONObject object = new JSONObject(msg);
            String sender = object.optString("sender");
            String type = object.optString("type");
            if ("server".equals(sender)) {
                WebRtcClient.handleServerMessage(object);
                return;
            }
            if ("connect".equals(type)) {
                WebRtcClient.handleConnectRequest(object);
                return;
            }
            String receiver = object.optString("receiver");
            if (receiver.length() > 0 && !config.localId().equals(receiver)) {
                return;
            }
            PeerSession session = sessions.get(sender);
            if (session == null) {
                return;
            }
            if ("offer".equals(type) || "answer".equals(type)) {
                WebRtcClient.status("Signaling " + type + " received, sdp=" + object.optString("data").length() + " bytes");
                WebRtcClient.setRemoteDescription(session, type, object.optString("data"));
            } else if ("candidate".equals(type)) {
                WebRtcClient.status("ICE candidate received");
                WebRtcClient.addRemoteCandidate(session, object);
            } else if ("error".equals(type)) {
                WebRtcClient.status("Peer error: " + object.optString("data"));
            }
        }
        catch (Exception e) {
            WebRtcClient.status("Invalid signaling message: " + e.getMessage());
        }
    }
    public static void stop() {
        WebRtcClient.stopSession(activeSession == null ? "" : WebRtcClient.activeSession.remoteId);
    }
    protected static boolean canReuseDesktopSessionForAuxMode(PeerSession session, String requestedMode) {
        return session != null && !session.stopped && session.peer != null && session.peerConnected && "ctl".equals(session.role) && "desktop".equals(session.mode) && !"desktop".equals(requestedMode);
    }
    public static boolean isPeerConnected() {
        return activeSession != null && WebRtcClient.activeSession.peerConnected;
    }
    public static boolean isControlRole() {
        return activeSession != null && "ctl".equals(WebRtcClient.activeSession.role);
    }
    public static boolean hasConnectedControlledDesktopSession() {
        for (PeerSession session : sessions.values()) {
            if (session == null || session.stopped || !session.peerConnected || !"cli".equals(session.role) || !"desktop".equals(session.mode)) continue;
            return true;
        }
        return false;
    }
    public static String currentRemoteId() {
        return activeSession == null ? "" : WebRtcClient.activeSession.remoteId;
    }
    public static String currentMode() {
        return activeSession == null ? "desktop" : WebRtcClient.activeSession.mode;
    }
    public static String runtimeDiagnostics() {
        StringBuilder builder = new StringBuilder();
        builder.append("WebRTC initialized: ").append(initialized).append('\n');
        builder.append("Local id: ").append(config == null ? "" : config.localId()).append('\n');
        builder.append("Screen permission: ").append(WebRtcClient.hasScreenCapturePermission() ? "ready" : "missing").append('\n');
        builder.append("Stream config: ").append(WebRtcClient.streamConfigSummary()).append('\n');
        builder.append("Audio mode: ").append(audioMode).append(" routeActive=").append(audioRouteActive).append('\n');
        builder.append("Sessions: ").append(sessions.size()).append('\n');
        PeerSession session = activeSession;
        if (session == null) {
            builder.append("Active session: none\n");
            return builder.toString();
        }
        builder.append("Active session: ").append(session.remoteId)
                .append(" role=").append(session.role)
                .append(" mode=").append(session.mode)
                .append(" connected=").append(session.peerConnected)
                .append(" stopped=").append(session.stopped).append('\n');
        builder.append("Channels: input=").append(DataChannelUtils.stateText(session.inputChannel))
                .append(" file=").append(DataChannelUtils.stateText(session.fileChannel))
                .append(" fileText=").append(DataChannelUtils.stateText(session.fileTextChannel)).append('\n');
        builder.append("Capture: coded=").append(session.captureWidth).append('x').append(session.captureHeight)
                .append(" visible=").append(session.captureVisibleWidth).append('x').append(session.captureVisibleHeight)
                .append('@').append(session.captureFps)
                .append(" screenTrack=").append(session.localVideoTrack != null)
                .append(" remoteVideo=").append(session.remoteVideoTrack != null).append('\n');
        builder.append("Audio: remoteRequest=").append(session.remoteRequestedAudioMode)
                .append(" localTrack=").append(session.localAudioTrack != null)
                .append(" remoteTrack=").append(session.remoteAudioTrack != null).append('\n');
        builder.append("Inbound video: bytes=").append(session.lastInboundVideoBytes)
                .append(" packets=").append(session.lastInboundVideoPackets)
                .append(" decoded=").append(session.lastInboundVideoFramesDecoded)
                .append(" lost=").append(session.lastInboundVideoPacketsLost)
                .append(" dropped=").append(session.lastInboundVideoFramesDropped)
                .append(" nack=").append(session.lastInboundVideoNackCount)
                .append(" pli=").append(session.lastInboundVideoPliCount).append('\n');
        builder.append("Inbound EWMA: kbps=").append(Math.round(session.inboundArrivalKbpsEwma))
                .append(" jitterMs=").append(Math.round(session.inboundJitterMsEwma))
                .append(" loss=").append(String.format(Locale.US, "%.3f", session.inboundLossRateEwma))
                .append(" decodeGapMs=").append(Math.round(session.inboundDecodeGapEwma)).append('\n');
        builder.append("Outbound video: encoder=").append(session.lastOutboundVideoEncoder)
                .append(" encoded=").append(session.lastOutboundVideoWidth).append('x').append(session.lastOutboundVideoHeight)
                .append(" bytes=").append(session.lastOutboundVideoBytes)
                .append(" frames=").append(session.lastOutboundVideoFramesEncoded)
                .append(" huge=").append(session.lastOutboundVideoHugeFrames).append('\n');
        builder.append("Adaptation: level=").append(session.videoAdaptLevel)
                .append(" stableFeedbacks=").append(session.stableVideoFeedbacks)
                .append(" feedbackKbps=").append(Math.round(session.feedbackArrivalKbpsEwma))
                .append(" feedbackJitterMs=").append(Math.round(session.feedbackJitterMsEwma))
                .append(" feedbackQueue=").append(Math.round(session.feedbackDecodeQueueEwma))
                .append(" feedbackLoss=").append(String.format(Locale.US, "%.3f", session.feedbackLossRateEwma)).append('\n');
        return builder.toString();
    }
    public static boolean selectSession(String remoteId) {
        PeerSession session = sessions.get(remoteId);
        if (session == null) {
            return false;
        }
        activeSession = session;
        WebRtcClient.attachRemoteVideoToRenderer(session);
        return true;
    }
    public static boolean isSessionConnected(String remoteId) {
        PeerSession session = sessions.get(remoteId);
        return session != null && session.peerConnected;
    }
    public static boolean restartActiveControlSession() {
        PeerSession session = activeSession;
        if (session == null || !"ctl".equals(session.role)) {
            return false;
        }
        return WebRtcClient.startControl(session.remoteId, session.remotePwdMd5, session.mode);
    }
    public static boolean requestScreenCapturePermission() {
        return WebRtcClient.requestScreenCapturePermission(true);
    }
    protected static boolean requestScreenCapturePermission(boolean allowPrompt) {
        UiEvents events;
        if (screenCaptureIntent != null) {
            WebRtcClient.continuePendingControlledDesktopOffer();
            return true;
        }
        if (!allowPrompt) {
            WebRtcClient.status("Screen capture permission prompt suppressed for this connection");
            return false;
        }
        if (screenCapturePermissionRequestInFlight) {
            long elapsed = System.currentTimeMillis() - screenCapturePermissionRequestStartedMs;
            if (elapsed < 15000L) {
                WebRtcClient.status("Screen capture permission request already in progress");
                return true;
            }
            screenCapturePermissionRequestInFlight = false;
            screenCapturePermissionRequestStartedMs = 0L;
            WebRtcClient.status("Stale screen capture permission request cleared");
        }
        if ((events = uiEvents) != null) {
            screenCapturePermissionRequestInFlight = true;
            screenCapturePermissionRequestStartedMs = System.currentTimeMillis();
            events.onScreenCapturePermissionRequired();
            return true;
        }
        return false;
    }
    protected static boolean requestScreenCapturePermissionForSession(PeerSession session, String reason) {
        if (screenCaptureIntent != null) {
            WebRtcClient.continuePendingControlledDesktopOffer();
            return true;
        }
        if (session == null || !"cli".equals(session.role) || !"desktop".equals(session.mode)) {
            return WebRtcClient.requestScreenCapturePermission(true);
        }
        return WebRtcClient.requestScreenCapturePermission(true);
    }
    protected static void requestStorageAccessPermission() {
        UiEvents events = uiEvents;
        if (events != null) {
            events.onStorageAccessRequired();
        }
    }
    public static void stopSession(String remoteId) {
        PeerSession session = sessions.get(remoteId);
        if (session == null) {
            return;
        }
        WebRtcClient.stopPeer(session);
        sessions.remove(remoteId);
        if (activeSession == session) {
            activeSession = WebRtcClient.firstSession();
            WebRtcClient.attachRemoteVideoToRenderer(activeSession);
        }
    }
    public static List<SessionInfo> connectedControlSessions() {
        ArrayList<SessionInfo> infos = new ArrayList<SessionInfo>();
        for (PeerSession session : sessions.values()) {
            if (!session.peerConnected || !"ctl".equals(session.role)) continue;
            infos.add(new SessionInfo(session.remoteId, session.mode, true, true));
        }
        return infos;
    }
    public static boolean sendTerminalInput(String text) {
        PeerSession session = WebRtcClient.requireActiveSession("terminal input");
        if (session == null) {
            return false;
        }
        try {
            byte[] bytes = text == null ? new byte[]{} : text.getBytes("UTF-8");
            DataChannel channel = WebRtcClient.fileControlChannel(session);
            WebRtcClient.status("terminal input send: remote=" + session.remoteId + " mode=" + session.mode + " channel=" + DataChannelUtils.stateText(channel) + " bytes=" + bytes.length + " text=" + DataChannelUtils.bytePreview(bytes));
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"terminal_input");
            object.put("encoding", (Object)"base64");
            object.put("data", (Object)Base64.encodeToString((byte[])bytes, (int)2));
            boolean sent = WebRtcClient.sendFileText(session, object);
            WebRtcClient.status("terminal input send " + (sent ? "ok" : "failed") + ": remote=" + session.remoteId + " channel=" + DataChannelUtils.stateText(WebRtcClient.fileControlChannel(session)));
            return sent;
        }
        catch (Exception e) {
            WebRtcClient.status("terminal input failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean sendTerminalResize(int rows, int cols) {
        PeerSession session = WebRtcClient.requireActiveSession("terminal resize");
        if (session == null) {
            return false;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"terminal_resize");
            object.put("rows", Math.max(8, rows));
            object.put("cols", Math.max(20, cols));
            return WebRtcClient.sendFileText(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("terminal resize failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean requestTerminalStart() {
        return WebRtcClient.requestTerminalStart(24, 80);
    }
    public static boolean requestTerminalStart(int rows, int cols) {
        PeerSession session = WebRtcClient.requireActiveSession("terminal start");
        if (session == null) {
            return false;
        }
        return WebRtcClient.requestTerminalStart(session, rows, cols);
    }
    public static boolean sendControlHeartbeat() {
        PeerSession session = WebRtcClient.requireActiveSession("control heartbeat");
        if (session == null) {
            return false;
        }
        return WebRtcClient.sendControlHeartbeat(session);
    }
    static boolean sendControlHeartbeat(PeerSession session) {
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"control_heartbeat");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            return WebRtcClient.sendInput(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("control heartbeat failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean requestKeyframe() {
        PeerSession session = WebRtcClient.requireActiveSession("keyframe request");
        if (session == null) {
            return false;
        }
        return WebRtcClient.requestKeyframe(session, "manual");
    }
    protected static boolean requestKeyframe(PeerSession session, String reason) {
        try {
            if (!"ctl".equals(session.role) || !"desktop".equals(session.mode)) {
                return false;
            }
            long now = System.currentTimeMillis();
            long backoffMs = Math.max(KEYFRAME_REQUEST_MIN_INTERVAL_MS, session.keyframeRequestBackoffMs);
            if (now - session.lastKeyframeRequestMs < backoffMs) {
                WebRtcClient.status("keyframe request skipped: throttled" + (reason == null || reason.length() == 0 ? "" : " (" + reason + ")"));
                return false;
            }
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"keyframe_request");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            boolean sent = WebRtcClient.sendInput(session, object);
            if (sent) {
                session.lastKeyframeRequestMs = now;
                session.keyframeRequestBackoffMs = Math.min(16000L, Math.max(KEYFRAME_REQUEST_MIN_INTERVAL_MS, backoffMs * 2L));
            }
            WebRtcClient.status(sent ? "keyframe request sent" + (reason == null ? "" : ": " + reason) : "keyframe request queued until input opens");
            return sent;
        }
        catch (Exception e) {
            WebRtcClient.status("keyframe request failed: " + e.getMessage());
            return false;
        }
    }
    protected static void sendKeyframeResponse(PeerSession session) {
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"keyframe_response");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("status", true);
            WebRtcClient.sendInput(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("keyframe response failed: " + e.getMessage());
        }
    }
    public static boolean sendStreamConfig() {
        PeerSession session = WebRtcClient.requireActiveSession("stream config");
        if (session == null) {
            return false;
        }
        return WebRtcClient.sendStreamConfig(session);
    }
    public static boolean setBitrateProfile(String profile) {
        String normalized;
        bitrateProfile = normalized = StreamConfigPolicy.normalizeOneOf(profile, DEFAULT_BITRATE_PROFILE, "low", DEFAULT_BITRATE_PROFILE, "high");
        WebRtcClient.resetActiveVideoAdaptationBase();
        return WebRtcClient.sendStreamConfig();
    }
    public static boolean setResolution(int width, int height) {
        streamWidth = Math.max(0, width);
        streamHeight = Math.max(0, height);
        WebRtcClient.resetActiveVideoAdaptationBase();
        return WebRtcClient.sendStreamConfig();
    }
    public static boolean setCaptureBackend(String backend) {
        String normalized;
        captureBackend = normalized = StreamConfigPolicy.normalizeOneOf(backend, DEFAULT_CAPTURE_BACKEND, DEFAULT_CAPTURE_BACKEND, "qt", DEFAULT_NETWORK_PATH);
        return WebRtcClient.sendStreamConfig();
    }
    public static boolean setNetworkPath(String path) {
        networkPath = StreamConfigPolicy.normalizeOneOf(path, DEFAULT_NETWORK_PATH, DEFAULT_NETWORK_PATH, "direct", "turn_udp", "turn_tcp");
        return true;
    }
    public static String bitrateProfile() {
        return bitrateProfile;
    }
    public static String networkPath() {
        return networkPath;
    }
    public static String captureBackend() {
        return captureBackend;
    }
    public static int streamWidth() {
        return streamWidth;
    }
    public static int streamHeight() {
        return streamHeight;
    }
    public static int remoteVisibleWidth() {
        return remoteVisibleWidth;
    }
    public static int remoteVisibleHeight() {
        return remoteVisibleHeight;
    }
    public static int remoteCodedWidth() {
        return remoteCodedWidth;
    }
    public static int remoteCodedHeight() {
        return remoteCodedHeight;
    }
    public static int remotePadLeft() {
        return remotePadLeft;
    }
    public static int remotePadTop() {
        return remotePadTop;
    }
    public static int remotePadRight() {
        return remotePadRight;
    }
    public static int remotePadBottom() {
        return remotePadBottom;
    }
    public static int streamFps() {
        return streamFps;
    }
    public static boolean setAudioMode(String mode) {
        String normalized = AudioModePolicy.normalize(mode);
        if (normalized.equals(audioMode)) {
            return WebRtcClient.sendAudioCaptureConfig();
        }
        audioMode = normalized;
        PeerSession session = activeSession;
        if (session != null && "desktop".equals(session.mode)) {
            WebRtcClient.applyLocalAudioState(session);
            WebRtcClient.sendAudioCaptureConfig(session);
            if (session.peerConnected && "ctl".equals(session.role)) {
                WebRtcClient.createOffer(session);
            }
        }
        return true;
    }
    public static String audioMode() {
        return audioMode;
    }
    public static boolean sendAudioCaptureConfig() {
        PeerSession session = WebRtcClient.requireActiveSession("audio config");
        return session != null && WebRtcClient.sendAudioCaptureConfig(session);
    }
    protected static boolean sendStreamConfig(PeerSession session) {
        if (session == null || session.stopped || session.peer == null) {
            return false;
        }
        try {
            boolean sent;
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"stream_config");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            object.put("bitrateProfile", (Object)bitrateProfile);
            object.put("networkPath", (Object)networkPath);
            object.put("captureBackend", (Object)captureBackend);
            object.put("width", streamWidth);
            object.put("height", streamHeight);
            object.put("fps", streamFps);
            object.put("audioMode", (Object)audioMode);
            if ("cli".equals(session.role)) {
                object.put("statusOnly", true);
                object.put("adaptive", session.videoAdaptLevel > 0);
                object.put("adaptLevel", session.videoAdaptLevel);
                object.put("os", (Object)"android");
                int bitrateWidth = session.captureVisibleWidth > 0 ? session.captureVisibleWidth : session.captureWidth;
                int bitrateHeight = session.captureVisibleHeight > 0 ? session.captureVisibleHeight : session.captureHeight;
                object.put("bitrate", WebRtcClient.targetVideoBitrateBps(bitrateWidth, bitrateHeight, session.captureFps) / 1000);
                JSONArray backends = new JSONArray();
                backends.put((Object)"mediaprojection");
                object.put("captureBackends", (Object)backends);
                object.put("captureBackend", (Object)"mediaprojection");
                object.put("captureName", (Object)"Android MediaProjection");
                int[] rect = WebRtcClient.outboundCaptureRectForSession(session);
                object.put(AiranConstants.KEY_CODED_WIDTH, rect[CAPTURE_RECT_CODED_WIDTH]);
                object.put(AiranConstants.KEY_CODED_HEIGHT, rect[CAPTURE_RECT_CODED_HEIGHT]);
                object.put(AiranConstants.KEY_VISIBLE_WIDTH, rect[CAPTURE_RECT_VISIBLE_WIDTH]);
                object.put(AiranConstants.KEY_VISIBLE_HEIGHT, rect[CAPTURE_RECT_VISIBLE_HEIGHT]);
                object.put(AiranConstants.KEY_PAD_LEFT, rect[CAPTURE_RECT_PAD_LEFT]);
                object.put(AiranConstants.KEY_PAD_TOP, rect[CAPTURE_RECT_PAD_TOP]);
                object.put(AiranConstants.KEY_PAD_RIGHT, rect[CAPTURE_RECT_PAD_RIGHT]);
                object.put(AiranConstants.KEY_PAD_BOTTOM, rect[CAPTURE_RECT_PAD_BOTTOM]);
                if (session.lastOutboundVideoEncoder.length() > 0) {
                    object.put("encoderName", (Object)session.lastOutboundVideoEncoder);
                }
            }
            WebRtcClient.status((sent = WebRtcClient.sendInput(session, object)) ? "stream config sent: " + WebRtcClient.streamConfigSummary() : "stream config waiting for input channel");
            return sent;
        }
        catch (Exception e) {
            WebRtcClient.status("stream config failed: " + e.getMessage());
            return false;
        }
    }
    protected static String streamConfigSummary() {
        return StreamConfigPolicy.summary(bitrateProfile, streamWidth, streamHeight, streamFps, captureBackend, networkPath);
    }
    protected static boolean sendAudioCaptureConfig(PeerSession session) {
        try {
            if (session == null || !"ctl".equals(session.role) || !DataChannelUtils.isOpen(session.inputChannel)) {
                return false;
            }
            JSONObject object = new JSONObject();
            object.put(AiranConstants.KEY_MSGTYPE, (Object)AiranConstants.TYPE_AUDIO_CAPTURE);
            object.put(AiranConstants.KEY_SENDER, (Object)config.localId());
            object.put(AiranConstants.KEY_RECEIVER, (Object)session.remoteId);
            object.put(AiranConstants.KEY_RECEIVER_PWD, (Object)session.remotePwdMd5);
            object.put(AiranConstants.KEY_ENABLED, AudioModePolicy.receivesRemoteAudio(audioMode));
            object.put(AiranConstants.KEY_AUDIO_MODE, (Object)audioMode);
            boolean sent = WebRtcClient.sendInput(session, object);
            WebRtcClient.status(sent ? "audio mode: " + audioMode : "audio config waiting for input channel");
            return sent;
        }
        catch (Exception e) {
            WebRtcClient.status("audio config failed: " + e.getMessage());
            return false;
        }
    }
    protected static boolean requestTerminalStart(PeerSession session) {
        return WebRtcClient.requestTerminalStart(session, 24, 80);
    }
    protected static boolean requestTerminalStart(PeerSession session, int rows, int cols) {
        if (session == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (session.terminalStartRequested && (session.terminalOutputSeen || now - session.terminalStartRequestedMs < 3000L)) {
            return true;
        }
        try {
            if (WebRtcClient.fileControlChannel(session) == null) {
                session.terminalStartPending = true;
                WebRtcClient.status("terminal start queued: file channel not open");
                return true;
            }
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"terminal_start");
            object.put("rows", Math.max(8, rows));
            object.put("cols", Math.max(20, cols));
            object.put("encoding", (Object)"utf-8");
            WebRtcClient.status("terminal start send: remote=" + session.remoteId + " mode=" + session.mode + " channel=" + DataChannelUtils.stateText(WebRtcClient.fileControlChannel(session)));
            boolean sent = WebRtcClient.sendFileText(session, object);
            if (sent) {
                session.terminalStartPending = false;
                session.terminalStartRequested = true;
                session.terminalStartRequestedMs = now;
                WebRtcClient.status("terminal start requested: remote=" + session.remoteId);
            } else {
                WebRtcClient.status("terminal start send failed: remote=" + session.remoteId + " channel=" + DataChannelUtils.stateText(WebRtcClient.fileControlChannel(session)));
            }
            return sent;
        }
        catch (Exception e) {
            WebRtcClient.status("terminal start failed: " + e.getMessage());
            return false;
        }
    }
    static {
        sessions = new LinkedHashMap<String, PeerSession>();
        bitrateProfile = DEFAULT_BITRATE_PROFILE;
        networkPath = DEFAULT_NETWORK_PATH;
        captureBackend = DEFAULT_CAPTURE_BACKEND;
        audioMode = DEFAULT_AUDIO_MODE;
        streamWidth = 0;
        streamHeight = 0;
        streamFps = 25;
        remoteCodedWidth = 0;
        remoteCodedHeight = 0;
        remoteVisibleWidth = 0;
        remoteVisibleHeight = 0;
        remotePadLeft = 0;
        remotePadTop = 0;
        remotePadRight = 0;
        remotePadBottom = 0;
    }
    public static interface UiEvents {
        public void onStatus(String var1);

        public void onPeerConnectionChanged(boolean var1);

        public void onRemoteFiles(JSONArray var1, String var2, JSONArray var3);

        public void onFileTransferProgress(String var1, String var2, String var3, String var4, String var5, long var6, long var8, boolean var10, boolean var11);

        public void onFileUploadFinished(boolean var1, String var2);

        public void onTerminalInfo(String var1, String var2, String var3, boolean var4);

        public void onTerminalText(String var1);

        public void onTerminalBytes(byte[] var1);

        public void onScreenCapturePermissionRequired();

        public void onStorageAccessRequired();

        public void onAccessibilityPermissionRequired();
    }

}
