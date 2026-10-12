package com.pawkeys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.pawkeys.keyboard.engine.Suggestion

/**
 * The bar above the keys. Like Samsung's keyboard it shows a toolbar (emoji,
 * clipboard, text editing, one-handed mode, ...) when you're not typing, and
 * three suggestions (best one in the middle) while you are.
 */
@SuppressLint("ViewConstructor")
class CandidateStrip(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onToolbarAction(action: ToolbarAction)
        fun onSuggestionPicked(index: Int, suggestion: Suggestion)
        fun onSuggestionLongPressed(suggestion: Suggestion)
    }

    enum class ToolbarAction(val icon: Int) {
        MENU(R.drawable.ic_pets),
        EMOJI(R.drawable.ic_mood),
        CLIPBOARD(R.drawable.ic_content_paste),
        TEXT_EDIT(R.drawable.ic_edit_note),
        ONE_HANDED(R.drawable.ic_front_hand),
        SIZE(R.drawable.ic_height),
        SETTINGS(R.drawable.ic_settings),
        HIDE(R.drawable.ic_keyboard_arrow_down),
    }

    var theme = KeyboardTheme.CALICO
        set(v) { field = v; invalidate() }

    /** Highlights the toolbar button of the panel that's currently open. */
    var activeAction: ToolbarAction? = null
        set(v) { field = v; invalidate() }

    private var suggestions: List<Suggestion> = emptyList()
    /** Index in [suggestions] that autocorrect will apply on space (drawn in the accent colour). */
    private var autoIndex = -1
    private var showToolbar = true

    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, dm)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slots = ArrayList<RectF>()
    private var pressed = -1
    private val handler = Handler(Looper.getMainLooper())
    private var longPressed = false
    private val longPress = Runnable {
        val s = slotSuggestion(pressed)
        if (!showToolbar && s != null) {
            longPressed = true
            listener.onSuggestionLongPressed(s)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(46f).toInt())
    }

    fun showToolbar() {
        showToolbar = true
        suggestions = emptyList()
        invalidate()
    }

    fun setSuggestions(list: List<Suggestion>, autoCorrectIndex: Int) {
        if (list.isEmpty()) { showToolbar(); return }
        showToolbar = false
        suggestions = list
        autoIndex = autoCorrectIndex
        invalidate()
    }

    val isShowingSuggestions get() = !showToolbar

    /** Samsung puts the strongest suggestion in the middle slot. */
    private fun slotOrder(n: Int): IntArray = when (n) {
        1 -> intArrayOf(0)
        2 -> intArrayOf(1, 0)
        else -> intArrayOf(1, 0, 2)
    }

    private fun slotSuggestion(slot: Int): Suggestion? {
        if (slot < 0) return null
        val order = slotOrder(minOf(3, suggestions.size))
        return order.getOrNull(slot)?.let { suggestions.getOrNull(it) }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        slots.clear()
        if (showToolbar) drawToolbar(canvas) else drawSuggestions(canvas)
    }

    private fun drawToolbar(canvas: Canvas) {
        val actions = ToolbarAction.entries
        val w = width.toFloat() / actions.size
        val size = dp(23f).toInt()
        actions.forEachIndexed { i, a ->
            val r = RectF(i * w, 0f, (i + 1) * w, height.toFloat())
            slots += r
            if (i == pressed || a == activeAction) {
                paint.color = if (a == activeAction) theme.accent else theme.keyPressed
                paint.alpha = if (a == activeAction) 50 else 255
                val rad = dp(18f)
                canvas.drawCircle(r.centerX(), r.centerY(), rad, paint)
                paint.alpha = 255
            }
            val d = ContextCompat.getDrawable(context, a.icon)!!.mutate()
            d.setTint(if (a == ToolbarAction.MENU || a == activeAction) theme.accent else theme.stripText)
            val cx = r.centerX().toInt(); val cy = r.centerY().toInt()
            d.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
            d.draw(canvas)
        }
    }

    private fun drawSuggestions(canvas: Canvas) {
        val n = minOf(3, suggestions.size)
        val order = slotOrder(n)
        val w = width.toFloat() / 3f
        // Centre the slots when there are fewer than three.
        val offset = (3 - n) * w / 2f
        paint.color = theme.hintText
        paint.alpha = 90
        for (s in 0 until n) {
            val r = RectF(offset + s * w, 0f, offset + (s + 1) * w, height.toFloat())
            slots += r
            if (s == pressed) {
                paint.color = theme.keyPressed; paint.alpha = 255
                canvas.drawRoundRect(r.left + dp(4f), dp(5f), r.right - dp(4f), height - dp(5f), dp(10f), dp(10f), paint)
            }
            if (s > 0) {
                paint.color = theme.hintText; paint.alpha = 90
                canvas.drawRect(r.left, height * 0.3f, r.left + dp(1f), height * 0.7f, paint)
            }
            val idx = order[s]
            val sug = suggestions[idx]
            val auto = idx == autoIndex
            textPaint.color = if (auto) theme.accent else theme.stripText
            textPaint.typeface = if (idx == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val label = if (sug.isShortcut) "↪ ${sug.word}" else sug.word
            val text = TextUtils.ellipsize(label, textPaint, r.width() - dp(12f), TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, r.centerX(), r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = slots.indexOfFirst { it.contains(e.x, e.y) }
                longPressed = false
                handler.postDelayed(longPress, 500)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val now = slots.indexOfFirst { it.contains(e.x, e.y) }
                if (now != pressed) { handler.removeCallbacks(longPress); pressed = -1; invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                val slot = pressed
                pressed = -1
                invalidate()
                if (slot >= 0 && !longPressed) {
                    performClick()
                    if (showToolbar) {
                        ToolbarAction.entries.getOrNull(slot)?.let { listener.onToolbarAction(it) }
                    } else {
                        val order = slotOrder(minOf(3, suggestions.size))
                        val idx = order.getOrNull(slot) ?: return true
                        suggestions.getOrNull(idx)?.let { listener.onSuggestionPicked(idx, it) }
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longPress); pressed = -1; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
