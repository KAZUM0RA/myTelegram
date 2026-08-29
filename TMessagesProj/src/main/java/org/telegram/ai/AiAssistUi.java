/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Додаткові AI-функції: підсумок переписки, чернетка відповіді,
 * виправлення стилю.
 *
 * <p>Усі три — поверх того самого {@link AiClient#request}. Різниця лише в
 * промті й у тому, куди подівається результат: підсумок показуємо для
 * читання, а відповідь і виправлений текст підставляємо в поле вводу.
 *
 * <p>Як і з перекладом, <b>нічого не відправляється саме</b>. Згенерований
 * моделлю текст завжди проходить через прев'ю, де його можна відредагувати
 * або відхилити.
 */
public class AiAssistUi {

    /**
     * Скільки останніх повідомлень беремо в підсумок.
     *
     * <p>Компроміс: замало — підсумок втратить контекст, забагато — зросте
     * вартість запиту й модель почне переказувати давнє замість важливого.
     * Шістдесят приблизно відповідає «сьогоднішній розмові».
     */
    private static final int SUMMARY_MESSAGE_LIMIT = 60;

    /** Захист від надсилання гігантського запиту, якщо в чаті довгі полотна. */
    private static final int SUMMARY_CHAR_LIMIT = 12000;

    private AiAssistUi() {}

    // ── Підсумок переписки ───────────────────────────────────────────────

    public static void summarize(BaseFragment fragment, List<MessageObject> messages) {
        if (!ready(fragment)) {
            return;
        }
        final String transcript = buildTranscript(messages);
        if (TextUtils.isEmpty(transcript)) {
            BulletinFactory.of(fragment)
                    .createErrorBulletin(getString(R.string.AiErrorEmptyText))
                    .show();
            return;
        }

        final String system =
                "Ти читаєш переписку і робиш стислий підсумок. "
                        + "Мова підсумку: " + myLanguage() + ". "
                        + "Опиши: про що йшлося, які рішення ухвалені, що лишилось "
                        + "невирішеним або потребує відповіді. "
                        + "Пиши по суті, без вступів на кшталт «ось підсумок». "
                        + "Якщо переписка коротка чи беззмістовна — так і скажи, "
                        + "не вигадуй важливості.";

        run(fragment, system, transcript,
                result -> showReadOnly(fragment, getString(R.string.AiSummaryTitle), result));
    }

    /**
     * Збирає переписку в текст для моделі.
     *
     * <p>Ідемо від найновіших назад, щоб при обрізанні за лімітом втратити
     * давнє, а не свіже — у підсумку важливіше останнє. Наприкінці
     * розвертаємо, бо модель має читати діалог у природному порядку.
     */
    private static String buildTranscript(List<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        final ArrayList<String> lines = new ArrayList<>();
        int chars = 0;
        for (int i = 0; i < messages.size() && lines.size() < SUMMARY_MESSAGE_LIMIT; i++) {
            final MessageObject message = messages.get(i);
            if (message == null || message.messageOwner == null) {
                continue;
            }
            final CharSequence text = message.messageOwner.message;
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            final String line = (message.isOutOwner() ? "Я: " : "Співрозмовник: ") + text;
            if (chars + line.length() > SUMMARY_CHAR_LIMIT) {
                break;
            }
            chars += line.length();
            lines.add(line);
        }
        if (lines.isEmpty()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    // ── Чернетка відповіді ───────────────────────────────────────────────

    public static void suggestReply(BaseFragment fragment, MessageObject message,
                                    Utilities.Callback<String> onAccepted) {
        if (!ready(fragment)) {
            return;
        }
        final CharSequence text = message == null || message.messageOwner == null
                ? null : message.messageOwner.message;
        if (TextUtils.isEmpty(text)) {
            BulletinFactory.of(fragment)
                    .createErrorBulletin(getString(R.string.AiErrorEmptyText))
                    .show();
            return;
        }

        // Просимо саме одну відповідь: список варіантів довелося б показувати
        // окремим інтерфейсом, а прев'ю з редагуванням і так дозволяє
        // переписати запропоноване.
        final String system =
                "Тобі дають повідомлення від співрозмовника. Напиши доречну "
                        + "коротку відповідь ТІЄЮ САМОЮ мовою, якою написане "
                        + "повідомлення. Тон — такий самий, як в оригіналі: на "
                        + "неформальне відповідай неформально. "
                        + "Дай ВИКЛЮЧНО текст відповіді, один варіант, без лапок "
                        + "і без пояснень.";

        run(fragment, system, text.toString(),
                result -> AiTranslateUi.showPreview(fragment, result, onAccepted));
    }

    // ── Виправлення стилю ────────────────────────────────────────────────

    public static void improveStyle(BaseFragment fragment, CharSequence text,
                                    Utilities.Callback<String> onAccepted) {
        if (!ready(fragment)) {
            return;
        }
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.toString().trim())) {
            BulletinFactory.of(fragment)
                    .createErrorBulletin(getString(R.string.AiErrorEmptyText))
                    .show();
            return;
        }

        final String system =
                "Виправ у тексті користувача граматику, орфографію й пунктуацію. "
                        + "Мову НЕ міняй — відповідай тією самою, якою написаний "
                        + "оригінал. Зміст, тон і рівень формальності збережи: "
                        + "не роби розмовне діловим. Якщо помилок немає — поверни "
                        + "текст без змін. "
                        + "Дай ВИКЛЮЧНО виправлений текст, без пояснень і без лапок.";

        run(fragment, system, text.toString(),
                result -> AiTranslateUi.showPreview(fragment, result, onAccepted));
    }

    // ── Спільне ──────────────────────────────────────────────────────────

    private static boolean ready(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return false;
        }
        if (!AiConfig.isReady()) {
            BulletinFactory.of(fragment)
                    .createErrorBulletin(getString(R.string.AiErrorNoKey))
                    .show();
            return false;
        }
        return true;
    }

    /** Запит із індикатором очікування й показом помилки. */
    private static void run(BaseFragment fragment, String system, String userText,
                            Utilities.Callback<String> onResult) {
        final AlertDialog progress =
                new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        progress.show();

        AiClient.request(system, userText, new AiClient.Callback() {
            @Override
            public void onSuccess(String text) {
                progress.dismiss();
                onResult.run(text);
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                BulletinFactory.of(fragment).createErrorBulletin(message).show();
            }
        });
    }

    /** Показ результату, який нікуди не підставляється — лише читання й копіювання. */
    private static void showReadOnly(BaseFragment fragment, String title, String text) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();

        final TextView textView = new TextView(context);
        textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setText(text);
        textView.setTextIsSelectable(true);

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(textView, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        final FrameLayout container = new FrameLayout(context);
        container.addView(scroll, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 24, 6, 24, 0));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Copy), (dialog, which) ->
                AndroidUtilities.addToClipboard(text));
        builder.setNegativeButton(getString(R.string.Close), null);
        builder.show();
    }

    /** Мова, якою користувач читає — для підсумку. */
    private static String myLanguage() {
        final String configured = AiConfig.getTargetLanguage();
        try {
            final String code = TextUtils.isEmpty(configured)
                    ? LocaleController.getInstance().getCurrentLocale().getLanguage()
                    : configured;
            final String name = org.telegram.ui.Components.TranslateAlert2.languageName(code);
            if (!TextUtils.isEmpty(name)) {
                // Назву мови віддаємо як є, у називному відмінку. Відмінювати
                // її в промті не варто: у слов'янських мовах це вимагало б
                // окремої логіки, а модель і так зрозуміє «Мова підсумку: X».
                return name;
            }
        } catch (Throwable ignored) {
        }
        return "мова користувача";
    }
}
