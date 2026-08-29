/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Клієнт до AI-провайдера. Підтримує Anthropic і Google Gemini.
 *
 * <p>На {@link HttpURLConnection} — так само, як решта мережевого коду
 * Telegram. Один клас на два провайдери навмисно: різниця між ними зводиться
 * до формату запиту й відповіді, і розводити це по ієрархії класів заради
 * двох варіантів було б надмірно.
 *
 * <p>Голосові вміє лише Gemini: моделі Anthropic аудіо не приймають взагалі.
 */
public class AiClient {

    private static final String ANTHROPIC_ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    /** Аудіо читається довше: розпізнавання хвилинного голосового не миттєве. */
    private static final int AUDIO_READ_TIMEOUT_MS = 120_000;

    private static final int MAX_TOKENS = 2048;

    /**
     * Стеля розміру голосового. Gemini приймає вбудовані дані приблизно до
     * 20 МБ на запит; беремо із запасом, бо base64 роздуває файл на третину.
     */
    private static final long MAX_AUDIO_BYTES = 12L * 1024 * 1024;

    public interface Callback {
        void onSuccess(String text);
        /** @param message готовий до показу текст помилки */
        void onError(String message);
    }

    private AiClient() {}

    // ── Переклад ─────────────────────────────────────────────────────────

    public static void translate(String text, String targetLanguage, Callback callback) {
        final String target = TextUtils.isEmpty(targetLanguage)
                ? "мову, якою написане попереднє повідомлення співрозмовника"
                : targetLanguage;

        final String system =
                "Ти перекладач. Переклади текст користувача на " + target + ". "
                        + "Збережи тон, регістр і стиль оригіналу: неформальне лишається "
                        + "неформальним, емодзі й розмітка зберігаються. "
                        + "У відповідь дай ВИКЛЮЧНО текст перекладу — без лапок, "
                        + "без пояснень, без вступних слів.";

        request(system, text, callback);
    }

    // ── Довільний текстовий запит ────────────────────────────────────────

    public static void request(String systemPrompt, String userText, Callback callback) {
        final String provider = AiConfig.getProvider();
        final String apiKey = AiKeyStorage.getKey(provider);
        if (TextUtils.isEmpty(apiKey)) {
            fail(callback, LocaleController.getString(R.string.AiErrorNoKey));
            return;
        }
        if (TextUtils.isEmpty(userText)) {
            fail(callback, LocaleController.getString(R.string.AiErrorEmptyText));
            return;
        }
        Utilities.globalQueue.postRunnable(() ->
                execute(provider, apiKey, systemPrompt, userText, null, callback));
    }

    // ── Голосові ─────────────────────────────────────────────────────────

    /**
     * Розпізнає голосове повідомлення.
     *
     * <p>Працює лише з Gemini. Аудіо йде в запит як вбудовані дані, тобто
     * одним викликом: розпізнавання й осмислення відбуваються разом, окремий
     * сервіс перетворення мовлення в текст не потрібен.
     */
    public static void transcribe(File audioFile, String targetLanguage, Callback callback) {
        if (!AiConfig.supportsAudio()) {
            fail(callback, LocaleController.getString(R.string.AiErrorNoAudioSupport));
            return;
        }
        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0) {
            fail(callback, LocaleController.getString(R.string.AiErrorAudioMissing));
            return;
        }
        if (audioFile.length() > MAX_AUDIO_BYTES) {
            fail(callback, LocaleController.getString(R.string.AiErrorAudioTooBig));
            return;
        }
        final String provider = AiConfig.getProvider();
        final String apiKey = AiKeyStorage.getKey(provider);
        if (TextUtils.isEmpty(apiKey)) {
            fail(callback, LocaleController.getString(R.string.AiErrorNoKey));
            return;
        }

        final String system =
                "Розпізнай мовлення в аудіо і запиши його текстом. "
                        + "Розстав пунктуацію й великі літери. "
                        + (TextUtils.isEmpty(targetLanguage) ? ""
                        : "Якщо мова аудіо відрізняється від такої: " + targetLanguage
                        + " — після розшифровки додай порожній рядок і переклад на "
                        + targetLanguage + ". ")
                        + "Дай ВИКЛЮЧНО текст, без коментарів про якість запису.";

