package com.wxalh.airan_desk.input;

import android.content.Context;
import android.util.DisplayMetrics;
import com.wxalh.airan_desk.util.DisplayMetricsProvider;
import java.util.Locale;
import org.json.JSONObject;

public final class RemoteAndroidInputHandler {
    public interface Listener {
        void onStatus(String message);
    }

    public static final class PointerState {
        boolean pointerDown;
        float downX;
        float downY;
    }

    private final Context context;
    private final Listener listener;
    private final InputRateLimiter rateLimiter = new InputRateLimiter(60, 300.0);
    private long lastDropWarningMs;

    public RemoteAndroidInputHandler(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void handleNavigation(String action, AccessibilityWarning warning, Runnable accessibilityRequest) {
        if (!rateLimiter.tryAcquire()) {
            warnRateLimited("nav");
            return;
        }
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.US);
        boolean ok = "back".equals(normalized) ? RemoteInputAccessibilityService.globalBack() : ("home".equals(normalized) ? RemoteInputAccessibilityService.globalHome() : ("recents".equals(normalized) ? RemoteInputAccessibilityService.globalRecents() : false));
        reportResult(warning, ok, accessibilityRequest);
        if (!ok) {
            status("Android navigation unsupported or accessibility disabled: " + normalized);
        }
    }

    public void handleKeyboard(JSONObject object, AccessibilityWarning warning, Runnable accessibilityRequest) {
        if (!rateLimiter.tryAcquire()) {
            warnRateLimited("keyboard");
            return;
        }
        boolean handled;
        if (!RemoteInputAccessibilityService.isReady()) {
            status("Accessibility service is not enabled; keyboard input ignored");
            requireAccessibility(accessibilityRequest);
            return;
        }
        String text = object.optString("text", "");
        if (text.length() > 0 || "text".equals(object.optString("dwFlags", ""))) {
            boolean ok = RemoteInputAccessibilityService.inputText(text);
            reportResult(warning, ok, accessibilityRequest);
            if (!ok) {
                status("Android keyboard text ignored: no focused editable field");
            }
            return;
        }
        int keyCode = object.optInt("key", -1);
        if (keyCode <= 0) {
            status("Android keyboard input ignored: invalid key code");
            return;
        }
        String flags = object.optString("dwFlags", "");
        boolean down = "down".equals(flags) || object.optBoolean("down");
        boolean up = "up".equals(flags) || object.optBoolean("up");
        boolean pressDown = down;
        if (!down && !up) {
            pressDown = true;
        }
        if (!((handled = RemoteInputAccessibilityService.keyboard(keyCode, pressDown)) || !up && down)) {
            status("Android keyboard input ignored: no focused editable field or unsupported key");
        }
    }

    public void handleMouse(JSONObject object, PointerState pointerState, AccessibilityWarning warning, Runnable accessibilityRequest) {
        if (!rateLimiter.tryAcquire()) {
            warnRateLimited("mouse");
            return;
        }
        boolean isDoubleClick;
        float x = (float)object.optDouble("x", 0.0);
        float y = (float)object.optDouble("y", 0.0);
        DisplayMetrics metrics = DisplayMetricsProvider.realMetrics(this.context);
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        float px = x <= 1.0f ? x * (float)width : x;
        float py = y <= 1.0f ? y * (float)height : y;
        String flags = object.optString("dwFlags", "");
        boolean isDown = "down".equals(flags) || object.optBoolean("down");
        boolean isUp = "up".equals(flags) || object.optBoolean("up");
        boolean isMove = "move".equals(flags) || object.optBoolean("move");
        boolean isWheel = "wheel".equals(flags) || object.optBoolean("wheel");
        boolean bl = isDoubleClick = "doubleClick".equals(flags) || object.optBoolean("doubleClick");
        if (isWheel) {
            int wheelData = object.optInt("mouseData", 0);
            float distance = Math.max(80.0f, Math.min((float)height * 0.32f, (float)Math.abs(wheelData) / 120.0f * (float)height * 0.18f));
            float toY = wheelData > 0 ? py + distance : py - distance;
            toY = Math.max(1.0f, Math.min((float)height - 1.0f, toY));
            reportResult(warning, RemoteInputAccessibilityService.swipe(px, py, px, toY), accessibilityRequest);
            return;
        }
        if (isDown) {
            pointerState.pointerDown = true;
            pointerState.downX = px;
            pointerState.downY = py;
            return;
        }
        if (isDoubleClick) {
            boolean first = RemoteInputAccessibilityService.tap(px, py);
            boolean second = first && RemoteInputAccessibilityService.tap(px, py);
            reportResult(warning, first && second, accessibilityRequest);
            return;
        }
        if (isUp) {
            if (!pointerState.pointerDown) {
                reportResult(warning, RemoteInputAccessibilityService.tap(px, py), accessibilityRequest);
                return;
            }
            float dx = px - pointerState.downX;
            float dy = py - pointerState.downY;
            boolean moved = dx * dx + dy * dy > 144.0f;
            boolean ok = moved ? RemoteInputAccessibilityService.swipe(pointerState.downX, pointerState.downY, px, py) : RemoteInputAccessibilityService.tap(px, py);
            pointerState.pointerDown = false;
            reportResult(warning, ok, accessibilityRequest);
            return;
        }
        if (isMove && !pointerState.pointerDown) {
            return;
        }
    }

    private void reportResult(AccessibilityWarning warning, boolean ok, Runnable accessibilityRequest) {
        if (warning == null) {
            return;
        }
        if (ok) {
            warning.warned = false;
            return;
        }
        if (!warning.warned) {
            warning.warned = true;
            status("Accessibility service is not enabled; input ignored");
            requireAccessibility(accessibilityRequest);
        }
    }

    private void status(String message) {
        if (this.listener != null) {
            this.listener.onStatus(message);
        }
    }

    private void warnRateLimited(String channel) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastDropWarningMs < 2000L) {
            return;
        }
        lastDropWarningMs = now;
        status("input rate limited: " + channel);
    }

    private void requireAccessibility(Runnable accessibilityRequest) {
        if (accessibilityRequest != null) {
            accessibilityRequest.run();
        }
    }

    public static final class AccessibilityWarning {
        public boolean warned;
    }
}
