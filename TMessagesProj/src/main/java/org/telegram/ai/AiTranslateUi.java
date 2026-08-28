/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TranslateAlert2;

/**
 * Інтерфейс перекладу тексту, який користувач збирається відправити.
 *
 * <p>Логіка винесена сюди навмисно: {@code ChatActivityEnterView} — файл на
 * 777 КБ, і що менша там наша правка, то легше буде зливати оновлення
 * апстріму. Там лишається тільки кнопка й один виклик.
 *
 * <p><b>Повідомлення не відправляється автоматично.</b> Переклад показується
 * для перевірки, і за підтвердження підставляється в поле вводу — далі
 * користувач тисне «надіслати» сам. Автовідправка тексту, згенерованого
 * моделлю, у месенджері неприйнятна: помилку перекладу видно лише після
 * того, як її вже прочитав співрозмовник.
 */
public class AiTranslateUi {

    private AiTranslateUi() {}

    /**
     * Перекладає текст і, за підтвердження користувача, повертає результат
     * через {@code onAccepted}.
     *
     * @param dialogId  діалог — потрібен, щоб визначити мову співрозмовника
     * @param text      те, що зараз у полі вводу
     * @param onAccepted викликається в UI-потоці з фінальним текстом
     */
    public static void translateForSending(BaseFragment fragment, long dialogId,
                                           CharSequence text, Utilities.Callback<String> onAccepted) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();

        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.toString().trim())) {
            BulletinFactory.of(fragment)
                    .createErrorBulletin(getString(R.string.AiErrorEmptyText))
                    .show();
            return;
        }
        if (!AiConfig.isReady()) {
            BulletinFactory.of(fragment)
                    .createErrorBulletin(getString(R.string.AiErrorNoKey))
                    .show();
            return;
        }

        final AlertDialog progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        progress.show();

        AiClient.translate(text.toString(), resolveTargetLanguage(dialogId), new AiClient.Callback() {
            @Override
            public void onSuccess(String translated) {
                progress.dismiss();
                showPreview(fragment, translated, onAccepted);
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                BulletinFactory.of(fragment).createErrorBulletin(message).show();
            }
        });
    }

    /**
     * Мова, на яку перекладаємо.
     *
     * <p>Якщо в налаштуваннях не задано явно — беремо мову, яку Telegram уже
     * визначив для цього діалогу за вхідними повідомленнями. Свій
     * визначальник мови писати не треба: {@code TranslateController} робить
     * це для власної функції перекладу, і результат уже лежить готовий.
     *
     * <p>Якщо мова невідома (новий чат, самі стікери) — повертаємо порожній
     * рядок, і рішення приймає модель за контекстом.
     */
    private static String resolveTargetLanguage(long dialogId) {
        final String configured = AiConfig.getTargetLanguage();
        if (!TextUtils.isEmpty(configured)) {
            return TranslateAlert2.languageName(configured);
        }
        try {
            final String detected = MessagesController.getInstance(UserConfig.selectedAccount)
                    .getTranslateController()
                    .getDialogDetectedLanguage(dialogId);
            if (!TextUtils.isEmpty(detected) && !"und".equals(detected)) {
                final String name = TranslateAlert2.languageName(detected);
                if (!TextUtils.isEmpty(name)) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
            // Визначення мови — зручність, а не обов'язкова умова. Якщо щось
            // пішло не так, краще перекласти за контекстом, ніж не перекласти.
        }
        return AiConfig.TARGET_LANG_AUTO;
    }

    /** Прев'ю з можливістю відредагувати переклад перед підстановкою в поле. */
    private static void showPreview(BaseFragment fragment, String translated,
                                    Utilities.Callback<String> onAccepted) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();

        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setBackgroundDrawable(null);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setMaxLines(8);
        editText.setText(translated);
        editText.setSelection(editText.getText().length());

        final FrameLayout container = new FrameLayout(context);
        container.addView(editText, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 24, 6, 24, 0));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.AiTranslatePreviewTitle));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.AiTranslateInsert), (dialog, which) -> {
            final String finalText = editText.getText().toString();
            if (!TextUtils.isEmpty(finalText) && onAccepted != null) {
                onAccepted.run(finalText);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();

        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 100);
    }
}
