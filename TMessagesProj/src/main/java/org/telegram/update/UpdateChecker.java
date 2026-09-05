/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.ai.AiKeyStorage;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Перевірка й завантаження оновлень з релізів GitHub.
 *
 * <p>Порівнюємо не версію, а <b>короткий хеш коміта</b>: версія застосунку
 * між нашими збірками не змінюється, тож за нею відрізнити нову збірку від
 * старої неможливо. Хеш зашивається у BuildConfig під час збірки, а в релізі
 * він стоїть у назві файлу — {@code mytelegram-debug-<хеш>.apk}.
 *
 * <p>Репозиторій приватний, тому потрібен токен GitHub. Без нього API
 * відповідає 404 — не «немає доступу», а «немає такого репозиторію», тож це
 * окремо пояснюється користувачу. Якщо колись зробиш репозиторій публічним,
 * усе працюватиме й без токена: код це вже враховує.
 */
public final class UpdateChecker {

    private UpdateChecker() {
    }

    public static final String REPO = "KAZUM0RA/myTelegram";
    private static final String RELEASE_TAG = "debug-latest";
    private static final String API_URL =
            "https://api.github.com/repos/" + REPO + "/releases/tags/" + RELEASE_TAG;

    /** Токен зберігається тим самим шифрованим сховищем, що й AI-ключі. */
    public static final String TOKEN_PROVIDER = "github";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    public static final class Update {
        public final String commit;
        public final String downloadUrl;
        public final long size;
        public final String assetName;

        Update(String commit, String downloadUrl, long size, String assetName) {
            this.commit = commit;
            this.downloadUrl = downloadUrl;
            this.size = size;
            this.assetName = assetName;
        }
    }

    public interface CheckCallback {
        void onResult(Update update);

        void onUpToDate();

        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(int percent);

        void onReady(File apk);

        void onError(String message);
    }

    /** Хеш, з якого зібрано цей застосунок. Порожній — якщо збирали не з git. */
    public static String currentCommit() {
        try {
            return BuildConfig.GIT_COMMIT;
        } catch (Throwable e) {
            return "";
        }
    }

    public static boolean hasToken() {
        return AiKeyStorage.isAvailable() && AiKeyStorage.hasKey(TOKEN_PROVIDER);
    }

    // ── Автоматична перевірка ────────────────────────────────────────────

    private static final String PREFS = "update";
    private static final String PREF_AUTO = "auto";
    private static final String PREF_LAST_CHECK = "lastCheck";
    private static final String PREF_LAST_SEEN = "lastSeenCommit";

