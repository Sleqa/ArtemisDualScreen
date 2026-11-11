package com.limelight.companion.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import com.limelight.R;
import com.limelight.companion.InputSender;
import com.limelight.nvstream.input.MouseButtonPacket;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Touchpad surface. A dim dot grid lights up in the accent colour around the finger, taps
 * send a ripple outward, and gestures are turned into host mouse input:
 * <ul>
 *   <li>one finger: move (relative) or point (absolute)</li>
 *   <li>tap: left click, two-finger tap: right click</li>
 *   <li>two-finger drag: scroll</li>
 *   <li>double-tap and hold: drag with the left button held</li>
 * </ul>
 */
public class TouchpadView extends View {

    public interface Listener {
        void onActiveChanged(boolean active);
    }

    private static final long TAP_TIMEOUT_MS = 220;
    private static final long DOUBLE_TAP_WINDOW_MS = 280;

    private final float density;
    private final float spacing;
    private final float glowRadius;
    private final int accent;
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final float cornerRadius;
    private final int touchSlop;

    private boolean absoluteMode;
    private float sensitivity = 1.6f;
    private Listener listener;

    // Glow state
    private float glowX, glowY;
    private float glowAmount;
    private ValueAnimator glowAnimator;

    private static final class Ripple {
        float x, y, progress;
    }

    private final List<Ripple> ripples = new ArrayList<>();
    private ValueAnimator rippleTicker;

    // Gesture state
    private float lastX, lastY, downX, downY;
    private float remX, remY, scrollRem;
    private long downTime, lastTapUpTime;
    private int maxPointers;
    private boolean moved, dragging, scrolling;
    private float lastScrollY;

    public TouchpadView(Context context) {
        this(context, null);
    }

    public TouchpadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        spacing = 22 * density;
        glowRadius = 110 * density;
        cornerRadius = 18 * density;
        accent = getResources().getColor(R.color.companion_accent);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        bgPaint.setColor(getResources().getColor(R.color.companion_surface));
        dotPaint.setColor(getResources().getColor(R.color.companion_stroke));
        glowPaint.setColor(accent);
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(2 * density);
        ripplePaint.setColor(accent);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(density);
        borderPaint.setColor(getResources().getColor(R.color.companion_stroke));
        setHapticFeedbackEnabled(true);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setAbsoluteMode(boolean absolute) {
        absoluteMode = absolute;
    }

    public boolean isAbsoluteMode() {
        return absoluteMode;
    }

    public void setSensitivity(float s) {
        sensitivity = s;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        bounds.set(0.5f * density, 0.5f * density, getWidth() - 0.5f * density, getHeight() - 0.5f * density);
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, bgPaint);

        canvas.save();
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(bounds, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);

        float baseR = 1.0f * density;
        float offX = (getWidth() % spacing) / 2f + spacing / 2f;
        float offY = (getHeight() % spacing) / 2f + spacing / 2f;
        for (float y = offY; y < getHeight(); y += spacing) {
            for (float x = offX; x < getWidth(); x += spacing) {
                canvas.drawCircle(x, y, baseR, dotPaint);
            }
        }

        if (glowAmount > 0.01f) {
            haloPaint.setShader(new RadialGradient(glowX, glowY, glowRadius * 1.3f,
                    withAlpha(accent, (int) (40 * glowAmount)), withAlpha(accent, 0), Shader.TileMode.CLAMP));
            canvas.drawCircle(glowX, glowY, glowRadius * 1.3f, haloPaint);

            // Only visit dots near the finger.
            float startX = offX + (float) Math.ceil((glowX - glowRadius - offX) / spacing) * spacing;
            float startY = offY + (float) Math.ceil((glowY - glowRadius - offY) / spacing) * spacing;
            for (float y = Math.max(offY, startY); y <= glowY + glowRadius; y += spacing) {
                for (float x = Math.max(offX, startX); x <= glowX + glowRadius; x += spacing) {
                    float d = (float) Math.hypot(x - glowX, y - glowY);
                    if (d >= glowRadius) continue;
                    float t = 1f - d / glowRadius;
                    float strength = t * t * glowAmount;
                    glowPaint.setAlpha((int) (255 * Math.min(1f, strength * 1.3f)));
                    canvas.drawCircle(x, y, baseR + 1.1f * density * strength, glowPaint);
                }
            }
        }

        for (Ripple r : ripples) {
            float eased = 1f - (1f - r.progress) * (1f - r.progress) * (1f - r.progress);
            ripplePaint.setAlpha((int) (140 * (1f - r.progress)));
            canvas.drawCircle(r.x, r.y, 8 * density + 72 * density * eased, ripplePaint);
        }
        canvas.restore();

