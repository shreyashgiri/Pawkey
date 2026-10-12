package com.pawkeys.keyboard.panels

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pawkeys.keyboard.KeyboardTheme

/** Small helpers so every panel's buttons look like the keyboard's keys. */
object UiKit {
    fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).toInt()

    fun keyBackground(ctx: Context, theme: KeyboardTheme, color: Int, radiusDp: Float = 12f): RippleDrawable {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(ctx, radiusDp).toFloat()
            setColor(color)
            if (theme.id == KeyboardTheme.HIGH_CONTRAST.id) setStroke(dp(ctx, 1f), theme.border)
        }
        return RippleDrawable(ColorStateList.valueOf(theme.keyPressed), shape, null)
    }

    fun iconButton(
        ctx: Context, theme: KeyboardTheme, icon: Int, description: String,
        background: Int = theme.functionKey, tint: Int = theme.keyText, onClick: () -> Unit,
    ): ImageView = ImageView(ctx).apply {
        setImageDrawable(ContextCompat.getDrawable(ctx, icon)!!.mutate().apply { setTint(tint) })
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        this.background = keyBackground(ctx, theme, background)
        setOnClickListener { onClick() }
    }

    fun textButton(
        ctx: Context, theme: KeyboardTheme, text: String,
        background: Int = theme.functionKey, textColor: Int = theme.keyText, sizeSp: Float = 14f, bold: Boolean = true,
        onClick: () -> Unit,
    ): TextView = TextView(ctx).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = if (bold) MEDIUM else REGULAR
        val pad = dp(ctx, 6f)
        setPadding(pad, 0, pad, 0)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        this.background = keyBackground(ctx, theme, background)
        setOnClickListener { onClick() }
    }

    val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Den item: an icon in a soft circle with a small label underneath. */
    fun tile(ctx: Context, theme: KeyboardTheme, icon: Int, label: String, highlighted: Boolean, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = keyBackground(ctx, theme, theme.background, 14f)
            setOnClickListener { onClick() }
            contentDescription = label
            addView(ImageView(ctx).apply {
                setImageDrawable(ContextCompat.getDrawable(ctx, icon)!!.mutate().apply { setTint(if (highlighted) theme.accent else theme.keyText) })
                scaleType = ImageView.ScaleType.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (highlighted) blendAccent(theme) else theme.key)
                    if (theme.id == KeyboardTheme.HIGH_CONTRAST.id) setStroke(dp(ctx, 1f), theme.border)
                }
            }, LinearLayout.LayoutParams(dp(ctx, 40f), dp(ctx, 40f)))
            addView(TextView(ctx).apply {
                text = label
                setTextColor(theme.stripText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = REGULAR
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(ctx, 2f), dp(ctx, 4f), dp(ctx, 2f), 0)
            })
        }

    /** Panel header: a quiet title on the left and an optional text action on the right. */
    fun header(ctx: Context, theme: KeyboardTheme, title: String, action: String? = null, onAction: () -> Unit = {}) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 18f), 0, dp(ctx, 8f), 0)
            addView(TextView(ctx).apply {
                text = title
                setTextColor(theme.keyText)
                typeface = MEDIUM
                letterSpacing = 0.02f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (action != null) {
                addView(textButton(ctx, theme, action, background = theme.background, textColor = theme.accent, sizeSp = 13.5f, onClick = onAction),
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 36f)))
            }
        }

    fun blendAccent(theme: KeyboardTheme): Int {
        val a = theme.accent
        val b = theme.key
        fun mix(shift: Int) = (((a shr shift) and 0xFF) * 0.22 + ((b shr shift) and 0xFF) * 0.78).toInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    fun weighted(weight: Float = 1f, marginDp: Float = 3f, ctx: Context, vertical: Boolean = false) =
        LinearLayout.LayoutParams(if (vertical) LinearLayout.LayoutParams.MATCH_PARENT else 0, if (vertical) 0 else LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            val m = dp(ctx, marginDp)
            setMargins(m, m, m, m)
        }

    /** Makes a view fire [action] repeatedly while held, like a held arrow key. */
    @SuppressLint("ClickableViewAccessibility")
    fun repeatOnHold(view: View, action: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() { action(); handler.postDelayed(this, 70) }
        }
        view.setOnClickListener(null)
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; action(); handler.postDelayed(r, 400) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; handler.removeCallbacks(r) }
            }
            true
        }
    }
}
