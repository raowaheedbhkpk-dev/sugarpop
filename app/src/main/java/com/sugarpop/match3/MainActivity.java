package com.sugarpop.match3;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity implements BoardView.Listener, MapView.Listener {

    // ---- economy
    private static final int START_COINS = 150;
    private static final int START_BOOSTERS = 3;
    private static final int EXTRA_MOVES = 5;
    private static final int EXTRA_MOVES_COST = 60;
    private static final int WIN_AD_MULTIPLIER = 3;
    private static final int REFILL_COST = 100;
    private static final int AD_COINS = 30;
    private static final String[] BOOSTER_KEY = {"inv_hammer", "inv_swap", "inv_shuffle"};
    private static final String[] BOOSTER_NAME = {"Hammer", "Free Swap", "Shuffle"};
    private static final String[] BOOSTER_DESC = {"Smash any one candy.", "Swap two candies, even without a match. Doesn't use a move.", "Mix up every candy on the board."};
    private static final int[] BOOSTER_ICON = {R.drawable.boost_hammer, R.drawable.boost_swap, R.drawable.boost_shuffle};
    private static final int[] BOOSTER_PRICE = {120, 120, 90};
    private static final int HAMMER = 0, SWAP = 1, SHUFFLE = 2;

    // ---- lives
    private static final int MAX_LIVES = 5;
    private static final long LIFE_MS = 20 * 60 * 1000L;

    // ---- daily rewards: {coins, booster index or -1, booster amount}
    private static final int[][] DAILY = {{20, -1, 0}, {0, HAMMER, 1}, {40, -1, 0}, {0, SWAP, 1}, {60, -1, 0}, {0, SHUFFLE, 1}, {100, HAMMER, 2}};

    private static final int[][] WORLD_BG = {
            {0xFFFF5E3A, 0xFFFFD23F}, {0xFF2E6BE6, 0xFF4FD1F0}, {0xFFE2457A, 0xFFFFA26B},
            {0xFF7A3FD1, 0xFFE070C8}, {0xFF1FA67A, 0xFF9BE15D}, {0xFF6B3E26, 0xFFE0A060},
    };

    private SharedPreferences prefs;
    private AdsManager ads;
    private Sfx sfx;
    private MediaPlayer music;
    private boolean musicOn, vibrateOn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NumberFormat nf = NumberFormat.getIntegerInstance();

    private FrameLayout content;
    private View screenMap, screenGame, splash;
    private MapView mapView;
    private ScrollView mapScroll;
    private BoardView boardView;
    private ScoreBar scoreBar;
    private TextView livesText, lifeTimer, mapCoins, scoreText, levelTitle, dailyBadge;
    private TextView[] badges = new TextView[3];
    private ImageView goalIcon;
    private Ui.OutlineText movesText, goalText;

    private final List<GameDialog> dialogs = new ArrayList<>();
    private OnBackInvokedCallback backCallback;

    private int unlocked, coins;
    private Level level;
    private Board board;
    private int movesLeft;
    private boolean levelOver, movedThisAttempt, extraMovesAdUsed;
    private long lastHudScore = -1;

    // ================================================================== lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdge();
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("sugarpop", MODE_PRIVATE);
        if (!prefs.contains("coins")) {
            SharedPreferences.Editor e = prefs.edit().putInt("coins", START_COINS);
            for (String k : BOOSTER_KEY) e.putInt(k, START_BOOSTERS);
            e.apply();
        }
        unlocked = prefs.getInt("unlocked", 1);
        coins = prefs.getInt("coins", START_COINS);
        sfx = new Sfx(this);
        sfx.enabled = prefs.getBoolean("sound", true);
        musicOn = prefs.getBoolean("music", true);
        vibrateOn = prefs.getBoolean("vibrate", true);

        content = findViewById(R.id.content);
        screenMap = findViewById(R.id.screenMap);
        screenGame = findViewById(R.id.screenGame);
        splash = findViewById(R.id.splash);
        mapView = findViewById(R.id.mapView);
        mapScroll = findViewById(R.id.mapScroll);
        boardView = findViewById(R.id.boardView);
        scoreBar = findViewById(R.id.scoreBar);
        livesText = findViewById(R.id.livesText);
        lifeTimer = findViewById(R.id.lifeTimer);
        mapCoins = findViewById(R.id.mapCoins);
        scoreText = findViewById(R.id.scoreText);
        levelTitle = findViewById(R.id.levelTitle);
        dailyBadge = findViewById(R.id.dailyBadge);
        goalIcon = findViewById(R.id.goalIcon);
        badges[HAMMER] = findViewById(R.id.badgeHammer);
        badges[SWAP] = findViewById(R.id.badgeSwap);
        badges[SHUFFLE] = findViewById(R.id.badgeShuffle);
        Ui.applyFont(findViewById(R.id.root));

        movesText = new Ui.OutlineText(this, 36, Color.WHITE, 0xFF0B6E99);
        ((FrameLayout) findViewById(R.id.movesHolder)).addView(movesText);
        goalText = new Ui.OutlineText(this, 24, Color.WHITE, 0xFF0B6E99);
        ((FrameLayout) findViewById(R.id.goalHolder)).addView(goalText);

        mapView.setListener(this);
        boardView.setListener(this);
        boardView.haptics = vibrateOn;

        click(R.id.btnSettings, v -> showSettings());
        click(R.id.livesBox, v -> { if (getLives() < MAX_LIVES) showNoLives(0); });
        click(R.id.coinsBox, v -> showShop());
        click(R.id.navMap, v -> scrollMapToCurrent(true));
        click(R.id.navDaily, v -> showDaily());
        click(R.id.navShop, v -> showShop());
        click(R.id.btnPause, v -> showPause());
        click(R.id.btnHammer, v -> onBooster(HAMMER));
        click(R.id.btnSwap, v -> onBooster(SWAP));
        click(R.id.btnShuffle, v -> onBooster(SHUFFLE));
        splash.setOnClickListener(v -> hideSplash());

        ads = new AdsManager(this, findViewById(R.id.adContainer));
        ads.start();

        showMap();
        tickLives();
        startSplash();
        updateBack();
    }

    private void click(int id, View.OnClickListener l) {
        View v = findViewById(id);
        Ui.pressable(v);
        v.setOnClickListener(l);
    }

    @Override
    protected void onPause() {
        ads.pause();
        if (music != null && music.isPlaying()) music.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ads.resume();
        startMusic();
        if (screenMap.getVisibility() == View.VISIBLE) updateLives();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        ads.destroy();
        sfx.release();
        if (music != null) {
            music.release();
            music = null;
        }
        super.onDestroy();
    }

    // ================================================================== window

    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onContentChanged() {
        super.onContentChanged();
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft();
                t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight();
                b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return insets;
        });
        root.requestApplyInsets();
    }

    // ================================================================== back button

    /** Back goes: close dialog -> pause menu in game -> leave app from map. */
    private boolean handleBack() {
        if (splash.getVisibility() == View.VISIBLE) {
            hideSplash();
            return true;
        }
        for (int i = dialogs.size() - 1; i >= 0; i--) {
            GameDialog d = dialogs.get(i);
            if (d.isShowing()) {
                d.close();
                return true;
            }
        }
        if (screenGame.getVisibility() == View.VISIBLE) {
            showPause();
            return true;
        }
        return false;
    }

    private void updateBack() {
        for (java.util.Iterator<GameDialog> it = dialogs.iterator(); it.hasNext(); ) {
            if (!it.next().isShowing()) it.remove();
        }
        if (Build.VERSION.SDK_INT < 33) return;
        boolean need = splash.getVisibility() == View.VISIBLE || !dialogs.isEmpty()
                || screenGame.getVisibility() == View.VISIBLE;
        OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (need && backCallback == null) {
            backCallback = () -> {
                handleBack();
                updateBack();
            };
            d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        } else if (!need && backCallback != null) {
            d.unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (!handleBack()) super.onBackPressed();
    }

    private GameDialog dialog(String title, int color, boolean closable) {
        GameDialog d = GameDialog.show(content, title, color, closable);
        d.afterDismiss(() -> handler.post(this::updateBack));
        dialogs.add(d);
        updateBack();
        return d;
    }

    private void dismiss(GameDialog d) {
        d.dismiss();
        handler.post(this::updateBack);
    }

    // ================================================================== splash + music

    private void startSplash() {
        View m = findViewById(R.id.splashMascot);
        ObjectAnimator bob = ObjectAnimator.ofFloat(m, "translationY", 0f, -Ui.dp(this, 18));
        bob.setDuration(600);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.setRepeatMode(ValueAnimator.REVERSE);
        bob.start();
        View logo = findViewById(R.id.splashLogo);
        logo.setScaleX(0.3f);
        logo.setScaleY(0.3f);
        logo.animate().scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(new OvershootInterpolator(2f)).start();
        View tap = findViewById(R.id.splashTap);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(tap, "alpha", 1f, 0.35f);
        pulse.setDuration(700);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.start();
    }

    private void hideSplash() {
        if (splash.getVisibility() != View.VISIBLE) return;
        splash.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            splash.setVisibility(View.GONE);
            updateBack();
            scrollMapToCurrent(false);
            if (canClaimDaily()) handler.postDelayed(this::showDaily, 400);
        }).start();
    }

    private void startMusic() {
        if (!musicOn) return;
        if (music == null) {
            music = MediaPlayer.create(this, R.raw.music);
            if (music == null) return;
            music.setLooping(true);
            music.setVolume(0.45f, 0.45f);
        }
        if (!music.isPlaying()) music.start();
    }

    private void setMusic(boolean on) {
        musicOn = on;
        prefs.edit().putBoolean("music", on).apply();
        if (on) startMusic();
        else if (music != null && music.isPlaying()) music.pause();
    }

    // ================================================================== map

    private void showMap() {
        screenGame.setVisibility(View.GONE);
        screenMap.setVisibility(View.VISIBLE);
        int[] stars = new int[unlocked + 120];
        for (int i = 1; i < stars.length; i++) stars[i] = prefs.getInt("s" + i, 0);
        mapView.setProgress(unlocked, stars);
        updateCoins();
        updateLives();
        dailyBadge.setVisibility(canClaimDaily() ? View.VISIBLE : View.GONE);
        scrollMapToCurrent(false);
        updateBack();
    }

    private void scrollMapToCurrent(boolean smooth) {
        mapScroll.post(() -> {
            int y = (int) (mapView.nodeY(unlocked) - mapScroll.getHeight() * 0.55f);
            if (smooth) mapScroll.smoothScrollTo(0, Math.max(0, y));
            else mapScroll.scrollTo(0, Math.max(0, y));
        });
    }

    @Override
    public void onLevelTapped(int n) {
        showLevelStart(n);
    }

    private void showLevelStart(int n) {
        Level lv = Level.get(n);
        GameDialog d = dialog("Level " + n, Ui.PINK, true);
        LinearLayout row = d.row();
        int best = prefs.getInt("s" + n, 0);
        for (int i = 0; i < 3; i++) row.addView(starView(i < best, i == 1 ? 58 : 46));
        LinearLayout goal = d.row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(goalIconRes(lv));
        goal.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        TextView gt = new TextView(this);
        gt.setText(goalDescription(lv));
        gt.setTextSize(22);
        gt.setTextColor(0xFF6A1B4D);
        gt.setTypeface(Ui.font(this));
        gt.setPadding(Ui.dp(this, 10), 0, 0, 0);
        goal.addView(gt);
        d.text(lv.moves + " moves", 18, 0xFF2C7A9B);
        d.button("Play", Ui.GREEN, v -> {
            dismiss(d);
            if (getLives() <= 0) showNoLives(n);
            else startLevel(n);
        });
    }

    private ImageView starView(boolean gold, float sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(gold ? R.drawable.star_gold : R.drawable.star_empty);
        int s = Ui.dp(this, sizeDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 2);
        iv.setLayoutParams(lp);
        return iv;
    }

    private int goalIconRes(Level lv) {
        switch (lv.goalType) {
            case Level.GOAL_COLLECT: return BoardView.CANDY[lv.collectColor];
            case Level.GOAL_JELLY: return R.drawable.node_done;
            default: return R.drawable.star_gold;
        }
    }

    private String goalDescription(Level lv) {
        switch (lv.goalType) {
            case Level.GOAL_JELLY: return "Clear all the jelly";
            case Level.GOAL_COLLECT: return "Collect " + lv.collectCount;
            default: return "Score " + nf.format(lv.targetScore);
        }
    }

    // ================================================================== level flow

    private void startLevel(int n) {
        level = Level.get(n);
        board = level.newBoard();
        movesLeft = level.moves;
        levelOver = false;
        movedThisAttempt = false;
        extraMovesAdUsed = false;
        lastHudScore = -1;
        int[] bg = WORLD_BG[((n - 1) / 20) % WORLD_BG.length];
        screenGame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bg));
        boardView.setBoard(board);
        screenMap.setVisibility(View.GONE);
        screenGame.setVisibility(View.VISIBLE);
        levelTitle.setText("Level " + n);
        goalIcon.setImageResource(goalIconRes(level));
        scoreBar.setThresholds(level.targetScore, level.star2, level.star3);
        updateHud(0, new int[8], level.totalJelly());
        updateBadges();
        updateBack();
        boardView.setInputLocked(true);
        handler.postDelayed(() -> {
            if (board != null && !levelOver) {
                boardView.setInputLocked(false);
                boardView.showPraise(level.goalType == Level.GOAL_JELLY ? "Clear the jelly!"
                        : level.goalType == Level.GOAL_COLLECT ? "Collect them!" : "Go for it!");
            }
        }, 350);
    }

    private void updateHud(long score, int[] collected, int jellyLeft) {
        movesText.setText(String.valueOf(Math.max(0, movesLeft)));
        switch (level.goalType) {
            case Level.GOAL_JELLY:
                goalText.setText(String.valueOf(jellyLeft));
                break;
            case Level.GOAL_COLLECT:
                goalText.setText(Math.min(collected[level.collectColor], level.collectCount) + "/" + level.collectCount);
                break;
            default:
                goalText.setText(nf.format(level.targetScore));
                break;
        }
        if (score != lastHudScore) {
            lastHudScore = score;
            scoreText.setText("Score: " + nf.format(score));
            scoreBar.setScore(score);
        }
    }

    @Override
    public void onMoveUsed() {
        movesLeft--;
        movedThisAttempt = true;
        movesText.setText(String.valueOf(Math.max(0, movesLeft)));
        if (movesLeft == 5) boardView.showPraise("5 moves left!");
        sfx.swap();
    }

    @Override
    public void onSwapRejected() {
        sfx.bad();
    }

    @Override
    public void onHammerUsed() {
        movedThisAttempt = true;
        useBooster(HAMMER);
        sfx.special();
    }

    @Override
    public void onFreeSwapUsed() {
        movedThisAttempt = true;
        useBooster(SWAP);
        sfx.swap();
    }

    @Override
    public void onStepPopped(Board.Step step) {
        if (level == null) return;
        updateHud(step.scoreAfter, step.collectedAfter, step.jellyLeftAfter);
        if (step.shuffle) return;
        if (!step.fired.isEmpty() || !step.created.isEmpty()) sfx.special();
        else sfx.pop(step.cascade);
    }

    @Override
    public void onSettled() {
        if (levelOver || board == null) return;
        updateHud(board.score, board.collected, board.jellyLeft());
        if (level.goalMet(board)) {
            levelOver = true;
            boardView.setInputLocked(true);
            boardView.setHammerArmed(false);
            boardView.setFreeSwapArmed(false);
            int bonus = movesLeft;
            movesLeft = 0;
            boardView.showPraise("Sugar Bonus!");
            handler.postDelayed(() -> {
                if (board != null) boardView.playExternal(board.finale(bonus), this::showWin);
            }, 700);
        } else if (movesLeft <= 0) {
            levelOver = true;
            boardView.setInputLocked(true);
            showFail();
        }
    }

    private void showWin() {
        if (board == null) return;
        updateHud(board.score, board.collected, board.jellyLeft());
        sfx.win();
        int n = level.number;
        int stars = level.stars(board.score);
        if (stars > prefs.getInt("s" + n, 0)) prefs.edit().putInt("s" + n, stars).apply();
        if (n >= unlocked) {
            unlocked = n + 1;
            prefs.edit().putInt("unlocked", unlocked).apply();
        }
        final int reward = 10 + stars * 5;
        GameDialog d = dialog("Level " + n, Ui.TEAL, true);
        LinearLayout row = d.row();
        ImageView[] sv = new ImageView[3];
        for (int i = 0; i < 3; i++) {
            sv[i] = starView(false, i == 1 ? 82 : 66);
            if (i != 1) ((LinearLayout.LayoutParams) sv[i].getLayoutParams()).topMargin = Ui.dp(this, 18);
            row.addView(sv[i]);
        }
        for (int i = 0; i < stars; i++) {
            final ImageView s = sv[i];
            final int idx = i;
            handler.postDelayed(() -> {
                s.setImageResource(R.drawable.star_gold);
                s.setScaleX(0.2f);
                s.setScaleY(0.2f);
                s.animate().scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(new OvershootInterpolator(3f)).start();
                sfx.pop(2 + idx * 2);
            }, 350L + i * 300L);
        }
        ImageView m = d.image(R.drawable.mascot, 120);
        ObjectAnimator bob = ObjectAnimator.ofFloat(m, "translationY", 0f, -Ui.dp(this, 10));
        bob.setDuration(450);
        bob.setRepeatCount(8);
        bob.setRepeatMode(ValueAnimator.REVERSE);
        bob.start();
        d.scoreBox("Score:", nf.format(board.score), 0xFF2E7D6B);
        d.text("+" + reward + " coins", 20, 0xFFE08A00);
        d.button("Next", Ui.GREEN, v -> {
            dismiss(d);
            addCoins(reward);
            backToMap(true);
        });
        d.button("▶ Watch ad: +" + reward * WIN_AD_MULTIPLIER + " coins", Ui.ORANGE, v -> ads.showRewarded(() -> {
            dismiss(d);
            addCoins(reward * WIN_AD_MULTIPLIER);
            backToMap(false);
        }, this::adNotReady));
        d.onClose(() -> {
            addCoins(reward);
            backToMap(true);
        });
    }

    private void showFail() {
        sfx.fail();
        GameDialog d = dialog("Out of moves!", Ui.PURPLE, true);
        LinearLayout row = d.row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(goalIconRes(level));
        row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        TextView t = new TextView(this);
        t.setTypeface(Ui.font(this));
        t.setTextSize(22);
        t.setTextColor(0xFF5B2A86);
        t.setPadding(Ui.dp(this, 10), 0, 0, 0);
        switch (level.goalType) {
            case Level.GOAL_JELLY: t.setText(board.jellyLeft() + " jelly left"); break;
            case Level.GOAL_COLLECT: t.setText((level.collectCount - board.collected[level.collectColor]) + " more to go"); break;
            default: t.setText(nf.format(level.targetScore - board.score) + " points short"); break;
        }
        row.addView(t);
        d.text("Keep going with 5 extra moves?", 17, 0xFF555555);
        if (!extraMovesAdUsed) {
            d.button("▶ +5 moves (free)", Ui.GREEN, v -> ads.showRewarded(() -> {
                dismiss(d);
                grantExtraMoves(true);
            }, this::adNotReady));
        }
        d.button("+5 moves: " + EXTRA_MOVES_COST + " coins", Ui.ORANGE, v -> {
            if (coins >= EXTRA_MOVES_COST) {
                addCoins(-EXTRA_MOVES_COST);
                dismiss(d);
                grantExtraMoves(false);
            } else {
                toast("Not enough coins. Watch an ad instead!");
            }
        });
        d.onClose(() -> {
            loseLife();
            backToMap(true);
        });
    }

    private void grantExtraMoves(boolean fromAd) {
        if (fromAd) extraMovesAdUsed = true;
        movesLeft += EXTRA_MOVES;
        levelOver = false;
        boardView.setInputLocked(false);
        movesText.setText(String.valueOf(movesLeft));
        boardView.showPraise("+5 moves!");
    }

    private void showPause() {
        if (screenGame.getVisibility() != View.VISIBLE || levelOver) return;
        GameDialog d = dialog("Paused", Ui.BLUE, true);
        d.body.addView(settingsRow(false));
        d.button("Continue", Ui.GREEN, v -> dismiss(d));
        d.button("Quit level", Ui.PINK, v -> {
            dismiss(d);
            if (!movedThisAttempt) {
                backToMap(false);
                return;
            }
            GameDialog q = dialog("Quit level?", Ui.PINK, true);
            q.image(R.drawable.ui_heart, 70);
            q.text("You will lose a life.", 20, 0xFF6A1B4D);
            q.button("Keep playing", Ui.GREEN, x -> dismiss(q));
            q.button("Quit", Ui.PINK, x -> {
                dismiss(q);
                loseLife();
                backToMap(true);
            });
        });
    }

    private void backToMap(boolean allowInterstitial) {
        int finished = level != null ? level.number : 0;
        level = null;
        board = null;
        boardView.setBoard(null);
        tickLives();
        if (allowInterstitial) ads.onLevelFinished(finished, this::showMap);
        else showMap();
    }

    // ================================================================== boosters

    private int inv(int b) {
        return prefs.getInt(BOOSTER_KEY[b], 0);
    }

    private void addBooster(int b, int n) {
        prefs.edit().putInt(BOOSTER_KEY[b], Math.max(0, inv(b) + n)).apply();
        updateBadges();
    }

    private void useBooster(int b) {
        addBooster(b, -1);
    }

    private void updateBadges() {
        for (int i = 0; i < 3; i++) {
            int n = inv(i);
            badges[i].setText(n > 0 ? String.valueOf(n) : "+");
        }
    }

    private boolean canUseBooster() {
        return level != null && !levelOver && boardView.isIdle();
    }

    private void onBooster(int b) {
        if (!canUseBooster()) return;
        if (b == HAMMER && boardView.isHammerArmed()) { boardView.setHammerArmed(false); return; }
        if (b == SWAP && boardView.isFreeSwapArmed()) { boardView.setFreeSwapArmed(false); return; }
        if (inv(b) <= 0) {
            showGetBooster(b);
            return;
        }
        switch (b) {
            case HAMMER:
                boardView.setHammerArmed(true);
                toast("Tap a candy to smash it");
                break;
            case SWAP:
                boardView.setFreeSwapArmed(true);
                toast("Swipe any two candies");
                break;
            default:
                useBooster(SHUFFLE);
                movedThisAttempt = true;
                boardView.playExternal(board.shuffleBoard(), null);
                break;
        }
    }

    private void showGetBooster(int b) {
        GameDialog d = dialog(BOOSTER_NAME[b], Ui.GREEN, true);
        d.image(BOOSTER_ICON[b], 100);
        d.text(BOOSTER_DESC[b], 17, 0xFF2E5E3E);
        d.button("▶ Watch ad: +1", Ui.GREEN, v -> ads.showRewarded(() -> {
            dismiss(d);
            addBooster(b, 1);
        }, this::adNotReady));
        d.button("3 for " + BOOSTER_PRICE[b] + " coins", Ui.ORANGE, v -> {
            if (coins >= BOOSTER_PRICE[b]) {
                addCoins(-BOOSTER_PRICE[b]);
                addBooster(b, 3);
                dismiss(d);
            } else {
                toast("Not enough coins");
            }
        });
    }

    // ================================================================== shop

    private void showShop() {
        GameDialog d = dialog("Shop", Ui.ORANGE, true);
        TextView bal = d.text("You have " + coins + " coins", 18, 0xFF9E5A00);
        for (int b = 0; b < 3; b++) {
            final int bi = b;
            LinearLayout row = shopRow(d, BOOSTER_ICON[b], "3 " + BOOSTER_NAME[b] + "s", BOOSTER_PRICE[b] + "", () -> {
                if (coins >= BOOSTER_PRICE[bi]) {
                    addCoins(-BOOSTER_PRICE[bi]);
                    addBooster(bi, 3);
                    bal.setText("You have " + coins + " coins");
                    sfx.special();
                } else toast("Not enough coins");
            });
            d.body.addView(row);
        }
        d.body.addView(shopRow(d, R.drawable.ui_heart, "Refill lives", REFILL_COST + "", () -> {
            if (getLives() >= MAX_LIVES) toast("Your lives are full");
            else if (coins >= REFILL_COST) {
                addCoins(-REFILL_COST);
                prefs.edit().putInt("lives", MAX_LIVES).putLong("lifeTime", 0).apply();
                updateLives();
                bal.setText("You have " + coins + " coins");
                sfx.special();
            } else toast("Not enough coins");
        }));
        d.button("▶ Watch ad: +" + AD_COINS + " coins", Ui.GREEN, v -> ads.showRewarded(() -> {
            addCoins(AD_COINS);
            bal.setText("You have " + coins + " coins");
        }, this::adNotReady));
    }

    private LinearLayout shopRow(GameDialog d, int icon, String label, String price, Runnable buy) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.rounded(Color.WHITE, Ui.dp(this, 16), Ui.dp(this, 1), 0x22000000));
        row.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(this, 8);
        row.setLayoutParams(rlp);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTypeface(Ui.font(this));
        t.setTextSize(18);
        t.setTextColor(0xFF6A3A10);
        t.setPadding(Ui.dp(this, 8), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView coin = new ImageView(this);
        coin.setImageResource(R.drawable.ui_coin);
        row.addView(coin, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        Ui.OutlineText b = Ui.button(this, price, Ui.GREEN, 18);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 10));
        b.setOnClickListener(v -> buy.run());
        row.addView(b);
        return row;
    }

    // ================================================================== daily reward

    private long today() {
        long now = System.currentTimeMillis();
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L;
    }

    private boolean canClaimDaily() {
        return today() > prefs.getLong("daily_last", 0);
    }

    private void showDaily() {
        int streak = prefs.getInt("daily_streak", 0);
        long last = prefs.getLong("daily_last", 0);
        if (today() - last > 1) streak = 0;
        final int dayIdx = streak;
        boolean can = canClaimDaily();
        GameDialog d = dialog("Daily Treats", Ui.PINK, true);
        d.text(can ? "Come back every day for bigger treats!" : "Come back tomorrow for more!", 16, 0xFF7A2E57);
        LinearLayout row1 = d.row(), row2 = d.row();
        for (int i = 0; i < 7; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            boolean claimed = i < dayIdx || (!can && i == dayIdx - 1);
            boolean todayCell = can && i == dayIdx;
            int bg = todayCell ? 0xFFFFE07A : claimed ? 0xFFD7F5DC : Color.WHITE;
            cell.setBackground(Ui.rounded(bg, Ui.dp(this, 12), Ui.dp(this, todayCell ? 3 : 1), todayCell ? 0xFFFF9A1F : 0x22000000));
            cell.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            TextView day = new TextView(this);
            day.setText("Day " + (i + 1));
            day.setTypeface(Ui.font(this));
            day.setTextSize(13);
            day.setTextColor(0xFF7A2E57);
            cell.addView(day);
            int[] rw = DAILY[i];
            ImageView iv = new ImageView(this);
            iv.setImageResource(rw[1] >= 0 ? BOOSTER_ICON[rw[1]] : R.drawable.ui_coin);
            cell.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34)));
            TextView amt = new TextView(this);
            amt.setText(claimed ? "✓" : rewardLabel(rw));
            amt.setTypeface(Ui.font(this));
            amt.setTextSize(12);
            amt.setTextColor(0xFF444444);
            amt.setGravity(Gravity.CENTER);
            cell.addView(amt);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = lp.rightMargin = Ui.dp(this, 3);
            (i < 4 ? row1 : row2).addView(cell, lp);
        }
        if (can) {
            d.button("Claim", Ui.GREEN, v -> {
                claimDaily(dayIdx, 1);
                dismiss(d);
            });
            d.button("▶ Watch ad: claim x2", Ui.ORANGE, v -> ads.showRewarded(() -> {
                claimDaily(dayIdx, 2);
                dismiss(d);
            }, this::adNotReady));
        }
    }

    private String rewardLabel(int[] rw) {
        StringBuilder s = new StringBuilder();
        if (rw[0] > 0) s.append(rw[0]).append(" coins");
        if (rw[1] >= 0) {
            if (s.length() > 0) s.append("\n+");
            s.append(rw[2]).append(" ").append(BOOSTER_NAME[rw[1]]);
        }
        return s.toString();
    }

    private void claimDaily(int dayIdx, int mult) {
        if (!canClaimDaily()) return;
        int[] rw = DAILY[dayIdx];
        if (rw[0] > 0) addCoins(rw[0] * mult);
        if (rw[1] >= 0) addBooster(rw[1], rw[2] * mult);
        prefs.edit().putLong("daily_last", today()).putInt("daily_streak", (dayIdx + 1) % 7).apply();
        dailyBadge.setVisibility(View.GONE);
        sfx.win();
        toast("Got " + rewardLabel(rw).replace("\n", " ") + (mult > 1 ? " x2!" : "!"));
    }

    // ================================================================== settings

    private LinearLayout settingsRow(boolean withExtras) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(this);
        r1.setGravity(Gravity.CENTER);
        box.addView(r1);
        roundToggle(r1, R.drawable.set_music, R.drawable.set_music_off, musicOn, on -> setMusic(on));
        roundToggle(r1, R.drawable.set_sound, R.drawable.set_sound_off, sfx.enabled, on -> {
            sfx.enabled = on;
            prefs.edit().putBoolean("sound", on).apply();
        });
        roundToggle(r1, R.drawable.set_vibrate, R.drawable.set_vibrate_off, vibrateOn, on -> {
            vibrateOn = on;
            boardView.haptics = on;
            prefs.edit().putBoolean("vibrate", on).apply();
        });
        if (withExtras) {
            LinearLayout r2 = new LinearLayout(this);
            r2.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 10);
            box.addView(r2, lp);
            if (ads.isPrivacyOptionsRequired()) roundButton(r2, R.drawable.set_privacy, v -> ads.showPrivacyOptions());
            roundButton(r2, R.drawable.set_help, v -> showHelp());
            roundButton(r2, R.drawable.set_reset, v -> confirmReset());
        }
        return box;
    }

    private interface Toggle {
        void set(boolean on);
    }

    private void roundToggle(LinearLayout row, int onRes, int offRes, boolean initial, Toggle t) {
        final boolean[] state = {initial};
        ImageView iv = roundButton(row, initial ? onRes : offRes, null);
        iv.setOnClickListener(v -> {
            state[0] = !state[0];
            iv.setImageResource(state[0] ? onRes : offRes);
            t.set(state[0]);
        });
    }

    private ImageView roundButton(LinearLayout row, int res, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        int s = Ui.dp(this, 76);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 3);
        row.addView(iv, lp);
        Ui.pressable(iv);
        if (l != null) iv.setOnClickListener(l);
        return iv;
    }

    private void showSettings() {
        GameDialog d = dialog("Settings", Ui.BLUE, true);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        logo.setAdjustViewBounds(true);
        d.body.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 200), ViewGroup.LayoutParams.WRAP_CONTENT));
        d.body.addView(settingsRow(true));
        d.text("Version " + BuildConfig.VERSION_NAME, 13, 0xFF7A8A99);
    }

    private void showHelp() {
        GameDialog d = dialog("How to play", Ui.PURPLE, true);
        d.text("Swipe two candies to make a line of 3 or more of the same kind.", 16, 0xFF4A2A6A);
        helpRow(d, R.drawable.candy_4_h, "Match 4 → striped candy. Clears a whole row or column.");
        helpRow(d, R.drawable.candy_0_w, "Match in an L or T → wrapped candy. Explodes nearby.");
        helpRow(d, R.drawable.candy_bomb, "Match 5 → rainbow ball. Swap it to clear one color.");
        d.text("Swap two special candies together for huge combos!", 16, 0xFF4A2A6A);
        d.button("Got it", Ui.GREEN, v -> dismiss(d));
    }

    private void helpRow(GameDialog d, int icon, String text) {
        LinearLayout row = d.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTypeface(Ui.font(this));
        t.setTextSize(15);
        t.setTextColor(0xFF333333);
        t.setPadding(Ui.dp(this, 8), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void confirmReset() {
        GameDialog d = dialog("Reset progress?", Ui.PINK, true);
        d.text("Go back to level 1. Stars, coins and boosters will be reset.", 17, 0xFF6A1B4D);
        d.button("Cancel", Ui.GREEN, v -> dismiss(d));
        d.button("Reset", Ui.PINK, v -> {
            boolean s = sfx.enabled;
            prefs.edit().clear().putBoolean("sound", s).putBoolean("music", musicOn).putBoolean("vibrate", vibrateOn)
                    .putInt("coins", START_COINS).putInt(BOOSTER_KEY[0], START_BOOSTERS)
                    .putInt(BOOSTER_KEY[1], START_BOOSTERS).putInt(BOOSTER_KEY[2], START_BOOSTERS).apply();
            unlocked = 1;
            coins = START_COINS;
            for (GameDialog g : new ArrayList<>(dialogs)) g.dismiss();
            dialogs.clear();
            showMap();
        });
    }

    // ================================================================== lives

    private int getLives() {
        int lives = prefs.getInt("lives", MAX_LIVES);
        long since = prefs.getLong("lifeTime", 0);
        long now = System.currentTimeMillis();
        if (lives >= MAX_LIVES) return MAX_LIVES;
        if (since <= 0 || since > now) since = now;
        long gained = (now - since) / LIFE_MS;
        if (gained > 0) {
            lives = (int) Math.min(MAX_LIVES, lives + gained);
            since = lives >= MAX_LIVES ? 0 : since + gained * LIFE_MS;
            prefs.edit().putInt("lives", lives).putLong("lifeTime", since).apply();
        }
        return lives;
    }

    private long msToNextLife() {
        if (getLives() >= MAX_LIVES) return 0;
        long since = prefs.getLong("lifeTime", System.currentTimeMillis());
        return Math.max(0, since + LIFE_MS - System.currentTimeMillis());
    }

    private void loseLife() {
        int lives = getLives();
        if (lives <= 0) return;
        SharedPreferences.Editor e = prefs.edit().putInt("lives", lives - 1);
        if (lives >= MAX_LIVES) e.putLong("lifeTime", System.currentTimeMillis());
        e.apply();
        updateLives();
    }

    private void addLife() {
        int lives = getLives();
        if (lives >= MAX_LIVES) return;
        SharedPreferences.Editor e = prefs.edit().putInt("lives", lives + 1);
        if (lives + 1 >= MAX_LIVES) e.putLong("lifeTime", 0);
        e.apply();
        updateLives();
    }

    private void showNoLives(int levelToPlay) {
        GameDialog d = dialog(getLives() <= 0 ? "No lives" : "Lives", Ui.PINK, true);
        d.image(R.drawable.ui_heart, 90);
        TextView timer = d.text("", 20, 0xFF6A1B4D);
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!d.isShowing()) return;
                int lives = getLives();
                timer.setText(lives >= MAX_LIVES ? "Lives are full!" : lives + " of " + MAX_LIVES + " lives\nNext life in " + formatTime(msToNextLife()));
                handler.postDelayed(this, 1000);
            }
        };
        tick.run();
        d.button("▶ Watch ad: +1 life", Ui.GREEN, v -> ads.showRewarded(() -> {
            addLife();
            dismiss(d);
            if (levelToPlay > 0 && getLives() > 0) startLevel(levelToPlay);
        }, this::adNotReady));
        d.button("Refill: " + REFILL_COST + " coins", Ui.ORANGE, v -> {
            if (coins >= REFILL_COST) {
                addCoins(-REFILL_COST);
                prefs.edit().putInt("lives", MAX_LIVES).putLong("lifeTime", 0).apply();
                updateLives();
                dismiss(d);
                if (levelToPlay > 0) startLevel(levelToPlay);
            } else toast("Not enough coins");
        });
    }

    private void updateLives() {
        int lives = getLives();
        livesText.setText(String.valueOf(lives));
        lifeTimer.setText(lives >= MAX_LIVES ? "Full" : formatTime(msToNextLife()));
    }

    private final Runnable lifeTicker = new Runnable() {
        @Override
        public void run() {
            if (screenMap.getVisibility() == View.VISIBLE) updateLives();
            handler.postDelayed(this, 1000);
        }
    };

    private void tickLives() {
        handler.removeCallbacks(lifeTicker);
        handler.post(lifeTicker);
    }

    private static String formatTime(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    // ================================================================== helpers

    private void addCoins(int delta) {
        coins = Math.max(0, coins + delta);
        prefs.edit().putInt("coins", coins).apply();
        updateCoins();
    }

    private void updateCoins() {
        mapCoins.setText(nf.format(coins));
    }

    private void adNotReady() {
        toast("Ad not ready yet. Try again in a moment.");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
