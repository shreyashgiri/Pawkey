package com.pawkeys.keyboard.panels

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.R
import kotlin.math.abs

/** Samsung-style emoji panel: grid on top, categories along the bottom. */
@SuppressLint("ViewConstructor")
class EmojiPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val prefs: SharedPreferences,
    private val onEmoji: (String) -> Unit,
    private val onDelete: () -> Unit,
    private val onBack: () -> Unit,
) : LinearLayout(context) {

    private val grid = EmojiGrid(context)
    private val scroll = ScrollView(context)
    private val title = TextView(context)
    private val tabs = ArrayList<ImageView>()
    private var current = -1

    private val recent: MutableList<String> =
        prefs.getString(KEY_RECENTS, "")!!.split(SEP).filter { it.isNotEmpty() }.toMutableList()

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)

        title.apply {
            setTextColor(theme.hintText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(UiKit.dp(context, 12f), UiKit.dp(context, 4f), 0, UiKit.dp(context, 2f))
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(UiKit.textButton(context, theme, "ABC") { onBack() }, UiKit.weighted(1.4f, 3f, context))
        val categories = listOf(R.drawable.ic_schedule) + EmojiData.categories.map { it.icon }
        categories.forEachIndexed { i, icon ->
            val tab = ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, icon)!!.mutate())
                scaleType = ImageView.ScaleType.CENTER
                contentDescription = if (i == 0) "Recent" else EmojiData.categories[i - 1].name
                setOnClickListener { select(i) }
            }
            tabs += tab
            bar.addView(tab, UiKit.weighted(1f, 0f, context))
        }
        val del = UiKit.iconButton(context, theme, R.drawable.ic_backspace, "Delete") { }
        UiKit.repeatOnHold(del) { onDelete() }
        bar.addView(del, UiKit.weighted(1.4f, 3f, context))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 46f)))

        select(if (recent.isEmpty()) 1 else 0)
    }

    private fun select(index: Int) {
        current = index
        tabs.forEachIndexed { i, t -> t.drawable.setTint(if (i == index) theme.accent else theme.hintText) }
        if (index == 0) {
            title.text = "Recently used"
            grid.emojis = recent.toList()
        } else {
            val cat = EmojiData.categories[index - 1]
            title.text = cat.name
            grid.emojis = cat.emojis
        }
        scroll.scrollTo(0, 0)
    }

    private fun pick(emoji: String) {
        onEmoji(emoji)
        recent.remove(emoji)
        recent.add(0, emoji)
        while (recent.size > 32) recent.removeAt(recent.size - 1)
        prefs.edit().putString(KEY_RECENTS, recent.joinToString(SEP)).apply()
    }

    private inner class EmojiGrid(context: Context) : View(context) {
        private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        /** Only emojis this phone's font can draw (older Androids lack newer ones). */
        var emojis: List<String> = emptyList()
            set(v) { field = v.filter { paint.hasGlyph(it) }; requestLayout(); invalidate() }
        private val columns = 8
        private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f
        private var downY = 0f
        private var pressed = -1

        private val cell get() = width / columns.toFloat()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val rows = (emojis.size + columns - 1) / columns
            val empty = if (emojis.isEmpty()) UiKit.dp(context, 80f) else 0
            setMeasuredDimension(w, (rows * (w / columns.toFloat())).toInt() + empty)
        }

        override fun onDraw(canvas: Canvas) {
            val c = cell
            if (emojis.isEmpty()) {
                paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)
                paint.color = theme.hintText
                canvas.drawText("Emojis you use will curl up here 🐾", width / 2f, height / 2f, paint)
                return
            }
            paint.textSize = c * 0.58f
            paint.color = theme.keyText
            emojis.forEachIndexed { i, e ->
                val x = (i % columns) * c
                val y = (i / columns) * c
                if (i == pressed) {
                    pressPaint.color = theme.keyPressed
                    canvas.drawRoundRect(x + 4, y + 4, x + c - 4, y + c - 4, c / 4, c / 4, pressPaint)
                }
                canvas.drawText(e, x + c / 2, y + c / 2 - (paint.descent() + paint.ascent()) / 2, paint)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y
                    pressed = indexAt(e.x, e.y); invalidate()
                }
                MotionEvent.ACTION_MOVE -> if (abs(e.y - downY) > cell / 3 || abs(e.x - downX) > cell / 3) { pressed = -1; invalidate() }
                MotionEvent.ACTION_UP -> {
                    val i = pressed
                    pressed = -1; invalidate()
                    if (i in emojis.indices) { performClick(); pick(emojis[i]) }
                }
                MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
            }
            return true
        }

        override fun performClick() = super.performClick()

        private fun indexAt(x: Float, y: Float): Int {
            val i = (y / cell).toInt() * columns + (x / cell).toInt().coerceIn(0, columns - 1)
            return if (i in emojis.indices) i else -1
        }
    }

    companion object {
        private const val KEY_RECENTS = "emoji_recents"
        private const val SEP = "\u001F"
    }
}
