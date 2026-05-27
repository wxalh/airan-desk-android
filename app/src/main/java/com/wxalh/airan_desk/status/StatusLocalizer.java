package com.wxalh.airan_desk.status;

import android.content.Context;
import com.wxalh.airan_desk.R;
import java.util.Locale;

public final class StatusLocalizer {
    private StatusLocalizer() {
    }

    public static String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    public static String localize(Context context, String message) {
        String text = normalize(message);
        if (text.length() == 0) {
            return "";
        }
        String upper = text.toUpperCase(Locale.US);
        if (upper.startsWith("ICE ")) {
            return context.getString(R.string.status_ice_state, text.substring(4));
        }
        if (upper.startsWith("SIGNALING ")) {
            return context.getString(R.string.status_signaling_state, text.substring("Signaling ".length()));
        }
        if (upper.startsWith("DATACHANNEL ")) {
            return context.getString(R.string.status_data_channel_state, text.substring("DataChannel ".length()));
        }
        if (upper.startsWith("SDP ")) {
            return context.getString(R.string.status_sdp_event, text.substring(4));
        }
        if (upper.startsWith("CONNECT SENT TO ")) {
            return context.getString(R.string.status_connect_sent, text.substring("CONNECT sent to ".length()));
        }
        if (upper.startsWith("BITRATE PROFILE: ")) {
            return context.getString(R.string.status_bitrate_profile, text.substring("bitrate profile: ".length()));
        }
        if (upper.startsWith("CAPTURE BACKEND: ")) {
            return context.getString(R.string.status_capture_backend, text.substring("capture backend: ".length()));
        }
        if (upper.startsWith("NETWORK PATH: ")) {
            return context.getString(R.string.status_network_path, text.substring("network path: ".length()));
        }
        if (upper.startsWith("DISPLAY MODE: ")) {
            return context.getString(R.string.status_display_mode, text.substring("display mode: ".length()));
        }
        if (upper.startsWith("RESOLUTION: ")) {
            return context.getString(R.string.status_resolution, text.substring("resolution: ".length()));
        }
        if (upper.startsWith("KEEP ALIVE START FAILED: ")) {
            return context.getString(R.string.status_keep_alive_failed, text.substring("keep alive start failed: ".length()));
        }
        if (upper.startsWith("SCREEN PERMISSION REQUEST FAILED: ")) {
            return context.getString(R.string.status_screen_permission_request_failed, text.substring("screen permission request failed: ".length()));
        }
        if (upper.startsWith("REMOTE PATH:")) {
            return context.getString(R.string.remote_path, text.substring(text.indexOf(58) + 1).trim());
        }
        if (upper.startsWith("TERMINAL:")) {
            return context.getString(R.string.status_terminal_ready, text.substring(text.indexOf(58) + 1).trim());
        }
        if (upper.startsWith("TERMINAL WEB READY")) {
            return context.getString(R.string.status_terminal_web_ready);
        }
        if (upper.startsWith("TERMINAL WEB NOT READY AFTER PAGE FINISHED")) {
            return context.getString(R.string.status_terminal_web_not_ready);
        }
        if (upper.startsWith("TERMINAL OUTPUT FLUSH")) {
            return context.getString(R.string.status_terminal_output);
        }
        if (upper.startsWith("TERMINAL OUTPUT PENDING")) {
            return context.getString(R.string.status_terminal_output_pending);
        }
        if (upper.startsWith("TERMINAL WAKE SCHEDULED")) {
            return context.getString(R.string.status_terminal_wake_scheduled);
        }
        if (upper.startsWith("TERMINAL WAKE INPUT SENT")) {
            return context.getString(R.string.status_terminal_wake_sent);
        }
        if (upper.startsWith("TERMINAL INPUT:")) {
            return context.getString(R.string.status_terminal_input);
        }
        if (upper.contains("REMOTE VIDEO TRACK ATTACHED")) {
            return context.getString(R.string.status_remote_video_attached);
        }
        if (upper.contains("REMOTE VIDEO TRACK CACHED")) {
            return context.getString(R.string.status_remote_video_cached);
        }
        if (upper.contains("WAITING FOR REMOTE MEDIA TRACKS")) {
            return context.getString(R.string.status_waiting_remote_media);
        }
        if (upper.contains("WEBRTC INITIALIZED")) {
            return context.getString(R.string.status_webrtc_initialized);
        }
        if (upper.contains("WEBRTC STOPPED")) {
            return context.getString(R.string.status_webrtc_stopped);
        }
        if (upper.contains("SCREEN CAPTURE PERMISSION READY")) {
            return context.getString(R.string.status_screen_permission_ready);
        }
        if (upper.contains("SCREEN CAPTURE PERMISSION DENIED")) {
            return context.getString(R.string.status_screen_permission_denied);
        }
        if (upper.contains("SCREEN PERMISSION REQUIRED")) {
            return context.getString(R.string.status_screen_permission_required);
        }
        if (upper.contains("SCREEN PERMISSION ALREADY READY")) {
            return context.getString(R.string.status_screen_permission_already_ready);
        }
        if (upper.contains("SCREEN PERMISSION REQUEST ALREADY OPEN") || upper.contains("SCREEN CAPTURE PERMISSION REQUEST ALREADY IN PROGRESS")) {
            return context.getString(R.string.status_screen_permission_request_open);
        }
        if (upper.contains("MEDIA PROJECTION FOREGROUND SERVICE STARTING")) {
            return context.getString(R.string.status_media_projection_starting);
        }
        if (upper.contains("ANDROID SCREEN TRACK STARTED")) {
            return context.getString(R.string.status_screen_track_started);
        }
        if (upper.contains("ICE CANDIDATE RECEIVED")) {
            return context.getString(R.string.status_ice_candidate_received);
        }
        if (upper.contains("WAITING FOR SCREEN CAPTURE PERMISSION")) {
            return context.getString(R.string.status_waiting_screen_permission);
        }
        if (upper.contains("WEBSOCKET IS NOT CONNECTED")) {
            return context.getString(R.string.status_websocket_not_connected);
        }
        if (upper.contains("REMOTE CREDENTIALS FILLED FROM PASTED TEXT")) {
            return context.getString(R.string.status_credentials_from_paste);
        }
        if (upper.contains("DOWNLOAD REQUEST SENT")) {
            return text;
        }
        if (upper.contains("UPLOAD FAILED")) {
            return context.getString(R.string.status_upload_failed);
        }
        if (upper.contains("COPIED")) {
            return context.getString(R.string.status_copied);
        }
        if (upper.contains("NOTHING SELECTED")) {
            return context.getString(R.string.status_nothing_selected);
        }
        if (upper.contains("CLIPBOARD IS EMPTY")) {
            return context.getString(R.string.status_clipboard_empty);
        }
        return text;
    }

