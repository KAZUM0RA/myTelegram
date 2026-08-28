/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Зберігання ключа Anthropic API.
 *
 * <p>Ключ шифрується AES-256/GCM, і — головне — <b>сам ключ шифрування ніколи
 * не залишає Android Keystore</b>. На пристроях з апаратним модулем безпеки
 * (а Xiaomi 12 такий має) він фізично не існує поза захищеним середовищем:
 * ми передаємо туди дані й отримуємо результат, але самого ключа не бачимо
 * навіть ми. Тому дамп файлів застосунку — навіть з root — не дає нічого,
 * крім зашифрованого блоку.
 *
 * <p>GCM обрано не випадково: він не лише шифрує, а й перевіряє цілісність.
 * Якщо зашифрований блок хтось підмінить, розшифрування впаде з помилкою,
 * а не поверне сміття, яке ми потім відправимо в мережу.
 *
 * <p>Навмисно без сторонніх бібліотек: androidx.security.crypto
 * (EncryptedSharedPreferences) робить приблизно те саме, але тягне залежність
 * у застосунок, який обходиться без них. Тут усе на стандартному API.
 */
public class AiKeyStorage {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "mytelegram_ai_apikey";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final String PREFS = "ai_config";
    private static final String PREF_ENCRYPTED = "api_key_enc";

    /** Довжина тега автентичності GCM у бітах — стандартне значення. */
    private static final int GCM_TAG_BITS = 128;
    /** Довжина IV для GCM у байтах. 12 — рекомендація NIST для цього режиму. */
    private static final int GCM_IV_BYTES = 12;

    private AiKeyStorage() {}

    /**
     * Чи доступне шифроване сховище на цьому пристрої.
     *
     * <p>AES у Android Keystore з'явився в API 23. Нижче ми ключ не зберігаємо
     * взагалі — краще чесно вимкнути AI-функції, ніж класти ключ до Anthropic
     * у відкритому вигляді й вдавати, що він захищений.
     */
    public static boolean isAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /** Чи збережений ключ. Сам ключ при цьому не розшифровується. */
    public static boolean hasKey() {
        return isAvailable() && !prefs().getString(PREF_ENCRYPTED, "").isEmpty();
    }

    /**
     * Зберігає ключ. Порожній рядок або null стирають збережене.
     *
     * @return true, якщо операція вдалася
     */
    public static boolean saveKey(String apiKey) {
        if (!isAvailable()) {
            return false;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            clear();
            return true;
        }
        try {
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());

            final byte[] iv = cipher.getIV();
            final byte[] encrypted = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));

            // IV не є секретом, але потрібен для розшифрування, тож кладемо
            // його перед даними одним блоком.
            final byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            prefs().edit()
                    .putString(PREF_ENCRYPTED, Base64.encodeToString(combined, Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Throwable e) {
            // Свідомо логуємо лише факт помилки, без винятку з деталями:
            // трасування може містити фрагменти вхідних даних.
            FileLog.e("AiKeyStorage: не вдалося зберегти ключ");
            return false;
        }
    }

    /**
     * Повертає розшифрований ключ або null.
     *
     * <p>Викликати лише в момент запиту до API. Не кешувати в полях і не
     * передавати далі, ніж потрібно — що менше місць у пам'яті, то краще.
     */
    public static String getKey() {
        if (!isAvailable()) {
            return null;
        }
        final String stored = prefs().getString(PREF_ENCRYPTED, "");
        if (stored.isEmpty()) {
            return null;
        }
        try {
            final byte[] combined = Base64.decode(stored, Base64.NO_WRAP);
            if (combined.length <= GCM_IV_BYTES) {
                return null;
            }
            final byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            final byte[] encrypted = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, GCM_IV_BYTES, encrypted, 0, encrypted.length);

            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            // Найчастіша причина — ключ Keystore зник: користувач змінив
            // блокування екрана, відновив застосунок з бекапу на інший
            // пристрій тощо. Розшифрувати вже неможливо, тож прибираємо
            // непотріб і просимо ввести ключ заново.
            FileLog.e("AiKeyStorage: не вдалося розшифрувати ключ, стираю збережене");
            clear();
            return null;
        }
    }

    /** Стирає збережений ключ. Сам ключ Keystore лишається — він безпечний і порожній. */
    public static void clear() {
        prefs().edit().remove(PREF_ENCRYPTED).apply();
    }

    /**
     * Маска для показу в інтерфейсі: {@code sk-ant-...a1b2}.
     * Повний ключ на екран не виводимо ніде й ніколи.
     */
    public static String getMaskedKey() {
        final String key = getKey();
        if (key == null || key.length() < 8) {
            return null;
        }
        return key.substring(0, Math.min(7, key.length())) + "…" + key.substring(key.length() - 4);
    }

    private static SecretKey getOrCreateSecretKey() throws Exception {
        final KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);

        final KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }

        final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // setUserAuthenticationRequired(true) вимагало б розблокування
                // екрана на кожен переклад — для цього сценарію надто нав'язливо.
                .build());
        return generator.generateKey();
    }

    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }
}
