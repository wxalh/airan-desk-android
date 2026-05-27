package com.wxalh.airan_desk.rtc;

import android.util.Log;
import java.util.Locale;

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
    }
}
