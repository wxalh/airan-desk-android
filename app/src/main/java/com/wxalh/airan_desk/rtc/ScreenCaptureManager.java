package com.wxalh.airan_desk.rtc;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import com.wxalh.airan_desk.rtc.WebRtcClient;

@SuppressWarnings({"deprecation", "unchecked"})
public class ScreenCaptureManager {
    private ScreenCaptureManager() {
    }

    public static boolean startCapture(Context context, MediaProjection projection, int width, int height, int density) {
        return WebRtcClient.hasScreenCapturePermission();
    }

    public static void setPermissionResult(int resultCode, Intent data) {
        WebRtcClient.setScreenCapturePermission(resultCode, data);
    }

    public static void stopCapture() {
        WebRtcClient.stop();
    }

    public static void requestKeyFrame() {
    }
}
