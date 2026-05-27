package com.wxalh.airan_desk.ui;

import android.view.MotionEvent;

public final class DesktopViewportMath {
    private DesktopViewportMath() {
    }

    public static int effectiveViewportHeight(int height, int keyboardInset, int minHeightPx) {
        return Math.max(minHeightPx, height - Math.max(0, keyboardInset));
    }

    public static float pointerDistance(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0.0f;
        }
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    public static float[] normalizedPoint(float viewportX, float viewportY, float panX, float panY, float zoom, int baseWidth, int baseHeight) {
        float contentX = (viewportX - panX) / Math.max(0.001f, zoom);
        float contentY = (viewportY - panY) / Math.max(0.001f, zoom);
        float x = contentX / (float)Math.max(1, baseWidth);
        float y = contentY / (float)Math.max(1, baseHeight);
        return new float[]{clamp(x, 0.0f, 1.0f), clamp(y, 0.0f, 1.0f)};
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
