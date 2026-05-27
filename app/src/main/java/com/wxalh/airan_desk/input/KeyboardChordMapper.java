package com.wxalh.airan_desk.input;

public final class KeyboardChordMapper {
    private KeyboardChordMapper() {
    }

    public static KeyboardChord fromChar(char c) {
        if (c >= 'a' && c <= 'z') {
            return new KeyboardChord(65 + c - 97, false);
        }
        if (c >= 'A' && c <= 'Z') {
            return new KeyboardChord(65 + c - 65, true);
        }
        if (c >= '0' && c <= '9') {
            return new KeyboardChord(48 + c - 48, false);
        }
        if (c == ' ') {
            return new KeyboardChord(32, false);
        }
        switch (c) {
            case '!':
                return new KeyboardChord(49, true);
            case '@':
                return new KeyboardChord(50, true);
            case '#':
                return new KeyboardChord(51, true);
            case '$':
                return new KeyboardChord(52, true);
            case '%':
                return new KeyboardChord(53, true);
            case '^':
                return new KeyboardChord(54, true);
            case '&':
                return new KeyboardChord(55, true);
            case '*':
                return new KeyboardChord(56, true);
            case '(':
                return new KeyboardChord(57, true);
            case ')':
                return new KeyboardChord(48, true);
            case '-':
                return new KeyboardChord(189, false);
            case '_':
                return new KeyboardChord(189, true);
            case '=':
                return new KeyboardChord(187, false);
            case '+':
                return new KeyboardChord(187, true);
            case '[':
                return new KeyboardChord(219, false);
            case '{':
                return new KeyboardChord(219, true);
            case ']':
                return new KeyboardChord(221, false);
            case '}':
                return new KeyboardChord(221, true);
            case '\\':
                return new KeyboardChord(220, false);
            case '|':
                return new KeyboardChord(220, true);
            case ';':
                return new KeyboardChord(186, false);
            case ':':
                return new KeyboardChord(186, true);
            case '\'':
                return new KeyboardChord(222, false);
            case '"':
                return new KeyboardChord(222, true);
            case ',':
                return new KeyboardChord(188, false);
            case '<':
                return new KeyboardChord(188, true);
            case '.':
                return new KeyboardChord(190, false);
            case '>':
                return new KeyboardChord(190, true);
            case '/':
                return new KeyboardChord(191, false);
            case '?':
                return new KeyboardChord(191, true);
            case '`':
                return new KeyboardChord(192, false);
            case '~':
                return new KeyboardChord(192, true);
            default:
                return null;
        }
    }
}
