package com.pawkeys.keyboard.translate

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.R
import com.pawkeys.keyboard.panels.UiKit

/**
 * Replaces the Whisker bar while translating:
 *   [English ▾]  ⇄  [Hindi ▾]                      ✕
 *   what you typed → translation                [Insert]
 */
@SuppressLint("ViewConstructor")
class TranslateBar(
    context: Context,
    private val theme: KeyboardTheme,
    private val listener: Listener,
) : LinearLayout(context) {

    interface Listener {
        fun onPickLanguage(source: Boolean)
        fun onSwapLanguages()
        fun onInsertTranslation()
        fun onCloseTranslate()
    }

    private val from = chip { listener.onPickLanguage(true) }
    private val to = chip { listener.onPickLanguage(false) }
    private val source = TextView(context)
    private val result = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 8f)
        setPadding(pad, UiKit.dp(context, 4f), pad, UiKit.dp(context, 4f))

        val top = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(from, LayoutParams(LayoutParams.WRAP_CONTENT, UiKit.dp(context, 34f)))
        top.addView(UiKit.iconButton(context, theme, R.drawable.ic_swap_horiz, "Swap languages", background = theme.background, tint = theme.stripText) {
            listener.onSwapLanguages()
        }, LayoutParams(UiKit.dp(context, 40f), UiKit.dp(context, 34f)))
        top.addView(to, LayoutParams(LayoutParams.WRAP_CONTENT, UiKit.dp(context, 34f)))
        top.addView(View(context), LayoutParams(0, 1, 1f))
        top.addView(UiKit.iconButton(context, theme, R.drawable.ic_close, "Close translate", background = theme.background, tint = theme.stripText) {
            listener.onCloseTranslate()
        }, LayoutParams(UiKit.dp(context, 40f), UiKit.dp(context, 34f)))
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 40f)))

        val bottom = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.keyBackground(context, theme, theme.key, 12f)
            setPadding(UiKit.dp(context, 12f), 0, UiKit.dp(context, 4f), 0)
        }
        val texts = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        source.apply {
            setTextColor(theme.hintText); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); maxLines = 1
            ellipsize = TextUtils.TruncateAt.START; typeface = UiKit.REGULAR
        }
        result.apply {
            setTextColor(theme.keyText); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); maxLines = 1
            ellipsize = TextUtils.TruncateAt.START; typeface = UiKit.MEDIUM
        }
        texts.addView(source)
        texts.addView(result)
        bottom.addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(UiKit.textButton(context, theme, "Insert", background = theme.accent, textColor = theme.onAccent, sizeSp = 13.5f) {
            listener.onInsertTranslation()
        }, LayoutParams(UiKit.dp(context, 72f), UiKit.dp(context, 34f)))
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 48f)))
    }

    private fun chip(onClick: () -> Unit) = TextView(context).apply {
        setTextColor(theme.keyText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        typeface = UiKit.MEDIUM
        gravity = Gravity.CENTER
        setPadding(UiKit.dp(context, 12f), 0, UiKit.dp(context, 12f), 0)
        background = UiKit.keyBackground(context, theme, theme.functionKey, 17f)
        setOnClickListener { onClick() }
    }

    fun setLanguages(fromCode: String, toCode: String) {
        from.text = "${Languages.name(fromCode)} ▾"
        to.text = "${Languages.name(toCode)} ▾"
    }

    /** [status] (e.g. "Downloading…") replaces the translation line while set. */
    fun show(sourceText: String, translated: String, status: String?) {
        source.text = sourceText.ifEmpty { "Type in ${from.text.removeSuffix(" ▾")}" }
        result.text = status ?: translated.ifEmpty { " " }
        result.setTextColor(if (status != null) theme.hintText else theme.keyText)
    }
}

/** Language picker shown in place of the keys. */
@SuppressLint("ViewConstructor")
class LanguagePanel(
    context: Context,
    theme: KeyboardTheme,
    title: String,
    selected: String,
    onPick: (String) -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        addView(UiKit.header(context, theme, title), LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f)))
        val list = LinearLayout(context).apply { orientation = VERTICAL }
        Languages.all.chunked(3).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            chunk.forEach { (code, name) ->
                val chosen = code == selected
                row.addView(UiKit.textButton(context, theme, name,
                    background = if (chosen) theme.accent else theme.key,
                    textColor = if (chosen) theme.onAccent else theme.keyText, bold = chosen, sizeSp = 13.5f) { onPick(code) },
                    UiKit.weighted(1f, 3f, context))
            }
            repeat(3 - chunk.size) { row.addView(View(context), UiKit.weighted(1f, 3f, context)) }
            list.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 46f)))
        }
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(list)
            setPadding(UiKit.dp(context, 4f), 0, UiKit.dp(context, 4f), UiKit.dp(context, 4f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }
}
