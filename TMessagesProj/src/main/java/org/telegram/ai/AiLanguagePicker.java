/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ai;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Вибір мови з пошуком.
 *
 * <p>Простий {@code setItems} тут не годиться: мов близько сотні, і гортати
 * їх щоразу незручно. Список фільтрується по полю {@code q}, яке
 * {@code TranslateController} уже заповнює назвою мови обома мовами —
 * і місцевою, і власною. Тобто «німецька» і «deutsch» знайдуть те саме.
 */
public class AiLanguagePicker {

    private AiLanguagePicker() {}

    /**
     * @param withAuto чи показувати перший пункт «мова співрозмовника»;
     *                 він повертає порожній код
     * @param onPicked отримує код мови або порожній рядок для автовизначення
     */
    public static void show(BaseFragment fragment, String title, boolean withAuto,
                            Utilities.Callback<String> onPicked) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        final Context context = fragment.getParentActivity();
        final ArrayList<TranslateController.Language> languages = TranslateController.getLanguages();

        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(list, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        final EditTextBoldCursor search = new EditTextBoldCursor(context);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        search.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        search.setHintText(getString(R.string.Search));
        search.setBackgroundDrawable(null);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        final LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(search, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        // Обмежуємо висоту списку: без цього діалог зі ста мовами розтягнувся б
        // на весь екран і сховав поле пошуку.
        column.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 320));

        final FrameLayout container = new FrameLayout(context);
        container.addView(column, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 22, 4, 22, 0));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(container);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        final AlertDialog dialog = builder.show();

        final Utilities.Callback<String> pick = code -> {
            dialog.dismiss();
            AndroidUtilities.hideKeyboard(search);
            onPicked.run(code);
        };

        rebuild(context, list, languages, withAuto, "", pick);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                rebuild(context, list, languages, withAuto, s.toString().toLowerCase(), pick);
            }
        });
    }

    /**
     * Перебудовує список під запит.
     *
     * <p>Повне перестворення, а не адаптер із фільтром: сотня простих рядків
     * будується миттєво, а коду в рази менше.
     */
    private static void rebuild(Context context, LinearLayout list,
                                ArrayList<TranslateController.Language> languages,
                                boolean withAuto, String query,
                                Utilities.Callback<String> onPicked) {
        list.removeAllViews();

        if (withAuto && TextUtils.isEmpty(query)) {
            list.addView(item(context, getString(R.string.AiTargetLangAuto), () -> onPicked.run("")),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        for (TranslateController.Language language : languages) {
            if (!TextUtils.isEmpty(query) && (language.q == null || !language.q.contains(query))) {
                continue;
            }
            final String code = language.code;
            list.addView(item(context, language.displayName, () -> onPicked.run(code)),
                    LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private static TextView item(Context context, String text, Runnable onClick) {
        final TextView view = new TextView(context);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        view.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        view.setText(text);
        view.setPadding(dp(4), dp(12), dp(4), dp(12));
        view.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
        view.setOnClickListener(v -> onClick.run());
        return view;
    }
}
