package com.wxalh.airan_desk.terminal;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.JavascriptInterface;

public final class TerminalBridge {
    public interface Callback {
        void onReady();

        void onInput(String text);

        void onCopy(String text);

        void onSelectionChanged(boolean hasSelection);

        void onResize(int rows, int cols);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Callback callback;

    public TerminalBridge(Callback callback) {
        this.callback = callback;
    }

    @JavascriptInterface
    public void ready() {
        post(new Runnable() {
            @Override
            public void run() {
                callback.onReady();
            }
        });
    }

    @JavascriptInterface
    public void input(String base64) {
        final String text = decodeUtf8(base64);
        if (text == null) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                callback.onInput(text);
            }
        });
    }

    @JavascriptInterface
    public void copy(String base64) {
        final String text = decodeUtf8(base64);
        if (text == null) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                callback.onCopy(text);
            }
        });
    }

    @JavascriptInterface
    public void selectionChanged(final boolean hasSelection) {
        post(new Runnable() {
            @Override
            public void run() {
                callback.onSelectionChanged(hasSelection);
            }
        });
    }

    @JavascriptInterface
    public void resize(final int rows, final int cols) {
        post(new Runnable() {
            @Override
            public void run() {
                callback.onResize(rows, cols);
            }
        });
    }

    private void post(Runnable runnable) {
        if (this.callback == null) {
            return;
        }
        this.mainHandler.post(runnable);
    }

    private static String decodeUtf8(String base64) {
        try {
            byte[] bytes = Base64.decode(base64 == null ? "" : base64, 0);
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
