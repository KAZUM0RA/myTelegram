/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
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
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.QuickButtonsFab;

import java.util.ArrayList;

/**
 * Редактор однієї групи швидких кнопок: її фрази й вигляд.
 *
 * <p>Вигляд тут-таки, а не окремим екраном: колір і значок потрібні саме
 * для того, щоб відрізняти групи між собою, тож вибирати їх зручно поруч
 * зі списком фраз, а не деінде.
 */
public class QuickButtonGroupActivity extends BaseFragment {

    private static final int REQUEST_IMAGE = 4821;

    private final long dialogId;
    private final int index;
    private final Runnable onChanged;

    private ArrayList<QuickButtons.Group> groups;
    private QuickButtons.Group group;

    private LinearLayout listLayout, swatches;
    private FrameLayout previewHolder;
    private QuickButtonsFab preview;
    private TextSettingsCell nameCell, modeCell, styleCell, iconCell, sizeCell, alphaCell, placeCell;

    public QuickButtonGroupActivity(long dialogId, int index, Runnable onChanged) {
        this.dialogId = dialogId;
        this.index = index;
        this.onChanged = onChanged;
    }

    @Override
    public boolean onFragmentCreate() {
        groups = QuickButtons.get(dialogId);
        if (index < 0 || index >= groups.size()) {
            return false;
        }
        group = groups.get(index);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(group.name);
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

        root.addView(header(context, getString(R.string.QuickButtonsPhrasesHeader)));

        listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(listLayout);

        final TextSettingsCell add = new TextSettingsCell(context);
        add.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        add.setText(getString(R.string.QuickButtonsAdd), false);
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        add.setOnClickListener(v -> {
            if (group.buttons.size() >= QuickButtons.MAX_BUTTONS) {
                BulletinFactory.of(this)
                        .createErrorBulletin(getString(R.string.QuickButtonsTooMany)).show();
                return;
            }
            editPhrase(null);
        });
        root.addView(add);

        root.addView(info(context, getString(R.string.QuickButtonsInfo)));

        // ── Вигляд ───────────────────────────────────────────────────────
        root.addView(header(context, getString(R.string.QuickButtonsAppearance)));

        // Живий перегляд: без нього вибір кольору й режиму — гра наосліп,
        // бо наслідок видно лише вийшовши в чат.
        previewHolder = new FrameLayout(context);
        previewHolder.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        previewHolder.setPadding(0, dp(14), 0, dp(14));
        preview = new QuickButtonsFab(context);
        preview.setPreviewMode(true);
        previewHolder.addView(preview, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        root.addView(previewHolder);

        // Палітра кружечками, а не кодами: за «#4E8FE0» неможливо зрозуміти,
        // який це колір, доки не побачиш.
        swatches = new LinearLayout(context);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        swatches.setGravity(Gravity.CENTER);
        swatches.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        swatches.setPadding(0, 0, 0, dp(14));
        root.addView(swatches);

        nameCell = row(context, v -> renameGroup());
        root.addView(nameCell);
        placeCell = row(context, v -> pickPlace());
        root.addView(placeCell);
        modeCell = row(context, v -> pickMode());
        root.addView(modeCell);
        styleCell = row(context, v -> pickStyle());
        root.addView(styleCell);
        iconCell = row(context, v -> pickIconKind());
        root.addView(iconCell);
        sizeCell = row(context, v -> pickNumber(true));
        root.addView(sizeCell);
        alphaCell = row(context, v -> pickNumber(false));
        root.addView(alphaCell);

        root.addView(info(context, getString(R.string.QuickButtonsAppearanceInfo)));

        final TextSettingsCell delete = new TextSettingsCell(context);
        delete.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        delete.setText(getString(R.string.QuickButtonsDeleteGroup), false);
        delete.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        delete.setOnClickListener(v -> confirmDelete());
        root.addView(delete);

        refreshAll(context);

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        scrollView.addView(root, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        fragmentView = scrollView;
        return fragmentView;
    }

    // ── Оновлення екрана ─────────────────────────────────────────────────

    private void refreshAll(Context context) {
        buildSwatches(context);
        rebuildPhrases(context);
        updateRows();
        if (preview != null) {
            // Показуємо саме цю групу, а не всі кнопки чату: редагуємо одну,
            // і бачити поруч чужі було б плутаниною.
            preview.showPreviewOf(group);
        }
    }

    private void buildSwatches(Context context) {
        swatches.removeAllViews();
        for (final int color : QuickButtons.COLORS) {
            final View dot = new View(context);
            final int shown = color == QuickButtons.COLOR_THEME
                    ? Theme.getColor(Theme.key_chats_actionBackground) : color;
            dot.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(30), shown, shown));
            if (color == group.color) {
                dot.setScaleX(1.25f);
                dot.setScaleY(1.25f);
            }
            dot.setOnClickListener(v -> {
                group.color = color;
                persist();
            });
            swatches.addView(dot, LayoutHelper.createLinear(30, 30, 6, 6, 6, 6));
        }
    }

