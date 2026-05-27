package com.wxalh.airan_desk.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.wxalh.airan_desk.service.KeepAliveForegroundService;

@SuppressWarnings({"deprecation", "unchecked"})
public class BootReceiver
extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if ("android.intent.action.BOOT_COMPLETED".equals(action) || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            KeepAliveForegroundService.start(context);
        }
    }
}
