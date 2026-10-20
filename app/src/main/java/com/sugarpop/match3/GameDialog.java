package com.sugarpop.match3;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A game-style popup: colored frame, outlined title, light inner panel, round close button. */
public final class GameDialog {

    private final FrameLayout host;
    private final FrameLayout overlay;
    private final View cardWrap;
    public final LinearLayout body;
    private Runnable onClose, afterDismiss;
    private boolean dismissed;

    private GameDialog(FrameLayout host, String title, int frameColor, boolean closable) {
        this.host = host;
        Context c = host.getContext();
        overlay = new FrameLayout(c);
        overlay.setBackgroundColor(0xA6000000);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setOnClickListener(v -> { });

        FrameLayout wrap = new FrameLayout(c);
        cardWrap = wrap;
        int maxW = Math.min(Ui.dp(c, 340), c.getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 32));
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        overlay.addView(wrap, wlp);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(Ui.panelRes(frameColor));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(c, 30);
        wrap.addView(card, clp);

        // space under the ribbon
        card.addView(new View(c), new LinearLayout.LayoutParams(1, Ui.dp(c, 30)));

        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setBackgroundResource(R.drawable.panel_inner);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(body, blp);

        // ribbon title on top of the panel
        FrameLayout ribbon = new FrameLayout(c);
        ribbon.setBackgroundResource(Ui.ribbonRes(frameColor));
        Ui.OutlineText t = new Ui.OutlineText(c, 28, Color.WHITE, 0xFF7A1048);
        t.setText(title);
        ribbon.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 66), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ribbon.setMinimumWidth(Ui.dp(c, 220));
        wrap.addView(ribbon, rlp);

        if (closable) {
            ImageView close = new ImageView(c);
            close.setImageResource(R.drawable.ui_close);
            close.setContentDescription("Close");
            int s = Ui.dp(c, 48);
            FrameLayout.LayoutParams xlp = new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.END);
            xlp.topMargin = Ui.dp(c, 16);
            xlp.rightMargin = -Ui.dp(c, 4);
            wrap.addView(close, xlp);
            close.setElevation(Ui.dp(c, 14));
            Ui.pressable(close);
            close.setOnClickListener(v -> close());
        }
    }

    public static GameDialog show(FrameLayout host, String title, int frameColor, boolean closable) {
        GameDialog d = new GameDialog(host, title, frameColor, closable);
        host.addView(d.overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        d.cardWrap.setScaleX(0.6f);
        d.cardWrap.setScaleY(0.6f);
        d.cardWrap.setAlpha(0f);
        d.cardWrap.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(280)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
        return d;
    }

    /** Runs when closed with the X (not when dismissed from code). */
    public GameDialog onClose(Runnable r) {
        onClose = r;
        return this;
    }

    public GameDialog afterDismiss(Runnable r) {
        afterDismiss = r;
        return this;
    }

    public void close() {
        Runnable r = onClose;
        dismiss();
        if (r != null) r.run();
    }

    public void dismiss() {
        if (dismissed) return;
        dismissed = true;
        if (afterDismiss != null) afterDismiss.run();
        cardWrap.animate().scaleX(0.7f).scaleY(0.7f).alpha(0f).setDuration(150)
                .withEndAction(() -> host.removeView(overlay)).start();
    }

    public boolean isShowing() {
        return !dismissed;
    }

    // ---------------------------------------------------------------- content helpers

    public TextView text(String s, float sp, int color) {
        Context c = body.getContext();
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Ui.font(c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 4);
        body.addView(t, lp);
        return t;
    }

    public ImageView image(int res, float sizeDp) {
        Context c = body.getContext();
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        int s = Ui.dp(c, sizeDp);
        body.addView(iv, new LinearLayout.LayoutParams(s, s));
        return iv;
    }

    public Ui.OutlineText button(String label, int color, View.OnClickListener l) {
        Context c = body.getContext();
        Ui.OutlineText b = Ui.button(c, label, color, 22);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 62));
        lp.topMargin = Ui.dp(c, 10);
        body.addView(b, lp);
        return b;
    }

    public LinearLayout row() {
        Context c = body.getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 6);
        body.addView(r, lp);
        return r;
    }

    /** White rounded box with a label and a big number, like a score panel. */
    public TextView scoreBox(String label, String value, int color) {
        Context c = body.getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Ui.rounded(Color.WHITE, Ui.dp(c, 16), Ui.dp(c, 1), 0x22000000));
        box.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 8));
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(20);
        l.setTextColor(color);
        l.setTypeface(Ui.font(c));
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(40);
        v.setTextColor(color);
        v.setTypeface(Ui.font(c));
        box.addView(l);
        box.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 8);
        body.addView(box, lp);
        return v;
    }
}
