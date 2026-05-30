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
abstract class WebRtcLifecycleOps
extends WebRtcStatsOps {
    protected static void stopPeer(PeerSession session) {
        if (session == null) {
            return;
        }
        session.stopped = true;
        try {
            boolean hadScreenCapturer = session.screenCapturer != null;
            WebRtcClient.closeDataChannel(session.inputChannel);
            WebRtcClient.closeDataChannel(session.fileChannel);
            WebRtcClient.closeDataChannel(session.fileTextChannel);
            if (session.filePacketCodec != null) {
                session.filePacketCodec.cancelAllReassemblies();
            }
            if (session.terminalSession != null) {
                session.terminalSession.stop();
                session.terminalSession = null;
            }
            if (session.peer != null) {
                try {
                    session.peer.close();
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            WebRtcClient.disposeScreenCaptureResources(session, true);
            WebRtcClient.disposeLocalAudioResources(session);
            if (session.remoteVideoTrack != null && renderer != null) {
                try {
                    session.remoteVideoTrack.removeSink(renderer);
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            session.remoteVideoTrack = null;
            WebRtcClient.setRemoteAudioTrackEnabled(session, false);
            session.remoteAudioTrack = null;
            if (hadScreenCapturer && !WebRtcClient.hasConnectedControlledDesktopSession()) {
                /* Foreground service stays alive for the idle grace window so a quick
                 * reconnect within 60s can rebind to the shared capturer without
                 * re-prompting the user for MediaProjection permission. The grace
                 * runnable scheduled by disposeScreenCaptureResources() will stop the
                 * service if no session reconnects in time. */
                lastSessionEndedMs = System.currentTimeMillis();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        session.inputChannel = null;
        session.fileChannel = null;
        session.fileTextChannel = null;
        WebRtcClient.stopControlHeartbeat(session);
        WebRtcClient.stopStatsPolling(session);
        session.peerConnected = false;
        WebRtcClient.updateRemoteScreenWakeLock();
        session.inboundVideoStatsSeen = false;
        session.lastInboundVideoBytes = 0L;
        session.lastInboundVideoPackets = 0L;
        session.lastInboundVideoFramesDecoded = 0L;
        session.lastInboundVideoPacketsLost = 0L;
        session.lastInboundVideoFramesDropped = 0L;
        session.lastInboundVideoNackCount = 0L;
        session.lastInboundVideoPliCount = 0L;
        session.lastInboundVideoPacketProgressMs = 0L;
        session.lastInboundVideoStatsMs = 0L;
        session.inboundArrivalKbpsEwma = 0.0;
        session.inboundArrivalKbpsVar = 0.0;
        session.inboundJitterMsEwma = 0.0;
        session.inboundJitterMsVar = 0.0;
        session.inboundLossRateEwma = 0.0;
        session.inboundLossRateVar = 0.0;
        session.inboundDecodeGapEwma = 0.0;
        session.inboundDecodeGapVar = 0.0;
        session.feedbackArrivalKbpsEwma = 0.0;
        session.feedbackArrivalKbpsVar = 0.0;
        session.feedbackJitterMsEwma = 0.0;
        session.feedbackJitterMsVar = 0.0;
        session.feedbackDecodeQueueEwma = 0.0;
        session.feedbackDecodeQueueVar = 0.0;
        session.feedbackLossRateEwma = 0.0;
        session.feedbackLossRateVar = 0.0;
        session.baseCaptureFps = 0;
        session.videoAdaptLevel = 0;
        session.stableVideoFeedbacks = 0;
        session.stagnantInboundVideoPolls = 0;
        session.lastVideoRecoveryMs = 0L;
        session.lastKeyframeRequestMs = 0L;
        session.lastOutboundVideoBytes = 0L;
        session.lastOutboundVideoFramesEncoded = 0L;
        session.lastOutboundVideoHugeFrames = 0L;
        session.lastOutboundVideoWidth = 0L;
        session.lastOutboundVideoHeight = 0L;
        session.lastOutboundResolutionRestoreMs = 0L;
        session.initialKeyframeRequested = false;
        session.terminalStartPending = false;
        session.terminalStartRequested = false;
        session.terminalOutputSeen = false;
        session.terminalStartRequestedMs = 0L;
        session.pendingRemoteFileListPath = "";
        session.remoteDescriptionSet = false;
        session.remoteAnswerSet = false;
        session.lastRemoteDescriptionType = "";
        session.lastRemoteDescriptionSdp = "";
        session.localOfferFirstVideoCodec = "";
        session.localAnswerVideoCodec = "";
        session.remoteOfferFirstVideoCodec = "";
        session.remoteAnswerVideoCodec = "";
        session.negotiatedVideoCodec = "";
        session.pendingCandidates.clear();
        if (session.peer != null) {
            try {
                session.peer.dispose();
            }
            catch (Exception exception) {
                // empty catch block
            }
            session.peer = null;
        }
        WebRtcClient.applyAudioOutputRoute();
        WebRtcClient.status("WebRTC stopped");
    }
    protected static void updateRemoteScreenWakeLock() {
        if (WebRtcClient.hasConnectedControlledDesktopSession()) {
            WebRtcClient.acquireRemoteScreenWakeLock();
        } else {
            WebRtcClient.releaseRemoteScreenWakeLock();
        }
    }
    protected static void acquireRemoteScreenWakeLock() {
        if (remoteScreenWakeLock != null && remoteScreenWakeLock.isHeld()) {
            return;
        }
        if (appContext == null) {
            return;
        }
        PowerManager manager = (PowerManager)appContext.getSystemService("power");
        if (manager == null) {
            return;
        }
        remoteScreenWakeLock = manager.newWakeLock(10, "AiranDesk:RemoteScreenOn");
        remoteScreenWakeLock.setReferenceCounted(false);
        try {
            remoteScreenWakeLock.acquire();
            WebRtcClient.status("Remote control screen wake lock acquired");
        }
        catch (Exception e) {
            WebRtcClient.status("Remote control screen wake lock failed: " + e.getMessage());
        }
    }
    protected static void releaseRemoteScreenWakeLock() {
        if (remoteScreenWakeLock == null) {
            return;
        }
        try {
            if (remoteScreenWakeLock.isHeld()) {
                remoteScreenWakeLock.release();
                WebRtcClient.status("Remote control screen wake lock released");
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        remoteScreenWakeLock = null;
    }
    protected static void closeDataChannel(DataChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
    protected static void invalidateScreenCapturePermission() {
        screenCaptureIntent = null;
        screenCaptureResultCode = 0;
        screenCapturePermissionRequestInFlight = false;
        screenCapturePermissionRequestStartedMs = 0L;
    }
    protected static boolean isExpiredMediaProjectionError(Exception e) {
        String message = e == null ? "" : String.valueOf(e.getMessage());
        return e instanceof SecurityException || message.toLowerCase(Locale.US).contains("mediaprojection") || message.toLowerCase(Locale.US).contains("virtualdisplay");
    }
    protected static void disposeScreenCaptureResources(PeerSession session, boolean stopCapturer) {
        if (session == null) {
            return;
        }
        /* Per-session teardown only: drop the local video track + transceiver sender, and
         * release the per-session references to the shared capturer pipeline. The shared
         * capturer/source/helper outlive the session and are released by
         * releaseSharedScreenCapture() either after the idle grace window or on genuine
         * MediaProjection revocation. */
        if (session.localVideoTrack != null) {
            try {
                session.localVideoTrack.dispose();
            }
            catch (Exception exception) {
                // empty catch block
            }
            session.localVideoTrack = null;
        }
        session.localVideoSender = null;
        session.screenCapturer = null;
        session.videoSource = null;
        session.surfaceTextureHelper = null;
        if (stopCapturer) {
            /* Caller (typically stopPeer with no surviving sessions) wants to halt the
             * shared capture loop too — but only if no other session is using it. */
            if (!WebRtcClient.hasConnectedControlledDesktopSession()) {
                WebRtcClient.scheduleSharedScreenCaptureRelease();
            }
        }
    }
    protected static void releaseSharedScreenCapture() {
        WebRtcClient.cancelSharedCaptureRelease();
        if (sharedScreenCapturer != null) {
            try {
                sharedScreenCapturer.stopCapture();
            }
            catch (Exception exception) {
                // empty catch block
            }
            try {
                sharedScreenCapturer.dispose();
            }
            catch (Exception exception) {
                // empty catch block
            }
            sharedScreenCapturer = null;
        }
        if (sharedVideoSource != null) {
            try {
                sharedVideoSource.dispose();
            }
            catch (Exception exception) {
                // empty catch block
            }
            sharedVideoSource = null;
        }
        if (sharedSurfaceTextureHelper != null) {
            try {
                sharedSurfaceTextureHelper.dispose();
            }
            catch (Exception exception) {
                // empty catch block
            }
            sharedSurfaceTextureHelper = null;
        }
        sharedScreenCaptureStopped = true;
        sharedCaptureWidth = 0;
        sharedCaptureHeight = 0;
        sharedCaptureVisibleWidth = 0;
        sharedCaptureVisibleHeight = 0;
        sharedCapturePadLeft = 0;
        sharedCapturePadTop = 0;
        sharedCapturePadRight = 0;
        sharedCapturePadBottom = 0;
        sharedCaptureFps = 0;
        sharedFramePaddingObserver = null;
    }
    protected static void scheduleSharedScreenCaptureRelease() {
        if (sharedScreenCapturer == null) {
            return;
        }
        WebRtcClient.cancelSharedCaptureRelease();
        lastSessionEndedMs = System.currentTimeMillis();
        sharedCaptureReleaseRunnable = new Runnable(){

            @Override
            public void run() {
                if (sharedCaptureReleaseRunnable != this) {
                    return;
                }
                sharedCaptureReleaseRunnable = null;
                if (WebRtcClient.hasConnectedControlledDesktopSession()) {
                    WebRtcClient.status("Shared screen capture release skipped: session reconnected within grace window");
                    return;
                }
                WebRtcClient.status("Shared screen capture released after idle grace");
                WebRtcClient.releaseSharedScreenCapture();
                WebRtcClient.invalidateScreenCapturePermission();
                if (!WebRtcClient.hasConnectedControlledDesktopSession()) {
                    MediaProjectionForegroundService.stop(appContext);
                }
            }
        };
        MAIN.postDelayed(sharedCaptureReleaseRunnable, SHARED_CAPTURE_IDLE_GRACE_MS);
        WebRtcClient.status("Shared screen capture release scheduled in " + SHARED_CAPTURE_IDLE_GRACE_MS / 1000L + "s");
    }
    protected static void cancelSharedCaptureRelease() {
        if (sharedCaptureReleaseRunnable != null) {
            MAIN.removeCallbacks(sharedCaptureReleaseRunnable);
            sharedCaptureReleaseRunnable = null;
        }
    }
    protected static boolean applySharedCaptureFormat(int[] rect, int fps) {
        if (sharedScreenCapturer == null || sharedVideoSource == null || sharedScreenCaptureStopped) {
            return false;
        }
        if (rect == null || rect.length < 8 || fps <= 0 ||
                rect[CAPTURE_RECT_CODED_WIDTH] <= 0 || rect[CAPTURE_RECT_CODED_HEIGHT] <= 0 ||
                rect[CAPTURE_RECT_VISIBLE_WIDTH] <= 0 || rect[CAPTURE_RECT_VISIBLE_HEIGHT] <= 0) {
            return false;
        }
        try {
            sharedScreenCapturer.changeCaptureFormat(rect[CAPTURE_RECT_VISIBLE_WIDTH], rect[CAPTURE_RECT_VISIBLE_HEIGHT], fps);
            sharedVideoSource.adaptOutputFormat(rect[CAPTURE_RECT_CODED_WIDTH], rect[CAPTURE_RECT_CODED_HEIGHT], fps);
            WebRtcClient.setSharedCaptureRect(rect, fps);
            return true;
        }
        catch (Exception e) {
            WebRtcClient.status("shared capture format change failed: " + e.getMessage());
            if (WebRtcClient.isExpiredMediaProjectionError(e)) {
                WebRtcClient.handleSharedScreenCaptureInvalidated("format change failed");
            }
            return false;
        }
    }
    protected static void handleSharedScreenCaptureInvalidated(final String reason) {
        Runnable task = new Runnable(){

            @Override
            public void run() {
                if (sharedScreenCaptureStopped && screenCaptureIntent == null && sharedScreenCapturer == null) {
                    return;
                }
                sharedScreenCaptureStopped = true;
                WebRtcClient.status("Screen capture invalidated" + (reason == null || reason.length() == 0 ? "" : ": " + reason));
                WebRtcClient.invalidateScreenCapturePermission();
                WebRtcClient.releaseSharedScreenCapture();
                for (PeerSession s : sessions.values()) {
                    if (s == null || s.stopped) continue;
                    if (!"cli".equals(s.role) || !"desktop".equals(s.mode)) continue;
                    s.screenCaptureStopped = true;
                    s.screenCaptureOfferStarting = false;
                    s.localVideoSender = null;
                    s.screenCapturer = null;
                    s.videoSource = null;
                    s.surfaceTextureHelper = null;
                    if (s.localVideoTrack != null) {
                        try {
                            s.localVideoTrack.dispose();
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                        s.localVideoTrack = null;
                    }
                    boolean shouldAutoRequestAgain = s.peer != null
                            && s.peerConnected
                            && s.inputChannel != null
                            && s.inputChannel.state() == DataChannel.State.OPEN
                            && uiEvents != null;
                    s.pendingOfferAfterScreenPermission = shouldAutoRequestAgain;
                    if (shouldAutoRequestAgain) {
                        WebRtcClient.status("Screen capture invalidated; requesting permission again for " + s.remoteId);
                        WebRtcClient.requestScreenCapturePermissionForSession(s, reason);
                    }
                }
                if (!WebRtcClient.hasConnectedControlledDesktopSession()) {
                    MediaProjectionForegroundService.stop(appContext);
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
            return;
        }
        MAIN.post(task);
    }
    protected static void handleSharedScreenCaptureStopped() {
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                WebRtcClient.status("Screen capture stopped by system");
                sharedScreenCaptureStopped = true;
                /* OS revoked the MediaProjection token — drop the shared pipeline and
                 * the cached intent so the next desktop offer re-prompts the user. */
                WebRtcClient.invalidateScreenCapturePermission();
                WebRtcClient.releaseSharedScreenCapture();
                /* Tell every active controlled-desktop session that its bound capturer
                 * is gone; they will re-request permission on the next offer/refresh. */
                for (PeerSession s : sessions.values()) {
                    if (s == null || s.stopped) continue;
                    if (!"cli".equals(s.role) || !"desktop".equals(s.mode)) continue;
                    s.screenCaptureStopped = true;
                    s.screenCaptureOfferStarting = false;
                    s.localVideoSender = null;
                    s.screenCapturer = null;
                    s.videoSource = null;
                    s.surfaceTextureHelper = null;
                    if (s.localVideoTrack != null) {
                        try {
                            s.localVideoTrack.dispose();
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                        s.localVideoTrack = null;
                    }
                    boolean shouldAutoRequestAgain = s.peer != null
                            && s.peerConnected
                            && s.inputChannel != null
                            && s.inputChannel.state() == DataChannel.State.OPEN
                            && uiEvents != null;
                    s.pendingOfferAfterScreenPermission = shouldAutoRequestAgain;
                    if (shouldAutoRequestAgain) {
                        WebRtcClient.status("Screen capture stopped; requesting permission again for " + s.remoteId);
                        WebRtcClient.requestScreenCapturePermissionForSession(s, "capture stopped");
                    }
                }
                if (!WebRtcClient.hasConnectedControlledDesktopSession()) {
                    MediaProjectionForegroundService.stop(appContext);
                }
            }
        });
    }
    protected static void disposeLocalAudioResources(PeerSession session) {
        if (session == null) {
            return;
        }
        RtpSender sender = session.localAudioSender;
        if (sender == null && session.peer != null) {
            try {
                List<RtpTransceiver> transceivers = session.peer.getTransceivers();
                if (transceivers != null) {
                    for (RtpTransceiver transceiver : transceivers) {
                        if (transceiver == null || transceiver.getMediaType() != MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                            continue;
                        }
                        sender = transceiver.getSender();
                        break;
                    }
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (sender != null) {
            try {
                sender.setTrack(null, false);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        session.localAudioSender = null;
        if (session.localAudioTrack != null) {
            try {
                session.localAudioTrack.dispose();
            }
            catch (Exception exception) {
                // empty catch block
            }
            session.localAudioTrack = null;
        }
        if (session.audioSource != null) {
            try {
                session.audioSource.dispose();
            }
            catch (Exception exception) {
                // empty catch block
            }
            session.audioSource = null;
        }
        WebRtcClient.applyAudioOutputRoute();
    }
    protected static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("WebRtcClient.init must be called first");
        }
    }
    static void status(final String message) {
        Log.i((String)TAG, (String)message);
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                UiEvents events = uiEvents;
                if (events != null) {
                    events.onStatus(message);
                }
            }
        });
    }
    static void postDelayed(Runnable runnable, long delayMs) {
        MAIN.postDelayed(runnable, delayMs);
    }
    protected static void startControlHeartbeat(PeerSession session) {
        if (!"ctl".equals(session.role) || !"desktop".equals(session.mode)) {
            return;
        }
        if (session.controlHeartbeatRunning) {
            return;
        }
        session.controlHeartbeatRunning = true;
        MAIN.removeCallbacks(session.controlHeartbeatRunnable);
        MAIN.post(session.controlHeartbeatRunnable);
    }
    protected static void stopControlHeartbeat(PeerSession session) {
        session.controlHeartbeatRunning = false;
        MAIN.removeCallbacks(session.controlHeartbeatRunnable);
    }

}
