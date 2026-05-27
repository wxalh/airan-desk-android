package com.wxalh.airan_desk.rtc;

import java.nio.ByteBuffer;
import org.webrtc.CapturerObserver;
import org.webrtc.JavaI420Buffer;
import org.webrtc.VideoFrame;

final class VideoFramePaddingCapturerObserver implements CapturerObserver {
    private final CapturerObserver delegate;
    private volatile int codedWidth;
    private volatile int codedHeight;
    private volatile int visibleWidth;
    private volatile int visibleHeight;
    private volatile int padLeft;
    private volatile int padTop;

    VideoFramePaddingCapturerObserver(CapturerObserver delegate) {
        this.delegate = delegate;
    }

    void setFrameRect(int codedWidth, int codedHeight, int visibleWidth, int visibleHeight, int padLeft, int padTop) {
        int cw = Math.max(0, codedWidth);
        int ch = Math.max(0, codedHeight);
        int vw = cw > 0 ? Math.min(Math.max(0, visibleWidth), cw) : Math.max(0, visibleWidth);
        int vh = ch > 0 ? Math.min(Math.max(0, visibleHeight), ch) : Math.max(0, visibleHeight);
        int left = cw > 0 ? Math.min(Math.max(0, padLeft), Math.max(0, cw - vw)) : Math.max(0, padLeft);
        int top = ch > 0 ? Math.min(Math.max(0, padTop), Math.max(0, ch - vh)) : Math.max(0, padTop);
        this.codedWidth = cw;
        this.codedHeight = ch;
        this.visibleWidth = vw;
        this.visibleHeight = vh;
        this.padLeft = left;
        this.padTop = top;
    }

    @Override
    public void onCapturerStarted(boolean success) {
        this.delegate.onCapturerStarted(success);
    }

    @Override
    public void onCapturerStopped() {
        this.delegate.onCapturerStopped();
    }

    @Override
    public void onFrameCaptured(VideoFrame frame) {
        VideoFrame padded = this.padFrame(frame);
        try {
            this.delegate.onFrameCaptured(padded);
        } finally {
            if (padded != frame) {
                padded.release();
            }
        }
    }

    private VideoFrame padFrame(VideoFrame frame) {
        if (frame == null || frame.getBuffer() == null) {
            return frame;
        }
        int cw = this.codedWidth;
        int ch = this.codedHeight;
        int vw = this.visibleWidth;
        int vh = this.visibleHeight;
        int left = this.padLeft;
        int top = this.padTop;
        if (cw <= 0 || ch <= 0 || vw <= 0 || vh <= 0) {
            return frame;
        }
        if (cw == vw && ch == vh && left == 0 && top == 0
                && frame.getBuffer().getWidth() == cw && frame.getBuffer().getHeight() == ch) {
            return frame;
        }

        VideoFrame.Buffer visibleBuffer = frame.getBuffer().cropAndScale(
                0, 0,
                frame.getBuffer().getWidth(),
                frame.getBuffer().getHeight(),
                vw,
                vh);
        VideoFrame.I420Buffer visibleI420 = null;
        JavaI420Buffer codedI420 = null;
        boolean handedOff = false;
        try {
            visibleI420 = visibleBuffer.toI420();
            codedI420 = JavaI420Buffer.allocate(cw, ch);
            fillPlane(codedI420.getDataY(), codedI420.getStrideY(), cw, ch, (byte)0);
            fillPlane(codedI420.getDataU(), codedI420.getStrideU(), (cw + 1) / 2, (ch + 1) / 2, (byte)128);
            fillPlane(codedI420.getDataV(), codedI420.getStrideV(), (cw + 1) / 2, (ch + 1) / 2, (byte)128);

            copyPlane(visibleI420.getDataY(), visibleI420.getStrideY(),
                    codedI420.getDataY(), codedI420.getStrideY(), vw, vh, left, top);
            copyPlane(visibleI420.getDataU(), visibleI420.getStrideU(),
                    codedI420.getDataU(), codedI420.getStrideU(), (vw + 1) / 2, (vh + 1) / 2, left / 2, top / 2);
            copyPlane(visibleI420.getDataV(), visibleI420.getStrideV(),
                    codedI420.getDataV(), codedI420.getStrideV(), (vw + 1) / 2, (vh + 1) / 2, left / 2, top / 2);

            VideoFrame out = new VideoFrame(codedI420, frame.getRotation(), frame.getTimestampNs());
            handedOff = true;
            return out;
        } finally {
            if (visibleI420 != null) {
                visibleI420.release();
            }
            if (codedI420 != null && !handedOff) {
                codedI420.release();
            }
            visibleBuffer.release();
        }
    }

    private static void fillPlane(ByteBuffer data, int stride, int width, int height, byte value) {
        ByteBuffer out = data.duplicate();
        for (int y = 0; y < height; ++y) {
            int row = y * stride;
            for (int x = 0; x < width; ++x) {
                out.put(row + x, value);
            }
        }
    }

    private static void copyPlane(ByteBuffer srcData, int srcStride, ByteBuffer dstData, int dstStride,
                                  int width, int height, int dstX, int dstY) {
        ByteBuffer src = srcData.duplicate();
        ByteBuffer dst = dstData.duplicate();
        for (int y = 0; y < height; ++y) {
            int srcRow = y * srcStride;
            int dstRow = (dstY + y) * dstStride + dstX;
            for (int x = 0; x < width; ++x) {
                dst.put(dstRow + x, src.get(srcRow + x));
            }
        }
    }
}
