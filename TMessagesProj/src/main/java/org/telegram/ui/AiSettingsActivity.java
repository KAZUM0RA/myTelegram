/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.ai.AiConfig;
import org.telegram.ai.AiKeyStorage;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TranslateAlert2;

import java.util.ArrayList;

/**
 * Екран «AI-асистент»: провайдер, ключ, модель, мова перекладу.
 *
 * <p>Замість RecyclerView з адаптером — звичайний список у ScrollView.
 * Для десятка рядків перевикористання комірок не дає нічого, а коду втричі
 * більше.
 */
public class AiSettingsActivity extends BaseFragment {

    /** Де отримати ключ і налаштувати оплату. */
    private static final String URL_GEMINI_KEY = "https://aistudio.google.com/apikey";
    private static final String URL_GEMINI_BILLING = "https://aistudio.google.com/usage";
    private static final String URL_ANTHROPIC_KEY = "https://console.anthropic.com/settings/keys";
    private static final String URL_ANTHROPIC_BILLING = "https://console.anthropic.com/settings/billing";

    private TextSettingsCell providerCell;
    private TextSettingsCell keyCell;
    private TextSettingsCell modelCell;
    private TextSettingsCell langCell;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AiSettingsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        // ── Провайдер і ключ ───────────────────────────────────────────────
        root.addView(header(context, getString(R.string.AiSectionProvider)));

        providerCell = row(context, v -> showProviderDialog());
        root.addView(providerCell);

        keyCell = row(context, v -> showKeyDialog());
        root.addView(keyCell);

        final TextSettingsCell checkCell = row(context, v -> checkConnection());
        checkCell.setText(getString(R.string.AiCheckConnection), false);
        root.addView(checkCell);

        root.addView(info(context, getString(R.string.AiKeyInfo)));

        // ── Посилання ──────────────────────────────────────────────────────
        root.addView(header(context, getString(R.string.AiSectionLinks)));

        root.addView(link(context, "Google AI Studio — ключ", URL_GEMINI_KEY, true));
        root.addView(link(context, "Google AI Studio — оплата", URL_GEMINI_BILLING, true));
        root.addView(link(context, "Anthropic Console — ключ", URL_ANTHROPIC_KEY, true));
        root.addView(link(context, "Anthropic Console — оплата", URL_ANTHROPIC_BILLING, false));

        root.addView(info(context, getString(R.string.AiLinksInfo)));

        // ── Переклад ───────────────────────────────────────────────────────
        root.addView(header(context, getString(R.string.AiSectionTranslate)));

        modelCell = row(context, v -> showModelDialog());
        root.addView(modelCell);

        langCell = row(context, v -> showLanguageDialog());
        root.addView(langCell);

        root.addView(info(context, getString(R.string.AiCostInfo)));

        updateRows();

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        scrollView.addView(root, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        fragmentView = scrollView;
        return fragmentView;
    }

    // ── Будівельні блоки ──────────────────────────────────────────────────

    private TextSettingsCell row(Context context, View.OnClickListener onClick) {
        final TextSettingsCell cell = new TextSettingsCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(onClick);
        return cell;
    }

    private TextSettingsCell link(Context context, String title, String url, boolean divider) {
        final TextSettingsCell cell = new TextSettingsCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setText(title, divider);
        cell.setOnClickListener(v -> Browser.openUrl(getParentActivity(), url));
        return cell;
    }

    private HeaderCell header(Context context, String text) {
        final HeaderCell cell = new HeaderCell(context);
        cell.setText(text);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return cell;
    }

    private TextInfoPrivacyCell info(Context context, CharSequence text) {
        final TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
        cell.setText(text);
        return cell;
    }

    private void updateRows() {
        final String provider = AiConfig.getProvider();

        if (providerCell != null) {
            providerCell.setTextAndValue(
                    getString(R.string.AiProviderRow), AiConfig.getProviderTitle(), true);
        }
        if (keyCell != null) {
            final String masked = AiKeyStorage.isAvailable()
                    ? AiKeyStorage.getMaskedKey(provider) : null;
            keyCell.setTextAndValue(
                    getString(R.string.AiKeyRow),
                    masked != null ? masked : getString(R.string.AiKeyNotSet),
                    false);
            keyCell.setEnabled(AiKeyStorage.isAvailable());
        }
        if (modelCell != null) {
            modelCell.setTextAndValue(getString(R.string.AiModelRow), AiConfig.getModelTitle(), true);
        }
        if (langCell != null) {
            final String code = AiConfig.getTargetLanguage();
            final String value = code.isEmpty()
                    ? getString(R.string.AiTargetLangAuto)
                    : TranslateAlert2.capitalFirst(TranslateAlert2.languageName(code));
            langCell.setTextAndValue(getString(R.string.AiTargetLangRow), value, false);
        }
    }

