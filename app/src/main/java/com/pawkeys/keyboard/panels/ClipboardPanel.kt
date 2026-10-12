package com.pawkeys.keyboard.panels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pawkeys.keyboard.ClipboardHistory
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.R

/** Clipboard history with pin / delete, like Samsung's clipboard panel. */
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
        val pad = UiKit.dp(context, 6f)

        val header = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "Clipboard"
            setTextColor(theme.keyText)
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(UiKit.dp(context, 12f), 0, 0, 0)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            UiKit.textButton(context, theme, "Clear", background = theme.background, textColor = theme.accent) {
                history.clearUnpinned(); refresh()
            },
            LayoutParams(UiKit.dp(context, 72f), UiKit.dp(context, 34f)),
        )
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 40f)))

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
                text = "Nothing here yet.\nCopy some text and it'll be waiting for you 🐾"
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
        background = UiKit.keyBackground(context, theme, theme.key, 12f)
        setPadding(UiKit.dp(context, 12f), UiKit.dp(context, 4f), UiKit.dp(context, 4f), UiKit.dp(context, 4f))
        setOnClickListener { onPaste(clip.text) }
        addView(TextView(context).apply {
            text = clip.text
            setTextColor(theme.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val size = UiKit.dp(context, 38f)
        addView(UiKit.iconButton(
            context, theme, if (clip.pinned) R.drawable.ic_push_pin_fill1 else R.drawable.ic_push_pin,
            if (clip.pinned) "Unpin" else "Pin", background = theme.key,
            tint = if (clip.pinned) theme.accent else theme.hintText,
        ) { history.togglePin(clip); refresh() }, LayoutParams(size, size))
        addView(UiKit.iconButton(context, theme, R.drawable.ic_delete, "Delete", background = theme.key, tint = theme.hintText) {
            history.remove(clip); refresh()
        }, LayoutParams(size, size))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, UiKit.dp(context, 3f), 0, UiKit.dp(context, 3f))
        }
    }
}
