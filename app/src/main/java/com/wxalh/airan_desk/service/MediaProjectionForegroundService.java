package com.wxalh.airan_desk.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import com.wxalh.airan_desk.MainActivity;

@SuppressWarnings({"deprecation", "unchecked"})
public class MediaProjectionForegroundService
extends Service {
    private static final String CHANNEL_ID = "airan_media_projection";
    private static final int NOTIFICATION_ID = 1003;
    private static volatile boolean running;

    public static void start(Context context) {
        if (context == null || running) {
            return;
        }
        Intent intent = new Intent(context, MediaProjectionForegroundService.class);
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
        context.stopService(new Intent(context, MediaProjectionForegroundService.class));
    }

    public static boolean isRunning() {
        return running;
    }

    public void onCreate() {
        super.onCreate();
        this.createChannel();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = this.buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            this.startForeground(1003, notification, 32);
        } else {
            this.startForeground(1003, notification);
        }
        running = true;
        return 1;
    }

    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager)this.getSystemService("notification");
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, (CharSequence)"Airan Desk screen capture", 2);
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
        return builder.setSmallIcon(17301678).setContentTitle((CharSequence)"Airan Desk").setContentText((CharSequence)"Screen sharing is active").setContentIntent(pendingIntent).setOngoing(true).build();
    }
}