    public static String connectionProgress(Context context, String message) {
        if (message == null) {
            return context.getString(R.string.waiting_remote_connection);
        }
        String normalized = message.toUpperCase(Locale.US);
        if (normalized.contains("WEBSOCKET") || normalized.contains("SIGNALING")) {
            return context.getString(R.string.connection_status_signaling);
        }
        if (normalized.contains("ICE CHECKING") || normalized.contains("ICE NEW")) {
            return context.getString(R.string.connection_status_checking_network);
        }
        if (normalized.contains("DATACHANNEL")) {
            return context.getString(R.string.connection_status_data_channel);
        }
        if (normalized.contains("ICE CONNECTED") || normalized.contains("ICE COMPLETED")) {
            return context.getString(R.string.remote_connected);
        }
        if (normalized.contains("FAILED") || normalized.contains("ERROR")) {
            return context.getString(R.string.connection_failed);
        }
        return context.getString(R.string.waiting_remote_connection);
    }

    public static String connectionFailureReason(Context context, String message) {
        String text = normalize(message);
        String normalized = text.toUpperCase(Locale.US);
        if (normalized.contains("PASSWORD_INCORRECT")) {
            return context.getString(R.string.connection_reason_password_incorrect);
        }
        if (normalized.contains("WEBSOCKET IS NOT CONNECTED") || normalized.contains("CONNECT WAS NOT SENT")) {
            return context.getString(R.string.connection_reason_websocket_not_connected);
        }
        if (normalized.contains("WEBSOCKET ERROR")) {
            int index = text.indexOf(':');
            String detail = index >= 0 && index + 1 < text.length() ? text.substring(index + 1).trim() : "";
            return detail.length() > 0 ? context.getString(R.string.connection_reason_websocket_error_with_detail, detail) : context.getString(R.string.connection_reason_websocket_error);
        }
        if (normalized.contains("ICE FAILED")) {
            return context.getString(R.string.connection_reason_ice_failed);
        }
        if (normalized.contains("ICE DISCONNECTED") || normalized.contains("REMOTE SESSION DISCONNECTED: DISCONNECTED")) {
            return context.getString(R.string.connection_reason_ice_disconnected);
        }
        if (normalized.contains("ICE CLOSED") || normalized.contains("REMOTE SESSION DISCONNECTED: CLOSED")) {
            return context.getString(R.string.connection_reason_closed);
        }
        if (normalized.startsWith("PEER ERROR:")) {
            String detail = text.substring("Peer error:".length()).trim();
            return detail.length() > 0 ? detail : context.getString(R.string.connection_reason_peer_error);
        }
        if (normalized.contains("CREATE FAILED")) {
            return context.getString(R.string.connection_reason_peer_create_failed);
        }
        if (text.length() > 0) {
            return localize(context, text);
        }
        return context.getString(R.string.connection_reason_unknown);
    }

    public static boolean isFatalConnectionStatus(String message) {
        String normalized = message == null ? "" : message.toUpperCase(Locale.US);
        return normalized.contains("ICE FAILED") || normalized.contains("ICE CLOSED") || normalized.contains("ICE DISCONNECTED") || normalized.contains("PEER ERROR") || normalized.contains("PASSWORD_INCORRECT") || normalized.contains("CREATE FAILED") || normalized.contains("WEBSOCKET IS NOT CONNECTED") || normalized.contains("CONNECT WAS NOT SENT");
    }
}