        Utilities.globalQueue.postRunnable(() ->
                execute(provider, apiKey, system, null, audioFile, callback));
    }

    // ── Виконання запиту ─────────────────────────────────────────────────

    private static void execute(String provider, String apiKey, String systemPrompt,
                                String userText, File audioFile, Callback callback) {
        final boolean gemini = AiConfig.PROVIDER_GEMINI.equals(provider);
        HttpURLConnection connection = null;
        try {
            final String model = audioFile != null ? AiConfig.MODEL_GEMINI_AUDIO : AiConfig.getModel();
            final URL url = new URL(gemini ? String.format(GEMINI_ENDPOINT, model) : ANTHROPIC_ENDPOINT);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(audioFile != null ? AUDIO_READ_TIMEOUT_MS : READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("content-type", "application/json");
            if (gemini) {
                // Ключ у заголовку, а не в рядку запиту: URL потрапляють у
                // журнали проксі та системи, тіло запиту — ні.
                connection.setRequestProperty("x-goog-api-key", apiKey);
            } else {
                connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION);
                connection.setRequestProperty("x-api-key", apiKey);
            }

            final String body = gemini
                    ? buildGeminiBody(systemPrompt, userText, audioFile)
                    : buildAnthropicBody(systemPrompt, userText, model);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }

            final int code = connection.getResponseCode();
            final String response = readStream(code >= 400
                    ? connection.getErrorStream()
                    : connection.getInputStream());

            if (code == 200) {
                final String text = gemini ? extractGeminiText(response) : extractAnthropicText(response);
                if (TextUtils.isEmpty(text)) {
                    fail(callback, LocaleController.getString(R.string.AiErrorEmptyResponse));
                } else {
                    AndroidUtilities.runOnUIThread(() -> callback.onSuccess(text));
                }
            } else {
                // Логуємо код і пояснення сервера: це відповідь API, не текст
                // користувача, тож безпечно. Потрібно, бо в спливному
                // повідомленні довгі пояснення обрізаються, а саме вони
                // зазвичай і містять причину.
                FileLog.d("AiClient: HTTP " + code + " model=" + model
                        + " detail=" + extractErrorMessage(response));
                fail(callback, describeHttpError(code, response));
            }
        } catch (java.net.UnknownHostException | java.net.SocketTimeoutException e) {
            fail(callback, LocaleController.getString(R.string.AiErrorNetwork));
        } catch (Throwable e) {
            // Без тіла запиту в логах: там текст повідомлення користувача.
            FileLog.e("AiClient: запит не вдався (" + e.getClass().getSimpleName() + ")");
            fail(callback, LocaleController.getString(R.string.AiErrorUnknown));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ── Anthropic ────────────────────────────────────────────────────────

    private static String buildAnthropicBody(String systemPrompt, String userText, String model) throws Exception {
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

        // Різні покоління по-різному ставляться до «мислення», а для перекладу
        // воно лише додає затримку й вартість:
        //   Haiku 4.5 — output_config.effort відхиляє помилкою 400, не чіпаємо
        //   Sonnet 5  — без вказівки вмикає мислення, тож вимикаємо
        //   Opus 5    — вимикати не варто (лишає службові теги в тексті),
        //               натомість знижуємо витрати через effort=low
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

    private static String extractAnthropicText(String response) throws Exception {
        final JSONObject root = new JSONObject(response);
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

    // ── Gemini ───────────────────────────────────────────────────────────

    private static String buildGeminiBody(String systemPrompt, String userText, File audioFile) throws Exception {
        final JSONArray parts = new JSONArray();
        if (!TextUtils.isEmpty(userText)) {
            parts.put(new JSONObject().put("text", userText));
        }
        if (audioFile != null) {
            final JSONObject inline = new JSONObject();
            inline.put("mime_type", mimeTypeOf(audioFile));
            inline.put("data", Base64.encodeToString(readFile(audioFile), Base64.NO_WRAP));
            parts.put(new JSONObject().put("inline_data", inline));
        }

        final JSONObject content = new JSONObject();
        content.put("role", "user");
        content.put("parts", parts);

        final JSONObject root = new JSONObject();
        root.put("contents", new JSONArray().put(content));
        if (!TextUtils.isEmpty(systemPrompt)) {
            root.put("system_instruction",
                    new JSONObject().put("parts", new JSONObject().put("text", systemPrompt)));
        }
        root.put("generationConfig", new JSONObject().put("maxOutputTokens", MAX_TOKENS));
        return root.toString();
    }

    private static String extractGeminiText(String response) throws Exception {
        final JSONArray candidates = new JSONObject(response).optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            return null;
        }
        final JSONObject content = candidates.optJSONObject(0) == null
                ? null : candidates.optJSONObject(0).optJSONObject("content");
        if (content == null) {
            return null;
        }
        final JSONArray parts = content.optJSONArray("parts");
        if (parts == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            final JSONObject part = parts.optJSONObject(i);
            if (part != null && part.has("text")) {
                sb.append(part.optString("text"));
            }
        }
        return sb.toString().trim();
    }

    /**
     * Голосові Telegram — ogg/opus. Перевіряємо розширення, бо той самий шлях
     * може привести й до іншого формату (пересланий аудіофайл).
     */
    private static String mimeTypeOf(File file) {
        final String name = file.getName().toLowerCase();
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".m4a") || name.endsWith(".mp4")) return "audio/mp4";
        if (name.endsWith(".wav")) return "audio/wav";
        return "audio/ogg";
    }

    private static byte[] readFile(File file) throws Exception {
        final byte[] data = new byte[(int) file.length()];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                final int read = in.read(data, offset, data.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        return data;
    }

    // ── Помилки ──────────────────────────────────────────────────────────

    /**
     * Пояснення помилки.
     *
     * <p>Повідомлення сервера додаємо ЗАВЖДИ, коли воно є, а не лише для 400.
     * Раніше 404 показувався самим лише кодом, і зрозуміти, що річ у назві
     * моделі, було неможливо — тоді як Google пише це прямим текстом.
     */
    private static String describeHttpError(int code, String response) {
        final String base;
        switch (code) {
            case 401:
            case 403:
                base = LocaleController.getString(R.string.AiErrorInvalidKey);
                break;
            case 404:
                base = LocaleController.getString(R.string.AiErrorModelNotFound);
                break;
            case 429:
                base = LocaleController.getString(R.string.AiErrorRateLimit);
                break;
            case 400:
                base = LocaleController.getString(R.string.AiErrorBadRequest);
                break;
            default:
                base = code >= 500
                        ? LocaleController.getString(R.string.AiErrorServer)
                        : LocaleController.formatString(R.string.AiErrorHttp, code);
                break;
        }
        final String detail = extractErrorMessage(response);
        return TextUtils.isEmpty(detail) ? base : base + "\n\n" + detail;
    }

    /**
     * Перевірка зв'язку: питає в провайдера, які моделі доступні цьому ключу.
     *
     * <p>Існує саме через 404 на, здавалося б, правильній назві моделі:
     * замість вгадування — спитати API, що він насправді віддає.
     */
    public static void listModels(Callback callback) {
        final String provider = AiConfig.getProvider();
        final String apiKey = AiKeyStorage.getKey(provider);
        if (TextUtils.isEmpty(apiKey)) {
            fail(callback, LocaleController.getString(R.string.AiErrorNoKey));
            return;
        }
        if (!AiConfig.PROVIDER_GEMINI.equals(provider)) {
            // В Anthropic список моделей фіксований і відомий заздалегідь,
            // тож перевіряємо ключ найдешевшим можливим запитом.
            request("Відповідай одним словом.", "ok", callback);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)
                        new URL("https://generativelanguage.googleapis.com/v1beta/models").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("x-goog-api-key", apiKey);

                final int code = connection.getResponseCode();
                final String response = readStream(code >= 400
                        ? connection.getErrorStream() : connection.getInputStream());
                if (code != 200) {
                    fail(callback, describeHttpError(code, response));
                    return;
                }
                final JSONArray models = new JSONObject(response).optJSONArray("models");
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; models != null && i < models.length(); i++) {
                    final JSONObject m = models.optJSONObject(i);
                    if (m == null) continue;
                    // Цікавлять лише ті, що вміють generateContent — решта
                    // (вбудовування, TTS) для нас безкорисні.
                    final JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                    boolean ok = methods == null;
                    for (int j = 0; methods != null && j < methods.length(); j++) {
                        if ("generateContent".equals(methods.optString(j))) {
                            ok = true;
                            break;
                        }
                    }
                    if (ok) {
                        sb.append(m.optString("name").replace("models/", "")).append('\n');
                    }
                }
                final String result = sb.toString().trim();
                AndroidUtilities.runOnUIThread(() -> callback.onSuccess(
                        result.isEmpty() ? "—" : result));
            } catch (Throwable e) {
                FileLog.e("AiClient: список моделей не отримано (" + e.getClass().getSimpleName() + ")");
                fail(callback, LocaleController.getString(R.string.AiErrorNetwork));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /** Обидва провайдери кладуть пояснення в {@code error.message}. */
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
