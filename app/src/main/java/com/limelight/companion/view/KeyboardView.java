package com.limelight.companion.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.R;
import com.limelight.companion.InputSender;
import com.limelight.companion.KeyCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Full PC keyboard drawn on a canvas. Ctrl, Alt, Shift and Win are sticky: tap one and it
 * stays held for the next key, then releases. Keys sink and glow when pressed. Multi-touch,
 * so a real held key works for games.
 */
public class KeyboardView extends View {

    public interface Listener {
        void onModifiersChanged(List<String> held);
    }

    private static final class Key {
        final String label;
        final int vk;
        final float weight;
        final RectF rect = new RectF();
        float press; // 0..1 animated
        ValueAnimator anim;

        Key(String label, int vk, float weight) {
            this.label = label;
            this.vk = vk;
            this.weight = weight;
        }

        boolean isModifier() {
            return KeyCatalog.isModifier(vk);
        }
    }

    private final List<List<Key>> rows = new ArrayList<>();
    private final SparseArray<Key> pointerKeys = new SparseArray<>();
    private final List<Key> sticky = new ArrayList<>();
    private final float density;
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final int cKey, cKeyWide, cStroke, cText, cTextMuted, cAccent, cOnAccent;
    private Listener listener;

    public KeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        cKey = getResources().getColor(R.color.companion_key);
        cKeyWide = getResources().getColor(R.color.companion_key_wide);
        cStroke = getResources().getColor(R.color.companion_stroke_strong);
        cText = getResources().getColor(R.color.companion_text);
        cTextMuted = getResources().getColor(R.color.companion_text_secondary);
        cAccent = getResources().getColor(R.color.companion_accent);
        cOnAccent = getResources().getColor(R.color.companion_on_accent);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(density);
        glowPaint.setStyle(Paint.Style.STROKE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setHapticFeedbackEnabled(true);
        buildLayout();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    private void buildLayout() {
        row(k("Esc", 0x1B, 1.3f), f(1), f(2), f(3), f(4), f(5), f(6), f(7), f(8), f(9), f(10), f(11), f(12), k("Del", 0x2E, 1.3f));
        row(k("`", 0xC0), k("1", '1'), k("2", '2'), k("3", '3'), k("4", '4'), k("5", '5'), k("6", '6'), k("7", '7'),
                k("8", '8'), k("9", '9'), k("0", '0'), k("-", 0xBD), k("=", 0xBB), k("⌫", 0x08, 1.6f));
        row(k("Tab", 0x09, 1.4f), l('Q'), l('W'), l('E'), l('R'), l('T'), l('Y'), l('U'), l('I'), l('O'), l('P'),
                k("[", 0xDB), k("]", 0xDD), k("\\", 0xDC, 1.2f));
        row(k("Caps", 0x14, 1.7f), l('A'), l('S'), l('D'), l('F'), l('G'), l('H'), l('J'), l('K'), l('L'),
                k(";", 0xBA), k("'", 0xDE), k("Enter", 0x0D, 1.9f));
        row(k("Shift", KeyCatalog.VK_SHIFT, 2.2f), l('Z'), l('X'), l('C'), l('V'), l('B'), l('N'), l('M'),
                k(",", 0xBC), k(".", 0xBE), k("/", 0xBF), k("↑", 0x26), k("Shift", KeyCatalog.VK_SHIFT, 1.4f));
        row(k("Ctrl", KeyCatalog.VK_CTRL, 1.3f), k("Win", KeyCatalog.VK_WIN, 1.2f), k("Alt", KeyCatalog.VK_ALT, 1.2f),
                k("Space", 0x20, 5.4f), k("Alt", KeyCatalog.VK_ALT, 1.2f), k("←", 0x25), k("↓", 0x28), k("→", 0x27));
    }

    private void row(Key... keys) {
        List<Key> r = new ArrayList<>();
        for (Key k : keys) r.add(k);
        rows.add(r);
    }

    private static Key k(String label, int vk) {
        return new Key(label, vk, 1f);
    }

    private static Key k(String label, int vk, float w) {
        return new Key(label, vk, w);
    }

    private static Key l(char c) {
        return new Key(String.valueOf(c), c, 1f);
    }

    private static Key f(int n) {
        return new Key("F" + n, 0x70 + n - 1, 1f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float gap = 5 * density;
        float rowH = (h - gap * (rows.size() - 1)) / rows.size();
        float y = 0;
        for (List<Key> r : rows) {
            float total = 0;
            for (Key k : r) total += k.weight;
            float unit = (w - gap * (r.size() - 1)) / total;
            float x = 0;
            for (Key k : r) {
                float kw = unit * k.weight;
                k.rect.set(x, y, x + kw, y + rowH);
                x += kw + gap;
            }
            y += rowH + gap;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float radius = 9 * density;
        for (List<Key> r : rows) {
            for (Key k : r) {
                boolean held = sticky.contains(k) || isStickyVk(k.vk);
                float p = k.press;
                tmp.set(k.rect);
                float inset = p * 1.5f * density;
                tmp.inset(inset, inset);
                tmp.offset(0, p * density);

                int fill = held ? cAccent : (k.weight > 1.25f || k.isModifier() ? cKeyWide : cKey);
                if (!held && p > 0) fill = blend(fill, 0xFF22344A, p);
                keyPaint.setColor(fill);
                c.drawRoundRect(tmp, radius, radius, keyPaint);

                strokePaint.setColor(held ? cAccent : blend(cStroke, cAccent, p));
                c.drawRoundRect(tmp, radius, radius, strokePaint);

                if (p > 0.02f) {
                    glowPaint.setColor(cAccent);
                    for (int i = 1; i <= 3; i++) {
                        glowPaint.setAlpha((int) (60 * p / i));
                        glowPaint.setStrokeWidth(2f * density * i);
                        c.drawRoundRect(tmp, radius + i * density, radius + i * density, glowPaint);
                    }
                }

                textPaint.setColor(held ? cOnAccent : (k.isModifier() ? cTextMuted : cText));
                textPaint.setTextSize((k.label.length() > 2 ? 12 : 15) * density);
                float ty = tmp.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
                c.drawText(k.label, tmp.centerX(), ty, textPaint);
            }
        }
    }

    private boolean isStickyVk(int vk) {
        for (Key s : sticky) if (s.vk == vk) return true;
        return false;
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private void animatePress(Key k, float to) {
        if (k.anim != null) k.anim.cancel();
        k.anim = ValueAnimator.ofFloat(k.press, to).setDuration(to > 0 ? 70 : 220);
        k.anim.addUpdateListener(a -> {
            k.press = (float) a.getAnimatedValue();
            invalidate();
        });
        k.anim.start();
    }

    private Key keyAt(float x, float y) {
        for (List<Key> r : rows) {
            for (Key k : r) {
                if (y >= k.rect.top - 2.5f * density && y <= k.rect.bottom + 2.5f * density
                        && x >= k.rect.left - 2.5f * density && x <= k.rect.right + 2.5f * density) {
                    return k;
                }
            }
        }
        return null;
    }

    private byte stickyMask() {
        byte m = 0;
        for (Key s : sticky) m |= InputSender.modifierFor(s.vk);
        return m;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        int id = e.getPointerId(idx);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                getParent().requestDisallowInterceptTouchEvent(true);
                Key k = keyAt(e.getX(idx), e.getY(idx));
                if (k == null) return true;
                pointerKeys.put(id, k);
                animatePress(k, 1f);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (k.isModifier()) {
                    toggleSticky(k);
                } else {
                    byte mods = 0;
                    for (Key s : sticky) {
                        InputSender.keyDown(s.vk, mods);
                        mods |= InputSender.modifierFor(s.vk);
                    }
                    InputSender.keyDown(k.vk, mods);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (action == MotionEvent.ACTION_CANCEL) {
                    for (int i = 0; i < pointerKeys.size(); i++) release(pointerKeys.valueAt(i));
                    pointerKeys.clear();
                    return true;
                }
                Key k = pointerKeys.get(id);
                pointerKeys.remove(id);
                if (k != null) release(k);
                return true;
            }
        }
        return true;
    }

    private void release(Key k) {
        animatePress(k, 0f);
        if (k.isModifier()) return;
        byte mods = stickyMask();
        InputSender.keyUp(k.vk, mods);
        if (!sticky.isEmpty()) {
            for (int i = sticky.size() - 1; i >= 0; i--) {
                Key s = sticky.get(i);
                mods &= (byte) ~InputSender.modifierFor(s.vk);
                InputSender.keyUp(s.vk, mods);
            }
            sticky.clear();
            notifyMods();
            invalidate();
        }
    }

    private void toggleSticky(Key k) {
        Key existing = null;
        for (Key s : sticky) if (s.vk == k.vk) existing = s;
        if (existing != null) sticky.remove(existing);
        else sticky.add(k);
        notifyMods();
        invalidate();
    }

    private void notifyMods() {
        if (listener == null) return;
        List<String> held = new ArrayList<>();
        for (Key s : sticky) held.add(s.label);
        listener.onModifiersChanged(held);
    }
}
