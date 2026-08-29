package org.telegram.wordpack;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Словник — набір підмін для написів у застосунку.
 *
 * <p>Кожен словник лежить окремим файлом у {@code assets/wordpacks/} у вигляді
 * {@code ключ = текст}, по одній парі на рядок. Ключ — це ім'я рядка з
 * {@code strings.xml} (наприклад {@code Settings}), а не саме слово: одне й те
 * саме слово трапляється в десятках рядків, і підміняти за текстом означало б
 * псувати ті, де воно вжите в іншому значенні.
 *
 * <p>Щоб додати новий словник, досить покласти ще один файл поруч — код
 * чіпати не треба. Назва береться з рядка {@code _назва} всередині файлу.
 *
 * <p>Підміна вмикається в {@code LocaleController.getStringInternal()} — через
 * нього проходять усі написи, і локальні, і завантажені з серверів Telegram.
 */
public final class WordPack {

    private WordPack() {
    }

    private static final String DIR = "wordpacks";
    private static final String PREFS = "wordpack";
    private static final String PREF_ACTIVE = "active";

    /** Порожній ідентифікатор означає звичайні написи, без підмін. */
    public static final String STANDARD = "";

    /** Службовий ключ усередині файлу: як словник називається в налаштуваннях. */
    private static final String NAME_KEY = "_назва";

    /**
     * Завантажений словник. {@code volatile}, бо {@code getString()}
     * викликається з багатьох потоків, а перемикання — з головного.
     */
    private static volatile HashMap<String, String> loaded;
    private static volatile String loadedId;

    // ── Стан ─────────────────────────────────────────────────────────────

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getActiveId() {
        try {
            return prefs().getString(PREF_ACTIVE, STANDARD);
        } catch (Throwable e) {
            return STANDARD;
        }
    }

    public static void setActive(String id) {
        try {
            prefs().edit().putString(PREF_ACTIVE, id == null ? STANDARD : id).apply();
        } catch (Throwable e) {
            FileLog.e("WordPack: не вдалося зберегти вибір словника");
        }
        // Скидаємо кеш, а не перезавантажуємо: наступний getString() зробить
        // це сам, і лише якщо словник справді знадобиться.
        loaded = null;
        loadedId = null;
    }

    // ── Підміна ──────────────────────────────────────────────────────────

    /**
     * Заміна для ключа або {@code null}, якщо її немає.
     *
     * <p>Викликається на кожен напис, тож мусить бути дешевою: у типовому
     * випадку (словник стандартний) це одна перевірка рядка.
     */
    public static String override(String key) {
        final String id = getActiveId();
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(key)) {
            return null;
        }
        HashMap<String, String> map = loaded;
        if (map == null || !id.equals(loadedId)) {
            map = load(id);
            loaded = map;
            loadedId = id;
        }
        return map.get(key);
    }

    // ── Читання файлів ───────────────────────────────────────────────────

    /** Ідентифікатори й назви доступних словників, окрім стандартного. */
    public static ArrayList<String[]> available() {
        final ArrayList<String[]> result = new ArrayList<>();
        try {
            final String[] files = ApplicationLoader.applicationContext
                    .getAssets().list(DIR);
            if (files == null) {
                return result;
            }
            final ArrayList<String> sorted = new ArrayList<>();
            for (String file : files) {
                if (file.endsWith(".txt")) {
                    sorted.add(file.substring(0, file.length() - 4));
                }
            }
            Collections.sort(sorted);
            for (String id : sorted) {
                final String name = load(id).get(NAME_KEY);
                result.add(new String[]{id, TextUtils.isEmpty(name) ? id : name});
            }
        } catch (Throwable e) {
            FileLog.e("WordPack: не вдалося прочитати список словників");
        }
        return result;
    }

    private static HashMap<String, String> load(String id) {
        final HashMap<String, String> map = new HashMap<>();
        try (InputStream in = ApplicationLoader.applicationContext
                .getAssets().open(DIR + "/" + id + ".txt");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Порожні рядки й коментарі пропускаємо: файл має лишатися
                // придатним для читання й правки руками.
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                final int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                final String key = trimmed.substring(0, eq).trim();
                final String value = trimmed.substring(eq + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    map.put(key, value);
                }
            }
        } catch (Throwable e) {
            FileLog.e("WordPack: не вдалося прочитати словник " + id);
        }
        return map;
    }
}