    private void rebuildPhrases(Context context) {
        listLayout.removeAllViews();
        for (final QuickButtons.Button button : group.buttons) {
            final TextSettingsCell cell = new TextSettingsCell(context);
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            // У значенні — сам текст: назва може бути короткою, і без цього
            // легко забути, що за нею стоїть.
            cell.setTextAndValue(button.label, button.text, true);
            cell.setOnClickListener(v -> editPhrase(button));
            listLayout.addView(cell);
        }
    }

    private void updateRows() {
        nameCell.setTextAndValue(getString(R.string.QuickButtonsGroupName), group.name, true);
        placeCell.setTextAndValue(getString(R.string.QuickButtonsPlace), placeName(), true);
        modeCell.setTextAndValue(getString(R.string.QuickButtonsMode), modeName(), true);
        styleCell.setTextAndValue(getString(R.string.QuickButtonsStyle), styleName(), true);
        // Список є лише в режимі «одна кнопка зі списком».
        styleCell.setEnabled(group.mode == QuickButtons.MODE_SINGLE);
        iconCell.setTextAndValue(getString(R.string.QuickButtonsEmoji), iconName(), true);
        sizeCell.setTextAndValue(getString(R.string.QuickButtonsSize), group.size + " dp", true);
        alphaCell.setTextAndValue(getString(R.string.QuickButtonsAlpha), group.alpha + "%", false);
    }

    private String actionName(int action) {
        switch (action) {
            case QuickButtons.ACTION_INSERT:
                return getString(R.string.QuickButtonsActionInsert);
            case QuickButtons.ACTION_LINK:
                return getString(R.string.QuickButtonsActionLink);
            default:
                return getString(R.string.QuickButtonsActionSend);
        }
    }

    private String placeName() {
        switch (group.place) {
            case QuickButtons.PLACE_HEADER:
                return getString(R.string.QuickButtonsPlaceHeader);
            case QuickButtons.PLACE_INPUT:
                return getString(R.string.QuickButtonsPlaceInput);
            default:
                return getString(R.string.QuickButtonsPlaceFloating);
        }
    }

