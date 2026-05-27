package com.wxalh.airan_desk.rtc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.util.Log;
import android.view.TextureView;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

@SuppressWarnings({"deprecation", "unchecked"})
public final class TextureVideoRenderer
extends TextureView
implements TextureView.SurfaceTextureListener,
VideoSink {
    private static final String TAG = "TextureVideoRenderer";
    private static final long FRAME_FINGERPRINT_INTERVAL_MS = 2000L;
    private final EglRenderer eglRenderer = new EglRenderer("TextureVideoRenderer");
    private boolean initialized;
    private boolean surfaceReady;
    private long frameCount;
    private long lastFingerprintMs;
    private int lastFingerprint;
    private int unchangedFingerprintSamples;
    private int previousFrameFingerprint;
    private int twoFramesAgoFingerprint;
    private int previousBufferId;
    private int twoFramesAgoBufferId;
    private String previousBufferClass = "";
    private String twoFramesAgoBufferClass = "";
    private long previousTimestampNs;
    private long twoFramesAgoTimestampNs;
    private int repeatedPreviousFrames;
    private int alternatingFrames;
    private volatile int frameWidth;
    private volatile int frameHeight;
    private volatile int visibleWidth;
    private volatile int visibleHeight;
    private volatile int padLeft;
    private volatile int padTop;
    private volatile int padRight;
    private volatile int padBottom;
    private int lastNotifiedFrameWidth;
    private int lastNotifiedFrameHeight;
    private FrameSizeListener frameSizeListener;

    public TextureVideoRenderer(Context context) {
        super(context);
        this.setOpaque(true);
        this.setSurfaceTextureListener(this);
    }

    public void init(EglBase.Context eglContext) {
        if (this.initialized) {
            return;
        }
        this.eglRenderer.init(eglContext, EglBase.CONFIG_PLAIN, (RendererCommon.GlDrawer)new GlRectDrawer());
        this.initialized = true;
        if (this.isAvailable()) {
            this.onSurfaceTextureAvailable(this.getSurfaceTexture(), this.getWidth(), this.getHeight());
        }
    }

    public void setMirror(boolean mirror) {
        this.eglRenderer.setMirror(mirror);
    }

    public void setFrameSizeListener(FrameSizeListener listener) {
        this.frameSizeListener = listener;
    }

    public void setVisibleRect(int visibleWidth, int visibleHeight, int padLeft, int padTop, int padRight, int padBottom) {
        this.visibleWidth = Math.max(0, visibleWidth);
        this.visibleHeight = Math.max(0, visibleHeight);
        this.padLeft = Math.max(0, padLeft);
        this.padTop = Math.max(0, padTop);
        this.padRight = Math.max(0, padRight);
        this.padBottom = Math.max(0, padBottom);
    }

    public void release() {
        if (this.surfaceReady) {
            this.releaseSurfaceBlocking();
        }
        this.eglRenderer.release();
        this.initialized = false;
    }

    private void releaseSurfaceBlocking() {
        final CountDownLatch latch = new CountDownLatch(1);
        this.eglRenderer.releaseEglSurface(new Runnable(){

            @Override
            public void run() {
                latch.countDown();
            }
        });
        try {
            latch.await(500L, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.surfaceReady = false;
    }

    public void onFrame(VideoFrame frame) {
        VideoFrame renderFrame = this.cropVisibleFrame(frame);
        try {
            this.updateFrameSize(renderFrame);
            this.logFrameFingerprint(frame, renderFrame);
            this.eglRenderer.onFrame(renderFrame);
        } finally {
            if (renderFrame != frame) {
                renderFrame.release();
            }
        }
    }

    private static int computePhysicalBufferId(VideoFrame.Buffer buffer) {
        if (buffer == null) {
            return 0;
        }
        if (buffer instanceof VideoFrame.TextureBuffer) {
            return ((VideoFrame.TextureBuffer) buffer).getTextureId();
        }
        if (buffer instanceof VideoFrame.I420Buffer) {
            ByteBuffer y = ((VideoFrame.I420Buffer) buffer).getDataY();
            return y == null ? 0 : System.identityHashCode(y);
        }
        return System.identityHashCode(buffer);
    }

    private VideoFrame cropVisibleFrame(VideoFrame frame) {
        if (frame == null || frame.getBuffer() == null) {
            return frame;
        }
        int codedW = frame.getRotatedWidth();
        int codedH = frame.getRotatedHeight();
        int cropW = this.visibleWidth > 0 ? Math.min(this.visibleWidth, codedW) : codedW;
        int cropH = this.visibleHeight > 0 ? Math.min(this.visibleHeight, codedH) : codedH;
        int left = Math.min(Math.max(0, this.padLeft), Math.max(0, codedW - cropW));
        int top = Math.min(Math.max(0, this.padTop), Math.max(0, codedH - cropH));
        if (cropW <= 0 || cropH <= 0 || (left == 0 && top == 0 && cropW == codedW && cropH == codedH)) {
            return frame;
        }
        VideoFrame.Buffer cropped = frame.getBuffer().cropAndScale(left, top, cropW, cropH, cropW, cropH);
        return new VideoFrame(cropped, frame.getRotation(), frame.getTimestampNs());
    }

    private void updateFrameSize(VideoFrame frame) {
        if (frame == null || frame.getBuffer() == null) {
            return;
        }
        int width = frame.getRotatedWidth();
        int height = frame.getRotatedHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        this.frameWidth = width;
        this.frameHeight = height;
        if (width == this.lastNotifiedFrameWidth && height == this.lastNotifiedFrameHeight) {
            return;
        }
        this.lastNotifiedFrameWidth = width;
        this.lastNotifiedFrameHeight = height;
        final int notifiedWidth = width;
        final int notifiedHeight = height;
        final FrameSizeListener listener = this.frameSizeListener;
        if (listener != null) {
            this.post(new Runnable(){

                @Override
                public void run() {
                    listener.onFrameSizeChanged(notifiedWidth, notifiedHeight);
                }
            });
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void logFrameFingerprint(VideoFrame originalFrame, VideoFrame frame) {
        if (frame == null || frame.getBuffer() == null) {
            return;
        }
        ++this.frameCount;
        this.frameWidth = frame.getRotatedWidth();
        this.frameHeight = frame.getRotatedHeight();
        VideoFrame.Buffer originalBuffer = originalFrame != null ? originalFrame.getBuffer() : frame.getBuffer();
        int rawBufferId = computePhysicalBufferId(originalBuffer);
        String rawBufferClass = originalBuffer.getClass().getSimpleName();
        long currentTimestampNs = frame.getTimestampNs();
        VideoFrame.I420Buffer i420 = null;
        try {
            boolean changed;
            boolean sameAsTwoFramesAgo;
            i420 = frame.getBuffer().toI420();
            if (VideoFrameDumper.isEnabled()) {
                VideoFrameDumper.dumpDecodedI420(i420, frame.getRotation(), frame.getTimestampNs());
            }
            int fingerprint = this.fingerprintPlane(i420.getDataY(), i420.getStrideY(), i420.getWidth(), i420.getHeight(), 17);
            fingerprint = 31 * fingerprint + this.fingerprintPlane(i420.getDataU(), i420.getStrideU(), i420.getWidth() / 2, i420.getHeight() / 2, 31);
            fingerprint = 31 * fingerprint + this.fingerprintPlane(i420.getDataV(), i420.getStrideV(), i420.getWidth() / 2, i420.getHeight() / 2, 47);
            boolean sameAsPrevious = this.previousFrameFingerprint != 0 && fingerprint == this.previousFrameFingerprint;
            boolean bl = sameAsTwoFramesAgo = this.twoFramesAgoFingerprint != 0 && fingerprint == this.twoFramesAgoFingerprint;
            if (sameAsPrevious) {
                ++this.repeatedPreviousFrames;
            }
            if (!sameAsPrevious && sameAsTwoFramesAgo) {
                ++this.alternatingFrames;
                Log.w((String)TAG, (String)("alternate2 hit frames=" + this.frameCount
                        + " curHash=" + Integer.toHexString(fingerprint)
                        + " curBuf=" + rawBufferClass + "@" + Integer.toHexString(rawBufferId)
                        + " curTsNs=" + currentTimestampNs
                        + " prevHash=" + Integer.toHexString(this.previousFrameFingerprint)
                        + " prevBuf=" + this.previousBufferClass + "@" + Integer.toHexString(this.previousBufferId)
                        + " prevTsNs=" + this.previousTimestampNs
                        + " prev2Hash=" + Integer.toHexString(this.twoFramesAgoFingerprint)
                        + " prev2Buf=" + this.twoFramesAgoBufferClass + "@" + Integer.toHexString(this.twoFramesAgoBufferId)
                        + " prev2TsNs=" + this.twoFramesAgoTimestampNs
                        + " sameBufAsPrev2=" + (rawBufferId == this.twoFramesAgoBufferId)));
            }
            this.twoFramesAgoFingerprint = this.previousFrameFingerprint;
            this.twoFramesAgoBufferId = this.previousBufferId;
            this.twoFramesAgoBufferClass = this.previousBufferClass;
            this.twoFramesAgoTimestampNs = this.previousTimestampNs;
            this.previousFrameFingerprint = fingerprint;
            this.previousBufferId = rawBufferId;
            this.previousBufferClass = rawBufferClass;
            this.previousTimestampNs = currentTimestampNs;
            long now = SystemClock.elapsedRealtime();
            if (now - this.lastFingerprintMs < 2000L) {
                return;
            }
            this.lastFingerprintMs = now;
            boolean bl2 = changed = fingerprint != this.lastFingerprint;
            this.unchangedFingerprintSamples = this.lastFingerprint != 0 && !changed ? ++this.unchangedFingerprintSamples : 0;
            this.lastFingerprint = fingerprint;
            Log.i((String)TAG, (String)("decoded frame fingerprint width=" + i420.getWidth() + " height=" + i420.getHeight() + " rotation=" + frame.getRotation() + " frames=" + this.frameCount + " hash=" + Integer.toHexString(fingerprint) + " bufClass=" + rawBufferClass + " changed=" + changed + " unchangedSamples=" + this.unchangedFingerprintSamples + " repeatPrev=" + this.repeatedPreviousFrames + " alternate2=" + this.alternatingFrames));
            this.repeatedPreviousFrames = 0;
            this.alternatingFrames = 0;
        }
        catch (Exception e) {
            Log.w((String)TAG, (String)("decoded frame fingerprint failed: " + e.getMessage()));
        }
        finally {
            if (i420 != null) {
                i420.release();
            }
        }
    }

    private int fingerprintPlane(ByteBuffer data, int stride, int width, int height, int seed) {
        if (data == null || width <= 0 || height <= 0 || stride <= 0) {
            return seed;
        }
        ByteBuffer buffer = data.duplicate();
        int hash = seed;
        int rows = Math.min(height, 32);
        int cols = Math.min(width, 32);
        int rowStep = Math.max(1, height / rows);
        int colStep = Math.max(1, width / cols);
        for (int y = 0; y < height; y += rowStep) {
            int row = y * stride;
            for (int x = 0; x < width; x += colStep) {
                int index = row + x;
                if (index < 0 || index >= buffer.capacity()) continue;
                hash = hash * 16777619 ^ buffer.get(index) & 0xFF;
            }
        }
        return hash;
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (!this.initialized || surface == null || this.surfaceReady) {
            return;
        }
        this.eglRenderer.createEglSurface(surface);
        this.surfaceReady = true;
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (this.surfaceReady) {
            this.releaseSurfaceBlocking();
        }
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public static interface FrameSizeListener {
        public void onFrameSizeChanged(int width, int height);
    }
}
