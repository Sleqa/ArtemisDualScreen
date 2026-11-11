package com.limelight.companion.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.limelight.R;

/**
 * Last-30-seconds trend line. Draws itself in on first show, then slides smoothly as each
 * new sample arrives.
 */
public class SparklineView extends View {

    private static final int CAPACITY = 30;

    private final float[] values = new float[CAPACITY];
    private int count;
    private float shift;    // 0..1 slide progress for the newest sample
    private float reveal = 0f;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();
    private final Path partial = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final int accent;
    private ValueAnimator shiftAnim;
    private ValueAnimator revealAnim;

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        accent = getResources().getColor(R.color.companion_accent);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(accent);
    }

    public void clear() {
        count = 0;
        reveal = 0;
        invalidate();
    }

    public void push(float v) {
        if (count == CAPACITY) {
            System.arraycopy(values, 1, values, 0, CAPACITY - 1);
            values[CAPACITY - 1] = v;
        } else {
            values[count++] = v;
        }
        if (count == 2 && reveal == 0) startReveal();
        if (shiftAnim != null) shiftAnim.cancel();
        shiftAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(380);
        shiftAnim.setInterpolator(new DecelerateInterpolator());
        shiftAnim.addUpdateListener(a -> {
            shift = (float) a.getAnimatedValue();
            invalidate();
        });
        shiftAnim.start();
    }

    private void startReveal() {
        revealAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(1100);
        revealAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        revealAnim.addUpdateListener(a -> {
            reveal = (float) a.getAnimatedValue();
            invalidate();
        });
        revealAnim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (count < 2) return;
        float w = getWidth(), h = getHeight(), pad = line.getStrokeWidth();
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        float range = Math.max(max - min, Math.max(0.5f, Math.abs(max) * 0.08f));
        float mid = (max + min) / 2f;
        min = mid - range * 0.65f;
        max = mid + range * 0.65f;

        float step = w / (CAPACITY - 1);
        // Newest sample sits at the right edge; the whole line slides left as it arrives.
        float xOffset = (1f - shift) * step;
        path.reset();
        for (int i = 0; i < count; i++) {
            float x = w - (count - 1 - i) * step + xOffset;
            if (i == count - 1) x = w;
            float y = pad + (h - 2 * pad) * (1f - (values[i] - min) / (max - min));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }

        Path toDraw = path;
        if (reveal < 1f) {
            measure.setPath(path, false);
            partial.reset();
            measure.getSegment(0, measure.getLength() * reveal, partial, true);
            toDraw = partial;
        }

        fillPath.set(toDraw);
        float startX = w - (count - 1) * step + xOffset;
        fillPath.lineTo(reveal < 1f ? startX + (w - startX) * reveal : w, h);
        fillPath.lineTo(startX, h);
        fillPath.close();
        fill.setShader(new LinearGradient(0, 0, 0, h, (accent & 0x00FFFFFF) | 0x33000000,
                accent & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(fillPath, fill);
        c.drawPath(toDraw, line);
    }
}
