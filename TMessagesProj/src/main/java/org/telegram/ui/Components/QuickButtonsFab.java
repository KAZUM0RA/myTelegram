/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.quickbuttons.QuickButtons;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/**
 * Шар швидких кнопок поверх чату.
 *
 * <p>Тримає всі групи чату одночасно: кожна — окрема плаваюча панель зі
 * своїм місцем, значком і набором фраз. Саме шар, а не одна кнопка, бо
 * груп може бути кілька, і кожна пересувається незалежно.
 *
 * <p>Режими для кожної групи свої:
 * {@code MODE_SINGLE} — кружечок відкриває список;
 * {@code MODE_SEPARATE} — фрази розкладені поруч;
 * {@code MODE_DIRECT} — кружечок одразу надсилає єдину фразу.
 */
public class QuickButtonsFab extends FrameLayout {

    private final int touchSlop;
    private long dialogId;
    private Utilities.Callback<QuickButtons.Button> onPick;
    private PopupWindow popup;

    public QuickButtonsFab(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        // Сам шар подій не ловить — інакше він перехопив би дотики по чату
        // на всю свою площу. Ловлять лише самі панелі.
        setClickable(false);
        setFocusable(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    public void bind(long dialogId, Utilities.Callback<QuickButtons.Button> onPick) {
        this.dialogId = dialogId;
        this.onPick = onPick;
        refresh();
    }

    /** Перебудовує шар після зміни груп або їхнього вигляду. */
    public void refresh() {
        removeAllViews();
        final ArrayList<QuickButtons.Group> groups = QuickButtons.get(dialogId);
        boolean anything = false;
        for (QuickButtons.Group group : groups) {
            if (group.buttons.isEmpty()) {
                continue;
            }
            anything = true;
            addView(buildPanel(group), LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.LEFT | Gravity.TOP));
        }
        setVisibility(anything ? VISIBLE : GONE);
    }

    // ── Панель однієї групи ──────────────────────────────────────────────

    private View buildPanel(QuickButtons.Group group) {
        final Context context = getContext();
        final LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.END);
        panel.setAlpha(group.alpha / 100f);

        if (group.mode == QuickButtons.MODE_SEPARATE) {
            for (QuickButtons.Button button : group.buttons) {
                panel.addView(makePill(group, button, panel),
                        LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                                LayoutHelper.WRAP_CONTENT, Gravity.END, 0, 0, 0, 6));
            }
        } else {
            final View circle = makeCircle(group);
            // MODE_DIRECT надсилає першу фразу, MODE_SINGLE відкриває список.
            final QuickButtons.Button direct = group.mode == QuickButtons.MODE_DIRECT
                    ? group.buttons.get(0) : null;
            circle.setOnTouchListener((v, e) -> handleTouch(panel, group, e, direct, true));
            panel.addView(circle, LayoutHelper.createLinear(group.size, group.size));
        }

