package com.wxalh.airan_desk.rtc;

import java.nio.ByteBuffer;
import org.json.JSONObject;
import org.webrtc.DataChannel;

final class DataChannelUtils {
    interface Listener {
        void onStatus(String message);
    }

    private DataChannelUtils() {
    }

    static boolean isOpen(DataChannel channel) {
        return channel != null && channel.state() == DataChannel.State.OPEN;
    }

    static String stateText(DataChannel channel) {
        return channel == null ? "null" : channel.label() + "/" + channel.state();
    }

    static boolean sendText(DataChannel channel, JSONObject object, Listener listener) {
        byte[] bytes;
        try {
            bytes = object.toString().getBytes("UTF-8");
        }
        catch (Exception e) {
            bytes = object.toString().getBytes();
        }
        return sendText(channel, bytes, listener);
    }

    static boolean sendText(DataChannel channel, byte[] bytes, Listener listener) {
        if (channel == null || channel.state() != DataChannel.State.OPEN) {
            notifyStatus(listener, "channel is not open");
            return false;
        }
        boolean sent = channel.send(new DataChannel.Buffer(ByteBuffer.wrap(bytes), false));
        if (!sent) {
            notifyStatus(listener, "data channel send failed");
        }
        return sent;
    }

    static String bytePreview(byte[] bytes) {
        String text;
        if (bytes == null || bytes.length == 0) {
            return "\"\"";
        }
        try {
            text = new String(bytes, "UTF-8");
        }
        catch (Exception e) {
            text = new String(bytes);
        }
        String preview = text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        if (preview.length() > 80) {
            preview = preview.substring(0, 80) + "...";
        }
        return "\"" + preview + "\"";
    }

    private static void notifyStatus(Listener listener, String message) {
        if (listener != null) {
            listener.onStatus(message);
        }
    }
}
