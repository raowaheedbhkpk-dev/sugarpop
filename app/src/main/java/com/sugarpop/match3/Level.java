package com.sugarpop.match3;

import java.util.Random;

/** Level goals and difficulty, generated from the level number (same number = same level). */
public final class Level {

    public static final int GOAL_SCORE = 0, GOAL_JELLY = 1, GOAL_COLLECT = 2;
    public static final int ROWS = 8, COLS = 8;

    public final int number;
    public final int goalType;
    public final int moves;
    public final int colors;
    public final long targetScore;      // 1 star
    public final long star2, star3;
    public final int[][] jelly;         // starting jelly layers per cell
    public final int collectColor;      // for GOAL_COLLECT
    public final int collectCount;
    public final long seed;
    /** Board shape: false = hole. */
    public final boolean[][] shape;

    /** Board shapes ('#' = cell, '.' = hole). */
    private static final String[][] SHAPES = {
            {"########", "########", "########", "########", "########", "########", "########", "########"},
            {".######.", "########", "########", "########", "########", "########", "########", ".######."},
            {"..####..", ".######.", "########", "########", "########", "########", ".######.", "..####.."},
            {"..####..", "..####..", "########", "########", "########", "########", "..####..", "..####.."},
            {"########", "########", ".######.", "..####..", "..####..", ".######.", "########", "########"},
            {"########", "########", "########", "###..###", "###..###", "########", "########", "########"},
            {"###..###", "###..###", "########", "########", "########", "########", "########", "########"},
            {"########", "########", ".######.", ".######.", ".######.", ".######.", "########", "########"},
    };

    private Level(int number, int goalType, int moves, int colors, long target, int[][] jelly,
                  int collectColor, int collectCount, long seed, boolean[][] shape) {
        this.number = number;
        this.goalType = goalType;
        this.moves = moves;
        this.colors = colors;
        this.targetScore = target;
        this.star2 = target * 3 / 2;
        this.star3 = target * 2;
        this.jelly = jelly;
        this.collectColor = collectColor;
        this.collectCount = collectCount;
        this.seed = seed;
        this.shape = shape;
    }

    /** Number of levels with hand-calibrated move counts; later levels reuse the last 100. */
    public static final int CALIBRATED = LevelTable.MOVES.length - 1;

    public static boolean isHard(int n) {
        return n >= 20 && n % 5 == 0;
    }

    public static Level get(int n) {
        return build(n, -1);
    }

    /** Builds level n. movesOverride > 0 is used by the calibration tool. */
    public static Level build(int n, int movesOverride) {
        long seed = n * 2654435761L + 97;
        Random rnd = new Random(seed);
        int tier = Math.min(n / 10, 8);
        int capped = Math.min(n, 200);

        int type;
        if (n <= 2) type = GOAL_SCORE;
        else if (n % 3 == 0) type = GOAL_JELLY;
        else if (n % 3 == 1) type = GOAL_COLLECT;
        else type = GOAL_SCORE;

        int colors = 5;
        if (n <= 2) colors = 4;
        else if (isHard(n) && type != GOAL_JELLY) colors = 6;

        int shapeIndex = n <= 5 ? 0 : new Random(seed ^ 0x5DEECE66DL).nextInt(SHAPES.length);
        boolean[][] shape = new boolean[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) shape[r][c] = SHAPES[shapeIndex][r].charAt(c) == '#';

        long target;
        int[][] jelly = new int[ROWS][COLS];
        int collectColor = -1, collectCount = 0;
        switch (type) {
            case GOAL_JELLY:
                makeJelly(jelly, rnd, n, tier);
                for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) if (!shape[r][c]) jelly[r][c] = 0;
                target = 2500 + capped * 25L;
                break;
            case GOAL_COLLECT:
                collectColor = rnd.nextInt(colors);
                collectCount = 4 * (6 + Math.min(n, 120) / 12);
                if (colors == 6) collectCount = collectCount * 3 / 4;
                target = 2000 + capped * 20L;
                break;
            default:
                target = 1000L * (8 + Math.min(n, 120) / 10);
                if (colors == 6) target = target * 2 / 3;
                break;
        }
        if (n == 1) target = 2500;

        int moves;
        if (movesOverride > 0) moves = movesOverride;
        else {
            int idx = n <= CALIBRATED ? n : CALIBRATED - 99 + ((n - CALIBRATED - 1) % 100);
            moves = LevelTable.MOVES[idx] + 2; // a little slack on top of the calibrated value
        }
        return new Level(n, type, moves, colors, target, jelly, collectColor, collectCount, seed, shape);
    }

    private static void makeJelly(int[][] j, Random rnd, int n, int tier) {
        int pattern = n < 20 ? (rnd.nextBoolean() ? 0 : 4) : rnd.nextInt(5);
        int layers2 = n >= 30 ? 1 + rnd.nextInt(2) : 1;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                boolean on;
                switch (pattern) {
                    case 0: on = r >= 2 && r <= 5 && c >= 1 && c <= 6; break;               // center block
                    case 1: on = r == 0 || r == 7 || c == 0 || c == 7; break;               // frame
                    case 2: on = Math.abs(r - 3.5) + Math.abs(c - 3.5) <= 4; break;         // diamond
                    case 3: on = r == c || r == COLS - 1 - c || r == c + 1 || r + 1 == c; break; // X
                    default: on = r >= 5; break;                                             // bottom rows
                }
                if (tier >= 4 && rnd.nextInt(5) == 0) on = true;
                if (on) j[r][c] = (layers2 == 2 && (r + c) % 2 == 0) ? 2 : 1;
            }
        }
    }

    public int totalJelly() {
        int s = 0;
        for (int[] row : jelly) for (int v : row) s += v;
        return s;
    }

    public Board newBoard() {
        return newBoard(seed);
    }

    /** A board for this level with a different random stream (used by the calibration bot). */
    public Board newBoard(long boardSeed) {
        Board b = new Board(ROWS, COLS, colors, boardSeed, shape);
        for (int r = 0; r < ROWS; r++) System.arraycopy(jelly[r], 0, b.jelly[r], 0, COLS);
        return b;
    }

    /** True when the level goal (not counting moves) is met. */
    public boolean goalMet(Board b) {
        switch (goalType) {
            case GOAL_JELLY: return b.jellyLeft() == 0;
            case GOAL_COLLECT: return b.collected[collectColor] >= collectCount;
            default: return b.score >= targetScore;
        }
    }

    public int stars(long score) {
        if (score >= star3) return 3;
        if (score >= star2) return 2;
        return 1;
    }
}
