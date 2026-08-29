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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
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

        doTranslate(fragment, dialogId, text.toString(), resolveTargetLanguage(dialogId), onAccepted);
    }

    /**
     * Один прохід перекладу з показом результату.
     *
     * <p>Винесено окремо, щоб «Інша мова» в прев'ю могла перекласти той самий
     * оригінал ще раз — не той, що вже перекладений, інакше кожна зміна мови
     * була б перекладом перекладу з накопиченням спотворень.
     */
    private static void doTranslate(BaseFragment fragment, long dialogId, String originalText,
                                    String languageName, Utilities.Callback<String> onAccepted) {
        final AlertDialog progress =
                new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        progress.show();

        AiClient.translate(originalText, languageName, new AiClient.Callback() {
            @Override
            public void onSuccess(String translated) {
                progress.dismiss();
                showPreview(fragment, translated, onAccepted,
                        () -> pickLanguageAndRetranslate(fragment, dialogId, originalText, onAccepted));
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                BulletinFactory.of(fragment).createErrorBulletin(message).show();
            }
        });
    }

    /**
     * Вибір мови для цього чату.
     *
     * <p>Обране ЗАПАМ'ЯТОВУЄТЬСЯ для діалогу: інакше при кожному перекладі
     * довелося б вибирати мову заново, а з тим самим співрозмовником вона
     * зазвичай одна й та сама.
     */
    private static void pickLanguageAndRetranslate(BaseFragment fragment, long dialogId,
                                                   String originalText,
                                                   Utilities.Callback<String> onAccepted) {
        AiLanguagePicker.show(fragment, getString(R.string.AiTargetLangRow), true, code -> {
            AiConfig.setOutgoingLanguage(dialogId, code);
            doTranslate(fragment, dialogId, originalText, resolveTargetLanguage(dialogId), onAccepted);
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
        final String configured = AiConfig.getOutgoingLanguage(dialogId);
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

    /**
     * Переклад вхідного повідомлення.
     *
     * <p>Цільова мова тут протилежна до {@link #translateForSending}: вхідне
     * перекладаємо <b>на свою</b> мову, а не на мову співрозмовника.
     *
     * <p>Показуємо оригінал і переклад разом, а не замінюємо текст у чаті, як
     * робить вбудований переклад Telegram. Так видно, що саме було сказано, —
     * важливо, коли переклад виглядає дивно і треба звірити з першоджерелом.
     */
    public static void translateIncoming(BaseFragment fragment, CharSequence original) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (TextUtils.isEmpty(original) || TextUtils.isEmpty(original.toString().trim())) {
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

        final AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        progress.show();

        AiClient.translate(original.toString(), myLanguageName(), new AiClient.Callback() {
            @Override
            public void onSuccess(String translated) {
                progress.dismiss();
                showIncomingResult(fragment, original.toString(), translated);
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                BulletinFactory.of(fragment).createErrorBulletin(message).show();
            }
        });
    }

    /**
     * Мова, якою читає користувач: явно задана в налаштуваннях або мова
     * інтерфейсу застосунку.
     */
    /**
     * Мова, якою читає користувач.
     *
     * <p>Навмисно НЕ бере налаштування «мова перекладу» — воно стосується
     * лише вихідних. Вхідні завжди перекладаються на мову системи: читати
     * їх чужою мовою сенсу немає.
     */
    private static String myLanguageName() {
        final String name = TranslateAlert2.languageName(AiConfig.getReadingLanguageCode());
        return TextUtils.isEmpty(name) ? AiConfig.TARGET_LANG_AUTO : name;
    }

    /** Оригінал зверху приглушено, переклад під ним — той самий порядок, що в брифі. */
    private static void showIncomingResult(BaseFragment fragment, String original, String translated) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        final TextView originalView = new TextView(context);
        originalView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        originalView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        originalView.setText(original);
        column.addView(originalView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        final TextView translatedView = new TextView(context);
        translatedView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        translatedView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        translatedView.setText(translated);
        // Переклад можна виділити й скопіювати — оригінал уже є в чаті,
        // а от переклад більше ніде не збережеться.
        translatedView.setTextIsSelectable(true);
        column.addView(translatedView, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(column, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        final FrameLayout container = new FrameLayout(context);
        container.addView(scroll, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 24, 6, 24, 0));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.AiTranslatePreviewTitle));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Copy), (dialog, which) ->
                AndroidUtilities.addToClipboard(translated));
        builder.setNegativeButton(getString(R.string.Close), null);
        builder.show();
    }

    /** Прев'ю з можливістю відредагувати переклад перед підстановкою в поле. */
    /** Форк: доступно сусіднім класам пакета — той самий діалог потрібен і в AiAssistUi. */
    static void showPreview(BaseFragment fragment, String translated,
                            Utilities.Callback<String> onAccepted) {
        showPreview(fragment, translated, onAccepted, null);
    }

    /**
     *  onChangeLanguage якщо не null — у діалозі з'являється третя кнопка
     *                         «Інша мова». Для чернетки відповіді й виправлення
     *                         стилю вона зайва, тож там передається null.
     */
    static void showPreview(BaseFragment fragment, String translated,
                            Utilities.Callback<String> onAccepted,
                            Runnable onChangeLanguage) {
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
        if (onChangeLanguage != null) {
            builder.setNeutralButton(getString(R.string.AiOtherLanguage), (dialog, which) -> onChangeLanguage.run());
        }
        builder.show();

        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 100);
    }
}
