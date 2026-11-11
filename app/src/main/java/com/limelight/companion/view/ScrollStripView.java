package com.limelight.companion.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.R;
import com.limelight.companion.InputSender;

/** Vertical strip beside the touchpad: drag up or down to scroll, with a lit thumb under the finger. */
public class ScrollStripView extends View {

    private final float density;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevron = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private float touchY = -1, lastY, rem, active;
    private float phase;
    private ValueAnimator activeAnim;

    public ScrollStripView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        bg.setColor(getResources().getColor(R.color.companion_surface));
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(density);
        border.setColor(getResources().getColor(R.color.companion_stroke));
        track.setColor(getResources().getColor(R.color.companion_stroke_strong));
        track.setStrokeWidth(4 * density);
        track.setStrokeCap(Paint.Cap.ROUND);
        chevron.setStyle(Paint.Style.STROKE);
        chevron.setStrokeWidth(2 * density);
        chevron.setStrokeCap(Paint.Cap.ROUND);
        chevron.setStrokeJoin(Paint.Join.ROUND);
        chevron.setColor(getResources().getColor(R.color.companion_text_faint));
        thumb.setColor(getResources().getColor(R.color.companion_accent));
        thumb.setStrokeWidth(4 * density);
        thumb.setStrokeCap(Paint.Cap.ROUND);
        setContentDescription(getResources().getString(R.string.companion_scroll_strip));
    }

    @Override
    protected void onDraw(Canvas c) {
        float r = 18 * density, w = getWidth(), h = getHeight(), cx = w / 2f;
        rect.set(0.5f * density, 0.5f * density, w - 0.5f * density, h - 0.5f * density);
        c.drawRoundRect(rect, r, r, bg);
        c.drawRoundRect(rect, r, r, border);

        float top = 40 * density, bottom = h - 40 * density, dash = 6 * density;
        float offset = phase % (dash * 2);
        for (float y = top - dash * 2 + offset; y < bottom; y += dash * 2) {
            float y0 = Math.max(top, y), y1 = Math.min(bottom, y + dash);
            if (y1 > y0) c.drawLine(cx, y0, cx, y1, track);
        }
        if (active > 0.01f && touchY >= 0) {
            thumb.setAlpha((int) (255 * active));
            float half = 26 * density;
            c.drawLine(cx, Math.max(top, touchY - half), cx, Math.min(bottom, touchY + half), thumb);
        }

        float s = 6 * density;
        path.reset();
        path.moveTo(cx - s, 22 * density);
        path.lineTo(cx, 16 * density);
        path.lineTo(cx + s, 22 * density);
        path.moveTo(cx - s, h - 22 * density);
        path.lineTo(cx, h - 16 * density);
        path.lineTo(cx + s, h - 22 * density);
        c.drawPath(path, chevron);
    }

    private void animateActive(float to) {
        if (activeAnim != null) activeAnim.cancel();
        activeAnim = ValueAnimator.ofFloat(active, to).setDuration(to > 0 ? 140 : 420);
        activeAnim.addUpdateListener(a -> {
            active = (float) a.getAnimatedValue();
            invalidate();
        });
        activeAnim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                lastY = touchY = e.getY();
                rem = 0;
                animateActive(1f);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float y = e.getY();
                float dy = y - lastY;
                phase += dy;
                rem += -dy * 4f / density;
                int amount = (int) rem;
                if (Math.abs(amount) >= 12) {
                    InputSender.scroll(amount);
                    rem -= amount;
                }
                lastY = touchY = y;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animateActive(0f);
                return true;
        }
        return super.onTouchEvent(e);
    }
}
