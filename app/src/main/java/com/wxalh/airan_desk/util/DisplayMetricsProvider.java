package com.wxalh.airan_desk.util;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

public final class DisplayMetricsProvider {
    private DisplayMetricsProvider() {
    }

    public static DisplayMetrics realMetrics(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        try {
            WindowManager windowManager = (WindowManager)context.getSystemService("window");
            Display display = windowManager == null ? null : windowManager.getDefaultDisplay();
            if (display != null) {
                display.getRealMetrics(metrics);
            }
        }
        catch (Exception exception) {
            // Fall through to resource metrics below.
        }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            DisplayMetrics fallback = context.getResources().getDisplayMetrics();
            metrics.widthPixels = fallback.widthPixels;
            metrics.heightPixels = fallback.heightPixels;
            metrics.density = fallback.density;
            metrics.densityDpi = fallback.densityDpi;
            metrics.scaledDensity = fallback.scaledDensity;
            metrics.xdpi = fallback.xdpi;
            metrics.ydpi = fallback.ydpi;
        }
        return metrics;
    }
}
