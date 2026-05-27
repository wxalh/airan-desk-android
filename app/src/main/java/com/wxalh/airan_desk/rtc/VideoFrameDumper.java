package com.wxalh.airan_desk.rtc;

import android.content.Context;
import android.util.Log;

import org.webrtc.VideoFrame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Optional decoded I420 frame dumper. No-op unless an opt-in marker file
 * named "airan_dump_video.flag" exists inside the app's external files
 * directory (getExternalFilesDir(null)). Output is raw planar I420 (Y, U,
 * V tightly packed without stride padding) plus a CSV sidecar with
 * per-frame geometry. Capped at 512 MiB per session.
 *
 * Replay example:
 *   ffplay -f rawvideo -pix_fmt yuv420p -s 1920x1080 airan_android_&lt;ts&gt;.yuv
 */
public final class VideoFrameDumper {
    private static final String TAG = "VideoFrameDumper";
    private static final String MARKER_NAME = "airan_dump_video.flag";
    private static final long MAX_BYTES = 512L * 1024L * 1024L;

    private static final Object LOCK = new Object();
    private static boolean resolved;
    private static boolean enabled;
    private static File yuvFile;
    private static FileOutputStream yuvStream;
    private static Writer sidecarWriter;
    private static long bytesWritten;
    private static long frameIndex;
    private static boolean cappedWarned;

    private VideoFrameDumper() {
    }

    public static boolean isEnabled() {
        synchronized (LOCK) {
            if (resolved) {
                return enabled;
            }
            resolved = true;
            Context ctx = WebRtcCore.appContext;
            if (ctx == null) {
                return false;
            }
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) {
                return false;
            }
            File marker = new File(dir, MARKER_NAME);
            if (!marker.exists()) {
                return false;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            yuvFile = new File(dir, "airan_android_" + stamp + ".yuv");
            File sidecarFile = new File(dir, "airan_android_" + stamp + ".txt");
            try {
                yuvStream = new FileOutputStream(yuvFile, false);
                sidecarWriter = new OutputStreamWriter(new FileOutputStream(sidecarFile, false), StandardCharsets.UTF_8);
                sidecarWriter.write("# index,width,height,strideY,strideU,strideV,rotation,timestamp_ns,wallclock_ms\n");
                sidecarWriter.flush();
            } catch (IOException e) {
                Log.w(TAG, "open dump files failed: " + e.getMessage());
                closeQuietlyLocked();
                return false;
            }
            enabled = true;
            Log.i(TAG, "video frame dump enabled, output=" + yuvFile.getAbsolutePath());
            return true;
        }
    }

    public static void dumpDecodedI420(VideoFrame.I420Buffer buffer, int rotation, long timestampNs) {
        if (buffer == null) {
            return;
        }
        synchronized (LOCK) {
            if (!enabled) {
                return;
            }
            if (bytesWritten >= MAX_BYTES) {
                if (!cappedWarned) {
                    Log.w(TAG, "video frame dump capped at " + MAX_BYTES + " bytes");
                    cappedWarned = true;
                }
                return;
            }
            int width = buffer.getWidth();
            int height = buffer.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            int chromaWidth = (width + 1) / 2;
            int chromaHeight = (height + 1) / 2;
            try {
                writePlane(buffer.getDataY(), buffer.getStrideY(), width, height);
                writePlane(buffer.getDataU(), buffer.getStrideU(), chromaWidth, chromaHeight);
                writePlane(buffer.getDataV(), buffer.getStrideV(), chromaWidth, chromaHeight);
                bytesWritten += (long) width * height + 2L * chromaWidth * chromaHeight;
                if (sidecarWriter != null) {
                    sidecarWriter.write(frameIndex + "," + width + "," + height + ","
                            + buffer.getStrideY() + "," + buffer.getStrideU() + "," + buffer.getStrideV() + ","
                            + rotation + "," + timestampNs + "," + System.currentTimeMillis() + "\n");
                }
                frameIndex++;
                if ((frameIndex & 63L) == 0L) {
                    yuvStream.flush();
                    if (sidecarWriter != null) {
                        sidecarWriter.flush();
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "write decoded frame failed: " + e.getMessage());
            }
        }
    }

    private static void writePlane(ByteBuffer plane, int stride, int width, int height) throws IOException {
        if (plane == null || width <= 0 || height <= 0 || stride <= 0) {
            return;
        }
        ByteBuffer src = plane.duplicate();
        byte[] row = new byte[width];
        for (int y = 0; y < height; y++) {
            int rowStart = y * stride;
            if (rowStart + width > src.capacity()) {
                break;
            }
            src.position(rowStart);
            src.get(row, 0, width);
            yuvStream.write(row);
        }
    }

    private static void closeQuietlyLocked() {
        try {
            if (yuvStream != null) {
                yuvStream.close();
            }
        } catch (IOException ignored) {
        } finally {
            yuvStream = null;
        }
        try {
            if (sidecarWriter != null) {
                sidecarWriter.close();
            }
        } catch (IOException ignored) {
        } finally {
            sidecarWriter = null;
        }
        enabled = false;
    }
}
