/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.quickbuttons;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;

/**
 * Швидкі кнопки — заготовані фрази, які надсилаються одним дотиком.
 *
 * <p>Набір кнопок свій для КОЖНОГО чату: з різними людьми потрібні різні
 * заготовки, і спільний список швидко став би звалищем. Ключ у
 * налаштуваннях — ідентифікатор діалогу.
 *
 * <p>Положення панелі, навпаки, спільне: вона має бути там, де її звикла
 * шукати рука, а не стрибати між чатами.
 *
 * <p>Зберігаємо JSON у SharedPreferences. Своєї бази тут не треба: кнопок
 * одиниці, а не тисячі, і читаються вони лише при відкритті чату.
 */
public final class QuickButtons {

    private QuickButtons() {
    }

    private static final String PREFS = "quickbuttons";
    private static final String PREF_POSITION_X = "posX";
    private static final String PREF_POSITION_Y = "posY";

    /** Скільки кнопок дозволяємо. Більше не влізе у спливний список. */
    public static final int MAX_BUTTONS = 12;

    public static final class Button {
        public String label;
        public String text;
        /**
         * {@code true} — надіслати одразу, {@code false} — покласти в поле
         * вводу. Вибір для кожної кнопки окремо: коротке «Добре» зручно
         * слати відразу, а заготовку, яку щоразу дописуєш, — ні.
         */
        public boolean sendNow;

        public Button(String label, String text, boolean sendNow) {
            this.label = label;
            this.text = text;
            this.sendNow = sendNow;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Кнопки чату ──────────────────────────────────────────────────────

    public static ArrayList<Button> get(long dialogId) {
        final ArrayList<Button> result = new ArrayList<>();
        try {
            final String raw = prefs().getString(key(dialogId), null);
            if (TextUtils.isEmpty(raw)) {
                return result;
            }
            final JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                final JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final String label = item.optString("label");
                final String text = item.optString("text");
                if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(text)) {
                    // Типово «надіслати одразу»: саме заради швидкості ці
                    // кнопки й потрібні. Хто хоче інакше — перемкне.
                    result.add(new Button(label, text, item.optBoolean("send", true)));
                }
            }
        } catch (Throwable e) {
            FileLog.e("QuickButtons: не вдалося прочитати кнопки чату");
        }
        return result;
    }

    public static void save(long dialogId, ArrayList<Button> buttons) {
        try {
            if (buttons == null || buttons.isEmpty()) {
                prefs().edit().remove(key(dialogId)).apply();
                return;
            }
            final JSONArray array = new JSONArray();
            for (Button button : buttons) {
                array.put(new JSONObject()
                        .put("label", button.label)
                        .put("text", button.text)
                        .put("send", button.sendNow));
            }
            prefs().edit().putString(key(dialogId), array.toString()).apply();
        } catch (Throwable e) {
            FileLog.e("QuickButtons: не вдалося зберегти кнопки чату");
        }
    }

    public static boolean hasAny(long dialogId) {
        return !get(dialogId).isEmpty();
    }

    private static String key(long dialogId) {
        return "d" + dialogId;
    }

    // ── Положення панелі ─────────────────────────────────────────────────

    /**
     * Збережене положення або {@code -1}, якщо його ще не рухали.
     *
     * <p>Мінус один, а не нуль: нуль — це справжній лівий верхній кут, і
     * відрізнити «не рухали» від «поставили в кут» інакше було б неможливо.
     */
    public static float getPositionX() {
        return prefs().getFloat(PREF_POSITION_X, -1);
    }

    public static float getPositionY() {
        return prefs().getFloat(PREF_POSITION_Y, -1);
    }

    public static void savePosition(float x, float y) {
        prefs().edit().putFloat(PREF_POSITION_X, x).putFloat(PREF_POSITION_Y, y).apply();
    }
    // ── Вигляд кнопки ────────────────────────────────────────────────────
    //
    // Усе нижче — ДЛЯ КОЖНОГО ЧАТУ ОКРЕМО. Спершу було спільним, але з
    // робочим чатом і чатом із близькими потрібні різні кнопки: інший колір,
    // інший значок, іноді й інший режим. Спільним лишилося тільки положення:
    // рука шукає кнопку на одному місці незалежно від того, хто на тому боці.

    private static final String PREF_COLOR = "color";
    private static final String PREF_EMOJI = "emoji";
    private static final String PREF_SIZE = "size";
    private static final String PREF_ALPHA = "alpha";
    private static final String PREF_ICON = "icon";
    private static final String PREF_MODE = "mode";
    private static final String PREF_STYLE = "style";

    private static String k(long dialogId, String name) {
        return "d" + dialogId + "_" + name;
    }

    /** Нуль означає «як у темі» — колір підхоплюється з оформлення чату. */
    public static final int COLOR_THEME = 0;

    public static final int[] COLORS = {
            COLOR_THEME,
            0xFF4E8FE0, 0xFF6D3A5D, 0xFF2F6B45,
            0xFFB4531F, 0xFFA32C22, 0xFF394049,
    };

