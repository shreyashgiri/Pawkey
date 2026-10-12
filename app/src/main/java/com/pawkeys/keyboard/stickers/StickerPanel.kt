package com.pawkeys.keyboard.stickers

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.panels.UiKit

/** Stickers: the Whiskers pack in a scrolling grid. Tap one to send it. */
@SuppressLint("ViewConstructor")
class StickerPanel(
    context: Context,
    theme: KeyboardTheme,
    private val onSticker: (Sticker) -> Unit,
    onBack: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        addView(UiKit.header(context, theme, "Whiskers stickers"), LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f)))
        val grid = LinearLayout(context).apply { orientation = VERTICAL }
        val preview = UiKit.dp(context, 76f)
        StickerArt.pack.chunked(COLUMNS).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            chunk.forEach { st ->
                row.addView(ImageView(context).apply {
                    setImageBitmap(StickerArt.render(st, preview))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = st.caption
                    background = UiKit.keyBackground(context, theme, theme.background, 14f)
                    setPadding(UiKit.dp(context, 4f), UiKit.dp(context, 4f), UiKit.dp(context, 4f), UiKit.dp(context, 4f))
                    setOnClickListener { onSticker(st) }
                }, UiKit.weighted(1f, 2f, context))
            }
            repeat(COLUMNS - chunk.size) { row.addView(View(context), UiKit.weighted(1f, 2f, context)) }
            grid.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, preview + UiKit.dp(context, 4f)))
        }
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(context).apply { orientation = HORIZONTAL }
        bar.addView(UiKit.textButton(context, theme, "ABC") { onBack() }, UiKit.weighted(1f, 3f, context))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 46f)))
    }

    companion object {
        private const val COLUMNS = 4
    }
}
