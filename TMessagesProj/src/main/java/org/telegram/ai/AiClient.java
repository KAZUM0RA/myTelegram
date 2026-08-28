/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Клієнт Anthropic Messages API.
 *
 * <p>На {@link HttpURLConnection} — так само, як решта мережевого коду
 * Telegram (ImageLoader, BotDownloads, TranslateAlert2). Офіційний Java SDK
 * потягнув би OkHttp і Jackson у застосунок, який обходиться без них, заради
 * одного POST на один ендпоінт.
 *
 * <p>Запит виконується у {@code Utilities.globalQueue}, відповідь повертається
 * в UI-потік — як заведено в цьому проєкті.
 */
public class AiClient {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";

    /** Версія API. Anthropic вимагає її в кожному запиті. */
    private static final String API_VERSION = "2023-06-01";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    /**
     * Стеля відповіді. Для перекладу повідомлення з надлишком: переклад
     * рідко довший за оригінал більш ніж удвічі. Занадто мале значення
     * обрізало б відповідь посеред речення.
     */
    private static final int MAX_TOKENS = 2048;

    public interface Callback {
        void onSuccess(String text);
        /** @param message готовий до показу текст помилки українською */
        void onError(String message);
    }

    private AiClient() {}

    /**
     * Переклад тексту.
     *
     * @param targetLanguage назва мови у вигляді, зрозумілому моделі
     *                       («українську», «English»); порожнє значення
     *                       означає, що мову має обрати сама модель
     */
    public static void translate(String text, String targetLanguage, Callback callback) {
        final String target = TextUtils.isEmpty(targetLanguage)
                ? "мову, якою написане попереднє повідомлення співрозмовника"
                : targetLanguage;

        // Промт свідомо короткий і жорсткий: що більше зайвих слів, то вища
        // ймовірність, що модель додасть від себе пояснення чи лапки, і їх
        // доведеться вирізати.
        final String system =
                "Ти перекладач. Переклади текст користувача на " + target + ". "
                        + "Збережи тон, регістр і стиль оригіналу: неформальне лишається "
                        + "неформальним, емодзі й розмітка зберігаються. "
                        + "У відповідь дай ВИКЛЮЧНО текст перекладу — без лапок, "
                        + "без пояснень, без вступних слів.";

        request(system, text, callback);
    }

    /**
     * Довільний запит до моделі. Знадобиться в Частині 6 для підсумків,
     * швидких відповідей і виправлення стилю.
     */
    public static void request(String systemPrompt, String userText, Callback callback) {
        final String apiKey = AiKeyStorage.getKey();
        if (TextUtils.isEmpty(apiKey)) {
            fail(callback, LocaleController.getString(R.string.AiErrorNoKey));
            return;
        }
        if (TextUtils.isEmpty(userText)) {
            fail(callback, LocaleController.getString(R.string.AiErrorEmptyText));
            return;
        }

        Utilities.globalQueue.postRunnable(() -> execute(apiKey, systemPrompt, userText, callback));
    }

    private static void execute(String apiKey, String systemPrompt, String userText, Callback callback) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("content-type", "application/json");
            connection.setRequestProperty("anthropic-version", API_VERSION);
            connection.setRequestProperty("x-api-key", apiKey);

