package com.wxalh.airan_desk.rtc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SdpH264Patcher {
    public interface Listener {
        void onPatched(String label);
    }

    private static final String H264_DEFAULT_PROFILE_LEVEL_ID = "42e01f";
    private static final String H264_BITRATE_HINTS = "x-google-min-bitrate=2500;x-google-start-bitrate=6000;x-google-max-bitrate=18000";
    private static final String H264_FMTP_COMPAT = "profile-level-id=" + H264_DEFAULT_PROFILE_LEVEL_ID + ";level-asymmetry-allowed=1;packetization-mode=1;" + H264_BITRATE_HINTS;
    private static final String OPUS_FMTP_HIGH_QUALITY = "minptime=10;useinbandfec=1;usedtx=0;stereo=1;sprop-stereo=1;maxplaybackrate=48000;maxaveragebitrate=48000";

    private SdpH264Patcher() {
    }

    public static String forceH264Only(String sdp, String label, Listener listener) {
        return SdpH264Patcher.forceH264Only(sdp, label, listener, true);
    }

    public static String forceH264Only(String sdp, String label, Listener listener, boolean patchAudio) {
        if (sdp == null || sdp.length() == 0) {
            return sdp;
        }
        String normalized = sdp.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        ArrayList<String> out = new ArrayList<String>();
        int i = 0;
        boolean changed = false;
        while (i < lines.length) {
            String line = lines[i];
            if (line.startsWith("m=")) {
                ArrayList<String> section = new ArrayList<String>();
                section.add(line);
                ++i;
                while (i < lines.length && !lines[i].startsWith("m=")) {
                    section.add(lines[i]);
                    ++i;
                }
                if (line.startsWith("m=video")) {
                    PatchResult result = forceVideoH264OnlySection(section);
                    PatchResult bitrateResult = addVideoBandwidthLines(result.lines);
                    out.addAll(bitrateResult.lines);
                    changed |= result.changed || bitrateResult.changed;
                    continue;
                }
                if (patchAudio && line.startsWith("m=audio")) {
                    PatchResult result = patchAudioOpusSection(section);
                    out.addAll(result.lines);
                    changed |= result.changed;
                    continue;
                }
                out.addAll(section);
                continue;
            }
            out.add(line);
            ++i;
        }
        if (!changed) {
            return sdp;
        }
        if (listener != null) {
            listener.onPatched(label);
        }
        return joinSdpLines(out);
    }

    private static PatchResult addVideoBandwidthLines(List<String> section) {
        ArrayList<String> out = new ArrayList<String>();
        boolean hasAs = false;
        boolean hasTias = false;
        for (String line : section) {
            String lower = line.toLowerCase(Locale.US);
            hasAs |= lower.startsWith("b=as:");
            hasTias |= lower.startsWith("b=tias:");
        }
        boolean changed = false;
        for (String line : section) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.startsWith("b=as:")) {
                String value = "b=AS:18000";
                out.add(value);
                changed |= !value.equals(line);
                continue;
            }
            if (lower.startsWith("b=tias:")) {
                String value = "b=TIAS:18000000";
                out.add(value);
                changed |= !value.equals(line);
                continue;
            }
            out.add(line);
            if (line.startsWith("m=video")) {
                if (!hasAs) {
                    out.add("b=AS:18000");
                    hasAs = true;
                    changed = true;
                }
                if (!hasTias) {
                    out.add("b=TIAS:18000000");
                    hasTias = true;
                    changed = true;
                }
            }
        }
        return new PatchResult(out, changed);
    }

    private static PatchResult forceVideoH264OnlySection(List<String> section) {
        PatchResult patched = patchVideoH264Section(section);
        String selectedH264Payload = "";
        for (String line : patched.lines) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.startsWith("a=rtpmap:") && lower.contains(" h264/90000")) {
                String pt = payloadTypeFromAttribute(line, "a=rtpmap:");
                if (pt.length() > 0) {
                    selectedH264Payload = pt;
                    break;
                }
            }
        }
        if (selectedH264Payload.length() == 0) {
            return patched;
        }

        ArrayList<String> out = new ArrayList<String>();
        boolean changed = patched.changed;
        String pcH264Payload = "96";
        for (String line : patched.lines) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.startsWith("m=video")) {
                String[] parts = line.split(" ");
                StringBuilder mline = new StringBuilder();
                int fixedParts = Math.min(3, parts.length);
                for (int i = 0; i < fixedParts; ++i) {
                    if (i > 0) {
                        mline.append(' ');
                    }
                    mline.append(parts[i]);
                }
                mline.append(' ').append(pcH264Payload);
                String value = mline.toString();
                out.add(value);
                changed |= !value.equals(line);
                continue;
            }
            String pt = payloadTypeFromMediaAttribute(line);
            if (pt.length() > 0 && !selectedH264Payload.equals(pt)) {
                changed = true;
                continue;
            }
            if (lower.startsWith("a=rtpmap:") && selectedH264Payload.equals(payloadTypeFromAttribute(line, "a=rtpmap:"))) {
                String value = "a=rtpmap:" + pcH264Payload + " H264/90000";
                out.add(value);
                changed |= !value.equals(line);
                continue;
            }
            if (lower.startsWith("a=fmtp:") && selectedH264Payload.equals(payloadTypeFromAttribute(line, "a=fmtp:"))) {
                String patchedLine = patchH264FmtpLine(line);
                int space = patchedLine.indexOf(32);
                String value = "a=fmtp:" + pcH264Payload + (space >= 0 ? patchedLine.substring(space) : " " + H264_FMTP_COMPAT);
                out.add(value);
                changed |= !value.equals(line);
                continue;
            }
            if (lower.startsWith("a=rtcp-fb:") && selectedH264Payload.equals(payloadTypeFromAttribute(line, "a=rtcp-fb:"))) {
                int space = line.indexOf(32);
                String value = "a=rtcp-fb:" + pcH264Payload + (space >= 0 ? line.substring(space) : "");
                out.add(value);
                changed |= !value.equals(line);
                continue;
            }
            out.add(line);
        }
        return new PatchResult(out, changed);
    }

    private static PatchResult patchVideoH264Section(List<String> section) {
        ArrayList<String> h264Payloads = new ArrayList<String>();
        ArrayList<String> fmtpPayloads = new ArrayList<String>();
        for (String line : section) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.startsWith("a=rtpmap:") && lower.contains(" h264/90000")) {
                String pt = payloadTypeFromAttribute(line, "a=rtpmap:");
                if (pt.length() > 0 && !h264Payloads.contains(pt)) {
                    h264Payloads.add(pt);
                }
                continue;
            }
            if (lower.startsWith("a=fmtp:")) {
                String pt = payloadTypeFromAttribute(line, "a=fmtp:");
                if (pt.length() > 0 && !fmtpPayloads.contains(pt)) {
                    fmtpPayloads.add(pt);
                }
            }
        }
        if (h264Payloads.isEmpty()) {
            return new PatchResult(section, false);
        }

        ArrayList<String> out = new ArrayList<String>();
        boolean changed = false;
        for (String line : section) {
            String lower = line.toLowerCase(Locale.US);
            String pt = payloadTypeFromAttribute(line, "a=fmtp:");
            if (lower.startsWith("a=fmtp:") && h264Payloads.contains(pt)) {
                String patched = patchH264FmtpLine(line);
                out.add(patched);
                changed |= !patched.equals(line);
                continue;
            }
            out.add(line);
            if (lower.startsWith("a=rtpmap:") && lower.contains(" h264/90000")) {
                pt = payloadTypeFromAttribute(line, "a=rtpmap:");
                if (pt.length() > 0 && !fmtpPayloads.contains(pt)) {
                    out.add("a=fmtp:" + pt + " " + H264_FMTP_COMPAT);
                    changed = true;
                }
            }
        }
        return new PatchResult(out, changed);
    }

    private static PatchResult patchAudioOpusSection(List<String> section) {
        ArrayList<String> opusPayloads = new ArrayList<String>();
        ArrayList<String> fmtpPayloads = new ArrayList<String>();
        for (String line : section) {
            String lower = line.toLowerCase(Locale.US);
            if (lower.startsWith("a=rtpmap:") && lower.contains(" opus/48000")) {
                String pt = payloadTypeFromAttribute(line, "a=rtpmap:");
                if (pt.length() > 0 && !opusPayloads.contains(pt)) {
                    opusPayloads.add(pt);
                }
                continue;
            }
            if (lower.startsWith("a=fmtp:")) {
                String pt = payloadTypeFromAttribute(line, "a=fmtp:");
                if (pt.length() > 0 && !fmtpPayloads.contains(pt)) {
                    fmtpPayloads.add(pt);
                }
            }
        }
        if (opusPayloads.isEmpty()) {
            return new PatchResult(section, false);
        }

        ArrayList<String> out = new ArrayList<String>();
        boolean changed = false;
        for (String line : section) {
            String lower = line.toLowerCase(Locale.US);
            String pt = payloadTypeFromAttribute(line, "a=fmtp:");
            if (lower.startsWith("a=fmtp:") && opusPayloads.contains(pt)) {
                String patched = patchOpusFmtpLine(line);
                out.add(patched);
                changed |= !patched.equals(line);
                continue;
            }
            out.add(line);
            if (lower.startsWith("a=rtpmap:") && lower.contains(" opus/48000")) {
                pt = payloadTypeFromAttribute(line, "a=rtpmap:");
                if (pt.length() > 0 && !fmtpPayloads.contains(pt)) {
                    out.add("a=fmtp:" + pt + " " + OPUS_FMTP_HIGH_QUALITY);
                    changed = true;
                }
            }
        }
        return new PatchResult(out, changed);
    }

    private static String patchH264FmtpLine(String line) {
        int space = line.indexOf(32);
        if (space < 0) {
            return line + " " + H264_FMTP_COMPAT;
        }
        String prefix = line.substring(0, space + 1);
        String params = line.substring(space + 1);
        String profileLevelId = fmtpParamValue(params, "profile-level-id");
        if (profileLevelId.length() == 0) {
            profileLevelId = H264_DEFAULT_PROFILE_LEVEL_ID;
        }
        params = removeFmtpParam(params, "profile-level-id");
        params = removeFmtpParam(params, "level-asymmetry-allowed");
        params = removeFmtpParam(params, "packetization-mode");
        params = removeFmtpParam(params, "x-google-min-bitrate");
        params = removeFmtpParam(params, "x-google-start-bitrate");
        params = removeFmtpParam(params, "x-google-max-bitrate");
        String h264Params = "profile-level-id=" + profileLevelId + ";level-asymmetry-allowed=1;packetization-mode=1;" + H264_BITRATE_HINTS;
        return params.length() == 0 ? prefix + h264Params : prefix + params + ";" + h264Params;
    }

    private static String patchOpusFmtpLine(String line) {
        int space = line.indexOf(32);
        if (space < 0) {
            return line + " " + OPUS_FMTP_HIGH_QUALITY;
        }
        String prefix = line.substring(0, space + 1);
        String params = line.substring(space + 1);
        params = removeFmtpParam(params, "minptime");
        params = removeFmtpParam(params, "useinbandfec");
        params = removeFmtpParam(params, "usedtx");
        params = removeFmtpParam(params, "stereo");
        params = removeFmtpParam(params, "sprop-stereo");
        params = removeFmtpParam(params, "maxplaybackrate");
        params = removeFmtpParam(params, "maxaveragebitrate");
        return params.length() == 0 ? prefix + OPUS_FMTP_HIGH_QUALITY : prefix + params + ";" + OPUS_FMTP_HIGH_QUALITY;
    }

    private static String payloadTypeFromMediaAttribute(String line) {
        String lower = line.toLowerCase(Locale.US);
        if (lower.startsWith("a=rtpmap:")) {
            return payloadTypeFromAttribute(line, "a=rtpmap:");
        }
        if (lower.startsWith("a=fmtp:")) {
            return payloadTypeFromAttribute(line, "a=fmtp:");
        }
        if (lower.startsWith("a=rtcp-fb:")) {
            return payloadTypeFromAttribute(line, "a=rtcp-fb:");
        }
        return "";
    }

    private static String removeFmtpParam(String params, String key) {
        String[] parts = params.split(";");
        StringBuilder kept = new StringBuilder();
        for (String part : parts) {
            String item = part.trim();
            if (item.length() == 0 || item.toLowerCase(Locale.US).startsWith(key.toLowerCase(Locale.US) + "=")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append(';');
            }
            kept.append(item);
        }
        return kept.toString();
    }

    private static String fmtpParamValue(String params, String key) {
        String[] parts = params.split(";");
        String lowerKey = key.toLowerCase(Locale.US);
        for (String part : parts) {
            String item = part.trim();
            int eq = item.indexOf(61);
            if (eq <= 0) {
                continue;
            }
            String itemKey = item.substring(0, eq).trim().toLowerCase(Locale.US);
            if (lowerKey.equals(itemKey)) {
                return item.substring(eq + 1).trim();
            }
        }
        return "";
    }

    private static String payloadTypeFromAttribute(String line, String prefix) {
        if (line == null || !line.startsWith(prefix)) {
            return "";
        }
        int start = prefix.length();
        int end = line.indexOf(32, start);
        if (end < 0) {
            end = line.length();
        }
        return line.substring(start, end).trim();
    }

    private static String joinSdpLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); ++i) {
            if (i > 0) {
                builder.append("\r\n");
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private static final class PatchResult {
        final List<String> lines;
        final boolean changed;

        PatchResult(List<String> lines, boolean changed) {
            this.lines = lines;
            this.changed = changed;
        }
    }
}
