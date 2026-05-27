package com.wxalh.airan_desk.rtc;

import com.wxalh.airan_desk.config.AppConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.webrtc.PeerConnection;

final class RtcIceServerFactory {
    interface Listener {
        void onStatus(String message);
    }

    private RtcIceServerFactory() {
    }

    static List<PeerConnection.IceServer> build(AppConfig config, String networkPath, String defaultNetworkPath, Listener listener) {
        ArrayList<PeerConnection.IceServer> servers = new ArrayList<PeerConnection.IceServer>();
        String uri = config.iceUri();
        if (uri == null || uri.length() == 0) {
            return servers;
        }
        String normalizedPath = networkPath == null ? defaultNetworkPath : networkPath.toLowerCase(Locale.US);
        boolean forceTurn = "turn_udp".equals(normalizedPath) || "turn_tcp".equals(normalizedPath);
        boolean turnUri = isTurnUri(uri);
        if ("direct".equals(normalizedPath) && turnUri) {
            return servers;
        }
        if (forceTurn && !turnUri && listener != null) {
            listener.onStatus("TURN network path requested but ICE_URI is not TURN; using configured ICE server");
        }
        if (forceTurn && turnUri) {
            uri = withTransport(uri, "turn_udp".equals(normalizedPath) ? "udp" : "tcp");
        }
        PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder((String)uri);
        if (config.iceUser().length() > 0 || config.icePassword().length() > 0) {
            builder.setUsername(config.iceUser());
            builder.setPassword(config.icePassword());
        }
        servers.add(builder.createIceServer());
        return servers;
    }

    private static boolean isTurnUri(String uri) {
        if (uri == null) {
            return false;
        }
        String lower = uri.trim().toLowerCase(Locale.US);
        return lower.startsWith("turn:") || lower.startsWith("turns:");
    }

    private static String withTransport(String uri, String transport) {
        String lower = uri.toLowerCase(Locale.US);
        if (lower.contains("transport=")) {
            return uri;
        }
        return uri + (uri.indexOf(63) >= 0 ? "&" : "?") + "transport=" + transport;
    }
}
