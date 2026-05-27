package com.wxalh.airan_desk.model;

public final class SessionInfo {
    public final String remoteId;
    public final String mode;
    public final boolean connected;
    public final boolean controlRole;

    public SessionInfo(String remoteId, String mode, boolean connected, boolean controlRole) {
        this.remoteId = remoteId == null ? "" : remoteId;
        this.mode = mode == null || mode.length() == 0 ? "desktop" : mode;
        this.connected = connected;
        this.controlRole = controlRole;
    }
}
