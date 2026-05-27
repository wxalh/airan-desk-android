package com.wxalh.airan_desk.terminal;

import android.util.Base64;
import org.json.JSONObject;

public final class TerminalInputDecoder {
    private TerminalInputDecoder() {
    }

    public static byte[] decode(JSONObject object) throws Exception {
        String data = object.optString("data", "");
        String encoding = object.optString("encoding", "");
        if ("base64".equalsIgnoreCase(encoding)) {
            return Base64.decode((String)data, (int)0);
        }
        return data.getBytes("UTF-8");
    }
}
