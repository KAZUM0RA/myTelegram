/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.R;
import org.telegram.quickbuttons.QuickButtons;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Налаштування швидких кнопок для одного чату.
 *
 * <p>Саме для одного: набір заготовок прив'язаний до співрозмовника, і
 * спільний список для всіх чатів швидко став би звалищем.
 */
public class QuickButtonsActivity extends BaseFragment {

    private final long dialogId;
    private final Runnable onChanged;
    private ArrayList<QuickButtons.Button> buttons;
    private LinearLayout listLayout;

    public QuickButtonsActivity(long dialogId, Runnable onChanged) {
        this.dialogId = dialogId;
        this.onChanged = onChanged;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.QuickButtonsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        buttons = QuickButtons.get(dialogId);

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(listLayout);

        final TextSettingsCell add = new TextSettingsCell(context);
        add.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        add.setText(getString(R.string.QuickButtonsAdd), false);
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        add.setOnClickListener(v -> {
            if (buttons.size() >= QuickButtons.MAX_BUTTONS) {
                BulletinFactory.of(this)
                        .createErrorBulletin(getString(R.string.QuickButtonsTooMany)).show();
                return;
            }
            edit(null);
        });
        root.addView(add);

        final TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setText(getString(R.string.QuickButtonsInfo));
        root.addView(info);

        // ── Вигляд самої кнопки ──────────────────────────────────────────
        // Налаштування спільні для всіх чатів: це та сама кнопка, лише
        // вміст списку різний.
        final org.telegram.ui.Cells.HeaderCell appearance =
                new org.telegram.ui.Cells.HeaderCell(context);
        appearance.setText(getString(R.string.QuickButtonsAppearance));
        appearance.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        root.addView(appearance);

        colorCell = new TextSettingsCell(context);
        colorCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        colorCell.setOnClickListener(v -> pickColor());
        root.addView(colorCell);

        emojiCell = new TextSettingsCell(context);
        emojiCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        emojiCell.setOnClickListener(v -> pickEmoji());
        root.addView(emojiCell);

        sizeCell = new TextSettingsCell(context);
        sizeCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        sizeCell.setOnClickListener(v -> pickNumber(true));
        root.addView(sizeCell);

        alphaCell = new TextSettingsCell(context);
        alphaCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        alphaCell.setOnClickListener(v -> pickNumber(false));
        root.addView(alphaCell);

        final TextInfoPrivacyCell appearanceInfo = new TextInfoPrivacyCell(context);
        appearanceInfo.setText(getString(R.string.QuickButtonsAppearanceInfo));
        root.addView(appearanceInfo);

        updateAppearanceRows();

        rebuild();

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        scrollView.addView(root, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        fragmentView = scrollView;
        return fragmentView;
    }

    private void rebuild() {
        if (listLayout == null || getParentActivity() == null) {
            return;
        }
        listLayout.removeAllViews();
        final Context context = getParentActivity();
        for (int i = 0; i < buttons.size(); i++) {
            final QuickButtons.Button button = buttons.get(i);
            final TextSettingsCell cell = new TextSettingsCell(context);
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            // У значенні показуємо саме текст, який піде у чат: назва кнопки
            // може бути короткою, і без цього легко забути, що за нею стоїть.
            cell.setTextAndValue(button.label, button.text, true);
            cell.setOnClickListener(v -> edit(button));
            listLayout.addView(cell);
        }
    }

    /** @param existing {@code null} — створюємо нову кнопку. */
    private void edit(QuickButtons.Button existing) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }

