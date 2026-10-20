package com.sugarpop.match3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import java.util.List;
import java.util.Random;

/** Draws the board and runs the swap / pop / fall animations and effects. */
public class BoardView extends View {

    public interface Listener {
        void onMoveUsed();
        void onStepPopped(Board.Step step);
        void onSettled();
        void onHammerUsed();
        void onFreeSwapUsed();
        void onSwapRejected();
    }

    public static final int[] COLORS = {0xFFE8223A, 0xFFFF8010, 0xFFFFCD19, 0xFF28BE46, 0xFF1E78F5, 0xFF963CE6};
    public static final int[] CANDY = {R.drawable.candy_0, R.drawable.candy_1, R.drawable.candy_2,
            R.drawable.candy_3, R.drawable.candy_4, R.drawable.candy_5};
    private static final int[][] SPECIAL_RES = {
            {R.drawable.candy_0_h, R.drawable.candy_0_v, R.drawable.candy_0_w},
            {R.drawable.candy_1_h, R.drawable.candy_1_v, R.drawable.candy_1_w},
            {R.drawable.candy_2_h, R.drawable.candy_2_v, R.drawable.candy_2_w},
            {R.drawable.candy_3_h, R.drawable.candy_3_v, R.drawable.candy_3_w},
            {R.drawable.candy_4_h, R.drawable.candy_4_v, R.drawable.candy_4_w},
            {R.drawable.candy_5_h, R.drawable.candy_5_v, R.drawable.candy_5_w},
    };

    private Board board;
    private Listener listener;

    private float cell, left, top;
    private final float density;
    private final Bitmap[][] sprites = new Bitmap[6][4];
    private Bitmap bombSprite;
    private final RectF dst = new RectF();
    private Path boardPath;

    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint boardFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tileA = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tileB = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jelly1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jelly2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jellyEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint partPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scoreFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scoreStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint praiseFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint praiseStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ---- animation state
    private static final int IDLE = 0, SWAP = 1, CLEAR = 2, FALL = 3, SHUFFLE = 4;
    private int phase = IDLE;
    private float t;
    private ValueAnimator anim;
    private int[][] showColor, showSpecial, showJelly;
    private int sr1, sc1, sr2, sc2;
    private boolean swapBack;
    private List<Board.Step> steps;
    private int stepIndex;
    private Runnable afterSteps;

    // ---- input
    private int downR = -1, downC = -1;
    private float downX, downY;
    private int selR = -1, selC = -1;
    private boolean hammerArmed, freeSwapArmed, inputLocked;

    // ---- hint, praise, particles
    private int[] hint;
    private long lastActivity;
    private ValueAnimator frameAnim;
    private long lastFrame;
    private String praise;
    private long praiseStart;
    private static final int MAX_PARTS = 500;
    private final float[] px = new float[MAX_PARTS], py = new float[MAX_PARTS], pvx = new float[MAX_PARTS],
            pvy = new float[MAX_PARTS], plife = new float[MAX_PARTS], psize = new float[MAX_PARTS];
    private final int[] pcolor = new int[MAX_PARTS];
    private int partCount;
    private final Random rnd = new Random();
    public boolean haptics = true;

