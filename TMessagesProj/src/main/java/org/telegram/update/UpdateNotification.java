/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

/**
 * Сповіщення про нову збірку.
 *
 * <p>Окремий канал, а не той, що в служби з'єднання: це різні за змістом
 * речі, і користувач має змогу вимкнути одне, не чіпаючи інше.
 */
final class UpdateNotification {

    private UpdateNotification() {
    }

    private static final String CHANNEL_ID = "mytelegram_update";
    private static final int NOTIFICATION_ID = 38472;

    static void show(String commit) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        try {
            createChannel(context);

            final Intent intent = new Intent(context, LaunchActivity.class);
            intent.setAction("com.tmessages.openchat");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            final PendingIntent open = PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            final Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(LocaleController.getString(R.string.UpdateNotificationTitle))
                    .setContentText(LocaleController.formatString(
                            R.string.UpdateNotificationText, commit))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build();

            // Дозвіл на сповіщення міг бути не наданий — тоді просто мовчимо:
            // це не та подія, заради якої варто турбувати проханнями.
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
        } catch (Throwable e) {
            FileLog.e("UpdateNotification: не вдалося показати сповіщення");
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        final NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                LocaleController.getString(R.string.UpdateNotificationChannel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
