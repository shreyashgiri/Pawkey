package com.pawkeys.keyboard.panels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.R

/**
 * Perch (floating keyboard): the keyboard sits on a card that can be dragged
 * anywhere. This container fills the screen but is transparent; the keyboard
 * service only claims touches inside [cardBounds], so the app stays usable.
 */
@SuppressLint("ViewConstructor")
class PerchContainer(
    context: Context,
    theme: KeyboardTheme,
    keyboard: View,
    private var fx: Float,
    private var fy: Float,
    private val onMoved: (Float, Float) -> Unit,
    onDock: () -> Unit,
) : FrameLayout(context) {

    val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        background = GradientDrawable().apply {
            cornerRadius = UiKit.dp(context, 22f).toFloat()
            setColor(theme.background)
            setStroke(UiKit.dp(context, 1f), theme.border)
        }
        elevation = UiKit.dp(context, 14f).toFloat()
        setPadding(UiKit.dp(context, 4f), 0, UiKit.dp(context, 4f), UiKit.dp(context, 6f))
    }

    init {
        clipChildren = false
        // Grab handle: a small pill to drag by, and a dock button to go back to normal.
        val handle = FrameLayout(context)
        handle.addView(View(context).apply {
            background = GradientDrawable().apply { cornerRadius = 100f; setColor(theme.hintText) }
            alpha = 0.6f
        }, LayoutParams(UiKit.dp(context, 40f), UiKit.dp(context, 4f), Gravity.CENTER))
        handle.addView(UiKit.iconButton(context, theme, R.drawable.ic_open_in_full, "Dock keyboard",
            background = theme.background, tint = theme.hintText) { onDock() },
            LayoutParams(UiKit.dp(context, 36f), UiKit.dp(context, 26f), Gravity.END or Gravity.CENTER_VERTICAL))
        handle.setOnTouchListener(DragListener())
        card.addView(handle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(context, 26f)))
        card.addView(keyboard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(card, LayoutParams((resources.displayMetrics.widthPixels * WIDTH).toInt(), LayoutParams.WRAP_CONTENT))
    }

    /** Fill the screen so the card can go anywhere. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = resources.displayMetrics.heightPixels
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        place()
    }

    private fun place() {
        val maxX = (width - card.width).coerceAtLeast(0)
        val maxY = (height - card.height).coerceAtLeast(0)
        card.x = fx.coerceIn(0f, 1f) * maxX
        card.y = fy.coerceIn(0f, 1f) * maxY
    }

    /** The card's rectangle in this view's coordinates (the only touchable part). */
    fun cardBounds(out: Rect) {
        out.set(card.x.toInt(), card.y.toInt(), (card.x + card.width).toInt(), (card.y + card.height).toInt())
    }

    private inner class DragListener : OnTouchListener {
        private var lastX = 0f
        private var lastY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = e.rawX; lastY = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (width - card.width).coerceAtLeast(1)
                    val maxY = (height - card.height).coerceAtLeast(1)
                    fx = ((card.x + e.rawX - lastX) / maxX).coerceIn(0f, 1f)
                    fy = ((card.y + e.rawY - lastY) / maxY).coerceIn(0f, 1f)
                    lastX = e.rawX; lastY = e.rawY
                    place()
                    // Re-run layout so the keyboard service recomputes the touchable area.
                    requestLayout()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onMoved(fx, fy)
            }
            return true
        }
    }

    companion object {
        const val WIDTH = 0.88f
    }
}
