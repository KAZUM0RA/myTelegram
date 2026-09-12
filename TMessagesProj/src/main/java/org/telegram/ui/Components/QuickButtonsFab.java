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
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
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
 * Панель швидких фраз поверх чату.
 *
 * <p>Два режими на вибір для кожного чату. {@code MODE_SINGLE} — одна
 * кругла кнопка, що відкриває список: не займає місця, але потребує двох
 * дотиків. {@code MODE_SEPARATE} — усі фрази одразу на екрані: один дотик,
 * але більше зайнятої площі. Що краще, залежить від чату, тож вибір
 * зберігається окремо для кожного.
 *
 * <p>Панель перетягується пальцем і запам'ятовує місце. Положення, на
 * відміну від решти вигляду, спільне для всіх чатів: рука шукає кнопку на
 * одному місці незалежно від того, хто на тому боці.
 *
 * <p>Відрізняти перетягування від дотику доводиться вручну: звичайний
 * {@code OnClickListener} спрацьовував би наприкінці кожного перетягування.
 */
public class QuickButtonsFab extends FrameLayout {

    private final int touchSlop;
    private final LinearLayout content;

    private float downX, downY, startX, startY;
    private boolean dragging;

    private long dialogId;
    private Utilities.Callback<QuickButtons.Button> onPick;
    private PopupWindow popup;

    public QuickButtonsFab(Context context) {
        super(context);
        touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.END);
        addView(content, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
    }

    public void bind(long dialogId, Utilities.Callback<QuickButtons.Button> onPick) {
        this.dialogId = dialogId;
        this.onPick = onPick;
        refresh();
    }

    /** Перебудовує панель після зміни кнопок або вигляду. */
    public void refresh() {
        final ArrayList<QuickButtons.Button> buttons = QuickButtons.get(dialogId);
        setVisibility(buttons.isEmpty() ? GONE : VISIBLE);
        content.removeAllViews();
        if (buttons.isEmpty()) {
            return;
        }
        if (QuickButtons.getMode(dialogId) == QuickButtons.MODE_SEPARATE) {
            buildSeparate(buttons);
        } else {
            buildSingle();
        }
        setAlpha(QuickButtons.getAlphaPercent(dialogId) / 100f);
    }

    // ── Режим «одна кнопка» ──────────────────────────────────────────────

    private void buildSingle() {
        final int size = QuickButtons.getSize(dialogId);
        final View circle = makeCircle(size);
        circle.setOnTouchListener(this::handleTouch);
        content.addView(circle, LayoutHelper.createLinear(size, size));
    }

