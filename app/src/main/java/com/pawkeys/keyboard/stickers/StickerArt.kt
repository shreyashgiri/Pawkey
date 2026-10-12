package com.pawkeys.keyboard.stickers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/** The cat's expression on a sticker. */
enum class Mood { HAPPY, LAUGH, LOVE, SHOCKED, SAD, GRUMPY, SLEEPY, WINK, COOL, THANKS, YUM, MISS }

/** One sticker of the built-in "Whiskers" pack. */
data class Sticker(val id: String, val mood: Mood, val caption: String, val fur: Int, val inner: Int)

/**
 * Draws the Whiskers sticker pack: round cat faces with different expressions, a
 * caption, and the white die-cut outline stickers usually have. All original
 * artwork drawn in code.
 */
object StickerArt {
    private const val GINGER = 0xFFF0A257.toInt()
    private const val GREY = 0xFFA8AEB8.toInt()
    private const val BLACK = 0xFF3A3A40.toInt()
    private const val CREAM = 0xFFF4DEC0.toInt()
    private const val WHITE = 0xFFF7F4EF.toInt()
    private const val PINK = 0xFFF6A7B5.toInt()

    val pack = listOf(
        Sticker("hi", Mood.HAPPY, "Hi!", GINGER, PINK),
        Sticker("lol", Mood.LAUGH, "LOL", GREY, PINK),
        Sticker("love", Mood.LOVE, "Love it", CREAM, PINK),
        Sticker("omg", Mood.SHOCKED, "OMG", BLACK, PINK),
        Sticker("sorry", Mood.SAD, "Sorry", GREY, PINK),
        Sticker("grr", Mood.GRUMPY, "Grr", GINGER, PINK),
        Sticker("night", Mood.SLEEPY, "Good night", BLACK, PINK),
        Sticker("sure", Mood.WINK, "Sure", CREAM, PINK),
        Sticker("cool", Mood.COOL, "Cool", GINGER, PINK),
        Sticker("thanks", Mood.THANKS, "Thanks", WHITE, PINK),
        Sticker("yum", Mood.YUM, "Yum", GREY, PINK),
        Sticker("miss", Mood.MISS, "Miss you", WHITE, PINK),
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private const val INK = 0xFF2B2420.toInt()
    private const val LIGHT_INK = 0xFFF4EFE8.toInt()

    /** Colour for eyes, mouth and whiskers: light on dark fur so they stay visible. */
    private var features = INK

    /** Renders [sticker] as a square bitmap with a transparent background. */
    fun render(sticker: Sticker, size: Int): Bitmap {
        val art = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawArt(Canvas(art), sticker, size.toFloat())
        // Die-cut outline: stamp the art's silhouette in white around itself.
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val mask = art.extractAlpha()
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
        val r = size * 0.025f
        c.drawBitmap(mask, 0f, r * 0.6f + r, shadow)
        for (i in 0 until 24) {
            val a = i * Math.PI * 2 / 24
            c.drawBitmap(mask, (cos(a) * r).toFloat(), (sin(a) * r).toFloat(), white)
        }
        c.drawBitmap(art, 0f, 0f, null)
        mask.recycle()
        art.recycle()
        return out
    }

    private fun drawArt(c: Canvas, st: Sticker, s: Float) {
        val cx = s / 2f
        val cy = s * 0.43f
        val r = s * 0.30f
        line.color = INK
        line.strokeWidth = s * 0.018f

        // Ears.
        val ears = Path().apply {
            moveTo(cx - r * 0.95f, cy - r * 0.25f); lineTo(cx - r * 0.80f, cy - r * 1.18f); lineTo(cx - r * 0.20f, cy - r * 0.86f); close()
            moveTo(cx + r * 0.95f, cy - r * 0.25f); lineTo(cx + r * 0.80f, cy - r * 1.18f); lineTo(cx + r * 0.20f, cy - r * 0.86f); close()
        }
        fill.color = st.fur
        c.drawPath(ears, fill)
        c.drawPath(ears, line)
        fill.color = st.inner
        c.drawPath(Path().apply {
            moveTo(cx - r * 0.78f, cy - r * 0.45f); lineTo(cx - r * 0.72f, cy - r * 0.98f); lineTo(cx - r * 0.38f, cy - r * 0.80f); close()
            moveTo(cx + r * 0.78f, cy - r * 0.45f); lineTo(cx + r * 0.72f, cy - r * 0.98f); lineTo(cx + r * 0.38f, cy - r * 0.80f); close()
        }, fill)

        // Head: a soft, slightly wide oval.
        val head = RectF(cx - r * 1.08f, cy - r * 0.92f, cx + r * 1.08f, cy + r * 0.86f)
        fill.color = st.fur
        c.drawOval(head, fill)
        c.drawOval(head, line)

        features = if (luminance(st.fur) < 0.3f) LIGHT_INK else INK

        // Cheeks.
        if (st.mood in setOf(Mood.HAPPY, Mood.LOVE, Mood.THANKS, Mood.WINK, Mood.MISS, Mood.YUM, Mood.LAUGH)) {
            fill.color = 0x66FF7A8A
            c.drawCircle(cx - r * 0.62f, cy + r * 0.22f, r * 0.15f, fill)
            c.drawCircle(cx + r * 0.62f, cy + r * 0.22f, r * 0.15f, fill)
        }

        drawEyes(c, st.mood, cx, cy, r, s)
        drawMouth(c, st.mood, cx, cy, r, s, st.inner)

        // Whiskers.
        line.color = features
        line.strokeWidth = s * 0.010f
        for (side in listOf(-1f, 1f)) {
            for (k in -1..1) {
                c.drawLine(cx + side * r * 0.70f, cy + r * (0.22f + k * 0.10f), cx + side * r * 1.25f, cy + r * (0.14f + k * 0.18f), line)
            }
        }

        // Extras.
        when (st.mood) {
            Mood.LOVE, Mood.MISS -> heart(c, cx + r * 1.05f, cy - r * 0.95f, r * 0.30f, 0xFFE8485F.toInt())
            Mood.SLEEPY -> {
                text.style = Paint.Style.FILL
                text.color = INK; text.textSize = s * 0.07f
                c.drawText("z", cx + r * 1.0f, cy - r * 0.8f, text)
                text.textSize = s * 0.095f
                c.drawText("Z", cx + r * 1.25f, cy - r * 1.1f, text)
            }
            Mood.SHOCKED -> {
                line.color = INK
                line.strokeWidth = s * 0.014f
                c.drawLine(cx - r * 1.35f, cy - r * 0.9f, cx - r * 1.2f, cy - r * 0.6f, line)
                c.drawLine(cx + r * 1.35f, cy - r * 0.9f, cx + r * 1.2f, cy - r * 0.6f, line)
            }
            Mood.SAD -> {
                fill.color = 0xFF7FC4F2.toInt()
                c.drawCircle(cx + r * 0.42f, cy + r * 0.12f, r * 0.08f, fill)
            }
            else -> Unit
        }

        // Caption with an ink outline.
        text.textSize = s * 0.115f
        text.letterSpacing = 0.03f
        text.style = Paint.Style.STROKE
        text.strokeWidth = s * 0.024f
        text.color = INK
        val ty = s * 0.90f
        c.drawText(st.caption, cx, ty, text)
        text.style = Paint.Style.FILL
        text.color = 0xFFFFFFFF.toInt()
        c.drawText(st.caption, cx, ty, text)
        text.letterSpacing = 0f
        line.color = INK
    }

    private fun luminance(color: Int): Float {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun drawEyes(c: Canvas, mood: Mood, cx: Float, cy: Float, r: Float, s: Float) {
        val ex = r * 0.40f
        val ey = cy - r * 0.10f
        line.color = features
        line.strokeWidth = s * 0.02f
        fill.color = features
        fun arcEye(x: Float, up: Boolean) {
            val box = RectF(x - r * 0.16f, ey - r * 0.12f, x + r * 0.16f, ey + r * 0.12f)
            c.drawArc(box, if (up) 200f else 20f, 140f, false, line)
        }
        when (mood) {
            Mood.HAPPY, Mood.YUM -> {
                c.drawCircle(cx - ex, ey, r * 0.10f, fill); c.drawCircle(cx + ex, ey, r * 0.10f, fill)
                fill.color = 0xFFFFFFFF.toInt()
                c.drawCircle(cx - ex + r * 0.035f, ey - r * 0.04f, r * 0.035f, fill); c.drawCircle(cx + ex + r * 0.035f, ey - r * 0.04f, r * 0.035f, fill)
            }
            Mood.LAUGH, Mood.THANKS -> { arcEye(cx - ex, true); arcEye(cx + ex, true) }
            Mood.SLEEPY -> { arcEye(cx - ex, false); arcEye(cx + ex, false) }
            Mood.WINK -> {
                c.drawCircle(cx - ex, ey, r * 0.10f, fill)
                arcEye(cx + ex, true)
            }
            Mood.LOVE, Mood.MISS -> {
                heart(c, cx - ex, ey, r * 0.16f, 0xFFE8485F.toInt())
                heart(c, cx + ex, ey, r * 0.16f, 0xFFE8485F.toInt())
            }
            Mood.SHOCKED -> {
                fill.color = 0xFFFFFFFF.toInt()
                c.drawCircle(cx - ex, ey, r * 0.17f, fill); c.drawCircle(cx + ex, ey, r * 0.17f, fill)
                c.drawCircle(cx - ex, ey, r * 0.17f, line); c.drawCircle(cx + ex, ey, r * 0.17f, line)
                fill.color = INK
                c.drawCircle(cx - ex, ey, r * 0.06f, fill); c.drawCircle(cx + ex, ey, r * 0.06f, fill)
                line.color = features
            }
            Mood.SAD -> {
                c.drawCircle(cx - ex, ey + r * 0.02f, r * 0.09f, fill); c.drawCircle(cx + ex, ey + r * 0.02f, r * 0.09f, fill)
                c.drawLine(cx - ex - r * 0.15f, ey - r * 0.12f, cx - ex + r * 0.10f, ey - r * 0.22f, line)
                c.drawLine(cx + ex + r * 0.15f, ey - r * 0.12f, cx + ex - r * 0.10f, ey - r * 0.22f, line)
            }
            Mood.GRUMPY -> {
                c.drawCircle(cx - ex, ey + r * 0.03f, r * 0.08f, fill); c.drawCircle(cx + ex, ey + r * 0.03f, r * 0.08f, fill)
                c.drawLine(cx - ex - r * 0.16f, ey - r * 0.20f, cx - ex + r * 0.12f, ey - r * 0.08f, line)
                c.drawLine(cx + ex + r * 0.16f, ey - r * 0.20f, cx + ex - r * 0.12f, ey - r * 0.08f, line)
            }
            Mood.COOL -> {
                // Sunglasses.
                fill.color = 0xFF1E1E22.toInt()
                c.drawRoundRect(RectF(cx - ex - r * 0.24f, ey - r * 0.12f, cx - ex + r * 0.22f, ey + r * 0.16f), r * 0.08f, r * 0.08f, fill)
                c.drawRoundRect(RectF(cx + ex - r * 0.22f, ey - r * 0.12f, cx + ex + r * 0.24f, ey + r * 0.16f), r * 0.08f, r * 0.08f, fill)
                c.drawLine(cx - ex + r * 0.2f, ey - r * 0.06f, cx + ex - r * 0.2f, ey - r * 0.06f, line)
                fill.color = 0x66FFFFFF
                c.drawRect(cx - ex - r * 0.15f, ey - r * 0.08f, cx - ex - r * 0.07f, ey + r * 0.02f, fill)
            }
        }
    }

    private fun drawMouth(c: Canvas, mood: Mood, cx: Float, cy: Float, r: Float, s: Float, inner: Int) {
        val my = cy + r * 0.28f
        // Nose.
        fill.color = 0xFFE07A8A.toInt()
        c.drawPath(Path().apply {
            moveTo(cx - r * 0.08f, my - r * 0.12f); lineTo(cx + r * 0.08f, my - r * 0.12f); lineTo(cx, my - r * 0.03f); close()
        }, fill)
        line.color = features
        line.strokeWidth = s * 0.016f
        when (mood) {
            Mood.LAUGH, Mood.SHOCKED -> {
                fill.color = 0xFF7A2E38.toInt()
                val box = if (mood == Mood.LAUGH) RectF(cx - r * 0.22f, my - r * 0.02f, cx + r * 0.22f, my + r * 0.30f)
                else RectF(cx - r * 0.12f, my, cx + r * 0.12f, my + r * 0.26f)
                if (mood == Mood.LAUGH) c.drawArc(box, 0f, 180f, true, fill) else c.drawOval(box, fill)
            }
            Mood.SAD, Mood.GRUMPY -> {
                c.drawArc(RectF(cx - r * 0.18f, my + r * 0.04f, cx + r * 0.18f, my + r * 0.24f), 200f, 140f, false, line)
            }
            Mood.YUM -> {
                c.drawArc(RectF(cx - r * 0.16f, my - r * 0.06f, cx, my + r * 0.08f), 0f, 180f, false, line)
                c.drawArc(RectF(cx, my - r * 0.06f, cx + r * 0.16f, my + r * 0.08f), 0f, 180f, false, line)
                fill.color = inner
                c.drawOval(RectF(cx - r * 0.06f, my + r * 0.04f, cx + r * 0.10f, my + r * 0.22f), fill)
            }
            else -> {
                // The classic "w" cat mouth.
                c.drawArc(RectF(cx - r * 0.16f, my - r * 0.06f, cx, my + r * 0.08f), 0f, 180f, false, line)
                c.drawArc(RectF(cx, my - r * 0.06f, cx + r * 0.16f, my + r * 0.08f), 0f, 180f, false, line)
            }
        }
    }

    private fun heart(c: Canvas, x: Float, y: Float, size: Float, color: Int) {
        fill.color = color
        val p = Path().apply {
            moveTo(x, y + size * 0.9f)
            cubicTo(x - size * 1.4f, y - size * 0.1f, x - size * 0.6f, y - size * 1.0f, x, y - size * 0.35f)
            cubicTo(x + size * 0.6f, y - size * 1.0f, x + size * 1.4f, y - size * 0.1f, x, y + size * 0.9f)
            close()
        }
        c.drawPath(p, fill)
    }
}
