package com.wxalh.airan_desk.input;

public final class KeyCodeMapper {
    private KeyCodeMapper() {
    }

    public static int androidKeyCodeToWinKey(int keyCode) {
        if (keyCode >= 29 && keyCode <= 54) {
            return 65 + (keyCode - 29);
        }
        if (keyCode >= 7 && keyCode <= 16) {
            return 48 + (keyCode - 7);
        }
        if (keyCode >= 131 && keyCode <= 142) {
            return 112 + (keyCode - 131);
        }
        switch (keyCode) {
            case 67:
                return 8;
            case 61:
                return 9;
            case 66:
                return 13;
            case 59:
            case 60:
                return 16;
            case 113:
            case 114:
                return 17;
            case 57:
            case 58:
                return 18;
            case 111:
                return 27;
            case 62:
                return 32;
            case 92:
                return 33;
            case 93:
                return 34;
            case 123:
                return 35;
            case 122:
                return 36;
            case 21:
                return 37;
            case 19:
                return 38;
            case 22:
                return 39;
            case 20:
                return 40;
            case 112:
                return 46;
            case 69:
                return 189;
            case 70:
                return 187;
            case 71:
                return 219;
            case 72:
                return 221;
            case 73:
                return 220;
            case 74:
                return 186;
            case 75:
                return 222;
            case 55:
                return 188;
            case 56:
                return 190;
            case 76:
                return 191;
            case 68:
                return 192;
            default:
                return 0;
        }
    }
}