    /**
     * Перевірка зв'язку: показує моделі, доступні саме цьому ключу.
     *
     * <p>З'явилось після 404 на назві моделі, яку документація вважала
     * чинною. Замість звіряння з документацією — питаємо сам API.
     */
    private void checkConnection() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog progress =
                new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        progress.show();

        org.telegram.ai.AiClient.listModels(new org.telegram.ai.AiClient.Callback() {
            @Override
            public void onSuccess(String text) {
                progress.dismiss();
                showTextDialog(getString(R.string.AiCheckConnection), text);
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                showTextDialog(getString(R.string.AiCheckConnection), message);
            }
        });
    }

    /** Простий діалог із текстом, який можна виділити й скопіювати. */
    private void showTextDialog(String title, String text) {
        if (getParentActivity() == null) {
            return;
        }
        final Context context = getParentActivity();

        final android.widget.TextView textView = new android.widget.TextView(context);
        textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
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

    // ── Діалоги ───────────────────────────────────────────────────────────

    /**
     * Вибір провайдера. Ключ і модель зберігаються окремо для кожного, тож
     * перемикання туди-сюди нічого не втрачає.
     */
    private void showProviderDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final String[][] providers = AiConfig.AVAILABLE_PROVIDERS;
        final CharSequence[] titles = new CharSequence[providers.length];
        for (int i = 0; i < providers.length; i++) {
            titles[i] = providers[i][1];
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AiProviderRow));
        builder.setItems(titles, (dialog, which) -> {
            if (which >= 0 && which < providers.length) {
                AiConfig.setProvider(providers[which][0]);
                updateRows();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void showKeyDialog() {
        if (getParentActivity() == null) {
            return;
        }
        if (!AiKeyStorage.isAvailable()) {
            BulletinFactory.of(this)
                    .createErrorBulletin(getString(R.string.AiKeystoreUnavailable))
                    .show();
            return;
        }
        final String provider = AiConfig.getProvider();
        final Context context = getParentActivity();

        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHintText(AiConfig.isGemini() ? "AIza…" : "sk-ant-api03-…");
        editText.setBackgroundDrawable(null);
        editText.setSingleLine(true);
        // Ключ довгий і містить дефіси — без цього прапорця клавіатура
        // спробує його «виправити».
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setPadding(0, dp(4), 0, dp(4));

        final FrameLayout container = new FrameLayout(context);
        container.addView(editText, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 24, 6, 24, 0));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(AiConfig.getProviderTitle());
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            final String entered = editText.getText().toString().trim();
            if (entered.isEmpty()) {
                return;
            }
            final boolean saved = AiKeyStorage.saveKey(provider, entered);
            updateRows();
            BulletinFactory.of(this)
                    .createSimpleBulletin(
                            saved ? R.raw.chats_infotip : R.raw.error,
                            getString(saved ? R.string.AiKeySaved : R.string.AiKeySaveFailed))
                    .show();
        });
        if (AiKeyStorage.hasKey(provider)) {
            builder.setNegativeButton(getString(R.string.Delete), (dialog, which) -> {
                AiKeyStorage.clear(provider);
                updateRows();
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.chats_infotip, getString(R.string.AiKeyRemoved))
                        .show();
            });
        } else {
            builder.setNegativeButton(getString(R.string.Cancel), null);
        }
        builder.show();

        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 100);
    }

    private void showModelDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final String[][] models = AiConfig.availableModels(AiConfig.getProvider());
        final CharSequence[] titles = new CharSequence[models.length];
        for (int i = 0; i < models.length; i++) {
            titles[i] = models[i][1];
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AiModelRow));
        builder.setItems(titles, (dialog, which) -> {
            if (which >= 0 && which < models.length) {
                AiConfig.setModel(models[which][0]);
                updateRows();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    /**
     * Вибір мови перекладу.
     *
     * <p>Список беремо з {@code TranslateController.getLanguages()} — той самий,
     * що Telegram використовує для власного перекладу, тож назви мов узгоджені
     * з рештою інтерфейсу.
     */
    private void showLanguageDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final ArrayList<TranslateController.Language> languages = TranslateController.getLanguages();

        final CharSequence[] titles = new CharSequence[languages.size() + 1];
        titles[0] = getString(R.string.AiTargetLangAuto);
        for (int i = 0; i < languages.size(); i++) {
            titles[i + 1] = languages.get(i).displayName;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AiTargetLangRow));
        builder.setItems(titles, (dialog, which) -> {
            if (which == 0) {
                AiConfig.setTargetLanguage(AiConfig.TARGET_LANG_AUTO);
            } else if (which - 1 < languages.size()) {
                AiConfig.setTargetLanguage(languages.get(which - 1).code);
            }
            updateRows();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
    }
}
