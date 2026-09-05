/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

/**
 * Екран «Іконка застосунку»: вибір кольорового варіанта.
 *
 * <p>Показуємо лише свої варіанти, а не весь перелік
 * {@link LauncherIconController.LauncherIcon}: решта — іконки Telegram,
 * і форку вони ні до чого. Але перемикаємо через їхній контролер, бо
 * {@code tryFixLauncherIconIfNeeded()} звіряється саме з тим переліком і
 * скинув би наш вибір, якби ми обходили його стороною.
 */
public class AppIconActivity extends BaseFragment {

    /** Порядок такий, як у налаштуваннях: спершу типова. */
    private static final LauncherIconController.LauncherIcon[] OURS = {
            LauncherIconController.LauncherIcon.DEFAULT,
            LauncherIconController.LauncherIcon.FORK_AMETHYST,
            LauncherIconController.LauncherIcon.FORK_EMERALD,
            LauncherIconController.LauncherIcon.FORK_AMBER,
    };

    private final ArrayList<RadioCell> cells = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AppIconTitle));
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

        for (int i = 0; i < OURS.length; i++) {
            final LauncherIconController.LauncherIcon icon = OURS[i];

            final LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            final ImageView preview = new ImageView(context);
            preview.setImageDrawable(previewOf(context, icon));
            row.addView(preview, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL,
                    20, 8, 0, 8));

            final RadioCell cell = new RadioCell(context);
            cell.setText(getString(icon.title), false, i < OURS.length - 1);
            row.addView(cell, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            row.setOnClickListener(v -> select(icon));
            root.addView(row, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            cells.add(cell);
        }

        final TextInfoPrivacyCell info = new TextInfoPrivacyCell(context);
        info.setText(getString(R.string.AppIconInfo));
        root.addView(info);

        updateChecks();

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        scrollView.addView(root, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        fragmentView = scrollView;
        return fragmentView;
    }

    /** Той самий поділ на шари, що й в адаптивній іконці на робочому столі. */
    private Drawable previewOf(Context context, LauncherIconController.LauncherIcon icon) {
        final Drawable back = ContextCompat.getDrawable(context, icon.background);
        final Drawable front = ContextCompat.getDrawable(context, icon.foreground);
        return new LayerDrawable(new Drawable[]{back, front});
    }

    private void select(LauncherIconController.LauncherIcon icon) {
        if (LauncherIconController.isEnabled(icon)) {
            return;
        }
        LauncherIconController.setIcon(icon);
        updateChecks();
        AndroidUtilities.runOnUIThread(this::updateChecks, 300);
    }

    private void updateChecks() {
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).setChecked(LauncherIconController.isEnabled(OURS[i]), true);
        }
    }
}