        panel.post(() -> placePanel(panel, group));
        return panel;
    }

    private void placePanel(View panel, QuickButtons.Group group) {
        final View parent = (View) panel.getParent();
        if (parent == null) {
            return;
        }
        if (group.x < 0 || group.y < 0) {
            // Ще не пересували: ставимо праворуч над полем вводу — там
            // найменше шансів перекрити текст повідомлень. Кожну наступну
            // групу трохи вище, щоб вони не лягли одна на одну.
            final int index = indexOfChild(panel);
            panel.setTranslationX(parent.getWidth() - panel.getWidth() - dp(12));
            panel.setTranslationY(parent.getHeight() - panel.getHeight()
                    - dp(120) - dp(56) * index);
        } else {
            panel.setTranslationX(clamp(group.x, parent.getWidth() - panel.getWidth()));
            panel.setTranslationY(clamp(group.y, parent.getHeight() - panel.getHeight()));
        }
    }

    /** Кругла кнопка групи з обраним значком. */
    private View makeCircle(QuickButtons.Group group) {
        final Context context = getContext();
        final FrameLayout circle = new FrameLayout(context);

        final int background = group.color == QuickButtons.COLOR_THEME
                ? Theme.getColor(Theme.key_chats_actionBackground) : group.color;
        circle.setBackground(Theme.createSimpleSelectorCircleDrawable(
                dp(group.size), background, darken(background)));
        circle.setElevation(dp(4));

        // Порядок навмисний: власна картинка перекриває емодзі, емодзі —
        // готовий значок, той — типову стрілку. Один зрозумілий вибір
        // замість кількох прапорців, які могли б суперечити.
        final java.io.File image = QuickButtons.imageFile(group.image);
        if (image != null) {
            final ImageView view = new ImageView(context);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(BitmapFactory.decodeFile(image.getAbsolutePath()));
            view.setClipToOutline(true);
            view.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    outline.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            circle.addView(view, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else if (!TextUtils.isEmpty(group.emoji)) {
            final TextView view = new TextView(context);
            view.setGravity(Gravity.CENTER);
            view.setText(group.emoji);
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, group.size * 0.45f);
            circle.addView(view, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            final ImageView view = new ImageView(context);
            view.setScaleType(ImageView.ScaleType.CENTER);
            view.setImageResource(iconResource(group.icon));
            view.setColorFilter(Theme.getColor(Theme.key_chats_actionIcon));
            circle.addView(view, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        return circle;
    }

    private TextView makePill(QuickButtons.Group group, QuickButtons.Button button, View panel) {
        final int background = group.color == QuickButtons.COLOR_THEME
                ? Theme.getColor(Theme.key_chats_actionBackground) : group.color;
        final TextView pill = new TextView(getContext());
        pill.setText(button.label);
        pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        pill.setTextColor(Theme.getColor(Theme.key_chats_actionIcon));
        pill.setGravity(Gravity.CENTER);
        pill.setMaxLines(1);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        pill.setPadding(dp(14), dp(8), dp(14), dp(8));
        pill.setBackground(Theme.createRoundRectDrawable(dp(18), background));
        pill.setElevation(dp(3));
        // Кожна фраза і надсилає, і пересуває панель: окремої «ручки» для
        // перетягування немає, бо вона з'їдала б місце.
        pill.setOnTouchListener((v, e) -> handleTouch(panel, group, e, button, false));
        return pill;
    }

    private int iconResource(String name) {
        if (TextUtils.isEmpty(name)) {
            return R.drawable.msg_send;
        }
        final int id = getResources().getIdentifier(
                name, "drawable", getContext().getPackageName());
        return id != 0 ? id : R.drawable.msg_send;
    }

    // ── Перетягування й дотики ───────────────────────────────────────────

    private float downX, downY, startX, startY;
    private boolean dragging;

    /**
     * @param direct     фраза, яку надіслати при дотику; {@code null} — відкрити список
     * @param allowsList чи може ця панель показувати список
     */
    private boolean handleTouch(View panel, QuickButtons.Group group,
                                MotionEvent event, QuickButtons.Button direct,
                                boolean allowsList) {
        final View parent = (View) panel.getParent();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                startX = panel.getTranslationX();
                startY = panel.getTranslationY();
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                final float dx = event.getRawX() - downX;
                final float dy = event.getRawY() - downY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                }
                if (dragging && parent != null) {
                    panel.setTranslationX(clamp(startX + dx, parent.getWidth() - panel.getWidth()));
                    panel.setTranslationY(clamp(startY + dy, parent.getHeight() - panel.getHeight()));
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    savePosition(group, panel.getTranslationX(), panel.getTranslationY());
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (direct != null) {
                        if (onPick != null) {
                            onPick.run(direct);
                        }
                    } else if (allowsList) {
                        showList(panel, group);
                    }
                }
                dragging = false;
                return true;
        }
        return false;
    }

    /**
     * Зберігає місце саме цієї групи.
     *
     * <p>Перечитуємо й перезаписуємо весь набір: груп одиниці, тож простота
     * тут дорожча за економію. Знаходимо групу за назвою й порядком, бо
     * власного ідентифікатора в неї немає.
     */
    private void savePosition(QuickButtons.Group group, float x, float y) {
        group.x = x;
        group.y = y;
        final ArrayList<QuickButtons.Group> groups = QuickButtons.get(dialogId);
        for (QuickButtons.Group stored : groups) {
            if (TextUtils.equals(stored.name, group.name)) {
                stored.x = x;
                stored.y = y;
                break;
            }
        }
        QuickButtons.save(dialogId, groups);
    }

    private float clamp(float value, int max) {
        return Math.max(0, Math.min(value, Math.max(0, max)));
    }

    private static int darken(int color) {
        final float k = 0.82f;
        return android.graphics.Color.argb(
                android.graphics.Color.alpha(color),
                (int) (android.graphics.Color.red(color) * k),
                (int) (android.graphics.Color.green(color) * k),
                (int) (android.graphics.Color.blue(color) * k));
    }

    // ── Список групи ─────────────────────────────────────────────────────

    private void showList(View anchor, QuickButtons.Group group) {
        if (group.buttons.isEmpty() || onPick == null) {
            return;
        }
        final Context context = getContext();

        final View body;
        if (group.style == QuickButtons.STYLE_GRID) {
            body = buildGrid(context, group);
        } else if (group.style == QuickButtons.STYLE_ROW) {
            body = buildRow(context, group);
        } else {
            body = buildColumn(context, group);
        }

        final FrameLayout wrapper = new FrameLayout(context);
        wrapper.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground)));
        wrapper.setElevation(dp(6));
        wrapper.addView(body);

        popup = new PopupWindow(wrapper,
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(null);
        popup.setOutsideTouchable(true);
        // Показуємо вгору: кнопка зазвичай унизу екрана, і список, розкритий
        // донизу, не вмістився б.
        popup.showAsDropDown(anchor, -dp(140),
                -dp(60) - dp(40) * Math.min(group.buttons.size(), 6));
    }

    private View buildColumn(Context context, QuickButtons.Group group) {
        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        for (QuickButtons.Button button : group.buttons) {
            list.addView(makeItem(context, button, dp(200)));
        }
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        return scroll;
    }

    private View buildRow(Context context, QuickButtons.Group group) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (QuickButtons.Button button : group.buttons) {
            row.addView(makeItem(context, button, 0));
        }
        final HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.addView(row);
        return scroll;
    }

    private View buildGrid(Context context, QuickButtons.Group group) {
        final GridLayout grid = new GridLayout(context);
        grid.setColumnCount(2);
        for (QuickButtons.Button button : group.buttons) {
            grid.addView(makeItem(context, button, dp(130)));
        }
        return grid;
    }

    private View makeItem(Context context, QuickButtons.Button button, int width) {
        final TextView item = new TextView(context);
        item.setText(button.label);
        item.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        item.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        item.setPadding(dp(16), dp(12), dp(16), dp(12));
        item.setMaxLines(1);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
        item.setOnClickListener(v -> {
            dismiss();
            if (onPick != null) {
                onPick.run(button);
            }
        });
        if (width > 0) {
            item.setWidth(width);
        }
        return item;
    }

    private void dismiss() {
        if (popup != null) {
            try {
                popup.dismiss();
            } catch (Throwable ignored) {
            }
            popup = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismiss();
    }
}