    public BoardView(Context context) {
        this(context, null);
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 6; i++) {
            sprites[i][0] = Ui.bmp(getResources(), CANDY[i]);
            for (int k = 0; k < 3; k++) sprites[i][k + 1] = Ui.bmp(getResources(), SPECIAL_RES[i][k]);
        }
        bombSprite = Ui.bmp(getResources(), R.drawable.candy_bomb);
        boardFill.setColor(0xB31B2C7A);
        boardEdge.setStyle(Paint.Style.STROKE);
        boardEdge.setColor(0xCCFFFFFF);
        tileA.setColor(0x33A9C8FF);
        tileB.setColor(0x1A6E8EFF);
        jellyEdge.setStyle(Paint.Style.STROKE);
        jellyEdge.setColor(0x99FFFFFF);
        beamPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        for (Paint p : new Paint[]{scoreFill, scoreStroke, praiseFill, praiseStroke}) {
            p.setTypeface(Ui.font(context));
            p.setTextAlign(Paint.Align.CENTER);
        }
        scoreStroke.setStyle(Paint.Style.STROKE);
        scoreStroke.setStrokeJoin(Paint.Join.ROUND);
        praiseStroke.setStyle(Paint.Style.STROKE);
        praiseStroke.setStrokeJoin(Paint.Join.ROUND);
        praiseStroke.setColor(0xFF6A1B9A);
        selPaint.setStyle(Paint.Style.STROKE);
        selPaint.setColor(Color.WHITE);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setBoard(Board b) {
        cancelAll();
        board = b;
        phase = IDLE;
        selR = selC = -1;
        hammerArmed = freeSwapArmed = false;
        inputLocked = false;
        hint = null;
        praise = null;
        partCount = 0;
        buildBoardPath();
        touchActivity();
        invalidate();
    }

    public boolean isIdle() {
        return phase == IDLE && board != null;
    }

    public void setInputLocked(boolean locked) {
        inputLocked = locked;
    }

    public void setHammerArmed(boolean armed) {
        hammerArmed = armed;
        if (armed) freeSwapArmed = false;
        selR = selC = -1;
        invalidate();
    }

    public void setFreeSwapArmed(boolean armed) {
        freeSwapArmed = armed;
        if (armed) hammerArmed = false;
        selR = selC = -1;
        invalidate();
    }

    public boolean isHammerArmed() {
        return hammerArmed;
    }

    public boolean isFreeSwapArmed() {
        return freeSwapArmed;
    }

    public void playExternal(List<Board.Step> s, Runnable done) {
        hint = null;
        if (s == null || s.isEmpty()) {
            if (done != null) done.run();
            return;
        }
        playSteps(s, done);
    }

    public void showPraise(String text) {
        praise = text;
        praiseStart = System.currentTimeMillis();
        invalidate();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float pad = 10 * density;
        float size = Math.min(w, h) - pad * 2;
        cell = size / Level.COLS;
        left = (w - size) / 2f;
        top = (h - size) / 2f;
        boardEdge.setStrokeWidth(3 * density);
        jellyEdge.setStrokeWidth(1.5f * density);
        jelly1.setShader(new LinearGradient(0, 0, 0, cell, 0xCCFFB3D9, 0xCCFF6FB0, Shader.TileMode.CLAMP));
        jelly2.setShader(new LinearGradient(0, 0, 0, cell, 0xEEFF6FB0, 0xEED0217A, Shader.TileMode.CLAMP));
        scoreFill.setTextSize(cell * 0.5f);
        scoreStroke.setTextSize(cell * 0.5f);
        scoreStroke.setStrokeWidth(cell * 0.08f);
        praiseFill.setTextSize(cell * 1.15f);
        praiseStroke.setTextSize(cell * 1.15f);
        praiseStroke.setStrokeWidth(cell * 0.16f);
        selPaint.setStrokeWidth(3 * density);
        buildBoardPath();
    }

    private void buildBoardPath() {
        if (board == null || cell <= 0) return;
        Path p = new Path();
        float grow = 5 * density, rr = cell * 0.28f;
        for (int r = 0; r < board.rows; r++) {
            for (int c = 0; c < board.cols; c++) {
                if (!board.valid[r][c]) continue;
                Path q = new Path();
                q.addRoundRect(left + c * cell - grow, top + r * cell - grow,
                        left + (c + 1) * cell + grow, top + (r + 1) * cell + grow, rr, rr, Path.Direction.CW);
                p.op(q, Path.Op.UNION);
            }
        }
        boardPath = p;
    }

    private float cx(float c) {
        return left + (c + 0.5f) * cell;
    }

