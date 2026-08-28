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

/**
 * Екран «AI-асистент»: ключ API, модель, мова перекладу.
 *
 * <p>Замість RecyclerView з адаптером тут звичайний список у ScrollView.
 * Для шести рядків адаптер — це втричі більше коду заради нуля виграшу:
 * перевикористання комірок має сенс, коли їх сотні.
 */
public class AiSettingsActivity extends BaseFragment {

    private TextSettingsCell keyCell;
    private TextSettingsCell modelCell;

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

        // ── Ключ ───────────────────────────────────────────────────────────
        root.addView(header(context, getString(R.string.AiSectionKey)));

        keyCell = new TextSettingsCell(context);
        keyCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        keyCell.setOnClickListener(v -> showKeyDialog());
        root.addView(keyCell);

        root.addView(info(context, getString(R.string.AiKeyInfo)));

        // ── Переклад ───────────────────────────────────────────────────────
        root.addView(header(context, getString(R.string.AiSectionTranslate)));

        modelCell = new TextSettingsCell(context);
        modelCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        modelCell.setOnClickListener(v -> showModelDialog());
        root.addView(modelCell);

        root.addView(info(context, getString(R.string.AiCostInfo)));

        updateRows();

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        scrollView.addView(root, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        fragmentView = scrollView;
        return fragmentView;
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
        if (keyCell != null) {
            final String masked = AiKeyStorage.isAvailable() ? AiKeyStorage.getMaskedKey() : null;
            keyCell.setTextAndValue(
                    getString(R.string.AiKeyRow),
                    masked != null ? masked : getString(R.string.AiKeyNotSet),
                    true);
            keyCell.setEnabled(AiKeyStorage.isAvailable());
        }
        if (modelCell != null) {
            modelCell.setTextAndValue(
                    getString(R.string.AiModelRow), AiConfig.getModelTitle(), false);
        }
    }

    private void showKeyDialog() {
        if (getParentActivity() == null) {
            return;
        }
        if (!AiKeyStorage.isAvailable()) {
            // Пристрій без AES у Keystore (до Android 6). Зберігати ключ до
            // Anthropic у відкритому вигляді ми не будемо, тож чесно кажемо,
            // що функція недоступна.
            BulletinFactory.of(this)
                    .createErrorBulletin(getString(R.string.AiKeystoreUnavailable))
                    .show();
            return;
        }

        final Context context = getParentActivity();

        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHintText(getString(R.string.AiKeyHint));
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
        builder.setTitle(getString(R.string.AiSectionKey));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            final String entered = editText.getText().toString().trim();
            if (entered.isEmpty()) {
                return;
            }
            final boolean saved = AiKeyStorage.saveKey(entered);
            updateRows();
            BulletinFactory.of(this)
                    .createSimpleBulletin(
                            saved ? R.raw.chats_infotip : R.raw.error,
                            getString(saved ? R.string.AiKeySaved : R.string.AiKeySaveFailed))
                    .show();
        });
        if (AiKeyStorage.hasKey()) {
            builder.setNegativeButton(getString(R.string.Delete), (dialog, which) -> {
                AiKeyStorage.clear();
                updateRows();
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.chats_infotip, getString(R.string.AiKeyRemoved))
                        .show();
            });
        } else {
            builder.setNegativeButton(getString(R.string.Cancel), null);
        }
        builder.show();

        // Клавіатура одразу: користувач прийшов сюди саме щоб вставити ключ.
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 100);
    }

    private void showModelDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final String[][] models = AiConfig.AVAILABLE_MODELS;
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

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
    }
}
