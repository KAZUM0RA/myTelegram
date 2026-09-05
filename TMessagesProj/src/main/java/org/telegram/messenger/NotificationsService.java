/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import org.telegram.ui.LaunchActivity;

/**
 * Служба, що тримає з'єднання, коли застосунок закритий.
 *
 * <p>Форк: служба переведена в ПЕРЕДНІЙ ПЛАН. В апстрімі вона була
 * звичайною фоновою й трималася трюком «вбили — перезапустись через
 * широкомовний сигнал» у {@link #onDestroy()}. До Android 8 цього
 * вистачало, але сучасна система, а надто MIUI, прибиває такі служби за
 * хвилини, і після цього перезапуск уже не дозволяється.
 *
 * <p>Офіційному Telegram це не заважає, бо в нього є push від Firebase, а
 * ця служба — лише запасний варіант. Нам push недоступний у принципі
 * (див. коментар у кінці TMessagesProj_App/build.gradle), тож живе
 * з'єднання — єдиний спосіб дізнатися про нове повідомлення. Постійне
 * з'єднання на сучасному Android дозволене лише службі переднього плану, а
 * та зобов'язана показувати сповіщення. Ціна помітна, але альтернатива —
 * не отримувати повідомлень зовсім.
 *
 * <p>Сповіщення навмисно в каналі з найнижчою важливістю: воно не звучить,
 * не показується поверх екрана і згортається в самий низ шторки.
 */
public class NotificationsService extends Service {

    private static final String CHANNEL_ID = "mytelegram_connection";
    private static final int NOTIFICATION_ID = 38471;

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Якщо службу запустили як foreground, система дає лише кілька секунд
        // на startForeground(), інакше застосунок падає. Тому робимо це
        // першою дією, до будь-якої іншої роботи.
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
        } catch (Throwable e) {
            FileLog.e("NotificationsService: не вдалося перейти в передній план");
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        createChannel();

        PendingIntent openApp = null;
        try {
            final Intent intent = new Intent(this, LaunchActivity.class);
            intent.setAction("com.tmessages.openchat");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            openApp = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } catch (Throwable ignored) {
            // Без дії по натисканню сповіщення лишається робочим.
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(LocaleController.getString(R.string.ConnectionServiceTitle))
                .setContentText(LocaleController.getString(R.string.ConnectionServiceText))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(openApp)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            final NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
                return;
            }
            final NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    LocaleController.getString(R.string.ConnectionServiceChannel),
                    NotificationManager.IMPORTANCE_MIN);
            channel.setDescription(LocaleController.getString(R.string.ConnectionServiceText));
            channel.setShowBadge(false);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);
            manager.createNotificationChannel(channel);
        } catch (Throwable e) {
            FileLog.e("NotificationsService: не вдалося створити канал сповіщень");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        super.onDestroy();
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        if (preferences.getBoolean("pushService", true)) {
            Intent intent = new Intent("org.telegram.start");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }
}