    private float cy(float r) {
        return top + (r + 0.5f) * cell;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board == null || cell <= 0) return;
        if (boardPath != null) {
            canvas.drawPath(boardPath, boardFill);
            canvas.drawPath(boardPath, boardEdge);
        }
        int[][] col = board.color, spe = board.special, jel = board.jelly;
        if (phase != IDLE && showColor != null) {
            col = showColor;
            spe = showSpecial;
            jel = showJelly;
        }
        float in = cell * 0.035f, rc = cell * 0.2f;
        for (int r = 0; r < board.rows; r++) {
            for (int c = 0; c < board.cols; c++) {
                if (!board.valid[r][c]) continue;
                float x = left + c * cell, y = top + r * cell;
                canvas.drawRoundRect(x + in, y + in, x + cell - in, y + cell - in, rc, rc, (r + c) % 2 == 0 ? tileA : tileB);
                int j = jel[r][c];
                if (j > 0) {
                    canvas.save();
                    canvas.translate(x, y);
                    canvas.drawRoundRect(in * 1.5f, in * 1.5f, cell - in * 1.5f, cell - in * 1.5f, rc, rc, j >= 2 ? jelly2 : jelly1);
                    canvas.drawRoundRect(in * 1.5f, in * 1.5f, cell - in * 1.5f, cell - in * 1.5f, rc, rc, jellyEdge);
                    canvas.restore();
                }
            }
        }

        long now = System.currentTimeMillis();
        float wiggle = 0f;
        if (hint != null && phase == IDLE) wiggle = (float) Math.sin(now / 90.0) * ((now / 600) % 2 == 0 ? 1 : 0);

        canvas.save();
        if (boardPath != null) canvas.clipPath(boardPath);
        for (int r = 0; r < board.rows; r++) {
            for (int c = 0; c < board.cols; c++) {
                int color = col[r][c];
                if (color == Board.EMPTY) continue;
                float x = cx(c), y = cy(r), scale = 1f, rot = 0f;
                if (phase == SWAP) {
                    float p = swapBack ? 1f - t : t;
                    if (r == sr1 && c == sc1) { x = cx(c + (sc2 - sc1) * p); y = cy(r + (sr2 - sr1) * p); scale = 1f + 0.15f * (float) Math.sin(p * Math.PI); }
                    else if (r == sr2 && c == sc2) { x = cx(c + (sc1 - sc2) * p); y = cy(r + (sr1 - sr2) * p); }
                } else if (phase == CLEAR) {
                    Board.Step st = steps.get(stepIndex);
                    if (st.cleared[r][c]) scale = t < 0.25f ? 1f + t * 0.8f : 1.2f * (1f - (t - 0.25f) / 0.75f);
                } else if (phase == FALL) {
                    Board.Step st = steps.get(stepIndex);
                    int from = st.fromRow[r][c];
                    if (from != r) y = cy(from + (r - from) * t);
                } else if (phase == SHUFFLE) {
                    scale = t < 0.5f ? 1f - t * 2f : (t - 0.5f) * 2f;
                    rot = t * 360f;
                }
                if (hint != null && phase == IDLE && ((r == hint[0] && c == hint[1]) || (r == hint[2] && c == hint[3]))) {
                    rot = wiggle * 12f;
                    scale = 1.05f;
                }
                if (r == selR && c == selC && phase == IDLE) scale = 1.14f;
                if (scale <= 0.02f) continue;
                drawCandy(canvas, x, y, scale, rot, color, spe[r][c]);
            }
        }
        canvas.restore();

        if (phase == CLEAR) drawEffects(canvas, steps.get(stepIndex));
        drawParticles(canvas);