    private void pickPlace() {
        final CharSequence[] names = {
                getString(R.string.QuickButtonsPlaceFloating),
                getString(R.string.QuickButtonsPlaceHeader),
                getString(R.string.QuickButtonsPlaceInput),
        };
        chooser(getString(R.string.QuickButtonsPlace), names, which -> {
            group.place = which;
            persist();
            if (which == QuickButtons.PLACE_HEADER) {
                // Шапка будується при відкритті чату, тож значок з'явиться
                // лише наступного разу. Краще сказати, ніж лишити гадати.
                BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.chats_infotip,
                                getString(R.string.QuickButtonsPlaceHeaderHint)).show();
            }
        });
    }

    private String modeName() {
        switch (group.mode) {
            case QuickButtons.MODE_SEPARATE:
                return getString(R.string.QuickButtonsModeSeparate);
            case QuickButtons.MODE_DIRECT:
                return getString(R.string.QuickButtonsModeDirect);
            default:
                return getString(R.string.QuickButtonsModeSingle);
        }
    }

    private String styleName() {
        switch (group.style) {
            case QuickButtons.STYLE_GRID:
                return getString(R.string.QuickButtonsStyleGrid);
            case QuickButtons.STYLE_ROW:
                return getString(R.string.QuickButtonsStyleRow);
            default:
                return getString(R.string.QuickButtonsStyleList);
        }
    }

    private int[] iconTitles() {
        return new int[]{
                R.string.QuickButtonsEmojiNone, R.string.QuickButtonsIconFlash,
                R.string.QuickButtonsIconChat, R.string.QuickButtonsIconStar,
                R.string.QuickButtonsIconHeart, R.string.QuickButtonsIconWork,
                R.string.QuickButtonsIconClock, R.string.QuickButtonsIconCheck,
                R.string.QuickButtonsIconQuestion,
        };
    }

    private String iconName() {
        if (!TextUtils.isEmpty(group.image)) {
            return getString(R.string.QuickButtonsImageOwn);
        }
        if (!TextUtils.isEmpty(group.emoji)) {
            return group.emoji;
        }
        for (int i = 0; i < QuickButtons.ICONS.length; i++) {
            if (QuickButtons.ICONS[i].equals(group.icon)) {
                return getString(iconTitles()[i]);
            }
        }
        return getString(R.string.QuickButtonsEmojiNone);
    }

    // ── Фрази ────────────────────────────────────────────────────────────

    private void editPhrase(QuickButtons.Button existing) {
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

        // Три дії замість колишнього перемикача: посилання не вкладалося в
        // «надіслати чи вставити» — воно взагалі не про текст повідомлення.
        final int[] action = { existing == null ? QuickButtons.ACTION_SEND : existing.action };
        final TextSettingsCell actionCell = new TextSettingsCell(context);
        actionCell.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        actionCell.setPadding(0, 0, 0, 0);
        actionCell.setTextAndValue(getString(R.string.QuickButtonsAction),
                actionName(action[0]), false);
        actionCell.setOnClickListener(v -> {
            final CharSequence[] names = {
                    getString(R.string.QuickButtonsActionSend),
                    getString(R.string.QuickButtonsActionInsert),
                    getString(R.string.QuickButtonsActionLink),
            };
            chooser(getString(R.string.QuickButtonsAction), names, which -> {
                action[0] = which;
                actionCell.setTextAndValue(getString(R.string.QuickButtonsAction),
                        actionName(which), false);
                // Підказка поля змінюється разом із дією: для посилання
                // «текст, який надсилати» збивало б з пантелику.
                textField.setHintText(getString(which == QuickButtons.ACTION_LINK
                        ? R.string.QuickButtonsLinkHint : R.string.QuickButtonsTextHint));
            });
        });
        container.addView(actionCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        if (existing != null && existing.action == QuickButtons.ACTION_LINK) {
            textField.setHintText(getString(R.string.QuickButtonsLinkHint));
        }

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
            final String finalLabel = TextUtils.isEmpty(label) ? shorten(text) : label;
            if (existing == null) {
                group.buttons.add(new QuickButtons.Button(finalLabel, text, action[0]));
            } else {
                existing.label = finalLabel;
                existing.text = text;
                existing.action = action[0];
            }
            persist();
        });
        if (existing != null) {
            builder.setNeutralButton(getString(R.string.Delete), (dialog, which) -> {
                group.buttons.remove(existing);
                persist();
            });
        }
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private String shorten(String text) {
        final String single = text.replace('\n', ' ').trim();
        return single.length() <= 20 ? single : single.substring(0, 20) + "…";
    }

    // ── Вигляд ───────────────────────────────────────────────────────────

    private void renameGroup() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor editText = field(context,
                getString(R.string.QuickButtonsGroupName), false);
        editText.setText(group.name);
        final LinearLayout container = new LinearLayout(context);
        container.setPadding(dp(24), dp(6), dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsGroupName))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    final String name = editText.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        group.name = name;
                        actionBar.setTitle(name);
                        persist();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void pickMode() {
        final CharSequence[] names = {
                getString(R.string.QuickButtonsModeSingle),
                getString(R.string.QuickButtonsModeSeparate),
                getString(R.string.QuickButtonsModeDirect),
        };
        chooser(getString(R.string.QuickButtonsMode), names, which -> {
            group.mode = which;
            persist();
        });
    }

    private void pickStyle() {
        final CharSequence[] names = {
                getString(R.string.QuickButtonsStyleList),
                getString(R.string.QuickButtonsStyleGrid),
                getString(R.string.QuickButtonsStyleRow),
        };
        chooser(getString(R.string.QuickButtonsStyle), names, which -> {
            group.style = which;
            persist();
        });
    }

    private void pickIconKind() {
        final CharSequence[] names = {
                getString(R.string.QuickButtonsIconReady),
                getString(R.string.QuickButtonsEmoji),
                getString(R.string.QuickButtonsImageOwn),
        };
        chooser(getString(R.string.QuickButtonsEmoji), names, which -> {
            if (which == 0) {
                pickReadyIcon();
            } else if (which == 1) {
                askEmoji();
            } else {
                pickImage();
            }
        });
    }

    private void pickReadyIcon() {
        final int[] titles = iconTitles();
        final CharSequence[] names = new CharSequence[titles.length];
        for (int i = 0; i < titles.length; i++) {
            names[i] = getString(titles[i]);
        }
        chooser(getString(R.string.QuickButtonsIconReady), names, which -> {
            // Значок, емодзі й картинка виключають одне одного — інакше
            // довелося б мовчки вирішувати, що з них головніше.
            clearIcon();
            group.icon = QuickButtons.ICONS[which];
            persist();
        });
    }

    private void askEmoji() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditTextBoldCursor editText = field(context,
                getString(R.string.QuickButtonsEmojiHint), false);
        editText.setText(group.emoji);
        final LinearLayout container = new LinearLayout(context);
        container.setPadding(dp(24), dp(6), dp(24), 0);
        container.addView(editText, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsEmoji))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    clearIcon();
                    group.emoji = editText.getText().toString().trim();
                    persist();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void clearIcon() {
        QuickButtons.deleteImage(group.image);
        group.image = "";
        group.emoji = "";
        group.icon = "";
    }

    private void pickImage() {
        try {
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_IMAGE);
        } catch (Throwable e) {
            BulletinFactory.of(this)
                    .createErrorBulletin(getString(R.string.QuickButtonsImageFailed)).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_IMAGE || resultCode != android.app.Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        final String name = QuickButtons.saveImage(data.getData());
        if (TextUtils.isEmpty(name)) {
            BulletinFactory.of(this)
                    .createErrorBulletin(getString(R.string.QuickButtonsImageFailed)).show();
            return;
        }
        clearIcon();
        group.image = name;
        persist();
    }

    /** @param size {@code true} — розмір, {@code false} — прозорість. */
    private void pickNumber(boolean size) {
        final int[] values = size
                ? new int[]{36, 44, 52, 60, 72}
                : new int[]{40, 60, 80, 100};
        final CharSequence[] names = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = size ? values[i] + " dp" : values[i] + "%";
        }
        chooser(getString(size ? R.string.QuickButtonsSize : R.string.QuickButtonsAlpha),
                names, which -> {
                    if (size) {
                        group.size = values[which];
                    } else {
                        group.alpha = values[which];
                    }
                    persist();
                });
    }

    private void confirmDelete() {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.QuickButtonsDeleteGroup))
                .setMessage(getString(R.string.QuickButtonsDeleteGroupText))
                .setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                    QuickButtons.deleteImage(group.image);
                    groups.remove(index);
                    QuickButtons.save(dialogId, groups);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                    finishFragment();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    // ── Дрібні помічники ─────────────────────────────────────────────────

    private interface Choice {
        void run(int which);
    }

    private void chooser(String title, CharSequence[] names, Choice choice) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(names, (dialog, which) -> choice.run(which))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void persist() {
        QuickButtons.save(dialogId, groups);
        if (getParentActivity() != null) {
            refreshAll(getParentActivity());
        }
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private TextSettingsCell row(Context context, View.OnClickListener onClick) {
        final TextSettingsCell cell = new TextSettingsCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setOnClickListener(onClick);
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

    private EditTextBoldCursor field(Context context, String hint, boolean multiline) {
        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        // Саме setHintColor: EditTextBoldCursor малює власний підпис, і
        // стандартний setHintTextColor його не фарбує — поле виглядало б
        // порожнім місцем без жодної підказки.
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHintText(hint);
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
