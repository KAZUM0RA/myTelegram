/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TranslateAlert2;

import java.util.HashSet;
import java.util.Set;

/**
 * Автопереклад вхідних повідомлень через Claude.
 *
 * <p>Вбудований автопереклад Telegram доступний лише з Premium
 * ({@code TranslateController.isFeatureAvailable} перевіряє {@code isPremium}).
 * Обходити цю перевірку ми не будемо — натомість робимо свій, на власному
 * двигуні.
 *
 * <p>Результат кладемо в те саме поле {@code messageOwner.translatedText},
 * яким користується Telegram. Завдяки цьому безкоштовно отримуємо три речі:
 * показ перекладу в чаті без жодних правок у {@code ChatMessageCell},
 * збереження в базі (кеш переживає перезапуск застосунку) і штатне
 * оновлення інтерфейсу.
 *
 * <p><b>Про витрати.</b> Кожне нове повідомлення — це запит до API. Тому:
 * перекладаємо лише вхідні, лише в чатах, де користувач сам увімкнув
 * автопереклад, лише те, що є на екрані, і ніколи двічі те саме. Плюс
 * обмеження на кількість одночасних запитів, щоб відкриття чату з довгою
 * історією не перетворилось на десятки викликів одразу.
 */
public class AiAutoTranslate {

    private static final String PREFS = "ai_autotranslate";

    /**
     * Скільки перекладів дозволяємо одночасно. Верхня межа радше про гроші,
     * ніж про продуктивність: при прокрутці чату видимих повідомлень може
     * бути з десяток, і слати їх усі одночасно — швидкий спосіб здивуватися
     * рахунку.
     */
    private static final int MAX_IN_FLIGHT = 3;

    /** Повідомлення, для яких запит уже в дорозі. Захист від дублювання. */
    private static final Set<Integer> inFlight = new HashSet<>();

    private AiAutoTranslate() {}

    public static boolean isEnabled(long dialogId) {
        return prefs().getBoolean(String.valueOf(dialogId), false);
    }

    public static void setEnabled(long dialogId, boolean enabled) {
        if (enabled) {
            prefs().edit().putBoolean(String.valueOf(dialogId), true).apply();
            // Даємо помилці шанс показатися знову: користувач міг щойно
            // виправити ключ і вмикає автопереклад повторно.
            errorReported = false;
        } else {
            prefs().edit().remove(String.valueOf(dialogId)).apply();
        }
    }

    /**
     * Викликається для кожного повідомлення в чаті.
     *
     * @param onScreen чи є повідомлення у видимому діапазоні — саме це
     *                 обмеження не дає перекладати всю історію чату
     */
    public static void check(int currentAccount, MessageObject messageObject, boolean onScreen) {
        if (!onScreen || messageObject == null || messageObject.messageOwner == null) {
            return;
        }
        if (!AiConfig.isReady() || !isEnabled(messageObject.getDialogId())) {
            return;
        }
        // Свої повідомлення перекладати немає сенсу: ти й так знаєш, що написав.
        if (messageObject.isOutOwner()) {
            return;
        }
        final String target = targetLanguageCode();
        if (TextUtils.isEmpty(target)) {
            return;
        }
        // Уже перекладено на потрібну мову — нічого не робимо. Це і є кеш:
        // translatedText зберігається в базі разом із повідомленням.
        if (messageObject.messageOwner.translatedText != null
                && target.equals(messageObject.messageOwner.translatedToLanguage)) {
            return;
        }
        final CharSequence text = messageObject.messageOwner.message;
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.toString().trim())) {
            return;
        }
        // Не перекладаємо те, що вже й так цією мовою.
        if (target.equals(messageObject.messageOwner.originalLanguage)) {
            return;
        }

        final int id = messageObject.getId();
        synchronized (inFlight) {
            if (inFlight.contains(id) || inFlight.size() >= MAX_IN_FLIGHT) {
                return;
            }
            inFlight.add(id);
        }

        AiClient.translate(text.toString(), TranslateAlert2.languageName(target), new AiClient.Callback() {
            @Override
            public void onSuccess(String translated) {
                release(id);
                apply(currentAccount, messageObject, translated, target);
            }

            @Override
            public void onError(String message) {
                release(id);
                // Про кожну невдачу не повідомляємо — автопереклад працює у
                // фоні, і потік сповіщень дратував би. Але ПЕРШУ показуємо:
                // інакше при невалідному ключі чи відсутній мережі функція
                // просто мовчить, і зрозуміти, що не так, неможливо.
                reportFirstError(message);
            }
        });
    }

    private static void release(int id) {
        synchronized (inFlight) {
            inFlight.remove(id);
        }
    }

    /**
     * Чи показували вже помилку в цьому сеансі. Скидається при вмиканні
     * автоперекладу — щоб після виправлення ключа перша невдача знову була
     * помітною.
     */
    private static volatile boolean errorReported;

    private static void reportFirstError(String message) {
        if (errorReported || TextUtils.isEmpty(message)) {
            return;
        }
        errorReported = true;
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.showBulletin,
                org.telegram.ui.Components.Bulletin.TYPE_ERROR,
                message);
    }

    /**
     * Кладе переклад туди ж, куди його кладе Telegram, і повідомляє інтерфейс.
     * Той самий порядок дій, що в {@code TranslateController}: спершу поля,
     * потім запис у базу, потім сповіщення.
     */
    private static void apply(int currentAccount, MessageObject messageObject,
                              String translated, String language) {
        if (TextUtils.isEmpty(translated)) {
            return;
        }
        final TLRPC.TL_textWithEntities result = new TLRPC.TL_textWithEntities();
        result.text = translated;

        messageObject.messageOwner.translatedToLanguage = language;
        messageObject.messageOwner.translatedText = result;

        MessagesStorage.getInstance(currentAccount)
                .updateMessageCustomParams(messageObject.getDialogId(), messageObject.messageOwner);
        NotificationCenter.getInstance(currentAccount)
                .postNotificationName(NotificationCenter.messageTranslated, messageObject);
    }

    /** Код мови, якою читає користувач. */
    private static String targetLanguageCode() {
        final String configured = AiConfig.getTargetLanguage();
        if (!TextUtils.isEmpty(configured)) {
            return configured;
        }
        try {
            return org.telegram.messenger.LocaleController.getInstance()
                    .getCurrentLocale().getLanguage();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
