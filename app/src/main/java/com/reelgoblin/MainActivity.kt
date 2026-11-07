package com.reelgoblin

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** The dashboard: today's count, time lost, last 7 days, plus all settings. Built in code, no XML layouts. */
class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val INK = 0xFF2B2B3A.toInt()
    private val PURPLE = 0xFF7C4DFF.toInt()

    private lateinit var goblin: GoblinView
    private lateinit var tvStatus: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvGoal: TextView
    private lateinit var bar: ProgressBar
    private lateinit var chart: WeekChartView
    private lateinit var tvWeek: TextView
    private lateinit var btnL1: TextView
    private lateinit var btnL2: TextView
    private lateinit var tvLevelHint: TextView

    private val ticker = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 1000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, r: Int = 20) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun lp(w: Int, hh: Int, top: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, hh, weight).apply { topMargin = dp(top) }

    private fun tv(t: String, sp: Float, color: Int = INK, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun pill(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(INK); gravity = Gravity.CENTER
        setPadding(dp(14), dp(9), dp(14), dp(9)); background = bg(0xFFEDE7FF.toInt(), 14)
        setOnClickListener { onClick() }
    }

    private fun card(parent: LinearLayout, title: String? = null): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14)); elevation = dp(2).toFloat()
        }
        parent.addView(c, lp(MATCH, WRAP, 12))
        if (title != null) c.addView(tv(title, 17f, bold = true))
        return c
    }

    private fun statBox(row: LinearLayout, title: String, color: Int): TextView {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg(color); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        box.addView(tv(title, 13f, 0xFF5A5470.toInt()))
        val v = tv("0", 30f, bold = true)
        box.addView(v)
        val params = lp(0, WRAP, 0, 1f).apply { leftMargin = dp(4); rightMargin = dp(4) }
        row.addView(box, params)
        return v
    }

    private fun stepper(parent: LinearLayout, label: String, get: () -> Int, set: (Int) -> Unit,
                        step: Int, min: Int, max: Int, fmt: (Int) -> String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(tv(label, 15f), lp(0, WRAP, 0, 1f))
        val value = tv(fmt(get()), 15f, bold = true).apply { gravity = Gravity.CENTER; minWidth = dp(70) }
        fun change(d: Int) { set((get() + d).coerceIn(min, max)); value.text = fmt(get()); refresh() }
        row.addView(pill("−") { change(-step) })
        row.addView(value)
        row.addView(pill("+") { change(step) })
        parent.addView(row, lp(MATCH, WRAP, 10))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFFFFF7EC.toInt()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFFFFF7EC.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(32))
        }
        scroll.addView(root)
        setContentView(scroll)
        build(root)
    }

    private fun build(root: LinearLayout) {
        // --- hero
        val hero = card(root).apply { gravity = Gravity.CENTER_HORIZONTAL }
        hero.addView(tv("Reel Goblin", 26f, bold = true))
        goblin = GoblinView(this)
        goblin.setOnClickListener { goblin.pop() }
        hero.addView(goblin, lp(dp(150), dp(150), 8))
        tvStatus = tv("", 14f).apply { gravity = Gravity.CENTER }
        hero.addView(tvStatus, lp(MATCH, WRAP, 6))
        hero.addView(pill("Wake the Goblin (turn on Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, lp(WRAP, WRAP, 10))
        hero.addView(tv("Can't switch it on? Open App info → ⋮ menu → \"Allow restricted settings\", then try again.", 12f, 0xFF7A7390.toInt()), lp(MATCH, WRAP, 8))
        hero.addView(pill("Open App info") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }, lp(WRAP, WRAP, 6))

        // --- today
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(row, lp(MATCH, WRAP, 12))
        tvToday = statBox(row, "Reels today", 0xFFE8DDFF.toInt())
        tvTime = statBox(row, "Time lost", 0xFFFFE1D6.toInt())

        val goalCard = card(root)
        tvGoal = tv("", 14f)
        goalCard.addView(tvGoal)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        goalCard.addView(bar, lp(MATCH, dp(14), 8))
        val fix = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fix.addView(pill("−1 fix") { Store.add(this, -1); refresh() })
        fix.addView(pill("+1 fix") { Store.add(this, 1); refresh() }, lp(WRAP, WRAP).apply { leftMargin = dp(8) })
        goalCard.addView(fix, lp(WRAP, WRAP, 10))

        // --- week
        val week = card(root, "Last 7 days")
        chart = WeekChartView(this)
        week.addView(chart, lp(MATCH, WRAP, 8))
        tvWeek = tv("", 13f, 0xFF5A5470.toInt())
        week.addView(tvWeek, lp(MATCH, WRAP, 6))

        // --- level
        val lvl = card(root, "Choose your level")
        val lrow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnL1 = pill("Level 1 · Manual") { Store.setLevel(this, Store.LEVEL_MANUAL); refresh() }
        btnL2 = pill("Level 2 · Auto") { Store.setLevel(this, Store.LEVEL_AUTO); refresh() }
        lrow.addView(btnL1, lp(0, WRAP, 0, 1f).apply { rightMargin = dp(4) })
        lrow.addView(btnL2, lp(0, WRAP, 0, 1f).apply { leftMargin = dp(4) })
        lvl.addView(lrow, lp(MATCH, WRAP, 10))
        tvLevelHint = tv("", 13f, 0xFF5A5470.toInt())
        lvl.addView(tvLevelHint, lp(MATCH, WRAP, 8))

        // --- giant mode
        val giant = card(root, "Monster mode 👹")
        giant.addView(tv("When on, the goblin keeps growing while you are inside Reels / Shorts and slowly covers the whole screen.", 13f, 0xFF5A5470.toInt()), lp(MATCH, WRAP, 4))
        val sw = Switch(this).apply {
            text = "Giant mode  "; textSize = 16f; isChecked = Store.coverMode(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Store.setCoverMode(this@MainActivity, on) }
        }
        giant.addView(sw, lp(MATCH, WRAP, 10))
        stepper(giant, "Fully covers at", { Store.coverFull(this) }, { Store.setCoverFull(this, it) }, 5, 10, 150) { "$it reels" }
        giant.addView(tv("Giant goblin: tap = go home · long-press = 60 s peek.", 12f, 0xFF7A7390.toInt()), lp(MATCH, WRAP, 8))

        // --- settings
        val set = card(root, "Settings")
        stepper(set, "Daily goal", { Store.goal(this) }, { Store.setGoal(this, it) }, 5, 0, 300) { if (it == 0) "off" else "$it" }
        stepper(set, "Seconds per reel", { Store.secPerReel(this) }, { Store.setSecPerReel(this, it) }, 5, 5, 120) { "${it}s" }
        val colors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        colors.addView(pill("🟢 Mint") { Store.setPalette(this, 0); refresh() })
        colors.addView(pill("🟣 Berry") { Store.setPalette(this, 1); refresh() }, lp(WRAP, WRAP).apply { leftMargin = dp(8) })
        colors.addView(pill("🍑 Peach") { Store.setPalette(this, 2); refresh() }, lp(WRAP, WRAP).apply { leftMargin = dp(8) })
        set.addView(tv("Goblin colour", 15f), lp(MATCH, WRAP, 12))
        set.addView(colors, lp(WRAP, WRAP, 6))

        // --- privacy
        val pr = card(root, "Private by design 🔒")
        pr.addView(tv("No login, no account, no internet permission. Your counts live only in this phone. The accessibility service only looks at Instagram and YouTube screens, on-device, to spot reels.", 13f, 0xFF5A5470.toInt()), lp(MATCH, WRAP, 4))
    }

    private fun accessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains(packageName) && s.contains("GoblinService")
    }

    private fun fmt(s: Int) = when {
        s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m"
        s >= 60 -> "${s / 60}m ${s % 60}s"
        else -> "${s}s"
    }

    private fun refresh() {
        val n = Store.count(this)
        val goal = Store.goal(this)
        goblin.count = n; goblin.stage = Lines.stageOf(n); goblin.palette = Store.palette(this)
        goblin.angry = goal > 0 && n > goal; goblin.invalidate()

        tvStatus.text = if (accessibilityOn()) "✅ Goblin is awake — open Instagram or YouTube!" else "😴 Goblin is asleep. Wake him up below."
        tvToday.text = n.toString()
        tvTime.text = fmt(Store.lostSeconds(this))
        if (goal > 0) {
            tvGoal.text = if (n <= goal) "Goal: $n / $goal  ·  ${goal - n} left" else "Goal: $n / $goal  ·  over by ${n - goal} 😤"
            bar.visibility = View.VISIBLE; bar.progress = (n * 100 / goal).coerceAtMost(100)
        } else { tvGoal.text = "No daily goal set"; bar.visibility = View.GONE }

        val labels = ArrayList<String>(); val vals = ArrayList<Int>(); var secs = 0
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -i)
            labels.add(SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time).take(3))
            vals.add(Store.count(this, Store.dayKey(i)))
            secs += Store.lostSeconds(this, Store.dayKey(i))
        }
        chart.set(labels, vals, goal)
        tvWeek.text = "7-day total ${vals.sum()}  ·  avg ${vals.sum() / 7}/day  ·  time lost ${fmt(secs)}"

        val auto = Store.level(this) == Store.LEVEL_AUTO
        fun style(b: TextView, on: Boolean) { b.background = bg(if (on) PURPLE else 0xFFEDE7FF.toInt(), 14); b.setTextColor(if (on) Color.WHITE else INK) }
        style(btnL1, !auto); style(btnL2, auto)
        tvLevelHint.text = if (auto) "Level 2: the goblin counts every new reel by itself. Tap = tickle, long-press = undo one."
        else "Level 1: tap the goblin after each reel. Long-press = undo one."
    }

    override fun onResume() { super.onResume(); h.post(ticker) }
    override fun onPause() { h.removeCallbacks(ticker); super.onPause() }
}
