package com.limelight.dualscreen;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Circular ring gauge in the style of the AYN Thor's bottom-screen readouts: a faint full-circle
 * track, an accent-colored arc that sweeps clockwise from 12 o'clock, a dot marking the arc head,
 * and the metric's name / value / unit stacked in the middle.
 */
public class GaugeView extends View {

    private static final float START_ANGLE = -90f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcBounds = new RectF();

    private String label = "";
    private String value = "--";
    private String unit = "";
    private int accentColor = 0xFFB14AE8;

    /** Currently drawn fill fraction; animated toward {@link #targetFraction}. */
    private float fraction = 0f;
    private float targetFraction = 0f;
    private ValueAnimator animator;

    private float strokeWidth;

    public GaugeView(Context context) {
        this(context, null);
    }

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        strokeWidth = 4.5f * density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setColor(0x24FFFFFF);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(accentColor);

        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.WHITE);

        labelPaint.setColor(0x99FFFFFF);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(9f * density);
        labelPaint.setTypeface(Typeface.DEFAULT);

        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(19f * density);
        valuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        unitPaint.setColor(0x80FFFFFF);
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setTextSize(9f * density);
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
        invalidate();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        arcPaint.setColor(color);
        invalidate();
    }

    /**
     * Updates the readout. A negative fraction (or a null value) puts the gauge in its
     * "no data" state: empty ring and a placeholder value.
     */
    public void setReading(String value, String unit, float fraction) {
        this.value = value != null ? value : "--";
        this.unit = unit != null ? unit : "";
        animateTo(fraction < 0f ? 0f : Math.min(fraction, 1f));
        invalidate();
    }

    public void clearReading() {
        setReading("--", unit, -1f);
    }

    private void animateTo(float target) {
        if (Math.abs(target - targetFraction) < 0.001f) {
            return;
        }
        targetFraction = target;
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(fraction, target);
        animator.setDuration(350);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = Math.min(getWidth(), getHeight());
        float inset = strokeWidth / 2f + 1f;
        float left = (getWidth() - size) / 2f + inset;
        float top = (getHeight() - size) / 2f + inset;
        arcBounds.set(left, top, left + size - inset * 2f, top + size - inset * 2f);

        canvas.drawOval(arcBounds, trackPaint);

        float sweep = fraction * 360f;
        if (sweep > 0f) {
            // A full ring drawn as a 360 degree arc with round caps leaves a visible notch,
            // so draw it as a plain circle instead.
            if (sweep >= 359.5f) {
                canvas.drawOval(arcBounds, arcPaint);
            } else {
                canvas.drawArc(arcBounds, START_ANGLE, sweep, false, arcPaint);
            }
        }

        // Dot marking the head of the arc (top of the circle when empty)
        float centerX = arcBounds.centerX();
        float centerY = arcBounds.centerY();
        float radius = arcBounds.width() / 2f;
        double headAngle = Math.toRadians(START_ANGLE + sweep);
        float dotX = centerX + (float) Math.cos(headAngle) * radius;
        float dotY = centerY + (float) Math.sin(headAngle) * radius;
        dotPaint.setColor(sweep > 0f ? Color.WHITE : 0x40FFFFFF);
        canvas.drawCircle(dotX, dotY, strokeWidth * 0.62f, dotPaint);

        // Center stack: label / value / unit
        float density = getResources().getDisplayMetrics().density;
        float valueBaseline = centerY + valuePaint.getTextSize() * 0.36f;
        canvas.drawText(value, centerX, valueBaseline, valuePaint);
        canvas.drawText(label, centerX, valueBaseline - valuePaint.getTextSize() - 2f * density, labelPaint);
        if (!unit.isEmpty()) {
            canvas.drawText(unit, centerX, valueBaseline + unitPaint.getTextSize() + 4f * density, unitPaint);
        }
    }
}
