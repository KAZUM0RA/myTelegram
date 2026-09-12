/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.quickbuttons;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.ArrayList;

/**
 * Швидкі кнопки — заготовані фрази, які надсилаються одним дотиком.
 *
 * <p>Влаштовано ГРУПАМИ. Кожна група — окрема плаваюча кнопка зі своїм
 * списком фраз, своїм значком, кольором і місцем на екрані. Так в одному
 * чаті можна тримати «Робота» й «Побутове» поруч, не змішуючи їх: список
 * із двадцяти фраз без поділу шукати довше, ніж просто набрати текст.
 *
 * <p>Групи свої для КОЖНОГО чату: з різними людьми потрібні різні
 * заготовки.
 *
 * <p>Зберігаємо одним JSON на діалог. Своєї бази тут не треба: груп
 * одиниці, а читаються вони лише при відкритті чату.
 */
public final class QuickButtons {

    private QuickButtons() {
    }

    private static final String PREFS = "quickbuttons";

    /** Скільки груп дозволяємо. Більше не влізе на екран, не перекривши чат. */
    public static final int MAX_GROUPS = 6;
    /** Скільки фраз у групі. Довший список простіше набрати, ніж знайти. */
    public static final int MAX_BUTTONS = 12;

    // ── Моделі ───────────────────────────────────────────────────────────

    public static final class Button {
        public String label;
        public String text;
        /**
         * {@code true} — надіслати одразу, {@code false} — покласти в поле
         * вводу. Вибір для кожної фрази окремо: коротке «Добре» зручно слати
         * відразу, а заготовку, яку щоразу дописуєш, — ні.
         */
        public boolean sendNow;

        public Button(String label, String text, boolean sendNow) {
            this.label = label;
            this.text = text;
            this.sendNow = sendNow;
        }
    }

    public static final class Group {
        public String name = "";
        public String icon = "";
        public String emoji = "";
        /** Ім'я файлу власної картинки. Порожнє — картинки немає. */
        public String image = "";
        public int color = COLOR_THEME;
        public int size = 44;
        public int alpha = 100;
        public int mode = MODE_SINGLE;
        public int style = STYLE_LIST;
        /** Місце на екрані. {@code -1} — ще не пересували. */
        public float x = -1, y = -1;
        public final ArrayList<Button> buttons = new ArrayList<>();
    }

    // ── Сталі вигляду ────────────────────────────────────────────────────

    /** Нуль означає «як у темі» — колір підхоплюється з оформлення чату. */
    public static final int COLOR_THEME = 0;

    public static final int[] COLORS = {
            COLOR_THEME,
            0xFF4E8FE0, 0xFF6D3A5D, 0xFF2F6B45,
            0xFFB4531F, 0xFFA32C22, 0xFF394049,
    };

    /** Одна кнопка, яка відкриває список. */
    public static final int MODE_SINGLE = 0;
    /** Фрази розкладені поруч, без проміжного дотику. */
    public static final int MODE_SEPARATE = 1;
    /**
     * Проста кнопка без списку: дотик одразу надсилає першу фразу групи.
     *
     * <p>Для випадку, коли фраза одна й список їй ні до чого — наприклад
     * «Виїжджаю» чи «+». Формально це те саме, що група з однією фразою в
     * режимі «поруч», але кругла кнопка зі значком займає менше місця й
     * читається як дія, а не як список із одного пункту.
     */
    public static final int MODE_DIRECT = 2;

    public static final int STYLE_LIST = 0;
    public static final int STYLE_GRID = 1;
    public static final int STYLE_ROW = 2;

    /**
     * Готові значки під різні задачі. Порожній рядок — типова стрілка.
     *
     * <p>Векторні й одноколірні: кнопка фарбується під обраний колір, тож
     * значок має бути силуетом, інакше на кольоровому тлі виглядав би чужим.
     */
    public static final String[] ICONS = {
            "", "qb_flash", "qb_chat", "qb_star",
            "qb_heart", "qb_work", "qb_clock", "qb_check", "qb_question",
    };

    // ── Читання й запис ──────────────────────────────────────────────────

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(long dialogId) {
        return "g" + dialogId;
    }

