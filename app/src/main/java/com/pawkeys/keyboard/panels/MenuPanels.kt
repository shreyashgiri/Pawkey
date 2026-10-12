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

/**
 * The Den: every PawKeys tool one tap away, opened from the paw on the Whisker bar.
 * "Customize" switches to pinning mode: tap tools to add them to the bar (numbered
 * in bar order) or take them off.
 */
@SuppressLint("ViewConstructor")
class PawMenuPanel(
    context: Context,
    private val theme: KeyboardTheme,
    private val prefs: Prefs,
    private val onAction: (Action) -> Unit,
) : LinearLayout(context) {
    private var customizing = false
    private val body = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 6f)
        setPadding(pad, 0, pad, pad)
        addView(android.widget.ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(body)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        render()
    }

    private fun render() {
        body.removeAllViews()
        body.addView(
            if (customizing) UiKit.header(context, theme, "Tap to pin to your bar", "Done") { customizing = false; render() }
            else UiKit.header(context, theme, "Den", "Customize") { customizing = true; render() },
            LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f)),
        )
        val pinned = prefs.toolbarButtons
        val tiles = Action.entries.filter { it.pinnable }.map { a ->
            val on = when (a) {
                Action.NUMBER_ROW -> prefs.numberRow
                Action.SOUND -> prefs.sound
                Action.VIBRATION -> prefs.vibrate
                Action.ONE_HANDED -> prefs.oneHanded != "off"
                Action.GESTURES -> prefs.gestureMode != "off"
                else -> false
            }
            val label = when (a) {
                Action.GESTURES -> when (prefs.gestureMode) { "glide" -> "Prowl"; "off" -> "No gestures"; else -> "Flicks" }
                Action.SOUND -> if (prefs.sound) com.pawkeys.keyboard.Feedback.label(prefs.soundStyle) else "Silent"
                else -> a.label
            }
            val tile = UiKit.tile(context, theme, a.icon, label, if (customizing) a in pinned else on) {
                if (customizing) togglePin(a) else onAction(a)
            }
            if (customizing) badge(tile, pinned.indexOf(a)) else tile
        }
        tiles.chunked(COLUMNS).forEach { chunk ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            chunk.forEach { row.addView(it, UiKit.weighted(1f, 3f, context)) }
            repeat(COLUMNS - chunk.size) { row.addView(View(context), UiKit.weighted(1f, 3f, context)) }
            body.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 78f)))
        }
        if (customizing) {
            body.addView(UiKit.textButton(context, theme, "Reset bar", background = theme.background, textColor = theme.hintText, sizeSp = 13f) {
                prefs.toolbarButtons = com.pawkeys.keyboard.CandidateStrip.DEFAULT_BUTTONS
                render()
            }, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 40f)))
        }
    }

    private fun togglePin(a: Action) {
        val list = prefs.toolbarButtons.toMutableList()
        if (a in list) list.remove(a)
        else if (list.size < com.pawkeys.keyboard.CandidateStrip.MAX_BUTTONS) list += a
        else {
            android.widget.Toast.makeText(context, "The bar holds ${com.pawkeys.keyboard.CandidateStrip.MAX_BUTTONS} tools. Unpin one first.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        prefs.toolbarButtons = list
        render()
    }

    /** Pin badge: the tool's position on the bar, or a faint "+" if it isn't pinned. */
    private fun badge(tile: View, position: Int): View = FrameLayout(context).apply {
        addView(tile, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(TextView(context).apply {
            text = if (position >= 0) "${position + 1}" else "+"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = UiKit.MEDIUM
            setTextColor(if (position >= 0) theme.onAccent else theme.hintText)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (position >= 0) theme.accent else theme.functionKey)
            }
        }, FrameLayout.LayoutParams(UiKit.dp(context, 18f), UiKit.dp(context, 18f), Gravity.TOP or Gravity.END).apply {
            setMargins(0, UiKit.dp(context, 2f), UiKit.dp(context, 10f), 0)
        })
    }

    companion object {
        private const val COLUMNS = 5
    }
}

private typealias Action = com.pawkeys.keyboard.CandidateStrip.ToolbarAction

/** Coats: a mini keyboard preview per colour theme. */
@SuppressLint("ViewConstructor")
class ThemePanel(context: Context, theme: KeyboardTheme, current: String, breed: com.pawkeys.keyboard.Breed, private val onPick: (String) -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 6f)
        setPadding(pad, 0, pad, pad)
        addView(UiKit.header(context, theme, "Coats"), LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f)))
        val options = listOf("system" to "Auto") + KeyboardTheme.ALL.map { it.id to it.name }
        val tiles = options.map { (id, name) ->
            val t = KeyboardTheme.resolve(context, id, breed)
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

/** Breeds: pick the keyboard style. Each card is a tiny preview of that breed's keys. */
@SuppressLint("ViewConstructor")
class BreedPanel(context: Context, theme: KeyboardTheme, current: com.pawkeys.keyboard.Breed, private val onPick: (com.pawkeys.keyboard.Breed) -> Unit) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val pad = UiKit.dp(context, 6f)
        setPadding(pad, 0, pad, pad)
        addView(UiKit.header(context, theme, "Breeds"), LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 38f)))
        val night = theme.isDark
        val cards = com.pawkeys.keyboard.Breed.entries.map { b -> card(b, if (night) b.darkCoat else b.lightCoat, b == current, theme) }
        grid(cards, 2)
    }

    private fun card(b: com.pawkeys.keyboard.Breed, t: KeyboardTheme, selected: Boolean, ui: KeyboardTheme): View {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(context, 14f).toFloat()
                setColor(t.background)
                setStroke(UiKit.dp(context, if (selected) 2.5f else 1f), if (selected) ui.accent else ui.border)
            }
            setOnClickListener { onPick(b) }
            contentDescription = b.label
        }
        val keys = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        val r = UiKit.dp(context, b.radiusDp * 0.6f).toFloat()
        val enterColor = when (b.enter) {
            com.pawkeys.keyboard.Breed.EnterStyle.ACCENT, com.pawkeys.keyboard.Breed.EnterStyle.PILL, com.pawkeys.keyboard.Breed.EnterStyle.IOS_TEXT -> t.accent
            com.pawkeys.keyboard.Breed.EnterStyle.FUNCTION -> t.functionKey
        }
        listOf(t.key, t.key, t.key, t.functionKey, enterColor).forEachIndexed { i, c ->
            keys.addView(View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = if (i == 4 && b.enter == com.pawkeys.keyboard.Breed.EnterStyle.PILL) 100f else r
                    setColor(c)
                }
            }, LayoutParams(UiKit.dp(context, if (i == 4) 22f else 14f), UiKit.dp(context, 18f)).apply { setMargins(3, 3, 3, 3) })
        }
        box.addView(keys)
        box.addView(TextView(context).apply {
            text = b.label
            setTextColor(t.keyText)
            typeface = UiKit.MEDIUM
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            gravity = Gravity.CENTER
            setPadding(4, UiKit.dp(context, 6f), 4, 0)
        })
        return box
    }
}