    public static int getColor(long dialogId) {
        return prefs().getInt(k(dialogId, PREF_COLOR), COLOR_THEME);
    }

    public static void setColor(long dialogId, int color) {
        prefs().edit().putInt(k(dialogId, PREF_COLOR), color).apply();
    }

    // ── Режим панелі ─────────────────────────────────────────────────────

    /** Одна кнопка, яка відкриває список. */
    public static final int MODE_SINGLE = 0;
    /** Кнопки розкладені поруч, без проміжного дотику. */
    public static final int MODE_SEPARATE = 1;

    public static int getMode(long dialogId) {
        return prefs().getInt(k(dialogId, PREF_MODE), MODE_SINGLE);
    }

    public static void setMode(long dialogId, int mode) {
        prefs().edit().putInt(k(dialogId, PREF_MODE), mode).apply();
    }

    // ── Вигляд списку ────────────────────────────────────────────────────

    public static final int STYLE_LIST = 0;
    public static final int STYLE_GRID = 1;
    public static final int STYLE_ROW = 2;

    public static int getStyle(long dialogId) {
        return prefs().getInt(k(dialogId, PREF_STYLE), STYLE_LIST);
    }

    public static void setStyle(long dialogId, int style) {
        prefs().edit().putInt(k(dialogId, PREF_STYLE), style).apply();
    }

    // ── Значок ───────────────────────────────────────────────────────────

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

    public static String getIcon(long dialogId) {
        return prefs().getString(k(dialogId, PREF_ICON), "");
    }

    public static void setIcon(long dialogId, String icon) {
        prefs().edit().putString(k(dialogId, PREF_ICON), icon == null ? "" : icon).apply();
    }

    /** Порожній рядок — значок не емодзі. */
    public static String getEmoji(long dialogId) {
        return prefs().getString(k(dialogId, PREF_EMOJI), "");
    }

    public static void setEmoji(long dialogId, String emoji) {
        prefs().edit().putString(k(dialogId, PREF_EMOJI), emoji == null ? "" : emoji.trim()).apply();
    }

    /** Діаметр у dp. Обмежений знизу, щоб у кнопку можна було влучити. */
    public static int getSize(long dialogId) {
        return Math.max(36, Math.min(prefs().getInt(k(dialogId, PREF_SIZE), 44), 72));
    }

    public static void setSize(long dialogId, int dp) {
        prefs().edit().putInt(k(dialogId, PREF_SIZE), dp).apply();
    }

    /**
     * Непрозорість у відсотках. Нижче сорока не пускаємо: напівневидиму
     * кнопку неможливо знайти, і це виглядало б як зникла функція.
     */
    public static int getAlphaPercent(long dialogId) {
        return Math.max(40, Math.min(prefs().getInt(k(dialogId, PREF_ALPHA), 100), 100));
    }

    public static void setAlphaPercent(long dialogId, int percent) {
        prefs().edit().putInt(k(dialogId, PREF_ALPHA), percent).apply();
    }

    // ── Власна картинка ──────────────────────────────────────────────────
    //
    // Теж для кожного чату: файл названо за діалогом.

    private static java.io.File imageFile(long dialogId) {
        return new java.io.File(ApplicationLoader.getFilesDirFixed(),
                "quickbutton_" + dialogId + ".png");
    }

    public static java.io.File getImage(long dialogId) {
        try {
            final java.io.File file = imageFile(dialogId);
            return file.exists() && file.length() > 0 ? file : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Копіює обрану картинку до себе.
     *
     * <p>Саме копіює, а не запам'ятовує шлях: доступ до чужого файлу можна
     * втратити будь-коли — користувач видалить фото, система відкличе дозвіл,
     * і кнопка лишиться без значка без жодного пояснення.
     */
    public static boolean setImage(long dialogId, android.net.Uri uri) {
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        try {
            in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
            if (in == null) {
                return false;
            }
            final android.graphics.Bitmap source = android.graphics.BitmapFactory.decodeStream(in);
            if (source == null) {
                return false;
            }
            // 144 пікселі: більше за найбільший розмір кнопки, і не тягне
            // зайвих мегабайтів у пам'ять на кожному відкритті чату.
            final int side = 144;
            final android.graphics.Bitmap scaled =
                    android.graphics.Bitmap.createScaledBitmap(source, side, side, true);
            out = new java.io.FileOutputStream(imageFile(dialogId));
            scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            if (scaled != source) {
                scaled.recycle();
            }
            source.recycle();
            return true;
        } catch (Throwable e) {
            FileLog.e("QuickButtons: не вдалося зберегти картинку кнопки");
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
            try { if (out != null) out.close(); } catch (Throwable ignored) { }
        }
    }

    public static void clearImage(long dialogId) {
        try {
            final java.io.File file = getImage(dialogId);
            if (file != null) {
                file.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