        if (phase == IDLE && selR >= 0) {
            float x = left + selC * cell, y = top + selR * cell;
            canvas.drawRoundRect(x + in, y + in, x + cell - in, y + cell - in, rc, rc, selPaint);
        }
        if (hammerArmed || freeSwapArmed) {
            selPaint.setAlpha((int) (140 + 115 * Math.sin(now / 150.0)));
            if (boardPath != null) canvas.drawPath(boardPath, selPaint);
            selPaint.setAlpha(255);
        }
        drawPraise(canvas, now);
    }

    private void drawCandy(Canvas canvas, float x, float y, float scale, float rot, int color, int special) {
        Bitmap b;
        if (special == Board.COLOR_BOMB || color == Board.BOMB) b = bombSprite;
        else {
            int i = Math.max(0, Math.min(5, color));
            int v = special == Board.STRIPE_H ? 1 : special == Board.STRIPE_V ? 2 : special == Board.WRAPPED ? 3 : 0;
            b = sprites[i][v];
        }
        float h = cell * 0.56f * scale;
        dst.set(x - h, y - h, x + h, y + h);
        if (rot != 0f) {
            canvas.save();
            canvas.rotate(rot, x, y);
            canvas.drawBitmap(b, null, dst, bmpPaint);
            canvas.restore();
        } else {
            canvas.drawBitmap(b, null, dst, bmpPaint);
        }
    }

    private void drawEffects(Canvas canvas, Board.Step st) {
        float a = 1f - t;
        for (int[] f : st.fired) {
            int r = f[0], c = f[1], type = f[2];
            float x = cx(c), y = cy(r);
            switch (type) {
                case Board.STRIPE_H:
                case Board.STRIPE_V: {
                    float w = cell * (0.25f + 0.5f * a);
                    boolean h = type == Board.STRIPE_H;
                    beamPaint.setShader(h
                            ? new LinearGradient(0, y - w / 2, 0, y + w / 2, new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP)
                            : new LinearGradient(x - w / 2, 0, x + w / 2, 0, new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                    beamPaint.setAlpha((int) (255 * a));
                    float reach = cell * board.cols * Math.min(1f, t * 3f);
                    if (h) canvas.drawRect(x - reach, y - w / 2, x + reach, y + w / 2, beamPaint);
                    else canvas.drawRect(x - w / 2, y - reach, x + w / 2, y + reach, beamPaint);
                    beamPaint.setShader(null);
                    break;
                }
                case Board.WRAPPED:
                case Board.FX_BIG_BLAST: {
                    float rad = cell * (type == Board.WRAPPED ? 1.6f : 2.8f) * t;
                    ringPaint.setShader(new RadialGradient(x, y, Math.max(1f, rad), new int[]{0x00FFFFFF, 0xCCFFF59D, 0x00FF9800}, new float[]{0f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                    ringPaint.setStyle(Paint.Style.FILL);
                    ringPaint.setAlpha((int) (255 * a));
                    canvas.drawCircle(x, y, Math.max(1f, rad), ringPaint);
                    ringPaint.setShader(null);
                    ringPaint.setStyle(Paint.Style.STROKE);
                    break;
                }
                case Board.COLOR_BOMB: {
                    beamPaint.setShader(null);
                    beamPaint.setStyle(Paint.Style.STROKE);
                    beamPaint.setStrokeWidth(cell * 0.08f);
                    beamPaint.setColor(0xFFFFFFFF);
                    beamPaint.setAlpha((int) (230 * a));
                    for (int rr = 0; rr < board.rows; rr++)
                        for (int cc = 0; cc < board.cols; cc++)
                            if (st.cleared[rr][cc] && st.beforeColor[rr][cc] == f[3]) {
                                float tx = cx(cc), ty = cy(rr);
                                float mx = (x + tx) / 2 + (ty - y) * 0.15f, my = (y + ty) / 2 - (tx - x) * 0.15f;
                                Path p = new Path();
                                p.moveTo(x, y);
                                p.quadTo(mx, my, x + (tx - x) * Math.min(1f, t * 2f), y + (ty - y) * Math.min(1f, t * 2f));
                                canvas.drawPath(p, beamPaint);
                            }
                    beamPaint.setStyle(Paint.Style.FILL);
                    break;
                }
                default:
                    break;
            }
        }
        // floating points
        if (st.points > 0) {
            float sx = 0, sy = 0;
            int n = 0;
            for (int r = 0; r < board.rows; r++)
                for (int c = 0; c < board.cols; c++)
                    if (st.cleared[r][c]) { sx += cx(c); sy += cy(r); n++; }
            if (n > 0) {
                int alpha = (int) (255 * Math.min(1f, 2.2f - 2.2f * t));
                float x = sx / n, y = sy / n - cell * 0.7f * t;
                int colorIdx = -1;
                for (int r = 0; r < board.rows && colorIdx < 0; r++)
                    for (int c = 0; c < board.cols; c++)
                        if (st.cleared[r][c] && st.beforeColor[r][c] >= 0 && st.beforeColor[r][c] < 6) { colorIdx = st.beforeColor[r][c]; break; }
                scoreFill.setColor(Color.WHITE);
                scoreFill.setAlpha(alpha);
                scoreStroke.setColor(colorIdx >= 0 ? Ui.darken(COLORS[colorIdx], 0.7f) : 0xFF6A1B9A);
                scoreStroke.setAlpha(alpha);
                String s = String.valueOf(st.points);
                canvas.drawText(s, x, y, scoreStroke);
                canvas.drawText(s, x, y, scoreFill);
            }
        }
    }

    private void drawPraise(Canvas canvas, long now) {
        if (praise == null) return;
        float age = (now - praiseStart) / 1200f;
        if (age >= 1f) {
            praise = null;
            return;
        }
        float s = age < 0.18f ? 0.4f + age / 0.18f * 0.7f : age < 0.3f ? 1.1f - (age - 0.18f) / 0.12f * 0.1f : 1f;
        int alpha = age > 0.75f ? (int) (255 * (1f - (age - 0.75f) / 0.25f)) : 255;
        float cxp = getWidth() / 2f, cyp = top + cell * board.rows * 0.42f;
        praiseFill.setShader(new LinearGradient(0, cyp - cell, 0, cyp, 0xFFFFF59D, 0xFFFF5FA2, Shader.TileMode.CLAMP));
        praiseFill.setAlpha(alpha);
        praiseStroke.setAlpha(alpha);
        canvas.save();
        canvas.scale(s, s, cxp, cyp);
        canvas.drawText(praise, cxp, cyp, praiseStroke);
        canvas.drawText(praise, cxp, cyp, praiseFill);
        canvas.restore();
    }

    private void spawnParticles(Board.Step st) {
        for (int r = 0; r < board.rows; r++) {
            for (int c = 0; c < board.cols; c++) {
                if (!st.cleared[r][c]) continue;
                int colorIdx = st.beforeColor[r][c];
                int base = colorIdx >= 0 && colorIdx < 6 ? COLORS[colorIdx] : COLORS[rnd.nextInt(6)];
                for (int k = 0; k < 5 && partCount < MAX_PARTS; k++) {
                    int i = partCount++;
                    double a = rnd.nextDouble() * Math.PI * 2;
                    float sp = cell * (2.5f + rnd.nextFloat() * 4f);
                    px[i] = cx(c);
                    py[i] = cy(r);
                    pvx[i] = (float) Math.cos(a) * sp;
                    pvy[i] = (float) Math.sin(a) * sp - cell * 3f;
                    plife[i] = 0.5f + rnd.nextFloat() * 0.4f;
                    psize[i] = cell * (0.06f + rnd.nextFloat() * 0.08f);
                    pcolor[i] = k == 0 ? Color.WHITE : Ui.lighten(base, rnd.nextFloat() * 0.4f);
                }
            }
        }
    }

    private void updateParticles(float dt) {
        int w = 0;
        for (int i = 0; i < partCount; i++) {
            plife[i] -= dt;
            if (plife[i] <= 0) continue;
            pvy[i] += cell * 14f * dt;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            if (w != i) {
                px[w] = px[i]; py[w] = py[i]; pvx[w] = pvx[i]; pvy[w] = pvy[i];
                plife[w] = plife[i]; psize[w] = psize[i]; pcolor[w] = pcolor[i];
            }
            w++;
        }
        partCount = w;
    }

    private void drawParticles(Canvas canvas) {
        for (int i = 0; i < partCount; i++) {
            partPaint.setColor(pcolor[i]);
            partPaint.setAlpha((int) (255 * Math.min(1f, plife[i] * 2.5f)));
            canvas.drawCircle(px[i], py[i], psize[i], partPaint);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (board == null || cell <= 0) return true;
        float x = e.getX(), y = e.getY();
        int c = (int) Math.floor((x - left) / cell), r = (int) Math.floor((y - top) / cell);
        boolean inside = r >= 0 && c >= 0 && r < board.rows && c < board.cols && board.valid[r][c];
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchActivity();
                if (!isIdle() || inputLocked || !inside) { downR = -1; return true; }
                downR = r; downC = c; downX = x; downY = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downR < 0 || hammerArmed) return true;
                float dx = x - downX, dy = y - downY;
                if (Math.max(Math.abs(dx), Math.abs(dy)) > cell * 0.35f) {
                    int r2 = downR, c2 = downC;
                    if (Math.abs(dx) > Math.abs(dy)) c2 += dx > 0 ? 1 : -1; else r2 += dy > 0 ? 1 : -1;
                    int r1 = downR, c1 = downC;
                    downR = -1;
                    selR = selC = -1;
                    if (r2 >= 0 && c2 >= 0 && r2 < board.rows && c2 < board.cols && board.valid[r2][c2]) trySwap(r1, c1, r2, c2);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (downR < 0) return true;
                int tr = downR, tc = downC;
                downR = -1;
                performClick();
                if (hammerArmed) {
                    useHammer(tr, tc);
                } else if (selR < 0) {
                    selR = tr; selC = tc;
                } else if (selR == tr && selC == tc) {
                    selR = selC = -1;
                } else if (Board.adjacent(selR, selC, tr, tc)) {
                    int r1 = selR, c1 = selC;
                    selR = selC = -1;
                    trySwap(r1, c1, tr, tc);
                } else {
                    selR = tr; selC = tc;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                downR = -1;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void useHammer(int r, int c) {
        hammerArmed = false;
        List<Board.Step> s = board.smash(r, c);
        if (s == null || s.isEmpty()) return;
        if (listener != null) listener.onHammerUsed();
        playSteps(s, null);
    }

    private void trySwap(int r1, int c1, int r2, int c2) {
        hint = null;
        boolean free = freeSwapArmed;
        snapshot();
        sr1 = r1; sc1 = c1; sr2 = r2; sc2 = c2;
        final List<Board.Step> result = board.swap(r1, c1, r2, c2, free);
        if (result != null && listener != null) {
            if (free) {
                freeSwapArmed = false;
                listener.onFreeSwapUsed();
            } else {
                listener.onMoveUsed();
            }
        }
        swapBack = false;
        runPhase(SWAP, 180, new DecelerateInterpolator(), () -> {
            if (result == null) {
                swapBack = true;
                if (listener != null) listener.onSwapRejected();
                runPhase(SWAP, 180, new AccelerateInterpolator(), this::finishIdle);
            } else {
                playSteps(result, null);
            }
        });
    }

    private void snapshot() {
        showColor = Board.copy(board.color);
        showSpecial = Board.copy(board.special);
        showJelly = Board.copy(board.jelly);
    }

    // ------------------------------------------------------------------ steps

    private void playSteps(List<Board.Step> s, Runnable done) {
        steps = s;
        stepIndex = 0;
        afterSteps = done;
        playCurrentStep();
    }

    private void playCurrentStep() {
        if (steps == null || stepIndex >= steps.size()) {
            Runnable d = afterSteps;
            afterSteps = null;
            finishIdle();
            if (d != null) d.run();
            return;
        }
        Board.Step st = steps.get(stepIndex);
        showColor = st.beforeColor;
        showSpecial = st.beforeSpecial;
        showJelly = st.beforeJelly;
        if (st.shuffle) {
            if (listener != null) listener.onStepPopped(st);
            runPhase(SHUFFLE, 520, new LinearInterpolator(), () -> {
                stepIndex++;
                playCurrentStep();
            });
            postDelayed(() -> {
                if (phase == SHUFFLE && steps != null) {
                    showColor = st.afterColor;
                    showSpecial = st.afterSpecial;
                    showJelly = st.afterJelly;
                }
            }, 260);
            return;
        }
        if (st.cascade == 2) showPraise("Nice!");
        else if (st.cascade == 4) showPraise("Yummy!");
        else if (st.cascade == 6) showPraise("Sweet Pop!");
        else if (st.cascade == 8) showPraise("Super Sugar!");
        if (listener != null) listener.onStepPopped(st);
        if (!st.fired.isEmpty() && haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        spawnParticles(st);
        long clearMs = st.fired.isEmpty() ? 230 : 380;
        runPhase(CLEAR, clearMs, new LinearInterpolator(), () -> {
            showColor = st.afterColor;
            showSpecial = st.afterSpecial;
            showJelly = st.afterJelly;
            int maxDrop = 1;
            for (int r = 0; r < board.rows; r++)
                for (int c = 0; c < board.cols; c++) maxDrop = Math.max(maxDrop, r - st.fromRow[r][c]);
            runPhase(FALL, 150 + maxDrop * 45L, new AccelerateInterpolator(1.4f), () -> {
                stepIndex++;
                playCurrentStep();
            });
        });
    }

    private void finishIdle() {
        phase = IDLE;
        showColor = null;
        steps = null;
        touchActivity();
        invalidate();
        if (listener != null) listener.onSettled();
    }

    private void runPhase(int p, long ms, TimeInterpolator interp, Runnable end) {
        if (anim != null) {
            anim.removeAllListeners();
            anim.cancel();
        }
        phase = p;
        t = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(ms);
        anim.setInterpolator(interp);
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) end.run();
            }
        });
        anim.start();
    }

    private void cancelAll() {
        if (anim != null) {
            anim.removeAllListeners();
            anim.cancel();
            anim = null;
        }
        steps = null;
        afterSteps = null;
        showColor = null;
    }

    private void touchActivity() {
        lastActivity = System.currentTimeMillis();
        if (hint != null) {
            hint = null;
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrame = System.nanoTime();
        frameAnim = ValueAnimator.ofFloat(0f, 1f);
        frameAnim.setDuration(1000);
        frameAnim.setRepeatCount(ValueAnimator.INFINITE);
        frameAnim.addUpdateListener(a -> {
            long nowN = System.nanoTime();
            float dt = Math.min(0.05f, (nowN - lastFrame) / 1e9f);
            lastFrame = nowN;
            boolean dirty = false;
            if (partCount > 0) {
                updateParticles(dt);
                dirty = true;
            }
            if (praise != null) dirty = true;
            if (hammerArmed || freeSwapArmed) dirty = true;
            if (board != null && phase == IDLE && !inputLocked) {
                if (hint == null && System.currentTimeMillis() - lastActivity > 6000) hint = board.findMove();
                if (hint != null) dirty = true;
            }
            if (dirty) invalidate();
        });
        frameAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (frameAnim != null) frameAnim.cancel();
        cancelAll();
        super.onDetachedFromWindow();
    }
}
