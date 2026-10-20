package com.sugarpop.match3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Match-3 rules engine. No Android dependencies, so it can be unit tested.
 *
 * Every player action returns a list of {@link Step}s (one per cascade) that the
 * view animates: pieces in {@code cleared} pop, then pieces fall from {@code fromRow}.
 */
public final class Board {

    public static final int NONE = 0, STRIPE_H = 1, STRIPE_V = 2, WRAPPED = 3, COLOR_BOMB = 4;
    public static final int EMPTY = -1;
    /** Color value of a color bomb: never matches anything. */
    public static final int BOMB = 99;

    public final int rows, cols, numColors;
    public final int[][] color, special, jelly;
    /** false = hole in the board (no cell). Candies fall through holes. */
    public final boolean[][] valid;
    private final Random rnd;

    public long score;
    public final int[] collected = new int[8];

    /** One cascade: what popped, and how everything fell afterwards. */
    public static final class Step {
        public int[][] beforeColor, beforeSpecial, beforeJelly;
        public boolean[][] cleared;
        public int[][] afterColor, afterSpecial, afterJelly, fromRow;
        public final List<int[]> created = new ArrayList<>(); // {r, c, type}
        public int points;
        public int cascade;
        public boolean shuffle;
        public int clearedCount;
        /** Totals after this step, so the HUD can count up during cascades. */
        public long scoreAfter;
        public int[] collectedAfter;
        public int jellyLeftAfter;
        /** Specials that went off in this step, for effects: {r, c, type, extra}. */
        public final List<int[]> fired = new ArrayList<>();
    }

    /** Effect type for a big wrapped+wrapped blast. */
    public static final int FX_BIG_BLAST = 5;
    private final List<int[]> pendingFx = new ArrayList<>();

    private void stamp(Step s) {
        s.scoreAfter = score;
        s.collectedAfter = collected.clone();
        s.jellyLeftAfter = jellyLeft();
    }

    public Board(int rows, int cols, int numColors, long seed) {
        this(rows, cols, numColors, seed, null);
    }

    public Board(int rows, int cols, int numColors, long seed, boolean[][] shape) {
        this.rows = rows;
        this.cols = cols;
        this.numColors = numColors;
        this.color = new int[rows][cols];
        this.special = new int[rows][cols];
        this.jelly = new int[rows][cols];
        this.valid = new boolean[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++) valid[r][c] = shape == null || shape[r][c];
        this.rnd = new Random(seed);
        fillFresh();
    }

