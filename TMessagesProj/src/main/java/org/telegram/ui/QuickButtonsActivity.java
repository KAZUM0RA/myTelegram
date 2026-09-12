/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.quickbuttons.QuickButtons;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Список груп швидких кнопок для одного чату.
 *
 * <p>Кожна група — окрема плаваюча кнопка зі своїм набором фраз. Тут лише
 * перелік і створення; усе про конкретну групу редагується всередині неї.
 */
public class QuickButtonsActivity extends BaseFragment {

    private final long dialogId;
    private final Runnable onChanged;
    private ArrayList<QuickButtons.Group> groups;
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

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(listLayout);

        final TextSettingsCell add = new TextSettingsCell(context);
        add.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        add.setText(getString(R.string.QuickButtonsAddGroup), false);
        add.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        add.setOnClickListener(v -> {
            if (groups.size() >= QuickButtons.MAX_GROUPS) {
                BulletinFactory.of(this)
                        .createErrorBulletin(getString(R.string.QuickButtonsTooManyGroups)).show();
                return;
            }
            final QuickButtons.Group group = new QuickButtons.Group();
            group.name = getString(R.string.QuickButtonsNewGroup);
            groups.add(group);
            persist();
            openGroup(group);
        });
        root.addView(add);

        final TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setText(getString(R.string.QuickButtonsGroupsInfo));
        root.addView(info);

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        scrollView.addView(root, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        fragmentView = scrollView;
        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        groups = QuickButtons.get(dialogId);
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Перечитуємо після повернення з редактора групи: там могли змінити
        // назву, режим чи набір фраз.
        groups = QuickButtons.get(dialogId);
        rebuild();
    }

    private void rebuild() {
        if (listLayout == null || getParentActivity() == null) {
            return;
        }
        listLayout.removeAllViews();
        final Context context = getParentActivity();
        for (final QuickButtons.Group group : groups) {
            final TextSettingsCell cell = new TextSettingsCell(context);
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            // У значенні — скільки фраз усередині: інакше групи з однаковими
            // назвами не відрізнити, а порожню не помітити.
            cell.setTextAndValue(group.name,
                    LocaleController.formatPluralString("QuickButtonsPhrases",
                            group.buttons.size()), true);
            cell.setOnClickListener(v -> openGroup(group));
            listLayout.addView(cell);
        }
    }

    private void openGroup(QuickButtons.Group group) {
        presentFragment(new QuickButtonGroupActivity(dialogId, groups.indexOf(group), () -> {
            if (onChanged != null) {
                onChanged.run();
            }
        }));
    }

    private void persist() {
        QuickButtons.save(dialogId, groups);
        rebuild();
        if (onChanged != null) {
            onChanged.run();
        }
    }
}
