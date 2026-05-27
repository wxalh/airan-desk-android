package com.wxalh.airan_desk.util;

import com.wxalh.airan_desk.model.RemoteCredentials;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RemoteCredentialParser {
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern LABELED_PATTERN = Pattern.compile("(?is)(?:id|uuid|remote[_ -]?id|sessionId|\\u8bc6\\u522b\\u7801)[^0-9a-f]*([0-9a-f-]{16,64}).*?(?:pwd|password|code|\\u9a8c\\u8bc1\\u7801|\\u5bc6\\u7801)[^0-9a-f]*([0-9a-f-]{8,64})");

    private RemoteCredentialParser() {
    }

    public static RemoteCredentials parse(String text) {
        if (text == null) {
            return null;
        }
        String id = "";
        String pwd = "";
        Matcher matcher = UUID_PATTERN.matcher(text);
        if (matcher.find()) {
            id = matcher.group();
        }
        if (matcher.find()) {
            pwd = matcher.group();
        }
        if (id.length() == 0 || pwd.length() == 0) {
            Matcher labeledMatcher = LABELED_PATTERN.matcher(text);
            if (labeledMatcher.find()) {
                id = labeledMatcher.group(1);
                pwd = labeledMatcher.group(2);
            }
        }
        if (id.length() == 0 || pwd.length() == 0) {
            return null;
        }
        return new RemoteCredentials(normalize(id), normalize(pwd));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.replace("{", "").replace("}", "").trim();
    }
}
