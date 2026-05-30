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

    private static Map<String, String> codecMimeById(RTCStatsReport report) {
        LinkedHashMap<String, String> codecs = new LinkedHashMap<String, String>();
        if (report == null || report.getStatsMap() == null) {
            return codecs;
        }
        for (RTCStats stat : report.getStatsMap().values()) {
            if (stat == null || stat.getMembers() == null || !"codec".equals(stat.getType())) {
                continue;
            }
            String mime = RtcStatsUtils.stringMember(stat.getMembers(), "mimeType");
            if (mime.length() > 0) {
                codecs.put(stat.getId(), normalizeVideoCodec(mime));
            }
        }
        return codecs;
    }

    private static String normalizeVideoCodec(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        String codec = mimeType.trim();
        int slash = codec.indexOf('/');
        if (slash >= 0 && slash + 1 < codec.length()) {
            codec = codec.substring(slash + 1);
        }
        int semicolon = codec.indexOf(';');
        if (semicolon >= 0) {
            codec = codec.substring(0, semicolon);
        }
        return codec.trim().toUpperCase(Locale.US);
    }

    protected static void logOutboundStats(PeerSession session, RTCStatsReport report) {
        if (session == null || report == null || report.getStatsMap() == null || !"cli".equals(session.role)) {
            return;
        }
        Map<String, String> codecById = codecMimeById(report);
        String video = "";
        String encoder = "";
        String negotiatedCodec = "";
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
            String codecId = RtcStatsUtils.stringMember(members, "codecId");
            if (codecId.length() > 0 && codecById.containsKey(codecId)) {
                negotiatedCodec = codecById.get(codecId);
            }
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
            video = "codec=" + negotiatedCodec + " bytes=" + bytesSent + " packets=" + RtcStatsUtils.longMember(members, "packetsSent") + " framesEncoded=" + framesEncoded + " keyFramesEncoded=" + RtcStatsUtils.longMember(members, "keyFramesEncoded") + " hugeFramesSent=" + hugeFramesSent + " encoded=" + encodedWidth + "x" + encodedHeight + " qpSum=" + RtcStatsUtils.longMember(members, "qpSum") + " encoder=" + encoder + " qualityLimit=" + qualityLimit;
        }
        if (video.length() == 0) {
            return;
        }
        Log.i((String)TAG, (String)("RTC outbound stats video[" + video + "]"));
        if (negotiatedCodec.length() > 0 && !negotiatedCodec.equals(session.negotiatedVideoCodec)) {
            session.negotiatedVideoCodec = negotiatedCodec;
            WebRtcClient.status("Negotiated outbound video codec: " + negotiatedCodec);
            if (session.inputChannel != null && session.inputChannel.state() == DataChannel.State.OPEN) {
                WebRtcClient.sendStreamConfig(session);
            }
        }
        if (encoder.length() > 0 && !encoder.equals(session.lastOutboundVideoEncoder)) {
            session.lastOutboundVideoEncoder = encoder;
            WebRtcClient.status("Android video encoder: " + encoder);
            if (session.inputChannel != null && session.inputChannel.state() == DataChannel.State.OPEN) {
                WebRtcClient.sendStreamConfig(session);
            }
        }
        if (bytesSent >= 0L && framesEncoded >= 0L && hugeFramesSent >= 0L) {
        }
    }
    protected static void logInboundStats(PeerSession session, RTCStatsReport report) {
        if (report == null || report.getStatsMap() == null) {
            return;
        }
        Map<String, String> codecById = codecMimeById(report);
        String video = "";
        String audio = "";
        String inboundVideoCodec = "";
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
                String codecId = RtcStatsUtils.stringMember(members, "codecId");
                if (codecId.length() > 0 && codecById.containsKey(codecId)) {
                    inboundVideoCodec = codecById.get(codecId);
                }
                video = "codec=" + inboundVideoCodec + " " + summary;
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
        if (session != null && inboundVideoCodec.length() > 0 && !inboundVideoCodec.equals(session.negotiatedVideoCodec)) {
            session.negotiatedVideoCodec = inboundVideoCodec;
            WebRtcClient.status("Negotiated inbound video codec: " + inboundVideoCodec);
        }
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
        decodingFrames = framesDecoded > session.lastInboundVideoFramesDecoded;
        session.stagnantInboundVideoPolls = decodingFrames || !receivingPackets ? 0 : session.stagnantInboundVideoPolls + 1;
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
}
