package com.wxalh.airan_desk.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public final class AudioPermissionHelper {
    private AudioPermissionHelper() {
    }

    public static boolean hasRecordAudio(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestRecordAudio(Activity activity, int requestCode) {
        if (activity == null || Build.VERSION.SDK_INT < 23) {
            return;
        }
        activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, requestCode);
    }
}
