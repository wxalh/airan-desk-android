package com.wxalh.airan_desk.input;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

@SuppressWarnings({"deprecation", "unchecked"})
public class RemoteInputAccessibilityService
extends AccessibilityService {
    private static RemoteInputAccessibilityService instance;
    private boolean shiftDown;
    private boolean ctrlDown;
    private boolean altDown;
    private boolean metaDown;

    public static boolean tap(float x, float y) {
        RemoteInputAccessibilityService service = instance;
        if (service == null || Build.VERSION.SDK_INT < 24) {
            return false;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, 80L)).build();
        return service.dispatchGesture(gesture, null, null);
    }

    public static boolean swipe(float fromX, float fromY, float toX, float toY) {
        RemoteInputAccessibilityService service = instance;
        if (service == null || Build.VERSION.SDK_INT < 24) {
            return false;
        }
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(toX, toY);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, 160L)).build();
        return service.dispatchGesture(gesture, null, null);
    }

    public static boolean isReady() {
        return instance != null;
    }

    public static boolean globalBack() {
        return RemoteInputAccessibilityService.performGlobal(1);
    }

    public static boolean globalHome() {
        return RemoteInputAccessibilityService.performGlobal(2);
    }

    public static boolean globalRecents() {
        return RemoteInputAccessibilityService.performGlobal(3);
    }

    public static boolean keyboard(int keyCode, boolean down) {
        RemoteInputAccessibilityService service = instance;
        return service != null && service.handleKeyboard(keyCode, down);
    }

    public static boolean inputText(String text) {
        RemoteInputAccessibilityService service = instance;
        return service != null && service.insertText(text);
    }

    private static boolean performGlobal(int action) {
        RemoteInputAccessibilityService service = instance;
        return service != null && service.performGlobalAction(action);
    }

    private boolean handleKeyboard(int keyCode, boolean down) {
        if (keyCode <= 0) {
            return false;
        }
        if (this.isShiftKey(keyCode)) {
            this.shiftDown = down;
            return true;
        }
        if (this.isCtrlKey(keyCode)) {
            this.ctrlDown = down;
            return true;
        }
        if (this.isAltKey(keyCode)) {
            this.altDown = down;
            return true;
        }
        if (this.isMetaKey(keyCode)) {
            this.metaDown = down;
            return true;
        }
        if (down) {
            return true;
        }
        if (this.ctrlDown || this.altDown || this.metaDown) {
            return false;
        }
        if (keyCode == 8) {
            return this.editFocusedText(null, true, false);
        }
        if (keyCode == 46) {
            return this.editFocusedText(null, false, true);
        }
        if (keyCode == 37) {
            return this.moveCursor(-1);
        }
        if (keyCode == 39) {
            return this.moveCursor(1);
        }
        if (keyCode == 36) {
            return this.moveCursorToStart();
        }
        if (keyCode == 35) {
            return this.moveCursorToEnd();
        }
        if (keyCode == 13) {
            return this.insertText("\n");
        }
        if (keyCode == 9) {
            return this.insertText("\t");
        }
        Character c = this.printableChar(keyCode, this.shiftDown);
        return c != null && this.insertText(String.valueOf(c.charValue()));
    }

    private boolean insertText(String text) {
        if (text == null || text.length() == 0) {
            return true;
        }
        return this.editFocusedText(text, false, false);
    }

    private boolean editFocusedText(String insert, boolean backspace, boolean delete) {
        AccessibilityNodeInfo node = this.focusedEditableNode();
        if (node == null) {
            return false;
        }
        CharSequence currentValue = node.getText();
        String current = currentValue == null ? "" : currentValue.toString();
        int start = this.selectionStart(node, current.length());
        int end = this.selectionEnd(node, current.length());
        int left = Math.min(start, end);
        int right = Math.max(start, end);
        String next = current;
        int cursor = left;
        if (insert != null) {
            next = current.substring(0, left) + insert + current.substring(right);
            cursor = left + insert.length();
        } else if (backspace) {
            if (left != right) {
                next = current.substring(0, left) + current.substring(right);
                cursor = left;
            } else if (left > 0) {
                next = current.substring(0, left - 1) + current.substring(right);
                cursor = left - 1;
            }
        } else if (delete) {
            if (left != right) {
                next = current.substring(0, left) + current.substring(right);
                cursor = left;
            } else if (right < current.length()) {
                next = current.substring(0, left) + current.substring(right + 1);
                cursor = left;
            }
        }
        return this.setNodeText(node, next, cursor);
    }

    private boolean moveCursor(int delta) {
        AccessibilityNodeInfo node = this.focusedEditableNode();
        if (node == null) {
            return false;
        }
        CharSequence currentValue = node.getText();
        String current = currentValue == null ? "" : currentValue.toString();
        int start = this.selectionStart(node, current.length());
        int end = this.selectionEnd(node, current.length());
        int left = Math.min(start, end);
        int right = Math.max(start, end);
        int cursor = delta < 0 ? left : right;
        cursor = this.clampSelection(cursor + delta, current.length());
        return this.setSelection(node, cursor);
    }

    private boolean moveCursorToStart() {
        AccessibilityNodeInfo node = this.focusedEditableNode();
        if (node == null) {
            return false;
        }
        return this.setSelection(node, 0);
    }

    private boolean moveCursorToEnd() {
        AccessibilityNodeInfo node = this.focusedEditableNode();
        if (node == null) {
            return false;
        }
        CharSequence currentValue = node.getText();
        int length = currentValue == null ? 0 : currentValue.length();
        return this.setSelection(node, length);
    }

    private AccessibilityNodeInfo focusedEditableNode() {
        AccessibilityNodeInfo root = this.getRootInActiveWindow();
        if (root == null) {
            return null;
        }
        AccessibilityNodeInfo focused = root.findFocus(1);
        if (this.isEditable(focused)) {
            return focused;
        }
        AccessibilityNodeInfo fallback = this.findFocusedEditable(root);
        if (fallback != null) {
            return fallback;
        }
        return null;
    }

    private AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (this.isEditable(node) && node.isFocused()) {
            return node;
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; ++i) {
            AccessibilityNodeInfo found = this.findFocusedEditable(node.getChild(i));
            if (found == null) continue;
            return found;
        }
        return null;
    }

    private boolean isEditable(AccessibilityNodeInfo node) {
        return node != null && node.isEditable() && node.isEnabled();
    }

    private int selectionStart(AccessibilityNodeInfo node, int fallback) {
        int value = node.getTextSelectionStart();
        return this.clampSelection(value < 0 ? fallback : value, fallback);
    }

    private int selectionEnd(AccessibilityNodeInfo node, int fallback) {
        int value = node.getTextSelectionEnd();
        return this.clampSelection(value < 0 ? fallback : value, fallback);
    }

    private int clampSelection(int value, int length) {
        return Math.max(0, Math.min(length, value));
    }

    private boolean setNodeText(AccessibilityNodeInfo node, String text, int cursor) {
        Bundle args = new Bundle();
        args.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", (CharSequence)text);
        boolean ok = node.performAction(0x200000, args);
        if (ok) {
            this.setSelection(node, cursor);
        }
        return ok;
    }

    private boolean setSelection(AccessibilityNodeInfo node, int cursor) {
        if (node == null) {
            return false;
        }
        Bundle selection = new Bundle();
        selection.putInt("ACTION_ARGUMENT_SELECTION_START_INT", cursor);
        selection.putInt("ACTION_ARGUMENT_SELECTION_END_INT", cursor);
        return node.performAction(131072, selection);
    }

    private boolean isShiftKey(int keyCode) {
        return keyCode == 16 || keyCode == 160 || keyCode == 161;
    }

    private boolean isCtrlKey(int keyCode) {
        return keyCode == 17 || keyCode == 162 || keyCode == 163;
    }

    private boolean isAltKey(int keyCode) {
        return keyCode == 18 || keyCode == 164 || keyCode == 165;
    }

    private boolean isMetaKey(int keyCode) {
        return keyCode == 91 || keyCode == 92;
    }

    private Character printableChar(int keyCode, boolean shift) {
        if (keyCode >= 65 && keyCode <= 90) {
            char base = (char)(97 + keyCode - 65);
            return shift ? Character.valueOf(Character.toUpperCase(base)) : Character.valueOf(base);
        }
        if (keyCode >= 48 && keyCode <= 57) {
            if (!shift) {
                return Character.valueOf((char)(48 + keyCode - 48));
            }
            switch (keyCode) {
                case 48: {
                    return Character.valueOf(')');
                }
                case 49: {
                    return Character.valueOf('!');
                }
                case 50: {
                    return Character.valueOf('@');
                }
                case 51: {
                    return Character.valueOf('#');
                }
                case 52: {
                    return Character.valueOf('$');
                }
                case 53: {
                    return Character.valueOf('%');
                }
                case 54: {
                    return Character.valueOf('^');
                }
                case 55: {
                    return Character.valueOf('&');
                }
                case 56: {
                    return Character.valueOf('*');
                }
                case 57: {
                    return Character.valueOf('(');
                }
            }
            return null;
        }
        if (keyCode == 32) {
            return Character.valueOf(' ');
        }
        switch (keyCode) {
            case 186: {
                return Character.valueOf(shift ? (char)':' : ';');
            }
            case 187: {
                return Character.valueOf(shift ? (char)'+' : '=');
            }
            case 188: {
                return Character.valueOf(shift ? (char)'<' : ',');
            }
            case 189: {
                return Character.valueOf(shift ? (char)'_' : '-');
            }
            case 190: {
                return Character.valueOf(shift ? (char)'>' : '.');
            }
            case 191: {
                return Character.valueOf(shift ? (char)'?' : '/');
            }
            case 192: {
                return Character.valueOf(shift ? (char)'~' : '`');
            }
            case 219: {
                return Character.valueOf(shift ? (char)'{' : '[');
            }
            case 220: {
                return Character.valueOf(shift ? (char)'|' : '\\');
            }
            case 221: {
                return Character.valueOf(shift ? (char)'}' : ']');
            }
            case 222: {
                return Character.valueOf(shift ? (char)'\"' : '\'');
            }
        }
        return null;
    }

    protected void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = this.getServiceInfo();
        if (info != null) {
            info.flags |= 0x50;
            this.setServiceInfo(info);
        }
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    public void onInterrupt() {
    }

    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }
}
