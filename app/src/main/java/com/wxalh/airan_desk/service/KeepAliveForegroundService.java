package com.wxalh.airan_desk.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import com.wxalh.airan_desk.MainActivity;

@SuppressWarnings({"deprecation", "unchecked"})
public class KeepAliveForegroundService
extends Service {
    private static final String CHANNEL_ID = "airan_keep_alive";
    private static final int NOTIFICATION_ID = 1004;
    private static volatile boolean running;
    private PowerManager.WakeLock wakeLock;

    public static void start(Context context) {
        if (context == null || running) {
            return;
        }
        Intent intent = new Intent(context, KeepAliveForegroundService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        if (context == null) {
            return;
        }
        context.stopService(new Intent(context, KeepAliveForegroundService.class));
    }

    public static boolean isRunning() {
        return running;
    }

    public void onCreate() {
        super.onCreate();
        this.createChannel();
        this.acquireWakeLock();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = this.buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                this.startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            }
            catch (Exception ex) {
                this.startForeground(NOTIFICATION_ID, notification);
            }
        } else {
            this.startForeground(NOTIFICATION_ID, notification);
        }
        running = true;
        this.acquireWakeLock();
        return 1;
    }

    public void onDestroy() {
        this.releaseWakeLock();
        running = false;
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    private void acquireWakeLock() {
        if (this.wakeLock != null && this.wakeLock.isHeld()) {
            return;
        }
        PowerManager manager = (PowerManager)this.getSystemService("power");
        if (manager == null) {
            return;
        }
        this.wakeLock = manager.newWakeLock(1, "AiranDesk:KeepAlive");
        this.wakeLock.setReferenceCounted(false);
        this.wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (this.wakeLock != null && this.wakeLock.isHeld()) {
            try {
                this.wakeLock.release();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        this.wakeLock = null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager)this.getSystemService("notification");
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, (CharSequence)"Airan Desk keep alive", 2);
        channel.setDescription("Keeps remote sessions connected after the screen is locked");
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent intent = new Intent((Context)this, MainActivity.class);
        intent.setFlags(0x24000000);
        int flags = 0x8000000;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= 0x4000000;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity((Context)this, (int)0, (Intent)intent, (int)flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder((Context)this, CHANNEL_ID) : new Notification.Builder((Context)this);
        return builder.setSmallIcon(17301611).setContentTitle((CharSequence)"Airan Desk").setContentText((CharSequence)"Keeping remote connection alive").setContentIntent(pendingIntent).setOngoing(true).build();
    }
}
