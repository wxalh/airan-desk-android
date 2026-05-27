package com.wxalh.airan_desk.model;

public final class RemoteCredentials {
    public final String remoteId;
    public final String password;

    public RemoteCredentials(String remoteId, String password) {
        this.remoteId = remoteId == null ? "" : remoteId;
        this.password = password == null ? "" : password;
    }
}
