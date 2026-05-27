package com.wxalh.airan_desk.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import com.wxalh.airan_desk.Md5Util;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings({"deprecation", "unchecked"})
public final class AppConfig {
    private static final String PREFS = "airan_config";
    private static final String KEY_LOCAL_ID = "local_id";
    private static final String KEY_LOCAL_PWD = "local_pwd";
    private static final String KEY_INSTALL_ID = "install_id";
    private static final String KEY_WS_URL = "ws_url";
    private static final String KEY_ICE_URI = "ice_uri";
    private static final String KEY_ICE_USER = "ice_user";
    private static final String KEY_ICE_PASS = "ice_pass";
    private static final String KEY_LANGUAGE = "language";
    private final SharedPreferences prefs;

    public AppConfig(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, 0);
        this.ensureIdentity();
    }

    private void ensureIdentity() {
        SharedPreferences.Editor editor = this.prefs.edit();
        boolean changed = false;
        String localId = this.prefs.getString(KEY_LOCAL_ID, null);
        if (localId == null) {
            editor.putString(KEY_LOCAL_ID, this.newUuidText());
            changed = true;
        } else {
            String normalized = this.normalizeUuidText(localId);
            if (!normalized.equals(localId)) {
                editor.putString(KEY_LOCAL_ID, normalized);
                changed = true;
            }
        }
        String localPwd = this.prefs.getString(KEY_LOCAL_PWD, null);
        if (localPwd == null) {
            editor.putString(KEY_LOCAL_PWD, this.newLocalPassword());
            changed = true;
        } else {
            String normalized = this.normalizeLocalPassword(localPwd);
            if (!normalized.equals(localPwd)) {
                editor.putString(KEY_LOCAL_PWD, normalized);
                changed = true;
            }
        }
        if (this.prefs.getString(KEY_INSTALL_ID, null) == null) {
            editor.putString(KEY_INSTALL_ID, UUID.randomUUID().toString());
            changed = true;
        }
        if (changed) {
            editor.apply();
        }
    }

    public String localId() {
        return this.normalizeUuidText(this.prefs.getString(KEY_LOCAL_ID, ""));
    }

    public String localPassword() {
        return this.normalizeLocalPassword(this.prefs.getString(KEY_LOCAL_PWD, ""));
    }

    public String localPasswordMd5() {
        return Md5Util.md5Upper(this.localPassword());
    }

    public void rotatePassword() {
        this.prefs.edit().putString(KEY_LOCAL_PWD, this.newLocalPassword()).apply();
    }

    public void replaceLocalId(String localId) {
        this.prefs.edit().putString(KEY_LOCAL_ID, this.normalizeUuidText(localId)).apply();
    }

    private String newLocalPassword() {
        return this.newUuidText();
    }

    private String normalizeLocalPassword(String value) {
        return this.normalizeUuidText(value);
    }

    private String newUuidText() {
        return UUID.randomUUID().toString().toUpperCase(Locale.US);
    }

    private String normalizeUuidText(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().toUpperCase(Locale.US);
        if (trimmed.matches("^[0-9A-F]{32}$")) {
            return trimmed.substring(0, 8) + "-" + trimmed.substring(8, 12) + "-" + trimmed.substring(12, 16) + "-" + trimmed.substring(16, 20) + "-" + trimmed.substring(20);
        }
        return trimmed;
    }

    public String wsUrl() {
        return this.prefs.getString(KEY_WS_URL, "ws://airan-desk.wxalh.com:3480");
    }

    public void setWsUrl(String url) {
        this.prefs.edit().putString(KEY_WS_URL, url == null ? "" : url.trim()).apply();
    }

    public String iceUri() {
        return this.prefs.getString(KEY_ICE_URI, "stun:coturn.wxalh.com:3478");
    }

    public void setIceUri(String uri) {
        this.prefs.edit().putString(KEY_ICE_URI, uri == null ? "" : uri.trim()).apply();
    }

    public String iceUser() {
        return this.prefs.getString(KEY_ICE_USER, "coturn");
    }

    public void setIceUser(String value) {
        this.prefs.edit().putString(KEY_ICE_USER, value == null ? "" : value.trim()).apply();
    }

    public String icePassword() {
        return this.prefs.getString(KEY_ICE_PASS, "123456");
    }

    public void setIcePassword(String value) {
        this.prefs.edit().putString(KEY_ICE_PASS, value == null ? "" : value).apply();
    }

    public String language() {
        return this.prefs.getString(KEY_LANGUAGE, "zh");
    }

    public void setLanguage(String language) {
        this.prefs.edit().putString(KEY_LANGUAGE, language == null ? "zh" : language).apply();
    }

    public String buildWsUrl(Context context) {
        Uri base = Uri.parse((String)this.wsUrl());
        Uri.Builder builder = base.buildUpon().clearQuery();
        for (String name : base.getQueryParameterNames()) {
            if ("sessionId".equals(name) || "hostname".equals(name) || "installId".equals(name)) continue;
            builder.appendQueryParameter(name, base.getQueryParameter(name));
        }
        builder.appendQueryParameter("sessionId", this.localId());
        builder.appendQueryParameter("hostname", Build.MODEL == null ? "Android" : Build.MODEL);
        builder.appendQueryParameter("installId", this.prefs.getString(KEY_INSTALL_ID, ""));
        return builder.build().toString();
    }
}
