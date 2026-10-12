package com.pawkeys.keyboard.gifs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.panels.UiKit
import java.nio.ByteBuffer

/**
 * GIFs from GIPHY: a two-column grid of animated previews. Without a key it explains
 * how to add one. Shows "Powered by GIPHY" as GIPHY's terms require.
 */
@SuppressLint("ViewConstructor")
class GifPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val source: GifSource?,
    private val query: String?,
    private val onGif: (Gif) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) : LinearLayout(context) {
    private val content = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val title = if (query.isNullOrBlank()) "Trending GIFs" else "GIFs: $query"
        addView(UiKit.header(context, theme, title, if (source != null) "Search" else null) { onSearch() },
            LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f)))
        addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
            setPadding(UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f), 0)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bar.addView(UiKit.textButton(context, theme, "ABC") { onBack() }, UiKit.weighted(1f, 3f, context))
        bar.addView(TextView(context).apply {
            text = "Powered by GIPHY"
            setTextColor(theme.hintText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
        }, UiKit.weighted(2f, 3f, context))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 46f)))

        if (source == null) {
            message("Add your free GIPHY key to see GIFs:\nSettings → GIFs → GIPHY key", "Open settings", onOpenSettings)
        } else {
            message("Loading…")
            source.list(query) { gifs, error ->
                when {
                    error != null -> message(error)
                    gifs.isNullOrEmpty() -> message("No GIFs found")
                    else -> showGrid(gifs)
                }
            }
        }
    }

    private fun message(text: String, action: String? = null, onAction: () -> Unit = {}) {
        content.removeAllViews()
        content.addView(TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(theme.hintText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.2f)
            setPadding(0, UiKit.dp(context, 28f), 0, UiKit.dp(context, 12f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (action != null) {
            content.addView(UiKit.textButton(context, theme, action, background = theme.accent, textColor = theme.onAccent) { onAction() },
                LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 40f)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
    }

    private fun showGrid(gifs: List<Gif>) {
        content.removeAllViews()
        val cell = UiKit.dp(context, 104f)
        gifs.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            pair.forEach { gif ->
                val image = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = gif.title
                    background = UiKit.keyBackground(context, theme, theme.key, 12f)
                    clipToOutline = true
                    setOnClickListener { onGif(gif) }
                }
                row.addView(image, UiKit.weighted(1f, 3f, context))
                source?.fetch(gif.preview) { bytes -> if (bytes != null) show(image, bytes) }
            }
            if (pair.size == 1) row.addView(View(context), UiKit.weighted(1f, 3f, context))
            content.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, cell))
        }
    }

    /** Animated on Android 9+, first frame on older phones. */
    private fun show(view: ImageView, bytes: ByteArray) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
                view.setImageDrawable(d)
                (d as? AnimatedImageDrawable)?.start()
            } else {
                view.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            }
        }
    }
}
