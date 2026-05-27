package com.wxalh.airan_desk.rtc;

import org.json.JSONObject;

final class FileTransferHeaders {
    static final String TRANSFER_BASE_BYTES = "transferBaseBytes";
    static final String TRANSFER_FILE_INDEX = "transferFileIndex";

    private FileTransferHeaders() {
    }

    static JSONObject fileHeader(String msgType, String pathCtl, String pathCli, String transferId, long transferTotalBytes, int transferTotalFiles, long baseBytes, long fileIndex, long fileSize, boolean includeDirectoryFlag) throws Exception {
        JSONObject header = new JSONObject();
        header.put("msgType", (Object)msgType);
        header.put("path_ctl", (Object)pathCtl);
        header.put("path_cli", (Object)pathCli);
        header.put("transferId", (Object)transferId);
        header.put("transferTotalBytes", transferTotalBytes);
        header.put("transferTotalFiles", transferTotalFiles);
        header.put(TRANSFER_BASE_BYTES, baseBytes);
        header.put(TRANSFER_FILE_INDEX, fileIndex);
        header.put("file_size", fileSize);
        if (includeDirectoryFlag) {
            header.put("isDirectory", false);
        }
        return header;
    }

    static JSONObject directoryMarker(String msgType, String pathCtl, String pathCli, String transferId, boolean start, boolean end, boolean ok, int fileCount, long transferredBytes, long totalBytes, int totalFiles) throws Exception {
        JSONObject header = new JSONObject();
        header.put("msgType", (Object)msgType);
        header.put("path_ctl", (Object)pathCtl);
        header.put("path_cli", (Object)pathCli);
        header.put("transferId", (Object)transferId);
        header.put("transferTotalBytes", totalBytes);
        header.put("transferTotalFiles", totalFiles);
        header.put("isDirectory", true);
        header.put("directoryStart", start);
        header.put("directoryEnd", end);
        header.put("fileCount", fileCount);
        header.put("status", ok);
        header.put(TRANSFER_BASE_BYTES, transferredBytes);
        return header;
    }
}
