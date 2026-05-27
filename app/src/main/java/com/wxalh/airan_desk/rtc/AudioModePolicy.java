package com.wxalh.airan_desk.rtc;

public final class AudioModePolicy {
    public static final String OFF = "off";
    public static final String LISTEN = "listen";
    public static final String CALL = "call";

    private AudioModePolicy() {
    }

    public static String normalize(String mode) {
        if (LISTEN.equals(mode) || CALL.equals(mode)) {
            return mode;
        }
        return OFF;
    }

    public static boolean receivesRemoteAudio(String mode) {
        String normalized = normalize(mode);
        return LISTEN.equals(normalized) || CALL.equals(normalized);
    }

    public static boolean sendsMicrophoneAudio(String mode) {
        return CALL.equals(normalize(mode));
    }
}
