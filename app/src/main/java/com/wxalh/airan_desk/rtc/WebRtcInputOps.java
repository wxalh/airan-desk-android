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
abstract class WebRtcInputOps
extends WebRtcFileOps {
    public static boolean sendPointer(float normalizedX, float normalizedY, boolean down, boolean up) {
        return WebRtcClient.sendPointer(normalizedX, normalizedY, down, up, 1);
    }
    public static boolean sendPointer(float normalizedX, float normalizedY, boolean down, boolean up, int button) {
        PeerSession session = WebRtcClient.requireActiveSession("input");
        if (session == null) {
            return false;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"mouse");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            object.put("x", (double)normalizedX);
            object.put("y", (double)normalizedY);
            if (down) {
                object.put("dwFlags", (Object)"down");
                object.put("down", true);
                object.put("up", false);
                object.put("move", false);
                object.put("button", button);
            } else if (up) {
                object.put("dwFlags", (Object)"up");
                object.put("down", false);
                object.put("up", true);
                object.put("move", false);
                object.put("button", button);
            } else {
                object.put("dwFlags", (Object)"move");
                object.put("down", false);
                object.put("up", false);
                object.put("move", true);
                object.put("button", button);
            }
            return WebRtcClient.sendInput(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("input failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean sendMouseWheel(float normalizedX, float normalizedY, int wheelDelta) {
        PeerSession session = WebRtcClient.requireActiveSession("input");
        if (session == null) {
            return false;
        }
        if (wheelDelta == 0) {
            return true;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"mouse");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            object.put("x", (double)normalizedX);
            object.put("y", (double)normalizedY);
            object.put("dwFlags", (Object)"wheel");
            object.put("down", false);
            object.put("up", false);
            object.put("move", false);
            object.put("wheel", true);
            object.put("mouseData", wheelDelta);
            object.put("button", 0);
            return WebRtcClient.sendInput(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("wheel input failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean sendSwitchScreen() {
        PeerSession session = WebRtcClient.requireActiveSession("switch screen");
        if (session == null) {
            return false;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)AiranConstants.TYPE_SWITCH_SCREEN);
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            return WebRtcClient.sendInput(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("switch screen failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean sendKeyboardEvent(int keyCode, boolean down) {
        PeerSession session = WebRtcClient.requireActiveSession("keyboard");
        if (session == null) {
            return false;
        }
        if (keyCode <= 0) {
            return false;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"keyboard");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            object.put("key", keyCode);
            object.put("dwFlags", (Object)(down ? "down" : "up"));
            return WebRtcClient.sendInput(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("keyboard failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean sendKeyboardText(String text) {
        if (text == null || text.length() == 0) {
            return true;
        }
        boolean ok = true;
        StringBuilder textRun = new StringBuilder();
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                ok = WebRtcClient.sendKeyboardTextInput(textRun.toString()) && ok;
                textRun.setLength(0);
                ok = WebRtcClient.sendKeyboardTap(13) && ok;
                continue;
            }
            if (c == '\t') {
                ok = WebRtcClient.sendKeyboardTextInput(textRun.toString()) && ok;
                textRun.setLength(0);
                ok = WebRtcClient.sendKeyboardTap(9) && ok;
                continue;
            }
            KeyboardChord chord = KeyboardChordMapper.fromChar(c);
            if (chord == null) {
                textRun.append(c);
                continue;
            }
            ok = WebRtcClient.sendKeyboardTextInput(textRun.toString()) && ok;
            textRun.setLength(0);
            if (chord.shift) {
                ok = WebRtcClient.sendKeyboardEvent(16, true) && ok;
            }
            boolean bl = ok = WebRtcClient.sendKeyboardTap(chord.keyCode) && ok;
            if (!chord.shift) continue;
            ok = WebRtcClient.sendKeyboardEvent(16, false) && ok;
        }
        ok = WebRtcClient.sendKeyboardTextInput(textRun.toString()) && ok;
        return ok;
    }
    protected static boolean sendKeyboardTextInput(String text) {
        PeerSession session = WebRtcClient.requireActiveSession("keyboard text");
        if (session == null) {
            return false;
        }
        if (text == null || text.length() == 0) {
            return true;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"keyboard");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            object.put("dwFlags", (Object)"text");
            object.put("text", (Object)text);
            boolean ok = WebRtcClient.sendInput(session, object);
            WebRtcClient.status(ok ? "keyboard text sent: " + text.length() : "keyboard text send failed");
            return ok;
        }
        catch (Exception e) {
            WebRtcClient.status("keyboard text failed: " + e.getMessage());
            return false;
        }
    }
    public static boolean sendRemoteOperation(String action) {
        PeerSession session = WebRtcClient.requireActiveSession("remote operation");
        if (session == null) {
            return false;
        }
        if (action == null || action.length() == 0) {
            return false;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)"remote_operation");
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            object.put("action", (Object)action);
            return WebRtcClient.sendInput(session, object);
        }
        catch (Exception e) {
            WebRtcClient.status("remote operation failed: " + e.getMessage());
            return false;
        }
    }
    protected static boolean sendKeyboardTap(int keyCode) {
        return WebRtcClient.sendKeyboardEvent(keyCode, true) && WebRtcClient.sendKeyboardEvent(keyCode, false);
    }
    protected static boolean sendInput(PeerSession session, JSONObject object) {
        return WebRtcClient.sendDataChannelText(session.inputChannel, object);
    }
    protected static void handleRemoteInput(PeerSession session, JSONObject object) {
        boolean isDoubleClick;
        String msgType = object.optString("msgType");
        if ("stream_config".equals(msgType)) {
            WebRtcClient.handleStreamConfig(session, object);
            return;
        }
        if (AiranConstants.TYPE_AUDIO_CAPTURE.equals(msgType)) {
            String requestedMode = AudioModePolicy.normalize(object.optString(AiranConstants.KEY_AUDIO_MODE, object.optBoolean(AiranConstants.KEY_ENABLED, false) ? AudioModePolicy.LISTEN : AudioModePolicy.OFF));
            session.remoteRequestedAudioMode = requestedMode;
            if ("cli".equals(session.role) && !AudioModePolicy.OFF.equals(requestedMode) && !WebRtcClient.hasAudioTransceiver(session)) {
                WebRtcClient.status("remote requested audio mode pending SDP offer: " + requestedMode);
                return;
            }
            WebRtcClient.applyLocalAudioState(session);
            WebRtcClient.status("remote requested audio mode: " + requestedMode);
            return;
        }
        if (AiranConstants.TYPE_VIDEO_ADAPT_FEEDBACK.equals(msgType)) {
            WebRtcClient.handleVideoAdaptFeedback(session, object);
            return;
        }
        if ("keyframe_request".equals(msgType)) {
            if (sharedScreenCapturer == null || sharedScreenCaptureStopped || screenCaptureIntent == null) {
                WebRtcClient.refreshScreenCapture(session);
            }
            WebRtcClient.sendKeyframeResponse(session);
            return;
        }
        if ("keyframe_response".equals(msgType)) {
            WebRtcClient.status("keyframe response received");
            return;
        }
        if ("keyboard".equals(msgType)) {
            WebRtcClient.remoteInputHandler.handleKeyboard(object, session.inputAccessibilityWarning, WebRtcClient.accessibilityRequest(session));
            return;
        }
        if ("desktop_state".equals(msgType)) {
            boolean locked = object.optBoolean("locked", false);
            String message = object.optString("message");
            WebRtcClient.status("desktop state: locked=" + locked + (message.length() == 0 ? "" : " " + message));
            return;
        }
        if ("control_heartbeat".equals(msgType)) {
            return;
        }
        if (!"cli".equals(session.role)) {
            if ("keyboard".equals(msgType) || "android_navigation".equals(msgType) || "mouse".equals(msgType) || "remote_operation".equals(msgType) || "switch_screen".equals(msgType)) {
                WebRtcClient.status("remote input ignored on control side: " + msgType);
            }
            return;
        }
        if ("android_navigation".equals(msgType)) {
            WebRtcClient.remoteInputHandler.handleNavigation(object.optString("action"), session.inputAccessibilityWarning, WebRtcClient.accessibilityRequest(session));
            return;
        }
        if ("remote_operation".equals(msgType) || "switch_screen".equals(msgType)) {
            WebRtcClient.status("remote input type unsupported on Android controlled side: " + msgType);
            return;
        }
        if (!"mouse".equals(msgType)) {
            return;
        }
        WebRtcClient.remoteInputHandler.handleMouse(object, session.inputPointerState, session.inputAccessibilityWarning, WebRtcClient.accessibilityRequest(session));
    }
    protected static Runnable accessibilityRequest(final PeerSession session) {
        return new Runnable(){

            @Override
            public void run() {
                WebRtcClient.notifyAccessibilityPermissionRequired(session);
            }
        };
    }
    protected static void notifyAccessibilityPermissionRequired(PeerSession session) {
        if (session == null || !"cli".equals(session.role)) {
            return;
        }
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                UiEvents events = uiEvents;
                if (events != null) {
                    events.onAccessibilityPermissionRequired();
                }
            }
        });
    }
    protected static void startRemoteTerminal(PeerSession session, int rows, int cols) {
        if (session.terminalSession == null) {
            session.terminalSession = new TerminalSession();
        }
        session.terminalSession.start(session.fileTextChannel, rows, cols);
    }

}