    /** Deep copy (used by the test bot to try moves). */
    public Board(Board o, long seed) {
        this.rows = o.rows;
        this.cols = o.cols;
        this.numColors = o.numColors;
        this.color = copy(o.color);
        this.special = copy(o.special);
        this.jelly = copy(o.jelly);
        this.valid = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) System.arraycopy(o.valid[r], 0, valid[r], 0, cols);
        this.rnd = new Random(seed);
        this.score = o.score;
        System.arraycopy(o.collected, 0, collected, 0, collected.length);
    }

    // ------------------------------------------------------------------ setup

    private void fillFresh() {
        for (int attempt = 0; attempt < 200; attempt++) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    special[r][c] = NONE;
                    if (!valid[r][c]) {
                        color[r][c] = EMPTY;
                        continue;
                    }
                    int col;
                    do {
                        col = rnd.nextInt(numColors);
                    } while ((c >= 2 && color[r][c - 1] == col && color[r][c - 2] == col)
                            || (r >= 2 && color[r - 1][c] == col && color[r - 2][c] == col));
                    color[r][c] = col;
                }
            }
            if (findMove() != null) return;
        }
    }

    public int jellyLeft() {
        int n = 0;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) n += jelly[r][c];
        return n;
    }

    // ------------------------------------------------------------------ moves

    public static boolean adjacent(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2) == 1;
    }

    /**
     * Tries swapping two neighbouring pieces. Returns the animation steps, or null if the swap
     * does nothing (the board is left unchanged).
     */
    public List<Step> swap(int r1, int c1, int r2, int c2) {
        return swap(r1, c1, r2, c2, false);
    }

    /** force = Free Swap booster: the swap happens even without a match. */
    public List<Step> swap(int r1, int c1, int r2, int c2, boolean force) {
        if (!adjacent(r1, c1, r2, c2)) return null;
        if (color[r1][c1] == EMPTY || color[r2][c2] == EMPTY) return null;
        int s1 = special[r1][c1], s2 = special[r2][c2];
        swapCells(r1, c1, r2, c2);

        boolean[][] forced = null;
        if (s1 == COLOR_BOMB || s2 == COLOR_BOMB) {
            forced = new boolean[rows][cols];
            if (s1 == COLOR_BOMB && s2 == COLOR_BOMB) {
                for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) forced[r][c] = true;
                special[r1][c1] = special[r2][c2] = NONE;
                pendingFx.add(new int[]{r2, c2, FX_BIG_BLAST, 0});
            } else {
                // after the swap the bomb sits where the other piece came from
                int br = s1 == COLOR_BOMB ? r2 : r1, bc = s1 == COLOR_BOMB ? c2 : c1;
                int or = s1 == COLOR_BOMB ? r1 : r2, oc = s1 == COLOR_BOMB ? c1 : c2;
                int target = color[or][oc];
                int otherSpecial = special[or][oc];
                pendingFx.add(new int[]{br, bc, COLOR_BOMB, target});
                special[br][bc] = NONE;
                forced[br][bc] = true;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (color[r][c] != target) continue;
                        forced[r][c] = true;
                        if (otherSpecial == STRIPE_H || otherSpecial == STRIPE_V) {
                            special[r][c] = rnd.nextBoolean() ? STRIPE_H : STRIPE_V;
                        } else if (otherSpecial == WRAPPED) {
                            special[r][c] = WRAPPED;
                        }
                    }
                }
            }
        } else if (s1 != NONE && s2 != NONE) {
            forced = new boolean[rows][cols];
            special[r1][c1] = special[r2][c2] = NONE;
            forced[r1][c1] = forced[r2][c2] = true;
            boolean w1 = s1 == WRAPPED, w2 = s2 == WRAPPED;
            if (w1 && w2) {
                markArea(forced, r2, c2, 2);
                pendingFx.add(new int[]{r2, c2, FX_BIG_BLAST, 0});
            } else if (w1 || w2) {
                for (int d = -1; d <= 1; d++) {
                    markRow(forced, r2 + d);
                    markCol(forced, c2 + d);
                    pendingFx.add(new int[]{r2 + d, c2, STRIPE_H, 0});
                    pendingFx.add(new int[]{r2, c2 + d, STRIPE_V, 0});
                }
            } else {
                markRow(forced, r2);
                markCol(forced, c2);
                pendingFx.add(new int[]{r2, c2, STRIPE_H, 0});
                pendingFx.add(new int[]{r2, c2, STRIPE_V, 0});
            }
        } else if (!hasMatchAt(r1, c1) && !hasMatchAt(r2, c2)) {
            if (force) return resolve(null, null);
            swapCells(r1, c1, r2, c2);
            return null;
        }
        return resolve(forced, new int[]{r1, c1, r2, c2});
    }

    /** Hammer booster: smash one piece. */
    public List<Step> smash(int r, int c) {
        if (color[r][c] == EMPTY) return null;
        boolean[][] forced = new boolean[rows][cols];
        forced[r][c] = true;
        if (special[r][c] == COLOR_BOMB) {
            int target = mostCommonColor();
            special[r][c] = NONE;
            for (int rr = 0; rr < rows; rr++)
                for (int cc = 0; cc < cols; cc++) if (color[rr][cc] == target) forced[rr][cc] = true;
        }
        return resolve(forced, null);
    }

    /** Shuffle booster. */
    public List<Step> shuffleBoard() {
        List<Step> steps = new ArrayList<>();
        steps.add(shuffleStep());
        return steps;
    }

    /**
     * End-of-level bonus: each move left turns a random piece into a striped piece,
     * then every special on the board goes off.
     */
    public List<Step> finale(int movesLeft) {
        List<int[]> normals = new ArrayList<>();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (special[r][c] == NONE && color[r][c] >= 0 && color[r][c] < BOMB) normals.add(new int[]{r, c});
        for (int i = 0; i < movesLeft && !normals.isEmpty(); i++) {
            int[] p = normals.remove(rnd.nextInt(normals.size()));
            special[p[0]][p[1]] = rnd.nextBoolean() ? STRIPE_H : STRIPE_V;
        }
        score += movesLeft * 250L;
        boolean[][] forced = new boolean[rows][cols];
        boolean any = false;
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (special[r][c] != NONE) { forced[r][c] = true; any = true; }
        if (!any) return new ArrayList<>();
        return resolve(forced, null);
    }

    private List<Step> resolve(boolean[][] forced, int[] preferred) {
        List<Step> steps = new ArrayList<>();
        int cascade = 1;
        Step s = resolveStep(forced, preferred, cascade);
        while (s != null) {
            steps.add(s);
            cascade++;
            s = resolveStep(null, null, cascade);
            if (cascade > 60) break;
        }
        if (findMove() == null) steps.add(shuffleStep());
        return steps;
    }

    // ------------------------------------------------------------------ matching

    private void swapCells(int r1, int c1, int r2, int c2) {
        int t = color[r1][c1]; color[r1][c1] = color[r2][c2]; color[r2][c2] = t;
        t = special[r1][c1]; special[r1][c1] = special[r2][c2]; special[r2][c2] = t;
    }

    private boolean matchable(int r, int c) {
        return color[r][c] >= 0 && color[r][c] < BOMB;
    }

    private boolean hasMatchAt(int r, int c) {
        if (!matchable(r, c)) return false;
        int col = color[r][c];
        int h = 1;
        for (int x = c - 1; x >= 0 && color[r][x] == col; x--) h++;
        for (int x = c + 1; x < cols && color[r][x] == col; x++) h++;
        if (h >= 3) return true;
        int v = 1;
        for (int y = r - 1; y >= 0 && color[y][c] == col; y--) v++;
        for (int y = r + 1; y < rows && color[y][c] == col; y++) v++;
        return v >= 3;
    }

    /** Returns a possible move {r1,c1,r2,c2}, or null if the board is stuck. */
    public int[] findMove() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (color[r][c] == EMPTY) continue;
                int[][] dirs = {{0, 1}, {1, 0}};
                for (int[] d : dirs) {
                    int r2 = r + d[0], c2 = c + d[1];
                    if (r2 >= rows || c2 >= cols || color[r2][c2] == EMPTY) continue;
                    if (special[r][c] == COLOR_BOMB || special[r2][c2] == COLOR_BOMB) return new int[]{r, c, r2, c2};
                    if (special[r][c] != NONE && special[r2][c2] != NONE) return new int[]{r, c, r2, c2};
                    swapCells(r, c, r2, c2);
                    boolean ok = hasMatchAt(r, c) || hasMatchAt(r2, c2);
                    swapCells(r, c, r2, c2);
                    if (ok) return new int[]{r, c, r2, c2};
                }
            }
        }
        return null;
    }

    private Step resolveStep(boolean[][] forced, int[] preferred, int cascade) {
        int[][] hLen = new int[rows][cols], vLen = new int[rows][cols];
        boolean anyMatch = false;
        for (int r = 0; r < rows; r++) {
            int c = 0;
            while (c < cols) {
                int e = c + 1;
                if (matchable(r, c)) while (e < cols && color[r][e] == color[r][c]) e++;
                if (matchable(r, c) && e - c >= 3) {
                    for (int x = c; x < e; x++) hLen[r][x] = e - c;
                    anyMatch = true;
                }
                c = e;
            }
        }
        for (int c = 0; c < cols; c++) {
            int r = 0;
            while (r < rows) {
                int e = r + 1;
                if (matchable(r, c)) while (e < rows && color[e][c] == color[r][c]) e++;
                if (matchable(r, c) && e - r >= 3) {
                    for (int y = r; y < e; y++) vLen[y][c] = e - r;
                    anyMatch = true;
                }
                r = e;
            }
        }
        if (!anyMatch && forced == null) return null;

        Step step = new Step();
        step.cascade = cascade;
        step.fired.addAll(pendingFx);
        pendingFx.clear();
        step.beforeColor = copy(color);
        step.beforeSpecial = copy(special);
        step.beforeJelly = copy(jelly);
        boolean[][] clear = forced != null ? forced : new boolean[rows][cols];
        boolean[][] keep = new boolean[rows][cols]; // cells turning into new specials
        int points = 0;

        // Group matched cells and decide which special each group makes.
        boolean[][] seen = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (seen[r][c] || (hLen[r][c] == 0 && vLen[r][c] == 0)) continue;
                List<int[]> group = new ArrayList<>();
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{r, c});
                seen[r][c] = true;
                int col = color[r][c];
                while (!q.isEmpty()) {
                    int[] p = q.poll();
                    group.add(p);
                    int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] d : nb) {
                        int y = p[0] + d[0], x = p[1] + d[1];
                        if (y < 0 || x < 0 || y >= rows || x >= cols || seen[y][x]) continue;
                        if (color[y][x] != col || (hLen[y][x] == 0 && vLen[y][x] == 0)) continue;
                        seen[y][x] = true;
                        q.add(new int[]{y, x});
                    }
                }
                int maxH = 0, maxV = 0;
                int[] cross = null;
                for (int[] p : group) {
                    maxH = Math.max(maxH, hLen[p[0]][p[1]]);
                    maxV = Math.max(maxV, vLen[p[0]][p[1]]);
                    if (hLen[p[0]][p[1]] > 0 && vLen[p[0]][p[1]] > 0) cross = p;
                    clear[p[0]][p[1]] = true;
                }
                int type = NONE;
                if (maxH >= 5 || maxV >= 5) type = COLOR_BOMB;
                else if (cross != null) type = WRAPPED;
                else if (maxH == 4) type = STRIPE_V;
                else if (maxV == 4) type = STRIPE_H;
                points += group.size() * 20;
                if (type != NONE) {
                    int[] pos = pickSpot(group, preferred, cross);
                    keep[pos[0]][pos[1]] = true;
                    step.created.add(new int[]{pos[0], pos[1], type});
                    points += type == COLOR_BOMB ? 200 : type == WRAPPED ? 120 : 60;
                }
            }
        }

        // Set off specials caught in the clear (chain reactions).
        boolean[][] fired = new boolean[rows][cols];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (clear[r][c] && special[r][c] != NONE) queue.add(new int[]{r, c});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int r = p[0], c = p[1];
            if (fired[r][c]) continue;
            fired[r][c] = true;
            boolean[][] area = new boolean[rows][cols];
            if (special[r][c] != NONE && special[r][c] != COLOR_BOMB) step.fired.add(new int[]{r, c, special[r][c], 0});
            switch (special[r][c]) {
                case STRIPE_H: markRow(area, r); break;
                case STRIPE_V: markCol(area, c); break;
                case WRAPPED: markArea(area, r, c, 1); break;
                case COLOR_BOMB: {
                    int target = mostCommonColor();
                    step.fired.add(new int[]{r, c, COLOR_BOMB, target});
                    for (int y = 0; y < rows; y++)
                        for (int x = 0; x < cols; x++) if (color[y][x] == target) area[y][x] = true;
                    break;
                }
                default: break;
            }
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (!area[y][x] || keep[y][x] || color[y][x] == EMPTY) continue;
                    if (!clear[y][x]) {
                        clear[y][x] = true;
                        points += 30;
                    }
                    if (special[y][x] != NONE && !fired[y][x]) queue.add(new int[]{y, x});
                }
            }
        }

        // Apply.
        int count = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!clear[r][c] || color[r][c] == EMPTY) {
                    clear[r][c] = false;
                    continue;
                }
                if (jelly[r][c] > 0) {
                    jelly[r][c]--;
                    points += 100;
                }
                if (keep[r][c]) {
                    clear[r][c] = false;
                    continue;
                }
                if (color[r][c] >= 0 && color[r][c] < collected.length) collected[color[r][c]]++;
                color[r][c] = EMPTY;
                special[r][c] = NONE;
                count++;
            }
        }
        for (int[] cr : step.created) {
            int r = cr[0], c = cr[1];
            special[r][c] = cr[2];
            if (cr[2] == COLOR_BOMB) color[r][c] = BOMB;
        }
        step.cleared = clear;
        step.clearedCount = count;
        step.points = points * cascade;
        score += step.points;
        step.afterJelly = copy(jelly);

        applyGravity(step);
        stamp(step);
        return step;
    }

    private int[] pickSpot(List<int[]> group, int[] preferred, int[] cross) {
        if (preferred != null) {
            for (int[] p : group) {
                if ((p[0] == preferred[0] && p[1] == preferred[1]) || (p[0] == preferred[2] && p[1] == preferred[3])) {
                    if (special[p[0]][p[1]] == NONE) return p;
                }
            }
        }
        if (cross != null && special[cross[0]][cross[1]] == NONE) return cross;
        int[] best = group.get(group.size() / 2);
        if (special[best[0]][best[1]] == NONE) return best;
        for (int[] p : group) if (special[p[0]][p[1]] == NONE) return p;
        return best;
    }

    private void applyGravity(Step step) {
        int[][] from = new int[rows][cols];
        int[] cells = new int[rows];
        for (int c = 0; c < cols; c++) {
            int n = 0;
            for (int r = 0; r < rows; r++) if (valid[r][c]) cells[n++] = r;
            int write = n - 1;
            for (int i = n - 1; i >= 0; i--) {
                int r = cells[i];
                if (color[r][c] == EMPTY) continue;
                int wr = cells[write];
                if (wr != r) {
                    color[wr][c] = color[r][c];
                    special[wr][c] = special[r][c];
                    color[r][c] = EMPTY;
                    special[r][c] = NONE;
                }
                from[wr][c] = r;
                write--;
            }
            int spawn = -1;
            for (int i = write; i >= 0; i--) {
                int r = cells[i];
                color[r][c] = rnd.nextInt(numColors);
                special[r][c] = NONE;
                from[r][c] = (n > 0 ? cells[0] : 0) + spawn--;
            }
            for (int r = 0; r < rows; r++) if (!valid[r][c]) from[r][c] = r;
        }
        step.afterColor = copy(color);
        step.afterSpecial = copy(special);
        step.fromRow = from;
    }

    private Step shuffleStep() {
        Step st = new Step();
        st.shuffle = true;
        st.beforeColor = copy(color);
        st.beforeSpecial = copy(special);
        st.beforeJelly = copy(jelly);
        st.cleared = new boolean[rows][cols];
        List<Integer> pool = new ArrayList<>();
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (special[r][c] == NONE && color[r][c] >= 0 && color[r][c] < BOMB) pool.add(color[r][c]);
        boolean ok = false;
        for (int attempt = 0; attempt < 300 && !ok; attempt++) {
            java.util.Collections.shuffle(pool, rnd);
            int i = 0;
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                    if (special[r][c] == NONE && color[r][c] >= 0 && color[r][c] < BOMB) color[r][c] = pool.get(i++);
            ok = !anyMatchOnBoard() && findMove() != null;
        }
        if (!ok) {
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) color[r][c] = EMPTY;
            fillFresh();
        }
        st.afterColor = copy(color);
        st.afterSpecial = copy(special);
        st.afterJelly = copy(jelly);
        int[][] from = new int[rows][cols];
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) from[r][c] = r;
        st.fromRow = from;
        stamp(st);
        return st;
    }

    private boolean anyMatchOnBoard() {
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (hasMatchAt(r, c)) return true;
        return false;
    }

    private int mostCommonColor() {
        int[] n = new int[numColors];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                if (color[r][c] >= 0 && color[r][c] < numColors) n[color[r][c]]++;
        int best = 0;
        for (int i = 1; i < numColors; i++) if (n[i] > n[best]) best = i;
        return best;
    }

    private void markRow(boolean[][] a, int r) {
        if (r < 0 || r >= rows) return;
        for (int c = 0; c < cols; c++) a[r][c] = true;
    }

    private void markCol(boolean[][] a, int c) {
        if (c < 0 || c >= cols) return;
        for (int r = 0; r < rows; r++) a[r][c] = true;
    }

    private void markArea(boolean[][] a, int r0, int c0, int radius) {
        for (int r = r0 - radius; r <= r0 + radius; r++)
            for (int c = c0 - radius; c <= c0 + radius; c++)
                if (r >= 0 && c >= 0 && r < rows && c < cols) a[r][c] = true;
    }

    static int[][] copy(int[][] a) {
        int[][] b = new int[a.length][];
        for (int i = 0; i < a.length; i++) b[i] = a[i].clone();
        return b;
    }
}
