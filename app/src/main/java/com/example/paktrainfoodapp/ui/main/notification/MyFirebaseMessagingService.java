package com.example.paktrainfoodapp.ui.main.notification;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.MainActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        Log.d("FCM_TOKEN", token);
    }

    /** Matches the key written by the Settings screen. */
    private boolean notificationsEnabled() {

        return getSharedPreferences("PakTrainSettings", MODE_PRIVATE)
                .getBoolean("notifications_enabled", true);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // The in-app toggle has to be honoured here: FCM still delivers the
        // message, so if we don't check the setting the notification appears
        // even after the user switched it off.
        if (!notificationsEnabled()) {
            Log.d("FCM", "Notification suppressed - disabled in app settings");
            return;
        }

        String title = remoteMessage.getData().get("title");
        String body = remoteMessage.getData().get("body");

        if (title == null) title = "";
        if (body == null) body = "";
        String orderId = remoteMessage.getData().get("orderId");
        String screen = remoteMessage.getData().get("screen");
        String status = remoteMessage.getData().get("status");
        String notificationType = remoteMessage.getData().get("notificationType");
        String deepLinkId = remoteMessage.getData().get("deepLinkId");

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("orderId", orderId);
        intent.putExtra("screen", screen);
        intent.putExtra("status", status);
        intent.putExtra("notificationType", notificationType);
        intent.putExtra("deepLinkId", deepLinkId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) (System.currentTimeMillis() % Integer.MAX_VALUE),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Bitmap largeIcon = BitmapFactory.decodeResource(
                getResources(),
                R.drawable.logo5
        );
        // "fullMessage" (sent by the payment/order functions) holds the long
        // version. The collapsed notification shows the short line; expanding
        // it reveals the detail.
        String fullMessage = remoteMessage.getData().get("fullMessage");

        if (fullMessage == null || fullMessage.isEmpty()) fullMessage = body;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "channel_id")
                        .setSmallIcon(R.drawable.logo5)
                        .setLargeIcon(largeIcon)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(fullMessage))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        int notificationId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

        NotificationManagerCompat.from(this)
                .notify(notificationId, builder.build());
    }
    }
