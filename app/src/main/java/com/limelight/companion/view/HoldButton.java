package com.limelight.companion.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;

import androidx.appcompat.widget.AppCompatImageButton;

import com.limelight.R;

/**
 * Button that only fires after being held for {@link #HOLD_MS}; a ring fills around it while
 * held and rewinds if released early.
 */
public class HoldButton extends AppCompatImageButton {

    public static final long HOLD_MS = 900;

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private float progress;
    private ValueAnimator anim;
    private Runnable onConfirmed;
    private boolean fired;

    public HoldButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * d);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setColor(getResources().getColor(R.color.companion_warning));
        setHapticFeedbackEnabled(true);
    }

    public void setOnConfirmed(Runnable r) {
        onConfirmed = r;
    }

    private void animateTo(float target, long duration) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(progress, target).setDuration(duration);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
            if (progress >= 1f && !fired) {
                fired = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (onConfirmed != null) onConfirmed.run();
            }
        });
        anim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                fired = false;
                setPressed(true);
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).start();
                animateTo(1f, (long) (HOLD_MS * (1f - progress)));
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                animate().scaleX(1f).scaleY(1f).setDuration(220).start();
                if (!fired) {
                    animateTo(0f, (long) (300 * progress));
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        announceForAccessibility(getContext().getString(R.string.companion_hold_to_disconnect));
                    }
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress <= 0f) return;
        float inset = ring.getStrokeWidth();
        arc.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawArc(arc, -90, 360 * progress, false, ring);
    }
}
