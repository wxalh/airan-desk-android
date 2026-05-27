package com.wxalh.airan_desk.model;

public final class RemoteSession {
    public final String remoteId;
    public final String mode;

    public RemoteSession(String remoteId, String mode) {
        this.remoteId = remoteId == null ? "" : remoteId;
        this.mode = mode == null || mode.length() == 0 ? "desktop" : mode;
    }
}
