package com.pawkeys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ShiftState { OFF, ON, LOCKED }
enum class Flick { LEFT, RIGHT, UP, DOWN }

/**
 * Draws the keys and turns touches into key presses and gestures:
 *  - tap / long-press popups with alternative characters
 *  - Fleksy flicks (swipe right = space, left = delete word, up/down = cycle suggestions)
 *  - Samsung swipe typing ("glide"), spacebar cursor control and delete-key swipe selection
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        /** Finger went down on a key: play sound / vibration. */
        fun onKeyDown(key: Key)
        fun onKey(key: Key)
        fun onPopupText(key: Key, text: String)
        fun onFlick(flick: Flick)
        fun onSpaceCursor(steps: Int)
        fun onSpaceLongPress()
        /** Live preview while swiping left from the delete key; [words] grows as the finger moves. */
        fun onDeleteSwipe(words: Int)
        fun onDeleteSwipeEnd(words: Int)
        fun onGlide(xs: FloatArray, ys: FloatArray)
    }

    var theme: KeyboardTheme = KeyboardTheme.CALICO
        set(v) { field = v; invalidate() }
    var layout: KeyboardLayout? = null
        set(v) { field = v; cancelGesture(); requestLayout(); invalidate() }
    var shiftState = ShiftState.OFF
        set(v) { if (field != v) { field = v; invalidate() } }
    var enterIcon = R.drawable.ic_keyboard_return
        set(v) { field = v; invalidate() }
    var spaceLabel = "English (US)"
    var showHints = true
    var keyBorders = false
    var heightScale = 1f
        set(v) { field = v; requestLayout() }
    var gestureMode = "fleksy"
    var spaceCursor = true
    var deleteSwipe = true
    var keyPreview = true
    var longPressMs = 300L
    var pawTrail = true

    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)

    private val rowHeight get() = dp(54f) * heightScale
    private val padTop = dp(4f)
    private val padBottom = dp(6f)
    private val padSide = dp(3f)
    private val gapH = dp(2.6f)
    private val gapV = dp(4.5f)
    private val radius = dp(8f)

    /** Width of one key unit in px (a letter key's width). */
    var unitWidth = 0f
        private set

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val tmpRect = RectF()
    private val trailPath = Path()
    private val icons = HashMap<Int, Drawable>()

    // ---- gesture state ----
    private enum class Mode { NONE, TAP, POPUP, SPACE_CURSOR, DELETE_SWIPE, DELETE_REPEAT, GLIDE, FLICK, CONSUMED }

    private var mode = Mode.NONE
    private var activePointer = -1
    private var downKey: Key? = null
    private var downX = 0f
    private var downY = 0f
    private var lastCursorX = 0f
    private var deleteWords = 0
    private var pressedKey: Key? = null
    private var previewKey: Key? = null
    private val traceX = ArrayList<Float>()
    private val traceY = ArrayList<Float>()

    private var popupKey: Key? = null
    private var popupItems: List<String> = emptyList()
    private val popupCells = ArrayList<RectF>()
    private var popupSelected = 0

    private val handler = Handler(Looper.getMainLooper())
    private val longPress = Runnable { onLongPress() }
    private var repeatCount = 0
    private val repeat = object : Runnable {
        override fun run() {
            val key = downKey ?: return
            mode = Mode.DELETE_REPEAT
            listener.onKey(key)
            repeatCount++
            handler.postDelayed(this, if (repeatCount > 15) 30L else 60L)
        }
    }
    private val hidePreview = Runnable { previewKey = null; invalidate() }

    // ---- layout ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout?.rows?.size ?: 4
        setMeasuredDimension(width, desiredHeight(rows))
    }

    fun desiredHeight(rows: Int = layout?.rows?.size ?: 4) = (padTop + padBottom + rows * rowHeight).toInt()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (changed) layoutKeys()
    }

    private fun layoutKeys() {
        val rows = layout?.rows ?: return
        val avail = width - 2 * padSide
        val maxUnits = rows.maxOf { r -> r.sumOf { it.width.toDouble() } }.toFloat()
        unitWidth = avail / maxUnits
        rows.forEachIndexed { r, row ->
            val units = row.sumOf { it.width.toDouble() }.toFloat()
            var x = padSide + (maxUnits - units) * unitWidth / 2f
            val top = padTop + r * rowHeight
            for (key in row) {
                val w = key.width * unitWidth
                key.bounds.set(x, top, x + w, top + rowHeight)
                x += w
            }
        }
    }

    /** Centre of every letter key, for the glide decoder. */
    fun letterCenters(): Map<Char, Pair<Float, Float>> {
        val map = HashMap<Char, Pair<Float, Float>>()
        layout?.rows?.forEach { row -> row.forEach { k -> if (k.isLetter) map[k.code.toChar()] = k.bounds.centerX() to k.bounds.centerY() } }
        return map
    }

    private fun keyAt(x: Float, y: Float): Key? {
        val rows = layout?.rows ?: return null
        if (rows.isEmpty()) return null
        val r = ((y - padTop) / rowHeight).toInt().coerceIn(0, rows.size - 1)
        val row = rows[r]
        return row.firstOrNull { x >= it.bounds.left && x < it.bounds.right }
            ?: row.minByOrNull { min(abs(x - it.bounds.left), abs(x - it.bounds.right)) }
    }

    // ---- drawing ----

    private fun icon(res: Int): Drawable = icons.getOrPut(res) { ContextCompat.getDrawable(context, res)!!.mutate() }

    override fun onDraw(canvas: Canvas) {
        // Not drawColor(): the parents don't clip us (so previews can float above
        // the top row), and drawColor would paint over the suggestion strip too.
        keyPaint.color = theme.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), keyPaint)
        val rows = layout?.rows ?: return
        for (row in rows) for (key in row) drawKey(canvas, key)
        drawTrail(canvas)
        previewKey?.let { drawPreview(canvas, it) }
        if (popupKey != null) drawPopup(canvas)
    }

    private fun drawKey(canvas: Canvas, key: Key) {
        tmpRect.set(key.bounds.left + gapH, key.bounds.top + gapV, key.bounds.right - gapH, key.bounds.bottom - gapV)
        val pressed = key === pressedKey
        val bg = when {
            key.type == KeyType.ACTION -> if (pressed) blend(theme.accent, theme.keyPressed) else theme.accent
            pressed -> theme.keyPressed
            key.type == KeyType.FUNCTION -> theme.functionKey
            else -> theme.key
        }
        keyPaint.color = bg
        canvas.drawRoundRect(tmpRect, radius, radius, keyPaint)
        if (keyBorders || theme.id == KeyboardTheme.HIGH_CONTRAST.id) {
            borderPaint.color = theme.border
            canvas.drawRoundRect(tmpRect, radius, radius, borderPaint)
        }
        val fg = if (key.type == KeyType.ACTION) theme.onAccent else theme.keyText
        val cx = tmpRect.centerX()
        val cy = tmpRect.centerY()

        when {
            key.code == Codes.SHIFT -> {
                val (res, tint) = when (shiftState) {
                    ShiftState.OFF -> R.drawable.ic_shift to fg
                    ShiftState.ON -> R.drawable.ic_shift_fill1 to theme.accent
                    ShiftState.LOCKED -> R.drawable.ic_shift_lock_fill1 to theme.accent
                }
                drawIcon(canvas, res, cx, cy, dp(24f), tint)
            }
            key.code == Codes.ENTER -> drawIcon(canvas, enterIcon, cx, cy, dp(24f), fg)
            key.type == KeyType.SPACE -> drawSpace(canvas, cx, cy)
            key.icon != 0 -> drawIcon(canvas, key.icon, cx, cy, dp(24f), fg)
            else -> {
                val label = if (key.isLetter && shiftState != ShiftState.OFF) key.label.uppercase() else key.label
                val size = when {
                    key.type == KeyType.FUNCTION -> sp(15f)
                    label.length > 1 -> sp(17f)
                    layout?.id == LayoutId.NUMPAD || layout?.id == LayoutId.PHONE -> sp(24f)
                    else -> sp(22f)
                } * min(1.1f, max(0.85f, heightScale))
                labelPaint.color = fg
                labelPaint.textSize = size
                labelPaint.typeface = if (key.type == KeyType.FUNCTION) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(label, cx, cy - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
                val hint = key.hint
                if (hint != null && (showHints || !key.isLetter)) {
                    hintPaint.color = theme.hintText
                    hintPaint.textSize = sp(10.5f)
                    canvas.drawText(hint, tmpRect.right - dp(5f), tmpRect.top + dp(3f) - hintPaint.ascent(), hintPaint)
                }
            }
        }
    }

    private fun drawSpace(canvas: Canvas, cx: Float, cy: Float) {
        hintPaint.color = theme.hintText
        labelPaint.color = theme.hintText
        labelPaint.textSize = sp(12.5f)
        labelPaint.typeface = Typeface.DEFAULT
        val textW = labelPaint.measureText(spaceLabel)
        val iconSize = dp(13f)
        val total = textW + iconSize + dp(5f)
        val start = cx - total / 2f
        drawIcon(canvas, R.drawable.ic_pets, start + iconSize / 2f, cy, iconSize, theme.accent)
        canvas.drawText(spaceLabel, start + iconSize + dp(5f) + textW / 2f, cy - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
    }

    private fun drawIcon(canvas: Canvas, res: Int, cx: Float, cy: Float, size: Float, tint: Int) {
        val d = icon(res)
        d.setTint(tint)
        val h = (size / 2f).toInt()
        d.setBounds((cx - h).toInt(), (cy - h).toInt(), (cx + h).toInt(), (cy + h).toInt())
        d.draw(canvas)
    }

    private fun drawPreview(canvas: Canvas, key: Key) {
        val w = (key.bounds.width() - 2 * gapH) * 1.15f
        val h = rowHeight * 1.05f
        val left = (key.bounds.centerX() - w / 2f).coerceIn(0f, width - w)
        val bottom = key.bounds.top + gapV - dp(4f)
        tmpRect.set(left, bottom - h, left + w, bottom)
        keyPaint.color = theme.popup
        keyPaint.setShadowLayer(dp(4f), 0f, dp(1.5f), 0x40000000)
        canvas.drawRoundRect(tmpRect, radius * 1.3f, radius * 1.3f, keyPaint)
        keyPaint.clearShadowLayer()
        labelPaint.color = theme.keyText
        labelPaint.textSize = sp(30f)
        labelPaint.typeface = Typeface.DEFAULT
        val label = if (key.isLetter && shiftState != ShiftState.OFF) key.label.uppercase() else key.label
        canvas.drawText(label, tmpRect.centerX(), tmpRect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
    }

    private fun drawPopup(canvas: Canvas) {
        if (popupCells.isEmpty()) return
        tmpRect.set(popupCells.minOf { it.left }, popupCells.minOf { it.top }, popupCells.maxOf { it.right }, popupCells.maxOf { it.bottom })
        tmpRect.inset(-dp(4f), -dp(4f))
        keyPaint.color = theme.popup
        keyPaint.setShadowLayer(dp(6f), 0f, dp(2f), 0x50000000)
        canvas.drawRoundRect(tmpRect, radius * 1.3f, radius * 1.3f, keyPaint)
        keyPaint.clearShadowLayer()
        popupItems.forEachIndexed { i, text ->
            val cell = popupCells[i]
            val selected = i == popupSelected
            if (selected) {
                keyPaint.color = theme.accent
                canvas.drawRoundRect(cell, radius, radius, keyPaint)
            }
            labelPaint.color = if (selected) theme.onAccent else theme.keyText
            labelPaint.textSize = if (text.length > 2) sp(14f) else sp(21f)
            labelPaint.typeface = Typeface.DEFAULT
            canvas.drawText(text, cell.centerX(), cell.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f, labelPaint)
        }
    }

    private fun drawTrail(canvas: Canvas) {
        if (mode != Mode.GLIDE || traceX.size < 2) return
        trailPath.reset()
        trailPath.moveTo(traceX[0], traceY[0])
        for (i in 1 until traceX.size) trailPath.lineTo(traceX[i], traceY[i])
        trailPaint.color = theme.accent
        trailPaint.alpha = 190
        trailPaint.strokeWidth = dp(5f)
        canvas.drawPath(trailPath, trailPaint)
        if (pawTrail) {
            // Little paw prints stamped along the path, like a cat walked across the keys.
            var acc = 0f
            val step = dp(52f)
            var left = true
            for (i in 1 until traceX.size) {
                acc += hypot(traceX[i] - traceX[i - 1], traceY[i] - traceY[i - 1])
                if (acc >= step) {
                    acc = 0f
                    val offset = if (left) -dp(12f) else dp(12f)
                    left = !left
                    drawIcon(canvas, R.drawable.ic_pets, traceX[i], traceY[i] + offset, dp(13f), theme.accent)
                }
            }
        }
    }

    private fun blend(a: Int, b: Int): Int {
        val r = ((a shr 16 and 0xFF) + (b shr 16 and 0xFF)) / 2
        val g = ((a shr 8 and 0xFF) + (b shr 8 and 0xFF)) / 2
        val bl = ((a and 0xFF) + (b and 0xFF)) / 2
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    // ---- touch handling ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> start(e, 0)
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Fast typing: a second finger lands before the first lifted. Finish the
                // first tap now, then follow the new finger.
                if (mode == Mode.TAP) finishTap()
                else if (mode != Mode.NONE && downKey?.code != Codes.SHIFT) return true
                start(e, e.actionIndex)
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = e.findPointerIndex(activePointer)
                if (idx >= 0) move(e.getX(idx), e.getY(idx))
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == activePointer) up(e.getX(e.actionIndex), e.getY(e.actionIndex))
            MotionEvent.ACTION_UP -> if (e.getPointerId(e.actionIndex) == activePointer) up(e.x, e.y)
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun start(e: MotionEvent, index: Int) {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
        activePointer = e.getPointerId(index)
        downX = e.getX(index)
        downY = e.getY(index)
        lastCursorX = downX
        deleteWords = 0
        repeatCount = 0
        traceX.clear(); traceY.clear()
        traceX += downX; traceY += downY
        val key = keyAt(downX, downY) ?: run { mode = Mode.NONE; return }
        downKey = key
        pressedKey = key
        mode = Mode.TAP
        listener.onKeyDown(key)
        when (key.code) {
            Codes.SHIFT -> {
                // Shift acts on press so it can be held while tapping a letter.
                listener.onKey(key)
                mode = Mode.CONSUMED
            }
            Codes.DELETE -> handler.postDelayed(repeat, 380)
            else -> handler.postDelayed(longPress, if (key.type == KeyType.SPACE) 450 else longPressMs)
        }
        if (keyPreview && key.type == KeyType.CHAR && layout?.id != LayoutId.NUMPAD && layout?.id != LayoutId.PHONE) {
            handler.removeCallbacks(hidePreview)
            previewKey = key
        } else {
            previewKey = null
        }
        invalidate()
    }

    private fun move(x: Float, y: Float) {
        val key = downKey ?: return
        val dx = x - downX
        val dy = y - downY
        when (mode) {
            Mode.TAP -> {
                val dist = hypot(dx, dy)
                when {
                    key.type == KeyType.SPACE && spaceCursor && abs(dx) > unitWidth * 0.45f -> {
                        handler.removeCallbacks(longPress)
                        mode = Mode.SPACE_CURSOR
                        lastCursorX = downX
                        pressedKey = null
                        move(x, y)
                    }
                    key.code == Codes.DELETE && deleteSwipe && dx < -unitWidth * 0.5f && repeatCount == 0 -> {
                        handler.removeCallbacks(repeat)
                        mode = Mode.DELETE_SWIPE
                        move(x, y)
                    }
                    key.type == KeyType.CHAR && gestureMode == "glide" && key.isLetter && dist > unitWidth * 0.6f -> {
                        handler.removeCallbacks(longPress)
                        mode = Mode.GLIDE
                        pressedKey = null
                        previewKey = null
                        addTrace(x, y)
                    }
                    key.type == KeyType.CHAR && gestureMode == "fleksy" && dist > unitWidth * 0.7f -> {
                        handler.removeCallbacks(longPress)
                        mode = Mode.FLICK
                        pressedKey = null
                        previewKey = null
                        invalidate()
                    }
                    key.type == KeyType.CHAR && dist > unitWidth * 0.6f -> {
                        // Finger slid onto another key: follow it like Samsung does.
                        val now = keyAt(x, y)
                        if (now != null && now !== key && now.type == KeyType.CHAR) {
                            handler.removeCallbacks(longPress)
                            downKey = now; pressedKey = now
                            if (previewKey != null) previewKey = now
                            downX = x; downY = y
                            handler.postDelayed(longPress, longPressMs)
                            invalidate()
                        }
                    }
                }
            }
            Mode.SPACE_CURSOR -> {
                val step = unitWidth * 0.4f
                val steps = ((x - lastCursorX) / step).toInt()
                if (steps != 0) {
                    lastCursorX += steps * step
                    listener.onSpaceCursor(steps)
                }
            }
            Mode.DELETE_SWIPE -> {
                val words = max(0, (-dx / (unitWidth * 0.8f)).toInt())
                if (words != deleteWords) {
                    deleteWords = words
                    listener.onDeleteSwipe(words)
                }
            }
            Mode.GLIDE -> addTrace(x, y)
            Mode.POPUP -> {
                val idx = popupCells.indexOfFirst { x >= it.left && x < it.right && y >= it.top - rowHeight && y < it.bottom + rowHeight }
                val sel = if (idx >= 0) idx else popupCells.indices.minByOrNull { abs(popupCells[it].centerX() - x) + abs(popupCells[it].centerY() - y) } ?: 0
                if (sel != popupSelected) { popupSelected = sel; invalidate() }
            }
            else -> Unit
        }
    }

    private fun addTrace(x: Float, y: Float) {
        traceX += x; traceY += y
        invalidate()
    }

    private fun up(x: Float, y: Float) {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
        val key = downKey
        when (mode) {
            Mode.TAP -> if (key != null) finishTap()
            Mode.POPUP -> {
                val pk = popupKey
                val text = popupItems.getOrNull(popupSelected)
                if (pk != null && text != null) {
                    listener.onPopupText(pk, if (pk.isLetter && shiftState != ShiftState.OFF) text.uppercase() else text)
                }
            }
            Mode.DELETE_SWIPE -> listener.onDeleteSwipeEnd(deleteWords)
            Mode.GLIDE -> {
                addTrace(x, y)
                listener.onGlide(traceX.toFloatArray(), traceY.toFloatArray())
            }
            Mode.FLICK -> {
                val dx = x - downX
                val dy = y - downY
                if (hypot(dx, dy) > unitWidth * 0.7f) {
                    listener.onFlick(
                        if (abs(dx) >= abs(dy)) (if (dx > 0) Flick.RIGHT else Flick.LEFT)
                        else (if (dy > 0) Flick.DOWN else Flick.UP)
                    )
                } else if (key != null) {
                    listener.onKey(key)
                }
            }
            else -> Unit
        }
        resetGesture()
    }

    private fun finishTap() {
        val key = downKey ?: return
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
        listener.onKey(key)
        pressedKey = null
        handler.postDelayed(hidePreview, 60)
        mode = Mode.NONE
        invalidate()
    }

    private fun onLongPress() {
        val key = downKey ?: return
        if (mode != Mode.TAP) return
        if (key.type == KeyType.SPACE) {
            mode = Mode.CONSUMED
            pressedKey = null
            listener.onSpaceLongPress()
            invalidate()
            return
        }
        if (key.popupChars.isEmpty()) return
        showPopup(key)
    }

    private fun showPopup(key: Key) {
        mode = Mode.POPUP
        previewKey = null
        popupKey = key
        popupItems = key.popupChars
        popupSelected = 0
        popupCells.clear()
        val cellW = max(key.bounds.width() - 2 * gapH, dp(34f)).let { if (popupItems.any { it.length > 2 }) max(it, dp(48f)) else it }
        val cellH = rowHeight * 0.92f
        // As many per row as fit across the keyboard; wrap the rest.
        val fit = max(1, ((width - dp(16f)) / cellW).toInt())
        val rowsN = (popupItems.size + fit - 1) / fit
        val perRow = (popupItems.size + rowsN - 1) / rowsN
        val totalW = perRow * cellW
        val left = (key.bounds.centerX() - cellW / 2f).coerceAtMost(width - totalW - dp(4f)).coerceAtLeast(dp(4f))
        // Float above the key, but never higher than the suggestion strip (the window ends there).
        val bottom = max(key.bounds.top + gapV - dp(6f), rowsN * cellH - dp(42f))
        for (i in popupItems.indices) {
            val r = i / perRow
            val c = i % perRow
            val top = bottom - (rowsN - r) * cellH
            popupCells += RectF(left + c * cellW, top, left + (c + 1) * cellW, top + cellH)
        }
        listener.onKeyDown(key)
        invalidate()
    }

    private fun resetGesture() {
        mode = Mode.NONE
        downKey = null
        pressedKey = null
        popupKey = null
        popupCells.clear()
        activePointer = -1
        traceX.clear(); traceY.clear()
        handler.postDelayed(hidePreview, 60)
        invalidate()
    }

    fun cancelGesture() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(repeat)
        if (mode == Mode.DELETE_SWIPE) listener.onDeleteSwipeEnd(0)
        previewKey = null
        resetGesture()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}
