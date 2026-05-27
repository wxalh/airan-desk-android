package com.wxalh.airan_desk.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

public final class SettingsNavigator {
    public enum AutoStartResult {
        OPENED,
        FALLBACK
    }

    private SettingsNavigator() {
    }

    public static void openBatteryOptimizationSettings(Context context) {
        try {
            PowerManager manager;
            if (Build.VERSION.SDK_INT >= 23 && (manager = (PowerManager)context.getSystemService("power")) != null && !manager.isIgnoringBatteryOptimizations(context.getPackageName())) {
                Intent request = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
                request.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(request);
                return;
            }
            context.startActivity(new Intent("android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"));
        } catch (Exception e) {
            openAppDetailsSettings(context);
        }
    }

    public static AutoStartResult openAutoStartSettings(Context context) {
        Intent[] intents = new Intent[]{
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                componentIntent("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                componentIntent("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                componentIntent("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity"),
                componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity")
        };
        for (Intent intent : intents) {
            if (tryStartActivity(context, intent)) {
                return AutoStartResult.OPENED;
            }
        }
        openAppDetailsSettings(context);
        return AutoStartResult.FALLBACK;
    }

    public static void openAppDetailsSettings(Context context) {
        try {
            Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (Exception e) {
            context.startActivity(new Intent("android.settings.SETTINGS"));
        }
    }

    private static Intent componentIntent(String pkg, String cls) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(pkg, cls));
        intent.addFlags(0x10000000);
        return intent;
    }

    private static boolean tryStartActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
