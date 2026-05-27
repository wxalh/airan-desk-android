package com.wxalh.airan_desk.model;

public final class TransferRecord {
    public String transferId = "";
    public String direction = "";
    public String name = "";
    public String sourcePath = "";
    public String targetPath = "";
    public String localPath = "";
    public long transferredBytes = 0L;
    public long totalBytes = 0L;
    public boolean done = false;
    public boolean success = true;
    public long startedAt = 0L;
    public long updatedAt = 0L;
}
