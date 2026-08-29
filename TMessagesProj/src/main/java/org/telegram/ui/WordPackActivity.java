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
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.wordpack.WordPack;

import java.util.ArrayList;

/**
 * Екран «Мова застосунку»: вибір словника, яким підписані кнопки й розділи.
 *
 * <p>Список будується з файлів у {@code assets/wordpacks/}, тож новий словник
 * не потребує змін у цьому екрані.
 */
public class WordPackActivity extends BaseFragment {

    private final ArrayList<RadioCell> cells = new ArrayList<>();
    private final ArrayList<String> ids = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.WordPackTitle));
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

        final ArrayList<String[]> packs = WordPack.available();

        add(context, root, WordPack.STANDARD, getString(R.string.WordPackStandard),
                !packs.isEmpty());
        for (int i = 0; i < packs.size(); i++) {
            add(context, root, packs.get(i)[0], packs.get(i)[1], i < packs.size() - 1);
        }

        final TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setText(getString(R.string.WordPackInfo));
        root.addView(info);

        updateChecks();

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        scrollView.addView(root, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        fragmentView = scrollView;
        return fragmentView;
    }

    private void add(Context context, LinearLayout root, String id, String title, boolean divider) {
        final RadioCell cell = new RadioCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setText(title, false, divider);
        cell.setOnClickListener(v -> select(id));
        root.addView(cell);
        cells.add(cell);
        ids.add(id);
    }

    private void select(String id) {
        if (id.equals(WordPack.getActiveId())) {
            return;
        }
        WordPack.setActive(id);
        updateChecks();
        // Написи вже побудованих екранів не оновлюються самі: Telegram кешує
        // їх у створених вʼю. Той самий сигнал шле зміна мови інтерфейсу —
        // ним і користуємось, щоб не вигадувати власного.
        NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.reloadInterface);
    }

    private void updateChecks() {
        final String active = WordPack.getActiveId();
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).setChecked(ids.get(i).equals(active), true);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        LocaleController.getInstance();
        return super.onFragmentCreate();
    }
}
