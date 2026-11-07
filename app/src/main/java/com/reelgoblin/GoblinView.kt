package com.reelgoblin

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The cute monster, drawn with plain Canvas code so it scales from a 84dp bubble to a full-screen giant. */
class GoblinView(ctx: Context) : View(ctx) {
    var count = 0
    var stage = 0
    var palette = 0
    var angry = false

    companion object {
        val PALETTES = arrayOf(
            intArrayOf(0xFFA8F5C8.toInt(), 0xFF3FBF83.toInt()), // mint
            intArrayOf(0xFFE3BFFF.toInt(), 0xFF9B5DE5.toInt()), // berry
            intArrayOf(0xFFFFD2B8.toInt(), 0xFFFF8F5B.toInt())  // peach
        )
    }

    private var idle = 0f
    private var blink = 0f
    private var pop = 0f
    private val ev = ArgbEvaluator()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFF4A2433.toInt()
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = 0xFF3A2A4D.toInt()
    }
    private val path = Path()
    private val rect = RectF()
    private var shader: RadialGradient? = null
    private var shW = 0f; private var shH = 0f; private var shL = 0; private var shD = 0
    private var idleAnim: ValueAnimator? = null

    private val blinkTask = object : Runnable {
        override fun run() {
            blink = 1f; invalidate()
            postDelayed({ blink = 0f; invalidate() }, 130)
            postDelayed(this, 2200L + (Math.random() * 2500).toLong())
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        idleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { idle = it.animatedValue as Float; invalidate() }
            start()
        }
        postDelayed(blinkTask, 1500)
    }

    override fun onDetachedFromWindow() {
        idleAnim?.cancel(); removeCallbacks(blinkTask)
        super.onDetachedFromWindow()
    }

    fun pop() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            addUpdateListener { pop = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w < 2f || h < 2f) return
        val pal = PALETTES[palette.mod(PALETTES.size)]
        var lite = pal[0]; var dark = pal[1]
        if (angry) {
            lite = ev.evaluate(0.5f, lite, 0xFFFFA08F.toInt()) as Int
            dark = ev.evaluate(0.55f, dark, 0xFFE0524A.toInt()) as Int
        }
        val m = min(w, h)
        fun r(a: Float, b: Float, c: Float, d: Float) = rect.set(a * w, b * h, c * w, d * h)

        val sc = 1f + 0.07f * sin(pop * Math.PI.toFloat())
        val bob = (idle - 0.5f) * h * 0.03f
        cv.save()
        cv.scale(sc, sc, w / 2, h * 0.6f)
        cv.translate(0f, bob)
        p.shader = null; p.style = Paint.Style.FILL

        // horns
        p.color = 0xFFFFD36B.toInt()
        path.reset(); path.moveTo(w * .28f, h * .26f); path.lineTo(w * .24f, h * .07f); path.lineTo(w * .44f, h * .20f); path.close(); cv.drawPath(path, p)
        path.reset(); path.moveTo(w * .72f, h * .26f); path.lineTo(w * .76f, h * .07f); path.lineTo(w * .56f, h * .20f); path.close(); cv.drawPath(path, p)
        // ears
        p.color = dark
        path.reset(); path.moveTo(w * .16f, h * .36f); path.lineTo(0f, h * .24f); path.lineTo(w * .10f, h * .58f); path.close(); cv.drawPath(path, p)
        path.reset(); path.moveTo(w * .84f, h * .36f); path.lineTo(w, h * .24f); path.lineTo(w * .90f, h * .58f); path.close(); cv.drawPath(path, p)
        p.color = 0xFFFFB3C7.toInt()
        path.reset(); path.moveTo(w * .13f, h * .40f); path.lineTo(w * .04f, h * .30f); path.lineTo(w * .10f, h * .52f); path.close(); cv.drawPath(path, p)
        path.reset(); path.moveTo(w * .87f, h * .40f); path.lineTo(w * .96f, h * .30f); path.lineTo(w * .90f, h * .52f); path.close(); cv.drawPath(path, p)

        // body
        if (shader == null || shW != w || shH != h || shL != lite || shD != dark) {
            shader = RadialGradient(w * .4f, h * .35f, max(w, h) * .8f, lite, dark, Shader.TileMode.CLAMP)
            shW = w; shH = h; shL = lite; shD = dark
        }
        p.shader = shader
        r(.07f, .17f, .93f, .95f); cv.drawOval(rect, p)
        p.shader = null

        // belly + counter number
        p.color = Color.argb(170, 255, 255, 255)
        r(.26f, .66f, .74f, .92f); cv.drawOval(rect, p)
        val s = count.toString()
        val bw = rect.width(); val bh = rect.height()
        text.textSize = bh * 0.8f
        val tw = text.measureText(s)
        if (tw > bw * 0.85f) text.textSize = text.textSize * bw * 0.85f / tw
        val fm = text.fontMetrics
        cv.drawText(s, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2f, text)

        // cheeks
        p.color = Color.argb(150, 255, 120, 160)
        r(.14f, .52f, .26f, .60f); cv.drawOval(rect, p)
        r(.74f, .52f, .86f, .60f); cv.drawOval(rect, p)

        // eyes
        val look = (idle - 0.5f) * 0.02f * w
        for (ex in floatArrayOf(.33f, .67f)) {
            val ry = .125f * (1f - blink * 0.9f)
            p.color = Color.WHITE
            r(ex - .115f, .43f - ry, ex + .115f, .43f + ry); cv.drawOval(rect, p)
            p.color = 0xFF3A2A4D.toInt()
            val pr = .06f
            rect.set(ex * w - pr * w + look, (.44f - ry * .55f) * h, ex * w + pr * w + look, (.44f + ry * .55f) * h)
            if (blink < .5f) cv.drawOval(rect, p)
            p.color = Color.WHITE
            if (blink < .5f) cv.drawCircle(ex * w + look + pr * w * .35f, .41f * h, m * .012f, p)
        }
        line.strokeWidth = m * 0.03f
        if (angry) {
            cv.drawLine(w * .20f, h * .27f, w * .40f, h * .34f, line)
            cv.drawLine(w * .80f, h * .27f, w * .60f, h * .34f, line)
        }

        // mouth, changes with growth stage
        when {
            stage >= 4 -> { // big roaring mouth with fangs
                p.color = 0xFF5B1F33.toInt()
                r(.34f, .52f, .66f, .68f); cv.drawOval(rect, p)
                p.color = Color.WHITE
                path.reset(); path.moveTo(w * .39f, h * .555f); path.lineTo(w * .44f, h * .555f); path.lineTo(w * .415f, h * .615f); path.close(); cv.drawPath(path, p)
                path.reset(); path.moveTo(w * .56f, h * .555f); path.lineTo(w * .61f, h * .555f); path.lineTo(w * .585f, h * .615f); path.close(); cv.drawPath(path, p)
                p.color = 0xFFFF8FA8.toInt()
                r(.42f, .62f, .58f, .68f); cv.drawOval(rect, p)
            }
            angry -> { // frown
                line.style = Paint.Style.STROKE
                r(.41f, .60f, .59f, .70f); cv.drawArc(rect, 200f, 140f, false, line)
            }
            stage == 3 -> { // wide grin
                p.color = 0xFF5B1F33.toInt()
                r(.36f, .50f, .64f, .70f); cv.drawArc(rect, 0f, 180f, true, p)
                p.color = Color.WHITE
                rect.set(w * .40f, h * .60f, w * .45f, h * .625f); cv.drawRect(rect, p)
                p.color = 0xFFFF8FA8.toInt()
                r(.45f, .645f, .55f, .695f); cv.drawOval(rect, p)
            }
            stage == 2 -> { // hungry little "o"
                p.color = 0xFF5B1F33.toInt()
                r(.46f, .56f, .54f, .64f); cv.drawOval(rect, p)
            }
            else -> { // happy smile
                r(.41f, .52f, .59f, .62f); cv.drawArc(rect, 15f, 150f, false, line)
            }
        }
        cv.restore()
    }
}
