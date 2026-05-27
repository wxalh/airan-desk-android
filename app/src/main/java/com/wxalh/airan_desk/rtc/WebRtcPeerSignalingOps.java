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
abstract class WebRtcPeerSignalingOps
extends WebRtcPeerOps {
    protected static void createOffer(final PeerSession session) {
        if (session == null || session.stopped || session.peer == null) {
            return;
        }
        WebRtcClient.applyLocalAudioState(session);
        session.remoteAnswerSet = false;
        session.peer.createOffer((SdpObserver)new LoggingSdpObserver(){

            @Override
            public void onCreateSuccess(SessionDescription desc) {
                if (session.stopped || session.peer == null) {
                    return;
                }
                final SessionDescription offer = WebRtcClient.forceH264OnlyDescription(desc, "local offer");
                WebRtcClient.logSdpSummary("local " + offer.type.canonicalForm(), offer.description);
                session.peer.setLocalDescription((SdpObserver)new LoggingSdpObserver(){

                    @Override
                    public void onSetSuccess() {
                        if (session.stopped || session.peer == null) {
                            return;
                        }
                        WebRtcClient.sendDescription(session, offer);
                    }
                }, offer);
            }
        }, new MediaConstraints());
    }
    protected static void createAnswer(final PeerSession session) {
        if (session == null || session.stopped || session.peer == null) {
            WebRtcClient.status("Cannot create answer: peer is null");
            return;
        }
        try {
            WebRtcClient.applyLocalAudioState(session);
            session.peer.createAnswer((SdpObserver)new LoggingSdpObserver(){

                @Override
                public void onCreateSuccess(SessionDescription desc) {
                    if (session.stopped || session.peer == null) {
                        WebRtcClient.status("Cannot set local answer: peer is null");
                        return;
                    }
                    final SessionDescription answer = WebRtcClient.forceH264OnlyDescription(desc, "local answer");
                    WebRtcClient.logSdpSummary("local " + answer.type.canonicalForm(), answer.description);
                    session.peer.setLocalDescription((SdpObserver)new LoggingSdpObserver(){

                        @Override
                        public void onSetSuccess() {
                            if (session.stopped || session.peer == null) {
                                return;
                            }
                            WebRtcClient.sendDescription(session, answer);
                        }
                    }, answer);
                }
            }, WebRtcClient.answerConstraints());
        }
        catch (Exception e) {
            WebRtcClient.status("createAnswer exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    protected static MediaConstraints answerConstraints() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        return constraints;
    }
    protected static void prepareReceiveOnlyAnswer(PeerSession session) {
        if (session.peer == null || !"ctl".equals(session.role) || "file".equals(session.mode)) {
            return;
        }
        try {
            WebRtcClient.ensureReceiveOnlyVideoTransceiver(session);
            WebRtcClient.applyLocalAudioState(session);
            List<RtpTransceiver> transceivers = session.peer.getTransceivers();
            if (transceivers == null) {
                return;
            }
            boolean anyOk = false;
            int videoTransceiverCount = 0;
            for (RtpTransceiver transceiver : transceivers) {
                MediaStreamTrack.MediaType mediaType;
                if (transceiver == null || (mediaType = transceiver.getMediaType()) != MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO && mediaType != MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) continue;
                if (mediaType != MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) continue;
                boolean ok = transceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);
                anyOk = anyOk || ok;
                videoTransceiverCount++;
            }
            if (videoTransceiverCount > 0) {
                String entry = "Answer " + MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO + " recvonly=" + anyOk;
                if (!entry.equals(session.lastRecvonlyAnswerLog)) {
                    session.lastRecvonlyAnswerLog = entry;
                    WebRtcClient.status(entry);
                }
            }
        }
        catch (Exception e) {
            WebRtcClient.status("prepare recvonly answer failed: " + e.getMessage());
        }
    }
    protected static void setRemoteDescription(final PeerSession session, String type, String sdp) {
        if (sdp == null || sdp.length() == 0) {
            return;
        }
        if (session == null || session.stopped || session.peer == null) {
            WebRtcClient.status("Cannot set remote description: peer is null");
            return;
        }
        final SessionDescription.Type descType = "offer".equals(type) ? SessionDescription.Type.OFFER : SessionDescription.Type.ANSWER;
        try {
            if (type.equals(session.lastRemoteDescriptionType) && sdp.equals(session.lastRemoteDescriptionSdp)) {
                WebRtcClient.status("Duplicate remote SDP " + type + " ignored");
                return;
            }
            PeerConnection.SignalingState signalingState = session.peer.signalingState();
            if (descType == SessionDescription.Type.ANSWER) {
                if (signalingState != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    WebRtcClient.status("Remote SDP answer ignored in state " + signalingState);
                    return;
                }
            } else if (signalingState != PeerConnection.SignalingState.STABLE) {
                WebRtcClient.status("Remote SDP offer ignored in state " + signalingState);
                return;
            }
            String remoteSdp = SdpH264Patcher.forceH264Only(sdp, "remote " + type, new SdpH264Patcher.Listener(){

                @Override
                public void onPatched(String patchedLabel) {
                    WebRtcClient.status("Android H264-only SDP patch applied: " + patchedLabel);
                }
            }, false);
            WebRtcClient.logSdpSummary("remote " + type, remoteSdp);
            session.lastRemoteDescriptionType = type;
            session.lastRemoteDescriptionSdp = sdp;
            session.peer.setRemoteDescription((SdpObserver)new LoggingSdpObserver(){

                @Override
                public void onSetSuccess() {
                    if (session.stopped || session.peer == null) {
                        return;
                    }
                    session.remoteDescriptionSet = true;
                    if (descType == SessionDescription.Type.ANSWER) {
                        session.remoteAnswerSet = true;
                    }
                    WebRtcClient.flushPendingCandidates(session);
                    WebRtcClient.syncRemoteTracks(session);
                    if (descType == SessionDescription.Type.OFFER) {
                        WebRtcClient.prepareReceiveOnlyAnswer(session);
                        WebRtcClient.createAnswer(session);
                    }
                }

                @Override
                public void onSetFailure(String s) {
                    session.lastRemoteDescriptionType = "";
                    session.lastRemoteDescriptionSdp = "";
                    WebRtcClient.status("SDP set failed: " + s);
                }
            }, new SessionDescription(descType, remoteSdp));
            WebRtcClient.status("Remote SDP " + type + " accepted for setting");
        }
        catch (Exception e) {
            WebRtcClient.status("setRemoteDescription exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    protected static void logSdpSummary(String label, String sdp) {
        SdpSummaryLogger.log(TAG, label, sdp);
    }
    protected static SessionDescription forceH264OnlyDescription(SessionDescription desc, String label) {
        if (desc == null) {
            return null;
        }
        String patched = SdpH264Patcher.forceH264Only(desc.description, label, new SdpH264Patcher.Listener(){

            @Override
            public void onPatched(String patchedLabel) {
                WebRtcClient.status("Android H264-only SDP patch applied: " + patchedLabel);
            }
        });
        return patched.equals(desc.description) ? desc : new SessionDescription(desc.type, patched);
    }
    protected static void addRemoteCandidate(PeerSession session, JSONObject object) throws Exception {
        if (session.peer == null) {
            WebRtcClient.status("Ignore ICE candidate: peer is null");
            return;
        }
        IceCandidate candidate = new IceCandidate(object.optString("mid"), 0, object.optString("data"));
        if (!session.remoteDescriptionSet) {
            session.pendingCandidates.add(candidate);
        } else {
            session.peer.addIceCandidate(candidate);
        }
    }
    protected static void flushPendingCandidates(PeerSession session) {
        for (IceCandidate candidate : session.pendingCandidates) {
            try {
                if (session.peer == null) continue;
                session.peer.addIceCandidate(candidate);
            }
            catch (Exception e) {
                WebRtcClient.status("addIceCandidate failed: " + e.getMessage());
            }
        }
        session.pendingCandidates.clear();
    }
    protected static boolean sendConnect(PeerSession session, String connectMode) {
        try {
            JSONObject object = new JSONObject();
            object.put("role", (Object)"ctl");
            object.put("type", (Object)"connect");
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            object.put("sender", (Object)config.localId());
            object.put("is_only_file", !"desktop".equals(connectMode));
            object.put("width", -1);
            object.put("height", -1);
            object.put("bitrateProfile", (Object)bitrateProfile);
            object.put("networkPath", (Object)networkPath);
            object.put("captureBackend", (Object)captureBackend);
            object.put(AiranConstants.KEY_AUDIO_MODE, (Object)audioMode);
            object.put("fps", streamFps);
            boolean sent = SignalingClient.sendText(object.toString());
            WebRtcClient.status(sent ? "CONNECT sent to " + session.remoteId : "WebSocket is not connected; CONNECT was not sent");
            return sent;
        }
        catch (Exception e) {
            WebRtcClient.status("connect failed: " + e.getMessage());
            return false;
        }
    }
    protected static void handlePeerConnectionLost(final PeerSession session, PeerConnection.IceConnectionState state) {
        if (session == null || session.stopped) {
            return;
        }
        final String reason = String.valueOf(state);
        Runnable stopTask = new Runnable(){

            @Override
            public void run() {
                if (session.stopped || session.peerConnected) {
                    return;
                }
                WebRtcClient.status("Remote session disconnected: " + reason);
                String remoteId = session.remoteId;
                WebRtcClient.stopSession(remoteId);
            }
        };
        if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
            MAIN.postDelayed(stopTask, 8000L);
        } else {
            MAIN.post(stopTask);
        }
    }
    protected static void sendDescription(PeerSession session, SessionDescription desc) {
        if (session == null || session.stopped || session.peer == null) {
            return;
        }
        try {
            String type = desc.type.canonicalForm();
            JSONObject object = new JSONObject();
            object.put("role", (Object)session.role);
            object.put("type", (Object)type);
            object.put("receiver", (Object)session.remoteId);
            object.put("sender", (Object)config.localId());
            object.put("data", (Object)desc.description);
            boolean sent = SignalingClient.sendText(object.toString());
            WebRtcClient.status(sent ? "SDP " + type + " sent" : "WebSocket is not connected; SDP was not sent");
        }
        catch (Exception e) {
            WebRtcClient.status("send sdp failed: " + e.getMessage());
        }
    }
    protected static void sendCandidate(PeerSession session, IceCandidate candidate) {
        if (session == null || session.stopped || session.peer == null) {
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("role", (Object)session.role);
            object.put("type", (Object)"candidate");
            object.put("receiver", (Object)session.remoteId);
            object.put("sender", (Object)config.localId());
            object.put("data", (Object)candidate.sdp);
            object.put("mid", (Object)candidate.sdpMid);
            if (!SignalingClient.sendText(object.toString())) {
                WebRtcClient.status("WebSocket is not connected; ICE candidate was not sent");
            }
        }
        catch (Exception e) {
            WebRtcClient.status("candidate failed: " + e.getMessage());
        }
    }
    protected static boolean sendDataChannelText(DataChannel dc, JSONObject object) {
        return DataChannelUtils.sendText(dc, object, WebRtcClient::status);
    }
    protected static boolean sendDataChannelText(DataChannel dc, JSONObject object, byte[] bytes) {
        return DataChannelUtils.sendText(dc, bytes, WebRtcClient::status);
    }
    protected static void handleDataChannelMessage(PeerSession session, String label, DataChannel.Buffer buffer) {
        try {
            if (("file_airan".equals(label) || "file_text_airan".equals(label)) && buffer.binary) {
                session.filePacketCodec.onFragment(buffer.data);
                return;
            }
            byte[] bytes = new byte[buffer.data.remaining()];
            buffer.data.get(bytes);
            String text = new String(bytes, "UTF-8");
            if ("file_text_airan".equals(label) || "file_airan".equals(label)) {
                WebRtcClient.handleFileTextMessage(session, new JSONObject(text));
            } else if ("input_airan".equals(label)) {
                WebRtcClient.handleRemoteInput(session, new JSONObject(text));
            }
        }
        catch (Exception e) {
            WebRtcClient.status("channel message failed: " + e.getMessage());
        }
    }
    protected static void handleServerMessage(JSONObject object) {
        String type = object.optString("type");
        if ("deviceIdConflict".equals(type)) {
            String newSessionId;
            JSONObject data = object.optJSONObject("data");
            String string = newSessionId = data == null ? "" : data.optString("newSessionId");
            if (newSessionId.length() == 0) {
                newSessionId = object.optString("newSessionId");
            }
            if (newSessionId.length() == 0) {
                WebRtcClient.status("Server reported duplicate device id but did not provide a replacement id");
                return;
            }
            config.replaceLocalId(newSessionId);
            WebRtcClient.status("Server reported duplicate device id; switched to " + config.localId());
            try {
                SignalingClient.connect(config.buildWsUrl(appContext));
            }
            catch (Exception e) {
                WebRtcClient.status("Reconnect after device id conflict failed: " + e.getMessage());
            }
        } else if ("error".equals(type)) {
            WebRtcClient.status("Server error: " + object.optString("data"));
        }
    }
    protected static PeerSession firstSession() {
        Iterator<PeerSession> iterator = sessions.values().iterator();
        if (iterator.hasNext()) {
            PeerSession session = iterator.next();
            return session;
        }
        return null;
    }
    protected static PeerSession requireActiveSession(String operation) {
        if (activeSession == null) {
            WebRtcClient.status(operation + " failed: no active session");
            return null;
        }
        return activeSession;
    }

}
