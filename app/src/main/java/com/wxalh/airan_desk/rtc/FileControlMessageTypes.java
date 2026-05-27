package com.wxalh.airan_desk.rtc;

final class FileControlMessageTypes {
    private FileControlMessageTypes() {
    }

    static boolean isFragmentedControlMessage(String msgType) {
        return "file_list".equals(msgType) || "upload_file_res".equals(msgType) || "file_transfer_progress".equals(msgType) || "file_transfer_cancel".equals(msgType) || "terminal_start".equals(msgType) || "terminal_input".equals(msgType) || "terminal_resize".equals(msgType) || "terminal_output".equals(msgType) || "terminal_stop".equals(msgType) || "terminal_closed".equals(msgType) || "terminal_error".equals(msgType) || "terminal_info".equals(msgType) || "run_file".equals(msgType);
    }
}
