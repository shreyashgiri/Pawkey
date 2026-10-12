package com.pawkeys.keyboard.voice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.R
import com.pawkeys.keyboard.panels.UiKit

/** Replaces the Whisker bar while listening: a mic that pulses with your voice, live words, Stop. */
@SuppressLint("ViewConstructor")
class VoiceBar(context: Context, private val theme: KeyboardTheme, onStop: () -> Unit) : LinearLayout(context) {
    private val pulse = android.view.View(context)
    private val text = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(theme.background)
        setPadding(UiKit.dp(context, 10f), 0, UiKit.dp(context, 6f), 0)

        val mic = FrameLayout(context)
        pulse.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(theme.accent) }
        pulse.alpha = 0.25f
        mic.addView(pulse, FrameLayout.LayoutParams(UiKit.dp(context, 36f), UiKit.dp(context, 36f), Gravity.CENTER))
        mic.addView(ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_mic)!!.mutate().apply { setTint(theme.accent) })
            scaleType = ImageView.ScaleType.CENTER
        }, FrameLayout.LayoutParams(UiKit.dp(context, 36f), UiKit.dp(context, 36f), Gravity.CENTER))
        addView(mic, LayoutParams(UiKit.dp(context, 44f), UiKit.dp(context, 44f)))

        text.apply {
            setTextColor(theme.hintText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = UiKit.REGULAR
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            setPadding(UiKit.dp(context, 8f), 0, UiKit.dp(context, 8f), 0)
        }
        addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(UiKit.textButton(context, theme, "Stop", background = theme.functionKey, sizeSp = 13.5f) { onStop() },
            LayoutParams(UiKit.dp(context, 64f), UiKit.dp(context, 34f)))
        show("Listening…", hint = true)
    }

    fun show(words: String, hint: Boolean = false) {
        text.text = words
        text.setTextColor(if (hint) theme.hintText else theme.keyText)
    }

    /** Voice level from the recognizer (roughly -2..10 dB). */
    fun level(rmsDb: Float) {
        val scale = 1f + (rmsDb.coerceIn(0f, 10f) / 10f) * 0.6f
        pulse.scaleX = scale
        pulse.scaleY = scale
        pulse.alpha = 0.18f + (scale - 1f) * 0.5f
    }
}
