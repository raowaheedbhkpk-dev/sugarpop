# Sugar Pop Match (Android match-3 + AdMob)

An original match-3 puzzle game: swap candies, make matches, set off special candies
and chain reactions, and work through an endless level map.

## Features
- Glossy 3D candies (heart, disc, star, square, drop, berry), striped and wrapped versions, and a rainbow ball
- Shaped boards with gaps (candies fall through), pink jelly to clear, three goal types: score, jelly, collect
- Special candies and combos with beams, blasts, rainbow lightning, particles and praise text
- Level map: grass, winding road, striped candy path, trees, candy houses, lamps, world signs, mascot on your level
- Top bar with lives and timer, coins, settings; bottom tabs: Map, Daily treats (7-day rewards), Shop
- Popups: level start, win with stars popping in, out of moves, pause, settings (music/sound/vibration,
  privacy, help, reset), shop, booster offers, no lives
- Boosters with inventory: Hammer, Free Swap, Shuffle
- Original background music, sound effects, splash screen with logo and mascot
- Levels 1–300 calibrated by simulating thousands of games (about 2 in 3 attempts win)

All art, sounds, music and characters are original. Font: Lilita One (SIL Open Font License, see assets/fonts/OFL.txt).

## Ads (AdMob)
| Ad | Where |
|---|---|
| Banner | Bottom of screen |
| Interstitial | Returning to the map, from level 4, every 2 levels, max once per 90 s |
| Rewarded | +5 moves when out of moves, a free life, boosters, 3x coins after a win, 2x daily treat, free coins in shop |

Rules: top of `AdsManager.java`. Prices: top of `MainActivity.java`.

## Build the APK on GitHub (from Termux)
```
cd ~
unzip -o ~/storage/downloads/SugarPopMatch.zip -d SugarPopMatch
cd SugarPopMatch
git init -b main
git add -A
git commit -m "Sugar Pop Match"
gh repo create sugarpop --private --source=. --push
sleep 15
gh run watch $(gh run list -L 1 --json databaseId -q '.[0].databaseId') --exit-status
gh run download -n sugarpop-debug-apk -D ~/storage/downloads/SugarPopAPK
```
Install `app-debug.apk` from Downloads/SugarPopAPK. Debug builds always show Google test ads.

## Before publishing on Google Play
1. In `gradle.properties`: change `APPLICATION_ID`, and paste your real AdMob App ID and
   3 ad unit IDs (Banner, Interstitial, Rewarded).
2. Add a signing key as GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`). Create it with:
   `keytool -genkeypair -v -keystore release.jks -alias sugarpop -keyalg RSA -keysize 2048 -validity 10000`
   then `base64 -w 0 release.jks > release.b64`. Back it up; never commit it.
3. Upload `sugarpop-release-aab` to Play Console. Set "Contains ads" = Yes, target age 13+,
   add a privacy policy URL, and host `app-ads.txt` on your website.
4. Store graphics are in `store/`.

## Code
```
app/src/main/java/com/sugarpop/match3/
  Board.java        match-3 rules: matching, specials, combos, cascades, shuffle
  Level.java        level goals per level number
  LevelTable.java   calibrated move counts for levels 1-300
  BoardView.java    board drawing, input, animations, effects
  MapView.java      scrolling level map with scenery
  ScoreBar.java     score bar with star markers
  GameDialog.java   game-style popups
  Ui.java           font, outlined text, glossy buttons
  MainActivity.java screens, lives, coins, boosters, daily treats, shop, settings
  AdsManager.java   AdMob + consent
  Sfx.java          sound effects
```