            final byte[] body = buildRequestBody(systemPrompt, userText).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }

            final int code = connection.getResponseCode();
            final String response = readStream(code >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream());

            if (code == 200) {
                final String text = extractText(response);
                if (TextUtils.isEmpty(text)) {
                    fail(callback, LocaleController.getString(R.string.AiErrorEmptyResponse));
                } else {
                    AndroidUtilities.runOnUIThread(() -> callback.onSuccess(text));
                }
            } else {
                fail(callback, describeHttpError(code, response));
            }
        } catch (java.net.UnknownHostException | java.net.SocketTimeoutException e) {
            fail(callback, LocaleController.getString(R.string.AiErrorNetwork));
        } catch (Throwable e) {
            // Виняток логуємо без тіла запиту: там текст повідомлення користувача.
            FileLog.e("AiClient: запит не вдався (" + e.getClass().getSimpleName() + ")");
            fail(callback, LocaleController.getString(R.string.AiErrorUnknown));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String buildRequestBody(String systemPrompt, String userText) throws Exception {
        final String model = AiConfig.getModel();

        final JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", userText);

        final JSONArray messages = new JSONArray();
        messages.put(message);

        final JSONObject root = new JSONObject();
        root.put("model", model);
        root.put("max_tokens", MAX_TOKENS);
        root.put("system", systemPrompt);
        root.put("messages", messages);

        // Різні покоління моделей по-різному ставляться до «мислення», а для
        // перекладу воно лише додає затримку й вартість:
        //
        //   Haiku 4.5 — мислення вимкнене за замовчуванням, а output_config.effort
        //               вона взагалі не приймає й відповідає помилкою 400.
        //               Тому для неї не додаємо нічого.
        //   Sonnet 5  — без явної вказівки вмикає мислення, тож вимикаємо.
        //   Opus 5    — мислення ввімкнене за замовчуванням; вимикати його не
        //               варто (модель тоді схильна лишати службові теги в
        //               тексті), натомість знижуємо витрати через effort=low.
        if (AiConfig.MODEL_SONNET.equals(model)) {
            final JSONObject thinking = new JSONObject();
            thinking.put("type", "disabled");
            root.put("thinking", thinking);
        } else if (AiConfig.MODEL_OPUS.equals(model)) {
            final JSONObject outputConfig = new JSONObject();
            outputConfig.put("effort", "low");
            root.put("output_config", outputConfig);
        }

        return root.toString();
    }

    /**
     * Дістає текст із відповіді.
     *
     * <p>Поле {@code content} — масив блоків різних типів; нас цікавлять лише
     * текстові. Беремо всі й склеюємо: модель може розбити відповідь на кілька
     * блоків, і взяти тільки перший означало б мовчки втратити частину.
     */
    private static String extractText(String response) throws Exception {
        final JSONObject root = new JSONObject(response);

        // stop_reason == "refusal" означає, що модель відмовилась відповідати.
        // Тіло при цьому порожнє, тож без окремої перевірки користувач побачив
        // би незрозуміле «порожня відповідь».
        if ("refusal".equals(root.optString("stop_reason"))) {
            return null;
        }

        final JSONArray content = root.optJSONArray("content");
        if (content == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            final JSONObject block = content.optJSONObject(i);
            if (block != null && "text".equals(block.optString("type"))) {
                sb.append(block.optString("text"));
            }
        }
        return sb.toString().trim();
    }

    private static String describeHttpError(int code, String response) {
        switch (code) {
            case 401:
            case 403:
                return LocaleController.getString(R.string.AiErrorInvalidKey);
            case 429:
                return LocaleController.getString(R.string.AiErrorRateLimit);
            case 400:
                // Найчастіша причина 400 у цьому сценарії — вичерпаний баланс
                // або незнайома моделі назва. Показуємо пояснення від сервера,
                // якщо воно є: воно конкретніше за будь-яке наше формулювання.
                final String detail = extractErrorMessage(response);
                return TextUtils.isEmpty(detail)
                        ? LocaleController.getString(R.string.AiErrorBadRequest)
                        : LocaleController.getString(R.string.AiErrorBadRequest) + "\n" + detail;
            default:
                if (code >= 500) {
                    return LocaleController.getString(R.string.AiErrorServer);
                }
                return LocaleController.formatString(R.string.AiErrorHttp, code);
        }
    }

    private static String extractErrorMessage(String response) {
        try {
            final JSONObject error = new JSONObject(response).optJSONObject("error");
            return error == null ? null : error.optString("message");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void fail(Callback callback, String message) {
        AndroidUtilities.runOnUIThread(() -> callback.onError(message));
    }
}
