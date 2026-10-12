package com.pawkeys.keyboard.panels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.pawkeys.keyboard.KeyboardTheme
import com.pawkeys.keyboard.Prefs
import com.pawkeys.keyboard.R

/** Lays [tiles] out in a grid of [columns], filling the panel. */
private fun LinearLayout.grid(tiles: List<View>, columns: Int) {
    tiles.chunked(columns).forEach { chunk ->
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        chunk.forEach { row.addView(it, UiKit.weighted(1f, 3f, context)) }
        repeat(columns - chunk.size) { row.addView(View(context), UiKit.weighted(1f, 3f, context)) }
        addView(row, UiKit.weighted(1f, 0f, context, vertical = true))
    }
}

/** The Den: every PawKeys tool one tap away, opened from the paw on the Whisker bar. */
@SuppressLint("ViewConstructor")
class PawMenuPanel(context: Context, theme: KeyboardTheme, prefs: Prefs, private val onAction: (Item) -> Unit) : LinearLayout(context) {
    enum class Item { EMOJI, CLIPBOARD, TEXT_EDIT, ONE_HANDED, SIZE, THEMES, NUMBER_ROW, GESTURES, SOUND, VIBRATION, SHORTCUTS, SETTINGS }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 6f)
        setPadding(pad, 0, pad, pad)
        addView(UiKit.header(context, theme, "Den"), LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f)))
        val gesture = when (prefs.gestureMode) { "glide" -> "Prowl"; "off" -> "No gestures"; else -> "Flicks" }
        val tiles = listOf(
            Triple(Item.EMOJI, R.drawable.ic_mood, "Emoji"),
            Triple(Item.CLIPBOARD, R.drawable.ic_content_paste, "Stash"),
            Triple(Item.TEXT_EDIT, R.drawable.ic_edit_note, "Groom"),
            Triple(Item.ONE_HANDED, R.drawable.ic_front_hand, "Single paw"),
            Triple(Item.SIZE, R.drawable.ic_height, "Stretch"),
            Triple(Item.THEMES, R.drawable.ic_palette, "Coats"),
            Triple(Item.NUMBER_ROW, R.drawable.ic_keyboard, "Number row"),
            Triple(Item.GESTURES, R.drawable.ic_gesture, gesture),
            Triple(Item.SOUND, R.drawable.ic_volume_up, "Sound"),
            Triple(Item.VIBRATION, R.drawable.ic_vibration, "Haptics"),
            Triple(Item.SHORTCUTS, R.drawable.ic_short_text, "Tricks"),
            Triple(Item.SETTINGS, R.drawable.ic_settings, "Settings"),
        ).map { (item, icon, label) ->
            val on = when (item) {
                Item.NUMBER_ROW -> prefs.numberRow
                Item.SOUND -> prefs.sound
                Item.VIBRATION -> prefs.vibrate
                Item.ONE_HANDED -> prefs.oneHanded != "off"
                Item.GESTURES -> prefs.gestureMode != "off"
                else -> false
            }
            UiKit.tile(context, theme, icon, label, on) { onAction(item) }
        }
        grid(tiles, 4)
    }
}

/** Coats: a mini keyboard preview per colour theme. */
@SuppressLint("ViewConstructor")
class ThemePanel(context: Context, theme: KeyboardTheme, current: String, private val onPick: (String) -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 6f)
        setPadding(pad, 0, pad, pad)
        addView(UiKit.header(context, theme, "Coats"), LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f)))
        val options = listOf("system" to "Auto") + KeyboardTheme.ALL.map { it.id to it.name }
        val tiles = options.map { (id, name) ->
            val t = KeyboardTheme.resolve(context, id)
            swatch(t, name, id == current, theme) { onPick(id) }
        }
        grid(tiles, 4)
    }

    private fun swatch(t: KeyboardTheme, name: String, selected: Boolean, ui: KeyboardTheme, onClick: () -> Unit): View {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(context, 12f).toFloat()
                setColor(t.background)
                setStroke(UiKit.dp(context, if (selected) 2.5f else 1f), if (selected) ui.accent else ui.border)
            }
            setOnClickListener { onClick() }
            contentDescription = name
        }
        // Three tiny keys plus the accent "enter" key.
        val keys = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        listOf(t.key, t.key, t.functionKey, t.accent).forEach { c ->
            keys.addView(View(context).apply {
                background = GradientDrawable().apply { cornerRadius = UiKit.dp(context, 3f).toFloat(); setColor(c) }
            }, LayoutParams(UiKit.dp(context, 11f), UiKit.dp(context, 14f)).apply { setMargins(2, 2, 2, 2) })
        }
        box.addView(keys)
        box.addView(TextView(context).apply {
            text = name
            setTextColor(t.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(4, UiKit.dp(context, 3f), 4, 0)
        })
        return box
    }
}

/**
 * Replaces the suggestion bar while resizing so the keys stay visible and the
 * change is live, like Samsung's keyboard size mode.
 */
@SuppressLint("ViewConstructor")
class SizeBar(context: Context, theme: KeyboardTheme, start: Int, private val onChange: (Int) -> Unit, private val onDone: () -> Unit) : LinearLayout(context) {
    private var value = start
    private val label = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 3f)
        setPadding(pad, pad, pad, pad)
        val small = { w: Float -> LayoutParams(UiKit.dp(context, w), LayoutParams.MATCH_PARENT).apply { setMargins(pad, 0, pad, 0) } }
        addView(UiKit.iconButton(context, theme, R.drawable.ic_remove, "Smaller") { set(value - 5) }, small(44f))
        label.apply {
            setTextColor(theme.keyText)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        addView(label, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(UiKit.iconButton(context, theme, R.drawable.ic_add, "Bigger") { set(value + 5) }, small(44f))
        addView(UiKit.textButton(context, theme, "Reset") { set(100) }, small(64f))
        addView(UiKit.iconButton(context, theme, R.drawable.ic_check, "Done", background = theme.accent, tint = theme.onAccent) { onDone() }, small(44f))
        update()
    }

    private fun set(v: Int) {
        value = v.coerceIn(Prefs.MIN_HEIGHT, Prefs.MAX_HEIGHT)
        update()
        onChange(value)
    }

    private fun update() { label.text = "Stretch  $value%" }
}

/** Single paw side rail: move the keys to the other side, or go back to full width. */
@SuppressLint("ViewConstructor")
class OneHandedRail(context: Context, theme: KeyboardTheme, side: String, onSwitch: () -> Unit, onExit: () -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(theme.background)
        val size = UiKit.dp(context, 44f)
        val lp = { LayoutParams(size, size).apply { setMargins(0, UiKit.dp(context, 8f), 0, UiKit.dp(context, 8f)) } }
        addView(UiKit.iconButton(context, theme, R.drawable.ic_open_in_full, "Full width") { onExit() }, lp())
        addView(UiKit.iconButton(
            context, theme,
            if (side == "left") R.drawable.ic_keyboard_arrow_right else R.drawable.ic_keyboard_arrow_left,
            "Move keyboard",
        ) { onSwitch() }, lp())
        addView(FrameLayout(context), LayoutParams(1, 1))
    }
}
