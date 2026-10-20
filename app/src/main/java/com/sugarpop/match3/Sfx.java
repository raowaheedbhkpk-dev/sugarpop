package com.sugarpop.match3;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** Short sound effects. */
public final class Sfx {
    private final SoundPool pool;
    private final int pop, swap, bad, special, win, fail;
    public boolean enabled = true;

    public Sfx(Context ctx) {
        pool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pop = pool.load(ctx, R.raw.pop, 1);
        swap = pool.load(ctx, R.raw.swap, 1);
        bad = pool.load(ctx, R.raw.bad, 1);
        special = pool.load(ctx, R.raw.special, 1);
        win = pool.load(ctx, R.raw.win, 1);
        fail = pool.load(ctx, R.raw.fail, 1);
    }

    private void play(int id, float volume, float rate) {
        if (enabled) pool.play(id, volume, volume, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
    }

    /** Each cascade pops a little higher. */
    public void pop(int cascade) { play(pop, 0.8f, 1f + (cascade - 1) * 0.12f); }
    public void swap() { play(swap, 0.5f, 1f); }
    public void bad() { play(bad, 0.6f, 1f); }
    public void special() { play(special, 0.7f, 1f); }
    public void win() { play(win, 0.9f, 1f); }
    public void fail() { play(fail, 0.8f, 1f); }

    public void release() {
        pool.release();
    }
}
