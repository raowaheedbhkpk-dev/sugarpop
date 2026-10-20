package com.sugarpop.match3;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Scrolling level map (level 1 at the bottom). Put it inside a ScrollView. */
public class MapView extends View {

    public interface Listener {
        void onLevelTapped(int level);
    }

    private static final String[] WORLD_NAMES = {
            "Sugar Meadow", "Mint Lagoon", "Caramel Canyon", "Berry Hills", "Lime Jungle", "Cocoa Castle",
            "Marshmallow Peaks", "Gumdrop Galaxy",
    };
    private static final int[][] GRASS = {
            {0xFF7FD45A, 0xFF4FAE3C}, {0xFF6FD6B8, 0xFF3BA88E}, {0xFFE8B464, 0xFFC98A3C},
            {0xFFE58FC0, 0xFFB95592}, {0xFF9BD64A, 0xFF5E9E28}, {0xFFB08A6A, 0xFF7C5A40},
            {0xFFB5C8F0, 0xFF7E96D0}, {0xFF8E7BE0, 0xFF5B45B0},
    };

    private Listener listener;
    private int unlocked = 1;
    private int[] stars = new int[0];
    private int count = 60;

    private final float density, spacing, pad, nodeW;
    private final Bitmap nodeCur, nodeDone, nodeLocked, starGold, starEmpty, mascot;
    private final Bitmap tree, house, lamp, bushBmp, cloudBmp;
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint grass = new Paint();
    private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint roadEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint road = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathBase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathStripe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pathShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint signPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint signText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sky = new Paint();
    private final RectF dst = new RectF();
    private final Rect clip = new Rect();
    private final int touchSlop;
    private float downX, downY;

    public MapView(Context context) {
        this(context, null);
    }

    public MapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        spacing = 108 * density;
        pad = 200 * density;
        nodeW = 62 * density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        nodeCur = Ui.bmp(getResources(), R.drawable.node_current);
        nodeDone = Ui.bmp(getResources(), R.drawable.node_done);
        nodeLocked = Ui.bmp(getResources(), R.drawable.node_locked);
        starGold = Ui.bmp(getResources(), R.drawable.star_gold);
        starEmpty = Ui.bmp(getResources(), R.drawable.star_empty);
        mascot = Ui.bmp(getResources(), R.drawable.mascot);
        tree = Ui.bmp(getResources(), R.drawable.map_tree);
        house = Ui.bmp(getResources(), R.drawable.map_house);
        lamp = Ui.bmp(getResources(), R.drawable.map_lamp);
        bushBmp = Ui.bmp(getResources(), R.drawable.map_bush);
        cloudBmp = Ui.bmp(getResources(), R.drawable.map_cloud);

        blob.setColor(0x22FFFFFF);
        roadEdge.setStyle(Paint.Style.STROKE);
        roadEdge.setColor(0xFFD9A066);
        roadEdge.setStrokeCap(Paint.Cap.ROUND);
        road.setStyle(Paint.Style.STROKE);
        road.setColor(0xFFF8D3A0);
        road.setStrokeCap(Paint.Cap.ROUND);
        float pw = 13 * density;
        pathShadow.setStyle(Paint.Style.STROKE);
        pathShadow.setStrokeWidth(pw + 6 * density);
        pathShadow.setColor(0x33000000);
        pathShadow.setStrokeCap(Paint.Cap.ROUND);
        pathBase.setStyle(Paint.Style.STROKE);
        pathBase.setStrokeWidth(pw);
        pathBase.setColor(0xFFFFFFFF);
        pathBase.setStrokeCap(Paint.Cap.ROUND);
        pathStripe.setStyle(Paint.Style.STROKE);
        pathStripe.setStrokeWidth(pw);
        pathStripe.setColor(0xFFF77BC0);
        pathStripe.setPathEffect(new DashPathEffect(new float[]{9 * density, 9 * density}, 0));
        for (Paint p : new Paint[]{numFill, numStroke, signText}) {
            p.setTypeface(Ui.font(context));
            p.setTextAlign(Paint.Align.CENTER);
        }
        numFill.setTextSize(26 * density);
        numFill.setColor(0xFFFFFFFF);
        numStroke.setTextSize(26 * density);
        numStroke.setStyle(Paint.Style.STROKE);
        numStroke.setStrokeWidth(5 * density);
        numStroke.setStrokeJoin(Paint.Join.ROUND);
        numStroke.setColor(0xFFB0145E);
        signText.setTextSize(20 * density);
        signText.setColor(0xFFFFFFFF);
        signPaint.setColor(0xFF8E4BC9);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setProgress(int unlockedLevel, int[] starsByLevel) {
        unlocked = unlockedLevel;
        stars = starsByLevel;
        int newCount = Math.max(40, ((unlockedLevel + 25) / 20 + 1) * 20);
        if (newCount != count) {
            count = newCount;
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = (int) (pad * 2 + (count - 1) * spacing);
        setMeasuredDimension(w, h);
    }

    public float nodeY(int level) {
        return getMeasuredHeight() - pad - (level - 1) * spacing;
    }

    private float roadX(float y) {
        float w = getWidth();
        return w / 2f + (float) Math.sin(y / (spacing * 2.6)) * w * 0.10f;
    }

    private float nodeX(int level) {
        float y = nodeY(level);
        return roadX(y) + (float) Math.sin(level * 1.1) * getWidth() * 0.14f;
    }

    private static float hash(int a, int b) {
        int h = a * 73856093 ^ b * 19349663;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h >>> 8) & 0xFFFF) / 65535f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (!canvas.getClipBounds(clip)) clip.set(0, 0, w, h);
        float vTop = clip.top - spacing, vBot = clip.bottom + spacing;
        float roadW = w * 0.46f;

