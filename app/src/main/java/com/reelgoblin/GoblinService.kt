package com.reelgoblin

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.max

/**
 * One service does everything: floats the goblin (accessibility overlay, no "draw over apps" permission),
 * knows when Instagram/YouTube is in front, counts reels (Level 2) and grows over the Reels screen (Giant mode).
 * Android restarts enabled accessibility services after a reboot, and counts are stored on disk.
 */
class GoblinService : AccessibilityService() {
    companion object {
        val TARGETS = setOf(ReelDetector.IG, ReelDetector.YT)
        private val SIZES = intArrayOf(84, 92, 100, 110, 120, 132)
        private const val TAG = "ReelGoblin"
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var goblin: GoblinView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var attached = false
    private var bubble: TextView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var bubbleAttached = false
    private var sizer: ValueAnimator? = null

    private var currentPkg: String? = null
    private var inReels = false
    private var lastSig: String? = null
    private var entryCounted = false
    private var lastScrollAt = 0L
    private var lastCountAt = 0L
    private var checkPosted = false
    private var peekUntil = 0L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun screenW() = resources.displayMetrics.widthPixels
    private fun screenH() = resources.displayMetrics.heightPixels

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hide()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- events

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        e ?: return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val isTarget = pkg in TARGETS

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isTarget) {
                if (currentPkg != pkg) { resetReelState(); currentPkg = pkg }
                show()
            } else if (!isIgnored(pkg)) {
                currentPkg = null
                hide()
            }
        }
        if (!isTarget) return
        if (currentPkg == null) { currentPkg = pkg; show() }
        if (currentPkg != pkg) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> { lastScrollAt = SystemClock.elapsedRealtime(); schedule(350, true) }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> schedule(500, true)
            else -> schedule(800, false)
        }
    }

    private fun isIgnored(pkg: String) =
        pkg == "android" || pkg.startsWith("com.android.systemui") ||
            pkg.contains("inputmethod") || pkg.contains("keyboard") || pkg.contains("swiftkey")

    private val check = Runnable { checkPosted = false; runCheck() }

    private fun schedule(delayMs: Long, reset: Boolean) {
        if (reset) handler.removeCallbacks(check) else if (checkPosted) return
        checkPosted = true
        handler.postDelayed(check, delayMs)
    }

    private fun runCheck() {
        val pkg = currentPkg ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val info = ReelDetector.inspect(root, pkg)
        Log.d(TAG, "pkg=$pkg inReels=${info.inReels} sig=${info.sig}")
        syncGoblin()
        if (info.inReels != inReels) setInReels(info.inReels)
        if (!info.inReels) return
        if (Store.level(this) != Store.LEVEL_AUTO) return   // Level 1: user taps

        val now = SystemClock.elapsedRealtime()
        if (info.sig.isNotEmpty()) {
            if (info.sig != lastSig) {
                val first = lastSig == null
                lastSig = info.sig
                if (!(first && entryCounted)) registerReel()
                entryCounted = true
            }
        } else if (!entryCounted) {
            entryCounted = true; registerReel()
        } else if (lastScrollAt > lastCountAt && now - lastCountAt > 900) {
            registerReel()   // fallback when the reel text can't be read: one settled scroll = one reel
        }
    }

    private fun registerReel() {
        lastCountAt = SystemClock.elapsedRealtime()
        bump(1)
    }

    private fun resetReelState() {
        inReels = false; lastSig = null; entryCounted = false
        handler.removeCallbacks(tick)
    }

    private fun setInReels(v: Boolean) {
        inReels = v
        handler.removeCallbacks(tick)
        if (v) handler.postDelayed(tick, 1000) else { lastSig = null; entryCounted = false }
        applyLayout(true)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!inReels) return
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isInteractive) Store.addSeconds(this@GoblinService, 1)
            handler.postDelayed(this, 1000)
        }
    }

    // ---------------------------------------------------------------- counting / growth

    private fun bump(delta: Int) {
        val n = Store.add(this, delta)
        syncGoblin()
        goblin?.pop()
        val goal = Store.goal(this)
        if (delta > 0) {
            if (n % 10 == 0 || (goal > 0 && n == goal)) buzz(if (n == goal) 160 else 50)
            Lines.forCount(n, goal)?.let { say(it) }
        }
        applyLayout(true)
    }

    private fun syncGoblin() {
        val g = goblin ?: return
        val n = Store.count(this)
        val goal = Store.goal(this)
        g.count = n
        g.stage = Lines.stageOf(n)
        g.palette = Store.palette(this)
        g.angry = goal > 0 && n > goal
        g.invalidate()
    }

    private fun buzz(ms: Long) {
        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- overlay window

    /** 0..1: how much of the screen the goblin should cover right now (Giant mode, inside Reels only). */
    private fun coverage(): Float {
        if (!Store.coverMode(this) || !inReels) return 0f
        if (SystemClock.elapsedRealtime() < peekUntil) return 0f
        return (Store.count(this).toFloat() / Store.coverFull(this)).coerceIn(0f, 1f)
    }

    private fun targetRect(): IntArray {
        val s = dp(SIZES[Lines.stageOf(Store.count(this))])
        val sw = screenW(); val sh = screenH()
        val bx = Store.posX(this, sw - s - dp(12)).coerceIn(0, max(0, sw - s))
        val by = Store.posY(this, (sh * 0.35f).toInt()).coerceIn(0, max(0, sh - s))
        val c = coverage()
        return intArrayOf(
            (s + (sw - s) * c).toInt(), (s + (sh - s) * c).toInt(),
            (bx * (1 - c)).toInt(), (by * (1 - c)).toInt()
        )
    }

    private fun applyLayout(animate: Boolean) {
        val g = goblin ?: return
        val p = lp ?: return
        if (!attached) return
        val t = targetRect()
        sizer?.cancel()
        if (!animate) {
            p.width = t[0]; p.height = t[1]; p.x = t[2]; p.y = t[3]
            try { wm.updateViewLayout(g, p) } catch (_: Exception) {}
            return
        }
        val from = intArrayOf(p.width, p.height, p.x, p.y)
        sizer = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                p.width = (from[0] + (t[0] - from[0]) * f).toInt()
                p.height = (from[1] + (t[1] - from[1]) * f).toInt()
                p.x = (from[2] + (t[2] - from[2]) * f).toInt()
                p.y = (from[3] + (t[3] - from[3]) * f).toInt()
                try { wm.updateViewLayout(g, p) } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun show() {
        if (attached) return
        val g = GoblinView(this)
        goblin = g
        val params = WindowManager.LayoutParams(
            dp(84), dp(84),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        lp = params
        val t = targetRect()
        params.width = t[0]; params.height = t[1]; params.x = t[2]; params.y = t[3]
        g.setOnTouchListener(touch)
        try {
            wm.addView(g, params)
            attached = true
            syncGoblin()
        } catch (e: Exception) {
            Log.e(TAG, "overlay failed", e)
        }
    }

    private fun hide() {
        sizer?.cancel()
        hideBubbleNow()
        val g = goblin
        if (g != null && attached) try { wm.removeView(g) } catch (_: Exception) {}
        attached = false; goblin = null; lp = null
        resetReelState()
        handler.removeCallbacks(check); checkPosted = false
    }

    // ---------------------------------------------------------------- touch: tap / long-press / drag

    private val touch = object : View.OnTouchListener {
        var dx0 = 0f; var dy0 = 0f; var sx = 0; var sy = 0
        var moved = false; var longDone = false
        val longTask = Runnable { if (!moved) { longDone = true; onLong() } }

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            val p = lp ?: return true
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dx0 = ev.rawX; dy0 = ev.rawY; sx = p.x; sy = p.y
                    moved = false; longDone = false
                    handler.postDelayed(longTask, 650)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - dx0; val dy = ev.rawY - dy0
                    if (!moved && hypot(dx, dy) > dp(10)) { moved = true; handler.removeCallbacks(longTask) }
                    if (moved && coverage() == 0f) {
                        p.x = (sx + dx).toInt().coerceIn(0, max(0, screenW() - p.width))
                        p.y = (sy + dy).toInt().coerceIn(0, max(0, screenH() - p.height))
                        try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longTask)
                    if (moved) { if (coverage() == 0f) Store.setPos(this@GoblinService, p.x, p.y) }
                    else if (!longDone) onTap()
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longTask)
            }
            return true
        }
    }

    private fun onTap() {
        if (coverage() >= 0.55f) {          // giant goblin: tap = go home
            say(Lines.randomHome())
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 900)
            return
        }
        if (Store.level(this) == Store.LEVEL_MANUAL) {
            bump(1)
        } else {
            goblin?.pop(); say(Lines.tickle(), 2000)
        }
    }

    private fun onLong() {
        buzz(40)
        if (coverage() >= 0.35f) {          // giant goblin: long-press = let me peek for 60s
            peekUntil = SystemClock.elapsedRealtime() + 60_000
            say("Okay okay... 60 seconds of peeking 👀")
            applyLayout(true)
            handler.postDelayed({ applyLayout(true) }, 60_500)
        } else {                            // normal goblin: long-press = undo one
            val n = Store.add(this, -1)
            syncGoblin(); goblin?.pop(); applyLayout(true)
            say("Oops, took one back → $n", 2000)
        }
    }

    // ---------------------------------------------------------------- speech bubble

    private val hideBubble = Runnable { hideBubbleNow() }

    private fun hideBubbleNow() {
        handler.removeCallbacks(hideBubble)
        val b = bubble
        if (b != null && bubbleAttached) try { wm.removeView(b) } catch (_: Exception) {}
        bubbleAttached = false
    }

    private fun say(msg: String, ms: Long = 3500) {
        val p = lp ?: return
        if (!attached) return
        val b = bubble ?: TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFF3A2A4D.toInt())
            setPadding(dp(14), dp(10), dp(14), dp(10))
            maxWidth = (screenW() * 0.8f).toInt()
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(18).toFloat(); setStroke(dp(2), 0xFF3A2A4D.toInt())
            }
        }.also { bubble = it }
        b.text = msg
        val bp = bubbleLp ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }.also { bubbleLp = it }
        bp.y = when {
            p.height > screenH() * 0.5f -> dp(96)
            p.y > dp(110) -> p.y - dp(64)
            else -> p.y + p.height + dp(8)
        }
        try {
            if (bubbleAttached) wm.updateViewLayout(b, bp) else { wm.addView(b, bp); bubbleAttached = true }
        } catch (_: Exception) {}
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, ms)
    }
}
