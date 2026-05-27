package com.wxalh.airan_desk.status;

import com.wxalh.airan_desk.model.TransferRecord;

public final class TransferProgressFormatter {
    private TransferProgressFormatter() {
    }

    public static int progressPermille(long transferredBytes, long totalBytes, boolean done) {
        long total = Math.max(0L, totalBytes);
        long transferred = Math.max(0L, transferredBytes);
        if (total <= 0L) {
            return done ? 1000 : 0;
        }
        return (int)Math.min(1000L, transferred * 1000L / total);
    }

    public static int progressPermille(TransferRecord record) {
        if (record == null) {
            return 0;
        }
        return progressPermille(record.transferredBytes, record.totalBytes, record.done);
    }
}
