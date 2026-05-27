package com.wxalh.airan_desk.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class TerminalOutputDecoder {
    private final ByteArrayOutputStream charsetPending = new ByteArrayOutputStream();

    public void reset() {
        this.charsetPending.reset();
    }

    public byte[] normalizeBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        byte[] bytes = data;
        if (this.charsetPending.size() > 0) {
            byte[] pending = this.charsetPending.toByteArray();
            this.charsetPending.reset();
            bytes = new byte[pending.length + data.length];
            System.arraycopy(pending, 0, bytes, 0, pending.length);
            System.arraycopy(data, 0, bytes, pending.length, data.length);
        }

        int utf8Tail = incompleteUtf8TailLength(bytes);
        int utf8ReadyLength = bytes.length - utf8Tail;
        if (utf8Tail > 0 && isValidUtf8(bytes, 0, utf8ReadyLength)) {
            this.charsetPending.write(bytes, utf8ReadyLength, utf8Tail);
            if (utf8ReadyLength == 0) {
                return new byte[0];
            }
            byte[] ready = new byte[utf8ReadyLength];
            System.arraycopy(bytes, 0, ready, 0, utf8ReadyLength);
            return ready;
        }
        if (isValidUtf8(bytes, 0, bytes.length)) {
            return bytes;
        }

        int gbkTail = bytes.length > 0 && isGbkLeadByte(bytes[bytes.length - 1]) ? 1 : 0;
        int gbkReadyLength = bytes.length - gbkTail;
        if (gbkTail > 0) {
            this.charsetPending.write(bytes, gbkReadyLength, gbkTail);
        }
        if (gbkReadyLength <= 0) {
            return new byte[0];
        }
        try {
            String text = new String(bytes, 0, gbkReadyLength, Charset.forName("GBK"));
            return text.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            byte[] ready = new byte[gbkReadyLength];
            System.arraycopy(bytes, 0, ready, 0, gbkReadyLength);
            return ready;
        }
    }

    public String decodeText(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        String text;
        try {
            text = new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            text = new String(data);
        }
        if (text.indexOf(65533) >= 0) {
            try {
                String gbk = new String(data, Charset.forName("GBK"));
                if (gbk.indexOf(65533) < 0) {
                    text = gbk;
                }
            } catch (Exception ignored) {
            }
        }
        return text;
    }

    private static boolean isValidUtf8(byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            return true;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer ignored = decoder.decode(ByteBuffer.wrap(data, offset, length));
            return ignored != null;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static int incompleteUtf8TailLength(byte[] data) {
        if (data == null || data.length == 0) {
            return 0;
        }
        int leadIndex = data.length - 1;
        while (leadIndex >= 0) {
            int value = data[leadIndex] & 0xff;
            if (value < 0x80 || value > 0xbf) {
                break;
            }
            --leadIndex;
        }
        if (leadIndex < 0) {
            return 0;
        }
        int lead = data[leadIndex] & 0xff;
        int expected;
        if (lead >= 0xc2 && lead <= 0xdf) {
            expected = 2;
        } else if (lead >= 0xe0 && lead <= 0xef) {
            expected = 3;
        } else if (lead >= 0xf0 && lead <= 0xf4) {
            expected = 4;
        } else {
            return 0;
        }
        int available = data.length - leadIndex;
        return available < expected ? available : 0;
    }

    private static boolean isGbkLeadByte(byte value) {
        int unsigned = value & 0xff;
        return unsigned >= 0x81 && unsigned <= 0xfe;
    }
}