    /** Кругла кнопка з обраним значком: картинка, емодзі, вектор або стрілка. */
    private View makeCircle(int size) {
        final Context context = getContext();
        final FrameLayout circle = new FrameLayout(context);

        final int chosen = QuickButtons.getColor(dialogId);
        final int background = chosen == QuickButtons.COLOR_THEME
                ? Theme.getColor(Theme.key_chats_actionBackground) : chosen;
        circle.setBackground(Theme.createSimpleSelectorCircleDrawable(
                dp(size), background, darken(background)));
        circle.setElevation(dp(4));

        // Порядок навмисний: власна картинка перекриває емодзі, емодзі —
        // готовий значок, той — типову стрілку. Один зрозумілий вибір
        // замість кількох прапорців, які могли б суперечити.
        final java.io.File image = QuickButtons.getImage(dialogId);
        final String emoji = QuickButtons.getEmoji(dialogId);
        final String icon = QuickButtons.getIcon(dialogId);

        if (image != null) {
            final ImageView view = new ImageView(context);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(BitmapFactory.decodeFile(image.getAbsolutePath()));
            // Обрізаємо колом: кнопка кругла, і квадратне фото в ній
            // виглядало б випадковим.
            view.setClipToOutline(true);
            view.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    outline.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            circle.addView(view, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else if (!TextUtils.isEmpty(emoji)) {
            final TextView view = new TextView(context);
            view.setGravity(Gravity.CENTER);
            view.setText(emoji);
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, size * 0.45f);
            circle.addView(view, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            final ImageView view = new ImageView(context);
            view.setScaleType(ImageView.ScaleType.CENTER);
            view.setImageResource(iconResource(icon));
            view.setColorFilter(Theme.getColor(Theme.key_chats_actionIcon));
            circle.addView(view, LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        return circle;
    }

    private int iconResource(String name) {
        if (TextUtils.isEmpty(name)) {
            return R.drawable.msg_send;
        }
        final int id = getResources().getIdentifier(
                name, "drawable", getContext().getPackageName());
        return id != 0 ? id : R.drawable.msg_send;
    }

    // ── Режим «кнопки поруч» ─────────────────────────────────────────────

    private void buildSeparate(ArrayList<QuickButtons.Button> buttons) {
        final Context context = getContext();
        final int chosen = QuickButtons.getColor(dialogId);
        final int background = chosen == QuickButtons.COLOR_THEME
                ? Theme.getColor(Theme.key_chats_actionBackground) : chosen;

        for (QuickButtons.Button button : buttons) {
            final TextView pill = new TextView(context);
            pill.setText(button.label);
            pill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
            pill.setTextColor(Theme.getColor(Theme.key_chats_actionIcon));
            pill.setGravity(Gravity.CENTER);
            pill.setMaxLines(1);
            pill.setEllipsize(TextUtils.TruncateAt.END);
            pill.setPadding(dp(14), dp(8), dp(14), dp(8));
            pill.setBackground(Theme.createRoundRectDrawable(dp(18), background));
            pill.setElevation(dp(3));
            // Кожна кнопка і надсилає, і перетягує панель: окремої «ручки»
            // для перетягування немає, бо вона з'їдала б місце.
            pill.setOnTouchListener((v, event) -> handleTouch(v, event, button));
            content.addView(pill, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.END, 0, 0, 0, 6));
        }
    }

    // ── Перетягування ────────────────────────────────────────────────────

    private boolean handleTouch(View v, MotionEvent event) {
        return handleTouch(v, event, null);
    }

    /** @param direct якщо не null — дотик одразу спрацьовує цією кнопкою. */
    private boolean handleTouch(View v, MotionEvent event, QuickButtons.Button direct) {
        final View parent = (View) getParent();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                startX = getTranslationX();
                startY = getTranslationY();
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                final float dx = event.getRawX() - downX;
                final float dy = event.getRawY() - downY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                }
                if (dragging && parent != null) {
                    setTranslationX(clamp(startX + dx, parent.getWidth() - getWidth()));
                    setTranslationY(clamp(startY + dy, parent.getHeight() - getHeight()));
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    QuickButtons.savePosition(getTranslationX(), getTranslationY());
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (direct != null) {
                        if (onPick != null) {
                            onPick.run(direct);
                        }
                    } else {
                        showList();
                    }
                }
                dragging = false;
                return true;
        }
        return false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            applySavedPosition();
        }
    }

    private void applySavedPosition() {
        final View parent = (View) getParent();
        if (parent == null) {
            return;
        }
        final float x = QuickButtons.getPositionX();
        final float y = QuickButtons.getPositionY();
        if (x < 0 || y < 0) {
            // Ще не пересували: ставимо над полем вводу праворуч — там, де
            // найменше шансів перекрити текст повідомлень.
            setTranslationX(parent.getWidth() - getWidth() - dp(12));
            setTranslationY(parent.getHeight() - getHeight() - dp(120));
        } else {
            setTranslationX(clamp(x, parent.getWidth() - getWidth()));
            setTranslationY(clamp(y, parent.getHeight() - getHeight()));
        }
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

    // ── Список ───────────────────────────────────────────────────────────

    private void showList() {
        final ArrayList<QuickButtons.Button> buttons = QuickButtons.get(dialogId);
        if (buttons.isEmpty() || onPick == null) {
            return;
        }
        final Context context = getContext();
        final int style = QuickButtons.getStyle(dialogId);

        final View body;
        if (style == QuickButtons.STYLE_GRID) {
            body = buildGrid(context, buttons);
        } else if (style == QuickButtons.STYLE_ROW) {
            body = buildRow(context, buttons);
        } else {
            body = buildColumn(context, buttons);
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
        popup.showAsDropDown(this, -dp(140), -dp(60) - dp(40) * Math.min(buttons.size(), 6));
    }

    private View buildColumn(Context context, ArrayList<QuickButtons.Button> buttons) {
        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        for (QuickButtons.Button button : buttons) {
            list.addView(makeItem(context, button, dp(200)));
        }
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(list);
        return scroll;
    }

    private View buildRow(Context context, ArrayList<QuickButtons.Button> buttons) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (QuickButtons.Button button : buttons) {
            row.addView(makeItem(context, button, LayoutHelper.WRAP_CONTENT));
        }
        final android.widget.HorizontalScrollView scroll =
                new android.widget.HorizontalScrollView(context);
        scroll.addView(row);
        return scroll;
    }

    private View buildGrid(Context context, ArrayList<QuickButtons.Button> buttons) {
        final GridLayout grid = new GridLayout(context);
        grid.setColumnCount(2);
        for (QuickButtons.Button button : buttons) {
            final View item = makeItem(context, button, dp(130));
            grid.addView(item);
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
