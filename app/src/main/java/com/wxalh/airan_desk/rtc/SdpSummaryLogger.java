package com.wxalh.airan_desk.rtc;

import android.util.Log;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SdpSummaryLogger {
    private SdpSummaryLogger() {
    }

    static void log(String tag, String label, String sdp) {
        if (sdp == null || sdp.length() == 0) {
            return;
        }
        String[] lines = sdp.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.US);
            if (!line.startsWith("m=video") && !line.startsWith("m=audio") && !lower.startsWith("a=mid:") && !lower.startsWith("a=rtpmap:") && !lower.startsWith("a=fmtp:") && !lower.startsWith("a=ssrc:") && !lower.startsWith("a=msid:") && !lower.equals("a=sendonly") && !lower.equals("a=recvonly") && !lower.equals("a=sendrecv") && !lower.equals("a=inactive")) continue;
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            summary.append(line);
            if (++count < 32) continue;
            summary.append(" | ...");
            break;
        }
        Log.i((String)tag, (String)("SDP " + label + " summary: " + summary.toString()));
        Log.i((String)tag, (String)("SDP " + label + " video codecs: " + videoCodecSummary(sdp)));
    }

    static String firstVideoCodec(String sdp) {
        List<VideoCodec> codecs = videoCodecs(sdp);
        return codecs.isEmpty() ? "" : codecs.get(0).codec;
    }

    static String videoCodecSummary(String sdp) {
        List<VideoCodec> codecs = videoCodecs(sdp);
        if (codecs.isEmpty()) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        for (VideoCodec codec : codecs) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(codec.payloadType).append(':').append(codec.codec);
            if (codec.fmtp.length() > 0) {
                out.append('[').append(codec.fmtp).append(']');
            }
        }
        return out.toString();
    }

    private static List<VideoCodec> videoCodecs(String sdp) {
        ArrayList<VideoCodec> codecs = new ArrayList<VideoCodec>();
        if (sdp == null || sdp.length() == 0) {
            return codecs;
        }
        String[] lines = sdp.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        ArrayList<String> payloadOrder = new ArrayList<String>();
        LinkedHashMap<String, String> codecByPayload = new LinkedHashMap<String, String>();
        Map<String, String> fmtpByPayload = new LinkedHashMap<String, String>();
        boolean inVideo = false;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.startsWith("m=")) {
                inVideo = line.startsWith("m=video ");
                if (inVideo) {
                    String[] parts = line.split("\\s+");
                    for (int i = 3; i < parts.length; ++i) {
                        payloadOrder.add(parts[i]);
                    }
                }
                continue;
            }
            if (!inVideo) {
                continue;
            }
            if (line.startsWith("a=rtpmap:")) {
                int space = line.indexOf(' ');
                if (space <= 9) {
                    continue;
                }
                String payload = line.substring(9, space);
                String codec = line.substring(space + 1);
                int slash = codec.indexOf('/');
                if (slash >= 0) {
                    codec = codec.substring(0, slash);
                }
                codecByPayload.put(payload, codec.toUpperCase(Locale.US));
            } else if (line.startsWith("a=fmtp:")) {
                int space = line.indexOf(' ');
                if (space <= 7) {
                    continue;
                }
                fmtpByPayload.put(line.substring(7, space), line.substring(space + 1));
            }
        }

        for (String payload : payloadOrder) {
            String codec = codecByPayload.get(payload);
            if (codec == null || codec.length() == 0) {
                continue;
            }
            codecs.add(new VideoCodec(payload, codec, fmtpByPayload.containsKey(payload) ? fmtpByPayload.get(payload) : ""));
        }
        return codecs;
    }

    private static final class VideoCodec {
        final String payloadType;
        final String codec;
        final String fmtp;

        VideoCodec(String payloadType, String codec, String fmtp) {
            this.payloadType = payloadType == null ? "" : payloadType;
            this.codec = codec == null ? "" : codec;
            this.fmtp = fmtp == null ? "" : fmtp;
        }
    }
}
