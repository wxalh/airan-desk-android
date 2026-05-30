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
abstract class WebRtcMediaOps
extends WebRtcPeerSignalingOps {
    protected static void handleStreamConfig(PeerSession session, JSONObject object) {
        if (session == null || session.stopped || session.peer == null) {
            return;
        }
        if ("ctl".equals(session.role)) {
            WebRtcClient.applyRemoteStreamStatus(session, object);
            return;
        }
        if (!"cli".equals(session.role)) {
            return;
        }
        try {
            WebRtcClient.applyRequestedStreamConfig(object);
            session.baseCaptureFps = streamFps;
            if (sharedScreenCapturer == null || sharedScreenCaptureStopped || screenCaptureIntent == null) {
                WebRtcClient.sendStreamConfig(session);
                return;
            }
            int[] rect = WebRtcClient.preferredCaptureRect(streamWidth, streamHeight);
            int fps = streamFps;
            if (!WebRtcClient.sharedCaptureRectMatches(rect, fps)) {
                if (!WebRtcClient.applySharedCaptureFormat(rect, fps)) {
                    return;
                }
                WebRtcClient.applySharedCaptureRectToSession(session);
                WebRtcClient.applyVideoSenderParameters(session);
                WebRtcClient.status("screen capture format: coded " + sharedCaptureWidth + "x" + sharedCaptureHeight + " visible " + sharedCaptureVisibleWidth + "x" + sharedCaptureVisibleHeight + "@" + sharedCaptureFps);
            } else {
                WebRtcClient.applySharedCaptureRectToSession(session);
                WebRtcClient.applyVideoSenderParameters(session);
            }
            WebRtcClient.sendStreamConfig(session);
        }
        catch (Exception e) {
            if (WebRtcClient.isExpiredMediaProjectionError(e)) {
                session.screenCaptureStopped = true;
                sharedScreenCaptureStopped = true;
                WebRtcClient.invalidateScreenCapturePermission();
                WebRtcClient.releaseSharedScreenCapture();
                WebRtcClient.disposeScreenCaptureResources(session, false);
                session.pendingOfferAfterScreenPermission = true;
                WebRtcClient.requestScreenCapturePermissionForSession(session, "stream config failed");
            }
            WebRtcClient.status("stream config apply failed: " + e.getMessage());
        }
    }
    protected static void applyRequestedStreamConfig(JSONObject object) {
        if (object == null) {
            return;
        }
        if (object.has("networkPath")) {
            networkPath = StreamConfigPolicy.normalizeOneOf(object.optString("networkPath"), DEFAULT_NETWORK_PATH, DEFAULT_NETWORK_PATH, "direct", "turn_udp", "turn_tcp");
        }
        if (object.has("width")) {
            streamWidth = object.optInt("width", streamWidth);
        }
        if (object.has("height")) {
            streamHeight = object.optInt("height", streamHeight);
        }
        if (object.has("fps")) {
            streamFps = Math.max(5, Math.min(60, object.optInt("fps", streamFps)));
        }
    }
    protected static void applyRemoteStreamStatus(PeerSession session, JSONObject object) {
        if (object == null) {
            return;
        }
        boolean statusOnly = object.optBoolean("statusOnly", false);
        if (!statusOnly && object.has("networkPath")) {
            networkPath = StreamConfigPolicy.normalizeOneOf(object.optString("networkPath"), DEFAULT_NETWORK_PATH, DEFAULT_NETWORK_PATH, "direct", "turn_udp", "turn_tcp");
        }
        boolean hasAnyVideoRect = object.has(AiranConstants.KEY_CODED_WIDTH) || object.has(AiranConstants.KEY_CODED_HEIGHT)
                || object.has(AiranConstants.KEY_VISIBLE_WIDTH) || object.has(AiranConstants.KEY_VISIBLE_HEIGHT)
                || object.has(AiranConstants.KEY_PAD_LEFT) || object.has(AiranConstants.KEY_PAD_TOP)
                || object.has(AiranConstants.KEY_PAD_RIGHT) || object.has(AiranConstants.KEY_PAD_BOTTOM);
        boolean hasFullVideoRect = object.has(AiranConstants.KEY_CODED_WIDTH) && object.has(AiranConstants.KEY_CODED_HEIGHT)
                && object.has(AiranConstants.KEY_VISIBLE_WIDTH) && object.has(AiranConstants.KEY_VISIBLE_HEIGHT)
                && object.has(AiranConstants.KEY_PAD_LEFT) && object.has(AiranConstants.KEY_PAD_TOP)
                && object.has(AiranConstants.KEY_PAD_RIGHT) && object.has(AiranConstants.KEY_PAD_BOTTOM);
        if (hasFullVideoRect) {
            remoteCodedWidth = Math.max(0, object.optInt(AiranConstants.KEY_CODED_WIDTH, 0));
            remoteCodedHeight = Math.max(0, object.optInt(AiranConstants.KEY_CODED_HEIGHT, 0));
            remoteVisibleWidth = Math.max(0, object.optInt(AiranConstants.KEY_VISIBLE_WIDTH, 0));
            remoteVisibleHeight = Math.max(0, object.optInt(AiranConstants.KEY_VISIBLE_HEIGHT, 0));
            remotePadLeft = Math.max(0, object.optInt(AiranConstants.KEY_PAD_LEFT, 0));
            remotePadTop = Math.max(0, object.optInt(AiranConstants.KEY_PAD_TOP, 0));
            remotePadRight = Math.max(0, object.optInt(AiranConstants.KEY_PAD_RIGHT, 0));
            remotePadBottom = Math.max(0, object.optInt(AiranConstants.KEY_PAD_BOTTOM, 0));
            sanitizeRemoteVideoRect();
            if (renderer instanceof TextureVideoRenderer) {
                ((TextureVideoRenderer)renderer).setVisibleRect(remoteVisibleWidth, remoteVisibleHeight,
                        remotePadLeft, remotePadTop, remotePadRight, remotePadBottom);
            }
        } else if (hasAnyVideoRect) {
            WebRtcClient.status("ignored incomplete remote video rect from same-version protocol peer");
        }
        String os = object.optString("os");
        String encoder = object.optString("encoderName");
        String frameRect = remoteVisibleWidth > 0 && remoteVisibleHeight > 0 ? " visible=" + remoteVisibleWidth + "x" + remoteVisibleHeight + " coded=" + remoteCodedWidth + "x" + remoteCodedHeight : "";
        String entry = "remote stream: " + WebRtcClient.streamConfigSummary() + frameRect + (os.length() == 0 ? "" : " os=" + os) + (encoder.length() == 0 ? "" : " encoder=" + encoder);
        if (session != null && entry.equals(session.lastRemoteStreamStatusLog)) {
            return;
        }
        if (session != null) {
            session.lastRemoteStreamStatusLog = entry;
        }
        WebRtcClient.status(entry);
    }
    protected static void sanitizeRemoteVideoRect() {
        remoteCodedWidth = Math.max(0, remoteCodedWidth);
        remoteCodedHeight = Math.max(0, remoteCodedHeight);
        remotePadLeft = Math.max(0, remotePadLeft);
        remotePadTop = Math.max(0, remotePadTop);
        remotePadRight = Math.max(0, remotePadRight);
        remotePadBottom = Math.max(0, remotePadBottom);
        if (remoteCodedWidth > 0) {
            remoteVisibleWidth = Math.min(Math.max(0, remoteVisibleWidth), remoteCodedWidth);
            remotePadLeft = Math.min(remotePadLeft, Math.max(0, remoteCodedWidth - remoteVisibleWidth));
            remotePadRight = Math.min(remotePadRight, Math.max(0, remoteCodedWidth - remoteVisibleWidth - remotePadLeft));
        }
        if (remoteCodedHeight > 0) {
            remoteVisibleHeight = Math.min(Math.max(0, remoteVisibleHeight), remoteCodedHeight);
            remotePadTop = Math.min(remotePadTop, Math.max(0, remoteCodedHeight - remoteVisibleHeight));
            remotePadBottom = Math.min(remotePadBottom, Math.max(0, remoteCodedHeight - remoteVisibleHeight - remotePadTop));
        }
    }
    protected static void applyVideoSenderParameters(PeerSession session) {
        if (session == null || session.stopped || session.peer == null || session.localVideoSender == null) {
            return;
        }
        try {
            RtpParameters parameters = session.localVideoSender.getParameters();
            if (parameters == null) {
                return;
            }
            parameters.degradationPreference = RtpParameters.DegradationPreference.BALANCED;
            if (parameters.encodings != null) {
                for (RtpParameters.Encoding encoding : parameters.encodings) {
                    if (encoding == null) continue;
                    encoding.active = true;
                    encoding.maxBitrateBps = null;
                    encoding.minBitrateBps = null;
                    encoding.maxFramerate = Math.max(5, session.captureFps);
                    encoding.scaleResolutionDownBy = 1.0;
                }
            }
            boolean ok = session.localVideoSender.setParameters(parameters);
            WebRtcClient.status("video sender params " + (ok ? "applied" : "rejected") + ": libwebrtc auto bitrate");
        }
        catch (Exception e) {
            WebRtcClient.status("video sender params failed: " + e.getMessage());
        }
    }
    protected static void refreshScreenCapture(PeerSession session) {
        if (session == null || session.stopped || session.peer == null) {
            return;
        }
        if (sharedScreenCapturer == null || sharedScreenCaptureStopped || screenCaptureIntent == null) {
            if ("cli".equals(session.role) && "desktop".equals(session.mode) && screenCaptureIntent == null) {
                session.pendingOfferAfterScreenPermission = true;
                WebRtcClient.requestScreenCapturePermissionForSession(session, "refresh requested");
            }
            return;
        }
        try {
            int targetFps = session.captureFps > 0 ? session.captureFps : sharedCaptureFps;
            int[] rect = WebRtcClient.outboundCaptureRectForSession(session);
            if (!WebRtcClient.applySharedCaptureFormat(rect, targetFps)) {
                return;
            }
            WebRtcClient.applySharedCaptureRectToSession(session);
            WebRtcClient.status("screen capture refreshed");
        }
        catch (Exception e) {
            if (WebRtcClient.isExpiredMediaProjectionError(e)) {
                session.screenCaptureStopped = true;
                sharedScreenCaptureStopped = true;
                WebRtcClient.invalidateScreenCapturePermission();
                WebRtcClient.releaseSharedScreenCapture();
                WebRtcClient.disposeScreenCaptureResources(session, false);
                session.pendingOfferAfterScreenPermission = true;
                WebRtcClient.requestScreenCapturePermissionForSession(session, "refresh failed");
            }
            WebRtcClient.status("screen capture refresh failed: " + e.getMessage());
        }
    }
    public static void refreshActiveVideo() {
        PeerSession session = activeSession;
        if (session == null || session.stopped || !"desktop".equals(session.mode)) {
            return;
        }
        WebRtcClient.sendStreamConfig(session);
        if ("ctl".equals(session.role)) {
            WebRtcClient.attachRemoteVideoToRenderer(session);
            WebRtcClient.requestKeyframe(session, "activity-resumed");
        } else if ("cli".equals(session.role)) {
            WebRtcClient.refreshScreenCapture(session);
        }
    }
    protected static void ensureAudioTransceiver(PeerSession session, RtpTransceiver.RtpTransceiverDirection direction) {
        if (session == null || session.peer == null || "file".equals(session.mode)) {
            return;
        }
        try {
            RtpTransceiver audioTransceiver = null;
            List<RtpTransceiver> transceivers = session.peer.getTransceivers();
            if (transceivers != null) {
                for (RtpTransceiver transceiver : transceivers) {
                    if (transceiver == null || transceiver.getMediaType() != MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                        continue;
                    }
                    audioTransceiver = transceiver;
                    break;
                }
            }
            boolean createdLocalAudioTrack = false;
            if (WebRtcClient.shouldSendLocalAudio(session) && session.localAudioTrack == null) {
                MediaConstraints constraints = new MediaConstraints();
                boolean fullDuplexCall = WebRtcClient.shouldReceiveRemoteAudio(session);
                constraints.optional.add(new MediaConstraints.KeyValuePair("googEchoCancellation", fullDuplexCall ? "true" : "false"));
                constraints.optional.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "false"));
                constraints.optional.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", fullDuplexCall ? "true" : "false"));
                constraints.optional.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "false"));
                constraints.optional.add(new MediaConstraints.KeyValuePair("googTypingNoiseDetection", "false"));
                session.audioSource = factory.createAudioSource(constraints);
                session.localAudioTrack = factory.createAudioTrack(AiranConstants.TYPE_AUDIO, session.audioSource);
                session.localAudioTrack.setEnabled(WebRtcClient.shouldSendLocalAudio(session));
                createdLocalAudioTrack = true;
                WebRtcClient.status("local microphone audio track started");
            }
            if (audioTransceiver != null) {
                audioTransceiver.setDirection(direction);
                if (session.localAudioTrack != null && WebRtcClient.shouldSendLocalAudio(session)) {
                    RtpSender sender = audioTransceiver.getSender();
                    if (sender != null) {
                        session.localAudioSender = sender;
                        sender.setTrack((MediaStreamTrack)session.localAudioTrack, false);
                        WebRtcClient.applyHighQualityAudioSenderParameters(session);
                        if (createdLocalAudioTrack) {
                            WebRtcClient.status("local microphone audio track attached to existing transceiver");
                        }
                    }
                }
                return;
            }
            if (session.localAudioTrack != null && WebRtcClient.shouldSendLocalAudio(session)) {
                audioTransceiver = session.peer.addTransceiver((MediaStreamTrack)session.localAudioTrack, new RtpTransceiver.RtpTransceiverInit(direction, Collections.singletonList(AiranConstants.TYPE_AUDIO)));
                session.localAudioSender = audioTransceiver == null ? null : audioTransceiver.getSender();
                WebRtcClient.applyHighQualityAudioSenderParameters(session);
            } else {
                session.peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, new RtpTransceiver.RtpTransceiverInit(direction));
                WebRtcClient.status("audio transceiver prepared: " + direction);
            }
        }
        catch (Exception e) {
            WebRtcClient.status("prepare audio transceiver failed: " + e.getMessage());
        }
    }
    protected static void applyHighQualityAudioSenderParameters(PeerSession session) {
        if (session == null || session.localAudioSender == null) {
            return;
        }
        try {
            RtpParameters parameters = session.localAudioSender.getParameters();
            if (parameters == null || parameters.encodings == null || parameters.encodings.isEmpty()) {
                return;
            }
            for (RtpParameters.Encoding encoding : parameters.encodings) {
                if (encoding != null) {
                    encoding.maxBitrateBps = 48000;
                }
            }
            session.localAudioSender.setParameters(parameters);
        }
        catch (Exception e) {
            WebRtcClient.status("audio sender parameters failed: " + e.getMessage());
        }
    }
    protected static void applyLocalAudioState(PeerSession session) {
        if (session == null || session.peer == null || "file".equals(session.mode)) {
            return;
        }
        boolean send = WebRtcClient.shouldSendLocalAudio(session);
        boolean receive = WebRtcClient.shouldReceiveRemoteAudio(session);
        RtpTransceiver.RtpTransceiverDirection direction = send && receive ? RtpTransceiver.RtpTransceiverDirection.SEND_RECV : (send ? RtpTransceiver.RtpTransceiverDirection.SEND_ONLY : (receive ? RtpTransceiver.RtpTransceiverDirection.RECV_ONLY : RtpTransceiver.RtpTransceiverDirection.INACTIVE));
        if (send || receive || WebRtcClient.hasAudioTransceiver(session)) {
            WebRtcClient.ensureAudioTransceiver(session, direction);
        }
        if (send) {
            WebRtcClient.setLocalAudioTrackEnabled(session, true);
        } else {
            WebRtcClient.disposeLocalAudioResources(session);
        }
        WebRtcClient.setRemoteAudioTrackEnabled(session, receive);
        if (!session.stopped) {
            WebRtcClient.applyAudioOutputRoute();
        }
    }
    protected static void setLocalAudioTrackEnabled(PeerSession session, boolean enabled) {
        if (session == null || session.localAudioTrack == null) {
            return;
        }
        try {
            session.localAudioTrack.setEnabled(enabled);
        }
        catch (IllegalStateException e) {
            session.localAudioTrack = null;
            session.localAudioSender = null;
            WebRtcClient.status("Local audio track was already disposed");
        }
        catch (Exception e) {
            WebRtcClient.status("Local audio track enable failed: " + e.getMessage());
        }
    }
    protected static void setRemoteAudioTrackEnabled(PeerSession session, boolean enabled) {
        if (session == null || session.remoteAudioTrack == null) {
            return;
        }
        try {
            session.remoteAudioTrack.setEnabled(enabled);
        }
        catch (IllegalStateException e) {
            session.remoteAudioTrack = null;
            session.lastAttachedRemoteAudioTrack = null;
            session.lastRemoteAudioReceiveEnabled = false;
            WebRtcClient.status("Remote audio track was already disposed");
        }
        catch (Exception e) {
            WebRtcClient.status("Remote audio track enable failed: " + e.getMessage());
        }
    }
    public static void resetAudioModeForNewConnection() {
        audioMode = DEFAULT_AUDIO_MODE;
        WebRtcClient.applyAudioOutputRoute();
    }
    protected static void applyAudioOutputRoute() {
        boolean shouldUseSpeaker = false;
        boolean shouldUseCommunicationMode = false;
        for (PeerSession session : sessions.values()) {
            if (session == null || session.stopped || !"desktop".equals(session.mode)) {
                continue;
            }
            if (WebRtcClient.shouldReceiveRemoteAudio(session)) {
                shouldUseSpeaker = true;
            }
            if (WebRtcClient.shouldReceiveRemoteAudio(session) && WebRtcClient.shouldSendLocalAudio(session)) {
                shouldUseCommunicationMode = true;
            }
        }
        AudioManager manager = appContext == null ? null : (AudioManager)appContext.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            if (shouldUseSpeaker) {
                if (!audioRouteActive) {
                    previousAudioMode = manager.getMode();
                    previousSpeakerphoneOn = manager.isSpeakerphoneOn();
                    audioRouteActive = true;
                }
                manager.setMode(shouldUseCommunicationMode ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
                manager.setSpeakerphoneOn(true);
            } else if (audioRouteActive) {
                manager.setSpeakerphoneOn(previousSpeakerphoneOn);
                manager.setMode(previousAudioMode);
                audioRouteActive = false;
            }
        }
        catch (Exception e) {
            WebRtcClient.status("audio route update failed: " + e.getMessage());
        }
    }
    protected static boolean shouldSendLocalAudio(PeerSession session) {
        if (session == null) {
            return false;
        }
        if ("cli".equals(session.role)) {
            String requested = AudioModePolicy.normalize(session.remoteRequestedAudioMode);
            return AudioModePolicy.LISTEN.equals(requested) || AudioModePolicy.CALL.equals(requested);
        }
        return AudioModePolicy.sendsMicrophoneAudio(audioMode);
    }
    protected static boolean hasAudioTransceiver(PeerSession session) {
        if (session == null || session.peer == null) {
            return false;
        }
        try {
            List<RtpTransceiver> transceivers = session.peer.getTransceivers();
            if (transceivers == null) {
                return false;
            }
            for (RtpTransceiver transceiver : transceivers) {
                if (transceiver != null && transceiver.getMediaType() == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                    return true;
                }
            }
        }
        catch (Exception exception) {
            return false;
        }
        return false;
    }
    protected static boolean isLocalAudioTrack(PeerSession session, MediaStreamTrack track) {
        if (session == null || track == null || session.localAudioTrack == null) {
            return false;
        }
        try {
            return session.localAudioTrack.id().equals(track.id());
        }
        catch (Exception exception) {
            return session.localAudioTrack == track;
        }
    }
    protected static boolean shouldReceiveRemoteAudio(PeerSession session) {
        if (session == null) {
            return false;
        }
        if ("cli".equals(session.role)) {
            return AudioModePolicy.CALL.equals(AudioModePolicy.normalize(session.remoteRequestedAudioMode));
        }
        return AudioModePolicy.receivesRemoteAudio(audioMode);
    }

}
