# Reel Goblin: Setup

## Option A: Android Studio (no sign-up at all)
1. Install Android Studio (free) from developer.android.com/studio.
2. File > Open > pick this `ReelGoblin` folder. Let Gradle sync (needs internet once, to download tools).
3. Build > Build Bundle(s) / APK(s) > **Build APK(s)**.
4. Click "locate" in the popup. File is `app/build/outputs/apk/debug/app-debug.apk`.

## Option B: GitHub Actions (no tools on your PC)
1. Create a free GitHub account, make a new repo, upload everything in this folder (keep `.github/`).
2. Open the **Actions** tab > "Build APK" > wait ~5 minutes.
3. Open the finished run > **Artifacts** > download `ReelGoblin-apk`, unzip to get `app-debug.apk`.

## Install on your phone
1. Copy `app-debug.apk` to the phone (USB, Drive, WhatsApp-to-self...).
2. Open it, allow "Install unknown apps" for that file manager when asked.
3. Open **Reel Goblin**.

## Wake the goblin (one time)
1. In the app tap **Wake the Goblin**. Settings > Accessibility > Installed apps > **Reel Goblin** > turn on.
2. If the switch is greyed out ("Restricted setting", Android 13+): App info > top-right **⋮** > **Allow restricted settings**, then retry.
3. Open Instagram or YouTube. The goblin floats in. Drag him anywhere.
4. After a reboot nothing needs redoing: Android restarts the service, counts are on disk.

## Tune / debug counting (Level 2)
Instagram and YouTube rename internal view ids sometimes.
`adb logcat -s ReelGoblin` prints `inReels=... sig=...` on every check. If it says `inReels=false` inside Reels, or the sig never changes between reels, edit the id lists at the top of `ReelDetector.kt`.

## Accuracy test (the "within 2 over 20 reels" check)
Level 2 on, open Reels, watch 20 reels while counting on paper (or tap the "+1 fix" button yourself). Goblin count should be 18 to 22. Repeat on YouTube Shorts.
