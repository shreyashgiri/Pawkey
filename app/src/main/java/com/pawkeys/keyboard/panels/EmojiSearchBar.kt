package com.pawkeys.keyboard.panels

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.R

/**
 * Replaces the Whisker bar during emoji search: the query on top, matching emoji
 * in a scrolling row underneath. The keys stay visible for typing the query.
 */
@SuppressLint("ViewConstructor")
class EmojiSearchBar(
    context: Context,
    private val theme: KeyboardTheme,
    private val onPick: (String) -> Unit,
    onClose: () -> Unit,
) : LinearLayout(context) {
    private val query = TextView(context)
    private val results = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 8f)
        setPadding(pad, UiKit.dp(context, 4f), pad, 0)

        val field = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.keyBackground(context, theme, theme.key, 20f)
            setPadding(UiKit.dp(context, 12f), 0, UiKit.dp(context, 2f), 0)
        }
        field.addView(ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_search)!!.mutate().apply { setTint(theme.hintText) })
        }, LayoutParams(UiKit.dp(context, 20f), UiKit.dp(context, 20f)))
        query.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = UiKit.REGULAR
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
            setPadding(UiKit.dp(context, 10f), 0, 0, 0)
        }
        field.addView(query, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        field.addView(UiKit.iconButton(context, theme, R.drawable.ic_close, "Close search", background = theme.key, tint = theme.hintText) { onClose() },
            LayoutParams(UiKit.dp(context, 36f), UiKit.dp(context, 36f)))
        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 40f)))

        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(results)
        }, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 48f)))
        show("", emptyList())
    }

    private val glyphs = android.graphics.Paint()

    fun show(text: String, found: List<String>) {
        // Only emoji this phone's font can draw (older Androids lack newer ones).
        val matches = found.filter { glyphs.hasGlyph(it) }
        query.text = text.ifEmpty { "Search emoji" }
        query.setTextColor(if (text.isEmpty()) theme.hintText else theme.keyText)
        results.removeAllViews()
        if (text.isNotEmpty() && matches.isEmpty()) {
            results.addView(TextView(context).apply {
                this.text = "No emoji found"
                setTextColor(theme.hintText)
                setPadding(UiKit.dp(context, 8f), 0, 0, 0)
            })
        }
        for (e in matches) {
            results.addView(TextView(context).apply {
                this.text = e
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                gravity = Gravity.CENTER
                background = UiKit.keyBackground(context, theme, theme.background, 12f)
                setOnClickListener { onPick(e) }
            }, LayoutParams(UiKit.dp(context, 46f), UiKit.dp(context, 46f)))
        }
    }
}
