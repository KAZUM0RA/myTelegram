/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.quickbuttons.QuickButtons;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/**
 * Кругла кнопка поверх чату, що відкриває список швидких фраз.
 *
 * <p>Перетягується пальцем і запам'ятовує місце: у кожного свій хват і своя
 * рука, тож нав'язувати єдиний кут було б незручно.
 *
 * <p>Відрізняти перетягування від дотику доводиться вручну: звичайний
 * {@code OnClickListener} спрацьовував би наприкінці кожного перетягування
 * і відкривав список тоді, коли його лише посунули.
 */
public class QuickButtonsFab extends FrameLayout {

    /** Скільки пікселів руху вже вважається перетягуванням, а не дотиком. */
    private final int touchSlop;

    private float downX, downY, startX, startY;
    private boolean dragging;

    private long dialogId;
    private Utilities.Callback<QuickButtons.Button> onPick;
    private PopupWindow popup;

    private final ImageView icon;
    private final TextView emojiView;

    public QuickButtonsFab(Context context) {
        super(context);
        touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();

        icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_send);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        addView(icon, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emojiView = new TextView(context);
        emojiView.setGravity(Gravity.CENTER);
        emojiView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        addView(emojiView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        applyAppearance();
        setElevation(dp(4));
    }

    /**
     * Застосовує колір, значок, розмір і прозорість із налаштувань.
     *
     * <p>Викликається і при створенні, і після зміни вигляду, щоб не
     * доводилося перевідкривати чат заради нового кольору.
     */
    public void applyAppearance() {
        final int chosen = QuickButtons.getColor();
        final int background = chosen == QuickButtons.COLOR_THEME
                ? Theme.getColor(Theme.key_chats_actionBackground) : chosen;
        // Колір натискання робимо трохи темнішим за основний, а не беремо з
        // теми: інакше при власному кольорі натискання виглядало б чужим.
        final int pressed = darken(background);

        final int size = QuickButtons.getSize();
        setBackground(Theme.createSimpleSelectorCircleDrawable(dp(size), background, pressed));

        final String emoji = QuickButtons.getEmoji();
        if (TextUtils.isEmpty(emoji)) {
            icon.setVisibility(VISIBLE);
            icon.setColorFilter(Theme.getColor(Theme.key_chats_actionIcon));
            emojiView.setVisibility(GONE);
        } else {
            icon.setVisibility(GONE);
            emojiView.setVisibility(VISIBLE);
            emojiView.setText(emoji);
            emojiView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, size * 0.45f);
        }

        setAlpha(QuickButtons.getAlphaPercent() / 100f);

        final ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null && params.width != dp(size)) {
            params.width = dp(size);
            params.height = dp(size);
            setLayoutParams(params);
        }
    }

    /** Темніший відтінок того самого кольору — для стану натискання. */
    private static int darken(int color) {
        final float k = 0.82f;
        return android.graphics.Color.argb(
                android.graphics.Color.alpha(color),
                (int) (android.graphics.Color.red(color) * k),
                (int) (android.graphics.Color.green(color) * k),
                (int) (android.graphics.Color.blue(color) * k));
    }

    public void bind(long dialogId, Utilities.Callback<QuickButtons.Button> onPick) {
        this.dialogId = dialogId;
        this.onPick = onPick;
        setVisibility(QuickButtons.hasAny(dialogId) ? VISIBLE : GONE);
    }

    /** Викликається після зміни набору кнопок або вигляду в налаштуваннях. */
    public void refresh() {
        applyAppearance();
        setVisibility(QuickButtons.hasAny(dialogId) ? VISIBLE : GONE);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
                    showList();
                }
                dragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void showList() {
        final ArrayList<QuickButtons.Button> buttons = QuickButtons.get(dialogId);
        if (buttons.isEmpty() || onPick == null) {
            return;
        }
        final Context context = getContext();

        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(Theme.createRoundRectDrawable(dp(10),
                Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground)));

        for (QuickButtons.Button button : buttons) {
            final TextView item = new TextView(context);
            item.setText(button.label);
            item.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
            item.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            item.setPadding(dp(16), dp(12), dp(16), dp(12));
            item.setMaxLines(1);
            item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            item.setOnClickListener(v -> {
                dismiss();
                onPick.run(button);
            });
            list.addView(item, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        final ScrollView scroll = new ScrollView(context);
        scroll.addView(list);

        popup = new PopupWindow(scroll, dp(220), LayoutHelper.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(null);
        popup.setOutsideTouchable(true);
        // Показуємо над кнопкою: вона зазвичай унизу екрана, і список,
        // відкритий донизу, не вмістився б.
        popup.showAsDropDown(this, -dp(180), -dp(8) - dp(44) * Math.min(buttons.size(), 6));
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