    public static ArrayList<Group> get(long dialogId) {
        final ArrayList<Group> groups = new ArrayList<>();
        try {
            final String raw = prefs().getString(key(dialogId), null);
            if (TextUtils.isEmpty(raw)) {
                return groups;
            }
            final JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                final JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final Group group = new Group();
                group.name = item.optString("name");
                group.icon = item.optString("icon");
                group.emoji = item.optString("emoji");
                group.image = item.optString("image");
                group.color = item.optInt("color", COLOR_THEME);
                group.size = clamp(item.optInt("size", 44), 36, 72);
                group.alpha = clamp(item.optInt("alpha", 100), 40, 100);
                group.mode = item.optInt("mode", MODE_SINGLE);
                group.style = item.optInt("style", STYLE_LIST);
                group.x = (float) item.optDouble("x", -1);
                group.y = (float) item.optDouble("y", -1);

                final JSONArray list = item.optJSONArray("buttons");
                if (list != null) {
                    for (int j = 0; j < list.length(); j++) {
                        final JSONObject b = list.optJSONObject(j);
                        if (b == null) {
                            continue;
                        }
                        final String label = b.optString("label");
                        final String text = b.optString("text");
                        if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(text)) {
                            group.buttons.add(new Button(label, text, b.optBoolean("send", true)));
                        }
                    }
                }
                groups.add(group);
            }
        } catch (Throwable e) {
            FileLog.e("QuickButtons: не вдалося прочитати групи чату");
        }
        return groups;
    }

    public static void save(long dialogId, ArrayList<Group> groups) {
        try {
            if (groups == null || groups.isEmpty()) {
                prefs().edit().remove(key(dialogId)).apply();
                return;
            }
            final JSONArray array = new JSONArray();
            for (Group group : groups) {
                final JSONArray list = new JSONArray();
                for (Button button : group.buttons) {
                    list.put(new JSONObject()
                            .put("label", button.label)
                            .put("text", button.text)
                            .put("send", button.sendNow));
                }
                array.put(new JSONObject()
                        .put("name", group.name)
                        .put("icon", group.icon)
                        .put("emoji", group.emoji)
                        .put("image", group.image)
                        .put("color", group.color)
                        .put("size", group.size)
                        .put("alpha", group.alpha)
                        .put("mode", group.mode)
                        .put("style", group.style)
                        .put("x", group.x)
                        .put("y", group.y)
                        .put("buttons", list));
            }
            prefs().edit().putString(key(dialogId), array.toString()).apply();
        } catch (Throwable e) {
            FileLog.e("QuickButtons: не вдалося зберегти групи чату");
        }
    }

    public static boolean hasAny(long dialogId) {
        for (Group group : get(dialogId)) {
            if (!group.buttons.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    // ── Власна картинка групи ────────────────────────────────────────────

    public static File imageFile(String name) {
        if (TextUtils.isEmpty(name)) {
            return null;
        }
        try {
            final File file = new File(ApplicationLoader.getFilesDirFixed(), name);
            return file.exists() && file.length() > 0 ? file : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Копіює обрану картинку до себе й повертає ім'я файлу.
     *
     * <p>Саме копіює, а не запам'ятовує шлях: доступ до чужого файлу можна
     * втратити будь-коли — користувач видалить фото, система відкличе
     * дозвіл, — і кнопка лишиться без значка без жодного пояснення.
     *
     * <p>Ім'я містить час створення: інакше дві групи з картинками
     * перетирали б файли одна одної.
     */
    public static String saveImage(Uri uri) {
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        try {
            in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            if (in == null) {
                return "";
            }
            final android.graphics.Bitmap source = android.graphics.BitmapFactory.decodeStream(in);
            if (source == null) {
                return "";
            }
            // 144 пікселі: більше за найбільший розмір кнопки, і не тягне
            // зайвих мегабайтів у пам'ять на кожному відкритті чату.
            final int side = 144;
            final android.graphics.Bitmap scaled =
                    android.graphics.Bitmap.createScaledBitmap(source, side, side, true);
            final String name = "quickbutton_" + System.currentTimeMillis() + ".png";
            out = new java.io.FileOutputStream(
                    new File(ApplicationLoader.getFilesDirFixed(), name));
            scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            if (scaled != source) {
                scaled.recycle();
            }
            source.recycle();
            return name;
        } catch (Throwable e) {
            FileLog.e("QuickButtons: не вдалося зберегти картинку кнопки");
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
            try { if (out != null) out.close(); } catch (Throwable ignored) { }
        }
    }

    public static void deleteImage(String name) {
        try {
            final File file = imageFile(name);
            if (file != null) {
                file.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
