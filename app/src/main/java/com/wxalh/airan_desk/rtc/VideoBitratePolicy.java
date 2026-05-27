package com.wxalh.airan_desk.rtc;

final class VideoBitratePolicy {
    private VideoBitratePolicy() {
    }

    static int targetBps(int width, int height, int fps, String bitrateProfile, String defaultBitrateProfile) {
        int pixels = Math.max(1, width) * Math.max(1, height);
        int safeFps = Math.max(5, Math.min(60, fps));
        double bitsPerPixel = "low".equals(bitrateProfile) ? 0.085 : (defaultBitrateProfile.equals(bitrateProfile) ? 0.14 : 0.22);
        int estimated = (int)Math.round((double)(pixels * safeFps) * bitsPerPixel);
        if ("low".equals(bitrateProfile)) {
            return Math.max(1800000, Math.min(6000000, estimated));
        }
        if (defaultBitrateProfile.equals(bitrateProfile)) {
            return Math.max(4000000, Math.min(10000000, estimated));
        }
        return Math.max(7000000, Math.min(18000000, estimated));
    }
}