        borderPaint.setColor(blend(getResources().getColor(R.color.companion_stroke),
                getResources().getColor(R.color.companion_accent_dim), glowAmount));
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, borderPaint);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private void animateGlow(float target, long duration) {
        if (glowAnimator != null) glowAnimator.cancel();
        glowAnimator = ValueAnimator.ofFloat(glowAmount, target);
        glowAnimator.setDuration(duration);
        glowAnimator.setInterpolator(new DecelerateInterpolator());
        glowAnimator.addUpdateListener(a -> {
            glowAmount = (float) a.getAnimatedValue();
            invalidate();
        });
        glowAnimator.start();
    }

    private void addRipple(float x, float y) {
        Ripple r = new Ripple();
        r.x = x;
        r.y = y;
        ripples.add(r);
        if (rippleTicker == null || !rippleTicker.isRunning()) {
            rippleTicker = ValueAnimator.ofFloat(0f, 1f);
            rippleTicker.setDuration(10_000);
            rippleTicker.setRepeatCount(ValueAnimator.INFINITE);
            final long[] last = {System.nanoTime()};
            rippleTicker.addUpdateListener(a -> {
                long now = System.nanoTime();
                float dt = (now - last[0]) / 1e9f;
                last[0] = now;
                Iterator<Ripple> it = ripples.iterator();
                while (it.hasNext()) {
                    Ripple rp = it.next();
                    rp.progress += dt / 0.52f;
                    if (rp.progress >= 1f) it.remove();
                }
                if (ripples.isEmpty()) a.cancel();
                invalidate();
            });
            rippleTicker.start();
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = lastX = glowX = e.getX();
                downY = lastY = glowY = e.getY();
                downTime = e.getEventTime();
                maxPointers = 1;
                moved = false;
                scrolling = false;
                remX = remY = scrollRem = 0;
                animateGlow(1f, 160);
                addRipple(downX, downY);
                if (listener != null) listener.onActiveChanged(true);

                if (absoluteMode) {
                    sendAbsolute(e.getX(), e.getY());
                } else if (downTime - lastTapUpTime < DOUBLE_TAP_WINDOW_MS) {
                    dragging = true;
                    InputSender.mouseDown(MouseButtonPacket.BUTTON_LEFT);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                maxPointers = Math.max(maxPointers, e.getPointerCount());
                if (e.getPointerCount() == 2) {
                    lastScrollY = averageY(e);
                }
                return true;

            case MotionEvent.ACTION_MOVE: {
                float x = e.getX(), y = e.getY();
                glowX = x;
                glowY = y;
                if (!moved && Math.hypot(x - downX, y - downY) > touchSlop) moved = true;

                if (e.getPointerCount() >= 2) {
                    float ay = averageY(e);
                    scrollRem += (ay - lastScrollY) * 4f / density;
                    lastScrollY = ay;
                    int amount = (int) scrollRem;
                    if (Math.abs(amount) >= 12) {
                        scrolling = true;
                        InputSender.scroll(amount);
                        scrollRem -= amount;
                    }
                } else if (absoluteMode) {
                    sendAbsolute(x, y);
                } else if (maxPointers == 1) {
                    float dx = (x - lastX) * sensitivity + remX;
                    float dy = (y - lastY) * sensitivity + remY;
                    int ix = (int) dx, iy = (int) dy;
                    remX = dx - ix;
                    remY = dy - iy;
                    InputSender.mouseMove(ix, iy);
                }
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerCount() == 2) {
                    // Continue moving with the remaining finger without a jump.
                    int remaining = e.getActionIndex() == 0 ? 1 : 0;
                    lastX = e.getX(remaining);
                    lastY = e.getY(remaining);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean cancelled = e.getActionMasked() == MotionEvent.ACTION_CANCEL;
                long held = e.getEventTime() - downTime;
                if (dragging) {
                    InputSender.mouseUp(MouseButtonPacket.BUTTON_LEFT);
                    dragging = false;
                    lastTapUpTime = 0;
                } else if (!cancelled && !moved && !scrolling && held < TAP_TIMEOUT_MS) {
                    byte button = maxPointers >= 2 ? MouseButtonPacket.BUTTON_RIGHT : MouseButtonPacket.BUTTON_LEFT;
                    click(button);
                    lastTapUpTime = maxPointers >= 2 ? 0 : e.getEventTime();
                } else if (absoluteMode && !cancelled && !scrolling && maxPointers == 1) {
                    // Absolute mode: a press-drag-release acts like touching the screen.
                    lastTapUpTime = 0;
                } else {
                    lastTapUpTime = 0;
                }
                animateGlow(0f, 520);
                if (listener != null) listener.onActiveChanged(false);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void click(byte button) {
        InputSender.mouseDown(button);
        postDelayed(() -> InputSender.mouseUp(button), 30);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void sendAbsolute(float x, float y) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        InputSender.mousePosition((int) Math.max(0, Math.min(w, x)), (int) Math.max(0, Math.min(h, y)), w, h);
    }

    private static float averageY(MotionEvent e) {
        float sum = 0;
        for (int i = 0; i < e.getPointerCount(); i++) sum += e.getY(i);
        return sum / e.getPointerCount();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (glowAnimator != null) glowAnimator.cancel();
        if (rippleTicker != null) rippleTicker.cancel();
        if (dragging) InputSender.mouseUp(MouseButtonPacket.BUTTON_LEFT);
    }
}
