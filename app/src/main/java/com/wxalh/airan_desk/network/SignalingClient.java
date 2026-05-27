package com.wxalh.airan_desk.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.wxalh.airan_desk.rtc.WebRtcClient;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

@SuppressWarnings({"deprecation", "unchecked"})
public class SignalingClient {
    private static final String TAG = "SignalingClient";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static OkHttpClient client;
    private static WebSocket webSocket;
    private static Listener listener;
    private static String currentUrl;
    private static boolean connected;
    private static boolean connecting;
    private static boolean autoReconnectEnabled;
    private static long reconnectDelayMs = 1500L;
    private static final Queue<String> pendingTextMessages;
    private static final Runnable reconnectRunnable;

    public static void setListener(Listener value) {
        listener = value;
    }

    public static synchronized void connect(String url) {
        Request req;
        SignalingClient.disconnect();
        currentUrl = url;
        connected = false;
        connecting = true;
        autoReconnectEnabled = true;
        MAIN.removeCallbacks(reconnectRunnable);
        SignalingClient.postConnecting(url);
        SignalingClient.probeDns(url);
        client = new OkHttpClient.Builder().connectTimeout(10L, TimeUnit.SECONDS).readTimeout(0L, TimeUnit.SECONDS).pingInterval(20L, TimeUnit.SECONDS).build();
        try {
            req = new Request.Builder().url(url).build();
        }
        catch (Exception e) {
            connecting = false;
            SignalingClient.postFailure("Invalid WebSocket URL: " + e.getMessage());
            return;
        }
        SignalingClient.postDebug("Opening WebSocket request: " + req.url());
        webSocket = client.newWebSocket(req, new WebSocketListener(){

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            public void onOpen(WebSocket ws, Response response) {
                Log.i((String)SignalingClient.TAG, (String)("WebSocket open: " + currentUrl));
                SignalingClient.postDebug("WebSocket handshake accepted: HTTP " + response.code());
                Class<SignalingClient> clazz = SignalingClient.class;
                synchronized (SignalingClient.class) {
                    if (webSocket != ws) {
                        // ** MonitorExit[var3_3] (shouldn't be in output)
                        return;
                    }
                    connected = true;
                    connecting = false;
                    reconnectDelayMs = 1500L;
                    while (!pendingTextMessages.isEmpty()) {
                        ws.send((String)pendingTextMessages.poll());
                    }
                    // ** MonitorExit[var3_3] (shouldn't be in output)
                    SignalingClient.postOpen();
                    return;
                }
            }

            public void onMessage(WebSocket ws, String text) {
                Listener l = listener;
                if (l != null) {
                    l.onTextMessage(text);
                } else {
                    WebRtcClient.onSignalingMessage(text);
                }
            }

            public void onMessage(WebSocket ws, ByteString bytes) {
                Listener l = listener;
                if (l != null) {
                    l.onBinaryMessage(bytes.toByteArray());
                }
            }

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            public void onClosed(WebSocket ws, int code, String reason) {
                Class<SignalingClient> clazz = SignalingClient.class;
                synchronized (SignalingClient.class) {
                    boolean activeSocket;
                    boolean bl = activeSocket = webSocket == ws;
                    if (!activeSocket) {
                        // ** MonitorExit[var5_4] (shouldn't be in output)
                        return;
                    }
                    connected = false;
                    connecting = false;
                    webSocket = null;
                    // ** MonitorExit[var5_4] (shouldn't be in output)
                    SignalingClient.postClosed(code + " " + reason);
                    SignalingClient.scheduleReconnect();
                    return;
                }
            }

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e((String)SignalingClient.TAG, (String)"WS failure", (Throwable)t);
                Class<SignalingClient> clazz = SignalingClient.class;
                synchronized (SignalingClient.class) {
                    if (webSocket != ws) {
                        // ** MonitorExit[var4_4] (shouldn't be in output)
                        return;
                    }
                    connected = false;
                    connecting = false;
                    webSocket = null;
                    pendingTextMessages.clear();
                    // ** MonitorExit[var4_4] (shouldn't be in output)
                    StringBuilder message = new StringBuilder();
                    message.append(t.getClass().getSimpleName()).append(": ");
                    message.append(t.getMessage() == null ? t.toString() : t.getMessage());
                    if (response != null) {
                        message.append(" HTTP ").append(response.code()).append(" ").append(response.message());
                    }
                    SignalingClient.postFailure(message.toString());
                    SignalingClient.scheduleReconnect();
                    return;
                }
            }
        });
    }

    public static synchronized void disconnect() {
        autoReconnectEnabled = false;
        MAIN.removeCallbacks(reconnectRunnable);
        if (webSocket != null) {
            webSocket.close(1000, "client-close");
            webSocket = null;
        }
        connected = false;
        connecting = false;
        pendingTextMessages.clear();
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client = null;
        }
    }

    public static synchronized boolean isConnected() {
        return connected;
    }

    public static synchronized boolean isConnecting() {
        return connecting;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void scheduleReconnect() {
        Class<SignalingClient> clazz = SignalingClient.class;
        synchronized (SignalingClient.class) {
            if (!autoReconnectEnabled || currentUrl == null || currentUrl.length() == 0) {
                // ** MonitorExit[var0] (shouldn't be in output)
                return;
            }
            MAIN.removeCallbacks(reconnectRunnable);
            long delay = reconnectDelayMs;
            MAIN.postDelayed(reconnectRunnable, delay);
            reconnectDelayMs = Math.min(reconnectDelayMs * 2L, 30000L);
            // ** MonitorExit[var0] (shouldn't be in output)
            return;
        }
    }

    public static void sendBinary(byte[] data) {
        WebSocket ws = webSocket;
        if (ws != null && data != null) {
            ws.send(ByteString.of((byte[])data));
        }
    }

    public static synchronized boolean sendText(String text) {
        WebSocket ws = webSocket;
        if (text == null) {
            return false;
        }
        if (ws != null && connected) {
            return ws.send(text);
        }
        if (ws != null && connecting) {
            pendingTextMessages.add(text);
            return true;
        }
        Log.w((String)TAG, (String)"sendText ignored because WebSocket is not connected");
        return false;
    }

    private static void postOpen() {
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                Listener l = listener;
                if (l != null) {
                    l.onOpen();
                }
            }
        });
    }

    private static void postConnecting(final String url) {
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                Listener l = listener;
                if (l != null) {
                    l.onConnecting(url);
                }
            }
        });
    }

    private static void postDebug(final String message) {
        Log.i((String)TAG, (String)message);
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                Listener l = listener;
                if (l != null) {
                    l.onDebug(message);
                }
            }
        });
    }

    private static void probeDns(final String url) {
        new Thread(new Runnable(){

            @Override
            public void run() {
                try {
                    URI uri = URI.create(url);
                    String host = uri.getHost();
                    if (host == null || host.length() == 0) {
                        SignalingClient.postDebug("DNS skipped: no host in URL");
                        return;
                    }
                    Object[] addresses = InetAddress.getAllByName(host);
                    SignalingClient.postDebug("DNS " + host + " -> " + Arrays.toString(addresses));
                }
                catch (Exception e) {
                    SignalingClient.postDebug("DNS failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }, "AiranDnsProbe").start();
    }

    private static void postClosed(final String reason) {
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                Listener l = listener;
                if (l != null) {
                    l.onClosed(reason);
                }
            }
        });
    }

    private static void postFailure(final String error) {
        MAIN.post(new Runnable(){

            @Override
            public void run() {
                Listener l = listener;
                if (l != null) {
                    l.onFailure(error);
                }
            }
        });
    }

    static {
        pendingTextMessages = new ArrayDeque<String>();
        reconnectRunnable = new Runnable(){

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            @Override
            public void run() {
                Class<SignalingClient> clazz = SignalingClient.class;
                synchronized (SignalingClient.class) {
                    if (!autoReconnectEnabled || connected || connecting || currentUrl == null || currentUrl.length() == 0) {
                        // ** MonitorExit[var2_1] (shouldn't be in output)
                        return;
                    }
                    String url = currentUrl;
                    // ** MonitorExit[var2_1] (shouldn't be in output)
                    SignalingClient.connect(url);
                    return;
                }
            }
        };
    }

    public static interface Listener {
        public void onConnecting(String var1);

        public void onDebug(String var1);

        public void onOpen();

        public void onClosed(String var1);

        public void onFailure(String var1);

        public void onTextMessage(String var1);

        public void onBinaryMessage(byte[] var1);
    }
}
