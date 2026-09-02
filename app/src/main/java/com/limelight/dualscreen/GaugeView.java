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

import androidx.core.content.res.ResourcesCompat;

import com.limelight.R;

/**
 * Fluent-style progress ring: a faint full-circle track with an accent arc sweeping clockwise
 * from 12 o'clock and the metric's name, value and unit stacked in the middle. Modelled on the
 * ring Windows 11 uses for determinate progress - thin stroke, rounded cap, no ornament.
 */
public class GaugeView extends View {

    private static final float START_ANGLE = -90f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setColor(0x1AFFFFFF);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(accentColor);

        Typeface uiFont = ResourcesCompat.getFont(getContext(), R.font.win_ui);

        labelPaint.setColor(0xC5FFFFFF);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(uiFont != null ? uiFont : Typeface.DEFAULT);

        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTypeface(uiFont != null ? uiFont : Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        unitPaint.setColor(0x8AFFFFFF);
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setTypeface(uiFont != null ? uiFont : Typeface.DEFAULT);

        applyMetrics(48f * getResources().getDisplayMetrics().density);
    }

    /** Ring and type are sized from the view itself so one gauge style works at any diameter. */
    private void applyMetrics(float size) {
        strokeWidth = Math.max(size * 0.05f, 2f);
        trackPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStrokeWidth(strokeWidth);
        labelPaint.setTextSize(size * 0.145f);
        valuePaint.setTextSize(size * 0.27f);
        unitPaint.setTextSize(size * 0.135f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyMetrics(Math.min(w, h));
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

        float centerX = arcBounds.centerX();
        float centerY = arcBounds.centerY();

        // Center stack: label / value / unit
        float valueBaseline = centerY + valuePaint.getTextSize() * 0.36f;
        canvas.drawText(value, centerX, valueBaseline, valuePaint);
        canvas.drawText(label, centerX, valueBaseline - valuePaint.getTextSize() * 1.05f, labelPaint);
        if (!unit.isEmpty()) {
            canvas.drawText(unit, centerX, valueBaseline + unitPaint.getTextSize() * 1.45f, unitPaint);
        }
    }
}