        // ---- ground, by world
        int worlds = (count + 19) / 20;
        for (int world = 0; world < worlds; world++) {
            float bottom = world == 0 ? h : nodeY(world * 20) - spacing / 2f;
            float topY = world == worlds - 1 ? 0 : nodeY((world + 1) * 20) - spacing / 2f;
            if (bottom < vTop || topY > vBot) continue;
            int[] g = GRASS[world % GRASS.length];
            grass.setShader(new LinearGradient(0, topY, 0, bottom, g[0], g[1], Shader.TileMode.CLAMP));
            canvas.drawRect(0, topY, w, bottom, grass);
        }
        // sky at the very top
        float skyEnd = nodeY(count) - spacing * 0.6f;
        if (skyEnd > clip.top) {
            sky.setShader(new LinearGradient(0, 0, 0, skyEnd, 0xFFFFC46B, 0xFFFFE9A8, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, skyEnd, sky);
            for (int k = 0; k < 4; k++) {
                float cw = (110 + 40 * hash(k, 3)) * density;
                float x = w * hash(k, 7) - cw / 4, y = skyEnd * (0.2f + 0.6f * hash(k, 11));
                dst.set(x, y, x + cw, y + cw);
                canvas.drawBitmap(cloudBmp, null, dst, bmp);
            }
        }
        // soft grass blobs
        for (float y = (float) Math.floor(vTop / (spacing / 2)) * (spacing / 2); y < vBot; y += spacing / 2) {
            int k = (int) (y / (spacing / 2));
            float x = w * hash(k, 1);
            canvas.drawCircle(x, y, (30 + 40 * hash(k, 2)) * density, blob);
        }

        // ---- road
        Path rp = new Path();
        boolean first = true;
        float step = 24 * density;
        for (float y = vTop - step; y <= vBot + step; y += step) {
            if (first) { rp.moveTo(roadX(y), y); first = false; } else rp.lineTo(roadX(y), y);
        }
        roadEdge.setStrokeWidth(roadW + 14 * density);
        road.setStrokeWidth(roadW);
        canvas.drawPath(rp, roadEdge);
        canvas.drawPath(rp, road);

        // ---- decorations (both sides of the road)
        int firstLv = Math.max(1, (int) ((h - pad - vBot) / spacing));
        int lastLv = Math.min(count + 2, (int) ((h - pad - vTop) / spacing) + 2);
        for (int lv = lastLv; lv >= firstLv; lv--) {
            float y = nodeY(lv) + spacing * 0.3f;
            for (int side = -1; side <= 1; side += 2) {
                float r = hash(lv, side + 5);
                Bitmap b = r < 0.3f ? tree : r < 0.5f ? house : r < 0.7f ? lamp : bushBmp;
                float size = (b == house ? 112 : b == tree ? 96 : b == lamp ? 80 : 70) * density;
                float edge = roadX(y) + side * (roadW / 2f + 6 * density);
                float x = side < 0 ? edge - size - hash(lv, 9) * (edge - size) * 0.6f
                        : edge + hash(lv, 9) * (w - edge - size) * 0.6f;
                if (side < 0) x = Math.max(-size * 0.3f, x); else x = Math.min(w - size * 0.7f, x);
                dst.set(x, y - size, x + size, y);
                canvas.drawBitmap(b, null, dst, bmp);
            }
        }

        // ---- candy path between nodes
        Path pp = new Path();
        int pFirst = Math.max(1, firstLv - 1), pLast = Math.min(count, lastLv + 1);
        for (int lv = pFirst; lv <= pLast; lv++) {
            float x = nodeX(lv), y = nodeY(lv);
            if (lv == pFirst) pp.moveTo(x, y);
            else {
                float px = nodeX(lv - 1), py = nodeY(lv - 1);
                pp.cubicTo(px, py - spacing / 2f, x, y + spacing / 2f, x, y);
            }
        }
        canvas.save();
        canvas.translate(0, 3 * density);
        canvas.drawPath(pp, pathShadow);
        canvas.restore();
        canvas.drawPath(pp, pathBase);
        canvas.drawPath(pp, pathStripe);

        // ---- world signs
        for (int world = 0; world * 20 < count; world++) {
            float y = (world == 0 ? nodeY(1) + spacing * 0.95f : nodeY(world * 20) - spacing / 2f);
            if (y < vTop || y > vBot) continue;
            String name = WORLD_NAMES[world % WORLD_NAMES.length];
            float tw = signText.measureText(name) + 36 * density;
            float x = w / 2f;
            signPaint.setColor(0xFF8E4BC9);
            canvas.drawRoundRect(x - tw / 2, y - 20 * density, x + tw / 2, y + 20 * density, 20 * density, 20 * density, signPaint);
            signPaint.setColor(0x55FFFFFF);
            canvas.drawRoundRect(x - tw / 2 + 4 * density, y - 17 * density, x + tw / 2 - 4 * density, y - 2 * density, 14 * density, 14 * density, signPaint);
            canvas.drawText(name, x, y + 7 * density, signText);
        }

        // ---- nodes
        long now = System.currentTimeMillis();
        for (int lv = pFirst; lv <= pLast; lv++) {
            float x = nodeX(lv), y = nodeY(lv);
            boolean open = lv <= unlocked;
            boolean current = lv == unlocked;
            float nw = current ? nodeW * (1.18f + 0.05f * (float) Math.sin(now / 220.0)) : nodeW;
            if (current) {
                glow.setShader(new android.graphics.RadialGradient(x, y, nw, 0x99FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                canvas.drawCircle(x, y, nw, glow);
            }
            Bitmap nb = current ? nodeCur : open ? nodeDone : nodeLocked;
            dst.set(x - nw / 2, y - nw / 2, x + nw / 2, y + nw / 2);
            canvas.drawBitmap(nb, null, dst, bmp);
            if (open) {
                numStroke.setColor(current ? 0xFF9E0F55 : 0xFFC2427F);
                String s = String.valueOf(lv);
                canvas.drawText(s, x, y + 9 * density, numStroke);
                canvas.drawText(s, x, y + 9 * density, numFill);
            }
            int s = lv < stars.length ? stars[lv] : 0;
            if (open && !current) {
                float ss = 20 * density;
                for (int k = 0; k < 3; k++) {
                    float sx = x + (k - 1) * ss * 0.95f, sy = y - nw / 2 - ss * 0.35f + (k == 1 ? -ss * 0.25f : 0);
                    dst.set(sx - ss / 2, sy - ss / 2, sx + ss / 2, sy + ss / 2);
                    canvas.drawBitmap(k < s ? starGold : starEmpty, null, dst, bmp);
                }
            }
            if (current) {
                float ms = 64 * density;
                float bob = (float) Math.sin(now / 300.0) * 4 * density;
                float side = x > w / 2f ? -1 : 1;
                float mx = x + side * (nw / 2 + ms * 0.55f);
                dst.set(mx - ms / 2, y - ms / 2 + bob, mx + ms / 2, y + ms / 2 + bob);
                canvas.drawBitmap(mascot, null, dst, bmp);
            }
        }
        postInvalidateDelayed(40);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (Math.hypot(e.getX() - downX, e.getY() - downY) > touchSlop) return true;
                for (int lv = 1; lv <= count; lv++) {
                    if (Math.hypot(e.getX() - nodeX(lv), e.getY() - nodeY(lv)) <= nodeW * 0.8f) {
                        performClick();
                        if (lv <= unlocked && listener != null) listener.onLevelTapped(lv);
                        break;
                    }
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
