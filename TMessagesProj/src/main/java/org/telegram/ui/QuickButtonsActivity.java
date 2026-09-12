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
import android.widget.FrameLayout;
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
        // Для КОЖНОГО ЧАТУ окремо: з робочим чатом і з близькими потрібні
        // різні кнопки. Спільним лишилося тільки положення.
        final org.telegram.ui.Cells.HeaderCell appearance =
                new org.telegram.ui.Cells.HeaderCell(context);
        appearance.setText(getString(R.string.QuickButtonsAppearance));
        appearance.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        root.addView(appearance);

        // Живий перегляд: без нього вибір кольору й розміру — гра наосліп.
        previewHolder = new FrameLayout(context);
        previewHolder.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        previewHolder.setPadding(0, dp(14), 0, dp(14));
        preview = new org.telegram.ui.Components.QuickButtonsFab(context);
        preview.bind(dialogId, null);
        previewHolder.addView(preview, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        root.addView(previewHolder, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Палітра кружечками, а не шістнадцятковими кодами: за «#4E8FE0»
        // неможливо зрозуміти, який це колір, доки не побачиш.
        swatches = new LinearLayout(context);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER);
        swatches.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        swatches.setPadding(0, dp(4), 0, dp(14));
        root.addView(swatches, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        buildSwatches(context);

        modeCell = new TextSettingsCell(context);
        modeCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        modeCell.setOnClickListener(v -> pickMode());
        root.addView(modeCell);

        styleCell = new TextSettingsCell(context);
        styleCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        styleCell.setOnClickListener(v -> pickStyle());
        root.addView(styleCell);

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
        // Без від'ємних відступів: у комірки є власні поля, і спроба
        // компенсувати ними поля діалогу зрізала початок напису.
        sendCell.setPadding(0, 0, 0, 0);
        container.addView(sendCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

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

    private TextSettingsCell emojiCell, sizeCell, alphaCell, modeCell, styleCell;

    /** Назви готових значків у тому ж порядку, що {@link QuickButtons#ICONS}. */
    private int[] iconTitles() {
        return new int[]{
                R.string.QuickButtonsEmojiNone, R.string.QuickButtonsIconFlash,
                R.string.QuickButtonsIconChat, R.string.QuickButtonsIconStar,
                R.string.QuickButtonsIconHeart, R.string.QuickButtonsIconWork,
                R.string.QuickButtonsIconClock, R.string.QuickButtonsIconCheck,
                R.string.QuickButtonsIconQuestion,
        };
    }

    private void pickMode() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final CharSequence[] names = {
                getString(R.string.QuickButtonsModeSingle),
                getString(R.string.QuickButtonsModeSeparate),
        };
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsMode))
                .setItems(names, (dialog, which) -> {
                    QuickButtons.setMode(dialogId, which);
                    appearanceChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void pickStyle() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final CharSequence[] names = {
                getString(R.string.QuickButtonsStyleList),
                getString(R.string.QuickButtonsStyleGrid),
                getString(R.string.QuickButtonsStyleRow),
        };
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsStyle))
                .setItems(names, (dialog, which) -> {
                    QuickButtons.setStyle(dialogId, which);
                    appearanceChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void pickReadyIcon() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final int[] titles = iconTitles();
        final CharSequence[] names = new CharSequence[titles.length];
        for (int i = 0; i < titles.length; i++) {
            names[i] = getString(titles[i]);
        }
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsIconReady))
                .setItems(names, (dialog, which) -> {
                    // Готовий значок виключає емодзі й картинку — інакше
                    // довелося б мовчки вирішувати, що з них головніше.
                    QuickButtons.setEmoji(dialogId, "");
                    QuickButtons.clearImage(dialogId);
                    QuickButtons.setIcon(dialogId, QuickButtons.ICONS[which]);
                    appearanceChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }
    private FrameLayout previewHolder;
    private org.telegram.ui.Components.QuickButtonsFab preview;
    private LinearLayout swatches;

    /** Кружечки палітри. Обраний позначаємо обідком. */
    private void buildSwatches(Context context) {
        swatches.removeAllViews();
        final int active = QuickButtons.getColor(dialogId);
        for (int color : QuickButtons.COLORS) {
            final View dot = new View(context);
            final int shown = color == QuickButtons.COLOR_THEME
                    ? Theme.getColor(Theme.key_chats_actionBackground) : color;
            dot.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(30), shown, shown));
            if (color == active) {
                dot.setScaleX(1.25f);
                dot.setScaleY(1.25f);
            }
            dot.setOnClickListener(v -> {
                QuickButtons.setColor(dialogId, color);
                buildSwatches(context);
                appearanceChanged();
            });
            swatches.addView(dot, LayoutHelper.createLinear(30, 30, 6, 6, 6, 6));
        }
    }

    private void updateAppearanceRows() {
        if (emojiCell == null) {
            return;
        }
        if (preview != null) {
            preview.refresh();
            preview.setVisibility(View.VISIBLE);
        }
        final String emoji = QuickButtons.getEmoji(dialogId);
        final String iconName = QuickButtons.getIcon(dialogId);
        final CharSequence iconValue;
        if (QuickButtons.getImage(dialogId) != null) {
            iconValue = getString(R.string.QuickButtonsImageOwn);
        } else if (!TextUtils.isEmpty(emoji)) {
            iconValue = emoji;
        } else {
            int index = 0;
            for (int i = 0; i < QuickButtons.ICONS.length; i++) {
                if (QuickButtons.ICONS[i].equals(iconName)) {
                    index = i;
                    break;
                }
            }
            iconValue = getString(iconTitles()[index]);
        }
        emojiCell.setTextAndValue(getString(R.string.QuickButtonsEmoji), iconValue, true);

        modeCell.setTextAndValue(getString(R.string.QuickButtonsMode),
                getString(QuickButtons.getMode(dialogId) == QuickButtons.MODE_SEPARATE
                        ? R.string.QuickButtonsModeSeparate : R.string.QuickButtonsModeSingle), true);
        final int style = QuickButtons.getStyle(dialogId);
        styleCell.setTextAndValue(getString(R.string.QuickButtonsStyle),
                getString(style == QuickButtons.STYLE_GRID ? R.string.QuickButtonsStyleGrid
                        : style == QuickButtons.STYLE_ROW ? R.string.QuickButtonsStyleRow
                        : R.string.QuickButtonsStyleList), true);
        // У режимі «кнопки поруч» списку немає, тож і вигляд його ні до чого.
        styleCell.setEnabled(QuickButtons.getMode(dialogId) == QuickButtons.MODE_SINGLE);
        sizeCell.setTextAndValue(getString(R.string.QuickButtonsSize),
                QuickButtons.getSize(dialogId) + " dp", true);
        alphaCell.setTextAndValue(getString(R.string.QuickButtonsAlpha),
                QuickButtons.getAlphaPercent(dialogId) + "%", false);
    }

    /** Вибір значка: типовий, емодзі або власна картинка. */
    private void pickEmoji() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final CharSequence[] options = {
                getString(R.string.QuickButtonsIconReady),
                getString(R.string.QuickButtonsEmoji),
                getString(R.string.QuickButtonsImageOwn),
        };
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsEmoji))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickReadyIcon();
                    } else if (which == 1) {
                        askEmoji();
                    } else {
                        pickImage();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void askEmoji() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor editText = field(context,
                getString(R.string.QuickButtonsEmojiHint), false);
        editText.setText(QuickButtons.getEmoji(dialogId));
        final LinearLayout container = new LinearLayout(context);
        container.setPadding(dp(24), dp(6), dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsEmoji))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    // Емодзі й картинка виключають одне одного: лишити обидва
                    // означало б мовчазне правило «картинка головніша».
                    QuickButtons.clearImage(dialogId);
                    QuickButtons.setEmoji(dialogId, editText.getText().toString());
                    appearanceChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private static final int REQUEST_IMAGE = 4821;

    private void pickImage() {
        try {
            final android.content.Intent intent =
                    new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_IMAGE);
        } catch (Throwable e) {
            BulletinFactory.of(this)
                    .createErrorBulletin(getString(R.string.QuickButtonsImageFailed)).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode != REQUEST_IMAGE) {
            return;
        }
        if (resultCode != android.app.Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (QuickButtons.setImage(dialogId, data.getData())) {
            QuickButtons.setEmoji(dialogId, "");
            appearanceChanged();
        } else {
            BulletinFactory.of(this)
                    .createErrorBulletin(getString(R.string.QuickButtonsImageFailed)).show();
        }
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
                        QuickButtons.setSize(dialogId, values[which]);
                    } else {
                        QuickButtons.setAlphaPercent(dialogId, values[which]);
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
        // Саме setHintColor, а не setHintTextColor: EditTextBoldCursor малює
        // власний підпис, і стандартний метод його не фарбує — через це поля
        // виглядали як порожнє місце, без жодної підказки.
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHintText(hint);
        // Лінія під полем: без неї не видно навіть того, що тут можна писати.
        editText.setLineColors(
                Theme.getColor(Theme.key_dialogInputField),
                Theme.getColor(Theme.key_dialogInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        editText.setPadding(0, dp(4), 0, dp(4));
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
