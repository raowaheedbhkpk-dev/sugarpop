package com.sugarpop.match3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Shared look: game font, outlined text, glossy buttons, sprite cache. */
public final class Ui {

    private static Typeface font;
    private static final SparseArray<Bitmap> bitmaps = new SparseArray<>();

    private Ui() {}

    public static Typeface font(Context c) {
        if (font == null) {
            try {
                font = Typeface.createFromAsset(c.getAssets(), "fonts/LilitaOne-Regular.ttf");
            } catch (RuntimeException e) {
                font = Typeface.DEFAULT_BOLD;
            }
        }
        return font;
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static Bitmap bmp(Resources res, int id) {
        Bitmap b = bitmaps.get(id);
        if (b == null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeResource(res, id, o);
            bitmaps.put(id, b);
        }
        return b;
    }

    /** Applies the game font to every TextView under {@code v}. */
    public static void applyFont(View v) {
        if (v instanceof TextView) ((TextView) v).setTypeface(font(v.getContext()));
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyFont(g.getChildAt(i));
        }
    }

    /** Rounded glossy pill background in the given base color. */
    public static Drawable pill(Context c, int color, float radiusDp) {
        float r = dp(c, radiusDp);
        GradientDrawable shadow = new GradientDrawable();
        shadow.setColor(darken(color, 0.55f));
        shadow.setCornerRadius(r);
        GradientDrawable body = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{lighten(color, 0.35f), color, darken(color, 0.85f)});
        body.setCornerRadius(r);
        body.setStroke(dp(c, 2), lighten(color, 0.55f));
        GradientDrawable gloss = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x88FFFFFF, 0x00FFFFFF});
        gloss.setCornerRadius(r);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{shadow, body, gloss});
        int s = dp(c, 4);
        ld.setLayerInset(0, 0, s, 0, 0);
        ld.setLayerInset(1, 0, 0, 0, s);
        ld.setLayerInset(2, dp(c, 8), dp(c, 3), dp(c, 8), s + dp(c, 14));
        return ld;
    }

    public static GradientDrawable rounded(int color, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    public static int lighten(int c, float f) {
        return Color.argb(Color.alpha(c),
                (int) (Color.red(c) + (255 - Color.red(c)) * f),
                (int) (Color.green(c) + (255 - Color.green(c)) * f),
                (int) (Color.blue(c) + (255 - Color.blue(c)) * f));
    }

    public static int darken(int c, float f) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    /** Makes a view shrink a little while pressed. */
    public static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Single-line text with a thick outline (and optional gradient fill), like game titles. */
    public static class OutlineText extends View {
        private String text = "";
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int topColor = Color.WHITE, bottomColor = Color.WHITE;
        private float pad;

        public OutlineText(Context c, float sizeSp, int fillColor, int strokeColor) {
            super(c);
            float px = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp, c.getResources().getDisplayMetrics());
            for (Paint p : new Paint[]{fill, stroke}) {
                p.setTypeface(font(c));
                p.setTextSize(px);
                p.setTextAlign(Paint.Align.CENTER);
            }
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setStrokeWidth(px * 0.16f);
            stroke.setColor(strokeColor);
            stroke.setShadowLayer(px * 0.08f, 0, px * 0.06f, 0x66000000);
            topColor = bottomColor = fillColor;
            pad = px * 0.15f;
        }

        public OutlineText gradient(int top, int bottom) {
            topColor = top;
            bottomColor = bottom;
            invalidate();
            return this;
        }

        public void setText(String t) {
            if (t == null) t = "";
            if (!t.equals(text)) {
                boolean resize = t.length() != text.length();
                text = t;
                if (resize) requestLayout();
                invalidate();
            }
        }

        @Override
        protected void onMeasure(int ws, int hs) {
            Paint.FontMetrics fm = fill.getFontMetrics();
            int w = (int) (fill.measureText(text) + stroke.getStrokeWidth() + pad * 2);
            int h = (int) (fm.descent - fm.ascent + stroke.getStrokeWidth() + pad);
            setMeasuredDimension(resolveSize(w + getPaddingLeft() + getPaddingRight(), ws),
                    resolveSize(h + getPaddingTop() + getPaddingBottom(), hs));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint.FontMetrics fm = fill.getFontMetrics();
            float cx = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight()) / 2f;
            float cy = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom()) / 2f - (fm.ascent + fm.descent) / 2f;
            fill.setShader(new LinearGradient(0, cy + fm.ascent, 0, cy, topColor, bottomColor, Shader.TileMode.CLAMP));
            canvas.drawText(text, cx, cy, stroke);
            canvas.drawText(text, cx, cy, fill);
        }
    }

    /** Glossy pill button with outlined label. */
    public static OutlineText button(Context c, String label, int color, float sizeSp) {
        OutlineText t = new OutlineText(c, sizeSp, Color.WHITE, darken(color, 0.45f));
        t.setText(label);
        t.setBackgroundResource(buttonRes(color));
        t.setClickable(true);
        t.setFocusable(true);
        pressable(t);
        return t;
    }

    /** 9-patch button art for a color constant. */
    public static int buttonRes(int color) {
        if (color == PINK) return R.drawable.btn_pink;
        if (color == ORANGE) return R.drawable.btn_orange;
        if (color == BLUE) return R.drawable.btn_blue;
        if (color == PURPLE) return R.drawable.btn_purple;
        if (color == TEAL) return R.drawable.btn_teal;
        return R.drawable.btn_green;
    }

    public static int panelRes(int color) {
        if (color == PINK) return R.drawable.panel_pink;
        if (color == ORANGE) return R.drawable.panel_orange;
        if (color == BLUE) return R.drawable.panel_blue;
        if (color == PURPLE) return R.drawable.panel_purple;
        if (color == TEAL) return R.drawable.panel_teal;
        return R.drawable.panel_green;
    }

    public static int ribbonRes(int color) {
        if (color == TEAL || color == GREEN) return R.drawable.ribbon_pink;
        if (color == PINK) return R.drawable.ribbon_purple;
        if (color == ORANGE) return R.drawable.ribbon_pink;
        if (color == BLUE) return R.drawable.ribbon_orange;
        return R.drawable.ribbon_teal;
    }

    public static final int GREEN = 0xFF2BC24A;
    public static final int PINK = 0xFFFF3D8B;
    public static final int BLUE = 0xFF1E90F0;
    public static final int ORANGE = 0xFFFF9A1F;
    public static final int PURPLE = 0xFF8C4BE0;
    public static final int TEAL = 0xFF2CC9B0;
}
