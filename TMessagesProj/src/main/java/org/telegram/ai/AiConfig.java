/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Налаштування AI-функцій.
 *
 * <p>Тут лежить усе, крім самого ключа — він у {@link AiKeyStorage}, бо
 * потребує шифрування, а модель чи мова секретами не є.
 */
public class AiConfig {

    private static final String PREFS = "ai_config";

    private static final String PREF_MODEL = "model";
    private static final String PREF_TARGET_LANG = "target_lang";

    /**
     * Модель за замовчуванням.
     *
     * <p>Haiku 4.5 — найдешевша з поточних ($1/$5 за мільйон токенів). Для
     * перекладу коротких повідомлень різниця з дорожчими моделями майже не
     * помітна, а вартість відрізняється в рази: типовий переклад виходить
     * близько $0.0004, тобто тисяча перекладів — менше половини долара.
     */
    public static final String MODEL_HAIKU = "claude-haiku-4-5";
    public static final String MODEL_SONNET = "claude-sonnet-5";
    public static final String MODEL_OPUS = "claude-opus-5";

    /** Моделі для вибору в налаштуваннях: id та підпис для користувача. */
    public static final String[][] AVAILABLE_MODELS = {
            {MODEL_HAIKU,  "Haiku 4.5 — найдешевша, швидка"},
            {MODEL_SONNET, "Sonnet 5 — точніша з тоном"},
            {MODEL_OPUS,   "Opus 5 — найкраща якість, найдорожча"},
    };

    /**
     * Порожня цільова мова означає «визначати автоматично» — брати мову
     * останнього вхідного повідомлення в чаті.
     */
    public static final String TARGET_LANG_AUTO = "";

    private AiConfig() {}

    public static String getModel() {
        return prefs().getString(PREF_MODEL, MODEL_HAIKU);
    }

    public static void setModel(String model) {
        prefs().edit().putString(PREF_MODEL, model).apply();
    }

    /** Людська назва поточної моделі для екрана налаштувань. */
    public static String getModelTitle() {
        final String current = getModel();
        for (String[] row : AVAILABLE_MODELS) {
            if (row[0].equals(current)) {
                return row[1];
            }
        }
        return current;
    }

    /** Код мови перекладу або {@link #TARGET_LANG_AUTO} для автовизначення. */
    public static String getTargetLanguage() {
        return prefs().getString(PREF_TARGET_LANG, TARGET_LANG_AUTO);
    }

    public static void setTargetLanguage(String langCode) {
        prefs().edit().putString(PREF_TARGET_LANG, langCode == null ? "" : langCode).apply();
    }

    /**
     * Чи готові AI-функції до роботи: пристрій підтримує шифроване сховище
     * і ключ збережено. Перевіряти перед показом кнопок, щоб не пропонувати
     * дію, яка гарантовано впаде.
     */
    public static boolean isReady() {
        return AiKeyStorage.isAvailable() && AiKeyStorage.hasKey();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
