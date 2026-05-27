package com.wxalh.airan_desk.rtc;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

class LoggingSdpObserver implements SdpObserver {
    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
    }

    @Override
    public void onSetSuccess() {
    }

    @Override
    public void onCreateFailure(String s) {
        WebRtcClient.status("SDP create failed: " + s);
    }

    @Override
    public void onSetFailure(String s) {
        WebRtcClient.status("SDP set failed: " + s);
    }
}
