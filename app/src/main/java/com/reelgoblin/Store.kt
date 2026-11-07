package com.reelgoblin

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** All data lives in one local SharedPreferences file. Counts are written with commit() so they survive reboots. */
object Store {
    const val LEVEL_MANUAL = 1
    const val LEVEL_AUTO = 2

    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("reelgoblin", Context.MODE_PRIVATE)

    fun dayKey(back: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -back)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    fun count(c: Context, day: String = dayKey()): Int = p(c).getInt("count_$day", 0)

    fun add(c: Context, delta: Int): Int {
        val key = "count_${dayKey()}"
        val n = (p(c).getInt(key, 0) + delta).coerceAtLeast(0)
        p(c).edit().putInt(key, n).commit()
        return n
    }

    fun seconds(c: Context, day: String = dayKey()): Int = p(c).getInt("sec_$day", 0)

    fun addSeconds(c: Context, s: Int) {
        val key = "sec_${dayKey()}"
        p(c).edit().putInt(key, p(c).getInt(key, 0) + s).apply()
    }

    fun level(c: Context) = p(c).getInt("level", LEVEL_MANUAL)
    fun setLevel(c: Context, v: Int) { p(c).edit().putInt("level", v).apply() }

    fun coverMode(c: Context) = p(c).getBoolean("cover", false)
    fun setCoverMode(c: Context, v: Boolean) { p(c).edit().putBoolean("cover", v).apply() }

    fun coverFull(c: Context) = p(c).getInt("coverFull", 40)
    fun setCoverFull(c: Context, v: Int) { p(c).edit().putInt("coverFull", v).apply() }

    fun goal(c: Context) = p(c).getInt("goal", 30)
    fun setGoal(c: Context, v: Int) { p(c).edit().putInt("goal", v).apply() }

    fun secPerReel(c: Context) = p(c).getInt("secPerReel", 25)
    fun setSecPerReel(c: Context, v: Int) { p(c).edit().putInt("secPerReel", v).apply() }

    fun palette(c: Context) = p(c).getInt("palette", 0)
    fun setPalette(c: Context, v: Int) { p(c).edit().putInt("palette", v).apply() }

    fun posX(c: Context, def: Int) = p(c).getInt("posX", def)
    fun posY(c: Context, def: Int) = p(c).getInt("posY", def)
    fun setPos(c: Context, x: Int, y: Int) { p(c).edit().putInt("posX", x).putInt("posY", y).apply() }

    /** Time lost = the bigger of (really measured time inside reels) and (count x seconds-per-reel). */
    fun lostSeconds(c: Context, day: String = dayKey()): Int =
        maxOf(seconds(c, day), count(c, day) * secPerReel(c))
}
