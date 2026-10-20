package com.sugarpop.match3;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * All AdMob logic in one place: consent (GDPR), banner, interstitial with
 * frequency cap, and rewarded ads. Ad unit IDs come from BuildConfig
 * (debug builds always use Google's test IDs).
 */
public final class AdsManager {

    /** Show an interstitial at most once every N finished levels (won or lost)... */
    private static final int LEVELS_BETWEEN_INTERSTITIALS = 2;
    /** ...and not more often than this. */
    private static final long MIN_MS_BETWEEN_FULLSCREEN = 90_000;
    /** No interstitials before this level, so new players get hooked first. */
    private static final int FIRST_INTERSTITIAL_LEVEL = 4;

    private final Activity activity;
    private final ViewGroup bannerContainer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean initStarted = new AtomicBoolean(false);
    private ConsentInformation consentInformation;

    private boolean ready;
    private AdView bannerView;
    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private boolean interstitialLoading, rewardedLoading;
    private int levelsSinceInterstitial;
    private long lastFullscreenAt;

    public AdsManager(Activity activity, ViewGroup bannerContainer) {
        this.activity = activity;
        this.bannerContainer = bannerContainer;
    }

    /** Call once from onCreate. Shows the consent form when the law requires it, then starts ads. */
    public void start() {
        consentInformation = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    if (consentInformation.canRequestAds()) initializeAds();
                }),
                requestError -> {
                    if (consentInformation.canRequestAds()) initializeAds();
                });
        // Consent from a previous session lets us start right away.
        if (consentInformation.canRequestAds()) initializeAds();
    }

    public boolean isPrivacyOptionsRequired() {
        return consentInformation != null
                && consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public void showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity, formError -> {
            if (consentInformation.canRequestAds()) initializeAds();
        });
    }

    private void initializeAds() {
        if (!initStarted.compareAndSet(false, true)) return;
        new Thread(() -> MobileAds.initialize(activity, status ->
                handler.post(this::onInitialized))).start();
    }

    private void onInitialized() {
        if (activity.isFinishing() || ready) return;
        ready = true;
        loadBanner();
        loadInterstitial();
        loadRewarded();
    }

    // ---------------- Banner ----------------

    private void loadBanner() {
        bannerView = new AdView(activity);
        bannerView.setAdUnitId(BuildConfig.AD_BANNER_ID);
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        bannerView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
        bannerContainer.removeAllViews();
        bannerContainer.addView(bannerView);
        bannerView.loadAd(new AdRequest.Builder().build());
    }

    // ---------------- Interstitial ----------------

    private void loadInterstitial() {
        if (!ready || interstitial != null || interstitialLoading) return;
        interstitialLoading = true;
        InterstitialAd.load(activity, BuildConfig.AD_INTERSTITIAL_ID, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialLoading = false;
                        interstitial = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        interstitialLoading = false;
                        interstitial = null;
                        handler.postDelayed(AdsManager.this::loadInterstitial, 30_000);
                    }
                });
    }

    /**
     * Call when the player leaves a finished level. Shows an interstitial only if the
     * frequency rules allow it; {@code then} always runs afterwards.
     */
    public void onLevelFinished(int levelJustFinished, Runnable then) {
        levelsSinceInterstitial++;
        boolean timeOk = SystemClock.elapsedRealtime() - lastFullscreenAt >= MIN_MS_BETWEEN_FULLSCREEN;
        if (interstitial == null
                || levelJustFinished < FIRST_INTERSTITIAL_LEVEL
                || levelsSinceInterstitial < LEVELS_BETWEEN_INTERSTITIALS
                || !timeOk) {
            then.run();
            return;
        }
        InterstitialAd ad = interstitial;
        interstitial = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                lastFullscreenAt = SystemClock.elapsedRealtime();
                loadInterstitial();
                then.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                loadInterstitial();
                then.run();
            }
        });
        levelsSinceInterstitial = 0;
        ad.show(activity);
    }

    // ---------------- Rewarded ----------------

    private void loadRewarded() {
        if (!ready || rewarded != null || rewardedLoading) return;
        rewardedLoading = true;
        RewardedAd.load(activity, BuildConfig.AD_REWARDED_ID, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewardedLoading = false;
                        rewarded = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        rewardedLoading = false;
                        rewarded = null;
                        handler.postDelayed(AdsManager.this::loadRewarded, 30_000);
                    }
                });
    }

    public boolean isRewardedReady() {
        return rewarded != null;
    }

    /** Shows a rewarded ad. {@code onReward} runs after the ad closes, only if the reward was earned. */
    public void showRewarded(Runnable onReward, Runnable onUnavailable) {
        if (rewarded == null) {
            loadRewarded();
            onUnavailable.run();
            return;
        }
        RewardedAd ad = rewarded;
        rewarded = null;
        final boolean[] earned = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                lastFullscreenAt = SystemClock.elapsedRealtime();
                loadRewarded();
                if (earned[0]) onReward.run();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError adError) {
                loadRewarded();
                onUnavailable.run();
            }
        });
        ad.show(activity, rewardItem -> earned[0] = true);
    }

    // ---------------- Lifecycle ----------------

    public void pause() {
        if (bannerView != null) bannerView.pause();
    }

    public void resume() {
        if (bannerView != null) bannerView.resume();
    }

    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        if (bannerView != null) bannerView.destroy();
    }
}