    /**
     * Як часто дозволено ходити на GitHub.
     *
     * <p>Шість годин, а не «щоразу при запуску»: без токена GitHub дозволяє
     * 60 запитів на годину з однієї адреси, і застосунок, який відкривають
     * десятки разів на день, легко вичерпав би ліміт на порожньому місці.
     */
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isAutoCheckEnabled() {
        try {
            return prefs().getBoolean(PREF_AUTO, true);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setAutoCheckEnabled(boolean enabled) {
        try {
            prefs().edit().putBoolean(PREF_AUTO, enabled).apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Тиха перевірка при запуску. Показує сповіщення, якщо є новіша збірка.
     *
     * <p>Помилки навмисно мовчазні: користувач цієї перевірки не просив, і
     * скарга на недоступний GitHub при кожному відкритті застосунку була б
     * гіршою за саму відсутність оновлення.
     */
    public static void checkInBackground() {
        if (!isAutoCheckEnabled()) {
            return;
        }
        final SharedPreferences prefs;
        try {
            prefs = prefs();
        } catch (Throwable e) {
            return;
        }
        final long now = System.currentTimeMillis();
        final long last = prefs.getLong(PREF_LAST_CHECK, 0);
        // Порівнюємо в обидва боки: годинник можна перевести назад, і тоді
        // різниця стане від'ємною, а перевірка не спрацювала б ніколи.
        if (last != 0 && Math.abs(now - last) < CHECK_INTERVAL_MS) {
            return;
        }
        prefs.edit().putLong(PREF_LAST_CHECK, now).apply();

        check(new CheckCallback() {
            @Override
            public void onResult(Update update) {
                // Про ту саму збірку нагадуємо лише раз: інакше сповіщення
                // з'являлося б кожні шість годин, доки не оновишся.
                if (update.commit.equals(prefs.getString(PREF_LAST_SEEN, ""))) {
                    return;
                }
                prefs.edit().putString(PREF_LAST_SEEN, update.commit).apply();
                UpdateNotification.show(update.commit);
            }

            @Override
            public void onUpToDate() {
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    // ── Перевірка ────────────────────────────────────────────────────────

    public static void check(CheckCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(API_URL, "application/vnd.github+json");
                final int code = connection.getResponseCode();
                final String body = read(code >= 400
                        ? connection.getErrorStream() : connection.getInputStream());

                if (code == 404) {
                    fail(callback, LocaleController.getString(hasToken()
                            ? R.string.UpdateErrorNoRelease : R.string.UpdateErrorNoToken));
                    return;
                }
                if (code == 401 || code == 403) {
                    fail(callback, LocaleController.getString(R.string.UpdateErrorBadToken));
                    return;
                }
                if (code != 200) {
                    fail(callback, LocaleController.formatString(
                            R.string.UpdateErrorHttp, code));
                    return;
                }

                final JSONArray assets = new JSONObject(body).optJSONArray("assets");
                if (assets == null || assets.length() == 0) {
                    fail(callback, LocaleController.getString(R.string.UpdateErrorNoApk));
                    return;
                }

                JSONObject apk = null;
                for (int i = 0; i < assets.length(); i++) {
                    final JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && asset.optString("name").endsWith(".apk")) {
                        apk = asset;
                        break;
                    }
                }
                if (apk == null) {
                    fail(callback, LocaleController.getString(R.string.UpdateErrorNoApk));
                    return;
                }

                final String name = apk.optString("name");
                final String commit = commitFromName(name);
                // Для приватного репозиторію качати треба через API-адресу
                // самого файлу; browser_download_url без токена поверне 404.
                final String url = hasToken()
                        ? apk.optString("url") : apk.optString("browser_download_url");
                final long size = apk.optLong("size");

                if (sameCommit(currentCommit(), commit)) {
                    AndroidUtilities.runOnUIThread(callback::onUpToDate);
                    return;
                }

                final Update update = new Update(commit, url, size, name);
                AndroidUtilities.runOnUIThread(() -> callback.onResult(update));
            } catch (Throwable e) {
                FileLog.e("UpdateChecker: перевірка не вдалася (" + e.getClass().getSimpleName() + ")");
                fail(callback, LocaleController.getString(R.string.UpdateErrorNetwork));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * Чи це той самий коміт.
     *
     * <p>Порівнюємо за префіксом, а не на рівність: {@code git rev-parse
     * --short} віддає різну кількість символів залежно від репозиторію —
     * локально може бути дев'ять, а на свіжому клоні в CI сім. За суворої
     * рівності перевірка завжди повідомляла б про оновлення.
     */
    private static boolean sameCommit(String a, String b) {
        if (TextUtils.isEmpty(a) || TextUtils.isEmpty(b)) {
            return false;
        }
        return a.length() <= b.length() ? b.startsWith(a) : a.startsWith(b);
    }

    /** {@code mytelegram-debug-f09b818.apk} → {@code f09b818}. */
    private static String commitFromName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        final int dot = name.lastIndexOf(".apk");
        final int dash = name.lastIndexOf('-', dot < 0 ? name.length() - 1 : dot);
        if (dash < 0 || dot < 0 || dash + 1 >= dot) {
            return "";
        }
        return name.substring(dash + 1, dot);
    }

    // ── Завантаження ─────────────────────────────────────────────────────

    public static void download(Update update, DownloadCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                final File dir = new File(ApplicationLoader.getFilesDirFixed(), "updates");
                if (!dir.exists() && !dir.mkdirs()) {
                    failDownload(callback, LocaleController.getString(R.string.UpdateErrorSave));
                    return;
                }
                final File apk = new File(dir, "update.apk");
                if (apk.exists() && !apk.delete()) {
                    failDownload(callback, LocaleController.getString(R.string.UpdateErrorSave));
                    return;
                }

                connection = openForAsset(update.downloadUrl);
                final int code = connection.getResponseCode();
                if (code != 200) {
                    failDownload(callback, LocaleController.formatString(
                            R.string.UpdateErrorHttp, code));
                    return;
                }

                final long total = update.size > 0 ? update.size : connection.getContentLength();
                long done = 0;
                int lastPercent = -1;

                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(apk)) {
                    final byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            final int percent = (int) (done * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int p = percent;
                                AndroidUtilities.runOnUIThread(() -> callback.onProgress(p));
                            }
                        }
                    }
                }

                if (apk.length() == 0) {
                    failDownload(callback, LocaleController.getString(R.string.UpdateErrorSave));
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> callback.onReady(apk));
            } catch (Throwable e) {
                FileLog.e("UpdateChecker: завантаження не вдалося (" + e.getClass().getSimpleName() + ")");
                failDownload(callback, LocaleController.getString(R.string.UpdateErrorNetwork));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    // ── Мережа ───────────────────────────────────────────────────────────

    private static HttpURLConnection open(String url, String accept) throws Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "MyTelegram");
        if (hasToken()) {
            final String token = AiKeyStorage.getKey(TOKEN_PROVIDER);
            if (!TextUtils.isEmpty(token)) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
        }
        return connection;
    }

    /**
     * З'єднання по сам файл релізу.
     *
     * <p>GitHub віддає файл перенаправленням на своє сховище, і туди
     * заголовок Authorization слати НЕ можна: сховище відмовить із
     * «дозволено лише один спосіб автентифікації». Тому перенаправлення
     * обробляємо вручну й другий запит робимо без токена.
     */
    private static HttpURLConnection openForAsset(String url) throws Exception {
        HttpURLConnection connection = open(url, "application/octet-stream");
        connection.setInstanceFollowRedirects(false);
        final int code = connection.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            final String location = connection.getHeaderField("Location");
            connection.disconnect();
            final HttpURLConnection redirected =
                    (HttpURLConnection) new URL(location).openConnection();
            redirected.setConnectTimeout(CONNECT_TIMEOUT_MS);
            redirected.setReadTimeout(READ_TIMEOUT_MS);
            redirected.setRequestProperty("User-Agent", "MyTelegram");
            return redirected;
        }
        return connection;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            final char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }

    private static void fail(CheckCallback callback, String message) {
        AndroidUtilities.runOnUIThread(() -> callback.onError(message));
    }

    private static void failDownload(DownloadCallback callback, String message) {
        AndroidUtilities.runOnUIThread(() -> callback.onError(message));
    }
}
