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
abstract class WebRtcStatsOps
extends WebRtcMediaOps {
    protected static void startStatsPolling(PeerSession session) {
        if (session.statsPollingRunning) {
            return;
        }
        session.statsPollingRunning = true;
        MAIN.postDelayed(session.statsPollRunnable, 1500L);
    }
    protected static void stopStatsPolling(PeerSession session) {
        session.statsPollingRunning = false;
        MAIN.removeCallbacks(session.statsPollRunnable);
    }
    static void pollRtcStats(final PeerSession session) {
        PeerConnection currentPeer = session.peer;
        if (currentPeer == null) {
            return;
        }
        currentPeer.getStats(new RTCStatsCollectorCallback(){

            public void onStatsDelivered(RTCStatsReport report) {
                WebRtcClient.logInboundStats(session, report);
                WebRtcClient.logOutboundStats(session, report);
            }
        });
    }
    protected static void logOutboundStats(PeerSession session, RTCStatsReport report) {
        if (session == null || report == null || report.getStatsMap() == null || !"cli".equals(session.role)) {
            return;
        }
        String video = "";
        String encoder = "";
        long bytesSent = -1L;
        long framesEncoded = -1L;
        long hugeFramesSent = -1L;
        String qualityLimit = "";
        for (RTCStats stat : report.getStatsMap().values()) {
            if (stat == null || !"outbound-rtp".equals(stat.getType()) || stat.getMembers() == null) continue;
            Map members = stat.getMembers();
            String kind = RtcStatsUtils.stringMember(members, "kind");
            if (kind.length() == 0) {
                kind = RtcStatsUtils.stringMember(members, "mediaType");
            }
            if (!"video".equals(kind)) continue;
            encoder = RtcStatsUtils.stringMember(members, "encoderImplementation");
            bytesSent = RtcStatsUtils.longMember(members, "bytesSent");
            framesEncoded = RtcStatsUtils.longMember(members, "framesEncoded");
            hugeFramesSent = RtcStatsUtils.longMember(members, "hugeFramesSent");
            long encodedWidth = RtcStatsUtils.longMember(members, "frameWidth");
            long encodedHeight = RtcStatsUtils.longMember(members, "frameHeight");
            if (encodedWidth <= 0L) {
                encodedWidth = RtcStatsUtils.longMember(members, "width");
            }
            if (encodedHeight <= 0L) {
                encodedHeight = RtcStatsUtils.longMember(members, "height");
            }
            session.lastOutboundVideoWidth = encodedWidth;
            session.lastOutboundVideoHeight = encodedHeight;
            qualityLimit = RtcStatsUtils.stringMember(members, "qualityLimitationReason");
            video = "bytes=" + bytesSent + " packets=" + RtcStatsUtils.longMember(members, "packetsSent") + " framesEncoded=" + framesEncoded + " keyFramesEncoded=" + RtcStatsUtils.longMember(members, "keyFramesEncoded") + " hugeFramesSent=" + hugeFramesSent + " encoded=" + encodedWidth + "x" + encodedHeight + " qpSum=" + RtcStatsUtils.longMember(members, "qpSum") + " encoder=" + encoder + " qualityLimit=" + qualityLimit;
        }
        if (video.length() == 0) {
            return;
        }
        Log.i((String)TAG, (String)("RTC outbound stats video[" + video + "]"));
        if (encoder.length() > 0 && !encoder.equals(session.lastOutboundVideoEncoder)) {
            session.lastOutboundVideoEncoder = encoder;
            WebRtcClient.status("Android video encoder: " + encoder);
            if (session.inputChannel != null && session.inputChannel.state() == DataChannel.State.OPEN) {
                WebRtcClient.sendStreamConfig(session);
            }
        }
        if (bytesSent >= 0L && framesEncoded >= 0L && hugeFramesSent >= 0L) {
            WebRtcClient.ensureOutboundResolutionLocked(session);
            WebRtcClient.maybeAdaptOutboundVideo(session, bytesSent, framesEncoded, hugeFramesSent, qualityLimit);
        }
    }
    protected static void ensureOutboundResolutionLocked(PeerSession session) {
        if (session == null || session.stopped || sharedScreenCapturer == null || sharedScreenCaptureStopped) {
            return;
        }
        long encodedWidth = session.lastOutboundVideoWidth;
        long encodedHeight = session.lastOutboundVideoHeight;
        if (encodedWidth <= 0L || encodedHeight <= 0L || session.captureWidth <= 0 || session.captureHeight <= 0) {
            return;
        }
        int encodedLong = (int)Math.max(encodedWidth, encodedHeight);
        int captureLong = Math.max(session.captureWidth, session.captureHeight);
        if (encodedLong * 100 >= captureLong * 90) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - session.lastOutboundResolutionRestoreMs < 6000L) {
            return;
        }
        session.lastOutboundResolutionRestoreMs = now;
        try {
            WebRtcClient.applyVideoSenderParameters(session);
            WebRtcClient.status("Android outbound resolution lock reapplied: encoded=" + encodedWidth + "x" + encodedHeight + " coded=" + session.captureWidth + "x" + session.captureHeight + " visible=" + session.captureVisibleWidth + "x" + session.captureVisibleHeight);
        }
        catch (Exception e) {
            WebRtcClient.status("Android outbound resolution restore failed: " + e.getMessage());
        }
    }
    protected static void maybeAdaptOutboundVideo(PeerSession session, long bytesSent, long framesEncoded, long hugeFramesSent, String qualityLimit) {
        if (session == null || !"cli".equals(session.role) || !"desktop".equals(session.mode)) {
            return;
        }
        if (session.lastOutboundVideoFramesEncoded <= 0L) {
            session.lastOutboundVideoBytes = bytesSent;
            session.lastOutboundVideoFramesEncoded = framesEncoded;
            session.lastOutboundVideoHugeFrames = hugeFramesSent;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - session.lastOutboundVideoAdaptMs < 8000L) {
            session.lastOutboundVideoBytes = bytesSent;
            session.lastOutboundVideoFramesEncoded = framesEncoded;
            session.lastOutboundVideoHugeFrames = hugeFramesSent;
            return;
        }
        long deltaFrames = Math.max(0L, framesEncoded - session.lastOutboundVideoFramesEncoded);
        long deltaHuge = Math.max(0L, hugeFramesSent - session.lastOutboundVideoHugeFrames);
        boolean limited = qualityLimit != null && qualityLimit.length() > 0 && !"none".equalsIgnoreCase(qualityLimit);
        boolean tooManyHugeFrames = deltaFrames >= 5L && deltaHuge * 3L >= deltaFrames;
        if ((limited || tooManyHugeFrames) && deltaFrames > 0L) {
            if (session.baseCaptureFps <= 0) {
                session.baseCaptureFps = streamFps;
            }
            if (session.baseBitrateProfile == null || session.baseBitrateProfile.length() == 0) {
                session.baseBitrateProfile = bitrateProfile;
            }
            if (session.videoAdaptLevel < 4) {
                ++session.videoAdaptLevel;
                session.stableVideoFeedbacks = 0;
                session.lastVideoAdaptApplyMs = now;
                session.lastOutboundVideoAdaptMs = now;
                WebRtcClient.applyVideoAdaptation(session);
                WebRtcClient.status("Android outbound video limited, adapt level " + session.videoAdaptLevel + " reason=" + qualityLimit);
            }
        }
        session.lastOutboundVideoBytes = bytesSent;
        session.lastOutboundVideoFramesEncoded = framesEncoded;
        session.lastOutboundVideoHugeFrames = hugeFramesSent;
    }
    protected static void logInboundStats(PeerSession session, RTCStatsReport report) {
        if (report == null || report.getStatsMap() == null) {
            return;
        }
        String video = "";
        String audio = "";
        long videoBytes = -1L;
        long videoPackets = -1L;
        long videoFramesDecoded = -1L;
        long videoPacketsLost = -1L;
        long videoFramesDropped = -1L;
        long videoNackCount = -1L;
        long videoPliCount = -1L;
        long videoJitterMs = -1L;
        long candidateRttMs = -1L;
        for (RTCStats stat : report.getStatsMap().values()) {
            if (stat == null || stat.getMembers() == null) continue;
            if ("candidate-pair".equals(stat.getType())) {
                Map candidateMembers = stat.getMembers();
                String state = RtcStatsUtils.stringMember(candidateMembers, "state");
                if ("succeeded".equals(state) || RtcStatsUtils.longMember(candidateMembers, "nominated") > 0L) {
                    double rttSeconds = RtcStatsUtils.doubleMember(candidateMembers, "currentRoundTripTime");
                    if (rttSeconds > 0.0) {
                        candidateRttMs = Math.round(rttSeconds * 1000.0);
                    }
                }
                continue;
            }
            if (!"inbound-rtp".equals(stat.getType())) continue;
            Map members = stat.getMembers();
            String kind = RtcStatsUtils.stringMember(members, "kind");
            if (kind.length() == 0) {
                kind = RtcStatsUtils.stringMember(members, "mediaType");
            }
            String summary = "bytes=" + RtcStatsUtils.longMember(members, "bytesReceived") + " packets=" + RtcStatsUtils.longMember(members, "packetsReceived") + " lost=" + RtcStatsUtils.longMember(members, "packetsLost") + " framesDecoded=" + RtcStatsUtils.longMember(members, "framesDecoded") + " framesReceived=" + RtcStatsUtils.longMember(members, "framesReceived") + " keyFramesDecoded=" + RtcStatsUtils.longMember(members, "keyFramesDecoded") + " framesDropped=" + RtcStatsUtils.longMember(members, "framesDropped") + " decoder=" + RtcStatsUtils.stringMember(members, "decoderImplementation") + " pli=" + RtcStatsUtils.longMember(members, "pliCount") + " fir=" + RtcStatsUtils.longMember(members, "firCount") + " nack=" + RtcStatsUtils.longMember(members, "nackCount");
            if ("video".equals(kind)) {
                video = summary;
                videoBytes = RtcStatsUtils.longMember(members, "bytesReceived");
                videoPackets = RtcStatsUtils.longMember(members, "packetsReceived");
                videoFramesDecoded = RtcStatsUtils.longMember(members, "framesDecoded");
                videoPacketsLost = RtcStatsUtils.longMember(members, "packetsLost");
                videoFramesDropped = RtcStatsUtils.longMember(members, "framesDropped");
                videoNackCount = RtcStatsUtils.longMember(members, "nackCount");
                videoPliCount = RtcStatsUtils.longMember(members, "pliCount");
                double jitterSeconds = RtcStatsUtils.doubleMember(members, "jitter");
                if (jitterSeconds > 0.0) {
                    videoJitterMs = Math.round(jitterSeconds * 1000.0);
                }
                continue;
            }
            if (!"audio".equals(kind)) continue;
            audio = summary;
        }
        Log.i((String)TAG, (String)("RTC inbound stats video[" + video + "] audio[" + audio + "]"));
        if (videoBytes >= 0L && videoPackets >= 0L && videoFramesDecoded >= 0L) {
            WebRtcClient.handleInboundVideoStats(session, videoBytes, videoPackets, videoFramesDecoded, videoPacketsLost, videoFramesDropped, videoNackCount, videoPliCount, candidateRttMs, videoJitterMs);
        }
    }
    protected static void handleInboundVideoStats(PeerSession session, long bytes, long packets, long framesDecoded, long packetsLost, long framesDropped, long nackCount, long pliCount, long rttMs, long jitterMs) {
        boolean decodingFrames;
        if (!"ctl".equals(session.role) || !"desktop".equals(session.mode)) {
            WebRtcClient.rememberInboundVideoStats(session, bytes, packets, framesDecoded, packetsLost, framesDropped, nackCount, pliCount);
            return;
        }
        if (!session.inboundVideoStatsSeen) {
            WebRtcClient.rememberInboundVideoStats(session, bytes, packets, framesDecoded, packetsLost, framesDropped, nackCount, pliCount);
            return;
        }
        long now = System.currentTimeMillis();
        boolean receivingPackets = packets > session.lastInboundVideoPackets || bytes > session.lastInboundVideoBytes;
        if (receivingPackets) {
            session.lastInboundVideoPacketProgressMs = now;
        }
        boolean recentPacketProgress = session.lastInboundVideoPacketProgressMs > 0L && now - session.lastInboundVideoPacketProgressMs < 9000L;
        boolean bl = decodingFrames = framesDecoded > session.lastInboundVideoFramesDecoded;
        WebRtcClient.maybeSendVideoAdaptFeedback(session, bytes, packets, framesDecoded, packetsLost, framesDropped, nackCount, pliCount, rttMs, jitterMs, receivingPackets, decodingFrames);
        if (decodingFrames) {
            session.stagnantInboundVideoPolls = 0;
            session.keyframeRequestBackoffMs = KEYFRAME_REQUEST_MIN_INTERVAL_MS;
        } else if (receivingPackets || recentPacketProgress) {
            ++session.stagnantInboundVideoPolls;
            if (session.stagnantInboundVideoPolls >= 2) {
                WebRtcClient.recoverFrozenInboundVideo(session, receivingPackets || recentPacketProgress, framesDecoded);
            }
        } else {
            session.stagnantInboundVideoPolls = 0;
        }
        WebRtcClient.rememberInboundVideoStats(session, bytes, packets, framesDecoded, packetsLost, framesDropped, nackCount, pliCount);
    }
    protected static void rememberInboundVideoStats(PeerSession session, long bytes, long packets, long framesDecoded, long packetsLost, long framesDropped, long nackCount, long pliCount) {
        session.inboundVideoStatsSeen = true;
        session.lastInboundVideoStatsMs = System.currentTimeMillis();
        session.lastInboundVideoBytes = bytes;
        session.lastInboundVideoPackets = packets;
        session.lastInboundVideoFramesDecoded = framesDecoded;
        session.lastInboundVideoPacketsLost = Math.max(0L, packetsLost);
        session.lastInboundVideoFramesDropped = Math.max(0L, framesDropped);
        session.lastInboundVideoNackCount = Math.max(0L, nackCount);
        session.lastInboundVideoPliCount = Math.max(0L, pliCount);
    }
    protected static void updateEwma(double sample, double[] state, double alpha) {
        if (sample < 0.0) {
            return;
        }
        if (state[0] <= 0.0) {
            state[0] = sample;
            state[1] = 0.0;
            return;
        }
        double diff = sample - state[0];
        state[0] += alpha * diff;
        state[1] = (1.0 - alpha) * (state[1] + alpha * diff * diff);
    }
    protected static double ewmaStd(double variance) {
        return Math.sqrt(Math.max(0.0, variance));
    }
    protected static void maybeSendVideoAdaptFeedback(PeerSession session, long bytes, long packets, long framesDecoded, long packetsLost, long framesDropped, long nackCount, long pliCount, long rttMs, long jitterMs, boolean receivingPackets, boolean decodingFrames) {
        if (!DataChannelUtils.isOpen(session.inputChannel)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - session.lastVideoAdaptFeedbackMs < VIDEO_ADAPT_FEEDBACK_MIN_INTERVAL_MS) {
            return;
        }
        long deltaBytes = Math.max(0L, bytes - session.lastInboundVideoBytes);
        long deltaPackets = Math.max(0L, packets - session.lastInboundVideoPackets);
        long safePacketsLost = Math.max(0L, packetsLost);
        long deltaLost = Math.max(0L, safePacketsLost - session.lastInboundVideoPacketsLost);
        long deltaFramesDecoded = Math.max(0L, framesDecoded - session.lastInboundVideoFramesDecoded);
        long safeFramesDropped = Math.max(0L, framesDropped);
        long deltaFramesDropped = Math.max(0L, safeFramesDropped - session.lastInboundVideoFramesDropped);
        long safeNackCount = Math.max(0L, nackCount);
        long deltaNack = Math.max(0L, safeNackCount - session.lastInboundVideoNackCount);
        long safePliCount = Math.max(0L, pliCount);
        long deltaPli = Math.max(0L, safePliCount - session.lastInboundVideoPliCount);
        boolean stalled = receivingPackets && !decodingFrames;
        long statsSpanMs = session.lastInboundVideoStatsMs > 0L ? Math.max(1L, now - session.lastInboundVideoStatsMs) : 4000L;
        long arrivalBitrateKbps = deltaBytes > 0L ? deltaBytes * 8L / statsSpanMs : 0L;
        double lossRate = deltaLost > 0L || deltaPackets > 0L ? (double)deltaLost / (double)Math.max(1L, deltaPackets + deltaLost) : 0.0;
        long decodeGap = decodingFrames && deltaFramesDecoded > 0L ? statsSpanMs / Math.max(1L, deltaFramesDecoded) : statsSpanMs;
        double[] arrivalState = new double[]{session.inboundArrivalKbpsEwma, session.inboundArrivalKbpsVar};
        double[] jitterState = new double[]{session.inboundJitterMsEwma, session.inboundJitterMsVar};
        double[] lossState = new double[]{session.inboundLossRateEwma, session.inboundLossRateVar};
        double[] decodeState = new double[]{session.inboundDecodeGapEwma, session.inboundDecodeGapVar};
        WebRtcClient.updateEwma((double)Math.max(0L, arrivalBitrateKbps), arrivalState, 0.18);
        WebRtcClient.updateEwma((double)Math.max(0L, jitterMs), jitterState, 0.18);
        WebRtcClient.updateEwma(lossRate, lossState, 0.18);
        WebRtcClient.updateEwma((double)Math.max(1L, decodeGap), decodeState, 0.18);
        session.inboundArrivalKbpsEwma = arrivalState[0];
        session.inboundArrivalKbpsVar = arrivalState[1];
        session.inboundJitterMsEwma = jitterState[0];
        session.inboundJitterMsVar = jitterState[1];
        session.inboundLossRateEwma = lossState[0];
        session.inboundLossRateVar = lossState[1];
        session.inboundDecodeGapEwma = decodeState[0];
        session.inboundDecodeGapVar = decodeState[1];
        boolean dynamicLossCongested = lossRate > Math.max(0.06, session.inboundLossRateEwma + WebRtcClient.ewmaStd(session.inboundLossRateVar) * 2.5);
        boolean dynamicJitterCongested = jitterMs > Math.max(180.0, session.inboundJitterMsEwma + WebRtcClient.ewmaStd(session.inboundJitterMsVar) * 2.5);
        boolean dynamicArrivalStarved = deltaBytes > 0L && arrivalBitrateKbps > 0L && arrivalBitrateKbps < session.inboundArrivalKbpsEwma * 0.70;
        boolean networkCongested = dynamicLossCongested || dynamicJitterCongested || dynamicArrivalStarved || deltaFramesDropped > 2L || deltaNack > 2L;
        boolean decoderStalled = stalled && !networkCongested;
        String feedbackType = networkCongested ? "network_congestion" : (decoderStalled ? "decoder_stall" : "stable");
        boolean congested = networkCongested;
        if (!congested && now - session.lastVideoAdaptFeedbackMs < VIDEO_ADAPT_FEEDBACK_MIN_INTERVAL_MS * 3L) {
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("msgType", (Object)AiranConstants.TYPE_VIDEO_ADAPT_FEEDBACK);
            object.put("sender", (Object)config.localId());
            object.put("receiver", (Object)session.remoteId);
            object.put("receiver_pwd", (Object)session.remotePwdMd5);
            object.put("networkCongested", networkCongested);
            object.put("decoderStalled", decoderStalled);
            object.put("senderFault", false);
            object.put("feedbackType", (Object)feedbackType);
            object.put("receivingPackets", receivingPackets);
            object.put("decodingFrames", decodingFrames);
            object.put("deltaBytes", deltaBytes);
            object.put("deltaPackets", deltaPackets);
            object.put("deltaLost", deltaLost);
            object.put("deltaFramesDecoded", deltaFramesDecoded);
            object.put("deltaFramesDropped", deltaFramesDropped);
            object.put("deltaNack", deltaNack);
            object.put("deltaPli", deltaPli);
            object.put("rttMs", Math.max(0L, rttMs));
            object.put("jitterMs", Math.max(0L, jitterMs));
            object.put("arrivalFrames", deltaFramesDecoded);
            object.put("arrivalBytes", deltaBytes);
            object.put("arrivalSpanMs", statsSpanMs);
            object.put("arrivalBitrateKbps", arrivalBitrateKbps);
            object.put("interArrivalAvgMs", decodeGap);
            object.put("interArrivalJitterMs", Math.max(0L, jitterMs));
            if (WebRtcClient.sendInput(session, object)) {
                session.lastVideoAdaptFeedbackMs = now;
            }
        }
        catch (Exception e) {
            WebRtcClient.status("video adapt feedback failed: " + e.getMessage());
        }
    }
    protected static void recoverFrozenInboundVideo(final PeerSession session, final boolean receivingPackets, final long framesDecoded) {
        long now = System.currentTimeMillis();
        if (now - session.lastVideoRecoveryMs < 10000L) {
            return;
        }
        session.lastVideoRecoveryMs = now;
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                WebRtcClient.status(receivingPackets ? "video decoder stalled at frame " + framesDecoded + ", requesting keyframe" : "video stream stalled at frame " + framesDecoded + ", requesting refresh");
                WebRtcClient.requestKeyframe(session, receivingPackets ? "decoder-stalled" : "stream-stalled");
                if (receivingPackets && renderer != null && session.remoteVideoTrack != null) {
                    WebRtcClient.attachRemoteVideoToRenderer(session);
                }
            }
        });
    }
    protected static void handleVideoAdaptFeedback(PeerSession session, JSONObject object) {
        if (session == null || session.stopped || session.peer == null || !"cli".equals(session.role) || !"desktop".equals(session.mode)) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean networkCongested = object.optBoolean("networkCongested", false);
        boolean decoderStalled = object.optBoolean("decoderStalled", false);
        boolean stalled = networkCongested || decoderStalled;
        boolean senderFault = object.optBoolean("senderFault", false);
        long deltaLost = Math.max(0L, object.optLong("deltaLost", 0L));
        long deltaPackets = Math.max(0L, object.optLong("deltaPackets", 0L));
        long deltaFramesDropped = Math.max(0L, object.optLong("deltaFramesDropped", 0L));
        long deltaNack = Math.max(0L, object.optLong("deltaNack", 0L));
        int arrivalBitrateKbps = Math.max(0, object.optInt("arrivalBitrateKbps", 0));
        int interArrivalJitterMs = Math.max(object.optInt("jitterMs", 0), object.optInt("interArrivalJitterMs", 0));
        int decodeQueue = Math.max(0, object.optInt("decodeQueue", 0));
        int arrivalFrames = Math.max(0, object.optInt("arrivalFrames", 0));
        int rttMs = Math.max(0, object.optInt("rttMs", 0));
        double lossRate = deltaLost > 0L || deltaPackets > 0L ? (double)deltaLost / (double)Math.max(1L, deltaPackets + deltaLost) : 0.0;
        double[] arrivalState = new double[]{session.feedbackArrivalKbpsEwma, session.feedbackArrivalKbpsVar};
        double[] jitterState = new double[]{session.feedbackJitterMsEwma, session.feedbackJitterMsVar};
        double[] queueState = new double[]{session.feedbackDecodeQueueEwma, session.feedbackDecodeQueueVar};
        double[] lossState = new double[]{session.feedbackLossRateEwma, session.feedbackLossRateVar};
        WebRtcClient.updateEwma((double)arrivalBitrateKbps, arrivalState, 0.18);
        WebRtcClient.updateEwma((double)interArrivalJitterMs, jitterState, 0.18);
        WebRtcClient.updateEwma((double)decodeQueue, queueState, 0.18);
        WebRtcClient.updateEwma(lossRate, lossState, 0.18);
        session.feedbackArrivalKbpsEwma = arrivalState[0];
        session.feedbackArrivalKbpsVar = arrivalState[1];
        session.feedbackJitterMsEwma = jitterState[0];
        session.feedbackJitterMsVar = jitterState[1];
        session.feedbackDecodeQueueEwma = queueState[0];
        session.feedbackDecodeQueueVar = queueState[1];
        session.feedbackLossRateEwma = lossState[0];
        session.feedbackLossRateVar = lossState[1];
        boolean dynamicArrivalStarved = arrivalFrames > 0 && arrivalBitrateKbps > 0 && arrivalBitrateKbps < session.feedbackArrivalKbpsEwma * 0.70;
        boolean dynamicJitterCongested = interArrivalJitterMs > Math.max(180.0, session.feedbackJitterMsEwma + WebRtcClient.ewmaStd(session.feedbackJitterMsVar) * 2.5);
        boolean dynamicQueueCongested = decodeQueue > Math.max(3.0, session.feedbackDecodeQueueEwma + WebRtcClient.ewmaStd(session.feedbackDecodeQueueVar) * 2.5);
        boolean dynamicLossCongested = lossRate > Math.max(0.06, session.feedbackLossRateEwma + WebRtcClient.ewmaStd(session.feedbackLossRateVar) * 2.5);
        boolean actualNetworkCongested = networkCongested || dynamicArrivalStarved || dynamicJitterCongested || dynamicQueueCongested || dynamicLossCongested;
        boolean cleanDecoderStall = decoderStalled && !actualNetworkCongested && deltaLost == 0L && deltaFramesDropped == 0L && deltaNack == 0L && interArrivalJitterMs < 80 && (rttMs <= 0 || rttMs < 120);
        if (senderFault || cleanDecoderStall) {
            session.stableVideoFeedbacks = 0;
            return;
        }
        if (session.baseCaptureFps <= 0) {
            session.baseCaptureFps = streamFps;
        }
        if (session.baseBitrateProfile == null || session.baseBitrateProfile.length() == 0) {
            session.baseBitrateProfile = bitrateProfile;
        }
        if (actualNetworkCongested) {
            session.stableVideoFeedbacks = 0;
            if (now - session.lastVideoAdaptApplyMs < 5000L && !stalled) {
                return;
            }
            int nextLevel = Math.min(4, session.videoAdaptLevel + (stalled && networkCongested ? 2 : 1));
            if (nextLevel != session.videoAdaptLevel) {
                session.videoAdaptLevel = nextLevel;
                session.lastVideoAdaptApplyMs = now;
                WebRtcClient.applyVideoAdaptation(session);
                WebRtcClient.status("video adapt level " + session.videoAdaptLevel + (stalled ? " stalled" : " congested"));
            }
            return;
        }
        if (session.videoAdaptLevel <= 0) {
            return;
        }
        ++session.stableVideoFeedbacks;
        if (session.stableVideoFeedbacks >= 2 && now - session.lastVideoAdaptApplyMs >= 8000L) {
            --session.videoAdaptLevel;
            session.stableVideoFeedbacks = 0;
            session.lastVideoAdaptApplyMs = now;
            WebRtcClient.applyVideoAdaptation(session);
            WebRtcClient.status("video adapt recovered to level " + session.videoAdaptLevel);
        }
    }
    protected static void applyVideoAdaptation(PeerSession session) {
        if (session == null || session.stopped || session.peer == null) {
            return;
        }
        String targetProfile = session.baseBitrateProfile == null || session.baseBitrateProfile.length() == 0 ? DEFAULT_BITRATE_PROFILE : session.baseBitrateProfile;
        int targetFps = Math.max(5, Math.min(60, session.baseCaptureFps > 0 ? session.baseCaptureFps : streamFps));
        if (session.videoAdaptLevel >= 1) {
            targetProfile = "low";
            targetFps = Math.max(16, targetFps * 4 / 5);
        }
        if (session.videoAdaptLevel >= 2) {
            targetFps = Math.max(10, targetFps * 2 / 3);
        }
        if (session.videoAdaptLevel >= 3) {
            targetFps = Math.max(8, targetFps / 2);
        }
        if (session.videoAdaptLevel >= 4) {
            targetFps = 5;
        }
        boolean changed = false;
        if (!targetProfile.equals(bitrateProfile)) {
            bitrateProfile = targetProfile;
            changed = true;
        }
        if (targetFps != streamFps || targetFps != session.captureFps) {
            streamFps = targetFps;
            session.captureFps = targetFps;
            changed = true;
        }
        if (!changed) {
            return;
        }
        WebRtcClient.applyVideoSenderParameters(session);
        WebRtcClient.sendStreamConfig(session);
    }

}
