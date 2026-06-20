package com.wawrapper.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PowerManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class WebSocketService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WAWrapper:WebSocketKeepAlive"
        );
        wakeLock.acquire(10 * 60 * 1000L);
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "websocket_channel",
                "WebSocket Service",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager mgr = getSystemService(NotificationManager.class);
            mgr.createNotificationChannel(channel);
        }

        return new Notification.Builder(this, "websocket_channel")
            .setContentTitle("WA Web Wrapper")
            .setContentText("Menjaga koneksi tetap aktif...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
