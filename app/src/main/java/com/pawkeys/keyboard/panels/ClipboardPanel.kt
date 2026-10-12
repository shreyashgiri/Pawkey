package com.pawkeys.keyboard.panels

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pawkeys.keyboard.ClipboardHistory
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.R

/** Stash: clipboard history with pin and delete. */
@SuppressLint("ViewConstructor")
class ClipboardPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val history: ClipboardHistory,
    private val onPaste: (String) -> Unit,
    private val onBack: () -> Unit,
) : LinearLayout(context) {

    private val list = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 10f)

        addView(UiKit.header(context, theme, "Stash", "Clear all") { history.clearUnpinned(); refresh() },
            LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 42f)))

        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
            setPadding(pad, 0, pad, 0)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(UiKit.textButton(context, theme, "ABC") { onBack() }, UiKit.weighted(1f, 3f, context))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 46f)))

        history.onChanged = { post { refresh() } }
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        history.onChanged = null
    }

    private fun refresh() {
        list.removeAllViews()
        val clips = history.clips.sortedByDescending { it.pinned }
        if (clips.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "Your Stash is empty.\nAnything you copy will be kept here."
                gravity = Gravity.CENTER
                setTextColor(theme.hintText)
                setPadding(0, UiKit.dp(context, 40f), 0, 0)
            })
            return
        }
        for (clip in clips) list.addView(row(clip))
    }

    private fun row(clip: ClipboardHistory.Clip): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = UiKit.keyBackground(context, theme, theme.key, 14f)
        setPadding(UiKit.dp(context, 16f), UiKit.dp(context, 10f), UiKit.dp(context, 6f), UiKit.dp(context, 10f))
        setOnClickListener { onPaste(clip.text) }
        addView(TextView(context).apply {
            text = clip.text
            setTextColor(theme.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = UiKit.REGULAR
            setLineSpacing(0f, 1.15f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val size = UiKit.dp(context, 36f)
        addView(UiKit.iconButton(
            context, theme, if (clip.pinned) R.drawable.ic_push_pin_fill1 else R.drawable.ic_push_pin,
            if (clip.pinned) "Unpin" else "Pin", background = theme.key,
            tint = if (clip.pinned) theme.accent else theme.hintText,
        ) { history.togglePin(clip); refresh() }, LayoutParams(size, size))
        addView(UiKit.iconButton(context, theme, R.drawable.ic_delete, "Delete", background = theme.key, tint = theme.hintText) {
            history.remove(clip); refresh()
        }, LayoutParams(size, size))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, UiKit.dp(context, 4f), 0, UiKit.dp(context, 4f))
        }
    }
}
