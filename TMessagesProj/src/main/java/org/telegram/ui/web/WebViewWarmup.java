/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui.web;

import android.content.Context;
import android.os.SystemClock;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

/**
 * Прогрів WebView для Mini Apps.
 *
 * <p>Перший WebView у процесі коштує дорого, і мережа тут ні до чого.
 * Система має підвантажити в наш процес APK системного WebView
 * (Android System WebView або Chrome — сотні мегабайт коду), а потім
 * підняти окремий процес рендерера. Поки це не сталося, відкриття
 * будь-якого бота-міні-аппа впирається саме в цю затримку.
 *
 * <p>Апстрім створює WebView лише тоді, коли користувач уже натиснув
 * кнопку бота, — тобто платить цю ціну в найгірший момент, коли на
 * екрані вже їде анімація шторки. Ми ж робимо ту саму роботу заздалегідь
 * і у фоні.
 *
 * <p>Прогрів свідомо <b>відкладений</b>: якби ми робили його прямо в
 * {@code ApplicationLoader.onCreate()}, то сповільнили б холодний старт
 * застосунку — а це шлях, яким користувач ходить у сотні разів частіше,
 * ніж відкриває міні-аппи. Тому чекаємо, поки застосунок закінчить
 * запускатись, і тільки тоді торкаємось WebView.
 */
public class WebViewWarmup {

    /**
     * Затримка від старту процесу. Достатньо велика, щоб не конкурувати
     * з відмальовуванням списку чатів, і достатньо мала, щоб устигнути
     * до того, як користувач дійде до бота.
     */
    private static final long WARMUP_DELAY_MS = 4000;

    /** Прогрів має сенс рівно один раз за життя процесу. */
    private static volatile boolean scheduled;
    private static volatile boolean done;

    private WebViewWarmup() {}

    /**
     * Ставить прогрів у чергу. Викликати можна скільки завгодно разів —
     * спрацює лише перший.
     */
    public static void scheduleWarmup(Context context) {
        if (scheduled || context == null) {
            return;
        }
        scheduled = true;
        final Context appContext = context.getApplicationContext();
        AndroidUtilities.runOnUIThread(() -> warmUp(appContext), WARMUP_DELAY_MS);
    }

    /**
     * Чи прогріто. Потрібне лише для логів — щоб при вимірюванні було
     * видно, чи відкриття міні-аппа скористалось прогрітим станом.
     */
    public static boolean isDone() {
        return done;
    }

    private static void warmUp(Context context) {
        if (done) {
            return;
        }
        final long start = SystemClock.elapsedRealtime();
        try {
            // Крок 1: підвантажує провайдер WebView у процес, не створюючи
            // жодного View. Найдорожча частина — саме вона.
            final String userAgent = WebSettings.getDefaultUserAgent(context);

            final long afterProvider = SystemClock.elapsedRealtime();

            // Крок 2: одноразовий WebView піднімає процес рендерера й
            // ініціалізує GPU-канал. Порожня сторінка потрібна, щоб
            // рендерер справді стартував, а не лише виділив об'єкти.
            final WebView probe = new WebView(context);
            probe.getSettings().setJavaScriptEnabled(false);
            probe.loadUrl("about:blank");

            // Тримати його довше сенсу немає: усе прогріте лишається на
            // рівні процесу, а не цього конкретного екземпляра. Знищуємо
            // з наступного проходу лупера, коли about:blank уже опрацьовано.
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    probe.destroy();
                } catch (Throwable ignored) {
                }
            }, 500);

            done = true;

            // FileLog.d сам перевіряє LOGS_ENABLED, тож у release-збірці
            // цей рядок нічого не коштує.
            FileLog.d("WebViewWarmup: провайдер " + (afterProvider - start) + " мс"
                    + ", рендерер " + (SystemClock.elapsedRealtime() - afterProvider) + " мс"
                    + ", UA=" + (userAgent == null ? "null" : userAgent.length() + " символів"));
        } catch (Throwable e) {
            // WebView може бути відсутнім, вимкненим або саме оновлюватись —
            // тоді конструктор кидає AndroidRuntimeException. Для нас це не
            // помилка: прогрів необов'язковий, міні-аппи просто відкриються
            // повільніше або покажуть штатну помилку самі.
            FileLog.e("WebViewWarmup: прогрів не вдався, працюємо без нього", e);
        }
    }
}
