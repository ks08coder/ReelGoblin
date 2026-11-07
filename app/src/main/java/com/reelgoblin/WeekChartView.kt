package com.reelgoblin

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

/** Tiny hand-drawn bar chart for the last 7 days. Today = purple, over goal = red, goal = dashed line. */
class WeekChartView(ctx: Context) : View(ctx) {
    private var labels: List<String> = emptyList()
    private var values: List<Int> = emptyList()
    private var goal = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun set(l: List<String>, v: List<Int>, g: Int) { labels = l; values = v; goal = g; invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (200 * resources.displayMetrics.density).toInt())
    }

    override fun onDraw(c: Canvas) {
        val n = values.size
        if (n == 0) return
        val d = resources.displayMetrics.density
        val top = 26 * d; val bottom = height - 26 * d
        val left = 6 * d; val right = width - 6 * d
        val maxV = max(max(values.maxOrNull() ?: 0, goal), 5).toFloat()
        val slot = (right - left) / n
        val bw = slot * 0.56f

        if (goal > 0) {
            val gy = bottom - (bottom - top) * goal / maxV
            p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = 0xFFB0A8C8.toInt()
            p.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            c.drawLine(left, gy, right, gy, p)
            p.pathEffect = null
        }
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        for (i in 0 until n) {
            val v = values[i]
            val bh = if (v == 0) 3 * d else (bottom - top) * v / maxV
            val x0 = left + slot * i + (slot - bw) / 2
            p.color = when {
                v == 0 -> 0xFFD9D4E6.toInt()
                i == n - 1 -> 0xFF7C4DFF.toInt()
                goal > 0 && v > goal -> 0xFFFF7A70.toInt()
                else -> 0xFF6FD9A3.toInt()
            }
            rect.set(x0, bottom - bh, x0 + bw, bottom)
            c.drawRoundRect(rect, 6 * d, 6 * d, p)
            p.color = 0xFF3A2A4D.toInt(); p.textSize = 12 * d
            c.drawText(v.toString(), x0 + bw / 2, bottom - bh - 5 * d, p)
            p.color = 0xFF7A7390.toInt(); p.textSize = 11 * d
            c.drawText(labels.getOrElse(i) { "" }, x0 + bw / 2, height - 7 * d, p)
        }
    }
}
