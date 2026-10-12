package com.pawkeys.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The Prowl (swipe typing) trail: a smooth, tapering curve that follows the finger,
 * with a little cat galloping after the fingertip. Everything is drawn in code so
 * it takes the current coat's accent colour.
 */
class ProwlTrail(private val density: Float) {
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()
    private val path = Path()
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val catFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val catStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val catPath = Path()

    // Cat state.
    private var catX = 0f
    private var catY = 0f
    private var facingRight = true
    private var tilt = 0f
    private var phase = 0f
    private var hasCat = false

    /** After the finger lifts the trail fades out instead of vanishing. */
    private var fadeStart = 0L
    var showCat = true

    private fun dp(v: Float) = v * density
    private val catSize get() = dp(34f)
    /** The cat runs a little above the fingertip so the finger doesn't hide it. */
    private val catLift get() = dp(30f)

    val isAnimating: Boolean
        get() = xs.isNotEmpty() && (fadeStart == 0L || SystemClock.uptimeMillis() - fadeStart < FADE_MS)

    fun start(x: Float, y: Float) {
        xs.clear(); ys.clear()
        fadeStart = 0L
        xs += x; ys += y
        catX = x; catY = y - catLift
        phase = 0f
        hasCat = true
    }

    fun add(x: Float, y: Float) {
        xs += x; ys += y
        chase(0.35f)
    }

    /** Finger lifted: keep the trail for a short fade. */
    fun release() {
        if (xs.isNotEmpty()) fadeStart = SystemClock.uptimeMillis()
    }

    fun clear() {
        xs.clear(); ys.clear(); hasCat = false; fadeStart = 0L
    }

    /** Moves the cat part of the way towards the fingertip and advances its gallop. */
    private fun chase(amount: Float) {
        if (xs.isEmpty()) return
        val tx = xs.last()
        val ty = ys.last() - catLift
        val dx = (tx - catX) * amount
        val dy = (ty - catY) * amount
        catX += dx
        catY += dy
        val moved = hypot(dx, dy)
        phase += moved / (catSize * 0.22f)
        if (abs(dx) > dp(0.6f)) facingRight = dx > 0
        if (moved > dp(0.5f)) {
            val target = Math.toDegrees(atan2(dy.toDouble(), abs(dx).toDouble() + 0.001)).toFloat().coerceIn(-28f, 28f)
            tilt += (target - tilt) * 0.3f
        } else {
            tilt *= 0.85f
        }
    }

    /** Draws the trail and cat; returns true while another frame is needed. */
    fun draw(canvas: Canvas, color: Int, eyeColor: Int): Boolean {
        if (xs.size < 2) return false
        val fade = if (fadeStart == 0L) 1f else 1f - (SystemClock.uptimeMillis() - fadeStart) / FADE_MS.toFloat()
        if (fade <= 0f) { clear(); return false }
        if (fadeStart == 0L) chase(0.25f)
        drawCurve(canvas, color, fade)
        if (showCat && hasCat) drawCat(canvas, color, eyeColor, fade)
        return true
    }

    private fun drawCurve(canvas: Canvas, color: Int, fade: Float) {
        // Draw the newest ~90 points in chunks that get wider and stronger towards
        // the finger, each chunk smoothed with quadratic curves through midpoints.
        val n = xs.size
        val from = max(0, n - 90)
        val chunks = 10
        val per = max(2, (n - from) / chunks)
        trailPaint.color = color
        var start = from
        var i = 0
        while (start < n - 1) {
            val end = min(n - 1, start + per)
            val t = (i + 1f) / chunks
            trailPaint.strokeWidth = dp(1.5f) + dp(4.5f) * t.coerceAtMost(1f)
            trailPaint.alpha = ((40 + 170 * t.coerceAtMost(1f)) * fade).toInt()
            // Flat ends where chunks meet (round ones overlap and show as dots);
            // only the newest chunk gets a round tip under the finger.
            trailPaint.strokeCap = if (end == n - 1) Paint.Cap.ROUND else Paint.Cap.BUTT
            path.reset()
            path.moveTo(xs[start], ys[start])
            for (k in start + 1 until end) {
                val mx = (xs[k] + xs[k + 1]) / 2f
                val my = (ys[k] + ys[k + 1]) / 2f
                path.quadTo(xs[k], ys[k], mx, my)
            }
            path.lineTo(xs[end], ys[end])
            canvas.drawPath(path, trailPaint)
            start = end
            i++
        }
    }

    /** A side-on cat silhouette mid-gallop. Unit = cat size; facing right before mirroring. */
    private fun drawCat(canvas: Canvas, color: Int, eyeColor: Int, fade: Float) {
        val s = catSize
        val alpha = (255 * fade).toInt()
        catFill.color = color; catFill.alpha = alpha
        catStroke.color = color; catStroke.alpha = alpha
        val bob = sin(phase * 2f) * 0.03f * s

        canvas.save()
        canvas.translate(catX, catY + bob)
        if (!facingRight) canvas.scale(-1f, 1f)
        canvas.rotate(tilt)

        // Legs: a gallop, front pair and back pair half a cycle apart.
        catStroke.strokeWidth = 0.085f * s
        val legLen = 0.30f * s
        fun leg(hipX: Float, offset: Float) {
            val a = sin(phase + offset) * 0.75f
            canvas.drawLine(hipX * s, 0.08f * s, hipX * s + sin(a) * legLen, 0.08f * s + cos(a) * legLen, catStroke)
        }
        leg(0.22f, 0f); leg(0.30f, 0.6f)
        leg(-0.30f, Math.PI.toFloat()); leg(-0.22f, Math.PI.toFloat() + 0.6f)

        // Body: stretches a little on each stride.
        val stretch = 1f + sin(phase) * 0.05f
        canvas.drawRoundRect(-0.44f * s * stretch, -0.17f * s, 0.36f * s * stretch, 0.15f * s, 0.16f * s, 0.16f * s, catFill)

        // Tail: a curve that sways with the stride.
        val sway = sin(phase * 0.5f) * 0.12f * s
        catStroke.strokeWidth = 0.08f * s
        catPath.reset()
        catPath.moveTo(-0.40f * s, -0.08f * s)
        catPath.cubicTo(-0.70f * s, -0.10f * s + sway, -0.78f * s, -0.40f * s, -0.62f * s, -0.52f * s - sway)
        canvas.drawPath(catPath, catStroke)

        // Head and ears.
        canvas.drawCircle(0.46f * s, -0.20f * s, 0.19f * s, catFill)
        catPath.reset()
        catPath.moveTo(0.32f * s, -0.30f * s); catPath.lineTo(0.35f * s, -0.52f * s); catPath.lineTo(0.47f * s, -0.36f * s); catPath.close()
        catPath.moveTo(0.48f * s, -0.37f * s); catPath.lineTo(0.61f * s, -0.52f * s); catPath.lineTo(0.62f * s, -0.30f * s); catPath.close()
        canvas.drawPath(catPath, catFill)

        // Eye.
        catFill.color = eyeColor; catFill.alpha = alpha
        canvas.drawCircle(0.53f * s, -0.22f * s, 0.032f * s, catFill)
        canvas.restore()
    }

    companion object {
        private const val FADE_MS = 260L
    }
}
