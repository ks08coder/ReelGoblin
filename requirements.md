# Reel Goblin: Requirements

## Functional
| # | Requirement | How it is met |
|---|---|---|
| F1 | Real installable Android app | Kotlin, minSdk 26 (Android 8+), debug-signed APK |
| F2 | Cute monster bubble over Instagram and YouTube | Accessibility overlay window, shown only while those apps are in front |
| F3 | Counter 1, 2, 3... on the monster | Number drawn on the goblin's belly |
| F4 | Level 1 manual: tap after each reel | Tap = +1, long-press = undo |
| F5 | Level 2 automatic | Accessibility events + reel detector (see architecture.md) |
| F6 | Grows and talks as count rises | 6 growth stages, new mouth per stage, 40+ funny lines |
| F7 | App shows today, time lost, last 7 days | Dashboard screen with bar chart |
| F8 | **Monster mode**: grows and covers the Reels screen | Giant mode switch, covers 0 to 100% of the screen by reel count, only inside Reels/Shorts |

## Done-when checklist
- [ ] APK installs on a real phone
- [ ] Goblin stays on top of Instagram AND YouTube, hides on Home/other apps
- [ ] Count survives a phone restart (stored on disk with commit())
- [ ] Level 2: over 20 reels, within 2 of your own count (test in setup.md)
- [ ] No internet, no login (manifest has NO INTERNET permission)

## Non-functional
- Zero third-party libraries; APK well under 2 MB
- Zero sign-ups, zero Gmail. Nothing leaves the phone
- Battery: event checks are throttled (350 to 800 ms), tree scan capped at 1200 nodes

## Known limits (honest)
- Level 2 depends on Instagram/YouTube view ids. App updates can break detection until `ReelDetector.kt` lists are updated
- Not on Play Store: Accessibility is a sensitive permission, so you sideload
- Time lost = bigger of measured time inside Reels and count x seconds-per-reel
