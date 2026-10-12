package com.pawkeys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.pawkeys.keyboard.engine.Suggestion

/**
 * The Whisker bar above the keys. When you're not typing it shows the Den button,
 * your chosen shortcut buttons and Hide. While typing it shows three Hunches
 * (suggestions) with the strongest one in the middle.
 */
@SuppressLint("ViewConstructor")
class CandidateStrip(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onToolbarAction(action: ToolbarAction)
        fun onSuggestionPicked(index: Int, suggestion: Suggestion)
        fun onSuggestionLongPressed(suggestion: Suggestion)
    }

    /**
     * Every tool. All of them appear in the Den; any can be pinned to the Whisker
     * bar (Den and Hide are always there). [label] is the PawKeys name.
     */
    enum class ToolbarAction(val icon: Int, val label: String) {
        MENU(R.drawable.ic_pets, "Den"),
        EMOJI(R.drawable.ic_mood, "Emoji"),
        STICKERS(R.drawable.ic_sticky_note_2, "Stickers"),
        TRANSLATE(R.drawable.ic_translate, "Translate"),
        VOICE(R.drawable.ic_mic, "Voice"),
        CLIPBOARD(R.drawable.ic_content_paste, "Stash"),
        TEXT_EDIT(R.drawable.ic_edit_note, "Groom"),
        ONE_HANDED(R.drawable.ic_front_hand, "Single paw"),
        FLOAT(R.drawable.ic_open_with, "Perch"),
        SIZE(R.drawable.ic_height, "Stretch"),
        THEMES(R.drawable.ic_palette, "Coats"),
        BREEDS(R.drawable.ic_keyboard, "Breeds"),
        NUMBER_ROW(R.drawable.ic_dialpad, "Number row"),
        GESTURES(R.drawable.ic_gesture, "Gestures"),
        SOUND(R.drawable.ic_volume_up, "Sound"),
        VIBRATION(R.drawable.ic_vibration, "Haptics"),
        SHORTCUTS(R.drawable.ic_short_text, "Tricks"),
        SETTINGS(R.drawable.ic_settings, "Settings"),
        HIDE(R.drawable.ic_keyboard_arrow_down, "Hide");

        /** Can be pinned to the bar (Den and Hide are fixed). */
        val pinnable get() = this != MENU && this != HIDE
    }

    var theme = KeyboardTheme.CALICO
        set(v) { field = v; invalidate() }

    /** The buttons between Den and Hide, in order. */
    var buttons: List<ToolbarAction> = DEFAULT_BUTTONS
        set(v) { field = v; invalidate() }

    /** Highlights the button of the panel that's currently open. */
    var activeAction: ToolbarAction? = null
        set(v) { field = v; invalidate() }

    private var suggestions: List<Suggestion> = emptyList()
    /** Index in [suggestions] that Auto-fix will apply on space (drawn in the accent colour). */
    private var autoIndex = -1
    private var showToolbar = true

    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15.5f, dm)
    }
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slots = ArrayList<RectF>()
    private val slotActions = ArrayList<ToolbarAction>()
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), UiDims.stripHeight(context))
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

    /** The strongest Hunch goes in the middle slot. */
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
        paint.color = theme.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        slots.clear()
        slotActions.clear()
        if (showToolbar) drawToolbar(canvas) else drawSuggestions(canvas)
    }

    private fun drawToolbar(canvas: Canvas) {
        val side = dp(8f)
        val cell = dp(48f)
        // Den on the left, Hide on the right, chosen buttons spread evenly between.
        val items = ArrayList<Pair<ToolbarAction, Float>>()
        items += ToolbarAction.MENU to side + cell / 2
        items += ToolbarAction.HIDE to width - side - cell / 2
        val middle = buttons.filter { it != ToolbarAction.MENU && it != ToolbarAction.HIDE }
        if (middle.isNotEmpty()) {
            val left = side + cell
            val span = width - 2 * side - 2 * cell
            val step = span / middle.size
            middle.forEachIndexed { i, a -> items += a to left + step * (i + 0.5f) }
        }
        val iconSize = dp(21f).toInt()
        val cy = height / 2f
        // A panel opened from the Den whose tool isn't pinned lights up the Den paw.
        val current = activeAction?.let { act -> if (items.any { it.first == act }) act else ToolbarAction.MENU }
        for ((a, cx) in items) {
            val r = RectF(cx - cell / 2, 0f, cx + cell / 2, height.toFloat())
            val index = slots.size
            slots += r
            slotActions += a
            val active = a == current
            if (active || index == pressed) {
                paint.color = if (active) theme.accent else theme.keyPressed
                paint.alpha = if (active) 36 else 255
                canvas.drawRoundRect(cx - dp(20f), cy - dp(15f), cx + dp(20f), cy + dp(15f), dp(15f), dp(15f), paint)
                paint.alpha = 255
            }
            val d = ContextCompat.getDrawable(context, a.icon)!!.mutate()
            d.setTint(if (a == ToolbarAction.MENU || active) theme.accent else theme.stripText)
            d.setBounds((cx - iconSize / 2).toInt(), (cy - iconSize / 2).toInt(), (cx + iconSize / 2).toInt(), (cy + iconSize / 2).toInt())
            d.draw(canvas)
        }
    }

    private fun drawSuggestions(canvas: Canvas) {
        val n = minOf(3, suggestions.size)
        val order = slotOrder(n)
        val w = width.toFloat() / 3f
        val offset = (3 - n) * w / 2f
        for (s in 0 until n) {
            val r = RectF(offset + s * w, 0f, offset + (s + 1) * w, height.toFloat())
            slots += r
            if (s == pressed) {
                paint.color = theme.keyPressed
                canvas.drawRoundRect(r.left + dp(6f), dp(6f), r.right - dp(6f), height - dp(6f), dp(12f), dp(12f), paint)
            }
            if (s > 0) {
                paint.color = theme.hintText
                paint.alpha = 45
                canvas.drawRect(r.left, height * 0.36f, r.left + dp(1f), height * 0.64f, paint)
                paint.alpha = 255
            }
            val idx = order[s]
            val sug = suggestions[idx]
            val auto = idx == autoIndex
            textPaint.color = if (auto) theme.accent else theme.stripText
            textPaint.typeface = if (idx == 0) medium else regular
            if (idx == 0 && !auto) textPaint.color = theme.keyText
            val label = if (sug.isShortcut) "↪ ${sug.word}" else sug.word
            val text = TextUtils.ellipsize(label, textPaint, r.width() - dp(16f), TextUtils.TruncateAt.END).toString()
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
                        slotActions.getOrNull(slot)?.let { listener.onToolbarAction(it) }
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

    companion object {
        val DEFAULT_BUTTONS = listOf(ToolbarAction.EMOJI, ToolbarAction.CLIPBOARD, ToolbarAction.TEXT_EDIT, ToolbarAction.TRANSLATE, ToolbarAction.VOICE)
        const val MAX_BUTTONS = 5
    }
}
