# Reel Goblin: Architecture

```
MainActivity (dashboard + settings) ──reads/writes──► Store (SharedPreferences, on disk)
                                                          ▲
GoblinService (AccessibilityService) ─────────────────────┘
   ├─ events from every app: only acts on com.instagram.android / com.google.android.youtube
   ├─ ReelDetector: scans the UI tree -> inReels? + reel signature (author+caption)
   ├─ GoblinView: Canvas-drawn monster (growth stages, blink, bob, pop)
   ├─ overlay window (TYPE_ACCESSIBILITY_OVERLAY) + speech-bubble window
   └─ Lines: funny text by milestone and growth stage
```

## Why one Accessibility Service
- Gives the overlay without the "draw over other apps" permission
- Tells us which app is in front, and sees scroll events inside Reels
- Android auto-restarts it after reboot; no boot receiver or foreground notification needed

## Level 2 counting algorithm
1. Scroll event: wait 350 ms of quiet. Content change: throttle to every 800 ms.
2. Scan tree. If a Reels/Shorts marker id is visible, we are `inReels`.
3. Build signature from visible author/caption nodes. **New signature = new reel = +1.** Same signature (video looping) = no count.
4. Fallback if text cannot be read: one settled scroll (min 900 ms apart) = +1.
5. Leaving Reels resets state, so re-entering counts the first reel again.

## Monster (Giant) mode
`coverage = count / coverFull` (0..1), only while `inReels`. The overlay window animates from bubble size to full screen (size, position and goblin all scale). At 55%+ coverage tap sends you Home; long-press gives a 60 s peek. Outside Reels the goblin shrinks back.

## Data model (SharedPreferences)
`count_YYYY-MM-DD`, `sec_YYYY-MM-DD`, `level`, `cover`, `coverFull`, `goal`, `secPerReel`, `palette`, `posX/posY`.
Days roll over by date key, which gives the 7-day history for free.

## Files
| File | Job |
|---|---|
| GoblinService.kt | overlay, events, counting, growth, touch |
| ReelDetector.kt | the only place with Instagram/YouTube view ids |
| GoblinView.kt | drawing the monster |
| MainActivity.kt / WeekChartView.kt | dashboard |
| Store.kt / Lines.kt | data / funny lines |
