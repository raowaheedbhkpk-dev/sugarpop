package com.sugarpop.match3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Score progress bar with three star markers that light up. */
public class ScoreBar extends View {

    private long score, s1 = 1, s2 = 2, s3 = 3;
    private float shown;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap gold, empty;
    private final RectF r = new RectF();
    private final float density;

    public ScoreBar(Context c) {
        this(c, null);
    }

    public ScoreBar(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        gold = Ui.bmp(getResources(), R.drawable.star_gold);
        empty = Ui.bmp(getResources(), R.drawable.star_empty);
        track.setColor(0x55FFFFFF);
        gloss.setColor(0x55FFFFFF);
    }

    public void setThresholds(long one, long two, long three) {
        s1 = one;
        s2 = two;
        s3 = Math.max(three, 1);
        score = 0;
        shown = 0;
        invalidate();
    }

    public void setScore(long s) {
        score = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float target = Math.min(1f, score / (float) s3);
        shown += (target - shown) * 0.2f;
        if (Math.abs(target - shown) > 0.002f) postInvalidateOnAnimation(); else shown = target;

        float h = 12 * density, star = 24 * density;
        float y = getHeight() - h - 4 * density;
        float x0 = 4 * density, x1 = getWidth() - 4 * density;
        r.set(x0, y, x1, y + h);
        canvas.drawRoundRect(r, h / 2, h / 2, track);
        if (shown > 0.01f) {
            r.set(x0, y, x0 + (x1 - x0) * shown, y + h);
            fill.setShader(new LinearGradient(0, y, 0, y + h, 0xFFB388FF, 0xFF7C3AED, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, h / 2, h / 2, fill);
            r.set(x0 + 3 * density, y + 2 * density, x0 + (x1 - x0) * shown - 3 * density, y + h * 0.45f);
            if (r.width() > 0) canvas.drawRoundRect(r, h / 4, h / 4, gloss);
        }
        long[] marks = {s1, s2, s3};
        for (long m : marks) {
            float fx = x0 + (x1 - x0) * Math.min(1f, m / (float) s3);
            fx = Math.min(fx, x1 - star / 2);
            r.set(fx - star / 2, y - star * 0.95f, fx + star / 2, y + star * 0.05f);
            canvas.drawBitmap(score >= m ? gold : empty, null, r, bmp);
        }
    }
}
