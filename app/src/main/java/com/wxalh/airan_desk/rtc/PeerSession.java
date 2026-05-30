package com.wxalh.airan_desk.rtc;

import android.content.Context;
import com.wxalh.airan_desk.input.RemoteAndroidInputHandler;
import com.wxalh.airan_desk.terminal.TerminalSession;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RtpSender;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

final class PeerSession {
    final String remoteId;
    final String role;
    final String remotePwdMd5;
    String mode;
    PeerConnection peer;
    boolean remoteDescriptionSet;
    boolean remoteAnswerSet;
    volatile boolean stopped;
    String lastRemoteDescriptionType = "";
    String lastRemoteDescriptionSdp = "";
    String localOfferFirstVideoCodec = "";
    String localAnswerVideoCodec = "";
    String remoteOfferFirstVideoCodec = "";
    String remoteAnswerVideoCodec = "";
    String negotiatedVideoCodec = "";
    volatile boolean peerConnected;
    final List<IceCandidate> pendingCandidates = new ArrayList<IceCandidate>();
    String pendingRemoteFileListPath = "";
    final Map<String, Long> lastTransferProgressMs = new LinkedHashMap<String, Long>();
    final Map<String, String> transferDisplayPaths = new LinkedHashMap<String, String>();
    final Map<String, String> transferSourcePaths = new LinkedHashMap<String, String>();
    final Map<String, String> transferTargetPaths = new LinkedHashMap<String, String>();
    final Map<String, String> transferFileNames = new LinkedHashMap<String, String>();
    boolean initialKeyframeRequested;
    DataChannel inputChannel;
    DataChannel fileChannel;
    DataChannel fileTextChannel;
    FilePacketCodec filePacketCodec;
    TerminalSession terminalSession;
    VideoCapturer screenCapturer;
    VideoSource videoSource;
    SurfaceTextureHelper surfaceTextureHelper;
    VideoTrack localVideoTrack;
    RtpSender localVideoSender;
    VideoTrack remoteVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    RtpSender localAudioSender;
    AudioTrack remoteAudioTrack;
    VideoTrack lastAttachedRemoteVideoTrack;
    AudioTrack lastAttachedRemoteAudioTrack;
    boolean lastRemoteAudioReceiveEnabled;
    String lastRecvonlyAnswerLog = "";
    String lastRemoteStreamStatusLog = "";
    String remoteRequestedAudioMode = AudioModePolicy.OFF;
    int captureWidth;
    int captureHeight;
    int captureVisibleWidth;
    int captureVisibleHeight;
    int capturePadLeft;
    int capturePadTop;
    int capturePadRight;
    int capturePadBottom;
    int captureFps = 25;
    boolean pendingOfferAfterScreenPermission;
    boolean screenCaptureOfferStarting;
    boolean screenCaptureStopped;
    boolean terminalStartPending;
    boolean terminalStartRequested;
    boolean terminalOutputSeen;
    long terminalStartRequestedMs;
    final RemoteAndroidInputHandler.PointerState inputPointerState = new RemoteAndroidInputHandler.PointerState();
    final RemoteAndroidInputHandler.AccessibilityWarning inputAccessibilityWarning = new RemoteAndroidInputHandler.AccessibilityWarning();
    boolean statsPollingRunning;
    boolean inboundVideoStatsSeen;
    long lastInboundVideoBytes;
    long lastInboundVideoPackets;
    long lastInboundVideoFramesDecoded;
    long lastInboundVideoPacketsLost;
    long lastInboundVideoFramesDropped;
    long lastInboundVideoNackCount;
    long lastInboundVideoPliCount;
    long lastInboundVideoPacketProgressMs;
    long lastInboundVideoStatsMs;
    double inboundArrivalKbpsEwma;
    double inboundArrivalKbpsVar;
    double inboundJitterMsEwma;
    double inboundJitterMsVar;
    double inboundLossRateEwma;
    double inboundLossRateVar;
    double inboundDecodeGapEwma;
    double inboundDecodeGapVar;
    int baseCaptureFps;
    int videoAdaptLevel;
    int stableVideoFeedbacks;
    double feedbackArrivalKbpsEwma;
    double feedbackArrivalKbpsVar;
    double feedbackJitterMsEwma;
    double feedbackJitterMsVar;
    double feedbackDecodeQueueEwma;
    double feedbackDecodeQueueVar;
    double feedbackLossRateEwma;
    double feedbackLossRateVar;
    int stagnantInboundVideoPolls;
    long lastVideoRecoveryMs;
    String lastOutboundVideoEncoder = "";
    long lastOutboundVideoBytes;
    long lastOutboundVideoFramesEncoded;
    long lastOutboundVideoHugeFrames;
    long lastOutboundVideoWidth;
    long lastOutboundVideoHeight;
    long lastOutboundResolutionRestoreMs;
    boolean controlHeartbeatRunning;
    long lastKeyframeRequestMs;
    final Runnable statsPollRunnable = new Runnable(){

        @Override
        public void run() {
            if (!statsPollingRunning || peer == null || !peerConnected) {
                statsPollingRunning = false;
                return;
            }
            WebRtcClient.pollRtcStats(PeerSession.this);
            WebRtcClient.postDelayed(this, 4000L);
        }
    };
    final Runnable controlHeartbeatRunnable = new Runnable(){

        @Override
        public void run() {
            if (!(controlHeartbeatRunning && "ctl".equals(role) && "desktop".equals(mode) && inputChannel != null && inputChannel.state() == DataChannel.State.OPEN)) {
                controlHeartbeatRunning = false;
                return;
            }
            WebRtcClient.sendControlHeartbeat(PeerSession.this);
            WebRtcClient.postDelayed(this, 3000L);
        }
    };

    PeerSession(Context context, String remoteId, String role, String remotePwdMd5, String mode) {
        this.remoteId = remoteId == null ? "" : remoteId;
        this.role = role == null ? "" : role;
        this.remotePwdMd5 = remotePwdMd5 == null ? "" : remotePwdMd5;
        this.mode = mode == null || mode.length() == 0 ? "desktop" : mode;
        this.filePacketCodec = new FilePacketCodec(context, new FilePacketCodec.Listener(){

            @Override
            public void onPacket(JSONObject header, File payloadFile) {
                WebRtcClient.handleFilePacket(PeerSession.this, header, payloadFile);
            }

            @Override
            public void onSendProgress(JSONObject header, long sentBytes, long totalBytes) {
                WebRtcClient.notifyFileSendProgress(PeerSession.this, header, sentBytes, totalBytes);
            }

            @Override
            public void onReceiveProgress(JSONObject header, long receivedBytes, long totalBytes, boolean done) {
                WebRtcClient.notifyFileReceiveProgress(PeerSession.this, header, receivedBytes, totalBytes, done);
            }

            @Override
            public void onError(String message) {
                WebRtcClient.status(message);
            }
        });
    }
}