        final LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(6), dp(24), 0);

        final EditTextBoldCursor labelField = field(context,
                getString(R.string.QuickButtonsLabelHint), false);
        final EditTextBoldCursor textField = field(context,
                getString(R.string.QuickButtonsTextHint), true);
        if (existing != null) {
            labelField.setText(existing.label);
            textField.setText(existing.text);
        }
        container.addView(labelField, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(textField, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        // Режим кожної кнопки окремо: надіслати відразу чи покласти в поле.
        final org.telegram.ui.Cells.TextCheckCell sendCell =
                new org.telegram.ui.Cells.TextCheckCell(context);
        sendCell.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        final boolean[] sendNow = { existing == null || existing.sendNow };
        sendCell.setTextAndCheck(getString(R.string.QuickButtonsSendNow), sendNow[0], false);
        sendCell.setOnClickListener(v -> {
            sendNow[0] = !sendNow[0];
            sendCell.setChecked(sendNow[0]);
        });
        container.addView(sendCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -24, 8, -24, 0));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(existing == null
                ? R.string.QuickButtonsAdd : R.string.QuickButtonsEdit));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            final String label = labelField.getText().toString().trim();
            final String text = textField.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                return;
            }
            if (existing == null) {
                // Порожня назва — беремо початок самого тексту: змушувати
                // вигадувати підпис для короткої фрази зайве.
                buttons.add(new QuickButtons.Button(
                        TextUtils.isEmpty(label) ? shorten(text) : label, text, sendNow[0]));
            } else {
                existing.label = TextUtils.isEmpty(label) ? shorten(text) : label;
                existing.text = text;
                existing.sendNow = sendNow[0];
            }
            persist();
        });
        if (existing != null) {
            builder.setNeutralButton(getString(R.string.Delete), (dialog, which) -> {
                buttons.remove(existing);
                persist();
            });
        }
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    // ── Вигляд кнопки ────────────────────────────────────────────────────

    private TextSettingsCell colorCell, emojiCell, sizeCell, alphaCell;

    private void updateAppearanceRows() {
        if (colorCell == null) {
            return;
        }
        final int color = QuickButtons.getColor();
        colorCell.setTextAndValue(getString(R.string.QuickButtonsColor),
                getString(color == QuickButtons.COLOR_THEME
                        ? R.string.QuickButtonsColorTheme : R.string.QuickButtonsColorOwn), true);
        final String emoji = QuickButtons.getEmoji();
        emojiCell.setTextAndValue(getString(R.string.QuickButtonsEmoji),
                TextUtils.isEmpty(emoji) ? getString(R.string.QuickButtonsEmojiNone) : emoji, true);
        sizeCell.setTextAndValue(getString(R.string.QuickButtonsSize),
                QuickButtons.getSize() + " dp", true);
        alphaCell.setTextAndValue(getString(R.string.QuickButtonsAlpha),
                QuickButtons.getAlphaPercent() + "%", false);
    }

    private void pickColor() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final CharSequence[] names = new CharSequence[QuickButtons.COLORS.length];
        for (int i = 0; i < QuickButtons.COLORS.length; i++) {
            names[i] = QuickButtons.COLORS[i] == QuickButtons.COLOR_THEME
                    ? getString(R.string.QuickButtonsColorTheme)
                    : String.format("#%06X", QuickButtons.COLORS[i] & 0xFFFFFF);
        }
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsColor))
                .setItems(names, (dialog, which) -> {
                    QuickButtons.setColor(QuickButtons.COLORS[which]);
                    appearanceChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void pickEmoji() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor editText = field(context,
                getString(R.string.QuickButtonsEmojiHint), false);
        editText.setText(QuickButtons.getEmoji());
        final LinearLayout container = new LinearLayout(context);
        container.setPadding(dp(24), dp(6), dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsEmoji))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    QuickButtons.setEmoji(editText.getText().toString());
                    appearanceChanged();
                })
                // Порожній значок — це не «скасувати», а окремий вибір:
                // повернутися до типової стрілки.
                .setNeutralButton(getString(R.string.QuickButtonsEmojiNone), (dialog, which) -> {
                    QuickButtons.setEmoji("");
                    appearanceChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** @param size {@code true} — розмір, {@code false} — прозорість. */
    private void pickNumber(boolean size) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final int[] values = size
                ? new int[]{36, 44, 52, 60, 72}
                : new int[]{40, 60, 80, 100};
        final CharSequence[] names = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = size ? values[i] + " dp" : values[i] + "%";
        }
        new AlertDialog.Builder(context)
                .setTitle(getString(size ? R.string.QuickButtonsSize : R.string.QuickButtonsAlpha))
                .setItems(names, (dialog, which) -> {
                    if (size) {
                        QuickButtons.setSize(values[which]);
                    } else {
                        QuickButtons.setAlphaPercent(values[which]);
                    }
                    appearanceChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void appearanceChanged() {
        updateAppearanceRows();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private String shorten(String text) {
        final String single = text.replace('\n', ' ').trim();
        return single.length() <= 20 ? single : single.substring(0, 20) + "…";
    }

    private void persist() {
        QuickButtons.save(dialogId, buttons);
        rebuild();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private EditTextBoldCursor field(Context context, String hint, boolean multiline) {
        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHintText(hint);
        editText.setBackgroundDrawable(null);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multiline) {
            editText.setMaxLines(5);
        } else {
            editText.setSingleLine(true);
        }
        return editText;
    }
}
