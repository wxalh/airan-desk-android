package com.wxalh.airan_desk.input;

public final class KeyboardChord {
    public final int keyCode;
    public final boolean shift;

    public KeyboardChord(int keyCode, boolean shift) {
        this.keyCode = keyCode;
        this.shift = shift;
    }
}
