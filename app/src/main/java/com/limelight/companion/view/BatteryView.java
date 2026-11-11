package com.limelight.companion.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.limelight.R;

/** Battery glyph whose fill animates to the level; accent-coloured while charging. */
public class BatteryView extends View {

    private final float d;
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path bolt = new Path();
    private float level = 0f, shown = 0f;
    private boolean charging;
    private ValueAnimator levelAnim;
    private final int cNormal, cLow, cCharging;

    public BatteryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(1.5f * d);
        outline.setColor(getResources().getColor(R.color.companion_text_secondary));
        cNormal = getResources().getColor(R.color.companion_text_soft);
        cLow = getResources().getColor(R.color.companion_warning);
        cCharging = getResources().getColor(R.color.companion_accent);
    }

    public void setState(float level01, boolean charging) {
        float clamped = Math.max(0f, Math.min(1f, level01));
        if (levelAnim != null && clamped == this.level && charging == this.charging) return;
        this.level = clamped;
        // Charging is shown by colour only: an endless pulse would keep the second screen redrawing.
        this.charging = charging;
        if (levelAnim != null) levelAnim.cancel();
        levelAnim = ValueAnimator.ofFloat(shown, level).setDuration(600);
        levelAnim.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        levelAnim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float bodyW = w - 3 * d;
        r.set(0.75f * d, 0.75f * d, bodyW, h - 0.75f * d);
        c.drawRoundRect(r, 3 * d, 3 * d, outline);
        fill.setColor(outline.getColor());
        c.drawRoundRect(bodyW + 0.8f * d, h * 0.32f, w, h * 0.68f, 0.8f * d, 0.8f * d, fill);

        int color = charging ? cCharging : (level <= 0.15f ? cLow : cNormal);
        fill.setColor(color);
        float inner = 2.5f * d;
        float maxW = bodyW - 0.75f * d - inner * 2;
        c.drawRoundRect(r.left + inner, r.top + inner, r.left + inner + Math.max(1.5f * d, maxW * shown),
                r.bottom - inner, 1.5f * d, 1.5f * d, fill);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (levelAnim != null) levelAnim.cancel();
    }
}
