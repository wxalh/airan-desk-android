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
abstract class WebRtcPeerOps
extends WebRtcInputOps {
    protected static void handleConnectRequest(JSONObject object) throws Exception {
        String receiver = object.optString("receiver");
        if (!config.localId().equals(receiver)) {
            return;
        }
        String pwd = object.optString("receiver_pwd");
        String sender = object.optString("sender");
        if (!config.localPasswordMd5().equals(pwd)) {
            JSONObject error = new JSONObject();
            error.put("role", (Object)"cli");
            error.put("type", (Object)"error");
            error.put("sender", (Object)config.localId());
            error.put("receiver", (Object)sender);
            error.put("data", (Object)"password_incorrect");
            SignalingClient.sendText(error.toString());
            return;
        }
        String sessionMode = object.optBoolean("is_only_file", false) ? "file" : "desktop";
        String requestedAudioMode = AudioModePolicy.normalize(object.optString(AiranConstants.KEY_AUDIO_MODE, AudioModePolicy.OFF));
        PeerSession existing = sessions.get(sender);
        if (WebRtcClient.canReuseControlledDesktopConnect(existing, sessionMode)) {
            activeSession = existing;
            if ("desktop".equals(sessionMode)) {
                existing.remoteRequestedAudioMode = requestedAudioMode;
            }
            WebRtcClient.status("Reusing existing controlled desktop session for duplicate connect");
            if ("desktop".equals(existing.mode)) {
                if (screenCaptureIntent == null && existing.localVideoTrack == null) {
                    existing.pendingOfferAfterScreenPermission = true;
                    WebRtcClient.requestScreenCapturePermissionForSession(existing, "duplicate desktop connect");
                } else if (existing.localVideoTrack != null && !existing.peerConnected && !existing.remoteAnswerSet) {
                    WebRtcClient.status("Duplicate desktop connect ignored while offer is pending");
                } else if (existing.peerConnected) {
                    WebRtcClient.sendStreamConfig(existing);
                } else {
                    WebRtcClient.continuePendingControlledDesktopOffer();
                }
            }
            return;
        }
        if (WebRtcClient.canReuseControlledDesktopSessionForAuxConnect(existing, sessionMode)) {
            activeSession = existing;
            WebRtcClient.status("Reusing existing desktop session for auxiliary connect");
            WebRtcClient.requestStorageAccessPermission();
            if (WebRtcClient.fileControlChannel(existing) != null) {
                WebRtcClient.sendLocalFileList(existing, LocalFileUtils.getHomeDir(appContext).getAbsolutePath());
            }
            return;
        }
        if ("desktop".equals(sessionMode) && existing != null && existing.peer != null && (existing.pendingOfferAfterScreenPermission || existing.screenCaptureOfferStarting) && existing.localVideoTrack == null) {
            activeSession = existing;
            if (screenCaptureIntent == null) {
                WebRtcClient.status("Waiting for screen capture permission before SDP offer");
                WebRtcClient.requestScreenCapturePermissionForSession(existing, "existing pending offer");
            } else {
                WebRtcClient.continuePendingControlledDesktopOffer();
            }
            return;
        }
        WebRtcClient.stopSession(sender);
        PeerSession session = new PeerSession(appContext, sender, "cli", pwd, sessionMode);
        sessions.put(sender, session);
        activeSession = session;
        if ("desktop".equals(sessionMode)) {
            session.remoteRequestedAudioMode = requestedAudioMode;
        }
        if (!"desktop".equals(sessionMode)) {
            WebRtcClient.requestStorageAccessPermission();
        }
        WebRtcClient.createPeer(session, true);
        if (session.peer == null) {
            WebRtcClient.status("PeerConnection create failed");
            WebRtcClient.sendPeerError(sender, "peer_connection_create_failed");
            WebRtcClient.stopSession(sender);
            return;
        }
        if ("desktop".equals(session.mode)) {
            session.pendingOfferAfterScreenPermission = true;
            if (screenCaptureIntent == null) {
                WebRtcClient.status("Waiting for screen capture permission before SDP offer");
                WebRtcClient.requestScreenCapturePermissionForSession(session, "new desktop connect");
            } else {
                WebRtcClient.continuePendingControlledDesktopOffer();
            }
            return;
        }
        WebRtcClient.createOffer(session);
    }
    protected static boolean canReuseControlledDesktopSessionForAuxConnect(PeerSession session, String requestedMode) {
        return session != null && !session.stopped && session.peer != null && session.peerConnected && "cli".equals(session.role) && "desktop".equals(session.mode) && !"desktop".equals(requestedMode);
    }
    protected static boolean canReuseControlledDesktopConnect(PeerSession session, String requestedMode) {
        return session != null
                && !session.stopped
                && session.peer != null
                && "cli".equals(session.role)
                && "desktop".equals(session.mode)
                && "desktop".equals(requestedMode)
                && (session.peerConnected
                || session.pendingOfferAfterScreenPermission
                || session.screenCaptureOfferStarting
                || session.localVideoTrack != null
                || session.remoteAnswerSet
                || session.remoteDescriptionSet);
    }
    protected static void createPeer(final PeerSession session, boolean controlledSide) {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(WebRtcClient.buildIceServers());
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.enableCpuOveruseDetection = false;
        rtcConfig.suspendBelowMinBitrate = false;
        rtcConfig.screencastMinBitrate = Integer.valueOf(SCREENCAST_MIN_BITRATE_BPS);
        rtcConfig.combinedAudioVideoBwe = Boolean.TRUE;
        if ("turn_udp".equals(networkPath) || "turn_tcp".equals(networkPath)) {
            rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
        }
        try {
            session.peer = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer(){

                public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                    if (session.stopped) {
                        return;
                    }
                    WebRtcClient.status("Signaling " + signalingState);
                }

                public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                    if (session.stopped) {
                        return;
                    }
                    session.peerConnected = state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED;
                    WebRtcClient.status("ICE " + state);
                    final boolean connected = session.peerConnected;
                    MAIN.post(new Runnable(){

                        @Override
                        public void run() {
                            UiEvents events = uiEvents;
                            if (events != null) {
                                events.onPeerConnectionChanged(connected);
                            }
                        }
                    });
                    if (session.peerConnected) {
                        WebRtcClient.startStatsPolling(session);
                    } else {
                        WebRtcClient.stopStatsPolling(session);
                    }
                    if (state == PeerConnection.IceConnectionState.FAILED || state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.CLOSED) {
                        WebRtcClient.handlePeerConnectionLost(session, state);
                    }
                    WebRtcClient.updateRemoteScreenWakeLock();
                }

                public void onIceConnectionReceivingChange(boolean b) {
                }

                public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                }

                public void onIceCandidate(IceCandidate candidate) {
                    try {
                        if (session.stopped) {
                            return;
                        }
                        WebRtcClient.sendCandidate(session, candidate);
                    }
                    catch (Exception e) {
                        WebRtcClient.status("ICE callback failed: " + e.getMessage());
                    }
                }

                public void onIceCandidatesRemoved(IceCandidate[] candidates) {
                }

                public void onAddStream(MediaStream mediaStream) {
                }

                public void onRemoveStream(MediaStream mediaStream) {
                }

                public void onDataChannel(DataChannel dc) {
                    try {
                        if (session.stopped) {
                            return;
                        }
                        WebRtcClient.bindDataChannel(session, dc);
                    }
                    catch (Exception e) {
                        WebRtcClient.status("DataChannel callback failed: " + e.getMessage());
                    }
                }

                public void onRenegotiationNeeded() {
                }

                public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
                    try {
                        if (session.stopped) {
                            return;
                        }
                        WebRtcClient.attachRemoteTrack(session, receiver == null ? null : receiver.track());
                    }
                    catch (Exception e) {
                        WebRtcClient.status("Track callback failed: " + e.getMessage());
                    }
                }

                public void onTrack(RtpTransceiver transceiver) {
                    try {
                        if (session.stopped) {
                            return;
                        }
                        WebRtcClient.attachRemoteTrack(session, transceiver == null || transceiver.getReceiver() == null ? null : transceiver.getReceiver().track());
                    }
                    catch (Exception e) {
                        WebRtcClient.status("Track callback failed: " + e.getMessage());
                    }
                }
            });
        }
        catch (Exception e) {
            session.peer = null;
            WebRtcClient.status("PeerConnection create exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }
        if (session.peer == null) {
            WebRtcClient.status("PeerConnectionFactory returned null");
            return;
        }
        if (!controlledSide && !"file".equals(session.mode)) {
            WebRtcClient.ensureReceiveOnlyVideoTransceiver(session);
            WebRtcClient.applyLocalAudioState(session);
        }
        if (controlledSide) {
            if (!"file".equals(session.mode)) {
                WebRtcClient.status(screenCaptureIntent == null ? "Screen capture permission missing; waiting for user authorization" : "Screen capture permission ready; waiting for foreground service");
                WebRtcClient.createLocalDataChannel(session, "input_airan", true);
            }
            WebRtcClient.createLocalDataChannel(session, "file_airan", false);
            WebRtcClient.createLocalDataChannel(session, "file_text_airan", false);
        } else if (!"file".equals(session.mode)) {
            WebRtcClient.status("Waiting for remote media tracks");
        }
    }
    protected static void sendPeerError(String receiver, String data) {
        try {
            if (receiver == null || receiver.length() == 0) {
                return;
            }
            JSONObject error = new JSONObject();
            error.put("role", (Object)"cli");
            error.put("type", (Object)"error");
            error.put("sender", (Object)config.localId());
            error.put("receiver", (Object)receiver);
            error.put("data", (Object)(data == null || data.length() == 0 ? "peer_error" : data));
            SignalingClient.sendText(error.toString());
        }
        catch (Exception e) {
            WebRtcClient.status("send peer error failed: " + e.getMessage());
        }
    }
    protected static void ensureReceiveOnlyVideoTransceiver(PeerSession session) {
        if (session == null || session.peer == null) {
            return;
        }
        try {
            List<RtpTransceiver> transceivers = session.peer.getTransceivers();
            if (transceivers != null) {
                for (RtpTransceiver transceiver : transceivers) {
                    if (transceiver == null || transceiver.getMediaType() != MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) continue;
                    transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
                    return;
                }
            }
            session.peer.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY));
            WebRtcClient.status("Control video transceiver prepared: recvonly");
        }
        catch (Exception e) {
            WebRtcClient.status("prepare control video transceiver failed: " + e.getMessage());
        }
    }
    protected static void attachRemoteTrack(PeerSession session, MediaStreamTrack track) {
        if (track instanceof AudioTrack) {
            if (WebRtcClient.isLocalAudioTrack(session, track)) {
                WebRtcClient.status("Local audio track callback ignored");
                return;
            }
            AudioTrack audioTrack = (AudioTrack)track;
            boolean shouldEnable = WebRtcClient.shouldReceiveRemoteAudio(session);
            session.remoteAudioTrack = audioTrack;
            WebRtcClient.setRemoteAudioTrackEnabled(session, shouldEnable);
            if (session.lastAttachedRemoteAudioTrack != audioTrack || session.lastRemoteAudioReceiveEnabled != shouldEnable) {
                session.lastAttachedRemoteAudioTrack = audioTrack;
                session.lastRemoteAudioReceiveEnabled = shouldEnable;
                WebRtcClient.status("Remote audio track " + (shouldEnable ? "enabled" : "muted"));
            }
            return;
        }
        if (!(track instanceof VideoTrack)) {
            if (track != null) {
                WebRtcClient.status("Remote track: " + track.kind());
            }
            return;
        }
        if (session.remoteVideoTrack != null && session.remoteVideoTrack != track) {
            try {
                if (renderer != null && activeSession == session) {
                    session.remoteVideoTrack.removeSink(renderer);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        VideoTrack videoTrack = (VideoTrack)track;
        boolean trackChanged = session.lastAttachedRemoteVideoTrack != videoTrack;
        session.remoteVideoTrack = videoTrack;
        session.remoteVideoTrack.setEnabled(true);
        if (renderer == null || activeSession != session) {
            if (trackChanged) {
                session.lastAttachedRemoteVideoTrack = videoTrack;
                WebRtcClient.status("Remote video track cached until renderer ready");
            }
            return;
        }
        session.lastAttachedRemoteVideoTrack = videoTrack;
        WebRtcClient.attachRemoteVideoToRenderer(session);
    }
    protected static void attachRemoteVideoToRenderer(PeerSession session) {
        if (renderer == null) {
            return;
        }
        WebRtcClient.detachRendererFromOtherSessions(session);
        if (session == null || session.remoteVideoTrack == null) {
            return;
        }
        try {
            session.remoteVideoTrack.setEnabled(true);
            try {
                session.remoteVideoTrack.removeSink(renderer);
            }
            catch (Exception exception) {
                // empty catch block
            }
            session.remoteVideoTrack.addSink(renderer);
            WebRtcClient.status("Remote video track attached");
            WebRtcClient.requestInitialKeyframeOnce(session);
            WebRtcClient.requestKeyframe(session, "renderer attached");
        }
        catch (Exception e) {
            WebRtcClient.status("attach remote video track failed: " + e.getMessage());
        }
    }
    protected static void detachRendererFromOtherSessions(PeerSession keep) {
        if (renderer == null) {
            return;
        }
        for (PeerSession session : sessions.values()) {
            if (session == keep || session.remoteVideoTrack == null) continue;
            try {
                session.remoteVideoTrack.removeSink(renderer);
            }
            catch (Exception exception) {}
        }
    }
    protected static void requestInitialKeyframeOnce(final PeerSession session) {
        if (session == null || !"ctl".equals(session.role) || !"desktop".equals(session.mode)) {
            return;
        }
        if (session.initialKeyframeRequested) {
            return;
        }
        session.initialKeyframeRequested = true;
        MAIN.postDelayed(new Runnable(){

            @Override
            public void run() {
                if (session.inputChannel != null && session.inputChannel.state() == DataChannel.State.OPEN) {
                    WebRtcClient.requestKeyframe(session, "initial");
                }
            }
        }, 1200L);
    }
    protected static void syncRemoteTracks(PeerSession session) {
        if (session.peer == null) {
            return;
        }
        try {
            List<RtpReceiver> receivers = session.peer.getReceivers();
            if (receivers == null) {
                return;
            }
            for (RtpReceiver receiver : receivers) {
                if (receiver == null) continue;
                WebRtcClient.attachRemoteTrack(session, receiver.track());
            }
        }
        catch (Exception e) {
            WebRtcClient.status("sync remote tracks failed: " + e.getMessage());
        }
    }
    protected static List<PeerConnection.IceServer> buildIceServers() {
        return RtcIceServerFactory.build(config, networkPath, DEFAULT_NETWORK_PATH, new RtcIceServerFactory.Listener(){

            @Override
            public void onStatus(String message) {
                WebRtcClient.status(message);
            }
        });
    }
    protected static void addScreenTrackIfReady(final PeerSession session) {
        if (session == null || session.stopped || session.peer == null) {
            return;
        }
        if (screenCaptureIntent == null) {
            WebRtcClient.status("No MediaProjection permission; desktop stream will be unavailable until permission is granted");
            return;
        }
        if (!MediaProjectionForegroundService.isRunning()) {
            MediaProjectionForegroundService.start(appContext);
            WebRtcClient.status("MediaProjection foreground service starting");
            return;
        }
        WebRtcClient.cancelSharedCaptureRelease();
        if (sharedScreenCapturer == null || sharedScreenCaptureStopped) {
            try {
                if (sharedScreenCapturer != null) {
                    WebRtcClient.releaseSharedScreenCapture();
                }
                sharedScreenCaptureStopped = false;
                sharedScreenCapturer = new ScreenCapturerAndroid(screenCaptureIntent, new MediaProjection.Callback(){

                    public void onStop() {
                        WebRtcClient.handleSharedScreenCaptureStopped();
                    }
                });
                sharedSurfaceTextureHelper = SurfaceTextureHelper.create((String)"AiranCaptureThread", (EglBase.Context)eglBase.getEglBaseContext());
                sharedVideoSource = factory.createVideoSource(true);
                sharedFramePaddingObserver = new VideoFramePaddingCapturerObserver(sharedVideoSource.getCapturerObserver());
                sharedScreenCapturer.initialize(sharedSurfaceTextureHelper, appContext, sharedFramePaddingObserver);
                int[] rect = WebRtcClient.preferredCaptureRect(streamWidth, streamHeight);
                WebRtcClient.setSharedCaptureRect(rect, streamFps);
                sharedCaptureFps = streamFps;
                sharedVideoSource.setIsScreencast(true);
                sharedVideoSource.adaptOutputFormat(sharedCaptureWidth, sharedCaptureHeight, sharedCaptureFps);
                sharedScreenCapturer.startCapture(sharedCaptureVisibleWidth, sharedCaptureVisibleHeight, sharedCaptureFps);
                WebRtcClient.status("Shared screen capture started: coded " + sharedCaptureWidth + "x" + sharedCaptureHeight + " visible " + sharedCaptureVisibleWidth + "x" + sharedCaptureVisibleHeight + "@" + sharedCaptureFps);
            }
            catch (Exception sharedInitEx) {
                session.screenCaptureStopped = true;
                WebRtcClient.invalidateScreenCapturePermission();
                WebRtcClient.releaseSharedScreenCapture();
                WebRtcClient.status("shared screen capture start failed: " + sharedInitEx.getMessage());
                if (WebRtcClient.isExpiredMediaProjectionError(sharedInitEx)) {
                    session.pendingOfferAfterScreenPermission = true;
                    WebRtcClient.requestScreenCapturePermissionForSession(session, "capture start failed");
                }
                return;
            }
        } else {
            int[] rect = WebRtcClient.preferredCaptureRect(streamWidth, streamHeight);
            if (!WebRtcClient.sharedCaptureRectMatches(rect, streamFps)) {
                if (!WebRtcClient.applySharedCaptureFormat(rect, streamFps)) {
                    return;
                }
                WebRtcClient.status("Shared screen output adapted: coded " + sharedCaptureWidth + "x" + sharedCaptureHeight + " visible " + sharedCaptureVisibleWidth + "x" + sharedCaptureVisibleHeight + "@" + sharedCaptureFps);
            } else {
                WebRtcClient.status("Reusing shared screen capture: coded " + sharedCaptureWidth + "x" + sharedCaptureHeight + " visible " + sharedCaptureVisibleWidth + "x" + sharedCaptureVisibleHeight + "@" + sharedCaptureFps);
            }
        }
        try {
            session.screenCaptureStopped = false;
            session.screenCapturer = sharedScreenCapturer;
            session.videoSource = sharedVideoSource;
            session.surfaceTextureHelper = sharedSurfaceTextureHelper;
            WebRtcClient.applySharedCaptureRectToSession(session);
            session.localVideoTrack = factory.createVideoTrack("video_airan", sharedVideoSource);
            RtpTransceiver videoTransceiver = session.peer.addTransceiver((MediaStreamTrack)session.localVideoTrack, new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, Collections.singletonList("video_stream1_airan")));
            session.localVideoSender = videoTransceiver == null ? null : videoTransceiver.getSender();
            WebRtcClient.applyVideoSenderParameters(session);
            WebRtcClient.status("Android screen track bound: coded " + session.captureWidth + "x" + session.captureHeight + " visible " + session.captureVisibleWidth + "x" + session.captureVisibleHeight + "@" + session.captureFps);
        }
        catch (Exception bindEx) {
            session.screenCaptureStopped = true;
            WebRtcClient.disposeScreenCaptureResources(session, false);
            WebRtcClient.status("screen track bind failed: " + bindEx.getMessage());
        }
    }
    protected static void continuePendingControlledDesktopOffer() {
        final PeerSession session = activeSession;
        if (!(session != null && session.pendingOfferAfterScreenPermission && "cli".equals(session.role) && "desktop".equals(session.mode))) {
            return;
        }
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                WebRtcClient.startPendingControlledDesktopOffer(session, 0);
            }
        });
    }
    protected static void startPendingControlledDesktopOffer(final PeerSession session, final int attempt) {
        if (session == null || session.stopped || session.peer == null || !session.pendingOfferAfterScreenPermission && !session.screenCaptureOfferStarting) {
            return;
        }
        if (attempt == 0 && session.screenCaptureOfferStarting) {
            return;
        }
        if (screenCaptureIntent == null) {
            session.pendingOfferAfterScreenPermission = true;
            session.screenCaptureOfferStarting = false;
            WebRtcClient.requestScreenCapturePermissionForSession(session, "pending desktop offer");
            return;
        }
        session.pendingOfferAfterScreenPermission = false;
        session.screenCaptureOfferStarting = true;
        if (!MediaProjectionForegroundService.isRunning()) {
            MediaProjectionForegroundService.start(appContext);
            if (attempt < 20) {
                WebRtcClient.status(attempt == 0 ? "MediaProjection foreground service starting" : "Waiting for MediaProjection foreground service");
                MAIN.postDelayed(new Runnable(){

                    @Override
                    public void run() {
                        session.pendingOfferAfterScreenPermission = true;
                        WebRtcClient.startPendingControlledDesktopOffer(session, attempt + 1);
                    }
                }, 200L);
            } else {
                session.pendingOfferAfterScreenPermission = true;
                session.screenCaptureOfferStarting = false;
                WebRtcClient.status("MediaProjection foreground service did not become ready");
            }
            return;
        }
        WebRtcClient.addScreenTrackIfReady(session);
        if (session.localVideoTrack == null) {
            if (attempt < 20) {
                MAIN.postDelayed(new Runnable(){

                    @Override
                    public void run() {
                        session.pendingOfferAfterScreenPermission = true;
                        WebRtcClient.startPendingControlledDesktopOffer(session, attempt + 1);
                    }
                }, 200L);
            } else {
                session.pendingOfferAfterScreenPermission = true;
                session.screenCaptureOfferStarting = false;
                WebRtcClient.status("Screen capture permission is ready but video track was not created");
            }
            return;
        }
        WebRtcClient.applyLocalAudioState(session);
        session.screenCaptureOfferStarting = false;
        WebRtcClient.createOffer(session);
        WebRtcClient.status("Screen capture permission accepted; desktop offer sent");
    }
    protected static int[] preferredCaptureRect(int requestedWidth, int requestedHeight) {
        DisplayMetrics metrics = DisplayMetricsProvider.realMetrics(appContext);
        int displayHeight = metrics.heightPixels;
        int displayWidth = metrics.widthPixels;
        int width = requestedWidth > 0 ? requestedWidth : displayWidth;
        int height = requestedHeight > 0 ? requestedHeight : displayHeight;
        boolean displayPortrait = displayHeight > displayWidth;
        boolean requestPortrait = height > width;
        if (displayPortrait != requestPortrait) {
            int tmp = width;
            width = height;
            height = tmp;
        }
        float displayRatio = (float)displayWidth / Math.max(1.0f, (float)displayHeight);
        if (displayHeight > displayWidth) {
            height = Math.min(Math.max(height, width), 1920);
            width = Math.round((float)height * displayRatio);
        } else {
            width = Math.min(Math.max(width, height), 1920);
            height = Math.round((float)width / Math.max(0.01f, displayRatio));
        }
        width = Math.max(2, width & 0xFFFFFFFE);
        height = Math.max(2, height & 0xFFFFFFFE);
        int codedWidth = WebRtcClient.alignUp16(width);
        int codedHeight = WebRtcClient.alignUp16(height);
        return new int[]{codedWidth, codedHeight, width, height, 0, 0, codedWidth - width, codedHeight - height};
    }
    protected static int alignUp16(int value) {
        return Math.max(16, (value + 15) & ~15);
    }
    protected static boolean sharedCaptureRectMatches(int[] rect, int fps) {
        return rect != null
                && sharedCaptureWidth == rect[CAPTURE_RECT_CODED_WIDTH]
                && sharedCaptureHeight == rect[CAPTURE_RECT_CODED_HEIGHT]
                && sharedCaptureVisibleWidth == rect[CAPTURE_RECT_VISIBLE_WIDTH]
                && sharedCaptureVisibleHeight == rect[CAPTURE_RECT_VISIBLE_HEIGHT]
                && sharedCapturePadLeft == rect[CAPTURE_RECT_PAD_LEFT]
                && sharedCapturePadTop == rect[CAPTURE_RECT_PAD_TOP]
                && sharedCapturePadRight == rect[CAPTURE_RECT_PAD_RIGHT]
                && sharedCapturePadBottom == rect[CAPTURE_RECT_PAD_BOTTOM]
                && sharedCaptureFps == fps;
    }
    protected static void setSharedCaptureRect(int[] rect, int fps) {
        sharedCaptureWidth = rect[CAPTURE_RECT_CODED_WIDTH];
        sharedCaptureHeight = rect[CAPTURE_RECT_CODED_HEIGHT];
        sharedCaptureVisibleWidth = rect[CAPTURE_RECT_VISIBLE_WIDTH];
        sharedCaptureVisibleHeight = rect[CAPTURE_RECT_VISIBLE_HEIGHT];
        sharedCapturePadLeft = rect[CAPTURE_RECT_PAD_LEFT];
        sharedCapturePadTop = rect[CAPTURE_RECT_PAD_TOP];
        sharedCapturePadRight = rect[CAPTURE_RECT_PAD_RIGHT];
        sharedCapturePadBottom = rect[CAPTURE_RECT_PAD_BOTTOM];
        sharedCaptureFps = fps;
        if (sharedFramePaddingObserver != null) {
            sharedFramePaddingObserver.setFrameRect(sharedCaptureWidth, sharedCaptureHeight,
                    sharedCaptureVisibleWidth, sharedCaptureVisibleHeight,
                    sharedCapturePadLeft, sharedCapturePadTop);
        }
    }
    protected static void applySharedCaptureRectToSession(PeerSession session) {
        if (session == null) {
            return;
        }
        session.captureWidth = sharedCaptureWidth;
        session.captureHeight = sharedCaptureHeight;
        session.captureVisibleWidth = sharedCaptureVisibleWidth;
        session.captureVisibleHeight = sharedCaptureVisibleHeight;
        session.capturePadLeft = sharedCapturePadLeft;
        session.capturePadTop = sharedCapturePadTop;
        session.capturePadRight = sharedCapturePadRight;
        session.capturePadBottom = sharedCapturePadBottom;
        session.captureFps = sharedCaptureFps;
    }
    protected static int[] outboundCaptureRectForSession(PeerSession session) {
        if (session != null && session.captureWidth > 0 && session.captureHeight > 0 &&
                session.captureVisibleWidth > 0 && session.captureVisibleHeight > 0) {
            return new int[]{session.captureWidth, session.captureHeight,
                    session.captureVisibleWidth, session.captureVisibleHeight,
                    session.capturePadLeft, session.capturePadTop,
                    session.capturePadRight, session.capturePadBottom};
        }
        return WebRtcClient.preferredCaptureRect(streamWidth, streamHeight);
    }
    protected static void createLocalDataChannel(PeerSession session, String label, boolean unordered) {
        DataChannel.Init init = new DataChannel.Init();
        boolean bl = init.ordered = !unordered;
        if (unordered) {
            init.maxRetransmits = 0;
        }
        DataChannel dc = session.peer.createDataChannel(label, init);
        WebRtcClient.bindDataChannel(session, dc);
    }
    protected static void bindDataChannel(final PeerSession session, final DataChannel dc) {
        String label = dc.label();
        if ("input_airan".equals(label)) {
            session.inputChannel = dc;
        } else if ("file_airan".equals(label)) {
            session.fileChannel = dc;
        } else if ("file_text_airan".equals(label)) {
            session.fileTextChannel = dc;
        }
        dc.registerObserver(new DataChannel.Observer(){

            public void onBufferedAmountChange(long l) {
            }

            public void onStateChange() {
                if (session.stopped) {
                    return;
                }
                WebRtcClient.status("DataChannel " + dc.label() + " " + dc.state());
                if (dc == session.fileTextChannel && dc.state() == DataChannel.State.OPEN && "cli".equals(session.role)) {
                    if (!"desktop".equals(session.mode)) {
                        WebRtcClient.requestStorageAccessPermission();
                    }
                    WebRtcClient.sendLocalFileList(session, LocalFileUtils.getHomeDir(appContext).getAbsolutePath());
                }
                if (dc == session.fileTextChannel && dc.state() == DataChannel.State.OPEN && "ctl".equals(session.role) && ("terminal".equals(session.mode) || session.terminalStartPending)) {
                    WebRtcClient.requestTerminalStart(session);
                }
                if (dc == session.fileTextChannel && dc.state() == DataChannel.State.OPEN && "ctl".equals(session.role)) {
                    if (session.pendingRemoteFileListPath.length() > 0) {
                        WebRtcClient.requestRemoteFileList(session, session.pendingRemoteFileListPath);
                    } else if ("file".equals(session.mode)) {
                        WebRtcClient.requestRemoteFileList(session, "home");
                    }
                }
                if (dc == session.inputChannel && dc.state() == DataChannel.State.OPEN) {
                    WebRtcClient.sendStreamConfig(session);
                    WebRtcClient.sendAudioCaptureConfig(session);
                    WebRtcClient.requestInitialKeyframeOnce(session);
                    WebRtcClient.startControlHeartbeat(session);
                    if ("cli".equals(session.role) && !RemoteInputAccessibilityService.isReady()) {
                        WebRtcClient.status("Accessibility service is not enabled; remote input will be ignored");
                        WebRtcClient.notifyAccessibilityPermissionRequired(session);
                    }
                } else if (dc == session.inputChannel && dc.state() != DataChannel.State.OPEN) {
                    WebRtcClient.stopControlHeartbeat(session);
                }
            }

            public void onMessage(DataChannel.Buffer buffer) {
                if (session.stopped) {
                    return;
                }
                WebRtcClient.handleDataChannelMessage(session, dc.label(), buffer);
            }
        });
    }

}
