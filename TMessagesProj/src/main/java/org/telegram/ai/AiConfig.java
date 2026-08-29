/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Налаштування AI-функцій: провайдер, модель, мова.
 *
 * <p>Ключі тут не зберігаються — вони в {@link AiKeyStorage}, бо потребують
 * шифрування. Ключ у кожного провайдера свій.
 */
public class AiConfig {

    private static final String PREFS = "ai_config";

    private static final String PREF_PROVIDER = "provider";
    private static final String PREF_MODEL_PREFIX = "model_";
    private static final String PREF_TARGET_LANG = "target_lang";

    // ── Провайдери ───────────────────────────────────────────────────────

    public static final String PROVIDER_ANTHROPIC = "anthropic";
    public static final String PROVIDER_GEMINI = "gemini";

    /**
     * Google за замовчуванням: удесятеро дешевший на тексті й, головне,
     * єдиний з двох, хто приймає аудіо — тобто голосові повідомлення
     * працюють лише з ним.
     */
    private static final String DEFAULT_PROVIDER = PROVIDER_GEMINI;

    public static final String[][] AVAILABLE_PROVIDERS = {
            {PROVIDER_GEMINI,    "Google Gemini"},
            {PROVIDER_ANTHROPIC, "Anthropic Claude"},
    };

    // ── Моделі ───────────────────────────────────────────────────────────

    public static final String MODEL_HAIKU = "claude-haiku-4-5";
    public static final String MODEL_SONNET = "claude-sonnet-5";
    public static final String MODEL_OPUS = "claude-opus-5";

    public static final String MODEL_GEMINI_FLASH_LITE = "gemini-3.5-flash-lite";
    public static final String MODEL_GEMINI_FLASH = "gemini-3.7-flash";
    /** Старіше покоління — лишаємо як запасний варіант, якщо новіші недоступні ключу. */
    public static final String MODEL_GEMINI_25_FLASH_LITE = "gemini-2.5-flash-lite";

    /**
     * Модель для голосових, зафіксована окремо від моделі для тексту.
     *
     * <p>Джерела Google суперечать одне одному щодо того, які саме моделі
     * приймають аудіо: сторінка цін наводить вартість аудіо для Flash-Lite,
     * сторінка моделей цього не підтверджує. Тому голосові не залежать від
     * вибору моделі для тексту, а «Перевірити зв'язок» у налаштуваннях
     * показує, що насправді доступне конкретному ключу.
     */
    public static final String MODEL_GEMINI_AUDIO = MODEL_GEMINI_FLASH_LITE;

    public static String[][] availableModels(String provider) {
        if (PROVIDER_GEMINI.equals(provider)) {
            return new String[][]{
                    {MODEL_GEMINI_FLASH_LITE,    "3.5 Flash-Lite — найдешевша, вміє аудіо"},
                    {MODEL_GEMINI_FLASH,         "3.7 Flash — найновіша"},
                    {MODEL_GEMINI_25_FLASH_LITE, "2.5 Flash-Lite — старіша, запасна"},
            };
        }
        return new String[][]{
                {MODEL_HAIKU,  "Haiku 4.5 — найдешевша, швидка"},
                {MODEL_SONNET, "Sonnet 5 — точніша з тоном"},
                {MODEL_OPUS,   "Opus 5 — найкраща якість"},
        };
    }

    private static String defaultModel(String provider) {
        return PROVIDER_GEMINI.equals(provider) ? MODEL_GEMINI_FLASH_LITE : MODEL_HAIKU;
    }

    public static final String TARGET_LANG_AUTO = "";

    private AiConfig() {}

    // ── Провайдер ────────────────────────────────────────────────────────

    public static String getProvider() {
        return prefs().getString(PREF_PROVIDER, DEFAULT_PROVIDER);
    }

    public static void setProvider(String provider) {
        prefs().edit().putString(PREF_PROVIDER, provider).apply();
    }

    public static String getProviderTitle() {
        final String current = getProvider();
        for (String[] row : AVAILABLE_PROVIDERS) {
            if (row[0].equals(current)) {
                return row[1];
            }
        }
        return current;
    }

    public static boolean isGemini() {
        return PROVIDER_GEMINI.equals(getProvider());
    }

    /** Голосові вміє лише Gemini — Anthropic аудіо не приймає взагалі. */
    public static boolean supportsAudio() {
        return isGemini();
    }

    // ── Модель ───────────────────────────────────────────────────────────

    /** Модель зберігається окремо для кожного провайдера. */
    public static String getModel() {
        final String provider = getProvider();
        return prefs().getString(PREF_MODEL_PREFIX + provider, defaultModel(provider));
    }

    public static void setModel(String model) {
        prefs().edit().putString(PREF_MODEL_PREFIX + getProvider(), model).apply();
    }

    public static String getModelTitle() {
        final String current = getModel();
        for (String[] row : availableModels(getProvider())) {
            if (row[0].equals(current)) {
                return row[1];
            }
        }
        return current;
    }

    // ── Мова ─────────────────────────────────────────────────────────────

    public static String getTargetLanguage() {
        return prefs().getString(PREF_TARGET_LANG, TARGET_LANG_AUTO);
    }

    public static void setTargetLanguage(String langCode) {
        prefs().edit().putString(PREF_TARGET_LANG, langCode == null ? "" : langCode).apply();
    }

    /** Чи готовий поточний провайдер до роботи: сховище доступне й ключ заданий. */
    public static boolean isReady() {
        return AiKeyStorage.isAvailable() && AiKeyStorage.hasKey(getProvider());
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
